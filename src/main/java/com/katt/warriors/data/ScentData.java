package com.katt.warriors.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Which territory scents a cat has learned.
 *
 * <p>Every scent starts out unknown. Each time a cat crosses into another
 * territory it smells it once more; after {@link #LEARN_VISITS} visits the
 * scent is known. The player's own clan always smells like home.</p>
 */
public final class ScentData {

    /** Compound holding the number of visits per territory id. */
    public static final String NBT_VISITS = "warriors.scent";
    /** Territory id the cat was last announced in. */
    public static final String NBT_LAST = "warriors.scent_last";

    /** Visits needed before a scent is known. */
    public static final int LEARN_VISITS = 3;

    private ScentData() {}

    public static int visits(Player player, String territory) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(NBT_VISITS)) {
            return 0;
        }
        return data.getCompound(NBT_VISITS).getInt(territory);
    }

    /** Records one more visit and returns the new total. */
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