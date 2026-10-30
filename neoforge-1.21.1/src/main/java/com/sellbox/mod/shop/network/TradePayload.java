package com.sellbox.mod.shop.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.network.ModPayloads;
import com.sellbox.mod.shop.SellBoxEconomy;
import com.sellbox.mod.shop.ShopConfig;
import com.sellbox.mod.shop.ShopEntry;
import com.sellbox.mod.shop.TradeLimitTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record TradePayload(
        String category,
        ResourceLocation itemId,
        ShopEntry.TradeType tradeType,
        int quantity,
        int totalPrice,
        @Nullable String nbt
) implements CustomPacketPayload {
    public static final Type<TradePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "trade"));

    private static final StreamCodec<FriendlyByteBuf, String> NULLABLE_STRING = StreamCodec.of(
            (buf, s) -> buf.writeUtf(s != null ? s : ""),
            buf -> {
                String s = buf.readUtf();
                return s.isEmpty() ? null : s;
            });

    public static final StreamCodec<FriendlyByteBuf, TradePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TradePayload::category,
            ResourceLocation.STREAM_CODEC, TradePayload::itemId,
            ByteBufCodecs.fromCodec(ShopEntry.TradeType.CODEC), TradePayload::tradeType,
            StreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt), TradePayload::quantity,
            StreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt), TradePayload::totalPrice,
            NULLABLE_STRING, TradePayload::nbt,
            TradePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TradePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ShopEntry entry = null;
                List<ShopEntry> entries = ShopConfig.getCategory(payload.category());
                if (entries != null) {
                    int price = payload.totalPrice() / payload.quantity();
                    for (ShopEntry e : entries) {
                        // NBT 校验：客户端传了 NBT 必须与服务端严格条目一致；没传 NBT 只能命中非严格条目（防作弊：传 null 不能绕过严格条目的 NBT 匹配）
                        boolean nbtMatch = (payload.nbt() == null)
                                ? (e.nbt() == null)
                                : (e.strictMatch() && payload.nbt().equals(e.nbt()));
                        if (e.itemId().equals(payload.itemId())
                                && e.tradeType() == payload.tradeType()
                                && e.price() == price
                                && nbtMatch) {
                            entry = e;
                            break;
                        }
                    }
                }
                if (entry == null && payload.nbt() == null) {
                    // 兜底：仅当该物品/类型没有任何严格条目时才允许按 ID 交易（未配置物品），否则拒绝
                    boolean hasStrictSameItem = false;
                    if (entries != null) {
                        for (ShopEntry e : entries) {
                            if (e.itemId().equals(payload.itemId()) && e.tradeType() == payload.tradeType() && e.strictMatch()) {
                                hasStrictSameItem = true;
                                break;
                            }
                        }
                    }
                    if (!hasStrictSameItem) {
                        entry = new ShopEntry(payload.itemId(), payload.tradeType(), payload.totalPrice() / payload.quantity());
                    }
                }
                if (entry == null) {
                    ModPayloads.sendCloseScreen(serverPlayer, "message.sellbox.trade_not_found");
                    return;
                }
                executeTrade(serverPlayer, payload.category(), entry, payload.quantity());
            }
        });
    }

    /** 限量条目成交后重新下发商店数据，让客户端把额度转红/置灰 */
    private static void resendShopData(ServerPlayer sp, String category) {
        var entries = ShopConfig.getCategory(category);
        if (entries == null) return;
        var cats = ShopConfig.getCategoryNames();
        for (ServerPlayer p : sp.getServer().getPlayerList().getPlayers()) {
            var target = entries;
            if (!ShopConfig.isEditMode() && com.sellbox.mod.integration.FTBQuestsIntegration.isLoaded()) {
                target = entries.stream()
                        .filter(e -> !e.hasPrerequisite()
                                || com.sellbox.mod.integration.FTBQuestsIntegration.isQuestCompleted(p, e.requiredQuestId()))
                        .toList();
            }
            PacketDistributor.sendToPlayer(p, new ShopDataPayload(category, target,
                    SellBoxEconomy.getBalance(p), cats,
                    TradeLimitTracker.snapshotUsed(target, category, p.getUUID())));
        }
    }

    /** 记一笔成交并按需刷新客户端额度显示 */
    private static void settle(ServerPlayer sp, String category, ShopEntry entry, int times) {
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
                ModPayloads.sendCloseScreen(player, remaining <= 0
                        ? "message.sellbox.trade_limit_reached"
                        : "message.sellbox.trade_limit_remaining");
                return;
            }
        }

        if ("command".equals(entry.entryType())) {
            if (!SellBoxEconomy.tryDeductBalance(player, totalPrice)) {
                
                ModPayloads.sendCloseScreen(player, "message.sellbox.not_enough_money");
                return;
            }
            ModPayloads.sendBalanceToPlayer(player);
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
            ModPayloads.sendCloseScreen(player, "message.sellbox.trade_success");
            return;
        }

        if ("xp".equals(entry.entryType())) {
            if (!SellBoxEconomy.tryDeductBalance(player, totalPrice)) {
                
                ModPayloads.sendCloseScreen(player, "message.sellbox.not_enough_money");
                return;
            }
            ModPayloads.sendBalanceToPlayer(player);
            int xpPerBottle;
            try { xpPerBottle = Integer.parseInt(entry.extraData()); } catch (NumberFormatException e) { return; }
            player.giveExperiencePoints(xpPerBottle * quantity);
            settle(player, category, entry, quantity);
            ModPayloads.sendCloseScreen(player, "message.sellbox.buy_success");
            return;
        }

        // 以物易物：扣出价物（玩家背包），发商品物（管理员生成）
        if ("barter".equals(entry.entryType()) && entry.barterItemId() != null) {
            int offerQtyPerUnit = Math.max(1, entry.barterItemQty());
            int goodsQtyPerUnit = Math.max(1, entry.quantity());
            int needOffer = offerQtyPerUnit * quantity;
            ItemStack offerTemplate = entry.createBarterOfferStack(player.registryAccess());
            Item offerItem = offerTemplate.getItem();
            // 统计玩家出价物的可用数量（严格匹配时需 ID + NBT 完全一致）
            int available = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (entry.matchesOffer(stack, player.registryAccess())) available += stack.getCount();
            }
            int actualOffer = Math.min(needOffer, available);
            if (actualOffer <= 0) {
                
                ModPayloads.sendCloseScreen(player, "message.sellbox.not_enough_items");
                return;
            }
            // 按组对齐（每组 offerQtyPerUnit 个出价物对应一组交易）
            int actualUnits = actualOffer / offerQtyPerUnit;
            int actualGiveOffer = actualUnits * offerQtyPerUnit;
            // 扣取出价物
            int toRemove = actualGiveOffer;
            for (ItemStack stack : player.getInventory().items) {
                if (toRemove <= 0) break;
                if (entry.matchesOffer(stack, player.registryAccess())) {
                    int r = Math.min(toRemove, stack.getCount());
                    stack.shrink(r);
                    toRemove -= r;
                }
            }
            // 发商品物（保留上架时选中的 NBT）
            ItemStack goodsStack = entry.createBarterGoodsStack(player.registryAccess());
            if (!entry.customName().isEmpty()) {
                goodsStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal(entry.customName()));
            }
            int maxGoodsStack = goodsStack.getMaxStackSize();
            int toGiveGoods = actualUnits * goodsQtyPerUnit;
            while (toGiveGoods > 0) {
                int chunk = Math.min(toGiveGoods, maxGoodsStack);
                player.getInventory().placeItemBackInInventory(goodsStack.copyWithCount(chunk));
                toGiveGoods -= chunk;
            }
            // 退还多余的出价物（按 offer 单位未对齐的部分）
            int excessOffer = actualOffer - actualGiveOffer;
            if (excessOffer > 0) {
                player.getInventory().placeItemBackInInventory(offerTemplate.copyWithCount(excessOffer));
            }
            
            settle(player, category, entry, actualUnits);
            ModPayloads.sendCloseScreen(player, "message.sellbox.barter_success");
            return;
        }

        if (entry.tradeType() == ShopEntry.TradeType.BUY) {
            if (!SellBoxEconomy.tryDeductBalance(player, totalPrice)) {
                
                ModPayloads.sendCloseScreen(player, "message.sellbox.not_enough_money");
                return;
            }
            ModPayloads.sendBalanceToPlayer(player);
            ItemStack template = entry.createStack(player.registryAccess());
            int totalItems = quantity * entry.quantity();
            if (!entry.customName().isEmpty()) {
                template.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal(entry.customName()));
            }
            int maxStack = template.getMaxStackSize();
            while (totalItems > 0) {
                int chunk = Math.min(totalItems, maxStack);
                player.getInventory().placeItemBackInInventory(template.copyWithCount(chunk));
                totalItems -= chunk;
            }
            
            settle(player, category, entry, quantity);
            ModPayloads.sendCloseScreen(player, "message.sellbox.buy_success");
        } else if (entry.tradeType() == ShopEntry.TradeType.SELL) {
            int qtyPerUnit = Math.max(1, entry.quantity());
            int needed = quantity * qtyPerUnit;
            int available = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (entry.matchesStack(stack, player.registryAccess())) {
                    available += stack.getCount();
                }
            }
            int toSell = Math.min(needed, available);
            if (toSell <= 0) {
                
                ModPayloads.sendCloseScreen(player, "message.sellbox.not_enough_items");
                return;
            }
            int toRemove = toSell;
            for (ItemStack stack : player.getInventory().items) {
                if (toRemove <= 0) break;
                if (entry.matchesStack(stack, player.registryAccess())) {
                    int r = Math.min(toRemove, stack.getCount());
                    stack.shrink(r);
                    toRemove -= r;
                }
            }
            // 价格按「组」计算：每 entry.quantity() 个物品 = entry.price() 金币
            int actualQty = toSell / qtyPerUnit;
            int earned = entry.price() * actualQty;
            if (earned > 0) {
                SellBoxEconomy.addBalance(player, earned);
                ModPayloads.sendBalanceToPlayer(player);
            }
            
            settle(player, category, entry, actualQty);
            ModPayloads.sendCloseScreen(player, "message.sellbox.sell_success");
        }
    }
}
