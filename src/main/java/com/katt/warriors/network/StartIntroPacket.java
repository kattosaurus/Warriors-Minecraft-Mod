package com.katt.warriors.network;

import com.katt.warriors.client.cutscene.IntroCutsceneClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: play the first-join intro cutscene now.
 *
 * <p>The client opens a full-screen {@code Screen} and freezes all input until
 * the script finishes, then answers with {@link IntroFinishedPacket}.</p>
 */
public record StartIntroPacket() {

    public static final StartIntroPacket INSTANCE = new StartIntroPacket();

    public void encode(FriendlyByteBuf buf) {
        // No payload.
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
