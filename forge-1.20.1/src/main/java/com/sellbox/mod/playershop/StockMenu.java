package com.sellbox.mod.playershop;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class StockMenu extends AbstractContainerMenu {
    private final StockBlockEntity blockEntity;
    private final Container container;

    public StockMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(id, playerInv, (StockBlockEntity) playerInv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public StockMenu(int id, Inventory playerInv, StockBlockEntity be) {
        super(PlayerShopRegistry.STOCK_MENU.get(), id);
        this.blockEntity = be;
        this.container = be;

        int rows = 6;
        int cols = 9;
        int i = (rows - 4) * 18;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int slot = row * cols + col;
                int x = 8 + col * 18;
                int y = 18 + row * 18;
                addSlot(new Slot(container, slot, x, y));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + i));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 161 + i));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            stack = slotStack.copy();

            if (slotIndex < 54) {
                if (!moveItemStackTo(slotStack, 54, 90, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= 54 && slotIndex < 90) {
                if (!moveItemStackTo(slotStack, 0, 54, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return stack;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    public StockBlockEntity getBlockEntity() {
        return blockEntity;
    }
}
