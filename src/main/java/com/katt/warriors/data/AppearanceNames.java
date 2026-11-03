package com.katt.warriors.data;

import java.util.List;

/**
 * Turns the stored {@code #RRGGBB} appearance colours into the plain words the
 * rebirth dialogue uses ("a <em>ginger</em> cat with <em>amber</em> eyes").
 *
 * <p>Colours are matched to the closest named swatch, so both the fixed palettes
 * and the blended colours rolled for a natural birth get a readable name.</p>
 */
public final class AppearanceNames {

    private record Named(String name, int rgb) {}

    private static final String FALLBACK_PELT = "natural";
    private static final String FALLBACK_EYE = "natural";

    /** Coat names, matched against both the natural and custom palettes. */
    private static final List<Named> PELTS = List.of(
            new Named("black", 0x141414),
            new Named("charcoal", 0x2A2A2A),
            new Named("dark grey", 0x4A4A4A),
            new Named("grey", 0x6E6E6E),
            new Named("silver", 0x9A9A9A),
            new Named("pale grey", 0xC8C8C8),
            new Named("white", 0xF2F2F2),
            new Named("dark brown", 0x2A1B12),
            new Named("brown", 0x4A2F20),
            new Named("chestnut", 0x6B3E26),
            new Named("tawny", 0x8C5A3C),
            new Named("ginger", 0xB87333),
            new Named("orange", 0xD9772E),
            new Named("sandy", 0xE8B97E),
            new Named("rust", 0xB5522A),
            new Named("apricot", 0xF0B27A),
            new Named("cream", 0xD8C39A),
            new Named("blue-grey", 0x3E4C63),
            new Named("slate", 0x6F8199),
            new Named("pale blue", 0xA9B8C8),
            new Named("olive", 0x4A5A3A),
            new Named("moss", 0x7A8C5A),
            new Named("pale green", 0xB4C48A)
    );

    /** Iris names, covering the realistic palette and common custom colours. */
    private static final List<Named> EYES = List.of(
            new Named("amber", 0xC98A2E),
            new Named("gold", 0xD4A72C),
            new Named("copper", 0xA65E2E),
            new Named("hazel", 0x8A7A3F),
            new Named("green", 0x6E8B3D),
            new Named("blue", 0x4F7EA6),
            new Named("grey-green", 0x7FA6A0),
            new Named("slate", 0x5B6B7A),
            new Named("brown", 0x6B4423),
            new Named("yellow", 0xE0C040),
            new Named("red", 0xC0392B),
            new Named("cyan", 0x4FA6A6),
            new Named("purple", 0x7A4FA6),
            new Named("pink", 0xD97FA6),
            new Named("black", 0x1A1A1A),
            new Named("white", 0xEDEDE3),
            new Named("grey", 0x8A8A8A)
    );

    private AppearanceNames() {}

    /** A readable coat name for a stored pelt colour. */
    public static String pelt(String hexColour) {
        return nearest(PELTS, hexColour, FALLBACK_PELT);
    }

    /** A readable iris name for a stored eye colour. */
    public static String eye(String hexColour) {
        return nearest(EYES, hexColour, FALLBACK_EYE);
    }

    private static String nearest(List<Named> names, String hexColour, String fallback) {
        int rgb = EyeData.parseColor(hexColour);
        if (rgb == -1) {
            return fallback;
        }

        String best = fallback;
        double bestDistance = Double.MAX_VALUE;
        for (Named named : names) {
            double distance = distance(rgb, named.rgb());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = named.name();
            }
        }
        return best;
    }

    private static double distance(int first, int second) {
        int dr = ((first >> 16) & 0xFF) - ((second >> 16) & 0xFF);
        int dg = ((first >> 8) & 0xFF) - ((second >> 8) & 0xFF);
        int db = (first & 0xFF) - (second & 0xFF);
        return dr * (double) dr + dg * (double) dg + db * (double) db;
    }
}
