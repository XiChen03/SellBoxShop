package com.sellbox.mod.playershop.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.config.SellBoxConfig;
import com.sellbox.mod.network.ModPayloads;
import com.sellbox.mod.playershop.MarketBlockEntity;
import com.sellbox.mod.playershop.PlayerShopConfig;
import com.sellbox.mod.playershop.StockBlockEntity;
import com.sellbox.mod.shop.SellBoxEconomy;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PlayerShopTradePayload(
        UUID shopOwnerUUID,
        ResourceLocation itemId,
        int quantity,
        int pricePerUnit,
        BlockPos marketPos
) implements CustomPacketPayload {
    public static final Type<PlayerShopTradePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "player_shop_trade"));

    public static final StreamCodec<FriendlyByteBuf, PlayerShopTradePayload> CODEC = StreamCodec.composite(
            StreamCodec.of((buf, uuid) -> buf.writeUUID(uuid), buf -> buf.readUUID()), PlayerShopTradePayload::shopOwnerUUID,
            ResourceLocation.STREAM_CODEC, PlayerShopTradePayload::itemId,
            StreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt), PlayerShopTradePayload::quantity,
            StreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt), PlayerShopTradePayload::pricePerUnit,
            BlockPos.STREAM_CODEC, PlayerShopTradePayload::marketPos,
            PlayerShopTradePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerShopTradePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer buyer)) return;

            var level = buyer.level();
            if (!(level instanceof ServerLevel sl)) return;
            BlockEntity be = level.getBlockEntity(payload.marketPos());
            if (!(be instanceof MarketBlockEntity market)) return;

            var shopInfo = PlayerShopConfig.getShop(payload.shopOwnerUUID());
            ShopEntry entry = null;
            for (ShopEntry e : shopInfo.items()) {
                if (e.itemId().equals(payload.itemId()) && e.price() == payload.pricePerUnit()) {
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

            // 解析卖家市场（当点击的市场不是卖家市场时，跨维度按注册表坐标查找）
            MarketBlockEntity sellerMarket = market;
            if (!payload.shopOwnerUUID().equals(market.getOwner())) {
                sellerMarket = resolveMarket(sl, payload.shopOwnerUUID());
                if (sellerMarket == null) {
                    ModPayloads.sendCloseScreen(buyer, "message.sellbox.not_owner");
                    return;
                }
            }

            // 以物易物分支
            if ("barter".equals(entry.entryType()) && entry.barterItemId() != null) {
                handleBarter(payload, buyer, sellerMarket, entry, shopInfo);
                return;
            }

            // 普通金币交易分支
            long totalPriceLong = (long) payload.pricePerUnit() * (long) payload.quantity();
            if (totalPriceLong <= 0 || totalPriceLong > Integer.MAX_VALUE) return;
            int totalPrice = (int) totalPriceLong;

            if (entry.tradeType() == ShopEntry.TradeType.SELL) {
                handleSell(payload, buyer, sellerMarket, entry, shopInfo, totalPrice);
            } else {
                handleBuy(payload, buyer, sellerMarket, entry, shopInfo, totalPrice);
            }
        });
    }

    // 以物易物：买家付出价物（背包），获得商品物（从卖家9x9x9内的备货台取出），出价物自动存入卖家市场方块（0-51槽，不占用收据/钱袋），塞不下转入卖家的备货台
    private static void handleBarter(PlayerShopTradePayload payload, ServerPlayer buyer, MarketBlockEntity market, ShopEntry entry, PlayerShopConfig.PlayerShopInfo shopInfo) {
        int offerQtyPerUnit = Math.max(1, entry.barterItemQty());
        int goodsQtyPerUnit = Math.max(1, entry.quantity());
        int r = 4;
        BlockPos marketPos = market.getBlockPos();
        var level = buyer.level();
        RegistryAccess access = level.registryAccess();

        // 1. 模板（严格NBT模式：出价物=玩家需支付的带NBT物品，商品物=玩家获得的带NBT物品）
        ItemStack offerTemplate = entry.createBarterOfferStack(access);
        if (offerTemplate.isEmpty()) return;
        ItemStack goodsTemplate = entry.createBarterGoodsStack(access);
        net.minecraft.world.item.Item goodsItem = goodsTemplate.getItem();
        if (goodsItem == null || goodsItem == net.minecraft.world.item.Items.AIR) return;

        // 2. 统计买家出价物可用数量（背包）
        long needOfferLong = (long) offerQtyPerUnit * (long) payload.quantity();
        if (needOfferLong <= 0 || needOfferLong > Integer.MAX_VALUE) return;
        int availableOffer = 0;
        for (ItemStack stack : buyer.getInventory().items) {
            if (entry.matchesOffer(stack, access)) availableOffer += stack.getCount();
        }
        int maxOfferUnits = availableOffer / offerQtyPerUnit;
        if (maxOfferUnits <= 0) {
            
            ModPayloads.sendCloseScreen(buyer, "message.sellbox.not_enough_items");
            return;
        }

        // 3. 统计卖家备货台商品物可用数量（已加载区块）
        List<StockBlockEntity> ownerStocks = null;
        if (level instanceof ServerLevel sl) {
            ownerStocks = collectOwnerStocks(sl, payload.shopOwnerUUID(), marketPos);
        }
        if (ownerStocks == null) {
            
            ModPayloads.sendCloseScreen(buyer, "message.sellbox.not_enough_stock");
            return;
        }
        int availableGoods = 0;
        for (StockBlockEntity stock : ownerStocks) {
            for (int i = 0; i < stock.getContainerSize(); i++) {
                ItemStack stack = stock.getItem(i);
                if (entry.matchesBarterGoods(stack, access)) availableGoods += stack.getCount();
            }
        }
        for (int i = 0; i < MarketBlockEntity.RECEIPT_BOOK_SLOT; i++) {
            ItemStack stack = market.getItem(i);
            if (entry.matchesBarterGoods(stack, access)) availableGoods += stack.getCount();
        }
        int maxGoodsUnits = availableGoods / goodsQtyPerUnit;
        if (maxGoodsUnits <= 0) {
            
            ModPayloads.sendCloseScreen(buyer, "message.sellbox.not_enough_stock");
            return;
        }

        // 4. 实际交易组数取可用量与请求量的较小值，不超过 payload.quantity
        int actualUnits = Math.min(payload.quantity(), Math.min(maxOfferUnits, maxGoodsUnits));
        if (actualUnits <= 0) return;
        long needOfferActualLong = (long) offerQtyPerUnit * actualUnits;
        long needGoodsActualLong = (long) goodsQtyPerUnit * actualUnits;
        if (needOfferActualLong > Integer.MAX_VALUE || needGoodsActualLong > Integer.MAX_VALUE) return;
        int needOfferActual = (int) needOfferActualLong;
        int needGoodsActual = (int) needGoodsActualLong;

        // 5. 精确从卖家备货台 + 市场取出商品物 needGoodsActual 个
        int toTakeGoods = needGoodsActual;
        for (StockBlockEntity stock : ownerStocks) {
            for (int i = 0; i < stock.getContainerSize() && toTakeGoods > 0; i++) {
                ItemStack stack = stock.getItem(i);
                if (entry.matchesBarterGoods(stack, access)) {
                    int take = Math.min(toTakeGoods, stack.getCount());
                    stock.removeItem(i, take);
                    toTakeGoods -= take;
                }
            }
        }
        if (toTakeGoods > 0) {
            for (int i = 0; i < MarketBlockEntity.RECEIPT_BOOK_SLOT && toTakeGoods > 0; i++) {
                ItemStack stack = market.getItem(i);
                if (entry.matchesBarterGoods(stack, access)) {
                    int take = Math.min(toTakeGoods, stack.getCount());
                    market.removeItem(i, take);
                    toTakeGoods -= take;
                }
            }
        }
        // 若实际取走的商品物 < needGoodsActual（理论不应发生，但保留兜底）
        // 把已取出的商品物原样放回卖家的备货台（堆叠到匹配槽，否则进入空槽），不损失物品
        if (toTakeGoods > 0 && level instanceof ServerLevel sl) {
            int taken = needGoodsActual - toTakeGoods;
            ItemStack toReturn = goodsTemplate.copyWithCount(taken);
            int toPlace = taken;
            for (StockBlockEntity stock : collectOwnerStocks(sl, payload.shopOwnerUUID(), marketPos)) {
                for (int i = 0; i < stock.getContainerSize() && toPlace > 0; i++) {
                    ItemStack slotItem = stock.getItem(i);
                    if (slotItem.isEmpty()) {
                        int add = Math.min(toPlace, Math.max(1, toReturn.getMaxStackSize()));
                        stock.setItem(i, toReturn.copyWithCount(add));
                        toPlace -= add;
                    } else if (ItemStack.isSameItemSameComponents(slotItem, toReturn)) {
                        int canAdd = Math.min(Math.max(0, toReturn.getMaxStackSize() - slotItem.getCount()), toPlace);
                        if (canAdd > 0) {
                            slotItem.grow(canAdd);
                            stock.setItem(i, slotItem);
                            toPlace -= canAdd;
                        }
                    }
                }
            }
            return;
        }
        int actualGivenGoods = needGoodsActual;

        // 6. 扣买家背包出价物 needOfferActual 个
        int toRemoveOffer = needOfferActual;
        for (ItemStack stack : buyer.getInventory().items) {
            if (toRemoveOffer <= 0) break;
            if (entry.matchesOffer(stack, access)) {
                int rm = Math.min(toRemoveOffer, stack.getCount());
                stack.shrink(rm);
                toRemoveOffer -= rm;
            }
        }
        int actualGiveOffer = needOfferActual - toRemoveOffer;
        if (actualGiveOffer <= 0) return;

        // 7. 发商品物给买家
        ItemStack goodsAsStack = goodsTemplate.copy();
        if (!entry.customName().isEmpty()) {
            goodsAsStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal(entry.customName()));
        }
        int maxGoodsStack = goodsAsStack.getMaxStackSize();
        int toGiveGoods = actualGivenGoods;
        while (toGiveGoods > 0) {
            int chunk = Math.min(toGiveGoods, maxGoodsStack);
            buyer.getInventory().placeItemBackInInventory(goodsAsStack.copyWithCount(chunk));
            toGiveGoods -= chunk;
        }

        // 8. 出价物入卖家市场方块槽 0-51（避开 RECEIPT_BOOK_SLOT=52 与 BAG_SLOT=53）
        int toDeposit = actualGiveOffer;
        int offerMaxStack = offerTemplate.getMaxStackSize();
        if (offerTemplate.getCount() != 1) offerTemplate = offerTemplate.copyWithCount(1);
        ItemStack cleanOffer = offerTemplate.copy(); // 标准单件模板，不带 custom_name
        // 不附加 custom_name
        for (int i = 0; i < MarketBlockEntity.RECEIPT_BOOK_SLOT /* 52 */ && toDeposit > 0; i++) {
            ItemStack slot = market.getItem(i);
            if (slot.isEmpty()) {
                int add = Math.min(toDeposit, offerMaxStack);
                market.setItem(i, cleanOffer.copyWithCount(add));
                toDeposit -= add;
            } else if (ItemStack.isSameItemSameComponents(slot, cleanOffer)) {
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

        // 9. 市场塞不下的转入卖家9x9x9备货台
        for (int dx = -r; dx <= r && toDeposit > 0; dx++) {
            for (int dy = -r; dy <= r && toDeposit > 0; dy++) {
                for (int dz = -r; dz <= r && toDeposit > 0; dz++) {
                    BlockPos pos = marketPos.offset(dx, dy, dz);
                    BlockEntity stockBe = level.getBlockEntity(pos);
                    if (stockBe instanceof StockBlockEntity stock) {
                        if (!payload.shopOwnerUUID().equals(stock.getOwner())) continue;
                        for (int i = 0; i < stock.getContainerSize() && toDeposit > 0; i++) {
                            ItemStack existing = stock.getItem(i);
                            if (existing.isEmpty()) {
                                int add = Math.min(toDeposit, offerMaxStack);
                                stock.setItem(i, cleanOffer.copyWithCount(add));
                                toDeposit -= add;
                            } else if (ItemStack.isSameItemSameComponents(existing, cleanOffer)) {
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

        // 10. 若备货台也塞不下、卖家在线则塞其背包；否则丢到市场任意槽（已塞不上，少见，丢弃在市场方块所以不丢服务器内存）
        if (toDeposit > 0) {
            ServerPlayer shopOwner = buyer.getServer().getPlayerList().getPlayer(payload.shopOwnerUUID());
            if (shopOwner != null) {
                while (toDeposit > 0) {
                    int chunk = Math.min(toDeposit, offerMaxStack);
                    shopOwner.getInventory().placeItemBackInInventory(cleanOffer.copyWithCount(chunk));
                    toDeposit -= chunk;
                }
            }
            // 仍塞不下（保守：丢在市场任意空槽或转回买家），不再走 depositEarnedItem 以避免占用收据/钱袋
            // 实际几乎不会发生，直接将剩余留给市场 attempt：因为上面已经穷举了 0-51
            // 此处交给买家背包最稳妥
            while (toDeposit > 0) {
                int chunk = Math.min(toDeposit, offerMaxStack);
                buyer.getInventory().placeItemBackInInventory(cleanOffer.copyWithCount(chunk));
                toDeposit -= chunk;
            }
        }

        // 11. 退还买家多余的出价物（不应有剩余，因为已做精确取整；保留兜底）
        // (实际Give等于needOfferActual，无多余)

        // 12. 写收据
        String offerName = cleanOffer.getHoverName().getString();
        String goodsName = entry.getDisplayName().getString();
        String shopName = getShopDisplayName(payload, shopInfo, market);
        String receiptText = String.format("%s 在 %s 用 %s x%d 换取了 %s x%d",
                buyer.getName().getString(), shopName, offerName, actualGiveOffer, goodsName, actualGivenGoods);
        market.writeReceipt(buyer.getName().getString(), receiptText);
        
        ModPayloads.sendCloseScreen(buyer, "message.sellbox.trade_success");
    }

    private static void handleSell(PlayerShopTradePayload payload, ServerPlayer buyer, MarketBlockEntity market, ShopEntry entry, PlayerShopConfig.PlayerShopInfo shopInfo, int totalPrice) {
        BlockPos sellMarketPos = market.getBlockPos();
        int qtyPerUnit = Math.max(1, entry.quantity());
        int maxUnits = payload.quantity();
        RegistryAccess access = buyer.level().registryAccess();

        // 1. 统计可用库存（备货台 + 市场自身；严格匹配时需 ID + NBT 完全一致）
        int available = 0;
        if (buyer.level() instanceof ServerLevel sl) {
            for (StockBlockEntity stock : collectOwnerStocks(sl, payload.shopOwnerUUID(), sellMarketPos)) {
                for (int i = 0; i < stock.getContainerSize(); i++) {
                    ItemStack stack = stock.getItem(i);
                    if (!stack.isEmpty() && entry.matchesStack(stack, access)) {
                        available += stack.getCount();
                    }
                }
            }
            for (int i = 0; i < MarketBlockEntity.RECEIPT_BOOK_SLOT; i++) {
                ItemStack stack = market.getItem(i);
                if (!stack.isEmpty() && entry.matchesStack(stack, access)) {
                    available += stack.getCount();
                }
            }
        }

        int actualUnits = Math.min(maxUnits, available / qtyPerUnit);
        if (actualUnits <= 0) {
            
            ModPayloads.sendCloseScreen(buyer, "message.sellbox.not_enough_stock");
            return;
        }

        int needed = actualUnits * qtyPerUnit;

        // 2. 扣钱
        if (!SellBoxEconomy.tryDeductBalance(buyer, totalPrice)) {
            
            ModPayloads.sendCloseScreen(buyer, "message.sellbox.not_enough_money");
            return;
        }
        ModPayloads.sendBalanceToPlayer(buyer);

        // 3. 如果实际购买单位少于请求，退差价
        int refundUnits = maxUnits - actualUnits;
        if (refundUnits > 0) {
            int refund = payload.pricePerUnit() * refundUnits;
            SellBoxEconomy.addBalance(buyer, refund);
            ModPayloads.sendBalanceToPlayer(buyer);
        }

        // 4. 精准从备货台 + 市场取物
        int remaining = needed;
        if (buyer.level() instanceof ServerLevel sl) {
            for (StockBlockEntity stock : collectOwnerStocks(sl, payload.shopOwnerUUID(), sellMarketPos)) {
                for (int i = 0; i < stock.getContainerSize() && remaining > 0; i++) {
                    ItemStack stack = stock.getItem(i);
                    if (!stack.isEmpty() && entry.matchesStack(stack, access)) {
                        int take = Math.min(remaining, stack.getCount());
                        stock.removeItem(i, take);
                        remaining -= take;
                    }
                }
            }
            if (remaining > 0) {
                for (int i = 0; i < MarketBlockEntity.RECEIPT_BOOK_SLOT && remaining > 0; i++) {
                    ItemStack stack = market.getItem(i);
                    if (!stack.isEmpty() && entry.matchesStack(stack, access)) {
                        int take = Math.min(remaining, stack.getCount());
                        market.removeItem(i, take);
                        remaining -= take;
                    }
                }
            }
        }

        int actualGiven = needed - remaining;

        // 5. 给买家物品（严格匹配时带 NBT）
        ItemStack template = entry.createStack(access);
        if (!entry.customName().isEmpty()) {
            template.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal(entry.customName()));
        }
        int maxStack = template.getMaxStackSize();
        int toGive = actualGiven;
        while (toGive > 0) {
            int chunk = Math.min(toGive, maxStack);
            buyer.getInventory().placeItemBackInInventory(template.copyWithCount(chunk));
            toGive -= chunk;
        }

        // 6. 付款给店主
        int ownerPayout = payload.pricePerUnit() * actualUnits;
        ServerPlayer shopOwner = buyer.getServer().getPlayerList().getPlayer(payload.shopOwnerUUID());
        if (shopOwner != null) {
            SellBoxEconomy.addBalance(shopOwner, ownerPayout);
            ModPayloads.sendBalanceToPlayer(shopOwner);
        } else {
            SellBoxConfig.addPendingBalance(payload.shopOwnerUUID(), ownerPayout);
        }

        // 7. 收据
        String itemName = entry.getDisplayName().getString();
        String shopName = getShopDisplayName(payload, shopInfo, market);
        String receiptText = String.format("%s 从 %s 购买了 %s x%d，消费 %d 金币",
                buyer.getName().getString(), shopName, itemName, actualUnits, ownerPayout);
        market.writeReceipt(buyer.getName().getString(), receiptText);
        PlayerShopConfig.addEarnings(payload.shopOwnerUUID(), ownerPayout, actualUnits);
        
        ModPayloads.sendCloseScreen(buyer, "message.sellbox.buy_success");
    }

    private static void handleBuy(PlayerShopTradePayload payload, ServerPlayer seller, MarketBlockEntity market, ShopEntry entry, PlayerShopConfig.PlayerShopInfo shopInfo, int totalPrice) {
        int needed = payload.quantity() * Math.max(1, entry.quantity());
        RegistryAccess access = seller.level().registryAccess();

        int available = 0;
        for (ItemStack stack : seller.getInventory().items) {
            if (!stack.isEmpty() && entry.matchesStack(stack, access)) {
                available += stack.getCount();
            }
        }
        int toSell = Math.min(needed, available);
        if (toSell <= 0) {
            
            ModPayloads.sendCloseScreen(seller, "message.sellbox.not_enough_items");
            return;
        }

        int qtyPerUnit = Math.max(1, entry.quantity());
        int actualUnits = toSell / qtyPerUnit;
        int sellerPayout = payload.pricePerUnit() * actualUnits;

        ServerPlayer shopOwner = seller.getServer().getPlayerList().getPlayer(payload.shopOwnerUUID());
        boolean ownerCanPay;
        if (shopOwner != null) {
            ownerCanPay = SellBoxEconomy.tryDeductBalance(shopOwner, sellerPayout);
            if (ownerCanPay) ModPayloads.sendBalanceToPlayer(shopOwner);
        } else {
            ownerCanPay = SellBoxConfig.tryDeductPendingBalance(payload.shopOwnerUUID(), sellerPayout);
        }

        if (!ownerCanPay) {
            
            ModPayloads.sendCloseScreen(seller, "message.sellbox.not_enough_money");
            return;
        }

        int toRemove = toSell;
        for (ItemStack stack : seller.getInventory().items) {
            if (toRemove <= 0) break;
            if (!stack.isEmpty() && entry.matchesStack(stack, access)) {
                int r = Math.min(toRemove, stack.getCount());
                stack.shrink(r);
                toRemove -= r;
            }
        }

        SellBoxEconomy.addBalance(seller, sellerPayout);
        ModPayloads.sendBalanceToPlayer(seller);

        ItemStack deposited = entry.createStack(access);
        deposited.setCount(toSell);
        market.depositEarnedItem(deposited);

        String itemName = entry.getDisplayName().getString();
        String shopName = getShopDisplayName(payload, shopInfo, market);
        String receiptText = String.format("%s 向 %s 出售了 %s x%d，获得 %d 金币",
                seller.getName().getString(), shopName, itemName, actualUnits, sellerPayout);
        market.writeReceipt(seller.getName().getString(), receiptText);
        
        ModPayloads.sendCloseScreen(seller, "message.sellbox.sell_success");
    }

    private static MarketBlockEntity resolveMarket(ServerLevel sl, UUID ownerUuid) {
        // 从全局注册表获取卖家市场坐标，跨维度检查所在区块是否加载（与 1.20.1 一致）
        BlockPos sellerPos = MarketBlockEntity.getMarketPos(ownerUuid);
        if (sellerPos == null) return null;
        for (ServerLevel level : sl.getServer().getAllLevels()) {
            if (!level.hasChunk(sellerPos.getX() >> 4, sellerPos.getZ() >> 4)) continue;
            BlockEntity be = level.getBlockEntity(sellerPos);
            if (be instanceof MarketBlockEntity m && ownerUuid.equals(m.getOwner())) {
                return m;
            }
        }
        return null;
    }

    private static String getShopDisplayName(PlayerShopTradePayload payload, PlayerShopConfig.PlayerShopInfo shopInfo, MarketBlockEntity market) {
        if (shopInfo.shopName() != null && !shopInfo.shopName().isEmpty()) {
            return shopInfo.shopName();
        }
        return market.getDisplayName().getString();
    }

    private static List<StockBlockEntity> collectOwnerStocks(ServerLevel sl, UUID ownerUuid, BlockPos marketPos) {
        List<StockBlockEntity> result = new ArrayList<>();
        int mcx = marketPos.getX() >> 4;
        int mcz = marketPos.getZ() >> 4;
        // 只扫描 Market 自身所在的 1 个 chunk（与 refreshStockCache 一致）
        if (!sl.hasChunk(mcx, mcz)) return result;
        LevelChunk chunk = sl.getChunk(mcx, mcz);
        if (chunk == null) return result;
        for (var be : chunk.getBlockEntities().values()) {
            if (be instanceof StockBlockEntity stock && ownerUuid.equals(stock.getOwner())) {
                result.add(stock);
            }
        }
        return result;
    }
}
