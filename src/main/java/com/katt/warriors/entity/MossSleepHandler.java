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

/**
 * Lets players sleep on moss blocks and moss carpets, the bedding of the clan
 * camps' dens, without a bed.
 *
 * <p>Right-click moss with an empty hand (not sneaking) at night to lie down;
 * the usual rules apply: no sleeping by day, none with monsters close by, and
 * the night passes once everyone is asleep. Sneak, or press the leave-bed
 * button, to get up.</p>
 *
 * <p>Vanilla only lets you stay asleep on a bed block, so the check that
 * wakes you up is told that moss is fine too.</p>
 */
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

        // Handled here, so nothing else reacts to the click.
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

        // Start from a fresh sleep timer, like going to a bed does.
        player.stopSleepInBed(true, false);
        player.startSleeping(bed);

        // Lie on top of the moss, not in it.
        double top = bed.getY() + (level.getBlockState(bed).is(Blocks.MOSS_BLOCK) ? 1.0D : 0.0625D);
        player.setPos(bed.getX() + 0.5D, top + 0.05D, bed.getZ() + 0.5D);
        player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        level.updateSleepingPlayerList();
    }

    /** Keeps a player asleep on moss: it counts as a valid place to be in bed. */
    @SubscribeEvent
    public void onSleepingLocationCheck(SleepingLocationCheckEvent event) {
        BlockState state = event.getEntity().level().getBlockState(event.getSleepingLocation());
        if (isMoss(state)) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    /** Sneaking gets you up from the moss. */
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

    /**
     * The block to lie on for a click on {@code clicked}: a carpet, or a moss
     * block (the carpet on top of it if there is one). {@code null} if it is
     * not moss or there is no room to lie down above it.
     */
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