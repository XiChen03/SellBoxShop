package com.sellbox.mod.playershop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class MarketScreen extends AbstractContainerScreen<MarketMenu> {
    private final com.sellbox.mod.gui.GuiTheme T = com.sellbox.mod.gui.GuiTheme.get();
    private static final ResourceLocation CONTAINER_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int CONTAINER_ROWS = 6;

    public MarketScreen(MarketMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageHeight = 114 + CONTAINER_ROWS * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(CONTAINER_BACKGROUND, x, y, 0, 0, this.imageWidth, CONTAINER_ROWS * 18 + 17);
        guiGraphics.blit(CONTAINER_BACKGROUND, x, y + CONTAINER_ROWS * 18 + 17, 0, 126, this.imageWidth, 96);

        // receipt slot (slot 52 = row 5, col 7)
        int receiptX = x + 8 + 7 * 18;
        int receiptY = y + 18 + 5 * 18;
        guiGraphics.fill(receiptX - 1, receiptY - 1, receiptX + 17, receiptY + 17, T.slotReceiptBorder);
        guiGraphics.fill(receiptX, receiptY, receiptX + 16, receiptY + 16, T.slotInnerFill);
        if (!this.menu.getSlot(52).hasItem()) {
            Component receiptLabel = Component.translatable("gui.sellbox.market_receipt_slot");
            int textW = font.width(receiptLabel);
            float scale = Math.min(1.0f, 14.0f / textW);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(receiptX + 8 - textW * scale / 2, receiptY + 12, 0);
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.drawString(font, receiptLabel, 0, 0, T.slotLabelText);
            guiGraphics.pose().popPose();
        }

        // bag slot (slot 53 = row 5, col 8)
        int bagX = x + 8 + 8 * 18;
        int bagY = y + 18 + 5 * 18;
        guiGraphics.fill(bagX - 1, bagY - 1, bagX + 17, bagY + 17, T.slotBagBorder);
        guiGraphics.fill(bagX, bagY, bagX + 16, bagY + 16, T.slotInnerFill);
        if (!this.menu.getSlot(53).hasItem()) {
            Component bagLabel = Component.translatable("gui.sellbox.market_bag_slot");
            int textW = font.width(bagLabel);
            float scale = Math.min(1.0f, 14.0f / textW);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(bagX + 8 - textW * scale / 2, bagY + 12, 0);
            guiGraphics.pose().scale(scale, scale, 1.0f);
            guiGraphics.drawString(font, bagLabel, 0, 0, T.slotLabelText);
            guiGraphics.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int receiptX = this.leftPos + 8 + 7 * 18;
        int receiptY = this.topPos + 18 + 5 * 18;
        if (mouseX >= receiptX && mouseX <= receiptX + 16 && mouseY >= receiptY && mouseY <= receiptY + 16
                && !this.menu.getSlot(52).hasItem()) {
            guiGraphics.renderTooltip(font, Component.translatable("gui.sellbox.market_receipt_slot"), mouseX, mouseY);
        }

        int bagX = this.leftPos + 8 + 8 * 18;
        int bagY = this.topPos + 18 + 5 * 18;
        if (mouseX >= bagX && mouseX <= bagX + 16 && mouseY >= bagY && mouseY <= bagY + 16
                && !this.menu.getSlot(53).hasItem()) {
            guiGraphics.renderTooltip(font, Component.translatable("gui.sellbox.market_bag_slot"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 跳过收据(52)和钱袋(53)槽位，避免与自定义 tooltip 重叠显示
        if (this.hoveredSlot != null && (this.hoveredSlot.index == 52 || this.hoveredSlot.index == 53)) {
            return;
        }
        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
