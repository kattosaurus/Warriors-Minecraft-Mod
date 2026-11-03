package com.katt.warriors.data;

import com.katt.warriors.data.RebirthChoice.Clan;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class ClanNames {

    private ClanNames() {}

    public static MutableComponent display(ClanConfig config, Clan clan) {
        ClanConfig.Slot slot = config.slot(clan);
        MutableComponent name = slot.name.isEmpty()
                ? Component.translatable(clan.translationKey())
                : Component.literal(slot.name);
        return name.withStyle(Style.EMPTY.withColor(
                TextColor.fromRgb(ClanConfig.PALETTE[slot.colour])));
    }

    public static MutableComponent plain(ClanConfig config, Clan clan) {
        ClanConfig.Slot slot = config.slot(clan);
        return slot.name.isEmpty()
                ? Component.translatable(clan.translationKey())
                : Component.literal(slot.name);
    }
}
