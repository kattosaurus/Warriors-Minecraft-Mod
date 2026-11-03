package com.katt.warriors.entity;

import com.katt.warriors.data.RebirthChoice;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class HiddenSlot extends Slot {

    private final Slot original;
    private final Player player;

    public HiddenSlot(Slot original, Player player) {
        super(original.container, original.getContainerSlot(), original.x, original.y);
        this.original = original;
        this.player = player;
        this.index = original.index;
    }

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

    @Override
    public void set(ItemStack stack) {
        original.set(stack);
    }

    @Override
    public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        return original.getNoItemIcon();
    }
}
