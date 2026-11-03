package com.katt.warriors.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** The world's clan layout, saved with the overworld. */
public class ClanConfigData extends SavedData {

    private static final String NAME = "warriors_clans";

    private ClanConfig config = ClanConfig.defaults();

    public static ClanConfigData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ClanConfigData::load, ClanConfigData::new, NAME);
    }

    public static ClanConfigData load(CompoundTag tag) {
        ClanConfigData data = new ClanConfigData();
        data.config = ClanConfigIO.fromNbt(tag.getCompound("config"));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("config", ClanConfigIO.toNbt(config));
        return tag;
    }

    public ClanConfig getConfig() {
        return config;
    }

    public void setConfig(ClanConfig config) {
        this.config = config;
        setDirty();
    }
}