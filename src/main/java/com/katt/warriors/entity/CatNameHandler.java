package com.katt.warriors.entity;

import com.katt.warriors.data.CatName;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CatNameHandler {

    @SubscribeEvent
    public void onNameFormat(PlayerEvent.NameFormat event) {
        String name = CatName.full(event.getEntity());
        if (!name.isEmpty()) {
            event.setDisplayname(Component.literal(name));
        }
    }
}
