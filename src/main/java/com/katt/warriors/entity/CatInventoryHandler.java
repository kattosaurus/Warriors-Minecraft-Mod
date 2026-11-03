package com.katt.warriors.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CatInventoryHandler {

    private static final int HOTBAR = 9;
    private static final int MAIN_END = 36;

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        InventoryMenu menu = player.inventoryMenu;
        for (int i = 5; i <= 35 && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!(slot instanceof HiddenSlot)) {
                menu.slots.set(i, new HiddenSlot(slot, player));
            }
        }
    }

    @SubscribeEvent
    public void onPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (!enforced(player)) {
            return;
        }
        if (!fitsInHotbar(player.getInventory(), event.getItem().getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 10 != 0
                || !enforced(player)) {
            return;
        }

        Inventory inventory = player.getInventory();
        for (int i = HOTBAR; i < MAIN_END; i++) {
            final int slot = i;
            evict(player, inventory, inventory.items.get(i), () -> inventory.items.set(slot, ItemStack.EMPTY));
        }
        for (int i = 0; i < inventory.armor.size(); i++) {
            final int slot = i;
            evict(player, inventory, inventory.armor.get(i), () -> inventory.armor.set(slot, ItemStack.EMPTY));
        }
    }

    private static boolean enforced(Player player) {
        return HiddenSlot.removed(player) && !player.isCreative() && !player.isSpectator();
    }

    private static void evict(ServerPlayer player, Inventory inventory, ItemStack stack, Runnable clear) {
        if (stack.isEmpty()) {
            return;
        }
        clear.run();
        ItemStack rest = addToHotbar(inventory, stack);
        if (!rest.isEmpty()) {
            ItemEntity dropped = player.drop(rest, false);
            if (dropped != null) {
                dropped.setPickUpDelay(60);
            }
        }
    }

    private static ItemStack addToHotbar(Inventory inventory, ItemStack stack) {
        ItemStack remaining = stack.copy();

        for (int i = 0; i < HOTBAR && !remaining.isEmpty(); i++) {
            ItemStack existing = inventory.items.get(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, remaining)
                    && existing.getCount() < existing.getMaxStackSize()) {
                int moved = Math.min(remaining.getCount(),
                        existing.getMaxStackSize() - existing.getCount());
                existing.grow(moved);
                remaining.shrink(moved);
            }
        }
        for (int i = 0; i < HOTBAR && !remaining.isEmpty(); i++) {
            if (inventory.items.get(i).isEmpty()) {
                inventory.items.set(i, remaining);
                return ItemStack.EMPTY;
            }
        }
        return remaining;
    }

    private static boolean fitsInHotbar(Inventory inventory, ItemStack stack) {
        int room = 0;
        for (int i = 0; i < HOTBAR; i++) {
            ItemStack existing = inventory.items.get(i);
            if (existing.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(existing, stack)) {
                room += existing.getMaxStackSize() - existing.getCount();
            }
        }
        return room >= stack.getCount();
    }
}
