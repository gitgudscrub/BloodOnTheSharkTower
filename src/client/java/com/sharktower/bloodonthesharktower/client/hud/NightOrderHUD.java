package com.sharktower.bloodonthesharktower.client.hud;

import net.minecraft.client.renderer.RenderPipelines;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.CustomRole;
import com.sharktower.bloodonthesharktower.core.GamePhase;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Storyteller phase / night-order bar modelled after Blood on the Blocktower's
 * original top-of-screen night HUD.
 *
 * Left/Right selects an entry and Up activates it. Dusk, Dawn and Nominations
 * are always present during a running game. Player role visits are inserted in
 * official wake order for the relevant night and use the Storyteller's current
 * Grimoire knowledge, so a first-night-only role such as Chef appears on N1 but
 * is absent on later nights.
 */
public final class NightOrderHUD {
    private static final int ICON_SIZE = 24;
    private static final int ICON_SPACING = 28;
    private static final int Y = 24;

    private static final Identifier DUSK_ICON = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/icons/dusk.png");
    private static final Identifier DAWN_ICON = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/icons/dawn.png");
    private static final Identifier NOMINATIONS_ICON = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/icons/nominations.png");

    /*
     * These orders are taken from the original 1.3.0 NightOrder table, keeping
     * only regular player visits whose original entry teleports to a seat.
     * Triggered/death-only visits are intentionally not synthesized here; they
     * remain Storyteller judgement until the full triggered-night-action port.
     */
    private static final List<Role> FIRST_NIGHT_VISITS = List.of(
            Role.KAZALI,
            Role.APPRENTICE,
            Role.BARISTA,
            Role.BUREAUCRAT,
            Role.THIEF,
            Role.BOFFIN,
            Role.PHILOSOPHER,
            Role.ALCHEMIST,
            Role.YAGGABABBLE,
            Role.LUNATIC,
            Role.SUMMONER,
            Role.SAILOR,
            Role.ENGINEER,
            Role.PREACHER,
            Role.LIL_MONSTA,
            Role.LLEECH,
            Role.POISONER,
            Role.WIDOW,
            Role.COURTIER,
            Role.WIZARD,
            Role.SNAKE_CHARMER,
            Role.GODFATHER,
            Role.ORGAN_GRINDER,
            Role.DEVILS_ADVOCATE,
            Role.EVIL_TWIN,
            Role.WITCH,
            Role.CERENOVUS,
            Role.FEARMONGER,
            Role.HARPY,
            Role.MEZEPHELES,
            Role.PUKKA,
            Role.PIXIE,
            Role.HUNTSMAN,
            Role.AMNESIAC,
            Role.WASHERWOMAN,
            Role.LIBRARIAN,
            Role.INVESTIGATOR,
            Role.CHEF,
            Role.EMPATH,
            Role.FORTUNE_TELLER,
            Role.BUTLER,
            Role.GRANDMOTHER,
            Role.CLOCKMAKER,
            Role.DREAMER,
            Role.SEAMSTRESS,
            Role.STEWARD,
            Role.KNIGHT,
            Role.NOBLE,
            Role.BALLOONIST,
            Role.SHUGENJA,
            Role.VILLAGE_IDIOT,
            Role.BOUNTY_HUNTER,
            Role.NIGHTWATCHMAN,
            Role.ARTIST,
            Role.SAVANT,
            Role.FISHERMAN,
            Role.SPY,
            Role.OGRE,
            Role.HIGH_PRIESTESS,
            Role.GENERAL,
            Role.CHAMBERMAID,
            Role.MATHEMATICIAN
    );

    private static final List<Role> OTHER_NIGHT_VISITS = List.of(
            Role.WRAITH,
            Role.DUCHESS,
            Role.BARISTA,
            Role.CACKLEJACK,
            Role.BUREAUCRAT,
            Role.THIEF,
            Role.HARLOT,
            Role.BONE_COLLECTOR,
            Role.PHILOSOPHER,
            Role.POPPY_GROWER,
            Role.SAILOR,
            Role.ENGINEER,
            Role.PREACHER,
            Role.POISONER,
            Role.COURTIER,
            Role.INNKEEPER,
            Role.WIZARD,
            Role.GAMBLER,
            Role.ACROBAT,
            Role.SNAKE_CHARMER,
            Role.MONK,
            Role.ORGAN_GRINDER,
            Role.DEVILS_ADVOCATE,
            Role.WITCH,
            Role.CERENOVUS,
            Role.PIT_HAG,
            Role.FEARMONGER,
            Role.HARPY,
            Role.SCARLET_WOMAN,
            Role.SUMMONER,
            Role.LUNATIC,
            Role.EXORCIST,
            Role.LYCANTHROPE,
            Role.IMP,
            Role.ZOMBUUL,
            Role.PUKKA,
            Role.SHABALOTH,
            Role.PO,
            Role.FANG_GU,
            Role.NO_DASHII,
            Role.VORTOX,
            Role.LORD_OF_TYPHON,
            Role.VIGORMORTIS,
            Role.OJO,
            Role.AL_HADIKHIA,
            Role.LLEECH,
            Role.LIL_MONSTA,
            Role.YAGGABABBLE,
            Role.KAZALI,
            Role.ASSASSIN,
            Role.GODFATHER,
            Role.HATTER,
            Role.BARBER,
            Role.SWEETHEART,
            Role.PLAGUE_DOCTOR,
            Role.SAGE,
            Role.BANSHEE,
            Role.PROFESSOR,
            Role.CHOIRBOY,
            Role.HUNTSMAN,
            Role.DAMSEL,
            Role.AMNESIAC,
            Role.FARMER,
            Role.GRANDMOTHER,
            Role.RAVENKEEPER,
            Role.EMPATH,
            Role.FORTUNE_TELLER,
            Role.UNDERTAKER,
            Role.DREAMER,
            Role.FLOWERGIRL,
            Role.TOWN_CRIER,
            Role.ORACLE,
            Role.SEAMSTRESS,
            Role.JUGGLER,
            Role.BALLOONIST,
            Role.VILLAGE_IDIOT,
            Role.KING,
            Role.BOUNTY_HUNTER,
            Role.NIGHTWATCHMAN,
            Role.BUTLER,
            Role.ARTIST,
            Role.SAVANT,
            Role.FISHERMAN,
            Role.SPY,
            Role.HIGH_PRIESTESS,
            Role.GENERAL,
            Role.CHAMBERMAID,
            Role.MATHEMATICIAN,
            Role.RIOT
    );

    private static final Set<Role> TRIGGER_ONLY_ROLES = Set.of(
            Role.SCARLET_WOMAN,
            Role.POPPY_GROWER,
            Role.HATTER,
            Role.BARBER,
            Role.SWEETHEART,
            Role.PLAGUE_DOCTOR,
            Role.SAGE,
            Role.BANSHEE,
            Role.CHOIRBOY,
            Role.FARMER,
            Role.GRANDMOTHER,
            Role.RAVENKEEPER,
            Role.UNDERTAKER
    );

    private static final Set<Role> TRIGGER_WITHOUT_SEAT_TELEPORT = Set.of(
            Role.SCARLET_WOMAN,
            Role.POPPY_GROWER,
            Role.SWEETHEART,
            Role.PLAGUE_DOCTOR,
            Role.FARMER,
            Role.GRANDMOTHER
    );

    private static int selectedIndex = 0;
    private static String selectedKey = "static:dusk";

    private NightOrderHUD() {}

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!isAvailable(minecraft) || !ClientState.isNightHudVisible || minecraft.gui.screen() != null) return;

        List<Visit> visits = buildVisits();
        if (visits.isEmpty()) return;
        restoreSelection(visits);

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int maxVisible = Math.max(3, Math.min(visits.size(), Math.max(3, (screenWidth - 24) / ICON_SPACING)));
        int first = 0;
        if (visits.size() > maxVisible) {
            first = selectedIndex - maxVisible / 2;
            first = Math.max(0, Math.min(first, visits.size() - maxVisible));
        }
        int visible = Math.min(maxVisible, visits.size());
        int totalWidth = visible * ICON_SPACING - (ICON_SPACING - ICON_SIZE);
        int startX = (screenWidth - totalWidth) / 2;

        for (int offset = 0; offset < visible; offset++) {
            int index = first + offset;
            Visit visit = visits.get(index);
            int x = startX + offset * ICON_SPACING;

            Identifier icon = visit.icon();
            if (icon != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, Y, 0, 0,
                        ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            }

            if (index == selectedIndex) {
                graphics.outline(x - 1, Y - 1, ICON_SIZE + 2, ICON_SIZE + 2, 0xFFFFFFFF);
            }

            if (visit.kind == Kind.ROLE && visit.seat > 0 && duplicateRoleCount(visits, visit) > 1) {
                String seat = Integer.toString(visit.seat);
                int sx = x + ICON_SIZE - minecraft.font.width(seat);
                graphics.text(minecraft.font, seat, sx, Y + ICON_SIZE - 8, 0xFFFFFF55, true);
            }

            if (visit.triggered) {
                graphics.text(minecraft.font, "!", x - 1, Y - 3, 0xFFFFAA00, true);
            }
        }

        // Small edge chevrons make it obvious when the visit row is wider than
        // the available screen width and the selected window has scrolled.
        if (first > 0) {
            graphics.text(minecraft.font, "<", Math.max(2, startX - 10), Y + 8, 0xFFAAAAAA, true);
        }
        if (first + visible < visits.size()) {
            graphics.text(minecraft.font, ">", Math.min(screenWidth - 8, startX + totalWidth + 4), Y + 8,
                    0xFFAAAAAA, true);
        }
    }

    public static void advance(int delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isAvailable(minecraft) || !ClientState.isNightHudVisible) return;
        List<Visit> visits = buildVisits();
        if (visits.isEmpty()) return;
        restoreSelection(visits);
        selectedIndex = Math.floorMod(selectedIndex + delta, visits.size());
        selectedKey = visits.get(selectedIndex).key();
        NightVisitInfoHUD.clear();
        showSelectionHint(minecraft, visits.get(selectedIndex));
    }

    public static void activate() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isAvailable(minecraft) || !ClientState.isNightHudVisible || minecraft.player == null) return;
        List<Visit> visits = buildVisits();
        if (visits.isEmpty()) return;
        restoreSelection(visits);
        Visit visit = visits.get(selectedIndex);

        switch (visit.kind) {
            case DUSK -> {
                NightVisitInfoHUD.clear();
                if (ClientState.phase() == GamePhase.NIGHT) {
                    minecraft.player.sendSystemMessage(Component.literal("Dusk can only begin from Day.")
                            .withStyle(ChatFormatting.RED));
                } else {
                    ClientStorytellerActions.send("phase_night");
                }
            }
            case DAWN -> {
                NightVisitInfoHUD.clear();
                if (ClientState.phase() != GamePhase.NIGHT) {
                    minecraft.player.sendSystemMessage(Component.literal("Dawn can only begin during Night.")
                            .withStyle(ChatFormatting.RED));
                } else {
                    ClientStorytellerActions.send("phase_day");
                }
            }
            case NOMINATIONS -> {
                NightVisitInfoHUD.clear();
                if (ClientState.phase() == GamePhase.NIGHT) {
                    minecraft.player.sendSystemMessage(Component.literal("Open Dawn before nominations.")
                            .withStyle(ChatFormatting.RED));
                } else if (ClientState.nominationsOpen) {
                    minecraft.player.sendSystemMessage(Component.literal("Nominations are already open.")
                            .withStyle(ChatFormatting.GRAY));
                } else {
                    ClientStorytellerActions.send("nominations_open");
                }
            }
            case ROLE -> {
                if (ClientState.phase() != GamePhase.NIGHT) {
                    minecraft.player.sendSystemMessage(Component.literal("Night role visits are only available during Night.")
                            .withStyle(ChatFormatting.RED));
                } else if (visit.playerId != null) {
                    NightVisitInfoHUD.showVisit(
                            visit.playerId,
                            visit.assignment,
                            visit.triggered,
                            visit.triggerSourcePlayer
                    );
                    if (visit.triggered) showTriggeredInstruction(minecraft, visit);
                    if (visit.seatTeleport) {
                        ClientStorytellerActions.send("night_visit", visit.playerId.toString());
                    }
                }
            }
        }
    }

    public static void resetSelection() {
        selectedIndex = 0;
        selectedKey = "static:dusk";
        NightVisitInfoHUD.clear();
    }

    private static boolean isAvailable(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) return false;
        if (!ClientState.isHudEnabled || ClientState.gameEnding) return false;
        // Once roles have been committed the first Dusk button must be usable
        // while the numeric day/night state is still 0/0 (which otherwise looks
        // like Setup). activePlayerCount is zero before SEND ROLES and becomes
        // authoritative at commit, so pending role edits do not expose the bar.
        if (ClientState.phase() == GamePhase.SETUP
                && ClientState.activePlayerCount + ClientState.travelerCount <= 0) return false;
        return ClientState.storytellerPlayers.contains(minecraft.player.getUUID());
    }

    private static List<Visit> buildVisits() {
        List<Visit> visits = new ArrayList<>();
        visits.add(Visit.staticVisit(Kind.DUSK, DUSK_ICON, 0.0D));

        GamePhase phase = ClientState.phase();

        // During Setup (after roles are committed), preview Night 1 so the ST can
        // see the opening wake order before pressing Dusk. During an active Night,
        // show that night's wake order. During Day, hide all night-role visits;
        // otherwise OTHER_NIGHT roles such as the Imp would appear immediately on
        // Day 1 even though they should not be actionable until Dusk starts Night 2.
        boolean showRoleVisits = phase == GamePhase.NIGHT
                || (phase == GamePhase.SETUP
                && ClientState.activePlayerCount + ClientState.travelerCount > 0);

        boolean firstNight = ClientState.currentDay == 0 && ClientState.currentNight <= 1;
        List<Role> officialOrder = firstNight ? FIRST_NIGHT_VISITS : OTHER_NIGHT_VISITS;
        Map<Role, Double> order = new HashMap<>();
        for (int i = 0; i < officialOrder.size(); i++) order.putIfAbsent(officialOrder.get(i), (double) (i + 1));

        // Death/event triggers in the original mod are sourced from the OTHER
        // night order even when the trigger happens on Night 1. Keep a separate
        // map so Ravenkeeper/Sage/etc. are inserted at the same relative point
        // rather than all being dumped immediately before Dawn on Night 1.
        Map<Role, Double> triggeredOrder = new HashMap<>();
        for (int i = 0; i < OTHER_NIGHT_VISITS.size(); i++) {
            triggeredOrder.putIfAbsent(OTHER_NIGHT_VISITS.get(i), (double) (i + 1));
        }

        // A custom script may provide an explicit first/other night order. When
        // present it is a stronger ordering signal than the built-in official
        // table, while the teleport eligibility still comes from the role data.
        Map<String, Double> scriptOrder = explicitScriptOrder(firstNight);
        Map<String, Double> triggeredScriptOrder = explicitScriptOrder(false);

        List<Visit> roleVisits = new ArrayList<>();
        if (showRoleVisits) {
        for (Map.Entry<UUID, PendingRoleAssignment> entry : ClientState.grimoireRoles.entrySet()) {
            UUID playerId = entry.getKey();
            PendingRoleAssignment assignment = entry.getValue();
            if (playerId == null || assignment == null) continue;
            if (ClientState.storytellerPlayers.contains(playerId)) continue;
            int seat = ClientState.grimoireSeatNumbers.getOrDefault(playerId,
                    ClientState.playerSeatNumbers.getOrDefault(playerId, 0));
            if (seat <= 0) continue;
            if (Boolean.TRUE.equals(ClientState.playerDeathStatus.get(playerId))) continue;

            double sort;
            if (assignment.isCustomRole()) {
                CustomRole custom = assignment.customRole().orElse(null);
                if (custom == null) continue;
                double customOrder = firstNight ? custom.firstNight() : custom.otherNight();
                if (customOrder <= 0.0D) continue;
                sort = scriptOrder.getOrDefault(normalize(custom.id()), customOrder);
            } else {
                Role role = assignment.role();
                if (role == null || role == Role.NO_ROLE) continue;
                Double builtIn = order.get(role);

                // Trigger-only characters do not appear merely because they are
                // in play. Undertaker is the exception: it is execution-based.
                if (role == Role.UNDERTAKER) {
                    // Undertaker only wakes when somebody actually died by execution.
                    // An execution that was survived still sets executionToday for
                    // daytime bookkeeping, so use the dedicated Storyteller fact.
                    if (firstNight || ClientState.lastExecutedRoleName == null
                            || ClientState.lastExecutedRoleName.isBlank()) continue;
                } else if (!firstNight && TRIGGER_ONLY_ROLES.contains(role)) {
                    // Some death-triggered roles (notably Grandmother) still
                    // have a normal first-night visit. Only suppress their
                    // passive OTHER-night entry; first-night order remains intact.
                    continue;
                }

                if (builtIn == null) continue;
                sort = scriptOrder.getOrDefault(normalize(role.getId()), builtIn);
            }

            roleVisits.add(Visit.role(playerId, assignment, seat, sort));
        }

        if (phase == GamePhase.NIGHT) {
            for (ClientTriggeredNightOrder.Trigger trigger : ClientTriggeredNightOrder.forNight(ClientState.currentNight)) {
                UUID playerId = trigger.playerId();
                int seat = ClientState.grimoireSeatNumbers.getOrDefault(playerId,
                        ClientState.playerSeatNumbers.getOrDefault(playerId, 0));
                if (seat <= 0) continue;

                PendingRoleAssignment assignment = ClientState.grimoireRoles.get(playerId);
                if (assignment == null || assignment.isCustomRole() || assignment.role() != trigger.role()) {
                    assignment = new PendingRoleAssignment(trigger.role(), com.sharktower.bloodonthesharktower.core.AlignmentOverride.DEFAULT);
                }

                Double builtIn = triggeredOrder.get(trigger.role());
                double fallbackSort = builtIn == null ? 9_000.0D : builtIn;
                double sort = triggeredScriptOrder.getOrDefault(normalize(trigger.role().getId()), fallbackSort);
                boolean seatTeleport = !TRIGGER_WITHOUT_SEAT_TELEPORT.contains(trigger.role());
                roleVisits.add(Visit.triggeredRole(
                        playerId,
                        assignment,
                        seat,
                        sort,
                        trigger.sourcePlayerId(),
                        seatTeleport
                ));
            }
        }
        }

        roleVisits.sort(Comparator
                .comparingDouble((Visit visit) -> visit.sortOrder)
                .thenComparingInt(visit -> visit.seat)
                .thenComparing(visit -> visit.playerId == null ? "" : visit.playerId.toString()));
        visits.addAll(roleVisits);

        visits.add(Visit.staticVisit(Kind.DAWN, DAWN_ICON, 10_000.0D));
        visits.add(Visit.staticVisit(Kind.NOMINATIONS, NOMINATIONS_ICON, 11_000.0D));
        return visits;
    }

    private static Map<String, Double> explicitScriptOrder(boolean firstNight) {
        Map<String, Double> result = new HashMap<>();
        if (ClientState.currentScript == null) return result;
        List<String> ids = firstNight
                ? ClientState.currentScript.firstNightOrder()
                : ClientState.currentScript.otherNightOrder();
        if (ids == null || ids.isEmpty()) return result;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (id != null && !id.isBlank()) result.putIfAbsent(normalize(id), (double) (i + 1));
        }
        return result;
    }

    private static void restoreSelection(List<Visit> visits) {
        if (visits.isEmpty()) {
            selectedIndex = 0;
            selectedKey = "static:dusk";
            return;
        }
        for (int i = 0; i < visits.size(); i++) {
            if (visits.get(i).key().equals(selectedKey)) {
                selectedIndex = i;
                return;
            }
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, visits.size() - 1));
        selectedKey = visits.get(selectedIndex).key();
    }

    private static int duplicateRoleCount(List<Visit> visits, Visit target) {
        if (target.assignment == null) return 0;
        String id = normalize(target.assignment.getRoleId());
        int count = 0;
        for (Visit visit : visits) {
            if (visit.assignment != null && normalize(visit.assignment.getRoleId()).equals(id)) count++;
        }
        return count;
    }

    private static void showSelectionHint(Minecraft minecraft, Visit visit) {
        // The selected visit is already indicated by the white outline in the HUD.
        // 26.3 moved the vanilla action-bar API again, so avoid depending on it
        // (and avoid filling Storyteller chat while cycling through the night order).
    }

    private static void showTriggeredInstruction(Minecraft minecraft, Visit visit) {
        if (minecraft.player == null || visit.assignment == null) return;
        String roleId = normalize(visit.assignment.getRoleId());
        minecraft.player.sendSystemMessage(Component.literal("Triggered: " + visit.assignment.getDisplayName())
                .withStyle(ChatFormatting.GOLD));
        minecraft.player.sendSystemMessage(Component.translatable(
                "nightorder." + BloodOnTheSharktower.MOD_ID + "." + roleId + ".other")
                .withStyle(ChatFormatting.GRAY));
    }

    private static String normalize(String id) {
        if (id == null) return "";
        return id.toLowerCase(Locale.ROOT).replaceAll("[_\\s-]", "");
    }

    private enum Kind {
        DUSK,
        ROLE,
        DAWN,
        NOMINATIONS
    }

    private record Visit(
            Kind kind,
            UUID playerId,
            PendingRoleAssignment assignment,
            int seat,
            double sortOrder,
            Identifier staticIcon,
            boolean triggered,
            UUID triggerSourcePlayer,
            boolean seatTeleport
    ) {
        static Visit staticVisit(Kind kind, Identifier icon, double sortOrder) {
            return new Visit(kind, null, null, 0, sortOrder, icon, false, null, false);
        }

        static Visit role(UUID playerId, PendingRoleAssignment assignment, int seat, double sortOrder) {
            return new Visit(Kind.ROLE, playerId, assignment, seat, sortOrder, null, false, null, true);
        }

        static Visit triggeredRole(UUID playerId, PendingRoleAssignment assignment, int seat, double sortOrder,
                                   UUID triggerSourcePlayer, boolean seatTeleport) {
            return new Visit(Kind.ROLE, playerId, assignment, seat, sortOrder, null, true,
                    triggerSourcePlayer, seatTeleport);
        }

        String key() {
            if (kind != Kind.ROLE) return "static:" + kind.name().toLowerCase(Locale.ROOT);
            if (triggered) {
                return "trigger:" + playerId + ":" + normalize(assignment == null ? "" : assignment.getRoleId())
                        + ":" + triggerSourcePlayer;
            }
            return "role:" + playerId;
        }

        Identifier icon() {
            if (staticIcon != null) return staticIcon;
            if (assignment == null) return null;
            ScriptRole role = assignment.getScriptRole();
            return role == null ? null : role.getIcon();
        }
    }
}
