package com.katt.warriors.data;

import com.katt.warriors.data.RebirthChoice.Clan;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** How a clan is called, in its own colour. */
public final class ClanNames {

    private ClanNames() {}

    /** The clan's display name: the custom one, or the translated default. */
    public static MutableComponent display(ClanConfig config, Clan clan) {
        ClanConfig.Slot slot = config.slot(clan);
        MutableComponent name = slot.name.isEmpty()
                ? Component.translatable(clan.translationKey())
                : Component.literal(slot.name);
        return name.withStyle(Style.EMPTY.withColor(
                TextColor.fromRgb(ClanConfig.PALETTE[slot.colour])));
    }

    /** The same name without a colour (for places that colour the text themselves). */
    public static MutableComponent plain(ClanConfig config, Clan clan) {
        ClanConfig.Slot slot = config.slot(clan);
        return slot.name.isEmpty()
                ? Component.translatable(clan.translationKey())
                : Component.literal(slot.name);
    }
}