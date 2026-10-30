package com.sellbox.mod.shop.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.shop.SellBoxEconomy;
import com.sellbox.mod.shop.ShopConfig;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

public record ShopEditPayload(
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
        @Nullable String barterItemId,
        int barterItemQty,
        boolean strictMatch,
        @Nullable String nbt,
        boolean barterOfferStrict,
        @Nullable String barterOfferNbt,
        boolean barterGoodsStrict
) implements CustomPacketPayload {
    public static final Type<ShopEditPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "shop_edit"));

    public ShopEditPayload(String action, String category, String extraData, String tradeType, int price, int index,
                           boolean requireOp, String icon, int quantity, String customName) {
        this(action, category, extraData, tradeType, price, index, requireOp, icon, quantity, customName, null, 1, false, null, false, null, false);
    }

    public ShopEditPayload(String action, String category, String extraData, String tradeType, int price, int index,
                           boolean requireOp, String icon, int quantity, String customName,
                           @Nullable String barterItemId, int barterItemQty) {
        this(action, category, extraData, tradeType, price, index, requireOp, icon, quantity, customName, barterItemId, barterItemQty, false, null, false, null, false);
    }

    public ShopEditPayload(String action, String category, String extraData, String tradeType, int price, int index,
                           boolean requireOp, String icon, int quantity, String customName,
                           @Nullable String barterItemId, int barterItemQty,
                           boolean strictMatch, @Nullable String nbt) {
        this(action, category, extraData, tradeType, price, index, requireOp, icon, quantity, customName, barterItemId, barterItemQty, strictMatch, nbt, false, null, false);
    }

    public ShopEditPayload(String action, String category, String extraData, String tradeType, int price, int index,
                           boolean requireOp, String icon, int quantity, String customName,
                           @Nullable String barterItemId, int barterItemQty,
                           boolean strictMatch, @Nullable String nbt,
                           boolean barterOfferStrict, @Nullable String barterOfferNbt) {
        this(action, category, extraData, tradeType, price, index, requireOp, icon, quantity, customName, barterItemId, barterItemQty, strictMatch, nbt, barterOfferStrict, barterOfferNbt, false);
    }

    public static final StreamCodec<FriendlyByteBuf, ShopEditPayload> CODEC = new StreamCodec<>() {
        @Override
        public ShopEditPayload decode(FriendlyByteBuf buf) {
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
            boolean strictMatch = buf.readBoolean();
            String nbt = buf.readUtf();
            boolean barterOfferStrict = buf.readBoolean();
            String barterOfferNbt = buf.readUtf();
            boolean barterGoodsStrict = buf.readBoolean();
            String realBarter = barterItemId.isEmpty() ? null : barterItemId;
            String realNbt = nbt.isEmpty() ? null : nbt;
            String realOfferNbt = barterOfferNbt.isEmpty() ? null : barterOfferNbt;
            return new ShopEditPayload(action, category, extraData, tradeType, price, index, requireOp, icon, quantity, customName, realBarter, barterItemQty, strictMatch, realNbt, barterOfferStrict, realOfferNbt, barterGoodsStrict);
        }

        @Override
        public void encode(FriendlyByteBuf buf, ShopEditPayload value) {
            buf.writeUtf(value.action);
            buf.writeUtf(value.category);
            buf.writeUtf(value.extraData);
            buf.writeUtf(value.tradeType);
            buf.writeVarInt(value.price);
            buf.writeVarInt(value.index);
            buf.writeBoolean(value.requireOp);
            buf.writeUtf(value.icon);
            buf.writeVarInt(value.quantity);
            buf.writeUtf(value.customName);
            buf.writeUtf(value.barterItemId != null ? value.barterItemId : "");
            buf.writeVarInt(value.barterItemQty);
            buf.writeBoolean(value.strictMatch);
            buf.writeUtf(value.nbt != null ? value.nbt : "");
            buf.writeBoolean(value.barterOfferStrict);
            buf.writeUtf(value.barterOfferNbt != null ? value.barterOfferNbt : "");
            buf.writeBoolean(value.barterGoodsStrict);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShopEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || !player.hasPermissions(2)) return;

            String resultCategory = payload.category();

            switch (payload.action()) {
                case "add_category" -> {
                    ShopConfig.addCategory(payload.extraData());
                    resultCategory = payload.extraData();
                }
                case "remove_category" -> ShopConfig.removeCategory(payload.category());
                case "rename_category" -> {
                    ShopConfig.renameCategory(payload.category(), payload.extraData());
                    resultCategory = payload.extraData();
                }
                case "move_category_up" -> ShopConfig.moveCategoryUp(payload.category());
                case "move_category_down" -> ShopConfig.moveCategoryDown(payload.category());
                case "add_item" -> {
                    ResourceLocation id = ResourceLocation.tryParse(payload.extraData());
                    if (id != null) {
                        ShopEntry.TradeType type = "sell".equalsIgnoreCase(payload.tradeType()) ? ShopEntry.TradeType.SELL : ShopEntry.TradeType.BUY;
                        int qty = payload.quantity() > 0 ? payload.quantity() : 1;
                        ShopConfig.addItem(payload.category(), new ShopEntry(id, type, payload.price(), "item", "", false, qty, payload.customName(), null, 1, null, null, payload.strictMatch(), payload.nbt()));
                    }
                }
                case "edit_item" -> {
                    ResourceLocation id = ResourceLocation.tryParse(payload.extraData());
                    if (id != null) {
                        ShopEntry.TradeType type = "sell".equalsIgnoreCase(payload.tradeType()) ? ShopEntry.TradeType.SELL : ShopEntry.TradeType.BUY;
                        int qty = payload.quantity() > 0 ? payload.quantity() : 1;
                        ShopConfig.updateItem(payload.category(), payload.index(), new ShopEntry(id, type, payload.price(), "item", "", false, qty, payload.customName(), null, 1, null, null, payload.strictMatch(), payload.nbt()));
                    }
                }
                case "add_barter_item", "edit_barter_item" -> {
                    ResourceLocation goodsId = ResourceLocation.tryParse(payload.extraData());
                    ResourceLocation offerId = payload.barterItemId() != null ? ResourceLocation.tryParse(payload.barterItemId()) : null;
                    if (goodsId != null && offerId != null) {
                        int goodsQty = payload.price() > 0 ? payload.price() : 1;
                        int offerQty = payload.quantity() > 0 ? payload.quantity() : 1;
                        // ShopEntry: itemId=goodsId(商品物), price=0(无意义), entryType="barter", quantity=goodsQty(商品数量),
                        // barterItemId=offerId(出价物), barterItemQty=offerQty(出价数量),
                        // nbt=商品物NBT, barterOfferStrict/barterOfferNbt=出价物严格匹配, barterGoodsStrict=商品物严格匹配
                        ShopEntry entry = new ShopEntry(goodsId, ShopEntry.TradeType.SELL, 0, "barter", "", false, goodsQty, payload.customName(), offerId, offerQty, null, null,
                                false, payload.nbt(), payload.barterOfferStrict(), payload.barterOfferNbt(), payload.barterGoodsStrict());
                        if ("edit_barter_item".equals(payload.action())) {
                            ShopConfig.updateItem(payload.category(), payload.index(), entry);
                        } else {
                            ShopConfig.addItem(payload.category(), entry);
                        }
                    }
                }
                case "edit_xp" -> {
                    int xpAmount;
                    try { xpAmount = Integer.parseInt(payload.extraData()); } catch (NumberFormatException e) { return; }
                    ShopConfig.updateItem(payload.category(), payload.index(), ShopEntry.xp(xpAmount, payload.price()));
                }
                case "remove_item" -> ShopConfig.removeItem(payload.category(), payload.index());
                case "move_item_up" -> ShopConfig.moveItemUp(payload.category(), payload.index());
                case "move_item_down" -> ShopConfig.moveItemDown(payload.category(), payload.index());
                case "add_command" -> {
                    ResourceLocation icon = ResourceLocation.tryParse(payload.icon());
                    if (icon == null) icon = ResourceLocation.withDefaultNamespace("command_block");
                    ShopEntry entry = new ShopEntry(icon, ShopEntry.TradeType.BUY, payload.price(), "command", payload.extraData(), payload.requireOp(), 1, payload.customName(), null, 1, null, null);
                    ShopConfig.addItem(payload.category(), entry);
                }
                case "edit_command" -> {
                    ResourceLocation icon = ResourceLocation.tryParse(payload.icon());
                    if (icon == null) icon = ResourceLocation.withDefaultNamespace("command_block");
                    ShopEntry entry = new ShopEntry(icon, ShopEntry.TradeType.BUY, payload.price(), "command", payload.extraData(), payload.requireOp(), 1, payload.customName(), null, 1, null, null);
                    ShopConfig.updateItem(payload.category(), payload.index(), entry);
                }
                case "add_xp" -> {
                    int xpAmount;
                    try { xpAmount = Integer.parseInt(payload.extraData()); } catch (NumberFormatException e) { return; }
                    ShopConfig.addItem(payload.category(), ShopEntry.xp(xpAmount, payload.price()));
                }
                case "copy_item" -> {
                    var cat = ShopConfig.getCategory(payload.category());
                    if (cat != null && payload.index() >= 0 && payload.index() < cat.size()) {
                        ShopEntry original = cat.get(payload.index());
                        ShopConfig.addItem(payload.category(), original);
                    }
                }
                case "copy_category" -> {
                    var source = ShopConfig.getCategory(payload.category());
                    if (source != null) {
                        String newName = payload.category() + " - 副本";
                        ShopConfig.addCategory(newName);
                        for (ShopEntry entry : source) {
                            ShopConfig.addItem(newName, entry);
                        }
                        resultCategory = newName;
                    }
                }
            }

            if (player instanceof ServerPlayer sp) {
                var entries = ShopConfig.getCategory(resultCategory);
                int balance = SellBoxEconomy.getBalance(sp);
                PacketDistributor.sendToPlayer(sp, new ShopDataPayload(
                        resultCategory, entries != null ? entries : java.util.List.of(), balance,
                        ShopConfig.getCategoryNames(),
                        com.sellbox.mod.shop.TradeLimitTracker.snapshotUsed(entries, resultCategory, sp.getUUID())));
            }
        });
    }
}
