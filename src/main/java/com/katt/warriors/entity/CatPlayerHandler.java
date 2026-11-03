package com.katt.warriors.entity;

import com.katt.warriors.data.EyeData;
import com.katt.warriors.data.RealisticAppearance;
import com.katt.warriors.data.RebirthChoice;
import com.katt.warriors.network.WarriorsNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;
import java.util.UUID;

/**
 * Server-side (and shared) handler:
 *  - On join / respawn: give the player cat stats (10 HP, normal walk speed).
 *  - On break-speed: reduce mining to 25% of normal.
 *  - On EntityEvent.Size: shrink the player's hitbox to cat size (0.6 × 0.7).
 *
 * Speed notes:
 *   The base movement-speed attribute for a player is 0.1 (vanilla default),
 *   which is used for walking so the cat walks at the normal player pace.
 *   While sprinting, a transient modifier raises the attribute to the vanilla
 *   cat's MOVEMENT_SPEED (0.3) after the vanilla ×1.3 sprint boost is applied.
 *   That makes running exactly as fast as a Minecraft cat, while walking stays
 *   unchanged.
 */
@SuppressWarnings("deprecation")
public class CatPlayerHandler {

    // ── Cat stat constants ────────────────────────────────────────────────────
    public static final double CAT_MAX_HEALTH = 10.0;   // 5 hearts
    /**
     * Walk speed = vanilla player base value. No permanent modifier is added,
     * so walking stays at 0.1 blocks / tick. The sprint-only run modifier is
     * described below.
     */
    public static final double PLAYER_BASE_SPEED = 0.1;

    /**
     * Vanilla cat movement-speed attribute (Cat#createAttributes). Used as the
     * target speed while the player is sprinting.
     */
    public static final double CAT_RUN_SPEED = 0.3;

    /** Vanilla sprinting boost is MULTIPLY_TOTAL 0.3, i.e. ×1.3. */
    private static final double SPRINT_SPEED_MULTIPLIER = 1.3;

    /**
     * Addition applied while sprinting so that
     * {@code (PLAYER_BASE_SPEED + CAT_SPRINT_MODIFIER) * 1.3 == CAT_RUN_SPEED}.
     */
    private static final double CAT_SPRINT_MODIFIER =
            CAT_RUN_SPEED / SPRINT_SPEED_MULTIPLIER - PLAYER_BASE_SPEED;

    public static final float CAT_WIDTH      = 0.6f;
    public static final float CAT_HEIGHT     = 0.7f;
    public static final float CAT_EYE_HEIGHT = CAT_HEIGHT * 0.5f; // 0.35

    // Stable UUIDs so we can remove-and-reapply modifiers cleanly
    public static final UUID UUID_HEALTH = UUID.fromString("a1b2c3d4-0001-0000-0000-000000000001");
    /** Transient modifier that raises sprint speed to {@link #CAT_RUN_SPEED}. */
    public static final UUID UUID_SPEED  = UUID.fromString("a1b2c3d4-0002-0000-0000-000000000002");
    /** Permanent walk/run speed multiplier from the age the cat was born at. */
    public static final UUID UUID_AGE_SPEED  = UUID.fromString("a1b2c3d4-0003-0000-0000-000000000003");
    /** Permanent melee damage multiplier from the age the cat was born at. */
    public static final UUID UUID_AGE_ATTACK = UUID.fromString("a1b2c3d4-0004-0000-0000-000000000004");

    /** NBT key to persist the chosen cat variant name across sessions. */
    public static final String NBT_CAT_VARIANT = "warriors.cat_variant";

    /**
     * Cat variants handed out at random to players without a custom pelt colour.
     * Each name is a texture file: {@code textures/entity/cat_player/<name>.png}.
     * A name whose texture is not painted yet simply shows the orange coat, so
     * the not-yet-drawn ones can stay listed.
     */
    public static final String[] VARIANT_NAMES = {
            "orange", "tabby", "white", "black", "calico", "tuxedo", "siamese"
    };

    private static final float MINING_SPEED_MULTIPLIER = 0.25f;
    private static final Random RANDOM = new Random();

    private static final EntityDimensions CAT_DIMENSIONS =
            EntityDimensions.scalable(CAT_WIDTH, CAT_HEIGHT);

    // ── Event handlers ────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyCatStats(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyCatStats(player);
        }
    }

    /**
     * Make sure the rebirth choices (mode, age, look) survive death even if the
     * persistent data was not carried over to the new player entity.
     */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            event.getEntity().getPersistentData()
                    .merge(event.getOriginal().getPersistentData());
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // A randomized cat is born looking different every life. (Leaving
            // the End is also a "respawn", but not a new life.)
            if (!event.isEndConquered() && RebirthChoice.rerollsOnDeath(player)) {
                RealisticAppearance.rollInto(player, player.getRandom());
                player.displayClientMessage(
                        Component.translatable("warriors.rebirth.reborn"), true);
            }
            applyCatStats(player);
        }
    }

    /** Respawn/dimension change creates a fresh client entity: re-send cosmetics. */
    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncNextTick(player);
        }
    }

    /** Another player just became visible to us: send them our cosmetics. */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer tracked
                && event.getEntity() instanceof ServerPlayer viewer) {
            WarriorsNetwork.syncAppearanceTo(tracked, viewer);
        }
    }

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        // Vanilla mode keeps the ordinary mining pace; only a realistic life
        // slows a cat's paws down.
        if (RebirthChoice.getMode(event.getEntity()) == RebirthChoice.Mode.REALISTIC) {
            event.setNewSpeed(event.getOriginalSpeed() * MINING_SPEED_MULTIPLIER
                    * (float) RebirthChoice.getAge(event.getEntity()).miningFactor());
        }
    }

    /**
     * While sprinting, boost the player's movement speed to the vanilla cat's
     * running speed. The modifier is transient, so it is only active during a
     * sprint and is never persisted to the player NBT.
     *
     * <p>Runs on both sides: the server is authoritative, while the client
     * copy lets the local player predict the faster sprint immediately.</p>
     */
    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        var moveSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed == null) return;

        boolean hasRunModifier = moveSpeed.getModifier(UUID_SPEED) != null;
        if (player.isSprinting()) {
            if (!hasRunModifier) {
                moveSpeed.addTransientModifier(new AttributeModifier(
                        UUID_SPEED, "warriors.cat_run_speed", CAT_SPRINT_MODIFIER,
                        AttributeModifier.Operation.ADDITION));
            }
        } else if (hasRunModifier) {
            moveSpeed.removeModifier(UUID_SPEED);
        }
    }

    @SubscribeEvent
    public void onEntitySize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) return;
        float scale = RebirthChoice.getAge(player).scale();
        event.setNewSize(CAT_DIMENSIONS.scale(scale));
        event.setNewEyeHeight(CAT_EYE_HEIGHT * scale);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * (Re)applies the cat's stats: health, speed and attack from the age the
     * player was born at, plus cosmetics. Safe to call whenever those change.
     */
    public static void applyCatStats(ServerPlayer player) {
        RebirthChoice.Age age = RebirthChoice.getAge(player);

        // 1. Assign a random cat variant on first login
        CompoundTag data = player.getPersistentData();
        if (!data.contains(NBT_CAT_VARIANT)) {
            data.putString(NBT_CAT_VARIANT, VARIANT_NAMES[RANDOM.nextInt(VARIANT_NAMES.length)]);
        }

        // 2. Heal texture keys written by older builds (e.g. a colour name that
        //    was mistakenly stored as a texture key, which showed the missing-
        //    texture checkerboard).
        if (!EyeData.isValidTextureKey(data.getString(EyeData.NBT_SCLERA))) {
            data.putString(EyeData.NBT_SCLERA, EyeData.DEFAULT_SCLERA);
        }
        if (!EyeData.isValidTextureKey(data.getString(EyeData.NBT_LEFT_PUPIL))) {
            data.putString(EyeData.NBT_LEFT_PUPIL, EyeData.DEFAULT_LEFT_PUPIL);
        }
        if (!EyeData.isValidTextureKey(data.getString(EyeData.NBT_RIGHT_PUPIL))) {
            data.putString(EyeData.NBT_RIGHT_PUPIL, EyeData.DEFAULT_RIGHT_PUPIL);
        }

        // 3. One-time migration: older builds auto-randomised the sclera.
        //    The sclera is meant to stay white by default; only pupils are random.
        if (data.getInt(EyeData.NBT_DEFAULTS_VERSION) < EyeData.DEFAULTS_VERSION) {
            data.remove(EyeData.NBT_SCLERA_COLOR);
            data.putInt(EyeData.NBT_DEFAULTS_VERSION, EyeData.DEFAULTS_VERSION);
        }

        // 4. Assign random (dark) pupil colours on first login. Without a tint
        //    the pure-white pupil texture looks like Herobrine's glowing eyes.
        //    Both pupils share one colour by default; /eye can still split them.
        if (!data.contains(EyeData.NBT_LEFT_PUPIL_COLOR)
                || !data.contains(EyeData.NBT_RIGHT_PUPIL_COLOR)) {
            String pupilColor = EyeData.getRandomPupilColor();
            if (!data.contains(EyeData.NBT_LEFT_PUPIL_COLOR)) {
                data.putString(EyeData.NBT_LEFT_PUPIL_COLOR, pupilColor);
            }
            if (!data.contains(EyeData.NBT_RIGHT_PUPIL_COLOR)) {
                data.putString(EyeData.NBT_RIGHT_PUPIL_COLOR, pupilColor);
            }
        }

        // 5. Max health: 10 HP for an adult, less for younger cats
        var maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(UUID_HEALTH);
            double delta = age.maxHealth() - maxHealth.getBaseValue();
            maxHealth.addPermanentModifier(new AttributeModifier(
                    UUID_HEALTH, "warriors.cat_health", delta,
                    AttributeModifier.Operation.ADDITION));
            if (player.getHealth() > (float) age.maxHealth()) {
                player.setHealth((float) age.maxHealth());
            }
        }

        // 6. Walk speed → vanilla player base (0.1), running speed → cat (0.3).
        //    Remove any old speed modifier first; the sprint-only modifier is
        //    (re)applied by onLivingTick while the player is sprinting.
        var moveSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            moveSpeed.removeModifier(UUID_SPEED); // remove any previous override
            // Do NOT add a sprint modifier here — the base 0.1 is what we want.

            // Younger cats are slower, both walking and running.
            moveSpeed.removeModifier(UUID_AGE_SPEED);
            if (age.speedFactor() != 1.0D) {
                moveSpeed.addPermanentModifier(new AttributeModifier(
                        UUID_AGE_SPEED, "warriors.age_speed", age.speedFactor() - 1.0D,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }

        // 6b. Younger cats hit softer.
        var attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(UUID_AGE_ATTACK);
            if (age.attackFactor() != 1.0D) {
                attack.addPermanentModifier(new AttributeModifier(
                        UUID_AGE_ATTACK, "warriors.age_attack", age.attackFactor() - 1.0D,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }

        // 7. Force attribute sync and bounding-box refresh
        player.resetSentInfo();
        player.refreshDimensions();

        // 8. Push coat + eye cosmetics to the client (and to anyone tracking us)
        syncNextTick(player);
    }

    /**
     * Sends the cosmetic snapshot at the end of the current server tick, after
     * the login / respawn / dimension packets, so the client entity already
     * exists when the packet arrives.
     */
    private static void syncNextTick(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) {
            WarriorsNetwork.syncAppearance(player);
        } else {
            server.execute(() -> {
                if (player.connection != null) {
                    WarriorsNetwork.syncAppearance(player);
                }
            });
        }
    }
}