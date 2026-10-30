package com.sellbox.mod.shop;

import com.sellbox.mod.gui.GuiTheme;
import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.util.ItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ModernTradeConfirmScreen extends Screen {
    private final GuiTheme T = GuiTheme.get();
    private final ModernShopScreen parentScreen;
    private final ShopEntry entry;
    private EditBox quantityBox;
    private int quantityCache = 1;
    private float scale;

    public ModernTradeConfirmScreen(ModernShopScreen parent, ShopEntry entry) {
        super(Component.literal("Trade Confirm"));
        this.parentScreen = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft == null) minecraft = Minecraft.getInstance();
        if (font == null) font = Minecraft.getInstance().font;

        scale = Math.min(1.5f, (float) Math.min(0.7, (double)(height - 48) / 300));

        int popupW = Math.min(220, (int)(width * 0.6));
        int popupH = Math.min(200, (int)((height - 48) * 0.6));
        int cx = width / 2;
        int cy = (height - 48) / 2;
        int left = cx - popupW / 2;
        int top = cy - popupH / 2;

        int boxW = (int)(80 * scale);
        int boxH = (int)(16 * scale);

        quantityBox = new EditBox(font, left + (int)(60 * scale), top + (int)(56 * scale), boxW, boxH, Component.literal("1"));
        quantityBox.setBordered(false);
        quantityBox.setValue("1");
        quantityBox.setResponder(s -> {
            try {
                int v = Integer.parseInt(s);
                if (v > 0) quantityCache = v;
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(quantityBox);

        int qtyBtnY = top + (int)(56 * scale) + boxH + (int)(10 * scale);
        int btnStartX = left + (int)(15 * scale);
        int btnWSmall = (int)(40 * scale);
        int btnWMed = (int)(50 * scale);
        int btnShortH = (int)(16 * scale);

        addRenderableWidget(new ModernButton(btnStartX, qtyBtnY, btnWSmall, btnShortH,
                Component.literal("x1"), btn -> quantityBox.setValue("1")));
        addRenderableWidget(new ModernButton(btnStartX + (int)(43 * scale), qtyBtnY, btnWSmall, btnShortH,
                Component.literal("x10"), btn -> quantityBox.setValue("10")));
        addRenderableWidget(new ModernButton(btnStartX + (int)(86 * scale), qtyBtnY, btnWSmall, btnShortH,
                Component.literal("x64"), btn -> quantityBox.setValue("64")));
        addRenderableWidget(new ModernButton(btnStartX + (int)(129 * scale), qtyBtnY, btnWMed, btnShortH,
                Component.literal("x999"), btn -> quantityBox.setValue("999")));

        int totalY = top + popupH - (int)(22 * scale);
        int btnH = (int)(20 * scale);
        int btnWBig = (int)(70 * scale);
        int confirmY = totalY - btnH - (int)(5 * scale);

        addRenderableWidget(new ModernButton(left + (int)(30 * scale), confirmY, btnWBig, btnH,
                Component.literal("Confirm"), btn -> confirmTrade()));
        addRenderableWidget(new ModernButton(left + (int)(120 * scale), confirmY, btnWBig, btnH,
                Component.literal("Cancel"), btn -> this.minecraft.setScreen(parentScreen)));
    }

    private void confirmTrade() {
        try {
            int quantity = Integer.parseInt(quantityBox.getValue());
            if (quantity <= 0) return;
            if ("barter".equals(entry.entryType()) && entry.barterItemId() != null) {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.TradePacket(
                        parentScreen.currentCategory, entry.itemId(), entry.tradeType(), quantity, 0));
            } else {
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.TradePacket(
                        parentScreen.currentCategory, entry.itemId(), entry.tradeType(), quantity, entry.price() * quantity));
            }
            this.minecraft.setScreen(parentScreen);
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, T.screenBackground);

        int popupW = Math.min(220, (int)(width * 0.6));
        int popupH = Math.min(200, (int)((height - 48) * 0.6));
        int cx = width / 2;
        int cy = (height - 48) / 2;
        int left = cx - popupW / 2;
        int top = cy - popupH / 2;

        fillRoundedRect(g, left, top, popupW, popupH, T.popupBg);
        g.fill(left + 2, top, left + popupW - 2, top + 1, T.accentStripe);

        String titleStr;
        if ("barter".equals(entry.entryType()) && entry.barterItemId() != null) {
            titleStr = "Barter - " + entry.getDisplayName().getString();
        } else {
            String tradeType = entry.tradeType() == ShopEntry.TradeType.BUY ? "Buy" : "Sell";
            titleStr = tradeType + " - " + entry.getDisplayName().getString();
        }
        drawCenteredText(g, font, titleStr, cx, top + (int)(8 * scale), T.textTitle);

        if (!("barter".equals(entry.entryType()) && entry.barterItemId() != null)) {
            drawText(g, font, "Price: @" + entry.price(), left + (int)(15 * scale), top + (int)(38 * scale), T.textPrice);
        }

        ItemStack stack = entry.createStack();
        int iconSize = (int)(32 * scale);
        int iconX = left + popupW / 2 - iconSize / 2;
        int iconY = top + (int)(24 * scale);
        ItemIcons.render(g, stack, iconX, iconY);

        drawText(g, font, "Quantity:", left + (int)(15 * scale), top + (int)(58 * scale), T.textPrimary);

        int total = entry.price() * quantityCache;
        drawCenteredText(g, font, "Total: @" + String.format("%,d", total), cx, top + popupH - (int)(22 * scale), T.textTotal);

        super.render(g, mx, my, pt);
    }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }

    private void drawCenteredText(GuiGraphics g, net.minecraft.client.gui.Font font, String text, int x, int y, int color) {
        int w = font.width(text);
        g.drawString(font, text, x - w / 2, y, color, true);
    }

    private void drawText(GuiGraphics g, net.minecraft.client.gui.Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
