package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.ClientLunaticBluffs;
import com.sharktower.bloodonthesharktower.states.ClientState;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
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

    @Override
    protected void init() {
        build(true);
    }

    /** Refresh after the server returns the ST-only Lunatic bluff snapshot. */
    public void refreshLunaticBluffs() {
        clearWidgets();
        build(false);
    }

    private void build(boolean requestLunaticBluffs) {
        if (this.minecraft == null || this.minecraft.player == null
                || !ClientState.storytellerPlayers.contains(this.minecraft.player.getUUID())) return;

        boolean manualLunaticDemon = demon && lunaticOnScript();
        int controlY = 48;

        if (manualLunaticDemon) {
            // Lunatic makes Demon setup a judgment call: false Minions and fake
            // bluffs must never be inferred by the automatic real-team sender.
            if (requestLunaticBluffs) ClientLunaticBluffs.request();

            if (roleInPlay(Role.LUNATIC)) {
                int bluffCount = ClientLunaticBluffs.current().size();
                this.addRenderableWidget(Button.builder(
                                Component.literal("Lunatic Bluffs: " + bluffCount + "/3"), b ->
                                        this.minecraft.gui.setScreen(new LunaticBluffSelectionScreen()))
                        .bounds(this.width / 2 - 110, controlY, 220, 20).build());
                controlY += 24;

                Button sendBluffs = Button.builder(Component.literal("Send Lunatic Bluffs"), b ->
                                ClientLunaticBluffs.sendToLunatic())
                        .bounds(this.width / 2 - 110, controlY, 220, 20).build();
                sendBluffs.active = bluffCount == 3;
                this.addRenderableWidget(sendBluffs);
                controlY += 24;
            }
        } else {
            this.addRenderableWidget(Button.builder(Component.literal(demon ? "Demon Info — Preview" : "Minion Info — Preview"), b ->
                    ClientStorytellerActions.send("team_info_preview", (demon ? "demon" : "minion") + "|" + useMagician + "|" + releasePoppyGrower + "|" + smallGameOverride))
                    .bounds(this.width / 2 - 110, controlY, 220, 20).build());
            controlY += 24;

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
        }

        int y = controlY + 4;
        var visits = ClientState.grimoireRoles.entrySet().stream()
                .filter(entry -> entry.getValue() != null && visitRelevant(entry.getValue(), manualLunaticDemon))
                .sorted(java.util.Comparator.comparingInt(entry -> ClientState.grimoireSeatNumbers.getOrDefault(entry.getKey(), 0)))
                .toList();
        int rows = Math.max(1, (this.height - y - 56) / 24);
        int pages = Math.max(1, (visits.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        for (var entry : visits.subList(Math.min(visits.size(), page * rows), Math.min(visits.size(), (page + 1) * rows))) {
            var id = entry.getKey();
            int seat = ClientState.grimoireSeatNumbers.getOrDefault(id, 0);
            String prefix = manualLunaticDemon ? visitPrefix(entry.getValue()) : "Visit";
            this.addRenderableWidget(Button.builder(Component.literal(prefix + " " + ClientState.playerName(id, seat)), b -> {
                ClientStorytellerActions.send("night_visit", id.toString());
                this.onClose();
            }).bounds(this.width / 2 - 110, y, 220, 20).build());
            y += 24;
        }
        if (pages > 1) {
            this.addRenderableWidget(Button.builder(Component.literal("Previous Visits"), b -> {
                page = Math.max(0, page - 1);
                clearWidgets();
                build(false);
            }).bounds(this.width / 2 - 110, this.height - 54, 106, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Next Visits"), b -> {
                page = Math.min(pages - 1, page + 1);
                clearWidgets();
                build(false);
            }).bounds(this.width / 2 + 4, this.height - 54, 106, 20).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(this.width / 2 - 45, this.height - 28, 90, 20).build());
    }

    private boolean visitRelevant(PendingRoleAssignment assignment, boolean manualLunaticDemon) {
        if (!manualLunaticDemon) {
            return assignment.getRoleType() == (demon ? RoleType.DEMON : RoleType.MINION);
        }
        return assignment.getRoleType() == RoleType.DEMON
                || assignment.getRoleType() == RoleType.MINION
                || isRole(assignment, Role.LUNATIC);
    }

    private static String visitPrefix(PendingRoleAssignment assignment) {
        if (isRole(assignment, Role.LUNATIC)) return "Visit Lunatic —";
        if (assignment.getRoleType() == RoleType.DEMON) return "Visit Demon —";
        if (assignment.getRoleType() == RoleType.MINION) return "Visit Minion —";
        return "Visit —";
    }

    private static boolean roleInPlay(Role role) {
        return ClientState.grimoireRoles.values().stream()
                .anyMatch(value -> isRole(value, role));
    }

    private static boolean isRole(PendingRoleAssignment value, Role role) {
        return value != null && value.isOfficialRole() && value.role() == role;
    }

    private static boolean lunaticOnScript() {
        return ClientState.currentScript != null && ClientState.currentScript.allRoles().stream()
                .anyMatch(role -> role != null && role.getId().equalsIgnoreCase(Role.LUNATIC.getId()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        boolean manualLunaticDemon = demon && lunaticOnScript();
        String title = manualLunaticDemon ? "Demon Info — Manual" : (demon ? "Demon Info" : "Minion Info");
        g.text(this.font, title, (this.width - this.font.width(title)) / 2, 15, UiDrawing.GOLD, true);
        if (manualLunaticDemon) {
            String line = "Lunatic on script: Demon Info is manual.";
            g.text(this.font, line, (this.width - this.font.width(line)) / 2, 30, UiDrawing.MUTED, false);
        } else if (ClientState.activePlayerCount < 7) {
            g.text(this.font, "No starting evil-team information below 7 players unless the ST overrides it.", 12, 30, UiDrawing.MUTED, false);
        }
    }
}
