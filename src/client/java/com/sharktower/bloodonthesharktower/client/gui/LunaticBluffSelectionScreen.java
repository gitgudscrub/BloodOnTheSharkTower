package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.ClientLunaticBluffs;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Separate three-bluff picker used only for an assigned Lunatic. */
public final class LunaticBluffSelectionScreen extends BluffPickerScreen {
    public LunaticBluffSelectionScreen() {
        this(new NightTeamInfoScreen(true));
    }

    public LunaticBluffSelectionScreen(Screen returnScreen) {
        this(firstThree(availableRoles(), ClientLunaticBluffs.current()), 0, returnScreen);
    }

    private LunaticBluffSelectionScreen(Set<String> selected, int page, Screen returnScreen) {
        super(Component.literal("Choose 3 Lunatic Bluffs"), selected, page, returnScreen);
    }

    @Override
    protected List<ScriptRole> roles() { return availableRoles(); }

    @Override
    protected String heading() { return "Choose 3 fake good roles for the Lunatic — in-play roles are allowed"; }

    @Override
    protected Screen with(Set<String> selected, int page) {
        return new LunaticBluffSelectionScreen(selected, page, returnScreen);
    }

    @Override
    protected void confirm() {
        if (selected.size() != 3) return;
        ClientLunaticBluffs.set(new ArrayList<>(selected));
        back();
    }

    @Override
    protected void back() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(returnScreen != null ? returnScreen : new NightTeamInfoScreen(true));
    }

    // Lunatic bluff information may be false in exactly this way: unlike a
    // real Demon's bluff set, an in-play good character is a valid fake bluff.
    private static List<ScriptRole> availableRoles() {
        return goodRoles(Set.of());
    }
}
