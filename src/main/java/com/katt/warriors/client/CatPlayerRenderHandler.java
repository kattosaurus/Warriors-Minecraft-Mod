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

@OnlyIn(Dist.CLIENT)
public class CatPlayerRenderHandler {

    private static final float WALK_SPEED = 0.216f;

    private static final float RUN_SPEED = 0.648f;

    private static final float ACCEL_TAU_TICKS = 4.0f;

    private static final float ACCEL_ALPHA = 1.0f - (float) Math.exp(-1.0f / ACCEL_TAU_TICKS);

    private static final class AnimState {
        final AnimationState walk = new AnimationState();
        final AnimationState run  = new AnimationState();
        float smoothSpeed;
        long lastSpeedTick = Long.MIN_VALUE;
    }

    private final Map<Player, AnimState> animStates = new WeakHashMap<>();

    private static final float PAW_X     =  0.42f;
    private static final float PAW_Y     = -0.42f;
    private static final float PAW_Z     = -0.50f;
    private static final float PAW_YAW   =  8.0f;
    private static final float PAW_PITCH = 10.0f;
    private static final float PAW_SCALE =  1.2f;

    private static ModelPart pawPart;

    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        event.setCanceled(true);

        Player player = event.getEntity();
        Minecraft mc  = Minecraft.getInstance();
        if (mc.level == null) return;

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
        float ageInTicks = mc.level.getGameTime() + partialTick;
        long gameTick = mc.level.getGameTime();

        AnimState anim = animStates.computeIfAbsent(player, p -> new AnimState());

        if (anim.lastSpeedTick != gameTick) {
            anim.lastSpeedTick = gameTick;
            double dx = player.getX() - player.xOld;
            double dz = player.getZ() - player.zOld;
            float actualSpeed = (float) Math.sqrt(dx * dx + dz * dz);
            anim.smoothSpeed += (actualSpeed - anim.smoothSpeed) * ACCEL_ALPHA;
        }

        float smoothSpeed = anim.smoothSpeed;

        boolean isMoving  = smoothSpeed > 0.01f;
        boolean isRunning = smoothSpeed > WALK_SPEED;

        if (isMoving)  anim.walk.startIfStopped((int) gameTick);
        else           anim.walk.stop();

        if (isRunning) anim.run.startIfStopped((int) gameTick);
        else           anim.run.stop();

        float runBlend  = Mth.clamp((smoothSpeed - WALK_SPEED) / (RUN_SPEED - WALK_SPEED), 0f, 1f);
        float walkBlend = Mth.clamp(smoothSpeed / WALK_SPEED, 0f, 1f) * (1f - runBlend);

        model.root().getAllParts().forEach(ModelPart::resetPose);
        model.animateWalk(anim.walk, ageInTicks, walkBlend);
        model.animateRun(anim.run,   ageInTicks, runBlend);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buf = event.getMultiBufferSource();
        int light = event.getPackedLight();

        poseStack.pushPose();

        float ageScale = RebirthChoice.getAge(player).scale();
        if (ageScale != 1.0f) {
            poseStack.scale(ageScale, ageScale, ageScale);
        }

        float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);

        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float netHeadYaw = Mth.wrapDegrees(headYaw - yaw);
        float headPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        model.applyHeadLook(netHeadYaw, headPitch);

        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        VertexConsumer bodyVc = buf.getBuffer(RenderType.entityCutoutNoCull(skinTexture));
        model.renderToBuffer(poseStack, bodyVc, light, OverlayTexture.NO_OVERLAY,
                peltR, peltG, peltB, 1f);

        renderEyeOverlay(player, model, poseStack, buf, light);

        poseStack.popPose();
    }

    private void renderEyeOverlay(Player player, CatModel<?> model,
                                  PoseStack ps, MultiBufferSource buf, int normalLight) {
        ResourceLocation scleraTex     = eyeTex(EyeData.getSclera(player), EyeData.DEFAULT_SCLERA);
        ResourceLocation leftPupilTex  = eyeTex(EyeData.getLeftPupil(player), EyeData.DEFAULT_LEFT_PUPIL);
        ResourceLocation rightPupilTex = eyeTex(EyeData.getRightPupil(player), EyeData.DEFAULT_RIGHT_PUPIL);

        float[] scleraTint = tint(EyeData.parseColor(EyeData.getScleraColor(player)));
        float[] leftTint   = tint(EyeData.parseColor(EyeData.getLeftPupilColor(player)));
        float[] rightTint  = tint(EyeData.parseColor(EyeData.getRightPupilColor(player)));

        int lightLevel = player.level().getMaxLocalRawBrightness(player.blockPosition());
        boolean glowSclera = lightLevel < 6;

        ModelPart head = model.getHead();

        VertexConsumer scleraVc = glowSclera
                ? buf.getBuffer(RenderType.eyes(scleraTex))
                : buf.getBuffer(RenderType.entityCutoutNoCull(scleraTex));
        int scleraLight = glowSclera ? 0xF000F0 : normalLight;
        head.render(ps, scleraVc, scleraLight, OverlayTexture.NO_OVERLAY,
                scleraTint[0], scleraTint[1], scleraTint[2], 1f);

        VertexConsumer leftVc = buf.getBuffer(RenderType.entityCutoutNoCull(leftPupilTex));
        head.render(ps, leftVc, normalLight, OverlayTexture.NO_OVERLAY,
                leftTint[0], leftTint[1], leftTint[2], 1f);

        VertexConsumer rightVc = buf.getBuffer(RenderType.entityCutoutNoCull(rightPupilTex));
        head.render(ps, rightVc, normalLight, OverlayTexture.NO_OVERLAY,
                rightTint[0], rightTint[1], rightTint[2], 1f);
    }

    private static float[] tint(int rgb) {
        if (rgb == -1) return new float[] { 1f, 1f, 1f };
        return new float[] {
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f
        };
    }

    private static final Map<String, ResourceLocation> EYE_TEXTURES = new HashMap<>();

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
                            .texOffs(18, 9)
                            .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F),
                    PartPose.ZERO);
            pawPart = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("paw");
        }
        return pawPart;
    }
}
