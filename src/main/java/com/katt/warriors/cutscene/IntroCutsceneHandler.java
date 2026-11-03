package com.katt.warriors.cutscene;

import com.katt.warriors.network.StartIntroPacket;
import com.katt.warriors.network.WarriorsNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class IntroCutsceneHandler {

    private static final int START_DELAY_TICKS = 40;

    private static final int TIMEOUT_TICKS = 20 * 60 * 10;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        final UUID id;
        ServerPlayer player;
        int ticks;
        boolean started;
        boolean wasInvulnerable;

        Session(UUID id) {
            this.id = id;
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            schedule(player, false);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SESSIONS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) {
            return;
        }

        Iterator<Session> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            ServerPlayer player = session.player;

            if (player == null || player.hasDisconnected() || player.isRemoved()) {
                iterator.remove();
                continue;
            }

            session.ticks++;

            if (!session.started) {
                if (session.ticks < START_DELAY_TICKS) {
                    continue;
                }
                session.started = true;
                begin(player, session);
            } else {
                hold(player);
            }

            if (session.started && session.ticks > START_DELAY_TICKS + TIMEOUT_TICKS) {
                iterator.remove();
                finish(player, session);
            }
        }
    }

    public static boolean hasSeen(ServerPlayer player) {
        return IntroSavedData.get(player.serverLevel()).hasSeen(player.getUUID());
    }

    public static void start(ServerPlayer player, boolean force) {
        if (!force && hasSeen(player)) {
            return;
        }

        Session previous = SESSIONS.remove(player.getUUID());
        if (previous != null && previous.started) {
            player.setInvulnerable(previous.wasInvulnerable);
        }

        Session session = new Session(player.getUUID());
        session.player = player;
        session.ticks = force ? START_DELAY_TICKS - 10 : 0;
        SESSIONS.put(player.getUUID(), session);
    }

    public static void finish(ServerPlayer player) {
        finish(player, SESSIONS.remove(player.getUUID()));
    }

    private static void finish(ServerPlayer player, Session session) {
        if (session != null) {
            player.setInvulnerable(session.wasInvulnerable);
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        }
        IntroSavedData.get(player.serverLevel()).markSeen(player.getUUID());
    }

    public static void cancel(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            player.setInvulnerable(session.wasInvulnerable);
        }
    }

    public static void reset(ServerPlayer player) {
        IntroSavedData.get(player.serverLevel()).forget(player.getUUID());
    }

    public static void resetAll(ServerPlayer player) {
        IntroSavedData.get(player.serverLevel()).forgetAll();
    }

    private static void begin(ServerPlayer player, Session session) {
        session.wasInvulnerable = player.isInvulnerable();
        player.setInvulnerable(true);
        hold(player);

        WarriorsNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                StartIntroPacket.INSTANCE);
    }

    private static void hold(ServerPlayer player) {
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.clearFire();
        player.fallDistance = 0.0F;
    }

    private static void schedule(ServerPlayer player, boolean force) {
        start(player, force);
    }
}
