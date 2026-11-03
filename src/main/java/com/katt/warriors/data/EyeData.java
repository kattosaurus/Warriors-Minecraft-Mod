package com.katt.warriors.data;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Holds per-player eye customisation data:
 *  - sclera texture key  (defaults to "scelera")
 *  - left-pupil key      (defaults to "left_pupil")
 *  - right-pupil key     (defaults to "right_pupil")
 *  - optional sclera / left-pupil / right-pupil colours
 *    (e.g. "#C0392B" or "red")
 *
 * Keys are simple names that map to textures under:
 *   assets/warriors/textures/entity/cat_player/<key>.png
 *
 * A colour tints the matching white texture pixels and takes priority over the
 * texture's own colours. If no colour is set, the texture is used as-is.
 * The sclera defaults to its plain white texture; new players get a random
 * dark pupil colour (see {@link #getRandomPupilColor()}).
 *
 * Data is stored in the player's persistent NBT so it survives sessions.
 * Forge does not sync that NBT to clients, so {@code WarriorsNetwork} pushes
 * a snapshot to the owning player and to everyone tracking them.
 */
public class EyeData {

    public static final String NBT_SCLERA      = "warriors.eye_sclera";
    public static final String NBT_LEFT_PUPIL  = "warriors.eye_left_pupil";
    public static final String NBT_RIGHT_PUPIL = "warriors.eye_right_pupil";
    public static final String NBT_SCLERA_COLOR       = "warriors.eye_sclera_color";
    public static final String NBT_LEFT_PUPIL_COLOR  = "warriors.eye_left_pupil_color";
    public static final String NBT_RIGHT_PUPIL_COLOR = "warriors.eye_right_pupil_color";

    /**
     * Version marker for one-time default migrations. Version 2 moved the
     * random default colour from the sclera to the pupils.
     */
    public static final String NBT_DEFAULTS_VERSION = "warriors.eye_defaults_version";
    public static final int DEFAULTS_VERSION = 2;

    public static final String DEFAULT_SCLERA      = "scelera";
    public static final String DEFAULT_LEFT_PUPIL  = "left_pupil";
    public static final String DEFAULT_RIGHT_PUPIL = "right_pupil";

    /** Eye textures are simple lowercase names (no paths, no traversal). */
    private static final Pattern TEXTURE_KEY = Pattern.compile("[a-z0-9_]+");

    // ── Randomised sclera colour ─────────────────────────────────────────────

    private static final Random RANDOM = new Random();

    /**
     * Rolls a new, realistic-looking sclera colour.
     * The hue spans the full spectrum while saturation/value are clamped to
     * natural eye ranges, so the result always looks like a plausible eye.
     */
    public static String getRandomScleraColor() {
        float hue = RANDOM.nextFloat();
        float saturation = 0.20f + RANDOM.nextFloat() * 0.35f; // ~0.20 - 0.55
        float value = 0.30f + RANDOM.nextFloat() * 0.55f;      // ~0.30 - 0.85
        return toHexColor(hsvToRgb(hue, saturation, value));
    }

    /**
     * Rolls a new random pupil colour.
     *
     * <p>Pupil textures are painted pure white so they can be tinted, which
     * means an un-tinted pupil would look like Herobrine's glowing white eyes.
     * The default is therefore a random, very dark shade (slight hue and
     * saturation for variety, low brightness so it still reads as a pupil).</p>
     */
    public static String getRandomPupilColor() {
        float hue = RANDOM.nextFloat();
        float saturation = 0.15f + RANDOM.nextFloat() * 0.55f; // ~0.15 - 0.70
        float value = 0.05f + RANDOM.nextFloat() * 0.20f;      // ~0.05 - 0.25
        return toHexColor(hsvToRgb(hue, saturation, value));
    }

    // ── Sclera texture getters / setters ──────────────────────────────────────

    public static String getSclera(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(NBT_SCLERA) ? data.getString(NBT_SCLERA) : DEFAULT_SCLERA;
    }

    public static String getLeftPupil(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(NBT_LEFT_PUPIL) ? data.getString(NBT_LEFT_PUPIL) : DEFAULT_LEFT_PUPIL;
    }

    public static String getRightPupil(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(NBT_RIGHT_PUPIL) ? data.getString(NBT_RIGHT_PUPIL) : DEFAULT_RIGHT_PUPIL;
    }

    public static void setSclera(Player player, String key) {
        player.getPersistentData().putString(NBT_SCLERA, key);
    }

    public static void setLeftPupil(Player player, String key) {
        player.getPersistentData().putString(NBT_LEFT_PUPIL, key);
    }

    public static void setRightPupil(Player player, String key) {
        player.getPersistentData().putString(NBT_RIGHT_PUPIL, key);
    }

    public static void setPupils(Player player, String key) {
        setLeftPupil(player, key);
        setRightPupil(player, key);
    }

    // ── Sclera colour getters / setters ───────────────────────────────────────

    /** Returns the stored sclera hex colour (e.g. "#C0392B") or null if none is set. */
    public static String getScleraColor(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(NBT_SCLERA_COLOR) ? data.getString(NBT_SCLERA_COLOR) : null;
    }

    public static void setScleraColor(Player player, String hexColor) {
        player.getPersistentData().putString(NBT_SCLERA_COLOR, hexColor);
    }

    /** Removes the custom sclera colour override, reverting to texture defaults. */
    public static void clearScleraColor(Player player) {
        player.getPersistentData().remove(NBT_SCLERA_COLOR);
    }

    // ── Pupil colour getters / setters ────────────────────────────────────────

    /** Returns the stored left-pupil hex colour or null if none is set. */
    public static String getLeftPupilColor(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(NBT_LEFT_PUPIL_COLOR) ? data.getString(NBT_LEFT_PUPIL_COLOR) : null;
    }

    /** Returns the stored right-pupil hex colour or null if none is set. */
    public static String getRightPupilColor(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(NBT_RIGHT_PUPIL_COLOR) ? data.getString(NBT_RIGHT_PUPIL_COLOR) : null;
    }

    public static void setLeftPupilColor(Player player, String hexColor) {
        player.getPersistentData().putString(NBT_LEFT_PUPIL_COLOR, hexColor);
    }

    public static void setRightPupilColor(Player player, String hexColor) {
        player.getPersistentData().putString(NBT_RIGHT_PUPIL_COLOR, hexColor);
    }

    /** Sets both pupils to the same colour. */
    public static void setPupilColors(Player player, String hexColor) {
        setLeftPupilColor(player, hexColor);
        setRightPupilColor(player, hexColor);
    }

    /** Removes both pupil colour overrides. */
    public static void clearPupilColors(Player player) {
        clearLeftPupilColor(player);
        clearRightPupilColor(player);
    }

    public static void clearLeftPupilColor(Player player) {
        player.getPersistentData().remove(NBT_LEFT_PUPIL_COLOR);
    }

    public static void clearRightPupilColor(Player player) {
        player.getPersistentData().remove(NBT_RIGHT_PUPIL_COLOR);
    }

    // ── Texture key helpers ───────────────────────────────────────────────────

    /**
     * Normalises a texture key to lowercase and returns it, or null when the
     * key contains anything other than letters, digits and underscores.
     */
    public static String sanitizeTextureKey(String key) {
        if (key == null) return null;
        String clean = key.trim().toLowerCase(Locale.ROOT);
        return TEXTURE_KEY.matcher(clean).matches() ? clean : null;
    }

    /**
     * Returns true when the key is well-formed <em>and</em> a matching texture
     * exists in the mod jar, so a typo can never put the missing-texture
     * checker (pink/purple squares) on a player's eyes.
     */
    public static boolean isValidTextureKey(String key) {
        String clean = sanitizeTextureKey(key);
        if (clean == null) return false;
        return EyeData.class.getResource(texturePath(clean)) != null;
    }

    private static String texturePath(String key) {
        return "/assets/warriors/textures/entity/cat_player/" + key + ".png";
    }

    // ── Colour helpers ────────────────────────────────────────────────────────

    /** Returns true when the given string is a valid hex or named colour. */
    public static boolean isValidColor(String input) {
        return parseColor(input) != -1;
    }

    /**
     * Parses a colour into an opaque ARGB int, or -1 if invalid.
     *
     * <p>Accepts {@code #RRGGBB}, {@code RRGGBB}, {@code 0xRRGGBB} and the
     * vanilla chat-colour names ({@code red}, {@code dark_red}, {@code gold},
     * {@code aqua}, …). Names use the vanilla palette (e.g. {@code red} is
     * {@code #FF5555}); use hex for an exact colour.</p>
     */
    public static int parseColor(String input) {
        int hex = parseHexColor(input);
        if (hex != -1) return hex;

        if (input == null) return -1;
        String name = input.trim();
        if (name.isEmpty()) return -1;

        ChatFormatting formatting = ChatFormatting.getByName(name);
        if (formatting != null && formatting.isColor() && formatting.getColor() != null) {
            return 0xFF000000 | (formatting.getColor() & 0xFFFFFF);
        }
        return -1;
    }

    /** Parses "#RRGGBB" / "RRGGBB" / "0xRRGGBB" into an opaque ARGB int, or -1. */
    public static int parseHexColor(String input) {
        if (input == null) return -1;
        String hex = input.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.regionMatches(true, 0, "0x", 0, 2)) {
            hex = hex.substring(2);
        }
        if (hex.length() != 6) return -1;
        for (int i = 0; i < hex.length(); i++) {
            if (Character.digit(hex.charAt(i), 16) < 0) return -1;
        }
        long value = Long.parseLong(hex, 16);
        return (int) (0xFF000000L | (value & 0xFFFFFFL));
    }

    /** Formats an ARGB int as a "#RRGGBB" string. */
    public static String toHexColor(int rgb) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /** Converts HSV (h: 0-1, s: 0-1, v: 0-1) to an RGB int. */
    public static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1.0f - Math.abs((h * 6.0f) % 2.0f - 1.0f));
        float m = v - c;
        float r = 0.0f, g = 0.0f, b = 0.0f;
        int sector = (int) Math.floor(h * 6.0f);
        if (sector == 0)      { r = c; g = x; b = 0.0f; }
        else if (sector == 1) { r = x; g = c; b = 0.0f; }
        else if (sector == 2) { r = 0.0f; g = c; b = x; }
        else if (sector == 3) { r = 0.0f; g = x; b = c; }
        else if (sector == 4) { r = x; g = 0.0f; b = c; }
        else                  { r = c; g = 0.0f; b = x; }
        return ((int) ((r + m) * 255.0f) << 16) | ((int) ((g + m) * 255.0f) << 8) | (int) ((b + m) * 255.0f);
    }
}
