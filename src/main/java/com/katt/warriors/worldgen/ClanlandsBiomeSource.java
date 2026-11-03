package com.katt.warriors.worldgen;

import com.katt.warriors.data.RebirthChoice.Clan;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.BiomeSource;

import java.util.stream.Stream;

public class ClanlandsBiomeSource extends BiomeSource {

    public static final Codec<ClanlandsBiomeSource> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Biome.LIST_CODEC.fieldOf("biomes")
                            .forGetter(ClanlandsBiomeSource::biomes)
            ).apply(instance, ClanlandsBiomeSource::new));

    private static final ThreadLocal<int[]> LAST = ThreadLocal.withInitial(
            () -> new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE, 0});

    private final HolderSet<Biome> biomes;

    public ClanlandsBiomeSource(HolderSet<Biome> biomes) {
        this.biomes = biomes;
    }

    public HolderSet<Biome> biomes() {
        return this.biomes;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.biomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ,
                                       Climate.Sampler sampler) {
        int x = QuartPos.toBlock(quartX);
        int z = QuartPos.toBlock(quartZ);

        int[] last = LAST.get();
        if (last[0] != x || last[1] != z) {
            last[0] = x;
            last[1] = z;
            last[2] = ClanlandsTerrain.biomeIndexAt(x, z);
        }
        return biomeAt(last[2]);
    }

    private Holder<Biome> biomeAt(int index) {
        if (this.biomes.size() == 0) {
            throw new IllegalStateException("Clanlands biome source has no biomes");
        }
        return this.biomes.get(Math.max(0, Math.min(index, this.biomes.size() - 1)));
    }
}
