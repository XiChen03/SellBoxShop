package com.sellbox.mod.playershop.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.playershop.PlayerShopConfig;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record PlayerShopEditPayload(
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
        UUID targetUuid,
        boolean strictMatch,
        String nbt,
        boolean barterOfferStrict,
        String barterOfferNbt,
        boolean barterGoodsStrict
) implements CustomPacketPayload {
    public PlayerShopEditPayload(String action, int index, ResourceLocation itemId, int price, int quantity,
                                 String customName, BlockPos marketPos, ShopEntry.TradeType tradeType) {
        this(action, index, itemId, price, quantity, customName, marketPos, tradeType, null, 1, null, false, null, false, null, false);
    }

    public PlayerShopEditPayload(String action, int index, ResourceLocation itemId, int price, int quantity,
                                 String customName, BlockPos marketPos, ShopEntry.TradeType tradeType,
                                 ResourceLocation barterItemId, int barterItemQty) {
        this(action, index, itemId, price, quantity, customName, marketPos, tradeType, barterItemId, barterItemQty, null, false, null, false, null, false);
    }

    public PlayerShopEditPayload(String action, int index, ResourceLocation itemId, int price, int quantity,
                                 String customName, BlockPos marketPos, ShopEntry.TradeType tradeType,
                                 ResourceLocation barterItemId, int barterItemQty, UUID targetUuid) {
        this(action, index, itemId, price, quantity, customName, marketPos, tradeType, barterItemId, barterItemQty, targetUuid, false, null, false, null, false);
    }

    public PlayerShopEditPayload(String action, int index, ResourceLocation itemId, int price, int quantity,
                                 String customName, BlockPos marketPos, ShopEntry.TradeType tradeType,
                                 UUID targetUuid, boolean strictMatch, String nbt) {
        this(action, index, itemId, price, quantity, customName, marketPos, tradeType, null, 1, targetUuid, strictMatch, nbt, false, null, false);
    }

    public PlayerShopEditPayload(String action, int index, ResourceLocation itemId, int price, int quantity,
                                 String customName, BlockPos marketPos, ShopEntry.TradeType tradeType,
                                 ResourceLocation barterItemId, int barterItemQty, UUID targetUuid,
                                 String goodsNbt, boolean barterOfferStrict, String barterOfferNbt, boolean barterGoodsStrict) {
        this(action, index, itemId, price, quantity, customName, marketPos, tradeType, barterItemId, barterItemQty, targetUuid, false, goodsNbt, barterOfferStrict, barterOfferNbt, barterGoodsStrict);
    }

    public static final Type<PlayerShopEditPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "player_shop_edit"));

    public static final StreamCodec<FriendlyByteBuf, PlayerShopEditPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.action());
                buf.writeVarInt(payload.index());
                buf.writeResourceLocation(payload.itemId());
                buf.writeVarInt(payload.price());
                buf.writeVarInt(payload.quantity());
                buf.writeUtf(payload.customName());
                buf.writeBlockPos(payload.marketPos());
                buf.writeUtf(payload.tradeType().name());
                if (payload.barterItemId() != null) {
                    buf.writeBoolean(true);
                    buf.writeResourceLocation(payload.barterItemId());
                } else {
                    buf.writeBoolean(false);
                }
                buf.writeVarInt(payload.barterItemQty());
                buf.writeBoolean(payload.targetUuid() != null);
                if (payload.targetUuid() != null) {
                    buf.writeLong(payload.targetUuid().getMostSignificantBits());
                    buf.writeLong(payload.targetUuid().getLeastSignificantBits());
                }
                buf.writeBoolean(payload.strictMatch());
                buf.writeUtf(payload.nbt() != null ? payload.nbt() : "");
                buf.writeBoolean(payload.barterOfferStrict());
                buf.writeUtf(payload.barterOfferNbt() != null ? payload.barterOfferNbt() : "");
                buf.writeBoolean(payload.barterGoodsStrict());
            },
            buf -> {
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
                UUID target = hasTarget ? new UUID(buf.readLong(), buf.readLong()) : null;
                boolean strictMatch = buf.readBoolean();
                String nbt = buf.readUtf();
                String realNbt = nbt.isEmpty() ? null : nbt;
                boolean barterOfferStrict = buf.readBoolean();
                String barterOfferNbt = buf.readUtf();
                String realOfferNbt = barterOfferNbt.isEmpty() ? null : barterOfferNbt;
                boolean barterGoodsStrict = buf.readBoolean();
                return new PlayerShopEditPayload(action, index, itemId, price, quantity, customName, mp, tt, barterId, barterQty, target, strictMatch, realNbt, barterOfferStrict, realOfferNbt, barterGoodsStrict);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerShopEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                UUID uuid;
                boolean isAdminTarget = payload.targetUuid() != null
                        && !payload.targetUuid().equals(sp.getUUID())
                        && sp.hasPermissions(2)
                        && PlayerShopConfig.isAdminEditMode();
                if (isAdminTarget) {
                    uuid = payload.targetUuid();
                } else {
                    uuid = sp.getUUID();
                }
                switch (payload.action()) {
                    case "add_item" -> {
                        if (PlayerShopConfig.isItemBlacklisted(payload.itemId())) {
                            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c该物品已被管理员加入黑名单，无法添加为商品"));
                            return;
                        }
                        int price = Math.max(1, payload.price());
                        ShopEntry entry = new ShopEntry(payload.itemId(), payload.tradeType(), price, "item", "", false, payload.quantity() > 0 ? payload.quantity() : 1, payload.customName(), null, 1, null, null, payload.strictMatch(), payload.nbt());
                        PlayerShopConfig.addItem(uuid, entry);
                    }
                    case "edit_item" -> {
                        int price = Math.max(1, payload.price());
                        ShopEntry entry = new ShopEntry(payload.itemId(), payload.tradeType(), price, "item", "", false, payload.quantity() > 0 ? payload.quantity() : 1, payload.customName(), null, 1, null, null, payload.strictMatch(), payload.nbt());
                        PlayerShopConfig.updateItem(uuid, payload.index(), entry);
                    }
                    case "add_barter_item" -> {
                        if (payload.barterItemId() != null) {
                            if (PlayerShopConfig.isItemBlacklisted(payload.itemId()) || PlayerShopConfig.isItemBlacklisted(payload.barterItemId())) {
                                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c该物品已被管理员加入黑名单，无法添加为商品"));
                                return;
                            }
                            // itemId=商品物, price=商品数量, quantity=出价数量, barterItemId=出价物
                            ShopEntry barter = new ShopEntry(payload.itemId(), ShopEntry.TradeType.SELL, 0, "barter", "",
                                    false, payload.price() > 0 ? payload.price() : 1, payload.customName(),
                                    payload.barterItemId(), payload.quantity() > 0 ? payload.quantity() : 1, null, null,
                                    false, payload.nbt(), payload.barterOfferStrict(), payload.barterOfferNbt(), payload.barterGoodsStrict());
                            PlayerShopConfig.addItem(uuid, barter);
                        }
                    }
                    case "edit_barter_item" -> {
                        if (payload.barterItemId() != null) {
                            ShopEntry barter = new ShopEntry(payload.itemId(), ShopEntry.TradeType.SELL, 0, "barter", "",
                                    false, payload.price() > 0 ? payload.price() : 1, payload.customName(),
                                    payload.barterItemId(), payload.quantity() > 0 ? payload.quantity() : 1, null, null,
                                    false, payload.nbt(), payload.barterOfferStrict(), payload.barterOfferNbt(), payload.barterGoodsStrict());
                            PlayerShopConfig.updateItem(uuid, payload.index(), barter);
                        }
                    }
                    case "remove_item" -> PlayerShopConfig.removeItem(uuid, payload.index());
                    case "copy_item" -> {
                        var info = PlayerShopConfig.getShop(uuid);
                        if (payload.index() >= 0 && payload.index() < info.items().size()) {
                            PlayerShopConfig.addItem(uuid, info.items().get(payload.index()));
                        }
                    }
                    case "move_item_up" -> PlayerShopConfig.moveItemUp(uuid, payload.index());
                    case "move_item_down" -> PlayerShopConfig.moveItemDown(uuid, payload.index());
                }
            }
        });
    }
}
