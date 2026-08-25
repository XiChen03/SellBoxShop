package com.sellbox.mod.shop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AddItemTypeScreen extends Screen {
    private int popupW = 280;
    private int popupH = 200;

    private final ShopScreen parentScreen;
    private final String categoryName;

    public AddItemTypeScreen(ShopScreen parent, String categoryName) {
        super(Component.literal("选择添加类型"));
        this.parentScreen = parent;
        this.categoryName = categoryName;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x00000000);
    }

    @Override
    protected void init() {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        double scale = ShopUI.getScale(height);
        popupW = (int) Math.round(280 * scale);
        popupH = (int) Math.round(200 * scale);
        int left = (width - popupW) / 2;
        int top = (height - popupH) / 2;

        int btnW = (int) Math.round(160 * scale);
        int btnH = (int) Math.round(28 * scale);
        int gap = (int) Math.round(12 * scale);
        int totalH = 3 * btnH + 2 * gap;
        int startY = top + (popupH - (totalH + btnH + gap)) / 2;
        int x = left + (popupW - btnW) / 2;

        addRenderableWidget(new ShopButton(x, startY, btnW, btnH, Component.literal("物品"), btn -> {
            if (minecraft == null) minecraft = Minecraft.getInstance();
            minecraft.setScreen(new ShopItemPickerScreen(parentScreen, categoryName));
        }));

        addRenderableWidget(new ShopButton(x, startY + btnH + gap, btnW, btnH, Component.literal("指令"), btn -> {
            if (minecraft == null) minecraft = Minecraft.getInstance();
            minecraft.setScreen(new AddCommandScreen(parentScreen, categoryName));
        }));

        addRenderableWidget(new ShopButton(x, startY + 2 * (btnH + gap), btnW, btnH, Component.literal("经验"), btn -> {
            if (minecraft == null) minecraft = Minecraft.getInstance();
            minecraft.setScreen(new AddXpScreen(parentScreen, categoryName));
        }));

        addRenderableWidget(new ShopButton(x, startY + 3 * (btnH + gap), btnW, btnH, Component.literal("返回"), btn -> {
            if (minecraft == null) minecraft = Minecraft.getInstance();
            minecraft.setScreen(parentScreen);
        }));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (minecraft == null) minecraft = Minecraft.getInstance();
            minecraft.setScreen(parentScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}