package com.sellbox.mod.shop;

import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PlayerInventoryPickerScreen extends Screen {
    private final Screen parentScreen;
    private final Consumer<ItemStack> onSelect;
    private List<ItemStack> inventoryItems = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int COLS = 9;
    private static final int ROWS = 4;
    private int cellW = 20;
    private int cellH = 20;
    private float sf;
    private static final float ITEM_ZOOM = 1.5f;
    private int gridLeft, gridTop, panelLeft, panelTop, panelW, panelH;

    public PlayerInventoryPickerScreen(Screen parent, Consumer<ItemStack> onSelect) {
        super(Component.literal("选择背包物品"));
        this.parentScreen = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        sf = (float) ShopUI.getScale(height);
        cellW = (int) Math.round(20 * sf * ITEM_ZOOM);
        cellH = (int) Math.round(20 * sf * ITEM_ZOOM);

        inventoryItems.clear();
        if (minecraft.player != null) {
            for (ItemStack stack : minecraft.player.getInventory().items) {
                if (!stack.isEmpty()) {
                    inventoryItems.add(stack.copy());
                }
            }
        }

        int gridW = COLS * cellW;
        int gridH = ROWS * cellH;
        int pad = (int) Math.round(12 * sf);
        panelW = gridW + pad * 2;
        panelH = (int) Math.round(26 * sf) + (int) Math.round(8 * sf) + gridH + (int) Math.round(8 * sf) + (int) Math.round(18 * sf) + (int) Math.round(6 * sf);
        panelLeft = (width - panelW) / 2;
        panelTop = (height - panelH) / 2;
        gridLeft = panelLeft + pad;
        gridTop = panelTop + (int) Math.round(26 * sf) + (int) Math.round(6 * sf);

        addRenderableWidget(new ShopButton(panelLeft + panelW / 2 - (int)(30 * sf), gridTop + gridH + (int)(6 * sf),
                (int)(60 * sf), (int)(18 * sf), Component.literal("返回"),
                btn -> { if (minecraft == null) minecraft = Minecraft.getInstance(); minecraft.setScreen(parentScreen); }));
    }

    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0xC0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        fillRoundedRect(g, panelLeft, panelTop, panelW, panelH, 0xFF333333);
        g.fill(panelLeft + 2, panelTop, panelLeft + panelW - 2, panelTop + 1, 0xFFFFAA00);
        super.render(g, mx, my, pt);

        FontUtil.drawAutoFitCentered(g, font, title.getString(), panelLeft + panelW / 2, panelTop + (int)(6 * sf), panelW - 10, 0xFFFFAA);

        int startIdx = scrollOffset;
        int vis = COLS * ROWS;
        int endIdx = Math.min(startIdx + vis, inventoryItems.size());

        for (int i = startIdx; i < endIdx; i++) {
            int local = i - startIdx;
            int col = local % COLS;
            int row = local / COLS;
            int x = gridLeft + col * cellW;
            int y = gridTop + row * cellH;

            boolean hovered = mx >= x && mx < x + cellW && my >= y && my < y + cellH;
            if (hovered) {
                g.fill(x + 1, y + 1, x + cellW - 1, y + cellH - 1, 0x404488FF);
            }

            g.fill(x + 2, y, x + cellW - 2, y + 1, 0xFF444444);
            g.fill(x + 2, y + cellH - 1, x + cellW - 2, y + cellH, 0xFF444444);
            g.fill(x, y + 2, x + 1, y + cellH - 2, 0xFF444444);
            g.fill(x + cellW - 1, y + 2, x + cellW, y + cellH - 2, 0xFF444444);

            ItemStack stack = inventoryItems.get(i);
            // 放大渲染物品图标
            int iconSize = 16;
            int scaledSize = (int) Math.round(iconSize * ITEM_ZOOM);
            int iconX = x + (cellW - scaledSize) / 2;
            int iconY = y + (cellH - scaledSize) / 2;
            g.pose().pushPose();
            g.pose().translate(iconX, iconY, 0);
            g.pose().scale(ITEM_ZOOM, ITEM_ZOOM, 1);
            g.renderFakeItem(stack, 0, 0);
            g.pose().popPose();
            // 数量标签：放大图标的右下角、原版大小、置顶显示（renderItemDecorations 自带描边阴影）
            g.renderItemDecorations(font, stack, iconX, iconY);
        }

        for (int i = startIdx; i < endIdx; i++) {
            int local = i - startIdx;
            int col = local % COLS;
            int row = local / COLS;
            int x = gridLeft + col * cellW;
            int y = gridTop + row * cellH;
            if (mx >= x && mx < x + cellW && my >= y && my < y + cellH) {
                g.renderTooltip(font, inventoryItems.get(i), mx, my);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int startIdx = scrollOffset;
        int vis = COLS * ROWS;
        int endIdx = Math.min(startIdx + vis, inventoryItems.size());
        for (int i = startIdx; i < endIdx; i++) {
            int local = i - startIdx;
            int col = local % COLS;
            int row = local / COLS;
            int x = gridLeft + col * cellW;
            int y = gridTop + row * cellH;
            if (mx >= x && mx < x + cellW && my >= y && my < y + cellH) {
                if (onSelect != null) onSelect.accept(inventoryItems.get(i));
                if (minecraft == null) minecraft = Minecraft.getInstance();
                minecraft.setScreen(parentScreen);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int vis = COLS * ROWS;
        int max = Math.max(0, inventoryItems.size() - vis);
        if (delta < 0) scrollOffset = Math.min(scrollOffset + COLS, max);
        else if (delta > 0) scrollOffset = Math.max(0, scrollOffset - COLS);
        return true;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == 256) {
            if (minecraft == null) minecraft = Minecraft.getInstance();
            minecraft.setScreen(parentScreen);
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }
}