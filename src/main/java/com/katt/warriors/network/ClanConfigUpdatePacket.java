package com.katt.warriors.network;

import com.katt.warriors.data.ClanConfig;
import com.katt.warriors.data.ClanConfigIO;
import com.katt.warriors.data.ClanConfigManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClanConfigUpdatePacket(ClanConfig config) {

    public void encode(FriendlyByteBuf buf) {
        ClanConfigIO.write(buf, config);
    }

    public static ClanConfigUpdatePacket decode(FriendlyByteBuf buf) {
        return new ClanConfigUpdatePacket(ClanConfigIO.read(buf));
    }

    public static void handle(ClanConfigUpdatePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.getServer() == null) {
                return;
            }
            if (ClanConfigManager.canEdit(player)) {
                ClanConfigManager.update(player.getServer(), packet.config());
            } else {
                ClanConfigManager.sendTo(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
