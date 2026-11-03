package com.katt.warriors.data;

public final class StylizedAppearance {

    public record Result(String peltColor, String eyeColor, String scleraColor) {}

    public static final Result PRESET = new Result("#3B3B40", "#D9A441", "#F2EBDD");

    private StylizedAppearance() {}
}
