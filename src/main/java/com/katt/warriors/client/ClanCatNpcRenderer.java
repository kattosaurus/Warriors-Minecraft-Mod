package com.katt.warriors.client;

import com.katt.warriors.data.EyeData;
import com.katt.warriors.data.PeltData;
import com.katt.warriors.entity.CatSkinHelper;
import com.katt.warriors.npc.ClanCatNpc;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;

import java.util.Map;
import java.util.WeakHashMap;

public class ClanCatNpcRenderer extends MobRenderer<ClanCatNpc, ClanCatNpcRenderer.NpcCatModel> {

    private static final float SHADOW = 0.4f;

    private static final ResourceLocation FALLBACK =
            new ResourceLocation("warriors", "textures/entity/cat_player/tabby.png");
    private static final ResourceLocation SCLERA =
            new ResourceLocation("warriors", "textures/entity/cat_player/scelera.png");
    private static final ResourceLocation LEFT_PUPIL =
            new ResourceLocation("warriors", "textures/entity/cat_player/left_pupil.png");
    private static final ResourceLocation RIGHT_PUPIL =
            new ResourceLocation("warriors", "textures/entity/cat_player/right_pupil.png");

    public ClanCatNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcCatModel(context.bakeLayer(CatModel.LAYER_LOCATION)), SHADOW);
        this.addLayer(new EyeLayer(this));
    }

    private static ResourceLocation patternTexture(ClanCatNpc npc) {
        String pattern = npc.getNpcPattern();
        if (pattern == null || pattern.isEmpty() || !PeltData.isPattern(pattern)) {
            return null;
        }
        return CatSkinHelper.patternTexture(pattern);
    }

    @Override
    public ResourceLocation getTextureLocation(ClanCatNpc npc) {
        ResourceLocation pattern = patternTexture(npc);
        if (pattern != null) {
            return pattern;
        }
        ResourceLocation white = CatSkinHelper.getWhiteTexture();
        return white != null ? white : FALLBACK;
    }

    public static float[] getCoatTint(ClanCatNpc npc) {
        String hex = npc.getNpcPeltColor();
        if (hex == null || hex.isEmpty()) {
            return new float[]{1.0f, 1.0f, 1.0f};
        }
        int rgb = EyeData.parseColor(hex);
        if (rgb == -1) {
            return new float[]{1.0f, 1.0f, 1.0f};
        }
        if (patternTexture(npc) != null) {
            return PeltData.softTint(rgb);
        }
        float[] tint = PeltData.tint(hex);
        return tint == null ? new float[]{1.0f, 1.0f, 1.0f} : tint;
    }

    @Override
    public void render(ClanCatNpc npc, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float[] tint = getCoatTint(npc);
        this.model.setTint(tint[0], tint[1], tint[2]);
        this.shadowRadius = SHADOW * ClanCatNpc.modelScale(npc.getNpcRole());
        super.render(npc, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    @Override
    protected void scale(ClanCatNpc npc, PoseStack poseStack, float partialTick) {
        float scale = ClanCatNpc.modelScale(npc.getNpcRole());
        poseStack.scale(scale, scale, scale);
    }

    public static final class NpcCatModel extends CatModel<ClanCatNpc> {

        private static final class Motion {
            final AnimationState walk = new AnimationState();
            final AnimationState run = new AnimationState();
        }

        private static final float RUN_FROM = 0.75F;
        private static final float RUN_FULL = 0.95F;
        private static final float WALK_FULL = 0.70F;

        private final Map<ClanCatNpc, Motion> motions = new WeakHashMap<>();

        private float tintR = 1.0F;
        private float tintG = 1.0F;
        private float tintB = 1.0F;

        public NpcCatModel(ModelPart root) {
            super(root);
        }

        void setTint(float r, float g, float b) {
            this.tintR = r;
            this.tintG = g;
            this.tintB = b;
        }

        @Override
        public void setupAnim(ClanCatNpc npc, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(npc, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

            Motion motion = this.motions.computeIfAbsent(npc, n -> new Motion());
            float run = Mth.clamp((limbSwingAmount - RUN_FROM) / (RUN_FULL - RUN_FROM), 0.0F, 1.0F);
            float walk = Mth.clamp(limbSwingAmount / WALK_FULL, 0.0F, 1.0F) * (1.0F - run);

            if (limbSwingAmount > 0.02F) {
                motion.walk.startIfStopped(npc.tickCount);
            } else {
                motion.walk.stop();
            }
            if (run > 0.0F) {
                motion.run.startIfStopped(npc.tickCount);
            } else {
                motion.run.stop();
            }

            this.animateWalk(motion.walk, ageInTicks, walk);
            this.animateRun(motion.run, ageInTicks, run);
        }

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                                   int packedOverlay, float red, float green, float blue, float alpha) {
            super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay,
                    red * this.tintR, green * this.tintG, blue * this.tintB, alpha);
        }
    }

    private static final class EyeLayer extends RenderLayer<ClanCatNpc, NpcCatModel> {

        EyeLayer(RenderLayerParent<ClanCatNpc, NpcCatModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           ClanCatNpc npc, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (npc.isInvisible()) {
                return;
            }
            float[] iris = tint(EyeData.parseColor(npc.getNpcEyeColor()));
            ModelPart head = this.getParentModel().getHead();

            boolean dark = npc.level().getMaxLocalRawBrightness(npc.blockPosition()) < 6;
            VertexConsumer sclera = dark
                    ? buffer.getBuffer(RenderType.eyes(SCLERA))
                    : buffer.getBuffer(RenderType.entityCutoutNoCull(SCLERA));
            head.render(poseStack, sclera, dark ? 0xF000F0 : packedLight,
                    OverlayTexture.NO_OVERLAY, 0.94F, 0.93F, 0.88F, 1.0F);

            head.render(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(LEFT_PUPIL)),
                    packedLight, OverlayTexture.NO_OVERLAY, iris[0], iris[1], iris[2], 1.0F);
            head.render(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(RIGHT_PUPIL)),
                    packedLight, OverlayTexture.NO_OVERLAY, iris[0], iris[1], iris[2], 1.0F);
        }

        private static float[] tint(int rgb) {
            if (rgb == -1) {
                return new float[]{1.0F, 1.0F, 1.0F};
            }
            return new float[]{
                    ((rgb >> 16) & 0xFF) / 255.0F,
                    ((rgb >> 8) & 0xFF) / 255.0F,
                    (rgb & 0xFF) / 255.0F
            };
        }
    }
}
