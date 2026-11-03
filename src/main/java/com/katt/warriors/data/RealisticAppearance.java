package com.katt.warriors.data;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

public final class RealisticAppearance {

    public static final String[] EYE_COLORS = {
            "#C98A2E",
            "#D4A72C",
            "#A65E2E",
            "#8A7A3F",
            "#6E8B3D",
            "#4F7EA6",
            "#7FA6A0",
            "#5B6B7A"
    };

    public static final String[] SCLERA_COLORS = {
            "#EDEDE3",
            "#F0E6C8",
            "#DCE8D0",
            "#D8E4EC",
            "#F0ECC0"
    };

    private RealisticAppearance() {}

    public record Result(String peltColor, String eyeColor, String scleraColor,
                         String pattern) {}

    public static Result roll(RandomSource random) {
        return new Result(
                blendTwo(PeltData.NATURAL_PELTS, random, 0.10f),
                blendTwo(EYE_COLORS, random, 0.07f),
                blendTwo(SCLERA_COLORS, random, 0.04f),
                PeltData.rollPattern(random));
    }

    public static void rollInto(Player player, RandomSource random) {
        Result look = roll(random);
        PeltData.setPeltColor(player, look.peltColor());
        PeltData.setPattern(player, look.pattern());
        EyeData.setLeftPupilColor(player, look.eyeColor());
        EyeData.setRightPupilColor(player, look.eyeColor());
        EyeData.setScleraColor(player, look.scleraColor());
    }

    private static String blendTwo(String[] palette, RandomSource random, float jitter) {
        int first = EyeData.parseHexColor(palette[random.nextInt(palette.length)]);
        int second = EyeData.parseHexColor(palette[random.nextInt(palette.length)]);

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
