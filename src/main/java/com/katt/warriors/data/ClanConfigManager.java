package com.katt.warriors.data;

import com.katt.warriors.network.ClanConfigSyncPacket;
import com.katt.warriors.network.WarriorsNetwork;
import com.katt.warriors.worldgen.ClanlandsLayout;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class ClanConfigManager {

    private static volatile ClanConfig current = ClanConfig.defaults();

    private ClanConfigManager() {}

    public static ClanConfig get() {
        return current;
    }

    public static void load(MinecraftServer server) {
        current = ClanConfigData.get(server).getConfig().copy();
        ClanlandsLayout.set(current);
    }

    public static void reset() {
        current = ClanConfig.defaults();
        ClanlandsLayout.set(current);
    }

    public static boolean canEdit(ServerPlayer player) {
        if (current.isLocked()) {
            return false;
        }
        MinecraftServer server = player.getServer();
        return player.hasPermissions(2)
                || (server != null && server.isSingleplayerOwner(player.getGameProfile()));
    }

    public static void update(MinecraftServer server, ClanConfig edited) {
        ClanConfig next = edited.copy();
        next.sanitize();
        next.setLocked(false);
        store(server, next);
        broadcast(server);
    }

    public static void lock(MinecraftServer server) {
        if (current.isLocked()) {
            return;
        }
        ClanConfig next = current.copy();
        next.setLocked(true);
        store(server, next);
        broadcast(server);
    }

    private static void store(MinecraftServer server, ClanConfig next) {
        current = next;
        ClanConfigData.get(server).setConfig(next.copy());
        ClanlandsLayout.set(next);
    }

    public static void sendTo(ServerPlayer player) {
        WarriorsNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClanConfigSyncPacket(current, canEdit(player)));
    }

    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player);
        }
    }
}
