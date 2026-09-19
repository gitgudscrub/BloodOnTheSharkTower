package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One-use, player-targeted true-Grimoire snapshot for abilities such as Spy and Widow.
 * This packet is intentionally separate from SendGrimoireS2CPayload so it never
 * overwrites the player's personal deduction Grimoire.
 */
public record AbilityGrimoireS2CPayload(
        Map<UUID, PendingRoleAssignment> roles,
        Map<UUID, Integer> seatNumbers,
        Map<UUID, List<Reminder>> reminders,
        String sourceRoleId
) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "ability_grimoire");
    public static final Type<AbilityGrimoireS2CPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityGrimoireS2CPayload> CODEC = new StreamCodec<>() {
        @Override
        public AbilityGrimoireS2CPayload decode(RegistryFriendlyByteBuf buf) {
            String sourceRole = buf.readUtf();
            StateWire.GrimoireDecoded decoded = StateWire.grimoireFromJson(buf.readUtf(), null);
            return new AbilityGrimoireS2CPayload(
                    decoded.roles(),
                    decoded.seats(),
                    decoded.reminders(),
                    sourceRole
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, AbilityGrimoireS2CPayload value) {
            buf.writeUtf(value.sourceRoleId());
            buf.writeUtf(StateWire.grimoireToJson(
                    value.roles(),
                    Map.of(),
                    value.seatNumbers(),
                    value.reminders(),
                    List.of(),
                    true
            ));
        }
    };

    public AbilityGrimoireS2CPayload {
        roles = Map.copyOf(roles);
        seatNumbers = Map.copyOf(seatNumbers);
        reminders = Map.copyOf(reminders);
        sourceRoleId = sourceRoleId == null ? "" : sourceRoleId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
