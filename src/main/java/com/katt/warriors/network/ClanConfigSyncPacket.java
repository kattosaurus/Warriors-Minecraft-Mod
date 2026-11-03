package com.katt.warriors.network;

import com.katt.warriors.client.ClientClanConfig;
import com.katt.warriors.data.ClanConfig;
import com.katt.warriors.data.ClanConfigIO;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the world's clan layout and whether this player may change it. */
public record ClanConfigSyncPacket(ClanConfig config, boolean canEdit) {

    public void encode(FriendlyByteBuf buf) {
        ClanConfigIO.write(buf, config);
        buf.writeBoolean(canEdit);
    }

    public static ClanConfigSyncPacket decode(FriendlyByteBuf buf) {
        return new ClanConfigSyncPacket(ClanConfigIO.read(buf), buf.readBoolean());
    }

    public static void handle(ClanConfigSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // Keep the client classes out of the dedicated-server class loader.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientClanConfig.set(packet.config(), packet.canEdit()));
        ctx.setPacketHandled(true);
    }
}