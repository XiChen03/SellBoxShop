package com.sellbox.mod.shop;

import com.sellbox.mod.gui.GuiDraw;
import com.sellbox.mod.gui.GuiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 现代风格按钮。
 * <p>
 * 只传 x/y/w/h/message/onPress 时，颜色实时取自 {@link GuiTheme#get()}，
 * 因此主题切换后按钮会立刻跟着变；显式传色则按传入值画。
 */
public class ModernButton extends Button {
    /** 是否使用主题色（未显式指定颜色） */
    private final boolean useTheme;
    private final int bgColor;
    private final int hoverColor;
    private final int borderColor;
    private final int textColor;

    public ModernButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.useTheme = true;
        this.bgColor = 0;
        this.hoverColor = 0;
        this.borderColor = 0;
        this.textColor = 0;
    }

    public ModernButton(int x, int y, int width, int height, Component message, OnPress onPress,
                        int bgColor, int hoverColor, int borderColor, int textColor) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.useTheme = false;
        this.bgColor = bgColor;
        this.hoverColor = hoverColor;
        this.borderColor = borderColor;
        this.textColor = textColor;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme T = GuiTheme.get();

        int bg = useTheme ? (isHovered ? T.btnBgHovered : T.btnBgNormal) : (isHovered ? hoverColor : bgColor);
        int border = useTheme ? (isHovered ? T.textPrimary : T.accentStripe) : (isHovered ? T.textPrimary : borderColor);
        int text = useTheme ? T.btnText : textColor;

        // 圆角底色（角半径跟主题走）
        int r = Math.max(0, Math.min(T.cornerRadius, Math.min(getWidth(), getHeight()) / 2));
        if (r <= 2) {
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
        } else {
            g.fill(getX(), getY() + r, getX() + getWidth(), getY() + getHeight() - r, bg);
            for (int i = 0; i < r; i++) {
                int dy = r - i - 1;
                int inset = r - (int) Math.round(Math.sqrt((double) (r * r - dy * dy)));
                g.fill(getX() + inset, getY() + i, getX() + getWidth() - inset, getY() + i + 1, bg);
                g.fill(getX() + inset, getY() + getHeight() - i - 1, getX() + getWidth() - inset, getY() + getHeight() - i, bg);
            }
        }

        g.fill(getX(), getY(), getX() + getWidth(), getY() + 1, border);
        g.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), border);
        g.fill(getX(), getY(), getX() + 1, getY() + getHeight(), border);
        g.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), border);

        var font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        int textW = font.width(label);
        int textX = getX() + (getWidth() - textW) / 2;
        int textY = getY() + (getHeight() - 8) / 2;
        g.drawString(font, label, textX, textY, text,GuiDraw.shadow());
    }
}
