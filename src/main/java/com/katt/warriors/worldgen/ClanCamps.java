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

/**
 * The clan camps: a sheltered hollow sunk into the land, ringed by a dense,
 * irregular thicket, with a worn clearing, a Highrock for the leader and five
 * dens (warriors, apprentices, nursery, medicine cat, elders) that are real
 * caves dug under brush-and-earth mounds.
 *
 * <p>Only clans with {@link Clan#hasCamp()} get one, so strays, rogues and
 * kittypets never do. Like {@link KittypetVillage}, every block is a pure
 * function of its world position and the layout, so each chunk writes only its
 * own slice and neighbouring chunks always agree. The land around a camp is
 * shaped by {@link ClanlandsTerrain} using {@link #insetDepth}.</p>
 *
 * <p>Local coordinates: {@code v} runs from the camp centre towards the
 * entrance (which faces the middle of the valley) and {@code u} across it.
 * {@code h} is the height above the clearing floor ({@link Camp#groundY()});
 * the land around the hollow stands {@link #INSET} blocks higher.</p>
 *
 * <p>Nothing here is a perfect circle: the hollow's outline is warped by
 * noise (and each camp is a little oval and turned), the thicket wanders and
 * varies in height and thickness, the dens are lumpy caves made of several
 * overlapping chambers, and the way in is a meandering gully.</p>
 */
public final class ClanCamps {

    /** Outer edge of the barrier. */
    public static final int RADIUS = 37;
    /** The land is shaped by the camp out to here and eases back into the terrain after it. */
    public static final double FLAT_RADIUS = 48.0;
    public static final double EASE_RADIUS = 86.0;

    /** How far the clearing is sunk below the land around the camp. */
    public static final int INSET = 4;

    /** The clearing floor is flat out to this (warped) radius, then the wall rises. */
    private static final double BOWL_IN = 27.0;
    /** The wall has reached the rim by this (warped) radius. */
    private static final double BOWL_OUT = 34.0;
    private static final double BARRIER_IN = 31.0;
    private static final double BARRIER_OUT = 37.0;
    /** Half-width of the gap in the barrier. */
    private static final double GATE = 2.5;
    /** How far the entrance path runs out of the camp. */
    private static final int PATH_REACH = 46;
    private static final int MIN_GROUND = ClanlandsTerrain.SEA_LEVEL + 4;
    private static final int MAX_GROUND = 92;

    /** How deep the den caves are dug below the clearing floor. */
    private static final int DEPTH = 4;
    /** Highest block (above the clearing floor) any camp piece reaches. */
    private static final int TOP = 14;
    /** Half-width of a den's doorway and the ramp leading down into it. */
    private static final double RAMP_HALF = 2.5;

    /** One camp. {@code ex}/{@code ez} is the unit vector the entrance faces. */
    public record Camp(Clan clan, Terrain terrain, int x, int z, int groundY, int rimY,
                       int ex, int ez, Den[] dens) {}

    /** One chamber of a cave, in camp-local coordinates. */
    public record Lobe(double u, double v, double r) {}

    /**
     * A den: a cave of several overlapping chambers dug {@link #DEPTH} blocks
     * into the ground under a lumpy mound, entered by a wide ramp from the
     * clearing. {@code radius} is the main chamber's radius; the extra chambers
     * wander off behind it, and where they run into the wall of the hollow they
     * burrow into the hillside. {@code rock} dens (the Highrock) are stone with
     * a thicker roof.
     */
    public record Den(double u, double v, double radius, double height, boolean rock,
                      double doorU, double doorV, Lobe[] lobes, int seed, double reach) {
        /** Thickness of the mound around the chambers. */
        public double shell() {
            return rock ? 2.8 : 2.2;
        }

        /** How high the chamber ceiling domes above the clearing floor. */
        public double ceiling() {
            return Math.min(height - (rock ? 2.6 : 2.0), 3.8);
        }
    }

    /** Base layout: u, v, chamber radius, mound height, rock (1) or not (0). */
    private static final double[][] LAYOUT = {
            {0, -20, 7.0, 8.5, 1},      // Highrock: the leader's den
            {-21, 5, 8.0, 6.4, 0},      // warriors
            {21, 2, 6.8, 6.0, 0},       // apprentices
            {19, -14, 5.8, 5.6, 0},     // nursery
            {-19, -14, 5.2, 5.4, 0},    // medicine cat
            {15, 19, 5.4, 5.4, 0}       // elders
    };

    private static volatile List<Camp> camps = Collections.emptyList();
    private static volatile ClanlandsLayout campsLayout;

    private ClanCamps() {}

    // ── Which clans, where ────────────────────────────────────────────────────

    /** Whether this clan has a camp in the current layout. Strays and rogues never do. */
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

    /** All camps of the current layout. */
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
            // The wild centre has no clan; the village territory has its village instead.
            if (clan == null || !clan.hasCamp() || site == layout.village()) {
                continue;
            }
            // The clearing sits a little below the natural land, the rim a little above it.
            int natural = (int) Math.round(ClanlandsTerrain.naturalHeight(site.x(), site.z()));
            int floor = Math.max(MIN_GROUND, Math.min(MAX_GROUND, natural - 2));

            // The entrance faces the middle of the valley.
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

    /** The dens of one camp: the base layout, nudged and reshaped so no two camps match. */
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

            // The door faces the middle of the camp, a little off-axis.
            double angle = Math.atan2(-v, -u) + (unit(camp, s + 4) - 0.5) * 0.7;
            double doorU = Math.cos(angle);
            double doorV = Math.sin(angle);

            // The main chamber, then side chambers wandering off behind it.
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

    /**
     * World X of a camp-local point. {@code u} runs across the entrance and
     * {@code v} towards it, the inverse of the transform used in {@link #generate}.
     */
    public static double worldX(Camp camp, double u, double v) {
        return camp.x() + v * camp.ex() - u * camp.ez();
    }

    /** World Z of a camp-local point (see {@link #worldX}). */
    public static double worldZ(Camp camp, double u, double v) {
        return camp.z() + v * camp.ez() + u * camp.ex();
    }

    /** Where members of a clan wake up: inside the entrance of their camp's clearing. */
    public static BlockPos spawnPos(Clan clan) {
        Camp camp = campOf(clan);
        if (camp == null) {
            return null;
        }
        return new BlockPos(camp.x() + camp.ex() * 3, camp.groundY() + 1, camp.z() + camp.ez() * 3);
    }

    // ── The hollow's shape (shared with the terrain) ──────────────────────────

    /**
     * Distance from the camp centre, bent so the camp is not a circle: each
     * camp is a little oval and turned, and slow noise pushes the outline in
     * and out by several blocks. The hollow, the thicket and the clearing all
     * use this, so they always agree.
     */
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

    /**
     * How far (0 to {@link #INSET} blocks) the land at a position is sunk below
     * the rim around the camp: the whole clearing, a wall rising round it, and
     * a gully climbing out along the entrance path.
     */
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

    // ── Chunk entry point ─────────────────────────────────────────────────────

    /** Everything one column needs, worked out once instead of once per block. */
    private static final class Col {
        int u;
        int v;
        int wx;
        int wz;
        /** Height of the land here above the clearing floor. */
        int rel;
        double sr;
        int wallHeight;
        double wallCore;
        Cell cell;
    }

    /** What the dens do to one column. */
    private static final class Cell {
        boolean cave;
        /** Height of the cave floor block and of the highest air block above it. */
        int floor;
        int top;
        boolean steps;
        boolean nestOk;
        /** 0 at the chamber's wall, 1 in its middle. */
        double edge;
        /** Top of the mound shell here (0: none). */
        int mound;
        boolean rock;
    }

    /** Writes the part of every camp that lies in this chunk. */
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
                    // Local coordinates: v towards the entrance, u across it.
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

    // ── The camp itself ───────────────────────────────────────────────────────

    /**
     * The block {@code h} blocks above the clearing floor in a column (negative
     * below it), or {@code null} to leave the terrain as it is.
     */
    private static BlockState blockAt(Palette p, Camp camp, Col col, int h) {
        int roll = hash(col.wx, col.wz, h);
        Cell cell = col.cell;

        // The dens first: caves dug under, and mounds heaped over, the land.
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

        // The barrier: a wandering thicket ring on top of the hollow's rim.
        int up = h - col.rel;
        if (col.wallHeight > 0 && up >= 1 && up <= col.wallHeight) {
            // The outside of the wall carries more brush; the foot is trampled debris.
            int pick = roll % 100;
            if (col.wallCore > 0.55 && pick < 18) {
                return p.barrierAlt;
            }
            if (up == 1 && col.wallCore < 0.35 && pick < 30) {
                return p.barrierFloor;
            }
            return p.barrier;
        }

        // The ground itself: trodden floor in the clearing, bare earth up the wall.
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

    // ── The thicket barrier ─────────────────────────────────────────────────

    /**
     * Works out the camp wall at a column. A cat camp is not a castle: it is a
     * dense tangle of brambles, brush and fallen wood that cats can slip
     * through but predators and rivals cannot. The ring wanders in and out,
     * its thickness and height vary a lot (tall brambly stands, thin low
     * stretches, the odd hole), and it sits on the rim of the hollow.
     */
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

        // The gate: a gap that follows the meandering path out of the camp.
        if (col.v > 0 && Math.abs(col.u - pathOffset(col.v)) <= GATE) {
            return;
        }
        // Thin stretches have the odd hole a cat could slip through.
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

    // ── The entrance path ───────────────────────────────────────────────────

    /** How far the path centreline drifts sideways at distance {@code v}. */
    private static double pathOffset(double v) {
        if (v <= 0.0) {
            return 0.0;
        }
        return Math.sin(v * 0.16) * 2.2 * Math.min(1.0, v / 10.0);
    }

    /** Half-width of the worn path; it narrows as it fades into the grass. */
    private static double pathWidth(double v) {
        double fade = 1.0 - ClanNoise.smooth(PATH_REACH - 12.0, PATH_REACH, v);
        return (1.6 + ClanNoise.fbm(v * 0.2, 3.7, 1, 77) * 0.5) * fade + 0.4;
    }

    private static BlockState pathBlock(Palette p, int v, int roll) {
        // Near the gate the ground is bare and packed; further out grass
        // starts to win back the edges of the trail.
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

    // ── The clearing floor and the wall ─────────────────────────────────────

    /**
     * The floor. Never a block in the {@code dirt} tag: vanilla decoration
     * would grow trees and grass on it, and a camp should stay clear. Instead
     * the clearing is a patchwork of packed, trodden ground: bare paths between
     * dens, worn patches in front of each den mouth, gravel and leaf litter.
     */
    private static BlockState floor(Palette p, Camp camp, Col col, int roll) {
        int u = col.u;
        int v = col.v;

        // Around a den mound the ground stays plain so the doorway reads clearly.
        for (Den den : camp.dens()) {
            if (Math.hypot(u - den.u(), v - den.v()) < den.radius() + den.shell() + 1.0) {
                return (roll % 10 == 0) ? Blocks.GRAVEL.defaultBlockState() : p.floorB;
            }
        }

        // Worn trails between the dens and the centre: trampled bare ground.
        for (Den den : camp.dens()) {
            double len = Math.hypot(den.u(), den.v());
            if (len < 1.0) {
                continue;
            }
            // Distance along and across the line from the camp centre to the den.
            double along = (u * den.u() + v * den.v()) / len;
            double across = Math.abs((u * -den.v() + v * den.u()) / len);
            if (along > 2.0 && along < len - den.radius() - den.shell() + 1.0 && across < 1.8) {
                return (roll % 8 == 0) ? p.floorB : p.path;
            }
        }

        // Patchy worn ground: noise decides where the grass has been trodden away.
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
        // Occasional drift of leaves in the corners of the camp.
        if (pick < 20 && col.sr > 20.0) {
            return p.leafLitter;
        }
        return p.floorA;
    }

    /**
     * The inside of the hollow's wall: bare earth, stones and gravel. Like the
     * floor it avoids the {@code dirt} tag so nothing grows on it.
     */
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

    // ── The dens ──────────────────────────────────────────────────────────────

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /**
     * What the dens do to one column, or {@code null} if none touches it.
     *
     * <p>Each den is a cave of overlapping chambers. A chamber has a flat floor
     * {@link #DEPTH} blocks below the clearing (rising unevenly near its walls)
     * and a domed, ragged ceiling; the door is a wide ramp of one-block steps
     * running in from the clearing, under an arch in the mound. Above the
     * ground the main chamber sits under a lumpy mound: packed earth at the
     * base and a ragged brush roof (stone for the Highrock).</p>
     *
     * <p>The caves never break the surface: a chamber is only dug where there
     * are at least two blocks of earth or mound over its ceiling. So the side
     * chambers appear only where they run into the hollow's wall, burrowing
     * into the hillside, and never leave a pit in the open clearing.</p>
     */
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

            // Slow noise makes the walls and the mound lumpy.
            double lump = ClanNoise.fbm(col.wx * 0.17, col.wz * 0.17, 2, den.seed());
            double warp = 1.0 - 0.2 * lump;

            // How far inside any chamber (f) and inside the main mound (g) this column is.
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

            // The door: along runs from the den centre towards the middle of the camp.
            double along = du * den.doorU() + dv * den.doorV();
            double across = Math.abs(du * den.doorV() - dv * den.doorU());
            double mouth = den.radius() + den.shell();
            boolean ramp = across <= RAMP_HALF && along > -0.3 * den.radius() && along <= mouth + 1.0;

            // The mound over the main chamber (flatter-topped for the rock).
            int mound = 0;
            if (g > 0.0) {
                double rise = 1.0 - g;
                double profile = den.rock()
                        ? Math.pow(1.0 - rise * rise * rise, 1.0 / 3.0)
                        : Math.sqrt(1.0 - rise * rise);
                mound = (int) Math.round(den.height() * profile * (1.0 + 0.16 * lump));
                if (ramp) {
                    // An arch over the doorway.
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
                    // The floor climbs unevenly towards the walls.
                    floor = -DEPTH + (f < 0.07 ? 2 : 1);
                    if (hash(col.wx, col.wz, 77) % 3 == 0) {
                        floor -= 1;
                    }
                }

                double fc = Math.max(0.0, Math.min(1.0, f));
                double dome = den.ceiling() * Math.sqrt(1.0 - (1.0 - fc) * (1.0 - fc));
                int top = (int) Math.round(dome + 0.7 * lump);
                top = Math.max(top, floor + 3);
                // Always leave two blocks of earth or mound overhead.
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

    /**
     * The block of the den mound at height {@code h}: earth (with the odd
     * stone) at the base and a ragged brush roof on top, or stone for the
     * Highrock.
     */
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

    /**
     * What fills the inside of a cave cell: air, or part of a moss nest on the
     * floor (a carpet, or a raised moss block topped with a carpet). Beds hug
     * the walls and the middle of the chamber stays open.
     */
    private static BlockState nest(Cell cell, int h, int wx, int wz) {
        if (!cell.nestOk || h > cell.floor + 2) {
            return AIR;
        }
        // Nests are laid out in 2 x 2 patches.
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

    /**
     * Stone for the Highrock: a jumble of stone, andesite and cobble, with
     * mossy cobble in the crevices.
     */
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

    // ── Materials per kind of land ────────────────────────────────────────────

    /**
     * The blocks a camp is built from. {@code path} is the trampled ground,
     * {@code pathEdge} the half-worn ground at the trail's edge,
     * {@code floorA}/{@code floorB} the two commonest clearing floors,
     * {@code leafLitter} a drift of fallen leaves, {@code moundEarth} the
     * packed earth a den is dug into, {@code moundStone} stones in its walls,
     * and {@code barrierFloor} trampled debris at the foot of the thicket.
     */
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

        /** Persistent, so camp leaves never decay. */
        private static BlockState leaves(Block block) {
            return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        }
    }

    /** A salt derived from the camp position, so each camp's noise is its own. */
    private static int campSalt(Camp camp, int salt) {
        return hash(camp.x(), camp.z(), salt);
    }

    /** A per-camp number in [0, 1). */
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