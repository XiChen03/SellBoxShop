package com.sellbox.mod.shop;

import com.sellbox.mod.gui.GuiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 现代风格按钮
 */
public class ModernButton extends Button {
    private final int bgColor;
    private final int hoverColor;
    private final int borderColor;
    private final int textColor;

    public ModernButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, 0xFF2A3050, 0x664A90D9, 0xFF4A90D9, 0xFFFFFF);
    }

    public ModernButton(int x, int y, int width, int height, Component message, OnPress onPress,
                        int bgColor, int hoverColor, int borderColor, int textColor) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.bgColor = bgColor;
        this.hoverColor = hoverColor;
        this.borderColor = borderColor;
        this.textColor = textColor;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme T = GuiTheme.get();

        int bg = isHovered ? hoverColor : bgColor;
        int border = isHovered ? T.textPrimary : borderColor;

        // 背景
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);

        // 边框
        g.fill(getX(), getY(), getX() + getWidth(), getY() + 1, border);
        g.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), border);
        g.fill(getX(), getY(), getX() + 1, getY() + getHeight(), border);
        g.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), border);

        // 文字
        var mc = net.minecraft.client.Minecraft.getInstance();
        var font = mc.font;
        int textW = font.width(getMessage().getString());
        int textX = getX() + (getWidth() - textW) / 2;
        int textY = getY() + (getHeight() - 8) / 2;
        g.drawString(font, getMessage().getString(), textX, textY, textColor, true);
    }
}
