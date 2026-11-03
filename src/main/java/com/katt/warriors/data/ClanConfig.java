package com.katt.warriors.data;

import com.katt.warriors.data.RebirthChoice.Clan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * How the clanlands are laid out: one slot per clan, each with a name, a kind
 * of terrain, a size and a colour, and whether the clan exists at all.
 *
 * <p>The slots are the eight {@link Clan}s, so ids, scents and saved player
 * data stay stable however the clans are customised. An empty name means "use
 * the default (translated) name".</p>
 */
public final class ClanConfig {

    public static final int MAX_NAME_LENGTH = 20;
    /** At least this many clans always exist. */
    public static final int MIN_ENABLED = 2;

    /** Colours a clan can use for its name. */
    public static final int[] PALETTE = {
            0x7BC96F, 0x5BA7E0, 0xA78BD9, 0xE8C170, 0xE48AAE, 0xE8855C, 0xC9CED6, 0x9FD9C8
    };

    /** Terrain each clan has by default (in {@link Clan} order). */
    private static final Terrain[] DEFAULT_TERRAIN = {
            Terrain.FOREST, Terrain.LAKE, Terrain.MARSH, Terrain.MOOR,
            Terrain.MOUNTAIN, Terrain.TOWN, Terrain.SCRUB, Terrain.TAIGA
    };

    public enum Size {
        SMALL(0.84D), NORMAL(1.0D), LARGE(1.2D);

        private final double weight;

        Size(double weight) {
            this.weight = weight;
        }

        public double weight() {
            return weight;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String translationKey() {
            return "warriors.clans.size." + id();
        }

        public static Size byId(String value) {
            for (Size size : values()) {
                if (size.id().equalsIgnoreCase(value)) {
                    return size;
                }
            }
            return NORMAL;
        }
    }

    /** One clan's settings. */
    public static final class Slot {
        /** Custom name; empty means the default name. */
        public String name = "";
        public Terrain terrain = Terrain.FOREST;
        public boolean enabled = true;
        public Size size = Size.NORMAL;
        /** Index into {@link #PALETTE}. */
        public int colour;

        public Slot copy() {
            Slot copy = new Slot();
            copy.name = name;
            copy.terrain = terrain;
            copy.enabled = enabled;
            copy.size = size;
            copy.colour = colour;
            return copy;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Slot slot)) {
                return false;
            }
            return enabled == slot.enabled && colour == slot.colour
                    && terrain == slot.terrain && size == slot.size
                    && name.equals(slot.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, terrain, enabled, size, colour);
        }
    }

    private final Slot[] slots = new Slot[Clan.values().length];
    private boolean locked;

    private ClanConfig() {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new Slot();
        }
    }

    public static ClanConfig defaults() {
        ClanConfig config = new ClanConfig();
        for (int i = 0; i < config.slots.length; i++) {
            config.slots[i].terrain = DEFAULT_TERRAIN[i];
            config.slots[i].colour = i % PALETTE.length;
        }
        return config;
    }

    /** An empty config for deserialisers to fill in. */
    public static ClanConfig blank() {
        return defaults();
    }

    public Slot slot(Clan clan) {
        return slots[clan.ordinal()];
    }

    public Slot slot(int index) {
        return slots[index];
    }

    public int slotCount() {
        return slots.length;
    }

    public boolean isEnabled(Clan clan) {
        return slot(clan).enabled;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public int enabledCount() {
        int count = 0;
        for (Slot slot : slots) {
            if (slot.enabled) {
                count++;
            }
        }
        return count;
    }

    public List<Clan> enabledClans() {
        List<Clan> list = new ArrayList<>();
        for (Clan clan : Clan.values()) {
            if (isEnabled(clan)) {
                list.add(clan);
            }
        }
        return list;
    }

    /** The first enabled clan, used when a choice names one that no longer exists. */
    public Clan firstEnabled() {
        for (Clan clan : Clan.values()) {
            if (isEnabled(clan)) {
                return clan;
            }
        }
        return Clan.THUNDERCLAN;
    }

    /** Adds one clan (the lowest-numbered missing one). */
    public void addClan() {
        for (Slot slot : slots) {
            if (!slot.enabled) {
                slot.enabled = true;
                return;
            }
        }
    }

    /** Removes one clan (the highest-numbered existing one), keeping the minimum. */
    public void removeClan() {
        if (enabledCount() <= MIN_ENABLED) {
            return;
        }
        for (int i = slots.length - 1; i >= 0; i--) {
            if (slots[i].enabled) {
                slots[i].enabled = false;
                return;
            }
        }
    }

    public ClanConfig copy() {
        ClanConfig copy = new ClanConfig();
        for (int i = 0; i < slots.length; i++) {
            copy.slots[i] = slots[i].copy();
        }
        copy.locked = locked;
        return copy;
    }

    /** Cleans up anything that came from outside (names, ranges, minimum clans). */
    public void sanitize() {
        for (Slot slot : slots) {
            slot.name = cleanName(slot.name);
            if (slot.terrain == null || !slot.terrain.selectable()) {
                slot.terrain = Terrain.FOREST;
            }
            if (slot.size == null) {
                slot.size = Size.NORMAL;
            }
            slot.colour = Math.floorMod(slot.colour, PALETTE.length);
        }
        for (int i = 0; enabledCount() < MIN_ENABLED && i < slots.length; i++) {
            slots[i].enabled = true;
        }
    }

    public static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c >= 32 && c != 127 && c != '\u00a7') {
                builder.append(c);
            }
        }
        String name = builder.toString().strip();
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH).strip() : name;
    }

    /** Equality of the settings (the locked flag is not a setting). */
    public boolean sameSettings(ClanConfig other) {
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i].equals(other.slots[i])) {
                return false;
            }
        }
        return true;
    }
}