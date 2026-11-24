package com.sellbox.mod.extras.client;

import com.sellbox.mod.gui.GuiDraw;
import com.sellbox.mod.gui.GuiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * 右下角成就式提醒的绘制（Forge 1.20.1）。
 * <p>
 * 由 {@code SellBoxModClient} 通过 {@code RenderGuiEvent.Post} 挂到 HUD 层。
 * 界面打开时 HUD 与 Screen 的先后顺序在 1.20.1 里不稳定（可能被盖住、也可能盖住界面），
 * 所以这里统一约定：<b>有界面打开就不在 HUD 层画</b>，改由 {@link ExtrasScreen}
 * 用屏幕坐标自己画一遍（{@link #DRAW_IN_SCREEN}），保证任何情况下只有一份、且在最上层。
 */
public final class ExtrasNoticeOverlay {
    private ExtrasNoticeOverlay() {}

    /** 面板打开时是否需要在 Screen 里再画一遍 */
    public static final boolean DRAW_IN_SCREEN = true;

    private static final int W = 220;
    private static final int H = 30;
    private static final int GAP = 4;
    private static final int MARGIN = 6;

    /** 每帧调用：滑入/滑出 + 绘制（由 {@code RenderGuiEvent.Post} 触发） */
    public static void render(GuiGraphics g) {
        if (Minecraft.getInstance().screen != null) return;  // 界面打开时交给 Screen 自己画
        draw(g);
    }

    /** 屏幕右下角绘制（{@code g} 须处于未平移的屏幕坐标系） */
    public static void draw(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.options.hideGui) return;   // F1 隐藏 HUD 时一起隐藏

        List<ExtrasNoticeQueue.Notice> list = ExtrasNoticeQueue.visible();
        if (list.isEmpty()) return;

        GuiTheme T = GuiDraw.T();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int idx = 0;
        for (ExtrasNoticeQueue.Notice n : list) {
            // 滑入：前 180ms 从右侧滑入
            long age = System.currentTimeMillis() - n.bornAt;
            float t = Math.min(1f, age / 180f);
            int slide = (int) ((1f - t) * (W + 12));

            int x = sw - W - MARGIN + slide;
            int y = sh - MARGIN - H - idx * (H + GAP);
            drawCard(g, T, n, x, y);
            idx++;
        }
    }

    private static void drawCard(GuiGraphics g, GuiTheme T, ExtrasNoticeQueue.Notice n, int x, int y) {
        int bg = T.popupBg != 0 ? T.popupBg : 0xF0202020;
        GuiDraw.fillRounded(g, x, y, W, H, bg);

        int accent = accentOf(T, n.kind);
        if (accent != 0) GuiDraw.fillRounded(g, x, y, 3, H, accent);

        int border = T.popupBorder != 0 ? T.popupBorder : T.separatorLine;
        if (border != 0) GuiDraw.borderRounded(g, x, y, W, H, border);

        Minecraft mc = Minecraft.getInstance();
        if (!n.title.isEmpty()) {
            g.drawString(mc.font, n.title, x + 9, y + 5, T.textPrimary, GuiDraw.shadow());
        }
        if (!n.text.isEmpty()) {
            // 太长就截断，最多一行；没有标题时垂直居中，避免上方空一块
            String text = mc.font.plainSubstrByWidth(n.text, W - 18);
            int ty = n.title.isEmpty() ? y + (H - mc.font.lineHeight) / 2 : y + 16;
            int color = "error".equals(n.kind) ? 0xFFFF9A9A : T.textMuted;
            g.drawString(mc.font, text, x + 9, ty, color, GuiDraw.shadow());
        }
    }

    /** 语义色：不跟主题走，保证任何主题下都能区分 */
    private static int accentOf(GuiTheme T, String kind) {
        return switch (kind == null ? "" : kind) {
            case "money", "sale" -> 0xFFE8C878;
            case "buy" -> 0xFF8FD8FF;
            case "error" -> 0xFFFF8A8A;
            case "mail", "ok" -> 0xFF9CE8A0;
            case "crate" -> 0xFFFFC64D;
            default -> T.accentStripe != 0 ? T.accentStripe : 0xFF8FD8FF;
        };
    }
}
