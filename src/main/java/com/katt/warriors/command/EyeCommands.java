package com.katt.warriors.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.katt.warriors.data.EyeData;
import com.katt.warriors.network.WarriorsNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EyeCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("eye")
                .then(Commands.literal("color")
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> setBothPupilColors(ctx, StringArgumentType.getString(ctx, "value")))))
                .then(Commands.literal("random")
                    .executes(this::randomizeSclera))
                .then(Commands.literal("sclera")
                    .then(Commands.literal("color")
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes(ctx -> setScleraColor(ctx, StringArgumentType.getString(ctx, "value")))))
                    .then(Commands.literal("random")
                        .executes(this::randomizeSclera))
                    .then(Commands.literal("texture")
                        .then(Commands.argument("key", StringArgumentType.word())
                            .executes(ctx -> setScleraTexture(ctx, StringArgumentType.getString(ctx, "key")))))
                    .then(Commands.argument("key", StringArgumentType.word())
                        .executes(ctx -> setScleraOrColor(ctx, StringArgumentType.getString(ctx, "key")))))
                .then(Commands.literal("pupil")
                    .then(Commands.literal("color")
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes(ctx -> setBothPupilColors(ctx, StringArgumentType.getString(ctx, "value")))))
                    .then(Commands.literal("texture")
                        .then(Commands.argument("key", StringArgumentType.word())
                            .executes(ctx -> setBothPupilTextures(ctx, StringArgumentType.getString(ctx, "key")))))
                    .then(Commands.literal("left")
                        .then(Commands.literal("color")
                            .then(Commands.argument("value", StringArgumentType.greedyString())
                                .executes(ctx -> applyPupilColors(ctx,
                                        StringArgumentType.getString(ctx, "value"), true, false))))
                        .then(Commands.literal("texture")
                            .then(Commands.argument("key", StringArgumentType.word())
                                .executes(ctx -> setLeftPupilTexture(ctx, StringArgumentType.getString(ctx, "key")))))
                        .then(Commands.argument("key", StringArgumentType.word())
                            .executes(ctx -> setLeftPupilOrColor(ctx, StringArgumentType.getString(ctx, "key")))))
                    .then(Commands.literal("right")
                        .then(Commands.literal("color")
                            .then(Commands.argument("value", StringArgumentType.greedyString())
                                .executes(ctx -> applyPupilColors(ctx,
                                        StringArgumentType.getString(ctx, "value"), false, true))))
                        .then(Commands.literal("texture")
                            .then(Commands.argument("key", StringArgumentType.word())
                                .executes(ctx -> setRightPupilTexture(ctx, StringArgumentType.getString(ctx, "key")))))
                        .then(Commands.argument("key", StringArgumentType.word())
                            .executes(ctx -> setRightPupilOrColor(ctx, StringArgumentType.getString(ctx, "key")))))
                    .then(Commands.argument("key", StringArgumentType.word())
                        .executes(ctx -> setPupilOrColor(ctx, StringArgumentType.getString(ctx, "key")))))
                .then(Commands.literal("reset")
                    .executes(this::resetEyes))
        );
    }

    private int setScleraOrColor(CommandContext<CommandSourceStack> ctx, String value) {
        if (isClearKeyword(value) || EyeData.isValidColor(value)) {
            return setScleraColor(ctx, value);
        }
        return setScleraTexture(ctx, value);
    }

    private int setScleraTexture(CommandContext<CommandSourceStack> ctx, String key) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        String clean = requireTextureKey(ctx, key);
        if (clean == null) return 0;

        EyeData.setSclera(player, clean);
        EyeData.clearScleraColor(player);
        sync(player);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Sclera texture set to: " + clean), false);
        return 1;
    }

    private int setScleraColor(CommandContext<CommandSourceStack> ctx, String value) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        if (isClearKeyword(value)) {
            EyeData.clearScleraColor(player);
            sync(player);
            ctx.getSource().sendSuccess(
                    () -> Component.literal("Sclera colour override cleared."), false);
            return 1;
        }

        int rgb = EyeData.parseColor(value);
        if (rgb == -1) return invalidColor(ctx, value);

        String normalized = EyeData.toHexColor(rgb);
        EyeData.setScleraColor(player, normalized);
        sync(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Sclera colour set to: " + normalized), false);
        return 1;
    }

    private int randomizeSclera(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        String hex = EyeData.getRandomScleraColor();
        EyeData.setScleraColor(player, hex);
        sync(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Random sclera colour rolled: " + hex), false);
        return 1;
    }

    private int setPupilOrColor(CommandContext<CommandSourceStack> ctx, String value) {
        if (isClearKeyword(value) || EyeData.isValidColor(value)) {
            return setBothPupilColors(ctx, value);
        }
        return setBothPupilTextures(ctx, value);
    }

    private int setLeftPupilOrColor(CommandContext<CommandSourceStack> ctx, String value) {
        if (isClearKeyword(value) || EyeData.isValidColor(value)) {
            return applyPupilColors(ctx, value, true, false);
        }
        return setLeftPupilTexture(ctx, value);
    }

    private int setRightPupilOrColor(CommandContext<CommandSourceStack> ctx, String value) {
        if (isClearKeyword(value) || EyeData.isValidColor(value)) {
            return applyPupilColors(ctx, value, false, true);
        }
        return setRightPupilTexture(ctx, value);
    }

    private int setBothPupilColors(CommandContext<CommandSourceStack> ctx, String value) {
        return applyPupilColors(ctx, value, true, true);
    }

    private int applyPupilColors(CommandContext<CommandSourceStack> ctx, String value,
                                 boolean left, boolean right) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        String which = left && right ? "Pupil" : (left ? "Left pupil" : "Right pupil");

        if (isClearKeyword(value)) {
            if (left)  EyeData.clearLeftPupilColor(player);
            if (right) EyeData.clearRightPupilColor(player);
            sync(player);
            ctx.getSource().sendSuccess(
                    () -> Component.literal(which + " colour override cleared."), false);
            return 1;
        }

        int rgb = EyeData.parseColor(value);
        if (rgb == -1) return invalidColor(ctx, value);

        String normalized = EyeData.toHexColor(rgb);
        if (left)  EyeData.setLeftPupilColor(player, normalized);
        if (right) EyeData.setRightPupilColor(player, normalized);
        sync(player);
        ctx.getSource().sendSuccess(
                () -> Component.literal(which + " colour set to: " + normalized), false);
        return 1;
    }

    private int setBothPupilTextures(CommandContext<CommandSourceStack> ctx, String key) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        String clean = requireTextureKey(ctx, key);
        if (clean == null) return 0;

        EyeData.setPupils(player, clean);
        EyeData.clearPupilColors(player);
        sync(player);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Both pupil textures set to: " + clean), false);
        return 1;
    }

    private int setLeftPupilTexture(CommandContext<CommandSourceStack> ctx, String key) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        String clean = requireTextureKey(ctx, key);
        if (clean == null) return 0;

        EyeData.setLeftPupil(player, clean);
        EyeData.clearLeftPupilColor(player);
        sync(player);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Left pupil texture set to: " + clean), false);
        return 1;
    }

    private int setRightPupilTexture(CommandContext<CommandSourceStack> ctx, String key) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        String clean = requireTextureKey(ctx, key);
        if (clean == null) return 0;

        EyeData.setRightPupil(player, clean);
        EyeData.clearRightPupilColor(player);
        sync(player);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Right pupil texture set to: " + clean), false);
        return 1;
    }

    private int resetEyes(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        EyeData.setSclera(player,      EyeData.DEFAULT_SCLERA);
        EyeData.setLeftPupil(player,   EyeData.DEFAULT_LEFT_PUPIL);
        EyeData.setRightPupil(player,  EyeData.DEFAULT_RIGHT_PUPIL);
        String pupilColor = EyeData.getRandomPupilColor();
        EyeData.clearScleraColor(player);
        EyeData.setPupilColors(player, pupilColor);
        sync(player);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Eyes reset to defaults (white sclera, pupils "
                        + pupilColor + ")."), false);
        return 1;
    }

    private static boolean isClearKeyword(String value) {
        String trimmed = value.trim();
        return trimmed.equalsIgnoreCase("reset") || trimmed.equalsIgnoreCase("clear")
                || trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("default");
    }

    private static int invalidColor(CommandContext<CommandSourceStack> ctx, String value) {
        ctx.getSource().sendFailure(Component.literal(
                "Invalid colour: " + value + ". Use #RRGGBB (e.g. #FF0000), "
                        + "0xRRGGBB, or a vanilla name such as red, dark_red, gold, aqua."));
        return 0;
    }

    private static String requireTextureKey(CommandContext<CommandSourceStack> ctx, String key) {
        if (EyeData.isValidTextureKey(key)) {
            return EyeData.sanitizeTextureKey(key);
        }
        ctx.getSource().sendFailure(Component.literal(
                "Unknown eye texture: " + key + ". Textures live in "
                        + "assets/warriors/textures/entity/cat_player/ "
                        + "(e.g. scelera, left_pupil, right_pupil). "
                        + "To tint with a colour use red or #FF0000 instead."));
        return null;
    }

    private static void sync(ServerPlayer player) {
        WarriorsNetwork.syncAppearance(player);
    }

    private ServerPlayer getPlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("This command can only be run by a player."));
            return null;
        }
    }
}
