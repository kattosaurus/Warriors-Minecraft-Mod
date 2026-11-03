package com.katt.warriors.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/** Saving, loading and sending {@link ClanConfig}s. */
public final class ClanConfigIO {

    private ClanConfigIO() {}

    // ── NBT (saved with the world) ───────────────────────────────────────────

    public static CompoundTag toNbt(ClanConfig config) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("locked", config.isLocked());
        ListTag list = new ListTag();
        for (int i = 0; i < config.slotCount(); i++) {
            ClanConfig.Slot slot = config.slot(i);
            CompoundTag entry = new CompoundTag();
            entry.putString("name", slot.name);
            entry.putString("terrain", slot.terrain.id());
            entry.putBoolean("enabled", slot.enabled);
            entry.putString("size", slot.size.id());
            entry.putInt("colour", slot.colour);
            list.add(entry);
        }
        tag.put("slots", list);
        return tag;
    }

    public static ClanConfig fromNbt(CompoundTag tag) {
        ClanConfig config = ClanConfig.blank();
        config.setLocked(tag.getBoolean("locked"));
        ListTag list = tag.getList("slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < config.slotCount() && i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ClanConfig.Slot slot = config.slot(i);
            slot.name = entry.getString("name");
            slot.terrain = Terrain.byId(entry.getString("terrain"));
            slot.enabled = entry.getBoolean("enabled");
            slot.size = ClanConfig.Size.byId(entry.getString("size"));
            slot.colour = entry.getInt("colour");
        }
        config.sanitize();
        return config;
    }

    // ── Network ──────────────────────────────────────────────────────────────

    public static void write(FriendlyByteBuf buf, ClanConfig config) {
        buf.writeBoolean(config.isLocked());
        for (int i = 0; i < config.slotCount(); i++) {
            ClanConfig.Slot slot = config.slot(i);
            buf.writeUtf(slot.name, ClanConfig.MAX_NAME_LENGTH * 2);
            buf.writeUtf(slot.terrain.id(), 16);
            buf.writeBoolean(slot.enabled);
            buf.writeUtf(slot.size.id(), 16);
            buf.writeVarInt(slot.colour);
        }
    }

    public static ClanConfig read(FriendlyByteBuf buf) {
        ClanConfig config = ClanConfig.blank();
        config.setLocked(buf.readBoolean());
        for (int i = 0; i < config.slotCount(); i++) {
            ClanConfig.Slot slot = config.slot(i);
            slot.name = buf.readUtf(ClanConfig.MAX_NAME_LENGTH * 2);
            slot.terrain = Terrain.byId(buf.readUtf(16));
            slot.enabled = buf.readBoolean();
            slot.size = ClanConfig.Size.byId(buf.readUtf(16));
            slot.colour = buf.readVarInt();
        }
        config.sanitize();
        return config;
    }
}