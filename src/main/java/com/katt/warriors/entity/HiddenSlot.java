package com.katt.warriors.entity;

import com.katt.warriors.data.RebirthChoice;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Stands in for one of the player inventory's slots and takes it away for cats
 * in realistic mode: it is not drawn, cannot be clicked and accepts nothing.
 * For everyone else it behaves exactly like the slot it replaces.
 */
public class HiddenSlot extends Slot {

    private final Slot original;
    private final Player player;

    public HiddenSlot(Slot original, Player player) {
        super(original.container, original.getContainerSlot(), original.x, original.y);
        this.original = original;
        this.player = player;
        // Keep the slot's position in the menu.
        this.index = original.index;
    }

    /** Whether this player's inventory (and armor) has been taken away. */
    public static boolean removed(Player player) {
        return RebirthChoice.getMode(player) == RebirthChoice.Mode.REALISTIC;
    }

    @Override
    public boolean isActive() {
        return !removed(player) && original.isActive();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !removed(player) && original.mayPlace(stack);
    }

    @Override
    public boolean mayPickup(Player other) {
        return !removed(player) && original.mayPickup(other);
    }

    @Override
    public int getMaxStackSize() {
        return original.getMaxStackSize();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return original.getMaxStackSize(stack);
    }

    /** Armor slots do their equip handling in {@code set}, so hand it to the original. */
    @Override
    public void set(ItemStack stack) {
        original.set(stack);
    }

    @Override
    public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        return original.getNoItemIcon();
    }
}