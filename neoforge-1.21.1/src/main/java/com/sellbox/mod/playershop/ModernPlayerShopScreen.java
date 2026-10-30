package com.sellbox.mod.playershop;

import com.sellbox.mod.gui.GuiTheme;
import com.sellbox.mod.gui.ModernTheme;
import com.sellbox.mod.shop.ShopEntry;
import com.sellbox.mod.shop.ShopUI;
import com.sellbox.mod.util.ItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ModernPlayerShopScreen extends Screen {
    private final GuiTheme T = GuiTheme.get();

    private List<PlayerShopConfig.PlayerShopInfo> shops = new ArrayList<>();
    private List<ShopEntry> currentItems = new ArrayList<>();
    private String myShopName = "";
    private int balance = 0;
    private int selectedShopIndex = -1;

    private EditBox searchBox;
    private String searchQuery = "";
    private float scale;

    // 布局
    private int left, top, panelW, panelH;
    private int leftPanelW, rightX, rightW;
    private int catStartY, catEndY;
    private int gridStartX, gridStartY, gridW, gridH;
    private int cellW, cellH;

    public ModernPlayerShopScreen() {
        super(Component.literal("Player Shop"));
    }

    public void setData(List<PlayerShopConfig.PlayerShopInfo> shops, List<String> shopNames,
                        List<ShopEntry> items, int balance) {
        this.shops = new ArrayList<>(shops);
        this.currentItems = new ArrayList<>(items);
        this.balance = balance;
        this.myShopName = shopNames.isEmpty() ? "" : shopNames.get(0);
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft == null) minecraft = Minecraft.getInstance();
        if (font == null) font = Minecraft.getInstance().font;

        scale = (float) ShopUI.getScale(height);

        // 面板布局
        panelW = Math.max(380, Math.round(440 * scale));
        panelH = Math.max(260, Math.round(280 * scale));
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        leftPanelW = Math.round(110 * scale);
        rightX = left + leftPanelW;
        rightW = panelW - leftPanelW;

        // 搜索框
        int searchH = Math.round(20 * scale);
        searchBox = new EditBox(font, rightX + 10, top + 8, rightW - 24, searchH, Component.literal(""));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setResponder(s -> {
            searchQuery = s.toLowerCase().trim();
        });
        addRenderableWidget(searchBox);

        catStartY = top + 36;
        catEndY = top + panelH - 40;

        gridStartX = rightX + 8;
        gridStartY = top + 36;
        cellW = Math.round(48 * scale);
        cellH = Math.round(52 * scale);
        gridW = rightW - 16;
        gridH = panelH - 44;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 半透明背景蒙版
        g.fill(0, 0, width, height, T.screenBackground);

        // 左侧面板背景
        fillRoundedRect(g, left, top, leftPanelW, panelH, T.leftPanelBg);
        // 右侧主面板背景
        fillRoundedRect(g, rightX, top, rightW, panelH, T.mainPanelBg);

        // 分割线
        g.fill(rightX - 1, top + 4, rightX, top + panelH - 4, T.panelDivider);

        renderShopList(g, mouseX, mouseY);
        renderSearchBox(g);
        renderGrid(g, mouseX, mouseY);
        renderBalance(g);
        renderMoneyHud(g);

        super.render(g, mouseX, mouseY, partialTick);
    }

    // ════════════════════════════════════════════
    //  左侧商店列表
    // ════════════════════════════════════════════
    private void renderShopList(GuiGraphics g, int mx, int my) {
        // 标题
        drawCenteredText(g, font, "SHOPS", left + leftPanelW / 2, top + 10, T.catTitleText);

        // 分割线
        g.fill(left + 8, top + 22, left + leftPanelW - 8, top + 23, T.separatorLine);

        int y = catStartY;
        int catH = Math.round(28 * scale);

        // "我的商店" 优先
        if (!myShopName.isEmpty()) {
            boolean selected = selectedShopIndex == 0;
            boolean hovered = mx >= left + 4 && mx <= left + leftPanelW - 4
                    && my >= y && my <= y + catH;

            if (selected) {
                g.fill(left + 4, y, left + leftPanelW - 4, y + catH, T.catSelectedFill);
            } else if (hovered) {
                g.fill(left + 4, y, left + leftPanelW - 4, y + catH, T.catHoveredFill);
            }

            renderShopIcon(g, "mine", left + 12, y + (catH - 10) / 2);
            drawText(g, font, myShopName, left + 28, y + (catH - 8) / 2, T.myShopLabel);
            y += catH + 4;
        }

        // 其他商店
        for (int i = 0; i < shops.size(); i++) {
            if (y + catH > catEndY) break;
            PlayerShopConfig.PlayerShopInfo shop = shops.get(i);

            boolean selected = selectedShopIndex == i + 1;
            boolean hovered = mx >= left + 4 && mx <= left + leftPanelW - 4
                    && my >= y && my <= y + catH;

            if (selected) {
                g.fill(left + 4, y, left + leftPanelW - 4, y + catH, T.catSelectedFill);
            } else if (hovered) {
                g.fill(left + 4, y, left + leftPanelW - 4, y + catH, T.catHoveredFill);
            }

            renderShopIcon(g, "other", left + 12, y + (catH - 10) / 2);
            drawText(g, font, shop.shopName(), left + 28, y + (catH - 8) / 2, T.catText);
            y += catH + 4;
        }

        // 底部添加按钮
        int addBtnY = top + panelH - 32;
        int addBtnH = 22;
        boolean addHovered = mx >= left + 8 && mx <= left + leftPanelW - 8
                && my >= addBtnY && my <= addBtnY + addBtnH;

        g.fill(left + 8, addBtnY, left + leftPanelW - 8, addBtnY + addBtnH,
                addHovered ? T.btnBgGreenHovered : T.btnBgGreenNormal);
        drawCenteredText(g, font, "+ New Shop", left + leftPanelW / 2, addBtnY + 7, T.textPrice);
    }

    private void renderShopIcon(GuiGraphics g, String type, int x, int y) {
        int color;
        switch (type) {
            case "mine" -> color = T.myShopLabel;
            case "other" -> color = T.textCategoryName;
            default -> color = T.textMuted;
        }
        g.fill(x, y, x + 10, y + 10, color);
    }

    // ════════════════════════════════════════════
    //  搜索框
    // ════════════════════════════════════════════
    private void renderSearchBox(GuiGraphics g) {
        int x = rightX + 10;
        int y = top + 8;
        int w = rightW - 24;
        int h = Math.round(20 * scale);

        g.fill(x, y, x + w, y + h, T.leftPanelBg);
        boolean focused = searchBox != null && searchBox.isFocused();
        g.fill(x, y, x + w, y + 1, focused ? T.inputBorderFocused : T.inputBorderUnfocused);
        g.fill(x, y + h - 1, x + w, y + h, focused ? T.inputBorderFocused : T.inputBorderUnfocused);
        g.fill(x, y, x + 1, y + h, focused ? T.inputBorderFocused : T.inputBorderUnfocused);
        g.fill(x + w - 1, y, x + w, y + h, focused ? T.inputBorderFocused : T.inputBorderUnfocused);

        if (searchBox != null && searchBox.getValue().isEmpty() && !focused) {
            drawText(g, font, "What are you looking for?", x + 8, y + 6, T.textHint);
        }
    }

    // ════════════════════════════════════════════
    //  物品网格
    // ════════════════════════════════════════════
    private void renderGrid(GuiGraphics g, int mx, int my) {
        List<ShopEntry> filtered = getFilteredItems();
        if (filtered.isEmpty()) {
            drawCenteredText(g, font, "No items", gridStartX + gridW / 2, gridStartY + gridH / 2, T.textDisabled);
            return;
        }

        int cols = Math.max(1, gridW / (cellW + 8));
        int startX = gridStartX + (gridW - cols * (cellW + 8) + 8) / 2;
        int y = gridStartY;

        for (int i = 0; i < filtered.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = startX + col * (cellW + 8);
            int cy = y + row * (cellH + 8);

            if (cy + cellH > top + panelH - 8) break;

            renderCell(g, filtered.get(i), x, cy, mx, my);
        }
    }

    private void renderCell(GuiGraphics g, ShopEntry item, int x, int y, int mx, int my) {
        boolean hovered = mx >= x && mx <= x + cellW && my >= y && my <= y + cellH;

        g.fill(x, y, x + cellW, y + cellH, hovered ? T.cellHoveredFill : T.leftPanelBg);
        g.fill(x, y, x + cellW, y + 1, T.cellBorder);
        g.fill(x, y + cellH - 1, x + cellW, y + cellH, T.cellBorder);
        g.fill(x, y, x + 1, y + cellH, T.cellBorder);
        g.fill(x + cellW - 1, y, x + cellW, y + cellH, T.cellBorder);

        // 物品图标
        ItemStack stack = item.createStack();
        int iconSize = Math.min(32, cellW - 8);
        int iconX = x + (cellW - iconSize) / 2;
        int iconY = y + 4;
        ItemIcons.render(g, stack, iconX, iconY);

        // 交易类型
        String label = item.tradeType() == ShopEntry.TradeType.BUY ? "Buy" : "Sell";
        int labelColor = item.tradeType() == ShopEntry.TradeType.BUY ? T.textPrice : 0xFFFF9800;
        drawCenteredText(g, font, label, x + cellW / 2, y + cellH - 18, labelColor);

        // 价格
        String priceStr = "@" + item.price();
        drawCenteredText(g, font, priceStr, x + cellW / 2, y + cellH - 8, T.textTotal);
    }

    // ════════════════════════════════════════════
    //  余额
    // ════════════════════════════════════════════
    private void renderBalance(GuiGraphics g) {
        int x = left + 6;
        int y = top + panelH - 56;
        int w = leftPanelW - 12;
        int h = 24;

        g.fill(x, y, x + w, y + h, T.leftPanelBg);
        g.fill(x, y, x + w, y + 1, T.balanceBorder);

        drawCenteredText(g, font, "Balance", x + w / 2, y + 3, T.textMuted);
        drawCenteredText(g, font, "@ " + String.format("%,d", balance), x + w / 2, y + 13, T.balanceText);
    }

    // ════════════════════════════════════════════
    //  底部金钱HUD
    // ════════════════════════════════════════════
    private void renderMoneyHud(GuiGraphics g) {
        int x = left;
        int y = top + panelH + 8;
        int w = 160;
        int h = 28;

        g.fill(x, y, x + w, y + h, T.ctxMenuBg);
        g.fill(x, y, x + w, y + 1, T.balanceBorder);

        drawText(g, font, "Money:", x + 8, y + 5, T.textMuted);
        drawText(g, font, "@ " + String.format("%,d", balance), x + 60, y + 5, T.balanceText);
    }

    // ════════════════════════════════════════════
    //  辅助方法
    // ════════════════════════════════════════════
    private List<ShopEntry> getFilteredItems() {
        if (searchQuery.isEmpty()) return currentItems;
        return currentItems.stream()
                .filter(item -> {
                    ItemStack stack = item.createStack();
                    return stack.getHoverName().getString().toLowerCase().contains(searchQuery)
                            || item.itemId().toString().contains(searchQuery);
                })
                .toList();
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
    public boolean mouseClicked(double mx, double my, int button) {
        int catH = Math.round(28 * scale);
        int y = catStartY;

        // 跳过"我的商店"
        if (!myShopName.isEmpty()) {
            if (mx >= left + 4 && mx <= left + leftPanelW - 4 && my >= y && my <= y + catH) {
                selectedShopIndex = 0;
                return true;
            }
            y += catH + 4;
        }

        // 其他商店
        for (int i = 0; i < shops.size(); i++) {
            if (y + catH > catEndY) break;
            if (mx >= left + 4 && mx <= left + leftPanelW - 4 && my >= y && my <= y + catH) {
                selectedShopIndex = i + 1;
                return true;
            }
            y += catH + 4;
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(null);
    }
}
