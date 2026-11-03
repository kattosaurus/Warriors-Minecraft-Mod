package com.katt.warriors.worldgen;

import com.katt.warriors.Warriors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Keys and lookup helpers for the custom {@code warriors:clanlands} dimension.
 *
 * <p>The dimension itself is declared by the datapack files
 * {@code data/warriors/dimension_type/clanlands.json} and
 * {@code data/warriors/dimension/clanlands.json}; this class only exposes the
 * {@link ResourceKey} the rest of the mod uses.</p>
 */
public final class ClanlandsDimension {

    public static final ResourceKey<Level> LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(Warriors.MODID, "clanlands"));

    private ClanlandsDimension() {}

    /** Returns the loaded clanlands level, or {@code null} if the datapack failed. */
    public static ServerLevel get(MinecraftServer server) {
        return server == null ? null : server.getLevel(LEVEL);
    }
}
