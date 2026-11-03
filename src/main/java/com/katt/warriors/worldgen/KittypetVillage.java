package com.katt.warriors.worldgen;

import com.katt.warriors.data.RebirthChoice.Clan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The Kittypet village: a proper little twoleg town, generated together with
 * the terrain.
 *
 * <p>The layout is planned once, deterministically: a plaza with a well and
 * market stalls at the centre, two winding main streets, a ring road with four
 * spurs, and around them gabled houses, cottages, long houses, a library, a
 * smithy, a hall, a barn, fenced farm plots and street lamps. Every building
 * is a pure function of its local coordinates, so each chunk writes only its
 * own slice and neighbouring chunks always agree, with no loading, no
 * "already built" marker and no structure registry.</p>
 *
 * <p>The terrain under the village is levelled by {@link ClanlandsTerrain}, so
 * the whole layout sits at {@link #GROUND_Y}.</p>
 */
public final class KittypetVillage {

    /**
     * Centre of the village, set when the plan is built. The village stands in
     * the first territory whose terrain is TOWN; the plan is rebuilt whenever
     * the world's clan layout changes.
     */
    public static volatile int centerX;
    public static volatile int centerZ;
    public static final int GROUND_Y = ClanlandsTerrain.VILLAGE_Y;

    /** Nothing is built further than this from the centre. */
    public static final int RADIUS = 58;

    private static final int PLAZA_RADIUS = 8;
    private static final int RING_RADIUS = 31;

    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();

    private static volatile Plan plan;
    private static volatile ClanlandsLayout planLayout;

    /** The village plan for the current layout, or {@code null} if there is no town. */
    private static Plan plan() {
        ClanlandsLayout layout = ClanlandsLayout.current();
        if (layout.village() == null) {
            return null;
        }
        if (planLayout != layout) {
            synchronized (KittypetVillage.class) {
                if (planLayout != layout) {
                    centerX = layout.village().x();
                    centerZ = layout.village().z();
                    plan = buildPlan();
                    planLayout = layout;
                }
            }
        }
        return plan;
    }

    /** Whether the village stands in this clan's territory. */
    public static boolean isVillageClan(Clan clan) {
        ClanlandsLayout.Site village = ClanlandsLayout.current().village();
        return village != null && village.clan() == clan;
    }

    private KittypetVillage() {}

    /** Where Kittypet players wake up: on the plaza, a few steps from the well. */
    public static BlockPos spawnPos() {
        plan();
        return new BlockPos(centerX, GROUND_Y + 1, centerZ + 4);
    }

    // ── Chunk entry point ─────────────────────────────────────────────────────

    /** Writes the part of the village that lies in this chunk (if any). */
    public static void generate(ChunkAccess chunk, Heightmap oceanFloor, Heightmap worldSurface) {
        Plan village = plan();
        if (village == null) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int limit = RADIUS + 3;

        if (minX + 15 < centerX - limit || minX > centerX + limit
                || minZ + 15 < centerZ - limit || minZ > centerZ + limit) {
            return;
        }

        Placer placer = new Placer(chunk, oceanFloor, worldSurface, minX, minZ);

        // Streets and the plaza first, so buildings can overwrite them.
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = minX + lx;
                int z = minZ + lz;
                if (village.paths.contains(key(x, z))) {
                    placer.set(x, GROUND_Y, z, Blocks.DIRT_PATH.defaultBlockState());
                } else if (roadCell(x, z)) {
                    placer.set(x, GROUND_Y, z, roadState(x, z));
                }
            }
        }

        for (Piece piece : village.pieces) {
            if (piece.intersects(minX, minZ)) {
                piece.place(placer);
            }
        }
    }

    // ── Roads ─────────────────────────────────────────────────────────────────

    private static boolean roadCell(int x, int z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r > RADIUS - 2) {
            return false;
        }
        if (r <= PLAZA_RADIUS) {
            return true;
        }

        // The two main streets wind gently.
        if (Math.abs(dz - 3.0 * Math.sin(dx * 0.07)) <= 1.6) {
            return true;
        }
        if (Math.abs(dx - 3.0 * Math.sin(dz * 0.06 + 1.0)) <= 1.6) {
            return true;
        }

        // The ring road, slightly wavy.
        double angle = Math.atan2(dz, dx);
        if (Math.abs(r - (RING_RADIUS + 1.5 * Math.sin(angle * 3.0))) <= 1.5) {
            return true;
        }

        // Four spurs leading out of the ring.
        if (r >= RING_RADIUS - 2) {
            for (int k = 0; k < 4; k++) {
                double a = Math.toRadians(45.0 + 90.0 * k);
                double along = dx * Math.cos(a) + dz * Math.sin(a);
                double across = -dx * Math.sin(a) + dz * Math.cos(a);
                if (along >= RING_RADIUS - 2 && Math.abs(across) <= 1.5) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BlockState roadState(int x, int z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        double r = Math.sqrt(dx * dx + dz * dz);
        int roll = hash(x, z) % 100;

        if (r <= PLAZA_RADIUS) {
            if (r > PLAZA_RADIUS - 1.2) {
                return Blocks.STONE_BRICKS.defaultBlockState();
            }
            if (roll < 55) {
                return COBBLE;
            }
            if (roll < 80) {
                return Blocks.STONE_BRICKS.defaultBlockState();
            }
            if (roll < 92) {
                return Blocks.ANDESITE.defaultBlockState();
            }
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }

        if (roll < 78) {
            return Blocks.DIRT_PATH.defaultBlockState();
        }
        if (roll < 89) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        if (roll < 96) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        return Blocks.DIRT_PATH.defaultBlockState();
    }

    // ── Planning ──────────────────────────────────────────────────────────────

    private static final class Plan {
        final List<Piece> pieces = new ArrayList<>();
        final Set<Long> paths = new HashSet<>();
        int libraries;
        int smithies;
        int barns;
        int halls;
    }

    private enum Kind { COTTAGE, HOUSE, LONGHOUSE, LIBRARY, SMITHY, HALL, BARN }

    private static Plan buildPlan() {
        Plan plan = new Plan();
        Random random = new Random(0x4B17A9E5L);

        plan.pieces.add(new Well());

        // Market stalls on the corners of the plaza.
        int[][] stalls = {{-5, 5}, {5, 5}, {-5, -5}, {5, -5}};
        for (int[] stall : stalls) {
            plan.pieces.add(new Stall(centerX + stall[0], centerZ + stall[1],
                    (stall[0] + stall[1]) % 2 == 0));
        }

        // The hall: the biggest building, just outside the ring road where there
        // is room for it, facing the village.
        int[][] hallSpots = {{13, -42}, {-13, -42}, {42, 13}, {-42, 13},
                {13, 42}, {-13, 42}, {42, -13}, {-42, -13},
                {17, -12}, {-17, -12}, {17, 12}, {-17, 12}};
        for (int[] spot : hallSpots) {
            if (plan.halls == 0 && tryBuilding(plan, random, Kind.HALL,
                    centerX + spot[0], centerZ + spot[1])) {
                plan.halls++;
            }
        }

        // Two barns out by the fields.
        int[][] barnSpots = {{-45, -27}, {45, 27}, {27, -45}, {-27, 45},
                {-47, 20}, {47, -20}};
        for (int[] spot : barnSpots) {
            if (plan.barns < 2) {
                tryBuilding(plan, random, Kind.BARN, centerX + spot[0], centerZ + spot[1]);
            }
        }

        // Houses around the plaza and along the outer ring, in two passes so
        // gaps left by the first one get filled.
        int[] radii = {15, 19, 24, 37, 41, 46, 50};
        for (int pass = 0; pass < 2; pass++) {
            for (int radius : radii) {
                int count = (int) (2.0 * Math.PI * radius / 9.0);
                double start = random.nextDouble() * 360.0;
                for (int i = 0; i < count; i++) {
                    double angle = Math.toRadians(start + i * 360.0 / count + random.nextInt(9) - 4);
                    int x = centerX + (int) Math.round(radius * Math.cos(angle)) + random.nextInt(3) - 1;
                    int z = centerZ + (int) Math.round(radius * Math.sin(angle)) + random.nextInt(3) - 1;

                    if (radius >= 37 && random.nextInt(100) < 26) {
                        tryFarm(plan, random, x, z);
                    } else {
                        tryBuilding(plan, random, pickKind(plan, radius, random), x, z);
                    }
                }
            }
        }

        // Street lamps along the main streets and the ring road.
        addLamps(plan);

        return plan;
    }

    private static Kind pickKind(Plan plan, int radius, Random random) {
        int roll = random.nextInt(100);
        if (radius < 30) {
            if (roll < 30 && plan.libraries == 0) {
                return Kind.LIBRARY;
            }
            if (roll < 60 && plan.smithies == 0) {
                return Kind.SMITHY;
            }
            if (roll < 55) {
                return Kind.COTTAGE;
            }
            if (roll < 88) {
                return Kind.HOUSE;
            }
            return Kind.LONGHOUSE;
        }
        if (roll < 30 && plan.barns < 2) {
            return Kind.BARN;
        }
        if (roll < 45) {
            return Kind.COTTAGE;
        }
        if (roll < 78) {
            return Kind.HOUSE;
        }
        return Kind.LONGHOUSE;
    }

    /** Places a building facing the village centre, if it fits and has a way to a street. */
    private static boolean tryBuilding(Plan plan, Random random, Kind kind, int x, int z) {
        int width;
        int depth;
        int wallHeight = 3;
        switch (kind) {
            case COTTAGE -> { width = 5; depth = random.nextBoolean() ? 5 : 7; }
            case HOUSE -> { width = 7; depth = random.nextBoolean() ? 7 : 9; }
            case LONGHOUSE -> { width = 7; depth = random.nextBoolean() ? 11 : 13; }
            case LIBRARY -> { width = 7; depth = 9; wallHeight = 4; }
            case SMITHY -> { width = 7; depth = 7; }
            case HALL -> { width = 9; depth = 15; wallHeight = 5; }
            default -> { width = 9; depth = 11; wallHeight = 4; }
        }

        // Face the centre along the dominant axis.
        double dx = centerX - x;
        double dz = centerZ - z;
        int fx = 0;
        int fz = 0;
        if (Math.abs(dx) > Math.abs(dz)) {
            fx = dx > 0 ? 1 : -1;
        } else {
            fz = dz > 0 ? 1 : -1;
        }

        Palette palette = switch (kind) {
            case HALL -> PALETTES[4];
            case BARN -> PALETTES[1];
            default -> PALETTES[random.nextInt(4)];
        };

        Building building = new Building(kind, width, depth, wallHeight, x, z, fx, fz,
                palette, random.nextInt(5), kind != Kind.BARN && kind != Kind.HALL);

        if (!fits(plan, building)) {
            return false;
        }

        // The door must connect to a street with a short path.
        List<Long> path = pathToRoad(plan, building);
        if (path == null) {
            return false;
        }

        plan.paths.addAll(path);
        plan.pieces.add(building);
        switch (kind) {
            case LIBRARY -> plan.libraries++;
            case SMITHY -> plan.smithies++;
            case BARN -> plan.barns++;
            default -> { }
        }
        return true;
    }

    private static void tryFarm(Plan plan, Random random, int x, int z) {
        Farm farm = new Farm(x, z, random.nextBoolean());
        if (fits(plan, farm)) {
            plan.pieces.add(farm);
        }
    }

    private static boolean fits(Plan plan, Piece candidate) {
        int[][] corners = {
                {candidate.minX, candidate.minZ}, {candidate.maxX, candidate.minZ},
                {candidate.minX, candidate.maxZ}, {candidate.maxX, candidate.maxZ}};
        for (int[] corner : corners) {
            double dx = corner[0] - centerX;
            double dz = corner[1] - centerZ;
            if (Math.sqrt(dx * dx + dz * dz) > RADIUS - 4) {
                return false;
            }
        }

        for (Piece other : plan.pieces) {
            if (candidate.minX - 2 <= other.maxX && candidate.maxX + 2 >= other.minX
                    && candidate.minZ - 2 <= other.maxZ && candidate.maxZ + 2 >= other.minZ) {
                return false;
            }
        }

        for (int x = candidate.minX - 1; x <= candidate.maxX + 1; x++) {
            for (int z = candidate.minZ - 1; z <= candidate.maxZ + 1; z++) {
                if (roadCell(x, z) || plan.paths.contains(key(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Cells from the door out to the nearest street, or {@code null} if none is close. */
    private static List<Long> pathToRoad(Plan plan, Building building) {
        int mid = (building.w - 1) / 2;
        int startX = building.wx(mid, -1);
        int startZ = building.wz(mid, -1);
        int stepX = -building.b;   // local -v points out of the front door
        int stepZ = -building.e;

        List<Long> cells = new ArrayList<>();
        for (int k = 0; k < 14; k++) {
            int x = startX + stepX * k;
            int z = startZ + stepZ * k;

            if (roadCell(x, z)) {
                return cells;
            }
            for (Piece piece : plan.pieces) {
                if (x >= piece.minX && x <= piece.maxX && z >= piece.minZ && z <= piece.maxZ) {
                    return null;
                }
            }
            cells.add(key(x, z));
        }
        return null;
    }

    private static void addLamps(Plan plan) {
        for (int t = -48; t <= 48; t += 12) {
            if (Math.abs(t) < 11) {
                continue;
            }
            // East-west street, south side; north-south street, east side.
            addLamp(plan, centerX + t, (int) Math.round(centerZ + 3.0 * Math.sin(t * 0.07)) + 3);
            addLamp(plan, (int) Math.round(centerX + 3.0 * Math.sin(t * 0.06 + 1.0)) + 3, centerZ + t);
        }
        for (int k = 0; k < 8; k++) {
            double angle = Math.toRadians(22.5 + k * 45.0);
            double radius = RING_RADIUS + 3.5;
            addLamp(plan,
                    centerX + (int) Math.round(radius * Math.cos(angle)),
                    centerZ + (int) Math.round(radius * Math.sin(angle)));
        }
        // Around the plaza.
        for (int k = 0; k < 4; k++) {
            double angle = Math.toRadians(90.0 * k);
            addLamp(plan,
                    centerX + (int) Math.round((PLAZA_RADIUS + 1.5) * Math.cos(angle)),
                    centerZ + (int) Math.round((PLAZA_RADIUS + 1.5) * Math.sin(angle)));
        }
    }

    private static void addLamp(Plan plan, int x, int z) {
        Lamp lamp = new Lamp(x, z);
        double dx = x - centerX;
        double dz = z - centerZ;
        if (Math.sqrt(dx * dx + dz * dz) > RADIUS - 3 || roadCell(x, z)
                || plan.paths.contains(key(x, z))) {
            return;
        }
        for (Piece other : plan.pieces) {
            if (x >= other.minX - 1 && x <= other.maxX + 1
                    && z >= other.minZ - 1 && z <= other.maxZ + 1) {
                return;
            }
        }
        plan.pieces.add(lamp);
    }

    // ── Building materials ────────────────────────────────────────────────────

    private record Palette(BlockState log, BlockState planks, Block door,
                           Block stairs, BlockState roofFull) {}

    private static final Palette[] PALETTES = {
            new Palette(Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(),
                    Blocks.OAK_DOOR, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE.defaultBlockState()),
            new Palette(Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(),
                    Blocks.SPRUCE_DOOR, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS.defaultBlockState()),
            new Palette(Blocks.BIRCH_LOG.defaultBlockState(), Blocks.BIRCH_PLANKS.defaultBlockState(),
                    Blocks.BIRCH_DOOR, Blocks.BRICK_STAIRS, Blocks.BRICKS.defaultBlockState()),
            new Palette(Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(),
                    Blocks.OAK_DOOR, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS.defaultBlockState()),
            new Palette(Blocks.DARK_OAK_LOG.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                    Blocks.DARK_OAK_DOOR, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICKS.defaultBlockState())
    };

    private static final Block[] BEDS = {
            Blocks.RED_BED, Blocks.BLUE_BED, Blocks.GREEN_BED, Blocks.YELLOW_BED, Blocks.WHITE_BED
    };

    // ── Pieces ────────────────────────────────────────────────────────────────

    private abstract static class Piece {
        int minX;
        int maxX;
        int minZ;
        int maxZ;

        abstract void place(Placer placer);

        boolean intersects(int chunkMinX, int chunkMinZ) {
            return maxX >= chunkMinX && minX <= chunkMinX + 15
                    && maxZ >= chunkMinZ && minZ <= chunkMinZ + 15;
        }
    }

    /** The well at the heart of the plaza. */
    private static final class Well extends Piece {
        Well() {
            minX = centerX - 2;
            maxX = centerX + 2;
            minZ = centerZ - 2;
            maxZ = centerZ + 2;
        }

        @Override
        void place(Placer p) {
            BlockState water = Blocks.WATER.defaultBlockState();
            BlockState fence = Blocks.OAK_FENCE.defaultBlockState();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    boolean centre = dx == 0 && dz == 0;
                    boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;

                    p.set(x, GROUND_Y - 2, z, COBBLE);
                    p.set(x, GROUND_Y - 1, z, centre ? water : COBBLE);
                    p.set(x, GROUND_Y, z, centre ? water : COBBLE);

                    if (corner) {
                        p.set(x, GROUND_Y + 1, z, fence);
                        p.set(x, GROUND_Y + 2, z, fence);
                        p.set(x, GROUND_Y + 3, z, fence);
                    }
                    p.set(x, GROUND_Y + 4, z, Blocks.COBBLESTONE_SLAB.defaultBlockState());
                }
            }
        }
    }

    /** A market stall: four posts, a striped awning and a barrel counter. */
    private static final class Stall extends Piece {
        private final int x;
        private final int z;
        private final boolean redFirst;

        Stall(int x, int z, boolean redFirst) {
            this.x = x;
            this.z = z;
            this.redFirst = redFirst;
            minX = x - 1;
            maxX = x + 1;
            minZ = z - 1;
            maxZ = z + 1;
        }

        @Override
        void place(Placer p) {
            BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                    if (corner) {
                        p.set(x + dx, GROUND_Y + 1, z + dz, fence);
                        p.set(x + dx, GROUND_Y + 2, z + dz, fence);
                    }
                    boolean red = ((dx + 1) % 2 == 0) == redFirst;
                    p.set(x + dx, GROUND_Y + 3, z + dz, red
                            ? Blocks.RED_WOOL.defaultBlockState()
                            : Blocks.WHITE_WOOL.defaultBlockState());
                }
            }
            p.set(x, GROUND_Y + 1, z, Blocks.BARREL.defaultBlockState());
            p.set(x, GROUND_Y + 2, z, Blocks.LANTERN.defaultBlockState()
                    .setValue(BlockStateProperties.HANGING, true));
        }
    }

    /** A fence-post lamp. */
    private static final class Lamp extends Piece {
        private final int x;
        private final int z;

        Lamp(int x, int z) {
            this.x = x;
            this.z = z;
            minX = x;
            maxX = x;
            minZ = z;
            maxZ = z;
        }

        @Override
        void place(Placer p) {
            BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
            p.set(x, GROUND_Y + 1, z, fence);
            p.set(x, GROUND_Y + 2, z, fence);
            p.set(x, GROUND_Y + 3, z, fence);
            p.set(x, GROUND_Y + 4, z, Blocks.LANTERN.defaultBlockState());
        }
    }

    /** A fenced 9 × 9 farm plot with a water channel and three kinds of crops. */
    private static final class Farm extends Piece {
        private final int cx;
        private final int cz;
        private final boolean swap;

        Farm(int cx, int cz, boolean swap) {
            this.cx = cx;
            this.cz = cz;
            this.swap = swap;
            minX = cx - 4;
            maxX = cx + 4;
            minZ = cz - 4;
            maxZ = cz + 4;
        }

        private static boolean ring(int i, int j) {
            return Math.max(Math.abs(i), Math.abs(j)) == 4;
        }

        /** A ring cell that holds a fence (everything but the gate). */
        private static boolean isFence(int i, int j) {
            return ring(i, j) && !(i == 0 && j == 4);
        }

        @Override
        void place(Placer p) {
            BlockState farmland = Blocks.FARMLAND.defaultBlockState()
                    .setValue(BlockStateProperties.MOISTURE, 7);

            for (int i = -4; i <= 4; i++) {
                for (int j = -4; j <= 4; j++) {
                    int x = cx + (swap ? j : i);
                    int z = cz + (swap ? i : j);

                    if (ring(i, j)) {
                        if (i == 0 && j == 4) {
                            p.set(x, GROUND_Y, z, Blocks.DIRT_PATH.defaultBlockState()); // gate
                            continue;
                        }
                        // Plot axes may be swapped relative to the world's.
                        boolean east = swap ? isFence(i, j + 1) : isFence(i + 1, j);
                        boolean west = swap ? isFence(i, j - 1) : isFence(i - 1, j);
                        boolean south = swap ? isFence(i + 1, j) : isFence(i, j + 1);
                        boolean north = swap ? isFence(i - 1, j) : isFence(i, j - 1);
                        p.set(x, GROUND_Y + 1, z, Blocks.OAK_FENCE.defaultBlockState()
                                .setValue(BlockStateProperties.NORTH, north)
                                .setValue(BlockStateProperties.SOUTH, south)
                                .setValue(BlockStateProperties.WEST, west)
                                .setValue(BlockStateProperties.EAST, east));
                        continue;
                    }

                    if (i == 0) {
                        p.set(x, GROUND_Y, z, Blocks.WATER.defaultBlockState());
                        continue;
                    }

                    p.set(x, GROUND_Y, z, farmland);
                    int age = 4 + hash(x, z) % 4;
                    Block crop = Math.abs(i) == 1 ? Blocks.CARROTS
                            : (i > 0 ? Blocks.POTATOES : Blocks.WHEAT);
                    p.set(x, GROUND_Y + 1, z, crop.defaultBlockState()
                            .setValue(BlockStateProperties.AGE_7, age));
                }
            }
        }
    }

    /**
     * A building. Local coordinates: {@code u} runs along the front wall, {@code v}
     * runs from the front door to the back, {@code y} is height above the floor
     * (the floor is at y = 0, which is the village ground level).
     */
    private static final class Building extends Piece {
        final Kind kind;
        final int w;
        final int d;
        final int h;
        // World position of local (u, v): (ox + a·u + b·v, oz + c·u + e·v).
        final int ox;
        final int oz;
        final int a;
        final int b;
        final int c;
        final int e;
        final Palette palette;
        final Block bed;
        final boolean chimney;
        final Direction uDir;
        final Direction vDir;

        Building(Kind kind, int w, int d, int h, int centreX, int centreZ,
                 int frontX, int frontZ, Palette palette, int bedIndex, boolean chimney) {
            this.kind = kind;
            this.w = w;
            this.d = d;
            this.h = h;
            this.palette = palette;
            this.bed = BEDS[bedIndex % BEDS.length];
            this.chimney = chimney;

            // Local +v points away from the door, i.e. opposite to the front.
            this.b = -frontX;
            this.e = -frontZ;
            this.a = frontZ;
            this.c = -frontX;
            this.uDir = directionOf(a, c);
            this.vDir = directionOf(b, e);

            int midU = (w - 1) / 2;
            int midV = (d - 1) / 2;
            this.ox = centreX - (a * midU + b * midV);
            this.oz = centreZ - (c * midU + e * midV);

            int[] xs = {wx(-1, -1), wx(w, -1), wx(-1, d), wx(w, d)};
            int[] zs = {wz(-1, -1), wz(w, -1), wz(-1, d), wz(w, d)};
            minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
            maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
            minZ = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
            maxZ = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
        }

        int wx(int u, int v) {
            return ox + a * u + b * v;
        }

        int wz(int u, int v) {
            return oz + c * u + e * v;
        }

        private int top() {
            return h + 2 + (w - 1) / 2 + 1;
        }

        @Override
        void place(Placer p) {
            int top = top();
            for (int u = -1; u <= w; u++) {
                for (int v = -1; v <= d; v++) {
                    int x = wx(u, v);
                    int z = wz(u, v);
                    if (!p.inChunk(x, z)) {
                        continue;
                    }
                    for (int y = 0; y <= top; y++) {
                        BlockState state = blockAt(u, y, v);
                        if (state != null) {
                            p.set(x, GROUND_Y + y, z, state);
                        }
                    }
                }
            }
        }

        private BlockState blockAt(int u, int y, int v) {
            int mid = (w - 1) / 2;
            boolean inU = u >= 0 && u < w;
            boolean inV = v >= 0 && v < d;

            if (y == 0) {
                if (!inU || !inV) {
                    return null;
                }
                boolean edge = u == 0 || u == w - 1 || v == 0 || v == d - 1;
                return edge ? COBBLE : palette.planks();
            }

            if (y > h) {
                return roofBlock(u, y, v, mid, inU, inV);
            }

            if (!inU || !inV) {
                return null;
            }
            boolean wallU = u == 0 || u == w - 1;
            boolean wallV = v == 0 || v == d - 1;
            if (wallU || wallV) {
                return wallBlock(u, y, v, mid, wallU, wallV);
            }
            return interior(u, y, v, mid);
        }

        // ── Walls ─────────────────────────────────────────────────────────────

        private BlockState wallBlock(int u, int y, int v, int mid, boolean wallU, boolean wallV) {
            if (wallU && wallV) {
                return palette.log();
            }
            if (v == 0 && u == mid) {
                if (y == 1) {
                    return door(DoubleBlockHalf.LOWER);
                }
                if (y == 2) {
                    return door(DoubleBlockHalf.UPPER);
                }
                return palette.planks();
            }
            if (isWindow(u, y, v, mid, wallU)) {
                // Front and back walls run along u; side walls run along v.
                Direction.Axis axis = wallU ? vDir.getAxis() : uDir.getAxis();
                boolean alongX = axis == Direction.Axis.X;
                return Blocks.GLASS_PANE.defaultBlockState()
                        .setValue(BlockStateProperties.EAST, alongX)
                        .setValue(BlockStateProperties.WEST, alongX)
                        .setValue(BlockStateProperties.NORTH, !alongX)
                        .setValue(BlockStateProperties.SOUTH, !alongX);
            }
            // A band of timber under the roof gives the walls some structure.
            return y == h ? palette.log() : palette.planks();
        }

        private boolean isWindow(int u, int y, int v, int mid, boolean wallU) {
            if (kind == Kind.BARN) {
                return false;
            }
            boolean tall = kind == Kind.HALL || kind == Kind.LIBRARY;
            if (y != 2 && !(tall && y == 3)) {
                return false;
            }
            if (wallU) {
                if (kind == Kind.HALL) {
                    return v > 1 && v < d - 2 && v % 3 == 0;
                }
                return d >= 7 ? (v == 2 || v == d - 3) : v == mid;
            }
            if (v == 0) {
                return w >= 5 && (u == 1 || u == w - 2);
            }
            return u == mid; // back wall
        }

        private BlockState door(DoubleBlockHalf half) {
            return palette.door().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, vDir)
                    .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, half);
        }

        // ── Roof ──────────────────────────────────────────────────────────────

        private BlockState roofBlock(int u, int y, int v, int mid, boolean inU, boolean inV) {
            // The chimney rises through the roof.
            if (chimney && u == w - 2 && v == d - 2 && y <= h + 5) {
                return COBBLE;
            }
            if (u < -1 || u > w || v < -1 || v > d) {
                return null;
            }

            if (u == mid) {
                if (y == h + 1 + mid) {
                    return palette.roofFull();
                }
            } else {
                int k = u < mid ? u : w - 1 - u;
                if (y == h + 2 + k) {
                    Direction facing = u < mid ? uDir : uDir.getOpposite();
                    return palette.stairs().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
                }
            }

            // Fill the gable ends so the attic is closed.
            if (inU && (v == 0 || v == d - 1)) {
                int k = Math.min(u, w - 1 - u);
                int fillTop = u == mid ? h + mid : h + 1 + k;
                if (y <= fillTop) {
                    return palette.planks();
                }
            }

            if (y == h + 1 && inV) {
                // Beam along the top of the side walls, and a ceiling inside.
                if (u == 0 || u == w - 1) {
                    return palette.planks();
                }
                if (inU) {
                    return palette.planks();
                }
            }
            return null;
        }

        // ── Interiors ─────────────────────────────────────────────────────────

        private BlockState interior(int u, int y, int v, int mid) {
            if (chimney && u == w - 2 && v == d - 2) {
                return COBBLE;
            }
            if (y == h && u == mid && v == mid) {
                return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
            }

            return switch (kind) {
                case LIBRARY -> libraryInterior(u, y, v, mid);
                case SMITHY -> smithyInterior(u, y, v, mid);
                case HALL -> hallInterior(u, y, v, mid);
                case BARN -> barnInterior(u, y, v, mid);
                default -> homeInterior(u, y, v, mid);
            };
        }

        private BlockState bedPart(BedPart part) {
            return bed.defaultBlockState()
                    .setValue(BlockStateProperties.BED_PART, part)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, vDir);
        }

        private BlockState homeInterior(int u, int y, int v, int mid) {
            if (y == 1) {
                if (u == 1 && v == d - 2) {
                    return bedPart(BedPart.HEAD);
                }
                if (u == 1 && v == d - 3) {
                    return bedPart(BedPart.FOOT);
                }
                if (w >= 7 && d >= 9 && u == w - 2 && v == d - 4) {
                    return bedPart(BedPart.HEAD);
                }
                if (w >= 7 && d >= 9 && u == w - 2 && v == d - 5) {
                    return bedPart(BedPart.FOOT);
                }
                if (u == w - 2 && v == 1) {
                    return Blocks.CRAFTING_TABLE.defaultBlockState();
                }
                if (d >= 7 && u == 1 && v == 1) {
                    return Blocks.FURNACE.defaultBlockState().setValue(
                            BlockStateProperties.HORIZONTAL_FACING, vDir.getOpposite());
                }
                if (d >= 7 && u == mid && v == mid) {
                    return Blocks.OAK_FENCE.defaultBlockState();
                }
                if (u == mid && v == 2) {
                    return Blocks.RED_CARPET.defaultBlockState();
                }
            }
            if (y == 2 && d >= 7 && u == mid && v == mid) {
                return Blocks.OAK_PRESSURE_PLATE.defaultBlockState();
            }
            return null;
        }

        private BlockState libraryInterior(int u, int y, int v, int mid) {
            if (y <= 2) {
                if (v == d - 2) {
                    return Blocks.BOOKSHELF.defaultBlockState();
                }
                if ((u == 1 || u == w - 2) && v >= 2 && v <= d - 4) {
                    return Blocks.BOOKSHELF.defaultBlockState();
                }
            }
            if (y == 1) {
                if (u == mid && v == 2) {
                    return Blocks.LECTERN.defaultBlockState().setValue(
                            BlockStateProperties.HORIZONTAL_FACING, vDir.getOpposite());
                }
                if (u == mid && v == d - 4) {
                    return Blocks.OAK_FENCE.defaultBlockState();
                }
                if (u == mid) {
                    return Blocks.RED_CARPET.defaultBlockState();
                }
            }
            if (y == 2 && u == mid && v == d - 4) {
                return Blocks.OAK_PRESSURE_PLATE.defaultBlockState();
            }
            return null;
        }

        private BlockState smithyInterior(int u, int y, int v, int mid) {
            if (y == 1) {
                if (v == d - 2 && u == 1) {
                    return Blocks.FURNACE.defaultBlockState().setValue(
                            BlockStateProperties.HORIZONTAL_FACING, vDir.getOpposite());
                }
                if (v == d - 2 && u == 2) {
                    return Blocks.BLAST_FURNACE.defaultBlockState().setValue(
                            BlockStateProperties.HORIZONTAL_FACING, vDir.getOpposite());
                }
                if (v == d - 2 && u == w - 3) {
                    return Blocks.CAULDRON.defaultBlockState();
                }
                if (u == mid && v == mid) {
                    return Blocks.ANVIL.defaultBlockState();
                }
                if (u == w - 2 && v == 1) {
                    return Blocks.BARREL.defaultBlockState();
                }
            }
            return null;
        }

        private BlockState hallInterior(int u, int y, int v, int mid) {
            if (y == 1) {
                if (u == mid && v >= 1 && v <= d - 3) {
                    return Blocks.RED_CARPET.defaultBlockState();
                }
                if (u == mid && v == d - 2) {
                    return Blocks.LECTERN.defaultBlockState().setValue(
                            BlockStateProperties.HORIZONTAL_FACING, vDir.getOpposite());
                }
                if ((u == 1 || u == w - 2) && v == d - 2) {
                    return Blocks.BOOKSHELF.defaultBlockState();
                }
                // Two rows of benches either side of the aisle.
                if ((u == mid - 2 || u == mid + 2) && v >= 3 && v <= d - 5 && v % 2 == 1) {
                    return Blocks.OAK_STAIRS.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, vDir.getOpposite());
                }
            }
            if (y == h - 1 && (u == mid - 2 || u == mid + 2) && (v == 3 || v == d / 2 || v == d - 4)) {
                return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
            }
            return null;
        }

        private BlockState barnInterior(int u, int y, int v, int mid) {
            if (y <= 2 && v >= d - 3 && (u <= 2 || u >= w - 3)) {
                return Blocks.HAY_BLOCK.defaultBlockState();
            }
            if (y == 1 && u == mid && v == d - 2) {
                return Blocks.BARREL.defaultBlockState();
            }
            if (y == 1 && (u == 1 || u == w - 2) && v == 2) {
                return Blocks.BARREL.defaultBlockState();
            }
            return null;
        }
    }

    // ── Chunk writer ──────────────────────────────────────────────────────────

    /** Writes blocks into one chunk, ignoring anything outside it. */
    private static final class Placer {
        private final ChunkAccess chunk;
        private final Heightmap oceanFloor;
        private final Heightmap worldSurface;
        private final int minX;
        private final int minZ;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Placer(ChunkAccess chunk, Heightmap oceanFloor, Heightmap worldSurface,
               int minX, int minZ) {
            this.chunk = chunk;
            this.oceanFloor = oceanFloor;
            this.worldSurface = worldSurface;
            this.minX = minX;
            this.minZ = minZ;
        }

        boolean inChunk(int x, int z) {
            int lx = x - minX;
            int lz = z - minZ;
            return lx >= 0 && lx < 16 && lz >= 0 && lz < 16;
        }

        void set(int x, int y, int z, BlockState state) {
            if (!inChunk(x, z)
                    || y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) {
                return;
            }
            int lx = x - minX;
            int lz = z - minZ;
            pos.set(lx, y, lz);
            chunk.setBlockState(pos, state, false);
            worldSurface.update(lx, y, lz, state);
            oceanFloor.update(lx, y, lz, state);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Direction directionOf(int dx, int dz) {
        if (dx > 0) {
            return Direction.EAST;
        }
        if (dx < 0) {
            return Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }
}