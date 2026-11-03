package com.katt.warriors.cutscene;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class IntroSavedData extends SavedData {

    private static final String DATA_NAME = "warriors_intro";
    private static final String TAG_SEEN = "seen";
    private static final String TAG_ID = "id";

    private final Set<UUID> seen = new HashSet<>();

    public static IntroSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(IntroSavedData::load, IntroSavedData::new, DATA_NAME);
    }

    public boolean hasSeen(UUID player) {
        return this.seen.contains(player);
    }

    public void markSeen(UUID player) {
        if (this.seen.add(player)) {
            this.setDirty();
        }
    }

    public void forget(UUID player) {
        if (this.seen.remove(player)) {
            this.setDirty();
        }
    }

    public void forgetAll() {
        if (!this.seen.isEmpty()) {
            this.seen.clear();
            this.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID id : this.seen) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(TAG_ID, id);
            list.add(entry);
        }
        tag.put(TAG_SEEN, list);
        return tag;
    }

    private static IntroSavedData load(CompoundTag tag) {
        IntroSavedData data = new IntroSavedData();
        ListTag list = tag.getList(TAG_SEEN, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID(TAG_ID)) {
                data.seen.add(entry.getUUID(TAG_ID));
            }
        }
        return data;
    }
}
