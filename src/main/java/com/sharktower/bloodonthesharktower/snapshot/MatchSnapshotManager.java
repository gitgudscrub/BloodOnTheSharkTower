package com.sharktower.bloodonthesharktower.snapshot;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.Script;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A.6 start-of-match checkpoint.
 *
 * The checkpoint is deliberately immutable for the duration of a match: deaths,
 * votes, block state, private chat and other runtime changes never write back to
 * it. Full Reset / Game Complete restore this clean baseline.
 *
 * World capture uses vanilla structure files in 48-block tiles. It preserves
 * blocks, block entities and non-player map entities; players are never cloned.
 * Runtime non-player entities in the restore region are cleared before the
 * clean snapshot entities are placed back.
 */
public final class MatchSnapshotManager {
    private static final int TILE_SIZE = 48;
    // Snapshot corners are selected while standing in the playable area. Treat
    // them as horizontal map corners and include a useful vertical band so the
    // floor, basements and nearby buildings are actually captured.
    private static final int DEFAULT_VERTICAL_BELOW = 16;
    private static final int DEFAULT_VERTICAL_ABOVE = 48;
    private static final String CURRENT_ROOT = "match_current";
    private static final String PREVIOUS_ROOT = "match_previous";

    public record Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static Region between(int x1, int y1, int z1, int x2, int y2, int z2) {
            return new Region(
                    Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                    Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
            );
        }

        public long blockCount() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    public record Result(boolean ok, String message) {
        public static Result ok(String message) { return new Result(true, message); }
        public static Result fail(String message) { return new Result(false, message); }
    }

    private record SetupSnapshot(
            Map<UUID, Integer> seats,
            Map<UUID, PendingRoleAssignment> roles,
            Map<UUID, PendingRoleAssignment> perceivedRoles,
            Map<UUID, Boolean> deathStatus,
            Script script,
            Set<UUID> storytellers,
            Map<UUID, List<Reminder>> reminders,
            List<ScriptRole> bluffs,
            SeatPositionManager.Configuration seatConfiguration
    ) {}

    private static int corner1X, corner1Y, corner1Z;
    private static int corner2X, corner2Y, corner2Z;
    private static boolean hasCorner1;
    private static boolean hasCorner2;

    private static SetupSnapshot currentSetup;
    private static SetupSnapshot previousSetup;
    private static Region currentWorldRegion;
    private static Region previousWorldRegion;

    private MatchSnapshotManager() {}

    public static synchronized Result setCorner1(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) return Result.fail("Server/player is not available.");
        if (player.level() != server.overworld()) {
            return Result.fail("A.6 world snapshots currently use the Overworld. Set the restore corners there.");
        }
        corner1X = floor(player.getX());
        corner1Y = floor(player.getY());
        corner1Z = floor(player.getZ());
        hasCorner1 = true;
        return Result.ok("Snapshot corner 1 set to " + coords(corner1X, corner1Y, corner1Z) + ".");
    }

    public static synchronized Result setCorner2(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) return Result.fail("Server/player is not available.");
        if (player.level() != server.overworld()) {
            return Result.fail("A.6 world snapshots currently use the Overworld. Set the restore corners there.");
        }
        corner2X = floor(player.getX());
        corner2Y = floor(player.getY());
        corner2Z = floor(player.getZ());
        hasCorner2 = true;
        return Result.ok("Snapshot corner 2 set to " + coords(corner2X, corner2Y, corner2Z) + ".");
    }

    public static synchronized Result clearRegion() {
        hasCorner1 = false;
        hasCorner2 = false;
        return Result.ok("World snapshot region cleared. Game-state snapshots will still be created.");
    }

    public static synchronized Region configuredRegion() {
        if (!hasCorner1 || !hasCorner2) return null;

        // Players naturally set both corners while standing on the ground. Using
        // the raw feet Y coordinate made the old region one block tall (usually
        // an air layer), so world restoration appeared to succeed while almost no
        // map blocks had actually been captured. Expand the vertical range around
        // the selected horizontal corners instead.
        int minY = Math.min(corner1Y, corner2Y) - DEFAULT_VERTICAL_BELOW;
        int maxY = Math.max(corner1Y, corner2Y) + DEFAULT_VERTICAL_ABOVE;
        return Region.between(corner1X, minY, corner1Z, corner2X, maxY, corner2Z);
    }

    public static synchronized boolean hasSnapshot() {
        return currentSetup != null;
    }

    /**
     * Refresh only the mutable setup portion of the current start checkpoint.
     * Used by setup-only roster cleanup so a disconnected player is not restored
     * by Reset for Next Game. The world snapshot and previous-game checkpoint are
     * intentionally left untouched.
     */
    public static synchronized boolean refreshCurrentSetupState() {
        if (currentSetup == null) return false;
        currentSetup = captureSetup();
        return true;
    }

    public static synchronized String status() {
        Region configured = configuredRegion();
        String regionText = configured == null
                ? "not configured"
                : coords(configured.minX, configured.minY, configured.minZ) + " -> "
                + coords(configured.maxX, configured.maxY, configured.maxZ)
                + " (" + configured.blockCount() + " blocks)";
        return "snapshot=" + (currentSetup == null ? "none" : "ready")
                + ", previous=" + (previousSetup == null ? "none" : "ready")
                + ", worldRegion=" + regionText
                + ", capturedWorld=" + (currentWorldRegion == null ? "no" : "yes");
    }

    /** Called immediately after setup becomes authoritative. */
    public static synchronized Result captureAtGameStart(MinecraftServer server) {
        if (server == null) return Result.fail("Server is not available.");

        if (currentSetup != null) {
            previousSetup = currentSetup;
            previousWorldRegion = currentWorldRegion;
            try {
                copyStructureTree(server, CURRENT_ROOT, PREVIOUS_ROOT);
            } catch (IOException exception) {
                BloodOnTheSharktower.LOGGER.warn("Could not rotate previous match world snapshot", exception);
            }
        }

        currentSetup = captureSetup();
        Region region = configuredRegion();
        currentWorldRegion = null;

        if (region == null) {
            return Result.ok("Start-of-game snapshot created. World restore is disabled until both snapshot corners are set.");
        }

        try {
            saveRegion(server, region, CURRENT_ROOT);
            currentWorldRegion = region;
            return Result.ok("Start-of-game snapshot created, including " + region.blockCount()
                    + " world block(s) across " + tileCount(region) + " structure tile(s).");
        } catch (RuntimeException exception) {
            BloodOnTheSharktower.LOGGER.error("Could not capture match world snapshot", exception);
            return Result.ok("Game-state snapshot created, but the world region could not be captured: " + exception.getMessage());
        }
    }

    public static synchronized Result restoreCurrent(MinecraftServer server) {
        return restore(server, currentSetup, currentWorldRegion, CURRENT_ROOT, "start-of-game");
    }

    public static synchronized Result restorePrevious(MinecraftServer server) {
        return restore(server, previousSetup, previousWorldRegion, PREVIOUS_ROOT, "previous-game");
    }

    private static Result restore(
            MinecraftServer server,
            SetupSnapshot setup,
            Region worldRegion,
            String structureRoot,
            String label
    ) {
        if (server == null) return Result.fail("Server is not available.");
        if (setup == null) return Result.fail("No " + label + " snapshot exists.");

        restoreSetup(setup);
        if (worldRegion != null) {
            try {
                loadRegion(server, worldRegion, structureRoot);
            } catch (RuntimeException exception) {
                BloodOnTheSharktower.LOGGER.error("Could not restore {} world snapshot", label, exception);
                return Result.fail("Restored the Sharktower setup, but world restore failed: " + exception.getMessage());
            }
        }
        return Result.ok("Restored the " + label + " snapshot"
                + (worldRegion == null ? "." : " including the configured world region."));
    }

    private static SetupSnapshot captureSetup() {
        Map<UUID, List<Reminder>> reminders = new HashMap<>();
        StorytellerState.REMINDERS.forEach((id, list) -> reminders.put(id, new ArrayList<>(list)));
        return new SetupSnapshot(
                new HashMap<>(ServerState.PLAYER_SEAT_NUMBERS),
                new HashMap<>(ServerState.PLAYER_ROLES),
                new HashMap<>(ServerState.PLAYER_PERCEIVED_ROLES),
                new HashMap<>(ServerState.PLAYER_DEATH_STATUS),
                ServerState.currentScript,
                new HashSet<>(StorytellerState.STORYTELLERS),
                reminders,
                new ArrayList<>(StorytellerState.DEMON_BLUFFS),
                SeatPositionManager.snapshotConfiguration()
        );
    }

    private static void restoreSetup(SetupSnapshot snapshot) {
        ServerState.updateSeats(snapshot.seats());
        ServerState.updateRoles(snapshot.roles());
        ServerState.updatePerceivedRoles(snapshot.perceivedRoles());
        ServerState.updateDeathStatus(snapshot.deathStatus());
        ServerState.currentScript = snapshot.script();
        ServerState.currentNight = 0;
        ServerState.currentDay = 0;
        ServerState.executionToday = false;
        ServerState.gameEnded = false;
        ServerState.winningTeam = "NONE";
        ServerState.rolesRevealed = false;

        StorytellerState.clearSetupState();
        StorytellerState.STORYTELLERS.clear();
        StorytellerState.STORYTELLERS.addAll(snapshot.storytellers());
        snapshot.reminders().forEach((id, list) -> StorytellerState.REMINDERS.put(id, new ArrayList<>(list)));
        StorytellerState.DEMON_BLUFFS.addAll(snapshot.bluffs());
        SeatPositionManager.restoreConfiguration(snapshot.seatConfiguration());
    }

    private static void saveRegion(MinecraftServer server, Region region, String root) {
        StructureTemplateManager manager = server.getStructureTemplateManager();
        int index = 0;
        for (int x = region.minX; x <= region.maxX; x += TILE_SIZE) {
            int x2 = Math.min(region.maxX, x + TILE_SIZE - 1);
            for (int y = region.minY; y <= region.maxY; y += TILE_SIZE) {
                int y2 = Math.min(region.maxY, y + TILE_SIZE - 1);
                for (int z = region.minZ; z <= region.maxZ; z += TILE_SIZE) {
                    int z2 = Math.min(region.maxZ, z + TILE_SIZE - 1);
                    Identifier id = structureId(root, index++);
                    StructureTemplate template = manager.getOrCreate(id);
                    template.fillFromWorld(
                            server.overworld(),
                            new BlockPos(x, y, z),
                            new Vec3i(x2 - x + 1, y2 - y + 1, z2 - z + 1),
                            true,
                            List.of()
                    );
                    if (!manager.save(id)) {
                        throw new IllegalStateException("Could not save world snapshot tile " + id);
                    }
                }
            }
        }
    }

    private static void loadRegion(MinecraftServer server, Region region, String root) {
        StructureTemplateManager manager = server.getStructureTemplateManager();
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(false);
        clearRuntimeEntities(server, region);
        int index = 0;
        for (int x = region.minX; x <= region.maxX; x += TILE_SIZE) {
            for (int y = region.minY; y <= region.maxY; y += TILE_SIZE) {
                for (int z = region.minZ; z <= region.maxZ; z += TILE_SIZE) {
                    Identifier id = structureId(root, index++);
                    // The previous snapshot may have been created by rotating files,
                    // so evict any cached template before reading it from disk.
                    if (PREVIOUS_ROOT.equals(root)) manager.remove(id);
                    StructureTemplate template = manager.get(id)
                            .orElseThrow(() -> new IllegalStateException("Missing world snapshot tile " + id));

                    // Clear the exact tile before placement. Structure templates are
                    // excellent at putting captured blocks back, but relying on them
                    // alone can leave blocks that were *added* after the snapshot if
                    // an air position was not represented the way we expect. Clearing
                    // first guarantees both destruction and construction are rolled
                    // back to the checkpoint. This all happens on the server thread
                    // immediately before the clean tile is placed.
                    int x2 = Math.min(region.maxX, x + TILE_SIZE - 1);
                    int y2 = Math.min(region.maxY, y + TILE_SIZE - 1);
                    int z2 = Math.min(region.maxZ, z + TILE_SIZE - 1);
                    clearTile(server, x, y, z, x2, y2, z2);

                    BlockPos origin = new BlockPos(x, y, z);
                    boolean placed = template.placeInWorld(
                            server.overworld(),
                            origin,
                            origin,
                            settings,
                            RandomSource.create(),
                            3
                    );
                    if (!placed) throw new IllegalStateException("Could not restore world snapshot tile " + id);
                }
            }
        }
    }


    private static void clearTile(
            MinecraftServer server,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ
    ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (!server.overworld().getBlockState(cursor).isAir()) {
                        // Flag 2 sends the client update without expensive neighbour
                        // cascades for every temporary clear. Structure placement
                        // immediately follows and uses its normal update flags.
                        server.overworld().setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static void clearRuntimeEntities(MinecraftServer server, Region region) {
        AABB box = AABB.encapsulatingFullBlocks(
                new BlockPos(region.minX, region.minY, region.minZ),
                new BlockPos(region.maxX, region.maxY, region.maxZ)
        );
        List<Entity> entities = server.overworld().getEntitiesOfClass(
                Entity.class,
                box,
                entity -> !(entity instanceof Player)
        );
        for (Entity entity : entities) entity.discard();
    }

    private static int tileCount(Region region) {
        int x = (region.maxX - region.minX + TILE_SIZE) / TILE_SIZE;
        int y = (region.maxY - region.minY + TILE_SIZE) / TILE_SIZE;
        int z = (region.maxZ - region.minZ + TILE_SIZE) / TILE_SIZE;
        return x * y * z;
    }

    private static Identifier structureId(String root, int index) {
        return Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID, root + "/tile_" + index);
    }

    private static Path structureRoot(MinecraftServer server, String root) {
        return server.getWorldPath(LevelResource.GENERATED_DIR)
                .resolve(BloodOnTheSharktower.MOD_ID)
                .resolve("structures")
                .resolve(root);
    }

    private static void copyStructureTree(MinecraftServer server, String from, String to) throws IOException {
        Path source = structureRoot(server, from);
        if (!Files.exists(source)) return;
        Path target = structureRoot(server, to);
        deleteTree(target);
        try (var stream = Files.walk(source)) {
            for (Path path : stream.sorted(Comparator.naturalOrder()).toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static String coords(int x, int y, int z) {
        return x + ", " + y + ", " + z;
    }
}
