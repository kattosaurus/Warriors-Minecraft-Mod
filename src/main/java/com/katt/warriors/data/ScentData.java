package com.katt.warriors.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class ScentData {

    public static final String NBT_VISITS = "warriors.scent";
    public static final String NBT_LAST = "warriors.scent_last";

    public static final int LEARN_VISITS = 3;

    private ScentData() {}

    public static int visits(Player player, String territory) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(NBT_VISITS)) {
            return 0;
        }
        return data.getCompound(NBT_VISITS).getInt(territory);
    }

    public static int recordVisit(Player player, String territory) {
        CompoundTag data = player.getPersistentData();
        CompoundTag visits = data.getCompound(NBT_VISITS);
        int count = visits.getInt(territory) + 1;
        visits.putInt(territory, count);
        data.put(NBT_VISITS, visits);
        return count;
    }

    public static boolean isKnown(Player player, String territory) {
        return visits(player, territory) >= LEARN_VISITS;
    }

    public static String getLast(Player player) {
        return player.getPersistentData().getString(NBT_LAST);
    }

    public static void setLast(Player player, String territory) {
        player.getPersistentData().putString(NBT_LAST, territory);
    }

    public static void clear(Player player) {
        player.getPersistentData().remove(NBT_VISITS);
        player.getPersistentData().remove(NBT_LAST);
    }
}
