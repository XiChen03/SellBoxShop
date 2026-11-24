package com.sellbox.mod.gui;

import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 主题化的绘制原语 —— 所有附属界面（编辑分类、选物品、加经验、加指令…）都应该走这里，
 * 而不再自己写 0xFF333333 之类硬编码颜色。
 * <p>
 * 颜色全部实时取自 {@link GuiTheme#get()}，因此切换主题后已打开的界面会立刻跟着变。
 * 圆角半径统一用主题的 {@code cornerRadius}，所以「现代」是大圆角、「默认」是小切角。
 */
public final class GuiDraw {

    private GuiDraw() {
    }

    /** 当前主题（简写） */
    /**
     * 按「左上角 + 宽高」画实心矩形。
     * <p>⚠️ 不要写成 {@code g.fill(x, y, w, h, color)} —— {@code GuiGraphics.fill} 的签名是
     * {@code (x1, y1, x2, y2, color)}，第三四个参数是<b>右下角坐标</b>而不是宽高。
     * 传成宽高时会画出反向四边形，表现为「一大块莫名其妙的色块盖住半个界面」。
     */
    public static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (color == 0 || w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, color);
    }

    /** 小方块（品质色点之类） */
    public static void dot(GuiGraphics g, int x, int y, int size, int color) {
        rect(g, x, y, size, size, color);
    }

    public static GuiTheme T() {
        return GuiTheme.get();
    }

    // ════════════════════════════════════════════
    //  形状
    // ════════════════════════════════════════════

    /** 圆角填充：半径取主题 cornerRadius（<=2 时走方形快速路径） */
    public static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (color == 0 || w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(T().cornerRadius, Math.min(w, h) / 2));
        if (r <= 2) {
            g.fill(x + 2, y, x + w - 2, y + h, color);
            g.fill(x, y + 2, x + w, y + h - 2, color);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int dy = r - i - 1;
            int inset = r - (int) Math.round(Math.sqrt((double) (r * r - dy * dy)));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    /** 1px 圆角边框（形状与 {@link #fillRounded} 对应） */
    public static void borderRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (color == 0 || w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(T().cornerRadius, Math.min(w, h) / 2));
        if (r <= 2) {
            g.fill(x + 2, y, x + w - 2, y + 1, color);
            g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
            g.fill(x, y + 2, x + 1, y + h - 2, color);
            g.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
            return;
        }
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        for (int i = 0; i < r; i++) {
            int dy = r - i - 1;
            int inset = r - (int) Math.round(Math.sqrt((double) (r * r - dy * dy)));
            g.fill(x + inset, y + i, x + inset + 1, y + i + 1, color);
            g.fill(x + w - inset - 1, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + inset + 1, y + h - i, color);
            g.fill(x + w - inset - 1, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    // ════════════════════════════════════════════
    //  组件
    // ════════════════════════════════════════════

    /** 全屏遮罩（弹窗类界面用，压在世界上） */
    public static void scrim(GuiGraphics g, int width, int height) {
        int c = T().scrim;
        if (c != 0) g.fill(0, 0, width, height, c);
    }

    /** 弹窗面板：底色 + 外框 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        fillRounded(g, x, y, w, h, T().popupBg);
        if (T().popupBorder != 0) borderRounded(g, x, y, w, h, T().popupBorder);
    }

    /** 弹窗面板 + 顶部强调条（旧版 popup + 0xFFFFAA00 那条） */
    public static void panelWithStripe(GuiGraphics g, int x, int y, int w, int h) {
        panel(g, x, y, w, h);
        if (T().accentStripe != 0) {
            g.fill(x + 4, y, x + w - 4, y + 1, T().accentStripe);
        }
    }

    /** 输入框：底色 + 边框。底色取 inputBg，为 0 时退化到 searchBg（兼容只有 searchBg 的旧主题） */
    public static void inputBox(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        inputBox(g, x, y, w, h, focused, focused ? T().inputBorderFocused : T().inputBorderUnfocused);
    }

    /**
     * 带自定义边框色的输入框。
     * <p>兜底规则：底色和边框都没配（现代表即为这种"无描边"风格）时，用分隔线色铺一层底，
     * 保证输入框在<b>任何</b>主题下都可见——这正是"现代主题下输入框全消失"的修法。
     */
    public static void inputBox(GuiGraphics g, int x, int y, int w, int h, boolean focused, int borderColor) {
        int bg = T().inputBg != 0 ? T().inputBg : T().searchBg;
        if (bg == 0 && borderColor == 0) {
            bg = T().separatorLine != 0 ? T().separatorLine : 0x33FFFFFF;
        }
        if (bg != 0) fillRounded(g, x, y, w, h, bg);
        if (borderColor != 0) borderRounded(g, x, y, w, h, borderColor);
    }

    /**
     * 输入框内文字的 y。
     * <p>关键事实：非边框（setBordered(false)）的 EditBox 在 renderWidget 里把文字画在
     * <b>getY() 本身</b>（见 EditBox 源码 `int k = this.bordered ? getY() + (height-8)/2 : getY()`）。
     * 所以要让文字在视觉上居中，必须把 EditBox 的 y 设成这个值，占位文字也用同一个值，
     * 两者才会严丝合缝地对齐。
     */
    public static int innerTextY(int boxY, int boxH) {
        return boxY + (boxH - 9) / 2;   // 9 = Minecraft 字体行高
    }

    /**
     * 输入框的水平内边距。圆角越大需要越多留白，否则文字会顶到圆角弧线上。
     * 经典主题 cornerRadius=2 → 2px；现代表 8 → 6px。
     */
    public static int textPadX() {
        return Math.max(2, Math.min(6, T().cornerRadius - 1));
    }

    /** 输入框内文字的起始 x（EditBox 的 getX() 直接就是绘制起点，所以建框时也要用这个） */
    public static int innerTextX(int boxX) {
        return boxX + textPadX();
    }

    /** 输入框内文字的可用宽度（左右各留一个内边距） */
    public static int innerWidth(int boxW) {
        return Math.max(8, boxW - textPadX() * 2);
    }

    /** 列表行底色（选中/悬浮/常态） */
    public static void listRow(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered) {
        int c = 0;
        if (selected && T().listRowSelected != 0) c = T().listRowSelected;
        else if (hovered && T().listRowHover != 0) c = T().listRowHover;
        else if (T().listRowBg != 0) c = T().listRowBg;
        if (c != 0) fillRounded(g, x, y, w, h, c);
    }

    /** 列表行之间的细分隔线 */
    public static void divider(GuiGraphics g, int x, int y, int w) {
        if (w <= 0) return;
        int c = T().separatorLine != 0 ? T().separatorLine : 0xFFFFFFFF;
        rect(g, x, y, w, 1, (c & 0x00FFFFFF) | 0x55000000);
    }

    /** 页内分组：当前版本只用内容区外框，不再叠加容易切字的二级框 */
    public static void section(GuiGraphics g, int x, int y, int w, int h) {
        // 保留 API，视觉上交给内容区外框和列表行，避免“框套框”显得杂乱
    }

    /**
     * 竖向滚动条：offset 为当前起始下标/像素，maxOffset 为最大起始值。
     * 没有可滚动内容时不画。
     */
    public static void scrollbar(GuiGraphics g, int x, int y, int h, int offset, int maxOffset) {
        if (maxOffset <= 0 || h <= 0) return;
        int track = T().separatorLine != 0 ? T().separatorLine : 0x33FFFFFF;
        rect(g, x, y, 2, h, track);
        int thumbH = Math.max(6, (int) ((long) h * h / (h + maxOffset)));
        thumbH = Math.min(h, thumbH);
        int travel = Math.max(0, h - thumbH);
        int thumbY = y + (int) ((long) travel * Math.max(0, Math.min(offset, maxOffset)) / maxOffset);
        int thumb = T().accentStripe != 0 ? T().accentStripe : T().textMuted;
        rect(g, x, thumbY, 2, thumbH, thumb);
    }

    /** 物品格底块（无边框风格时靠它撑出格子） */
    public static void cell(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        if (T().cellBg != 0) fillRounded(g, x, y, w, h, T().cellBg);
        if (T().drawCellBorder) borderRounded(g, x, y, w, h, T().cellBorder);
        if (hovered && T().cellHoveredFill != 0) fillRounded(g, x + 1, y + 1, w - 2, h - 2, T().cellHoveredFill);
    }

    /**
     * 文字是否带投影（阴影）。
     * <p>Minecraft 的投影是「同一段文字在下方 1px 处、颜色压到 25% 再画一遍」
     * （见 {@code Font.StringRenderOutput} 的 {@code dimFactor = dropShadow ? 0.25F : 1.0F}）。
     * 深色主题下这是加分项，但在浅色主题里投影变成一块偏白的糊影，
     * 和深色文字叠在一起就像"两层字"，所以由主题自己决定。
     */
    public static boolean shadow() {
        return T().textShadow;
    }

    /**
     * 细框/细线用的颜色：优先 cellBorder；主题把 cellBorder 关成 0 时（现代主题的风格是无描边），
     * 退化到 separatorLine。这样「图标外框」「页签底」「槽位描边」这类靠 cellBorder 画的元素
     * 在任何主题下都不会凭空消失。
     */
    public static int frameColor() {
        int c = T().cellBorder;
        if (c != 0) return c;
        if (T().separatorLine != 0) return T().separatorLine;
        return 0x33FFFFFF;
    }

    /** 一个 size×size 的图标外框（1px 四边），颜色走 {@link #frameColor()} */
    public static void iconFrame(GuiGraphics g, int x, int y, int size) {
        int c = frameColor();
        g.fill(x, y, x + size, y + 1, c);
        g.fill(x, y + size - 1, x + size, y + size, c);
        g.fill(x, y, x + 1, y + size, c);
        g.fill(x + size - 1, y, x + size, y + size, c);
    }

    /** 圆角版的图标外框（切 2px 角），颜色走 {@link #frameColor()} */
    public static void iconFrameRounded(GuiGraphics g, int x, int y, int w, int h) {
        int c = frameColor();
        g.fill(x + 2, y, x + w - 2, y + 1, c);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, c);
        g.fill(x, y + 2, x + 1, y + h - 2, c);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, c);
    }

    // ════════════════════════════════════════════
    //  文字
    // ════════════════════════════════════════════

    /** 界面标题（居中，自动缩放到 maxWidth 内） */
    public static void title(GuiGraphics g, Font font, String text, int centerX, int y, int maxWidth) {
        FontUtil.drawAutoFitCentered(g, font, text, centerX, y, maxWidth, T().textTitle);
    }

    /** 字段标签 */
    public static void label(GuiGraphics g, Font font, String text, int x, int y, int maxWidth) {
        FontUtil.drawAutoFitText(g, font, text, x, y, maxWidth, T().textPrimary);
    }

    /** 提示 / 占位文字 */
    public static void hint(GuiGraphics g, Font font, String text, int x, int y, int maxWidth) {
        FontUtil.drawAutoFitText(g, font, text, x, y, maxWidth, T().textHint);
    }

    /** 次要说明（居中） */
    public static void note(GuiGraphics g, Font font, String text, int centerX, int y, int maxWidth) {
        FontUtil.drawAutoFitCentered(g, font, text, centerX, y, maxWidth, T().textMuted);
    }

    /** 错误 / 提醒文字（居中） */
    public static void error(GuiGraphics g, Font font, String text, int centerX, int y, int maxWidth) {
        FontUtil.drawAutoFitCentered(g, font, text, centerX, y, maxWidth, T().textError);
    }

    /** 数值（价格/余额之类） */
    public static void price(GuiGraphics g, Font font, Component text, int centerX, int y, int maxWidth) {
        FontUtil.drawAutoFitCentered(g, font, text.getString(), centerX, y, maxWidth, T().textPrice);
    }
}
