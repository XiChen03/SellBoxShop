package com.sellbox.mod.playershop;

import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.shop.ShopConfig;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class PlayerShopEditHandler {

    private static CompoundTag parseNbt(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return TagParser.parseTag(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ShopEntry buildItemEntry(ModNetwork.PlayerShopEditPacket msg) {
        return ShopEntry.itemWithNbt(msg.itemId(), msg.tradeType(), msg.price(),
                msg.quantity() > 0 ? msg.quantity() : 1, msg.customName(),
                parseNbt(msg.nbt()), msg.strictMatch());
    }

    private static ShopEntry buildBarterEntry(ModNetwork.PlayerShopEditPacket msg) {
        return new ShopEntry(msg.itemId(), ShopEntry.TradeType.SELL, 0, "barter", "",
                false, msg.price() > 0 ? msg.price() : 1, msg.customName(),
                msg.barterItemId(), msg.quantity() > 0 ? msg.quantity() : 1, null, null,
                parseNbt(msg.nbt()), msg.strictMatch(),
                parseNbt(msg.barterNbt()), msg.barterStrictMatch());
    }

    public static void handle(ServerPlayer player, ModNetwork.PlayerShopEditPacket msg) {
        UUID uuid = player.getUUID();

        UUID targetUUID = uuid;
        if (msg.targetOwner() != null && !msg.targetOwner().equals(uuid)) {
            if (player.hasPermissions(2) && ShopConfig.isAdminEditEnabled()) {
                targetUUID = msg.targetOwner();
            }
        }

        String action = msg.action();
        if (action.startsWith("add") || action.startsWith("edit")) {
            if (PlayerShopBlacklist.isBlacklisted(msg.itemId())) {
                player.sendSystemMessage(Component.literal("§c该物品已被管理员加入黑名单"));
                return;
            }
            if (msg.barterItemId() != null && PlayerShopBlacklist.isBlacklisted(msg.barterItemId())) {
                player.sendSystemMessage(Component.literal("§c该物品已被管理员加入黑名单"));
                return;
            }
        }

        switch (action) {
            case "add_item" -> {
                if (msg.price() < 1) return;
                PlayerShopConfig.addItem(targetUUID, buildItemEntry(msg));
            }
            case "edit_item" -> {
                if (msg.price() < 1) return;
                PlayerShopConfig.updateItem(targetUUID, msg.index(), buildItemEntry(msg));
            }
            case "add_barter_item" -> {
                if (msg.barterItemId() != null) {
                    PlayerShopConfig.addItem(targetUUID, buildBarterEntry(msg));
                }
            }
            case "edit_barter_item" -> {
                if (msg.barterItemId() != null) {
                    PlayerShopConfig.updateItem(targetUUID, msg.index(), buildBarterEntry(msg));
                }
            }
            case "remove_item" -> PlayerShopConfig.removeItem(targetUUID, msg.index());
            case "copy_item" -> {
                var info = PlayerShopConfig.getShop(targetUUID);
                if (msg.index() >= 0 && msg.index() < info.items().size()) {
                    PlayerShopConfig.addItem(targetUUID, info.items().get(msg.index()));
                }
            }
            case "move_item_up" -> PlayerShopConfig.moveItemUp(targetUUID, msg.index());
            case "move_item_down" -> PlayerShopConfig.moveItemDown(targetUUID, msg.index());
            case "set_name" -> PlayerShopConfig.setShopName(targetUUID, msg.customName());
        }
    }
}
