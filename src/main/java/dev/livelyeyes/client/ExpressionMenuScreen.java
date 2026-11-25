package dev.livelyeyes.client;

import dev.livelyeyes.logic.Mood;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Small menu on the period key: click an expression and your face shows it for a few seconds. */
public final class ExpressionMenuScreen extends Screen {
    private static final int BUTTON_W = 96, BUTTON_H = 20, GAP = 6, COLUMNS = 3;

    public ExpressionMenuScreen() {
        super(Component.translatable("lively_eyes.menu.title"));
    }

    @Override
    protected void init() {
        Mood[] moods = Mood.values();
        int rows = (moods.length + COLUMNS - 1) / COLUMNS;
        int totalW = COLUMNS * BUTTON_W + (COLUMNS - 1) * GAP;
        int totalH = rows * BUTTON_H + (rows - 1) * GAP;
        int x0 = (width - totalW) / 2, y0 = (height - totalH) / 2 + 8;
        for (int i = 0; i < moods.length; i++) {
            Mood mood = moods[i];
            int x = x0 + (i % COLUMNS) * (BUTTON_W + GAP);
            int y = y0 + (i / COLUMNS) * (BUTTON_H + GAP);
            addRenderableWidget(Button.builder(label(mood), b -> {
                Expressions.pick(mood);
                onClose();
            }).bounds(x, y, BUTTON_W, BUTTON_H).build());
        }
    }

    public static Component label(Mood mood) {
        return Component.translatable("lively_eyes.mood." + mood.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int rows = (Mood.values().length + COLUMNS - 1) / COLUMNS;
        int top = (height - (rows * BUTTON_H + (rows - 1) * GAP)) / 2 + 8;
        g.drawCenteredString(font, title, width / 2, top - 30, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("lively_eyes.menu.hint"), width / 2, top - 16, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
