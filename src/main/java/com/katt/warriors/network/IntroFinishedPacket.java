package com.katt.warriors.network;

import com.katt.warriors.cutscene.IntroCutsceneHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record IntroFinishedPacket() {

    public static final IntroFinishedPacket INSTANCE = new IntroFinishedPacket();

    public void encode(FriendlyByteBuf buf) {
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
