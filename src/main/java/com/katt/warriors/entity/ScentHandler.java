package com.katt.warriors.entity;

import com.katt.warriors.data.ClanConfig;
import com.katt.warriors.data.ClanConfigManager;
import com.katt.warriors.data.ClanNames;
import com.katt.warriors.data.RebirthChoice;
import com.katt.warriors.data.RebirthChoice.Clan;
import com.katt.warriors.data.ScentData;
import com.katt.warriors.worldgen.ClanlandsDimension;
import com.katt.warriors.worldgen.ClanlandsTerrain;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScentHandler {

    private static final int SETTLE_SECONDS = 3;

    private record Pending(String territory, int seconds) {}

    private final Map<UUID, Pending> pending = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0
                || player.level().dimension() != ClanlandsDimension.LEVEL
                || !RebirthChoice.hasChosen(player)) {
            return;
        }

        int x = player.getBlockX();
        int z = player.getBlockZ();

        if (ClanlandsTerrain.biomeIndexAt(x, z) == ClanlandsTerrain.RIM_BIOME) {
            return;
        }

        Clan clan = ClanlandsTerrain.clanAt(x, z);
        String territory = clan == null ? "wild" : clan.id();

        if (territory.equals(ScentData.getLast(player))) {
            pending.remove(player.getUUID());
            return;
        }

        Pending current = pending.get(player.getUUID());
        int seconds = current != null && current.territory().equals(territory)
                ? current.seconds() + 1 : 1;
        if (seconds < SETTLE_SECONDS) {
            pending.put(player.getUUID(), new Pending(territory, seconds));
            return;
        }

        pending.remove(player.getUUID());
        ScentData.setLast(player, territory);
        announce(player, clan, territory);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        pending.remove(event.getEntity().getUUID());
    }

    private void announce(ServerPlayer player, Clan clan, String territory) {
        if (clan == null) {
            thought(player, Component.translatable("warriors.scent.wild"));
            bar(player, Component.translatable("warriors.scent.bar.wild"));
            return;
        }

        ClanConfig config = ClanConfigManager.get();
        Component name = ClanNames.display(config, clan);

        if (clan == RebirthChoice.getClan(player)) {
            thought(player, Component.translatable("warriors.scent.home", name));
            bar(player, Component.translatable("warriors.scent.bar.home", name));
            return;
        }

        int visits = ScentData.recordVisit(player, territory);
        if (visits == 1) {
            thought(player, Component.translatable("warriors.scent.unknown",
                    Component.translatable(config.slot(clan).terrain.scentKey()), name));
            bar(player, Component.translatable("warriors.scent.bar.unknown"));
        } else if (visits < ScentData.LEARN_VISITS) {
            thought(player, Component.translatable("warriors.scent.familiar", name));
            bar(player, Component.translatable("warriors.scent.bar.unknown"));
        } else if (visits == ScentData.LEARN_VISITS) {
            thought(player, Component.translatable("warriors.scent.learned", name));
            bar(player, Component.translatable("warriors.scent.bar.known", name));
        } else {
            thought(player, Component.translatable("warriors.scent.known", name));
            bar(player, Component.translatable("warriors.scent.bar.known", name));
        }
    }

    private static void thought(ServerPlayer player, Component text) {
        player.sendSystemMessage(text.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private static void bar(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }
}
