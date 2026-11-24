package com.sellbox.mod.gui;

import com.sellbox.mod.gui.GuiDraw;
import com.sellbox.mod.shop.ModernButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI 主题切换界面。
 * <p>
 * 按钮列表完全由 {@link GuiTheme#getRegistry()} 生成，新增主题无需改这个类。
 * 点击后调用 {@link GuiTheme#setByName(String)}（身份恒定 + 立即写盘），
 * 已打开的界面会当场变色，不需要重开。
 * <p>
 * 布局按屏幕高度自适应：按钮高、间距、字号一起缩放，标题与底部按钮始终留在屏内。
 */
public class ThemeSwitchScreen extends Screen {

    private final List<String> themeIds = new ArrayList<>();
    private final GuiTheme T = GuiTheme.get();

    /** 本帧的布局尺寸（重算时写入，render 里复用） */
    private int btnW, btnH, gap, startY, titleY, infoY, closeY, closeH;
    private float titleScale = 1.0f, btnTextScale = 1.0f;

    public ThemeSwitchScreen() {
        super(Component.literal("主题切换"));
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft == null) minecraft = Minecraft.getInstance();

        themeIds.clear();
        themeIds.addAll(GuiTheme.getRegistry().keySet());

        int n = themeIds.size();

        // ── 竖向预算：标题(28) + 说明(18) + 按钮区 + 关闭按钮(24) + 上下留白 ──
        int reserved = 28 + 18 + 24 + 24;
        int availForList = Math.max(40, height - reserved);

        // 每个按钮占用的总高（按钮 + 间隙），先按理想值算，超了就压
        int perItemIdeal = 34;                     // 24 + 10
        int perItem = Math.min(perItemIdeal, availForList / Math.max(1, n));
        perItem = Math.max(16, perItem);

        btnH = Math.max(12, perItem - Math.max(2, perItem / 6));
        gap = Math.max(2, perItem - btnH);
        int totalListH = n * btnH + (n - 1) * gap;

        titleScale = Math.max(0.75f, Math.min(1.6f, height / 340f));
        btnTextScale = Math.max(0.7f, Math.min(1.3f, btnH / 20f));

        // 横向：宽度占屏 40%，夹在 [180, 340]
        btnW = Math.max(120, Math.min(340, (int) (width * 0.40)));
        btnW = Math.min(btnW, width - 40);

        // 竖向定位：标题紧贴顶部留白，按钮区居中于剩余空间
        titleY = Math.max(6, (int) (height * 0.05f));
        infoY = titleY + Math.round(16 * titleScale) + 2;
        int listTop = infoY + 14;
        startY = listTop + Math.max(0, (availForList - totalListH) / 2);
        if (startY + totalListH > height - 8) startY = Math.max(listTop, height - 8 - totalListH);

        closeH = Math.max(14, Math.min(24, btnH));
        closeY = Math.min(height - closeH - 6, startY + totalListH + Math.max(6, gap * 2));

        int centerX = width / 2;
        for (int i = 0; i < n; i++) {
            String id = themeIds.get(i);
            int y = startY + i * (btnH + gap);
            addRenderableWidget(new ModernButton(
                    centerX - btnW / 2, y, btnW, btnH,
                    labelFor(id),
                    btn -> {
                        GuiTheme.setByName(id);
                        // 主题换了，重建按钮以刷新选中标记
                        rebuildWidgets();
                    }));
        }

        addRenderableWidget(new ModernButton(
                centerX - btnW / 2, closeY, btnW, closeH,
                Component.literal("关闭"),
                btn -> onClose()));
    }

    /** 按钮文字：显示名 + 当前标记 */
    private Component labelFor(String id) {
        String name = GuiTheme.displayName(id);
        boolean isCurrent = id.equals(GuiTheme.getCurrentName());
        return Component.literal((isCurrent ? "§a✔ " : "") + name + (isCurrent ? " §7(当前)" : ""));
    }

    /**
     * 不画原版背景（菜单背景/模糊）。
     * <p>本界面的标题在 {@code super.render} 之前画，原版背景会把它盖掉或糊掉，
     * 覆盖成空实现后只保留我们自己的 scrim。
     */
    @Override
    public void renderBackground(GuiGraphics g) {
        // 由 render() 里的 GuiDraw.scrim 负责压暗
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiDraw.scrim(g, width, height);

        String title = "选择 GUI 主题";
        g.pose().pushPose();
        float tw = font.width(title) * titleScale;
        g.pose().translate((width - tw) / 2, titleY, 0);
        g.pose().scale(titleScale, titleScale, 1.0f);
        g.drawString(font, title, 0, 0, T.textTitle,GuiDraw.shadow());
        g.pose().popPose();

        String info = "当前：" + GuiTheme.getCurrentDisplayName();
        if (font.width(info) > width - 20) {
            GuiDraw.note(g, font, info, width / 2, infoY, width - 20);
        } else {
            g.drawString(font, info, (width - font.width(info)) / 2, infoY, T.textMuted,GuiDraw.shadow());
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (com.sellbox.mod.SellBoxModClient.themeKey != null
                && com.sellbox.mod.SellBoxModClient.themeKey.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(null);
    }

    /** 供外部注册入口使用：直接打开主题界面 */
    public static void open() {
        Minecraft.getInstance().setScreen(new ThemeSwitchScreen());
    }

    /** 主题 id -> 显示名，暴露给命令提示 */
    public static Map<String, String> describeThemes() {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String id : GuiTheme.getRegistry().keySet()) {
            out.put(id, GuiTheme.displayName(id));
        }
        return out;
    }
}
