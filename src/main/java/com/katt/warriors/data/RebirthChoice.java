package com.katt.warriors.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import java.util.Locale;

public final class RebirthChoice {

    public static final String NBT_MODE       = "warriors.rebirth.mode";
    public static final String NBT_APPEARANCE = "warriors.rebirth.appearance";
    public static final String NBT_CLAN       = "warriors.rebirth.clan";
    public static final String NBT_CHOSEN     = "warriors.rebirth.chosen";
    public static final String NBT_AGE        = "warriors.rebirth.age";

    private RebirthChoice() {}

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

    public enum Age {
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

        public double maxHealth() { return maxHealth; }
        public double speedFactor() { return speedFactor; }
        public double attackFactor() { return attackFactor; }
        public double miningFactor() { return miningFactor; }
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
