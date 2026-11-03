package com.katt.warriors.npc;

import com.katt.warriors.Warriors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers the {@link ClanCatNpc} entity type with Forge.
 */
public final class ClanCatNpcRegistry {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Warriors.MODID);

    public static final RegistryObject<EntityType<ClanCatNpc>> CLAN_CAT_NPC =
            ENTITY_TYPES.register("clan_cat_npc", () ->
                    EntityType.Builder.<ClanCatNpc>of(ClanCatNpc::new, MobCategory.CREATURE)
                            .sized(0.6f, 0.7f)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("warriors:clan_cat_npc"));

    private ClanCatNpcRegistry() {}
}
