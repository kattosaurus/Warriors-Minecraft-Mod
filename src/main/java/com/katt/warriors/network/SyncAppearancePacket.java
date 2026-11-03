package com.katt.warriors.network;

import com.katt.warriors.entity.CatPlayerHandler;
import com.katt.warriors.data.CatName;
import com.katt.warriors.data.EyeData;
import com.katt.warriors.data.PeltData;
import com.katt.warriors.data.RebirthChoice;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → client snapshot of one player's cosmetic appearance.
 *
 * <p>Forge's {@code ForgeData} compound (the player's persistent NBT) is not
 * part of the entity spawn packet, so values written by {@code /eye} on the
 * server never reach the client renderer on their own. This packet carries the
 * values across and is re-sent on login, respawn, dimension change, when another
 * player starts tracking us, and after every command mutation.</p>
 */
public record SyncAppearancePacket(
        UUID playerId,
        String variant,
        String sclera,
        String leftPupil,
        String rightPupil,
        String scleraColor,
        String leftPupilColor,
        String rightPupilColor,
        String peltColor,
        String mode,
        String pattern,
        String age,
        String namePrefix,
        String nameSuffix) {

    /** Builds a packet from the server-side player data. */
    public static SyncAppearancePacket fromPlayer(ServerPlayer player) {
        // Players born before names existed get one now.
        CatName.ensure(player);
        return new SyncAppearancePacket(
                player.getUUID(),
                player.getPersistentData().getString(CatPlayerHandler.NBT_CAT_VARIANT),
                EyeData.getSclera(player),
                EyeData.getLeftPupil(player),
                EyeData.getRightPupil(player),
                nullToEmpty(EyeData.getScleraColor(player)),
                nullToEmpty(EyeData.getLeftPupilColor(player)),
                nullToEmpty(EyeData.getRightPupilColor(player)),
                nullToEmpty(PeltData.getPeltColor(player)),
                RebirthChoice.getMode(player).id(),
                nullToEmpty(PeltData.getPattern(player)),
                RebirthChoice.getAge(player).id(),
                CatName.getPrefix(player),
                CatName.getSuffix(player));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(playerId);
        buf.writeUtf(variant, 64);
        buf.writeUtf(sclera, 128);
        buf.writeUtf(leftPupil, 128);
        buf.writeUtf(rightPupil, 128);
        buf.writeUtf(scleraColor, 16);
        buf.writeUtf(leftPupilColor, 16);
        buf.writeUtf(rightPupilColor, 16);
        buf.writeUtf(peltColor, 16);
        buf.writeUtf(mode, 32);
        buf.writeUtf(pattern, 32);
        buf.writeUtf(age, 32);
        buf.writeUtf(namePrefix, 32);
        buf.writeUtf(nameSuffix, 32);
    }

    public static SyncAppearancePacket decode(FriendlyByteBuf buf) {
        return new SyncAppearancePacket(
                buf.readUUID(),
                buf.readUtf(64),
                buf.readUtf(128),
                buf.readUtf(128),
                buf.readUtf(128),
                buf.readUtf(16),
                buf.readUtf(16),
                buf.readUtf(16),
                buf.readUtf(16),
                buf.readUtf(32),
                buf.readUtf(32),
                buf.readUtf(32),
                buf.readUtf(32),
                buf.readUtf(32));
    }

    public static void handle(SyncAppearancePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // Keep the client classes out of the dedicated-server class loader.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAppearanceSync.apply(packet));
        ctx.setPacketHandled(true);
    }
}