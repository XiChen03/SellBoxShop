package com.sellbox.mod.shop;

import com.sellbox.mod.shop.network.ShopEditPayload;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class AddCategoryScreen extends Screen {
    private float sf;
    private int panelLeft, panelTop, panelW, panelH;
    private int cx;
    private int nameBoxX, nameBoxY, nameBoxW, boxH;
    private int labelY;
    private int addBtnX, backBtnX, btnRowY, btnW, btnH;

    private final ShopScreen parentScreen;
    private EditBox nameBox;

    public AddCategoryScreen(ShopScreen parent) {
        super(Component.translatable("添加分类"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();
        sf = (float) ShopUI.getScale(height);
        boxH = (int) Math.round(16 * sf);
        btnH = (int) Math.round(18 * sf);
        btnW = (int) Math.round(60 * sf);

        int maxW = Math.min(width - 20, (int) Math.round(260 * sf));
        panelW = Math.max(200, maxW);
        panelLeft = (width - panelW) / 2;
        cx = panelLeft + panelW / 2;

        int pad = (int) Math.round(15 * sf);
        nameBoxW = panelW - pad * 2;

        int titleAreaH = (int) Math.round(24 * sf);
        int labelH = (int) Math.round(14 * sf);
        int bottomH = btnH + (int) Math.round(12 * sf);
        int totalContentH = titleAreaH + labelH + (int) Math.round(4 * sf) + boxH + (int) Math.round(12 * sf) + bottomH;

        int maxPanelH = height - 15;
        panelH = Math.min((int) Math.round(totalContentH), maxPanelH);
        panelH = Math.max(panelH, (int) Math.round(120 * sf));

        panelTop = Math.max(10, (height - panelH) / 2);
        if (panelTop + panelH > height - 5) {
            panelTop = height - 5 - panelH;
            panelTop = Math.max(10, panelTop);
        }

        int curY = panelTop + (int) Math.round(8 * sf);
        curY += (int) Math.round(16 * sf);

        labelY = curY;
        curY += (int) Math.round(12 * sf);
        nameBoxY = curY;
        curY += boxH + (int) Math.round(12 * sf);
        btnRowY = curY;

        int lx = panelLeft + pad;
        nameBox = new EditBox(font, lx + (int) Math.round(2 * sf), nameBoxY + 1, nameBoxW - (int) Math.round(4 * sf), boxH - 2, Component.literal(""));
        nameBox.setBordered(false);
        nameBox.setValue("");
        addRenderableWidget(nameBox);

        addBtnX = cx - btnW - (int) Math.round(6 * sf);
        backBtnX = cx + (int) Math.round(6 * sf);
        addRenderableWidget(new ShopButton(addBtnX, btnRowY, btnW, btnH, Component.literal("添加"),
                btn -> {
                    String name = nameBox.getValue().trim();
                    if (!name.isEmpty()) {
                        PacketDistributor.sendToServer(new ShopEditPayload(
                                "add_category", "", name, "", 0, -1, false, "", 1, ""));
                    }
                    minecraft.setScreen(parentScreen);
                    parentScreen.refreshFromConfig();
                }));
        addRenderableWidget(new ShopButton(backBtnX, btnRowY, btnW, btnH,
                Component.translatable("gui.shop.cancel"),
                btn -> minecraft.setScreen(parentScreen)));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        fillRoundedRect(g, panelLeft, panelTop, panelW, panelH, 0xFF333333);
        g.fill(panelLeft + 2, panelTop, panelLeft + panelW - 2, panelTop + 1, 0xFFFFAA00);
        super.render(g, mx, my, pt);

        FontUtil.drawAutoFitCentered(g, font, title.getString(), cx, panelTop + (int) Math.round(8 * sf), panelW - 10, 0xFFFFAA);
 
        int lx = panelLeft + (int) Math.round(15 * sf);
        FontUtil.drawAutoFitText(g, font, "名称:", lx, labelY, nameBoxW, 0xFFFFFF);

        int col = nameBox.isFocused() ? 0x55FFFFFF : 0x55AAAAAA;
        g.fill(lx + 2, nameBoxY, lx + nameBoxW - 2, nameBoxY + 1, col);
        g.fill(lx + 2, nameBoxY + boxH - 1, lx + nameBoxW - 2, nameBoxY + boxH, col);
        g.fill(lx, nameBoxY + 2, lx + 1, nameBoxY + boxH - 2, col);
        g.fill(lx + nameBoxW - 1, nameBoxY + 2, lx + nameBoxW, nameBoxY + boxH - 2, col);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { minecraft.setScreen(parentScreen); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }
}
