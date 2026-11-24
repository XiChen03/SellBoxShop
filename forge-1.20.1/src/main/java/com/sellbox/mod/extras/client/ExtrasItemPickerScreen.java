package com.sellbox.mod.extras.client;

import com.sellbox.mod.gui.GuiDraw;
import com.sellbox.mod.util.FontUtil;
import com.sellbox.mod.util.ItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 扩展模块的背包选物品面板：只保留「背包选择 + ID相同/严格NBT」，
 * 不带价格、分类、排序等商店设置，避免把商店编辑面板整套搬过来。
 */
public class ExtrasItemPickerScreen extends Screen {
    private static final int DESIGN_W = 360;
    private static final int DESIGN_H = 250;
    private static final int COLS = 9;
    private static final int ROWS = 5;
    private static final int CELL = 22;

    private final ExtrasScreen parent;
    private final BiConsumer<ItemStack, Boolean> onSelect;
    private final List<ItemStack> items = new ArrayList<>();
    private boolean strictNbt = false;
    private int scroll = 0;
    private float sf = 1f;
    private int panelLeft, panelTop;
    private int hoverIndex = -1;

    public ExtrasItemPickerScreen(ExtrasScreen parent, BiConsumer<ItemStack, Boolean> onSelect) {
        super(Component.literal("选择物品"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        items.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            for (ItemStack stack : mc.player.getInventory().items) {
                if (!stack.isEmpty()) items.add(stack.copy());
            }
            for (ItemStack stack : mc.player.getInventory().offhand) {
                if (!stack.isEmpty()) items.add(stack.copy());
            }
        }
        sf = Math.max(0.55f, Math.min(2.0f, Math.min(width / (float) (DESIGN_W + 30),
                height / (float) (DESIGN_H + 30))));
        panelLeft = (int) ((width - DESIGN_W * sf) / 2);
        panelTop = (int) ((height - DESIGN_H * sf) / 2);
    }

    // 兼容 1.20.1 / 1.21.1 两种背景签名：都不画，由 render 里的 scrim 统一处理
    public void renderBackground(GuiGraphics g) {
    }

    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0000000);
        g.pose().pushPose();
        g.pose().translate(panelLeft, panelTop, 0);
        g.pose().scale(sf, sf, 1f);

        GuiDraw.panelWithStripe(g, 0, 0, DESIGN_W, DESIGN_H);
        g.drawString(font, "选择物品", 12, 8, 0xFFFFFFFF, GuiDraw.shadow());
        drawToggle(g, 202, 22, 64, "ID相同", !strictNbt, mx, my);
        drawToggle(g, 270, 22, 64, "严格NBT", strictNbt, mx, my);
        g.drawString(font, "选背包里的物品；严格NBT会保留附魔/自定义名等数据", 12, 44,
                0xFF9AA7BC, GuiDraw.shadow());

        int gridW = COLS * CELL;
        int gridX = (DESIGN_W - gridW) / 2;
        int gridY = 64;
        int vis = COLS * ROWS;
        int maxScroll = Math.max(0, items.size() - vis);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        // 网格框
        GuiDraw.fillRounded(g, gridX - 4, gridY - 4, gridW + 8, ROWS * CELL + 8, 0x55000000);
        GuiDraw.borderRounded(g, gridX - 4, gridY - 4, gridW + 8, ROWS * CELL + 8, 0x66FFFFFF);
        hoverIndex = -1;
        int mdx = (int) ((mx - panelLeft) / sf);
        int mdy = (int) ((my - panelTop) / sf);
        for (int i = scroll; i < items.size() && i < scroll + vis; i++) {
            int local = i - scroll;
            int x = gridX + (local % COLS) * CELL;
            int y = gridY + (local / COLS) * CELL;
            boolean hover = mdx >= x && mdx < x + CELL && mdy >= y && mdy < y + CELL;
            if (hover) {
                hoverIndex = i;
                GuiDraw.fillRounded(g, x + 1, y + 1, CELL - 2, CELL - 2, 0x66FFFFFF);
            }
            GuiDraw.borderRounded(g, x, y, CELL, CELL, 0x33FFFFFF);
            ItemStack stack = items.get(i);
            ItemIcons.render(g, stack, x + 3, y + 3);
            g.renderItemDecorations(font, stack, x + 3, y + 3);
        }
        if (items.isEmpty()) {
            g.drawString(font, "背包里没有可选择的物品", gridX, gridY + 8, 0xFF9AA7BC, GuiDraw.shadow());
        }

        // 返回
        GuiDraw.fillRounded(g, DESIGN_W - 78, DESIGN_H - 26, 64, 18, 0xFF334A66);
        GuiDraw.borderRounded(g, DESIGN_W - 78, DESIGN_H - 26, 64, 18, 0x66FFFFFF);
        g.drawString(font, "返回", DESIGN_W - 61, DESIGN_H - 20, 0xFFFFFFFF, GuiDraw.shadow());

        g.pose().popPose();

        if (hoverIndex >= 0) {
            g.renderTooltip(font, items.get(hoverIndex), mx, my);
        }
    }

    private void drawToggle(GuiGraphics g, int x, int y, int w, String label, boolean on, int mx, int my) {
        int mdx = (int) ((mx - panelLeft) / sf);
        int mdy = (int) ((my - panelTop) / sf);
        boolean hover = mdx >= x && mdx < x + w && mdy >= y && mdy < y + 18;
        GuiDraw.fillRounded(g, x, y, w, 18, on ? 0xFF4C6A96 : (hover ? 0xFF3A4A62 : 0xFF263244));
        GuiDraw.borderRounded(g, x, y, w, 18, 0x66FFFFFF);
        FontUtil.drawAutoFitCentered(g, font, label, x + w / 2, y + 4, w - 6, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int mdx = (int) ((mx - panelLeft) / sf);
        int mdy = (int) ((my - panelTop) / sf);
        if (button != 0) return false;
        if (mdx >= 202 && mdx < 266 && mdy >= 22 && mdy < 40) {
            strictNbt = false;
            return true;
        }
        if (mdx >= 270 && mdx < 334 && mdy >= 22 && mdy < 40) {
            strictNbt = true;
            return true;
        }
        if (mdx >= DESIGN_W - 78 && mdx < DESIGN_W - 14 && mdy >= DESIGN_H - 26 && mdy < DESIGN_H - 8) {
            close();
            return true;
        }
        int gridW = COLS * CELL;
        int gridX = (DESIGN_W - gridW) / 2;
        int gridY = 64;
        for (int i = scroll; i < items.size() && i < scroll + COLS * ROWS; i++) {
            int local = i - scroll;
            int x = gridX + (local % COLS) * CELL;
            int y = gridY + (local / COLS) * CELL;
            if (mdx >= x && mdx < x + CELL && mdy >= y && mdy < y + CELL) {
                if (onSelect != null) onSelect.accept(items.get(i).copy(), strictNbt);
                close();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, items.size() - COLS * ROWS);
        if (delta < 0) scroll = Math.min(scroll + COLS, max);
        else if (delta > 0) scroll = Math.max(0, scroll - COLS);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void close() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
