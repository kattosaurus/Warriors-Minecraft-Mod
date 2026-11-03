package com.katt.warriors.npc;

import com.katt.warriors.data.RebirthChoice.Age;
import com.katt.warriors.data.RebirthChoice.Clan;

import java.util.Locale;
import java.util.Random;

public final class ClanCatNames {

    private static final String[] PREFIXES = {
            "Acorn", "Adder", "Alder", "Amber", "Ant", "Apple", "Ash", "Aspen",
            "Badger", "Bark", "Bat", "Bay", "Bear", "Bee", "Berry", "Birch",
            "Bird", "Bitter", "Black", "Blaze", "Blossom", "Blue", "Bounce",
            "Boulder", "Bracken", "Bramble", "Brave", "Breeze", "Briar",
            "Bright", "Broken", "Brook", "Brown", "Brumble", "Buck",
            "Cedar", "Charcoal", "Cherry", "Chestnut", "Cinder", "Clay",
            "Clear", "Clover", "Cloud", "Cold", "Copper", "Crane", "Creek",
            "Crow", "Crystal",
            "Daisy", "Dark", "Dawn", "Dew", "Dove", "Dusk", "Dust",
            "Eagle", "Ebony", "Echo", "Ember", "Falcon", "Fallen", "Fawn",
            "Feather", "Fern", "Fire", "Flash", "Fleet", "Flint", "Flutter",
            "Fog", "Fox", "Frost", "Furze",
            "Ghost", "Ginger", "Gold", "Gorse", "Grass", "Gray", "Gravel",
            "Green", "Grey",
            "Hail", "Half", "Hawk", "Hazel", "Heath", "Heather", "Hollow",
            "Honey", "Ice", "Ivy",
            "Jay", "Juniper",
            "Lark", "Leaf", "Lightning", "Lily", "Lion", "Little", "Lizard",
            "Lone", "Long",
            "Maple", "Meadow", "Mist", "Misty", "Mole", "Moon", "Moss",
            "Moth", "Mouse", "Mud",
            "Nettle", "Night",
            "Oak", "Owl",
            "Pale", "Patch", "Pebble", "Pine", "Pod", "Poppy",
            "Quick",
            "Rain", "Rapid", "Raven", "Red", "Reed", "River", "Robin",
            "Rock", "Root", "Rose", "Rowan", "Rush", "Russet",
            "Sand", "Shade", "Shadow", "Sharp", "Short", "Silver", "Sky",
            "Slate", "Small", "Smoke", "Snow", "Song", "Sorrel", "Sparrow",
            "Speckle", "Spider", "Splash", "Spotted", "Spring", "Squirrel",
            "Star", "Stone", "Storm", "Stripe",
            "Sun", "Swift",
            "Tall", "Talon", "Tangle", "Tansy", "Thistle", "Thorn",
            "Thunder", "Tiger", "Tiny",
            "Viper",
            "Wading", "Weasel", "Web", "Wild", "Willow", "Wind",
            "Wolf", "Wood",
            "Yellow"
    };

    private static final String[] WARRIOR_SUFFIXES = {
            "claw", "coat", "ear", "eye", "face", "fall", "fang",
            "feather", "flower", "foot", "fur", "heart", "leap", "light",
            "mark", "mist", "nose", "pad", "pelt", "pool", "pounce", "run",
            "shade", "shine", "sky", "song", "spring", "step", "storm",
            "stream", "streak", "stripe", "tail", "tuft", "whisker", "wing"
    };

    public static final String KIT_SUFFIX = "kit";
    public static final String APPRENTICE_SUFFIX = "paw";
    public static final String LEADER_SUFFIX = "star";

    public static final int MAX_PREFIX_LENGTH = 12;
    public static final int MAX_SUFFIX_LENGTH = 10;
    public static final int MIN_PART_LENGTH = 2;

    private static final String[] KITTYPET_NAMES = {
            "Bella", "Cody", "Max", "Luna", "Charlie", "Mia", "Leo",
            "Lucy", "Millie", "Chester", "Daisy", "Oscar", "Rosie",
            "Alfie", "Molly", "Jasper", "Poppy", "George", "Lola",
            "Toby", "Lily", "Milo", "Ruby", "Felix", "Nala",
            "Sammy", "Cookie", "Biscuit", "Socks", "Whiskers",
            "Mittens", "Shadow", "Princess", "Duchess", "Tiger"
    };

    private static final String[] STRAY_ROGUE_NAMES = {
            "Bone", "Claw", "Clover", "Crest", "Dart", "Dusk", "Flame",
            "Flint", "Fray", "Ghost", "Gust", "Hack", "Hook", "Inch",
            "Knot", "Lash", "Mud", "Needle", "Notch", "Pick", "Pitch",
            "Rip", "Rust", "Scorch", "Shard", "Slash", "Smudge", "Snarl",
            "Snatch", "Soot", "Sparks", "Spike", "Spit", "Stab", "Stone",
            "Stub", "Tangle", "Tear", "Thorn", "Tusk", "Venom", "Vex"
    };

    private ClanCatNames() {}

    public enum Role {
        LEADER, DEPUTY, WARRIOR, MEDICINE_CAT, APPRENTICE, KIT, ELDER;

        public String fixedSuffix() {
            return switch (this) {
                case LEADER -> LEADER_SUFFIX;
                case APPRENTICE -> APPRENTICE_SUFFIX;
                case KIT -> KIT_SUFFIX;
                default -> null;
            };
        }
    }

    public static String fixedSuffix(Age age) {
        return switch (age) {
            case KIT -> KIT_SUFFIX;
            case ADOLESCENT -> APPRENTICE_SUFFIX;
            case ADULT -> null;
        };
    }

    public static String generate(Clan clan, Role role, long seed) {
        Random rng = new Random(seed);

        if (clan == Clan.KITTYPET) {
            return KITTYPET_NAMES[rng.nextInt(KITTYPET_NAMES.length)];
        }
        if (clan == Clan.STRAY || clan == Clan.ROGUE) {
            return STRAY_ROGUE_NAMES[rng.nextInt(STRAY_ROGUE_NAMES.length)];
        }

        String fixed = role.fixedSuffix();
        if (fixed != null) {
            return compose(randomPrefix(rng, fixed), fixed);
        }
        String prefix = randomPrefix(rng, "");
        return compose(prefix, randomWarriorSuffix(rng, prefix));
    }

    public static String randomPrefix(Random rng, String suffix) {
        String prefix = PREFIXES[rng.nextInt(PREFIXES.length)];
        for (int i = 0; i < 24 && !joinsWell(prefix, suffix); i++) {
            prefix = PREFIXES[rng.nextInt(PREFIXES.length)];
        }
        return prefix;
    }

    public static String randomWarriorSuffix(Random rng, String prefix) {
        String suffix = WARRIOR_SUFFIXES[rng.nextInt(WARRIOR_SUFFIXES.length)];
        for (int i = 0; i < 24 && !joinsWell(prefix, suffix); i++) {
            suffix = WARRIOR_SUFFIXES[rng.nextInt(WARRIOR_SUFFIXES.length)];
        }
        return suffix;
    }

    public static boolean joinsWell(String prefix, String suffix) {
        if (prefix == null || suffix == null || prefix.isEmpty() || suffix.isEmpty()) {
            return true;
        }
        String p = prefix.toLowerCase(Locale.ROOT);
        String s = suffix.toLowerCase(Locale.ROOT);
        return !p.endsWith(s)
                && !s.startsWith(p)
                && p.charAt(p.length() - 1) != s.charAt(0);
    }

    public static String compose(String prefix, String suffix) {
        return prefix + suffix.toLowerCase(Locale.ROOT);
    }

    public static String cleanPrefix(String raw) {
        String letters = lettersOnly(raw, MAX_PREFIX_LENGTH);
        if (letters.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(letters.charAt(0)) + letters.substring(1);
    }

    public static String cleanSuffix(String raw) {
        return lettersOnly(raw, MAX_SUFFIX_LENGTH);
    }

    public static boolean isReservedSuffix(String suffix) {
        return KIT_SUFFIX.equalsIgnoreCase(suffix)
                || APPRENTICE_SUFFIX.equalsIgnoreCase(suffix)
                || LEADER_SUFFIX.equalsIgnoreCase(suffix);
    }

    private static String lettersOnly(String raw, int max) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length() && out.length() < max; i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }
}
