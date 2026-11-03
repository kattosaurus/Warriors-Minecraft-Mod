package com.katt.warriors.worldgen;

import com.katt.warriors.data.RebirthChoice.Clan;
import com.katt.warriors.data.Terrain;
import com.katt.warriors.worldgen.ClanlandsLayout.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class ClanlandsTerrain {

    public static final int SEA_LEVEL = 63;

    public static final int VILLAGE_Y = 68;

    public static final int RIVER_BIOME = 9;
    public static final int RIM_BIOME = 10;

    private static final double BLEND = 40.0;

    private ClanlandsTerrain() {}

    private static Site siteOf(Clan clan) {
        return ClanlandsLayout.current().siteOf(clan);
    }

    public static int anchorX(Clan clan) {
        return siteOf(clan).x();
    }

    public static int anchorZ(Clan clan) {
        return siteOf(clan).z();
    }

    public static BlockPos anchor(Clan clan) {
        int x = anchorX(clan);
        int z = anchorZ(clan);
        return new BlockPos(x, columnAt(x, z).groundY() + 1, z);
    }

    public static Clan clanAt(int x, int z) {
        return ClanlandsLayout.current().sites()[sample(x, z).site].clan();
    }

    public static int biomeIndexAt(int x, int z) {
        Sample sample = sample(x + 2.0, z + 2.0);
        if (sample.rim > 0.55) {
            return RIM_BIOME;
        }
        if (sample.height < SEA_LEVEL - 0.5) {
            return RIVER_BIOME;
        }
        return ClanlandsLayout.current().sites()[sample.site].terrain().biomeIndex();
    }

    public static boolean isRiverWater(int x, int z) {
        return sample(x, z).height < SEA_LEVEL - 0.5;
    }

    private static final class Sample {
        double height;
        int site;
        double rim;
    }

    private static Sample sample(double x, double z) {
        return sample(x, z, true);
    }

    public static double naturalHeight(double x, double z) {
        return sample(x, z, false).height;
    }

    public static int groundY(int x, int z) {
        return (int) Math.floor(sample(x, z).height + 0.5);
    }

    private static Sample sample(double x, double z, boolean flattenCamps) {
        Sample out = new Sample();

        double wx = x + 55.0 * ClanNoise.fbm(x * 0.006, z * 0.006, 2, 11);
        double wz = z + 55.0 * ClanNoise.fbm(x * 0.006 + 91.7, z * 0.006 - 33.1, 2, 12);

        Site[] sites = ClanlandsLayout.current().sites();
        int count = sites.length;
        double[] distance = new double[count];
        double nearest = Double.MAX_VALUE;
        int nearestIndex = 0;
        for (int i = 0; i < count; i++) {
            double dx = wx - sites[i].x();
            double dz = wz - sites[i].z();
            distance[i] = Math.sqrt(dx * dx + dz * dz) / sites[i].weight();
            if (distance[i] < nearest) {
                nearest = distance[i];
                nearestIndex = i;
            }
        }
        out.site = nearestIndex;

        double broad = ClanNoise.fbm(x * 0.0035, z * 0.0035, 3, 21);
        double rolling = ClanNoise.fbm(x * 0.012, z * 0.012, 3, 22);
        double detail = ClanNoise.fbm(x * 0.045, z * 0.045, 2, 23);
        double ridge = ClanNoise.ridged(x * 0.006, z * 0.006, 3, 24);

        double sum = 0.0;
        double weights = 0.0;
        for (int i = 0; i < count; i++) {
            double gap = distance[i] - nearest;
            if (gap > BLEND * 4.0) {
                continue;
            }
            double weight = Math.exp(-gap / BLEND);
            sum += weight * profile(sites[i], x, z, broad, rolling, detail, ridge);
            weights += weight;
        }
        double height = sum / weights;

        double rimDistance = Math.hypot(x, z - 5.0);
        double rim = ClanNoise.smooth(470.0, 650.0, rimDistance);
        out.rim = rim;
        if (rim > 0.0) {
            double crest = ClanNoise.ridged(x * 0.012, z * 0.012, 3, 61);
            height += rim * (24.0 + 40.0 * crest);
        }

        double river = Math.abs(ClanNoise.fbm(x * 0.0042 + 17.3, z * 0.0042 - 8.1, 3, 51));
        double channel = 1.0 - ClanNoise.smooth(0.016, 0.056, river);
        if (channel > 0.0) {
            double nearestSite = Double.MAX_VALUE;
            for (Site site : sites) {
                nearestSite = Math.min(nearestSite,
                        Math.hypot(x - site.x(), z - site.z()));
            }
            double clear = ClanNoise.smooth(46.0, 92.0, nearestSite);
            double lowland = 1.0 - ClanNoise.smooth(72.0, 86.0, height);
            double strength = channel * clear * lowland * (1.0 - rim);
            if (strength > 0.0) {
                double bed = SEA_LEVEL - 2.0 - 2.5 * channel;
                height = ClanNoise.lerp(height, Math.min(height, bed), strength);
            }
        }

        Site townSite = ClanlandsLayout.current().village();
        if (townSite != null) {
            double village = Math.hypot(x - townSite.x(), z - townSite.z());
            double plateau = 1.0 - ClanNoise.smooth(64.0, 120.0, village);
            if (plateau > 0.0) {
                height = ClanNoise.lerp(height, VILLAGE_Y, plateau);
            }
        }

        if (flattenCamps) {
            for (ClanCamps.Camp camp : ClanCamps.camps()) {
                double dx = x - camp.x();
                double dz = z - camp.z();
                if (Math.abs(dx) > ClanCamps.EASE_RADIUS || Math.abs(dz) > ClanCamps.EASE_RADIUS) {
                    continue;
                }
                double fromCamp = Math.sqrt(dx * dx + dz * dz);
                double plateau = 1.0 - ClanNoise.smooth(ClanCamps.FLAT_RADIUS, ClanCamps.EASE_RADIUS, fromCamp);
                if (plateau > 0.0) {
                    double target = camp.rimY();
                    if (fromCamp <= ClanCamps.FLAT_RADIUS) {
                        target -= ClanCamps.insetDepth(camp, x, z);
                    }
                    height = ClanNoise.lerp(height, target, plateau);
                }
            }
        }

        out.height = height;
        return out;
    }

    private static double profile(Site site, double x, double z,
                                  double broad, double rolling, double detail, double ridge) {
        switch (site.terrain()) {
            case FOREST:
                return 70.0 + broad * 6.0 + rolling * 9.0 + detail * 1.6;

            case MOOR:
                return 75.0 + broad * 4.0 + rolling * 6.0 + detail * 0.7;

            case MOUNTAIN: {
                double distance = Math.hypot(x - site.x(), z - site.z());
                double mass = 1.0 - ClanNoise.smooth(0.0, 170.0, distance);
                return 70.0 + rolling * 5.0 * (1.0 - mass)
                        + Math.pow(mass, 1.5) * (30.0 + ridge * 13.0)
                        + detail * 1.2 * mass;
            }

            case MARSH: {
                double pool = ClanNoise.fbm(x * 0.03 + 50.0, z * 0.03 - 20.0, 2, 31);
                double height = 65.8 + broad * 2.4 + rolling * 3.6 + detail * 1.0;
                return height - ClanNoise.smooth(0.28, 0.55, pool) * 6.0;
            }

            case LAKE: {
                double distance = Math.hypot(x - site.x(), z - site.z());
                double wobble = ClanNoise.fbm(x * 0.018, z * 0.018, 2, 41);
                double ringRadius = 90.0 + 12.0 * wobble;

                double island = 64.6 + 8.0 * ClanNoise.smooth(0.0, 0.85, 1.0 - distance / ringRadius)
                        + rolling * 1.6;
                double mainland = 66.0 + broad * 3.0 + rolling * 3.4 + detail * 0.9;
                double land = ClanNoise.lerp(island, mainland,
                        ClanNoise.smooth(ringRadius - 6.0, ringRadius + 6.0, distance));

                double halfWidth = 26.0;
                double lake = 1.0 - ClanNoise.smooth(halfWidth * 0.35, halfWidth,
                        Math.abs(distance - ringRadius));
                double bed = SEA_LEVEL - 2.5 - 3.0 * lake;
                return ClanNoise.lerp(land, Math.min(land, bed), lake);
            }

            case TOWN:
                return VILLAGE_Y + rolling * 1.8 + detail * 0.4;

            case SCRUB:
                return 70.0 + broad * 4.0 + rolling * 6.0 + detail * 1.6;

            case TAIGA:
                return 72.0 + broad * 5.0 + rolling * 8.0 + ridge * 6.0 + detail * 1.4;

            case DESERT:
                return 71.0 + broad * 5.0 + rolling * 5.0 + ridge * 8.0 + detail * 0.9;

            case TUNDRA:
                return 73.0 + broad * 5.0 + rolling * 7.0 + ridge * 3.0 + detail * 1.2;

            default:
                return 69.0 + broad * 5.0 + rolling * 7.0 + detail * 1.4;
        }
    }

    public record Column(int groundY, BlockState surface, BlockState subsurface) {}

    public static Column columnAt(int x, int z) {
        Sample sample = sample(x, z);
        double height = sample.height;
        int ground = (int) Math.floor(height + 0.5);

        double slope = Math.max(
                Math.abs(sample(x + 3.0, z).height - height),
                Math.abs(sample(x, z + 3.0).height - height)) / 3.0;

        Terrain terrain = ClanlandsLayout.current().sites()[sample.site].terrain();
        double patch = ClanNoise.fbm(x * 0.05 + 7.0, z * 0.05 - 9.0, 2, 71);
        double speckle = ClanNoise.fbm(x * 0.13 - 4.0, z * 0.13 + 12.0, 1, 72);

        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState surface = grass;
        BlockState subsurface = dirt;

        if (ground < SEA_LEVEL - 1) {
            if (speckle > 0.55) {
                surface = Blocks.CLAY.defaultBlockState();
            } else if (patch > -0.15) {
                surface = Blocks.SAND.defaultBlockState();
            } else {
                surface = Blocks.GRAVEL.defaultBlockState();
            }
            subsurface = surface.is(Blocks.CLAY) ? Blocks.SAND.defaultBlockState() : surface;
        } else if (ground <= SEA_LEVEL + 1 && (patch > -0.3 || terrain == Terrain.LAKE)) {
            surface = Blocks.SAND.defaultBlockState();
            subsurface = Blocks.SAND.defaultBlockState();
        } else if (sample.rim > 0.35 && ground > 92) {
            surface = patch > 0.2 ? Blocks.GRAVEL.defaultBlockState() : stone;
            subsurface = stone;
        } else if (terrain == Terrain.MOUNTAIN && ground > 112) {
            surface = stone;
            subsurface = stone;
        } else if (slope >= 1.25) {
            surface = stone;
            subsurface = stone;
        } else if (slope >= 0.85) {
            surface = patch > 0.0 ? stone : Blocks.COARSE_DIRT.defaultBlockState();
            subsurface = surface.is(Blocks.STONE) ? stone : dirt;
        } else if (terrain == Terrain.MARSH) {
            surface = patch > 0.15 ? Blocks.PODZOL.defaultBlockState()
                    : (speckle > 0.45 ? Blocks.COARSE_DIRT.defaultBlockState() : grass);
        } else if (terrain == Terrain.TAIGA) {
            surface = patch > -0.25 ? Blocks.PODZOL.defaultBlockState()
                    : (speckle > 0.5 ? Blocks.COARSE_DIRT.defaultBlockState() : grass);
        } else if (terrain == Terrain.SCRUB) {
            surface = speckle > 0.4 ? Blocks.COARSE_DIRT.defaultBlockState()
                    : (patch > 0.4 ? Blocks.PODZOL.defaultBlockState() : grass);
        } else if (terrain == Terrain.DESERT) {
            surface = patch > 0.45 ? Blocks.RED_SAND.defaultBlockState()
                    : Blocks.SAND.defaultBlockState();
            subsurface = Blocks.SANDSTONE.defaultBlockState();
        } else if (terrain == Terrain.TUNDRA) {
            surface = speckle > 0.5 ? Blocks.GRAVEL.defaultBlockState()
                    : (patch > 0.35 ? Blocks.PODZOL.defaultBlockState() : grass);
        } else if (terrain == Terrain.FOREST && patch > 0.55) {
            surface = Blocks.PODZOL.defaultBlockState();
        } else if (terrain == Terrain.MOOR && speckle > 0.55) {
            surface = Blocks.COARSE_DIRT.defaultBlockState();
        }

        return new Column(ground, surface, subsurface);
    }
}
