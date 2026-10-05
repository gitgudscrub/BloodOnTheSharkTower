package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.RoleCounts;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Blood on the Sharktower role bag.
 *
 * The Storyteller chooses the exact characters represented by physical bag
 * tokens, then the server shuffles those characters across the currently seated
 * players. Drunk and Marionette are special setup reservations: their actual
 * tokens are never placed in the bag. Instead the Storyteller adds Townsfolk
 * cover tokens, distributes the bag, then changes an appropriate player to the
 * hidden role in the Grimoire and assigns the role they believe they are.
 */
public final class RoleBagScreen extends Screen {
    private static final int CELL_W = 82;
    private static final int CELL_H = 61;
    private static final int GAP_X = 5;
    private static final int GAP_Y = 4;

    private static final Pattern OUTSIDER_BRACKET = Pattern.compile("\\[([^\\]]*outsider[^\\]]*)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern OUTSIDER_RANGE = Pattern.compile("([+-]?\\d+)\\s*(?:or|to)\\s*([+-]?\\d+)\\s+outsiders?", Pattern.CASE_INSENSITIVE);
    private static final Pattern OUTSIDER_FIXED = Pattern.compile("([+-]\\d+)\\s+outsiders?", Pattern.CASE_INSENSITIVE);

    private static final Set<String> SELECTED = new LinkedHashSet<>();
    private static String scriptIdentity = "";
    private static boolean drunkReserved;
    private static boolean marionetteReserved;
    private static boolean openGrimoireAfterDistributionSync;

    private int page;

    public RoleBagScreen() {
        this(0);
    }

    private RoleBagScreen(int page) {
        super(Component.literal("Blood on the Sharktower — Role Bag"));
        this.page = Math.max(0, page);
    }

    @Override
    protected void init() {
        prepareSelection();
        List<ScriptRole> roles = roles();

        int top = hiddenSetupActive() ? 70 : 62;
        boolean narrow = this.width < 460;
        int bottomReserve = narrow ? 100 : 78;
        int rows = Math.max(1, (this.height - top - bottomReserve) / (CELL_H + GAP_Y));
        int columns = Math.max(1, (this.width - 24 + GAP_X) / (CELL_W + GAP_X));
        int perPage = rows * columns;
        int maxPage = Math.max(0, (roles.size() - 1) / perPage);
        if (page > maxPage) page = maxPage;

        int gridWidth = columns * CELL_W + (columns - 1) * GAP_X;
        int left = (this.width - gridWidth) / 2;
        int start = page * perPage;
        int end = Math.min(roles.size(), start + perPage);

        for (int i = start; i < end; i++) {
            ScriptRole role = roles.get(i);
            int local = i - start;
            int col = local % columns;
            int row = local / columns;
            int x = left + col * (CELL_W + GAP_X);
            int y = top + row * (CELL_H + GAP_Y);
            this.addRenderableWidget(new RoleBagRoleWidget(
                    x, y, CELL_W, CELL_H, role,
                    () -> isHiddenSetupRole(role) ? isHiddenReserved(role) : SELECTED.contains(role.getId()),
                    () -> {
                        toggleRole(role);
                        this.minecraft.gui.setScreen(new RoleBagScreen(page));
                    }
            ));
        }

        int navY = this.height - (narrow ? 90 : 66);
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                    if (page > 0) this.minecraft.gui.setScreen(new RoleBagScreen(page - 1));
                }).bounds(this.width / 2 - (narrow ? 80 : 220), navY, 36, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                    if (page < maxPage) this.minecraft.gui.setScreen(new RoleBagScreen(page + 1));
                }).bounds(this.width / 2 - (narrow ? 38 : -178), navY, 36, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
                    clearSelection();
                    this.minecraft.gui.setScreen(new RoleBagScreen(page));
                }).bounds(this.width / 2 + (narrow ? 4 : -132), navY, 72, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Current Setup"), b -> {
                    selectCurrentSetup();
                    this.minecraft.gui.setScreen(new RoleBagScreen(page));
                }).bounds(this.width / 2 - (narrow ? 120 : 54), navY + (narrow ? 24 : 0), 105, 20).build());

        boolean ready = seatedCount() > 0 && SELECTED.size() == seatedCount();
        Button distribute = Button.builder(
                        Component.literal(ready ? "Distribute Bag" : "Need " + seatedCount() + " roles")
                                .withStyle(ready ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY),
                        b -> distribute())
                .bounds(this.width / 2 + (narrow ? -9 : 57), navY + (narrow ? 24 : 0), 128, 20).build();
        distribute.active = ready;
        this.addRenderableWidget(distribute);

        this.addRenderableWidget(Button.builder(Component.literal("Back to Grimoire"), b ->
                        this.minecraft.gui.setScreen(new AssignRolesScreen()))
                .bounds(this.width / 2 - 90, this.height - 31, 180, 20).build());
    }

    private void toggleRole(ScriptRole role) {
        if (isDrunk(role)) {
            drunkReserved = !drunkReserved;
            return;
        }
        if (isMarionette(role)) {
            marionetteReserved = !marionetteReserved;
            return;
        }
        if (!SELECTED.add(role.getId())) SELECTED.remove(role.getId());
    }

    private void distribute() {
        if (SELECTED.size() != seatedCount()) return;

        // Wait for the server's Grimoire snapshot before opening the Grimoire.
        // This guarantees that the Storyteller sees the newly shuffled bag
        // assignments immediately instead of briefly opening stale setup state.
        openGrimoireAfterDistributionSync = true;
        ClientStorytellerActions.send("distribute_role_bag", String.join("|", SELECTED));
    }

    /**
     * Consumed by the Grimoire state receiver after a role-bag distribution.
     * Only the Storyteller client that pressed Distribute Bag sets this flag.
     */
    public static boolean consumeOpenGrimoireAfterDistributionSync() {
        if (!openGrimoireAfterDistributionSync) return false;
        openGrimoireAfterDistributionSync = false;
        return true;
    }

    private void prepareSelection() {
        String identity = scriptIdentity();
        if (!identity.equals(scriptIdentity)) {
            clearSelection();
            scriptIdentity = identity;
            selectCurrentSetup();
        } else {
            Set<String> valid = new java.util.HashSet<>();
            for (ScriptRole role : roles()) {
                if (!isHiddenSetupRole(role)) valid.add(role.getId());
            }
            SELECTED.removeIf(id -> !valid.contains(id));
            if (!scriptHasRole("drunk")) drunkReserved = false;
            if (!scriptHasRole("marionette")) marionetteReserved = false;
        }
    }

    private void clearSelection() {
        SELECTED.clear();
        drunkReserved = false;
        marionetteReserved = false;
    }

    private void selectCurrentSetup() {
        clearSelection();
        if (ClientState.currentScript == null) return;

        for (var entry : ClientState.grimoireRoles.entrySet()) {
            PendingRoleAssignment assignment = entry.getValue();
            if (assignment == null) continue;
            String id = assignment.getRoleId();
            if (id == null || id.isBlank() || "norole".equalsIgnoreCase(id)) continue;

            ClientState.currentScript.getScriptRole(id).ifPresent(role -> {
                if (isDrunk(role)) {
                    drunkReserved = true;
                    addCurrentCover(entry.getKey());
                } else if (isMarionette(role)) {
                    marionetteReserved = true;
                    addCurrentCover(entry.getKey());
                } else if (isBagRole(role)) {
                    SELECTED.add(role.getId());
                }
            });
        }
    }

    private void addCurrentCover(java.util.UUID player) {
        PendingRoleAssignment perceived = ClientState.grimoirePerceivedRoles.get(player);
        if (perceived == null || perceived.getRoleType() != RoleType.TOWNSFOLK) return;
        String perceivedId = perceived.getRoleId();
        if (perceivedId == null || perceivedId.isBlank()) return;
        ClientState.currentScript.getScriptRole(perceivedId).ifPresent(role -> {
            if (role.getTeam() == RoleType.TOWNSFOLK) SELECTED.add(role.getId());
        });
    }

    private List<ScriptRole> roles() {
        if (ClientState.currentScript == null) return List.of();
        List<ScriptRole> roles = new ArrayList<>(ClientState.currentScript.allRoles());
        roles.removeIf(role -> !isBagRole(role));
        roles.sort(Comparator
                .comparingInt((ScriptRole role) -> role.getTeam().ordinal())
                .thenComparing(ScriptRole::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return roles;
    }

    private static boolean isBagRole(ScriptRole role) {
        if (role == null) return false;
        return switch (role.getTeam()) {
            case TOWNSFOLK, OUTSIDER, MINION, DEMON -> true;
            default -> false;
        };
    }

    private static boolean isHiddenSetupRole(ScriptRole role) {
        return isDrunk(role) || isMarionette(role);
    }

    private static boolean isDrunk(ScriptRole role) {
        return role != null && normalizedId(role).equals("drunk");
    }

    private static boolean isMarionette(ScriptRole role) {
        return role != null && normalizedId(role).equals("marionette");
    }

    private static String normalizedId(ScriptRole role) {
        return role.getId().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private boolean isHiddenReserved(ScriptRole role) {
        if (isDrunk(role)) return drunkReserved;
        if (isMarionette(role)) return marionetteReserved;
        return false;
    }

    private boolean hiddenSetupActive() {
        return drunkReserved || marionetteReserved;
    }

    private boolean scriptHasRole(String id) {
        if (ClientState.currentScript == null) return false;
        return ClientState.currentScript.allRoles().stream()
                .anyMatch(role -> normalizedId(role).equals(id));
    }

    private int seatedCount() {
        if (!ClientState.grimoireSeatNumbers.isEmpty()) return ClientState.grimoireSeatNumbers.size();
        return ClientState.playerSeatNumbers.size();
    }

    private String scriptIdentity() {
        if (ClientState.currentScript == null) return "none";
        StringBuilder out = new StringBuilder(ClientState.currentScript.name()).append('|');
        for (ScriptRole role : ClientState.currentScript.allRoles()) out.append(role.getId()).append(',');
        return out.toString();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String title = "Role Bag — " + ClientState.displayScriptName();
        drawCentered(graphics, title, 8, UiDrawing.GOLD, true);

        int seated = seatedCount();
        int selected = SELECTED.size();
        String status = selected + "/" + seated + " bag tokens selected";
        drawCentered(graphics, status, 21, selected == seated && seated > 0 ? UiDrawing.GOLD : UiDrawing.TEXT, false);

        RoleCounts.RoleCountInfo base = RoleCounts.getCounts(seated);
        long townsfolk = actualCount(RoleType.TOWNSFOLK);
        long outsiders = actualCount(RoleType.OUTSIDER);
        long minions = actualCount(RoleType.MINION);
        long demons = actualCount(RoleType.DEMON);

        String counts;
        if (base == null) {
            counts = "Actual setup: " + townsfolk + " Townsfolk   •   " + outsiders + " Outsider   •   "
                    + minions + " Minion   •   " + demons + " Demon";
        } else {
            SetupExpectation expected = setupExpectation(base, outsiders);
            counts = "Actual setup: "
                    + countWithTarget(townsfolk, expected.townsfolkTarget()) + " Townsfolk   •   "
                    + countWithTarget(outsiders, expected.outsiderTarget()) + " Outsider   •   "
                    + minions + "/" + base.minions() + " Minion   •   "
                    + demons + "/" + base.demons() + " Demon";
        }
        drawCentered(graphics, counts, 34, UiDrawing.MUTED, false);

        OutsiderSetup outsiderSetup = outsiderSetup();
        if (base != null && (outsiderSetup.unknown() || !outsiderSetup.values().equals(Set.of(0)))) {
            String setupText;
            if (outsiderSetup.unknown()) {
                setupText = "Outsider setup modifier: ?  •  choose the composition manually";
            } else {
                int min = outsiderSetup.values().stream().min(Integer::compareTo).orElse(0);
                int max = outsiderSetup.values().stream().max(Integer::compareTo).orElse(0);
                int chosen = (int) outsiders - base.outsiders();
                String range = min == max ? signed(min) : signed(min) + " to " + signed(max);
                setupText = "Outsider setup modifier: " + range;
                if (outsiderSetup.values().contains(chosen)) setupText += "  •  current " + signed(chosen);
            }
            drawCentered(graphics, setupText, 46, UiDrawing.MUTED, false);
        }

        if (hiddenSetupActive()) {
            StringBuilder hidden = new StringBuilder("Hidden setup: ");
            if (drunkReserved) hidden.append("Drunk");
            if (drunkReserved && marionetteReserved) hidden.append(" + ");
            if (marionetteReserved) hidden.append("Marionette");
            hidden.append("  •  use Townsfolk cover token");
            if (drunkReserved && marionetteReserved) hidden.append("s");
            hidden.append("; assign actual role after deal");
            drawCentered(graphics, hidden.toString(), 58, UiDrawing.GOLD, false);
        }
    }

    private long count(RoleType type) {
        if (ClientState.currentScript == null) return 0;
        return SELECTED.stream()
                .map(ClientState.currentScript::getScriptRole)
                .flatMap(java.util.Optional::stream)
                .filter(role -> role.getTeam() == type)
                .count();
    }

    private long actualCount(RoleType type) {
        long selected = count(type);
        int hiddenCovers = (drunkReserved ? 1 : 0) + (marionetteReserved ? 1 : 0);
        if (type == RoleType.TOWNSFOLK) return Math.max(0, selected - hiddenCovers);
        if (type == RoleType.OUTSIDER) return selected + (drunkReserved ? 1 : 0);
        if (type == RoleType.MINION) return selected + (marionetteReserved ? 1 : 0);
        return selected;
    }

    private SetupExpectation setupExpectation(RoleCounts.RoleCountInfo base, long actualOutsiders) {
        OutsiderSetup modifiers = outsiderSetup();
        if (modifiers.unknown()) return new SetupExpectation("?", "?");

        int chosen = (int) actualOutsiders - base.outsiders();
        if (modifiers.values().contains(chosen)) {
            return new SetupExpectation(Integer.toString(base.townsfolk() - chosen), Integer.toString(base.outsiders() + chosen));
        }

        int min = modifiers.values().stream().min(Integer::compareTo).orElse(0);
        int max = modifiers.values().stream().max(Integer::compareTo).orElse(0);
        String outsiderTarget = targetRange(base.outsiders() + min, base.outsiders() + max);
        String townsfolkTarget = targetRange(base.townsfolk() - max, base.townsfolk() - min);
        return new SetupExpectation(townsfolkTarget, outsiderTarget);
    }

    private OutsiderSetup outsiderSetup() {
        Set<Integer> totals = new TreeSet<>();
        totals.add(0);
        if (ClientState.currentScript == null) return new OutsiderSetup(totals, false);

        for (String id : SELECTED) {
            ScriptRole role = ClientState.currentScript.getScriptRole(id).orElse(null);
            if (role == null) continue;
            RoleOutsiderModifier modifier = roleOutsiderModifier(role);
            if (modifier.unknown()) return new OutsiderSetup(Set.of(), true);
            if (modifier.values().equals(Set.of(0))) continue;

            Set<Integer> next = new TreeSet<>();
            for (int current : totals) {
                for (int add : modifier.values()) next.add(current + add);
            }
            totals = next;
        }
        return new OutsiderSetup(totals, false);
    }

    private RoleOutsiderModifier roleOutsiderModifier(ScriptRole role) {
        // Huntsman uses [+the Damsel] rather than a numeric Outsider marker.
        if (normalizedId(role).equals("huntsman")) return new RoleOutsiderModifier(Set.of(1), false);

        String ability = role.getAbility();
        if (ability == null || ability.isBlank()) return new RoleOutsiderModifier(Set.of(0), false);
        Matcher bracketMatcher = OUTSIDER_BRACKET.matcher(ability);
        Set<Integer> combined = new TreeSet<>();
        combined.add(0);
        boolean found = false;

        while (bracketMatcher.find()) {
            found = true;
            String setup = bracketMatcher.group(1).trim();
            if (setup.contains("?")) return new RoleOutsiderModifier(Set.of(), true);

            Set<Integer> values = new TreeSet<>();
            Matcher range = OUTSIDER_RANGE.matcher(setup);
            if (range.find()) {
                int a = Integer.parseInt(range.group(1));
                int b = Integer.parseInt(range.group(2));
                if (setup.toLowerCase(Locale.ROOT).contains(" to ")) {
                    for (int value = Math.min(a, b); value <= Math.max(a, b); value++) values.add(value);
                } else {
                    values.add(a);
                    values.add(b);
                }
            } else {
                Matcher fixed = OUTSIDER_FIXED.matcher(setup);
                if (fixed.find()) values.add(Integer.parseInt(fixed.group(1)));
            }

            if (values.isEmpty()) continue;
            Set<Integer> next = new TreeSet<>();
            for (int current : combined) {
                for (int add : values) next.add(current + add);
            }
            combined = next;
        }

        return found ? new RoleOutsiderModifier(combined, false) : new RoleOutsiderModifier(Set.of(0), false);
    }

    private static String countWithTarget(long count, String target) {
        return count + "/" + target;
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String targetRange(int min, int max) {
        if (min == max) return Integer.toString(min);
        return Math.min(min, max) + "–" + Math.max(min, max);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, y, colour, shadow);
    }

    private record RoleOutsiderModifier(Set<Integer> values, boolean unknown) {}
    private record OutsiderSetup(Set<Integer> values, boolean unknown) {}
    private record SetupExpectation(String townsfolkTarget, String outsiderTarget) {}
}
