package com.katt.warriors.client.cutscene;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client-side glue: opens the cutscene screen when the server asks, waiting for
 * the world to finish loading if necessary.
 */
public final class IntroCutsceneClient {

    /** How long to wait for other GUI screens (loading terrain etc.) to close. */
    private static final int MAX_WAIT_TICKS = 100;

    private static boolean pending;
    private static int waitedTicks;

    public IntroCutsceneClient() {}

    /** Called from {@code StartIntroPacket#handle} on the client thread. */
    public static void requestStart() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.screen instanceof IntroCutsceneScreen) {
            // Already playing (e.g. a replay was requested twice).
            pending = false;
            return;
        }

        if (mc.player != null && mc.level != null && mc.screen == null) {
            mc.setScreen(new IntroCutsceneScreen());
            pending = false;
            return;
        }

        pending = true;
        waitedTicks = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pending) {
            return;
        }

        waitedTicks++;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (mc.screen instanceof IntroCutsceneScreen) {
            pending = false;
            return;
        }

        if (mc.screen == null || waitedTicks > MAX_WAIT_TICKS) {
            pending = false;
            mc.setScreen(new IntroCutsceneScreen());
        }
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        pending = false;
    }
}
