package com.sellbox.mod.shop;

import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class EditCategoryScreen extends Screen {
    private int popupW = 240;
    private int popupH = 200;

    private final ShopScreen parentScreen;
    private final String categoryName;
    private EditBox nameBox;

    public EditCategoryScreen(ShopScreen parent, String categoryName) {
        super(Component.literal("编辑分类 - " + categoryName));
        this.parentScreen = parent;
        this.categoryName = categoryName;
    }

    @Override
    protected void init() {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        double scale = ShopUI.getScale(height);
        popupW = (int) Math.round(240 * scale);
        popupH = (int) Math.round(200 * scale);
        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - popupW / 2;
        int top = cy - popupH / 2;

        this.nameBox = new EditBox(this.font, cx - 47, top + 44, 94, 11,
                Component.literal("分类名称"));
        this.nameBox.setValue(categoryName);
        this.nameBox.setBordered(false);
        this.addRenderableWidget(this.nameBox);

        int btnH = 14;
        int gap = 6;

        int btn1w = 54;
        int btn2w = 34;
        int btn3w = 34;
        int row1Total = btn1w + gap + btn2w + gap + btn3w;
        int row1StartX = cx - row1Total / 2;

        addRenderableWidget(new ShopButton(row1StartX, top + 62, btn1w, btnH, Component.literal("保存名称"),
                btn -> {
                    String newName = nameBox.getValue().trim();
                    if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
                    if (!newName.isEmpty() && !newName.equals(categoryName)) {
                        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket(
                                "rename_category", categoryName, newName, "", 0, -1, false, "", 1, "", "", 1));
                    }
                    parentScreen.refreshFromConfig();
                    this.minecraft.setScreen(parentScreen);
                }));

        addRenderableWidget(new ShopButton(row1StartX + btn1w + gap, top + 62, btn2w, btnH, Component.literal("上移"),
                btn -> {
                    if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket(
                            "move_category_up", categoryName, "", "", 0, -1, false, "", 1, "", "", 1));
                    parentScreen.refreshFromConfig();
                }));

        addRenderableWidget(new ShopButton(row1StartX + btn1w + gap + btn2w + gap, top + 62, btn3w, btnH, Component.literal("下移"),
                btn -> {
                    if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket(
                            "move_category_down", categoryName, "", "", 0, -1, false, "", 1, "", "", 1));
                    parentScreen.refreshFromConfig();
                }));

        int btn4w = 107;
        addRenderableWidget(new ShopButton(cx - btn4w / 2, top + 80, btn4w, btnH, Component.literal("删除此分类"),
                btn -> {
                    if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket(
                            "remove_category", categoryName, "", "", 0, -1, false, "", 1, "", "", 1));
                    this.minecraft.setScreen(parentScreen);
                    parentScreen.refreshFromConfig();
                }));

        int btn5w = 67;
        addRenderableWidget(new ShopButton(cx - btn5w / 2, top + 98, btn5w, btnH,
                Component.translatable("gui.shop.cancel"),
                btn -> { if (this.minecraft == null) this.minecraft = Minecraft.getInstance(); this.minecraft.setScreen(parentScreen); }));
    }

    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xC0000000);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - popupW / 2;
        int top = cy - popupH / 2;

        fillRoundedRect(guiGraphics, left, top, popupW, popupH, 0xFF333333);
        guiGraphics.fill(left + 2, top, left + popupW - 2, top + 1, 0xFFFFAA00);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        FontUtil.drawAutoFitCentered(guiGraphics, this.font, "编辑分类", cx, top + 8, popupW - 10, 0xFFFFAA);

        drawBox(guiGraphics, cx - 47, top + 44, 94, 11, nameBox.isFocused());
        FontUtil.drawAutoFitCentered(guiGraphics, this.font, "名称:", cx, top + 34, 100, 0xFFFFFF);

        int count = ShopConfig.getCategory(categoryName) != null ? ShopConfig.getCategory(categoryName).size() : 0;
        Component info = Component.literal("商品数: " + count);
        FontUtil.drawAutoFitCentered(guiGraphics, this.font, info.getString(), cx, top + popupH - 25, popupW - 20, 0x888888);
    }

    private void drawBox(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        int col = focused ? 0x55FFFFFF : 0x55AAAAAA;
        g.fill(x + 2, y, x + w - 2, y + 1, col);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, col);
        g.fill(x, y + 2, x + 1, y + h - 2, col);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, col);
    }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        boolean handled = super.mouseClicked(mx, my, button);
        if (!handled) {
            for (var w : children()) {
                if (w instanceof EditBox eb) eb.setFocused(false);
            }
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
            this.minecraft.setScreen(parentScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}