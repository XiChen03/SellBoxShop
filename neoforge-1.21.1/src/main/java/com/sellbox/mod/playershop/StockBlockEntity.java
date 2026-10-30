package com.sellbox.mod.playershop;

import com.sellbox.mod.SellBoxMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class StockBlockEntity extends BlockEntity implements Container {
    private static final int CONTAINER_SIZE = 54;
    /** ItemStack 存档 codec 只接受 count ∈ [1,99]；超过会让整个方块实体保存失败 */
    private static final int MAX_SAVEABLE_STACK = 99;

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private UUID owner = null;
    private String ownerName = "";

    private ItemStackHandler itemHandler;

    public StockBlockEntity(BlockPos pos, BlockState state) {
        super(PlayerShopRegistry.STOCK_BLOCK_ENTITY.get(), pos, state);
        initHandler();
    }

    private void initHandler() {
        this.itemHandler = new ItemStackHandler(CONTAINER_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return true;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                if (slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
                return items.get(slot);
            }

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                if (slot >= 0 && slot < items.size()) {
                    items.set(slot, clampStack(stack));
                    onContentsChanged(slot);
                }
            }

            @Override
            public int getSlots() {
                return CONTAINER_SIZE;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (slot < 0 || slot >= items.size() || stack.isEmpty()) return stack;
                int limit = stackLimit(stack);
                ItemStack existing = items.get(slot);
                if (existing.isEmpty()) {
                    int put = Math.min(stack.getCount(), limit);
                    if (put <= 0) return stack;
                    if (!simulate) {
                        items.set(slot, stack.copyWithCount(put));
                        onContentsChanged(slot);
                    }
                    return put >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - put);
                }
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    int canInsert = Math.min(stack.getCount(), limit - existing.getCount());
                    if (canInsert > 0) {
                        if (!simulate) {
                            existing.grow(canInsert);
                            onContentsChanged(slot);
                        }
                        ItemStack remaining = stack.copy();
                        remaining.shrink(canInsert);
                        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
                    }
                }
                return stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (slot >= 0 && slot < items.size()) {
                    ItemStack existing = items.get(slot);
                    if (existing.isEmpty()) return ItemStack.EMPTY;
                    int toExtract = Math.min(amount, existing.getCount());
                    ItemStack extracted = existing.copy();
                    extracted.setCount(toExtract);
                    if (!simulate) {
                        existing.shrink(toExtract);
                        if (existing.isEmpty()) {
                            items.set(slot, ItemStack.EMPTY);
                        }
                        onContentsChanged(slot);
                    }
                    return extracted;
                }
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return MAX_SAVEABLE_STACK;
            }
        };
    }

    /** 单个槽位实际可容纳数量：既要能存档（<=99），也不能超过物品自身的堆叠上限 */
    private static int stackLimit(ItemStack stack) {
        return Math.max(1, Math.min(MAX_SAVEABLE_STACK, stack.getMaxStackSize()));
    }

    private static ItemStack clampStack(ItemStack stack) {
        if (stack.isEmpty()) return stack;
        int limit = stackLimit(stack);
        return stack.getCount() > limit ? stack.copyWithCount(limit) : stack;
    }

    /**
     * 存档兜底：ItemStack 只接受 count ∈ [1,99]，任何超过 99 的堆叠都会让整个方块实体保存失败
     * （所有物品一起丢）。这里把超大堆叠拆到其它槽位；实在放不下才丢弃并记日志。
     */
    private void sanitizeStacks() {
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty() || stack.getCount() <= MAX_SAVEABLE_STACK) continue;
            int original = stack.getCount();
            int excess = original - MAX_SAVEABLE_STACK;
            items.set(i, stack.copyWithCount(MAX_SAVEABLE_STACK));
            for (int j = 0; j < items.size() && excess > 0; j++) {
                if (j == i) continue;
                ItemStack target = items.get(j);
                if (target.isEmpty()) {
                    int put = Math.min(excess, MAX_SAVEABLE_STACK);
                    items.set(j, stack.copyWithCount(put));
                    excess -= put;
                } else if (ItemStack.isSameItemSameComponents(target, stack)
                        && target.getCount() < MAX_SAVEABLE_STACK) {
                    int put = Math.min(excess, MAX_SAVEABLE_STACK - target.getCount());
                    target.grow(put);
                    excess -= put;
                }
            }
            if (excess > 0) {
                SellBoxMod.LOGGER.error("Stock at {} had an unsaveable stack of {} x{}; {} item(s) could not be stored and were dropped",
                        worldPosition, stack.getItem(), original, excess);
            }
        }
    }

    @Nullable
    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Component getDisplayName() {
        Component base = Component.translatable("block.sellbox.stock");
        if (ownerName != null && !ownerName.isEmpty()) {
            return Component.literal("").append(base).append(Component.literal(" - " + ownerName));
        }
        return base;
    }

    public void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        sanitizeStacks();
        if (tag.hasUUID("Owner")) {
            this.owner = tag.getUUID("Owner");
        }
        this.ownerName = tag.getString("OwnerName");
        initHandler();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        sanitizeStacks();
        ContainerHelper.saveAllItems(tag, items, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, clampStack(stack));
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (owner == null) return true;
        return owner.equals(player.getUUID()) && Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return MAX_SAVEABLE_STACK;
    }
}
