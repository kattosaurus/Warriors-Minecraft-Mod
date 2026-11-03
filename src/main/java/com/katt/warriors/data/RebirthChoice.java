package com.katt.warriors.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import java.util.Locale;

/**
 * Persistent record of the choices made on the rebirth screens.
 *
 * <p>The values live in the player's Forge persistent data, so they survive
 * sessions just like the coat and eye customisation. The server writes them
 * from {@code RebirthChoicePacket}; the mode is mirrored to the client through
 * {@link com.katt.warriors.network.SyncAppearancePacket} so client-side systems
 * (mining speed, worldgen hints) can react without a second round trip.</p>
 */
public final class RebirthChoice {

    public static final String NBT_MODE       = "warriors.rebirth.mode";
    public static final String NBT_APPEARANCE = "warriors.rebirth.appearance";
    public static final String NBT_CLAN       = "warriors.rebirth.clan";
    public static final String NBT_CHOSEN     = "warriors.rebirth.chosen";
    public static final String NBT_AGE        = "warriors.rebirth.age";

    private RebirthChoice() {}

    // ── Enums ────────────────────────────────────────────────────────────────

    public enum Mode {
        VANILLA,
        REALISTIC;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Mode byId(String value) {
            if (value != null) {
                for (Mode mode : values()) {
                    if (mode.id().equalsIgnoreCase(value)
                            || mode.name().equalsIgnoreCase(value)) {
                        return mode;
                    }
                }
            }
            return VANILLA;
        }
    }

    public enum Appearance {
        REALISTIC,
        CUSTOM,
        STYLIZED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Appearance byId(String value) {
            if (value != null) {
                for (Appearance appearance : values()) {
                    if (appearance.id().equalsIgnoreCase(value)
                            || appearance.name().equalsIgnoreCase(value)) {
                        return appearance;
                    }
                }
            }
            return REALISTIC;
        }
    }

    /**
     * The age a cat is born at. Younger cats are smaller, weaker and slower;
     * an adult has the baseline stats of the mod.
     */
    public enum Age {
        //   max HP, speed, attack, mining, model scale
        KIT(6.0D, 0.70D, 0.50D, 0.55D, 0.60F),
        ADOLESCENT(8.0D, 0.88D, 0.85D, 0.80D, 0.85F),
        ADULT(10.0D, 1.00D, 1.00D, 1.00D, 1.00F);

        private final double maxHealth;
        private final double speedFactor;
        private final double attackFactor;
        private final double miningFactor;
        private final float scale;

        Age(double maxHealth, double speedFactor, double attackFactor,
            double miningFactor, float scale) {
            this.maxHealth = maxHealth;
            this.speedFactor = speedFactor;
            this.attackFactor = attackFactor;
            this.miningFactor = miningFactor;
            this.scale = scale;
        }

        /** Maximum health in half-hearts. */
        public double maxHealth() { return maxHealth; }
        /** Multiplier on walking and running speed. */
        public double speedFactor() { return speedFactor; }
        /** Multiplier on melee damage. */
        public double attackFactor() { return attackFactor; }
        /** Multiplier on block-breaking speed (realistic mode). */
        public double miningFactor() { return miningFactor; }
        /** Size of the model and hitbox relative to an adult. */
        public float scale() { return scale; }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String translationKey() {
            return "warriors.age." + id();
        }

        public static Age byId(String value) {
            if (value != null) {
                for (Age age : values()) {
                    if (age.id().equalsIgnoreCase(value)
                            || age.name().equalsIgnoreCase(value)) {
                        return age;
                    }
                }
            }
            return ADULT;
        }
    }

    public enum Clan {
        THUNDERCLAN,
        RIVERCLAN,
        SHADOWCLAN,
        WINDCLAN,
        SKYCLAN,
        KITTYPET,
        STRAY,
        ROGUE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String translationKey() {
            return "warriors.clan." + id();
        }

        public String descriptionKey() {
            return "warriors.clan." + id() + ".desc";
        }

        /**
         * Whether this clan builds a camp. Strays and rogues live without
         * one, and the kittypets have their village instead.
         */
        public boolean hasCamp() {
            return this != STRAY && this != ROGUE && this != KITTYPET;
        }

        public static Clan byId(String value) {
            if (value != null) {
                for (Clan clan : values()) {
                    if (clan.id().equalsIgnoreCase(value)
                            || clan.name().equalsIgnoreCase(value)) {
                        return clan;
                    }
                }
            }
            return ROGUE;
        }
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public static boolean hasChosen(Player player) {
        return player.getPersistentData().getBoolean(NBT_CHOSEN);
    }

    public static Mode getMode(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(NBT_MODE) ? Mode.byId(data.getString(NBT_MODE)) : Mode.VANILLA;
    }

    public static Appearance getAppearance(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(NBT_APPEARANCE)
                ? Appearance.byId(data.getString(NBT_APPEARANCE))
                : Appearance.REALISTIC;
    }

    public static Clan getClan(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(NBT_CLAN) ? Clan.byId(data.getString(NBT_CLAN)) : Clan.ROGUE;
    }

    public static Age getAge(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(NBT_AGE) ? Age.byId(data.getString(NBT_AGE)) : Age.ADULT;
    }

    public static void setAge(Player player, Age age) {
        player.getPersistentData().putString(NBT_AGE, age.id());
    }

    /**
     * Players who chose the randomized (natural) look get a fresh one every
     * life: custom and stylized choices are deliberate and stay.
     */
    public static boolean rerollsOnDeath(Player player) {
        return hasChosen(player) && getAppearance(player) == Appearance.REALISTIC;
    }

    public static void setMode(Player player, Mode mode) {
        player.getPersistentData().putString(NBT_MODE, mode.id());
    }

    public static void setAppearance(Player player, Appearance appearance) {
        player.getPersistentData().putString(NBT_APPEARANCE, appearance.id());
    }

    public static void setClan(Player player, Clan clan) {
        player.getPersistentData().putString(NBT_CLAN, clan.id());
    }

    public static void markChosen(Player player) {
        player.getPersistentData().putBoolean(NBT_CHOSEN, true);
    }

    public static void clear(Player player) {
        CompoundTag data = player.getPersistentData();
        data.remove(NBT_MODE);
        data.remove(NBT_APPEARANCE);
        data.remove(NBT_CLAN);
        data.remove(NBT_AGE);
        ScentData.clear(player);
        CatName.clear(player);
        data.remove(NBT_CHOSEN);
    }
}