package com.katt.warriors.npc;

import com.katt.warriors.worldgen.ClanCamps;
import com.katt.warriors.worldgen.ClanCamps.Camp;
import com.katt.warriors.worldgen.ClanCamps.Den;
import com.katt.warriors.worldgen.ClanlandsDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Places invisible, named {@link ArmorStand} entities above each den entrance
 * so the den's purpose shows as a floating label in the camp.
 *
 * <p>Labels are placed only once, on the first server start after a world is
 * created. On subsequent starts the existing armor stands are found and skipped.</p>
 *
 * <p>Den index → label:</p>
 * <pre>
 *   0 – Highrock (Leader's Den)
 *   1 – Warriors' Den
 *   2 – Apprentices' Den
 *   3 – Nursery
 *   4 – Medicine Cat's Den
 *   5 – Elders' Den
 * </pre>
 */
public class DenLabelPlacer {

    /** Translation keys for each den label. */
    private static final String[] DEN_LABEL_KEYS = {
            "warriors.den.leader",
            "warriors.den.warriors",
            "warriors.den.apprentices",
            "warriors.den.nursery",
            "warriors.den.medicine",
            "warriors.den.elders"
    };

    /** Tag added to each label armor stand, used to detect existing labels. */
    private static final String LABEL_MARKER = "warriors.den_label_v2";

    /** Tag of the old labels, which were placed at wrong offsets; they are removed on start. */
    private static final String OLD_LABEL_MARKER = "warriors.den_label";

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = ClanlandsDimension.get(server);
        if (level == null) {
            return;
        }
        placeAll(level);
    }

    /** Places den labels in every camp. */
    public static void placeAll(ServerLevel level) {
        List<Camp> camps = ClanCamps.camps();
        for (Camp camp : camps) {
            placeCamp(level, camp);
        }
    }

    private static void placeCamp(ServerLevel level, Camp camp) {
        Den[] dens = camp.dens();
        if (dens == null) return;

        removeOldLabels(level, camp);

        for (int i = 0; i < dens.length && i < DEN_LABEL_KEYS.length; i++) {
            Den den = dens[i];
            String labelKey = DEN_LABEL_KEYS[i];

            // Position: just outside the den doorway. u/v and doorU/doorV are camp-local,
            // so convert to world coordinates (the door vector is a direction, so rotate it too).
            double lu = den.u() + den.doorU() * (den.radius() + 1.0);
            double lv = den.v() + den.doorV() * (den.radius() + 1.0);
            int lx = (int) Math.floor(ClanCamps.worldX(camp, lu, lv));
            int lz = (int) Math.floor(ClanCamps.worldZ(camp, lu, lv));
            // Above the arch over the doorway (the mound is at least 5 high there).
            int ly = camp.groundY() + 7;

            BlockPos pos = new BlockPos(lx, ly, lz);

            // Skip if a label armor stand already exists near this position.
            if (hasLabelNear(level, pos)) {
                continue;
            }

            ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
            stand.moveTo(lx + 0.5, ly, lz + 0.5, 0.0f, 0.0f);

            // setMarker(boolean) is private in ArmorStand, but readAdditionalSaveData
            // reads the "Marker" tag and calls it for us. NOTE: it also resets
            // fields such as "Invisible", so apply the other settings AFTER this.
            CompoundTag markerTag = new CompoundTag();
            markerTag.putBoolean("Marker", true);
            markerTag.putBoolean("Invisible", true);
            stand.readAdditionalSaveData(markerTag);

            stand.setCustomName(Component.translatable(labelKey));
            stand.setCustomNameVisible(true);
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            stand.addTag(LABEL_MARKER);
            level.addFreshEntity(stand);
        }
    }

    /** Removes labels from the previous version, which sat at the wrong offsets. */
    private static void removeOldLabels(ServerLevel level, Camp camp) {
        double r = ClanCamps.RADIUS + 12.0;
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class,
                new net.minecraft.world.phys.AABB(camp.x() - r, camp.groundY() - 8, camp.z() - r,
                        camp.x() + r, camp.groundY() + 30, camp.z() + r),
                st -> st.getTags().contains(OLD_LABEL_MARKER))) {
            stand.discard();
        }
    }

    private static boolean hasLabelNear(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(ArmorStand.class,
                new net.minecraft.world.phys.AABB(pos).inflate(3.0),
                stand -> stand.getTags().contains(LABEL_MARKER)).isEmpty();
    }
}