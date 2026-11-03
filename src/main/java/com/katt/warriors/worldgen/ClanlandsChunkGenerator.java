package com.katt.warriors.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Chunk generator for the clanlands valley.
 *
 * <p>Terrain is built from {@link ClanlandsTerrain}: deepslate and stone, a few
 * dirt layers, the surface block and water up to sea level in rivers and lakes. Vanilla biome decoration (trees, flowers, …) still runs on top, so the
 * regions feel alive without a full noise-based generator.</p>
 *
 * <p>Structures and natural mob spawns are disabled: the valley is a peaceful
 * warrior's home, not a dungeon.</p>
 */
public class ClanlandsChunkGenerator extends ChunkGenerator {

    public static final Codec<ClanlandsChunkGenerator> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ClanlandsBiomeSource.CODEC.fieldOf("biome_source")
                            .forGetter(ClanlandsChunkGenerator::source)
            ).apply(instance, ClanlandsChunkGenerator::new));

    private static final int GEN_DEPTH = 384;
    private static final int MIN_Y = -64;

    private final ClanlandsBiomeSource source;

    public ClanlandsChunkGenerator(ClanlandsBiomeSource source) {
        super(source);
        this.source = source;
    }

    public ClanlandsBiomeSource source() {
        return this.source;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** No structure sets: keeps villages and strongholds out of the valley. */
    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets,
                                                    RandomState randomState, long seed) {
        return ChunkGeneratorStructureState.createForFlat(
                randomState, seed, this.getBiomeSource(), Stream.empty());
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender,
                                                        RandomState randomState,
                                                        StructureManager structureManager,
                                                        ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        int minY = chunk.getMinBuildHeight();

        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                ClanlandsTerrain.Column column = ClanlandsTerrain.columnAt(worldX, worldZ);
                int groundY = column.groundY();

                for (int y = minY; y <= groundY; y++) {
                    BlockState state;
                    int depth = groundY - y;
                    if (depth >= 1 && y <= minY + 1) {
                        state = bedrock;
                    } else if (depth == 0) {
                        state = column.surface();
                    } else if (depth <= 3) {
                        state = column.subsurface();
                    } else if (y < -8 || (y < 0 && ((worldX * 31 + worldZ * 17 + y * 13) & 3) != 0)) {
                        state = deepslate;
                    } else {
                        state = stone;
                    }

                    pos.set(localX, y, localZ);
                    chunk.setBlockState(pos, state, false);
                    oceanFloor.update(localX, y, localZ, state);
                    worldSurface.update(localX, y, localZ, state);
                }

                for (int y = groundY + 1; y <= ClanlandsTerrain.SEA_LEVEL; y++) {
                    pos.set(localX, y, localZ);
                    chunk.setBlockState(pos, water, false);
                    oceanFloor.update(localX, y, localZ, water);
                    worldSurface.update(localX, y, localZ, water);
                }
            }
        }

        // The Kittypet village is written straight into the chunks it covers,
        // on top of the levelled terrain, so it is there the moment they exist.
        KittypetVillage.generate(chunk, oceanFloor, worldSurface);

        // Likewise the clan camps (never for strays and rogues).
        ClanCamps.generate(chunk, oceanFloor, worldSurface);

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
                             RandomState randomState, ChunkAccess chunk) {
        // Surfaces are written in fillFromNoise so they match the heightmaps.
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
                             BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk, GenerationStep.Carving step) {
        // No caves or canyons.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // Natural spawning is handled (and denied) by RealisticSpawnHandler.
    }

    @Override
    public int getGenDepth() {
        return GEN_DEPTH;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getSeaLevel() {
        return ClanlandsTerrain.SEA_LEVEL;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type,
                             LevelHeightAccessor heightAccessor, RandomState randomState) {
        int groundY = ClanlandsTerrain.columnAt(x, z).groundY();
        // In the river ring the top of the column is the water surface.
        return Math.max(groundY, ClanlandsTerrain.SEA_LEVEL) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor,
                                     RandomState randomState) {
        int minY = heightAccessor.getMinBuildHeight();
        int height = heightAccessor.getHeight();
        BlockState[] states = new BlockState[height];

        ClanlandsTerrain.Column column = ClanlandsTerrain.columnAt(x, z);
        int groundY = column.groundY();

        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int i = 0; i < height; i++) {
            int y = minY + i;
            int depth = groundY - y;

            if (y <= minY + 1) {
                states[i] = bedrock;
            } else if (depth == 0) {
                states[i] = column.surface();
            } else if (depth > 0 && depth <= 3) {
                states[i] = column.subsurface();
            } else if (depth > 0) {
                states[i] = stone;
            } else if (y <= ClanlandsTerrain.SEA_LEVEL) {
                states[i] = water;
            } else {
                states[i] = air;
            }
        }

        return new NoiseColumn(minY, states);
    }

    /** No monsters spawn naturally; peaceful animals are welcome. */
    @Override
    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(
            Holder<Biome> biome, StructureManager structureManager,
            MobCategory category, BlockPos pos) {
        if (category == MobCategory.MONSTER) {
            return WeightedRandomList.create();
        }
        return super.getMobsAt(biome, structureManager, category, pos);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
        lines.add("Warriors clanlands: " + ClanlandsTerrain.clanAt(pos.getX(), pos.getZ()));
    }
}