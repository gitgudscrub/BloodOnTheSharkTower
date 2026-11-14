package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.ReminderCatalog;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
        // Three columns when the screen is wide enough, and rows 22px apart, so
        // a whole script's reminders usually fit on one page.
        int columns = this.width >= 3 * 140 + 30 ? 3 : 2;
        int rows = Math.max(1, (this.height - 78 - 58) / 22);
        int perPage = rows * columns;
        int w = Math.min(210, Math.max(100, (this.width - 30 - (columns - 1) * 6) / columns));
        int left = cx - (columns * w + (columns - 1) * 6) / 2;
        List<ReminderCatalog.Option> choices = tab == 1
                ? ReminderCatalog.forScript(ClientState.currentScript, ClientGrimoireEdits.roleFor(playerId), rolesInPlay())
                : ReminderCatalog.generic();
        List<Reminder> existing = ClientGrimoireEdits.remindersFor(playerId);
        int count = tab == 2 ? existing.size() : choices.size();
        pages = Math.max(1, (count + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        for (int i = page * perPage; i < Math.min(count, (page + 1) * perPage); i++) {
            int index = i, slot = i % perPage;
            int x = left + (slot % columns) * (w + 6), y = 78 + (slot / columns) * 22;
            if (tab == 2) {
                Reminder reminder = existing.get(i);
                String source = reminder.role().map(Role::getDisplayName).orElseGet(() ->
                        reminder.customRoleId().orElse(""));
                String label = "Remove: " + (source.isBlank() ? "" : source + ": ") + reminder.text();
                this.addRenderableWidget(SharktowerButton.builder(Component.literal(label), b -> {
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
                this.addRenderableWidget(SharktowerButton.builder(Component.literal("+ " + option.label()), b -> {
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
        // Two rows of controls: paging and the Storyteller's shortcuts, then Clear and Back.
        int rowW = storyteller ? 360 : 168;
        int x0 = cx - rowW / 2, y1 = this.height - 50, y2 = this.height - 26;
        SharktowerButton previous = SharktowerButton.builder(Component.literal("Previous"), b -> { page--; this.rebuildWidgets(); })
                .bounds(x0, y1, 60, 20).build();
        previous.active = page > 0; this.addRenderableWidget(previous);
        pageTextX = x0 + 84;
        pageTextY = y1 + 6;
        SharktowerButton next = SharktowerButton.builder(Component.literal("Next"), b -> { page++; this.rebuildWidgets(); })
                .bounds(x0 + 108, y1, 60, 20).build();
        next.active = page + 1 < pages; this.addRenderableWidget(next);
        if (storyteller) {
            this.addRenderableWidget(SharktowerButton.builder(Component.literal("Night Info"), b ->
                    this.minecraft.gui.setScreen(new NightInfoReminderScreen(playerId, seat)))
                    .bounds(x0 + 176, y1, 90, 20).build());
            SharktowerButton demonKill = SharktowerButton.builder(Component.literal("Demon Kill"), b -> DemonKillReminderScreen.openOrApply(playerId, seat))
                    .bounds(x0 + 270, y1, 90, 20).build();
            demonKill.active = !DemonKillReminderScreen.demonsInStorytellerGrimoire().isEmpty();
            this.addRenderableWidget(demonKill);
        }
        int halfW = 176;
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Clear All Reminders"), b -> {
            if (storyteller) {
                GrimoireReturnState.requestAfterNextGrimoireSync();
                ClientStorytellerActions.send("clear_reminders", Integer.toString(seat));
            } else {
                ClientGrimoireEdits.clearReminders(playerId); returnToGrimoire();
            }
        }).bounds(cx - halfW - 4, y2, halfW, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Back to Grimoire"), b -> returnToGrimoire())
                .bounds(cx + 4, y2, halfW, 20).build());
    }
    private int pageTextX, pageTextY;

    /** Role ids of every seated player, so their reminders come before the rest of the script. */
    private static java.util.Set<String> rolesInPlay() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (UUID seated : ClientState.playerSeatNumbers.keySet()) {
            var role = ClientGrimoireEdits.roleFor(seated);
            if (role != null && role.getRoleId() != null) ids.add(role.getRoleId());
        }
        return ids;
    }

    private void addTab(int x, String text, int selected, int width) {
        SharktowerButton button = SharktowerButton.builder(Component.literal(text), b -> { tab = selected; page = 0; this.rebuildWidgets(); })
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
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 18, UiDrawing.GOLD, UiTheme.dark());
        String sub = tab == 1 ? "This player's character first, then characters in play, then the rest" : tab == 2 ? "Remove placed reminders" : "Generic reminders";
        graphics.text(this.font, sub, (this.width - this.font.width(sub)) / 2, 32, UiDrawing.MUTED, false);
        String number = (page + 1) + "/" + pages;
        graphics.text(this.font, number, pageTextX - this.font.width(number) / 2, pageTextY, UiDrawing.TEXT, UiTheme.dark());
    }
}
