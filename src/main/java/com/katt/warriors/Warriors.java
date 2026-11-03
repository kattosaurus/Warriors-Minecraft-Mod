package com.katt.warriors;

import com.katt.warriors.client.CatModel;
import com.katt.warriors.client.ClanCatNpcRenderer;
import com.katt.warriors.client.CatPlayerRenderHandler;
import com.katt.warriors.client.InventoryScreenHandler;
import com.katt.warriors.client.cutscene.IntroCutsceneClient;
import com.katt.warriors.command.CutsceneCommands;
import com.katt.warriors.command.EyeCommands;
import com.katt.warriors.cutscene.IntroCutsceneHandler;
import com.katt.warriors.entity.CatInventoryHandler;
import com.katt.warriors.entity.CatPlayerHandler;
import com.katt.warriors.entity.ClanConfigHandler;
import com.katt.warriors.entity.MossSleepHandler;
import com.katt.warriors.entity.ScentHandler;
import com.katt.warriors.entity.CatSkinHelper;
import com.katt.warriors.network.WarriorsNetwork;
import com.katt.warriors.npc.ClanCatNpc;
import com.katt.warriors.npc.ClanCatNpcRegistry;
import com.katt.warriors.npc.ClanCatSpawner;
import com.katt.warriors.entity.CatNameHandler;
import com.katt.warriors.npc.DenLabelPlacer;
import com.katt.warriors.worldgen.ClanlandsBiomeSource;
import com.katt.warriors.worldgen.ClanlandsChunkGenerator;
import com.katt.warriors.worldgen.RealisticSpawnHandler;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

@Mod(Warriors.MODID)
public class Warriors
{
    public static final String MODID = "warriors";
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item>  ITEMS  = DeferredRegister.create(ForgeRegistries.ITEMS,  MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** Custom worldgen codecs for the {@code warriors:clanlands} dimension. */
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);
    public static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, MODID);

    public Warriors(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        CHUNK_GENERATORS.register("clanlands", () -> ClanlandsChunkGenerator.CODEC);
        BIOME_SOURCES.register("clanlands", () -> ClanlandsBiomeSource.CODEC);
        CHUNK_GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);

        // NPC entity type registration
        ClanCatNpcRegistry.ENTITY_TYPES.register(modEventBus);

        // Register entity attributes on the mod bus
        modEventBus.addListener(this::onEntityAttributeCreation);

        MinecraftForge.EVENT_BUS.register(this);

        // Server-side: cat stats + eye commands
        MinecraftForge.EVENT_BUS.register(new CatPlayerHandler());
        MinecraftForge.EVENT_BUS.register(new EyeCommands());

        // Warrior names (Prefix + suffix) shown instead of account names
        MinecraftForge.EVENT_BUS.register(new CatNameHandler());

        // No inventory or armor for cats in realistic mode (hotbar and crafting stay)
        MinecraftForge.EVENT_BUS.register(new CatInventoryHandler());

        // Sleeping on moss blocks and carpets (the nests in the camps' dens)
        MinecraftForge.EVENT_BUS.register(new MossSleepHandler());

        // Server-side: the world's clan layout (names, terrain, number of clans)
        MinecraftForge.EVENT_BUS.register(new ClanConfigHandler());

        // Server-side: scents of the clanlands territories
        MinecraftForge.EVENT_BUS.register(new ScentHandler());

        // Server-side: first-join cutscene + its commands
        MinecraftForge.EVENT_BUS.register(new IntroCutsceneHandler());
        MinecraftForge.EVENT_BUS.register(new CutsceneCommands());

        // Server-side: peaceful clanlands (no hostile mobs)
        MinecraftForge.EVENT_BUS.register(new RealisticSpawnHandler());

        // Server-side: clan cat NPC spawning
        MinecraftForge.EVENT_BUS.register(new ClanCatSpawner());

        // Server-side: den labels (armor stands above each den entrance)
        MinecraftForge.EVENT_BUS.register(new DenLabelPlacer());
    }

    /** Register attributes for our custom mob before the entity is loaded. */
    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ClanCatNpcRegistry.CLAN_CAT_NPC.get(), ClanCatNpc.createAttributes().build());
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Cosmetic sync channel (cat coat + eye customisation).
        event.enqueueWork(WarriorsNetwork::register);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        /** Register our custom cat model layer so EntityModelSet can bake it. */
        @SubscribeEvent
        public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event)
        {
            event.registerLayerDefinition(CatModel.LAYER_LOCATION, CatModel::createBodyLayer);
        }

        /** Register the renderer for clan cat NPCs. */
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
        {
            event.registerEntityRenderer(ClanCatNpcRegistry.CLAN_CAT_NPC.get(),
                    ClanCatNpcRenderer::new);
        }

        /** Re-check which cat variant textures exist whenever resources reload. */
        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event)
        {
            event.registerReloadListener(
                    (ResourceManagerReloadListener) manager -> CatSkinHelper.clearCache());
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Register the client-side cat-renderer handler
            MinecraftForge.EVENT_BUS.register(new CatPlayerRenderHandler());
            MinecraftForge.EVENT_BUS.register(new InventoryScreenHandler());

            // Cutscene opener (waits for the world to finish loading)
            MinecraftForge.EVENT_BUS.register(new IntroCutsceneClient());
        }
    }
}