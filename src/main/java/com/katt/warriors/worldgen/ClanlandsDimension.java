package com.katt.warriors.worldgen;

import com.katt.warriors.Warriors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class ClanlandsDimension {

    public static final ResourceKey<Level> LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(Warriors.MODID, "clanlands"));

    private ClanlandsDimension() {}

    public static ServerLevel get(MinecraftServer server) {
        return server == null ? null : server.getLevel(LEVEL);
    }
}
