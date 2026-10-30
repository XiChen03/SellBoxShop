package com.sellbox.mod.playershop;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MarketBlockEntity extends BlockEntity implements WorldlyContainer {
    static final int CONTAINER_SIZE = 54;
    public static final int BAG_SLOT = 53;
    public static final int RECEIPT_BOOK_SLOT = 52;
    /** ItemStack 存档 codec 只接受 count ∈ [1,99]；超过会让整个方块实体保存失败 */
    private static final int MAX_SAVEABLE_STACK = 99;

    private static final Map<UUID, BlockPos> MARKET_POSITIONS = new ConcurrentHashMap<>();

    public static BlockPos getMarketPos(UUID ownerUuid) {
        return MARKET_POSITIONS.get(ownerUuid);
    }

    public static void unregisterMarket(UUID ownerUuid) {
        MARKET_POSITIONS.remove(ownerUuid);
    }

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
        if (owner != null) {
            MARKET_POSITIONS.put(owner, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (owner != null) {
            MARKET_POSITIONS.remove(owner);
        }
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
                if (slot == RECEIPT_BOOK_SLOT) return stack.is(net.minecraft.world.item.Items.WRITTEN_BOOK);
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
                if (slot == RECEIPT_BOOK_SLOT || slot == BAG_SLOT) return stack;
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
            for (int j = 0; j < RECEIPT_BOOK_SLOT && excess > 0; j++) {
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
                SellBoxMod.LOGGER.error("Market at {} had an unsaveable stack of {} x{}; {} item(s) could not be stored and were dropped",
                        worldPosition, stack.getItem(), original, excess);
            }
        }
    }

    @Nullable
    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) {
        if (this.owner != null && !this.owner.equals(owner)) {
            MARKET_POSITIONS.remove(this.owner);
        }
        this.owner = owner;
        if (owner != null) {
            MARKET_POSITIONS.put(owner, worldPosition);
        }
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
        int remaining = stack.getCount();
        // 先堆到同类未满的槽位（单槽最多 99，保证能存档）
        for (int i = 0; i < RECEIPT_BOOK_SLOT && remaining > 0; i++) {
            ItemStack slotStack = items.get(i);
            if (!slotStack.isEmpty() && ItemStack.isSameItemSameComponents(slotStack, stack)
                    && slotStack.getCount() < MAX_SAVEABLE_STACK) {
                int put = Math.min(remaining, MAX_SAVEABLE_STACK - slotStack.getCount());
                slotStack.grow(put);
                remaining -= put;
            }
        }
        // 再填空槽
        for (int i = 0; i < RECEIPT_BOOK_SLOT && remaining > 0; i++) {
            if (items.get(i).isEmpty()) {
                int put = Math.min(remaining, MAX_SAVEABLE_STACK);
                items.set(i, stack.copyWithCount(put));
                remaining -= put;
            }
        }
        setChanged();
        sync();
        if (remaining > 0) {
            SellBoxMod.LOGGER.error("Market at {} is full; {} x{} could not be stored",
                    worldPosition, remaining, stack.getItem());
        }
    }

    public void writeReceipt(String playerName, String receiptText) {
        if (level == null || level.isClientSide) return;
        String time = java.time.LocalDateTime.now().toString().substring(0, 16).replace("T", " ");
        net.minecraft.network.chat.MutableComponent page = Component.literal("交易收据");
        page = page.append(Component.literal("\n时间: " + time));
        page = page.append(Component.literal("\n玩家: " + playerName));
        page = page.append(Component.literal("\n详情: " + receiptText));

        List<Filterable<Component>> pages = new ArrayList<>();
        ItemStack existing = items.get(RECEIPT_BOOK_SLOT);
        if (!existing.isEmpty() && existing.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent existingContent = existing.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (existingContent != null) {
                pages = new ArrayList<>(existingContent.pages());
            }
        }
        if (pages.size() >= 100) {
            pages.remove(0);
        }
        pages.add(Filterable.passThrough(page));

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough("市场收据"), "市场", 0, pages, true);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        items.set(RECEIPT_BOOK_SLOT, book);
        setChanged();
    }

    public void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        sanitizeStacks();
        ContainerHelper.saveAllItems(tag, items, registries);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        sanitizeStacks();
        if (tag.hasUUID("Owner")) {
            this.owner = tag.getUUID("Owner");
            MARKET_POSITIONS.put(this.owner, worldPosition);
        }
        this.ownerName = tag.getString("OwnerName");
        initHandler();
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
        items.set(slot, clampStack(stack));
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
    public int getMaxStackSize() { return MAX_SAVEABLE_STACK; }
}
