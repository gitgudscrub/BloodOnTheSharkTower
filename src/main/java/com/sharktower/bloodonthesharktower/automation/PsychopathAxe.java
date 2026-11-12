package com.sharktower.bloodonthesharktower.automation;

import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Psychopath: "Each day, before nominations, you may publicly choose a
 * player: they die."
 *
 * From dawn until nominations open, the living Psychopath (or an Alchemist
 * with a "Psychopath" reminder) carries the Psychopath's axe. Hitting a
 * living seated player with it is the public choice: everyone is told who
 * swung at whom straight away, the axe is taken, and the Storyteller rules
 * whether the target dies (a drunk or poisoned Psychopath, the Sailor, the
 * Tea Lady and so on). The axe does no damage itself, and it is taken away
 * when nominations open, at dusk, or when its holder dies or stops being
 * the Psychopath. Hits on anyone not playing, or already dead, are free.
 */
public final class PsychopathAxe {
    private static final String PROMPT = "psychopath";
    private static final String MARKER_KEY = "blood_on_the_sharktower_psychopath_axe";
    private static final String DAY_KEY = "blood_on_the_sharktower_psychopath_day";

    /** The day each player used the axe, was told they have it, or was told to make room. */
    private static final Map<UUID, Integer> USED_DAY = new HashMap<>();
    private static final Map<UUID, Integer> NOTIFIED_DAY = new HashMap<>();
    private static final Map<UUID, Integer> ROOM_WARNED_DAY = new HashMap<>();
    /** The last day nominations were open: no axe for the rest of that day. */
    private static int nominationsDay = Integer.MIN_VALUE;

    private static UUID pendingTarget;
    private static int pendingDay = -1;

    private PsychopathAxe() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!isAxe(player.getItemInHand(hand))) return InteractionResult.PASS;
            // The client lets the attack through so the server hears about it; the server cancels the damage.
            if (level.isClientSide()) return InteractionResult.PASS;
            if (player instanceof ServerPlayer attacker) swing(attacker, entity);
            return InteractionResult.FAIL;
        });
    }

    public static void reset() {
        USED_DAY.clear();
        NOTIFIED_DAY.clear();
        ROOM_WARNED_DAY.clear();
        nominationsDay = Integer.MIN_VALUE;
        clearPending();
    }

    static void onDusk() {
        clearPending();
    }

    /** True for the Psychopath, or an Alchemist the Storyteller gave the Psychopath ability. */
    static boolean hasAbility(UUID player) {
        if (RoleAutomation.is(player, Role.PSYCHOPATH)) return true;
        if (!RoleAutomation.is(player, Role.ALCHEMIST)) return false;
        for (Reminder reminder : StorytellerState.REMINDERS.getOrDefault(player, List.of())) {
            if (reminder.role().orElse(null) == Role.PSYCHOPATH
                    || reminder.text().toLowerCase(Locale.ROOT).contains("psychopath")) return true;
        }
        return false;
    }

    /** Why {@code player} may not carry the axe right now, or null when they may. */
    static String refusal(UUID player) {
        if (ServerState.gameEnded) return "The game has ended.";
        if (ServerState.currentDay <= 0 || ServerState.currentNight != ServerState.currentDay) {
            return "The Psychopath kills during the day.";
        }
        if (nominationsDay == ServerState.currentDay || DaytimeState.areNominationsOpen()) {
            return "Nominations have started: the Psychopath can no longer kill today.";
        }
        if (!RoleAutomation.isAlive(player)) return "Dead players cannot use the Psychopath ability.";
        if (!hasAbility(player)) return "Only the Psychopath can use the axe.";
        if (USED_DAY.getOrDefault(player, Integer.MIN_VALUE) == ServerState.currentDay) {
            return "You have already used the axe today.";
        }
        return null;
    }

    private static void swing(ServerPlayer attacker, Entity entity) {
        MinecraftServer server = attacker.level().getServer();
        UUID self = attacker.getUUID();
        String refusal = refusal(self);
        if (refusal != null) {
            attacker.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
            return;
        }
        if (!(entity instanceof ServerPlayer target) || target == attacker) return;
        UUID victim = target.getUUID();
        String whom = RoleAutomation.label(server, victim);
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(victim)) {
            attacker.sendSystemMessage(Component.literal(whom + " is not playing. Nothing happens.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!RoleAutomation.isAlive(victim)) {
            attacker.sendSystemMessage(Component.literal(whom + " is already dead. Nothing happens.").withStyle(ChatFormatting.GRAY));
            return;
        }

        USED_DAY.put(self, ServerState.currentDay);
        removeAxes(attacker);
        String who = RoleAutomation.label(server, self);
        attacker.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.8F);
        RoleAutomation.announce(server, who + " attacks " + whom + " with the Psychopath's axe!", ChatFormatting.DARK_RED);

        String why = (RoleAutomation.is(self, Role.PSYCHOPATH) ? who + " is the Psychopath"
                : who + " is the Alchemist with the Psychopath ability");
        if (RoleAutomation.droisoned(self)) {
            why += " but drunk or poisoned, so nothing should happen.";
        } else {
            String survives = survivalReason(victim);
            why += survives == null ? ": " + whom + " should die." : ", but " + whom + " should survive: " + survives + ".";
        }
        pendingTarget = victim;
        pendingDay = ServerState.currentDay;
        RoleAutomation.tellStorytellers(server, Component.literal("Psychopath: " + why + " Decide: ").withStyle(ChatFormatting.DARK_RED)
                .append(RoleAutomation.button("[" + whom + " dies]", "/bots automation psychopath kill", whom + " dies now"))
                .append(Component.literal(" "))
                .append(RoleAutomation.button("[Nothing happens]", "/bots automation psychopath dismiss", "The attack has no effect")));
        RoleAutomation.offer(server, new StorytellerPrompt(PROMPT, "Psychopath", who + " attacked " + whom + ". " + why,
                List.of(StorytellerPrompt.Action.danger(whom + " dies", "/bots automation psychopath kill"),
                        StorytellerPrompt.Action.ok("No effect", "/bots automation psychopath dismiss"))));
        StateBroadcaster.broadcastGrimoire(server);
    }

    /** Why a sober Psychopath's kill should not land on {@code target}, or null. */
    private static String survivalReason(UUID target) {
        if (RoleAutomation.is(target, Role.SAILOR) && !RoleAutomation.droisoned(target)) return "the sober Sailor cannot die";
        if (RoleAutomation.hasReminder(target, "Cannot Die") && !RoleAutomation.protectorDroisoned(Role.TEA_LADY)) {
            return "the Tea Lady says they cannot die";
        }
        if (RoleAutomation.is(target, Role.FOOL) && !RoleAutomation.droisoned(target) && !RoleAutomation.abilitySpent(target)) {
            return "the Fool survives their first death";
        }
        return null;
    }

    /** The Storyteller confirmed the kill. */
    public static String kill(MinecraftServer server) {
        if (pendingTarget == null || pendingDay != ServerState.currentDay) return "There is no Psychopath attack waiting.";
        UUID target = pendingTarget;
        clearPending();
        if (!RoleAutomation.isAlive(target)) return "That player is already dead.";
        RoleAutomation.killPublicly(server, target);
        return null;
    }

    /** The Storyteller ruled that the attack has no effect. */
    public static String dismiss(MinecraftServer server) {
        if (pendingTarget == null) return "There is no Psychopath attack waiting.";
        clearPending();
        RoleAutomation.announce(server, "Nothing happens.", ChatFormatting.GRAY);
        return null;
    }

    private static void clearPending() {
        pendingTarget = null;
        pendingDay = -1;
        RoleAutomation.resolve(PROMPT);
    }

    // ------------------------------------------------------------------ Item

    private static ItemStack create() {
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        axe.set(DataComponents.CUSTOM_NAME, Component.literal("Psychopath's Axe").withStyle(ChatFormatting.DARK_RED));
        CompoundTag marker = new CompoundTag();
        marker.putBoolean(MARKER_KEY, true);
        marker.putInt(DAY_KEY, ServerState.currentDay);
        axe.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        return axe;
    }

    public static boolean isAxe(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.IRON_AXE)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(MARKER_KEY, false);
    }

    private static int day(ItemStack stack) {
        return stack.get(DataComponents.CUSTOM_DATA).copyTag().getIntOr(DAY_KEY, -1);
    }

    private static void removeAxes(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isAxe(inventory.getItem(slot))) inventory.setItem(slot, ItemStack.EMPTY);
        }
        if (isAxe(player.containerMenu.getCarried())) player.containerMenu.setCarried(ItemStack.EMPTY);
        inventory.setChanged();
    }

    /** A free slot that is not the one in hand: the hotbar first, then the backpack. */
    private static int freeSlot(Inventory inventory) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (slot != inventory.getSelectedSlot() && inventory.getItem(slot).isEmpty()) return slot;
        }
        return -1;
    }

    /** Hands the axe to the Psychopath at dawn and takes every stale or misplaced axe away. */
    public static void serverTick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 10 != 0) return;
        if (ServerState.currentDay > 0 && ServerState.currentNight == ServerState.currentDay
                && DaytimeState.areNominationsOpen()) {
            nominationsDay = ServerState.currentDay;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            boolean may = refusal(id) == null;
            boolean kept = false;
            Inventory inventory = player.getInventory();
            boolean changed = false;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!isAxe(stack)) continue;
                if (may && !kept && day(stack) == ServerState.currentDay) {
                    kept = true;
                    continue;
                }
                inventory.setItem(slot, ItemStack.EMPTY);
                changed = true;
            }
            ItemStack carried = player.containerMenu.getCarried();
            if (isAxe(carried)) {
                if (may && !kept && day(carried) == ServerState.currentDay) kept = true;
                else player.containerMenu.setCarried(ItemStack.EMPTY);
            }
            if (may && !kept) {
                int slot = freeSlot(inventory);
                if (slot >= 0) {
                    inventory.setItem(slot, create());
                    changed = true;
                }
                if (slot >= 0 && once(NOTIFIED_DAY, id)) {
                    player.sendSystemMessage(Component.literal("You have the Psychopath's axe for today. Hit a living player with it "
                            + "before nominations to kill them: everyone will see who swung. Keep it out of your hand until you strike.")
                            .withStyle(ChatFormatting.DARK_RED));
                    RoleAutomation.tellStorytellers(server, Component.literal("Psychopath: "
                            + RoleAutomation.label(server, id) + " has the axe until nominations open.").withStyle(ChatFormatting.GRAY));
                } else if (slot < 0 && once(ROOM_WARNED_DAY, id)) {
                    player.sendSystemMessage(Component.literal("Make room in your inventory for the Psychopath's axe.")
                            .withStyle(ChatFormatting.RED));
                }
            }
            if (changed) inventory.setChanged();
        }
    }

    /** True the first time this day for {@code id} in {@code days}. */
    private static boolean once(Map<UUID, Integer> days, UUID id) {
        Integer previous = days.put(id, ServerState.currentDay);
        return previous == null || previous != ServerState.currentDay;
    }
}
