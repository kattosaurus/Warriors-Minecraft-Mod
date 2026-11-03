package com.katt.warriors.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum Terrain {
    FOREST(0, true),
    LAKE(1, true),
    MARSH(2, true),
    MOOR(3, true),
    MOUNTAIN(4, true),
    TOWN(5, true),
    SCRUB(6, true),
    TAIGA(7, true),
    WILD(8, false),
    DESERT(11, true),
    TUNDRA(12, true);

    private final int biomeIndex;
    private final boolean selectable;

    Terrain(int biomeIndex, boolean selectable) {
        this.biomeIndex = biomeIndex;
        this.selectable = selectable;
    }

    public int biomeIndex() {
        return biomeIndex;
    }

    public boolean selectable() {
        return selectable;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "warriors.terrain." + id();
    }

    public String descriptionKey() {
        return "warriors.terrain." + id() + ".desc";
    }

    public String scentKey() {
        return "warriors.scent.hint." + id();
    }

    public static List<Terrain> choices() {
        List<Terrain> list = new ArrayList<>();
        for (Terrain terrain : values()) {
            if (terrain.selectable) {
                list.add(terrain);
            }
        }
        return list;
    }

    public static Terrain byId(String value) {
        if (value != null) {
            for (Terrain terrain : values()) {
                if (terrain.selectable && terrain.id().equalsIgnoreCase(value)) {
                    return terrain;
                }
            }
        }
        return FOREST;
    }
}
