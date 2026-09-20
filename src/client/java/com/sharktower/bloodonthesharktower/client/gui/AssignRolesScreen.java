package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoireBluffWidget;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoirePlayerWidget;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoirePlayerHeadWidget;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoirePerceivedRoleWidget;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoireReminderWidget;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoireStorytellerWidget;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.GamePhase;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 0.8.0 direct visual/interaction port of BOTB's AssignRolesScreen.
 *
 * The original layout constants have been preserved from the 1.21.1 mod:
 *  - role tokens 32px
 *  - player/head markers 24px
 *  - circular role radius min(width/2,height/2)-50
 *  - inner player radius roleRadius-35
 *  - 90x20 Storyteller controls with 5px vertical gaps
 *  - contextual Storyteller controls that change with the active game phase
 *  - Script Builder top-left during Setup, bluffs bottom-left, compact utility
 *    controls along the bottom edge.
 */
public class AssignRolesScreen extends Screen {
    /**
     * The Grimoire layout is tuned at GUI Scale 4. Minecraft Auto may choose a
     * larger scale on high-resolution displays, making the usable Screen canvas
     * smaller and causing the circular Grim to collapse into the centre.
     */
    private static final int GRIMOIRE_REFERENCE_GUI_SCALE = 4;
    private static final int ROLE_SIZE = 32;
    private static final int PERCEIVED_ROLE_SIZE = 20;
    // Original BOTB reminder tokens are 14px with 2px padding around the 32px role token.
    private static final int REMINDER_SIZE = 14;
    private static final int REMINDER_PADDING = 2;
    private static final int DECEIVED_ROLE_GAP = 4;
    private static final int HEAD_SIZE = 24;
    private static final int CONTROL_W = 90;
    private static final int CONTROL_H = 20;
    private static final int MARGIN = 10;
    private static final int GAP = 5;

    private static boolean showUnseated = true;
    private static boolean showSelf = true;
    private static boolean showBluffs = true;

    // The Grim can stay open while seats are populated/shuffled. Keep a small
    // signature so widget instances are rebuilt as soon as the live seat map changes.
    private String lastSeatLayoutSignature = "";

    public AssignRolesScreen() {
        super(Component.literal("Blood on the Sharktower — Grimoire"));
    }

    @Override
    protected void init() {
        buildContextualControls();
        buildPlayerWidgets();
        buildReminderWidgets();
        buildStorytellerWidgets();
        buildUnseatedWidgets();
        buildBluffWidgets();
        lastSeatLayoutSignature = seatLayoutSignature();
    }

    @Override
    public void tick() {
        super.tick();
        if (!ClientState.nominationsOpen && GrimoireInteractionState.hasSelectedNominator()) {
            GrimoireInteractionState.clearNominator();
        }
        String currentSignature = seatLayoutSignature();
        if (!currentSignature.equals(lastSeatLayoutSignature) && this.minecraft != null) {
            // Role/head widgets are constructed in init(). Re-open this screen when
            // the authoritative seat occupants change so the widgets follow them.
            this.minecraft.gui.setScreen(new AssignRolesScreen());
        }
    }

    /**
     * Original BOTB uses the Grimoire itself as the primary game console.
     * Instead of leaving every Storyteller action visible at once, the right
     * rail changes with the current GamePhase. Advanced/recovery tools remain
     * one click away through TOOLS.
     */
    private void buildContextualControls() {
        if (!ClientGrimoireEdits.isLocalStoryteller()) return;

        int layoutWidth = layoutWidth();
        int layoutHeight = layoutHeight();
        int rightX = layoutWidth - CONTROL_W - MARGIN;
        int y = MARGIN;
        GamePhase phase = ClientState.phase();

        if (phase == GamePhase.SETUP) {
            this.addRenderableWidget(Button.builder(Component.literal("Script Builder"), b ->
                            this.minecraft.gui.setScreen(new ScriptBuilderScreen()))
                    .bounds(MARGIN, MARGIN, 100, CONTROL_H).build());

            y = addRightAction(rightX, y, "Shuffle Roles", ChatFormatting.AQUA, "shuffle_roles");
            y = addRightAction(rightX, y, "Shuffle Seats", ChatFormatting.AQUA, "shuffle_seats");
            y = addRightAction(rightX, y, "Randomize Roles", ChatFormatting.GOLD, "randomize_roles");

            this.addRenderableWidget(Button.builder(Component.literal("Role Bag").withStyle(ChatFormatting.LIGHT_PURPLE), b ->
                            this.minecraft.gui.setScreen(new RoleBagScreen()))
                    .bounds(rightX, y, CONTROL_W, CONTROL_H).build());
            y += CONTROL_H + GAP;

            this.addRenderableWidget(Button.builder(
                            Component.literal("Unseated: " + (showUnseated ? "SHOW" : "HIDE")), b -> {
                        showUnseated = !showUnseated;
                        this.minecraft.gui.setScreen(new AssignRolesScreen());
                    }).bounds(rightX, y, CONTROL_W, CONTROL_H).build());
            y += CONTROL_H + GAP;

            this.addRenderableWidget(Button.builder(
                            Component.literal("Self: " + (showSelf ? "SHOW" : "HIDE")), b -> {
                        showSelf = !showSelf;
                        this.minecraft.gui.setScreen(new AssignRolesScreen());
                    }).bounds(rightX, y, CONTROL_W, CONTROL_H).build());
            y += CONTROL_H + GAP + 10;

            y = addRightAction(rightX, y, "Send to Seats", ChatFormatting.LIGHT_PURPLE, "send_to_seats");
            addRightAction(rightX, y, "Send Home", ChatFormatting.AQUA, "send_home");
        } else if (phase == GamePhase.NIGHT) {
            y = addRightAction(rightX, y, "Send to Seats", ChatFormatting.LIGHT_PURPLE, "send_to_seats");
            y = addRightAction(rightX, y, "Send Home", ChatFormatting.AQUA, "send_home");
            this.addRenderableWidget(Button.builder(Component.literal("Start Day").withStyle(ChatFormatting.GOLD), b ->
                            action("phase_day"))
                    .bounds(rightX, y, CONTROL_W, CONTROL_H).build());
        } else if (phase == GamePhase.DAY) {
            y = addRightAction(rightX, y, "Send to Seats", ChatFormatting.LIGHT_PURPLE, "send_to_seats");
            y = addRightScreen(rightX, y, "Timer", ChatFormatting.YELLOW,
                    () -> this.minecraft.gui.setScreen(new TimerScreen()));
            y = addRightAction(rightX, y, "Open Noms", ChatFormatting.GOLD, "nominations_open");

            if (ClientState.canBeExiled.values().stream().anyMatch(Boolean.TRUE::equals)) {
                addRightScreen(rightX, y, "Traveller Exile", ChatFormatting.LIGHT_PURPLE,
                        () -> this.minecraft.gui.setScreen(new ExileControlScreen()));
            }
        } else if (phase == GamePhase.NOMINATIONS) {
            y = addRightScreen(rightX, y, "Timer", ChatFormatting.YELLOW,
                    () -> this.minecraft.gui.setScreen(new TimerScreen()));
            y = addRightScreen(rightX, y, "Nomination Flow", ChatFormatting.GOLD,
                    () -> this.minecraft.gui.setScreen(new NominationControlScreen()));
            y = addRightAction(rightX, y, "Close Noms", ChatFormatting.GRAY, "nominations_close");
            addRightAction(rightX, y, "No Execution", ChatFormatting.DARK_GRAY, "no_execution");
        } else if (phase == GamePhase.PLAYER_NOMINATED) {
            y = addRightScreen(rightX, y, "Timer", ChatFormatting.YELLOW,
                    () -> this.minecraft.gui.setScreen(new TimerScreen()));

            String voteLabel = ClientState.voteInProgress
                    ? (ClientState.voteClockComplete ? "Finish Vote" : "Vote Running")
                    : "Start Vote";
            Button voteButton = Button.builder(Component.literal(voteLabel).withStyle(ChatFormatting.AQUA), b -> {
                        if (ClientState.voteInProgress) {
                            if (ClientState.voteClockComplete) action("vote_finish");
                        } else {
                            action("vote_start");
                        }
                    })
                    .bounds(rightX, y, CONTROL_W, CONTROL_H).build();
            voteButton.active = !ClientState.voteInProgress || ClientState.voteClockComplete;
            this.addRenderableWidget(voteButton);
            y += CONTROL_H + GAP;

            y = addRightAction(rightX, y, "Cancel Nom.", ChatFormatting.GRAY, "nomination_cancel");
            addRightScreen(rightX, y, "Nomination Flow", ChatFormatting.GOLD,
                    () -> this.minecraft.gui.setScreen(new NominationControlScreen()));
        } else if (phase == GamePhase.PLAYER_MARKED) {
            y = addRightScreen(rightX, y, "Timer", ChatFormatting.YELLOW,
                    () -> this.minecraft.gui.setScreen(new TimerScreen()));
            y = addRightAction(rightX, y, "Execute — Dies", ChatFormatting.RED, "execute_marked");
            y = addRightAction(rightX, y, "Execute — Lives", ChatFormatting.GOLD, "execute_marked_survives");
            addRightAction(rightX, y, "Close Noms", ChatFormatting.GRAY, "nominations_close");
        } else if (phase == GamePhase.CALL_FOR_EXILE || phase == GamePhase.EXILE_SUPPORT) {
            y = addRightScreen(rightX, y, "Timer", ChatFormatting.YELLOW,
                    () -> this.minecraft.gui.setScreen(new TimerScreen()));
            y = addRightScreen(rightX, y, "Traveller Exile", ChatFormatting.LIGHT_PURPLE,
                    () -> this.minecraft.gui.setScreen(new ExileControlScreen()));
            addRightAction(rightX, y, "Reset Exile", ChatFormatting.GRAY, "exile_reset");
        }

        // Original bottom-left bluff visibility toggle.
        this.addRenderableWidget(Button.builder(
                        Component.literal("Bluffs: " + (showBluffs ? "SHOW" : "HIDE")), b -> {
                    showBluffs = !showBluffs;
                    this.minecraft.gui.setScreen(new AssignRolesScreen());
                }).bounds(MARGIN, layoutHeight - 30, CONTROL_W, CONTROL_H).build());

        // Keep advanced/recovery controls available without permanently filling
        // the main Grim with management buttons.
        // Keep the main Grim's bottom strip minimal. End Game remains
        // available inside TOOLS; duplicating it here made the small-width
        // Scale-4 layout unnecessarily cramped.
        this.addRenderableWidget(Button.builder(Component.literal("TOOLS"), b ->
                        this.minecraft.gui.setScreen(new StorytellerToolsScreen()))
                .bounds(rightX - 60, layoutHeight - 30, 55, CONTROL_H).build());

        if (phase == GamePhase.SETUP) {
            this.addRenderableWidget(Button.builder(Component.literal("SEND ROLES").withStyle(ChatFormatting.RED), b ->
                            action("send_roles"))
                    .bounds(rightX, layoutHeight - 30, CONTROL_W, CONTROL_H).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("CONTROLS"), b ->
                            this.minecraft.gui.setScreen(new GrimoireControlsScreen()))
                    .bounds(rightX, layoutHeight - 30, CONTROL_W, CONTROL_H).build());
        }
    }

    private int addRightAction(int x, int y, String label, ChatFormatting colour, String op) {
        this.addRenderableWidget(Button.builder(Component.literal(label).withStyle(colour), b -> action(op))
                .bounds(x, y, CONTROL_W, CONTROL_H).build());
        return y + CONTROL_H + GAP;
    }

    private int addRightScreen(int x, int y, String label, ChatFormatting colour, Runnable open) {
        this.addRenderableWidget(Button.builder(Component.literal(label).withStyle(colour), b -> open.run())
                .bounds(x, y, CONTROL_W, CONTROL_H).build());
        return y + CONTROL_H + GAP;
    }

    private void buildPlayerWidgets() {
        List<Map.Entry<UUID, Integer>> seats = sortedSeats();
        if (seats.isEmpty()) return;

        int centerX = layoutWidth() / 2;
        int centerY = layoutHeight() / 2;
        int radius = Math.max(54, Math.min(centerX, centerY) - 50);
        int innerRadius = Math.max(24, radius - 47);
        int count = seats.size();

        for (int i = 0; i < count; i++) {
            Map.Entry<UUID, Integer> entry = seats.get(i);
            UUID uuid = entry.getKey();
            int seat = entry.getValue() == null ? i + 1 : entry.getValue();
            double angle = (Math.PI * 2.0 / count) * i - Math.PI / 2.0;

            double tokenCenterX = centerX + radius * Math.cos(angle);
            double tokenCenterY = centerY + radius * Math.sin(angle);
            PendingRoleAssignment assignment = ClientGrimoireEdits.roleFor(uuid);
            boolean dead = ClientState.playerDeathStatus.getOrDefault(uuid, false);

            int headX = (int) Math.round(centerX + innerRadius * Math.cos(angle)) - HEAD_SIZE / 2;
            int headY = (int) Math.round(centerY + innerRadius * Math.sin(angle)) - HEAD_SIZE / 2;
            this.addRenderableWidget(new GrimoirePlayerHeadWidget(
                    headX, headY, HEAD_SIZE, uuid, seat, assignment
            ));

            if (isDeceivedCharacter(assignment)) {
                // Centre the TRUE + BELIEVED pair around the player's normal radial
                // token position. Previously the true token stayed on-centre and the
                // believed token was added to one side, which made the pair look
                // visually offset around the Grim.
                double tangentX = -Math.sin(angle);
                double tangentY = Math.cos(angle);
                double trueOffset = -(PERCEIVED_ROLE_SIZE + DECEIVED_ROLE_GAP) / 2.0;
                double perceivedOffset = (ROLE_SIZE + DECEIVED_ROLE_GAP) / 2.0;

                int roleX = (int) Math.round(tokenCenterX + tangentX * trueOffset) - ROLE_SIZE / 2;
                int roleY = (int) Math.round(tokenCenterY + tangentY * trueOffset) - ROLE_SIZE / 2;
                int perceivedX = (int) Math.round(tokenCenterX + tangentX * perceivedOffset)
                        - PERCEIVED_ROLE_SIZE / 2;
                int perceivedY = (int) Math.round(tokenCenterY + tangentY * perceivedOffset)
                        - PERCEIVED_ROLE_SIZE / 2;

                this.addRenderableWidget(new GrimoirePlayerWidget(
                        roleX, roleY, ROLE_SIZE, uuid, seat, assignment, dead
                ));
                this.addRenderableWidget(new GrimoirePerceivedRoleWidget(
                        perceivedX, perceivedY, PERCEIVED_ROLE_SIZE, uuid, seat
                ));
            } else {
                int roleX = (int) Math.round(tokenCenterX) - ROLE_SIZE / 2;
                int roleY = (int) Math.round(tokenCenterY) - ROLE_SIZE / 2;
                this.addRenderableWidget(new GrimoirePlayerWidget(
                        roleX, roleY, ROLE_SIZE, uuid, seat, assignment, dead
                ));
            }
        }
    }

    private static boolean isDeceivedCharacter(PendingRoleAssignment assignment) {
        return assignment != null && !assignment.isCustomRole()
                && (assignment.role() == com.sharktower.bloodonthesharktower.core.Role.DRUNK
                || assignment.role() == com.sharktower.bloodonthesharktower.core.Role.MARIONETTE);
    }

    /**
     * BOTB reminder tokens hug the 32px role token rather than forming a second
     * radial ring around the Grim. Eight compact slots surround each role token;
     * the outward-facing slots are preferred so reminders naturally avoid the
     * player's head/name on the inner side of the circle.
     */
    private void buildReminderWidgets() {
        List<Map.Entry<UUID, Integer>> seats = sortedSeats();
        if (seats.isEmpty()) return;

        int centerX = layoutWidth() / 2;
        int centerY = layoutHeight() / 2;
        int radius = Math.max(54, Math.min(centerX, centerY) - 50);
        int innerRadius = Math.max(24, radius - 47);
        int count = seats.size();

        for (int i = 0; i < count; i++) {
            Map.Entry<UUID, Integer> entry = seats.get(i);
            UUID uuid = entry.getKey();
            int seat = entry.getValue() == null ? i + 1 : entry.getValue();
            double angle = (Math.PI * 2.0 / count) * i - Math.PI / 2.0;

            double tokenCenterX = centerX + radius * Math.cos(angle);
            double tokenCenterY = centerY + radius * Math.sin(angle);
            PendingRoleAssignment assignment = ClientGrimoireEdits.roleFor(uuid);

            // Match the true-role position used by buildPlayerWidgets for
            // Drunk/Marionette true+believed role pairs.
            if (isDeceivedCharacter(assignment)) {
                double tangentX = -Math.sin(angle);
                double tangentY = Math.cos(angle);
                double trueOffset = -(PERCEIVED_ROLE_SIZE + DECEIVED_ROLE_GAP) / 2.0;
                tokenCenterX += tangentX * trueOffset;
                tokenCenterY += tangentY * trueOffset;
            }

            int roleX = (int) Math.round(tokenCenterX) - ROLE_SIZE / 2;
            int roleY = (int) Math.round(tokenCenterY) - ROLE_SIZE / 2;

            int top = roleY - REMINDER_SIZE - REMINDER_PADDING;
            int bottom = roleY + ROLE_SIZE + REMINDER_PADDING;
            int left = roleX - REMINDER_SIZE - REMINDER_PADDING;
            int right = roleX + ROLE_SIZE + REMINDER_PADDING;
            int nearLeft = roleX + ROLE_SIZE / 2 - REMINDER_SIZE - REMINDER_PADDING;
            int nearRight = roleX + ROLE_SIZE / 2 + REMINDER_PADDING;
            int nearTop = roleY + ROLE_SIZE / 2 - REMINDER_SIZE - REMINDER_PADDING;
            int nearBottom = roleY + ROLE_SIZE / 2 + REMINDER_PADDING;

            List<int[]> candidates = new ArrayList<>(List.of(
                    new int[]{nearLeft, top},
                    new int[]{nearRight, top},
                    new int[]{right, nearTop},
                    new int[]{right, nearBottom},
                    new int[]{nearRight, bottom},
                    new int[]{nearLeft, bottom},
                    new int[]{left, nearBottom},
                    new int[]{left, nearTop}
            ));

            int headX = (int) Math.round(centerX + innerRadius * Math.cos(angle)) - HEAD_SIZE / 2;
            int headY = (int) Math.round(centerY + innerRadius * Math.sin(angle)) - HEAD_SIZE / 2;
            final double roleCenterX = tokenCenterX;
            final double roleCenterY = tokenCenterY;
            final double outwardX = Math.cos(angle);
            final double outwardY = Math.sin(angle);

            candidates.sort(Comparator.comparingDouble((int[] p) ->
                    reminderPlacementScore(
                            p,
                            roleCenterX,
                            roleCenterY,
                            outwardX,
                            outwardY,
                            headX,
                            headY
                    )).reversed());

            List<Reminder> reminders = ClientGrimoireEdits.remindersFor(uuid);
            int reminderCount = Math.min(candidates.size(), reminders.size());
            for (int r = 0; r < reminderCount; r++) {
                int[] position = candidates.get(r);
                this.addRenderableWidget(new GrimoireReminderWidget(
                        position[0], position[1], REMINDER_SIZE, uuid, seat, reminders.get(r)
                ));
            }
        }
    }

    private static double reminderPlacementScore(
            int[] position,
            double roleCenterX,
            double roleCenterY,
            double outwardX,
            double outwardY,
            int headX,
            int headY
    ) {
        double reminderCenterX = position[0] + REMINDER_SIZE / 2.0;
        double reminderCenterY = position[1] + REMINDER_SIZE / 2.0;
        double score = (reminderCenterX - roleCenterX) * outwardX
                + (reminderCenterY - roleCenterY) * outwardY;

        boolean overlapsHead = position[0] < headX + HEAD_SIZE
                && position[0] + REMINDER_SIZE > headX
                && position[1] < headY + HEAD_SIZE
                && position[1] + REMINDER_SIZE > headY;
        return overlapsHead ? score - 1000.0 : score;
    }


    private void buildStorytellerWidgets() {
        List<UUID> storytellers = ClientState.storytellerPlayers;
        if (storytellers == null || storytellers.isEmpty()) return;

        int visible = Math.min(3, storytellers.size());
        int widgetW = 76;
        int gap = 8;
        int totalW = visible * widgetW + Math.max(0, visible - 1) * gap;
        int startX = layoutWidth() / 2 - totalW / 2;
        int y = layoutHeight() / 2 - 54;

        for (int i = 0; i < visible; i++) {
            UUID storytellerId = storytellers.get(i);
            this.addRenderableWidget(new GrimoireStorytellerWidget(
                    startX + i * (widgetW + gap),
                    y,
                    widgetW,
                    storytellerId,
                    id -> this.minecraft.gui.setScreen(new StorytellerInteractionScreen(id, this))
            ));
        }
    }

    private void buildUnseatedWidgets() {
        if (!ClientGrimoireEdits.isLocalStoryteller() || !showUnseated) return;

        // playerSeatNumbers is the live, server-authoritative seating map. During
        // Setup, grimoireSeatNumbers may intentionally lag behind it so the Grim
        // can preserve role state across reconnects. Prefer the live map whenever
        // it is available so sitting down / standing up updates immediately.
        java.util.Set<UUID> seated = new java.util.HashSet<>();
        if (!ClientState.playerSeatNumbers.isEmpty()) {
            seated.addAll(ClientState.playerSeatNumbers.keySet());
        } else {
            seated.addAll(ClientState.grimoireSeatNumbers.keySet());
        }
        java.util.List<UUID> unseated = new java.util.ArrayList<>();
        for (UUID id : ClientState.connectedPlayers) {
            if (seated.contains(id)) continue;
            if (ClientState.storytellerPlayers.contains(id)) continue;
            unseated.add(id);
        }
        if (unseated.isEmpty()) return;
        int x = MARGIN;
        int y = 42;
        for (int i = 0; i < Math.min(8, unseated.size()); i++) {
            UUID id = unseated.get(i);
            String label = "+ " + ClientState.playerName(id, 0);
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                        ClientStorytellerActions.send("seat_player", id.toString());
                    })
                    .bounds(x, y + i * 23, 110, 20).build());
        }
    }

    private void buildBluffWidgets() {
        if (!showBluffs) return;
        if (!ClientGrimoireEdits.isLocalStoryteller() && ClientGrimoireEdits.visibleDemonBluffs().isEmpty()) return;
        int startY = Math.max(55, layoutHeight() / 2 + 18);
        for (int i = 0; i < 3; i++) {
            String roleId = i < ClientGrimoireEdits.visibleDemonBluffs().size() ? ClientGrimoireEdits.visibleDemonBluffs().get(i) : "";
            this.addRenderableWidget(new GrimoireBluffWidget(MARGIN, startY + i * 42, 32, i, roleId));
        }
    }

    private void action(String action) {
        ClientStorytellerActions.send(action);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float scale = grimoireUiScale();
        int scaledMouseX = Math.round(mouseX / scale);
        int scaledMouseY = Math.round(mouseY / scale);

        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        try {
            // Render the widgets and custom Grim drawing through the same virtual
            // Scale-4 canvas. This keeps text, tokens, tooltips and controls in
            // the same proportions instead of letting Auto GUI scale enlarge them.
            super.extractRenderState(graphics, scaledMouseX, scaledMouseY, delta);

            List<Map.Entry<UUID, Integer>> seats = sortedSeats();
            int centerX = layoutWidth() / 2;
            int centerY = layoutHeight() / 2;
            int radius = Math.max(54, Math.min(centerX, centerY) - 50);
            int innerRadius = Math.max(24, radius - 47);

            renderPlayerHeadRing(graphics, seats, centerX, centerY, innerRadius);
            renderSeatNumbers(graphics, seats, centerX, centerY, radius);
            renderCenterStatus(graphics, seats.size());
            renderInteractionHint(graphics);
            renderHandVotingPanel(graphics);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void renderPlayerHeadRing(GuiGraphicsExtractor graphics, List<Map.Entry<UUID, Integer>> seats,
                                      int centerX, int centerY, int innerRadius) {
        int count = seats.size();
        for (int i = 0; i < count; i++) {
            Map.Entry<UUID, Integer> entry = seats.get(i);
            UUID uuid = entry.getKey();
            int seat = entry.getValue() == null ? i + 1 : entry.getValue();
            double angle = (Math.PI * 2.0 / count) * i - Math.PI / 2.0;
            int headX = (int) Math.round(centerX + innerRadius * Math.cos(angle)) - HEAD_SIZE / 2;
            int headY = (int) Math.round(centerY + innerRadius * Math.sin(angle)) - HEAD_SIZE / 2;
            boolean dead = ClientState.playerDeathStatus.getOrDefault(uuid, false);

            // Do not gate face rendering on connectedPlayers. That list can arrive a
            // tick later than the seat map, and PlayerFaceCompat already fails safely
            // when a profile/skin is not available yet. This lets a newly seated
            // player's head appear as soon as Minecraft knows their profile.
            boolean drewFace = PlayerFaceCompat.draw(graphics, uuid, headX, headY, HEAD_SIZE);
            if (!drewFace) {
                graphics.fill(headX, headY, headX + HEAD_SIZE, headY + HEAD_SIZE, 0xCC15151A);
                drawCenteredAt(graphics, Integer.toString(seat), headX + HEAD_SIZE / 2,
                        headY + 7, dead ? UiDrawing.DEAD : UiDrawing.TEXT, true);
            }
            graphics.outline(headX, headY, HEAD_SIZE, HEAD_SIZE, dead ? UiDrawing.DEAD : UiDrawing.TEXT);
            if (GrimoireInteractionState.isSelectedNominator(uuid)) {
                graphics.outline(headX - 2, headY - 2, HEAD_SIZE + 4, HEAD_SIZE + 4, UiDrawing.YES);
            } else if (uuid.equals(ClientState.currentNominee)) {
                graphics.outline(headX - 2, headY - 2, HEAD_SIZE + 4, HEAD_SIZE + 4, UiDrawing.GOLD);
            }
            String name = ClientState.playerName(uuid, seat);
            int nameY = headY + HEAD_SIZE + 2;
            drawCenteredAt(graphics, name, headX + HEAD_SIZE / 2, nameY, dead ? UiDrawing.DEAD : UiDrawing.TEXT, true);

            if (ClientState.isHandRaised(uuid)) {
                drawRaisedHand(graphics, headX + HEAD_SIZE + 3, headY + 5);
            }
        }
    }

    private void renderSeatNumbers(GuiGraphicsExtractor graphics,
                                   List<Map.Entry<UUID, Integer>> seats,
                                   int centerX, int centerY, int radius) {
        int count = seats.size();
        for (int i = 0; i < count; i++) {
            Map.Entry<UUID, Integer> entry = seats.get(i);
            UUID uuid = entry.getKey();
            int seat = entry.getValue() == null ? i + 1 : entry.getValue();
            double angle = (Math.PI * 2.0 / count) * i - Math.PI / 2.0;

            int seatRadius = radius + 20;
            int sx = (int) Math.round(centerX + seatRadius * Math.cos(angle));
            int sy = (int) Math.round(centerY + seatRadius * Math.sin(angle));
            drawCenteredAt(graphics, Integer.toString(seat), sx, sy - 4, UiDrawing.TEXT, true);
        }
    }

    private void renderCenterStatus(GuiGraphicsExtractor graphics, int playerCount) {
        // The original Grim only needs setup counts before play begins. Keeping
        // these off the live-game Grim leaves the centre focused on the ST head.
        if (ClientState.phase() != GamePhase.SETUP) return;

        String players = "Players: " + playerCount;
        String storytellers = "Storytellers: " + ClientState.storytellerPlayers.size();
        int baseY = layoutHeight() / 2 + 18;
        drawCentered(graphics, players, baseY, UiDrawing.TEXT, true);
        drawCentered(graphics, storytellers, baseY + 12, UiDrawing.MUTED, true);
    }

    private void renderInteractionHint(GuiGraphicsExtractor graphics) {
        String hint;
        if (ClientGrimoireEdits.isLocalStoryteller() && ClientState.nominationsOpen) {
            UUID selected = GrimoireInteractionState.selectedNominator();
            if (selected != null) {
                int seat = ClientState.playerSeatNumbers.getOrDefault(selected, 0);
                hint = "Nominator: " + ClientState.playerName(selected, seat)
                        + "  |  Shift+RMB a nominee";
            } else {
                hint = "Shift+LMB a nominator  |  Shift+RMB a nominee";
            }
        } else if (ClientGrimoireEdits.isLocalStoryteller()) {
            hint = "Role: edit  |  Head: reminders  |  RMB: actions";
        } else {
            hint = "Role: deduction  |  Head: reminders";
        }

        // Keep this below the radial role ring. The old h-44 position crossed
        // the bottom player's role token.
        int y = layoutHeight() - 19;
        drawCentered(graphics, hint, y, UiDrawing.MUTED, false);
    }

    private void renderHandVotingPanel(GuiGraphicsExtractor graphics) {
        if (!ClientState.nominationsOpen && !ClientState.voteInProgress) return;

        int x = layoutWidth() - CONTROL_W - MARGIN;
        int y = 225;
        int h = ClientState.voteInProgress ? 70 : 48;
        UiDrawing.panel(graphics, x, y, CONTROL_W, h);
        drawCenteredAt(graphics, ClientState.voteClockComplete ? "VOTE READY" : "VOTING",
                x + CONTROL_W / 2, y + 6, UiDrawing.GOLD, true);
        drawCenteredAt(graphics, "Required: " + ClientState.voteThreshold, x + CONTROL_W / 2, y + 19, UiDrawing.TEXT, false);
        drawCenteredAt(graphics, "Raised: " + ClientState.handsRaised, x + CONTROL_W / 2, y + 30,
                ClientState.handsRaised >= ClientState.voteThreshold ? 0xFF55CC66 : UiDrawing.TEXT, false);
        if (ClientState.voteInProgress) {
            drawCenteredAt(graphics, "Counted: " + ClientState.effectiveVoteCount, x + CONTROL_W / 2, y + 42, UiDrawing.MUTED, false);
            if (ClientState.voteClockComplete) {
                drawCenteredAt(graphics, "Finish / Override", x + CONTROL_W / 2, y + 54, 0xFF55CC66, true);
            } else {
                int seat = ClientState.playerSeatNumbers.getOrDefault(ClientState.currentVoteClockPlayer, 0);
                drawCenteredAt(graphics, "Now: " + (seat > 0 ? "Seat " + seat : "ST"), x + CONTROL_W / 2, y + 54, UiDrawing.MUTED, false);
            }
        }
    }

    /** Small pixel-art hand marker so we do not depend on emoji font support. */
    private void drawRaisedHand(GuiGraphicsExtractor graphics, int x, int y) {
        int gold = 0xFFFFC857;
        int dark = 0xFF4A3410;
        graphics.fill(x + 2, y + 4, x + 8, y + 10, gold);
        graphics.fill(x + 2, y, x + 3, y + 5, gold);
        graphics.fill(x + 4, y - 1, x + 5, y + 5, gold);
        graphics.fill(x + 6, y, x + 7, y + 5, gold);
        graphics.fill(x + 8, y + 2, x + 9, y + 7, gold);
        graphics.fill(x, y + 6, x + 3, y + 8, gold);
        graphics.outline(x, y - 1, 10, 12, dark);
    }


    /**
     * Normalize GUI scales above 4 back to the Scale-4 physical footprint.
     * Scales 1-4 keep their native sizing so smaller windows are never enlarged.
     */
    private float grimoireUiScale() {
        if (this.minecraft == null) return 1.0F;
        int activeGuiScale = Math.max(1, this.minecraft.getWindow().getGuiScale());
        if (activeGuiScale <= GRIMOIRE_REFERENCE_GUI_SCALE) return 1.0F;
        return GRIMOIRE_REFERENCE_GUI_SCALE / (float) activeGuiScale;
    }

    private int layoutWidth() {
        return Math.max(1, Math.round(this.width / grimoireUiScale()));
    }

    private int layoutHeight() {
        return Math.max(1, Math.round(this.height / grimoireUiScale()));
    }

    private MouseButtonEvent remapMouse(MouseButtonEvent event) {
        float scale = grimoireUiScale();
        if (scale == 1.0F) return event;
        return new MouseButtonEvent(event.x() / scale, event.y() / scale, event.buttonInfo());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(remapMouse(event), doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(remapMouse(event));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        float scale = grimoireUiScale();
        return super.mouseDragged(remapMouse(event), dragX / scale, dragY / scale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float scale = grimoireUiScale();
        return super.mouseScrolled(mouseX / scale, mouseY / scale, scrollX, scrollY);
    }

    private List<Map.Entry<UUID, Integer>> sortedSeats() {
        // playerSeatNumbers is the live, server-authoritative seating map and must
        // win completely while it contains data. Merging stale grimoire occupants
        // into it meant that an *unseated* player could remain visible indefinitely:
        // there was no new occupant to overwrite the old seat. Falling back to the
        // Grim snapshot only when the live map is genuinely unavailable preserves
        // reconnect compatibility without making Setup seating sticky.
        java.util.TreeMap<Integer, UUID> occupantBySeat = new java.util.TreeMap<>();
        Map<UUID, Integer> source = !ClientState.playerSeatNumbers.isEmpty()
                ? ClientState.playerSeatNumbers
                : ClientState.grimoireSeatNumbers;

        for (Map.Entry<UUID, Integer> entry : source.entrySet()) {
            if (entry.getValue() != null) occupantBySeat.put(entry.getValue(), entry.getKey());
        }

        List<Map.Entry<UUID, Integer>> seats = new ArrayList<>();
        for (Map.Entry<Integer, UUID> entry : occupantBySeat.entrySet()) {
            seats.add(new java.util.AbstractMap.SimpleImmutableEntry<>(entry.getValue(), entry.getKey()));
        }

        if (!showSelf && this.minecraft != null && this.minecraft.player != null) {
            UUID self = this.minecraft.player.getUUID();
            seats.removeIf(entry -> entry.getKey().equals(self));
        }

        return seats;
    }

    private String seatLayoutSignature() {
        StringBuilder signature = new StringBuilder();
        for (Map.Entry<UUID, Integer> entry : sortedSeats()) {
            signature.append(entry.getValue()).append(':').append(entry.getKey()).append(';');
        }
        return signature.toString();
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        drawCenteredAt(graphics, text, layoutWidth() / 2, y, colour, shadow);
    }

    private void drawCenteredAt(GuiGraphicsExtractor graphics, String text, int centerX, int y, int colour, boolean shadow) {
        graphics.text(this.font, text, centerX - this.font.width(text) / 2, y, colour, shadow);
    }
}
