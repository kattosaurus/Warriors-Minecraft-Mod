package com.katt.warriors.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;

public final class CatModelCache {

    private CatModelCache() {}

    private static CatModel<?> instance = null;
    private static EntityModelSet bakedFrom = null;

    public static CatModel<?> getOrBake(Minecraft mc) {
        EntityModelSet modelSet = mc.getEntityModels();
        if (instance != null && modelSet == bakedFrom) {
            return instance;
        }

        try {
            var root = modelSet.bakeLayer(CatModel.LAYER_LOCATION);
            instance = new CatModel<>(root);
            bakedFrom = modelSet;
        } catch (Exception e) {
        }
        return instance;
    }
}
