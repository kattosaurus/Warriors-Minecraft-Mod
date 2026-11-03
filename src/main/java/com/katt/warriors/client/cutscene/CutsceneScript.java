package com.katt.warriors.client.cutscene;

import java.util.List;

/**
 * The intro cutscene script: every timestamp in one place so the pacing can be
 * tweaked without touching the renderer.
 *
 * <p>All times are seconds from the moment the black screen appears.</p>
 */
public final class CutsceneScript {

    private CutsceneScript() {}

    // ── Scene beats ──────────────────────────────────────────────────────────

    /** Stars are fully faded in by this time (the requested 5 s of void). */
    public static final float STARS_FULL = 5.0f;

    /** Screen starts dimming at the end of the monologue. */
    public static final float BLACKOUT_START = 17.9f;
    /** Screen is completely black from here... */
    public static final float BLACKOUT_FULL = 18.4f;
    /** ...until the candle ignites here (roughly 3 s of pure black). */
    public static final float CANDLE_IGNITE = 21.2f;
    /** Black overlay has fully faded out by here, revealing the scene. */
    public static final float REVEAL_END = 22.8f;
    /** Cat brightness ramps in between these two times. */
    public static final float CAT_APPEAR_START = 21.4f;
    public static final float CAT_APPEAR_END = 23.0f;

    /**
     * Failsafe fade. The script does not end on its own any more: the last
     * line hands control to the rebirth choice screens, so these times sit well
     * past the final line and are only a safety net.
     */
    public static final float END_FADE_START = 90.0f;
    public static final float END = 100.0f;

    // ── Speakers ─────────────────────────────────────────────────────────────

    public enum Speaker {
        NARRATOR,
        PLAYER,
        CAT
    }

    /**
     * A single script line.
     *
     * @param speaker  who is speaking
     * @param key      translation key resolved from the language files
     * @param start    seconds from the start of the cutscene
     * @param duration how long the line stays on screen, in seconds
     */
    public record Line(Speaker speaker, String key, float start, float duration) {
        public float end() {
            return start + duration;
        }
    }

    /** How long the typewriter takes to reveal a line, in seconds. */
    public static float typewriterDuration(Line line) {
        return Math.min(0.85F, line.duration() * 0.45F);
    }

    // ── Translation keys ─────────────────────────────────────────────────────

    public static final String TITLE_KEY = "cutscene.warriors.intro.title";
    public static final String SPEAKER_PLAYER_KEY = "cutscene.warriors.intro.speaker.player";
    public static final String SPEAKER_CAT_KEY = "cutscene.warriors.intro.speaker.cat";

    private static final String MONOLOGUE_KEY = "cutscene.warriors.intro.monologue.";
    private static final String DIALOGUE_KEY = "cutscene.warriors.intro.dialogue.";

    // ── Script ───────────────────────────────────────────────────────────────

    /** The player's thoughts while floating in the starry void. */
    public static final List<Line> MONOLOGUE = List.of(
            new Line(Speaker.NARRATOR, MONOLOGUE_KEY + 1, 5.2f, 2.4f),
            new Line(Speaker.NARRATOR, MONOLOGUE_KEY + 2, 8.4f, 2.2f),
            new Line(Speaker.NARRATOR, MONOLOGUE_KEY + 3, 11.4f, 2.2f),
            new Line(Speaker.NARRATOR, MONOLOGUE_KEY + 4, 14.6f, 3.2f)
    );

    /**
     * Dialogue after the candle is lit. Lines may use {@code %s} for the
     * player's name. Edit the text/timings in {@code en_us.json} and here.
     *
     * <p>Monologue lines play on the clock (they never wait for a click), so
     * only the dialogue below pauses on each line.</p>
     */
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

    /** Player lines shown on the mode choice screen, one per mode. */
    public static final String CHOICE_LINE_REALISTIC =
            "warriors.rebirth.line.realistic";
    public static final String CHOICE_LINE_VANILLA =
            "warriors.rebirth.line.vanilla";

    // ── Rebirth dialogue lines (played between the choice menus) ──────────────

    /** Player reaction after picking the "realistic" way of life. */
    public static final String LINE_MODE_REALISTIC = "warriors.rebirth.line.realistic";
    public static final String LINE_MODE_VANILLA = "warriors.rebirth.line.vanilla";

    /** Spoken when the appearance was rolled (realistic / stylized). */
    public static final String LINE_FINAL = "warriors.rebirth.line.final";
    /** Alternative when the two eyes were given different colours. */
    public static final String LINE_FINAL_HETERO = "warriors.rebirth.line.final_hetero";
    public static final String LINE_FINAL_CAT = "warriors.rebirth.line.final_cat";

    /** Spoken when the appearance was customised. */
    public static final String LINE_CUSTOM = "warriors.rebirth.line.custom";
    public static final String LINE_CUSTOM_CAT = "warriors.rebirth.line.custom_cat";

    /** Spoken before the randomized (natural) appearance. */
    public static final String LINE_RANDOMIZED = "warriors.rebirth.line.randomized";
    public static final String LINE_RANDOMIZED_CAT = "warriors.rebirth.line.randomized_cat";

    /** Spoken when picking the age to be born at (the cat's reply is per age). */
    public static final String LINE_AGE = "warriors.rebirth.line.age";
    public static final String LINE_AGE_CAT = "warriors.rebirth.line.age_cat";

    /** Spoken before picking a clan. */
    public static final String LINE_CLAN = "warriors.rebirth.line.clan";
    public static final String LINE_CLAN_CAT = "warriors.rebirth.line.clan_cat";
}