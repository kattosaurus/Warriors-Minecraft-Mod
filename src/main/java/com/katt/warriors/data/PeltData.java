package com.katt.warriors.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.Random;
import java.util.UUID;

/**
 * Per-player pelt colour.
 *
 * <p>The custom cat model is drawn with the plain white coat and tinted with
 * this colour, so a single texture can produce any number of natural-looking
 * coats. When no colour is stored the renderer uses the painted variant
 * textures (orange, tabby, ...), each with a small per-player tint variation so
 * two cats of the same variant never look identical.</p>
 */
public final class PeltData {

    public static final String NBT_PELT_COLOR = "warriors.pelt_color";
    public static final String NBT_PATTERN    = "warriors.pelt_pattern";

    /**
     * Coat patterns. Each name is a fully painted texture,
     * {@code textures/entity/cat_player/<name>.png}; "solid" (no pattern) is
     * the plain white coat tinted by the pelt colour. A pattern whose texture
     * is missing simply falls back to solid.
     */
    public static final String[] PATTERNS = {"tabby", "calico", "tuxedo", "siamese", "black"};

    /** Chance that a randomized cat has a pattern rather than a plain tinted coat. */
    public static final float PATTERN_CHANCE = 0.95F;

    /**
     * How strongly the pelt colour tints a patterned coat (0 = not at all,
     * 1 = like a solid coat). Kept soft so the painted pattern stays readable.
     */
    public static final float PATTERN_TINT_STRENGTH = 0.40F;

    /**
     * Natural coats a realistic player can inherit from their parents. Values
     * are deliberately muted so tinting the shaded white texture keeps the
     * fur readable.
     */
    public static final String[] NATURAL_PELTS = {
            "#241F1B", // black
            "#3B2A1E", // dark brown
            "#5A4636", // brown
            "#6E6E6E", // grey
            "#9A9A9A", // silver
            "#B87333", // ginger
            "#CD9A5A", // sand
            "#D8C39A", // cream
            "#EFE9DC"  // white
    };

    /** Wider palette offered by the custom appearance screen. */
    public static final String[] CUSTOM_PELTS = {
            "#141414", "#2A2A2A", "#4A4A4A", "#6E6E6E", "#9A9A9A", "#C8C8C8", "#F2F2F2",
            "#2A1B12", "#4A2F20", "#6B3E26", "#8C5A3C", "#B87333", "#D98E4A", "#E8B97E",
            "#B5522A", "#D9772E", "#F0B27A",
            "#3E4C63", "#6F8199", "#A9B8C8",
            "#4A5A3A", "#7A8C5A", "#B4C48A"
    };

    private PeltData() {}

    // ── Storage ──────────────────────────────────────────────────────────────

    /** Returns the stored hex colour, or {@code null} when no tint is set. */
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

    // ── Patterns ─────────────────────────────────────────────────────────────

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

    /** The stored pattern name, or {@code null} for a solid coat. */
    public static String getPattern(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(NBT_PATTERN)) {
            return null;
        }
        String value = data.getString(NBT_PATTERN);
        return isPattern(value) ? value.toLowerCase(java.util.Locale.ROOT) : null;
    }

    /** Stores a pattern; {@code null}, empty or unknown names clear it (solid). */
    public static void setPattern(Player player, String name) {
        if (isPattern(name)) {
            player.getPersistentData().putString(NBT_PATTERN,
                    name.toLowerCase(java.util.Locale.ROOT));
        } else {
            player.getPersistentData().remove(NBT_PATTERN);
        }
    }

    /** Random pattern (more likely than not) or {@code null} for a solid coat. */
    public static String rollPattern(RandomSource random) {
        if (random.nextFloat() < PATTERN_CHANCE) {
            return PATTERNS[random.nextInt(PATTERNS.length)];
        }
        return null;
    }

    /** Soft tint for a patterned coat: the pelt colour pulled towards white. */
    public static float[] softTint(int rgb) {
        float strength = PATTERN_TINT_STRENGTH;
        return new float[] {
                1.0F - strength * (1.0F - ((rgb >> 16) & 0xFF) / 255.0F),
                1.0F - strength * (1.0F - ((rgb >> 8) & 0xFF) / 255.0F),
                1.0F - strength * (1.0F - (rgb & 0xFF) / 255.0F)
        };
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    /**
     * RGB multipliers for this player's coat: their stored pelt colour, or, for
     * players on a painted variant texture, a subtle variation derived from
     * their UUID.
     */
    public static float[] getTint(Player player) {
        String stored = getPeltColor(player);
        if (stored != null) {
            return tint(stored);
        }
        return variationTint(player.getUUID());
    }

    // ── Per-player variation for painted variant textures ────────────────────

    /** Darkest brightness a variation can reach (1.0 = texture unchanged). */
    private static final float VARIATION_MIN_BRIGHTNESS = 0.82F;
    /** Largest warm/cool colour shift, as a fraction of the channel. */
    private static final float VARIATION_MAX_WARMTH = 0.07F;

    /**
     * A stable, subtle tint for a player, so cats sharing one variant texture
     * still differ a little in how light, dark, warm or cool their coat looks.
     * Derived from the UUID, so every client computes the same value with no
     * extra syncing. A multiplier can only darken a texture, so values stay
     * at or below 1.
     */
    public static float[] variationTint(UUID id) {
        Random random = new Random(id.getMostSignificantBits() * 31L
                ^ id.getLeastSignificantBits());

        float brightness = VARIATION_MIN_BRIGHTNESS
                + random.nextFloat() * (1.0F - VARIATION_MIN_BRIGHTNESS);
        // -1 = cool, +1 = warm
        float warmth = (random.nextFloat() * 2.0F - 1.0F) * VARIATION_MAX_WARMTH;

        float red = brightness * (1.0F - Math.max(0.0F, -warmth));
        float green = brightness * (1.0F - Math.abs(warmth) * 0.4F);
        float blue = brightness * (1.0F - Math.max(0.0F, warmth));
        return new float[] {red, green, blue};
    }

    /** RGB multipliers for a hex colour; {@code null} for no/invalid colour. */
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