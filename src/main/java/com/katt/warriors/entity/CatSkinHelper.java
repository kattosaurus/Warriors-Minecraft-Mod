package com.katt.warriors.entity;

import com.katt.warriors.Warriors;
import com.katt.warriors.data.PeltData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CatSkinHelper {

    private CatSkinHelper() {}

    private static final String TEXTURE_DIR = "textures/entity/cat_player/";

    private static final ResourceLocation WHITE = variantLocation("white");
    private static final ResourceLocation ORANGE = variantLocation("orange");

    private static final Map<ResourceLocation, Boolean> EXISTS = new ConcurrentHashMap<>();

    public record Coat(ResourceLocation texture, float[] tint) {}

    public static Coat resolve(Player player) {
        String pelt = PeltData.getPeltColor(player);
        if (pelt == null) {
            return new Coat(forVariant(getVariantName(player)),
                    PeltData.variationTint(player.getUUID()));
        }

        int rgb = com.katt.warriors.data.EyeData.parseColor(pelt);

        ResourceLocation pattern = patternTexture(PeltData.getPattern(player));
        if (pattern != null && rgb != -1) {
            return new Coat(pattern, PeltData.softTint(rgb));
        }

        float[] tint = PeltData.tint(pelt);
        return new Coat(WHITE, tint == null ? new float[] {1F, 1F, 1F} : tint);
    }

    public static ResourceLocation patternTexture(String pattern) {
        if (pattern == null || !PeltData.isPattern(pattern)) {
            return null;
        }
        ResourceLocation location = variantLocation(pattern.toLowerCase(Locale.ROOT));
        return exists(location) ? location : null;
    }

    public static ResourceLocation getTexture(Player player) {
        if (PeltData.getPeltColor(player) != null) {
            return WHITE;
        }
        return forVariant(getVariantName(player));
    }

    public static ResourceLocation forVariant(String variant) {
        if (variant == null || variant.isEmpty()) {
            return ORANGE;
        }
        String name = variant.toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_]+")) {
            return ORANGE;
        }
        ResourceLocation location = variantLocation(name);
        return exists(location) ? location : ORANGE;
    }

    private static boolean exists(ResourceLocation location) {
        return EXISTS.computeIfAbsent(location, key ->
                Minecraft.getInstance().getResourceManager().getResource(key).isPresent());
    }

    public static void clearCache() {
        EXISTS.clear();
    }

    private static ResourceLocation variantLocation(String name) {
        return new ResourceLocation(Warriors.MODID, TEXTURE_DIR + name + ".png");
    }

    public static ResourceLocation getWhiteTexture() {
        return WHITE;
    }

    public static String getVariantName(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(CatPlayerHandler.NBT_CAT_VARIANT)
                ? data.getString(CatPlayerHandler.NBT_CAT_VARIANT)
                : "tabby";
    }
}
