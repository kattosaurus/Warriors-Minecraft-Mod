package com.katt.warriors.entity;

import com.katt.warriors.data.CatName;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Shows a player's warrior name instead of their account name: above their
 * head, in chat and in death messages. Runs on both sides; the client gets the
 * name through {@code SyncAppearancePacket}.
 */
public class CatNameHandler {

    @SubscribeEvent
    public void onNameFormat(PlayerEvent.NameFormat event) {
        String name = CatName.full(event.getEntity());
        if (!name.isEmpty()) {
            event.setDisplayname(Component.literal(name));
        }
    }
}