package com.sellbox.mod.shop;

import com.sellbox.mod.gui.GuiDraw;
import com.sellbox.mod.gui.GuiTheme;
import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
    /** 主题对象身份恒定，切换主题就地刷新 */
    private final GuiTheme T = GuiTheme.get();

    private int popupW = 240;
    private int popupH = 200;

    private final ShopScreen parentScreen;
    private final String categoryName;
    private EditBox nameBox;
    private int nameBoxX, nameBoxY, nameBoxW, nameBoxH;
    private int popupTop, popupLeft, row3Bottom;

    public EditCategoryScreen(ShopScreen parent, String categoryName) {
        super(Component.literal("编辑分类 - " + categoryName));
        this.parentScreen = parent;
        this.categoryName = categoryName;
    }

    @Override
    protected void init() {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;

        double scale = ShopUI.getScale(height);
        int btnH = (int) Math.round(16 * scale);
        int gap = (int) Math.round(6 * scale);
        int pad = (int) Math.round(14 * scale);

        popupW = Math.max(230, (int) Math.round(250 * scale));
        // 内容：标题(26) + 名称行 + 上移/下移 + 删除 + 取消/保存 + 底部信息(24)
        popupH = (int) Math.round(26 * scale) + (btnH * 3) + (gap * 2) + (int) Math.round(40 * scale);
        popupH = Math.max(popupH, 150);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - popupW / 2;
        int top = cy - popupH / 2;

        nameBoxW = popupW - pad * 2 - (int) Math.round(34 * scale);
        nameBoxH = (int) Math.round(12 * scale);
        nameBoxX = left + pad + (int) Math.round(34 * scale);
        nameBoxY = top + (int) Math.round(32 * scale);

        this.nameBox = new EditBox(this.font, GuiDraw.innerTextX(nameBoxX), GuiDraw.innerTextY(nameBoxY, nameBoxH), GuiDraw.innerWidth(nameBoxW), nameBoxH - 2,
                Component.literal("分类名称"));
        this.nameBox.setValue(categoryName);
        this.nameBox.setBordered(false);
        this.addRenderableWidget(this.nameBox);

        int pairGap = (int) Math.round(6 * scale);
        int halfW = (popupW - pad * 2 - pairGap) / 2;

        // ── 第 1 行：上移 | 下移 ──────────────────────────
        int row1Y = nameBoxY + nameBoxH + (int) Math.round(8 * scale);
        addRenderableWidget(new ShopButton(cx - halfW - pairGap / 2, row1Y, halfW, btnH,
                Component.literal("上移"),
                btn -> {
                    if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket(
                            "move_category_up", categoryName, "", "", 0, -1, false, "", 1, "", "", 1));
                    parentScreen.refreshFromConfig();
                }));
        addRenderableWidget(new ShopButton(cx + pairGap / 2, row1Y, halfW, btnH,
                Component.literal("下移"),
                btn -> {
                    if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket(
                            "move_category_down", categoryName, "", "", 0, -1, false, "", 1, "", "", 1));
                    parentScreen.refreshFromConfig();
                }));

        // ── 第 2 行：删除此分类（整行宽） ──────────────────
        int row2Y = row1Y + btnH + gap;
        int fullW = popupW - pad * 2;
        addRenderableWidget(new ShopButton(cx - fullW / 2, row2Y, fullW, btnH,
                Component.literal("删除此分类"),
                btn -> {
                    if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
                    ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket(
                            "remove_category", categoryName, "", "", 0, -1, false, "", 1, "", "", 1));
                    this.minecraft.setScreen(parentScreen);
                    parentScreen.refreshFromConfig();
                }));

        // ── 第 3 行：取消 | 保存 ──────────────────────────
        int row3Y = row2Y + btnH + gap;
        addRenderableWidget(new ShopButton(cx - halfW - pairGap / 2, row3Y, halfW, btnH,
                Component.translatable("gui.shop.cancel"),
                btn -> {
                    if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
                    this.minecraft.setScreen(parentScreen);
                }));
        addRenderableWidget(new ShopButton(cx + pairGap / 2, row3Y, halfW, btnH,
                Component.literal("保存"),
                btn -> {
                    String newName = nameBox.getValue().trim();
                    if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
                    if (!newName.isEmpty() && !newName.equals(categoryName)) {
                        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket(
                                "rename_category", categoryName, newName, "", 0, -1, false, "", 1, "", "", 1));
                    }
                    parentScreen.refreshFromConfig();
                    this.minecraft.setScreen(parentScreen);
                }));

        // 保存布局供 render 复用
        this.popupTop = top;
        this.popupLeft = left;
        this.row3Bottom = row3Y + btnH;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
        // 由 render 里的主题遮罩负责，这里不画原版模糊背景
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;

        int cx = this.width / 2;

        GuiDraw.scrim(guiGraphics, width, height);
        GuiDraw.panelWithStripe(guiGraphics, popupLeft, popupTop, popupW, popupH);
        GuiDraw.inputBox(guiGraphics, nameBoxX, nameBoxY, nameBoxW, nameBoxH, nameBox.isFocused());

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        GuiDraw.title(guiGraphics, this.font, "编辑分类", cx, popupTop + 8, popupW - 10);

        double scale = ShopUI.getScale(height);
        int pad = (int) Math.round(14 * scale);
        GuiDraw.label(guiGraphics, this.font, "名称:", popupLeft + pad, nameBoxY + 3,
                (int) Math.round(34 * scale));
        if (nameBox.getValue().isEmpty() && !nameBox.isFocused()) {
            GuiDraw.hint(guiGraphics, this.font, "分类名称", GuiDraw.innerTextX(nameBoxX), GuiDraw.innerTextY(nameBoxY, nameBoxH), nameBoxW - 8);
        }

        int count = ShopConfig.getCategory(categoryName) != null ? ShopConfig.getCategory(categoryName).size() : 0;
        GuiDraw.note(guiGraphics, this.font, "商品数: " + count, cx,
                Math.min(popupTop + popupH - 18, row3Bottom + 8), popupW - 20);
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
        if (keyCode == 256) {
            if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
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
