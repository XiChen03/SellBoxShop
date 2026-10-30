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

import java.util.ArrayList;
import java.util.List;

public class ModernShopScreen extends Screen {
    private final GuiTheme T = GuiTheme.get();

    String currentCategory = "";
    private List<String> categories = new ArrayList<>();
    private List<ShopEntry> currentEntries = new ArrayList<>();
    private int balance = 0;

    private EditBox searchBox;
    private String searchQuery = "";
    private int scrollOffset = 0;
    private boolean editMode = false;
    private float scale;

    private int left, top, panelW, panelH;
    private int leftPanelW, rightX, rightW;
    private int catStartY, catEndY;
    private int gridStartX, gridStartY, gridW, gridH;
    private int cellW, cellH;

    public ModernShopScreen() {
        super(Component.literal("Shop"));
    }

    public void setData(String category, List<ShopEntry> entries, int balance, List<String> categories) {
        this.currentCategory = category;
        this.categories = new ArrayList<>(categories);
        this.currentEntries = new ArrayList<>(entries);
        this.balance = balance;
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft == null) minecraft = Minecraft.getInstance();
        if (font == null) font = Minecraft.getInstance().font;

        scale = (float) ShopUI.getScale(height);

        panelW = Math.max(380, Math.round(440 * scale));
        panelH = Math.max(260, Math.round(280 * scale));
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        leftPanelW = Math.round(110 * scale);
        rightX = left + leftPanelW;
        rightW = panelW - leftPanelW;

        int searchH = Math.round(20 * scale);
        searchBox = new EditBox(font, rightX + 10, top + 8, rightW - 24, searchH, Component.literal(""));
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setResponder(s -> {
            searchQuery = s.toLowerCase().trim();
            scrollOffset = 0;
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

        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopOpenPacket(currentCategory));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, T.screenBackground);

        fillRoundedRect(g, left, top, leftPanelW, panelH, T.leftPanelBg);
        fillRoundedRect(g, rightX, top, rightW, panelH, T.mainPanelBg);

        g.fill(rightX - 1, top + 4, rightX, top + panelH - 4, T.panelDivider);

        renderCategories(g, mouseX, mouseY);
        renderSearchBox(g);
        renderGrid(g, mouseX, mouseY);
        renderBalance(g);
        renderMoneyHud(g);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderCategories(GuiGraphics g, int mx, int my) {
        String title = "CATEGORIES";
        drawCenteredText(g, font, title, left + leftPanelW / 2, top + 10, T.catTitleText);

        g.fill(left + 8, top + 22, left + leftPanelW - 8, top + 23, T.separatorLine);

        int y = catStartY;
        int catH = Math.round(28 * scale);

        for (int i = 0; i < categories.size(); i++) {
            if (y + catH > catEndY) break;

            String cat = categories.get(i);
            boolean selected = cat.equals(currentCategory);
            boolean hovered = mx >= left + 4 && mx <= left + leftPanelW - 4
                    && my >= y && my <= y + catH;

            if (selected) {
                g.fill(left + 4, y, left + leftPanelW - 4, y + catH, T.catSelectedFill);
            } else if (hovered) {
                g.fill(left + 4, y, left + leftPanelW - 4, y + catH, T.catHoveredFill);
            }

            int textX = left + 28;
            int textColor = selected ? T.textPrimary : T.catText;

            renderCategoryIcon(g, cat, left + 12, y + (catH - 12) / 2);
            drawText(g, font, cat, textX, y + (catH - 8) / 2, textColor);
            y += catH + 4;
        }

        int addBtnY = top + panelH - 32;
        int addBtnH = 22;
        boolean addHovered = mx >= left + 8 && mx <= left + leftPanelW - 8
                && my >= addBtnY && my <= addBtnY + addBtnH;

        g.fill(left + 8, addBtnY, left + leftPanelW - 8, addBtnY + addBtnH,
                addHovered ? T.btnBgGreenHovered : T.btnBgGreenNormal);
        drawCenteredText(g, font, "+ Create", left + leftPanelW / 2, addBtnY + 7, T.textPrice);
    }

    private void renderCategoryIcon(GuiGraphics g, String cat, int x, int y) {
        int color;
        switch (cat.toLowerCase()) {
            case "culture" -> color = 0xFF8BC34A;
            case "chickens" -> color = 0xFFFFC107;
            case "animals" -> color = 0xFF795548;
            case "create" -> color = 0xFF4CAF50;
            default -> color = 0xFF90CAF9;
        }
        g.fill(x, y, x + 10, y + 10, color);
    }

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

    private void renderGrid(GuiGraphics g, int mx, int my) {
        List<ShopEntry> filtered = getFilteredEntries();
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

    private void renderCell(GuiGraphics g, ShopEntry entry, int x, int y, int mx, int my) {
        boolean hovered = mx >= x && mx <= x + cellW && my >= y && my <= y + cellH;

        g.fill(x, y, x + cellW, y + cellH, hovered ? T.cellHoveredFill : T.leftPanelBg);
        g.fill(x, y, x + cellW, y + 1, T.cellBorder);
        g.fill(x, y + cellH - 1, x + cellW, y + cellH, T.cellBorder);
        g.fill(x, y, x + 1, y + cellH, T.cellBorder);
        g.fill(x + cellW - 1, y, x + cellW, y + cellH, T.cellBorder);

        ItemStack stack = entry.createStack();
        int iconSize = Math.min(32, cellW - 8);
        int iconX = x + (cellW - iconSize) / 2;
        int iconY = y + 4;
        ItemIcons.render(g, stack, iconX, iconY);

        String label = entry.tradeType() == ShopEntry.TradeType.BUY ? "Buy" : "Sell";
        int labelColor = entry.tradeType() == ShopEntry.TradeType.BUY ? T.textPrice : 0xFFFF9800;
        drawCenteredText(g, font, label, x + cellW / 2, y + cellH - 18, labelColor);

        String priceStr = "@" + entry.price();
        drawCenteredText(g, font, priceStr, x + cellW / 2, y + cellH - 8, T.textTotal);
    }

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

    private List<ShopEntry> getFilteredEntries() {
        if (searchQuery.isEmpty()) return currentEntries;
        return currentEntries.stream()
                .filter(e -> e.getDisplayName().getString().toLowerCase().contains(searchQuery)
                        || e.itemId().toString().contains(searchQuery))
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

    public void updateData(String category, List<ShopEntry> entries, int balance, List<String> categories) {
        this.currentCategory = category;
        this.categories = new ArrayList<>(categories);
        this.currentEntries = new ArrayList<>(entries);
        this.balance = balance;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int catH = Math.round(28 * scale);
        int y = catStartY;
        for (String cat : categories) {
            if (y + catH > catEndY) break;
            if (mx >= left + 4 && mx <= left + leftPanelW - 4 && my >= y && my <= y + catH) {
                currentCategory = cat;
                scrollOffset = 0;
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopOpenPacket(cat));
                return true;
            }
            y += catH + 4;
        }

        List<ShopEntry> filtered = getFilteredEntries();
        int cols = Math.max(1, gridW / (cellW + 8));
        int startX = gridStartX + (gridW - cols * (cellW + 8) + 8) / 2;
        int gy = gridStartY;

        for (int i = 0; i < filtered.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int gx = startX + col * (cellW + 8);
            int cy = gy + row * (cellH + 8);

            if (mx >= gx && mx <= gx + cellW && my >= cy && my <= cy + cellH) {
                minecraft.setScreen(new ModernTradeConfirmScreen(this, filtered.get(i)));
                return true;
            }
            if (cy + cellH > top + panelH - 8) break;
        }

        return super.mouseClicked(mx, my, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, scrollOffset + (scrollY < 0 ? 1 : -1));
        return true;
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
