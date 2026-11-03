package com.katt.warriors.data;

/**
 * The preset look applied when the player picks the "Stylized" appearance.
 *
 * <p>Stylized is a third flavour of the appearance step (alongside Realistic and
 * Customizable). Instead of inheriting colours from two parents or hand-picking
 * swatches, the cat is given a single dark, clean and modern preset coat / eye /
 * sclera combination.</p>
 */
public final class StylizedAppearance {

    /** A dark, clean, modern cat look with fixed preset colours. */
    public record Result(String peltColor, String eyeColor, String scleraColor) {}

    /**
     * The dark, clean, modern stylized cat look. Colours are {@code #RRGGBB}.
     */
    public static final Result PRESET = new Result("#3B3B40", "#D9A441", "#F2EBDD");

    private StylizedAppearance() {}
}
