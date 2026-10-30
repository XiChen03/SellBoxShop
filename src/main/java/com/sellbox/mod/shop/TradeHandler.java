package com.sellbox.mod.shop;

import com.sellbox.mod.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class TradeHandler {

    public static void executeTrade(ServerPlayer player, ShopEntry entry, int quantity) {
        int totalPrice = entry.price() * quantity;

        if ("command".equals(entry.entryType())) {
            if (!SellBoxEconomy.tryDeductBalance(player, totalPrice)) {
                ModNetwork.sendCloseScreen(player, "message.sellbox.not_enough_money");
                return;
            }
            ModNetwork.sendBalanceToPlayer(player);
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
            return;
        }

        if ("xp".equals(entry.entryType())) {
            if (!SellBoxEconomy.tryDeductBalance(player, totalPrice)) {
                ModNetwork.sendCloseScreen(player, "message.sellbox.not_enough_money");
                return;
            }
            ModNetwork.sendBalanceToPlayer(player);
            int xpPerBottle;
            try { xpPerBottle = Integer.parseInt(entry.extraData()); } catch (NumberFormatException e) { return; }
            player.giveExperiencePoints(xpPerBottle * quantity);
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
                ModNetwork.sendCloseScreen(player, "message.sellbox.not_enough_items");
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
                goodsStack.setHoverName(net.minecraft.network.chat.Component.literal(entry.customName()));
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
            return;
        }

        if (entry.tradeType() == ShopEntry.TradeType.BUY) {
            if (!SellBoxEconomy.tryDeductBalance(player, totalPrice)) {
                ModNetwork.sendCloseScreen(player, "message.sellbox.not_enough_money");
                return;
            }
            ModNetwork.sendBalanceToPlayer(player);
            ItemStack template = entry.createStack();
            int totalItems = quantity * entry.quantity();
            if (!entry.customName().isEmpty()) {
                template.setHoverName(net.minecraft.network.chat.Component.literal(entry.customName()));
            }
            int maxStack = template.getMaxStackSize();
            while (totalItems > 0) {
                int chunk = Math.min(totalItems, maxStack);
                player.getInventory().placeItemBackInInventory(template.copyWithCount(chunk));
                totalItems -= chunk;
            }
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
            if (toSell <= 0) {
                ModNetwork.sendCloseScreen(player, "message.sellbox.not_enough_items");
                return;
            }
            int toRemove = toSell;
            for (ItemStack stack : player.getInventory().items) {
                if (toRemove <= 0) break;
                if (entry.matches(stack)) {
                    int r = Math.min(toRemove, stack.getCount());
                    stack.shrink(r);
                    toRemove -= r;
                }
            }
            int actualQty = toSell / qtyPerUnit;
            int earned = entry.price() * actualQty;
            if (earned > 0) {
                SellBoxEconomy.addBalance(player, earned);
                ModNetwork.sendBalanceToPlayer(player);
            }
        }
    }
}
