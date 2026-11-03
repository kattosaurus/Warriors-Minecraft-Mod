package com.katt.warriors.client.cutscene;

import java.util.List;

public final class CutsceneScript {

    private CutsceneScript() {}

    public static final float STARS_FULL = 5.0f;

    public static final float BLACKOUT_START = 17.9f;
    public static final float BLACKOUT_FULL = 18.4f;
    public static final float CANDLE_IGNITE = 21.2f;
    public static final float REVEAL_END = 22.8f;
    public static final float CAT_APPEAR_START = 21.4f;
    public static final float CAT_APPEAR_END = 23.0f;

    public static final float END_FADE_START = 90.0f;
    public static final float END = 100.0f;

    public enum Speaker {
        NARRATOR,
        PLAYER,
        CAT
    }

    public record Line(Speaker speaker, String key, float start, float duration) {
        public float end() {
            return start + duration;
        }
    }

    public static float typewriterDuration(Line line) {
        return Math.min(0.85F, line.duration() * 0.45F);
    }

    public static final String TITLE_KEY = "cutscene.warriors.intro.title";
    public static final String SPEAKER_PLAYER_KEY = "cutscene.warriors.intro.speaker.player";
    public static final String SPEAKER_CAT_KEY = "cutscene.warriors.intro.speaker.cat";

    private static final String MONOLOGUE_KEY = "cutscene.warriors.intro.monologue.";
    private static final String DIALOGUE_KEY = "cutscene.warriors.intro.dialogue.";

    public static final List<Line> MONOLOGUE = List.of(
            new Line(Speaker.NARRATOR, MONOLOGUE_KEY + 1, 5.2f, 2.4f),
            new Line(Speaker.NARRATOR, MONOLOGUE_KEY + 2, 8.4f, 2.2f),
            new Line(Speaker.NARRATOR, MONOLOGUE_KEY + 3, 11.4f, 2.2f),
            new Line(Speaker.NARRATOR, MONOLOGUE_KEY + 4, 14.6f, 3.2f)
    );

    public static final List<Line> DIALOGUE = List.of(
            new Line(Speaker.CAT, DIALOGUE_KEY + 1, 23.4f, 2.8f),
            new Line(Speaker.PLAYER, DIALOGUE_KEY + 2, 27.2f, 2.6f),
            new Line(Speaker.CAT, DIALOGUE_KEY + 3, 30.6f, 2.2f),
            new Line(Speaker.PLAYER, DIALOGUE_KEY + 4, 33.6f, 3.0f),
            new Line(Speaker.CAT, DIALOGUE_KEY + 5, 37.4f, 4.0f),
            new Line(Speaker.PLAYER, DIALOGUE_KEY + 6, 42.2f, 3.2f),
            new Line(Speaker.CAT, DIALOGUE_KEY + 7, 46.2f, 5.0f),
            new Line(Speaker.PLAYER, DIALOGUE_KEY + 8, 52.0f, 3.0f),
            new Line(Speaker.CAT, DIALOGUE_KEY + 9, 55.8f, 4.6f),
            new Line(Speaker.PLAYER, DIALOGUE_KEY + 10, 61.2f, 3.4f),
            new Line(Speaker.CAT, DIALOGUE_KEY + 11, 65.4f, 1.4f),
            new Line(Speaker.PLAYER, DIALOGUE_KEY + 12, 67.6f, 2.2f),
            new Line(Speaker.CAT, DIALOGUE_KEY + 13, 70.6f, 4.8f)
    );

    public static final String CHOICE_LINE_REALISTIC =
            "warriors.rebirth.line.realistic";
    public static final String CHOICE_LINE_VANILLA =
            "warriors.rebirth.line.vanilla";

    public static final String LINE_MODE_REALISTIC = "warriors.rebirth.line.realistic";
    public static final String LINE_MODE_VANILLA = "warriors.rebirth.line.vanilla";

    public static final String LINE_FINAL = "warriors.rebirth.line.final";
    public static final String LINE_FINAL_HETERO = "warriors.rebirth.line.final_hetero";
    public static final String LINE_FINAL_CAT = "warriors.rebirth.line.final_cat";

    public static final String LINE_CUSTOM = "warriors.rebirth.line.custom";
    public static final String LINE_CUSTOM_CAT = "warriors.rebirth.line.custom_cat";

    public static final String LINE_RANDOMIZED = "warriors.rebirth.line.randomized";
    public static final String LINE_RANDOMIZED_CAT = "warriors.rebirth.line.randomized_cat";

    public static final String LINE_AGE = "warriors.rebirth.line.age";
    public static final String LINE_AGE_CAT = "warriors.rebirth.line.age_cat";

    public static final String LINE_CLAN = "warriors.rebirth.line.clan";
    public static final String LINE_CLAN_CAT = "warriors.rebirth.line.clan_cat";
}
