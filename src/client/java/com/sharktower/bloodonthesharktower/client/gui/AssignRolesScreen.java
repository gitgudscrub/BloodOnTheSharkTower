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
import net.minecraft.client.gui.components.Button;
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
        int baseCenterY = layoutHeight() / 2;
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
                || lunaticRevision != lastLunaticBluffRevision) && this.minecraft != null) {
            clearWidgets();
            buildContextualControls();
            buildBluffVisibilityControl();
            buildPlayerWidgets();
            buildReminderWidgets();
            buildStorytellerWidgets();
            buildUnseatedWidgets();
            buildBluffWidgets();
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

        this.addRenderableWidget(Button.builder(
                        Component.literal("Game End").withStyle(ChatFormatting.RED), b ->
                                this.minecraft.gui.setScreen(new EndGameControlScreen(this)))
                .bounds(MARGIN, 48, CONTROL_W, CONTROL_H).build());

        y = addRightAction(rightX, y, "Spectator", ChatFormatting.GRAY, "st_spectator");
        if (phase != GamePhase.SETUP && phase != GamePhase.NIGHT && !ClientState.pendingDeaths.isEmpty()) {
            y = addRightAction(rightX, y, "Reveal All Deaths", ChatFormatting.RED, "reveal_deaths");
        }

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
            addRightAction(rightX, y, "Send to Home", ChatFormatting.AQUA, "send_home");
        } else if (phase == GamePhase.NIGHT) {
            y = addRightScreen(rightX, y, "Minion Info", ChatFormatting.RED,
                    () -> this.minecraft.gui.setScreen(new NightTeamInfoScreen(false)));
            y = addRightScreen(rightX, y, "Demon Info", ChatFormatting.RED,
                    () -> this.minecraft.gui.setScreen(new NightTeamInfoScreen(true)));
            y = addRightAction(rightX, y, "Send to Seats", ChatFormatting.LIGHT_PURPLE, "send_to_seats");
            y = addRightAction(rightX, y, "Send to Home", ChatFormatting.AQUA, "send_home");
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

            int headX = (int) Math.round(centerX + layout.headRadiusX() * Math.cos(angle)) - HEAD_SIZE / 2;
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

            // Extra slots give true/believed pairs room without covering either token.
            for (int step=-2; step<=2; step++) {
                candidates.add(new int[]{roleX + step*16,roleY-32});
                candidates.add(new int[]{roleX + step*16,roleY+ROLE_SIZE+18});
                candidates.add(new int[]{roleX-32,roleY+step*16});
                candidates.add(new int[]{roleX+ROLE_SIZE+18,roleY+step*16});
            }
            candidates.sort(Comparator.comparingDouble((int[] p) ->
                    reminderPlacementScore(p,roleCenterX,roleCenterY,outwardX,outwardY,headX,headY)
                    - 0.08*(Math.pow(p[0]+REMINDER_SIZE/2.0-roleCenterX,2)+Math.pow(p[1]+REMINDER_SIZE/2.0-roleCenterY,2))).reversed());
            List<Reminder> reminders = ClientGrimoireEdits.remindersFor(uuid);
            int index=0;
            for (int[] position:candidates) {
                if (index>=Math.min(8,reminders.size())) break;
                int[] area={position[0]-1,position[1]-1,REMINDER_SIZE+2,REMINDER_SIZE+2};
                if (area[0]<120 || area[0]+area[2]>layoutWidth()-110 || area[1]<44 || area[1]+area[3]>layoutHeight()-44) continue;
                boolean collision=false;
                for (var child:this.children()) {
                    if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                            && overlaps(area,new int[]{widget.getX()-1,widget.getY()-1,widget.getWidth()+2,widget.getHeight()+2})) {collision=true;break;}
                }
                if (collision) continue;
                this.addRenderableWidget(new GrimoireReminderWidget(position[0],position[1],REMINDER_SIZE,uuid,seat,reminders.get(index++)));
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
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                        ClientStorytellerActions.send("seat_player", id.toString());
                    }).bounds(x, y + (i - start) * 24, CONTROL_W, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(name))).build());
        }
        if (pages > 1) {
            int navY = y + rows * 24;
            this.addRenderableWidget(Button.builder(Component.literal("<"), b -> { unseatedPage=Math.max(0,unseatedPage-1);rebuildWidgets(); })
                    .bounds(x,navY,42,20).build());
            this.addRenderableWidget(Button.builder(Component.literal(">"), b -> { unseatedPage=Math.min(pages-1,unseatedPage+1);rebuildWidgets(); })
                    .bounds(x+48,navY,42,20).build());
        }
    }

    private boolean shouldShowBluffs() {
        return ClientGrimoireEdits.isLocalStoryteller() ? showBluffs : showPlayerBluffs;
    }

    private void buildBluffVisibilityControl() {
        if (!ClientGrimoireEdits.isLocalStoryteller() && ClientGrimoireEdits.visibleDemonBluffs().isEmpty()) return;
        this.addRenderableWidget(Button.builder(
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
            // Use real widget bounds when placing labels, including believed roles/reminders.
            int nameWidth = 96;
            var notedRole = UiDrawing.roleOf(ClientGrimoireEdits.roleFor(uuid));
            for (int width = nameWidth; width >= 30; width -= 12) {
                String label = fitLabel(name,width);
                int[] area = nameArea(label,headX,headY,angle,labelAreas);
                if (area == null) continue;
                labelAreas.add(area);
                graphics.fill(area[0],area[1],area[0]+area[2],area[1]+area[3],0xB015151A);
                graphics.text(this.font,label,area[0]+3,area[1]+2,
                        notedRole == null ? UiDrawing.TEXT : UiDrawing.teamColor(notedRole.getTeam()),true);
                break;
            }
            if (dead) UiDrawing.deathShroud(graphics, headX, headY, HEAD_SIZE);

            boolean speaking = ClientState.handRaiseMode() == com.sharktower.bloodonthesharktower.core.HandRaiseMode.SPEAKING;
            boolean voting = ClientState.handRaiseMode() == com.sharktower.bloodonthesharktower.core.HandRaiseMode.VOTING;
            if (speaking && ClientState.attentionHands.containsKey(uuid) || voting && ClientState.isHandRaised(uuid)) {
                drawRaisedHand(graphics, headX + HEAD_SIZE + 3, headY - 16);
                if (speaking)
                    graphics.text(this.font, Integer.toString(ClientState.attentionHands.getOrDefault(uuid, 0)),
                            headX + HEAD_SIZE + 14, headY - 16, UiDrawing.GOLD, true);
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
                graphics.fill(sx - 7, sy - 7, sx + 7, sy + 7, 0xE015151A);
                graphics.outline(sx - 7, sy - 7, 14, 14, UiDrawing.BORDER);
                drawCenteredAt(graphics, Integer.toString(seat), sx, sy - 4, UiDrawing.GOLD, true);
            } finally {
                GrimoireRevealAnimation.endElement(graphics);
            }
        }
    }

    private void renderCenterStatus(GuiGraphicsExtractor graphics, int playerCount) {
        if (ClientState.phase() != GamePhase.SETUP) return;

        String players = "Players: " + playerCount;
        String storytellers = "Storytellers: " + ClientState.storytellerPlayers.size();
        int baseY = layoutHeight() / 2 + 16;
        drawCentered(graphics, players, baseY, UiDrawing.TEXT, true);
        drawCentered(graphics, storytellers, baseY + 12, UiDrawing.MUTED, true);
    }

    private void renderHoverHint(GuiGraphicsExtractor graphics) {
        String hint = GrimoireHoverHints.current();
        UUID selected = GrimoireInteractionState.selectedNominator();
        if ((hint == null || hint.isBlank()) && selected != null && ClientState.nominationsOpen) {
            int seat = ClientState.playerSeatNumbers.getOrDefault(selected, 0);
            hint = "Nominator: " + ClientState.playerName(selected, seat) + " — Shift+RMB a nominee";
        }

        if (hint == null || hint.isBlank()) return;
        int hintWidth = Math.max(80, layoutWidth() - 320);
        int hintLeft = (layoutWidth() - hintWidth) / 2;
        var lines = this.font.split(Component.literal(hint), hintWidth);
        for (int i=0; i<Math.min(2,lines.size()); i++)
            graphics.text(this.font, lines.get(i), hintLeft, layoutHeight() - 29 + i*10, UiDrawing.MUTED, false);
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
            graphics.text(this.font, label, x + 35, y + 12, UiDrawing.GOLD, true);
        } else {
            graphics.text(this.font, label,
                    x + (panelWidth - this.font.width(label)) / 2, y + 12, UiDrawing.GOLD, true);
        }
    }

    private void renderHandVotingPanel(GuiGraphicsExtractor graphics) {
        if (!ClientState.nominationsOpen && !ClientState.voteInProgress) return;

        int x = layoutWidth() - CONTROL_W - MARGIN;
        int h = ClientState.voteInProgress ? 70 : 48;
        int railBottom=10;
        for (var child:this.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                    && widget.getX()>=x && widget.getY()<layoutHeight()-40)
                railBottom=Math.max(railBottom,widget.getY()+widget.getHeight());
        }
        int y=Math.max(railBottom+10,Math.min(225,layoutHeight()-h-40));
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

    private final java.util.Set<UUID> submittedDeathReveals = new java.util.HashSet<>();

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
            if (hit != null && PendingDeathScreen.available(hit.playerId())) {
                // Consume repeat clicks while the authoritative reveal is in flight.
                if (submittedDeathReveals.add(hit.playerId())) {
                    GrimoireReturnState.requestAfterNextGrimoireSync();
                    ClientStorytellerActions.send("reveal_death", Integer.toString(hit.seat()));
                }
                return true;
            }
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
