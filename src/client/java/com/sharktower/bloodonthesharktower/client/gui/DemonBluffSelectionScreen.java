package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** The Storyteller's Demon bluff picker: three good roles that are not in play. */
public final class DemonBluffSelectionScreen extends BluffPickerScreen {
    public DemonBluffSelectionScreen() {
        this(null);
    }

    public DemonBluffSelectionScreen(Screen returnScreen) {
        this(firstThree(availableRoles(), ClientState.demonBluffs), 0, returnScreen);
    }

    private DemonBluffSelectionScreen(Set<String> selected, int page, Screen returnScreen) {
        super(Component.literal("Choose 3 Demon Bluffs"), selected, page, returnScreen);
    }

    @Override
    protected List<ScriptRole> roles() { return availableRoles(); }

    @Override
    protected String heading() { return "Choose 3 out-of-play good roles"; }

    @Override
    protected Screen with(Set<String> selected, int page) {
        return new DemonBluffSelectionScreen(selected, page, returnScreen);
    }

    @Override
    protected void confirm() {
        if (selected.size() != 3) return;

        if (returnScreen == null) {
            GrimoireReturnState.requestAfterNextGrimoireSync();
        }
        ClientStorytellerActions.send("set_bluffs", String.join("|", selected));
        if (returnScreen != null) {
            this.minecraft.gui.setScreen(returnScreen);
        }
    }

    @Override
    protected void back() {
        if (this.minecraft == null) return;
        if (returnScreen != null) {
            this.minecraft.gui.setScreen(returnScreen);
        } else {
            GrimoireReturnState.suppressNextReveal();
            this.minecraft.gui.setScreen(new AssignRolesScreen());
        }
    }

    /** Good roles minus every role (true or believed) already in the Grimoire. */
    private static List<ScriptRole> availableRoles() {
        Set<String> inPlay = new HashSet<>();
        Stream.concat(ClientState.grimoireRoles.values().stream(), ClientState.grimoirePerceivedRoles.values().stream())
                .filter(Objects::nonNull)
                .map(PendingRoleAssignment::getRoleId)
                .filter(Objects::nonNull)
                .map(BluffPickerScreen::key)
                .forEach(inPlay::add);
        return goodRoles(inPlay);
    }
}
