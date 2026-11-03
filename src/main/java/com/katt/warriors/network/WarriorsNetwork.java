package com.katt.warriors.network;

import com.katt.warriors.Warriors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Network channel used to push cosmetic data (cat coat + eye customisation)
 * from the server to clients, because Forge persistent data is not synced.
 */
public final class WarriorsNetwork {

    private static final String PROTOCOL_VERSION = "6";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Warriors.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextId;

    private WarriorsNetwork() {}

    /** Called once during common setup. */
    public static void register() {
        CHANNEL.messageBuilder(SyncAppearancePacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncAppearancePacket::encode)
                .decoder(SyncAppearancePacket::decode)
                .consumerMainThread(SyncAppearancePacket::handle)
                .add();

        CHANNEL.messageBuilder(StartIntroPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StartIntroPacket::encode)
                .decoder(StartIntroPacket::decode)
                .consumerMainThread(StartIntroPacket::handle)
                .add();

        CHANNEL.messageBuilder(IntroFinishedPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(IntroFinishedPacket::encode)
                .decoder(IntroFinishedPacket::decode)
                .consumerMainThread(IntroFinishedPacket::handle)
                .add();

        CHANNEL.messageBuilder(RebirthChoicePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RebirthChoicePacket::encode)
                .decoder(RebirthChoicePacket::decode)
                .consumerMainThread(RebirthChoicePacket::handle)
                .add();

        CHANNEL.messageBuilder(ClanConfigSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClanConfigSyncPacket::encode)
                .decoder(ClanConfigSyncPacket::decode)
                .consumerMainThread(ClanConfigSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(ClanConfigUpdatePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClanConfigUpdatePacket::encode)
                .decoder(ClanConfigUpdatePacket::decode)
                .consumerMainThread(ClanConfigUpdatePacket::handle)
                .add();
    }

    /**
     * Sends the player's appearance to everyone who can see them (including the
     * player themselves, so the local hand/eye renderer updates immediately).
     */
    public static void syncAppearance(ServerPlayer player) {
        CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                SyncAppearancePacket.fromPlayer(player));
    }

    /** Sends the appearance of {@code tracked} to a single viewer. */
    public static void syncAppearanceTo(ServerPlayer tracked, ServerPlayer viewer) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> viewer),
                SyncAppearancePacket.fromPlayer(tracked));
    }
}