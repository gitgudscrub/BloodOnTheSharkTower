package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 26.2 port of BOTB SendGrimoireS2CPayload's data shape.
 *
 * Death status remains its own packet just like the original mod.
 */
public record SendGrimoireS2CPayload(
        Map<UUID, PendingRoleAssignment> roles,
        Map<UUID, PendingRoleAssignment> perceivedRoles,
        Map<UUID, Integer> seatNumbers,
        Map<UUID, List<Reminder>> reminders,
        List<String> demonBluffs,
        boolean isTargetedSend
) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "send_grimoire"
    );
    public static final Type<SendGrimoireS2CPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, SendGrimoireS2CPayload> CODEC = new StreamCodec<>() {
        @Override
        public SendGrimoireS2CPayload decode(RegistryFriendlyByteBuf buf) {
            return SendGrimoireS2CPayload.fromJson(buf.readUtf());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SendGrimoireS2CPayload value) {
            buf.writeUtf(StateWire.grimoireToJson(
                    value.roles(),
                    value.perceivedRoles(),
                    value.seatNumbers(),
                    value.reminders(),
                    value.demonBluffs(),
                    value.isTargetedSend()
            ));
        }
    };

    public SendGrimoireS2CPayload {
        roles = Map.copyOf(roles);
        perceivedRoles = Map.copyOf(perceivedRoles);
        seatNumbers = Map.copyOf(seatNumbers);
        reminders = Map.copyOf(reminders);
        demonBluffs = List.copyOf(demonBluffs);
    }

    public static SendGrimoireS2CPayload fromStoryteller(
            Map<UUID, PendingRoleAssignment> roles,
            Map<UUID, PendingRoleAssignment> perceivedRoles,
            Map<UUID, Integer> seatNumbers,
            Map<UUID, List<Reminder>> reminders,
            List<ScriptRole> demonBluffs,
            boolean targeted
    ) {
        List<String> ids = demonBluffs.stream().map(SendGrimoireS2CPayload::roleId).toList();
        return new SendGrimoireS2CPayload(roles, perceivedRoles, seatNumbers, reminders, ids, targeted);
    }

    private static SendGrimoireS2CPayload fromJson(String json) {
        StateWire.GrimoireDecoded decoded = StateWire.grimoireFromJson(json, null);
        return new SendGrimoireS2CPayload(
                decoded.roles(),
                decoded.perceivedRoles(),
                decoded.seats(),
                decoded.reminders(),
                decoded.demonBluffs(),
                decoded.targeted()
        );
    }

    private static String roleId(ScriptRole role) {
        if (role instanceof ScriptRole.Official official) return official.role().getId();
        if (role instanceof ScriptRole.Custom custom) return custom.customRole().id();
        if (role instanceof ScriptRole.Fabled fabled) return fabled.fabledCharacter().id();
        return "none";
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
