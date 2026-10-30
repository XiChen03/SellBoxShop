package com.sellbox.mod.shop;

import com.sellbox.mod.shop.network.ShopEditPayload;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class AddXpScreen extends Screen {
    private int popupW = 260;
    private int popupH = 200;

    private final ShopScreen parentScreen;
    private final String categoryName;
    private final int editIndex;
    private final ShopEntry editEntry;

    private EditBox xpBox;
    private EditBox priceBox;

    public AddXpScreen(ShopScreen parent, String categoryName) {
        this(parent, categoryName, -1, null);
    }

    public AddXpScreen(ShopScreen parent, String categoryName, int editIndex, ShopEntry editEntry) {
        super(Component.literal(editIndex >= 0 ? "编辑经验" : "购买经验"));
        this.parentScreen = parent;
        this.categoryName = categoryName;
        this.editIndex = editIndex;
        this.editEntry = editEntry;
    }

    @Override
    protected void init() {
        double scale = ShopUI.getScale(height);
        popupW = (int) Math.round(260 * scale);
        popupH = (int) Math.round(200 * scale);
        int cx = width / 2;
        int cy = height / 2;
        int left = cx - popupW / 2;
        int top = cy - popupH / 2;
        float sf = (float) scale;

        int boxW = (int) Math.round(80 * sf);
        int boxH = (int) Math.round(16 * sf);
        int btnW = (int) Math.round(60 * sf);
        int btnH = (int) Math.round(20 * sf);

        this.xpBox = new EditBox(font, cx - boxW / 2, top + (int)(48 * sf), boxW, boxH, Component.literal("1"));
        this.xpBox.setBordered(false);
        this.xpBox.setFilter(s -> s.matches("\\d*"));
        if (editEntry != null) {
            try { xpBox.setValue(String.valueOf(Integer.parseInt(editEntry.extraData()))); } catch (NumberFormatException e) {}
        }
        this.addRenderableWidget(this.xpBox);

        this.priceBox = new EditBox(font, cx - boxW / 2, top + (int)(86 * sf), boxW, boxH, Component.literal("1"));
        this.priceBox.setBordered(false);
        this.priceBox.setFilter(s -> s.matches("\\d*"));
        if (editEntry != null) priceBox.setValue(String.valueOf(editEntry.price()));
        this.addRenderableWidget(this.priceBox);

        int btnGap = (int) Math.round(20 * sf);
        int btnGroupW = 2 * btnW + btnGap;
        int btnStartX = cx - btnGroupW / 2;
        int btnY = top + (int)(130 * sf);
        addRenderableWidget(new ShopButton(btnStartX, btnY, btnW, btnH, Component.literal("保存"), btn -> save()));
        addRenderableWidget(new ShopButton(btnStartX + btnW + btnGap, btnY, btnW, btnH, Component.literal("返回"), btn -> minecraft.setScreen(parentScreen)));
    }

    private void save() {
        int xpAmount;
        try { xpAmount = Integer.parseInt(xpBox.getValue()); if (xpAmount < 1) xpAmount = 1; }
        catch (NumberFormatException e) { return; }
        int price;
        try { price = Integer.parseInt(priceBox.getValue()); if (price < 0) price = 0; }
        catch (NumberFormatException e) { return; }
        String action = editIndex >= 0 ? "edit_xp" : "add_xp";
        PacketDistributor.sendToServer(new ShopEditPayload(
                action, categoryName, String.valueOf(xpAmount), "", price, Math.max(0, editIndex), false, "", 1, ""));
        minecraft.setScreen(parentScreen);
        parentScreen.refreshFromConfig();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int cx = width / 2;
        int cy = height / 2;
        int left = cx - popupW / 2;
        int top = cy - popupH / 2;

        fillRoundedRect(g, left, top, popupW, popupH, 0xFF333333);
        g.fill(left + 2, top, left + popupW - 2, top + 1, 0xFFFFAA00);
        float s = (float) ShopUI.getScale(height);
        FontUtil.drawAutoFitCentered(g, font, title.getString(), cx, top + (int)(8 * s), popupW - 10, 0xFFFFAA);
 
        Component hint = Component.literal("直接购买经验值，消耗金币");
        FontUtil.drawAutoFitCentered(g, font, hint.getString(), cx, top + (int)(22 * s), popupW - 10, 0xAAAAAA);

        super.render(g, mx, my, pt);

        int boxW = (int) Math.round(80 * s);
        int boxH = (int) Math.round(16 * s);
        int boxX = cx - boxW / 2;
        drawBox(g, boxX, top + (int)(48 * s), boxW, boxH, xpBox.isFocused());
        drawBox(g, boxX, top + (int)(86 * s), boxW, boxH, priceBox.isFocused());

        FontUtil.drawAutoFitCentered(g, font, "经验值:", cx, top + (int)(36 * s), popupW - 20, 0xFFFFFF);
        FontUtil.drawAutoFitCentered(g, font, "价格:", cx, top + (int)(74 * s), popupW - 20, 0xFFFFFF);
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
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == 256) { minecraft.setScreen(parentScreen); return true; }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
