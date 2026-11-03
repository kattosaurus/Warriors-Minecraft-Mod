package com.katt.warriors.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.SleepingLocationCheckEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class MossSleepHandler {

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.isSleeping()
                || player.isShiftKeyDown()
                || !player.getMainHandItem().isEmpty()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos bed = bedAt(level, event.getPos());
        if (bed == null) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (level.isDay()) {
            player.displayClientMessage(Component.translatable("block.minecraft.bed.no_sleep"), true);
            return;
        }
        AABB around = new AABB(bed.getX() - 8, bed.getY() - 5, bed.getZ() - 8,
                bed.getX() + 9, bed.getY() + 6, bed.getZ() + 9);
        if (!level.getEntitiesOfClass(Monster.class, around,
                monster -> monster.isPreventingPlayerRest(player)).isEmpty()) {
            player.displayClientMessage(Component.translatable("block.minecraft.bed.not_safe"), true);
            return;
        }

        player.stopSleepInBed(true, false);
        player.startSleeping(bed);

        double top = bed.getY() + (level.getBlockState(bed).is(Blocks.MOSS_BLOCK) ? 1.0D : 0.0625D);
        player.setPos(bed.getX() + 0.5D, top + 0.05D, bed.getZ() + 0.5D);
        player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        level.updateSleepingPlayerList();
    }

    @SubscribeEvent
    public void onSleepingLocationCheck(SleepingLocationCheckEvent event) {
        BlockState state = event.getEntity().level().getBlockState(event.getSleepingLocation());
        if (isMoss(state)) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || !player.isSleeping()
                || !player.isShiftKeyDown()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        boolean onMoss = player.getSleepingPos()
                .filter(pos -> isMoss(level.getBlockState(pos)))
                .isPresent();
        if (onMoss) {
            player.stopSleepInBed(true, true);
        }
    }

    private static BlockPos bedAt(ServerLevel level, BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        BlockPos bed;
        if (state.is(Blocks.MOSS_CARPET)) {
            bed = clicked;
        } else if (state.is(Blocks.MOSS_BLOCK)) {
            BlockPos above = clicked.above();
            bed = level.getBlockState(above).is(Blocks.MOSS_CARPET) ? above : clicked;
        } else {
            return null;
        }
        BlockPos head = bed.above();
        return level.getBlockState(head).getCollisionShape(level, head).isEmpty() ? bed : null;
    }

    private static boolean isMoss(BlockState state) {
        return state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MOSS_CARPET);
    }
}
