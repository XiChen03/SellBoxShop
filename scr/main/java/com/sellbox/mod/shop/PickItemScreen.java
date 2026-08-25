package com.sellbox.mod.shop;

import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.util.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class PickItemScreen extends Screen {
    private int popupW = 360;
    private int popupH = 290;
    private static final int INV_COLS = 9;
    private int invSlotSize = 24;

    private final ShopScreen parentScreen;
    private final String categoryName;

    private ResourceLocation selectedItemId;
    private String selectedItemName = "";
    private EditBox priceBox;
    private ShopEntry.TradeType tradeType = ShopEntry.TradeType.BUY;
    private ItemStack[] inventoryItems;

    public PickItemScreen(ShopScreen parent, String categoryName) {
        super(Component.literal("添加商品"));
        this.parentScreen = parent;
        this.categoryName = categoryName;
    }

    @Override
    protected void init() {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        double scale = ShopUI.getScale(height);
        popupW = (int) Math.round(360 * scale);
        popupH = (int) Math.round(290 * scale);
        invSlotSize = (int) Math.round(24 * scale);
        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - popupW / 2;
        int top = cy - popupH / 2;

        var player = Minecraft.getInstance().player;
        inventoryItems = new ItemStack[36];
        if (player != null) {
            for (int i = 0; i < 36; i++) {
                inventoryItems[i] = player.getInventory().items.get(i);
            }
        }

        this.priceBox = new EditBox(this.font, left + 55, top + 175, 100, 16,
                Component.literal("价格"));
        this.priceBox.setValue("1");
        this.addRenderableWidget(this.priceBox);

        addRenderableWidget(new ShopButton(left + 55, top + 200, 100, 20, Component.literal("购买/出售"),
                btn -> {
                    tradeType = (tradeType == ShopEntry.TradeType.BUY) ? ShopEntry.TradeType.SELL : ShopEntry.TradeType.BUY;
                    btn.setMessage(Component.literal(tradeType == ShopEntry.TradeType.BUY ? "当前: 购买" : "当前: 出售"));
                }));

        addRenderableWidget(new ShopButton(left + 70, top + 235, 60, 20, Component.literal("保存"), btn -> saveItem()));

        addRenderableWidget(new ShopButton(left + 150, top + 235, 60, 20,
                Component.translatable("gui.shop.cancel"),
                btn -> { if (this.minecraft == null) this.minecraft = Minecraft.getInstance(); this.minecraft.setScreen(parentScreen); }));
    }

    private void saveItem() {
        if (selectedItemId == null) return;
        int price;
        try {
            price = Integer.parseInt(priceBox.getValue().trim());
            if (price < 0) price = 0;
        } catch (NumberFormatException e) {
            return;
        }
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopEditPacket(
                "add_item", categoryName, selectedItemId.toString(),
                tradeType.name().toLowerCase(), price, -1, false, "", 1, "", "", 1));
        this.minecraft.setScreen(parentScreen);
    }

    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x00000000);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - popupW / 2;
        int top = cy - popupH / 2;

        fillRoundedRect(guiGraphics, left, top, popupW, popupH, 0xFF333333);
        guiGraphics.fill(left + 2, top, left + popupW - 2, top + 1, 0xFFFFAA00);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        FontUtil.drawAutoFitCentered(guiGraphics, this.font, "从背包选择物品", cx, top + 8, popupW - 10, 0xFFFFAA);

        int slotStartX = left + (popupW - INV_COLS * (invSlotSize + 2)) / 2;
        int slotStartY = top + 26;

        for (int i = 0; i < 36; i++) {
            int col = i % INV_COLS;
            int row = i / INV_COLS;
            int sx = slotStartX + col * (invSlotSize + 2);
            int sy = slotStartY + row * (invSlotSize + 2);

            boolean hovered = mouseX >= sx && mouseX <= sx + invSlotSize &&
                              mouseY >= sy && mouseY <= sy + invSlotSize;

            ItemStack stack = inventoryItems != null ? inventoryItems[i] : ItemStack.EMPTY;
            boolean isEmpty = stack == null || stack.isEmpty();

            guiGraphics.fill(sx, sy, sx + invSlotSize, sy + invSlotSize, 0x66444444);

            if (!isEmpty) {
                guiGraphics.renderItem(stack, sx + 4, sy + 4);
            }

            boolean isSelected = selectedItemId != null && !isEmpty &&
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(selectedItemId);

            if (isSelected) {
                guiGraphics.fill(sx - 1, sy - 1, sx + invSlotSize + 1, sy + invSlotSize + 1, 0xAAFFAA00);
            } else if (hovered && !isEmpty) {
                guiGraphics.fill(sx - 1, sy - 1, sx + invSlotSize + 1, sy + invSlotSize + 1, 0x66FFFFFF);
            }
        }

        FontUtil.drawAutoFitText(guiGraphics, this.font, "选中: " + selectedItemName,
                left + 10, top + 155, popupW - 20, 0xFFD700);

        FontUtil.drawAutoFitText(guiGraphics, this.font, "价格:", left + 15, top + 177, popupW - 20, 0xFFFFFF);
        FontUtil.drawAutoFitText(guiGraphics, this.font, "类型:", left + 15, top + 202, popupW - 20, 0xFFFFFF);
        FontUtil.drawAutoFitText(guiGraphics, this.font, "请在背包中选择一个物品",
                left + 15, top + popupH - 20, popupW - 30, 0x888888);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - popupW / 2;
        int top = cy - popupH / 2;

        int slotStartX = left + (popupW - INV_COLS * (invSlotSize + 2)) / 2;
        int slotStartY = top + 26;

        for (int i = 0; i < 36; i++) {
            int col = i % INV_COLS;
            int row = i / INV_COLS;
            int sx = slotStartX + col * (invSlotSize + 2);
            int sy = slotStartY + row * (invSlotSize + 2);

            if (mouseX >= sx && mouseX <= sx + invSlotSize &&
                mouseY >= sy && mouseY <= sy + invSlotSize) {
                ItemStack stack = inventoryItems != null ? inventoryItems[i] : ItemStack.EMPTY;
                if (stack != null && !stack.isEmpty()) {
                    selectedItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    selectedItemName = stack.getHoverName().getString();
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
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