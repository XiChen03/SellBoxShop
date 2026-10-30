package com.sellbox.mod.shop;

import com.sellbox.mod.shop.network.ShopEditPayload;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class EditCategoryScreen extends Screen {
    private float sf;
    private int panelLeft, panelTop, panelW, panelH;
    private int cx;

    private final ShopScreen parentScreen;
    private final String categoryName;
    private EditBox nameBox;
    private int nameBoxX, nameBoxY, nameBoxW, nameBoxH;

    public EditCategoryScreen(ShopScreen parent, String categoryName) {
        super(Component.literal("编辑分类 - " + categoryName));
        this.parentScreen = parent;
        this.categoryName = categoryName;
    }

    @Override
    protected void init() {
        sf = (float) ShopUI.getScale(height);
        int boxH = (int) Math.round(16 * sf);
        int btnH = (int) Math.round(18 * sf);

        int maxW = Math.min(width - 20, (int) Math.round(280 * sf));
        panelW = Math.max(240, maxW);
        panelLeft = (width - panelW) / 2;
        cx = panelLeft + panelW / 2;

        int pad = (int) Math.round(15 * sf);
        int rowGap = (int) Math.round(6 * sf);
        int totalContentH = (int) Math.round(24 * sf) + boxH + rowGap + btnH + rowGap + btnH + rowGap + btnH + (int) Math.round(10 * sf);

        int maxPanelH = height - 20;
        panelH = Math.min((int) Math.round(totalContentH) + (int) Math.round(12 * sf), maxPanelH);
        panelH = Math.max(panelH, (int) Math.round(180 * sf));
        panelTop = Math.max(10, (height - panelH) / 2 - 10);

        int lx = panelLeft + pad;
        int curY = panelTop + (int) Math.round(24 * sf);

        nameBoxX = lx + (int) Math.round(60 * sf);
        nameBoxY = curY;
        nameBoxW = (int) Math.round(140 * sf);
        nameBoxH = boxH;

        this.nameBox = new EditBox(this.font, nameBoxX + (int) Math.round(2 * sf), nameBoxY + 1, nameBoxW - (int) Math.round(4 * sf), boxH - 2,
                Component.literal("分类名称"));
        this.nameBox.setBordered(false);
        this.nameBox.setValue(categoryName);
        this.addRenderableWidget(this.nameBox);

        curY += boxH + rowGap;

        // Buttons shrunk 1/3 and centered on screen center
        float shrink = 2f / 3f;
        int btnGap = (int) Math.round(4 * sf);
        int btnW1 = (int) Math.round(70 * sf * shrink);
        int btnW2 = (int) Math.round(50 * sf * shrink);
        int row1Total = btnW1 + btnGap + btnW2 + btnGap + btnW2;
        int row1Start = cx - row1Total / 2;
        int btnRow1Y = curY;

        addRenderableWidget(new ShopButton(row1Start, btnRow1Y, btnW1, btnH, Component.literal("保存名称"),
                btn -> {
                    String newName = nameBox.getValue().trim();
                    if (!newName.isEmpty() && !newName.equals(categoryName)) {
                        PacketDistributor.sendToServer(new ShopEditPayload(
                                "rename_category", categoryName, newName, "", 0, -1, false, "", 1, ""));
                    }
                    parentScreen.refreshFromConfig();
                    this.minecraft.setScreen(parentScreen);
                }));

        addRenderableWidget(new ShopButton(row1Start + btnW1 + btnGap, btnRow1Y, btnW2, btnH, Component.literal("上移"),
                btn -> {
                    PacketDistributor.sendToServer(new ShopEditPayload(
                            "move_category_up", categoryName, "", "", 0, -1, false, "", 1, ""));
                    parentScreen.refreshFromConfig();
                }));

        addRenderableWidget(new ShopButton(row1Start + btnW1 + btnGap + btnW2 + btnGap, btnRow1Y, btnW2, btnH, Component.literal("下移"),
                btn -> {
                    PacketDistributor.sendToServer(new ShopEditPayload(
                            "move_category_down", categoryName, "", "", 0, -1, false, "", 1, ""));
                    parentScreen.refreshFromConfig();
                }));

        curY += btnH + rowGap;

        int deleteBtnW = (int) Math.round(140 * sf * shrink);
        addRenderableWidget(new ShopButton(cx - deleteBtnW / 2, curY, deleteBtnW, btnH, Component.literal("删除此分类"),
                btn -> {
                    PacketDistributor.sendToServer(new ShopEditPayload(
                            "remove_category", categoryName, "", "", 0, -1, false, "", 1, ""));
                    this.minecraft.setScreen(parentScreen);
                    parentScreen.refreshFromConfig();
                }));

        curY += btnH + rowGap;

        int cancelBtnW = (int) Math.round(100 * sf * shrink);
        addRenderableWidget(new ShopButton(cx - cancelBtnW / 2, curY, cancelBtnW, btnH,
                Component.translatable("gui.shop.cancel"),
                btn -> this.minecraft.setScreen(parentScreen)));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        fillRoundedRect(g, panelLeft, panelTop, panelW, panelH, 0xFF333333);
        g.fill(panelLeft + 2, panelTop, panelLeft + panelW - 2, panelTop + 1, 0xFFFFAA00);
        super.render(g, mx, my, pt);

        FontUtil.drawAutoFitCentered(g, font, "编辑分类", cx, panelTop + (int) Math.round(8 * sf), panelW - 10, 0xFFFFAA);

        int pad = (int) Math.round(15 * sf);
        FontUtil.drawAutoFitText(g, font, "名称:", panelLeft + pad, nameBoxY, 60, 0xFFFFFF);
        drawStyledBox(g, nameBoxX, nameBoxY, nameBoxW, nameBoxH, nameBox.isFocused());

        int count = ShopConfig.getCategory(categoryName) != null ? ShopConfig.getCategory(categoryName).size() : 0;
        Component info = Component.literal("商品数: " + count);
        FontUtil.drawAutoFitCentered(g, font, info.getString(), cx, panelTop + panelH - (int) Math.round(20 * sf), panelW - 20, 0x888888);
    }

    private void drawStyledBox(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        int col = focused ? 0x55FFFFFF : 0x55AAAAAA;
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.minecraft.setScreen(parentScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
