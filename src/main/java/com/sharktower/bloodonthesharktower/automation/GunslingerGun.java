package com.sharktower.bloodonthesharktower.automation;

import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.states.ServerState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Gunslinger: "Each day, after the 1st vote has been tallied, you may
 * choose a player that voted: they die."
 *
 * When the day's first vote is tallied, the living Gunslinger is handed a
 * loaded crossbow. It stays until the next vote starts, dusk, or they shoot
 * someone who voted. A bolt that hits a living player who raised a hand in
 * that vote is the choice: everyone is told, and the Storyteller rules in
 * Resolve whether they die. Misses and hits on players who did not vote are
 * free, and the crossbow comes back for another try while the window is
 * open. Bolts never hurt anyone.
 */
public final class GunslingerGun {
    private static final String PROMPT = "gunslinger";
    private static final String MARKER_KEY = "blood_on_the_sharktower_gunslinger";
    private static final String DAY_KEY = "blood_on_the_sharktower_gunslinger_day";
    private static final String SHOT_KEY = "blood_on_the_sharktower_gunslinger_shot";
    private static final int BOLT_LIFE_TICKS = 80;

    private record Shot(UUID shooter, long firedAt) {}

    private static final Map<String, Shot> LIVE = new HashMap<>();
    /** Who voted in today's first vote; the window is open while {@code windowDay} is today. */
    private static final Set<UUID> VOTERS = new HashSet<>();
    private static int windowDay = Integer.MIN_VALUE;
    private static boolean windowClosed;
    /** The day the first vote was tallied: later votes do not reopen the window. */
    private static int firstVoteDay = Integer.MIN_VALUE;
    private static final Map<UUID, Integer> USED_DAY = new HashMap<>();
    private static final Map<UUID, Integer> NOTIFIED_DAY = new HashMap<>();

    private static UUID pendingTarget;
    private static int pendingDay = -1;

    private GunslingerGun() {}

    public static void reset() {
        LIVE.clear();
        VOTERS.clear();
        windowDay = Integer.MIN_VALUE;
        windowClosed = false;
        firstVoteDay = Integer.MIN_VALUE;
        USED_DAY.clear();
        NOTIFIED_DAY.clear();
        clearPending();
    }

    static void onDusk() {
        windowClosed = true;
        LIVE.clear();
        clearPending();
    }

    /** A vote was tallied. The day's first one opens the Gunslinger's window. */
    public static void onVoteTallied(MinecraftServer server, List<UUID> voters) {
        if (server == null || ServerState.currentDay <= 0 || firstVoteDay == ServerState.currentDay) return;
        firstVoteDay = ServerState.currentDay;
        VOTERS.clear();
        VOTERS.addAll(voters);
        windowDay = ServerState.currentDay;
        windowClosed = false;
    }

    private static boolean windowOpen() {
        return windowDay == ServerState.currentDay && !windowClosed && !ServerState.gameEnded
                && ServerState.currentNight == ServerState.currentDay;
    }

    private static boolean mayHold(UUID player) {
        return windowOpen() && RoleAutomation.is(player, Role.GUNSLINGER) && RoleAutomation.isAlive(player)
                && USED_DAY.getOrDefault(player, Integer.MIN_VALUE) != ServerState.currentDay;
    }

    // --------------------------------------------------------------- Shots

    public static boolean isGunslingerBolt(AbstractArrow arrow) {
        return isGun(arrow.getWeaponItem());
    }

    public static void boltHit(AbstractArrow arrow, Entity hit) {
        if (!(arrow.level() instanceof ServerLevel level)) return;
        arrow.discard();
        Shot shot = LIVE.remove(shotId(arrow.getWeaponItem()));
        if (shot == null) return;
        if (!(arrow.getOwner() instanceof ServerPlayer shooter) || !shooter.getUUID().equals(shot.shooter())) return;
        MinecraftServer server = level.getServer();
        if (!(hit instanceof ServerPlayer target) || target == shooter) {
            missed(shooter, "Your bolt missed.");
            return;
        }
        UUID victim = target.getUUID();
        String whom = RoleAutomation.label(server, victim);
        if (!mayHold(shooter.getUUID())) return;
        if (!RoleAutomation.isAlive(victim)) {
            missed(shooter, whom + " is not a living player.");
            return;
        }
        if (!VOTERS.contains(victim)) {
            missed(shooter, whom + " did not vote in the first vote.");
            return;
        }
        USED_DAY.put(shooter.getUUID(), ServerState.currentDay);
        level.playSound(null, target.blockPosition(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0F, 1.0F);
        String who = RoleAutomation.label(server, shooter.getUUID());
        RoleAutomation.announce(server, who + " the Gunslinger shoots " + whom + "!", ChatFormatting.DARK_RED);

        String why;
        if (RoleAutomation.droisoned(shooter.getUUID())) {
            why = who + " is drunk or poisoned, so nothing should happen.";
        } else {
            String survives = PsychopathAxe.survivalReason(victim);
            why = survives == null ? whom + " voted, so they should die."
                    : whom + " voted, but should survive: " + survives + ".";
        }
        pendingTarget = victim;
        pendingDay = ServerState.currentDay;
        RoleAutomation.tellStorytellers(server, Component.literal("Gunslinger: " + who + " shot " + whom + ". " + why
                        + " Decide: ").withStyle(ChatFormatting.DARK_RED)
                .append(RoleAutomation.button("[" + whom + " dies]", "/bots automation gunslinger kill", whom + " dies now"))
                .append(Component.literal(" "))
                .append(RoleAutomation.button("[Nothing happens]", "/bots automation gunslinger dismiss", "The shot has no effect")));
        RoleAutomation.offer(server, new StorytellerPrompt(PROMPT, "Gunslinger", who + " shot " + whom + ". " + why,
                List.of(StorytellerPrompt.Action.danger(whom + " dies", "/bots automation gunslinger kill"),
                        StorytellerPrompt.Action.ok("No effect", "/bots automation gunslinger dismiss"))));
        StateBroadcaster.broadcastGrimoire(server);
    }

    private static void missed(ServerPlayer shooter, String why) {
        String more = mayHold(shooter.getUUID()) ? " Nothing happens; you can try again until the next vote." : " Nothing happens.";
        shooter.sendSystemMessage(Component.literal(why + more).withStyle(ChatFormatting.GRAY));
    }

    public static String kill(MinecraftServer server) {
        if (pendingTarget == null || pendingDay != ServerState.currentDay) return "There is no Gunslinger shot waiting.";
        UUID target = pendingTarget;
        clearPending();
        if (!RoleAutomation.isAlive(target)) return "That player is already dead.";
        RoleAutomation.killPublicly(server, target);
        return null;
    }

    public static String dismiss(MinecraftServer server) {
        if (pendingTarget == null) return "There is no Gunslinger shot waiting.";
        clearPending();
        RoleAutomation.announce(server, "Nothing happens.", ChatFormatting.GRAY);
        return null;
    }

    private static void clearPending() {
        pendingTarget = null;
        pendingDay = -1;
        RoleAutomation.resolve(PROMPT);
    }

    // ---------------------------------------------------------------- Item

    private static ItemStack create(String shotId) {
        ItemStack bow = new ItemStack(Items.CROSSBOW);
        bow.set(DataComponents.CUSTOM_NAME, Component.literal("Gunslinger's Crossbow").withStyle(ChatFormatting.DARK_RED));
        bow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
        CompoundTag marker = new CompoundTag();
        marker.putBoolean(MARKER_KEY, true);
        marker.putInt(DAY_KEY, ServerState.currentDay);
        marker.putString(SHOT_KEY, shotId);
        bow.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        return bow;
    }

    public static boolean isGun(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.CROSSBOW)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(MARKER_KEY, false);
    }

    private static String shotId(ItemStack stack) {
        CustomData data = stack == null ? null : stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr(SHOT_KEY, "");
    }

    private static int freeSlot(Inventory inventory) {
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (slot != inventory.getSelectedSlot() && inventory.getItem(slot).isEmpty()) return slot;
        }
        return -1;
    }

    /** Hands out, tracks and takes back the crossbow. */
    public static void serverTick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 5 != 0) return;
        if (windowDay == ServerState.currentDay && DaytimeState.isVoteInProgress()) windowClosed = true;
        long now = server.getTickCount();
        for (Iterator<Shot> it = LIVE.values().iterator(); it.hasNext(); ) {
            Shot shot = it.next();
            if (shot.firedAt() < 0 || now - shot.firedAt() < BOLT_LIFE_TICKS) continue;
            it.remove();
            ServerPlayer shooter = server.getPlayerList().getPlayer(shot.shooter());
            if (shooter != null) missed(shooter, "Your bolt missed.");
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            boolean may = mayHold(id);
            boolean kept = false;
            Inventory inventory = player.getInventory();
            boolean changed = false;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!isGun(stack)) continue;
                boolean charged = CrossbowItem.isCharged(stack);
                int day = stack.get(DataComponents.CUSTOM_DATA).copyTag().getIntOr(DAY_KEY, -1);
                if (may && charged && !kept && day == ServerState.currentDay) {
                    kept = true;
                    continue;
                }
                String shotId = shotId(stack);
                Shot shot = LIVE.get(shotId);
                if (shot != null) {
                    if (!charged && shot.firedAt() < 0) LIVE.put(shotId, new Shot(shot.shooter(), now));
                    else if (charged) LIVE.remove(shotId);
                }
                inventory.setItem(slot, ItemStack.EMPTY);
                changed = true;
            }
            if (isGun(player.containerMenu.getCarried())) {
                if (may && !kept) kept = true;
                else player.containerMenu.setCarried(ItemStack.EMPTY);
            }
            boolean inFlight = false;
            for (Shot shot : LIVE.values()) {
                if (shot.shooter().equals(id) && shot.firedAt() >= 0) inFlight = true;
            }
            if (may && !kept && !inFlight) {
                int slot = freeSlot(inventory);
                if (slot >= 0) {
                    String shotId = UUID.randomUUID().toString();
                    inventory.setItem(slot, create(shotId));
                    LIVE.put(shotId, new Shot(id, -1));
                    changed = true;
                    Integer previous = NOTIFIED_DAY.put(id, ServerState.currentDay);
                    if (previous == null || previous != ServerState.currentDay) {
                        player.sendSystemMessage(Component.literal("The first vote is in. You have the Gunslinger's crossbow"
                                + " until the next vote starts: shoot a player who voted and the Storyteller decides if they die.")
                                .withStyle(ChatFormatting.DARK_RED));
                        RoleAutomation.tellStorytellers(server, Component.literal("Gunslinger: "
                                + RoleAutomation.label(server, id) + " has the crossbow until the next vote starts.")
                                .withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            if (changed) inventory.setChanged();
        }
    }
}
