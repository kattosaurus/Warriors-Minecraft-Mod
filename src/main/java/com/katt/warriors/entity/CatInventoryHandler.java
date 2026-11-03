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

/**
 * Cats in realistic mode have no inventory and wear no armor: only the hotbar
 * (what the cat holds), the offhand and the 2 × 2 crafting grid are left.
 *
 * <p>The inventory screen loses the slots (see {@link HiddenSlot}); this class
 * keeps the rest of the game honest: items are only picked up if they fit in
 * the hotbar, and anything that still ends up in a removed slot (commands,
 * dispensers, loot) is moved to the hotbar or dropped.</p>
 */
public class CatInventoryHandler {

    private static final int HOTBAR = 9;
    private static final int MAIN_END = 36;

    /** Swaps the removable slots of every player's inventory menu (client and server). */
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        InventoryMenu menu = player.inventoryMenu;
        // 5-8: armor, 9-35: the inventory. 0-4 (crafting), 36-44 (hotbar) and 45 (offhand) stay.
        for (int i = 5; i <= 35 && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!(slot instanceof HiddenSlot)) {
                menu.slots.set(i, new HiddenSlot(slot, player));
            }
        }
    }

    /** Only pick things up if they fit in the hotbar. */
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

    /** Anything that still lands in a removed slot is moved or dropped. */
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
                // Long enough for the cat to walk off, so it isn't picked straight back up.
                dropped.setPickUpDelay(60);
            }
        }
    }

    /** Puts as much of the stack as fits into the hotbar; returns what is left. */
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