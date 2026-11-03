package com.katt.warriors.client;

import com.katt.warriors.Warriors;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

/**
 * Custom cat player model with walk and run animations from assets/model.java.
 * Provides public animate wrappers because HierarchicalModel#animate() is protected.
 */
public class CatModel<T extends Entity> extends HierarchicalModel<T> {

    /** Layer location registered in ClientModEvents. */
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(Warriors.MODID, "cat_player"), "main");

    // ── Animation definitions (from assets/model.java) ────────────────────────

    public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.0F).looping()
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F,  0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.posVec(0.0F,  0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.0F,  KeyframeAnimations.posVec(0.0F,  0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,  KeyframeAnimations.degreeVec( 10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(  0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.degreeVec(-22.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.75F, KeyframeAnimations.degreeVec( 10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.0F,  KeyframeAnimations.degreeVec( 10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F, -1.0F,  0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F,  1.0F, -0.5F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.posVec(0.0F, -1.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F,  0.75F,-0.5F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.0F,  KeyframeAnimations.posVec(0.0F, -1.0F,  0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,  KeyframeAnimations.degreeVec(-20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(  7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.degreeVec(  7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.0F,  KeyframeAnimations.degreeVec(-20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F, -1.0F,  0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.posVec(0.0F, -1.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F,  0.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.0F,  KeyframeAnimations.posVec(0.0F, -1.0F,  0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("back_left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,  KeyframeAnimations.degreeVec( -7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(  7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.degreeVec(-12.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.75F, KeyframeAnimations.degreeVec( 15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.0F,  KeyframeAnimations.degreeVec( -7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("back_left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F, 0.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, 0.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.posVec(0.0F, 0.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F,-1.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.0F,  KeyframeAnimations.posVec(0.0F, 0.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("back_right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,  KeyframeAnimations.degreeVec( 22.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec(-12.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.degreeVec( 22.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.0F,  KeyframeAnimations.degreeVec( 22.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("back_right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F,  0.0F,-1.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.posVec(0.0F, -1.0F,-1.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F,  0.0F,-1.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(1.0F,  KeyframeAnimations.posVec(0.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .build();

    public static final AnimationDefinition RUN = AnimationDefinition.Builder.withLength(0.5F).looping()
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,  KeyframeAnimations.degreeVec( -2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.degreeVec( 17.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.degreeVec( -2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F,  0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.posVec(0.0F,  0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F,  0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.posVec(0.0F,  0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,   KeyframeAnimations.degreeVec(-75.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.125F, KeyframeAnimations.degreeVec( 11.25F,0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F,  KeyframeAnimations.degreeVec(  5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,   KeyframeAnimations.degreeVec(-75.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("front_left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,   KeyframeAnimations.posVec(0.0F, -1.0F, -3.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.125F, KeyframeAnimations.posVec(0.0F, -3.5F, -3.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F,  KeyframeAnimations.posVec(0.0F, -1.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,   KeyframeAnimations.posVec(0.0F, -1.0F, -3.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,   KeyframeAnimations.degreeVec(-75.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.125F, KeyframeAnimations.degreeVec( 11.25F,0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F,  KeyframeAnimations.degreeVec(  5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,   KeyframeAnimations.degreeVec(-75.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("front_right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,   KeyframeAnimations.posVec(0.0F, -1.0F, -3.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.125F, KeyframeAnimations.posVec(0.0F, -3.5F, -3.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F,  KeyframeAnimations.posVec(0.0F, -1.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,   KeyframeAnimations.posVec(0.0F, -1.0F, -3.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("back_left_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,   KeyframeAnimations.degreeVec( 67.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.125F, KeyframeAnimations.degreeVec(  5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F,  KeyframeAnimations.degreeVec( -5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.375F, KeyframeAnimations.degreeVec(  5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,   KeyframeAnimations.degreeVec( 67.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("back_left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F, -1.0F, 3.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F,  0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.posVec(0.0F, -1.0F, 3.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("back_right_leg", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,   KeyframeAnimations.degreeVec( 67.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.125F, KeyframeAnimations.degreeVec(  5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F,  KeyframeAnimations.degreeVec( -5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.375F, KeyframeAnimations.degreeVec(  5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,   KeyframeAnimations.degreeVec( 67.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("back_right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F, -1.0F, 3.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F,  0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.posVec(0.0F, -1.0F, 3.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F,  0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.posVec(0.0F,  0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .addAnimation("tail2", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F,  0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
                    new Keyframe(0.5F,  KeyframeAnimations.posVec(0.0F,  0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
            ))
            .build();

    // ── Model parts ───────────────────────────────────────────────────────────

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart front_left_leg;
    private final ModelPart front_right_leg;
    private final ModelPart back_left_leg;
    private final ModelPart back_right_leg;
    private final ModelPart tail;
    private final ModelPart tail2;

    public CatModel(ModelPart root) {
        this.root          = root;
        this.head          = root.getChild("head");
        this.body          = root.getChild("body");
        this.front_left_leg  = root.getChild("front_left_leg");
        this.front_right_leg = root.getChild("front_right_leg");
        this.back_left_leg   = root.getChild("back_left_leg");
        this.back_right_leg  = root.getChild("back_right_leg");
        this.tail          = root.getChild("tail");
        this.tail2         = root.getChild("tail2");
    }

    /** Called by Warriors.ClientModEvents to register the layer geometry. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition head = partdefinition.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(18, 0).addBox(-2.5F, -2.0F, -3.0F, 5.0F, 4.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(24, 21).addBox(-1.5F, -0.02F, -4.0F, 3.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 13.0F, -8.0F));

        head.addOrReplaceChild("head_r1",
                CubeListBuilder.create().texOffs(8, 29)
                        .addBox(1.0F, -13.0F, -9.0F, 1.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(3.0F, 11.0F, 5.0F, -0.3054F, 0.6545F, 0.0F));

        head.addOrReplaceChild("head_r2",
                CubeListBuilder.create().texOffs(28, 25)
                        .addBox(-2.0F, -13.0F, -9.0F, 1.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-3.0F, 11.0F, 5.0F, -0.3054F, -0.6545F, 0.0F));

        head.addOrReplaceChild("cheekleft_r1",
                CubeListBuilder.create().texOffs(30, 9)
                        .addBox(-1.0F, -1.5F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-2.5F, 0.5F, 0.5F, 0.0F, 0.9163F, 0.0F));

        head.addOrReplaceChild("whiskerright_r1",
                CubeListBuilder.create().texOffs(14, 33).mirror()
                        .addBox(-1.0F, -1.5F, -0.5F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(-2.5F, 0.5F, -2.5F, 0.0F, 0.3491F, 0.0F));

        head.addOrReplaceChild("whiskerleft_r1",
                CubeListBuilder.create().texOffs(14, 33)
                        .addBox(-1.0F, -1.5F, -0.5F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.5F, 0.5F, -2.5F, 0.0F, -0.3491F, 0.0F));

        head.addOrReplaceChild("cheekright_r1",
                CubeListBuilder.create().texOffs(14, 29)
                        .addBox(-1.0F, -1.5F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.5F, 0.5F, 0.5F, 0.0F, -0.9163F, 0.0F));

        partdefinition.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.0F, 3.0F, -7.0F, 4.0F, 16.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 12.0F, -10.0F, 1.5708F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("front_left_leg",
                CubeListBuilder.create().texOffs(18, 9)
                        .addBox(-1.0F, 0.0F, 0.0F, 2.0F, 10.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.1F, 14.1F, -5.0F));

        partdefinition.addOrReplaceChild("front_right_leg",
                CubeListBuilder.create().texOffs(0, 21)
                        .addBox(-1.0F, 0.0F, 0.0F, 2.0F, 10.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.1F, 14.1F, -5.0F));

        partdefinition.addOrReplaceChild("back_left_leg",
                CubeListBuilder.create().texOffs(8, 21)
                        .addBox(-1.0F, 0.0F, 1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(1.1F, 18.0F, 5.0F));

        partdefinition.addOrReplaceChild("back_right_leg",
                CubeListBuilder.create().texOffs(16, 21)
                        .addBox(-1.0F, 0.0F, 1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(-1.1F, 18.0F, 5.0F));

        partdefinition.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(24, 25)
                        .addBox(-0.5F, 0.0F, 0.0F, 1.0F, 8.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 15.5F, 8.0F, 1.5708F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("tail2",
                CubeListBuilder.create().texOffs(26, 9)
                        .addBox(-0.5F, 0.0F, 0.0F, 1.0F, 8.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 15.5F, 16.0F, 1.5708F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    // ── HierarchicalModel ─────────────────────────────────────────────────────

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Animation is driven externally via the public animate wrappers below.
        applyHeadLook(netHeadYaw, headPitch);
    }

    /**
     * Turn the head towards the player's look direction.
     *
     * <p>The walk / run clips already animate the head (a bob and a nod), so
     * this is applied <em>after</em> them as an additive offset, exactly like
     * vanilla's player model does. The model is flipped on X/Y by the renderer
     * (see CatPlayerRenderHandler), which is why the vanilla signs and the
     * usual -75..75 degree head-yaw clamp can be used unchanged.</p>
     *
     * @param netHeadYaw head yaw relative to the body, in degrees
     * @param headPitch  head pitch, in degrees (positive = looking down)
     */
    public void applyHeadLook(float netHeadYaw, float headPitch) {
        this.head.yRot += Mth.clamp(netHeadYaw, -75.0F, 75.0F) * Mth.DEG_TO_RAD;
        this.head.xRot += headPitch * Mth.DEG_TO_RAD;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer,
                               int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        head.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        front_left_leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        front_right_leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        back_left_leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        back_right_leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        tail.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        tail2.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    // ── Public animation helpers ──────────────────────────────────────────────
    //
    // HierarchicalModel#animate() is protected, and its fourth parameter is the
    // animation *playback speed*, not an amplitude weight. To cross-fade walk
    // into run we call KeyframeAnimations.animate directly, whose fourth
    // parameter does scale every keyframe value.

    /** Scratch vector reused by KeyframeAnimations.animate (as vanilla does). */
    private static final Vector3f ANIMATION_VECTOR = new Vector3f();

    /**
     * Apply the walk animation with the given blend weight.
     * @param state      the AnimationState managing elapsed time
     * @param ageInTicks current game time in ticks (used by AnimationState#updateTime)
     * @param weight     blend weight, 0 = no effect, 1 = full amplitude
     */
    public void animateWalk(AnimationState state, float ageInTicks, float weight) {
        animateWeighted(state, WALK, ageInTicks, weight);
    }

    /**
     * Apply the run animation with the given blend weight.
     */
    public void animateRun(AnimationState state, float ageInTicks, float weight) {
        animateWeighted(state, RUN, ageInTicks, weight);
    }

    private void animateWeighted(AnimationState state, AnimationDefinition definition,
                                 float ageInTicks, float weight) {
        // Advance the clip at normal playback speed even when its current
        // weight is zero, so it is already at a sensible phase when it fades in.
        state.updateTime(ageInTicks, 1.0F);
        if (weight <= 0.0F) {
            return;
        }
        state.ifStarted(s -> KeyframeAnimations.animate(
                this, definition, s.getAccumulatedTime(), weight, ANIMATION_VECTOR));
    }

    /** Expose head part so the eye-overlay renderer can align to it. */
    public ModelPart getHead() { return head; }
}