package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.ReminderCatalog;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.UUID;

/** Generic, script-specific and placed reminders, with pages that fit the screen. */
public final class ReminderChooseScreen extends Screen {
    private final UUID playerId;
    private final int seat;
    private int tab;
    private int page;
    private int pages = 1;
    public ReminderChooseScreen(UUID playerId, int seat) {
        super(Component.literal("Reminders — Seat " + seat));
        this.playerId = playerId;
        this.seat = seat;
    }
    @Override protected void init() {
        int cx = this.width / 2;
        boolean storyteller = ClientGrimoireEdits.isLocalStoryteller();
        int tabWidth = 100;
        addTab(cx - 154, "Generic", 0, tabWidth);
        if (storyteller) addTab(cx - 50, "Script", 1, tabWidth);
        addTab(storyteller ? cx + 54 : cx - 50, "Placed", 2, tabWidth);
        int rows = Math.max(1, (this.height - 185) / 24);
        int perPage = rows * 2;
        int w = Math.min(210, Math.max(100, (this.width - 30) / 2));
        List<ReminderCatalog.Option> choices = tab == 1
                ? ReminderCatalog.forScript(ClientState.currentScript, ClientGrimoireEdits.roleFor(playerId))
                : ReminderCatalog.generic();
        List<Reminder> existing = ClientGrimoireEdits.remindersFor(playerId);
        int count = tab == 2 ? existing.size() : choices.size();
        pages = Math.max(1, (count + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        for (int i = page * perPage; i < Math.min(count, (page + 1) * perPage); i++) {
            int index = i, slot = i % perPage;
            int x = cx - w - 3 + (slot % 2) * (w + 6), y = 78 + (slot / 2) * 24;
            if (tab == 2) {
                Reminder reminder = existing.get(i);
                String source = reminder.role().map(Role::getDisplayName).orElseGet(() ->
                        reminder.customRoleId().orElse(""));
                String label = "Remove: " + (source.isBlank() ? "" : source + ": ") + reminder.text();
                this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    if (storyteller) {
                        GrimoireReturnState.requestAfterNextGrimoireSync();
                        ClientStorytellerActions.send("remove_reminder", seat + "|" + index);
                    } else {
                        ClientGrimoireEdits.removeReminder(playerId, index);
                        returnToGrimoire();
                    }
                }).bounds(x, y, w, 20).build());
            } else {
                ReminderCatalog.Option option = choices.get(i);
                this.addRenderableWidget(Button.builder(Component.literal("+ " + option.label()), b -> {
                    if (storyteller) {
                        GrimoireReturnState.requestAfterNextGrimoireSync();
                        ClientStorytellerActions.send(option.sourceId().isBlank() ? "add_reminder" : "add_role_reminder",
                                seat + "|" + (option.sourceId().isBlank() ? "" : option.sourceId() + "|") + option.text());
                    } else {
                        ClientGrimoireEdits.addReminder(playerId, option.text());
                        returnToGrimoire();
                    }
                }).bounds(x, y, w, 20).build());
            }
        }
        Button previous = Button.builder(Component.literal("Previous"), b -> { page--; this.rebuildWidgets(); })
                .bounds(cx - 115, this.height - 103, 90, 20).build();
        previous.active = page > 0; this.addRenderableWidget(previous);
        Button next = Button.builder(Component.literal("Next"), b -> { page++; this.rebuildWidgets(); })
                .bounds(cx + 25, this.height - 103, 90, 20).build();
        next.active = page + 1 < pages; this.addRenderableWidget(next);
        if (storyteller) {
            this.addRenderableWidget(Button.builder(Component.literal("Night Info"), b ->
                    this.minecraft.gui.setScreen(new NightInfoReminderScreen(playerId, seat)))
                    .bounds(cx - 115, this.height - 77, 112, 20).build());
            Button demonKill = Button.builder(Component.literal("Demon Kill"), b -> DemonKillReminderScreen.openOrApply(playerId, seat))
                    .bounds(cx + 3, this.height - 77, 112, 20).build();
            demonKill.active = !DemonKillReminderScreen.demonsInStorytellerGrimoire().isEmpty();
            this.addRenderableWidget(demonKill);
        }
        this.addRenderableWidget(Button.builder(Component.literal("Clear All Reminders"), b -> {
            if (storyteller) {
                GrimoireReturnState.requestAfterNextGrimoireSync();
                ClientStorytellerActions.send("clear_reminders", Integer.toString(seat));
            } else {
                ClientGrimoireEdits.clearReminders(playerId); returnToGrimoire();
            }
        }).bounds(cx - 115, this.height - 52, 230, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back to Grimoire"), b -> returnToGrimoire())
                .bounds(cx - 115, this.height - 27, 230, 20).build());
    }
    private void addTab(int x, String text, int selected, int width) {
        Button button = Button.builder(Component.literal(text), b -> { tab = selected; page = 0; this.rebuildWidgets(); })
                .bounds(x, 48, width, 20).build();
        button.active = tab != selected; this.addRenderableWidget(button);
    }
    private void returnToGrimoire() {
        if (this.minecraft == null) return;
        GrimoireReturnState.suppressNextReveal(); this.minecraft.gui.setScreen(new AssignRolesScreen());
    }
    @Override public void onClose() { returnToGrimoire(); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "Seat " + seat + " — " + ClientState.playerName(playerId, seat);
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 18, UiDrawing.GOLD, true);
        String sub = tab == 1 ? "Script reminders for this player" : tab == 2 ? "Remove placed reminders" : "Generic reminders";
        graphics.text(this.font, sub, (this.width - this.font.width(sub)) / 2, 32, UiDrawing.MUTED, false);
        String number = (page + 1) + "/" + pages;
        graphics.text(this.font, number, (this.width - this.font.width(number)) / 2, this.height - 97, UiDrawing.TEXT, true);
    }
}
