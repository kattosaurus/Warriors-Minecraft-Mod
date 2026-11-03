package com.katt.warriors.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Keeps the clanlands peaceful.
 *
 * <p>The chunk generator already reports empty spawn lists, but mob spawners,
 * mods and structure-free special spawns can still ask to place a monster. This
 * handler cancels any monster spawn inside the dimension, so a realistic
 * warrior never has to fight a zombie while patrolling.</p>
 */
public class RealisticSpawnHandler {

    @SubscribeEvent
    public void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        ServerLevel level = event.getLevel().getLevel();
        if (!level.dimension().equals(ClanlandsDimension.LEVEL)) {
            return;
        }
        if (event.getEntity().getType().getCategory() == MobCategory.MONSTER) {
            event.setSpawnCancelled(true);
        }
    }
}
