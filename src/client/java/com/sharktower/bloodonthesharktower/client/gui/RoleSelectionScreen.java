package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Compact role chooser used by player-token and bluff-slot clicks. */
public final class RoleSelectionScreen extends Screen {
    private enum Mode { PLAYER, PERCEIVED, BLUFF }

    private static final int PAGE_SIZE = 24;
    private final Mode mode;
    private final int seat;
    private final int bluffIndex;
    private final Screen returnScreen;
    private int page;

    private RoleSelectionScreen(Mode mode, int seat, int bluffIndex, Screen returnScreen) {
        this(mode, seat, bluffIndex, returnScreen, 0);
    }

    private RoleSelectionScreen(Mode mode, int seat, int bluffIndex, Screen returnScreen, int page) {
        super(Component.literal(switch (mode) {
            case BLUFF -> "Choose Demon Bluff";
            case PERCEIVED -> "Choose Believed Role";
            default -> "Choose Role";
        }));
        this.mode = mode;
        this.seat = seat;
        this.bluffIndex = bluffIndex;
        this.returnScreen = returnScreen;
        this.page = Math.max(0, page);
    }

    public static RoleSelectionScreen forSeat(int seat) {
        return forSeat(seat, new AssignRolesScreen());
    }

    public static RoleSelectionScreen forSeat(int seat, Screen returnScreen) {
        return new RoleSelectionScreen(Mode.PLAYER, seat, -1, returnScreen);
    }

    public static RoleSelectionScreen forPerceivedRole(int seat, Screen returnScreen) {
        return new RoleSelectionScreen(Mode.PERCEIVED, seat, -1, returnScreen);
    }

    public static RoleSelectionScreen forBluff(int index) {
        return forBluff(index, new AssignRolesScreen());
    }

    public static RoleSelectionScreen forBluff(int index, Screen returnScreen) {
        return new RoleSelectionScreen(Mode.BLUFF, -1, index, returnScreen);
    }

    @Override
    protected void init() {
        List<ScriptRole> roles = roles();
        int maxPage = Math.max(0, (roles.size() - 1) / PAGE_SIZE);
        if (page > maxPage) page = maxPage;

        int start = page * PAGE_SIZE;
        int end = Math.min(roles.size(), start + PAGE_SIZE);
        int columns = 4;
        int width = 122;
        int height = 20;
        int gapX = 6;
        int gapY = 5;
        int gridWidth = columns * width + (columns - 1) * gapX;
        int left = (this.width - gridWidth) / 2;
        int top = 44;

        for (int index = start; index < end; index++) {
            ScriptRole role = roles.get(index);
            int local = index - start;
            int col = local % columns;
            int row = local / columns;
            Component label = Component.literal(role.getDisplayName()).withStyle(colour(role.getTeam()));
            this.addRenderableWidget(Button.builder(label, b -> choose(role))
                    .bounds(left + col * (width + gapX), top + row * (height + gapY), width, height)
                    .build());
        }

        int navY = this.height - 52;
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (page > 0) this.minecraft.gui.setScreen(new RoleSelectionScreen(mode, seat, bluffIndex, returnScreen, page - 1));
        }).bounds(this.width / 2 - 76, navY, 40, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (page < maxPage) this.minecraft.gui.setScreen(new RoleSelectionScreen(mode, seat, bluffIndex, returnScreen, page + 1));
        }).bounds(this.width / 2 + 36, navY, 40, 20).build());
        if (mode == Mode.PERCEIVED) {
            this.addRenderableWidget(Button.builder(Component.literal("Clear Believed Role"), b -> {
                        java.util.UUID target = ClientGrimoireEdits.playerAtSeat(seat);
                        if (ClientGrimoireEdits.isLocalStoryteller()) {
                            ClientStorytellerActions.send("clear_perceived_role", Integer.toString(seat));
                        } else {
                            ClientGrimoireEdits.clearPerceivedRole(target);
                        }
                        returnToGrimoire();
                    })
                    .bounds(this.width / 2 - 125, this.height - 27, 120, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> returnToGrimoire())
                    .bounds(this.width / 2 + 5, this.height - 27, 100, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> returnToGrimoire())
                    .bounds(this.width / 2 - 50, this.height - 27, 100, 20).build());
        }
    }

    private List<ScriptRole> roles() {
        if (ClientState.currentScript == null) return List.of();
        List<ScriptRole> roles = new ArrayList<>(ClientState.currentScript.allRoles());
        if (mode == Mode.PERCEIVED) {
            java.util.UUID playerId = ClientGrimoireEdits.playerAtSeat(seat);
            var actual = playerId == null ? null : ClientGrimoireEdits.roleFor(playerId);
            Role actualRole = actual == null || actual.isCustomRole() ? Role.NO_ROLE : actual.role();
            roles.removeIf(role -> {
                if (actualRole == Role.DRUNK) return role.getTeam() != RoleType.TOWNSFOLK;
                if (actualRole == Role.MARIONETTE) {
                    return role.getTeam() != RoleType.TOWNSFOLK && role.getTeam() != RoleType.OUTSIDER;
                }
                return true;
            });
        } else {
            roles.removeIf(role -> switch (role.getTeam()) {
                case NONE, FABLED, LORIC -> true;
                case MINION, DEMON, TRAVELER -> mode == Mode.BLUFF;
                default -> false;
            });
        }
        roles.sort(Comparator
                .comparingInt((ScriptRole role) -> role.getTeam().ordinal())
                .thenComparing(ScriptRole::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return roles;
    }

    private void choose(ScriptRole role) {
        if (mode == Mode.PLAYER) {
            if (ClientGrimoireEdits.isLocalStoryteller()) {
                // Storyteller edits are authoritative and are pushed to players
                // only when SEND ROLES is used.
                ClientStorytellerActions.send("assign_role", seat + "|" + role.getId());
            } else {
                // Ordinary players use the Grimoire as a private notebook.
                // Never send their guesses through the Storyteller action channel.
                java.util.UUID target = ClientGrimoireEdits.playerAtSeat(seat);
                ClientGrimoireEdits.assignRole(target, role);
            }
        } else if (mode == Mode.PERCEIVED) {
            java.util.UUID target = ClientGrimoireEdits.playerAtSeat(seat);
            if (ClientGrimoireEdits.isLocalStoryteller()) {
                ClientStorytellerActions.send("assign_perceived_role", seat + "|" + role.getId());
            } else {
                ClientGrimoireEdits.assignPerceivedRole(target, role);
            }
        } else if (ClientGrimoireEdits.isLocalStoryteller()) {
            // Demon-bluff setup is Storyteller-owned state.
            ClientStorytellerActions.send("set_bluff", bluffIndex + "|" + role.getId());
        }

        // Role selection is a Grimoire sub-flow, not a terminal screen.
        returnToGrimoire();
    }

    private void returnToGrimoire() {
        if (this.minecraft == null || this.minecraft.gui == null) return;
        this.minecraft.gui.setScreen(returnScreen != null ? returnScreen : new AssignRolesScreen());
    }

    @Override
    public void onClose() {
        // Escape behaves like Back here so the Storyteller never falls all the
        // way out to the world while assigning roles.
        returnToGrimoire();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        List<ScriptRole> roles = roles();
        int maxPage = Math.max(0, (roles.size() - 1) / PAGE_SIZE);
        String heading = switch (mode) {
            case PLAYER -> "Assign role to seat " + seat;
            case PERCEIVED -> "Choose what seat " + seat + " believes they are";
            case BLUFF -> "Choose Demon bluff";
        };
        graphics.text(this.font, heading, (this.width - this.font.width(heading)) / 2, 17, UiDrawing.GOLD, true);
        String pageText = (page + 1) + "/" + (maxPage + 1) + "   " + roles.size() + " roles";
        graphics.text(this.font, pageText, (this.width - this.font.width(pageText)) / 2, 30, UiDrawing.MUTED, false);
    }

    private static ChatFormatting colour(RoleType type) {
        return switch (type) {
            case TOWNSFOLK, OUTSIDER -> ChatFormatting.AQUA;
            case MINION, DEMON -> ChatFormatting.RED;
            case TRAVELER -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }
}
