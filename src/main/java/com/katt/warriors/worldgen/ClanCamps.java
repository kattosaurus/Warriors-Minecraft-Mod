package com.katt.warriors.worldgen;

import com.katt.warriors.data.RebirthChoice.Clan;
import com.katt.warriors.data.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClanCamps {

    public static final int RADIUS = 37;
    public static final double FLAT_RADIUS = 48.0;
    public static final double EASE_RADIUS = 86.0;

    public static final int INSET = 4;

    private static final double BOWL_IN = 27.0;
    private static final double BOWL_OUT = 34.0;
    private static final double BARRIER_IN = 31.0;
    private static final double BARRIER_OUT = 37.0;
    private static final double GATE = 2.5;
    private static final int PATH_REACH = 46;
    private static final int MIN_GROUND = ClanlandsTerrain.SEA_LEVEL + 4;
    private static final int MAX_GROUND = 92;

    private static final int DEPTH = 4;
    private static final int TOP = 14;
    private static final double RAMP_HALF = 2.5;

    public record Camp(Clan clan, Terrain terrain, int x, int z, int groundY, int rimY,
                       int ex, int ez, Den[] dens) {}

    public record Lobe(double u, double v, double r) {}

    public record Den(double u, double v, double radius, double height, boolean rock,
                      double doorU, double doorV, Lobe[] lobes, int seed, double reach) {
        public double shell() {
            return rock ? 2.8 : 2.2;
        }

        public double ceiling() {
            return Math.min(height - (rock ? 2.6 : 2.0), 3.8);
        }
    }

    private static final double[][] LAYOUT = {
            {0, -20, 7.0, 8.5, 1},
            {-21, 5, 8.0, 6.4, 0},
            {21, 2, 6.8, 6.0, 0},
            {19, -14, 5.8, 5.6, 0},
            {-19, -14, 5.2, 5.4, 0},
            {15, 19, 5.4, 5.4, 0}
    };

    private static volatile List<Camp> camps = Collections.emptyList();
    private static volatile ClanlandsLayout campsLayout;

    private ClanCamps() {}

    public static boolean hasCamp(Clan clan) {
        return campOf(clan) != null;
    }

    public static Camp campOf(Clan clan) {
        if (!clan.hasCamp()) {
            return null;
        }
        for (Camp camp : camps()) {
            if (camp.clan() == clan) {
                return camp;
            }
        }
        return null;
    }

    public static List<Camp> camps() {
        ClanlandsLayout layout = ClanlandsLayout.current();
        if (campsLayout != layout) {
            synchronized (ClanCamps.class) {
                if (campsLayout != layout) {
                    camps = build(layout);
                    campsLayout = layout;
                }
            }
        }
        return camps;
    }

    private static List<Camp> build(ClanlandsLayout layout) {
        List<Camp> list = new ArrayList<>();
        for (ClanlandsLayout.Site site : layout.sites()) {
            Clan clan = site.clan();
            if (clan == null || !clan.hasCamp() || site == layout.village()) {
                continue;
            }
            int natural = (int) Math.round(ClanlandsTerrain.naturalHeight(site.x(), site.z()));
            int floor = Math.max(MIN_GROUND, Math.min(MAX_GROUND, natural - 2));

            double dx = 4 - site.x();
            double dz = -4 - site.z();
            int ex = 0;
            int ez = 0;
            if (Math.abs(dx) > Math.abs(dz)) {
                ex = dx > 0 ? 1 : -1;
            } else {
                ez = dz > 0 ? 1 : -1;
            }
            Camp bare = new Camp(clan, site.terrain(), site.x(), site.z(), floor, floor + INSET, ex, ez, null);
            list.add(new Camp(clan, site.terrain(), site.x(), site.z(), floor, floor + INSET, ex, ez,
                    makeDens(bare)));
        }
        return Collections.unmodifiableList(list);
    }

    private static Den[] makeDens(Camp camp) {
        Den[] dens = new Den[LAYOUT.length];
        for (int i = 0; i < LAYOUT.length; i++) {
            double[] l = LAYOUT[i];
            int s = 300 + i * 17;
            double u = l[0] + (unit(camp, s) - 0.5) * 4.0;
            double v = l[1] + (unit(camp, s + 1) - 0.5) * 4.0;
            double radius = l[2] * (0.94 + unit(camp, s + 2) * 0.14);
            double height = l[3] * (0.94 + unit(camp, s + 3) * 0.12);
            boolean rock = l[4] > 0.5;

            double angle = Math.atan2(-v, -u) + (unit(camp, s + 4) - 0.5) * 0.7;
            double doorU = Math.cos(angle);
            double doorV = Math.sin(angle);

            int count = rock || radius > 6.0 ? 3 : 2;
            Lobe[] lobes = new Lobe[count];
            lobes[0] = new Lobe(u, v, radius);
            double reach = radius;
            for (int k = 1; k < count; k++) {
                double side = (k % 2 == 1) ? 1.0 : -1.0;
                double theta = angle + Math.PI + side * (0.55 + 0.9 * unit(camp, s + 5 + k));
                double r = radius * (0.5 + 0.2 * unit(camp, s + 8 + k));
                double dist = radius * (0.85 + 0.15 * unit(camp, s + 11 + k));
                lobes[k] = new Lobe(u + Math.cos(theta) * dist, v + Math.sin(theta) * dist, r);
                reach = Math.max(reach, dist + r);
            }
            double shell = rock ? 2.8 : 2.2;
            dens[i] = new Den(u, v, radius, height, rock, doorU, doorV, lobes,
                    hash(camp.x(), camp.z(), 500 + i), reach + shell + 2.0);
        }
        return dens;
    }

    public static double worldX(Camp camp, double u, double v) {
        return camp.x() + v * camp.ex() - u * camp.ez();
    }

    public static double worldZ(Camp camp, double u, double v) {
        return camp.z() + v * camp.ez() + u * camp.ex();
    }

    public static BlockPos spawnPos(Clan clan) {
        Camp camp = campOf(clan);
        if (camp == null) {
            return null;
        }
        return new BlockPos(camp.x() + camp.ex() * 3, camp.groundY() + 1, camp.z() + camp.ez() * 3);
    }

    static double shapeRadius(Camp camp, double x, double z) {
        double dx = x - camp.x();
        double dz = z - camp.z();
        double v = dx * camp.ex() + dz * camp.ez();
        double u = -dx * camp.ez() + dz * camp.ex();

        double turn = (unit(camp, 120) - 0.5) * 1.0;
        double squash = 1.0 + (unit(camp, 121) - 0.5) * 0.30;
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        double ru = u * cos - v * sin;
        double rv = u * sin + v * cos;
        double r = Math.sqrt(ru * ru * squash * squash + rv * rv / (squash * squash));

        return r + ClanNoise.fbm(x * 0.028 + 13.0, z * 0.028 - 7.0, 3, campSalt(camp, 110)) * 6.5;
    }

    public static double insetDepth(Camp camp, double x, double z) {
        double bowl = 1.0 - ClanNoise.smooth(BOWL_IN, BOWL_OUT, shapeRadius(camp, x, z));
        double depth = bowl;

        double dx = x - camp.x();
        double dz = z - camp.z();
        double v = dx * camp.ex() + dz * camp.ez();
        if (v > 0.0) {
            double u = -dx * camp.ez() + dz * camp.ex();
            double side = Math.abs(u - pathOffset(v));
            double gully = (1.0 - ClanNoise.smooth(2.5, 7.0, side))
                    * (1.0 - ClanNoise.smooth(BOWL_IN - 3.0, PATH_REACH - 2.0, v));
            depth = Math.max(depth, gully);
        }
        return depth * INSET;
    }

    private static final class Col {
        int u;
        int v;
        int wx;
        int wz;
        int rel;
        double sr;
        int wallHeight;
        double wallCore;
        Cell cell;
    }

    private static final class Cell {
        boolean cave;
        int floor;
        int top;
        boolean steps;
        boolean nestOk;
        double edge;
        int mound;
        boolean rock;
    }

    public static void generate(ChunkAccess chunk, Heightmap oceanFloor, Heightmap worldSurface) {
        List<Camp> all = camps();
        if (all.isEmpty()) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int limit = PATH_REACH + 6;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Col col = new Col();

        for (Camp camp : all) {
            if (minX + 15 < camp.x() - limit || minX > camp.x() + limit
                    || minZ + 15 < camp.z() - limit || minZ > camp.z() + limit) {
                continue;
            }
            Palette palette = Palette.of(camp.terrain());

            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int wx = minX + lx;
                    int wz = minZ + lz;
                    int dx = wx - camp.x();
                    int dz = wz - camp.z();
                    if (dx * dx + dz * dz > limit * limit) {
                        continue;
                    }
                    col.v = dx * camp.ex() + dz * camp.ez();
                    col.u = -dx * camp.ez() + dz * camp.ex();
                    col.wx = wx;
                    col.wz = wz;
                    col.rel = ClanlandsTerrain.groundY(wx, wz) - camp.groundY();
                    col.sr = shapeRadius(camp, wx, wz);
                    wall(camp, col);
                    col.cell = denCell(camp, col);

                    for (int h = -DEPTH; h <= TOP; h++) {
                        BlockState state = blockAt(palette, camp, col, h);
                        if (state == null) {
                            continue;
                        }
                        int y = camp.groundY() + h;
                        if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) {
                            continue;
                        }
                        pos.set(lx, y, lz);
                        chunk.setBlockState(pos, state, false);
                        oceanFloor.update(lx, y, lz, state);
                        worldSurface.update(lx, y, lz, state);
                    }
                }
            }
        }
    }

    private static BlockState blockAt(Palette p, Camp camp, Col col, int h) {
        int roll = hash(col.wx, col.wz, h);
        Cell cell = col.cell;

        if (cell != null) {
            if (cell.cave) {
                if (h < cell.floor) {
                    return null;
                }
                if (h == cell.floor) {
                    if (cell.steps) {
                        return floor(p, camp, col, roll);
                    }
                    return (roll % 10 < 7) ? Blocks.MOSS_BLOCK.defaultBlockState() : p.moundEarth;
                }
                if (h <= cell.top) {
                    return nest(cell, h, col.wx, col.wz);
                }
            }
            if (h > col.rel && h <= cell.mound) {
                return mound(p, cell.rock, h, roll);
            }
        }

        int up = h - col.rel;
        if (col.wallHeight > 0 && up >= 1 && up <= col.wallHeight) {
            int pick = roll % 100;
            if (col.wallCore > 0.55 && pick < 18) {
                return p.barrierAlt;
            }
            if (up == 1 && col.wallCore < 0.35 && pick < 30) {
                return p.barrierFloor;
            }
            return p.barrier;
        }

        if (h == col.rel && (cell == null || cell.mound <= col.rel)) {
            return surfaceBlock(p, camp, col, roll);
        }
        return null;
    }

    private static BlockState surfaceBlock(Palette p, Camp camp, Col col, int roll) {
        if (col.v > 0 && col.v <= PATH_REACH
                && Math.abs(col.u - pathOffset(col.v)) <= pathWidth(col.v)) {
            return pathBlock(p, col.v, roll);
        }
        if (col.sr < BARRIER_OUT + 1.5) {
            return col.rel <= 0 ? floor(p, camp, col, roll) : wallBlock(p, roll);
        }
        return null;
    }

    private static void wall(Camp camp, Col col) {
        col.wallHeight = 0;
        if (col.sr < BARRIER_IN - 6.0 || col.sr > BARRIER_OUT + 6.0) {
            return;
        }
        double wobble = ClanNoise.fbm(col.wx * 0.05, col.wz * 0.05, 2, campSalt(camp, 101));
        double centre = (BARRIER_IN + BARRIER_OUT) * 0.5 + wobble * 1.8;
        double half = (BARRIER_OUT - BARRIER_IN) * 0.5
                + ClanNoise.fbm(col.wx * 0.09 + 40.0, col.wz * 0.09 - 17.0, 2, campSalt(camp, 102)) * 1.6;
        if (half < 0.9) {
            half = 0.9;
        }
        double dist = Math.abs(col.sr - centre);
        if (dist > half) {
            return;
        }

        if (col.v > 0 && Math.abs(col.u - pathOffset(col.v)) <= GATE) {
            return;
        }
        if (half < 1.6 && hash(col.wx, col.wz, 5) % 100 < 22) {
            return;
        }

        double core = 1.0 - dist / half;
        double clump = ClanNoise.fbm(col.wx * 0.11 - 9.0, col.wz * 0.11 + 23.0, 2, campSalt(camp, 103));
        double bulk = ClanNoise.fbm(col.wx * 0.04 + 3.0, col.wz * 0.04 - 5.0, 2, campSalt(camp, 105));
        col.wallHeight = 1 + (int) Math.round(core * 2.0 + Math.max(0.0, clump) * 2.2
                + Math.max(0.0, bulk) * 1.8);
        col.wallCore = core;
    }

    private static double pathOffset(double v) {
        if (v <= 0.0) {
            return 0.0;
        }
        return Math.sin(v * 0.16) * 2.2 * Math.min(1.0, v / 10.0);
    }

    private static double pathWidth(double v) {
        double fade = 1.0 - ClanNoise.smooth(PATH_REACH - 12.0, PATH_REACH, v);
        return (1.6 + ClanNoise.fbm(v * 0.2, 3.7, 1, 77) * 0.5) * fade + 0.4;
    }

    private static BlockState pathBlock(Palette p, int v, int roll) {
        int pick = roll % 100;
        double worn = 1.0 - ClanNoise.smooth(18.0, 34.0, v);
        if (pick < 55 * worn + 15) {
            return p.path;
        }
        if (pick < 70) {
            return p.floorB;
        }
        return p.pathEdge;
    }

    private static BlockState floor(Palette p, Camp camp, Col col, int roll) {
        int u = col.u;
        int v = col.v;

        for (Den den : camp.dens()) {
            if (Math.hypot(u - den.u(), v - den.v()) < den.radius() + den.shell() + 1.0) {
                return (roll % 10 == 0) ? Blocks.GRAVEL.defaultBlockState() : p.floorB;
            }
        }

        for (Den den : camp.dens()) {
            double len = Math.hypot(den.u(), den.v());
            if (len < 1.0) {
                continue;
            }
            double along = (u * den.u() + v * den.v()) / len;
            double across = Math.abs((u * -den.v() + v * den.u()) / len);
            if (along > 2.0 && along < len - den.radius() - den.shell() + 1.0 && across < 1.8) {
                return (roll % 8 == 0) ? p.floorB : p.path;
            }
        }

        double wear = ClanNoise.fbm(col.wx * 0.07, col.wz * 0.07, 2, campSalt(camp, 104));
        int pick = roll % 100;
        if (wear > 0.25) {
            if (pick < 8) {
                return Blocks.GRAVEL.defaultBlockState();
            }
            if (pick < 20) {
                return p.floorB;
            }
            return p.path;
        }
        if (pick < 6) {
            return Blocks.GRAVEL.defaultBlockState();
        }
        if (pick < 14) {
            return p.floorB;
        }
        if (pick < 20 && col.sr > 20.0) {
            return p.leafLitter;
        }
        return p.floorA;
    }

    private static BlockState wallBlock(Palette p, int roll) {
        int pick = roll % 100;
        if (pick < 18) {
            return p.moundStone;
        }
        if (pick < 34) {
            return p.barrierFloor;
        }
        return p.moundEarth;
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static Cell denCell(Camp camp, Col col) {
        Cell cell = null;
        for (Den den : camp.dens()) {
            double du = col.u - den.u();
            double dv = col.v - den.v();
            if (du * du + dv * dv > den.reach() * den.reach()) {
                continue;
            }
            if (cell == null) {
                cell = new Cell();
            }

            double lump = ClanNoise.fbm(col.wx * 0.17, col.wz * 0.17, 2, den.seed());
            double warp = 1.0 - 0.2 * lump;

            double f = -1.0;
            double g = -1.0;
            Lobe[] lobes = den.lobes();
            for (int i = 0; i < lobes.length; i++) {
                Lobe lobe = lobes[i];
                double lu = col.u - lobe.u();
                double lv = col.v - lobe.v();
                double d = Math.sqrt(lu * lu + lv * lv) * warp;
                double fi = 1.0 - d / lobe.r();
                if (fi > f) {
                    f = fi;
                }
                if (i == 0) {
                    g = 1.0 - d / (lobe.r() + den.shell());
                }
            }

            double along = du * den.doorU() + dv * den.doorV();
            double across = Math.abs(du * den.doorV() - dv * den.doorU());
            double mouth = den.radius() + den.shell();
            boolean ramp = across <= RAMP_HALF && along > -0.3 * den.radius() && along <= mouth + 1.0;

            int mound = 0;
            if (g > 0.0) {
                double rise = 1.0 - g;
                double profile = den.rock()
                        ? Math.pow(1.0 - rise * rise * rise, 1.0 / 3.0)
                        : Math.sqrt(1.0 - rise * rise);
                mound = (int) Math.round(den.height() * profile * (1.0 + 0.16 * lump));
                if (ramp) {
                    mound = Math.max(mound, 5);
                }
            }
            if (mound > cell.mound) {
                cell.mound = mound;
                cell.rock = den.rock();
            }

            if (f > 0.0 || ramp) {
                int floor = -DEPTH;
                boolean steps = false;
                if (ramp) {
                    int k = (int) Math.floor((along - (den.radius() - 2.0 * DEPTH)) / 2.0);
                    k = Math.max(0, Math.min(DEPTH, k));
                    floor = -DEPTH + k;
                    steps = k > 0;
                } else if (f < 0.16) {
                    floor = -DEPTH + (f < 0.07 ? 2 : 1);
                    if (hash(col.wx, col.wz, 77) % 3 == 0) {
                        floor -= 1;
                    }
                }

                double fc = Math.max(0.0, Math.min(1.0, f));
                double dome = den.ceiling() * Math.sqrt(1.0 - (1.0 - fc) * (1.0 - fc));
                int top = (int) Math.round(dome + 0.7 * lump);
                top = Math.max(top, floor + 3);
                top = Math.min(top, Math.max(col.rel, mound) - 2);

                if (top - floor >= 3 && (!cell.cave || top - floor > cell.top - cell.floor)) {
                    cell.cave = true;
                    cell.floor = floor;
                    cell.top = top;
                    cell.steps = steps;
                    cell.edge = fc;
                    cell.nestOk = !ramp && floor == -DEPTH && fc > 0.08;
                }
            }
        }
        if (cell != null && !cell.cave && cell.mound <= 0) {
            return null;
        }
        return cell;
    }

    private static BlockState mound(Palette p, boolean rock, int h, int roll) {
        if (rock) {
            return rock(roll);
        }
        if (h <= 2) {
            int pick = roll % 100;
            if (pick < 12) {
                return p.shellAlt;
            }
            if (pick < 24) {
                return p.moundStone;
            }
            return p.moundEarth;
        }
        return (roll & 3) == 0 ? p.shellAlt : p.shell;
    }

    private static BlockState nest(Cell cell, int h, int wx, int wz) {
        if (!cell.nestOk || h > cell.floor + 2) {
            return AIR;
        }
        int patch = hash(wx >> 1, wz >> 1, 31) % 100;
        int limit = cell.edge < 0.55 ? 38 : 14;
        if (patch >= limit) {
            return AIR;
        }
        boolean raised = patch < limit / 3;
        if (h == cell.floor + 1) {
            return raised ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSS_CARPET.defaultBlockState();
        }
        return raised ? Blocks.MOSS_CARPET.defaultBlockState() : AIR;
    }

    private static BlockState rock(int roll) {
        int r = roll % 10;
        if (r < 4) {
            return Blocks.STONE.defaultBlockState();
        }
        if (r < 6) {
            return Blocks.ANDESITE.defaultBlockState();
        }
        if (r < 8) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    }

    private record Palette(BlockState shell, BlockState shellAlt, BlockState barrier,
                           BlockState barrierAlt, BlockState floorA, BlockState floorB,
                           BlockState path, BlockState pathEdge, BlockState leafLitter,
                           BlockState moundEarth, BlockState moundStone, BlockState barrierFloor) {

        static Palette of(Terrain terrain) {
            BlockState path = Blocks.DIRT_PATH.defaultBlockState();
            BlockState gravel = Blocks.GRAVEL.defaultBlockState();
            BlockState earth = Blocks.PACKED_MUD.defaultBlockState();
            BlockState moundStone = Blocks.COBBLESTONE.defaultBlockState();
            BlockState debris = Blocks.GRAVEL.defaultBlockState();
            switch (terrain) {
                case TAIGA:
                case TUNDRA:
                    return leafy(Blocks.SPRUCE_LEAVES, Blocks.SPRUCE_LEAVES, path, gravel)
                            .withFloor(Blocks.SNOW_BLOCK.defaultBlockState(), gravel)
                            .withMound(earth, moundStone, debris);
                case MARSH:
                    return leafy(Blocks.DARK_OAK_LEAVES, Blocks.MANGROVE_LEAVES,
                            Blocks.PACKED_MUD.defaultBlockState(), path)
                            .withMound(Blocks.CLAY.defaultBlockState(), Blocks.MUD.defaultBlockState(), debris);
                case MOOR:
                    return leafy(Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES, path, gravel)
                            .withMound(earth, moundStone, debris);
                case LAKE:
                    return leafy(Blocks.MANGROVE_LEAVES, Blocks.OAK_LEAVES, path, gravel)
                            .withMound(earth, moundStone, debris);
                case SCRUB:
                    return leafy(Blocks.ACACIA_LEAVES, Blocks.OAK_LEAVES, path, gravel)
                            .withMound(Blocks.COARSE_DIRT.defaultBlockState(), moundStone, debris);
                case MOUNTAIN: {
                    BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
                    BlockState mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                    BlockState stone = Blocks.STONE.defaultBlockState();
                    return new Palette(stone, mossy, cobble, mossy, gravel, path,
                            path, gravel, leaves(Blocks.OAK_LEAVES), earth, stone, gravel);
                }
                case DESERT: {
                    BlockState sandstone = Blocks.SANDSTONE.defaultBlockState();
                    BlockState cut = Blocks.CUT_SANDSTONE.defaultBlockState();
                    return new Palette(sandstone, cut, sandstone, cut,
                            Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.SAND.defaultBlockState(),
                            Blocks.SAND.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                            Blocks.DEAD_BUSH.defaultBlockState(), earth, cut, Blocks.SAND.defaultBlockState());
                }
                default:
                    return leafy(Blocks.OAK_LEAVES, Blocks.AZALEA_LEAVES, path, gravel)
                            .withMound(earth, moundStone, debris);
            }
        }

        private Palette withFloor(BlockState newA, BlockState newB) {
            return new Palette(shell, shellAlt, barrier, barrierAlt, newA, newB,
                    path, pathEdge, leafLitter, moundEarth, moundStone, barrierFloor);
        }

        private Palette withMound(BlockState newEarth, BlockState newStone, BlockState newDebris) {
            return new Palette(shell, shellAlt, barrier, barrierAlt, floorA, floorB,
                    path, pathEdge, leafLitter, newEarth, newStone, newDebris);
        }

        private static Palette leafy(Block main, Block alt, BlockState floorA, BlockState floorB) {
            BlockState a = leaves(main);
            BlockState b = leaves(alt);
            return new Palette(a, b, a, b, floorA, floorB,
                    Blocks.DIRT_PATH.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
                    leaves(Blocks.OAK_LEAVES), Blocks.PACKED_MUD.defaultBlockState(),
                    Blocks.COBBLESTONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState());
        }

        private static BlockState leaves(Block block) {
            return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        }
    }

    private static int campSalt(Camp camp, int salt) {
        return hash(camp.x(), camp.z(), salt);
    }

    private static double unit(Camp camp, int salt) {
        return (campSalt(camp, salt) % 10000) / 10000.0;
    }

    private static int hash(int x, int z, int salt) {
        int h = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }
}
