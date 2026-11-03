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

@SuppressWarnings("deprecation")
public class CatPlayerHandler {

    public static final double CAT_MAX_HEALTH = 10.0;
    public static final double PLAYER_BASE_SPEED = 0.1;

    public static final double CAT_RUN_SPEED = 0.3;

    private static final double SPRINT_SPEED_MULTIPLIER = 1.3;

    private static final double CAT_SPRINT_MODIFIER =
            CAT_RUN_SPEED / SPRINT_SPEED_MULTIPLIER - PLAYER_BASE_SPEED;

    public static final float CAT_WIDTH      = 0.6f;
    public static final float CAT_HEIGHT     = 0.7f;
    public static final float CAT_EYE_HEIGHT = CAT_HEIGHT * 0.5f;

    public static final UUID UUID_HEALTH = UUID.fromString("a1b2c3d4-0001-0000-0000-000000000001");
    public static final UUID UUID_SPEED  = UUID.fromString("a1b2c3d4-0002-0000-0000-000000000002");
    public static final UUID UUID_AGE_SPEED  = UUID.fromString("a1b2c3d4-0003-0000-0000-000000000003");
    public static final UUID UUID_AGE_ATTACK = UUID.fromString("a1b2c3d4-0004-0000-0000-000000000004");

    public static final String NBT_CAT_VARIANT = "warriors.cat_variant";

    public static final String[] VARIANT_NAMES = {
            "orange", "tabby", "white", "black", "calico", "tuxedo", "siamese"
    };

    private static final float MINING_SPEED_MULTIPLIER = 0.25f;
    private static final Random RANDOM = new Random();

    private static final EntityDimensions CAT_DIMENSIONS =
            EntityDimensions.scalable(CAT_WIDTH, CAT_HEIGHT);

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
            if (!event.isEndConquered() && RebirthChoice.rerollsOnDeath(player)) {
                RealisticAppearance.rollInto(player, player.getRandom());
                player.displayClientMessage(
                        Component.translatable("warriors.rebirth.reborn"), true);
            }
            applyCatStats(player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncNextTick(player);
        }
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer tracked
                && event.getEntity() instanceof ServerPlayer viewer) {
            WarriorsNetwork.syncAppearanceTo(tracked, viewer);
        }
    }

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (RebirthChoice.getMode(event.getEntity()) == RebirthChoice.Mode.REALISTIC) {
            event.setNewSpeed(event.getOriginalSpeed() * MINING_SPEED_MULTIPLIER
                    * (float) RebirthChoice.getAge(event.getEntity()).miningFactor());
        }
    }

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

    public static void applyCatStats(ServerPlayer player) {
        RebirthChoice.Age age = RebirthChoice.getAge(player);

        CompoundTag data = player.getPersistentData();
        if (!data.contains(NBT_CAT_VARIANT)) {
            data.putString(NBT_CAT_VARIANT, VARIANT_NAMES[RANDOM.nextInt(VARIANT_NAMES.length)]);
        }

        if (!EyeData.isValidTextureKey(data.getString(EyeData.NBT_SCLERA))) {
            data.putString(EyeData.NBT_SCLERA, EyeData.DEFAULT_SCLERA);
        }
        if (!EyeData.isValidTextureKey(data.getString(EyeData.NBT_LEFT_PUPIL))) {
            data.putString(EyeData.NBT_LEFT_PUPIL, EyeData.DEFAULT_LEFT_PUPIL);
        }
        if (!EyeData.isValidTextureKey(data.getString(EyeData.NBT_RIGHT_PUPIL))) {
            data.putString(EyeData.NBT_RIGHT_PUPIL, EyeData.DEFAULT_RIGHT_PUPIL);
        }

        if (data.getInt(EyeData.NBT_DEFAULTS_VERSION) < EyeData.DEFAULTS_VERSION) {
            data.remove(EyeData.NBT_SCLERA_COLOR);
            data.putInt(EyeData.NBT_DEFAULTS_VERSION, EyeData.DEFAULTS_VERSION);
        }

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

        var moveSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            moveSpeed.removeModifier(UUID_SPEED);

            moveSpeed.removeModifier(UUID_AGE_SPEED);
            if (age.speedFactor() != 1.0D) {
                moveSpeed.addPermanentModifier(new AttributeModifier(
                        UUID_AGE_SPEED, "warriors.age_speed", age.speedFactor() - 1.0D,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }

        var attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(UUID_AGE_ATTACK);
            if (age.attackFactor() != 1.0D) {
                attack.addPermanentModifier(new AttributeModifier(
                        UUID_AGE_ATTACK, "warriors.age_attack", age.attackFactor() - 1.0D,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }

        player.resetSentInfo();
        player.refreshDimensions();

        syncNextTick(player);
    }

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
