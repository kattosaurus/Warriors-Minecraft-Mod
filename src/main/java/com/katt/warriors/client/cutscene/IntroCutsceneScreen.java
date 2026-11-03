package com.katt.warriors.client.cutscene;

import com.katt.warriors.Warriors;
import com.katt.warriors.client.ClientClanConfig;
import com.katt.warriors.data.ClanConfig;
import com.katt.warriors.data.ClanNames;
import com.katt.warriors.data.Terrain;
import com.katt.warriors.network.ClanConfigUpdatePacket;
import com.katt.warriors.data.AppearanceNames;
import com.katt.warriors.data.EyeData;
import com.katt.warriors.data.PeltData;
import com.katt.warriors.data.RealisticAppearance;
import com.katt.warriors.data.RebirthChoice;
import com.katt.warriors.data.StylizedAppearance;
import com.katt.warriors.npc.ClanCatNames;
import com.katt.warriors.network.IntroFinishedPacket;
import com.katt.warriors.network.RebirthChoicePacket;
import com.katt.warriors.network.WarriorsNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class IntroCutsceneScreen extends Screen {

    private record Cue(float time, SoundEvent sound, float pitch, float volume) {}

    private static final List<Cue> CUES = List.of(
            new Cue(0.4F, SoundEvents.AMBIENT_CAVE.value(), 0.55F, 0.45F),
            new Cue(5.2F, SoundEvents.AMETHYST_BLOCK_CHIME, 1.35F, 0.16F),
            new Cue(11.4F, SoundEvents.AMETHYST_BLOCK_CHIME, 1.05F, 0.13F),
            new Cue(18.7F, SoundEvents.AMBIENT_CAVE.value(), 0.42F, 0.5F),
            new Cue(CutsceneScript.CANDLE_IGNITE, SoundEvents.FLINTANDSTEEL_USE, 0.75F, 0.55F),
            new Cue(CutsceneScript.CANDLE_IGNITE + 0.1F, SoundEvents.CANDLE_AMBIENT, 1.0F, 0.45F),
            new Cue(CutsceneScript.CANDLE_IGNITE + 0.2F, SoundEvents.FIRE_AMBIENT, 0.9F, 0.3F),
            new Cue(39.8F, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.1F),
            new Cue(47.6F, SoundEvents.AMBIENT_CAVE.value(), 0.7F, 0.28F),
            new Cue(CutsceneScript.END_FADE_START, SoundEvents.CANDLE_EXTINGUISH, 0.9F, 0.45F)
    );

    private static final List<CutsceneScript.Line> LINES = buildLineSequence();

    private static List<CutsceneScript.Line> buildLineSequence() {
        List<CutsceneScript.Line> lines = new ArrayList<>(
                CutsceneScript.MONOLOGUE.size() + CutsceneScript.DIALOGUE.size());
        lines.addAll(CutsceneScript.MONOLOGUE);
        lines.addAll(CutsceneScript.DIALOGUE);
        return List.copyOf(lines);
    }

    private static final int TEXT_NARRATOR = 0xFFFFFF;
    private static final int TEXT_PLAYER = 0xBFD0E6;
    private static final int TEXT_CAT = 0xF2D8A2;

    private static final int VEIL         = 0x8C0D0F13;
    private static final int BORDER       = 0xFF34383F;
    private static final int ACCENT       = 0xFFC8A86A;
    private static final int TEXT         = 0xFFEDEAE3;
    private static final int MUTED        = 0xFF9A9DA3;
    private static final int DESC_TEXT    = 0xFFE3E0D8;
    private static final int CARD_BG      = 0xFF131519;
    private static final int CARD_DESC    = 0xFFA9ACB3;
    private static final int CARD_GAP     = 10;
    private static final int CARD_PAD     = 8;
    private static final int CARD_MIN_H   = 64;
    private static final int HOVER_TINT   = 0x1FFFFFFF;
    private static final int SELECT_TINT  = 0x33C8A86A;

    private static final float MENU_DIM_Z = 100.0F;
    private static final float MENU_Z = 500.0F;

    private static final float ENDING_FADE = 2.8F;
    private static final float ENDING_TEXT_IN = 2.4F;
    private static final float ENDING_SEND = 5.4F;
    private static final float ENDING_TEXT_OUT = 6.6F;
    private static final float ENDING_REVEAL_START = 7.4F;
    private static final float ENDING_CLOSE = 9.0F;

    private static final float CLOUD_ASPECT = 1.5F;

    private final IntroSceneRenderer scene = new IntroSceneRenderer();

    private long lastFrameNanos = System.nanoTime();

    private float time;
    private float rebirthTime;
    private float animationTime;

    private int nextCue;
    private int nextDialogueCue;
    private int nextLineIndex;
    private CutsceneScript.Line activeLine;
    private boolean waitingForClick;
    private boolean finished;
    private ResourceKey<Level> dimension;

    private enum Stage {
        MODE,
        APPEARANCE,
        CUSTOMIZE,
        AGE,
        NAME,
        CLAN,
        CLAN_EDIT
    }

    private record Option(int x, int y, int w, int h, Component title, Component desc, Runnable action) {}

    private record Swatch(int x, int y, int size, String colour, Runnable action) {}

    private record RebirthLine(CutsceneScript.Line line, String text) {}

    private record Choice(Component title, Component desc, Runnable action) {}

    private final List<Option> options = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();

    private Stage stage = null;

    private RebirthChoice.Mode mode = RebirthChoice.Mode.REALISTIC;
    private RebirthChoice.Appearance appearance = RebirthChoice.Appearance.REALISTIC;
    private RebirthChoice.Clan clan = RebirthChoice.Clan.THUNDERCLAN;

    private ClanConfig clanDraft = ClanConfig.defaults();
    private int editSlot;
    private boolean nameFocused;

    private String pelt;
    private String pattern;
    private RebirthChoice.Age age = RebirthChoice.Age.ADULT;

    private String namePrefix = "";
    private String nameSuffix = "";
    private int nameFocus;
    private String leftEye;
    private String rightEye;
    private String sclera;

    private boolean sent;

    private boolean ending;
    private float endingTime;

    private final List<RebirthLine> rebirthLines = new ArrayList<>();
    private int rebirthIndex;
    private Runnable rebirthAdvance;
    private CutsceneScript.Line rebirthActive;
    private String rebirthText;

    public IntroCutsceneScreen() {
        super(Component.translatable(CutsceneScript.TITLE_KEY));
    }

    @Override
    protected void init() {
        super.init();

        this.scene.preload();

        Minecraft mc = Minecraft.getInstance();
        long handle = mc.getWindow().getWindow();
        InputConstants.grabOrReleaseMouse(handle, GLFW.GLFW_CURSOR_HIDDEN,
                mc.getWindow().getScreenWidth() / 2.0D,
                mc.getWindow().getScreenHeight() / 2.0D);
    }

    @Override
    public void removed() {
        if (!this.finished) {
            this.finished = true;
            this.sendFinished();
        }
    }

    @Override
    public void onClose() {
        this.finishAndClose();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderContents(gfx, mouseX, mouseY);

        if (!this.ending && !this.finished && Minecraft.getInstance().screen == this) {
            this.renderCursor(gfx, mouseX, mouseY);
        }
    }

    private static final ResourceLocation CURSOR_IDLE =
            new ResourceLocation(Warriors.MODID, "textures/gui/cursor_idle.png");
    private static final ResourceLocation CURSOR_HOVER =
            new ResourceLocation(Warriors.MODID, "textures/gui/cursor_hover.png");

    private static final int CURSOR_TEXELS = 16;
    private static final float CURSOR_PIXEL_SCALE = 2.0F;

    private void renderCursor(GuiGraphics gfx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        com.mojang.blaze3d.platform.Window window = mc.getWindow();

        boolean hovering = false;
        for (Option option : this.options) {
            if (inside(mouseX, mouseY, option.x(), option.y(), option.w(), option.h())) {
                hovering = true;
                break;
            }
        }
        if (!hovering) {
            for (Swatch swatch : this.swatches) {
                if (inside(mouseX, mouseY, swatch.x(), swatch.y(), swatch.size(), swatch.size())) {
                    hovering = true;
                    break;
                }
            }
        }

        double guiScale = window.getGuiScale();
        double px = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double py = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        px = Math.floor(px * guiScale) / guiScale;
        py = Math.floor(py * guiScale) / guiScale;

        float scale = CURSOR_PIXEL_SCALE / (float) guiScale;
        int hot = hovering ? CURSOR_TEXELS / 2 : 0;

        gfx.pose().pushPose();
        gfx.pose().translate((float) px, (float) py, 800.0F);
        gfx.pose().scale(scale, scale, 1.0F);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        gfx.blit(hovering ? CURSOR_HOVER : CURSOR_IDLE, -hot, -hot, 0.0F, 0.0F,
                CURSOR_TEXELS, CURSOR_TEXELS, CURSOR_TEXELS, CURSOR_TEXELS);
        gfx.flush();

        gfx.pose().popPose();
    }

    private void renderContents(GuiGraphics gfx, int mouseX, int mouseY) {
        this.advanceClock();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null
                || mc.player.isDeadOrDying()
                || (this.dimension != null && mc.level.dimension() != this.dimension)) {
            this.finishAndClose();
            if (this.finished && mc.screen == this) {
                mc.setScreen(null);
            }
            return;
        }

        if (this.time >= CutsceneScript.END) {
            this.finishAndClose();
            return;
        }

        this.updateSounds();

        String playerName = mc.player.getGameProfile().getName();

        if (this.ending) {
            if (this.updateEnding(mc)) {
                return;
            }
            this.renderEnding(gfx, playerName);
            return;
        }

        gfx.fill(0, 0, this.width, this.height, 0xFF000000);
        this.scene.render(gfx, this.time, this.animationTime, this.width, this.height, playerName);

        float black = this.blackout();
        if (black > 0.002F) {
            gfx.pose().pushPose();
            gfx.pose().translate(0.0F, 0.0F, 400.0F);
            gfx.fill(0, 0, this.width, this.height, argb(black));
            gfx.flush();
            gfx.pose().popPose();
        }

        if (this.stage != null) {
            clearDepth(gfx);

            gfx.pose().pushPose();
            gfx.pose().translate(0.0F, 0.0F, MENU_DIM_Z);
            gfx.fill(0, 0, this.width, this.height, 0x33000000);
            gfx.flush();
            gfx.pose().popPose();

            gfx.pose().pushPose();
            gfx.pose().translate(0.0F, 0.0F, MENU_Z);
            if (this.rebirthActive != null) {
                this.renderRebirthLine(gfx);
            } else {
                this.renderRebirthMenu(gfx, mouseX, mouseY);
            }
            gfx.flush();
            gfx.pose().popPose();
        } else {
            this.scene.renderForeground(gfx, this.time, this.width, this.height, playerName);
        }
    }

    private void renderRebirthMenu(GuiGraphics gfx, int mouseX, int mouseY) {
        this.options.clear();
        this.swatches.clear();

        switch (this.stage) {
            case MODE -> renderMode(gfx, mouseX, mouseY);
            case APPEARANCE -> renderAppearance(gfx, mouseX, mouseY);
            case CUSTOMIZE -> renderCustomize(gfx, mouseX, mouseY);
            case AGE -> renderAge(gfx, mouseX, mouseY);
            case NAME -> renderName(gfx, mouseX, mouseY);
            case CLAN -> renderClan(gfx, mouseX, mouseY);
            case CLAN_EDIT -> renderClanEdit(gfx, mouseX, mouseY);
        }
    }

    private void renderMode(GuiGraphics gfx, int mouseX, int mouseY) {
        int bar = IntroSceneRenderer.barHeight(this.height);
        drawTitle(gfx, Component.translatable("warriors.rebirth.mode.title"), bar + 10);
        drawSubtitle(gfx, Component.translatable("warriors.rebirth.subtitle"), bar + 22);

        renderCardChoices(gfx, mouseX, mouseY, List.of(
                new Choice(
                        Component.translatable("warriors.rebirth.mode.vanilla.name"),
                        Component.translatable("warriors.rebirth.mode.vanilla.desc"),
                        () -> chooseMode(RebirthChoice.Mode.VANILLA)),
                new Choice(
                        Component.translatable("warriors.rebirth.mode.realistic.name"),
                        Component.translatable("warriors.rebirth.mode.realistic.desc"),
                        () -> chooseMode(RebirthChoice.Mode.REALISTIC))));
    }

    private void renderAppearance(GuiGraphics gfx, int mouseX, int mouseY) {
        int bar = IntroSceneRenderer.barHeight(this.height);
        drawTitle(gfx, Component.translatable("warriors.rebirth.appearance.title"), bar + 10);
        drawSubtitle(gfx, Component.translatable("warriors.rebirth.appearance.hint"), bar + 22);

        renderCardChoices(gfx, mouseX, mouseY, List.of(
                new Choice(
                        Component.translatable("warriors.rebirth.appearance.natural.name"),
                        Component.translatable("warriors.rebirth.appearance.natural.desc"),
                        () -> chooseAppearance(RebirthChoice.Appearance.REALISTIC)),
                new Choice(
                        Component.translatable("warriors.rebirth.appearance.custom.name"),
                        Component.translatable("warriors.rebirth.appearance.custom.desc"),
                        () -> chooseAppearance(RebirthChoice.Appearance.CUSTOM))));
    }

    private void renderAge(GuiGraphics gfx, int mouseX, int mouseY) {
        int bar = IntroSceneRenderer.barHeight(this.height);
        drawTitle(gfx, Component.translatable("warriors.rebirth.age.title"), bar + 10);
        drawSubtitle(gfx, Component.translatable("warriors.rebirth.age.hint"), bar + 22);

        RebirthChoice.Age[] ages = RebirthChoice.Age.values();
        List<Choice> choices = new ArrayList<>(ages.length);
        for (RebirthChoice.Age value : ages) {
            choices.add(new Choice(
                    Component.translatable(value.translationKey() + ".name"),
                    Component.translatable(value.translationKey() + ".desc"),
                    () -> chooseAge(value)));
        }
        renderCardChoices(gfx, mouseX, mouseY, choices);
    }

    private void renderClan(GuiGraphics gfx, int mouseX, int mouseY) {
        int bar = IntroSceneRenderer.barHeight(this.height);
        drawTitle(gfx, Component.translatable("warriors.rebirth.clan.title"), bar + 10);
        drawSubtitle(gfx, Component.translatable("warriors.rebirth.clan.hint"), bar + 22);

        ClanConfig config = ClientClanConfig.config();
        List<RebirthChoice.Clan> clans = config.enabledClans();
        List<Choice> choices = new ArrayList<>(clans.size());
        for (int i = 0; i < clans.size(); i++) {
            RebirthChoice.Clan value = clans.get(i);
            Component title = Component.literal((i + 1) + "  ")
                    .append(ClanNames.display(config, value));
            choices.add(new Choice(title,
                    Component.translatable(config.slot(value).terrain.descriptionKey()),
                    () -> chooseClan(value)));
        }

        renderCardChoices(gfx, mouseX, mouseY, choices, 26);

        int buttonW = 150;
        addButton(gfx, mouseX, mouseY, (this.width - buttonW) / 2, hintY() - 24, buttonW, 16,
                Component.translatable("warriors.rebirth.clan.customize"), this::openClanEditor);
    }

    private String fixedNameSuffix() {
        return ClanCatNames.fixedSuffix(this.age);
    }

    private void openNameStage() {
        this.rollName();
        this.stage = Stage.NAME;
    }

    private void rollName() {
        java.util.Random rng = new java.util.Random();
        String fixed = this.fixedNameSuffix();
        if (fixed != null) {
            this.namePrefix = ClanCatNames.randomPrefix(rng, fixed);
            this.nameSuffix = fixed;
        } else {
            this.namePrefix = ClanCatNames.randomPrefix(rng, "");
            this.nameSuffix = ClanCatNames.randomWarriorSuffix(rng, this.namePrefix);
        }
        this.nameFocus = 0;
    }

    private void confirmName() {
        this.nameFocus = 0;
        this.stage = Stage.CLAN;
    }

    private String previewName() {
        String prefix = ClanCatNames.cleanPrefix(this.namePrefix);
        String fixed = this.fixedNameSuffix();
        String suffix = fixed != null ? fixed : ClanCatNames.cleanSuffix(this.nameSuffix);
        return (prefix.isEmpty() ? "?" : prefix) + (suffix.isEmpty() ? "?" : suffix);
    }

    private void renderName(GuiGraphics gfx, int mouseX, int mouseY) {
        int bar = IntroSceneRenderer.barHeight(this.height);
        drawTitle(gfx, Component.translatable("warriors.rebirth.name.title"), bar + 10);
        drawSubtitle(gfx, Component.translatable("warriors.rebirth.name.hint"), bar + 22);

        String fixed = this.fixedNameSuffix();
        int panelW = Math.min(this.width - 24, 300);
        int x0 = (this.width - panelW) / 2;
        int y = bar + 46;

        gfx.drawCenteredString(this.font, Component.literal(this.previewName()),
                this.width / 2, y, ACCENT);
        y += 20;

        gfx.drawString(this.font, Component.translatable("warriors.rebirth.name.prefix"),
                x0, y, MUTED, false);
        y += 11;
        drawNameInput(gfx, x0, y, panelW, 16, this.namePrefix, 1);
        y += 24;

        gfx.drawString(this.font, Component.translatable("warriors.rebirth.name.suffix"),
                x0, y, MUTED, false);
        y += 11;
        if (fixed != null) {
            gfx.fill(x0, y, x0 + panelW, y + 16, CARD_BG);
            drawPanelBorder(gfx, x0, y, panelW, 16, BORDER);
            gfx.drawString(this.font, fixed, x0 + 5, y + 4, MUTED, false);
            Component note = Component.translatable("warriors.rebirth.name.fixed");
            gfx.drawString(this.font, note, x0 + panelW - this.font.width(note) - 5, y + 4, MUTED, false);
        } else {
            drawNameInput(gfx, x0, y, panelW, 16, this.nameSuffix, 2);
        }
        y += 28;

        addButton(gfx, mouseX, mouseY, this.width / 2 - 108, y, 104, 16,
                Component.translatable("warriors.rebirth.name.random"), this::rollName);
        addButton(gfx, mouseX, mouseY, this.width / 2 + 4, y, 104, 16,
                Component.translatable("warriors.rebirth.name.confirm"), this::confirmName);
    }

    private void drawNameInput(GuiGraphics gfx, int x, int y, int w, int h, String text, int index) {
        boolean focused = this.nameFocus == index;
        gfx.fill(x, y, x + w, y + h, CARD_BG);
        drawPanelBorder(gfx, x, y, w, h, focused ? ACCENT : BORDER);

        int textY = y + (h - 8) / 2;
        if (text.isEmpty() && !focused) {
            gfx.drawString(this.font, Component.translatable("warriors.rebirth.name.empty"),
                    x + 5, textY, MUTED, false);
        } else {
            gfx.drawString(this.font, text, x + 5, textY, TEXT, false);
            if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int caretX = x + 5 + this.font.width(text);
                gfx.fill(caretX, y + 3, caretX + 1, y + h - 3, TEXT);
            }
        }
        this.options.add(new Option(x, y, w, h, null, null, () -> this.nameFocus = index));
    }

    private void typeIntoName(String typed) {
        boolean prefix = this.nameFocus == 1;
        StringBuilder text = new StringBuilder(prefix ? this.namePrefix : this.nameSuffix);
        int max = prefix ? ClanCatNames.MAX_PREFIX_LENGTH : ClanCatNames.MAX_SUFFIX_LENGTH;
        for (char c : typed.toCharArray()) {
            if (text.length() >= max) {
                break;
            }
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                text.append(c);
            }
        }
        if (prefix) {
            this.namePrefix = text.toString();
        } else {
            this.nameSuffix = text.toString();
        }
    }

    private void handleNameKey(int keyCode) {
        if (this.nameFocus != 0) {
            boolean prefix = this.nameFocus == 1;
            String current = prefix ? this.namePrefix : this.nameSuffix;
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!current.isEmpty()) {
                    current = current.substring(0, current.length() - 1);
                    if (prefix) {
                        this.namePrefix = current;
                    } else {
                        this.nameSuffix = current;
                    }
                }
            } else if (keyCode == GLFW.GLFW_KEY_TAB) {
                this.nameFocus = (prefix && this.fixedNameSuffix() == null) ? 2 : 1;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.nameFocus = 0;
            } else if (isPaste(keyCode)) {
                this.typeIntoName(Minecraft.getInstance().keyboardHandler.getClipboard());
            }
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.confirmName();
        }
    }

    private void openClanEditor() {
        this.clanDraft = ClientClanConfig.config().copy();
        this.editSlot = this.clanDraft.firstEnabled().ordinal();
        this.nameFocused = false;
        this.stage = Stage.CLAN_EDIT;
    }

    private void closeClanEditor() {
        this.nameFocused = false;
        if (ClientClanConfig.canEdit()
                && !this.clanDraft.sameSettings(ClientClanConfig.config())) {
            this.clanDraft.sanitize();
            ClientClanConfig.set(this.clanDraft.copy(), true);
            WarriorsNetwork.CHANNEL.sendToServer(new ClanConfigUpdatePacket(this.clanDraft));
        }
        this.stage = Stage.CLAN;
    }

    private void renderClanEdit(GuiGraphics gfx, int mouseX, int mouseY) {
        boolean edit = ClientClanConfig.canEdit();
        ClanConfig config = this.clanDraft;
        RebirthChoice.Clan[] all = RebirthChoice.Clan.values();
        int bar = IntroSceneRenderer.barHeight(this.height);

        drawTitle(gfx, Component.translatable("warriors.clans.title"), bar + 10);
        if (edit) {
            drawSubtitle(gfx, Component.translatable("warriors.clans.hint"), bar + 22);
        } else {
            int noteY = bar + 22;
            for (FormattedCharSequence line : this.font.split(
                    Component.translatable("warriors.clans.locked"), this.width - 40)) {
                drawCenteredSequence(gfx, line, this.width / 2, noteY, 0xFFE8A07A);
                noteY += 10;
            }
        }

        int top = bar + 40;
        int panelW = Math.min(this.width - 24, 580);
        int x0 = (this.width - panelW) / 2;
        int listW = Math.min(190, panelW / 2 - 10);
        int detailX = x0 + listW + 12;
        int detailW = panelW - listW - 12;

        Component countLabel = Component.translatable("warriors.clans.count", config.enabledCount());
        gfx.drawString(this.font, countLabel, x0, top + 3, TEXT, false);
        int stepX = x0 + this.font.width(countLabel) + 8;
        drawSmallButton(gfx, mouseX, mouseY, stepX, top, 14, 14, Component.literal("-"),
                edit && config.enabledCount() > ClanConfig.MIN_ENABLED, config::removeClan);
        drawSmallButton(gfx, mouseX, mouseY, stepX + 18, top, 14, 14, Component.literal("+"),
                edit && config.enabledCount() < all.length, config::addClan);

        int listTop = top + 22;
        int rowH = 18;
        for (int i = 0; i < all.length; i++) {
            ClanConfig.Slot slot = config.slot(i);
            int ry = listTop + i * (rowH + 2);
            boolean selected = i == this.editSlot;
            boolean hovered = inside(mouseX, mouseY, x0, ry, listW, rowH);

            gfx.fill(x0, ry, x0 + listW, ry + rowH, CARD_BG);
            if (selected) {
                gfx.fill(x0, ry, x0 + listW, ry + rowH, SELECT_TINT);
            } else if (hovered) {
                gfx.fill(x0, ry, x0 + listW, ry + rowH, HOVER_TINT);
            }
            drawPanelBorder(gfx, x0, ry, listW, rowH, selected ? ACCENT : BORDER);
            gfx.fill(x0 + 1, ry + 1, x0 + 4, ry + rowH - 1,
                    0xFF000000 | ClanConfig.PALETTE[slot.colour]);

            int boxX = x0 + 9;
            int boxY = ry + 4;
            gfx.fill(boxX, boxY, boxX + 10, boxY + 10, VEIL);
            drawPanelBorder(gfx, boxX, boxY, 10, 10, slot.enabled ? ACCENT : BORDER);
            if (slot.enabled) {
                gfx.fill(boxX + 3, boxY + 3, boxX + 7, boxY + 7, ACCENT);
            }
            final int index = i;
            if (edit) {
                this.options.add(new Option(boxX - 2, boxY - 2, 14, 14, null, null, () -> {
                    ClanConfig.Slot target = config.slot(index);
                    if (!target.enabled) {
                        target.enabled = true;
                    } else if (config.enabledCount() > ClanConfig.MIN_ENABLED) {
                        target.enabled = false;
                    }
                }));
            }
            this.options.add(new Option(x0, ry, listW, rowH, null, null, () -> {
                this.editSlot = index;
                this.nameFocused = false;
            }));

            int nameColour = slot.enabled ? TEXT : MUTED;
            Component shown = ClanNames.plain(config, all[i]);
            String terrainName = Component.translatable(slot.terrain.translationKey()).getString();
            int terrainW = this.font.width(terrainName);
            gfx.drawString(this.font, this.font.plainSubstrByWidth(shown.getString(),
                    listW - terrainW - 40), x0 + 24, ry + 5, nameColour, false);
            gfx.drawString(this.font, slot.enabled ? terrainName
                            : Component.translatable("warriors.clans.off").getString(),
                    x0 + listW - terrainW - 6, ry + 5, MUTED, false);
        }

        ClanConfig.Slot slot = config.slot(this.editSlot);
        RebirthChoice.Clan clan = all[this.editSlot];
        int y = top;

        gfx.drawString(this.font, Component.translatable("warriors.clans.name"),
                detailX, y, MUTED, false);
        y += 11;
        drawNameField(gfx, detailX, y, detailW, 16, slot, clan, edit);
        y += 24;

        gfx.drawString(this.font, Component.translatable("warriors.clans.terrain"),
                detailX, y, MUTED, false);
        y += 11;
        int bx = detailX;
        for (Terrain terrain : Terrain.choices()) {
            Component label = Component.translatable(terrain.translationKey());
            int bw = this.font.width(label) + 12;
            if (bx + bw > detailX + detailW && bx > detailX) {
                bx = detailX;
                y += 18;
            }
            drawToggleButton(gfx, mouseX, mouseY, bx, y, bw, 14, label,
                    slot.terrain == terrain, edit, () -> slot.terrain = terrain);
            bx += bw + 4;
        }
        y += 20;
        for (FormattedCharSequence line : this.font.split(
                Component.translatable(slot.terrain.descriptionKey()), detailW)) {
            gfx.drawString(this.font, line, detailX, y, CARD_DESC, false);
            y += 10;
        }
        y += 8;

        gfx.drawString(this.font, Component.translatable("warriors.clans.size"),
                detailX, y, MUTED, false);
        y += 11;
        bx = detailX;
        for (ClanConfig.Size size : ClanConfig.Size.values()) {
            Component label = Component.translatable(size.translationKey());
            int bw = this.font.width(label) + 14;
            drawToggleButton(gfx, mouseX, mouseY, bx, y, bw, 14, label,
                    slot.size == size, edit, () -> slot.size = size);
            bx += bw + 4;
        }
        y += 26;

        gfx.drawString(this.font, Component.translatable("warriors.clans.colour"),
                detailX, y, MUTED, false);
        y += 11;
        for (int c = 0; c < ClanConfig.PALETTE.length; c++) {
            int sx = detailX + c * 16;
            boolean chosen = slot.colour == c;
            int rgb = 0xFF000000 | ClanConfig.PALETTE[c];
            gfx.fill(sx - 1, y - 1, sx + 13, y + 13, chosen ? 0xFFFFF1CE : BORDER);
            gfx.fill(sx, y, sx + 12, y + 12, rgb);
            if (edit) {
                final int colourIndex = c;
                this.swatches.add(new Swatch(sx, y, 12, String.format("#%06X",
                        ClanConfig.PALETTE[c]), () -> slot.colour = colourIndex));
            }
        }

        int buttonY = this.height - bar - 30;
        if (edit) {
            addButton(gfx, mouseX, mouseY, this.width / 2 - 112, buttonY, 104, 16,
                    Component.translatable("warriors.clans.reset"), () -> {
                        this.clanDraft = ClanConfig.defaults();
                        this.nameFocused = false;
                    });
        }
        addButton(gfx, mouseX, mouseY, this.width / 2 + (edit ? 8 : -52), buttonY, 104, 16,
                Component.translatable("warriors.clans.done"), this::closeClanEditor);
    }

    private void drawSmallButton(GuiGraphics gfx, int mouseX, int mouseY, int x, int y,
                                 int w, int h, Component label, boolean active, Runnable action) {
        boolean hovered = active && inside(mouseX, mouseY, x, y, w, h);
        gfx.fill(x, y, x + w, y + h, CARD_BG);
        if (hovered) {
            gfx.fill(x, y, x + w, y + h, HOVER_TINT);
        }
        drawPanelBorder(gfx, x, y, w, h, hovered ? ACCENT : BORDER);
        gfx.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2,
                active ? TEXT : MUTED);
        if (active) {
            this.options.add(new Option(x, y, w, h, label, null, action));
        }
    }

    private void drawToggleButton(GuiGraphics gfx, int mouseX, int mouseY, int x, int y,
                                  int w, int h, Component label, boolean selected,
                                  boolean active, Runnable action) {
        boolean hovered = active && inside(mouseX, mouseY, x, y, w, h);
        gfx.fill(x, y, x + w, y + h, CARD_BG);
        if (selected) {
            gfx.fill(x, y, x + w, y + h, SELECT_TINT);
        } else if (hovered) {
            gfx.fill(x, y, x + w, y + h, HOVER_TINT);
        }
        drawPanelBorder(gfx, x, y, w, h, selected ? ACCENT : BORDER);
        gfx.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2,
                selected ? 0xFFFFF1CE : (active ? TEXT : MUTED));
        if (active) {
            this.options.add(new Option(x, y, w, h, label, null, action));
        }
    }

    private void drawNameField(GuiGraphics gfx, int x, int y, int w, int h,
                               ClanConfig.Slot slot, RebirthChoice.Clan clan, boolean edit) {
        boolean focused = this.nameFocused && edit;
        gfx.fill(x, y, x + w, y + h, CARD_BG);
        drawPanelBorder(gfx, x, y, w, h, focused ? ACCENT : BORDER);

        int textY = y + (h - 8) / 2;
        if (slot.name.isEmpty() && !focused) {
            gfx.drawString(this.font, Component.translatable(clan.translationKey()),
                    x + 5, textY, MUTED, false);
        } else {
            gfx.drawString(this.font, slot.name, x + 5, textY, TEXT, false);
            if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int caretX = x + 5 + this.font.width(slot.name);
                gfx.fill(caretX, y + 3, caretX + 1, y + h - 3, TEXT);
            }
        }
        if (edit) {
            this.options.add(new Option(x, y, w, h, null, null, () -> this.nameFocused = true));
        }
    }

    private void appendToName(String typed) {
        ClanConfig.Slot slot = this.clanDraft.slot(this.editSlot);
        StringBuilder name = new StringBuilder(slot.name);
        for (char c : typed.toCharArray()) {
            if (name.length() >= ClanConfig.MAX_NAME_LENGTH) {
                break;
            }
            if (SharedConstants.isAllowedChatCharacter(c) && c != '\u00a7') {
                name.append(c);
            }
        }
        slot.name = name.toString();
    }

    private void handleClanEditKey(int keyCode) {
        boolean edit = ClientClanConfig.canEdit();
        if (this.nameFocused && edit) {
            ClanConfig.Slot slot = this.clanDraft.slot(this.editSlot);
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!slot.name.isEmpty()) {
                    slot.name = slot.name.substring(0, slot.name.length() - 1);
                }
            } else if (keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.nameFocused = false;
            } else if (isPaste(keyCode)) {
                this.appendToName(Minecraft.getInstance().keyboardHandler.getClipboard());
            }
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.closeClanEditor();
        }
    }

    private void renderCardChoices(GuiGraphics gfx, int mouseX, int mouseY,
                                   List<Choice> choices) {
        renderCardChoices(gfx, mouseX, mouseY, choices, 0);
    }

    private void renderCardChoices(GuiGraphics gfx, int mouseX, int mouseY,
                                   List<Choice> choices, int reserve) {
        int count = choices.size();
        int top = IntroSceneRenderer.barHeight(this.height) + 36;
        int bottom = hintY() - 10 - reserve;
        int availH = Math.max(60, bottom - top);

        int margin = Math.max(12, this.width / 20);
        int availW = Math.max(100, Math.min(this.width - margin * 2, 920));
        int maxCardW = count <= 3 ? 260 : 200;

        int preferred = count <= 3 ? count : 4;
        int bestCols = 1;
        int bestW = Math.max(90, Math.min(maxCardW, availW));
        int bestH = 0;
        int bestTotal = Integer.MAX_VALUE;

        for (int cols = preferred; cols >= 1; cols--) {
            int cardW = Math.min(maxCardW, (availW - (cols - 1) * CARD_GAP) / cols);
            if (cardW < 90 && cols > 1) {
                continue;
            }
            int cardH = cardHeight(choices, cardW);
            int rows = (count + cols - 1) / cols;
            int total = rows * cardH + (rows - 1) * CARD_GAP;

            if (total < bestTotal) {
                bestTotal = total;
                bestCols = cols;
                bestW = cardW;
                bestH = cardH;
            }
            if (total <= availH) {
                bestCols = cols;
                bestW = cardW;
                bestH = cardH;
                bestTotal = total;
                break;
            }
        }

        int rows = (count + bestCols - 1) / bestCols;
        int startY = top + Math.max(0, (availH - bestTotal) / 2);

        for (int i = 0; i < count; i++) {
            int row = i / bestCols;
            int inRow = Math.min(bestCols, count - row * bestCols);
            int rowW = inRow * bestW + (inRow - 1) * CARD_GAP;
            int x = (this.width - rowW) / 2 + (i % bestCols) * (bestW + CARD_GAP);
            int y = startY + row * (bestH + CARD_GAP);

            Choice choice = choices.get(i);
            boolean hovered = inside(mouseX, mouseY, x, y, bestW, bestH);
            drawCard(gfx, x, y, bestW, bestH, choice, hovered);

            this.options.add(new Option(x, y, bestW, bestH,
                    choice.title(), choice.desc(), choice.action()));
        }

        drawMenuHint(gfx);
    }

    private int cardHeight(List<Choice> choices, int cardW) {
        int innerW = Math.max(40, cardW - CARD_PAD * 2);
        int tallest = 0;
        for (Choice choice : choices) {
            int titleH = this.font.split(choice.title(), innerW).size() * 10;
            int descH = this.font.split(choice.desc(), innerW).size() * 10;
            tallest = Math.max(tallest, CARD_PAD + titleH + 9 + descH + CARD_PAD);
        }
        return Math.max(CARD_MIN_H, tallest);
    }

    private void drawCard(GuiGraphics gfx, int x, int y, int w, int h,
                          Choice choice, boolean hovered) {
        gfx.fill(x, y, x + w, y + h, CARD_BG);
        if (hovered) {
            gfx.fill(x, y, x + w, y + h, HOVER_TINT);
        }
        drawPanelBorder(gfx, x, y, w, h, hovered ? ACCENT : BORDER);
        if (hovered) {
            gfx.fill(x, y, x + w, y + 2, ACCENT);
        }

        int innerW = Math.max(40, w - CARD_PAD * 2);
        int textX = x + CARD_PAD;
        int cursorY = y + CARD_PAD;

        for (FormattedCharSequence line : this.font.split(choice.title(), innerW)) {
            gfx.drawString(this.font, line, textX, cursorY,
                    hovered ? 0xFFFFF1CE : TEXT, false);
            cursorY += 10;
        }

        cursorY += 3;
        gfx.fill(textX, cursorY, x + w - CARD_PAD, cursorY + 1, BORDER);
        cursorY += 6;

        for (FormattedCharSequence line : this.font.split(choice.desc(), innerW)) {
            gfx.drawString(this.font, line, textX, cursorY,
                    hovered ? DESC_TEXT : CARD_DESC, false);
            cursorY += 10;
        }
    }

    private int hintY() {
        return this.height - IntroSceneRenderer.barHeight(this.height) - 13;
    }

    private void drawMenuHint(GuiGraphics gfx) {
        gfx.drawCenteredString(this.font,
                Component.translatable("warriors.rebirth.click"),
                this.width / 2, hintY(), MUTED);
    }

    private void renderCustomize(GuiGraphics gfx, int mouseX, int mouseY) {
        int bar = IntroSceneRenderer.barHeight(this.height);
        drawTitle(gfx, Component.translatable("warriors.rebirth.customize.title"), bar + 10);

        int top = bar + 28;
        int bottom = this.height - bar - 46;

        int margin = Math.max(12, this.width / 20);
        int contentW = Math.min(this.width - margin * 2, 560);
        int contentX = (this.width - contentW) / 2;

        int previewH = Mth.clamp((int) ((bottom - top) * 0.62F), 70, 190);
        int previewW = Math.min(Math.round(previewH * CLOUD_ASPECT), contentW - 150);

        gfx.fill(contentX, top, contentX + previewW, top + previewH, CARD_BG);
        drawPanelBorder(gfx, contentX, top, previewW, previewH);
        drawPreview(gfx, contentX, top, previewW, previewH);

        int swatchX = contentX + previewW + 16;
        int swatchW = contentX + contentW - swatchX;
        int cursorY = top;

        cursorY = drawSwatchSection(gfx, mouseX, mouseY, swatchX, swatchW,
                Component.translatable("warriors.rebirth.customize.pelt"),
                PeltData.CUSTOM_PELTS, cursorY, value -> this.pelt = value, this.pelt);

        cursorY = drawPatternSection(gfx, mouseX, mouseY, swatchX, swatchW, cursorY);

        cursorY = drawSwatchSection(gfx, mouseX, mouseY, swatchX, swatchW,
                Component.translatable("warriors.rebirth.customize.eye_left"),
                RealisticAppearance.EYE_COLORS, cursorY,
                value -> this.leftEye = value, this.leftEye);

        cursorY = drawSwatchSection(gfx, mouseX, mouseY, swatchX, swatchW,
                Component.translatable("warriors.rebirth.customize.eye_right"),
                RealisticAppearance.EYE_COLORS, cursorY,
                value -> this.rightEye = value, this.rightEye);

        cursorY = drawSwatchSection(gfx, mouseX, mouseY, swatchX, swatchW,
                Component.translatable("warriors.rebirth.customize.sclera"),
                RealisticAppearance.SCLERA_COLORS, cursorY,
                value -> this.sclera = value, this.sclera);

        if (cursorY + 10 < bottom) {
            gfx.drawString(this.font,
                    Component.translatable("warriors.rebirth.customize.accessories")
                            .append(": ")
                            .append(Component.translatable("warriors.rebirth.customize.accessories_locked")),
                    swatchX, cursorY + 2, MUTED, false);
        }

        int buttonY = this.height - bar - 30;
        int buttonH = 20;
        int buttonGap = 6;
        int buttonW = Math.min(92, Math.max(52, (contentW - buttonGap * 2) / 3));

        addButton(gfx, mouseX, mouseY, contentX, buttonY, buttonW, buttonH,
                Component.translatable("warriors.rebirth.customize.randomize"),
                this::randomise);

        addButton(gfx, mouseX, mouseY,
                contentX + (contentW - buttonW) / 2, buttonY, buttonW, buttonH,
                Component.translatable("warriors.rebirth.customize.back"),
                () -> this.stage = Stage.APPEARANCE);

        addButton(gfx, mouseX, mouseY,
                contentX + contentW - buttonW, buttonY, buttonW, buttonH,
                Component.translatable("warriors.rebirth.customize.continue"),
                this::startFinalAppearanceExchange);
    }

    private int drawPatternSection(GuiGraphics gfx, int mouseX, int mouseY,
                                   int x, int width, int y) {
        gfx.drawString(this.font,
                Component.translatable("warriors.rebirth.customize.pattern"),
                x, y, TEXT, false);

        int rowY = y + 11;
        int buttonH = 14;
        int buttonX = x;

        List<String> names = new ArrayList<>();
        names.add(null);
        names.addAll(List.of(PeltData.PATTERNS));

        for (String name : names) {
            Component label = Component.translatable(
                    name == null ? "warriors.pattern.solid" : "warriors.pattern." + name);
            int buttonW = this.font.width(label) + 12;
            if (buttonX + buttonW > x + width && buttonX > x) {
                buttonX = x;
                rowY += buttonH + 4;
            }

            boolean selected = java.util.Objects.equals(name, this.pattern);
            boolean hovered = inside(mouseX, mouseY, buttonX, rowY, buttonW, buttonH);

            gfx.fill(buttonX, rowY, buttonX + buttonW, rowY + buttonH, CARD_BG);
            if (selected) {
                gfx.fill(buttonX, rowY, buttonX + buttonW, rowY + buttonH, SELECT_TINT);
            } else if (hovered) {
                gfx.fill(buttonX, rowY, buttonX + buttonW, rowY + buttonH, HOVER_TINT);
            }
            drawPanelBorder(gfx, buttonX, rowY, buttonW, buttonH,
                    selected ? ACCENT : BORDER);
            gfx.drawCenteredString(this.font, label, buttonX + buttonW / 2, rowY + 3,
                    selected ? 0xFFFFF1CE : TEXT);

            final String value = name;
            this.options.add(new Option(buttonX, rowY, buttonW, buttonH, label, null,
                    () -> this.pattern = value));
            buttonX += buttonW + 4;
        }

        return rowY + buttonH + 8;
    }

    private void drawPreview(GuiGraphics gfx, int x, int y, int w, int h) {
        this.scene.renderCatPreview(gfx, x, y, w, h,
                colour(this.pelt, 0xFFB87333),
                this.pattern,
                colour(this.sclera, 0xFFEDEDE3),
                colour(this.leftEye, 0xFFC98A2E),
                colour(this.rightEye, 0xFFC98A2E),
                this.animationTime);
    }

    private int drawSwatchSection(GuiGraphics gfx, int mouseX, int mouseY,
                                  int x, int width, Component label, String[] colours,
                                  int y, java.util.function.Consumer<String> setter,
                                  String selected) {
        gfx.drawString(this.font, label, x, y, TEXT, false);
        int chipY = y + 11;

        int chipSize = 12;
        int gap = 4;
        int perRow = Math.max(1, (width + gap) / (chipSize + gap));

        for (int i = 0; i < colours.length; i++) {
            int col = i % perRow;
            int row = i / perRow;
            int chipX = x + col * (chipSize + gap);
            int chip = chipY + row * (chipSize + gap);

            int fill = colour(colours[i], 0xFFFFFFFF);
            gfx.fill(chipX, chip, chipX + chipSize, chip + chipSize, fill);

            boolean isSelected = colours[i].equalsIgnoreCase(selected);
            boolean hovered = inside(mouseX, mouseY, chipX, chip, chipSize, chipSize);
            drawPanelBorder(gfx, chipX, chip, chipSize, chipSize);
            if (isSelected) {
                gfx.fill(chipX - 1, chip - 1, chipX + chipSize + 1, chip + chipSize + 1,
                        SELECT_TINT);
                drawPanelBorder(gfx, chipX - 1, chip - 1, chipSize + 2, chipSize + 2);
            } else if (hovered) {
                gfx.fill(chipX, chip, chipX + chipSize, chip + chipSize, HOVER_TINT);
            }

            String value = colours[i];
            this.swatches.add(new Swatch(chipX, chip, chipSize, value,
                    () -> setter.accept(value)));
        }

        int rows = (colours.length + perRow - 1) / perRow;
        return chipY + rows * (chipSize + gap) + 5;
    }

    private void renderRebirthLine(GuiGraphics gfx) {
        if (this.rebirthActive == null) {
            return;
        }

        CutsceneScript.Line line = this.rebirthActive;
        float local = this.rebirthTime - line.start();
        float alpha = Mth.clamp(local / CutsceneScript.typewriterDuration(line), 0.0F, 1.0F);
        if (alpha <= 0.002F) {
            return;
        }

        String shown = typewriter(this.rebirthText, local, line);
        if (shown.isEmpty()) {
            return;
        }

        int dialogueY = IntroSceneRenderer.dialogueY(this.height);

        int base = speakerColor(line.speaker());
        Component label = Component.translatable(line.speaker() == CutsceneScript.Speaker.CAT
                ? CutsceneScript.SPEAKER_CAT_KEY
                : CutsceneScript.SPEAKER_PLAYER_KEY);

        gfx.drawCenteredString(this.font, label, this.width / 2,
                dialogueY - 17, withAlpha(base, alpha * 0.55F));
        gfx.drawCenteredString(this.font, Component.literal(shown), this.width / 2, dialogueY,
                withAlpha(base, alpha));
    }

    private void drawTitle(GuiGraphics gfx, Component text, int y) {
        drawCentered(gfx, text, y, TEXT);
    }

    private void drawSubtitle(GuiGraphics gfx, Component text, int y) {
        drawCentered(gfx, text, y, MUTED);
    }

    private void drawCentered(GuiGraphics gfx, Component text, int y, int colour) {
        gfx.drawCenteredString(this.font, text, this.width / 2, y, colour);
    }

    private void drawCenteredSequence(GuiGraphics gfx, FormattedCharSequence text,
                                      int centerX, int y, int colour) {
        gfx.drawString(this.font, text, centerX - this.font.width(text) / 2, y, colour, false);
    }

    private void addButton(GuiGraphics gfx, int mouseX, int mouseY,
                           int x, int y, int w, int h,
                           Component label, Runnable action) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);

        gfx.fill(x, y, x + w, y + h, VEIL);
        drawPanelBorder(gfx, x, y, w, h);
        if (hovered) {
            gfx.fill(x, y, x + w, y + h, HOVER_TINT);
            gfx.fill(x, y, x + w, y + 2, ACCENT);
        }
        gfx.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2, TEXT);

        this.options.add(new Option(x, y, w, h, label, null, action));
    }

    private void drawPanelBorder(GuiGraphics gfx, int x, int y, int w, int h) {
        drawPanelBorder(gfx, x, y, w, h, BORDER);
    }

    private void drawPanelBorder(GuiGraphics gfx, int x, int y, int w, int h, int colour) {
        gfx.fill(x, y, x + w, y + 1, colour);
        gfx.fill(x, y + h - 1, x + w, y + h, colour);
        gfx.fill(x, y, x + 1, y + h, colour);
        gfx.fill(x + w - 1, y, x + w, y + h, colour);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int colour(String hex, int fallback) {
        int value = EyeData.parseColor(hex);
        return value == -1 ? fallback : 0xFF000000 | (value & 0xFFFFFF);
    }

    private void chooseMode(RebirthChoice.Mode chosen) {
        this.mode = chosen;
        String key = chosen == RebirthChoice.Mode.REALISTIC
                ? CutsceneScript.CHOICE_LINE_REALISTIC
                : CutsceneScript.CHOICE_LINE_VANILLA;

        startRebirthExchange(List.of(
                        playerLine(key, Component.translatable(key).getString())),
                () -> this.stage = Stage.APPEARANCE);
    }

    private void chooseAppearance(RebirthChoice.Appearance chosen) {
        this.appearance = chosen;

        if (chosen == RebirthChoice.Appearance.CUSTOM) {
            if (this.pelt == null || this.leftEye == null) {
                this.randomise();
            }

            startRebirthExchange(List.of(
                            playerLine(CutsceneScript.LINE_CUSTOM,
                                    Component.translatable(CutsceneScript.LINE_CUSTOM).getString()),
                            catLine(CutsceneScript.LINE_CUSTOM_CAT,
                                    Component.translatable(CutsceneScript.LINE_CUSTOM_CAT).getString())),
                    () -> this.stage = Stage.CUSTOMIZE);
            return;
        }

        if (chosen == RebirthChoice.Appearance.STYLIZED) {
            StylizedAppearance.Result look = StylizedAppearance.PRESET;
            this.pelt = look.peltColor();
            this.pattern = null;
            this.leftEye = look.eyeColor();
            this.rightEye = look.eyeColor();
            this.sclera = look.scleraColor();
        } else {
            RealisticAppearance.Result rolled = RealisticAppearance.roll(RandomSource.create());
            this.pelt = rolled.peltColor();
            this.pattern = rolled.pattern();
            this.leftEye = rolled.eyeColor();
            this.rightEye = rolled.eyeColor();
            this.sclera = rolled.scleraColor();
        }

        startRebirthExchange(List.of(
                        playerLine(CutsceneScript.LINE_RANDOMIZED,
                                Component.translatable(CutsceneScript.LINE_RANDOMIZED).getString()),
                        catLine(CutsceneScript.LINE_RANDOMIZED_CAT,
                                Component.translatable(CutsceneScript.LINE_RANDOMIZED_CAT).getString())),
                this::startFinalAppearanceExchange);
    }

    private void startFinalAppearanceExchange() {
        boolean heterochromia = this.leftEye != null && this.rightEye != null
                && !this.leftEye.equalsIgnoreCase(this.rightEye);

        String key = heterochromia
                ? CutsceneScript.LINE_FINAL_HETERO
                : CutsceneScript.LINE_FINAL;

        startRebirthExchange(List.of(
                        playerLine(key, finalLineText(heterochromia)),
                        catLine(CutsceneScript.LINE_FINAL_CAT,
                                Component.translatable(CutsceneScript.LINE_FINAL_CAT).getString())),
                () -> this.stage = Stage.AGE);
    }

    private void chooseAge(RebirthChoice.Age chosen) {
        this.age = chosen;

        String catKey = CutsceneScript.LINE_AGE_CAT + "." + chosen.id();
        String phrase = Component.translatable(chosen.translationKey() + ".phrase").getString();

        startRebirthExchange(List.of(
                        playerLine(CutsceneScript.LINE_AGE,
                                Component.translatable(CutsceneScript.LINE_AGE, phrase).getString()),
                        catLine(catKey, Component.translatable(catKey).getString())),
                this::openNameStage);
    }

    private void chooseClan(RebirthChoice.Clan chosen) {
        this.clan = chosen;
        startRebirthExchange(List.of(
                        playerLine(CutsceneScript.LINE_CLAN, clanLineText()),
                        catLine(CutsceneScript.LINE_CLAN_CAT,
                                Component.translatable(CutsceneScript.LINE_CLAN_CAT).getString())),
                this::completeRebirth);
    }

    private void completeRebirth() {
        if (this.ending) {
            return;
        }
        this.ending = true;
        this.endingTime = 0.0F;
        this.options.clear();
        this.swatches.clear();

        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.CANDLE_EXTINGUISH, 0.9F, 0.5F));
    }

    private static void clearDepth(GuiGraphics gfx) {
        gfx.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);
    }

    private void sendChoices() {
        if (this.sent) {
            return;
        }
        this.sent = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            WarriorsNetwork.CHANNEL.sendToServer(new RebirthChoicePacket(
                    this.mode.id(),
                    this.appearance.id(),
                    this.clan.id(),
                    this.pelt == null ? "" : this.pelt,
                    this.leftEye == null ? "" : this.leftEye,
                    this.rightEye == null ? "" : this.rightEye,
                    this.sclera == null ? "" : this.sclera,
                    this.pattern == null ? "" : this.pattern,
                    this.age.id(),
                    this.namePrefix,
                    this.nameSuffix));
        }

        this.finished = true;
    }

    private boolean updateEnding(Minecraft mc) {
        if (!this.sent && this.endingTime >= ENDING_SEND) {
            this.sendChoices();
        }

        if (this.endingTime >= ENDING_CLOSE) {
            this.finished = true;
            if (mc.screen == this) {
                mc.setScreen(null);
            }
            return true;
        }
        return false;
    }

    private void renderEnding(GuiGraphics gfx, String playerName) {
        float t = this.endingTime;

        float fade = smooth(0.0F, ENDING_FADE, t);
        float reveal = smooth(ENDING_REVEAL_START, ENDING_CLOSE, t);

        if (t < ENDING_FADE + 0.05F) {
            gfx.fill(0, 0, this.width, this.height, 0xFF000000);
            this.scene.render(gfx, this.time, this.animationTime, this.width, this.height,
                    playerName);
        }

        clearDepth(gfx);

        float black = Math.min(fade, 1.0F - reveal);
        if (black > 0.002F) {
            gfx.pose().pushPose();
            gfx.pose().translate(0.0F, 0.0F, 450.0F);
            gfx.fill(0, 0, this.width, this.height, argb(black));
            gfx.flush();
            gfx.pose().popPose();
        }

        float textAlpha = smooth(ENDING_TEXT_IN, ENDING_TEXT_IN + 1.2F, t)
                * (1.0F - smooth(ENDING_TEXT_OUT, ENDING_TEXT_OUT + 0.8F, t));
        if (textAlpha > 0.002F) {
            Component text = Component.translatable(
                    this.mode == RebirthChoice.Mode.REALISTIC
                            ? "warriors.rebirth.arriving"
                            : "warriors.rebirth.ending.vanilla");

            gfx.pose().pushPose();
            gfx.pose().translate(0.0F, 0.0F, MENU_Z);
            int y = (int) (this.height * 0.45F);
            for (FormattedCharSequence line : this.font.split(text,
                    Math.max(60, Math.min(this.width - 80, 360)))) {
                gfx.drawString(this.font, line, this.width / 2 - this.font.width(line) / 2,
                        y, withAlpha(TEXT_NARRATOR, textAlpha), false);
                y += 12;
            }
            gfx.flush();
            gfx.pose().popPose();
        }
    }

    private void randomise() {
        RandomSource random = RandomSource.create();
        this.pelt = PeltData.CUSTOM_PELTS[random.nextInt(PeltData.CUSTOM_PELTS.length)];
        this.pattern = PeltData.rollPattern(random);
        this.leftEye = RealisticAppearance.EYE_COLORS[
                random.nextInt(RealisticAppearance.EYE_COLORS.length)];
        this.rightEye = this.leftEye;
        this.sclera = RealisticAppearance.SCLERA_COLORS[
                random.nextInt(RealisticAppearance.SCLERA_COLORS.length)];
    }

    private void beginRebirth() {
        if (this.stage != null) {
            return;
        }
        this.stage = Stage.MODE;
        this.waitingForClick = true;
        this.time = Math.max(this.time,
                CutsceneScript.DIALOGUE.get(CutsceneScript.DIALOGUE.size() - 1).end() + 0.05F);
        this.rebirthLines.clear();
        this.rebirthIndex = 0;
        this.rebirthAdvance = null;
        this.rebirthActive = null;
    }

    private RebirthLine playerLine(String key, String text) {
        return makeLine(CutsceneScript.Speaker.PLAYER, key, text);
    }

    private RebirthLine catLine(String key, String text) {
        return makeLine(CutsceneScript.Speaker.CAT, key, text);
    }

    private RebirthLine makeLine(CutsceneScript.Speaker speaker, String key, String text) {
        float duration = Mth.clamp(text.length() * 0.05F, 2.4F, 5.0F);
        return new RebirthLine(
                new CutsceneScript.Line(speaker, key, 0.0F, duration), text);
    }

    private void startRebirthExchange(List<RebirthLine> lines, Runnable onExchangeDone) {
        this.rebirthLines.clear();
        this.rebirthLines.addAll(lines);
        this.rebirthIndex = 0;
        this.rebirthAdvance = onExchangeDone;
        this.playNextRebirthLine();
    }

    private void playNextRebirthLine() {
        if (this.rebirthIndex >= this.rebirthLines.size()) {
            this.rebirthActive = null;
            this.rebirthText = null;
            this.waitingForClick = true;
            Runnable advance = this.rebirthAdvance;
            this.rebirthAdvance = null;
            if (advance != null) {
                advance.run();
            }
            return;
        }

        RebirthLine rebirthLine = this.rebirthLines.get(this.rebirthIndex++);
        this.rebirthActive = rebirthLine.line();
        this.rebirthText = rebirthLine.text();
        this.activeLine = rebirthLine.line();
        this.rebirthTime = this.rebirthActive.start();
        this.waitingForClick = false;
    }

    private String finalLineText(boolean heterochromia) {
        String peltName = AppearanceNames.pelt(this.pelt);
        if (this.pattern != null) {
            peltName = peltName + " " + Component.translatable(
                            "warriors.pattern." + this.pattern).getString()
                    .toLowerCase(java.util.Locale.ROOT);
        }

        if (heterochromia) {
            return Component.translatable(CutsceneScript.LINE_FINAL_HETERO,
                    peltName,
                    AppearanceNames.eye(this.leftEye),
                    AppearanceNames.eye(this.rightEye)).getString();
        }

        String eye = AppearanceNames.eye(this.leftEye != null ? this.leftEye : this.rightEye);
        return Component.translatable(CutsceneScript.LINE_FINAL, peltName, eye).getString();
    }

    private String clanLineText() {
        String clanName = ClanNames.plain(ClientClanConfig.config(), this.clan).getString();
        return Component.translatable(CutsceneScript.LINE_CLAN, clanName).getString();
    }

    private void advanceClock() {
        long now = System.nanoTime();
        float delta = (now - this.lastFrameNanos) / 1_000_000_000.0F;
        this.lastFrameNanos = now;
        delta = Mth.clamp(delta, 0.0F, 0.1F);

        this.animationTime += delta;

        if (this.ending) {
            this.endingTime += delta;
            return;
        }

        if (this.stage != null) {
            this.advanceRebirthClock(delta);
            return;
        }

        if (this.waitingForClick) {
            return;
        }

        float next = this.time + delta;

        if (this.activeLine != null && next >= this.activeLine.end()) {
            CutsceneScript.Line ended = this.activeLine;
            this.time = ended.end();
            this.activeLine = null;

            if (ended.speaker() != CutsceneScript.Speaker.NARRATOR) {
                this.waitingForClick = true;
                return;
            }
        }

        if (this.activeLine == null && this.nextLineIndex < LINES.size()
                && this.time >= LINES.get(this.nextLineIndex).start()) {
            this.activeLine = LINES.get(this.nextLineIndex);
            this.nextLineIndex++;
        }

        this.time = next;
    }

    private void advanceRebirthClock(float delta) {
        if (this.waitingForClick || this.rebirthActive == null) {
            return;
        }

        float next = this.rebirthTime + delta;
        if (next >= this.rebirthActive.end()) {
            this.rebirthTime = this.rebirthActive.end();
            this.waitingForClick = true;
        } else {
            this.rebirthTime = next;
        }
    }

    private void advanceOnClick() {
        if (this.waitingForClick) {
            this.releaseLine();
            return;
        }

        if (this.activeLine == null) {
            return;
        }

        if (this.activeLine.speaker() == CutsceneScript.Speaker.NARRATOR) {
            float typedFor = this.time - this.activeLine.start();
            if (typedFor < CutsceneScript.typewriterDuration(this.activeLine)) {
                this.time = this.activeLine.start()
                        + CutsceneScript.typewriterDuration(this.activeLine);
            }
            return;
        }

        float typedFor = this.time - this.activeLine.start();
        if (typedFor < CutsceneScript.typewriterDuration(this.activeLine)) {
            this.time = this.activeLine.start() + CutsceneScript.typewriterDuration(this.activeLine);
        } else {
            this.time = this.activeLine.end();
            this.releaseLine();
        }
    }

    private void advanceRebirthOnClick() {
        if (this.rebirthActive == null) {
            return;
        }

        if (this.waitingForClick) {
            this.waitingForClick = false;
            this.playNextRebirthLine();
            return;
        }

        float typedFor = this.rebirthTime - this.rebirthActive.start();
        if (typedFor < CutsceneScript.typewriterDuration(this.rebirthActive)) {
            this.rebirthTime = this.rebirthActive.start()
                    + CutsceneScript.typewriterDuration(this.rebirthActive);
        } else {
            this.rebirthTime = this.rebirthActive.end();
            this.waitingForClick = false;
            this.playNextRebirthLine();
        }
    }

    private void releaseLine() {
        this.waitingForClick = false;
        this.activeLine = null;

        if (this.rebirthActive != null) {
            this.playNextRebirthLine();
            return;
        }

        if (this.nextLineIndex >= LINES.size()) {
            this.beginRebirth();
            return;
        }

        if (this.nextLineIndex != CutsceneScript.MONOLOGUE.size()) {
            this.time = LINES.get(this.nextLineIndex).start();
        }
    }

    private static final float DIALOGUE_SOUND_VOLUME = 0.25F;

    private void updateSounds() {
        if (this.stage != null) {
            return;
        }

        if (this.waitingForClick) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        while (this.nextCue < CUES.size() && this.time >= CUES.get(this.nextCue).time()) {
            Cue cue = CUES.get(this.nextCue++);
            mc.getSoundManager().play(
                    SimpleSoundInstance.forUI(cue.sound(), cue.pitch(), cue.volume()));
        }

        while (this.nextDialogueCue < CutsceneScript.DIALOGUE.size()
                && this.time >= CutsceneScript.DIALOGUE.get(this.nextDialogueCue).start()) {
            CutsceneScript.Line line = CutsceneScript.DIALOGUE.get(this.nextDialogueCue++);
            boolean cat = line.speaker() == CutsceneScript.Speaker.CAT;

            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    cat ? SoundEvents.CAT_PURREOW : SoundEvents.CAT_AMBIENT,
                    cat ? 0.9F : 1.1F,
                    DIALOGUE_SOUND_VOLUME));
        }
    }

    private void finishAndClose() {
        if (this.finished) {
            return;
        }
        this.finished = true;
        this.sendFinished();

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == this) {
            mc.setScreen(null);
        }
    }

    private void sendFinished() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            WarriorsNetwork.CHANNEL.sendToServer(IntroFinishedPacket.INSTANCE);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.stage != null) {
            return this.handleMenuKey(keyCode, scanCode, modifiers);
        }
        return true;
    }

    private boolean handleMenuKey(int keyCode, int scanCode, int modifiers) {
        if (this.ending) {
            return true;
        }
        if (this.rebirthActive != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_SPACE) {
                this.advanceRebirthOnClick();
            }
            return true;
        }

        switch (this.stage) {
            case MODE -> {
                if (keyCode == GLFW.GLFW_KEY_1) this.chooseMode(RebirthChoice.Mode.VANILLA);
                if (keyCode == GLFW.GLFW_KEY_2) this.chooseMode(RebirthChoice.Mode.REALISTIC);
            }
            case APPEARANCE -> {
                if (keyCode == GLFW.GLFW_KEY_1) {
                    this.chooseAppearance(RebirthChoice.Appearance.REALISTIC);
                }
                if (keyCode == GLFW.GLFW_KEY_2) {
                    this.chooseAppearance(RebirthChoice.Appearance.CUSTOM);
                }
            }
            case CUSTOMIZE -> {
                if (keyCode == GLFW.GLFW_KEY_ENTER
                        || keyCode == GLFW.GLFW_KEY_KP_ENTER
                        || keyCode == GLFW.GLFW_KEY_SPACE) {
                    this.startFinalAppearanceExchange();
                }
            }
            case AGE -> {
                int index = keyCode - GLFW.GLFW_KEY_1;
                RebirthChoice.Age[] ages = RebirthChoice.Age.values();
                if (index >= 0 && index < ages.length) {
                    this.chooseAge(ages[index]);
                }
            }
            case CLAN -> {
                int index = keyCode - GLFW.GLFW_KEY_1;
                List<RebirthChoice.Clan> clans = ClientClanConfig.config().enabledClans();
                if (index >= 0 && index < clans.size()) {
                    this.chooseClan(clans.get(index));
                }
            }
            case NAME -> this.handleNameKey(keyCode);
            case CLAN_EDIT -> this.handleClanEditKey(keyCode);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.stage != null) {
            return this.handleMenuMouse(mouseX, mouseY, button);
        }
        if (button != 0) {
            return true;
        }

        this.advanceOnClick();
        return true;
    }

    private boolean handleMenuMouse(double mouseX, double mouseY, int button) {
        if (this.ending || button != 0) {
            return true;
        }

        if (this.rebirthActive != null) {
            this.advanceRebirthOnClick();
            return true;
        }

        if (this.stage == Stage.CLAN_EDIT) {
            this.nameFocused = false;
        }
        if (this.stage == Stage.NAME) {
            this.nameFocus = 0;
        }

        for (Swatch swatch : this.swatches) {
            if (inside(mouseX, mouseY, swatch.x(), swatch.y(),
                    swatch.size(), swatch.size())) {
                swatch.action().run();
                return true;
            }
        }

        for (Option option : this.options) {
            if (inside(mouseX, mouseY, option.x(), option.y(),
                    option.w(), option.h())) {
                option.action().run();
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.stage == Stage.CLAN_EDIT && this.nameFocused && ClientClanConfig.canEdit()) {
            this.appendToName(String.valueOf(codePoint));
        }
        if (this.stage == Stage.NAME && this.nameFocus != 0
                && this.rebirthActive == null && !this.ending) {
            this.typeIntoName(String.valueOf(codePoint));
        }
        return true;
    }

    private static int argb(float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return a << 24;
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | ((rgb >> 16) & 0xFF) << 16 | ((rgb >> 8) & 0xFF) << 8 | (rgb & 0xFF);
    }

    private static float smooth(float from, float to, float value) {
        if (to <= from) {
            return value >= to ? 1.0F : 0.0F;
        }
        float t = Mth.clamp((value - from) / (to - from), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static String typewriter(String text, float local, CutsceneScript.Line line) {
        if (text.isEmpty()) {
            return text;
        }
        float reveal = CutsceneScript.typewriterDuration(line);
        int count = (int) (Mth.clamp(local / reveal, 0.0F, 1.0F) * text.length());
        return text.substring(0, Math.min(count, text.length()));
    }

    private float blackout() {
        float t = this.time;

        if (t < CutsceneScript.BLACKOUT_START) {
            return 0.0F;
        }
        if (t < CutsceneScript.BLACKOUT_FULL) {
            return smooth(CutsceneScript.BLACKOUT_START, CutsceneScript.BLACKOUT_FULL, t);
        }
        if (t < CutsceneScript.CANDLE_IGNITE) {
            return 1.0F;
        }
        if (t < CutsceneScript.REVEAL_END) {
            return 1.0F - smooth(CutsceneScript.CANDLE_IGNITE, CutsceneScript.REVEAL_END, t);
        }
        if (t >= CutsceneScript.END_FADE_START) {
            return smooth(CutsceneScript.END_FADE_START, CutsceneScript.END, t);
        }
        return 0.0F;
    }

    private static int speakerColor(CutsceneScript.Speaker speaker) {
        return switch (speaker) {
            case PLAYER -> TEXT_PLAYER;
            case CAT -> TEXT_CAT;
            default -> TEXT_NARRATOR;
        };
    }
}
