package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.RoleCounts;
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
import java.util.Set;

/**
 * Blood on the Sharktower role bag.
 *
 * The Storyteller chooses the exact characters in the bag, then the server
 * shuffles those characters across the currently seated players. This mirrors
 * the BOTC app workflow: bag composition is deliberate; player assignment is
 * random. The result remains pending in the Grimoire until SEND ROLES.
 */
public final class RoleBagScreen extends Screen {
    private static final int COLUMNS = 5;
    private static final int CELL_W = 82;
    private static final int CELL_H = 61;
    private static final int GAP_X = 5;
    private static final int GAP_Y = 4;

    private static final Set<String> SELECTED = new LinkedHashSet<>();
    private static String scriptIdentity = "";
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

        int top = 54;
        int bottomReserve = 78;
        int rows = Math.max(1, (this.height - top - bottomReserve) / (CELL_H + GAP_Y));
        int perPage = rows * COLUMNS;
        int maxPage = Math.max(0, (roles.size() - 1) / perPage);
        if (page > maxPage) page = maxPage;

        int gridWidth = COLUMNS * CELL_W + (COLUMNS - 1) * GAP_X;
        int left = (this.width - gridWidth) / 2;
        int start = page * perPage;
        int end = Math.min(roles.size(), start + perPage);

        for (int i = start; i < end; i++) {
            ScriptRole role = roles.get(i);
            int local = i - start;
            int col = local % COLUMNS;
            int row = local / COLUMNS;
            int x = left + col * (CELL_W + GAP_X);
            int y = top + row * (CELL_H + GAP_Y);
            this.addRenderableWidget(new RoleBagRoleWidget(
                    x, y, CELL_W, CELL_H, role,
                    () -> SELECTED.contains(role.getId()),
                    () -> {
                        if (!SELECTED.add(role.getId())) SELECTED.remove(role.getId());
                        this.minecraft.gui.setScreen(new RoleBagScreen(page));
                    }
            ));
        }

        int navY = this.height - 66;
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                    if (page > 0) this.minecraft.gui.setScreen(new RoleBagScreen(page - 1));
                }).bounds(this.width / 2 - 220, navY, 36, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                    if (page < maxPage) this.minecraft.gui.setScreen(new RoleBagScreen(page + 1));
                }).bounds(this.width / 2 - 178, navY, 36, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
                    SELECTED.clear();
                    this.minecraft.gui.setScreen(new RoleBagScreen(page));
                }).bounds(this.width / 2 - 132, navY, 72, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Current Setup"), b -> {
                    selectCurrentSetup();
                    this.minecraft.gui.setScreen(new RoleBagScreen(page));
                }).bounds(this.width / 2 - 54, navY, 105, 20).build());

        boolean ready = seatedCount() > 0 && SELECTED.size() == seatedCount();
        Button distribute = Button.builder(
                        Component.literal(ready ? "Distribute Bag" : "Need " + seatedCount() + " roles")
                                .withStyle(ready ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY),
                        b -> distribute())
                .bounds(this.width / 2 + 57, navY, 128, 20).build();
        distribute.active = ready;
        this.addRenderableWidget(distribute);

        this.addRenderableWidget(Button.builder(Component.literal("Back to Grimoire"), b ->
                        this.minecraft.gui.setScreen(new AssignRolesScreen()))
                .bounds(this.width / 2 - 90, this.height - 31, 180, 20).build());
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
            SELECTED.clear();
            scriptIdentity = identity;
            selectCurrentSetup();
        } else {
            Set<String> valid = new java.util.HashSet<>();
            for (ScriptRole role : roles()) valid.add(role.getId());
            SELECTED.removeIf(id -> !valid.contains(id));
        }
    }

    private void selectCurrentSetup() {
        SELECTED.clear();
        if (ClientState.currentScript == null) return;
        for (PendingRoleAssignment assignment : ClientState.grimoireRoles.values()) {
            if (assignment == null) continue;
            String id = assignment.getRoleId();
            if (id == null || id.isBlank() || "norole".equalsIgnoreCase(id)) continue;
            ClientState.currentScript.getScriptRole(id).ifPresent(role -> {
                if (isBagRole(role)) SELECTED.add(role.getId());
            });
        }
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
        drawCentered(graphics, title, 10, UiDrawing.GOLD, true);

        int seated = seatedCount();
        int selected = SELECTED.size();
        String status = selected + "/" + seated + " roles selected";
        drawCentered(graphics, status, 25, selected == seated && seated > 0 ? UiDrawing.GOLD : UiDrawing.TEXT, false);

        long townsfolk = count(RoleType.TOWNSFOLK);
        long outsiders = count(RoleType.OUTSIDER);
        long minions = count(RoleType.MINION);
        long demons = count(RoleType.DEMON);
        RoleCounts.RoleCountInfo expected = RoleCounts.getCounts(seated);
        String counts;
        if (expected == null) {
            counts = townsfolk + " Townsfolk   •   " + outsiders + " Outsider   •   "
                    + minions + " Minion   •   " + demons + " Demon";
        } else {
            counts = townsfolk + "/" + expected.townsfolk() + " Townsfolk   •   "
                    + outsiders + "/" + expected.outsiders() + " Outsider   •   "
                    + minions + "/" + expected.minions() + " Minion   •   "
                    + demons + "/" + expected.demons() + " Demon";
        }
        drawCentered(graphics, counts, 38, UiDrawing.MUTED, false);
    }

    private long count(RoleType type) {
        if (ClientState.currentScript == null) return 0;
        return SELECTED.stream()
                .map(ClientState.currentScript::getScriptRole)
                .flatMap(java.util.Optional::stream)
                .filter(role -> role.getTeam() == type)
                .count();
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, y, colour, shadow);
    }
}
