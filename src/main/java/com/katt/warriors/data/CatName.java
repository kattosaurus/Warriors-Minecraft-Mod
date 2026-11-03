package com.katt.warriors.data;

import com.katt.warriors.data.RebirthChoice.Age;
import com.katt.warriors.npc.ClanCatNames;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Random;

public final class CatName {

    public static final String NBT_PREFIX = "warriors.name.prefix";
    public static final String NBT_SUFFIX = "warriors.name.suffix";

    private CatName() {}

    public record Name(String prefix, String suffix) {
        public String full() {
            return ClanCatNames.compose(prefix, suffix);
        }
    }

    public static String getPrefix(Player player) {
        return player.getPersistentData().getString(NBT_PREFIX);
    }

    public static String getSuffix(Player player) {
        return player.getPersistentData().getString(NBT_SUFFIX);
    }

    public static boolean has(Player player) {
        return !getPrefix(player).isEmpty() && !getSuffix(player).isEmpty();
    }

    public static String full(Player player) {
        return has(player) ? ClanCatNames.compose(getPrefix(player), getSuffix(player)) : "";
    }

    public static void set(Player player, String prefix, String suffix) {
        CompoundTag data = player.getPersistentData();
        data.putString(NBT_PREFIX, prefix);
        data.putString(NBT_SUFFIX, suffix);
        player.refreshDisplayName();
    }

    public static void clear(Player player) {
        CompoundTag data = player.getPersistentData();
        data.remove(NBT_PREFIX);
        data.remove(NBT_SUFFIX);
        player.refreshDisplayName();
    }

    public static void assign(Player player, String rawPrefix, String rawSuffix) {
        Name name = resolve(rawPrefix, rawSuffix, RebirthChoice.getAge(player), new Random());
        set(player, name.prefix(), name.suffix());
    }

    public static void ensure(Player player) {
        if (!has(player) && RebirthChoice.hasChosen(player)) {
            Name name = resolve("", "", RebirthChoice.getAge(player),
                    new Random(player.getUUID().getLeastSignificantBits()));
            set(player, name.prefix(), name.suffix());
        }
    }

    public static Name resolve(String rawPrefix, String rawSuffix, Age age, Random rng) {
        String prefix = ClanCatNames.cleanPrefix(rawPrefix);
        if (prefix.length() < ClanCatNames.MIN_PART_LENGTH) {
            prefix = "";
        }

        String suffix = ClanCatNames.fixedSuffix(age);
        if (suffix == null) {
            suffix = ClanCatNames.cleanSuffix(rawSuffix);
            if (suffix.length() < ClanCatNames.MIN_PART_LENGTH
                    || ClanCatNames.isReservedSuffix(suffix)) {
                suffix = "";
            }
        }

        if (prefix.isEmpty() && !suffix.isEmpty()) {
            prefix = ClanCatNames.randomPrefix(rng, suffix);
        } else if (prefix.isEmpty()) {
            prefix = ClanCatNames.randomPrefix(rng, "");
        }
        if (suffix.isEmpty()) {
            suffix = ClanCatNames.randomWarriorSuffix(rng, prefix);
        }
        return new Name(prefix, suffix);
    }
}
