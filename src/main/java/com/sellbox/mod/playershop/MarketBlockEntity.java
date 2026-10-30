package com.sellbox.mod.playershop;

import com.sellbox.mod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MarketBlockEntity extends BlockEntity implements WorldlyContainer {
    static final int CONTAINER_SIZE = 54;
    public static final int BAG_SLOT = 53;
    public static final int RECEIPT_BOOK_SLOT = 52;

    public static final Map<UUID, BlockPos> MARKET_POSITIONS = new HashMap<>();

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private UUID owner;
    private String ownerName = "";
    private ItemStackHandler itemHandler;

    public MarketBlockEntity(BlockPos pos, BlockState state) {
        super(PlayerShopRegistry.MARKET_BLOCK_ENTITY.get(), pos, state);
        initHandler();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (owner != null) MARKET_POSITIONS.put(owner, worldPosition);
    }

    @Override
    public void setRemoved() {
        if (owner != null && MARKET_POSITIONS.get(owner) == worldPosition) MARKET_POSITIONS.remove(owner);
        super.setRemoved();
    }

    private void initHandler() {
        this.itemHandler = new ItemStackHandler(CONTAINER_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                if (slot == BAG_SLOT) return stack.is(ModItems.COIN_BAG.get());
                if (slot == RECEIPT_BOOK_SLOT) return stack.is(Items.WRITTEN_BOOK);
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
                    items.set(slot, stack);
                    onContentsChanged(slot);
                }
            }

            @Override
            public int getSlots() {
                return CONTAINER_SIZE;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (slot == RECEIPT_BOOK_SLOT || slot == BAG_SLOT) return stack;
                if (slot >= 0 && slot < items.size()) {
                    ItemStack existing = items.get(slot);
                    if (existing.isEmpty()) {
                        if (!simulate) {
                            items.set(slot, stack.copy());
                            onContentsChanged(slot);
                        }
                        return ItemStack.EMPTY;
                    }
                    if (ItemStack.isSameItemSameTags(existing, stack)) {
                        int canInsert = Math.min(stack.getCount(), getSlotLimit(slot) - existing.getCount());
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
                }
                return stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (slot == RECEIPT_BOOK_SLOT || slot == BAG_SLOT) return ItemStack.EMPTY;
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
                return 1000000;
            }
        };
    }

    @Nullable
    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) {
        this.owner = owner;
        if (owner != null) MARKET_POSITIONS.put(owner, worldPosition);
    }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String name) { this.ownerName = name; }

    public Component getDisplayName() {
        Component base = Component.translatable("block.sellbox.market");
        if (ownerName != null && !ownerName.isEmpty()) {
            return Component.literal("").append(base).append(Component.literal(" - " + ownerName));
        }
        return base;
    }

    public void depositEarnedItem(ItemStack stack) {
        if (level == null || level.isClientSide || stack.isEmpty()) return;
        for (int i = 0; i < RECEIPT_BOOK_SLOT; i++) {
            ItemStack slotStack = items.get(i);
            if (slotStack.isEmpty()) {
                items.set(i, stack.copy());
                setChanged();
                sync();
                return;
            }
            if (ItemStack.isSameItemSameTags(slotStack, stack)) {
                long total = (long) slotStack.getCount() + stack.getCount();
                slotStack.setCount(total > 1000000 ? 1000000 : (int) total);
                setChanged();
                sync();
                return;
            }
        }
    }

    public void writeReceipt(String playerName, String receiptText) {
        if (level == null || level.isClientSide) return;
        String time = java.time.LocalDateTime.now().toString().substring(0, 16).replace("T", " ");
        net.minecraft.network.chat.MutableComponent page = Component.literal("交易收据");
        page = page.append(Component.literal("\n时间: " + time));
        page = page.append(Component.literal("\n玩家: " + playerName));
        page = page.append(Component.literal("\n详情: " + receiptText));

        ListTag pagesTag = new ListTag();
        ItemStack existing = items.get(RECEIPT_BOOK_SLOT);
        if (!existing.isEmpty() && existing.is(Items.WRITTEN_BOOK)) {
            CompoundTag existingTag = existing.getTag();
            if (existingTag != null && existingTag.contains("pages")) {
                pagesTag = existingTag.getList("pages", 8);
            }
        }
        if (pagesTag.size() >= 100) {
            pagesTag.remove(0);
        }
        pagesTag.add(StringTag.valueOf(Component.Serializer.toJson(page)));

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag bookTag = book.getOrCreateTag();
        bookTag.putString("title", "市场收据");
        bookTag.putString("author", "市场");
        bookTag.putByte("resolved", (byte) 1);
        bookTag.put("pages", pagesTag);
        items.set(RECEIPT_BOOK_SLOT, book);
        setChanged();
    }

    public void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        if (tag.hasUUID("Owner")) {
            this.owner = tag.getUUID("Owner");
            MARKET_POSITIONS.put(owner, worldPosition);
        }
        this.ownerName = tag.getString("OwnerName");
        initHandler();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public int getContainerSize() { return CONTAINER_SIZE; }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (owner == null) return true;
        return owner.equals(player.getUUID()) && Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == BAG_SLOT) return stack.is(ModItems.COIN_BAG.get());
        if (slot == RECEIPT_BOOK_SLOT) return stack.is(Items.WRITTEN_BOOK);
        return true;
    }

    private static final int[] SLOTS_FOR_FACE = new int[52];
    static {
        for (int i = 0; i < 52; i++) SLOTS_FOR_FACE[i] = i;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS_FOR_FACE;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (slot == BAG_SLOT || slot == RECEIPT_BOOK_SLOT) return false;
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot == BAG_SLOT || slot == RECEIPT_BOOK_SLOT) return false;
        return true;
    }

    @Override
    public void clearContent() { items.clear(); setChanged(); }

    @Override
    public int getMaxStackSize() { return 1000000; }
}
