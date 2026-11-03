package com.katt.warriors.client.cutscene;

import com.katt.warriors.Warriors;
import com.katt.warriors.client.CatModel;
import com.katt.warriors.client.CatModelCache;
import com.katt.warriors.client.cutscene.CutsceneScript.Line;
import com.katt.warriors.client.cutscene.CutsceneScript.Speaker;
import com.mojang.blaze3d.platform.Lighting;
import com.katt.warriors.data.PeltData;
import com.katt.warriors.entity.CatSkinHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Draws every layer of the intro cutscene.
 *
 * <p>Layering (GUI z grows towards the viewer):</p>
 * <ul>
 *   <li>z = 0 – void gradient, nebulae, stars, candlelight pools, candle and dust</li>
 *   <li>z = 50 – the 3D cat model (renders on the depth buffer after the 2D pass)</li>
 *   <li>z = 300 – vignette, dialogue shading + text, letterbox bars</li>
 * </ul>
 */
public final class IntroSceneRenderer {

    private static final ResourceLocation GLOW =
            new ResourceLocation(Warriors.MODID, "textures/gui/glow.png");
    private static final ResourceLocation VIGNETTE =
            new ResourceLocation(Warriors.MODID, "textures/gui/vignette.png");
    private static final ResourceLocation CLOUD =
            new ResourceLocation(Warriors.MODID, "textures/gui/cloud.png");

    private static final ResourceLocation CAT_TEXTURE =
            new ResourceLocation(Warriors.MODID, "textures/entity/cat_player/white.png");
    private static final ResourceLocation SCLERA_TEXTURE =
            new ResourceLocation(Warriors.MODID, "textures/entity/cat_player/scelera.png");
    private static final ResourceLocation LEFT_PUPIL_TEXTURE =
            new ResourceLocation(Warriors.MODID, "textures/entity/cat_player/left_pupil.png");
    private static final ResourceLocation RIGHT_PUPIL_TEXTURE =
            new ResourceLocation(Warriors.MODID, "textures/entity/cat_player/right_pupil.png");

    /** Cat model is 13 px tall (0.8125 blocks) between its feet and ears. */
    private static final float CAT_MODEL_HEIGHT = 0.8125f;

    private static final int TEXT_NARRATOR = 0xFFFFFF;
    private static final int TEXT_PLAYER = 0xBFD0E6;
    private static final int TEXT_CAT = 0xF2D8A2;

    // ── Starfield ────────────────────────────────────────────────────────────

    private static final class Star {
        float x;
        float y;
        float size;
        float phase;
        float speed;
        float brightness;
        int r;
        int g;
        int b;
    }

    private final List<Star> stars = new ArrayList<>();
    private final List<Star> heroes = new ArrayList<>();
    private boolean built;

    /**
     * Loads everything the scene needs up front (cat model, textures) so the
     * first frame of the candle reveal does not stall on resource loading.
     */
    public void preload() {
        Minecraft mc = Minecraft.getInstance();
        CatModelCache.getOrBake(mc);
        for (String pattern : PeltData.PATTERNS) {
            ResourceLocation patternTexture = CatSkinHelper.patternTexture(pattern);
            if (patternTexture != null) {
                mc.getTextureManager().getTexture(patternTexture);
            }
        }
        for (ResourceLocation texture : new ResourceLocation[] {
                GLOW, VIGNETTE, CLOUD, CAT_TEXTURE, SCLERA_TEXTURE,
                LEFT_PUPIL_TEXTURE, RIGHT_PUPIL_TEXTURE}) {
            mc.getTextureManager().getTexture(texture);
        }
    }

    // ── Public entry point ───────────────────────────────────────────────────

    public void render(GuiGraphics gfx, float time, float animationTime,
                       int width, int height, String playerName) {
        if (!this.built) {
            this.buildStarfield();
            this.built = true;
        }

        this.renderVoid(gfx, time, animationTime, width, height);
        gfx.flush();

        this.renderCat(gfx, time, animationTime, width, height);

        gfx.pose().pushPose();
        gfx.pose().translate(0.0F, 0.0F, 300.0F);
        this.renderForeground(gfx, time, width, height, playerName);
        gfx.flush();
        gfx.pose().popPose();
    }

    // ── Background: void + candle ────────────────────────────────────────────

    private void renderVoid(GuiGraphics gfx, float time, float animationTime,
                            int width, int height) {
        float w = width;
        float h = height;

        gfx.fillGradient(0, 0, width, height, 0xFF05060F, 0xFF0B0713);

        float starFade = smooth(0.0F, CutsceneScript.STARS_FULL - 0.4F, time);

        if (starFade > 0.002F) {
            // Soft nebulae, slowly drifting.
            this.drawGlow(gfx, w * 0.02F, h * 0.00F + Mth.sin(animationTime * 0.07F) * 10.0F,
                    w * 0.95F, h * 0.85F, argb(0.11F * starFade, 70, 84, 150));
            this.drawGlow(gfx, w * 0.42F, -h * 0.12F + Mth.cos(animationTime * 0.05F) * 12.0F,
                    w * 0.85F, h * 0.8F, argb(0.085F * starFade, 118, 66, 150));
            this.drawGlow(gfx, w * 0.10F, h * 0.35F,
                    w * 1.0F, h * 0.9F, argb(0.07F * starFade, 40, 92, 132));

            // Stars.
            for (Star star : this.stars) {
                float twinkle = 0.55F + 0.45F * Mth.sin(animationTime * star.speed + star.phase);
                float alpha = starFade * star.brightness * twinkle;
                if (alpha < 0.02F) {
                    continue;
                }
                int x = (int) (star.x * w);
                int y = (int) (star.y * h);
                int size = (int) star.size;
                gfx.fill(x, y, x + size, y + size, argb(alpha, star.r, star.g, star.b));
            }

            // A handful of brighter stars with a soft halo.
            for (Star star : this.heroes) {
                float twinkle = 0.6F + 0.4F * Mth.sin(animationTime * star.speed + star.phase);
                float alpha = starFade * twinkle;
                float x = star.x * w;
                float y = star.y * h;
                this.drawGlow(gfx, x - 10.0F, y - 10.0F, 20.0F, 20.0F,
                        argb(0.26F * alpha, star.r, star.g, star.b));
                gfx.fill((int) x, (int) y, (int) x + 2, (int) y + 2,
                        argb(alpha, 255, 255, 255));
            }
        }

        float warm = smooth(CutsceneScript.CANDLE_IGNITE - 0.1F,
                CutsceneScript.CANDLE_IGNITE + 0.7F, time);
        if (warm > 0.002F) {
            this.renderCandle(gfx, animationTime, w, h, warm, starFade);
        }
    }

    private void renderCandle(GuiGraphics gfx, float animationTime, float w, float h,
                              float intensity, float starFade) {
        float x = w * 0.27F;
        float floor = h * 0.845F;

        float flicker = 0.86F + 0.10F * Mth.sin(animationTime * 9.3F)
                + 0.04F * Mth.sin(animationTime * 23.7F + 1.7F);
        float glow = intensity * flicker;

        // Warm pools of light: the floor pool spans the whole screen (and a bit
        // beyond, so the radial fall-off never shows an edge), plus a tighter
        // pool at the flame.
        this.drawGlow(gfx, -w * 0.15F, floor - h * 0.12F, w * 1.30F, h * 0.26F,
                argb(0.30F * glow, 255, 148, 62));
        this.drawGlow(gfx, x - w * 0.12F, floor - h * 0.28F, w * 0.24F, h * 0.34F,
                argb(0.20F * glow, 255, 158, 74));
        this.drawGlow(gfx, x - 90.0F, floor - 150.0F, 180.0F, 190.0F,
                argb(0.32F * glow, 255, 176, 92));

        // Light on the floor: full-width, fades in with the flame instead of popping.
        gfx.fillGradient(0, (int) (floor - h * 0.05F), (int) w, (int) floor,
                argb(0.0F, 255, 170, 90), argb(0.10F * glow, 255, 170, 90));
        gfx.fillGradient(0, (int) floor, (int) w, (int) (floor + h * 0.155F),
                argb(0.20F * glow, 255, 192, 122), argb(0.0F, 255, 192, 122));

        // ── Candle body ──────────────────────────────────────────────────────
        float height = h * (0.095F + 0.01F * Mth.sin(animationTime * 0.6F));
        float top = floor - height;
        int bodyW = Math.max(10, (int) (w * 0.011F));

        gfx.fill((int) (x - bodyW - 2), (int) top - 2, (int) (x + bodyW + 2), (int) floor,
                0xFF120D09);
        gfx.fill((int) (x - bodyW), (int) top, (int) (x + bodyW), (int) floor, 0xFFE7DAC0);
        gfx.fill((int) (x + bodyW * 0.25F), (int) top, (int) (x + bodyW), (int) floor,
                0xFFC8B896);
        gfx.fill((int) (x - bodyW), (int) top, (int) (x + bodyW), (int) top + 5, 0xFFF8F0DE);

        // Wax drips down one side.
        gfx.fill((int) (x - bodyW * 0.7F), (int) top + 4, (int) (x - bodyW * 0.3F),
                (int) (top + height * 0.26F), 0xFFF2E6CF);
        gfx.fill((int) (x + bodyW * 0.1F), (int) top + 4, (int) (x + bodyW * 0.45F),
                (int) (top + height * 0.16F), 0xFFEDE0C7);

        // Wick.
        gfx.fill((int) (x - 1), (int) top - 8, (int) x + 1, (int) top + 1, 0xFF241A12);

        // ── Flame ────────────────────────────────────────────────────────────
        float flameH = 30.0F * (0.92F + 0.10F * Mth.sin(animationTime * 13.1F));
        float flameW = 8.0F;
        float flameBase = top - 6.0F;
        float flameTop = flameBase - flameH;

        this.drawGlow(gfx, x - flameW * 5.0F, flameTop - flameH * 0.6F,
                flameW * 10.0F, flameH * 2.0F, argb(0.55F * glow, 255, 190, 110));
        gfx.fill((int) (x - flameW * 0.5F), (int) flameTop, (int) (x + flameW * 0.5F),
                (int) flameBase, argb(0.95F * glow, 255, 196, 96));
        gfx.fill((int) (x - flameW * 0.28F), (int) (flameBase - flameH * 0.7F),
                (int) (x + flameW * 0.28F), (int) flameBase,
                argb(0.98F * glow, 255, 238, 180));
        gfx.fill((int) (x - flameW * 0.16F), (int) (flameBase - flameH * 0.45F),
                (int) (x + flameW * 0.16F), (int) flameBase,
                argb(glow, 255, 255, 246));
        gfx.fill((int) (x - 3), (int) flameBase - 5, (int) (x + 3), (int) flameBase,
                argb(0.75F * glow, 130, 176, 255));

        // ── Dust motes drifting up through the light ─────────────────────────
        for (int i = 0; i < 26; i++) {
            float seed = ((i * 47) % 101) / 101.0F;
            float speed = 0.035F + (i % 6) * 0.0065F;
            float progress = (animationTime * speed + seed) % 1.0F;
            float mx = x + (seed - 0.5F) * w * 0.42F + Mth.sin(animationTime * 0.7F + i) * 9.0F;
            float my = floor - progress * h * 0.42F;
            float alpha = 0.55F * (1.0F - progress) * glow;
            int size = (i % 5 == 0) ? 2 : 1;
            gfx.fill((int) mx, (int) my, (int) mx + size, (int) my + size,
                    argb(alpha, 255, 214, 150));
        }

        // Faint stars keep twinkling behind the light.
        if (starFade > 0.01F) {
            this.drawGlow(gfx, w * 0.55F, h * 0.05F, w * 0.6F, h * 0.6F,
                    argb(0.06F * starFade, 90, 110, 190));
        }
    }

    // ── The cat ──────────────────────────────────────────────────────────────

    private void renderCat(GuiGraphics gfx, float time, float animationTime,
                           int width, int height) {
        float appear = smooth(CutsceneScript.CAT_APPEAR_START,
                CutsceneScript.CAT_APPEAR_END, time);
        if (appear <= 0.002F) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        CatModel<?> model = CatModelCache.getOrBake(mc);
        if (model == null) {
            return;
        }

        model.root().getAllParts().forEach(ModelPart::resetPose);

        // Gentle idle: slow head sway, breathing bob and a lazy tail.
        float headYaw = -7.0F + 4.0F * Mth.sin(animationTime * 0.55F);
        float headPitch = 2.0F + 1.8F * Mth.sin(animationTime * 0.37F);
        model.applyHeadLook(headYaw, headPitch);

        ModelPart tail = model.root().getChild("tail");
        tail.yRot = 0.20F * Mth.sin(animationTime * 1.05F);
        tail.zRot = 0.10F * Mth.sin(animationTime * 0.85F + 1.0F);
        ModelPart tail2 = model.root().getChild("tail2");
        tail2.yRot = 0.26F * Mth.sin(animationTime * 1.05F + 0.7F);

        float catHeightPx = Math.min(height * 0.42F, width * 0.32F);
        float scale = catHeightPx / CAT_MODEL_HEIGHT;
        float cx = width * 0.62F;
        float groundY = height * 0.845F + Mth.sin(animationTime * 1.15F) * 1.5F;

        PoseStack pose = gfx.pose();
        pose.pushPose();

        // Same sequence as InventoryScreen#renderEntityInInventory, followed by
        // LivingEntityRenderer's model-space flip + 1.501 block offset.
        pose.translate(cx, groundY, 50.0F);
        pose.scale(scale, scale, -scale);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);

        MultiBufferSource.BufferSource buffers = gfx.bufferSource();
        int light = LightTexture.FULL_BRIGHT;
        float flicker = 0.97F + 0.03F * Mth.sin(animationTime * 11.3F);
        float brightness = (0.30F + 0.70F * appear) * flicker;

        try {
            Lighting.setupForEntityInInventory();

            VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(CAT_TEXTURE));
            model.renderToBuffer(pose, body, light, OverlayTexture.NO_OVERLAY,
                    brightness, brightness * 0.955F, brightness * 0.885F, 1.0F);

            // Eyes: additive pale-blue sclera, then near-black pupils.
            float eyes = smooth(0.35F, 1.0F, appear);
            if (eyes > 0.01F) {
                ModelPart head = model.getHead();

                VertexConsumer sclera = buffers.getBuffer(RenderType.eyes(SCLERA_TEXTURE));
                head.render(pose, sclera, light, OverlayTexture.NO_OVERLAY,
                        0.34F * eyes, 0.56F * eyes, 1.0F * eyes, 1.0F);

                float pupil = 0.035F + 0.965F * eyes;
                VertexConsumer left = buffers.getBuffer(
                        RenderType.entityCutoutNoCull(LEFT_PUPIL_TEXTURE));
                head.render(pose, left, light, OverlayTexture.NO_OVERLAY,
                        0.04F * pupil, 0.045F * pupil, 0.07F * pupil, 1.0F);

                VertexConsumer right = buffers.getBuffer(
                        RenderType.entityCutoutNoCull(RIGHT_PUPIL_TEXTURE));
                head.render(pose, right, light, OverlayTexture.NO_OVERLAY,
                        0.04F * pupil, 0.045F * pupil, 0.07F * pupil, 1.0F);
            }

            // Must be drawn while the inventory lighting is still active.
            gfx.flush();
        } finally {
            Lighting.setupFor3DItems();
            pose.popPose();
        }
    }

    // ── Customize-screen preview ─────────────────────────────────────────────

    /** Degrees the preview cat's body is turned (negative = faces screen-right). */
    private static final float PREVIEW_BODY_YAW = -32.0F;
    /** Degrees the head turns back towards the viewer, relative to the body. */
    private static final float PREVIEW_HEAD_YAW = 22.0F;
    /** Middle of the cat's length (head to tail tip) along the model's z axis, in blocks. */
    private static final float PREVIEW_CENTER_Z = 6.5F / 16.0F;

    /**
     * Draws the real cat model inside a rectangle, tinted with the chosen pelt
     * and eye colours (all {@code 0xAARRGGBB}; only RGB is used), in a slow
     * three-quarter idle so the face, eyes and tail are all visible.
     */
    public void renderCatPreview(GuiGraphics gfx, int x, int y, int w, int h,
                                 int pelt, String pattern,
                                 int sclera, int leftEye, int rightEye,
                                 float animationTime) {
        Minecraft mc = Minecraft.getInstance();
        CatModel<?> model = CatModelCache.getOrBake(mc);
        if (model == null) {
            return;
        }

        model.root().getAllParts().forEach(ModelPart::resetPose);

        float bodyYaw = PREVIEW_BODY_YAW + 5.0F * Mth.sin(animationTime * 0.5F);
        model.applyHeadLook(PREVIEW_HEAD_YAW + 4.0F * Mth.sin(animationTime * 0.7F),
                3.0F + 1.5F * Mth.sin(animationTime * 0.4F));

        ModelPart tail = model.root().getChild("tail");
        tail.yRot = 0.20F * Mth.sin(animationTime * 1.05F);
        tail.zRot = 0.10F * Mth.sin(animationTime * 0.85F + 1.0F);
        ModelPart tail2 = model.root().getChild("tail2");
        tail2.yRot = 0.26F * Mth.sin(animationTime * 1.05F + 0.7F);

        // The cat is about 2.1 blocks long in this pose and 0.8125 tall.
        float scale = Math.min(0.82F * w / 2.1F, 0.70F * h / CAT_MODEL_HEIGHT);
        float catHeight = scale * CAT_MODEL_HEIGHT;
        float cx = x + w / 2.0F;
        float groundY = y + h / 2.0F + catHeight / 2.0F;

        gfx.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);

        // Soft light behind the cat and a faint pool under its paws.
        this.drawGlow(gfx, cx - w * 0.42F, y + h * 0.10F, w * 0.84F, h * 0.80F,
                argb(0.10F, 150, 175, 225));
        this.drawGlow(gfx, cx - w * 0.36F, groundY - h * 0.06F, w * 0.72F, h * 0.12F,
                argb(0.22F, 200, 215, 240));

        PoseStack pose = gfx.pose();
        pose.pushPose();

        // Same orientation as the cutscene cat (model-space flip cancels the
        // GUI's), then a turn about the middle of the cat's body.
        pose.translate(cx, groundY, 50.0F);
        pose.scale(scale, scale, -scale);
        pose.translate(0.0F, -1.501F, 0.0F);
        pose.translate(0.0F, 0.0F, PREVIEW_CENTER_Z);
        pose.mulPose(Axis.YP.rotationDegrees(bodyYaw));
        pose.translate(0.0F, 0.0F, -PREVIEW_CENTER_Z);

        MultiBufferSource.BufferSource buffers = gfx.bufferSource();
        int light = LightTexture.FULL_BRIGHT;

        try {
            Lighting.setupForEntityInInventory();

            // A patterned coat uses its painted texture with a soft tint; a solid
            // coat is the plain white texture tinted by the pelt colour (this
            // mirrors CatSkinHelper.resolve, which the in-game cat uses).
            ResourceLocation patternTexture = CatSkinHelper.patternTexture(pattern);
            float[] tint = patternTexture != null
                    ? PeltData.softTint(pelt)
                    : new float[] {channel(pelt, 16), channel(pelt, 8), channel(pelt, 0)};

            VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(
                    patternTexture != null ? patternTexture : CAT_TEXTURE));
            model.renderToBuffer(pose, body, light, OverlayTexture.NO_OVERLAY,
                    tint[0], tint[1], tint[2], 1.0F);

            ModelPart head = model.getHead();

            VertexConsumer scleraVc = buffers.getBuffer(
                    RenderType.entityCutoutNoCull(SCLERA_TEXTURE));
            head.render(pose, scleraVc, light, OverlayTexture.NO_OVERLAY,
                    channel(sclera, 16), channel(sclera, 8), channel(sclera, 0), 1.0F);

            VertexConsumer left = buffers.getBuffer(
                    RenderType.entityCutoutNoCull(LEFT_PUPIL_TEXTURE));
            head.render(pose, left, light, OverlayTexture.NO_OVERLAY,
                    channel(leftEye, 16), channel(leftEye, 8), channel(leftEye, 0), 1.0F);

            VertexConsumer right = buffers.getBuffer(
                    RenderType.entityCutoutNoCull(RIGHT_PUPIL_TEXTURE));
            head.render(pose, right, light, OverlayTexture.NO_OVERLAY,
                    channel(rightEye, 16), channel(rightEye, 8), channel(rightEye, 0), 1.0F);

            // Must be flushed while the inventory lighting and the scissor are active.
            gfx.flush();
        } finally {
            Lighting.setupFor3DItems();
            pose.popPose();
            gfx.disableScissor();
        }
    }

    private static float channel(int rgb, int shift) {
        return ((rgb >> shift) & 0xFF) / 255.0F;
    }

    // ── Foreground: vignette, text, letterbox ────────────────────────────────

    public void renderForeground(GuiGraphics gfx, float time, int width, int height,
                                 String playerName) {
        this.renderFrame(gfx, width, height);

        int dialogueY = dialogueY(height);

        this.renderMonologue(gfx, time, width, height);
        this.renderDialogue(gfx, time, width, height, dialogueY, playerName);
    }

    /**
     * Draws the cinematic vignette, dialogue shading and letterbox bars. The
     * rebirth menus lay themselves out between these bars.
     */
    public void renderFrame(GuiGraphics gfx, int width, int height) {
        this.renderVignette(gfx, width, height);

        int bar = barHeight(height);
        int dialogueY = dialogueY(height);

        // Shading behind the dialogue so it reads over the candlelight.
        gfx.fillGradient(0, dialogueY - 26, width, height - bar + 2,
                0x00000000, 0x90000000);

        // Cinematic bars.
        gfx.fill(0, 0, width, bar, 0xFF000000);
        gfx.fill(0, height - bar, width, height + 1, 0xFF000000);
    }

    /** Height of the top/bottom cinematic bars for a given GUI height. */
    public static int barHeight(int height) {
        return Mth.clamp((int) (height * 0.095F), 28, 64);
    }

    /** Baseline of a dialogue line, kept inside the letterbox. */
    public static int dialogueY(int height) {
        return height - barHeight(height) - 70;
    }

    /** The cinematic vignette, drawn over whatever scene is on screen. */
    public void renderVignette(GuiGraphics gfx, int width, int height) {
        this.drawTextured(gfx, VIGNETTE, 0.0F, 0.0F, width, height, 0xFFFFFFFF);
    }

    private void renderMonologue(GuiGraphics gfx, float time, int width, int height) {
        Font font = Minecraft.getInstance().font;

        for (Line line : CutsceneScript.MONOLOGUE) {
            if (time < line.start() || time > line.end()) {
                continue;
            }

            float local = time - line.start();
            float alpha = Mth.clamp(local / 0.35F, 0.0F, 1.0F);
            if (alpha <= 0.002F) {
                continue;
            }

            String text = Component.translatable(line.key()).getString();
            String shown = typewriter(text, local, line);
            if (shown.isEmpty()) {
                continue;
            }

            int y = (int) (height * 0.43F);
            gfx.drawCenteredString(font, Component.literal(shown), width / 2, y,
                    withAlpha(TEXT_NARRATOR, alpha));
        }
    }

    private void renderDialogue(GuiGraphics gfx, float time, int width, int height,
                                int dialogueY, String playerName) {
        Font font = Minecraft.getInstance().font;
        int maxWidth = Math.min(360, width - 80);

        for (Line line : CutsceneScript.DIALOGUE) {
            if (time < line.start() || time > line.end()) {
                continue;
            }

            float local = time - line.start();
            float alpha = Mth.clamp(local / 0.3F, 0.0F, 1.0F);
            if (alpha <= 0.002F) {
                continue;
            }

            String full = Component.translatable(line.key(), playerName).getString();
            String shown = typewriter(full, local, line);
            int base = speakerColor(line.speaker());
            Component label = Component.translatable(line.speaker() == Speaker.CAT
                    ? CutsceneScript.SPEAKER_CAT_KEY
                    : CutsceneScript.SPEAKER_PLAYER_KEY);

            gfx.drawCenteredString(font, label, width / 2,
                    dialogueY - 17, withAlpha(base, alpha * 0.55F));

            int visible = shown.length();
            int y = dialogueY;
            for (String wrapped : wrap(font, full, maxWidth)) {
                if (visible <= 0) {
                    break;
                }
                String part = wrapped.substring(0, Math.min(visible, wrapped.length()));
                visible -= wrapped.length() + 1;
                if (part.isEmpty()) {
                    continue;
                }
                gfx.drawCenteredString(font, Component.literal(part), width / 2, y,
                        withAlpha(base, alpha));
                y += 12;
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void buildStarfield() {
        Random random = new Random(0xC0FFEE);
        for (int i = 0; i < 230; i++) {
            Star star = new Star();
            star.x = random.nextFloat();
            star.y = random.nextFloat() * 0.96F;
            star.size = random.nextFloat() < 0.14F ? 2.0F : 1.0F;
            star.phase = random.nextFloat() * Mth.TWO_PI;
            star.speed = 0.35F + random.nextFloat() * 1.8F;
            star.brightness = 0.18F + random.nextFloat() * 0.82F;

            switch (random.nextInt(4)) {
                case 0 -> {
                    star.r = 255;
                    star.g = 232;
                    star.b = 200;
                }
                case 1 -> {
                    star.r = 196;
                    star.g = 220;
                    star.b = 255;
                }
                default -> {
                    star.r = 255;
                    star.g = 255;
                    star.b = 255;
                }
            }

            this.stars.add(star);
        }

        for (int i = 0; i < 7; i++) {
            Star star = new Star();
            star.x = 0.05F + random.nextFloat() * 0.9F;
            star.y = 0.04F + random.nextFloat() * 0.72F;
            star.size = 2.0F;
            star.phase = random.nextFloat() * Mth.TWO_PI;
            star.speed = 0.25F + random.nextFloat() * 0.7F;
            star.brightness = 1.0F;
            star.r = 255;
            star.g = 255;
            star.b = 255;
            this.heroes.add(star);
        }
    }

    /** Draws a tinted, texture-alpha-blended quad immediately. */
    private void drawTextured(GuiGraphics gfx, ResourceLocation texture,
                              float x, float y, float w, float h, int color) {
        this.drawTextured(gfx, texture, x, y, w, h, color, 0.0F, 0.0F, 1.0F, 1.0F);
    }

    /**
     * Draws a tinted, texture-alpha-blended quad immediately, with source UVs
     * taken from ({@code u}, {@code v}) to ({@code u2}, {@code v2}) so the texture
     * can be scaled without being stretched.
     */
    private void drawTextured(GuiGraphics gfx, ResourceLocation texture,
                              float x, float y, float w, float h, int color,
                              float u, float v, float u2, float v2) {
        if (w <= 0.0F || h <= 0.0F || (color >>> 24) == 0) {
            return;
        }

        // GuiGraphics#fill/fillGradient only queue their vertices; flush them
        // first so the immediate textured quad keeps the intended paint order.
        gfx.flush();

        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = gfx.pose().last().pose();
        float a = (color >>> 24) / 255.0F;
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        builder.vertex(matrix, x, y + h, 0.0F).color(r, g, b, a).uv(u, v2).endVertex();
        builder.vertex(matrix, x, y, 0.0F).color(r, g, b, a).uv(u, v).endVertex();
        builder.vertex(matrix, x + w, y, 0.0F).color(r, g, b, a).uv(u2, v).endVertex();
        builder.vertex(matrix, x + w, y + h, 0.0F).color(r, g, b, a).uv(u2, v2).endVertex();
        BufferUploader.drawWithShader(builder.end());

        RenderSystem.disableBlend();
    }

    /**
     * Draws the whole cloud texture into the given rectangle. Callers keep the
     * 768x1024 aspect ratio, so the artwork is never stretched or cropped.
     */
    public void drawCloud(GuiGraphics gfx, float x, float y, float w, float h) {
        this.drawTextured(gfx, CLOUD, x, y, w, h, 0xFFFFFFFF);
    }

    /** Warm halo + accent line that marks the cloud under the cursor. */
    public void drawCloudHover(GuiGraphics gfx, float x, float y, float w, float h) {
        float cx = x + w * 0.5F;
        float cy = y + h * 0.46F;
        this.drawGlow(gfx, cx - w * 0.62F, cy - h * 0.32F, w * 1.24F, h * 0.72F,
                argb(0.16F, 255, 214, 150));
        gfx.fill((int) (x + w * 0.28F), (int) (y + h - 9),
                (int) (x + w * 0.72F), (int) (y + h - 8), 0x99C8A86A);
    }

    private void drawGlow(GuiGraphics gfx, float x, float y, float w, float h, int color) {
        this.drawTextured(gfx, GLOW, x, y, w, h, color);
    }

    private static int speakerColor(Speaker speaker) {
        return switch (speaker) {
            case PLAYER -> TEXT_PLAYER;
            case CAT -> TEXT_CAT;
            default -> TEXT_NARRATOR;
        };
    }

    private static int argb(float alpha, int r, int g, int b) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return a << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    private static int withAlpha(int rgb, float alpha) {
        return argb(alpha, rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
    }

    private static float smooth(float from, float to, float value) {
        if (to <= from) {
            return value >= to ? 1.0F : 0.0F;
        }
        float t = Mth.clamp((value - from) / (to - from), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static String typewriter(String text, float local, CutsceneScript.Line line) {
        if (text.isEmpty()) {
            return text;
        }
        float reveal = CutsceneScript.typewriterDuration(line);
        int count = (int) (Mth.clamp(local / reveal, 0.0F, 1.0F) * text.length());
        return text.substring(0, Math.min(count, text.length()));
    }

    /** Greedy word-wrap that keeps the layout stable while typing. */
    private static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : text.split(" ")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (font.width(candidate) > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }

        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }
}