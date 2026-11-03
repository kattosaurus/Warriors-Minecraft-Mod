package com.katt.warriors.network;

import com.katt.warriors.cutscene.IntroCutsceneHandler;
import com.katt.warriors.data.CatName;
import com.katt.warriors.data.EyeData;
import com.katt.warriors.data.PeltData;
import com.katt.warriors.data.ClanConfigManager;
import com.katt.warriors.data.RebirthChoice;
import com.katt.warriors.entity.CatPlayerHandler;
import com.katt.warriors.worldgen.ClanlandsDimension;
import com.katt.warriors.worldgen.ClanlandsSpawn;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RebirthChoicePacket(
        String mode,
        String appearance,
        String clan,
        String peltColor,
        String leftEyeColor,
        String rightEyeColor,
        String scleraColor,
        String pattern,
        String age,
        String namePrefix,
        String nameSuffix) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(mode, 32);
        buf.writeUtf(appearance, 32);
        buf.writeUtf(clan, 32);
        buf.writeUtf(peltColor, 16);
        buf.writeUtf(leftEyeColor, 16);
        buf.writeUtf(rightEyeColor, 16);
        buf.writeUtf(scleraColor, 16);
        buf.writeUtf(pattern, 32);
        buf.writeUtf(age, 32);
        buf.writeUtf(namePrefix, 32);
        buf.writeUtf(nameSuffix, 32);
    }

    public static RebirthChoicePacket decode(FriendlyByteBuf buf) {
        return new RebirthChoicePacket(
                buf.readUtf(32),
                buf.readUtf(32),
                buf.readUtf(32),
                buf.readUtf(16),
                buf.readUtf(16),
                buf.readUtf(16),
                buf.readUtf(16),
                buf.readUtf(32),
                buf.readUtf(32),
                buf.readUtf(32),
                buf.readUtf(32));
    }

    public static void handle(RebirthChoicePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                apply(player, packet);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void apply(ServerPlayer player, RebirthChoicePacket packet) {
        RebirthChoice.Mode mode = RebirthChoice.Mode.byId(packet.mode());
        RebirthChoice.Appearance appearance = RebirthChoice.Appearance.byId(packet.appearance());
        RebirthChoice.Clan requested = RebirthChoice.Clan.byId(packet.clan());
        RebirthChoice.Clan clan = ClanConfigManager.get().isEnabled(requested)
                ? requested : ClanConfigManager.get().firstEnabled();

        RebirthChoice.setMode(player, mode);
        RebirthChoice.setAppearance(player, appearance);
        RebirthChoice.setClan(player, clan);
        RebirthChoice.setAge(player, RebirthChoice.Age.byId(packet.age()));
        CatName.assign(player, packet.namePrefix(), packet.nameSuffix());
        RebirthChoice.markChosen(player);

        if (isColour(packet.peltColor())) {
            PeltData.setPeltColor(player, packet.peltColor());
        }
        PeltData.setPattern(player, packet.pattern());
        if (isColour(packet.leftEyeColor())) {
            EyeData.setLeftPupilColor(player, packet.leftEyeColor());
        }
        if (isColour(packet.rightEyeColor())) {
            EyeData.setRightPupilColor(player, packet.rightEyeColor());
        }
        if (isColour(packet.scleraColor())) {
            EyeData.setScleraColor(player, packet.scleraColor());
        }

        IntroCutsceneHandler.finish(player);

        CatPlayerHandler.applyCatStats(player);

        if (mode == RebirthChoice.Mode.REALISTIC) {
            if (player.getServer() != null) {
                ClanConfigManager.lock(player.getServer());
            }
            ServerLevel level = ClanlandsDimension.get(player.getServer());
            if (level == null) {
                player.displayClientMessage(
                        Component.translatable("warriors.rebirth.missing_dimension"), false);
            } else {
                BlockPos spawn = ClanlandsSpawn.findSpawn(level, clan);
                player.teleportTo(level,
                        spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                        player.getYRot(), player.getXRot());
                player.displayClientMessage(
                        Component.translatable("warriors.rebirth.arriving"), false);
            }
        }

        MinecraftServer server = player.getServer();
        if (server != null) {
            server.execute(() -> {
                if (player.connection != null) {
                    WarriorsNetwork.syncAppearance(player);
                }
            });
        } else {
            WarriorsNetwork.syncAppearance(player);
        }
    }

    private static boolean isColour(String value) {
        return value != null && !value.isEmpty() && EyeData.isValidColor(value);
    }
}
