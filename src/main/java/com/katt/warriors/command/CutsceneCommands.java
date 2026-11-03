package com.katt.warriors.command;

import com.katt.warriors.cutscene.IntroCutsceneHandler;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CutsceneCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("warriors")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("intro")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    IntroCutsceneHandler.start(player, true);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable(
                                                    "command.warriors.intro.replay"), false);
                                    return 1;
                                })
                                .then(Commands.literal("reset")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            IntroCutsceneHandler.reset(player);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.translatable(
                                                            "command.warriors.intro.reset"),
                                                    false);
                                            return 1;
                                        })
                                        .then(Commands.literal("all")
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    IntroCutsceneHandler.resetAll(player);
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.translatable(
                                                                    "command.warriors.intro.reset_all"),
                                                            true);
                                                    return 1;
                                                })))
                        )
        );
    }
}
