package com.katt.warriors.data;

import com.katt.warriors.data.RebirthChoice.Age;
import com.katt.warriors.npc.ClanCatNames;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Random;

/**
 * A player's warrior name: a prefix and a suffix stored in the player's
 * persistent data (and mirrored to clients by {@code SyncAppearancePacket}).
 *
 * <p>The suffix follows the age the cat was born at: kits end in {@code kit},
 * adolescents in {@code paw}, and adults pick a warrior suffix of their own.
 * Anything the player leaves empty or gets wrong is filled in randomly, so a
 * name is always complete.</p>
 */
public final class CatName {

    public static final String NBT_PREFIX = "warriors.name.prefix";
    public static final String NBT_SUFFIX = "warriors.name.suffix";

    private CatName() {}

    /** A resolved name. */
    public record Name(String prefix, String suffix) {
        public String full() {
            return ClanCatNames.compose(prefix, suffix);
        }
    }

    // ── Reading ──────────────────────────────────────────────────────────────

    public static String getPrefix(Player player) {
        return player.getPersistentData().getString(NBT_PREFIX);
    }

    public static String getSuffix(Player player) {
        return player.getPersistentData().getString(NBT_SUFFIX);
    }

    public static boolean has(Player player) {
        return !getPrefix(player).isEmpty() && !getSuffix(player).isEmpty();
    }

    /** The written name ("Fernkit"), or an empty string when the player has none yet. */
    public static String full(Player player) {
        return has(player) ? ClanCatNames.compose(getPrefix(player), getSuffix(player)) : "";
    }

    // ── Writing ──────────────────────────────────────────────────────────────

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

    /**
     * Turns what the player typed into a complete, valid name for their age
     * and stores it. Empty or unusable parts are rolled randomly.
     */
    public static void assign(Player player, String rawPrefix, String rawSuffix) {
        Name name = resolve(rawPrefix, rawSuffix, RebirthChoice.getAge(player), new Random());
        set(player, name.prefix(), name.suffix());
    }

    /** Gives players who were born before names existed a random one (server side). */
    public static void ensure(Player player) {
        if (!has(player) && RebirthChoice.hasChosen(player)) {
            Name name = resolve("", "", RebirthChoice.getAge(player),
                    new Random(player.getUUID().getLeastSignificantBits()));
            set(player, name.prefix(), name.suffix());
        }
    }

    // ── Resolving ────────────────────────────────────────────────────────────

    /**
     * Builds a valid name from what was typed.
     *
     * <ul>
     *   <li>letters only; prefix capitalised, suffix lower case;</li>
     *   <li>kits always end in {@code kit} and adolescents in {@code paw};</li>
     *   <li>adults may not use {@code kit}, {@code paw} or {@code star}: those
     *       belong to a rank;</li>
     *   <li>a missing part is picked at random, so that the halves join well.</li>
     * </ul>
     */
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