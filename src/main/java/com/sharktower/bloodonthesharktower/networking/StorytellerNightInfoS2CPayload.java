package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Storyteller-only daily facts used by the night-visit helper HUD. */
public record StorytellerNightInfoS2CPayload(
        String lastExecutedRoleName,
        boolean demonVotedToday,
        boolean minionNominatedToday
) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "storyteller_night_info"
    );

    public static final Type<StorytellerNightInfoS2CPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, StorytellerNightInfoS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            StorytellerNightInfoS2CPayload::lastExecutedRoleName,
            ByteBufCodecs.BOOL,
            StorytellerNightInfoS2CPayload::demonVotedToday,
            ByteBufCodecs.BOOL,
            StorytellerNightInfoS2CPayload::minionNominatedToday,
            StorytellerNightInfoS2CPayload::new
    );

    public StorytellerNightInfoS2CPayload {
        lastExecutedRoleName = lastExecutedRoleName == null ? "" : lastExecutedRoleName;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
