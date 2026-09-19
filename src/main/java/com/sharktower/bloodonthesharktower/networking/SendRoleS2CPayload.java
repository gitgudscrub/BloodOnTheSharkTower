package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.core.AlignmentOverride;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Role;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 26.2 port of BOTB's SendRoleS2CPayload.
 *
 * The packet keeps the original data model: one PendingRoleAssignment plus
 * active/traveler counts and the silent flag used later by the role HUD/audio.
 */
public record SendRoleS2CPayload(
        PendingRoleAssignment assignment,
        int activePlayerCount,
        int travelerCount,
        boolean silent
) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "send_role"
    );

    public static final Type<SendRoleS2CPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, SendRoleS2CPayload> CODEC = StreamCodec.composite(
            PendingRoleAssignment.PACKET_CODEC,
            SendRoleS2CPayload::assignment,
            ByteBufCodecs.VAR_INT,
            SendRoleS2CPayload::activePlayerCount,
            ByteBufCodecs.VAR_INT,
            SendRoleS2CPayload::travelerCount,
            ByteBufCodecs.BOOL,
            SendRoleS2CPayload::silent,
            SendRoleS2CPayload::new
    );

    public Role role() {
        return assignment.role();
    }

    public static SendRoleS2CPayload ofRole(
            Role role,
            boolean good,
            int activePlayerCount,
            int travelerCount,
            boolean silent
    ) {
        return new SendRoleS2CPayload(
                new PendingRoleAssignment(
                        role,
                        good ? AlignmentOverride.FORCE_GOOD : AlignmentOverride.FORCE_BAD
                ),
                activePlayerCount,
                travelerCount,
                silent
        );
    }

    public static SendRoleS2CPayload ofAssignment(
            PendingRoleAssignment assignment,
            int activePlayerCount,
            int travelerCount,
            boolean silent
    ) {
        return new SendRoleS2CPayload(assignment, activePlayerCount, travelerCount, silent);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
