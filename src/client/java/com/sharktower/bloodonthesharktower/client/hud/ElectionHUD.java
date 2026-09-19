package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.client.event.KeyInputHandler;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.UUID;

/** Player-facing nomination, clockwise vote and block HUD. */
public final class ElectionHUD {
    private ElectionHUD() {}

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!ClientState.isHudEnabled || minecraft.player == null) return;
        boolean hasLastResult = ClientState.lastVoteNominee != null && !"NONE".equals(ClientState.lastVoteResult);
        String privateDayZone = privateDayZone();
        if (!ClientState.nominationsOpen && !ClientState.voteInProgress && ClientState.currentNominee == null
                && ClientState.markedForExecution == null && ClientState.storytellerMFE == null
                && !ClientState.exileSupportInProgress && ClientState.currentExileTarget == null
                && !hasLastResult) {
            if (privateDayZone != null) renderBottomStatus(graphics, minecraft,
                    "PRIVATE CHAT: " + prettyZoneName(privateDayZone));
            return;
        }

        // A plain "nominations are open" state should not cover the Storyteller's
        // phase/night-order bar at the top of the screen. Keep that persistent
        // state as a small green status line above the hotbar instead. As soon
        // as an actual nomination, vote, exile, block or result needs detail,
        // the full election panel returns.
        boolean nominationsOnly = ClientState.nominationsOpen
                && !ClientState.voteInProgress
                && ClientState.currentNominee == null
                && ClientState.markedForExecution == null
                && ClientState.storytellerMFE == null
                && !ClientState.exileSupportInProgress
                && ClientState.currentExileTarget == null
                && !hasLastResult;
        if (nominationsOnly) {
            renderBottomStatus(graphics, minecraft, "NOMINATIONS OPEN");
            return;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int width = 268;
        int x = (screenWidth - width) / 2;
        int y = 8;
        boolean exileActive = ClientState.currentExileTarget != null;
        boolean electionRunning = ClientState.voteInProgress || ClientState.exileSupportVote;
        int height = electionRunning ? 82 : ((ClientState.currentNominee != null || exileActive) ? 68 : 54);
        UiDrawing.panel(graphics, x, y, width, height);

        String headline;
        if (ClientState.exileSupportInProgress) headline = ClientState.voteClockComplete ? "EXILE CLOCK COMPLETE" : "EXILE VOTE";
        else if (exileActive) headline = "TRAVELLER EXILE";
        else if (ClientState.voteInProgress) headline = ClientState.voteClockComplete ? "VOTE CLOCK COMPLETE" : "VOTING";
        else if (ClientState.currentNominee != null) headline = "NOMINATION";
        else if (ClientState.markedForExecution != null || ClientState.storytellerMFE != null) headline = "ON THE BLOCK";
        else if (hasLastResult) headline = "VOTE RESULT";
        else headline = "NOMINATIONS OPEN";
        graphics.text(minecraft.font, headline, x + 8, y + 7, UiDrawing.GOLD, true);

        if (ClientState.exileSupportInProgress) {
            String exile = label(ClientState.currentExileCaller) + " → " + label(ClientState.currentExileTarget);
            graphics.text(minecraft.font, exile, x + 8, y + 22, UiDrawing.TEXT, true);
            int lineY = y + 36;
            if (ClientState.voteClockComplete) {
                String counted = "Support: " + ClientState.effectiveVoteCount + "   Required: " + ClientState.voteThreshold;
                if (ClientState.voteCountOverrideActive) counted += "   [ST override]";
                graphics.text(minecraft.font, counted, x + 8, lineY, 0xFF55CC66, true);
                graphics.text(minecraft.font, "Waiting for Storyteller to resolve the exile.", x + 8, lineY + 13, UiDrawing.MUTED, false);
            } else {
                int shownIndex = Math.min(ClientState.voteClockIndex + 1, Math.max(1, ClientState.voteClockTotal));
                graphics.text(minecraft.font,
                        "Now counting: " + label(ClientState.currentVoteClockPlayer)
                                + "   (" + shownIndex + "/" + ClientState.voteClockTotal + ")",
                        x + 8, lineY, UiDrawing.TEXT, true);
                graphics.text(minecraft.font,
                        "Support: " + ClientState.effectiveVoteCount + "   Required: " + ClientState.voteThreshold
                                + "   Hands up: " + ClientState.handsRaised,
                        x + 8, lineY + 13, UiDrawing.MUTED, false);
            }
            return;
        }

        if (exileActive) {
            String key = KeyInputHandler.toggleVoteHandKey == null
                    ? "U" : KeyInputHandler.toggleVoteHandKey.getTranslatedKeyMessage().getString();
            graphics.text(minecraft.font,
                    label(ClientState.currentExileCaller) + " calls to exile " + label(ClientState.currentExileTarget),
                    x + 8, y + 22, UiDrawing.TEXT, true);
            graphics.text(minecraft.font,
                    "Required: " + ClientState.voteThreshold + "   Hands raised: " + ClientState.handsRaised,
                    x + 8, y + 38, UiDrawing.TEXT, false);
            graphics.text(minecraft.font, "[" + key + "] Raise / lower exile support before the clock starts.",
                    x + 8, y + 51, UiDrawing.MUTED, false);
            return;
        }

        if (ClientState.currentNominee != null) {
            String nomination = label(ClientState.currentNominator) + " → " + label(ClientState.currentNominee);
            graphics.text(minecraft.font, nomination, x + 8, y + 22, UiDrawing.TEXT, true);
        }

        if (ClientState.voteInProgress) {
            int lineY = y + 36;
            if (ClientState.voteClockComplete) {
                String counted = "Counted: " + ClientState.effectiveVoteCount + "   Required: " + ClientState.voteThreshold;
                if (ClientState.voteCountOverrideActive) counted += "   [ST override]";
                graphics.text(minecraft.font, counted, x + 8, lineY, 0xFF55CC66, true);
                graphics.text(minecraft.font, "Waiting for Storyteller to finish the vote.", x + 8, lineY + 13, UiDrawing.MUTED, false);
            } else {
                int shownIndex = Math.min(ClientState.voteClockIndex + 1, Math.max(1, ClientState.voteClockTotal));
                String voter = "Now counting: " + label(ClientState.currentVoteClockPlayer)
                        + "   (" + shownIndex + "/" + ClientState.voteClockTotal + ")";
                graphics.text(minecraft.font, voter, x + 8, lineY, UiDrawing.TEXT, true);
                graphics.text(minecraft.font,
                        "Locked: " + ClientState.effectiveVoteCount + "   Required: " + ClientState.voteThreshold
                                + "   Hands up: " + ClientState.handsRaised,
                        x + 8, lineY + 13, UiDrawing.MUTED, false);
            }
            return;
        }

        if (ClientState.currentNominee != null) {
            String key = KeyInputHandler.toggleVoteHandKey == null
                    ? "U" : KeyInputHandler.toggleVoteHandKey.getTranslatedKeyMessage().getString();
            graphics.text(minecraft.font,
                    "Required: " + ClientState.voteThreshold + "   Hands raised: " + ClientState.handsRaised,
                    x + 8, y + 38, UiDrawing.TEXT, false);
            graphics.text(minecraft.font, "[" + key + "] Raise / lower your hand before the clock starts.",
                    x + 8, y + 51, UiDrawing.MUTED, false);
            return;
        }

        if (ClientState.markedForExecution != null || ClientState.storytellerMFE != null) {
            UUID marked = ClientState.storytellerMFE != null ? ClientState.storytellerMFE : ClientState.markedForExecution;
            int high = ClientState.storytellerMFE != null ? ClientState.storytellerMFEVotes : ClientState.votesForMarkedPlayer;
            graphics.text(minecraft.font, label(marked) + " — " + high + " vote(s)", x + 8, y + 24, UiDrawing.TEXT, true);
            return;
        }

        if (hasLastResult) {
            String result = prettyResult(ClientState.lastVoteResult);
            graphics.text(minecraft.font,
                    label(ClientState.lastVoteNominee) + " — " + ClientState.lastVoteCount + " vote(s) — " + result,
                    x + 8, y + 24, UiDrawing.TEXT, true);
            return;
        }

        graphics.text(minecraft.font, "Players may nominate.", x + 8, y + 24, UiDrawing.TEXT, false);
    }

    private static void renderBottomStatus(GuiGraphicsExtractor graphics, Minecraft minecraft, String text) {
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int x = (screenWidth - minecraft.font.width(text)) / 2;

        // Shared status slot above the hotbar/compass. Before nominations it
        // identifies the local player's automatic private-chat area; once
        // nominations open, private zones are disabled and this same slot shows
        // NOMINATIONS OPEN instead.
        int y = screenHeight - 82;
        graphics.text(minecraft.font, text, x, y, 0xFF55FF55, true);
    }

    private static String privateDayZone() {
        String route = ClientState.voiceRoute;
        if (route == null || !route.startsWith("DAY_ZONE:")) return null;
        String zone = route.substring("DAY_ZONE:".length());
        return zone.isBlank() ? null : zone;
    }

    private static String prettyZoneName(String raw) {
        String[] words = raw.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) result.append(word.substring(1));
        }
        return result.isEmpty() ? raw : result.toString();
    }

    private static String label(UUID id) {
        if (id == null) return "Unknown";
        int seat = ClientState.playerSeatNumbers.getOrDefault(id, 0);
        return ClientState.playerName(id, seat);
    }

    private static String prettyResult(String raw) {
        if (raw == null) return "";
        return switch (raw) {
            case "MARKED" -> "ON THE BLOCK";
            case "TIE" -> "TIE — BLOCK CLEARED";
            case "NOT_ENOUGH" -> "NOT ENOUGH";
            default -> raw.replace('_', ' ');
        };
    }
}
