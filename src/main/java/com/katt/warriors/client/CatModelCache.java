package com.katt.warriors.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;

/**
 * Lazy-initialised cache that holds a single baked CatModel for the local
 * client. The model is re-baked whenever the EntityModelSet changes (e.g. on
 * resource-pack reload), instead of on every rendered frame.
 */
public final class CatModelCache {

    private CatModelCache() {}

    private static CatModel<?> instance = null;
    private static EntityModelSet bakedFrom = null;

    /**
     * Returns the cached model, or bakes a new one from the current
     * EntityModelSet.
     */
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
            // Layer not registered yet — return whatever we have (may be null first frame)
        }
        return instance;
    }
}
