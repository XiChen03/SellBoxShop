package com.sellbox.mod.gui;

import com.sellbox.mod.shop.ShopButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class SellBoxScreen extends AbstractContainerScreen<SellBoxMenu> {
    private static final ResourceLocation CONTAINER_BACKGROUND = new ResourceLocation("minecraft:textures/gui/container/generic_54.png");
    private static final int CONTAINER_ROWS = 6;

    public SellBoxScreen(SellBoxMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageHeight = 114 + CONTAINER_ROWS * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;

        var player = Minecraft.getInstance().player;
        if (player != null && player.isCreative()) {
            String label = "定价管理";
            int btnW = font.width(label) + 12;
            int btnH = 14;
            int buttonX = this.leftPos + this.imageWidth - btnW;
            int buttonY = this.topPos - btnH - 5;
            this.addRenderableWidget(new ShopButton(buttonX, buttonY, btnW, btnH,
                    Component.literal(label),
                    btn -> Minecraft.getInstance().setScreen(new PricingScreen())));
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(CONTAINER_BACKGROUND, x, y, 0, 0, this.imageWidth, CONTAINER_ROWS * 18 + 17);
        guiGraphics.blit(CONTAINER_BACKGROUND, x, y + CONTAINER_ROWS * 18 + 17, 0, 126, this.imageWidth, 96);

        int bagX = x + 8 + 8 * 18;
        int bagY = y + 18 + 5 * 18;
        guiGraphics.fill(bagX - 1, bagY - 1, bagX + 17, bagY + 17, 0xFFFFD700);
        guiGraphics.fill(bagX, bagY, bagX + 16, bagY + 16, 0xFF2D2D2D);

        int receiptX = x + 8 + 7 * 18;
        int receiptY = y + 18 + 5 * 18;
        guiGraphics.fill(receiptX - 1, receiptY - 1, receiptX + 17, receiptY + 17, 0xFFBB00FF);
        guiGraphics.fill(receiptX, receiptY, receiptX + 16, receiptY + 16, 0xFF2D2D2D);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null) this.minecraft = Minecraft.getInstance();
        if (this.font == null) this.font = Minecraft.getInstance().font;
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        int bagX = this.leftPos + 8 + 8 * 18;
        int bagY = this.topPos + 18 + 5 * 18;
        if (mouseX >= bagX && mouseX <= bagX + 16 && mouseY >= bagY && mouseY <= bagY + 16) {
            ItemStack bagStack = this.menu.getSlot(53).getItem();
            if (bagStack.isEmpty()) {
                guiGraphics.renderTooltip(font, Component.translatable("gui.sellbox.bag_slot"), mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(font, bagStack.getHoverName(), mouseX, mouseY);
            }
        }

        int receiptX = this.leftPos + 8 + 7 * 18;
        int receiptY = this.topPos + 18 + 5 * 18;
        if (mouseX >= receiptX && mouseX <= receiptX + 16 && mouseY >= receiptY && mouseY <= receiptY + 16) {
            ItemStack receiptStack = this.menu.getSlot(52).getItem();
            if (receiptStack.isEmpty()) {
                guiGraphics.renderTooltip(font, Component.translatable("gui.sellbox.receipt_slot"), mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(font, receiptStack.getHoverName(), mouseX, mouseY);
            }
        }
    }
}