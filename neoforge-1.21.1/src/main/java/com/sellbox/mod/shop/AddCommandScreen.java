package com.sellbox.mod.shop;

import com.sellbox.mod.shop.network.ShopEditPayload;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public class AddCommandScreen extends Screen {
    private float sf;
    private int panelLeft, panelTop, panelW, panelH;
    private int cx;
    private int fieldW, boxH, btnH, btnW;
    private int commandBoxY, commandLabelY;
    private int priceBoxY, priceLabelY;
    private int opBtnY;
    private int iconBoxY, iconLabelY;
    private int nameBoxY, nameLabelY;
    private int saveBtnX, backBtnX, btnRowY;

    private final ShopScreen parentScreen;
    private final String categoryName;

    private EditBox commandBox;
    private EditBox priceBox;
    private EditBox iconBox;
    private EditBox nameBox;
    private boolean requireOp = false;

    private final int editIndex;
    private final ShopEntry editEntry;

    private String savedCmd, savedPrice, savedName, savedIcon;
    private boolean savedOp;

    public AddCommandScreen(ShopScreen parent, String categoryName) {
        this(parent, categoryName, -1, null);
    }

    public AddCommandScreen(ShopScreen parent, String categoryName, int editIndex, ShopEntry editEntry) {
        super(Component.literal(editIndex >= 0 ? "编辑指令" : "添加指令"));
        this.parentScreen = parent;
        this.categoryName = categoryName;
        this.editIndex = editIndex;
        this.editEntry = editEntry;
    }

    @Override
    protected void init() {
        super.init();
        sf = (float) ShopUI.getScale(height);
        boxH = (int) Math.round(16 * sf);
        btnH = (int) Math.round(18 * sf);
        btnW = (int) Math.round(60 * sf);

        int maxW = Math.min(width - 20, (int) Math.round(320 * sf));
        panelW = Math.max(260, maxW);
        panelLeft = (width - panelW) / 2;
        cx = panelLeft + panelW / 2;

        int pad = (int) Math.round(15 * sf);
        fieldW = panelW - pad * 2;

        // Content layout (relative to panel top)
        int titleAreaH = (int) Math.round(24 * sf);
        int rowGap = (int) Math.round(6 * sf);
        int fieldH = boxH + (int) Math.round(14 * sf); // label + box per row
        int opRowH = (int) Math.round(4 * sf) + btnH;
        int bottomRowH = btnH + (int) Math.round(10 * sf);

        // Rows: title, cmd, price, op, name, icon, bottom
        // 每个字段: label(12*sf) + box(boxH) + gap(8*sf)
        int fieldRowH = (int) Math.round(12 * sf) + boxH + (int) Math.round(8 * sf);
        int totalContentH = titleAreaH + fieldRowH * 4 + opRowH + bottomRowH;

        int maxPanelH = height - 15;
        panelH = Math.min((int) Math.round(totalContentH) + 18, maxPanelH);
        panelH = Math.max(panelH, (int) Math.round(200 * sf));

        panelTop = Math.max(10, (height - panelH) / 2 - 15);
        if (panelTop + panelH > height - 5) {
            panelTop = height - 5 - panelH;
            panelTop = Math.max(10, panelTop);
        }

        // Compute absolute Y positions
        int curY = panelTop + (int) Math.round(8 * sf); // title Y
        curY += (int) Math.round(16 * sf); // after title + accent gap

        commandLabelY = curY;
        curY += (int) Math.round(12 * sf);
        commandBoxY = curY;
        curY += boxH + (int) Math.round(8 * sf);

        priceLabelY = curY;
        curY += (int) Math.round(12 * sf);
        priceBoxY = curY;
        curY += boxH + (int) Math.round(8 * sf);

        opBtnY = curY;
        curY += btnH + (int) Math.round(8 * sf);

        nameLabelY = curY;
        curY += (int) Math.round(12 * sf);
        nameBoxY = curY;
        curY += boxH + (int) Math.round(8 * sf);

        iconLabelY = curY;
        curY += (int) Math.round(12 * sf);
        iconBoxY = curY;
        curY += boxH + (int) Math.round(10 * sf);

        btnRowY = curY;

        int lx = panelLeft + pad;
        commandBox = new EditBox(font, lx + (int) Math.round(2 * sf), commandBoxY + 1, fieldW - (int) Math.round(4 * sf), boxH - 2, Component.literal(""));
        commandBox.setBordered(false);
        commandBox.setMaxLength(256);
        if (editEntry != null) commandBox.setValue(editEntry.extraData());
        addRenderableWidget(commandBox);

        int priceW = (int) Math.round(80 * sf);
        priceBox = new EditBox(font, lx + (int) Math.round(2 * sf), priceBoxY + 1, priceW - (int) Math.round(4 * sf), boxH - 2, Component.literal(""));
        priceBox.setBordered(false);
        priceBox.setFilter(s -> s.matches("\\d*"));
        if (editEntry != null) priceBox.setValue(String.valueOf(editEntry.price()));
        addRenderableWidget(priceBox);

        if (editEntry != null) requireOp = editEntry.requireOp();
        addRenderableWidget(new ShopButton(lx, opBtnY, (int) Math.round(120 * sf), btnH,
                Component.literal("OP权限: " + (requireOp ? "是" : "否")),
                btn -> {
                    requireOp = !requireOp;
                    btn.setMessage(Component.literal("OP权限: " + (requireOp ? "是" : "否")));
                }));

        int pickerBtnX = lx + (int) Math.round(120 * sf) + (int) Math.round(4 * sf);
        int pickerBtnW = (int) Math.round(100 * sf);
        addRenderableWidget(new ShopButton(pickerBtnX, opBtnY, pickerBtnW, btnH,
                Component.literal("选择物品作为贴图"),
                btn -> {
                    savedCmd = commandBox.getValue();
                    savedPrice = priceBox.getValue();
                    savedName = nameBox.getValue();
                    savedOp = requireOp;
                    savedIcon = null;
                    minecraft.setScreen(new PlayerInventoryPickerScreen(this,
                            stack -> savedIcon = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
                }));

        nameBox = new EditBox(font, lx + (int) Math.round(2 * sf), nameBoxY + 1, fieldW - (int) Math.round(4 * sf), boxH - 2, Component.literal(""));
        nameBox.setBordered(false);
        nameBox.setMaxLength(64);
        if (editEntry != null) nameBox.setValue(editEntry.customName());
        addRenderableWidget(nameBox);

        iconBox = new EditBox(font, lx + (int) Math.round(2 * sf), iconBoxY + 1, fieldW - (int) Math.round(4 * sf), boxH - 2, Component.literal(""));
        iconBox.setBordered(false);
        if (editEntry != null) {
            iconBox.setValue(editEntry.itemId().toString());
        } else {
            iconBox.setValue("minecraft:command_block");
        }
        addRenderableWidget(iconBox);

        // Restore state from picker (values preserved across init)
        if (savedCmd != null) {
            commandBox.setValue(savedCmd);
            priceBox.setValue(savedPrice);
            nameBox.setValue(savedName);
            requireOp = savedOp;
            if (savedIcon != null) iconBox.setValue(savedIcon);
            for (var w : children()) {
                if (w instanceof ShopButton sb && sb.getMessage().getString().startsWith("OP权限:")) {
                    sb.setMessage(Component.literal("OP权限: " + (requireOp ? "是" : "否")));
                    break;
                }
            }
            savedCmd = null;
        }

        saveBtnX = cx - btnW - (int) Math.round(6 * sf);
        backBtnX = cx + (int) Math.round(6 * sf);
        addRenderableWidget(new ShopButton(saveBtnX, btnRowY, btnW, btnH, Component.literal("保存"), btn -> save()));
        addRenderableWidget(new ShopButton(backBtnX, btnRowY, btnW, btnH, Component.literal("返回"), btn -> minecraft.setScreen(parentScreen)));
    }

    private void save() {
        String cmd = commandBox.getValue().trim();
        if (cmd.isEmpty()) return;
        int price;
        try { price = Integer.parseInt(priceBox.getValue()); if (price < 0) price = 0; }
        catch (NumberFormatException e) { return; }
        ResourceLocation icon = ResourceLocation.tryParse(iconBox.getValue().trim());
        if (icon == null) icon = ResourceLocation.withDefaultNamespace("command_block");
        String action = editIndex >= 0 ? "edit_command" : "add_command";
        PacketDistributor.sendToServer(new ShopEditPayload(
                action, categoryName, cmd, "", price, Math.max(0, editIndex), requireOp, icon.toString(), 1, nameBox.getValue().trim()));
        minecraft.setScreen(parentScreen);
        parentScreen.refreshFromConfig();
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
 
        drawStyledBox(g, lx, commandBoxY, fieldW, boxH, commandBox.isFocused());
        FontUtil.drawAutoFitText(g, font, "指令 (不含/):", lx, commandLabelY, fieldW, 0xFFFFFF);
 
        int priceW = (int) Math.round(80 * sf);
        drawStyledBox(g, lx, priceBoxY, priceW, boxH, priceBox.isFocused());
        FontUtil.drawAutoFitText(g, font, "价格:", lx, priceLabelY, fieldW, 0xFFFFFF);
 
        drawStyledBox(g, lx, iconBoxY, fieldW, boxH, iconBox.isFocused());
        FontUtil.drawAutoFitText(g, font, "图标物品ID:", lx, iconLabelY, fieldW, 0xFFFFFF);
 
        drawStyledBox(g, lx, nameBoxY, fieldW, boxH, nameBox.isFocused());
        FontUtil.drawAutoFitText(g, font, "自定义名称:", lx, nameLabelY, fieldW, 0xFFFFFF);
        if (nameBox.getValue().isEmpty() && !nameBox.isFocused()) {
            FontUtil.drawAutoFitText(g, font, "可选，留空则默认显示指令", lx + (int) Math.round(6 * sf), nameBoxY + (int) Math.round(3 * sf), fieldW - (int) Math.round(12 * sf), 0x777777);
        }
    }

    private void drawStyledBox(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        int col = focused ? 0x55FFFFFF : 0x55AAAAAA;
        g.fill(x + 2, y, x + w - 2, y + 1, col);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, col);
        g.fill(x, y + 2, x + 1, y + h - 2, col);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, col);
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
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == 256) { minecraft.setScreen(parentScreen); return true; }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
    }
}
