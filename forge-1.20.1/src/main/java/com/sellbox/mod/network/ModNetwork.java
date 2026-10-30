package com.sellbox.mod.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.config.SellBoxConfig;
import com.sellbox.mod.playershop.MarketBlockEntity;
import com.sellbox.mod.playershop.PlayerShopConfig;
import com.sellbox.mod.playershop.PlayerShopEditHandler;
import com.sellbox.mod.playershop.PlayerShopOpenHandler;
import com.sellbox.mod.playershop.StockBlockEntity;
import com.sellbox.mod.shop.SellBoxEconomy;
import com.sellbox.mod.shop.TradeLimitTracker;
import com.sellbox.mod.shop.ShopConfig;
import com.sellbox.mod.shop.ShopEditHandler;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.*;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SellBoxMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static Map<String, Integer> clientPrices = new HashMap<>();
    public static int clientBalance = -1;
    public static boolean clientConfigSynced = false;
    public static boolean clientSdmEnabled = false;
    public static boolean clientShopOverride = false;
    public static boolean clientEditMode = false;
    public static Set<String> clientBlacklist = new HashSet<>();
    public static boolean clientAdminEditEnabled = false;
    public static final Map<Long, String> questNameCache = new HashMap<>();

    // ──────────────────── Packet Records ────────────────────

    public record SyncBalancePacket(int balance) {
        static void encode(SyncBalancePacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.balance);
        }

        static SyncBalancePacket decode(FriendlyByteBuf buf) {
            return new SyncBalancePacket(buf.readVarInt());
        }

        static void handle(SyncBalancePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> clientBalance = msg.balance);
            ctx.get().setPacketHandled(true);
        }
    }

    public record SyncAllPricesPacket(Map<String, Integer> prices) {
        static void encode(SyncAllPricesPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.prices.size());
            for (var entry : msg.prices.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeVarInt(entry.getValue());
            }
        }

        static SyncAllPricesPacket decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            Map<String, Integer> map = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                map.put(buf.readUtf(), buf.readVarInt());
            }
            return new SyncAllPricesPacket(map);
        }

        static void handle(SyncAllPricesPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                clientConfigSynced = true;
                clientPrices.clear();
                clientPrices.putAll(msg.prices);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SetPricesPacket(Map<String, Integer> prices) {
        static void encode(SetPricesPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.prices.size());
            for (var entry : msg.prices.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeVarInt(entry.getValue());
            }
        }

        static SetPricesPacket decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            Map<String, Integer> map = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                map.put(buf.readUtf(), buf.readVarInt());
            }
            return new SetPricesPacket(map);
        }

        static void handle(SetPricesPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                if (sp == null || !sp.hasPermissions(2) || !sp.isCreative()) return;

                for (var entry : msg.prices().entrySet()) {
                    String key = entry.getKey();
                    int pipeIdx = key.indexOf('|');
                    if (pipeIdx >= 0) {
                        // NBT 精确级价格：itemId|SNBT（如 handsetnbt / 定价GUI NBT 分类）
                        ResourceLocation nid = ResourceLocation.tryParse(key.substring(0, pipeIdx));
                        if (nid != null && BuiltInRegistries.ITEM.containsKey(nid)) {
                            String snbt = key.substring(pipeIdx + 1);
                            try {
                                net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(snbt);
                                ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(nid));
                                st.setTag(tag);
                                SellBoxConfig.setPriceNbt(st, entry.getValue());
                            } catch (Exception ignored) {
                            }
                        }
                    } else {
                        ResourceLocation id = new ResourceLocation(key);
                        if (BuiltInRegistries.ITEM.containsKey(id)) {
                            Item item = BuiltInRegistries.ITEM.get(id);
                            SellBoxConfig.setPrice(item, entry.getValue());
                        }
                    }
                }

                MinecraftServer server = sp.getServer();
                if (server != null) {
                    broadcastAllPrices(server);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record OpenPricingPacket() {
        static void encode(OpenPricingPacket msg, FriendlyByteBuf buf) {
        }

        static OpenPricingPacket decode(FriendlyByteBuf buf) {
            return new OpenPricingPacket();
        }

    }

    public record ShopOpenPacket(String category) {
        static void encode(ShopOpenPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.category);
        }

        static ShopOpenPacket decode(FriendlyByteBuf buf) {
            return new ShopOpenPacket(buf.readUtf());
        }

        static void handle(ShopOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                if (sp == null) return;
                List<String> cats = ShopConfig.getCategoryNames();
                String cat = msg.category();
                if (cat.isEmpty() && !cats.isEmpty()) {
                    cat = cats.get(0);
                }
                var entries = ShopConfig.getCategory(cat);
                if (entries != null && !entries.isEmpty()) {
                    // Auto-fill missing quest names
                    for (int i = 0; i < entries.size(); i++) {
                        ShopEntry e = entries.get(i);
                        if (e.requiredQuestId() != null && e.requiredQuestName() == null) {
                            String name = com.sellbox.mod.integration.FTBQuestsIntegration.getQuestNameById(e.requiredQuestId());
                            if (name != null && !name.isEmpty()) {
                                e = new ShopEntry(e.itemId(), e.tradeType(), e.price(),
                                        e.entryType(), e.extraData(), e.requireOp(), e.quantity(),
                                        e.customName(), e.barterItemId(), e.barterItemQty(),
                                        e.requiredQuestId(), name, e.nbt(), e.strictMatch(), e.barterNbt(), e.barterStrictMatch());
                                entries.set(i, e);
                            }
                        }
                    }
                    boolean showAll = ShopConfig.isEditMode() && sp.hasPermissions(2);
                    if (!showAll) {
                        final List<ShopEntry> finalEntries = entries;
                        entries = entries.stream()
                                .filter(e -> e.requiredQuestId() == null
                                        || com.sellbox.mod.integration.FTBQuestsIntegration.isQuestCompleted(sp, e.requiredQuestId()))
                                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                    }
                }
                int balance = SellBoxEconomy.getBalance(sp);
                sendToPlayer(sp, new ShopDataPacket(
                        cat, entries != null ? entries : List.of(), balance, cats,
                        TradeLimitTracker.snapshotUsed(entries, cat, sp.getUUID())));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record TradePacket(
            String category,
            ResourceLocation itemId,
            ShopEntry.TradeType tradeType,
            int quantity,
            int totalPrice
    ) {
        static void encode(TradePacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.category);
            buf.writeResourceLocation(msg.itemId);
            buf.writeUtf(msg.tradeType().name());
            buf.writeVarInt(msg.quantity);
            buf.writeVarInt(msg.totalPrice);
        }

        static TradePacket decode(FriendlyByteBuf buf) {
            String category = buf.readUtf();
            ResourceLocation itemId = buf.readResourceLocation();
            ShopEntry.TradeType tradeType = ShopEntry.TradeType.valueOf(buf.readUtf());
            int quantity = buf.readVarInt();
            int totalPrice = buf.readVarInt();
            return new TradePacket(category, itemId, tradeType, quantity, totalPrice);
        }

        static void handle(TradePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer serverPlayer = ctx.get().getSender();
                if (serverPlayer == null) return;
                ShopEntry entry = null;
                List<ShopEntry> entries = ShopConfig.getCategory(msg.category());
                if (entries != null && msg.quantity() > 0) {
                    int price = msg.totalPrice() / msg.quantity();
                    for (ShopEntry e : entries) {
                        if (e.itemId().equals(msg.itemId())
                                && e.tradeType() == msg.tradeType()
                                && e.price() == price) {
                            entry = e;
                            break;
                        }
                    }
                }
                if (entry == null) return;
                executeTrade(serverPlayer, msg.category(), entry, msg.quantity());
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 限量条目成交后重新下发商店数据，让客户端把额度转红/禁用点击 */
    static void resendShopData(ServerPlayer sp, String category) {
        var entries = ShopConfig.getCategory(category);
        if (entries == null) return;
        var cats = ShopConfig.getCategoryNames();
        for (ServerPlayer p : sp.getServer().getPlayerList().getPlayers()) {
            sendToPlayer(p, new ShopDataPacket(category, entries, SellBoxEconomy.getBalance(p), cats,
                    TradeLimitTracker.snapshotUsed(entries, category, p.getUUID())));
        }
    }

    /** 记一笔成交并按需刷新客户端额度显示 */
    static void settle(ServerPlayer sp, String category, ShopEntry entry, int times) {
        if (!entry.hasTradeLimit() || times <= 0) return;
        TradeLimitTracker.record(entry, category, sp, times);
        resendShopData(sp, category);
    }

    public static void executeTrade(ServerPlayer player, String category, ShopEntry entry, int quantity) {
        int totalPrice = entry.price() * quantity;

        // 交易限量：先于一切扣款/发货校验，请求次数超过剩余额度直接拒绝
        if (entry.hasTradeLimit()) {
            int remaining = TradeLimitTracker.remaining(entry, category, player.getUUID());
            if (quantity > remaining) {
                sendCloseScreen(player, remaining <= 0
                        ? "message.sellbox.trade_limit_reached"
                        : "message.sellbox.trade_limit_remaining");
                return;
            }
        }

        if ("command".equals(entry.entryType())) {
            if (!SellBoxEconomy.tryDeductBalance(player, totalPrice)) {
                sendCloseScreen(player, "message.sellbox.not_enough_money");
                return;
            }
            sendBalanceToPlayer(player);
            if (entry.requireOp()) {
                player.getServer().getCommands().performPrefixedCommand(
                        player.getServer().createCommandSourceStack().withPermission(4),
                        entry.extraData()
                );
            } else {
                player.getServer().getCommands().performPrefixedCommand(
                        player.createCommandSourceStack(),
                        entry.extraData()
                );
            }
            settle(player, category, entry, quantity);
            sendActionMessage(player, "message.sellbox.trade_success_command");
            return;
        }

        if ("xp".equals(entry.entryType())) {
            if (!SellBoxEconomy.tryDeductBalance(player, totalPrice)) {
                sendCloseScreen(player, "message.sellbox.not_enough_money");
                return;
            }
            sendBalanceToPlayer(player);
            int xpPerBottle;
            try { xpPerBottle = Integer.parseInt(entry.extraData()); } catch (NumberFormatException e) { return; }
            player.giveExperiencePoints(xpPerBottle * quantity);
            settle(player, category, entry, quantity);
            sendActionMessage(player, "message.sellbox.trade_success_buy");
            return;
        }

        if ("barter".equals(entry.entryType()) && entry.barterItemId() != null) {
            int offerQtyPerUnit = Math.max(1, entry.barterItemQty());
            int goodsQtyPerUnit = Math.max(1, entry.quantity());
            int needOffer = offerQtyPerUnit * quantity;
            ItemStack offerTemplate = entry.createBarterOfferStack();
            int available = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (entry.matchesOffer(stack)) available += stack.getCount();
            }
            int actualOffer = Math.min(needOffer, available);
            if (actualOffer <= 0) {
                sendCloseScreen(player, "message.sellbox.not_enough_items");
                return;
            }
            int actualUnits = actualOffer / offerQtyPerUnit;
            int actualGiveOffer = actualUnits * offerQtyPerUnit;
            int toRemove = actualGiveOffer;
            for (ItemStack stack : player.getInventory().items) {
                if (toRemove <= 0) break;
                if (entry.matchesOffer(stack)) {
                    int r = Math.min(toRemove, stack.getCount());
                    stack.shrink(r);
                    toRemove -= r;
                }
            }
            ItemStack goodsStack = entry.createStack();
            if (!entry.customName().isEmpty()) {
                goodsStack.getOrCreateTag().putString("CustomName", entry.customName());
            }
            int maxGoodsStack = goodsStack.getMaxStackSize();
            int toGiveGoods = actualUnits * goodsQtyPerUnit;
            while (toGiveGoods > 0) {
                int chunk = Math.min(toGiveGoods, maxGoodsStack);
                player.getInventory().placeItemBackInInventory(goodsStack.copyWithCount(chunk));
                toGiveGoods -= chunk;
            }
            int excessOffer = actualOffer - actualGiveOffer;
            if (excessOffer > 0) {
                player.getInventory().placeItemBackInInventory(offerTemplate.copyWithCount(excessOffer));
            }
            settle(player, category, entry, actualUnits);
            sendActionMessage(player, "message.sellbox.trade_success_barter");
            return;
        }

        if (entry.tradeType() == ShopEntry.TradeType.BUY) {
            if (!SellBoxEconomy.tryDeductBalance(player, totalPrice)) {
                sendCloseScreen(player, "message.sellbox.not_enough_money");
                return;
            }
            sendBalanceToPlayer(player);
            ItemStack template = entry.createStack();
            int totalItems = quantity * entry.quantity();
            if (!entry.customName().isEmpty()) {
                template.getOrCreateTag().putString("CustomName", entry.customName());
            }
            int maxStack = template.getMaxStackSize();
            while (totalItems > 0) {
                int chunk = Math.min(totalItems, maxStack);
                player.getInventory().placeItemBackInInventory(template.copyWithCount(chunk));
                totalItems -= chunk;
            }
            settle(player, category, entry, quantity);
            sendActionMessage(player, "message.sellbox.trade_success_buy");
        } else if (entry.tradeType() == ShopEntry.TradeType.SELL) {
            int qtyPerUnit = Math.max(1, entry.quantity());
            int needed = quantity * qtyPerUnit;
            int available = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (entry.matches(stack)) {
                    available += stack.getCount();
                }
            }
            int toSell = Math.min(needed, available);
            int actualQty = toSell / qtyPerUnit;
            if (actualQty <= 0) {
                sendCloseScreen(player, "message.sellbox.not_enough_items");
                return;
            }
            toSell = actualQty * qtyPerUnit;
            int toRemoveSell = toSell;
            for (ItemStack stack : player.getInventory().items) {
                if (toRemoveSell <= 0) break;
                if (entry.matches(stack)) {
                    int r = Math.min(toRemoveSell, stack.getCount());
                    stack.shrink(r);
                    toRemoveSell -= r;
                }
            }
            int earned = entry.price() * actualQty;
            if (earned > 0) {
                SellBoxEconomy.addBalance(player, earned);
                sendBalanceToPlayer(player);
            }
            settle(player, category, entry, actualQty);
            sendActionMessage(player, "message.sellbox.trade_success_sell");
        }
    }

    public record ShopEditPacket(
            String action,
            String category,
            String extraData,
            String tradeType,
            int price,
            int index,
            boolean requireOp,
            String icon,
            int quantity,
            String customName,
            String barterItemId,
            int barterItemQty,
            @Nullable String nbt,
            boolean strictMatch,
            @Nullable String barterNbt,
            boolean barterStrictMatch
    ) {
        public ShopEditPacket(String action, String category, String extraData, String tradeType, int price, int index,
                               boolean requireOp, String icon, int quantity, String customName) {
            this(action, category, extraData, tradeType, price, index, requireOp, icon, quantity, customName, null, 1, null, false, null, false);
        }
        public ShopEditPacket(String action, String category, String extraData, String tradeType, int price, int index,
                               boolean requireOp, String icon, int quantity, String customName,
                               String barterItemId, int barterItemQty) {
            this(action, category, extraData, tradeType, price, index, requireOp, icon, quantity, customName, barterItemId, barterItemQty, null, false, null, false);
        }
        public ShopEditPacket(String action, String category, String extraData, String tradeType, int price, int index,
                               boolean requireOp, String icon, int quantity, String customName,
                               String barterItemId, int barterItemQty,
                               @Nullable String nbt, boolean strictMatch) {
            this(action, category, extraData, tradeType, price, index, requireOp, icon, quantity, customName, barterItemId, barterItemQty, nbt, strictMatch, null, false);
        }
        static void encode(ShopEditPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.action);
            buf.writeUtf(msg.category);
            buf.writeUtf(msg.extraData);
            buf.writeUtf(msg.tradeType);
            buf.writeVarInt(msg.price);
            buf.writeVarInt(msg.index);
            buf.writeBoolean(msg.requireOp);
            buf.writeUtf(msg.icon);
            buf.writeVarInt(msg.quantity);
            buf.writeUtf(msg.customName);
            buf.writeUtf(msg.barterItemId != null ? msg.barterItemId : "");
            buf.writeVarInt(msg.barterItemQty);
            buf.writeUtf(msg.nbt != null ? msg.nbt : "");
            buf.writeBoolean(msg.strictMatch);
            buf.writeUtf(msg.barterNbt != null ? msg.barterNbt : "");
            buf.writeBoolean(msg.barterStrictMatch);
        }

        static ShopEditPacket decode(FriendlyByteBuf buf) {
            String action = buf.readUtf();
            String category = buf.readUtf();
            String extraData = buf.readUtf();
            String tradeType = buf.readUtf();
            int price = buf.readVarInt();
            int index = buf.readVarInt();
            boolean requireOp = buf.readBoolean();
            String icon = buf.readUtf();
            int quantity = buf.readVarInt();
            String customName = buf.readUtf();
            String barterItemId = buf.readUtf();
            int barterItemQty = buf.readVarInt();
            String nbt = buf.readUtf();
            boolean strictMatch = buf.readBoolean();
            String barterNbt = buf.readUtf();
            boolean barterStrictMatch = buf.readBoolean();
            return new ShopEditPacket(action, category, extraData, tradeType, price, index,
                    requireOp, icon, quantity, customName,
                    barterItemId.isEmpty() ? null : barterItemId, barterItemQty,
                    nbt.isEmpty() ? null : nbt, strictMatch,
                    barterNbt.isEmpty() ? null : barterNbt, barterStrictMatch);
        }

        static void handle(ShopEditPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                if (sp == null || !sp.hasPermissions(2)) return;
                ShopEditHandler.handle(sp, msg);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record ShopDataPacket(
            String category,
            List<ShopEntry> entries,
            int balance,
            List<String> categories,
            /** 条目 key -> 已交易次数，仅限量条目才有；单人限量按接收者分别计算 */
            java.util.Map<String, Integer> usedCounts
    ) {
        static void encode(ShopDataPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.category);
            buf.writeVarInt(msg.entries.size());
            for (ShopEntry e : msg.entries) {
                buf.writeUtf(e.itemId().toString());
                buf.writeUtf(e.tradeType().name());
                buf.writeVarInt(e.price());
                buf.writeUtf(e.entryType());
                buf.writeUtf(e.extraData());
                buf.writeBoolean(e.requireOp());
                buf.writeVarInt(e.quantity());
                buf.writeUtf(e.customName());
                if (e.barterItemId() != null) {
                    buf.writeBoolean(true);
                    buf.writeUtf(e.barterItemId().toString());
                } else {
                    buf.writeBoolean(false);
                }
                buf.writeVarInt(e.barterItemQty());
                if (e.requiredQuestId() != null) {
                    buf.writeBoolean(true);
                    buf.writeLong(e.requiredQuestId());
                    buf.writeUtf(e.requiredQuestName() != null ? e.requiredQuestName() : "");
                } else {
                    buf.writeBoolean(false);
                }
                buf.writeUtf(e.nbt() != null ? e.nbt().toString() : "");
                buf.writeBoolean(e.strictMatch());
                buf.writeUtf(e.barterNbt() != null ? e.barterNbt().toString() : "");
                buf.writeBoolean(e.barterStrictMatch());
                buf.writeVarInt(e.tradeLimit());
                buf.writeBoolean(e.limitPerPlayer());
            }
            buf.writeVarInt(msg.balance);
            buf.writeVarInt(msg.categories.size());
            for (String cat : msg.categories) {
                buf.writeUtf(cat);
            }
            java.util.Map<String, Integer> used = msg.usedCounts();
            buf.writeVarInt(used.size());
            for (java.util.Map.Entry<String, Integer> ue : used.entrySet()) {
                buf.writeUtf(ue.getKey());
                buf.writeVarInt(ue.getValue());
            }
        }

        static ShopDataPacket decode(FriendlyByteBuf buf) {
            String cat = buf.readUtf();
            int count = buf.readVarInt();
            List<ShopEntry> es = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ResourceLocation itemId = new ResourceLocation(buf.readUtf());
                ShopEntry.TradeType tt = ShopEntry.TradeType.valueOf(buf.readUtf());
                int pr = buf.readVarInt();
                String et = buf.readUtf();
                String ed = buf.readUtf();
                boolean ro = buf.readBoolean();
                int qty = buf.readVarInt();
                String cn = buf.readUtf();
                boolean hasB = buf.readBoolean();
                ResourceLocation bid = hasB ? new ResourceLocation(buf.readUtf()) : null;
                int bq = buf.readVarInt();
                Long questId = buf.readBoolean() ? buf.readLong() : null;
                String questName = questId != null ? buf.readUtf() : null;
                if (questName != null && questName.isEmpty()) questName = null;
                String nbtStr = buf.readUtf();
                boolean strictMatch = buf.readBoolean();
                CompoundTag nbt = null;
                if (!nbtStr.isEmpty()) {
                    try { nbt = TagParser.parseTag(nbtStr); } catch (Exception ignored) { }
                }
                String barterNbtStr = buf.readUtf();
                boolean barterStrictMatch = buf.readBoolean();
                CompoundTag barterNbt = null;
                if (!barterNbtStr.isEmpty()) {
                    try { barterNbt = TagParser.parseTag(barterNbtStr); } catch (Exception ignored) { }
                }
                int tradeLimit = buf.readVarInt();
                boolean limitPerPlayer = buf.readBoolean();
                es.add(new ShopEntry(itemId, tt, pr, et, ed, ro, qty, cn, bid, bq, questId, questName, nbt, strictMatch, barterNbt, barterStrictMatch, tradeLimit, limitPerPlayer));
            }
            int bal = buf.readVarInt();
            int catCount = buf.readVarInt();
            List<String> cats = new ArrayList<>();
            for (int i = 0; i < catCount; i++) {
                cats.add(buf.readUtf());
            }
            int usedCount = buf.readVarInt();
            java.util.Map<String, Integer> usedCounts = new java.util.HashMap<>();
            for (int i = 0; i < usedCount; i++) {
                String usedKey = buf.readUtf();
                usedCounts.put(usedKey, buf.readVarInt());
            }
            return new ShopDataPacket(cat, es, bal, cats, usedCounts);
        }

    }

    public record OpenShopScreenPacket() {
        static void encode(OpenShopScreenPacket msg, FriendlyByteBuf buf) {
        }

        static OpenShopScreenPacket decode(FriendlyByteBuf buf) {
            return new OpenShopScreenPacket();
        }

    }

    public record SyncShopConfigPacket(
            boolean sdmEnabled,
            boolean shopOverrideEnabled,
            boolean editMode
    ) {
        static void encode(SyncShopConfigPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.sdmEnabled);
            buf.writeBoolean(msg.shopOverrideEnabled);
            buf.writeBoolean(msg.editMode);
        }

        static SyncShopConfigPacket decode(FriendlyByteBuf buf) {
            return new SyncShopConfigPacket(buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }

        static void handle(SyncShopConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                clientConfigSynced = true;
                clientSdmEnabled = msg.sdmEnabled;
                clientShopOverride = msg.shopOverrideEnabled;
                clientEditMode = msg.editMode;
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record PlayerShopOpenPacket(BlockPos marketPos) {
        static void encode(PlayerShopOpenPacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.marketPos);
        }

        static PlayerShopOpenPacket decode(FriendlyByteBuf buf) {
            return new PlayerShopOpenPacket(buf.readBlockPos());
        }

        static void handle(PlayerShopOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                if (sp == null) return;
                PlayerShopOpenHandler.handle(sp, msg.marketPos());
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record PlayerShopTradePacket(
            UUID shopOwnerUUID,
            ResourceLocation itemId,
            int quantity,
            int pricePerUnit,
            BlockPos marketPos
    ) {
        static void encode(PlayerShopTradePacket msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.shopOwnerUUID);
            buf.writeResourceLocation(msg.itemId);
            buf.writeVarInt(msg.quantity);
            buf.writeVarInt(msg.pricePerUnit);
            buf.writeBlockPos(msg.marketPos);
        }

        static PlayerShopTradePacket decode(FriendlyByteBuf buf) {
            UUID uuid = buf.readUUID();
            ResourceLocation itemId = buf.readResourceLocation();
            int quantity = buf.readVarInt();
            int pricePerUnit = buf.readVarInt();
            BlockPos marketPos = buf.readBlockPos();
            return new PlayerShopTradePacket(uuid, itemId, quantity, pricePerUnit, marketPos);
        }

        static void handle(PlayerShopTradePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer buyer = ctx.get().getSender();
                if (buyer == null) return;

                var level = buyer.level();
                BlockEntity be = level.getBlockEntity(msg.marketPos());
                if (!(be instanceof MarketBlockEntity market)) return;

                MarketBlockEntity sellerMarket = market;
                if (!market.getOwner().equals(msg.shopOwnerUUID())) {
                    sellerMarket = resolveMarket((ServerLevel) level, msg.shopOwnerUUID());
                    if (sellerMarket == null) {
                        sendCloseScreen(buyer, "message.sellbox.not_owner");
                        return;
                    }
                }

                var shopInfo = PlayerShopConfig.getShop(msg.shopOwnerUUID());
                ShopEntry entry = null;
                for (ShopEntry e : shopInfo.items()) {
                    if (e.itemId().equals(msg.itemId()) && e.price() == msg.pricePerUnit()) {
                        if ("barter".equals(e.entryType())) {
                            entry = e;
                            break;
                        } else if (!"barter".equals(e.entryType())) {
                            entry = e;
                            break;
                        }
                    }
                }
                if (entry == null) return;

                if ("barter".equals(entry.entryType()) && entry.barterItemId() != null) {
                    handleBarter(msg, buyer, sellerMarket, entry, shopInfo);
                    return;
                }

                long totalPriceLong = (long) msg.pricePerUnit() * (long) msg.quantity();
                if (totalPriceLong <= 0 || totalPriceLong > Integer.MAX_VALUE) return;
                int totalPrice = (int) totalPriceLong;

                if (entry.tradeType() == ShopEntry.TradeType.SELL) {
                    handleSell(msg, buyer, sellerMarket, entry, shopInfo, totalPrice);
                } else {
                    handleBuy(msg, buyer, sellerMarket, entry, shopInfo, totalPrice);
                }
            });
            ctx.get().setPacketHandled(true);
        }

        private static MarketBlockEntity resolveMarket(ServerLevel level, UUID ownerUuid) {
            BlockPos pos = MarketBlockEntity.MARKET_POSITIONS.get(ownerUuid);
            if (pos == null) return null;
            for (ServerLevel sl : level.getServer().getAllLevels()) {
                if (!sl.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                BlockEntity be = sl.getBlockEntity(pos);
                if (be instanceof MarketBlockEntity market && ownerUuid.equals(market.getOwner())) return market;
            }
            return null;
        }

        private static void handleBarter(PlayerShopTradePacket msg, ServerPlayer buyer, MarketBlockEntity market, ShopEntry entry, PlayerShopConfig.PlayerShopInfo shopInfo) {
            int offerQtyPerUnit = Math.max(1, entry.barterItemQty());
            int goodsQtyPerUnit = Math.max(1, entry.quantity());
            int r = 4;
            var level = buyer.level();

            ItemStack offerTemplate = entry.createBarterOfferStack();
            if (offerTemplate.isEmpty()) return;
            ItemStack goodsTemplate = entry.createStack();
            Item goodsItem = goodsTemplate.getItem();
            if (goodsItem == null || goodsItem == net.minecraft.world.item.Items.AIR) return;

            long needOfferLong = (long) offerQtyPerUnit * (long) msg.quantity();
            if (needOfferLong <= 0 || needOfferLong > Integer.MAX_VALUE) return;
            int availableOffer = 0;
            for (ItemStack stack : buyer.getInventory().items) {
                if (entry.matchesOffer(stack)) availableOffer += stack.getCount();
            }
            int maxOfferUnits = availableOffer / offerQtyPerUnit;
            if (maxOfferUnits <= 0) {
                sendCloseScreen(buyer, "message.sellbox.not_enough_items");
                return;
            }
            List<StockBlockEntity> ownerStocks;
            if (level instanceof ServerLevel sl) {
                ownerStocks = collectOwnerStocks(sl, market.getBlockPos(), msg.shopOwnerUUID());
            } else {
                sendCloseScreen(buyer, "message.sellbox.not_enough_stock");
                return;
            }

            int availableGoods = 0;
            for (StockBlockEntity stock : ownerStocks) {
                for (int i = 0; i < stock.getContainerSize(); i++) {
                    ItemStack stack = stock.getItem(i);
                    if (!stack.isEmpty() && stack.is(goodsItem) && entry.matches(stack)) availableGoods += stack.getCount();
                }
            }
            int maxGoodsUnits = availableGoods / goodsQtyPerUnit;
            if (maxGoodsUnits <= 0) {
                sendCloseScreen(buyer, "message.sellbox.not_enough_stock");
                return;
            }

            int actualUnits = Math.min(msg.quantity(), Math.min(maxOfferUnits, maxGoodsUnits));
            if (actualUnits <= 0) return;
            long needOfferActualLong = (long) offerQtyPerUnit * actualUnits;
            long needGoodsActualLong = (long) goodsQtyPerUnit * actualUnits;
            if (needOfferActualLong > Integer.MAX_VALUE || needGoodsActualLong > Integer.MAX_VALUE) return;
            int needOfferActual = (int) needOfferActualLong;
            int needGoodsActual = (int) needGoodsActualLong;

            int toTakeGoods = needGoodsActual;
            // 记录被移除的货物栈（保留原样 NBT），库存不足时按原样退回
            List<ItemStack> removedGoods = new ArrayList<>();
            for (StockBlockEntity stock : ownerStocks) {
                for (int i = 0; i < stock.getContainerSize() && toTakeGoods > 0; i++) {
                    ItemStack stack = stock.getItem(i);
                    if (!stack.isEmpty() && stack.is(goodsItem) && entry.matches(stack)) {
                        int take = Math.min(toTakeGoods, stack.getCount());
                        stock.removeItem(i, take);
                        removedGoods.add(stack.copyWithCount(take));
                        toTakeGoods -= take;
                    }
                }
            }
            if (toTakeGoods > 0) {
                // 库存不足：把已取走的货物按原样退回（保留 NBT），不静默丢失
                for (ItemStack toReturn : removedGoods) {
                    if (toReturn.isEmpty()) continue;
                    int toPlace = toReturn.getCount();
                    // 1) 优先合并进相同 ID+NBT 的槽
                    for (StockBlockEntity stock : ownerStocks) {
                        for (int i = 0; i < stock.getContainerSize() && toPlace > 0; i++) {
                            ItemStack slotItem = stock.getItem(i);
                            if (!slotItem.isEmpty() && ItemStack.isSameItemSameTags(slotItem, toReturn)) {
                                int canAdd = Math.min(slotItem.getMaxStackSize() - slotItem.getCount(), toPlace);
                                if (canAdd > 0) {
                                    slotItem.grow(canAdd);
                                    stock.setItem(i, slotItem);
                                    toPlace -= canAdd;
                                }
                            }
                        }
                    }
                    // 2) 再放入空槽
                    for (StockBlockEntity stock : ownerStocks) {
                        for (int i = 0; i < stock.getContainerSize() && toPlace > 0; i++) {
                            if (stock.getItem(i).isEmpty()) {
                                int add = Math.min(toPlace, toReturn.getMaxStackSize());
                                stock.setItem(i, toReturn.copyWithCount(add));
                                toPlace -= add;
                            }
                        }
                    }
                    // 3) 实在放不下：先给在线店主背包，再退回买家背包（不丢失）
                    if (toPlace > 0) {
                        ItemStack leftover = toReturn.copyWithCount(toPlace);
                        ServerPlayer shopOwnerP = buyer.getServer().getPlayerList().getPlayer(msg.shopOwnerUUID());
                        if (shopOwnerP != null) shopOwnerP.getInventory().placeItemBackInInventory(leftover);
                        else buyer.getInventory().placeItemBackInInventory(leftover);
                    }
                }
                return;
            }
            int actualGivenGoods = needGoodsActual;

            int toRemoveOffer = needOfferActual;
            for (ItemStack stack : buyer.getInventory().items) {
                if (toRemoveOffer <= 0) break;
                if (entry.matchesOffer(stack)) {
                    int rm = Math.min(toRemoveOffer, stack.getCount());
                    stack.shrink(rm);
                    toRemoveOffer -= rm;
                }
            }
            int actualGiveOffer = needOfferActual - toRemoveOffer;

            if (actualGiveOffer <= 0) return;

            ItemStack goodsAsStack = goodsTemplate.copy();
            if (!entry.customName().isEmpty()) {
                goodsAsStack.getOrCreateTag().putString("CustomName", entry.customName());
            }
            int maxGoodsStack = goodsAsStack.getMaxStackSize();
            int toGiveGoods = actualGivenGoods;
            while (toGiveGoods > 0) {
                int chunk = Math.min(toGiveGoods, maxGoodsStack);
                buyer.getInventory().placeItemBackInInventory(goodsAsStack.copyWithCount(chunk));
                toGiveGoods -= chunk;
            }

            BlockPos sellerMarketPos = market.getBlockPos();
            int toDeposit = actualGiveOffer;
            int offerMaxStack = offerTemplate.getMaxStackSize();
            if (offerTemplate.getCount() != 1) offerTemplate = offerTemplate.copyWithCount(1);
            ItemStack cleanOffer = offerTemplate.copy();
            for (int i = 0; i < MarketBlockEntity.RECEIPT_BOOK_SLOT && toDeposit > 0; i++) {
                ItemStack slot = market.getItem(i);
                if (slot.isEmpty()) {
                    int add = Math.min(toDeposit, offerMaxStack);
                    market.setItem(i, cleanOffer.copyWithCount(add));
                    toDeposit -= add;
                } else if (ItemStack.isSameItemSameTags(slot, cleanOffer)) {
                    int canAdd = Math.min(offerMaxStack - slot.getCount(), toDeposit);
                    if (canAdd > 0) {
                        slot.grow(canAdd);
                        market.setItem(i, slot);
                        toDeposit -= canAdd;
                    }
                }
            }
            market.setChanged();
            market.sync();

            for (int dx = -r; dx <= r && toDeposit > 0; dx++) {
                for (int dy = -r; dy <= r && toDeposit > 0; dy++) {
                    for (int dz = -r; dz <= r && toDeposit > 0; dz++) {
                        BlockPos pos = sellerMarketPos.offset(dx, dy, dz);
                        BlockEntity stockBe = level.getBlockEntity(pos);
                        if (stockBe instanceof StockBlockEntity stock) {
                            if (!msg.shopOwnerUUID().equals(stock.getOwner())) continue;
                            for (int i = 0; i < stock.getContainerSize() && toDeposit > 0; i++) {
                                ItemStack existing = stock.getItem(i);
                                if (existing.isEmpty()) {
                                    int add = Math.min(toDeposit, offerMaxStack);
                                    stock.setItem(i, cleanOffer.copyWithCount(add));
                                    toDeposit -= add;
                                } else if (ItemStack.isSameItemSameTags(existing, cleanOffer)) {
                                    int canAdd = Math.min(offerMaxStack - existing.getCount(), toDeposit);
                                    if (canAdd > 0) {
                                        existing.grow(canAdd);
                                        stock.setItem(i, existing);
                                        toDeposit -= canAdd;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (toDeposit > 0) {
                ServerPlayer shopOwner = buyer.getServer().getPlayerList().getPlayer(msg.shopOwnerUUID());
                if (shopOwner != null) {
                    while (toDeposit > 0) {
                        int chunk = Math.min(toDeposit, offerMaxStack);
                        shopOwner.getInventory().placeItemBackInInventory(cleanOffer.copyWithCount(chunk));
                        toDeposit -= chunk;
                    }
                }
                while (toDeposit > 0) {
                    int chunk = Math.min(toDeposit, offerMaxStack);
                    buyer.getInventory().placeItemBackInInventory(cleanOffer.copyWithCount(chunk));
                    toDeposit -= chunk;
                }
            }

            String offerName = cleanOffer.getHoverName().getString();
            String goodsName = entry.getDisplayName().getString();
            String shopName = getShopDisplayName(msg, shopInfo, market);
            String receiptText = String.format("%s 在 %s 用 %s x%d 换取了 %s x%d",
                    buyer.getName().getString(), shopName, offerName, actualGiveOffer, goodsName, actualGivenGoods);
            market.writeReceipt(buyer.getName().getString(), receiptText);
            sendActionMessage(buyer, "message.sellbox.trade_success_barter");
        }

        private static void handleSell(PlayerShopTradePacket msg, ServerPlayer buyer, MarketBlockEntity market, ShopEntry entry, PlayerShopConfig.PlayerShopInfo shopInfo, int totalPrice) {
            if (!SellBoxEconomy.tryDeductBalance(buyer, totalPrice)) {
                sendCloseScreen(buyer, "message.sellbox.not_enough_money");
                return;
            }
            sendBalanceToPlayer(buyer);

            int qtyPerUnit = Math.max(1, entry.quantity());
            // 先按整单元统计可用库存（严格匹配）：累计匹配总数量后再整除，避免跨槽余数被丢弃
            int availableCount = 0;
            if (buyer.level() instanceof ServerLevel sl) {
                for (StockBlockEntity stock : collectOwnerStocks(sl, market.getBlockPos(), msg.shopOwnerUUID())) {
                    for (int i = 0; i < stock.getContainerSize(); i++) {
                        ItemStack stack = stock.getItem(i);
                        if (entry.matches(stack)) availableCount += stack.getCount();
                    }
                }
            }
            int maxUnits = availableCount / qtyPerUnit;
            if (maxUnits <= 0) {
                SellBoxEconomy.addBalance(buyer, totalPrice);
                sendBalanceToPlayer(buyer);
                sendCloseScreen(buyer, "message.sellbox.not_enough_stock");
                return;
            }

            int actualUnits = Math.min(msg.quantity(), maxUnits);
            int toTake = actualUnits * qtyPerUnit;
            int remaining = toTake;
            if (buyer.level() instanceof ServerLevel sl) {
                for (StockBlockEntity stock : collectOwnerStocks(sl, market.getBlockPos(), msg.shopOwnerUUID())) {
                    for (int i = 0; i < stock.getContainerSize() && remaining > 0; i++) {
                        ItemStack stack = stock.getItem(i);
                        if (entry.matches(stack)) {
                            int take = Math.min(remaining, stack.getCount());
                            stock.removeItem(i, take);
                            remaining -= take;
                        }
                    }
                }
            }

            int actualGiven = toTake - remaining;
            if (actualGiven <= 0) {
                SellBoxEconomy.addBalance(buyer, totalPrice);
                sendBalanceToPlayer(buyer);
                sendCloseScreen(buyer, "message.sellbox.not_enough_stock");
                return;
            }

            int refundUnits = msg.quantity() - actualUnits;
            if (refundUnits > 0) {
                int refund = msg.pricePerUnit() * refundUnits;
                SellBoxEconomy.addBalance(buyer, refund);
                sendBalanceToPlayer(buyer);
            }

            int ownerPayout = msg.pricePerUnit() * actualUnits;

            ServerPlayer shopOwner = buyer.getServer().getPlayerList().getPlayer(msg.shopOwnerUUID());
            if (shopOwner != null) {
                SellBoxEconomy.addBalance(shopOwner, ownerPayout);
                sendBalanceToPlayer(shopOwner);
            } else {
                SellBoxConfig.addPendingBalance(msg.shopOwnerUUID(), ownerPayout);
            }

            ItemStack template = entry.createStack();
            if (!entry.customName().isEmpty()) {
                template.getOrCreateTag().putString("CustomName", entry.customName());
            }
            int maxStack = template.getMaxStackSize();
            int toGive = actualGiven;
            while (toGive > 0) {
                int chunk = Math.min(toGive, maxStack);
                buyer.getInventory().placeItemBackInInventory(template.copyWithCount(chunk));
                toGive -= chunk;
            }

            String itemName = entry.getDisplayName().getString();
            String shopName = getShopDisplayName(msg, shopInfo, market);
            String receiptText = String.format("%s 从 %s 购买了 %s x%d，消费 %d 金币",
                    buyer.getName().getString(), shopName, itemName, actualUnits, ownerPayout);
            market.writeReceipt(buyer.getName().getString(), receiptText);
            PlayerShopConfig.addEarnings(msg.shopOwnerUUID(), ownerPayout, actualUnits);
            sendActionMessage(buyer, "message.sellbox.trade_success_buy");
        }

        private static void handleBuy(PlayerShopTradePacket msg, ServerPlayer seller, MarketBlockEntity market, ShopEntry entry, PlayerShopConfig.PlayerShopInfo shopInfo, int totalPrice) {
            int qtyPerUnit = Math.max(1, entry.quantity());

            int available = 0;
            for (ItemStack stack : seller.getInventory().items) {
                if (entry.matches(stack)) {
                    available += stack.getCount();
                }
            }
            int maxUnits = available / qtyPerUnit;
            if (maxUnits <= 0) {
                sendCloseScreen(seller, "message.sellbox.not_enough_items");
                return;
            }

            int actualUnits = Math.min(msg.quantity(), maxUnits);
            int toSell = actualUnits * qtyPerUnit;
            int sellerPayout = msg.pricePerUnit() * actualUnits;

            ServerPlayer shopOwner = seller.getServer().getPlayerList().getPlayer(msg.shopOwnerUUID());
            boolean ownerCanPay;
            if (shopOwner != null) {
                ownerCanPay = SellBoxEconomy.tryDeductBalance(shopOwner, sellerPayout);
                if (ownerCanPay) sendBalanceToPlayer(shopOwner);
            } else {
                ownerCanPay = SellBoxConfig.tryDeductPendingBalance(msg.shopOwnerUUID(), sellerPayout);
            }

            if (!ownerCanPay) {
                sendCloseScreen(seller, "message.sellbox.not_enough_money");
                return;
            }

            int toRemove = toSell;
            for (ItemStack stack : seller.getInventory().items) {
                if (toRemove <= 0) break;
                if (entry.matches(stack)) {
                    int r = Math.min(toRemove, stack.getCount());
                    stack.shrink(r);
                    toRemove -= r;
                }
            }

            SellBoxEconomy.addBalance(seller, sellerPayout);
            sendBalanceToPlayer(seller);

            ItemStack deposited = entry.createStack();
            deposited.setCount(toSell);
            market.depositEarnedItem(deposited);

            String itemName = entry.getDisplayName().getString();
            String shopName = getShopDisplayName(msg, shopInfo, market);
            String receiptText = String.format("%s 向 %s 出售了 %s x%d，获得 %d 金币",
                    seller.getName().getString(), shopName, itemName, actualUnits, sellerPayout);
            market.writeReceipt(seller.getName().getString(), receiptText);
            sendActionMessage(seller, "message.sellbox.trade_success_sell");
        }

        private static String getShopDisplayName(PlayerShopTradePacket msg, PlayerShopConfig.PlayerShopInfo shopInfo, MarketBlockEntity market) {
            if (shopInfo.shopName() != null && !shopInfo.shopName().isEmpty()) {
                return shopInfo.shopName();
            }
            return market.getDisplayName().getString();
        }

        private static List<StockBlockEntity> collectOwnerStocks(ServerLevel level, BlockPos sellerMarketPos, UUID ownerUuid) {
            List<StockBlockEntity> result = new ArrayList<>();
            int mcx = sellerMarketPos.getX() >> 4;
            int mcz = sellerMarketPos.getZ() >> 4;
            // 只扫描 Market 自身所在的 1 个 chunk（与 refreshStockCache 保持一致）
            if (!level.hasChunk(mcx, mcz)) return result;
            LevelChunk chunk = level.getChunk(mcx, mcz);
            if (chunk == null) return result;
            for (var be : chunk.getBlockEntities().values()) {
                if (be instanceof StockBlockEntity stock && ownerUuid.equals(stock.getOwner())) {
                    result.add(stock);
                }
            }
            return result;
        }
    }

    public record PlayerShopNamePacket(String shopName, BlockPos marketPos) {
        static void encode(PlayerShopNamePacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.shopName);
            buf.writeBlockPos(msg.marketPos);
        }

        static PlayerShopNamePacket decode(FriendlyByteBuf buf) {
            return new PlayerShopNamePacket(buf.readUtf(), buf.readBlockPos());
        }

        static void handle(PlayerShopNamePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                if (sp != null) {
                    PlayerShopConfig.setShopName(sp.getUUID(), msg.shopName());
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SyncBlacklistPacket(Set<String> itemIds) {
        static void encode(SyncBlacklistPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.itemIds.size());
            for (String id : msg.itemIds) {
                buf.writeUtf(id);
            }
        }

        static SyncBlacklistPacket decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < count; i++) {
                ids.add(buf.readUtf());
            }
            return new SyncBlacklistPacket(ids);
        }

        static void handle(SyncBlacklistPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                clientBlacklist = msg.itemIds;
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SyncAdminEditPacket(boolean enabled) {
        static void encode(SyncAdminEditPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.enabled);
        }

        static SyncAdminEditPacket decode(FriendlyByteBuf buf) {
            return new SyncAdminEditPacket(buf.readBoolean());
        }

        static void handle(SyncAdminEditPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                clientAdminEditEnabled = msg.enabled;
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SyncAllShopEntriesPacket(Map<String, List<ShopEntry>> categories) {
        static void encode(SyncAllShopEntriesPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.categories.size());
            for (Map.Entry<String, List<ShopEntry>> e : msg.categories.entrySet()) {
                buf.writeUtf(e.getKey());
                buf.writeVarInt(e.getValue().size());
                for (ShopEntry s : e.getValue()) {
                    buf.writeUtf(s.itemId().toString());
                    buf.writeUtf(s.tradeType().name());
                    buf.writeVarInt(s.price());
                    buf.writeUtf(s.entryType());
                    buf.writeUtf(s.extraData());
                    buf.writeBoolean(s.requireOp());
                    buf.writeVarInt(s.quantity());
                    buf.writeUtf(s.customName());
                    if (s.barterItemId() != null) {
                        buf.writeBoolean(true);
                        buf.writeUtf(s.barterItemId().toString());
                    } else {
                        buf.writeBoolean(false);
                    }
                    buf.writeVarInt(s.barterItemQty());
                    if (s.requiredQuestId() != null) {
                        buf.writeBoolean(true);
                        buf.writeLong(s.requiredQuestId());
                        buf.writeUtf(s.requiredQuestName() != null ? s.requiredQuestName() : "");
                    } else {
                        buf.writeBoolean(false);
                    }
                }
            }
        }

        static SyncAllShopEntriesPacket decode(FriendlyByteBuf buf) {
            int catCount = buf.readVarInt();
            Map<String, List<ShopEntry>> cats = new LinkedHashMap<>();
            for (int i = 0; i < catCount; i++) {
                String name = buf.readUtf();
                int count = buf.readVarInt();
                List<ShopEntry> list = new ArrayList<>();
                for (int j = 0; j < count; j++) {
                    ResourceLocation itemId = new ResourceLocation(buf.readUtf());
                    ShopEntry.TradeType tt = ShopEntry.TradeType.valueOf(buf.readUtf());
                    int pr = buf.readVarInt();
                    String et = buf.readUtf();
                    String ed = buf.readUtf();
                    boolean ro = buf.readBoolean();
                    int qty = buf.readVarInt();
                    String cn = buf.readUtf();
                    boolean hasB = buf.readBoolean();
                    ResourceLocation bid = hasB ? new ResourceLocation(buf.readUtf()) : null;
                    int bq = buf.readVarInt();
                    Long questId = buf.readBoolean() ? buf.readLong() : null;
                    String questName = questId != null ? buf.readUtf() : null;
                    if (questName != null && questName.isEmpty()) questName = null;
                    list.add(new ShopEntry(itemId, tt, pr, et, ed, ro, qty, cn, bid, bq, questId, questName));
                }
                cats.put(name, list);
            }
            return new SyncAllShopEntriesPacket(cats);
        }
    }

    public record PlayerShopEditPacket(
            String action,
            int index,
            ResourceLocation itemId,
            int price,
            int quantity,
            String customName,
            BlockPos marketPos,
            ShopEntry.TradeType tradeType,
            ResourceLocation barterItemId,
            int barterItemQty,
            @org.jetbrains.annotations.Nullable UUID targetOwner,
            @org.jetbrains.annotations.Nullable String nbt,
            boolean strictMatch,
            @org.jetbrains.annotations.Nullable String barterNbt,
            boolean barterStrictMatch
    ) {
        public PlayerShopEditPacket(String action, int index, ResourceLocation itemId, int price, int quantity,
                                    String customName, BlockPos marketPos, ShopEntry.TradeType tradeType,
                                    ResourceLocation barterItemId, int barterItemQty,
                                    @org.jetbrains.annotations.Nullable UUID targetOwner) {
            this(action, index, itemId, price, quantity, customName, marketPos, tradeType, barterItemId, barterItemQty, targetOwner, null, false, null, false);
        }

        static void encode(PlayerShopEditPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.action);
            buf.writeVarInt(msg.index);
            buf.writeResourceLocation(msg.itemId);
            buf.writeVarInt(msg.price);
            buf.writeVarInt(msg.quantity);
            buf.writeUtf(msg.customName);
            buf.writeBlockPos(msg.marketPos);
            buf.writeUtf(msg.tradeType().name());
            if (msg.barterItemId() != null) {
                buf.writeBoolean(true);
                buf.writeResourceLocation(msg.barterItemId());
            } else {
                buf.writeBoolean(false);
            }
            buf.writeVarInt(msg.barterItemQty);
            if (msg.targetOwner() != null) {
                buf.writeBoolean(true);
                buf.writeUUID(msg.targetOwner());
            } else {
                buf.writeBoolean(false);
            }
            buf.writeUtf(msg.nbt != null ? msg.nbt : "");
            buf.writeBoolean(msg.strictMatch);
            buf.writeUtf(msg.barterNbt != null ? msg.barterNbt : "");
            buf.writeBoolean(msg.barterStrictMatch);
        }

        static PlayerShopEditPacket decode(FriendlyByteBuf buf) {
            String action = buf.readUtf();
            int index = buf.readVarInt();
            ResourceLocation itemId = buf.readResourceLocation();
            int price = buf.readVarInt();
            int quantity = buf.readVarInt();
            String customName = buf.readUtf();
            BlockPos mp = buf.readBlockPos();
            ShopEntry.TradeType tt = ShopEntry.TradeType.valueOf(buf.readUtf());
            boolean hasBarter = buf.readBoolean();
            ResourceLocation barterId = hasBarter ? buf.readResourceLocation() : null;
            int barterQty = buf.readVarInt();
            boolean hasTarget = buf.readBoolean();
            UUID target = hasTarget ? buf.readUUID() : null;
            String nbt = buf.readUtf();
            boolean strictMatch = buf.readBoolean();
            String barterNbt = buf.readUtf();
            boolean barterStrictMatch = buf.readBoolean();
            return new PlayerShopEditPacket(action, index, itemId, price, quantity, customName, mp, tt, barterId, barterQty, target,
                    nbt.isEmpty() ? null : nbt, strictMatch, barterNbt.isEmpty() ? null : barterNbt, barterStrictMatch);
        }

        static void handle(PlayerShopEditPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                if (sp == null) return;
                PlayerShopEditHandler.handle(sp, msg);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record PlayerShopDataPacket(
            Map<UUID, String> shopNames,
            Map<UUID, List<ShopEntry>> shopItems,
            Map<UUID, Map<String, Integer>> stockCounts,
            Set<UUID> unloadedOwners,
            Map<UUID, String> ownerNames
    ) {
        static void encode(PlayerShopDataPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.shopNames.size());
            for (var entry : msg.shopNames.entrySet()) {
                buf.writeUUID(entry.getKey());
                buf.writeUtf(entry.getValue());
            }

            buf.writeVarInt(msg.shopItems.size());
            for (var entry : msg.shopItems.entrySet()) {
                buf.writeUUID(entry.getKey());
                List<ShopEntry> items = entry.getValue();
                buf.writeVarInt(items.size());
                for (ShopEntry item : items) {
                    buf.writeUtf(item.itemId().toString());
                    buf.writeUtf(item.tradeType().name());
                    buf.writeVarInt(item.price());
                    buf.writeUtf(item.entryType());
                    buf.writeUtf(item.extraData());
                    buf.writeBoolean(item.requireOp());
                    buf.writeVarInt(item.quantity());
                    buf.writeUtf(item.customName());
                    if (item.barterItemId() != null) {
                        buf.writeBoolean(true);
                        buf.writeUtf(item.barterItemId().toString());
                    } else {
                        buf.writeBoolean(false);
                    }
                    buf.writeVarInt(item.barterItemQty());
                    buf.writeUtf(item.nbt() != null ? item.nbt().toString() : "");
                    buf.writeBoolean(item.strictMatch());
                    buf.writeUtf(item.barterNbt() != null ? item.barterNbt().toString() : "");
                    buf.writeBoolean(item.barterStrictMatch());
                }
            }

            buf.writeVarInt(msg.stockCounts.size());
            for (var entry : msg.stockCounts.entrySet()) {
                buf.writeUUID(entry.getKey());
                Map<String, Integer> stocks = entry.getValue();
                buf.writeVarInt(stocks.size());
                for (var stock : stocks.entrySet()) {
                    buf.writeUtf(stock.getKey());
                    buf.writeVarInt(stock.getValue());
                }
            }

            buf.writeVarInt(msg.unloadedOwners.size());
            for (UUID uid : msg.unloadedOwners) {
                buf.writeUUID(uid);
            }

            buf.writeVarInt(msg.ownerNames.size());
            for (var entry : msg.ownerNames.entrySet()) {
                buf.writeUUID(entry.getKey());
                buf.writeUtf(entry.getValue());
            }
        }

        static PlayerShopDataPacket decode(FriendlyByteBuf buf) {
            int shopNamesSize = buf.readVarInt();
            Map<UUID, String> shopNames = new LinkedHashMap<>();
            for (int i = 0; i < shopNamesSize; i++) {
                shopNames.put(buf.readUUID(), buf.readUtf());
            }

            int shopItemsSize = buf.readVarInt();
            Map<UUID, List<ShopEntry>> shopItems = new LinkedHashMap<>();
            for (int i = 0; i < shopItemsSize; i++) {
                UUID uuid = buf.readUUID();
                int itemsSize = buf.readVarInt();
                List<ShopEntry> items = new ArrayList<>();
                for (int j = 0; j < itemsSize; j++) {
                    ResourceLocation itemId = new ResourceLocation(buf.readUtf());
                    ShopEntry.TradeType tradeType = ShopEntry.TradeType.valueOf(buf.readUtf());
                    int price = buf.readVarInt();
                    String entryType = buf.readUtf();
                    String extraData = buf.readUtf();
                    boolean requireOp = buf.readBoolean();
                    int quantity = buf.readVarInt();
                    String customName = buf.readUtf();
                    boolean hasBarter = buf.readBoolean();
                    ResourceLocation barterItemId = hasBarter ? new ResourceLocation(buf.readUtf()) : null;
                    int barterItemQty = buf.readVarInt();
                    String nbtStr = buf.readUtf();
                    boolean strictMatch = buf.readBoolean();
                    String barterNbtStr = buf.readUtf();
                    boolean barterStrictMatch = buf.readBoolean();
                    net.minecraft.nbt.CompoundTag nbt = null;
                    if (!nbtStr.isEmpty()) {
                        try { nbt = net.minecraft.nbt.TagParser.parseTag(nbtStr); } catch (Exception ignored) { }
                    }
                    net.minecraft.nbt.CompoundTag barterNbt = null;
                    if (!barterNbtStr.isEmpty()) {
                        try { barterNbt = net.minecraft.nbt.TagParser.parseTag(barterNbtStr); } catch (Exception ignored) { }
                    }
                    items.add(new ShopEntry(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName, barterItemId, barterItemQty, null, null, nbt, strictMatch, barterNbt, barterStrictMatch));
                }
                shopItems.put(uuid, items);
            }

            int stockCountsSize = buf.readVarInt();
            Map<UUID, Map<String, Integer>> stockCounts = new LinkedHashMap<>();
            for (int i = 0; i < stockCountsSize; i++) {
                UUID uuid = buf.readUUID();
                int stocksSize = buf.readVarInt();
                Map<String, Integer> stocks = new LinkedHashMap<>();
                for (int j = 0; j < stocksSize; j++) {
                    stocks.put(buf.readUtf(), buf.readVarInt());
                }
                stockCounts.put(uuid, stocks);
            }

            int unloadedSize = buf.readVarInt();
            Set<UUID> unloadedOwners = new HashSet<>();
            for (int i = 0; i < unloadedSize; i++) {
                unloadedOwners.add(buf.readUUID());
            }

            int ownerNamesSize = buf.readVarInt();
            Map<UUID, String> ownerNames = new LinkedHashMap<>();
            for (int i = 0; i < ownerNamesSize; i++) {
                ownerNames.put(buf.readUUID(), buf.readUtf());
            }

            return new PlayerShopDataPacket(shopNames, shopItems, stockCounts, unloadedOwners, ownerNames);
        }

    }

    public record OpenPlayerShopScreenPacket(BlockPos marketPos) {
        static void encode(OpenPlayerShopScreenPacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.marketPos);
        }

        static OpenPlayerShopScreenPacket decode(FriendlyByteBuf buf) {
            return new OpenPlayerShopScreenPacket(buf.readBlockPos());
        }

    }

    // Quest prerequisite packets

    public record SyncQuestListPacket(List<QuestEntry> quests) {
        public record QuestEntry(long questId, String questName, String chapterName, String chapterGroupName) {}

        static void encode(SyncQuestListPacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.quests.size());
            for (QuestEntry qe : msg.quests) {
                buf.writeLong(qe.questId);
                buf.writeUtf(qe.questName);
                buf.writeUtf(qe.chapterName);
                buf.writeUtf(qe.chapterGroupName);
            }
        }

        static SyncQuestListPacket decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<QuestEntry> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                long id = buf.readLong();
                String name = buf.readUtf();
                String chapter = buf.readUtf();
                String group = buf.readUtf();
                list.add(new QuestEntry(id, name, chapter, group));
            }
            return new SyncQuestListPacket(list);
        }
    }

    public record RequestQuestListPacket(String category, int index) {
        static void encode(RequestQuestListPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.category);
            buf.writeVarInt(msg.index);
        }

        static RequestQuestListPacket decode(FriendlyByteBuf buf) {
            return new RequestQuestListPacket(buf.readUtf(), buf.readVarInt());
        }

        static void handle(RequestQuestListPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                if (sp == null || !sp.hasPermissions(2)) return;
                List<com.sellbox.mod.integration.FTBQuestsIntegration.QuestInfo> quests =
                        com.sellbox.mod.integration.FTBQuestsIntegration.getAllQuests();
                if (quests == null) {
                    sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cFTB Quests 未安装或无法获取任务列表"));
                    return;
                }
                List<SyncQuestListPacket.QuestEntry> entries = new ArrayList<>();
                for (var qi : quests) {
                    entries.add(new SyncQuestListPacket.QuestEntry(qi.questId, qi.questName, qi.chapterName, qi.chapterGroupName));
                }
                sendToPlayer(sp, new SyncQuestListPacket(entries));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record SetEntryQuestPacket(String category, int index, @Nullable Long questId) {
        static void encode(SetEntryQuestPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.category);
            buf.writeVarInt(msg.index);
            buf.writeBoolean(msg.questId != null);
            if (msg.questId != null) buf.writeLong(msg.questId);
        }

        static SetEntryQuestPacket decode(FriendlyByteBuf buf) {
            String cat = buf.readUtf();
            int idx = buf.readVarInt();
            Long qid = buf.readBoolean() ? buf.readLong() : null;
            return new SetEntryQuestPacket(cat, idx, qid);
        }

        static void handle(SetEntryQuestPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                if (sp == null || !sp.hasPermissions(2)) return;
                var entries = ShopConfig.getCategory(msg.category);
                if (entries == null || msg.index < 0 || msg.index >= entries.size()) return;
                ShopEntry old = entries.get(msg.index);
                String questName = null;
                if (msg.questId != null) {
                    questName = com.sellbox.mod.integration.FTBQuestsIntegration.getQuestNameById(msg.questId);
                }
                ShopEntry updated = new ShopEntry(old.itemId(), old.tradeType(), old.price(),
                        old.entryType(), old.extraData(), old.requireOp(), old.quantity(),
                        old.customName(), old.barterItemId(), old.barterItemQty(), msg.questId, questName,
                        old.nbt(), old.strictMatch(), old.barterNbt(), old.barterStrictMatch());
                ShopConfig.updateItem(msg.category, msg.index, updated);
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        msg.questId != null ? "§a已设置前置任务" : "§a已清除前置任务"));
                // Refresh shop data for all players
                for (var player : sp.getServer().getPlayerList().getPlayers()) {
                    sendToPlayer(player, new ShopDataPacket(msg.category,
                            ShopConfig.getCategory(msg.category), com.sellbox.mod.shop.SellBoxEconomy.getBalance(player),
                            ShopConfig.getCategoryNames(),
                            TradeLimitTracker.snapshotUsed(ShopConfig.getCategory(msg.category), msg.category, player.getUUID())));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 设置某条商品的交易限量：limit<=0 表示无限；perPlayer=true 每个玩家各自计算 */
    public record SetEntryLimitPacket(String category, int index, int limit, boolean perPlayer) {
        static void encode(SetEntryLimitPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.category);
            buf.writeVarInt(msg.index);
            buf.writeVarInt(msg.limit);
            buf.writeBoolean(msg.perPlayer);
        }

        static SetEntryLimitPacket decode(FriendlyByteBuf buf) {
            return new SetEntryLimitPacket(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        static void handle(SetEntryLimitPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                if (sp == null || !sp.hasPermissions(2)) return;
                var entries = ShopConfig.getCategory(msg.category);
                if (entries == null || msg.index < 0 || msg.index >= entries.size()) return;
                ShopEntry old = entries.get(msg.index);
                ShopEntry updated = old.withTradeLimit(Math.max(0, msg.limit), msg.perPlayer);
                ShopConfig.updateItem(msg.category, msg.index, updated);
                // 额度变了就清空历史计数，否则新额度要等旧计数用完才生效
                TradeLimitTracker.reset(updated, msg.category);
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        updated.hasTradeLimit() ? "§a已设置交易限量" : "§a已取消交易限量"));
                for (var player : sp.getServer().getPlayerList().getPlayers()) {
                    sendToPlayer(player, new ShopDataPacket(msg.category,
                            ShopConfig.getCategory(msg.category),
                            SellBoxEconomy.getBalance(player),
                            ShopConfig.getCategoryNames(),
                            TradeLimitTracker.snapshotUsed(ShopConfig.getCategory(msg.category), msg.category, player.getUUID())));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record RequestBalancePacket() {
        static void encode(RequestBalancePacket msg, FriendlyByteBuf buf) {
        }

        static RequestBalancePacket decode(FriendlyByteBuf buf) {
            return new RequestBalancePacket();
        }

        static void handle(RequestBalancePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                if (sp != null) {
                    sendBalanceToPlayer(sp);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record CloseScreenPacket(String messageKey) {
        static void encode(CloseScreenPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.messageKey);
        }

        static CloseScreenPacket decode(FriendlyByteBuf buf) {
            return new CloseScreenPacket(buf.readUtf());
        }

    }

    public record ActionMessagePacket(String messageKey) {
        static void encode(ActionMessagePacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.messageKey);
        }

        static ActionMessagePacket decode(FriendlyByteBuf buf) {
            return new ActionMessagePacket(buf.readUtf());
        }
    }

    // ──────────────────── Send Helpers ────────────────────

    private static void sendToPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendAllPricesToPlayer(ServerPlayer player) {
        Map<Item, Integer> prices = SellBoxConfig.getAllPrices();
        Map<String, Integer> stringPrices = new HashMap<>();
        for (var entry : prices.entrySet()) {
            stringPrices.put(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(), entry.getValue());
        }
        for (var entry : SellBoxConfig.getAllPricesNbt().entrySet()) {
            stringPrices.put(entry.getKey(), entry.getValue());
        }
        sendToPlayer(player, new SyncAllPricesPacket(stringPrices));
    }

    public static void sendBalanceToPlayer(ServerPlayer player) {
        int balance = SellBoxEconomy.getBalance(player);
        sendToPlayer(player, new SyncBalancePacket(balance));
    }

    public static void sendCloseScreen(ServerPlayer player, String messageKey) {
        sendToPlayer(player, new CloseScreenPacket(messageKey));
    }

    public static void sendActionMessage(ServerPlayer player, String messageKey) {
        sendToPlayer(player, new ActionMessagePacket(messageKey));
    }

    public static void broadcastAllPrices(MinecraftServer server) {
        Map<String, Integer> stringPrices = new HashMap<>();
        for (var entry : SellBoxConfig.getAllPrices().entrySet()) {
            stringPrices.put(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(), entry.getValue());
        }
        for (var entry : SellBoxConfig.getAllPricesNbt().entrySet()) {
            stringPrices.put(entry.getKey(), entry.getValue());
        }
        SyncAllPricesPacket payload = new SyncAllPricesPacket(stringPrices);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToPlayer(player, payload);
        }
    }

    public static void sendShopConfigToPlayer(ServerPlayer player) {
        sendToPlayer(player, new SyncShopConfigPacket(
                SellBoxConfig.isSdmCurrencyEnabled(),
                SellBoxConfig.isShopOverrideEnabled(),
                ShopConfig.isEditMode() && player.hasPermissions(2)
        ));
    }

    public static void sendBlacklistToPlayer(ServerPlayer player) {
        sendToPlayer(player, new SyncBlacklistPacket(
                com.sellbox.mod.playershop.PlayerShopBlacklist.getAsStringSet()
        ));
    }

    public static void sendAdminEditToPlayer(ServerPlayer player) {
        sendToPlayer(player, new SyncAdminEditPacket(ShopConfig.isAdminEditEnabled()));
    }

    public static void sendAllShopEntriesToPlayer(ServerPlayer player) {
        sendToPlayer(player, new SyncAllShopEntriesPacket(ShopConfig.getCategories()));
    }

    public static void sendQuestListToPlayer(ServerPlayer player) {
        try {
            var quests = com.sellbox.mod.integration.FTBQuestsIntegration.getAllQuests();
            if (quests == null) {
                SellBoxMod.LOGGER.warn("sendQuestListToPlayer: getAllQuests returned null for player {}", player.getName().getString());
                return;
            }
            List<SyncQuestListPacket.QuestEntry> entries = new ArrayList<>();
            for (var qi : quests) {
                entries.add(new SyncQuestListPacket.QuestEntry(qi.questId, qi.questName, qi.chapterName, qi.chapterGroupName));
            }
            sendToPlayer(player, new SyncQuestListPacket(entries));
            SellBoxMod.LOGGER.debug("Sent {} quests to player {}", entries.size(), player.getName().getString());
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("sendQuestListToPlayer failed", e);
        }
    }

    // ──────────────────── Registration ────────────────────

    public static void register() {
        int id = 0;

        // S→C packets
        CHANNEL.registerMessage(id++, SyncBalancePacket.class,
                SyncBalancePacket::encode, SyncBalancePacket::decode, SyncBalancePacket::handle);

        CHANNEL.registerMessage(id++, SyncAllPricesPacket.class,
                SyncAllPricesPacket::encode, SyncAllPricesPacket::decode, SyncAllPricesPacket::handle);

        CHANNEL.registerMessage(id++, CloseScreenPacket.class,
                CloseScreenPacket::encode, CloseScreenPacket::decode,
                FMLEnvironment.dist.isClient() ? ClientPacketHandler::handleCloseScreen : (msg, ctx) -> ctx.get().setPacketHandled(true));

        CHANNEL.registerMessage(id++, ActionMessagePacket.class,
                ActionMessagePacket::encode, ActionMessagePacket::decode,
                FMLEnvironment.dist.isClient() ? ClientPacketHandler::handleActionMessage : (msg, ctx) -> ctx.get().setPacketHandled(true));

        CHANNEL.registerMessage(id++, ShopDataPacket.class,
                ShopDataPacket::encode, ShopDataPacket::decode,
                FMLEnvironment.dist.isClient() ? ClientPacketHandler::handleShopData : (msg, ctx) -> ctx.get().setPacketHandled(true));

        CHANNEL.registerMessage(id++, OpenShopScreenPacket.class,
                OpenShopScreenPacket::encode, OpenShopScreenPacket::decode,
                FMLEnvironment.dist.isClient() ? ClientPacketHandler::handleOpenShopScreen : (msg, ctx) -> ctx.get().setPacketHandled(true));

        CHANNEL.registerMessage(id++, SyncShopConfigPacket.class,
                SyncShopConfigPacket::encode, SyncShopConfigPacket::decode, SyncShopConfigPacket::handle);

        CHANNEL.registerMessage(id++, SyncBlacklistPacket.class,
                SyncBlacklistPacket::encode, SyncBlacklistPacket::decode, SyncBlacklistPacket::handle);

        CHANNEL.registerMessage(id++, SyncAdminEditPacket.class,
                SyncAdminEditPacket::encode, SyncAdminEditPacket::decode, SyncAdminEditPacket::handle);

        CHANNEL.registerMessage(id++, SyncAllShopEntriesPacket.class,
                SyncAllShopEntriesPacket::encode, SyncAllShopEntriesPacket::decode,
                FMLEnvironment.dist.isClient() ? ClientPacketHandler::handleSyncAllShopEntries : (msg, ctx) -> ctx.get().setPacketHandled(true));

        CHANNEL.registerMessage(id++, SyncQuestListPacket.class,
                SyncQuestListPacket::encode, SyncQuestListPacket::decode,
                FMLEnvironment.dist.isClient() ? ClientPacketHandler::handleSyncQuestList : (msg, ctx) -> ctx.get().setPacketHandled(true));

        CHANNEL.registerMessage(id++, PlayerShopDataPacket.class,
                PlayerShopDataPacket::encode, PlayerShopDataPacket::decode,
                FMLEnvironment.dist.isClient() ? ClientPacketHandler::handlePlayerShopData : (msg, ctx) -> ctx.get().setPacketHandled(true));

        CHANNEL.registerMessage(id++, OpenPlayerShopScreenPacket.class,
                OpenPlayerShopScreenPacket::encode, OpenPlayerShopScreenPacket::decode,
                FMLEnvironment.dist.isClient() ? ClientPacketHandler::handleOpenPlayerShopScreen : (msg, ctx) -> ctx.get().setPacketHandled(true));

        CHANNEL.registerMessage(id++, OpenPricingPacket.class,
                OpenPricingPacket::encode, OpenPricingPacket::decode,
                FMLEnvironment.dist.isClient() ? ClientPacketHandler::handleOpenPricing : (msg, ctx) -> ctx.get().setPacketHandled(true));

        // C→S packets
        CHANNEL.registerMessage(id++, SetPricesPacket.class,
                SetPricesPacket::encode, SetPricesPacket::decode, SetPricesPacket::handle);

        CHANNEL.registerMessage(id++, ShopOpenPacket.class,
                ShopOpenPacket::encode, ShopOpenPacket::decode, ShopOpenPacket::handle);

        CHANNEL.registerMessage(id++, TradePacket.class,
                TradePacket::encode, TradePacket::decode, TradePacket::handle);

        CHANNEL.registerMessage(id++, ShopEditPacket.class,
                ShopEditPacket::encode, ShopEditPacket::decode, ShopEditPacket::handle);

        CHANNEL.registerMessage(id++, PlayerShopOpenPacket.class,
                PlayerShopOpenPacket::encode, PlayerShopOpenPacket::decode, PlayerShopOpenPacket::handle);

        CHANNEL.registerMessage(id++, PlayerShopTradePacket.class,
                PlayerShopTradePacket::encode, PlayerShopTradePacket::decode, PlayerShopTradePacket::handle);

        CHANNEL.registerMessage(id++, PlayerShopNamePacket.class,
                PlayerShopNamePacket::encode, PlayerShopNamePacket::decode, PlayerShopNamePacket::handle);

        CHANNEL.registerMessage(id++, PlayerShopEditPacket.class,
                PlayerShopEditPacket::encode, PlayerShopEditPacket::decode, PlayerShopEditPacket::handle);

        CHANNEL.registerMessage(id++, RequestQuestListPacket.class,
                RequestQuestListPacket::encode, RequestQuestListPacket::decode, RequestQuestListPacket::handle);

        CHANNEL.registerMessage(id++, SetEntryQuestPacket.class,
                SetEntryQuestPacket::encode, SetEntryQuestPacket::decode, SetEntryQuestPacket::handle);
        CHANNEL.registerMessage(id++, SetEntryLimitPacket.class,
                SetEntryLimitPacket::encode, SetEntryLimitPacket::decode, SetEntryLimitPacket::handle);

        CHANNEL.registerMessage(id++, RequestBalancePacket.class,
                RequestBalancePacket::encode, RequestBalancePacket::decode, RequestBalancePacket::handle);

        SellBoxMod.LOGGER.info("SellBox network packets registered ({} packets)", id);
    }
}
