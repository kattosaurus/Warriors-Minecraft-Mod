package com.katt.warriors.network;

import com.katt.warriors.cutscene.IntroCutsceneHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: the intro cutscene has finished (or was closed).
 * The handler then unfreezes the player and marks them as "seen".
 */
public record IntroFinishedPacket() {

    public static final IntroFinishedPacket INSTANCE = new IntroFinishedPacket();

    public void encode(FriendlyByteBuf buf) {
        // No payload.
    }

    public static IntroFinishedPacket decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    public static void handle(IntroFinishedPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                IntroCutsceneHandler.finish(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
