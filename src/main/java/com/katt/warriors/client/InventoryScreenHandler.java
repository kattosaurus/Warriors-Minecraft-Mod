package com.katt.warriors.client;

import com.katt.warriors.Warriors;
import com.katt.warriors.entity.HiddenSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Opens a custom survival inventory screen for cats in realistic mode.
 * Vanilla's inventory.png is never drawn; only
 * assets/warriors/textures/gui/inventory.png is.
 */
public class InventoryScreenHandler {

    private static final ResourceLocation REALISTIC_INVENTORY =
            new ResourceLocation(Warriors.MODID, "textures/gui/inventory.png");

    @SubscribeEvent
    public void onOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof InventoryScreen)
                || event.getNewScreen() instanceof RealisticInventoryScreen) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player != null && HiddenSlot.removed(player)) {
            event.setNewScreen(new RealisticInventoryScreen(player));
        }
    }

    private static final class RealisticInventoryScreen extends InventoryScreen {

        private RealisticInventoryScreen(Player player) {
            super(player);
        }

        @Override
        protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
            int left = getGuiLeft();
            int top = getGuiTop();

            // Same call vanilla makes, just with our texture (256 x 256 file).
            g.blit(REALISTIC_INVENTORY, left, top, 0, 0, imageWidth, imageHeight);

            // Player model, same position and scale as vanilla (1.20.1).
            int x = left + 51;
            int y = top + 75;
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, x, y, 30, (float) (x - mouseX), (float) (y - 50 - mouseY), minecraft.player);
        }
    }
}