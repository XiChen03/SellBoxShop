package com.sellbox.mod.shop;

import com.sellbox.mod.gui.GuiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class ShopButton extends Button {
    private final int bgColor;
    private final int hoverColor;
    private final int borderColor;
    private final int textColor;

    public ShopButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        GuiTheme T = GuiTheme.get();
        this.bgColor = T.btnBgNormal;
        this.hoverColor = T.btnBgHovered;
        this.borderColor = T.btnBorder;
        this.textColor = T.btnText;
    }

    public ShopButton(int x, int y, int w, int h, Component message, OnPress onPress,
                      int bgColor, int hoverColor, int borderColor, int textColor) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        this.bgColor = bgColor;
        this.hoverColor = hoverColor;
        this.borderColor = borderColor;
        this.textColor = textColor;
    }

    @Override
    protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int bg = isHovered() ? hoverColor : bgColor;
        int border = isHovered() ? 0xFFFFFF : borderColor;

        gui.fill(getX() + 2, getY(), getX() + getWidth() - 2, getY() + 1, border);
        gui.fill(getX() + 2, getY() + getHeight() - 1, getX() + getWidth() - 2, getY() + getHeight(), border);
        gui.fill(getX(), getY() + 2, getX() + 1, getY() + getHeight() - 2, border);
        gui.fill(getX() + getWidth() - 1, getY() + 2, getX() + getWidth(), getY() + getHeight() - 2, border);
        gui.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, bg);

        var mc = Minecraft.getInstance();
        var font = mc.font;
        Component msg = getMessage();
        int textW = font.width(msg);
        gui.drawString(font, msg,
                getX() + (getWidth() - textW) / 2,
                getY() + (getHeight() - 8) / 2, textColor);
    }
}
