package com.sellbox.mod.playershop;

import com.sellbox.mod.playershop.network.PlayerShopTradePayload;
import com.sellbox.mod.shop.ShopButton;
import com.sellbox.mod.shop.ShopEntry;
import com.sellbox.mod.shop.ShopUI;
import com.sellbox.mod.util.FontUtil;
import com.sellbox.mod.util.ItemIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class PlayerTradeConfirmScreen extends Screen {
    private final com.sellbox.mod.gui.GuiTheme T = com.sellbox.mod.gui.GuiTheme.get();
    private int popupW = 220;
    private int popupH = 220;

    private final PlayerShopScreen parentScreen;
    private final ShopEntry entry;
    private final BlockPos marketPos;
    private final UUID shopOwnerUUID;
    private EditBox quantityBox;
    private int quantityCache = 1;
    private int iconX, iconY, iconSize;

    public PlayerTradeConfirmScreen(PlayerShopScreen parent, ShopEntry entry, BlockPos marketPos, UUID shopOwnerUUID) {
        super(Component.translatable("gui.shop.trade_confirm"));
        this.parentScreen = parent;
        this.entry = entry;
        this.marketPos = marketPos;
        this.shopOwnerUUID = shopOwnerUUID;
    }

    @Override
    protected void init() {
        double scale = ShopUI.getScale(height);
        int baseW = (int) Math.round(220 * scale);
        int baseH = (int) Math.round(200 * scale);
        int maxW = (int) (width * 0.7);
        int maxH = (int) ((height - 48) * 0.7);
        popupW = Math.min(baseW, maxW);
        popupH = Math.min(baseH, maxH);
        float sf = Math.min((float) scale, Math.min((float) maxW / 220, (float) maxH / 200));
        int cx = width / 2;
        int cy = (height - 48) / 2;
        int left = cx - popupW / 2;
        int top = cy - popupH / 2;

        int boxW = (int) Math.round(80 * sf);
        int boxH = (int) Math.round(16 * sf);
        int btnWSmall = (int) Math.round(40 * sf);
        int btnWMed = (int) Math.round(50 * sf);
        int btnWBig = (int) Math.round(70 * sf);
        int btnH = (int) Math.round(20 * sf);
        int btnShortH = (int) Math.round(16 * sf);

        this.quantityBox = new EditBox(font, left + (int)(60 * sf), top + (int)(56 * sf), boxW, boxH, Component.translatable("gui.shop.quantity"));
        this.quantityBox.setBordered(false);
        this.quantityBox.setValue("1");
        this.quantityBox.setResponder(s -> {
            try {
                int v = Integer.parseInt(s);
                if (v > 0) quantityCache = v;
            } catch (NumberFormatException ignored) {}
        });
        this.addRenderableWidget(this.quantityBox);

        int qtyBtnY = top + (int)(56 * sf) + boxH + (int)(10 * sf);

        iconSize = (int) Math.round(32 * sf);
        int btnStartX = left + (int)(15 * sf);
        int fourthBtnX = btnStartX + (int)(3 * 43 * sf);
        iconX = fourthBtnX + (btnWMed - iconSize) / 2;
        int titleBottom = top + (int)(8 * sf) + font.lineHeight;
        int centerY = (titleBottom + qtyBtnY) / 2;
        iconY = centerY - iconSize / 2;

        addRenderableWidget(new ShopButton(btnStartX, qtyBtnY, btnWSmall, btnShortH, Component.literal("\u00d71"),
                btn -> quantityBox.setValue("1")));
        addRenderableWidget(new ShopButton(btnStartX + (int)(43 * sf), qtyBtnY, btnWSmall, btnShortH, Component.literal("\u00d710"),
                btn -> quantityBox.setValue("10")));
        addRenderableWidget(new ShopButton(btnStartX + (int)(86 * sf), qtyBtnY, btnWSmall, btnShortH, Component.literal("\u00d764"),
                btn -> quantityBox.setValue("64")));
        addRenderableWidget(new ShopButton(fourthBtnX, qtyBtnY, btnWMed, btnShortH, Component.literal("\u00d7999"),
                btn -> quantityBox.setValue(String.valueOf(999))));

        int totalY = top + popupH - (int)(22 * sf);
        int confirmY = totalY - btnH - (int)(5 * sf);
        addRenderableWidget(new ShopButton(left + (int)(30 * sf), confirmY, btnWBig, btnH,
                Component.translatable("gui.shop.confirm"), btn -> confirmTrade()));
        addRenderableWidget(new ShopButton(left + (int)(120 * sf), confirmY, btnWBig, btnH,
                Component.translatable("gui.shop.cancel"),
                btn -> this.minecraft.setScreen(parentScreen)));
    }

    private void confirmTrade() {
        try {
            int quantity = Integer.parseInt(quantityBox.getValue());
            if (quantity <= 0) return;
            int pricePerUnit = ("barter".equals(entry.entryType()) && entry.barterItemId() != null) ? 0 : entry.price();
            PacketDistributor.sendToServer(new PlayerShopTradePayload(
                    shopOwnerUUID, entry.itemId(), quantity, pricePerUnit, marketPos));
            this.minecraft.setScreen(parentScreen);
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int cx = width / 2;
        int cy = (height - 48) / 2;
        int left = cx - popupW / 2;
        int top = cy - popupH / 2;
        float maxSfW = (float) (width * 0.7) / 220;
        float maxSfH = (float) ((height - 48) * 0.7) / 200;
        float sf = Math.min((float) ShopUI.getScale(height), Math.min(maxSfW, maxSfH));

        fillRoundedRect(g, left, top, popupW, popupH, T.popupBg);
        g.fill(left + 2, top, left + popupW - 2, top + 1, T.accentStripe);
        super.render(g, mx, my, pt);

        String displayName = entry.getDisplayName().getString();
        boolean isBarter = "barter".equals(entry.entryType()) && entry.barterItemId() != null;
        String titleStr;
        if (isBarter) {
            ItemStack offerStack = (minecraft.level != null)
                    ? entry.createBarterOfferStack(minecraft.level.registryAccess()) : entry.createBarterOfferStack();
            String offerName = offerStack.isEmpty() ? "?" : offerStack.getHoverName().getString();
            int offerQ = Math.max(1, entry.barterItemQty());
            int goodsQ = Math.max(1, entry.quantity());
            titleStr = "购买: " + displayName + " x" + (goodsQ * quantityCache);
        } else {
            boolean isSellTrade = entry.tradeType() == ShopEntry.TradeType.SELL;
            titleStr = isSellTrade
                    ? Component.translatable("gui.sellbox.trade_buy_title", displayName).getString()
                    : Component.translatable("gui.sellbox.trade_sell_title", displayName).getString();
        }
        FontUtil.drawAutoFitCentered(g, font, titleStr, cx, top + (int)(8 * sf), popupW - 10, T.textTitle);

        if (!isBarter) {
            Component price = Component.translatable("gui.sellbox.trade_unit_price", String.valueOf(entry.price()));
            FontUtil.drawAutoFitText(g, font, price.getString(), left + (int)(15 * sf), top + (int)(38 * sf), popupW - (int)(20 * sf), T.textPrice);
        } else {
            int offerQ = Math.max(1, entry.barterItemQty());
            Component barterPrice = Component.literal("单价: " +
                    (minecraft.level != null
                            ? entry.createBarterOfferStack(minecraft.level.registryAccess()) : entry.createBarterOfferStack())
                            .getHoverName().getString() + " x" + offerQ);
            FontUtil.drawAutoFitText(g, font, barterPrice.getString(), left + (int)(15 * sf), top + (int)(38 * sf), popupW - (int)(20 * sf), 0x88FF88);
        }

        ItemStack itemStack = (minecraft.level != null)
                ? entry.createStack(minecraft.level.registryAccess()) : entry.createStack();
        ItemIcons.render(g, itemStack, iconX, iconY);
        g.fill(iconX, iconY, iconX + iconSize, iconY + 1, T.cellBorder);
        g.fill(iconX, iconY + iconSize - 1, iconX + iconSize, iconY + iconSize, T.cellBorder);
        g.fill(iconX, iconY, iconX + 1, iconY + iconSize, T.cellBorder);
        g.fill(iconX + iconSize - 1, iconY, iconX + iconSize, iconY + iconSize, T.cellBorder);

        if (mx >= iconX && mx <= iconX + iconSize && my >= iconY && my <= iconY + iconSize) {
            ItemStack tooltipStack = itemStack.copy();
            if (!entry.customName().isEmpty()) {
                tooltipStack.set(DataComponents.CUSTOM_NAME, Component.literal(entry.customName()));
            }
            if (!tooltipStack.isEmpty()) {
                g.renderTooltip(font, tooltipStack, mx, my);
            } else {
                g.renderTooltip(font, Component.literal(displayName), mx, my);
            }
        }

        int boxW = (int) Math.round(80 * sf);
        int boxH = (int) Math.round(16 * sf);
        drawBox(g, left + (int)(60 * sf), top + (int)(56 * sf), boxW, boxH, quantityBox.isFocused());

        Component qtyLabel = Component.translatable("gui.sellbox.quantity_label");
        FontUtil.drawAutoFitText(g, font, qtyLabel.getString(), left + (int)(15 * sf), top + (int)(58 * sf), (int)(40 * sf), T.textPrimary);

        String totalStr;
        if (isBarter) {
            int offerQ = Math.max(1, entry.barterItemQty());
            ItemStack offerStack = (minecraft.level != null)
                    ? entry.createBarterOfferStack(minecraft.level.registryAccess()) : entry.createBarterOfferStack();
            String offerName = offerStack.isEmpty() ? "?" : offerStack.getHoverName().getString();
            totalStr = "总价: " + offerName + " x" + (offerQ * quantityCache);
        } else {
            int total = entry.price() * quantityCache;
            totalStr = "总价: ¥" + String.format("%,d", total);
        }
        FontUtil.drawAutoFitCentered(g, font, totalStr, cx, top + popupH - (int)(22 * sf), popupW - 20, T.textTotal);
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

    private void drawBox(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        int col = focused ? T.inputBorderFocused : T.inputBorderUnfocused;
        g.fill(x + 2, y, x + w - 2, y + 1, col);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, col);
        g.fill(x, y + 2, x + 1, y + h - 2, col);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, col);
    }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }
}
