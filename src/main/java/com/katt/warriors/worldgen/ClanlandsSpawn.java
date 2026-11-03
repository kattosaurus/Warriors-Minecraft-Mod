package com.katt.warriors.worldgen;

import com.katt.warriors.data.RebirthChoice.Clan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Resolves a safe, prepared spawn position for each clan's region centre.
 *
 * <p>The terrain is deterministic, but biome decoration may have placed a tree
 * over the anchor. A small pad is cleared above the surface so every player
 * wakes on solid ground in a readable, unobstructed spot.</p>
 */
public final class ClanlandsSpawn {

    private ClanlandsSpawn() {}

    /** Finds and prepares the spawn point for a clan. */
    public static BlockPos findSpawn(ServerLevel level, Clan clan) {
        if (KittypetVillage.isVillageClan(clan)) {
            // The village is part of the generated terrain: wake up on its plaza.
            BlockPos plaza = KittypetVillage.spawnPos();
            level.getChunk(plaza.getX() >> 4, plaza.getZ() >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    plaza.getX(), plaza.getZ());
            return new BlockPos(plaza.getX(), y, plaza.getZ());
        }

        BlockPos camp = ClanCamps.spawnPos(clan);
        if (camp != null) {
            // Clan members wake up in their camp's clearing, which is part of the terrain.
            level.getChunk(camp.getX() >> 4, camp.getZ() >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    camp.getX(), camp.getZ());
            return new BlockPos(camp.getX(), y, camp.getZ());
        }

        BlockPos anchor = ClanlandsTerrain.anchor(clan);
        level.getChunk(anchor.getX() >> 4, anchor.getZ() >> 4);

        ClanlandsTerrain.Column column = ClanlandsTerrain.columnAt(anchor.getX(), anchor.getZ());
        return clearPad(level, anchor, column.surface());
    }

    /**
     * Flattens a 5 × 5 pad at the anchor and clears enough headroom to stand.
     * Falls back to dirt underneath so the pad never floats.
     */
    private static BlockPos clearPad(ServerLevel level, BlockPos feet, BlockState floorState) {
        int baseY = feet.getY();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = feet.getX() + dx;
                int z = feet.getZ() + dz;

                // Support below the pad.
                for (int depth = 1; depth <= 3; depth++) {
                    BlockPos below = new BlockPos(x, baseY - depth, z);
                    BlockState state = level.getBlockState(below);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        level.setBlockAndUpdate(below, Blocks.DIRT.defaultBlockState());
                    }
                }

                level.setBlockAndUpdate(new BlockPos(x, baseY - 1, z), floorState);
                for (int dy = 0; dy <= 5; dy++) {
                    level.setBlockAndUpdate(new BlockPos(x, baseY + dy, z),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }

        return feet;
    }
}