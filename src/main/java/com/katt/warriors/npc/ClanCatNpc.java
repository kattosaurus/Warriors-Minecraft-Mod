package com.katt.warriors.npc;

import com.katt.warriors.data.PeltData;
import com.katt.warriors.data.RealisticAppearance;
import com.katt.warriors.data.RebirthChoice;
import com.katt.warriors.data.RebirthChoice.Clan;
import com.katt.warriors.npc.ClanCatNames.Role;
import com.katt.warriors.worldgen.ClanlandsDimension;
import com.katt.warriors.worldgen.ClanlandsTerrain;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * A named cat NPC that lives in a clan camp den or patrols a clan's territory.
 *
 * <p>Each NPC carries:
 * <ul>
 *   <li>A warrior-style name (via {@link ClanCatNames}).</li>
 *   <li>A pelt pattern ({@link PeltData#PATTERNS}) or solid colour, biased
 *       heavily towards patterned pelts (80%).</li>
 *   <li>A pelt colour hex from {@link PeltData#NATURAL_PELTS}.</li>
 *   <li>A {@link Clan} and a {@link Role}.</li>
 * </ul>
 *
 * <p>When a player who does <em>not</em> belong to this NPC's clan steps into
 * the clan's territory, there is a random chance that a nearby patrol NPC will
 * start chasing them out (the {@link ChaseIntruderGoal}).</p>
 */
public class ClanCatNpc extends PathfinderMob {

    // ── Synced data keys ──────────────────────────────────────────────────

    /** The NPC's warrior name, shown above their head. */
    private static final EntityDataAccessor<String> DATA_NAME =
            SynchedEntityData.defineId(ClanCatNpc.class, EntityDataSerializers.STRING);

    /** Clan id string (e.g. "thunderclan"). */
    private static final EntityDataAccessor<String> DATA_CLAN =
            SynchedEntityData.defineId(ClanCatNpc.class, EntityDataSerializers.STRING);

    /** Hex pelt colour (e.g. "#B87333") or empty for default. */
    private static final EntityDataAccessor<String> DATA_PELT_COLOR =
            SynchedEntityData.defineId(ClanCatNpc.class, EntityDataSerializers.STRING);

    /** Pattern name (e.g. "tabby") or empty for solid. */
    private static final EntityDataAccessor<String> DATA_PATTERN =
            SynchedEntityData.defineId(ClanCatNpc.class, EntityDataSerializers.STRING);

    /** Iris colour hex (e.g. "#C98A2E"). */
    private static final EntityDataAccessor<String> DATA_EYE_COLOR =
            SynchedEntityData.defineId(ClanCatNpc.class, EntityDataSerializers.STRING);

    /** Role ordinal (index into {@link Role#values()}). */
    private static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(ClanCatNpc.class, EntityDataSerializers.INT);

    // ── NBT keys ──────────────────────────────────────────────────────────

    public static final String NBT_NPC_NAME   = "warriors.npc.name";
    public static final String NBT_NPC_CLAN   = "warriors.npc.clan";
    public static final String NBT_NPC_COLOR  = "warriors.npc.color";
    public static final String NBT_NPC_PATTERN= "warriors.npc.pattern";
    public static final String NBT_NPC_ROLE   = "warriors.npc.role";
    public static final String NBT_NPC_EYES   = "warriors.npc.eyes";

    // ── Constants ─────────────────────────────────────────────────────────

    /**
     * Probability that a given NPC will start chasing an intruding player on
     * any given check (once per 5 seconds). Multiple NPCs can join the chase.
     */
    private static final float CHASE_CHANCE = 0.55f;

    /** Radius within which the chase goal activates (blocks). */
    private static final double CHASE_DETECT_RADIUS = 28.0;

    /** Chase continues until the intruder is this far outside the territory. */
    private static final double CHASE_GIVE_UP_RADIUS = 48.0;

    // ── Fields ────────────────────────────────────────────────────────────

    public ClanCatNpc(EntityType<? extends ClanCatNpc> type, Level level) {
        super(type, level);
        this.setMaxUpStep(0.6f);
    }

    // ── Attributes ────────────────────────────────────────────────────────

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    // ── Initialisation ────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_NAME,      "");
        this.entityData.define(DATA_CLAN,      Clan.ROGUE.id());
        this.entityData.define(DATA_PELT_COLOR,"");
        this.entityData.define(DATA_PATTERN,   "");
        this.entityData.define(DATA_EYE_COLOR, "");
        this.entityData.define(DATA_ROLE,      Role.WARRIOR.ordinal());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new ChaseIntruderGoal(this, CHASE_CHANCE, CHASE_DETECT_RADIUS, CHASE_GIVE_UP_RADIUS));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, false));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6, 0.0001f));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class,
                true, this::isIntruder));
    }

    /**
     * Returns true if this player is an intruder in this NPC's territory.
     * Players of the same clan are never intruders.
     *
     * <p>Public so that {@link ChaseIntruderGoal} can call it.</p>
     */
    public boolean isIntruder(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        if (!player.level().dimension().equals(ClanlandsDimension.LEVEL)) return false;
        Clan playerClan = com.katt.warriors.data.RebirthChoice.getClan(player);
        if (playerClan == getNpcClan()) return false;
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        Clan territoryClan = ClanlandsTerrain.clanAt(px, pz);
        return territoryClan == getNpcClan();
    }

    // ── Spawning initialisation ────────────────────────────────────────────

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        // If spawned with specific NBT data (our spawner does this), the data is
        // already set; otherwise pick random defaults.
        if (this.entityData.get(DATA_NAME).isEmpty()) {
            randomiseAppearance(new Random(this.blockPosition().asLong() ^ 0x5A7C3B1D));
        }
        refreshCustomName();
        return data;
    }

    /**
     * Assigns a random warrior-style name, pelt colour and pattern.
     * Called by {@link ClanCatSpawner} and by {@link #finalizeSpawn}.
     */
    public void randomiseAppearance(Random rng) {
        // Name
        long nameSeed = rng.nextLong();
        String name = ClanCatNames.generate(getNpcClan(), getNpcRole(), nameSeed);
        setNpcName(name);

        // Pelt colour: pick from the natural palette
        String[] naturalPelts = PeltData.NATURAL_PELTS;
        String color = naturalPelts[rng.nextInt(naturalPelts.length)];
        setNpcPeltColor(color);

        // Iris colour: one of the natural cat eye colours
        String[] eyes = RealisticAppearance.EYE_COLORS;
        setNpcEyeColor(eyes[rng.nextInt(eyes.length)]);

        // Pattern: 80% chance of a pattern (warrior cats in the books are
        // usually patterned), otherwise a solid coat.
        String pattern;
        if (rng.nextFloat() < 0.80f) {
            String[] patterns = PeltData.PATTERNS;
            pattern = patterns[rng.nextInt(patterns.length)];
        } else {
            pattern = "";   // solid coat
        }
        setNpcPattern(pattern);

        refreshCustomName();
    }

    /** Updates the entity's display name to match the stored warrior name. */
    private void refreshCustomName() {
        String name = getNpcName();
        if (!name.isEmpty()) {
            this.setCustomName(Component.literal(name));
            this.setCustomNameVisible(true);
        }
    }

    // ── Size ──────────────────────────────────────────────────────────────

    /**
     * How big a cat of this role is compared with an adult, using the same
     * proportions as a player born at that age: kits are small and
     * apprentices (adolescents) a bit smaller than a warrior.
     */
    public static float modelScale(Role role) {
        return switch (role) {
            case KIT -> RebirthChoice.Age.KIT.scale();
            case APPRENTICE -> RebirthChoice.Age.ADOLESCENT.scale();
            default -> RebirthChoice.Age.ADULT.scale();
        };
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(modelScale(getNpcRole()));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ROLE.equals(key)) {
            this.refreshDimensions();
        }
    }

    // ── Getters / setters ─────────────────────────────────────────────────

    public String getNpcName() {
        return this.entityData.get(DATA_NAME);
    }

    public void setNpcName(String name) {
        this.entityData.set(DATA_NAME, name);
    }

    public Clan getNpcClan() {
        return Clan.byId(this.entityData.get(DATA_CLAN));
    }

    public void setNpcClan(Clan clan) {
        this.entityData.set(DATA_CLAN, clan.id());
    }

    public Role getNpcRole() {
        Role[] roles = Role.values();
        int index = this.entityData.get(DATA_ROLE);
        return roles[Math.max(0, Math.min(roles.length - 1, index))];
    }

    public void setNpcRole(Role role) {
        this.entityData.set(DATA_ROLE, role.ordinal());
    }

    public String getNpcPeltColor() {
        return this.entityData.get(DATA_PELT_COLOR);
    }

    public void setNpcPeltColor(String hex) {
        this.entityData.set(DATA_PELT_COLOR, hex);
    }

    public String getNpcEyeColor() {
        return this.entityData.get(DATA_EYE_COLOR);
    }

    public void setNpcEyeColor(String hex) {
        this.entityData.set(DATA_EYE_COLOR, hex);
    }

    public String getNpcPattern() {
        return this.entityData.get(DATA_PATTERN);
    }

    public void setNpcPattern(String pattern) {
        this.entityData.set(DATA_PATTERN, pattern);
    }

    // ── Persistence ───────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(NBT_NPC_NAME,    getNpcName());
        tag.putString(NBT_NPC_CLAN,    getNpcClan().id());
        tag.putString(NBT_NPC_COLOR,   getNpcPeltColor());
        tag.putString(NBT_NPC_PATTERN, getNpcPattern());
        tag.putString(NBT_NPC_EYES,    getNpcEyeColor());
        tag.putString(NBT_NPC_ROLE,    getNpcRole().name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_NPC_CLAN)) {
            setNpcClan(Clan.byId(tag.getString(NBT_NPC_CLAN)));
        }
        if (tag.contains(NBT_NPC_ROLE)) {
            String roleStr = tag.getString(NBT_NPC_ROLE).toUpperCase(java.util.Locale.ROOT);
            try {
                setNpcRole(Role.valueOf(roleStr));
            } catch (IllegalArgumentException ignored) {
                setNpcRole(Role.WARRIOR);
            }
        }
        if (tag.contains(NBT_NPC_NAME)) {
            setNpcName(tag.getString(NBT_NPC_NAME));
        }
        if (tag.contains(NBT_NPC_COLOR)) {
            setNpcPeltColor(tag.getString(NBT_NPC_COLOR));
        }
        if (tag.contains(NBT_NPC_PATTERN)) {
            setNpcPattern(tag.getString(NBT_NPC_PATTERN));
        }
        if (tag.contains(NBT_NPC_EYES)) {
            setNpcEyeColor(tag.getString(NBT_NPC_EYES));
        }
        refreshCustomName();
    }

    // ── Misc overrides ────────────────────────────────────────────────────

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // NPCs are permanent residents; do not despawn them.
        return false;
    }

    @Override
    public ItemStack getMainHandItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }
}