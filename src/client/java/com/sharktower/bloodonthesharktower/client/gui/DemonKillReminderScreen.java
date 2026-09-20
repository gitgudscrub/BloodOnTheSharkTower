package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adds a demon-sourced "Kill" reminder to the selected player.
 *
 * The buttons are generated from Demon roles actually present in the
 * Storyteller's current Grimoire. This avoids offering out-of-play Demons merely
 * because they exist on the script.
 */
public final class DemonKillReminderScreen extends Screen {
    private final UUID playerId;
    private final int seat;

    public DemonKillReminderScreen(UUID playerId, int seat) {
        super(Component.literal("Demon Kill Marker — Seat " + seat));
        this.playerId = playerId;
        this.seat = seat;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int buttonW = 150;
        int gap = 8;
        int y = 62;

        List<ScriptRole> demons = demonsInStorytellerGrimoire();
        for (int i = 0; i < demons.size(); i++) {
            ScriptRole demon = demons.get(i);
            int col = i % 2;
            int row = i / 2;
            int x = cx - buttonW - gap / 2 + col * (buttonW + gap);

            this.addRenderableWidget(Button.builder(
                            Component.literal("+ " + demon.getDisplayName() + ": Kill"),
                            b -> {
                                ClientStorytellerActions.send(
                                        "add_role_reminder",
                                        seat + "|" + demon.getId() + "|Kill"
                                );
                                this.minecraft.gui.setScreen(new ReminderChooseScreen(playerId, seat));
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

        String title = "Demon Kill Marker — " + ClientState.playerName(playerId, seat);
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 18, UiDrawing.GOLD, true);

        String sub = "Choose which Demon selected this player. The reminder uses that Demon's token.";
        graphics.text(this.font, sub, (this.width - this.font.width(sub)) / 2, 34, UiDrawing.MUTED, false);

        if (demonsInStorytellerGrimoire().isEmpty()) {
            String none = "No Demon role is currently present in the Storyteller's Grimoire.";
            graphics.text(this.font, none, (this.width - this.font.width(none)) / 2, 70, UiDrawing.MUTED, false);
        }
    }

    public static List<ScriptRole> demonsInStorytellerGrimoire() {
        Map<String, ScriptRole> unique = new LinkedHashMap<>();

        for (PendingRoleAssignment assignment : ClientState.grimoireRoles.values()) {
            if (assignment == null || assignment.getRoleType() != RoleType.DEMON) continue;

            ScriptRole role = assignment.getScriptRole();
            if (role == null) continue;

            unique.putIfAbsent(role.getId().toLowerCase(java.util.Locale.ROOT), role);
        }

        List<ScriptRole> demons = new ArrayList<>(unique.values());
        demons.sort(Comparator.comparing(ScriptRole::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return demons;
    }
}
