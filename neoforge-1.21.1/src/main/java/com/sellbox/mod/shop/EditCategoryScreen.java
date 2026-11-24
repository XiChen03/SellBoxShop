package com.sellbox.mod.shop;

import com.sellbox.mod.gui.GuiDraw;
import com.sellbox.mod.gui.GuiTheme;
import com.sellbox.mod.shop.network.ShopEditPayload;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 编辑分类弹窗。
 * <p>
 * 按钮布局：
 * <pre>
 *      上移   下移
 *      删除此分类
 *   取消        保存
 * </pre>
 * 配色全部取自 {@link GuiTheme}，跟随主题切换。
 */
public class EditCategoryScreen extends Screen {
    private final GuiTheme T = GuiTheme.get();

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

        // 内容：标题带 + 名称行 + 3 行按钮
        int contentH = (int) Math.round(24 * sf)      // 标题
                + boxH + rowGap                        // 名称输入
                + btnH + rowGap                        // 上移/下移
                + btnH + rowGap                        // 删除此分类
                + btnH                                 // 取消/保存
                + (int) Math.round(22 * sf);           // 底部信息

        int maxPanelH = height - 20;
        panelH = Math.min(contentH + (int) Math.round(12 * sf), maxPanelH);
        panelH = Math.max(panelH, (int) Math.round(180 * sf));
        panelTop = Math.max(10, (height - panelH) / 2 - 10);

        int lx = panelLeft + pad;
        int curY = panelTop + (int) Math.round(24 * sf);

        nameBoxX = lx + (int) Math.round(60 * sf);
        nameBoxY = curY;
        nameBoxW = (int) Math.round(140 * sf);
        nameBoxH = boxH;

        this.nameBox = new EditBox(this.font, GuiDraw.innerTextX(nameBoxX), GuiDraw.innerTextY(nameBoxY, boxH),
                GuiDraw.innerWidth(nameBoxW), boxH - 2,
                Component.literal("分类名称"));
        this.nameBox.setBordered(false);
        this.nameBox.setValue(categoryName);
        this.addRenderableWidget(this.nameBox);

        curY += boxH + rowGap;

        // ── 第 1 行：上移 | 下移 ──────────────────────────
        int pairGap = (int) Math.round(6 * sf);
        int halfW = (panelW - pad * 2 - pairGap) / 2;
        int row1Y = curY;
        addRenderableWidget(new ShopButton(cx - halfW - pairGap / 2, row1Y, halfW, btnH,
                Component.literal("上移"),
                btn -> {
                    PacketDistributor.sendToServer(new ShopEditPayload(
                            "move_category_up", categoryName, "", "", 0, -1, false, "", 1, ""));
                    parentScreen.refreshFromConfig();
                }));
        addRenderableWidget(new ShopButton(cx + pairGap / 2, row1Y, halfW, btnH,
                Component.literal("下移"),
                btn -> {
                    PacketDistributor.sendToServer(new ShopEditPayload(
                            "move_category_down", categoryName, "", "", 0, -1, false, "", 1, ""));
                    parentScreen.refreshFromConfig();
                }));

        // ── 第 2 行：删除此分类（整行宽） ──────────────────
        curY += btnH + rowGap;
        int fullW = panelW - pad * 2;
        addRenderableWidget(new ShopButton(cx - fullW / 2, curY, fullW, btnH,
                Component.literal("删除此分类"),
                btn -> {
                    PacketDistributor.sendToServer(new ShopEditPayload(
                            "remove_category", categoryName, "", "", 0, -1, false, "", 1, ""));
                    this.minecraft.setScreen(parentScreen);
                    parentScreen.refreshFromConfig();
                }));

        // ── 第 3 行：取消 | 保存 ──────────────────────────
        curY += btnH + rowGap;
        int lastY = curY;
        addRenderableWidget(new ShopButton(cx - halfW - pairGap / 2, lastY, halfW, btnH,
                Component.translatable("gui.shop.cancel"),
                btn -> this.minecraft.setScreen(parentScreen)));
        addRenderableWidget(new ShopButton(cx + pairGap / 2, lastY, halfW, btnH,
                Component.literal("保存"),
                btn -> {
                    String newName = nameBox.getValue().trim();
                    if (!newName.isEmpty() && !newName.equals(categoryName)) {
                        PacketDistributor.sendToServer(new ShopEditPayload(
                                "rename_category", categoryName, newName, "", 0, -1, false, "", 1, ""));
                    }
                    parentScreen.refreshFromConfig();
                    this.minecraft.setScreen(parentScreen);
                }));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // 背景由 render 里的遮罩负责，这里不画原版模糊
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        GuiDraw.scrim(g, width, height);
        GuiDraw.panelWithStripe(g, panelLeft, panelTop, panelW, panelH);

        // 输入框底色画在 EditBox 下面（EditBox 自身无边框）
        GuiDraw.inputBox(g, nameBoxX, nameBoxY, nameBoxW, nameBoxH, nameBox.isFocused());

        super.render(g, mx, my, pt);

        GuiDraw.title(g, font, "编辑分类", cx, panelTop + (int) Math.round(8 * sf), panelW - 10);

        int pad = (int) Math.round(15 * sf);
        GuiDraw.label(g, font, "名称:", panelLeft + pad, nameBoxY + 4, 60);
        if (nameBox.getValue().isEmpty() && !nameBox.isFocused()) {
            GuiDraw.hint(g, font, "分类名称", GuiDraw.innerTextX(nameBoxX), GuiDraw.innerTextY(nameBoxY, nameBoxH), nameBoxW - 8);
        }

        int count = ShopConfig.getCategory(categoryName) != null ? ShopConfig.getCategory(categoryName).size() : 0;
        GuiDraw.note(g, font, "商品数: " + count, cx,
                panelTop + panelH - (int) Math.round(20 * sf), panelW - 20);
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
