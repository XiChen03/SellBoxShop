package com.sellbox.mod.shop;

import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 交易限量设置窗口。
 *
 * <p>版式（虚拟设计空间 260 x 190，整体按 {@link ShopUI#getScale(int)} 缩放）：
 * <pre>
 *   ────────────────────────────  标题：数量限制
 *   [       输入框        ]     第一行
 *  [ 无限 ] [ 全体限制 ] [ 单人限制 ]   第二行
 *        [ 取消 ] [ 确认 ]       第三行
 * </pre>
 *
 * <p>默认为「无限」；选中无限时输入框只绘制不接受输入。
 */
public class SetItemLimitScreen extends Screen {

    /** 限量模式 */
    public enum LimitMode {
        UNLIMITED("无限"),
        GLOBAL("全体限制"),
        PER_PLAYER("单人限制");

        private final String label;

        LimitMode(String label) { this.label = label; }

        public String label() { return label; }

        public static LimitMode of(boolean hasLimit, boolean perPlayer) {
            if (!hasLimit) return UNLIMITED;
            return perPlayer ? PER_PLAYER : GLOBAL;
        }
    }

    private static final int DESIGN_W = 260;
    private static final int DESIGN_H = 190;

    private final ShopScreen parentScreen;
    private final String categoryName;
    private final int editIndex;
    private final int initialLimit;

    private int popupW = DESIGN_W;
    private int popupH = DESIGN_H;

    private EditBox limitBox;
    private LimitMode mode;

    /** 三个模式按钮，单独存引用以便按选中态描高亮边 */
    private final AbstractWidget[] modeButtons = new AbstractWidget[3];

    public SetItemLimitScreen(ShopScreen parent, String categoryName, int editIndex, ShopEntry editEntry) {
        super(Component.literal("数量限制"));
        this.parentScreen = parent;
        this.categoryName = categoryName;
        this.editIndex = editIndex;
        this.initialLimit = editEntry == null ? 0 : editEntry.tradeLimit();
        this.mode = LimitMode.of(editEntry != null && editEntry.hasTradeLimit(),
                editEntry != null && editEntry.limitPerPlayer());
    }

    @Override
    protected void init() {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        double scale = ShopUI.getScale(height);
        popupW = (int) Math.round(DESIGN_W * scale);
        popupH = (int) Math.round(DESIGN_H * scale);
        int cx = width / 2;
        int top = height / 2 - popupH / 2;
        float sf = (float) scale;

        int boxW = (int) Math.round(90 * sf);
        int boxH = (int) Math.round(16 * sf);
        int boxX = cx - boxW / 2;
        int boxY = top + (int) (52 * sf);

        limitBox = new EditBox(font, boxX, boxY, boxW, boxH, Component.literal("0"));
        limitBox.setBordered(false);
        limitBox.setFilter(s -> s.matches("\\d*"));
        if (initialLimit > 0) limitBox.setValue(String.valueOf(initialLimit));
        applyModeToBox();
        addRenderableWidget(limitBox);

        // 第二行：三个模式按钮
        int btnH = (int) Math.round(20 * sf);
        int modeBtnW = (int) Math.round(70 * sf);
        int modeGap = (int) Math.round(10 * sf);
        int modeGroupW = modeBtnW * 3 + modeGap * 2;
        int modeStartX = cx - modeGroupW / 2;
        int modeY = top + (int) (92 * sf);
        LimitMode[] modes = LimitMode.values();
        for (int i = 0; i < modes.length; i++) {
            final LimitMode m = modes[i];
            modeButtons[i] = new ShopButton(modeStartX + i * (modeBtnW + modeGap), modeY, modeBtnW, btnH,
                    Component.literal(m.label()), b -> selectMode(m));
            addRenderableWidget(modeButtons[i]);
        }

        // 第三行：取消 / 确认
        int btnW = (int) Math.round(70 * sf);
        int btnGap = (int) Math.round(20 * sf);
        int groupW = btnW * 2 + btnGap;
        int btnStartX = cx - groupW / 2;
        int btnY = top + (int) (136 * sf);
        addRenderableWidget(new ShopButton(btnStartX, btnY, btnW, btnH,
                Component.literal("取消"), b -> minecraft.setScreen(parentScreen)));
        addRenderableWidget(new ShopButton(btnStartX + btnW + btnGap, btnY, btnW, btnH,
                Component.literal("确认"), b -> confirm()));
    }

    private void selectMode(LimitMode m) {
        this.mode = m;
        applyModeToBox();
    }

    /** 选「无限」时输入框只画不输入 */
    private void applyModeToBox() {
        if (limitBox == null) return;
        if (mode == LimitMode.UNLIMITED) {
            limitBox.setEditable(false);
            limitBox.setValue("");
            limitBox.setFocused(false);
        } else {
            limitBox.setEditable(true);
            if (limitBox.getValue().isEmpty()) {
                limitBox.setValue(String.valueOf(initialLimit > 0 ? initialLimit : 1));
            }
        }
    }

    private void confirm() {
        int limit;
        if (mode == LimitMode.UNLIMITED) {
            limit = 0;
        } else {
            try {
                limit = Integer.parseInt(limitBox.getValue());
            } catch (NumberFormatException e) {
                return;
            }
            if (limit < 1) limit = 1;
        }
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.SetEntryLimitPacket(
                categoryName, Math.max(0, editIndex), limit, mode == LimitMode.PER_PLAYER));
        minecraft.setScreen(parentScreen);
        parentScreen.refreshFromConfig();
    }

    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, 0xC0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        int cx = width / 2;
        int left = cx - popupW / 2;
        int top = height / 2 - popupH / 2;
        float s = (float) ShopUI.getScale(height);

        fillRoundedRect(g, left, top, popupW, popupH, 0xFF333333);
        g.fill(left + 2, top, left + popupW - 2, top + 1, 0xFFFFAA00);

        // 标题
        FontUtil.drawAutoFitCentered(g, font, title.getString(), cx, top + (int) (12 * s), popupW - 20, 0xFFFFAA);
        // 副标题
        FontUtil.drawAutoFitCentered(g, font, "不限次数则可无限交易", cx, top + (int) (30 * s), popupW - 20, 0xAAAAAA);
        // 输入框标签
        FontUtil.drawAutoFitCentered(g, font, "次数:", cx, top + (int) (38 * s), popupW - 20, 0xFFFFFF);

        super.render(g, mx, my, pt);

        int boxW = (int) Math.round(90 * s);
        int boxH = (int) Math.round(16 * s);
        int boxX = cx - boxW / 2;
        int boxY = top + (int) (52 * s);
        boolean editable = mode != LimitMode.UNLIMITED;
        drawBox(g, boxX, boxY, boxW, boxH, editable && limitBox.isFocused(), editable);

        // 选中态描边
        if (mode != LimitMode.UNLIMITED) {
            drawSelectedMark(g, mode);
        }
    }

    /** 给当前选中的模式按钮描一圈高亮边框 */
    private void drawSelectedMark(GuiGraphics g, LimitMode selected) {
        int idx = selected.ordinal();
        if (idx < 0 || idx >= modeButtons.length) return;
        AbstractWidget b = modeButtons[idx];
        if (b == null) return;
        int col = 0xFFFFAA;
        int pad = 2;
        g.fill(b.getX() - pad, b.getY() - pad, b.getX() + b.getWidth() + pad, b.getY() - pad + 1, col);
        g.fill(b.getX() - pad, b.getY() + b.getHeight(), b.getX() + b.getWidth() + pad, b.getY() + b.getHeight() + pad, col);
        g.fill(b.getX() - pad, b.getY() - pad, b.getX() - pad + 1, b.getY() + b.getHeight() + pad, col);
        g.fill(b.getX() + b.getWidth(), b.getY() - pad, b.getX() + b.getWidth() + pad, b.getY() + b.getHeight() + pad, col);
    }

    private void drawBox(GuiGraphics g, int x, int y, int w, int h, boolean focused, boolean editable) {
        int col = !editable ? 0x55FFFFFF : (focused ? 0xFFFFFFFF : 0x55AAAAAA);
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