package com.katt.warriors.network;

import com.katt.warriors.data.CatName;
import com.katt.warriors.data.EyeData;
import com.katt.warriors.data.PeltData;
import com.katt.warriors.data.RebirthChoice;
import com.katt.warriors.entity.CatPlayerHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class ClientAppearanceSync {

    private static final Map<UUID, SyncAppearancePacket> PENDING = new HashMap<>();

    private ClientAppearanceSync() {}

    public static void apply(SyncAppearancePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.level == null ? null : mc.level.getPlayerByUUID(packet.playerId());
        if (player == null) {
            PENDING.put(packet.playerId(), packet);
            return;
        }
        applyTo(player, packet);
    }

    public static void flushIfPending(Player player) {
        SyncAppearancePacket packet = PENDING.remove(player.getUUID());
        if (packet != null) {
            applyTo(player, packet);
        }
    }

    private static void applyTo(Player player, SyncAppearancePacket packet) {
        CompoundTag data = player.getPersistentData();

        if (!packet.variant().isEmpty()) {
            data.putString(CatPlayerHandler.NBT_CAT_VARIANT, packet.variant());
        }
        if (!packet.sclera().isEmpty()) {
            data.putString(EyeData.NBT_SCLERA, packet.sclera());
        }
        if (!packet.leftPupil().isEmpty()) {
            data.putString(EyeData.NBT_LEFT_PUPIL, packet.leftPupil());
        }
        if (!packet.rightPupil().isEmpty()) {
            data.putString(EyeData.NBT_RIGHT_PUPIL, packet.rightPupil());
        }
        if (packet.scleraColor().isEmpty()) {
            data.remove(EyeData.NBT_SCLERA_COLOR);
        } else {
            data.putString(EyeData.NBT_SCLERA_COLOR, packet.scleraColor());
        }
        if (packet.leftPupilColor().isEmpty()) {
            data.remove(EyeData.NBT_LEFT_PUPIL_COLOR);
        } else {
            data.putString(EyeData.NBT_LEFT_PUPIL_COLOR, packet.leftPupilColor());
        }
        if (packet.rightPupilColor().isEmpty()) {
            data.remove(EyeData.NBT_RIGHT_PUPIL_COLOR);
        } else {
            data.putString(EyeData.NBT_RIGHT_PUPIL_COLOR, packet.rightPupilColor());
        }

        if (packet.peltColor().isEmpty()) {
            data.remove(PeltData.NBT_PELT_COLOR);
        } else {
            data.putString(PeltData.NBT_PELT_COLOR, packet.peltColor());
        }

        if (packet.pattern().isEmpty()) {
            data.remove(PeltData.NBT_PATTERN);
        } else {
            data.putString(PeltData.NBT_PATTERN, packet.pattern());
        }

        if (!packet.mode().isEmpty()) {
            data.putString(RebirthChoice.NBT_MODE, packet.mode());
        }

        if (!packet.age().isEmpty()) {
            RebirthChoice.Age before = RebirthChoice.getAge(player);
            data.putString(RebirthChoice.NBT_AGE, packet.age());
            if (RebirthChoice.getAge(player) != before) {
                player.refreshDimensions();
            }
        }

        String nameBefore = CatName.full(player);
        if (packet.namePrefix().isEmpty() || packet.nameSuffix().isEmpty()) {
            data.remove(CatName.NBT_PREFIX);
            data.remove(CatName.NBT_SUFFIX);
        } else {
            data.putString(CatName.NBT_PREFIX, packet.namePrefix());
            data.putString(CatName.NBT_SUFFIX, packet.nameSuffix());
        }
        if (!CatName.full(player).equals(nameBefore)) {
            player.refreshDisplayName();
        }
    }
}
