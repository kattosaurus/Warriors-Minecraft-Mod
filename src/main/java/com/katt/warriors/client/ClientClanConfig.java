package com.katt.warriors.client;

import com.katt.warriors.data.ClanConfig;

public final class ClientClanConfig {

    private static ClanConfig config = ClanConfig.defaults();
    private static boolean canEdit;

    private ClientClanConfig() {}

    public static ClanConfig config() {
        return config;
    }

    public static boolean canEdit() {
        return canEdit && !config.isLocked();
    }

    public static void set(ClanConfig newConfig, boolean editable) {
        config = newConfig;
        canEdit = editable;
    }
}
