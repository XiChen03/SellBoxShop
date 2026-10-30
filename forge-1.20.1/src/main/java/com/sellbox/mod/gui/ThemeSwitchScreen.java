package com.sellbox.mod.gui;

import com.sellbox.mod.shop.ModernButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * GUI 主题切换界面
 */
public class ThemeSwitchScreen extends Screen {

    public ThemeSwitchScreen() {
        super(Component.literal("Theme Switcher"));
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft == null) minecraft = Minecraft.getInstance();
        if (font == null) font = Minecraft.getInstance().font;

        float scale = Math.max(0.8f, Math.min(2.0f, height / 400f));
        int btnW = 120;
        int btnH = 28;
        int gap = 12;
        int totalH = 5 * btnH + 4 * gap;
        int startY = (height - totalH) / 2;
        int centerX = width / 2;

        addRenderableWidget(new ModernButton(
                centerX - btnW / 2, startY, btnW, btnH,
                Component.literal("Default Dark"),
                btn -> {
                    GuiTheme.set(new GuiTheme());
                    GuiTheme.setByName("default");
                    minecraft.setScreen(null);
                }));

        addRenderableWidget(new ModernButton(
                centerX - btnW / 2, startY + btnH + gap, btnW, btnH,
                Component.literal("Modern"),
                btn -> {
                    GuiTheme.set(new ModernTheme());
                    GuiTheme.setByName("modern");
                    minecraft.setScreen(null);
                }));

        addRenderableWidget(new ModernButton(
                centerX - btnW / 2, startY + 2 * (btnH + gap), btnW, btnH,
                Component.literal("Light"),
                btn -> {
                    GuiTheme.set(ThemePresets.light());
                    GuiTheme.setByName("light");
                    minecraft.setScreen(null);
                }));

        addRenderableWidget(new ModernButton(
                centerX - btnW / 2, startY + 3 * (btnH + gap), btnW, btnH,
                Component.literal("Green"),
                btn -> {
                    GuiTheme.set(ThemePresets.green());
                    GuiTheme.setByName("green");
                    minecraft.setScreen(null);
                }));

        addRenderableWidget(new ModernButton(
                centerX - btnW / 2, startY + 4 * (btnH + gap), btnW, btnH,
                Component.literal("Purple"),
                btn -> {
                    GuiTheme.set(ThemePresets.purple());
                    GuiTheme.setByName("purple");
                    minecraft.setScreen(null);
                }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xCC000000);

        String title = "Select Theme";
        int titleW = font.width(title);
        g.drawString(font, title, (width - titleW) / 2, 40, 0xFFFFFF, true);

        String currentName = GuiTheme.getCurrentName();
        String info = "Current: " + currentName;
        int infoW = font.width(info);
        g.drawString(font, info, (width - infoW) / 2, 60, 0xAAAAAA, true);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(null);
    }
}
