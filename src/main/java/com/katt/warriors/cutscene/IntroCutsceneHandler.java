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

/**
 * Drives the first-join intro cutscene from the server side.
 *
 * <p>Workflow:</p>
 * <ol>
 *   <li>On login, if the player has not seen the intro in this world, queue a
 *       session. After {@link #START_DELAY_TICKS} the player is frozen
 *       (invulnerable, no fire, no fall damage) and a {@link StartIntroPacket}
 *       is sent.</li>
 *   <li>The client plays the cutscene; when it ends it sends
 *       {@code IntroFinishedPacket}. The handler then unfreezes the player and
 *       records them in {@link IntroSavedData}.</li>
 *   <li>If no finish packet arrives within {@link #TIMEOUT_TICKS}, the session
 *       is force-finished so a crashed client cannot lock the player in place
 *       forever.</li>
 * </ol>
 *
 * <p>Disconnecting (or changing dimension / respawning) mid-cutscene drops the
 * session without marking the player as seen, so the intro plays again the next
 * time they join.</p>
 */
public class IntroCutsceneHandler {

    /** Grace period after login before the screen is pushed (lets the world load). */
    private static final int START_DELAY_TICKS = 40;

    /**
     * Safety net: lines now wait for clicks, so a slow reader can take much
     * longer than the script's 66 s. Ten minutes still frees a crashed client.
     */
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

    // ── Events ───────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            schedule(player, false);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Do not mark as seen: the player should get the intro again.
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

    // ── Public API ───────────────────────────────────────────────────────────

    /** True when this player has already watched the intro in this world. */
    public static boolean hasSeen(ServerPlayer player) {
        return IntroSavedData.get(player.serverLevel()).hasSeen(player.getUUID());
    }

    /**
     * Queues the intro for a player.
     *
     * @param force {@code true} for the replay command (ignores the seen flag)
     */
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
        // Replays are triggered from in-game, so there is no world to load.
        session.ticks = force ? START_DELAY_TICKS - 10 : 0;
        SESSIONS.put(player.getUUID(), session);
    }

    /** Called when the client reports the cutscene is over (or the timeout hits). */
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

    /** Drops an active session without marking the player as seen. */
    public static void cancel(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            player.setInvulnerable(session.wasInvulnerable);
        }
    }

    /** Clears the "seen" flag so the intro plays again on the next login. */
    public static void reset(ServerPlayer player) {
        IntroSavedData.get(player.serverLevel()).forget(player.getUUID());
    }

    /** Clears the "seen" flag for every player in this world. */
    public static void resetAll(ServerPlayer player) {
        IntroSavedData.get(player.serverLevel()).forgetAll();
    }

    // ── Internals ────────────────────────────────────────────────────────────

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
