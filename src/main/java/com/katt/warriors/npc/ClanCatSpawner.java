package com.katt.warriors.npc;

import com.katt.warriors.data.PeltData;
import com.katt.warriors.data.RebirthChoice.Clan;
import com.katt.warriors.npc.ClanCatNames.Role;
import com.katt.warriors.worldgen.ClanCamps;
import com.katt.warriors.worldgen.ClanCamps.Camp;
import com.katt.warriors.worldgen.ClanCamps.Den;
import com.katt.warriors.worldgen.ClanlandsDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Spawns {@link ClanCatNpc} entities into each clan camp when the server starts.
 *
 * <p>Each den in a camp gets between 2 and 5 NPCs. The Highrock (den 0) always
 * gets one leader. NPCs are not duplicated across reloads: existing NPCs are
 * counted before adding new ones. NPCs never despawn ({@link ClanCatNpc#removeWhenFarAway}
 * returns false), so they persist between sessions.</p>
 *
 * <p>Den assignments by {@link ClanCamps} layout index:</p>
 * <pre>
 *   0 – Highrock     → leader + deputy
 *   1 – Warriors     → warriors
 *   2 – Apprentices  → apprentices
 *   3 – Nursery      → queens (warriors, role = WARRIOR) and kits
 *   4 – Medicine cat → medicine cats
 *   5 – Elders       → elders
 * </pre>
 */
public class ClanCatSpawner {

    /** Number of NPCs to try to place in each den (min inclusive, max inclusive). */
    private static final int[] DEN_MIN = {2, 3, 2, 2, 1, 2};
    private static final int[] DEN_MAX = {4, 6, 4, 4, 2, 4};

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = ClanlandsDimension.get(server);
        if (level == null) {
            return;
        }
        spawnAll(level);
    }

    /** Spawns NPCs in every clan camp. Skips camps that already have enough cats. */
    public static void spawnAll(ServerLevel level) {
        List<Camp> camps = ClanCamps.camps();
        for (Camp camp : camps) {
            spawnCamp(level, camp);
        }
    }

    // ── Per-camp spawning ─────────────────────────────────────────────────

    private static void spawnCamp(ServerLevel level, Camp camp) {
        Clan clan = camp.clan();
        Den[] dens = camp.dens();
        if (dens == null) return;

        // Count already-spawned NPCs for this clan to avoid duplicating on reload.
        long existingCount = level.getEntitiesOfClass(ClanCatNpc.class,
                new net.minecraft.world.phys.AABB(
                        camp.x() - ClanCamps.RADIUS,
                        camp.groundY() - ClanCamps.RADIUS,
                        camp.z() - ClanCamps.RADIUS,
                        camp.x() + ClanCamps.RADIUS,
                        camp.groundY() + ClanCamps.RADIUS,
                        camp.z() + ClanCamps.RADIUS),
                npc -> npc.getNpcClan() == clan).size();

        if (existingCount > 0) {
            // Camp already populated (e.g. world reloaded); skip.
            return;
        }

        Random rng = new Random(clan.ordinal() * 0xDEADC0DEL + camp.x() * 31L + camp.z());
        // No two cats of one camp share a name.
        Set<String> usedNames = new HashSet<>();

        for (int denIdx = 0; denIdx < dens.length && denIdx < DEN_MIN.length; denIdx++) {
            Den den = dens[denIdx];
            int count = DEN_MIN[denIdx] + rng.nextInt(DEN_MAX[denIdx] - DEN_MIN[denIdx] + 1);

            for (int i = 0; i < count; i++) {
                Role role = roleForDen(denIdx, i);
                spawnNpc(level, camp, den, clan, role, rng, usedNames);
            }
        }
    }

    /** Returns the appropriate role for an NPC in a given den slot. */
    private static Role roleForDen(int denIdx, int slotInDen) {
        return switch (denIdx) {
            case 0 -> slotInDen == 0 ? Role.LEADER : Role.DEPUTY;
            case 1 -> Role.WARRIOR;
            case 2 -> Role.APPRENTICE;
            case 3 -> slotInDen == 0 ? Role.WARRIOR : Role.KIT;
            case 4 -> Role.MEDICINE_CAT;
            case 5 -> Role.ELDER;
            default -> Role.WARRIOR;
        };
    }

    // ── Individual NPC placement ───────────────────────────────────────────

    private static void spawnNpc(ServerLevel level, Camp camp, Den den,
                                 Clan clan, Role role, Random rng, Set<String> usedNames) {
        BlockPos pos = findSpawnPos(level, camp, den, rng);
        if (pos == null) return;

        ClanCatNpc npc = ClanCatNpcRegistry.CLAN_CAT_NPC.get().create(level);
        if (npc == null) return;

        npc.setNpcClan(clan);
        npc.setNpcRole(role);
        for (int attempt = 0; attempt < 12; attempt++) {
            npc.randomiseAppearance(rng);
            if (usedNames.add(npc.getNpcName())) {
                break;
            }
        }
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                rng.nextFloat() * 360.0f, 0.0f);
        npc.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                MobSpawnType.MOB_SUMMONED, null, null);
        level.addFreshEntity(npc);
    }

    /**
     * Finds a solid-footed spawn position inside or near a den.
     * Falls back to the camp clearing if no solid ground is found inside.
     */
    private static BlockPos findSpawnPos(ServerLevel level, Camp camp, Den den, Random rng) {
        int cx = (int) Math.round(ClanCamps.worldX(camp, den.u(), den.v()));
        int cz = (int) Math.round(ClanCamps.worldZ(camp, den.u(), den.v()));

        // Try a few candidate positions around the den centre.
        for (int attempt = 0; attempt < 12; attempt++) {
            int ox = (int) Math.round((rng.nextFloat() * 2.0f - 1.0f) * den.radius() * 0.6f);
            int oz = (int) Math.round((rng.nextFloat() * 2.0f - 1.0f) * den.radius() * 0.6f);
            int x = cx + ox;
            int z = cz + oz;
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);

            BlockPos candidate = new BlockPos(x, y, z);
            // Make sure the block below is solid and the space above is air.
            if (!level.getBlockState(candidate.below()).isAir()
                    && level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }

        // Last resort: just above the camp centre clearing
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                camp.x(), camp.z());
        return new BlockPos(camp.x(), y, camp.z());
    }
}