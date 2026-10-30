package com.sellbox.mod.shop;

import com.sellbox.mod.util.FontUtil;
import com.sellbox.mod.util.ItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PlayerInventoryPickerScreen extends Screen {
    private static final float ZOOM = 1.5f; // 整体放大倍率
    private final Screen parentScreen;
    private final Consumer<ItemStack> onSelect;
    private List<ItemStack> inventoryItems = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int COLS = 9;
    private static final int ROWS = 4;
    private int cellW = 20;
    private int cellH = 20;
    private float sf;
    private int gridLeft, gridTop, panelLeft, panelTop, panelW, panelH;

    public PlayerInventoryPickerScreen(Screen parent, Consumer<ItemStack> onSelect) {
        super(Component.literal("选择背包物品"));
        this.parentScreen = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        sf = (float) ShopUI.getScale(height) * ZOOM;
        cellW = (int) Math.round(20 * sf);
        cellH = (int) Math.round(20 * sf);

        // 收集玩家背包物品（保留 NBT）
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
                btn -> minecraft.setScreen(parentScreen)));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        fillRoundedRect(g, panelLeft, panelTop, panelW, panelH, 0xFF333333);
        g.fill(panelLeft + 2, panelTop, panelLeft + panelW - 2, panelTop + 1, 0xFFFFAA00);
        super.render(g, mx, my, pt);

        FontUtil.drawAutoFitCentered(g, font, title.getString(), panelLeft + panelW / 2, panelTop + (int)(6 * sf), panelW - 10, 0xFFFFAA);

        // 背包物品网格
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

            // 边框
            g.fill(x + 2, y, x + cellW - 2, y + 1, 0xFF444444);
            g.fill(x + 2, y + cellH - 1, x + cellW - 2, y + cellH, 0xFF444444);
            g.fill(x, y + 2, x + 1, y + cellH - 2, 0xFF444444);
            g.fill(x + cellW - 1, y + 2, x + cellW, y + cellH - 2, 0xFF444444);

            ItemStack stack = inventoryItems.get(i);
            // 物品图标：放大 ZOOM 倍并居中（16px → 16*ZOOM px）
            g.pose().pushPose();
            g.pose().translate(x + cellW / 2.0f, y + cellH / 2.0f, 0);
            g.pose().scale(ZOOM, ZOOM, 1.0f);
            ItemIcons.render(g, stack, -8, -8, x + y * 64);
            g.pose().popPose();

            // 物品数量：右下角显示且置顶（避免被物品图标遮挡）
            if (stack.getCount() > 1) {
                String countStr = String.valueOf(stack.getCount());
                int qtyX = x + (cellW + 16) / 2 - font.width(countStr) + 7;
                int qtyY = y + (cellH + 16) / 2 - 4;
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.pose().translate(qtyX, qtyY, 0);
                g.drawString(font, countStr, 0, 0, 0xFFFFFF, true);
                g.pose().popPose();
            }
        }

        // 物品提示
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
                minecraft.setScreen(parentScreen);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int vis = COLS * ROWS;
        int max = Math.max(0, inventoryItems.size() - vis);
        if (sy < 0) scrollOffset = Math.min(scrollOffset + COLS, max);
        else if (sy > 0) scrollOffset = Math.max(0, scrollOffset - COLS);
        return true;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == 256) { minecraft.setScreen(parentScreen); return true; }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }
}
