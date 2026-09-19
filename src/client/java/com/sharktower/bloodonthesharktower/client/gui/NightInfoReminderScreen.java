package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Original-style source-role reminder markers used by the Storyteller's
 * automatic first-night information popup.
 */
public final class NightInfoReminderScreen extends Screen {
    private record Marker(Role role, String text) {}

    private static final List<Marker> MARKERS = List.of(
            new Marker(Role.WASHERWOMAN, "Townsfolk"),
            new Marker(Role.WASHERWOMAN, "Wrong"),
            new Marker(Role.LIBRARIAN, "Outsider"),
            new Marker(Role.LIBRARIAN, "Wrong"),
            new Marker(Role.INVESTIGATOR, "Minion"),
            new Marker(Role.INVESTIGATOR, "Wrong"),
            new Marker(Role.STEWARD, "Know"),
            new Marker(Role.KNIGHT, "Know"),
            new Marker(Role.NOBLE, "Know")
    );

    private final UUID playerId;
    private final int seat;

    public NightInfoReminderScreen(UUID playerId, int seat) {
        super(Component.literal("Night Info Markers — Seat " + seat));
        this.playerId = playerId;
        this.seat = seat;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int buttonW = 150;
        int gap = 8;
        int y = 62;

        List<Marker> visible = new ArrayList<>();
        for (Marker marker : MARKERS) {
            if (roleInPlay(marker.role())) visible.add(marker);
        }

        for (int i = 0; i < visible.size(); i++) {
            Marker marker = visible.get(i);
            int col = i % 2;
            int row = i / 2;
            int x = cx - buttonW - gap / 2 + col * (buttonW + gap);
            this.addRenderableWidget(Button.builder(
                            Component.literal("+ " + marker.role().getDisplayName() + ": " + marker.text()),
                            b -> {
                                ClientStorytellerActions.send(
                                        "add_role_reminder",
                                        seat + "|" + marker.role().getId() + "|" + marker.text()
                                );
                                this.minecraft.gui.setScreen(new NightInfoReminderScreen(playerId, seat));
                            })
                    .bounds(x, y + row * 25, buttonW, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Back to Reminders"), b ->
                        this.minecraft.gui.setScreen(new ReminderChooseScreen(playerId, seat)))
                .bounds(cx - 75, this.height - 30, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "Night Info Markers — " + ClientState.playerName(playerId, seat);
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 18, UiDrawing.GOLD, true);
        String sub = "Markers are tied to their source role, matching the original mod.";
        graphics.text(this.font, sub, (this.width - this.font.width(sub)) / 2, 34, UiDrawing.MUTED, false);

        boolean any = false;
        for (Marker marker : MARKERS) {
            if (roleInPlay(marker.role())) {
                any = true;
                break;
            }
        }
        if (!any) {
            String none = "No supported first-night information roles are currently in play.";
            graphics.text(this.font, none, (this.width - this.font.width(none)) / 2, 70, UiDrawing.MUTED, false);
        }
    }

    private static boolean roleInPlay(Role role) {
        for (PendingRoleAssignment assignment : ClientState.grimoireRoles.values()) {
            if (assignment != null && !assignment.isCustomRole() && assignment.role() == role) return true;
        }
        return false;
    }
}
