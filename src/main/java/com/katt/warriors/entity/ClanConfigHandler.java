package com.katt.warriors.entity;

import com.katt.warriors.data.ClanConfigManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClanConfigHandler {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ClanConfigManager.load(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        ClanConfigManager.reset();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClanConfigManager.sendTo(player);
        }
    }
}
