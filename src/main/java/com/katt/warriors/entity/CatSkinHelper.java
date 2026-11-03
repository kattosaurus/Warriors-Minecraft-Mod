package com.katt.warriors.entity;

import com.katt.warriors.Warriors;
import com.katt.warriors.data.PeltData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the texture for the custom cat-player model.
 *
 * <p>The model is UV-mapped for the Blockbench layout, so it must be drawn with
 * the matching textures under
 * {@code assets/warriors/textures/entity/cat_player/<variant>.png}.</p>
 *
 * <p><b>Adding a variant:</b> drop {@code <name>.png} into that folder and add
 * {@code <name>} to {@link CatPlayerHandler#VARIANT_NAMES}. Nothing else is
 * needed. A variant whose texture file does not exist yet quietly falls back
 * to the orange coat, so names can be listed before the art is finished.</p>
 *
 * <p>Players with a stored pelt colour (realistic mode / custom look) always use
 * the plain white coat, which the renderer tints.</p>
 */
public final class CatSkinHelper {

    private CatSkinHelper() {}

    private static final String TEXTURE_DIR = "textures/entity/cat_player/";

    private static final ResourceLocation WHITE = variantLocation("white");
    private static final ResourceLocation ORANGE = variantLocation("orange");

    /** Whether each variant texture exists (checked lazily, cleared on resource reload). */
    private static final Map<ResourceLocation, Boolean> EXISTS = new ConcurrentHashMap<>();

    /** The texture and RGB tint a player's coat is drawn with. */
    public record Coat(ResourceLocation texture, float[] tint) {}

    /**
     * Resolves everything about how this player's body is drawn:
     * <ul>
     *   <li>no stored pelt colour: a painted variant texture with a subtle
     *       per-player tint variation;</li>
     *   <li>pelt colour and a pattern: the painted pattern texture, softly
     *       tinted by the pelt colour;</li>
     *   <li>pelt colour only: the plain white coat tinted by it.</li>
     * </ul>
     */
    public static Coat resolve(Player player) {
        String pelt = PeltData.getPeltColor(player);
        if (pelt == null) {
            return new Coat(forVariant(getVariantName(player)),
                    PeltData.variationTint(player.getUUID()));
        }

        int rgb = com.katt.warriors.data.EyeData.parseColor(pelt);

        ResourceLocation pattern = patternTexture(PeltData.getPattern(player));
        if (pattern != null && rgb != -1) {
            return new Coat(pattern, PeltData.softTint(rgb));
        }

        float[] tint = PeltData.tint(pelt);
        return new Coat(WHITE, tint == null ? new float[] {1F, 1F, 1F} : tint);
    }

    /** Texture of a pattern, or {@code null} for solid / unknown / not yet painted. */
    public static ResourceLocation patternTexture(String pattern) {
        if (pattern == null || !PeltData.isPattern(pattern)) {
            return null;
        }
        ResourceLocation location = variantLocation(pattern.toLowerCase(Locale.ROOT));
        return exists(location) ? location : null;
    }

    public static ResourceLocation getTexture(Player player) {
        // A stored pelt colour always uses the plain white coat, which is then
        // tinted by the renderer. This gives every realistic / custom pelt a
        // single, consistent texture.
        if (PeltData.getPeltColor(player) != null) {
            return WHITE;
        }
        return forVariant(getVariantName(player));
    }

    /** Texture for a variant name, falling back to orange for unknown / unpainted ones. */
    public static ResourceLocation forVariant(String variant) {
        if (variant == null || variant.isEmpty()) {
            return ORANGE;
        }
        String name = variant.toLowerCase(Locale.ROOT);
        // Only plain a-z / 0-9 / _ names are valid file names; anything else
        // (e.g. a stale value from an older build) is treated as unknown.
        if (!name.matches("[a-z0-9_]+")) {
            return ORANGE;
        }
        ResourceLocation location = variantLocation(name);
        return exists(location) ? location : ORANGE;
    }

    private static boolean exists(ResourceLocation location) {
        return EXISTS.computeIfAbsent(location, key ->
                Minecraft.getInstance().getResourceManager().getResource(key).isPresent());
    }

    /** Called on resource reload so newly added textures are picked up. */
    public static void clearCache() {
        EXISTS.clear();
    }

    private static ResourceLocation variantLocation(String name) {
        return new ResourceLocation(Warriors.MODID, TEXTURE_DIR + name + ".png");
    }

    /** The untinted coat used for pelt-colour tinting. */
    public static ResourceLocation getWhiteTexture() {
        return WHITE;
    }

    /** Variant name stored in the player's persistent data by CatPlayerHandler. */
    public static String getVariantName(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(CatPlayerHandler.NBT_CAT_VARIANT)
                ? data.getString(CatPlayerHandler.NBT_CAT_VARIANT)
                : "tabby";
    }
}