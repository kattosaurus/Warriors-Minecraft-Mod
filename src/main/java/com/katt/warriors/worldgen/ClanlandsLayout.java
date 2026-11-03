package com.katt.warriors.worldgen;

import com.katt.warriors.data.ClanConfig;
import com.katt.warriors.data.RebirthChoice.Clan;
import com.katt.warriors.data.Terrain;

public final class ClanlandsLayout {

    public record Site(Clan clan, Terrain terrain, int x, int z, double weight) {}

    private static final int[][] POSITIONS = {
            {-272, -236},
            {-238, 22},
            {246, 34},
            {14, -298},
            {292, -218},
            {-6, 252},
            {-262, 276},
            {268, 262}
    };

    private static final double[] BASE_WEIGHT = {1.00, 1.02, 1.06, 1.08, 1.00, 0.98, 0.96, 1.00};

    private static volatile ClanlandsLayout current = of(ClanConfig.defaults());

    private final Site[] sites;
    private final Site village;

    private ClanlandsLayout(Site[] sites, Site village) {
        this.sites = sites;
        this.village = village;
    }

    public static ClanlandsLayout current() {
        return current;
    }

    public static void set(ClanConfig config) {
        current = of(config);
    }

    public static ClanlandsLayout of(ClanConfig config) {
        int enabled = config.enabledCount();
        Site[] sites = new Site[enabled + 1];
        Site village = null;

        int index = 0;
        for (Clan clan : Clan.values()) {
            if (!config.isEnabled(clan)) {
                continue;
            }
            ClanConfig.Slot slot = config.slot(clan);
            int ordinal = clan.ordinal();
            Site site = new Site(clan, slot.terrain, POSITIONS[ordinal][0], POSITIONS[ordinal][1],
                    BASE_WEIGHT[ordinal] * slot.size.weight());
            sites[index++] = site;
            if (village == null && slot.terrain == Terrain.TOWN) {
                village = site;
            }
        }
        sites[index] = new Site(null, Terrain.WILD, 4, -4, 0.92);
        return new ClanlandsLayout(sites, village);
    }

    public Site[] sites() {
        return sites;
    }

    public Site village() {
        return village;
    }

    public Site siteOf(Clan clan) {
        for (Site site : sites) {
            if (site.clan() == clan) {
                return site;
            }
        }
        return sites[0];
    }
}
