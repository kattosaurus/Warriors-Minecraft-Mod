package com.katt.warriors.data;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * Rolls a natural-looking cat appearance as if inherited from two parents.
 *
 * <p>Each trait picks two colours from a realistic palette, blends them with a
 * random bias, and adds a small amount of jitter. That avoids the flat "one of
 * eight preset colours" look while keeping every result plausible.</p>
 */
public final class RealisticAppearance {

    /** Typical cat iris colours. */
    public static final String[] EYE_COLORS = {
            "#C98A2E", // amber
            "#D4A72C", // gold
            "#A65E2E", // copper
            "#8A7A3F", // hazel
            "#6E8B3D", // green
            "#4F7EA6", // blue
            "#7FA6A0", // grey-green
            "#5B6B7A"  // slate
    };

    /** Pale whites / creams that read as a natural sclera. */
    public static final String[] SCLERA_COLORS = {
            "#EDEDE3", // off-white
            "#F0E6C8", // cream
            "#DCE8D0", // pale green
            "#D8E4EC", // pale blue
            "#F0ECC0"  // pale yellow
    };

    private RealisticAppearance() {}

    /** Final inherited appearance. Colours are {@code #RRGGBB} strings. */
    public record Result(String peltColor, String eyeColor, String scleraColor,
                         String pattern) {}

    /** Rolls a complete inherited appearance. */
    public static Result roll(RandomSource random) {
        return new Result(
                blendTwo(PeltData.NATURAL_PELTS, random, 0.10f),
                blendTwo(EYE_COLORS, random, 0.07f),
                blendTwo(SCLERA_COLORS, random, 0.04f),
                PeltData.rollPattern(random));
    }

    /**
     * Rolls a brand-new look and writes it to the player (coat, pattern, eyes,
     * sclera). Used for a new life; the caller is responsible for syncing.
     */
    public static void rollInto(Player player, RandomSource random) {
        Result look = roll(random);
        PeltData.setPeltColor(player, look.peltColor());
        PeltData.setPattern(player, look.pattern());
        EyeData.setLeftPupilColor(player, look.eyeColor());
        EyeData.setRightPupilColor(player, look.eyeColor());
        EyeData.setScleraColor(player, look.scleraColor());
    }

    /**
     * Blends two random palette entries.
     *
     * @param jitter maximum per-channel variation, 0..1
     */
    private static String blendTwo(String[] palette, RandomSource random, float jitter) {
        int first = EyeData.parseHexColor(palette[random.nextInt(palette.length)]);
        int second = EyeData.parseHexColor(palette[random.nextInt(palette.length)]);

        // Bias towards one parent so the result is not always a perfect average.
        float mix = 0.3f + random.nextFloat() * 0.4f;

        int red   = blendChannel(first, second, 16, mix, random, jitter);
        int green = blendChannel(first, second, 8,  mix, random, jitter);
        int blue  = blendChannel(first, second, 0,  mix, random, jitter);

        return EyeData.toHexColor((red << 16) | (green << 8) | blue);
    }

    private static int blendChannel(int first, int second, int shift, float mix,
                                    RandomSource random, float jitter) {
        int a = (first >> shift) & 0xFF;
        int b = (second >> shift) & 0xFF;
        int value = Math.round(a * (1.0f - mix) + b * mix);

        float offset = (random.nextFloat() * 2.0f - 1.0f) * jitter * 255.0f;
        return Math.max(0, Math.min(255, value + Math.round(offset)));
    }
}