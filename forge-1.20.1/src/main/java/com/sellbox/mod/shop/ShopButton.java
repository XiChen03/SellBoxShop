package com.sellbox.mod.shop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class ShopButton extends Button {
    public ShopButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int borderColor = 0xFF4488FF;
        int bgColor = isHovered() ? 0x44FFFFFF : 0x88333333;

        gui.fill(getX() + 2, getY(), getX() + getWidth() - 2, getY() + 1, borderColor);
        gui.fill(getX() + 2, getY() + getHeight() - 1, getX() + getWidth() - 2, getY() + getHeight(), borderColor);
        gui.fill(getX(), getY() + 2, getX() + 1, getY() + getHeight() - 2, borderColor);
        gui.fill(getX() + getWidth() - 1, getY() + 2, getX() + getWidth(), getY() + getHeight() - 2, borderColor);
        gui.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, bgColor);

        Component msg = getMessage();
        int textW = Minecraft.getInstance().font.width(msg);
        gui.drawString(Minecraft.getInstance().font, msg,
                getX() + (getWidth() - textW) / 2,
                getY() + (getHeight() - 8) / 2, 0xFFFFFF);
    }
}