package com.katt.warriors.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.Random;
import java.util.UUID;

public final class PeltData {

    public static final String NBT_PELT_COLOR = "warriors.pelt_color";
    public static final String NBT_PATTERN    = "warriors.pelt_pattern";

    public static final String[] PATTERNS = {"tabby", "calico", "tuxedo", "siamese", "black"};

    public static final float PATTERN_CHANCE = 0.95F;

    public static final float PATTERN_TINT_STRENGTH = 0.40F;

    public static final String[] NATURAL_PELTS = {
            "#241F1B",
            "#3B2A1E",
            "#5A4636",
            "#6E6E6E",
            "#9A9A9A",
            "#B87333",
            "#CD9A5A",
            "#D8C39A",
            "#EFE9DC"
    };

    public static final String[] CUSTOM_PELTS = {
            "#141414", "#2A2A2A", "#4A4A4A", "#6E6E6E", "#9A9A9A", "#C8C8C8", "#F2F2F2",
            "#2A1B12", "#4A2F20", "#6B3E26", "#8C5A3C", "#B87333", "#D98E4A", "#E8B97E",
            "#B5522A", "#D9772E", "#F0B27A",
            "#3E4C63", "#6F8199", "#A9B8C8",
            "#4A5A3A", "#7A8C5A", "#B4C48A"
    };

    private PeltData() {}

    public static String getPeltColor(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(NBT_PELT_COLOR) ? data.getString(NBT_PELT_COLOR) : null;
    }

    public static void setPeltColor(Player player, String hexColor) {
        if (!EyeData.isValidColor(hexColor)) {
            clearPeltColor(player);
            return;
        }
        int rgb = EyeData.parseColor(hexColor);
        player.getPersistentData().putString(NBT_PELT_COLOR, EyeData.toHexColor(rgb));
    }

    public static void clearPeltColor(Player player) {
        player.getPersistentData().remove(NBT_PELT_COLOR);
    }

    public static boolean isPattern(String name) {
        if (name != null) {
            for (String pattern : PATTERNS) {
                if (pattern.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String getPattern(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(NBT_PATTERN)) {
            return null;
        }
        String value = data.getString(NBT_PATTERN);
        return isPattern(value) ? value.toLowerCase(java.util.Locale.ROOT) : null;
    }

    public static void setPattern(Player player, String name) {
        if (isPattern(name)) {
            player.getPersistentData().putString(NBT_PATTERN,
                    name.toLowerCase(java.util.Locale.ROOT));
        } else {
            player.getPersistentData().remove(NBT_PATTERN);
        }
    }

    public static String rollPattern(RandomSource random) {
        if (random.nextFloat() < PATTERN_CHANCE) {
            return PATTERNS[random.nextInt(PATTERNS.length)];
        }
        return null;
    }

    public static float[] softTint(int rgb) {
        float strength = PATTERN_TINT_STRENGTH;
        return new float[] {
                1.0F - strength * (1.0F - ((rgb >> 16) & 0xFF) / 255.0F),
                1.0F - strength * (1.0F - ((rgb >> 8) & 0xFF) / 255.0F),
                1.0F - strength * (1.0F - (rgb & 0xFF) / 255.0F)
        };
    }

    public static float[] getTint(Player player) {
        String stored = getPeltColor(player);
        if (stored != null) {
            return tint(stored);
        }
        return variationTint(player.getUUID());
    }

    private static final float VARIATION_MIN_BRIGHTNESS = 0.82F;
    private static final float VARIATION_MAX_WARMTH = 0.07F;

    public static float[] variationTint(UUID id) {
        Random random = new Random(id.getMostSignificantBits() * 31L
                ^ id.getLeastSignificantBits());

        float brightness = VARIATION_MIN_BRIGHTNESS
                + random.nextFloat() * (1.0F - VARIATION_MIN_BRIGHTNESS);
        float warmth = (random.nextFloat() * 2.0F - 1.0F) * VARIATION_MAX_WARMTH;

        float red = brightness * (1.0F - Math.max(0.0F, -warmth));
        float green = brightness * (1.0F - Math.abs(warmth) * 0.4F);
        float blue = brightness * (1.0F - Math.max(0.0F, warmth));
        return new float[] {red, green, blue};
    }

    public static float[] tint(String hexColor) {
        int rgb = EyeData.parseColor(hexColor);
        if (rgb == -1) return null;
        return new float[] {
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f
        };
    }
}
