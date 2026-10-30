package com.sellbox.mod.shop;

import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class EditItemScreen extends Screen {
    private int popupW = 240;
    private int popupH = 200;

    private final ShopScreen parentScreen;
    private final String categoryName;
    private final int itemIndex;
    private final ShopEntry originalEntry;
    private final boolean isAdding;

    private EditBox idBox;
    private EditBox priceBox;
    private ShopEntry.TradeType tradeType = ShopEntry.TradeType.BUY;

    public EditItemScreen(ShopScreen parent, String categoryName, int itemIndex, ShopEntry entry) {
        super(Component.literal(entry == null ? "添加商品" : "编辑商品"));
        this.parentScreen = parent;
        this.categoryName = categoryName;
        this.itemIndex = itemIndex;
        this.originalEntry = entry;
        this.isAdding = entry == null;
    }

    @Override
    protected void init() {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        double scale = ShopUI.getScale(height);
        popupW = (int) Math.round(240 * scale);
        popupH = (int) Math.round(172 * scale);
        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - popupW / 2;
        int top = cy - popupH / 2 - 5;

        this.idBox = new EditBox(this.font, left + 60, top + 40, 150, 16,
                Component.literal("物品ID"));
        this.idBox.setBordered(false);
        this.idBox.setValue(originalEntry != null ? originalEntry.itemId().toString() : "minecraft:");
        this.addRenderableWidget(this.idBox);

        this.priceBox = new EditBox(this.font, left + 60, top + 90, 80, 16,
                Component.literal("价格"));
        this.priceBox.setBordered(false);
        this.priceBox.setValue(originalEntry != null ? String.valueOf(originalEntry.price()) : "1");
        this.addRenderableWidget(this.priceBox);

        if (originalEntry != null) {
            tradeType = originalEntry.tradeType();
        }

        addRenderableWidget(new ShopButton(left + 60, top + 115, 100, 20, Component.literal("购买/出售"),
                btn -> {
                    tradeType = (tradeType == ShopEntry.TradeType.BUY) ? ShopEntry.TradeType.SELL : ShopEntry.TradeType.BUY;
                    btn.setMessage(Component.literal(tradeType == ShopEntry.TradeType.BUY ? "当前: 购买" : "当前: 出售"));
                }));

        addRenderableWidget(new ShopButton(left + 30, top + 150, 60, 20, Component.literal("保存"), btn -> saveItem()));

        if (!isAdding) {
            addRenderableWidget(new ShopButton(left + 100, top + 150, 50, 20, Component.literal("删除"),
                    btn -> {
                        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
                        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket(
                                "remove_item", categoryName, "", "", 0, itemIndex, false, "", 1, "", "", 1));
                        this.minecraft.setScreen(parentScreen);
                        parentScreen.refreshFromConfig();
                    }));
        }

        addRenderableWidget(new ShopButton(left + 160, top + 150, 50, 20,
                Component.translatable("gui.shop.cancel"),
                btn -> { if (this.minecraft == null) this.minecraft = Minecraft.getInstance(); this.minecraft.setScreen(parentScreen); }));
    }

    private void saveItem() {
        String idStr = idBox.getValue().trim();
        ResourceLocation itemId = ResourceLocation.tryParse(idStr);
        if (itemId == null) return;

        int price;
        try {
            price = Integer.parseInt(priceBox.getValue().trim());
            if (price < 0) price = 0;
        } catch (NumberFormatException e) {
            return;
        }

        String action = isAdding ? "add_item" : "edit_item";
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket(
                action, categoryName, itemId.toString(),
                tradeType.name().toLowerCase(), price, isAdding ? -1 : itemIndex, false, "", 1, "", "", 1));
        this.minecraft.setScreen(parentScreen);
        parentScreen.refreshFromConfig();
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

        String titleStr = isAdding ? "添加商品" : "编辑商品";
        FontUtil.drawAutoFitCentered(guiGraphics, this.font, titleStr, cx, top + 8, popupW - 10, 0xFFFFAA);

        drawBox(guiGraphics, left + 60, top + 40, 150, 16, idBox.isFocused());
        drawBox(guiGraphics, left + 60, top + 90, 80, 16, priceBox.isFocused());
        FontUtil.drawAutoFitText(guiGraphics, this.font, "物品ID:", left + 15, top + 42, 40, 0xFFFFFF);
        FontUtil.drawAutoFitText(guiGraphics, this.font, "价格:", left + 15, top + 92, 40, 0xFFFFFF);
        FontUtil.drawAutoFitText(guiGraphics, this.font, "类型:", left + 15, top + 117, 40, 0xFFFFFF);
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