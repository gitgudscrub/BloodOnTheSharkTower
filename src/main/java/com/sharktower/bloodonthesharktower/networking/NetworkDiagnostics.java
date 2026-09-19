package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Diagnostic layer retained while the original BOTB packet families are bulk-ported. */
public final class NetworkDiagnostics {
    private static final AtomicInteger NEXT_SEQUENCE = new AtomicInteger();
    private static final Map<UUID, Ack> ACKS = new ConcurrentHashMap<>();
    private static final Map<UUID, RoleAck> ROLE_ACKS = new ConcurrentHashMap<>();

    private NetworkDiagnostics() {}

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(NetworkSyncAckC2SPayload.TYPE, (payload, context) -> {
            UUID playerId = context.player().getUUID();
            Ack ack = new Ack(
                    playerId,
                    payload.sequence(),
                    payload.clientPhase(),
                    normalizeScript(payload.scriptName()),
                    payload.scriptRoleCount(),
                    payload.seatCount(),
                    payload.deadCount(),
                    payload.grimoirePlayerCount()
            );
            ACKS.put(playerId, ack);
            BloodOnTheSharktower.LOGGER.info(
                    "Core sync ack {} from {}: phase={}, script={}, roles={}, seats={}, dead={}, grimoire={}",
                    payload.sequence(),
                    playerId,
                    payload.clientPhase(),
                    ack.scriptName(),
                    ack.scriptRoleCount(),
                    ack.seatCount(),
                    ack.deadCount(),
                    ack.grimoirePlayerCount()
            );
        });

        ServerPlayNetworking.registerGlobalReceiver(RoleSyncAckC2SPayload.TYPE, (payload, context) -> {
            UUID playerId = context.player().getUUID();
            String roleId = normalizeRole(payload.roleId());
            RoleAck ack = new RoleAck(playerId, roleId, payload.good());
            ROLE_ACKS.put(playerId, ack);
            BloodOnTheSharktower.LOGGER.info(
                    "Role sync acknowledgement from {}: role={}, alignment={}",
                    playerId,
                    roleId,
                    payload.good() ? "good" : "evil"
            );
        });
    }

    public static int sendProbe(ServerPlayer player) {
        int sequence = NEXT_SEQUENCE.incrementAndGet();
        ServerPlayNetworking.send(player, new NetworkSyncProbeS2CPayload(sequence));
        return sequence;
    }

    public static int ackedClientCount() {
        return ACKS.size();
    }

    public static Optional<Ack> latestAck() {
        return ACKS.values().stream().max(Comparator.comparingInt(Ack::sequence));
    }

    public static Optional<RoleAck> roleAck(UUID playerId) {
        return Optional.ofNullable(ROLE_ACKS.get(playerId));
    }

    private static String normalizeScript(String scriptName) {
        return scriptName == null || scriptName.isBlank() ? "none" : scriptName;
    }

    private static String normalizeRole(String roleId) {
        return roleId == null || roleId.isBlank() || roleId.equalsIgnoreCase("norole")
                ? "none"
                : roleId;
    }

    public record Ack(
            UUID playerId,
            int sequence,
            String clientPhase,
            String scriptName,
            int scriptRoleCount,
            int seatCount,
            int deadCount,
            int grimoirePlayerCount
    ) {}

    public record RoleAck(UUID playerId, String roleId, boolean good) {}
}
