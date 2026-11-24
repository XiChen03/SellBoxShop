package com.sellbox.mod.shop;

import com.sellbox.mod.gui.GuiDraw;
import com.sellbox.mod.gui.GuiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 通用按钮。
 * <p>
 * 默认构造器<b>不缓存颜色</b>，每帧实时读 {@link GuiTheme#get()}，因此主题切换后
 * 已打开的界面里的按钮会立刻跟着变。需要固定颜色的地方用带 4 个颜色参数的构造器。
 */
public class ShopButton extends Button {
    private final boolean useTheme;
    private final int bgColor;
    private final int hoverColor;
    private final int borderColor;
    private final int textColor;

    public ShopButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        this.useTheme = true;
        this.bgColor = 0;
        this.hoverColor = 0;
        this.borderColor = 0;
        this.textColor = 0;
    }

    public ShopButton(int x, int y, int w, int h, Component message, OnPress onPress,
                      int bgColor, int hoverColor, int borderColor, int textColor) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        this.useTheme = false;
        this.bgColor = bgColor;
        this.hoverColor = hoverColor;
        this.borderColor = borderColor;
        this.textColor = textColor;
    }

    @Override
    protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        GuiTheme T = GuiTheme.get();

        int bg = useTheme ? (isHovered() ? T.btnBgHovered : T.btnBgNormal) : (isHovered() ? hoverColor : bgColor);
        int border = useTheme ? (isHovered() ? T.textPrimary : T.btnBorder) : (isHovered() ? T.textPrimary : borderColor);
        int text = useTheme ? T.btnText : textColor;

        int r = Math.max(0, Math.min(T.cornerRadius, Math.min(getWidth(), getHeight()) / 2));
        if (r <= 2) {
            gui.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
        } else {
            gui.fill(getX(), getY() + r, getX() + getWidth(), getY() + getHeight() - r, bg);
            for (int i = 0; i < r; i++) {
                int dy = r - i - 1;
                int inset = r - (int) Math.round(Math.sqrt((double) (r * r - dy * dy)));
                gui.fill(getX() + inset, getY() + i, getX() + getWidth() - inset, getY() + i + 1, bg);
                gui.fill(getX() + inset, getY() + getHeight() - i - 1, getX() + getWidth() - inset, getY() + getHeight() - i, bg);
            }
        }

        if (border != 0) {
            gui.fill(getX() + r, getY(), getX() + getWidth() - r, getY() + 1, border);
            gui.fill(getX() + r, getY() + getHeight() - 1, getX() + getWidth() - r, getY() + getHeight(), border);
            gui.fill(getX(), getY() + r, getX() + 1, getY() + getHeight() - r, border);
            gui.fill(getX() + getWidth() - 1, getY() + r, getX() + getWidth(), getY() + getHeight() - r, border);
        }

        var font = Minecraft.getInstance().font;
        Component msg = getMessage();
        int textW = font.width(msg);
        gui.drawString(font, msg,
                getX() + (getWidth() - textW) / 2,
                getY() + (getHeight() - 8) / 2, text, GuiDraw.shadow());
    }
}
