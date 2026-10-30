package com.sellbox.mod.block;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.config.SellBoxConfig;
import com.sellbox.mod.shop.SellBoxEconomy;import com.sellbox.mod.network.ModPayloads;
import com.sellbox.mod.item.CoinBagItem;
import com.sellbox.mod.registry.ModBlockEntities;
import com.sellbox.mod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SellBoxBlockEntity extends BlockEntity implements Container {
    private static final int CONTAINER_SIZE = 54;
    private static final int BAG_SLOT = 53;
    private static final int RECEIPT_BOOK_SLOT = 52;
    private static final int MAX_BOOK_PAGES = 100;
    /** ItemStack 存档 codec 只接受 count ∈ [1,99]；超过会让整个方块实体保存失败 */
    private static final int MAX_SAVEABLE_STACK = 99;

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private UUID owner = null;
    private String ownerName = "";

    private ItemStackHandler itemHandler;

    public SellBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SELL_BOX.get(), pos, state);
        initHandler();
    }

    /**
     * 当前已加载（且未被移除）的出货箱位置。
     * 不依赖任何具体区块加载器：只要区块被加载（玩家附近、/forceload、出生点、
     * FTB Chunks 等任意模组的区块加载），它里面的出货箱就会通过 setLevel 登记到这里。
     */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED_BOXES = new HashMap<>();

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && !level.isClientSide) {
            LOADED_BOXES.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            Set<BlockPos> positions = LOADED_BOXES.get(level.dimension());
            if (positions != null) {
                positions.remove(worldPosition);
                if (positions.isEmpty()) LOADED_BOXES.remove(level.dimension());
            }
        }
        super.setRemoved();
    }

    public static void forEachLoadedSellBox(ServerLevel level, Consumer<SellBoxBlockEntity> action) {
        Set<BlockPos> positions = LOADED_BOXES.get(level.dimension());
        if (positions == null || positions.isEmpty()) return;
        for (BlockPos pos : new ArrayList<>(positions)) {
            if (!level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof SellBoxBlockEntity sellBox) {
                action.accept(sellBox);
            } else {
                positions.remove(pos);
            }
        }
        if (positions.isEmpty()) LOADED_BOXES.remove(level.dimension());
    }

    public static void clearLoadedRegistry() {
        LOADED_BOXES.clear();
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
     * 存档兜底：1.21 的 ItemStack codec 只接受 count ∈ [1,99]，任何超过 99 的堆叠都会让
     * 整个方块实体保存失败（所有物品一起丢）。这里把超大堆叠拆到其它槽位；实在放不下才丢弃并记日志。
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
                SellBoxMod.LOGGER.error("Sell box at {} had an unsaveable stack of {} x{}; {} item(s) could not be stored and were dropped",
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
        int pending = (owner != null) ? SellBoxConfig.getPendingBalance(owner) : 0;
        if (pending > 0) {
            return Component.translatable("block.sellbox.sell_box_with_balance", String.valueOf(pending));
        }
        return Component.translatable("block.sellbox.sell_box");
    }

    public static record SoldEntry(Component itemName, int count, int totalValue, ResourceLocation itemId) {}
    private static final int MAX_RECEIPT_LINES = 8;

    /** 收据合并用：同一物品无论占了几个槽位、数量多少，都累加成一行 */
    private static final class SoldGroup {
        final Component itemName;
        final ResourceLocation itemId;
        long count = 0;
        long value = 0;

        SoldGroup(Component itemName, ResourceLocation itemId) {
            this.itemName = itemName;
            this.itemId = itemId;
        }
    }

    public void sellItems() {
        if (level == null || level.isClientSide) return;
        if (owner == null) return;

        Map<ResourceLocation, SoldGroup> soldGroups = new LinkedHashMap<>();
        long totalValue = 0;
        int soldCount = 0;
        List<Integer> soldSlots = new ArrayList<>();

        for (int i = 0; i < CONTAINER_SIZE; i++) {
            if (i == BAG_SLOT) continue;
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;

            int price = SellBoxConfig.getPrice(stack, level.registryAccess());
            if (price <= 0) {
                // 未定价：整箱跳过，留箱子里不动
                // （曾经在这里就 items.set(i, EMPTY) 清空，导致一箱没定价的物品被静默销毁）
                continue;
            }

            long stackValue = (long) price * stack.getCount();
            totalValue += stackValue;
            soldCount += stack.getCount();

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            SoldGroup group = soldGroups.get(itemId);
            if (group == null) {
                group = new SoldGroup(stack.getHoverName(), itemId);
                soldGroups.put(itemId, group);
            }
            group.count += stack.getCount();
            group.value += stackValue;

            soldSlots.add(i);
        }

        if (totalValue <= 0 || soldCount <= 0) return;

        // 单次出货金额超过 int 上限时不结算：宁可留着，也不静默吞掉超出的价值
        if (totalValue > Integer.MAX_VALUE) {
            SellBoxMod.LOGGER.error("Sell box payout {} exceeds int limit, sale cancelled (items kept) @ {}", totalValue, worldPosition);
            return;
        }

        // 全部槽位结算完再截断：同种物品已合并，不会因为撞到行数上限被拆成"每 64 个一行"
        List<SoldEntry> soldEntries = new ArrayList<>();
        for (SoldGroup group : soldGroups.values()) {
            if (soldEntries.size() >= MAX_RECEIPT_LINES) break;
            soldEntries.add(new SoldEntry(
                    group.itemName,
                    (int) Math.min(group.count, Integer.MAX_VALUE),
                    (int) Math.min(group.value, Integer.MAX_VALUE),
                    group.itemId
            ));
        }

        int value = (int) totalValue;

        // Fill coin bag first if present
        int bagFill = 0;
        ItemStack bagStack = items.get(BAG_SLOT);
        boolean bagPresent = !bagStack.isEmpty() && bagStack.is(ModItems.COIN_BAG.get());
        if (bagPresent) {
            int bagCoins = CoinBagItem.getCoins(bagStack);
            int canFit = CoinBagItem.MAX_CAPACITY - bagCoins;
            bagFill = Math.min(value, canFit);
        }
        int remainder = value - bagFill;

        Player player = level.getPlayerByUUID(owner);
        boolean ownerOnline = player instanceof ServerPlayer;
        if (!ownerOnline && remainder > 0) {
            // 离线收益先落盘再清物品：写盘失败就整箱原样留下，绝不吞物品
            if (!SellBoxConfig.addPendingBalance(owner, remainder)) {
                SellBoxMod.LOGGER.error("Pending balance save failed, sale cancelled (items kept) owner={} @ {}", owner, worldPosition);
                return;
            }
        }

        // 钱已确定能到账：清空已售出槽位，并把钱装进钱袋
        for (int slot : soldSlots) {
            items.set(slot, ItemStack.EMPTY);
        }
        if (bagPresent && bagFill > 0) {
            CoinBagItem.setCoins(bagStack, CoinBagItem.getCoins(bagStack) + bagFill);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (remainder > 0) {
                SellBoxEconomy.addBalance(serverPlayer, remainder);
                ModPayloads.sendBalanceToPlayer(serverPlayer);
            }
            level.playSound(null, worldPosition, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.2F);
            sendOnlineReceipt(serverPlayer, soldEntries, soldCount, value, bagFill, worldPosition);
        }
        appendReceiptToBook(soldEntries, value, bagFill, !ownerOnline);

        setChanged();
        sync();
    }

    /** 收据标题前缀图标，聊天栏收据标题固定带这个箱子图标 */
    private static final String RECEIPT_ICON = "📦 ";

    /** 同一 tick 内多个出货箱同时出货时先攒着，tick 结束时合并成一份收据，避免刷屏 */
    private static final Map<UUID, PendingReceipt> PENDING_RECEIPTS = new HashMap<>();

    private static final class PendingReceipt {
        final LinkedHashMap<ResourceLocation, SoldGroup> groups = new LinkedHashMap<>();
        final LinkedHashSet<BlockPos> bagPositions = new LinkedHashSet<>();
        long totalCount = 0;
        long totalValue = 0;

        void sendTo(ServerPlayer player) {
            List<SoldEntry> merged = new ArrayList<>();
            for (SoldGroup group : groups.values()) {
                if (merged.size() >= MAX_RECEIPT_LINES) break;
                merged.add(new SoldEntry(
                        group.itemName,
                        (int) Math.min(group.count, Integer.MAX_VALUE),
                        (int) Math.min(group.value, Integer.MAX_VALUE),
                        group.itemId));
            }
            sendReceiptToPlayer(player, merged, totalCount, totalValue, new ArrayList<>(bagPositions));
        }
    }

    /** 服务器 tick 开头调用：把上一 tick 攒下的出货收据一次性发给玩家 */
    public static void flushPendingReceipts(ServerLevel level) {
        if (PENDING_RECEIPTS.isEmpty()) return;
        Iterator<Map.Entry<UUID, PendingReceipt>> it = PENDING_RECEIPTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingReceipt> entry = it.next();
            UUID uuid = entry.getKey();
            if (!(level.getPlayerByUUID(uuid) instanceof ServerPlayer player)) {
                // 玩家在别的维度就把收据留着给那个维度发，只有真离线才丢弃
                if (level.getServer() != null && level.getServer().getPlayerList().getPlayer(uuid) != null) continue;
                it.remove();
                continue;
            }
            it.remove();
            sendPendingSafely(entry.getValue(), player);
        }
    }

    /** 登出时连接可能已经在关闭，发失败只记日志，不能把整个 tick 事件带崩 */
    private static void sendPendingSafely(PendingReceipt pending, ServerPlayer player) {
        try {
            pending.sendTo(player);
        } catch (Exception e) {
            SellBoxMod.LOGGER.warn("Failed to send sell box receipt to {}", player.getName().getString(), e);
        }
    }

    private static void sendOnlineReceipt(ServerPlayer player, List<SoldEntry> entries, int totalCount, int totalValue, int bagFill, BlockPos pos) {
        PendingReceipt pending = PENDING_RECEIPTS.computeIfAbsent(player.getUUID(), uuid -> new PendingReceipt());
        for (SoldEntry sold : entries) {
            SoldGroup group = pending.groups.get(sold.itemId());
            if (group == null) {
                group = new SoldGroup(sold.itemName(), sold.itemId());
                pending.groups.put(sold.itemId(), group);
            }
            group.count += sold.count();
            group.value += sold.totalValue();
        }
        pending.totalCount += totalCount;
        pending.totalValue += totalValue;
        if (bagFill > 0) {
            pending.bagPositions.add(pos.immutable());
        }
    }

    private static void sendReceiptToPlayer(ServerPlayer player, List<SoldEntry> entries, long totalCount, long totalValue, List<BlockPos> bagPositions) {
        Component titleMain = Component.literal("✦ ")
                .append(Component.translatable("message.sellbox.receipt_title"))
                .append(Component.literal(" ✦"))
                .withStyle(net.minecraft.ChatFormatting.GREEN);
        Component titleSub = Component.translatable("message.sellbox.receipt_sold", totalCount)
                .append(Component.literal("  "))
                .append(Component.translatable("item.sellbox.gold_coin"))
                .append(Component.literal(" x" + totalValue))
                .withStyle(net.minecraft.ChatFormatting.YELLOW);

        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 15));
        player.connection.send(new ClientboundSetSubtitleTextPacket(titleSub));
        player.connection.send(new ClientboundSetTitleTextPacket(titleMain));

        Component chat = buildReceiptChat(entries, totalValue, bagPositions, "message.sellbox.receipt_header");
        player.displayClientMessage(chat, false);
    }

    private static MutableComponent buildReceiptChat(List<SoldEntry> entries, long totalValue, List<BlockPos> bagPositions, String headerKey) {
        int greenColor = ChatFormatting.GREEN.getColor() != null ? ChatFormatting.GREEN.getColor() : 0x00AA00;
        int yellowColor = ChatFormatting.YELLOW.getColor() != null ? ChatFormatting.YELLOW.getColor() : 0xFFFF55;

        List<String> contentStrs = new ArrayList<>();
        List<MutableComponent> contentComps = new ArrayList<>();

        for (SoldEntry entry : entries) {
            String itemName = entry.itemName().getString();
            String lineStr = "※ " + itemName + " x" + entry.count() + "  ◆" + entry.totalValue();
            contentStrs.add(lineStr);

            MutableComponent comp = Component.literal("※ ").withColor(greenColor)
                    .append(entry.itemName().copy().withColor(greenColor))
                    .append(Component.literal(" x" + entry.count()).withColor(yellowColor))
                    .append(Component.literal("  ◆" + entry.totalValue()).withColor(yellowColor));
            contentComps.add(comp);
        }

        ItemStack goldCoin = new ItemStack(com.sellbox.mod.registry.ModItems.GOLD_COIN.get());
        String totalStr = Component.translatable("message.sellbox.receipt_total", totalValue).getString()
                + " " + goldCoin.getHoverName().getString();
        contentStrs.add(totalStr);
        contentComps.add(Component.translatable("message.sellbox.receipt_total", totalValue).withColor(yellowColor)
                .append(Component.literal(" ").withColor(yellowColor))
                .append(goldCoin.getHoverName().copy().withColor(yellowColor)));

        if (!bagPositions.isEmpty()) {
            StringBuilder coordBuilder = new StringBuilder();
            for (BlockPos bagPos : bagPositions) {
                if (coordBuilder.length() > 0) coordBuilder.append("、");
                coordBuilder.append(bagPos.getX()).append(" ").append(bagPos.getY()).append(" ").append(bagPos.getZ());
            }
            String coord = coordBuilder.toString();
            String bagStr = Component.translatable("message.sellbox.receipt_bag_deposit", coord).getString();
            contentStrs.add(bagStr);
            contentComps.add(Component.translatable("message.sellbox.receipt_bag_deposit", coord).withColor(greenColor));
        }

        int maxWidth = 0;
        for (String s : contentStrs) {
            maxWidth = Math.max(maxWidth, visualWidth(s));
        }

// 「收据 / Receipt」必须保持可翻译：之前在服务端 getString() 之后拼进 literal，
        // 结果不管客户端什么语言都固定显示服务端（英文）的 Receipt
        // 纸箱图标保持原色：文字颜色会乘到字形贴图上，跟着绿色走会把图标一起染绿
        Component headerTitle = Component.literal(RECEIPT_ICON)
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFFF)))
                .append(Component.translatable(headerKey).withColor(greenColor));
        LineData headerLine = centeredLine(RECEIPT_ICON + Component.translatable(headerKey).getString(), maxWidth);
        LineData footerLine = centeredLine("", maxWidth);

        MutableComponent result = Component.literal(headerLine.left).withColor(greenColor)
                .append(headerTitle)
                .append(Component.literal(headerLine.right).withColor(greenColor));
        for (MutableComponent line : contentComps) {
            result = result.append(Component.literal("\n")).append(line);
        }
        result = result.append(Component.literal("\n"))
                .append(Component.literal(footerLine.left + footerLine.text + footerLine.right).withColor(greenColor));

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

    private void appendReceiptToBook(List<SoldEntry> entries, int totalValue, int bagFill, boolean ownerOffline) {
        if (level == null || level.isClientSide) return;

        MutableComponent page = Component.literal("收据" + toChineseNumeral(1));
        int startNum = 1;
        ItemStack existing = items.get(RECEIPT_BOOK_SLOT);
        if (!existing.isEmpty() && existing.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent existingContent = existing.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (existingContent != null) {
                startNum = existingContent.pages().size() + 1;
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
                .append(new ItemStack(com.sellbox.mod.registry.ModItems.GOLD_COIN.get()).getHoverName());

        // 主人不在线时钱先记在离线收益里，上线后才入余额，收据上要说清楚
        String payoutLine;
        if (bagFill > 0) {
            payoutLine = ownerOffline ? "货币已存入钱袋，其余将于上线后存入余额" : "货币已存入钱袋";
        } else {
            payoutLine = ownerOffline ? "货币将于上线后存入余额" : "货币已存入余额";
        }
        page = page.append(Component.literal("\n"))
                .append(Component.literal(payoutLine));

        List<Filterable<Component>> pages = new ArrayList<>();
        if (!existing.isEmpty() && existing.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent existingContent = existing.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (existingContent != null) {
                pages = new ArrayList<>(existingContent.pages());
            }
        }
        pages.add(Filterable.passThrough(page));

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough("收据"),
                "出货箱",
                0,
                pages,
                true
        );
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        items.set(RECEIPT_BOOK_SLOT, book);
        setChanged();
    }

    public boolean tryCollectWithCoinBag(ItemStack bagStack) {
        if (owner == null || bagStack.isEmpty() || level == null) return false;
        if (level.isClientSide) return false;

        int pending = SellBoxConfig.getPendingBalance(owner);
        if (pending <= 0) return false;

        int bagCoins = CoinBagItem.getCoins(bagStack);
        int canFit = CoinBagItem.MAX_CAPACITY - bagCoins;
        if (canFit <= 0) return false;

        // 先扣减（会立即落盘）再塞钱袋，崩溃窗口内不会白送
        int toAdd = SellBoxConfig.deductPendingBalance(owner, canFit);
        if (toAdd <= 0) return false;

        CoinBagItem.setCoins(bagStack, bagCoins + toAdd);
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
        // Balance is read from global map on each getDisplayName() call.
        // Individual sell boxes sync themselves when interacted with.
        // No server-wide iteration needed — titles are consistent at read time.
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
        ItemStack clamped = clampStack(stack);
        items.set(slot, clamped);
        setChanged();
        if (slot == BAG_SLOT && !clamped.isEmpty() && clamped.is(ModItems.COIN_BAG.get())) {
            tryCollectWithCoinBag(clamped);
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
        return MAX_SAVEABLE_STACK;
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }
}
