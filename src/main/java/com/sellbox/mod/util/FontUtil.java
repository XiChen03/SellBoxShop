package com.sellbox.mod.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class FontUtil {

    public static float getAutoFitScale(Font font, String text, int maxWidth) {
        int w = font.width(text);
        if (w <= maxWidth || maxWidth <= 0) return 1.0f;
        return (float) maxWidth / w;
    }

    public static void drawAutoFitText(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int color) {
        int w = font.width(text);
        if (w <= maxWidth || maxWidth <= 0) {
            g.drawString(font, text, (int) x, (int) y, color);
            return;
        }
        float scale = (float) maxWidth / w;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, 0, 0, color);
        g.pose().popPose();
    }

    public static void drawAutoFitText(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        drawAutoFitText(g, font, text.getString(), x, y, maxWidth, color);
    }

    public static void drawAutoFitCentered(GuiGraphics g, Font font, String text, int centerX, int y, int maxWidth, int color) {
        int w = font.width(text);
        if (w <= maxWidth || maxWidth <= 0) {
            g.drawString(font, text, centerX - w / 2, y, color);
            return;
        }
        float scale = (float) maxWidth / w;
        int scaledW = (int) (w * scale);
        g.pose().pushPose();
        g.pose().translate(centerX - scaledW / 2, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, 0, 0, color);
        g.pose().popPose();
    }

    public static void drawAutoFitCentered(GuiGraphics g, Font font, Component text, int centerX, int y, int maxWidth, int color) {
        drawAutoFitCentered(g, font, text.getString(), centerX, y, maxWidth, color);
    }
}