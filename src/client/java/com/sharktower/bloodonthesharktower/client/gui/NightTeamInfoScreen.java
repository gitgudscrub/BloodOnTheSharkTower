package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.states.ClientState;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class NightTeamInfoScreen extends Screen {
    private final boolean demon;
    private boolean useMagician = true;
    private boolean releasePoppyGrower;
    private boolean smallGameOverride;
    private int page;

    public NightTeamInfoScreen(boolean demon) {
        super(Component.literal(demon ? "Demon Info" : "Minion Info"));
        this.demon = demon;
    }

    protected void init() {
        if (this.minecraft.player == null || !ClientState.storytellerPlayers.contains(this.minecraft.player.getUUID())) return;

        this.addRenderableWidget(Button.builder(Component.literal(demon ? "Demon Info — Preview" : "Minion Info — Preview"), b ->
                ClientStorytellerActions.send("team_info_preview", (demon ? "demon" : "minion") + "|" + useMagician + "|" + releasePoppyGrower + "|" + smallGameOverride))
                .bounds(this.width / 2 - 110, 48, 220, 20).build());

        int controlY = 72;
        boolean magicianInPlay = roleInPlay(Role.MAGICIAN);
        boolean poppyGrowerInPlay = roleInPlay(Role.POPPY_GROWER);

        if (magicianInPlay) {
            this.addRenderableWidget(Button.builder(Component.literal(useMagician ? "Magician: Automatic" : "Magician: Disabled by ST"), b -> {
                useMagician = !useMagician;
                b.setMessage(Component.literal(useMagician ? "Magician: Automatic" : "Magician: Disabled by ST"));
            }).bounds(this.width / 2 - 110, controlY, 220, 20).build());
            controlY += 24;
        }

        if (poppyGrowerInPlay) {
            this.addRenderableWidget(Button.builder(Component.literal(releasePoppyGrower ? "Poppy Grower: Identities Released" : "Poppy Grower: Identities Withheld"), b -> {
                releasePoppyGrower = !releasePoppyGrower;
                b.setMessage(Component.literal(releasePoppyGrower ? "Poppy Grower: Identities Released" : "Poppy Grower: Identities Withheld"));
            }).bounds(this.width / 2 - 110, controlY, 220, 20).build());
            controlY += 24;
        }

        if (ClientState.activePlayerCount < 7) {
            this.addRenderableWidget(Button.builder(Component.literal(smallGameOverride ? "Small Game: ST Override" : "Small Game: No Starting Info"), b -> {
                smallGameOverride = !smallGameOverride;
                b.setMessage(Component.literal(smallGameOverride ? "Small Game: ST Override" : "Small Game: No Starting Info"));
            }).bounds(this.width / 2 - 110, controlY, 220, 20).build());
            controlY += 24;
        }

        int y = controlY + 4;
        var visits = ClientState.grimoireRoles.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().getRoleType() == (demon ? RoleType.DEMON : RoleType.MINION))
                .sorted(java.util.Comparator.comparingInt(e -> ClientState.grimoireSeatNumbers.getOrDefault(e.getKey(), 0))).toList();
        int rows = Math.max(1, (this.height - y - 56) / 24);
        int pages = Math.max(1, (visits.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        for (var entry : visits.subList(Math.min(visits.size(), page * rows), Math.min(visits.size(), (page + 1) * rows))) {
            var id = entry.getKey();
            int seat = ClientState.grimoireSeatNumbers.getOrDefault(id, 0);
            this.addRenderableWidget(Button.builder(Component.literal("Visit " + ClientState.playerName(id, seat)), b -> {
                ClientStorytellerActions.send("night_visit", id.toString());
                this.onClose();
            }).bounds(this.width / 2 - 110, y, 220, 20).build());
            y += 24;
        }
        if (pages > 1) {
            this.addRenderableWidget(Button.builder(Component.literal("Previous Visits"), b -> {
                page = Math.max(0, page - 1);
                clearWidgets();
                init();
            }).bounds(this.width / 2 - 110, this.height - 54, 106, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Next Visits"), b -> {
                page = Math.min(pages - 1, page + 1);
                clearWidgets();
                init();
            }).bounds(this.width / 2 + 4, this.height - 54, 106, 20).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(this.width / 2 - 45, this.height - 28, 90, 20).build());
    }

    private static boolean roleInPlay(Role role) {
        return ClientState.grimoireRoles.values().stream()
                .anyMatch(value -> value != null && value.isOfficialRole() && value.role() == role);
    }

    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        String title = demon ? "Demon Info" : "Minion Info";
        g.text(this.font, title, (this.width - this.font.width(title)) / 2, 15, UiDrawing.GOLD, true);
        if (ClientState.activePlayerCount < 7)
            g.text(this.font, "No starting evil-team information below 7 players unless the ST overrides it.", 12, 30, UiDrawing.MUTED, false);
    }
}
