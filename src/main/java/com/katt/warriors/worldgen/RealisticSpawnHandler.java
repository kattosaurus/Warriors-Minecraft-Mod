package com.katt.warriors.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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
