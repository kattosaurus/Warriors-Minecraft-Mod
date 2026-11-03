package com.katt.warriors.client;

import com.katt.warriors.Warriors;
import com.katt.warriors.data.EyeData;
import com.katt.warriors.data.PeltData;
import com.katt.warriors.data.RebirthChoice;
import com.katt.warriors.entity.CatSkinHelper;
import com.katt.warriors.network.ClientAppearanceSync;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client-side renderer:
 *
 *  1. Cancels vanilla player render → renders the custom CatModel at the
 *     player's position with correct body + head rotation and Blockbench
 *     walk / run animations (smooth acceleration / deceleration).
 *
 *  2. On top of the cat model renders an eye-overlay layer (sclera + two
 *     pupils).  The sclera uses full-bright light (RenderType.eyes) when the
 *     block light level at the player's feet is < 6, making it glow in the dark.
 *
 *  3. Replaces the empty first-person hand with a cat front-leg paw.
 *
 * Eyes are rendered with an optional hex colour override (see EyeData); when
 * set, the colour tints the sclera texture instead of using the flat texture.
 */
@OnlyIn(Dist.CLIENT)
public class CatPlayerRenderHandler {

    // ── Speed / animation blending ────────────────────────────────────────────

    /**
     * Vanilla walking speed in blocks / tick (≈4.317 blocks per second).
     * Movement at or below this speed plays the walk animation at full weight.
     */
    private static final float WALK_SPEED = 0.216f;

    /**
     * Cat running speed in blocks / tick.
     *
     * <p>A vanilla cat's {@code MOVEMENT_SPEED} attribute is 0.3, three times
     * the player base of 0.1, and the actual horizontal speed scales with that
     * attribute (vanilla walk 0.216 b/t at 0.1). CatPlayerHandler raises the
     * player's sprint speed to the same cat value, so a full run is about
     * 3 × 0.216 ≈ 0.648 blocks / tick.</p>
     */
    private static final float RUN_SPEED = 0.648f;

    /**
     * Time constant (in ticks) for the exponential speed smoothing that gives
     * the animation its acceleration / deceleration feel. Larger = lazier.
     */
    private static final float ACCEL_TAU_TICKS = 4.0f;

    /** Per-tick smoothing factor derived from {@link #ACCEL_TAU_TICKS}. */
    private static final float ACCEL_ALPHA = 1.0f - (float) Math.exp(-1.0f / ACCEL_TAU_TICKS);

    /**
     * Per-player animation state. Keeping this per player (instead of on the
     * handler) means two cats in the same world accelerate and animate
     * independently of each other.
     */
    private static final class AnimState {
        final AnimationState walk = new AnimationState();
        final AnimationState run  = new AnimationState();
        float smoothSpeed;
        long lastSpeedTick = Long.MIN_VALUE;
    }

    /** Weak keys so entries disappear when the Player is unloaded. */
    private final Map<Player, AnimState> animStates = new WeakHashMap<>();

    // ── First-person paw tuning ───────────────────────────────────────────────
    private static final float PAW_X     =  0.42f;
    private static final float PAW_Y     = -0.42f;
    private static final float PAW_Z     = -0.50f;
    private static final float PAW_YAW   =  8.0f;
    private static final float PAW_PITCH = 10.0f;
    private static final float PAW_SCALE =  1.2f;

    private static ModelPart pawPart;

    // ── Third-person render ───────────────────────────────────────────────────

    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        event.setCanceled(true);

        Player player = event.getEntity();
        Minecraft mc  = Minecraft.getInstance();
        if (mc.level == null) return;

        // Login/respawn can deliver the appearance packet before the entity
        // exists; apply it now that we are rendering this player.
        ClientAppearanceSync.flushIfPending(player);

        CatSkinHelper.Coat coat = CatSkinHelper.resolve(player);
        ResourceLocation skinTexture = coat.texture();
        float[] peltTint = coat.tint();
        float peltR = peltTint == null ? 1f : peltTint[0];
        float peltG = peltTint == null ? 1f : peltTint[1];
        float peltB = peltTint == null ? 1f : peltTint[2];

        CatModel<?> model = CatModelCache.getOrBake(mc);
        if (model == null) return;

        float partialTick = event.getPartialTick();
        // ageInTicks: current game time as a float, same value Minecraft passes to setupAnim
        float ageInTicks = mc.level.getGameTime() + partialTick;
        long gameTick = mc.level.getGameTime();

        AnimState anim = animStates.computeIfAbsent(player, p -> new AnimState());

        // ── Smooth speed ──────────────────────────────────────────────────────
        // xOld/zOld hold the position at the start of the current tick, so the
        // difference is this tick's movement in blocks. Smooth once per tick so
        // the acceleration feel does not depend on the frame rate.
        if (anim.lastSpeedTick != gameTick) {
            anim.lastSpeedTick = gameTick;
            double dx = player.getX() - player.xOld;
            double dz = player.getZ() - player.zOld;
            float actualSpeed = (float) Math.sqrt(dx * dx + dz * dz);
            anim.smoothSpeed += (actualSpeed - anim.smoothSpeed) * ACCEL_ALPHA;
        }

        float smoothSpeed = anim.smoothSpeed;

        // ── Update AnimationState start / stop ────────────────────────────────
        boolean isMoving  = smoothSpeed > 0.01f;
        boolean isRunning = smoothSpeed > WALK_SPEED;

        // startIfStopped, *not* start: start() resets AnimationState's
        // accumulator to zero, so calling it every frame froze the animation
        // on its first keyframe.
        if (isMoving)  anim.walk.startIfStopped((int) gameTick);
        else           anim.walk.stop();

        if (isRunning) anim.run.startIfStopped((int) gameTick);
        else           anim.run.stop();

        // ── Compute blend weights ─────────────────────────────────────────────
        // Cross-fade walk -> run between WALK_SPEED and RUN_SPEED.
        float runBlend  = Mth.clamp((smoothSpeed - WALK_SPEED) / (RUN_SPEED - WALK_SPEED), 0f, 1f);
        float walkBlend = Mth.clamp(smoothSpeed / WALK_SPEED, 0f, 1f) * (1f - runBlend);

        // ── Apply animations ──────────────────────────────────────────────────
        // KeyframeAnimations *adds* offsets to every part, so every part (not
        // just the root) has to be reset before the clips are applied.
        model.root().getAllParts().forEach(ModelPart::resetPose);
        model.animateWalk(anim.walk, ageInTicks, walkBlend);
        model.animateRun(anim.run,   ageInTicks, runBlend);

        // ── Render ────────────────────────────────────────────────────────────
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buf = event.getMultiBufferSource();
        int light = event.getPackedLight();

        poseStack.pushPose();

        // Kits and adolescents are smaller (the hitbox is shrunk to match).
        float ageScale = RebirthChoice.getAge(player).scale();
        if (ageScale != 1.0f) {
            poseStack.scale(ageScale, ageScale, ageScale);
        }

        // rotLerp (not lerp) so the body does not spin the long way around
        // when the yaw crosses the ±180° boundary.
        float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);

        // Head look: the body follows the head with the usual 30 %/tick lag, so
        // use the head-relative yaw (same value vanilla passes to setupAnim)
        // and let the head turn on Y and X instead of only the animated bob.
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float netHeadYaw = Mth.wrapDegrees(headYaw - yaw);
        float headPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        model.applyHeadLook(netHeadYaw, headPitch);

        // Replicate LivingEntityRenderer#render: body yaw, then the model-space
        // flip (-1, -1, 1) and the -1.501 vertical offset. The flip is what
        // turns the Y-down Blockbench model right-side-up and the offset puts
        // its feet on the ground instead of 24 pixels above it.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        // Base body
        VertexConsumer bodyVc = buf.getBuffer(RenderType.entityCutoutNoCull(skinTexture));
        model.renderToBuffer(poseStack, bodyVc, light, OverlayTexture.NO_OVERLAY,
                peltR, peltG, peltB, 1f);

        // Eye overlay
        renderEyeOverlay(player, model, poseStack, buf, light);

        poseStack.popPose();
    }

    // ── Eye overlay ───────────────────────────────────────────────────────────

    /**
     * Renders the sclera and pupil overlays on the head part.
     * Sclera is rendered with RenderType.eyes() (always full-bright, no depth test)
     * when the block light at the player's position is below 6 — glowing in the dark.
     */
    private void renderEyeOverlay(Player player, CatModel<?> model,
                                  PoseStack ps, MultiBufferSource buf, int normalLight) {
        // Errors (typos, old invalid keys) fall back to the default texture so
        // the missing-texture checker is never shown on a player's face.
        ResourceLocation scleraTex     = eyeTex(EyeData.getSclera(player), EyeData.DEFAULT_SCLERA);
        ResourceLocation leftPupilTex  = eyeTex(EyeData.getLeftPupil(player), EyeData.DEFAULT_LEFT_PUPIL);
        ResourceLocation rightPupilTex = eyeTex(EyeData.getRightPupil(player), EyeData.DEFAULT_RIGHT_PUPIL);

        // Optional colour overrides tint the white texture pixels.
        float[] scleraTint = tint(EyeData.parseColor(EyeData.getScleraColor(player)));
        float[] leftTint   = tint(EyeData.parseColor(EyeData.getLeftPupilColor(player)));
        float[] rightTint  = tint(EyeData.parseColor(EyeData.getRightPupilColor(player)));

        // getMaxLocalRawBrightness returns the combined sky+block light level (0-15)
        int lightLevel = player.level().getMaxLocalRawBrightness(player.blockPosition());
        boolean glowSclera = lightLevel < 6;

        ModelPart head = model.getHead();

        // Sclera: glow in dark uses RenderType.eyes() which is always unlit
        VertexConsumer scleraVc = glowSclera
                ? buf.getBuffer(RenderType.eyes(scleraTex))
                : buf.getBuffer(RenderType.entityCutoutNoCull(scleraTex));
        int scleraLight = glowSclera ? 0xF000F0 : normalLight;
        head.render(ps, scleraVc, scleraLight, OverlayTexture.NO_OVERLAY,
                scleraTint[0], scleraTint[1], scleraTint[2], 1f);

        // Pupils: always rendered with normal lighting
        VertexConsumer leftVc = buf.getBuffer(RenderType.entityCutoutNoCull(leftPupilTex));
        head.render(ps, leftVc, normalLight, OverlayTexture.NO_OVERLAY,
                leftTint[0], leftTint[1], leftTint[2], 1f);

        VertexConsumer rightVc = buf.getBuffer(RenderType.entityCutoutNoCull(rightPupilTex));
        head.render(ps, rightVc, normalLight, OverlayTexture.NO_OVERLAY,
                rightTint[0], rightTint[1], rightTint[2], 1f);
    }

    /** RGB multipliers for a colour override; white (no tint) when unset/invalid. */
    private static float[] tint(int rgb) {
        if (rgb == -1) return new float[] { 1f, 1f, 1f };
        return new float[] {
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f
        };
    }

    /** Successfully resolved eye textures, keyed by texture key. */
    private static final Map<String, ResourceLocation> EYE_TEXTURES = new HashMap<>();

    /**
     * Resolves an eye texture key, falling back to {@code fallbackKey} when the
     * key is malformed or the texture is missing from the client's resources.
     */
    private static ResourceLocation eyeTex(String key, String fallbackKey) {
        String clean = EyeData.sanitizeTextureKey(key);
        if (clean != null) {
            ResourceLocation cached = EYE_TEXTURES.get(clean);
            if (cached != null) return cached;

            ResourceLocation location = eyeTexLocation(clean);
            if (Minecraft.getInstance().getResourceManager().getResource(location).isPresent()) {
                EYE_TEXTURES.put(clean, location);
                return location;
            }
        }
        return eyeTexLocation(EyeData.sanitizeTextureKey(fallbackKey));
    }

    private static ResourceLocation eyeTexLocation(String key) {
        return new ResourceLocation(Warriors.MODID,
                "textures/entity/cat_player/" + key + ".png");
    }

    // ── First-person paw ──────────────────────────────────────────────────────

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        if (!event.getItemStack().isEmpty()) return;
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.isInvisible()) return;

        ClientAppearanceSync.flushIfPending(player);

        HumanoidArm arm = player.getMainArm();
        CatSkinHelper.Coat coat = CatSkinHelper.resolve(player);
        ResourceLocation texture = coat.texture();
        float[] peltTint = coat.tint();
        float peltR = peltTint == null ? 1f : peltTint[0];
        float peltG = peltTint == null ? 1f : peltTint[1];
        float peltB = peltTint == null ? 1f : peltTint[2];

        renderCatPaw(event.getPoseStack(), event.getMultiBufferSource(),
                event.getPackedLight(), arm,
                event.getSwingProgress(), event.getEquipProgress(), texture,
                peltR, peltG, peltB);
    }

    private void renderCatPaw(PoseStack ps, MultiBufferSource buf, int light,
                              HumanoidArm arm, float swing, float equip,
                              ResourceLocation texture, float tintR, float tintG, float tintB) {
        float side   = (arm == HumanoidArm.RIGHT) ? 1.0f : -1.0f;
        float f1     = Mth.sqrt(swing);
        float swingX = -0.3f * Mth.sin(f1 * Mth.PI);
        float swingY =  0.4f * Mth.sin(f1 * Mth.TWO_PI);
        float swingZ = -0.4f * Mth.sin(swing * Mth.PI);
        float swipe  =  Mth.sin(f1 * Mth.PI);

        ps.pushPose();
        ps.translate(side * (PAW_X + swingX), PAW_Y + swingY + equip * -0.6f, PAW_Z + swingZ);
        ps.mulPose(Axis.YP.rotationDegrees(side * (PAW_YAW + swipe * 35.0f)));
        ps.mulPose(Axis.XP.rotationDegrees(PAW_PITCH + swipe * 15.0f));
        ps.mulPose(Axis.XP.rotationDegrees(-90.0f));
        ps.scale(PAW_SCALE, PAW_SCALE, PAW_SCALE);

        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(texture));
        getPawPart().render(ps, vc, light, OverlayTexture.NO_OVERLAY,
                tintR, tintG, tintB, 1f);
        ps.popPose();
    }

    private static ModelPart getPawPart() {
        if (pawPart == null) {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("paw",
                    CubeListBuilder.create()
                            // Reuse the front-left leg's UVs so the paw is textured
                            // with the same cat fur region painted in Blockbench.
                            .texOffs(18, 9)
                            .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F),
                    PartPose.ZERO);
            // 64x64, same as the cat layer, so the leg UVs resolve to the same
            // texels as they do on the third-person model.
            pawPart = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("paw");
        }
        return pawPart;
    }
}