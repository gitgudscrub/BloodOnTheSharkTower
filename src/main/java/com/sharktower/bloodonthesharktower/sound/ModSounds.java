package com.sharktower.bloodonthesharktower.sound;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Sound events used by the physical town-square vote presentation. */
public final class ModSounds {
    public static final SoundEvent DUSK = register("dusk");
    public static final SoundEvent DAWN = register("dawn");
    public static final SoundEvent CALL_BACK = register("call_back");
    public static final SoundEvent TIMER_GONG = register("timer_gong");
    public static final SoundEvent NOMINATION = register("nomination");
    public static final SoundEvent VOTE_START = register("vote_start");
    public static final SoundEvent CLOCK_TICKING = register("clock_ticking"); // legacy asset
    public static final SoundEvent CLOCK_TICK = register("clock_tick");
    public static final SoundEvent EXECUTION = register("execution");
    public static final SoundEvent EXECUTION_SURVIVED = register("execution_survived");
    public static final SoundEvent GAME_END = register("game_end");

    private ModSounds() {}

    private static SoundEvent register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID, path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /**
     * Plays a phase/UI sound directly to every connected player.
     *
     * ServerPlayer's old notify-sound helpers were removed in Minecraft 26.x, so send the
     * vanilla sound packet directly. Using each player's own coordinates makes the cue local
     * to them while avoiding distance-based omissions for players who are away at their house.
     */
    public static void playForAll(MinecraftServer server, SoundEvent sound) {
        if (server == null || sound == null) return;
        var holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSoundPacket(
                    holder,
                    SoundSource.MASTER,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    1.0F,
                    1.0F,
                    0L
            ));
        }
    }

    /** Plays the original BOTB game-end sting once at the town square. */
    public static void playGameEnd(MinecraftServer server) {
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) return;
        ServerPlayer anchor = server.getPlayerList().getPlayers().getFirst();
        SeatPositionManager.Position center = SeatPositionManager.clockCenter();
        double x = center == null ? anchor.getX() : center.x();
        double y = center == null ? anchor.getY() : center.y();
        double z = center == null ? anchor.getZ() : center.z();
        anchor.level().playSound(null, x, y, z, GAME_END, SoundSource.MASTER, 1.0F, 1.0F);
    }

    /** Forces class initialization from the common mod initializer. */
    public static void initialize() {}
}
