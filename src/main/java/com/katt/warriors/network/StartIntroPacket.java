package com.katt.warriors.network;

import com.katt.warriors.client.cutscene.IntroCutsceneClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record StartIntroPacket() {

    public static final StartIntroPacket INSTANCE = new StartIntroPacket();

    public void encode(FriendlyByteBuf buf) {
    }

    public static StartIntroPacket decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    public static void handle(StartIntroPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> IntroCutsceneClient::requestStart);
        ctx.setPacketHandled(true);
    }
}
