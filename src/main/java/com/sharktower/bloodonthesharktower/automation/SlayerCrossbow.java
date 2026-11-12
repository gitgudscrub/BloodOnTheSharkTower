package com.sharktower.bloodonthesharktower.automation;

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
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Slayer's crossbow: {@code /slay} hands a living seated player a
 * crossbow already loaded with one bolt. The bolt does no damage. Whoever it
 * hits becomes the target of {@link RoleAutomation#slayerShot}, which
 * announces the shot and asks the Storyteller to confirm the kill or rule
 * that nothing happens. A player may keep trying until a bolt hits a living
 * seated player; that hit is their one Slayer shot for the game. Misses,
 * expired bolts and hits on someone who cannot be targeted are free.
 *
 * The crossbow is taken away once it has fired, at the end of the day, and
 * when its holder dies or leaves the game, so it can never be reloaded or
 * kept for later. Each crossbow carries a shot id; a bolt only counts while
 * its shot is live, and a shot that has not hit anything within
 * {@link #BOLT_LIFE_TICKS} of firing is a miss. That also covers a bolt that
 * flies out of the loaded area and freezes: it can never land later.
 */
public final class SlayerCrossbow {
    private static final String MARKER_KEY = "blood_on_the_sharktower_slayer_crossbow";
    private static final String DAY_KEY = "blood_on_the_sharktower_slayer_day";
    private static final String SHOT_KEY = "blood_on_the_sharktower_slayer_shot";
    private static final int BOLT_LIFE_TICKS = 80;

    /** A handed-out crossbow; {@code firedAt} is the server tick it fired, or -1. */
    private record Shot(UUID shooter, long firedAt) {}

    private static final Map<String, Shot> LIVE = new HashMap<>();
    private static int trackedDay = Integer.MIN_VALUE;

    private SlayerCrossbow() {}

    /** Gives {@code player} a loaded Slayer's crossbow; returns why not, or null. */
    public static String give(MinecraftServer server, ServerPlayer player) {
        String refusal = RoleAutomation.slayerShooterRefusal(player.getUUID());
        if (refusal != null) return refusal;
        if (holds(player)) return "You already have the Slayer's crossbow. Aim at a player and fire.";
        for (Shot live : LIVE.values()) {
            if (live.shooter().equals(player.getUUID())) return "Your bolt is still in the air.";
        }
        String shotId = UUID.randomUUID().toString();
        if (!player.getInventory().add(create(shotId))) return "Make room in your inventory for the crossbow.";
        LIVE.put(shotId, new Shot(player.getUUID(), -1));
        RoleAutomation.announce(server, RoleAutomation.label(server, player.getUUID())
                + " claims Slayer!", ChatFormatting.GOLD);
        player.sendSystemMessage(Component.literal(
                "One bolt. Aim at a player and fire: whoever it hits is your target, and that is your one Slayer shot this game. The Storyteller decides what happens.")
                .withStyle(ChatFormatting.GRAY));
        return null;
    }

    private static ItemStack create(String shotId) {
        ItemStack bow = new ItemStack(Items.CROSSBOW);
        bow.set(DataComponents.CUSTOM_NAME, Component.literal("Slayer's Crossbow").withStyle(ChatFormatting.GOLD));
        bow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
        CompoundTag marker = new CompoundTag();
        marker.putBoolean(MARKER_KEY, true);
        marker.putInt(DAY_KEY, ServerState.currentDay);
        marker.putString(SHOT_KEY, shotId);
        bow.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        return bow;
    }

    private static String shotId(ItemStack stack) {
        CustomData data = stack == null ? null : stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr(SHOT_KEY, "");
    }

    public static boolean isSlayerCrossbow(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.CROSSBOW)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(MARKER_KEY, false);
    }

    /** True for an arrow fired from a Slayer's crossbow. */
    public static boolean isSlayerBolt(AbstractArrow arrow) {
        return isSlayerCrossbow(arrow.getWeaponItem());
    }

    /**
     * A Slayer bolt struck {@code hit} (null when it hit a block). The bolt
     * is removed without doing damage; a seated player becomes the target.
     */
    public static void boltHit(AbstractArrow arrow, Entity hit) {
        if (!(arrow.level() instanceof ServerLevel level)) return;
        arrow.discard();
        // A bolt whose shot already landed, missed or expired does nothing.
        Shot shot = LIVE.remove(shotId(arrow.getWeaponItem()));
        if (shot == null) return;
        if (!(arrow.getOwner() instanceof ServerPlayer shooter) || !shooter.getUUID().equals(shot.shooter())) return;
        MinecraftServer server = level.getServer();
        if (hit instanceof ServerPlayer target && target != shooter) {
            String refusal = RoleAutomation.slayerShot(server, shooter.getUUID(), target.getUUID(), false);
            if (refusal == null) {
                level.playSound(null, target.blockPosition(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0F, 1.0F);
                return;
            }
            shooter.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
            offerRetry(shooter);
        } else {
            missed(shooter);
        }
    }

    private static void missed(ServerPlayer shooter) {
        shooter.sendSystemMessage(Component.literal("Your bolt missed. Nothing happens.").withStyle(ChatFormatting.GRAY));
        offerRetry(shooter);
    }

    private static void offerRetry(ServerPlayer shooter) {
        if (RoleAutomation.slayerShooterRefusal(shooter.getUUID()) == null) {
            shooter.sendSystemMessage(Component.literal("Use /slay to try again.").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void serverTick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 5 != 0) return;
        long now = server.getTickCount();
        if (trackedDay != ServerState.currentDay || ServerState.gameEnded) {
            trackedDay = ServerState.currentDay;
            LIVE.clear();
        }
        for (Iterator<Shot> it = LIVE.values().iterator(); it.hasNext(); ) {
            Shot shot = it.next();
            if (shot.firedAt() < 0 || now - shot.firedAt() < BOLT_LIFE_TICKS) continue;
            it.remove();
            ServerPlayer shooter = server.getPlayerList().getPlayer(shot.shooter());
            if (shooter != null) missed(shooter);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean mayHold = RoleAutomation.slayerShooterRefusal(player.getUUID()) == null;
            var inventory = player.getInventory();
            boolean changed = false;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!isSlayerCrossbow(stack)) continue;
                int day = stack.get(DataComponents.CUSTOM_DATA).copyTag().getIntOr(DAY_KEY, -1);
                boolean charged = CrossbowItem.isCharged(stack);
                if (mayHold && charged && day == ServerState.currentDay) continue;
                String id = shotId(stack);
                Shot shot = LIVE.get(id);
                if (shot != null) {
                    // Fired: the bolt has BOLT_LIFE_TICKS to hit. Taken away unfired: the shot is void.
                    if (!charged && shot.firedAt() < 0) LIVE.put(id, new Shot(shot.shooter(), now));
                    else if (charged) LIVE.remove(id);
                }
                inventory.setItem(slot, ItemStack.EMPTY);
                changed = true;
            }
            if (changed) inventory.setChanged();
        }
    }

    private static boolean holds(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isSlayerCrossbow(inventory.getItem(slot))) return true;
        }
        return false;
    }
}
