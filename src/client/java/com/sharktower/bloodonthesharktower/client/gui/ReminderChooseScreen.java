package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** Compact BOTB-style reminder picker/editor for one seated player. */
public final class ReminderChooseScreen extends Screen {
    private record Preset(String text, Role sourceRole) {}

    private static final Preset[] PRESETS = {
            new Preset("Poisoned", Role.POISONER),
            new Preset("Drunk", null),
            new Preset("Red Herring", Role.FORTUNE_TELLER),
            new Preset("Good", null),
            new Preset("Evil", null),
            new Preset("Demon", null),
            new Preset("Minion", null),
            new Preset("Protected", Role.MONK),
            new Preset("Grandchild", Role.GRANDMOTHER)
    };

    private final UUID playerId;
    private final int seat;

    public ReminderChooseScreen(UUID playerId, int seat) {
        super(Component.literal("Reminders — Seat " + seat));
        this.playerId = playerId;
        this.seat = seat;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 52;
        int w = 110;
        int gap = 6;
        for (int i = 0; i < PRESETS.length; i++) {
            Preset preset = PRESETS[i];
            int col = i % 2;
            int row = i / 2;
            this.addRenderableWidget(Button.builder(Component.literal("+ " + preset.text()), b -> {
                        if (ClientGrimoireEdits.isLocalStoryteller()) {
                            GrimoireReturnState.requestAfterNextGrimoireSync();
                            if (preset.sourceRole() != null) {
                                ClientStorytellerActions.send(
                                        "add_role_reminder",
                                        seat + "|" + preset.sourceRole().getId() + "|" + preset.text()
                                );
                            } else {
                                ClientStorytellerActions.send("add_reminder", seat + "|" + preset.text());
                            }
                        } else {
                            ClientGrimoireEdits.addReminder(playerId, preset.text());
                            returnToGrimoire();
                        }
                    })
                    .bounds(cx - w - gap / 2 + col * (w + gap), y + row * 25, w, 20).build());
        }

        List<Reminder> existing = ClientGrimoireEdits.remindersFor(playerId);
        int existingY = y + 137;
        for (int i = 0; i < Math.min(existing.size(), 6); i++) {
            int index = i;
            Reminder reminder = existing.get(i);
            String text = reminder.text();
            String source = reminder.role().map(Role::getDisplayName).orElse("");
            String removeLabel = "Remove: " + (source.isBlank() ? "" : source + " — ") + text;
            this.addRenderableWidget(Button.builder(Component.literal(removeLabel), b -> {
                        if (ClientGrimoireEdits.isLocalStoryteller()) {
                            GrimoireReturnState.requestAfterNextGrimoireSync();
                            ClientStorytellerActions.send("remove_reminder", seat + "|" + index);
                        } else {
                            ClientGrimoireEdits.removeReminder(playerId, index);
                            returnToGrimoire();
                        }
                    })
                    .bounds(cx - 115, existingY + i * 23, 230, 20).build());
        }

        if (ClientGrimoireEdits.isLocalStoryteller()) {
            this.addRenderableWidget(Button.builder(Component.literal("Night Info"), b ->
                            this.minecraft.gui.setScreen(new NightInfoReminderScreen(playerId, seat)))
                    .bounds(cx - 115, this.height - 77, 112, 20).build());
            Button demonKill = Button.builder(Component.literal("Demon Kill"), b ->
                            DemonKillReminderScreen.openOrApply(playerId, seat))
                    .bounds(cx + 3, this.height - 77, 112, 20).build();
            demonKill.active = !DemonKillReminderScreen.demonsInStorytellerGrimoire().isEmpty();
            this.addRenderableWidget(demonKill);
        }

        this.addRenderableWidget(Button.builder(Component.literal("Clear All Reminders"), b -> {
                    if (ClientGrimoireEdits.isLocalStoryteller()) {
                        GrimoireReturnState.requestAfterNextGrimoireSync();
                        ClientStorytellerActions.send("clear_reminders", Integer.toString(seat));
                    } else {
                        ClientGrimoireEdits.clearReminders(playerId);
                        returnToGrimoire();
                    }
                })
                .bounds(cx - 115, this.height - 52, 230, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back to Grimoire"), b -> returnToGrimoire())
                .bounds(cx - 115, this.height - 27, 230, 20).build());
    }

    private void returnToGrimoire() {
        if (this.minecraft == null) return;
        GrimoireReturnState.suppressNextReveal();
        this.minecraft.gui.setScreen(new AssignRolesScreen());
    }

    @Override
    public void onClose() {
        returnToGrimoire();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "Seat " + seat + " — " + ClientState.playerName(playerId, seat);
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 18, UiDrawing.GOLD, true);
        String sub = "Choose a reminder, or remove one already on the Grimoire.";
        graphics.text(this.font, sub, (this.width - this.font.width(sub)) / 2, 32, UiDrawing.MUTED, false);
    }
}
