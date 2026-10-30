package com.sellbox.mod.shop;

public class ShopUI {
    private static final int BASE_HEIGHT = 460;

    public static double getScale(int screenHeight) {
        return Math.max(0.78, Math.min(2.5, (double) screenHeight / BASE_HEIGHT));
    }

    public static int scale(int value, int screenHeight) {
        return (int) Math.round(value * getScale(screenHeight));
    }
}
