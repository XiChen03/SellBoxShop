package com.sellbox.mod.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * GUI 主题切换界面
 */
public class ThemeSwitchScreen extends Screen {
    private float scale;

    public ThemeSwitchScreen() {
        super(Component.literal("Theme Switcher"));
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft == null) minecraft = Minecraft.getInstance();
        if (font == null) font = Minecraft.getInstance().font;

        scale = Math.max(0.8f, Math.min(2.0f, height / 400f));

        int btnW = 120;
        int btnH = 28;
        int gap = 12;
        int totalH = 5 * btnH + 4 * gap;
        int startY = (height - totalH) / 2;
        int centerX = width / 2;

        // 默认深色
        addRenderableWidget(new com.sellbox.mod.shop.ModernButton(
                centerX - btnW / 2, startY, btnW, btnH,
                Component.literal("Default Dark"),
                btn -> {
                    GuiTheme.set(new GuiTheme());
                    minecraft.setScreen(null);
                }));

        // 现代风格
        addRenderableWidget(new com.sellbox.mod.shop.ModernButton(
                centerX - btnW / 2, startY + btnH + gap, btnW, btnH,
                Component.literal("Modern"),
                btn -> {
                    GuiTheme.set(new ModernTheme());
                    minecraft.setScreen(null);
                }));

        // 浅色
        addRenderableWidget(new com.sellbox.mod.shop.ModernButton(
                centerX - btnW / 2, startY + 2 * (btnH + gap), btnW, btnH,
                Component.literal("Light"),
                btn -> {
                    GuiTheme.set(ThemePresets.light());
                    minecraft.setScreen(null);
                }));

        // 绿色护眼
        addRenderableWidget(new com.sellbox.mod.shop.ModernButton(
                centerX - btnW / 2, startY + 3 * (btnH + gap), btnW, btnH,
                Component.literal("Green"),
                btn -> {
                    GuiTheme.set(ThemePresets.green());
                    minecraft.setScreen(null);
                }));

        // 紫色
        addRenderableWidget(new com.sellbox.mod.shop.ModernButton(
                centerX - btnW / 2, startY + 4 * (btnH + gap), btnW, btnH,
                Component.literal("Purple"),
                btn -> {
                    GuiTheme.set(ThemePresets.purple());
                    minecraft.setScreen(null);
                }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 背景
        g.fill(0, 0, width, height, 0xCC000000);

        // 标题
        String title = "Select Theme";
        int titleW = font.width(title);
        g.drawString(font, title, (width - titleW) / 2, 40, 0xFFFFFF, true);

        // 当前主题
        GuiTheme current = GuiTheme.get();
        String currentName = "Unknown";
        if (current instanceof ModernTheme) currentName = "Modern";
        else if (current.getClass() == GuiTheme.class) currentName = "Default Dark";

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
