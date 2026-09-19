package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.core.GamePhase;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Original-BOTB-inspired Storyteller visit card.
 *
 * It appears when the Storyteller activates a role visit from the night-order
 * bar and keeps the role instructions, objective info and droison reminder in
 * view while the Storyteller is at that player's house.
 *
 * The card is client-side and uses the Storyteller's already-synchronised Grim,
 * so no hidden role data is sent to ordinary players.
 */
public final class NightVisitInfoHUD {
    private static UUID visitedPlayer;
    private static PendingRoleAssignment visitAssignment;
    private static boolean triggered;
    private static UUID triggerSourcePlayer;

    private static final int OUTER = 0xB84C7582;
    private static final int INNER = 0xD9142229;
    private static final int BORDER = 0xFFD1ECF4;
    private static final int CYAN = 0xFF55FFFF;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int GREY = 0xFFB8B8B8;

    private NightVisitInfoHUD() {}

    public static void showVisit(UUID playerId, PendingRoleAssignment assignment, boolean isTriggered, UUID sourcePlayerId) {
        visitedPlayer = playerId;
        visitAssignment = assignment;
        triggered = isTriggered;
        triggerSourcePlayer = sourcePlayerId;
    }

    public static void clear() {
        visitedPlayer = null;
        visitAssignment = null;
        triggered = false;
        triggerSourcePlayer = null;
    }

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (visitedPlayer == null || visitAssignment == null || minecraft == null || minecraft.player == null) return;
        if (!ClientState.isHudEnabled || ClientState.gameEnding || ClientState.phase() != GamePhase.NIGHT) {
            clear();
            return;
        }
        if (!ClientState.storytellerPlayers.contains(minecraft.player.getUUID())) {
            clear();
            return;
        }
        if (minecraft.gui.screen() != null) return;

        ScriptRole visitRole = visitAssignment.getScriptRole();
        if (visitRole == null) return;

        boolean firstNight = ClientState.currentNight <= 1 && ClientState.currentDay == 0;
        String instructions = instructionText(visitAssignment, firstNight, triggered);
        String info = automaticInfo(visitedPlayer, visitAssignment, triggerSourcePlayer);

        PendingRoleAssignment actualTargetAssignment = ClientState.grimoireRoles.get(visitedPlayer);
        boolean drunk = isDrunk(visitedPlayer, actualTargetAssignment);
        boolean poisoned = hasReminder(visitedPlayer, "Poisoned");
        boolean droisoned = drunk || poisoned;
        boolean vortoxInPlay = isVortoxInPlay();

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int width = Math.min(390, Math.max(280, screenWidth - 24));
        int x = 12;
        int y = 56;
        int iconSize = 58;
        int textX = x + 82;
        int textWidth = Math.max(120, width - 94);

        List<String> instructionLines = wrap(minecraft, instructions, textWidth, 5);
        List<String> infoLines = info == null || info.isBlank()
                ? List.of()
                : wrap(minecraft, info, textWidth, 2);

        int innerTextHeight = 28 + instructionLines.size() * 11
                + (infoLines.isEmpty() ? 0 : 5 + infoLines.size() * 11)
                + 5 + 11
                + (vortoxInPlay ? 11 : 0);
        int height = Math.max(96, 18 + Math.max(iconSize, innerTextHeight) + 12);

        graphics.fill(x, y, x + width, y + height, OUTER);
        graphics.outline(x, y, width, height, BORDER);
        graphics.fill(textX - 5, y + 10, x + width - 10, y + height - 10, INNER);

        UiDrawing.roleToken(graphics, visitRole, x + 12, y + 13, iconSize);

        int teamColour = UiDrawing.opaque(UiDrawing.teamColor(visitRole.getTeam()));
        String roleName = visitRole.getDisplayName();
        graphics.text(minecraft.font, roleName, textX, y + 12, teamColour, true);

        int seat = ClientState.grimoireSeatNumbers.getOrDefault(visitedPlayer,
                ClientState.playerSeatNumbers.getOrDefault(visitedPlayer, 0));
        String playerName = ClientState.playerName(visitedPlayer, seat);
        graphics.text(minecraft.font, playerName, textX, y + 23, YELLOW, true);

        int lineY = y + 36;
        for (String line : instructionLines) {
            graphics.text(minecraft.font, line, textX, lineY, UiDrawing.TEXT, false);
            lineY += 11;
        }

        if (!infoLines.isEmpty()) {
            lineY += 2;
            for (String line : infoLines) {
                graphics.text(minecraft.font, line, textX, lineY, RED, true);
                lineY += 11;
            }
        }

        lineY += 3;
        String status = "Droisoned: " + (droisoned ? "YES" : "NO");
        graphics.text(minecraft.font, status, textX, lineY,
                droisoned ? RED : GREEN, true);

        if (vortoxInPlay) {
            lineY += 11;
            graphics.text(minecraft.font, "Vortox: YES", textX, lineY, RED, true);
        }
    }

    private static boolean isVortoxInPlay() {
        for (PendingRoleAssignment assignment : ClientState.grimoireRoles.values()) {
            if (assignment != null && !assignment.isCustomRole() && assignment.role() == Role.VORTOX) {
                return true;
            }
        }
        return false;
    }

    private static String instructionText(PendingRoleAssignment assignment, boolean firstNight, boolean triggeredVisit) {
        if (assignment == null) return "";
        if (assignment.isCustomRole()) {
            var custom = assignment.customRole().orElse(null);
            if (custom == null) return assignment.getDisplayName();
            String text = (firstNight && !triggeredVisit)
                    ? custom.firstNightReminder()
                    : custom.otherNightReminder();
            if (text == null || text.isBlank()) text = custom.ability();
            return text == null ? "" : text;
        }

        Role role = assignment.role();
        if (role == null || role == Role.NO_ROLE) return assignment.getDisplayName();
        String suffix = (firstNight && !triggeredVisit) ? "first" : "other";
        String key = "nightorder." + BloodOnTheSharktower.MOD_ID + "." + role.getId() + "." + suffix;
        String translated = Component.translatable(key).getString();
        if (translated == null || translated.isBlank() || translated.equals(key)) {
            translated = role.getDescription();
        }
        return translated == null ? "" : translated;
    }

    /**
     * Mirrors the original mod's "tell the ST the true value" idea for the
     * objective info that can be derived safely from the current Grim.
     * Storyteller-choice information is only shown when Sharktower has an
     * unambiguous marker for it (e.g. Red Herring / Grandchild).
     */
    private static String automaticInfo(UUID target, PendingRoleAssignment visit, UUID sourcePlayer) {
        if (visit == null || visit.isCustomRole()) return triggeredInfo(sourcePlayer);
        Role role = visit.role();
        if (role == null) return triggeredInfo(sourcePlayer);

        String info = switch (role) {
            case WASHERWOMAN -> firstNightPairInfo(Role.WASHERWOMAN, "Townsfolk", RoleType.TOWNSFOLK);
            case LIBRARIAN -> librarianInfo();
            case INVESTIGATOR -> firstNightPairInfo(Role.INVESTIGATOR, "Minion", RoleType.MINION);
            case CHEF -> "Evil pairs: " + chefPairs();
            case EMPATH -> {
                Integer count = empathCount(target);
                yield count == null ? null : "Evil neighbors: " + count;
            }
            case FORTUNE_TELLER -> {
                UUID redHerring = firstPlayerWithReminder("Red Herring");
                yield redHerring == null ? null : "Red Herring: " + playerLabel(redHerring);
            }
            case UNDERTAKER -> ClientState.lastExecutedRoleName == null || ClientState.lastExecutedRoleName.isBlank()
                    ? "No player died by execution today"
                    : "Executed: " + ClientState.lastExecutedRoleName;
            case GRANDMOTHER -> grandmotherInfo();
            case CLOCKMAKER -> {
                Integer distance = clockmakerDistance();
                yield distance == null ? null : "Distance: " + distance;
            }
            case FLOWERGIRL -> "Demon voted: " + (ClientState.demonVotedToday ? "YES" : "NO");
            case TOWN_CRIER -> "Minion nominated: " + (ClientState.minionNominatedToday ? "YES" : "NO");
            case NOBLE -> nobleInfo();
            case STEWARD -> stewardInfo();
            case KNIGHT -> knightInfo();
            case SHUGENJA -> {
                String direction = shugenjaDirection(target);
                yield direction == null ? null : "Closest evil: " + direction;
            }
            case ORACLE -> "Dead evil: " + deadEvilCount();
            case GODFATHER -> godfatherInfo();
            default -> null;
        };

        if (info != null && !info.isBlank()) return info;
        return triggeredInfo(sourcePlayer);
    }

    private static String triggeredInfo(UUID sourcePlayer) {
        if (!triggered || sourcePlayer == null || sourcePlayer.equals(visitedPlayer)) return null;
        PendingRoleAssignment source = ClientState.grimoireRoles.get(sourcePlayer);
        String sourceName = playerLabel(sourcePlayer);
        if (source == null) return "Triggered by: " + sourceName;
        return "Triggered by: " + sourceName + " (" + source.getDisplayName() + ")";
    }

    private static String firstNightPairInfo(Role sourceRole, String primaryMarker, RoleType expectedType) {
        List<UUID> marked = playersWithRoleReminder(sourceRole);
        UUID real = firstPlayerWithRoleReminder(sourceRole, primaryMarker);

        if (marked.isEmpty()) {
            return "Set " + sourceRole.getDisplayName() + " markers in the Grimoire";
        }
        if (marked.size() < 2) {
            return sourceRole.getDisplayName() + ": add the second marked player";
        }

        List<UUID> pair = marked.subList(0, Math.min(2, marked.size()));
        String players = joinPlayerLabels(pair);
        if (real == null) {
            return "Either: " + players + " — set the " + primaryMarker + " marker";
        }

        PendingRoleAssignment assignment = ClientState.grimoireRoles.get(real);
        if (assignment == null || assignment.getRoleType() != expectedType) {
            return "Either: " + players + " — check the " + primaryMarker + " marker";
        }
        return "Either: " + players + " — one is " + assignment.getDisplayName();
    }

    private static String librarianInfo() {
        List<UUID> marked = playersWithRoleReminder(Role.LIBRARIAN);
        if (marked.isEmpty() && countRoleType(RoleType.OUTSIDER) == 0) {
            return "No Outsiders in play";
        }
        return firstNightPairInfo(Role.LIBRARIAN, "Outsider", RoleType.OUTSIDER);
    }

    private static String stewardInfo() {
        List<UUID> marked = playersWithRoleReminder(Role.STEWARD);
        if (marked.isEmpty()) return "Set Steward: Know on the good player";
        UUID id = marked.getFirst();
        PendingRoleAssignment assignment = ClientState.grimoireRoles.get(id);
        String suffix = assignment != null && !assignment.isFinalGood() ? " [CHECK: actually evil]" : "";
        return "Good player: " + playerLabel(id) + suffix;
    }

    private static String knightInfo() {
        List<UUID> marked = playersWithRoleReminder(Role.KNIGHT);
        if (marked.isEmpty()) return "Set Knight: Know on 2 non-Demon players";
        String text = "Not Demon: " + joinPlayerLabels(marked.subList(0, Math.min(2, marked.size())));
        if (marked.size() < 2) return text + " — add 1 more marker";
        for (UUID id : marked.subList(0, 2)) {
            PendingRoleAssignment assignment = ClientState.grimoireRoles.get(id);
            if (assignment != null && assignment.getRoleType() == RoleType.DEMON) {
                return text + " [CHECK: Demon marked]";
            }
        }
        return text;
    }

    private static String nobleInfo() {
        List<UUID> marked = playersWithRoleReminder(Role.NOBLE);
        if (marked.isEmpty()) return "Set Noble: Know on 3 players";
        List<UUID> shown = marked.subList(0, Math.min(3, marked.size()));
        int evil = 0;
        for (UUID id : shown) if (isEvil(id)) evil++;
        String text = "Players: " + joinPlayerLabels(shown) + " — true evil count: " + evil;
        if (marked.size() < 3) return text + " (add " + (3 - marked.size()) + " marker" + (marked.size() == 2 ? "" : "s") + ")";
        return text;
    }

    private static List<UUID> playersWithRoleReminder(Role sourceRole) {
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, List<Reminder>> entry : ClientState.grimoireReminders.entrySet()) {
            for (Reminder reminder : entry.getValue()) {
                if (reminder.role().isPresent() && reminder.role().get() == sourceRole) {
                    result.add(entry.getKey());
                    break;
                }
            }
        }
        result.sort(Comparator.comparingInt(NightVisitInfoHUD::seatOf));
        return result;
    }

    private static UUID firstPlayerWithRoleReminder(Role sourceRole, String text) {
        String wanted = normalize(text);
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, List<Reminder>> entry : ClientState.grimoireReminders.entrySet()) {
            for (Reminder reminder : entry.getValue()) {
                if (reminder.role().isPresent()
                        && reminder.role().get() == sourceRole
                        && normalize(reminder.text()).equals(wanted)) {
                    result.add(entry.getKey());
                    break;
                }
            }
        }
        result.sort(Comparator.comparingInt(NightVisitInfoHUD::seatOf));
        return result.isEmpty() ? null : result.getFirst();
    }

    private static int countRoleType(RoleType type) {
        int count = 0;
        for (UUID id : orderedSeatPlayers(false)) {
            PendingRoleAssignment assignment = ClientState.grimoireRoles.get(id);
            if (assignment != null && assignment.getRoleType() == type) count++;
        }
        return count;
    }

    private static String joinPlayerLabels(List<UUID> players) {
        StringBuilder out = new StringBuilder();
        for (UUID id : players) {
            if (out.length() > 0) out.append(", ");
            out.append(playerLabel(id));
        }
        return out.toString();
    }

    private static String godfatherInfo() {
        List<String> outsiders = new ArrayList<>();
        for (UUID id : orderedSeatPlayers(false)) {
            PendingRoleAssignment assignment = ClientState.grimoireRoles.get(id);
            if (assignment != null && assignment.getRoleType() == RoleType.OUTSIDER) {
                outsiders.add(assignment.getDisplayName());
            }
        }
        return outsiders.isEmpty() ? "No Outsiders in play" : "Outsiders: " + String.join(", ", outsiders);
    }

    private static int chefPairs() {
        List<UUID> seats = orderedSeatPlayers(false);
        if (seats.size() < 2) return 0;
        int pairs = 0;
        for (int i = 0; i < seats.size(); i++) {
            UUID a = seats.get(i);
            UUID b = seats.get((i + 1) % seats.size());
            if (isEvil(a) && isEvil(b)) pairs++;
        }
        return pairs;
    }

    private static Integer empathCount(UUID empath) {
        List<UUID> seats = orderedSeatPlayers(false);
        int index = seats.indexOf(empath);
        if (index < 0 || seats.size() < 2) return null;

        UUID clockwise = nearestAlive(seats, index, 1);
        UUID counterClockwise = nearestAlive(seats, index, -1);
        if (clockwise == null || counterClockwise == null) return null;

        int count = 0;
        if (isEvil(clockwise)) count++;
        if (isEvil(counterClockwise)) count++;
        return count;
    }

    private static UUID nearestAlive(List<UUID> seats, int origin, int direction) {
        int size = seats.size();
        for (int step = 1; step < size; step++) {
            int index = Math.floorMod(origin + direction * step, size);
            UUID id = seats.get(index);
            if (!Boolean.TRUE.equals(ClientState.playerDeathStatus.get(id))) return id;
        }
        return null;
    }

    private static Integer clockmakerDistance() {
        List<UUID> seats = orderedSeatPlayers(false);
        if (seats.isEmpty()) return null;
        List<Integer> demons = new ArrayList<>();
        List<Integer> minions = new ArrayList<>();
        for (int i = 0; i < seats.size(); i++) {
            PendingRoleAssignment assignment = ClientState.grimoireRoles.get(seats.get(i));
            if (assignment == null) continue;
            if (assignment.getRoleType() == RoleType.DEMON) demons.add(i);
            if (assignment.getRoleType() == RoleType.MINION) minions.add(i);
        }
        if (demons.isEmpty() || minions.isEmpty()) return null;

        int best = Integer.MAX_VALUE;
        for (int demon : demons) {
            for (int minion : minions) {
                int raw = Math.abs(demon - minion);
                best = Math.min(best, Math.min(raw, seats.size() - raw));
            }
        }
        return best == Integer.MAX_VALUE ? null : best;
    }

    private static String shugenjaDirection(UUID shugenja) {
        List<UUID> seats = orderedSeatPlayers(false);
        int origin = seats.indexOf(shugenja);
        if (origin < 0 || seats.size() < 2) return null;

        int clockwise = Integer.MAX_VALUE;
        int counter = Integer.MAX_VALUE;
        int size = seats.size();
        for (int step = 1; step < size; step++) {
            UUID clockwiseId = seats.get(Math.floorMod(origin + step, size));
            if (clockwise == Integer.MAX_VALUE && isEvil(clockwiseId)) clockwise = step;
            UUID counterId = seats.get(Math.floorMod(origin - step, size));
            if (counter == Integer.MAX_VALUE && isEvil(counterId)) counter = step;
            if (clockwise != Integer.MAX_VALUE && counter != Integer.MAX_VALUE) break;
        }
        if (clockwise == Integer.MAX_VALUE && counter == Integer.MAX_VALUE) return null;
        if (clockwise == counter) return "Equidistant";
        return clockwise < counter ? "Clockwise" : "Counter-clockwise";
    }

    private static int deadEvilCount() {
        int count = 0;
        for (UUID id : orderedSeatPlayers(false)) {
            if (Boolean.TRUE.equals(ClientState.playerDeathStatus.get(id)) && isEvil(id)) count++;
        }
        return count;
    }

    private static String grandmotherInfo() {
        UUID grandchild = firstPlayerWithReminder("Grandchild");
        if (grandchild == null) return null;
        PendingRoleAssignment assignment = ClientState.grimoireRoles.get(grandchild);
        return "Grandchild: " + playerLabel(grandchild)
                + (assignment == null ? "" : " (" + assignment.getDisplayName() + ")");
    }

    private static UUID firstPlayerWithReminder(String text) {
        List<UUID> candidates = new ArrayList<>();
        for (UUID id : ClientState.grimoireReminders.keySet()) {
            if (hasReminder(id, text)) candidates.add(id);
        }
        candidates.sort(Comparator.comparingInt(NightVisitInfoHUD::seatOf));
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    private static boolean isDrunk(UUID id, PendingRoleAssignment actualAssignment) {
        return actualAssignment != null
                && !actualAssignment.isCustomRole()
                && actualAssignment.role() == Role.DRUNK
                || hasReminder(id, "Drunk");
    }

    private static boolean hasReminder(UUID id, String expected) {
        if (id == null) return false;
        String wanted = normalize(expected);
        for (Reminder reminder : ClientState.grimoireReminders.getOrDefault(id, List.of())) {
            if (normalize(reminder.text()).equals(wanted)) return true;
        }
        return false;
    }

    private static boolean isEvil(UUID id) {
        PendingRoleAssignment assignment = ClientState.grimoireRoles.get(id);
        return assignment != null && !assignment.isFinalGood();
    }

    private static List<UUID> orderedSeatPlayers(boolean livingOnly) {
        List<Map.Entry<UUID, Integer>> entries = new ArrayList<>();
        Map<UUID, Integer> seats = ClientState.grimoireSeatNumbers.isEmpty()
                ? ClientState.playerSeatNumbers
                : ClientState.grimoireSeatNumbers;
        for (Map.Entry<UUID, Integer> entry : seats.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) continue;
            PendingRoleAssignment assignment = ClientState.grimoireRoles.get(entry.getKey());
            if (assignment == null || assignment.getRoleType() == RoleType.NONE) continue;
            if (livingOnly && Boolean.TRUE.equals(ClientState.playerDeathStatus.get(entry.getKey()))) continue;
            entries.add(entry);
        }
        entries.sort(Map.Entry.comparingByValue());
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : entries) result.add(entry.getKey());
        return result;
    }

    private static int seatOf(UUID id) {
        return ClientState.grimoireSeatNumbers.getOrDefault(id,
                ClientState.playerSeatNumbers.getOrDefault(id, Integer.MAX_VALUE));
    }

    private static String playerLabel(UUID id) {
        return ClientState.playerName(id, seatOf(id) == Integer.MAX_VALUE ? 0 : seatOf(id));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static List<String> wrap(Minecraft minecraft, String text, int maxWidth, int maxLines) {
        if (text == null || text.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && minecraft.font.width(candidate) > maxWidth) {
                result.add(line.toString());
                if (result.size() >= maxLines) return result;
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty() && result.size() < maxLines) result.add(line.toString());
        return result;
    }
}
