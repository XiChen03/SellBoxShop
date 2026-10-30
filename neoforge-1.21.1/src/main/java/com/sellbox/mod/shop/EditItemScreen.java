package com.sellbox.mod.shop;

import com.sellbox.mod.shop.network.ShopEditPayload;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public class EditItemScreen extends Screen {
    private float sf;
    private int panelLeft, panelTop, panelW, panelH;
    private int cx;

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
        sf = (float) ShopUI.getScale(height);
        int boxH = (int) Math.round(16 * sf);
        int btnH = (int) Math.round(18 * sf);
        int btnW = (int) Math.round(60 * sf);

        int maxW = Math.min(width - 20, (int) Math.round(280 * sf));
        panelW = Math.max(240, maxW);
        panelLeft = (width - panelW) / 2;
        cx = panelLeft + panelW / 2;

        int pad = (int) Math.round(15 * sf);
        int labelH = (int) Math.round(12 * sf);
        int rowGap = (int) Math.round(6 * sf);
        int fieldH = labelH + boxH + (int) Math.round(4 * sf);
        int btnRowH = btnH + (int) Math.round(8 * sf);
        int tradeBtnYoff = 3 * fieldH + rowGap + labelH;
        int totalContentH = (int) Math.round(24 * sf) + 3 * fieldH + rowGap + btnH + rowGap + btnRowH;

        int maxPanelH = height - 20;
        panelH = Math.min((int) Math.round(totalContentH) + (int) Math.round(8 * sf), maxPanelH);
        panelH = Math.max(panelH, (int) Math.round(180 * sf));
        panelTop = Math.max(10, (height - panelH) / 2 - 10);

        int lx = panelLeft + pad;
        int curY = panelTop + (int) Math.round(24 * sf);

        int labelY = curY;
        curY += labelH + (int) Math.round(2 * sf);
        int idBoxY = curY;
        curY += boxH + rowGap;

        int priceLabelY = curY;
        curY += labelH + (int) Math.round(2 * sf);
        int priceBoxY = curY;
        curY += boxH + rowGap;

        int tradeLabelY = curY;
        curY += labelH + (int) Math.round(2 * sf);
        int tradeBtnY = curY;
        curY += btnH + rowGap;

        int btnRowY = curY;

        int idFieldW = (int) Math.round(150 * sf);
        this.idBox = new EditBox(this.font, lx + (int) Math.round(60 * sf), idBoxY + 1, idFieldW, boxH - 2,
                Component.literal("物品ID"));
        this.idBox.setBordered(false);
        this.idBox.setValue(originalEntry != null ? originalEntry.itemId().toString() : "minecraft:");
        this.addRenderableWidget(this.idBox);

        int priceFieldW = (int) Math.round(80 * sf);
        this.priceBox = new EditBox(this.font, lx + (int) Math.round(60 * sf), priceBoxY + 1, priceFieldW, boxH - 2,
                Component.literal("价格"));
        this.priceBox.setBordered(false);
        this.priceBox.setValue(originalEntry != null ? String.valueOf(originalEntry.price()) : "1");
        this.addRenderableWidget(this.priceBox);

        if (originalEntry != null) {
            tradeType = originalEntry.tradeType();
        }

        addRenderableWidget(new ShopButton(lx + (int) Math.round(60 * sf), tradeBtnY, (int) Math.round(100 * sf), btnH, Component.literal("购买/出售"),
                btn -> {
                    tradeType = (tradeType == ShopEntry.TradeType.BUY) ? ShopEntry.TradeType.SELL : ShopEntry.TradeType.BUY;
                    btn.setMessage(Component.literal(tradeType == ShopEntry.TradeType.BUY ? "当前: 购买" : "当前: 出售"));
                }));

        int saveBtnX = lx;
        int btnGap = (int) Math.round(6 * sf);
        int deleteBtnX = saveBtnX + btnW + btnGap;
        int cancelBtnX = panelLeft + panelW - pad - btnW;
        addRenderableWidget(new ShopButton(saveBtnX, btnRowY, btnW, btnH, Component.literal("保存"), btn -> saveItem()));

        if (!isAdding) {
            addRenderableWidget(new ShopButton(deleteBtnX, btnRowY, btnW, btnH, Component.literal("删除"),
                    btn -> {
                        PacketDistributor.sendToServer(new ShopEditPayload(
                                "remove_item", categoryName, "", "", 0, itemIndex, false, "", 1, ""));
                        this.minecraft.setScreen(parentScreen);
                        parentScreen.refreshFromConfig();
                    }));
        }

        addRenderableWidget(new ShopButton(cancelBtnX, btnRowY, btnW, btnH,
                Component.translatable("gui.shop.cancel"),
                btn -> this.minecraft.setScreen(parentScreen)));
    }

    private void saveItem() {
        String idStr = idBox.getValue().trim();
        ResourceLocation itemId = ResourceLocation.tryParse(idStr);
        if (itemId == null) return;

        int price;
        String priceText = priceBox.getValue().trim();
        if (priceText.isEmpty()) {
            price = 0; // 默认 0 元商品
        } else {
            try {
                price = Integer.parseInt(priceText);
                if (price < 0) price = 0;
            } catch (NumberFormatException e) {
                return;
            }
        }

        String action = isAdding ? "add_item" : "edit_item";
        boolean strictMatch = originalEntry != null && originalEntry.strictMatch();
        String nbtStr = originalEntry != null ? originalEntry.nbt() : null;
        PacketDistributor.sendToServer(new ShopEditPayload(
                action, categoryName, itemId.toString(),
                tradeType.name().toLowerCase(), price, isAdding ? -1 : itemIndex, false, "", 1, "",
                null, 1, strictMatch, nbtStr));
        this.minecraft.setScreen(parentScreen);
        parentScreen.refreshFromConfig();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        fillRoundedRect(g, panelLeft, panelTop, panelW, panelH, 0xFF333333);
        g.fill(panelLeft + 2, panelTop, panelLeft + panelW - 2, panelTop + 1, 0xFFFFAA00);
        super.render(g, mx, my, pt);

        String titleStr = isAdding ? "添加商品" : "编辑商品";
        FontUtil.drawAutoFitCentered(g, font, titleStr, cx, panelTop + (int) Math.round(8 * sf), panelW - 10, 0xFFFFAA);

        int pad = (int) Math.round(15 * sf);
        int lx = panelLeft + pad;
        int curY = panelTop + (int) Math.round(24 * sf);
        int labelH = (int) Math.round(12 * sf);
        int boxH = (int) Math.round(16 * sf);
        int rowGap = (int) Math.round(6 * sf);

        int labelY = curY;
        curY += labelH + (int) Math.round(2 * sf);
        int idBoxY = curY;
        curY += boxH + rowGap;

        int priceLabelY = curY;
        curY += labelH + (int) Math.round(2 * sf);
        int priceBoxY = curY;
        curY += boxH + rowGap;

        int tradeLabelY = curY;
        curY += labelH + (int) Math.round(2 * sf);

        FontUtil.drawAutoFitText(g, font, "物品ID:", lx, labelY, 60, 0xFFFFFF);
        drawStyledBox(g, lx + (int) Math.round(60 * sf), idBoxY, (int) Math.round(150 * sf), boxH, idBox.isFocused());

        FontUtil.drawAutoFitText(g, font, "价格:", lx, priceLabelY, 60, 0xFFFFFF);
        drawStyledBox(g, lx + (int) Math.round(60 * sf), priceBoxY, (int) Math.round(80 * sf), boxH, priceBox.isFocused());

        FontUtil.drawAutoFitText(g, font, "类型:", lx, tradeLabelY, 60, 0xFFFFFF);
    }

    private void drawStyledBox(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
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
