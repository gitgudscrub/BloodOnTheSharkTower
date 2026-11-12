package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.ClientLunaticBluffs;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoireBluffWidget;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoireHoverHints;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoirePlayerWidget;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoirePlayerHeadWidget;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoirePerceivedRoleWidget;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoireReminderWidget;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoireRevealAnimation;
import com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoireStorytellerWidget;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.GamePhase;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import org.lwjgl.sdl.SDLMouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 0.8.0 direct visual/interaction port of BOTB's AssignRolesScreen.
 *
 * The Grim uses its own responsive virtual canvas so Minecraft's GUI-scale
 * preference does not distort the circle. Token/control sizes stay consistent
 * relative to the physical window while the internal rings keep enough room for
 * player names, the centre Storyteller panel and the bluff rail.
 */
public class AssignRolesScreen extends Screen {
    /**
     * The compact multiplayer test window (~1024x576) is the reference viewport.
     * The Grim ignores Minecraft's global GUI scale, but grows/shrinks with the
     * physical window so it occupies roughly the same percentage of the screen.
     */
    private static final int GRIMOIRE_REFERENCE_GUI_SCALE = 2;
    private static final int GRIMOIRE_REFERENCE_WIDTH = 1024;
    private static final int GRIMOIRE_REFERENCE_HEIGHT = 576;
    private static final float GRIMOIRE_MIN_VIEWPORT_SCALE = 0.75F;
    private static final float GRIMOIRE_MAX_VIEWPORT_SCALE = 3.0F;
    private static final int ROLE_SIZE = 32;
    private static final int PERCEIVED_ROLE_SIZE = 20;
    private static final int REMINDER_SIZE = 14;
    private static final int REMINDER_PADDING = 2;
    private static final int DECEIVED_ROLE_GAP = 4;
    private static final int HEAD_SIZE = 24;
    private static final int CONTROL_W = 90;
    private static final int RESOLVE_W = 170;
    /** Height kept free at the bottom edge for the one-line hover hint. */
    private static final int HINT_BAND = 14;
    private static final int CONTROL_H = 20;
    private static final int MARGIN = 10;
    private static final int GAP = 5;

    private static final Identifier PHASE_DUSK = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/icons/original/dusk.png");
    private static final Identifier PHASE_DAWN = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/icons/original/dawn.png");
    private static final Identifier PHASE_NOMINATIONS = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/icons/original/nominations.png");

    private static boolean showUnseated = true;
    private static int unseatedPage;
    private static boolean showSelf = true;
    private static boolean showBluffs = true;
    private boolean showPlayerBluffs = true;

    private String lastSeatLayoutSignature = "";
    private int lastLunaticBluffRevision = -1;

    private record GrimHit(UUID playerId, int seat, PendingRoleAssignment assignment) {}

    private record GrimLayout(int centerX, int centerY, int roleRadius, int headRadius, int roleRadiusX, int headRadiusX) {}

    /**
     * Keep the same physical composition across window sizes, but use a slightly
     * wider ring for small games so names do not pile into the Storyteller in the
     * centre. Large games retain the safer outer margin and slight upward lift.
     */
    private GrimLayout grimoireLayout(int playerCount) {
        int centerX = layoutWidth() / 2;
        // The ring is centred in the space above the hover-hint band, so the lowest seat's role name never meets the hint.
        int baseCenterY = (layoutHeight() - HINT_BAND) / 2;
        int edgeMargin = playerCount <= 6 ? 42 : playerCount <= 9 ? 45 : 50;
        int baseRoleRadius = Math.max(54, Math.min(centerX, baseCenterY) - edgeMargin);

        int inward = switch (playerCount) {
            case 15 -> 8;
            case 14 -> 6;
            case 13 -> 4;
            default -> 0;
        };
        int lift = switch (playerCount) {
            case 15 -> 8;
            case 14 -> 6;
            case 13 -> 4;
            default -> 0;
        };

        int horizontal = Math.max(54, centerX - CONTROL_W - MARGIN - ROLE_SIZE/2 - REMINDER_SIZE);
        int roleRadius = Math.min(horizontal, Math.max(54, baseRoleRadius - inward));
        int headRadius = Math.max(28, roleRadius - 48);
        return new GrimLayout(centerX, baseCenterY - lift, roleRadius, headRadius, horizontal,
                Math.max(28,horizontal - 48));
    }

    private record ResolveEntry(int x, int y, int width, String title, List<net.minecraft.util.FormattedCharSequence> body, float scale) {}

    private final List<ResolveEntry> resolveEntries = new ArrayList<>();
    private final java.util.Set<net.minecraft.client.gui.components.AbstractWidget> resolveButtons = new java.util.HashSet<>();
    private int resolvePanelX, resolvePanelY, resolvePanelH;
    private int resolveIndex;
    private boolean resolveCoversVote;
    private String resolvePage = "";
    private int lastPromptsRevision = -1;

    public AssignRolesScreen() {
        super(Component.literal("Blood on the Sharktower — Grimoire"));
    }

    @Override
    protected void init() {
        if (GrimoireReturnState.consumeSuppressNextReveal()) {
            GrimoireRevealAnimation.showImmediately();
        } else {
            GrimoireRevealAnimation.beginScreen();
        }
        lastLunaticBluffRevision = ClientLunaticBluffs.revision();
        if (ClientGrimoireEdits.isLocalStoryteller() && lunaticAssignedInGrim()) {
            ClientLunaticBluffs.request();
        }
        buildContextualControls();
        buildBluffVisibilityControl();
        buildPlayerWidgets();
        buildReminderWidgets();
        buildStorytellerWidgets();
        buildUnseatedWidgets();
        buildBluffWidgets();
        buildResolveWidgets();
        lastSeatLayoutSignature = seatLayoutSignature();
    }

    @Override
    public void tick() {
        super.tick();
        if (!ClientState.nominationsOpen && GrimoireInteractionState.hasSelectedNominator()) {
            GrimoireInteractionState.clearNominator();
        }
        String currentSignature = seatLayoutSignature();
        int lunaticRevision = ClientLunaticBluffs.revision();
        if ((!currentSignature.equals(lastSeatLayoutSignature)
                || lunaticRevision != lastLunaticBluffRevision
                || ClientState.storytellerPromptsRevision != lastPromptsRevision) && this.minecraft != null) {
            clearWidgets();
            buildContextualControls();
            buildBluffVisibilityControl();
            buildPlayerWidgets();
            buildReminderWidgets();
            buildStorytellerWidgets();
            buildUnseatedWidgets();
            buildBluffWidgets();
            buildResolveWidgets();
            lastSeatLayoutSignature = currentSignature;
            lastLunaticBluffRevision = lunaticRevision;
        }
    }

    private void buildContextualControls() {
        if (!ClientGrimoireEdits.isLocalStoryteller()) return;

        int layoutWidth = layoutWidth();
        int layoutHeight = layoutHeight();
        int rightX = layoutWidth - CONTROL_W - MARGIN;
        int y = MARGIN;
        GamePhase phase = ClientState.phase();

        this.addRenderableWidget(SharktowerButton.builder(
                        Component.literal("Game End").withStyle(ChatFormatting.RED), b ->
                                this.minecraft.gui.setScreen(new EndGameControlScreen(this)))
                .bounds(MARGIN, 48, CONTROL_W, CONTROL_H).build());

        y = addRightAction(rightX, y, "Spectator", ChatFormatting.GRAY, "st_spectator");
        if (phase != GamePhase.SETUP && phase != GamePhase.NIGHT && !ClientState.pendingDeaths.isEmpty()) {
            y = addRightAction(rightX, y, "Reveal Deaths", ChatFormatting.RED, "reveal_deaths");
        }

        if (phase == GamePhase.SETUP) {
            this.addRenderableWidget(SharktowerButton.builder(Component.literal("Script Builder"), b ->
                            this.minecraft.gui.setScreen(new ScriptBuilderScreen()))
                    .bounds(MARGIN, MARGIN, 100, CONTROL_H).build());

            y = addRightAction(rightX, y, "Shuffle Roles", ChatFormatting.WHITE, "shuffle_roles");
            y = addRightAction(rightX, y, "Shuffle Seats", ChatFormatting.WHITE, "shuffle_seats");
            y = addRightAction(rightX, y, "Randomize Roles", ChatFormatting.WHITE, "randomize_roles");

            this.addRenderableWidget(SharktowerButton.builder(Component.literal("Role Bag"), b ->
                            this.minecraft.gui.setScreen(new RoleBagScreen()))
                    .bounds(rightX, y, CONTROL_W, CONTROL_H).build());
            y += CONTROL_H + GAP;

            this.addRenderableWidget(SharktowerButton.builder(
                            Component.literal("Unseated: " + (showUnseated ? "SHOW" : "HIDE")), b -> {
                        showUnseated = !showUnseated;
                        this.minecraft.gui.setScreen(new AssignRolesScreen());
                    }).bounds(rightX, y, CONTROL_W, CONTROL_H).build());
            y += CONTROL_H + GAP;

            this.addRenderableWidget(SharktowerButton.builder(
                            Component.literal("Self: " + (showSelf ? "SHOW" : "HIDE")), b -> {
                        showSelf = !showSelf;
                        this.minecraft.gui.setScreen(new AssignRolesScreen());
                    }).bounds(rightX, y, CONTROL_W, CONTROL_H).build());
            y += CONTROL_H + GAP + 10;

            y = addRightAction(rightX, y, "Send to Seats", ChatFormatting.WHITE, "send_to_seats");
            addRightAction(rightX, y, "Send to Home", ChatFormatting.WHITE, "send_home");
        } else if (phase == GamePhase.NIGHT) {
            y = addRightScreen(rightX, y, "Minion Info", ChatFormatting.RED,
                    () -> this.minecraft.gui.setScreen(new NightTeamInfoScreen(false)));
            y = addRightScreen(rightX, y, "Demon Info", ChatFormatting.RED,
                    () -> this.minecraft.gui.setScreen(new NightTeamInfoScreen(true)));
            y = addRightAction(rightX, y, "Send to Seats", ChatFormatting.WHITE, "send_to_seats");
            y = addRightAction(rightX, y, "Send to Home", ChatFormatting.WHITE, "send_home");
            this.addRenderableWidget(SharktowerButton.builder(Component.literal("Start Day").withStyle(ChatFormatting.GOLD), b ->
                            action("phase_day"))
                    .bounds(rightX, y, CONTROL_W, CONTROL_H).build());
        } else if (phase == GamePhase.DAY) {
            y = addRightAction(rightX, y, "Send to Seats", ChatFormatting.WHITE, "send_to_seats");
            y = addRightScreen(rightX, y, "Timer", ChatFormatting.WHITE,
                    () -> this.minecraft.gui.setScreen(new TimerScreen()));
            y = addRightAction(rightX, y, "Open Noms", ChatFormatting.GOLD, "nominations_open");

            if (ClientState.canBeExiled.values().stream().anyMatch(Boolean.TRUE::equals)) {
                addRightScreen(rightX, y, "Traveller Exile", ChatFormatting.LIGHT_PURPLE,
                        () -> this.minecraft.gui.setScreen(new ExileControlScreen()));
            }
        } else if (phase == GamePhase.NOMINATIONS) {
            y = addRightScreen(rightX, y, "Timer", ChatFormatting.WHITE,
                    () -> this.minecraft.gui.setScreen(new TimerScreen()));
            y = addRightScreen(rightX, y, "Nomination Flow", ChatFormatting.GOLD,
                    () -> this.minecraft.gui.setScreen(new NominationControlScreen()));
            y = addRightAction(rightX, y, "Close Noms", ChatFormatting.GRAY, "nominations_close");
            addRightAction(rightX, y, "No Execution", ChatFormatting.DARK_GRAY, "no_execution");
        } else if (phase == GamePhase.PLAYER_NOMINATED) {
            y = addRightScreen(rightX, y, "Timer", ChatFormatting.WHITE,
                    () -> this.minecraft.gui.setScreen(new TimerScreen()));

            String voteLabel = ClientState.voteInProgress
                    ? (ClientState.voteClockComplete ? "Finish Vote" : "Vote Running")
                    : "Start Vote";
            SharktowerButton voteButton = SharktowerButton.builder(Component.literal(voteLabel).withStyle(ClientState.voteInProgress && ClientState.voteClockComplete ? ChatFormatting.GREEN : ChatFormatting.GOLD), b -> {
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
            y = addRightScreen(rightX, y, "Timer", ChatFormatting.WHITE,
                    () -> this.minecraft.gui.setScreen(new TimerScreen()));
            y = addRightAction(rightX, y, "Execute — Dies", ChatFormatting.RED, "execute_marked");
            y = addRightAction(rightX, y, "Execute — Lives", ChatFormatting.GREEN, "execute_marked_survives");
            addRightAction(rightX, y, "Close Noms", ChatFormatting.GRAY, "nominations_close");
        } else if (phase == GamePhase.CALL_FOR_EXILE || phase == GamePhase.EXILE_SUPPORT) {
            y = addRightScreen(rightX, y, "Timer", ChatFormatting.WHITE,
                    () -> this.minecraft.gui.setScreen(new TimerScreen()));
            y = addRightScreen(rightX, y, "Traveller Exile", ChatFormatting.LIGHT_PURPLE,
                    () -> this.minecraft.gui.setScreen(new ExileControlScreen()));
            addRightAction(rightX, y, "Reset Exile", ChatFormatting.GRAY, "exile_reset");
        }

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("TOOLS"), b ->
                        this.minecraft.gui.setScreen(new StorytellerToolsScreen()))
                .bounds(rightX - 60, layoutHeight - 30, 55, CONTROL_H).build());

        if (phase == GamePhase.SETUP) {
            this.addRenderableWidget(SharktowerButton.builder(Component.literal("SEND ROLES").withStyle(ChatFormatting.GOLD), b ->
                            action("send_roles"))
                    .bounds(rightX, layoutHeight - 30, CONTROL_W, CONTROL_H).build());
        } else {
            this.addRenderableWidget(SharktowerButton.builder(Component.literal("CONTROLS"), b ->
                            this.minecraft.gui.setScreen(new GrimoireControlsScreen()))
                    .bounds(rightX, layoutHeight - 30, CONTROL_W, CONTROL_H).build());
        }
    }

    private int addRightAction(int x, int y, String label, ChatFormatting colour, String op) {
        this.addRenderableWidget(SharktowerButton.builder(Component.literal(label).withStyle(colour), b -> action(op))
                .bounds(x, y, CONTROL_W, CONTROL_H).build());
        return y + CONTROL_H + GAP;
    }

    private int addRightScreen(int x, int y, String label, ChatFormatting colour, Runnable open) {
        this.addRenderableWidget(SharktowerButton.builder(Component.literal(label).withStyle(colour), b -> open.run())
                .bounds(x, y, CONTROL_W, CONTROL_H).build());
        return y + CONTROL_H + GAP;
    }

    private void buildPlayerWidgets() {
        List<Map.Entry<UUID, Integer>> seats = sortedSeats();
        if (seats.isEmpty()) return;

        int count = seats.size();
        GrimLayout layout = grimoireLayout(count);
        int centerX = layout.centerX();
        int centerY = layout.centerY();
        int radius = layout.roleRadius();
        int innerRadius = layout.headRadius();

        for (int i = 0; i < count; i++) {
            Map.Entry<UUID, Integer> entry = seats.get(i);
            UUID uuid = entry.getKey();
            int seat = entry.getValue() == null ? i + 1 : entry.getValue();
            double angle = (Math.PI * 2.0 / count) * i - Math.PI / 2.0;

            double tokenCenterX = centerX + layout.roleRadiusX() * Math.cos(angle);
            double tokenCenterY = centerY + radius * Math.sin(angle);
            PendingRoleAssignment assignment = ClientGrimoireEdits.roleFor(uuid);
            boolean dead = ClientState.playerDeathStatus.getOrDefault(uuid, false);

            int headX = (int) Math.round(centerX + layout.headRadiusX() * Math.cos(angle)) - HEAD_SIZE / 2;
            int headY = (int) Math.round(centerY + innerRadius * Math.sin(angle)) - HEAD_SIZE / 2;
            this.addRenderableWidget(new GrimoirePlayerHeadWidget(
                    headX, headY, HEAD_SIZE, uuid, seat, assignment
            ));

            if (isDeceivedCharacter(assignment)) {
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
                && (assignment.role() == Role.DRUNK
                || assignment.role() == Role.MARIONETTE
                || assignment.role() == Role.LUNATIC);
    }

    private void buildReminderWidgets() {
        List<Map.Entry<UUID, Integer>> seats = sortedSeats();
        if (seats.isEmpty()) return;

        int count = seats.size();
        GrimLayout layout = grimoireLayout(count);
        int centerX = layout.centerX();
        int centerY = layout.centerY();
        int radius = layout.roleRadius();
        int innerRadius = layout.headRadius();

        for (int i = 0; i < count; i++) {
            Map.Entry<UUID, Integer> entry = seats.get(i);
            UUID uuid = entry.getKey();
            int seat = entry.getValue() == null ? i + 1 : entry.getValue();
            double angle = (Math.PI * 2.0 / count) * i - Math.PI / 2.0;

            double tokenCenterX = centerX + layout.roleRadiusX() * Math.cos(angle);
            double tokenCenterY = centerY + radius * Math.sin(angle);
            PendingRoleAssignment assignment = ClientGrimoireEdits.roleFor(uuid);

            if (isDeceivedCharacter(assignment)) {
                double tangentX = -Math.sin(angle);
                double tangentY = Math.cos(angle);
                double trueOffset = -(PERCEIVED_ROLE_SIZE + DECEIVED_ROLE_GAP) / 2.0;
                tokenCenterX += tangentX * trueOffset;
                tokenCenterY += tangentY * trueOffset;
            }

            int roleX = (int) Math.round(tokenCenterX) - ROLE_SIZE / 2;
            int roleY = (int) Math.round(tokenCenterY) - ROLE_SIZE / 2;

            placeReminderTokens(uuid, seat, ClientGrimoireEdits.remindersFor(uuid), tokenCenterX, tokenCenterY, angle, roleX, roleY);
        }
    }

    /**
     * Mini tokens on the rim: small round icons tucked against the role token's
     * edge, starting on the side away from the circle's centre and fanning out
     * either way, like reminder tokens in the real grimoire. The inward side
     * stays clear of the player's head and the bottom clear of the role name.
     * With more reminders than spots, the last spot becomes "+N".
     */
    private void placeReminderTokens(UUID uuid, int seat, List<Reminder> reminders,
                                     double cx, double cy, double outward, int roleX, int roleY) {
        if (reminders.isEmpty()) return;
        int size = REMINDER_SIZE;
        double ringRadius = ROLE_SIZE / 2.0 + size / 2.0 + 1;
        double step = 2 * Math.asin((size + 3) / 2.0 / ringRadius);
        List<Double> angles = new ArrayList<>();
        angles.add(outward);
        for (int k = 1; k <= 4; k++) {
            angles.add(outward + k * step);
            angles.add(outward - k * step);
        }
        boolean labels = com.sharktower.bloodonthesharktower.client.config.ClientSettings.grimoireLabels;
        boolean badge = com.sharktower.bloodonthesharktower.client.config.ClientSettings.alignmentLetters
                && ClientGrimoireEdits.visibleAlignmentFor(uuid) != com.sharktower.bloodonthesharktower.core.AlignmentOverride.DEFAULT;
        List<int[]> spots = new ArrayList<>();
        for (double a : angles) {
            if (Math.abs(angleDiff(a, outward + Math.PI)) < Math.toRadians(45)) continue;
            if (badge && Math.abs(angleDiff(a, Math.PI / 4)) < Math.toRadians(35)) continue;
            int x = (int) Math.round(cx + ringRadius * Math.cos(a)) - size / 2;
            int y = (int) Math.round(cy + ringRadius * Math.sin(a)) - size / 2;
            int[] area = {x - 1, y - 1, size + 2, size + 2};
            if (area[0] < 4 || area[0] + area[2] > layoutWidth() - 4
                    || area[1] < 16 || area[1] + area[3] > layoutHeight() - 30) continue;
            if (collidesWithWidget(area, roleX, roleY)) continue;
            // The role name under the token is up to ROLE_SIZE + 18 wide (GrimoirePlayerWidget.drawRoleLabel).
            if (labels && overlaps(area, new int[]{roleX - 10, roleY + ROLE_SIZE
                    + com.sharktower.bloodonthesharktower.client.gui.grimoire.GrimoirePlayerWidget.LABEL_GAP - 2, ROLE_SIZE + 20, 11})) continue;
            boolean onAnother = false;
            for (int[] spot : spots) {
                if (overlaps(new int[]{x, y, size, size}, new int[]{spot[0], spot[1], size, size})) { onAnother = true; break; }
            }
            if (onAnother) continue;
            spots.add(new int[]{x, y});
        }
        if (spots.isEmpty()) return;
        int shown = reminders.size() <= spots.size() ? reminders.size() : spots.size() - 1;
        for (int i = 0; i < shown; i++) {
            this.addRenderableWidget(GrimoireReminderWidget.rim(spots.get(i)[0], spots.get(i)[1], size, uuid, seat, reminders.get(i)));
        }
        if (shown < reminders.size()) {
            this.addRenderableWidget(GrimoireReminderWidget.overflow(spots.get(shown)[0], spots.get(shown)[1], size,
                    uuid, seat, reminders.size() - shown));
        }
    }

    private static double angleDiff(double a, double b) {
        double d = (a - b) % (Math.PI * 2);
        if (d > Math.PI) d -= Math.PI * 2;
        if (d < -Math.PI) d += Math.PI * 2;
        return d;
    }

    /** Overlap check that ignores this seat's own role token, which the rim tokens hug on purpose. */
    private boolean collidesWithWidget(int[] area, int ownX, int ownY) {
        for (var child : this.children()) {
            if (!(child instanceof net.minecraft.client.gui.components.AbstractWidget widget)) continue;
            if (widget.getX() == ownX && widget.getY() == ownY && widget.getWidth() == ROLE_SIZE) continue;
            if (overlaps(area, new int[]{widget.getX() - 1, widget.getY() - 1, widget.getWidth() + 2, widget.getHeight() + 2})) {
                return true;
            }
        }
        return false;
    }

    private boolean collidesWithWidget(int[] area) {
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                    && overlaps(area, new int[]{widget.getX() - 1, widget.getY() - 1, widget.getWidth() + 2, widget.getHeight() + 2})) {
                return true;
            }
        }
        return false;
    }

    private void buildStorytellerWidgets() {
        List<UUID> storytellers = ClientState.storytellerPlayers;
        if (storytellers == null || storytellers.isEmpty()) return;

        int visible = Math.min(3, storytellers.size());
        int widgetW = 76;
        int gap = 8;
        int totalW = visible * widgetW + Math.max(0, visible - 1) * gap;
        int startX = layoutWidth() / 2 - totalW / 2;
        int y = layoutHeight() / 2 - 40;

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
        int y = 76;
        int endY = shouldShowBluffs() ? bluffStartY() - 20 : layoutHeight() - 44;
        int rows = Math.max(1, (endY - y - 26) / 24);
        int pages = Math.max(1, (unseated.size() + rows - 1) / rows);
        unseatedPage = Math.min(unseatedPage, pages - 1);
        int start = unseatedPage * rows;
        for (int i = start; i < Math.min(start + rows, unseated.size()); i++) {
            UUID id = unseated.get(i);
            String name = ClientState.playerName(id, 0);
            String label = "+ " + this.font.plainSubstrByWidth(name, CONTROL_W - 16);
            this.addRenderableWidget(SharktowerButton.builder(Component.literal(label), b -> {
                        ClientStorytellerActions.send("seat_player", id.toString());
                    }).bounds(x, y + (i - start) * 24, CONTROL_W, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(name))).build());
        }
        if (pages > 1) {
            int navY = y + rows * 24;
            this.addRenderableWidget(SharktowerButton.builder(Component.literal("<"), b -> { unseatedPage=Math.max(0,unseatedPage-1);rebuildWidgets(); })
                    .bounds(x,navY,42,20).build());
            this.addRenderableWidget(SharktowerButton.builder(Component.literal(">"), b -> { unseatedPage=Math.min(pages-1,unseatedPage+1);rebuildWidgets(); })
                    .bounds(x+48,navY,42,20).build());
        }
    }

    private boolean shouldShowBluffs() {
        return ClientGrimoireEdits.isLocalStoryteller() ? showBluffs : showPlayerBluffs;
    }

    private void buildBluffVisibilityControl() {
        if (!ClientGrimoireEdits.isLocalStoryteller() && ClientGrimoireEdits.visibleDemonBluffs().isEmpty()) return;
        this.addRenderableWidget(SharktowerButton.builder(
                        Component.literal("Bluffs: " + (shouldShowBluffs() ? "SHOW" : "HIDE")), b -> {
                    if (ClientGrimoireEdits.isLocalStoryteller()) showBluffs = !showBluffs;
                    else showPlayerBluffs = !showPlayerBluffs;
                    GrimoireRevealAnimation.showImmediately();
                    this.rebuildWidgets();
                }).bounds(MARGIN, layoutHeight() - 30, CONTROL_W, CONTROL_H).build());
    }

    private int bluffStartY() {
        return Math.max(85, layoutHeight() / 2 - 10);
    }

    private void buildBluffWidgets() {
        if (!shouldShowBluffs()) return;
        boolean storyteller = ClientGrimoireEdits.isLocalStoryteller();
        if (!storyteller && ClientGrimoireEdits.visibleDemonBluffs().isEmpty()) return;

        int startY = bluffStartY();
        List<String> demonBluffs = ClientGrimoireEdits.visibleDemonBluffs();
        for (int i = 0; i < 3; i++) {
            String roleId = i < demonBluffs.size() ? demonBluffs.get(i) : "";
            this.addRenderableWidget(new GrimoireBluffWidget(MARGIN, startY + i * 42, 32, i, roleId));
        }

        if (storyteller && lunaticAssignedInGrim()) {
            int x = MARGIN + 44;
            List<String> lunaticBluffs = ClientLunaticBluffs.current();
            for (int i = 0; i < 3; i++) {
                String roleId = i < lunaticBluffs.size() ? lunaticBluffs.get(i) : "";
                this.addRenderableWidget(new GrimoireBluffWidget(x, startY + i * 42, 32, i, roleId, true));
            }
        }
    }

    private boolean lunaticAssignedInGrim() {
        return ClientState.grimoireRoles.values().stream()
                .anyMatch(value -> value != null && value.isOfficialRole() && value.role() == Role.LUNATIC);
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
            GrimoireHoverHints.clear();
            super.extractRenderState(graphics, scaledMouseX, scaledMouseY, delta);

            List<Map.Entry<UUID, Integer>> seats = sortedSeats();
            GrimLayout layout = grimoireLayout(seats.size());
            int centerX = layout.centerX();
            int centerY = layout.centerY();
            int radius = layout.roleRadius();
            int innerRadius = layout.headRadius();

            renderPlayerHeadRing(graphics, seats, centerX, centerY, innerRadius, layout.headRadiusX());
            renderSeatNumbers(graphics, seats, centerX, centerY, innerRadius, layout.headRadiusX());
            renderCenterStatus(graphics, seats.size());
            renderBluffLabels(graphics);
            renderPhaseIndicator(graphics);
            renderHoverHint(graphics);
            renderHandVotingPanel(graphics);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void renderBluffLabels(GuiGraphicsExtractor graphics) {
        if (!shouldShowBluffs() || !ClientGrimoireEdits.isLocalStoryteller()) return;
        if (!lunaticAssignedInGrim()) {
            graphics.text(this.font,"Bluffs",MARGIN,bluffStartY()-11,UiDrawing.MUTED,false);return;
        }
        int y = bluffStartY() - 11;
        graphics.text(this.font, "Demon", MARGIN, y, UiDrawing.MUTED, false);
        graphics.text(this.font, "Lunatic", MARGIN + 44, y, UiDrawing.MUTED, false);
    }

    private void renderPlayerHeadRing(GuiGraphicsExtractor graphics, List<Map.Entry<UUID, Integer>> seats,
                                      int centerX, int centerY, int innerRadius, int innerRadiusX) {
        int count = seats.size();
        List<int[]> labelAreas = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 / count) * i - Math.PI / 2.0;
            int badgeX = (int)Math.round(centerX + innerRadiusX * Math.cos(angle)) - HEAD_SIZE / 2 - 10;
            int badgeY = (int)Math.round(centerY + innerRadius * Math.sin(angle));
            labelAreas.add(new int[]{badgeX-8,badgeY-8,16,16});
            int handX=(int)Math.round(centerX+innerRadiusX*Math.cos(angle))+HEAD_SIZE/2+2;
            int handY=(int)Math.round(centerY+innerRadius*Math.sin(angle))-HEAD_SIZE/2-18;
            labelAreas.add(new int[]{handX,handY,24,16});
        }
        if (ClientState.phase()==GamePhase.SETUP) {
            String[] status={"Players: "+count,"Storytellers: "+ClientState.storytellerPlayers.size()};
            for (int i=0;i<status.length;i++) {
                int w=this.font.width(status[i])+6;
                labelAreas.add(new int[]{centerX-w/2,layoutHeight()/2+14+i*12,w,12});
            }
        }
        for (int i = 0; i < count; i++) {
            Map.Entry<UUID, Integer> entry = seats.get(i);
            UUID uuid = entry.getKey();
            int seat = entry.getValue() == null ? i + 1 : entry.getValue();
            double angle = (Math.PI * 2.0 / count) * i - Math.PI / 2.0;
            int headX = (int) Math.round(centerX + innerRadiusX * Math.cos(angle)) - HEAD_SIZE / 2;
            int headY = (int) Math.round(centerY + innerRadius * Math.sin(angle)) - HEAD_SIZE / 2;
            boolean dead = ClientState.playerDeathStatus.getOrDefault(uuid, false);

            float reveal = GrimoireRevealAnimation.progressForSeat(seat);
            if (!GrimoireRevealAnimation.beginElement(
                    graphics, headX, headY, HEAD_SIZE, HEAD_SIZE + 16, reveal)) {
                continue;
            }
            try {

            // Do not gate face rendering on connectedPlayers. That list can arrive a
            // tick later than the seat map, and PlayerFaceCompat already fails safely
            // when a profile/skin is not available yet. This lets a newly seated
            // player's head appear as soon as Minecraft knows their profile.
            boolean drewFace = PlayerFaceCompat.draw(graphics, uuid, headX, headY, HEAD_SIZE);
            if (!drewFace) {
                graphics.fill(headX, headY, headX + HEAD_SIZE, headY + HEAD_SIZE, UiTheme.dark() ? 0xCC15151A : 0xCCE6D9BB);
                drawCenteredAt(graphics, Integer.toString(seat), headX + HEAD_SIZE / 2,
                        headY + 7, dead ? UiDrawing.DEAD : UiDrawing.TEXT, UiTheme.dark());
            }
            graphics.outline(headX, headY, HEAD_SIZE, HEAD_SIZE, dead ? UiDrawing.DEAD : UiDrawing.TEXT);
            if (GrimoireInteractionState.isSelectedNominator(uuid)) {
                graphics.outline(headX - 2, headY - 2, HEAD_SIZE + 4, HEAD_SIZE + 4, UiTheme.yes());
            } else if (uuid.equals(ClientState.currentNominee)) {
                graphics.outline(headX - 2, headY - 2, HEAD_SIZE + 4, HEAD_SIZE + 4, UiDrawing.GOLD);
            }
            String name = ClientState.playerName(uuid, seat);
            // Use real widget bounds when placing labels, including believed roles/reminders.
            int nameWidth = 96;
            var notedRole = UiDrawing.roleOf(ClientGrimoireEdits.roleFor(uuid));
            for (int width = nameWidth; width >= 30; width -= 12) {
                String label = fitLabel(name,width);
                int[] area = nameArea(label,headX,headY,angle,labelAreas);
                if (area == null) continue;
                labelAreas.add(area);
                graphics.fill(area[0],area[1],area[0]+area[2],area[1]+area[3],UiTheme.dark() ? 0xB015151A : 0xC0F5EDD9);
                graphics.text(this.font,label,area[0]+3,area[1]+2,
                        notedRole == null ? UiDrawing.TEXT : UiDrawing.teamColor(notedRole.getTeam()),UiTheme.dark());
                break;
            }
            if (dead) UiDrawing.deathShroud(graphics, headX, headY, HEAD_SIZE);

            boolean speaking = ClientState.handRaiseMode() == com.sharktower.bloodonthesharktower.core.HandRaiseMode.SPEAKING;
            boolean voting = ClientState.handRaiseMode() == com.sharktower.bloodonthesharktower.core.HandRaiseMode.VOTING;
            if (speaking && ClientState.attentionHands.containsKey(uuid) || voting && ClientState.isHandRaised(uuid)) {
                drawRaisedHand(graphics, headX + HEAD_SIZE + 3, headY - 16);
                if (speaking)
                    graphics.text(this.font, Integer.toString(ClientState.attentionHands.getOrDefault(uuid, 0)),
                            headX + HEAD_SIZE + 14, headY - 16, UiDrawing.GOLD, UiTheme.dark());
            }
            } finally {
                GrimoireRevealAnimation.endElement(graphics);
            }
        }
    }

    private int[] nameArea(String name,int headX,int headY,double angle,List<int[]> reserved) {
        int width=this.font.width(name)+6;
        int left=headX+HEAD_SIZE/2-width/2;
        int above=headY-17,below=headY+HEAD_SIZE+6;
        int preferred=Math.sin(angle)>0.25?above:below;
        int alternate=preferred==above?below:above;
        int[][] candidates={{left,preferred,width,12},{left,alternate,width,12},
                {headX+HEAD_SIZE+6,headY+6,width,12},{headX-width-6,headY+6,width,12}};
        for (int[] area:candidates) {
            if (area[0]<120 || area[0]+area[2]>layoutWidth()-110 || area[1]<44 || area[1]+area[3]>layoutHeight()-44) continue;
            boolean collision=reserved.stream().anyMatch(other -> overlaps(area,other));
            if (!collision) for (var child:this.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                        && overlaps(area,new int[]{widget.getX()-2,widget.getY()-2,widget.getWidth()+4,widget.getHeight()+4})) { collision=true;break; }
            }
            if (!collision) return area;
        }
        return null; // The portrait's hover hint retains the full name when space is exhausted.
    }
    private static boolean overlaps(int[] a,int[] b) {
        return a[0]<b[0]+b[2] && a[0]+a[2]>b[0] && a[1]<b[1]+b[3] && a[1]+a[3]>b[1];
    }

    private void renderSeatNumbers(GuiGraphicsExtractor graphics,
                                   List<Map.Entry<UUID, Integer>> seats,
                                   int centerX, int centerY, int radius, int radiusX) {
        int count = seats.size();
        for (int i = 0; i < count; i++) {
            Map.Entry<UUID, Integer> entry = seats.get(i);
            UUID uuid = entry.getKey();
            int seat = entry.getValue() == null ? i + 1 : entry.getValue();
            double angle = (Math.PI * 2.0 / count) * i - Math.PI / 2.0;

            int sx = (int) Math.round(centerX + radiusX * Math.cos(angle)) - HEAD_SIZE / 2 - 10;
            int sy = (int) Math.round(centerY + radius * Math.sin(angle));

            float reveal = GrimoireRevealAnimation.progressForSeat(seat);
            if (!GrimoireRevealAnimation.beginElement(graphics, sx - 6, sy - 6, 12, 12, reveal)) {
                continue;
            }
            try {
                UiTheme.badge(graphics, sx - 7, sy - 7, 14, UiTheme.dark() ? 0xFF1E1822 : 0xFFF5EDD9,
                        Integer.toString(seat), UiTheme.dark() ? UiTheme.GOLD : 0xFF5C4315);
            } finally {
                GrimoireRevealAnimation.endElement(graphics);
            }
        }
    }

    private void renderCenterStatus(GuiGraphicsExtractor graphics, int playerCount) {
        if (ClientState.phase() != GamePhase.SETUP) return;

        String players = playerCount + (playerCount == 1 ? " PLAYER" : " PLAYERS");
        String storytellers = "Storytellers: " + ClientState.storytellerPlayers.size();
        int baseY = layoutHeight() / 2 + 16;
        Component label = UiTheme.display(players);
        graphics.text(this.font, label, (layoutWidth() - this.font.width(label)) / 2, baseY,
                UiTheme.dark() ? UiTheme.GOLD : 0xFF5C4315, UiTheme.dark());
        drawCentered(graphics, storytellers, baseY + 13, UiDrawing.MUTED, UiTheme.dark());
    }

    private void renderHoverHint(GuiGraphicsExtractor graphics) {
        String hint = GrimoireHoverHints.current();
        UUID selected = GrimoireInteractionState.selectedNominator();
        if ((hint == null || hint.isBlank()) && selected != null && ClientState.nominationsOpen) {
            int seat = ClientState.playerSeatNumbers.getOrDefault(selected, 0);
            hint = "Nominator: " + ClientState.playerName(selected, seat) + " — Shift+RMB a nominee";
        }

        if (hint == null || hint.isBlank()) return;
        // One line in the band at the bottom edge, between the bottom-left and bottom-right buttons.
        int bandTop = layoutHeight() - HINT_BAND;
        int leftLimit = MARGIN, rightLimit = layoutWidth() - MARGIN;
        for (var child : this.children()) {
            if (!(child instanceof net.minecraft.client.gui.components.AbstractWidget widget) || !widget.visible) continue;
            if (widget.getY() + widget.getHeight() <= bandTop - 4) continue;
            if (widget.getX() + widget.getWidth() / 2 < layoutWidth() / 2) leftLimit = Math.max(leftLimit, widget.getX() + widget.getWidth() + 6);
            else rightLimit = Math.min(rightLimit, widget.getX() - 6);
        }
        int available = Math.max(60, rightLimit - leftLimit - 8);
        float scale = this.font.width(hint) <= available ? 1.0F : 0.75F;
        String text = hint;
        int maxUnscaled = Math.round(available / scale);
        if (this.font.width(text) > maxUnscaled) text = this.font.plainSubstrByWidth(text, maxUnscaled - this.font.width("…")) + "…";
        int textWidth = Math.round(this.font.width(text) * scale);
        int mid = (leftLimit + rightLimit) / 2;
        int left = mid - textWidth / 2;
        int top = bandTop + 3;
        graphics.fill(left - 4, top - 2, left + textWidth + 4, top + Math.round(9 * scale) + 1, UiDrawing.PANEL_SOFT);
        graphics.pose().pushMatrix();
        graphics.pose().translate(left, top + (scale < 1 ? 1 : 0));
        graphics.pose().scale(scale, scale);
        graphics.text(this.font, text, 0, 0, UiDrawing.MUTED, false);
        graphics.pose().popMatrix();
    }

    private void renderPhaseIndicator(GuiGraphicsExtractor graphics) {
        GamePhase phase = ClientState.phase();
        Identifier icon = null;
        String label = null;

        switch (phase) {
            case NIGHT -> {
                icon = PHASE_DUSK;
                label = "NIGHT";
            }
            case DAY -> {
                icon = PHASE_DAWN;
                label = "DAY";
            }
            case NOMINATIONS, PLAYER_NOMINATED, PLAYER_MARKED -> {
                icon = PHASE_NOMINATIONS;
                label = phase == GamePhase.PLAYER_MARKED ? "EXECUTION" : "NOMINATIONS";
            }
            case CALL_FOR_EXILE, EXILE_SUPPORT -> label = "TRAVELLER EXILE";
            default -> {
                return;
            }
        }

        int x = MARGIN;
        int y = MARGIN;
        int iconSize = 28;
        int panelWidth = label.length() > 10 ? 118 : 92;
        UiDrawing.softPanel(graphics, x, y, panelWidth, 32);

        if (icon != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon,
                    x + 2, y + 2, 0, 0, iconSize, iconSize, iconSize, iconSize);
            graphics.text(this.font, label, x + 35, y + 12, UiDrawing.GOLD, UiTheme.dark());
        } else {
            graphics.text(this.font, label,
                    x + (panelWidth - this.font.width(label)) / 2, y + 12, UiDrawing.GOLD, UiTheme.dark());
        }
    }

    /** Bottom edge of the right-hand button rail (Resolve buttons excluded). */
    private int railBottom() {
        int x = layoutWidth() - CONTROL_W - MARGIN;
        int railBottom = 10;
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                    && !resolveButtons.contains(widget)
                    && widget.getX() >= x && widget.getY() < layoutHeight() - 40)
                railBottom = Math.max(railBottom, widget.getY() + widget.getHeight());
        }
        return railBottom;
    }

    private int votePanelTop() {
        int h = ClientState.voteInProgress ? 70 : 48;
        return Math.max(railBottom() + 10, Math.min(225, layoutHeight() - h - 40));
    }

    /**
     * Decisions waiting on the Storyteller (Virgin, Witch, Slayer, star-pass),
     * stacked in a panel at the lower right with a button per choice. Each
     * button runs the same command the chat buttons do, so the server decides.
     */
    private void buildResolveWidgets() {
        resolveEntries.clear();
        resolveButtons.clear();
        resolvePanelH = 0;
        resolveCoversVote = false;
        lastPromptsRevision = ClientState.storytellerPromptsRevision;
        List<com.sharktower.bloodonthesharktower.automation.StorytellerPrompt> prompts = ClientState.storytellerPrompts;
        if (prompts.isEmpty() || !ClientGrimoireEdits.isLocalStoryteller()) return;
        if (resolveIndex >= prompts.size()) resolveIndex = 0;
        var prompt = prompts.get(resolveIndex);
        int w = RESOLVE_W;
        int x = layoutWidth() - w - MARGIN;
        int innerW = w - 8;
        // The whole message always fits: past 5 lines it shrinks to 3/4 size,
        // then 5/8, and only beyond 12 small lines is the end cut.
        Component bodyText = ResolveText.colourRoles(prompt.body(), ClientState.currentScript);
        var actions = prompt.actions();
        int halfW = (innerW - 4) / 2;
        boolean wide = actions.stream().anyMatch(action -> this.font.width(action.label()) + 10 > halfW);
        int top = railBottom() + 6;
        int bottom = layoutHeight() - 36;

        // Normal layout first. If it is taller than the gap between the rail
        // and the bottom buttons (an 854x480 window), compact it in steps:
        // buttons side by side (their labels shrink), then smaller body text.
        float bodyScale = 1.0F;
        List<net.minecraft.util.FormattedCharSequence> body = List.of();
        int bodyH = 0;
        int perRow = 2;
        int height = 0;
        for (int step = 0; step < 4; step++) {
            perRow = (wide && step == 0) ? 1 : 2;
            float minScale = step <= 1 ? 1.0F : (step == 2 ? 0.75F : 0.625F);
            bodyScale = minScale;
            body = this.font.split(bodyText, (int) (innerW / bodyScale));
            for (float scale : new float[]{0.75F, 0.625F}) {
                if (body.size() <= 5 || scale >= bodyScale) continue;
                bodyScale = scale;
                body = this.font.split(bodyText, (int) (innerW / scale));
            }
            if (body.size() > 12) body = body.subList(0, 12);
            bodyH = (int) Math.ceil(body.size() * 9 * bodyScale);
            int rows = (actions.size() + perRow - 1) / perRow;
            height = 18 + 10 + bodyH + 3 + rows * 22 + 4;
            if (height <= bottom - top) break;
        }
        int y;
        if (ClientState.nominationsOpen || ClientState.voteInProgress) {
            int voteTop = votePanelTop();
            int voteBottom = voteTop + (ClientState.voteInProgress ? 70 : 48);
            if (voteBottom + 6 + height <= bottom) y = bottom - height;
            else if (top + height <= voteTop - 6) y = voteTop - 6 - height;
            else {
                // No room above or below the vote panel. Sit beside it, to its
                // left, when that clears the seat ring (big windows); otherwise
                // cover the vote counter, whose numbers are also on the
                // in-world nomination card. Either way no button leaves the screen.
                GrimLayout ring = grimoireLayout(sortedSeats().size());
                int ringRight = ring.centerX() + Math.max(ring.roleRadiusX(), ring.headRadiusX()) + ROLE_SIZE / 2 + 45;
                int besideX = layoutWidth() - CONTROL_W - MARGIN - 6 - w;
                if (besideX >= ringRight) {
                    x = besideX;
                    y = Math.max(10, bottom - height);
                } else {
                    y = Math.max(top, bottom - height);
                    resolveCoversVote = true;
                }
            }
        } else {
            y = Math.max(top, bottom - height);
        }
        resolvePanelX = x;
        resolvePanelY = y;
        resolvePanelH = height;
        this.addRenderableWidget(new ResolvePanelWidget(x, y, w, height));
        if (prompts.size() > 1) {
            String page = (resolveIndex + 1) + "/" + prompts.size();
            resolvePage = page;
            SharktowerButton next = SharktowerButton.builder(Component.literal(">"), b -> {
                resolveIndex = (resolveIndex + 1) % ClientState.storytellerPrompts.size();
                lastPromptsRevision = -1;
            }).bounds(x + w - 4 - 14, y + 3, 14, 12).build();
            resolveButtons.add(next);
            this.addRenderableWidget(next);
        } else {
            resolvePage = "";
        }
        int cursor = y + 18;
        resolveEntries.add(new ResolveEntry(x + 4, cursor, innerW, prompt.title(), body, bodyScale));
        cursor += 10 + bodyH + 3;
        for (int i = 0; i < actions.size(); i += perRow) {
            int inRow = Math.min(perRow, actions.size() - i);
            int bw = (innerW - (inRow - 1) * 4) / inRow;
            for (int j = 0; j < inRow; j++) {
                var action = actions.get(i + j);
                ChatFormatting colour = switch (action.tone()) {
                    case "danger" -> ChatFormatting.RED;
                    case "ok" -> ChatFormatting.GREEN;
                    default -> ChatFormatting.WHITE;
                };
                SharktowerButton button = SharktowerButton.builder(Component.literal(action.label()).withStyle(colour),
                        b -> runCommand(action.command())).bounds(x + 4 + j * (bw + 4), cursor, bw, 20).build();
                resolveButtons.add(button);
                this.addRenderableWidget(button);
            }
            cursor += 22;
        }
    }

    private void runCommand(String command) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        this.minecraft.player.connection.sendCommand(cmd);
    }

    /** The Resolve panel's frame and text; inactive so clicks fall through to the buttons on it. */
    private final class ResolvePanelWidget extends net.minecraft.client.gui.components.AbstractWidget {
        ResolvePanelWidget(int x, int y, int w, int h) {
            super(x, y, w, h, Component.literal("Resolve"));
            this.active = false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            UiDrawing.panel(graphics, getX(), getY(), getWidth(), getHeight());
            UiTheme.drawSectionLabel(graphics, AssignRolesScreen.this.font, "Resolve", getX() + 4, getY() + 5);
            if (!resolvePage.isEmpty()) {
                graphics.text(AssignRolesScreen.this.font, resolvePage,
                        getX() + getWidth() - 20 - AssignRolesScreen.this.font.width(resolvePage), getY() + 5, UiDrawing.MUTED, false);
            }
            var font = AssignRolesScreen.this.font;
            for (ResolveEntry entry : resolveEntries) {
                UiDrawing.textScaledToFit(graphics, font, Component.literal(entry.title()), entry.x(), entry.y(),
                        entry.width(), UiDrawing.GOLD, UiTheme.dark());
                float scale = entry.scale();
                graphics.pose().pushMatrix();
                graphics.pose().translate(entry.x(), entry.y() + 10);
                graphics.pose().scale(scale, scale);
                int ly = 0;
                for (net.minecraft.util.FormattedCharSequence line : entry.body()) {
                    graphics.text(font, line, 0, ly, UiDrawing.TEXT, false);
                    ly += 9;
                }
                graphics.pose().popMatrix();
            }
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        }
    }

    private void renderHandVotingPanel(GuiGraphicsExtractor graphics) {
        if (!ClientState.nominationsOpen && !ClientState.voteInProgress) return;
        if (resolveCoversVote && resolvePanelH > 0) return;

        int x = layoutWidth() - CONTROL_W - MARGIN;
        int h = ClientState.voteInProgress ? 70 : 48;
        int y = votePanelTop();
        UiDrawing.panel(graphics, x, y, CONTROL_W, h);
        drawCenteredAt(graphics, ClientState.voteClockComplete ? "VOTE READY" : "VOTING",
                x + CONTROL_W / 2, y + 6, UiDrawing.GOLD, UiTheme.dark());
        drawCenteredAt(graphics, "Required: " + ClientState.voteThreshold, x + CONTROL_W / 2, y + 19, UiDrawing.TEXT, false);
        drawCenteredAt(graphics, "Raised: " + ClientState.handsRaised, x + CONTROL_W / 2, y + 30,
                ClientState.handsRaised >= ClientState.voteThreshold ? UiTheme.ok() : UiDrawing.TEXT, false);
        if (ClientState.voteInProgress) {
            drawCenteredAt(graphics, "Counted: " + ClientState.effectiveVoteCount, x + CONTROL_W / 2, y + 42, UiDrawing.MUTED, false);
            if (ClientState.voteClockComplete) {
                drawCenteredAt(graphics, "Finish / Override", x + CONTROL_W / 2, y + 54, UiTheme.ok(), UiTheme.dark());
            } else {
                int seat = ClientState.playerSeatNumbers.getOrDefault(ClientState.currentVoteClockPlayer, 0);
                drawCenteredAt(graphics, "Now: " + (seat > 0 ? "Seat " + seat : "ST"), x + CONTROL_W / 2, y + 54, UiDrawing.MUTED, false);
            }
        }
    }

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

    private float grimoireUiScale() {
        if (this.minecraft == null) return 1.0F;
        var window = this.minecraft.getWindow();
        int activeGuiScale = Math.max(1, window.getGuiScale());
        float widthScale = window.getWidth() / (float) GRIMOIRE_REFERENCE_WIDTH;
        float heightScale = window.getHeight() / (float) GRIMOIRE_REFERENCE_HEIGHT;
        float viewportScale = Math.min(widthScale, heightScale);
        viewportScale = Math.max(GRIMOIRE_MIN_VIEWPORT_SCALE,
                Math.min(GRIMOIRE_MAX_VIEWPORT_SCALE, viewportScale));
        float reference = (GRIMOIRE_REFERENCE_GUI_SCALE * viewportScale) / (float) activeGuiScale;
        int count=sortedSeats().size();
        int minWidth=count<10?500:520+Math.min(15,count)*20;
        int minHeight=count<10?288:300+Math.min(15,count)*12;
        return Math.min(reference,Math.min(this.width/(float)minWidth,this.height/(float)minHeight));
    }

    private String fitLabel(String name,int width) {
        if (this.font.width(name) <= width) return name;
        return this.font.plainSubstrByWidth(name,Math.max(0,width-this.font.width("…")))+"…";
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
        MouseButtonEvent mapped = remapMouse(event);

        if (mapped.buttonInfo().button() == SDLMouse.SDL_BUTTON_MIDDLE) {
            for (var child : this.children()) {
                if (child instanceof GrimoireBluffWidget bluff && bluff.isMouseOver(mapped.x(), mapped.y()) && bluff.displayRole() != null) {
                    this.minecraft.gui.setScreen(new CharacterDetailsScreen(bluff.displayRole(), this));
                    return true;
                }
            }
            GrimHit hit = grimHitAt(mapped.x(), mapped.y());
            if (hit != null && hit.assignment() != null) {
                this.minecraft.gui.setScreen(new CharacterDetailsScreen(hit.assignment().getScriptRole(), this));
                return true;
            }
        }
        if (mapped.buttonInfo().button() == SDLMouse.SDL_BUTTON_RIGHT) {
            GrimHit hit = grimHitAt(mapped.x(), mapped.y());
            if (hit != null) {
                GrimoirePlayerClicks.handleRight(
                        hit.playerId(),
                        hit.seat(),
                        hit.assignment(),
                        mapped.hasShiftDown()
                );
                return true;
            }
        }

        return super.mouseClicked(mapped, doubleClick);
    }

    private GrimHit grimHitAt(double mouseX, double mouseY) {
        List<Map.Entry<UUID, Integer>> seats = sortedSeats();
        if (seats.isEmpty()) return null;

        int count = seats.size();
        GrimLayout layout = grimoireLayout(count);
        int centerX = layout.centerX();
        int centerY = layout.centerY();
        int radius = layout.roleRadius();
        int innerRadius = layout.headRadius();

        for (int i = 0; i < count; i++) {
            Map.Entry<UUID, Integer> entry = seats.get(i);
            UUID uuid = entry.getKey();
            int seat = entry.getValue() == null ? i + 1 : entry.getValue();
            PendingRoleAssignment assignment = ClientGrimoireEdits.roleFor(uuid);
            double angle = (Math.PI * 2.0 / count) * i - Math.PI / 2.0;

            int headX = (int) Math.round(centerX + layout.headRadiusX() * Math.cos(angle)) - HEAD_SIZE / 2;
            int headY = (int) Math.round(centerY + innerRadius * Math.sin(angle)) - HEAD_SIZE / 2;
            if (inside(mouseX, mouseY, headX, headY, HEAD_SIZE, HEAD_SIZE)) {
                return new GrimHit(uuid, seat, assignment);
            }

            double tokenCenterX = centerX + layout.roleRadiusX() * Math.cos(angle);
            double tokenCenterY = centerY + radius * Math.sin(angle);

            if (isDeceivedCharacter(assignment)) {
                double tangentX = -Math.sin(angle);
                double tangentY = Math.cos(angle);
                double trueOffset = -(PERCEIVED_ROLE_SIZE + DECEIVED_ROLE_GAP) / 2.0;
                double perceivedOffset = (ROLE_SIZE + DECEIVED_ROLE_GAP) / 2.0;

                int roleX = (int) Math.round(tokenCenterX + tangentX * trueOffset) - ROLE_SIZE / 2;
                int roleY = (int) Math.round(tokenCenterY + tangentY * trueOffset) - ROLE_SIZE / 2;
                if (inside(mouseX, mouseY, roleX, roleY, ROLE_SIZE, ROLE_SIZE)) {
                    return new GrimHit(uuid, seat, assignment);
                }

                int perceivedX = (int) Math.round(tokenCenterX + tangentX * perceivedOffset)
                        - PERCEIVED_ROLE_SIZE / 2;
                int perceivedY = (int) Math.round(tokenCenterY + tangentY * perceivedOffset)
                        - PERCEIVED_ROLE_SIZE / 2;
                if (inside(mouseX, mouseY, perceivedX, perceivedY,
                        PERCEIVED_ROLE_SIZE, PERCEIVED_ROLE_SIZE)) {
                    return new GrimHit(uuid, seat, ClientGrimoireEdits.perceivedRoleFor(uuid));
                }
            } else {
                int roleX = (int) Math.round(tokenCenterX) - ROLE_SIZE / 2;
                int roleY = (int) Math.round(tokenCenterY) - ROLE_SIZE / 2;
                if (inside(mouseX, mouseY, roleX, roleY, ROLE_SIZE, ROLE_SIZE)) {
                    return new GrimHit(uuid, seat, assignment);
                }
            }
        }

        return null;
    }

    private static boolean inside(
            double mouseX, double mouseY,
            int x, int y, int width, int height
    ) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
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
        signature.append(ClientState.phase()).append('|')
                .append(new java.util.TreeSet<>(ClientState.storytellerPlayers)).append('|')
                .append(new java.util.TreeSet<>(ClientState.pendingDeaths)).append('|')
                .append(ClientGrimoireEdits.visibleDemonBluffs()).append('|')
                .append(ClientLunaticBluffs.current()).append('|');
        for (var entry : sortedSeats()) {
            var id = entry.getKey();
            signature.append(ClientGrimoireEdits.roleFor(id)).append(ClientGrimoireEdits.perceivedRoleFor(id))
                    .append(ClientGrimoireEdits.remindersFor(id));
        }
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
