package com.sellbox.mod.block;

import com.sellbox.mod.config.SellBoxConfig;
import com.sellbox.mod.item.CoinBagItem;
import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.registry.ModBlockEntities;
import com.sellbox.mod.registry.ModItems;
import com.sellbox.mod.shop.SellBoxEconomy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SellBoxBlockEntity extends BlockEntity implements Container {
    public static final Map<UUID, BlockPos> SELLBOX_POSITIONS = new HashMap<>();
    private static final int CONTAINER_SIZE = 54;
    private static final int BAG_SLOT = 53;
    private static final int RECEIPT_BOOK_SLOT = 52;
    private static final int MAX_BOOK_PAGES = 100;

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private UUID owner = null;
    private String ownerName = "";

    private ItemStackHandler itemHandler;

    public SellBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SELL_BOX.get(), pos, state);
        initHandler();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (owner != null) SELLBOX_POSITIONS.put(owner, worldPosition);
    }

    @Override
    public void setRemoved() {
        if (owner != null && SELLBOX_POSITIONS.get(owner) == worldPosition) SELLBOX_POSITIONS.remove(owner);
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

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        if (owner != null) SELLBOX_POSITIONS.put(owner, worldPosition);
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Component getDisplayName() {
        int pending = (owner != null) ? SellBoxConfig.getPendingBalance(owner) : 0;
        if (pending > 0) {
            return Component.translatable("block.sellbox.sell_box_with_balance", String.valueOf(pending));
        }
        return Component.translatable("block.sellbox.sell_box");
    }

    public static record SoldEntry(Component itemName, int count, int totalValue, ResourceLocation itemId) {}
    private static final int MAX_RECEIPT_LINES = 8;

    public void sellItems() {
        if (level == null || level.isClientSide) return;
        if (owner == null) return;

        List<SoldEntry> soldEntries = new ArrayList<>();
        long totalValue = 0;
        int soldCount = 0;

        for (int i = 0; i < CONTAINER_SIZE; i++) {
            if (i == BAG_SLOT) continue;
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;

            int price = SellBoxConfig.getPrice(stack);
            if (price <= 0) continue;

            long stackValue = (long) price * stack.getCount();
            totalValue += stackValue;
            soldCount += stack.getCount();

            if (soldEntries.size() < MAX_RECEIPT_LINES) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                soldEntries.add(new SoldEntry(
                        stack.getHoverName(),
                        stack.getCount(),
                        (int) Math.min(stackValue, Integer.MAX_VALUE),
                        itemId
                ));
            }

            items.set(i, ItemStack.EMPTY);
        }

        if (totalValue <= 0 || soldCount <= 0) return;

        int value = (int) Math.min(totalValue, Integer.MAX_VALUE);

        int bagFill = 0;
        ItemStack bagStack = items.get(BAG_SLOT);
        if (!bagStack.isEmpty() && bagStack.is(ModItems.COIN_BAG.get())) {
            int bagCoins = CoinBagItem.getCoins(bagStack);
            int canFit = CoinBagItem.MAX_CAPACITY - bagCoins;
            bagFill = Math.min(value, canFit);
            if (bagFill > 0) {
                CoinBagItem.setCoins(bagStack, bagCoins + bagFill);
            }
        }
        int remainder = value - bagFill;

        Player player = level.getPlayerByUUID(owner);
        if (player instanceof ServerPlayer serverPlayer) {
            if (remainder > 0) {
                SellBoxEconomy.addBalance(serverPlayer, remainder);
                ModNetwork.sendBalanceToPlayer(serverPlayer);
            }
            level.playSound(null, worldPosition, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.2F);
            sendOnlineReceipt(serverPlayer, soldEntries, soldCount, value, bagFill);
        } else {
            if (remainder > 0) {
                SellBoxConfig.addPendingBalance(owner, remainder);
            }
        }
        appendReceiptToBook(soldEntries, value, bagFill);

        setChanged();
        sync();
    }

    private void sendOnlineReceipt(ServerPlayer player, List<SoldEntry> entries, int totalCount, int totalValue, int bagFill) {
        Component titleMain = Component.literal("✦ ")
                .append(Component.literal("昨日收益"))
                .append(Component.literal(" ✦"))
                .withStyle(ChatFormatting.GREEN);
        Component titleSub = Component.translatable("message.sellbox.receipt_sold", totalCount)
                .append(Component.literal("  "))
                .append(Component.translatable("item.sellbox.gold_coin"))
                .append(Component.literal(" x" + totalValue))
                .withStyle(ChatFormatting.YELLOW);

        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 15));
        player.connection.send(new ClientboundSetSubtitleTextPacket(titleSub));
        player.connection.send(new ClientboundSetTitleTextPacket(titleMain));

        Component chat = buildReceiptChat(entries, totalValue, bagFill, worldPosition, "message.sellbox.receipt_header");
        player.displayClientMessage(chat, false);
    }

    private MutableComponent buildReceiptChat(List<SoldEntry> entries, int totalValue, int bagFill, BlockPos pos, String headerKey) {

        List<String> contentStrs = new ArrayList<>();
        List<MutableComponent> contentComps = new ArrayList<>();

        for (SoldEntry entry : entries) {
            String itemName = entry.itemName().getString();
            String lineStr = "※ " + itemName + " x" + entry.count() + "  ◆" + entry.totalValue();
            contentStrs.add(lineStr);

            MutableComponent comp = Component.literal("※ ").withStyle(ChatFormatting.GREEN)
                    .append(entry.itemName().copy().withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" x" + entry.count()).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("  ◆" + entry.totalValue()).withStyle(ChatFormatting.YELLOW));
            contentComps.add(comp);
        }

        ItemStack goldCoin = new ItemStack(ModItems.GOLD_COIN.get());
        String totalStr = Component.translatable("message.sellbox.receipt_total", totalValue).getString()
                + " " + goldCoin.getHoverName().getString();
        contentStrs.add(totalStr);
        contentComps.add(Component.translatable("message.sellbox.receipt_total", totalValue).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" ").withStyle(ChatFormatting.YELLOW))
                .append(goldCoin.getHoverName().copy().withStyle(ChatFormatting.YELLOW)));

        if (bagFill > 0) {
            String coord = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            String bagStr = Component.translatable("message.sellbox.receipt_bag_deposit", coord).getString();
            contentStrs.add(bagStr);
            contentComps.add(Component.translatable("message.sellbox.receipt_bag_deposit", coord).withStyle(ChatFormatting.GREEN));
        }

        int maxWidth = 0;
        for (String s : contentStrs) {
            maxWidth = Math.max(maxWidth, visualWidth(s));
        }

        String title = Component.translatable(headerKey).getString();
        LineData headerLine = centeredLine(title, maxWidth);
        LineData footerLine = centeredLine("", maxWidth);

        MutableComponent result = Component.literal(headerLine.left + headerLine.text + headerLine.right).withStyle(ChatFormatting.GREEN);
        for (MutableComponent line : contentComps) {
            result = result.append(Component.literal("\n")).append(line);
        }
        result = result.append(Component.literal("\n"))
                .append(Component.literal(footerLine.left + footerLine.text + footerLine.right).withStyle(ChatFormatting.GREEN));

        return result;
    }

    private record LineData(String left, String text, String right) {}

    private static int visualWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += (c >= '\u4e00' && c <= '\u9fff') || (c >= '\u3000' && c <= '\u303f') || (c >= '\uff00' && c <= '\uffef') || c == '\u2014' ? 2 : 1;
        }
        return w;
    }

    private static String repeatDash(int count) {
        return "—".repeat(count);
    }

    private static LineData centeredLine(String text, int targetWidth) {
        int textW = visualWidth(text);
        int needed = targetWidth - textW;
        if (needed <= 0) {
            return new LineData("", text, "");
        }
        int dashW = visualWidth("—");
        int totalDashes = needed / dashW;
        if (totalDashes < 1) totalDashes = 1;
        int left = totalDashes / 2;
        int right = totalDashes - left;
        return new LineData(repeatDash(left), text, repeatDash(right));
    }

    private static String toChineseNumeral(int n) {
        if (n < 1 || n > 99) return String.valueOf(n);
        String[] digits = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (n <= 10) return n == 10 ? "十" : digits[n];
        int tens = n / 10;
        int ones = n % 10;
        if (tens == 1) return "十" + digits[ones];
        return digits[tens] + "十" + (ones == 0 ? "" : digits[ones]);
    }

    private void appendReceiptToBook(List<SoldEntry> entries, int totalValue, int bagFill) {
        if (level == null || level.isClientSide) return;

        MutableComponent page = Component.literal("收据" + toChineseNumeral(1));
        int startNum = 1;
        ItemStack existing = items.get(RECEIPT_BOOK_SLOT);
        if (!existing.isEmpty() && existing.is(Items.WRITTEN_BOOK)) {
            CompoundTag existingTag = existing.getTag();
            if (existingTag != null && existingTag.contains("pages")) {
                ListTag existingPages = existingTag.getList("pages", 8);
                startNum = existingPages.size() + 1;
                if (startNum > MAX_BOOK_PAGES) return;
                page = Component.literal("收据" + toChineseNumeral(startNum));
            }
        }

        String time = java.time.LocalDateTime.now().toString().substring(0, 16).replace("T", " ");
        page = page.append(Component.literal("\n时间: " + time));

        for (SoldEntry entry : entries) {
            page = page.append(Component.literal("\n"))
                    .append(entry.itemName())
                    .append(Component.literal(" x" + entry.count() + "  "))
                    .append(Component.literal(String.valueOf(entry.totalValue())));
        }

        page = page.append(Component.literal("\n"))
                .append(Component.literal("总收益 " + totalValue + " "))
                .append(new ItemStack(ModItems.GOLD_COIN.get()).getHoverName());

        page = page.append(Component.literal("\n"))
                .append(Component.literal(bagFill > 0 ? "货币已存入钱袋" : "货币已存入余额"));

        ListTag pagesTag = new ListTag();
        if (!existing.isEmpty() && existing.is(Items.WRITTEN_BOOK)) {
            CompoundTag existingTag = existing.getTag();
            if (existingTag != null && existingTag.contains("pages")) {
                pagesTag = existingTag.getList("pages", 8);
            }
        }
        pagesTag.add(StringTag.valueOf(Component.Serializer.toJson(page)));

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag bookTag = book.getOrCreateTag();
        bookTag.putString("title", "收据");
        bookTag.putString("author", "出货箱");
        bookTag.putByte("resolved", (byte) 1);
        bookTag.put("pages", pagesTag);
        items.set(RECEIPT_BOOK_SLOT, book);
        setChanged();
    }

    public void collectOfflineBalance(ServerPlayer player) {
        if (owner == null || !owner.equals(player.getUUID())) return;
        int pending = SellBoxConfig.getPendingBalance(owner);
        if (pending <= 0) return;
        SellBoxEconomy.addBalance(player, pending);
        SellBoxConfig.deductPendingBalance(owner, pending);
        ModNetwork.sendBalanceToPlayer(player);
        syncAllForPlayer(owner);
        player.displayClientMessage(
                Component.translatable("message.sellbox.offline_balance_collected", pending),
                false
        );
    }

    public boolean tryCollectWithCoinBag(ItemStack bagStack) {
        if (owner == null || bagStack.isEmpty() || level == null) return false;
        if (level.isClientSide) return false;

        int pending = SellBoxConfig.getPendingBalance(owner);
        if (pending <= 0) return false;

        int bagCoins = CoinBagItem.getCoins(bagStack);
        int canFit = CoinBagItem.MAX_CAPACITY - bagCoins;

        int toAdd = Math.min(pending, canFit);
        if (toAdd <= 0) return false;

        CoinBagItem.setCoins(bagStack, bagCoins + toAdd);
        SellBoxConfig.deductPendingBalance(owner, toAdd);
        syncAllForPlayer(owner);

        Player player = level.getPlayerByUUID(owner);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.sellbox.bag_collected", toAdd),
                    false
            );
        }
        return true;
    }

    public void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static void syncAllForPlayer(UUID playerUuid) {
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        if (tag.hasUUID("Owner")) {
            this.owner = tag.getUUID("Owner");
            SELLBOX_POSITIONS.put(owner, worldPosition);
        }
        this.ownerName = tag.getString("OwnerName");
        initHandler();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
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
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
        if (slot == BAG_SLOT && !stack.isEmpty() && stack.is(ModItems.COIN_BAG.get())) {
            tryCollectWithCoinBag(stack);
        }
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
        return 1000000;
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }
}
