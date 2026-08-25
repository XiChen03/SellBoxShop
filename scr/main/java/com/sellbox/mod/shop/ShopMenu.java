package com.sellbox.mod.shop;

import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

public class ShopMenu extends AbstractContainerMenu {
    private final Player player;
    private String currentCategory = "";

    public ShopMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenuTypes.SHOP.get(), id);
        this.player = playerInv.player;
    }

    public ShopMenu(int id, Inventory playerInv) {
        super(ModMenuTypes.SHOP.get(), id);
        this.player = playerInv.player;
    }

    public String getCurrentCategory() { return currentCategory; }

    public void openCategory(String category) {
        currentCategory = category;
        List<ShopEntry> entries = ShopConfig.getCategory(category);

        if (player instanceof ServerPlayer serverPlayer) {
            int balance = SellBoxEconomy.getBalance(serverPlayer);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new ModNetwork.ShopDataPacket(
                    category, entries != null ? entries : List.of(), balance, ShopConfig.getCategoryNames()));
        }
    }

    public void handleTrade(ShopEntry entry, int quantity) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        int totalPrice = entry.price() * quantity;

        if ("barter".equals(entry.entryType()) && entry.barterItemId() != null) {
            int offerQtyPerUnit = Math.max(1, entry.barterItemQty());
            int goodsQtyPerUnit = Math.max(1, entry.quantity());
            int needOffer = offerQtyPerUnit * quantity;
            ItemStack offerTemplate = entry.createBarterOfferStack();
            int available = 0;
            for (ItemStack stack : serverPlayer.getInventory().items) {
                if (entry.matchesOffer(stack)) available += stack.getCount();
            }
            int actualOffer = Math.min(needOffer, available);
            if (actualOffer <= 0) return;
            int actualUnits = actualOffer / offerQtyPerUnit;
            int actualGiveOffer = actualUnits * offerQtyPerUnit;
            int toRemove = actualGiveOffer;
            for (ItemStack stack : serverPlayer.getInventory().items) {
                if (toRemove <= 0) break;
                if (entry.matchesOffer(stack)) {
                    int r = Math.min(toRemove, stack.getCount());
                    stack.shrink(r);
                    toRemove -= r;
                }
            }
            ItemStack goodsStack = entry.createStack();
            if (!entry.customName().isEmpty()) {
                goodsStack.setHoverName(Component.literal(entry.customName()));
            }
            int maxGoodsStack = goodsStack.getMaxStackSize();
            int toGiveGoods = actualUnits * goodsQtyPerUnit;
            while (toGiveGoods > 0) {
                int chunk = Math.min(toGiveGoods, maxGoodsStack);
                serverPlayer.getInventory().placeItemBackInInventory(goodsStack.copyWithCount(chunk));
                toGiveGoods -= chunk;
            }
            int excessOffer = actualOffer - actualGiveOffer;
            if (excessOffer > 0) {
                serverPlayer.getInventory().placeItemBackInInventory(offerTemplate.copyWithCount(excessOffer));
            }
            return;
        }

        if ("command".equals(entry.entryType())) {
            if (!SellBoxEconomy.tryDeductBalance(serverPlayer, totalPrice)) return;
            ModNetwork.sendBalanceToPlayer(serverPlayer);
            if (entry.requireOp()) {
                serverPlayer.getServer().getCommands().performPrefixedCommand(
                        serverPlayer.getServer().createCommandSourceStack().withPermission(4),
                        entry.extraData()
                );
            } else {
                serverPlayer.getServer().getCommands().performPrefixedCommand(
                        serverPlayer.createCommandSourceStack(),
                        entry.extraData()
                );
            }
            return;
        }

        if ("xp".equals(entry.entryType())) {
            if (!SellBoxEconomy.tryDeductBalance(serverPlayer, totalPrice)) return;
            ModNetwork.sendBalanceToPlayer(serverPlayer);
            int xpPerBottle;
            try { xpPerBottle = Integer.parseInt(entry.extraData()); } catch (NumberFormatException e) { return; }
            int totalXp = xpPerBottle * quantity;
            serverPlayer.giveExperiencePoints(totalXp);
            return;
        }

        if (entry.tradeType() == ShopEntry.TradeType.BUY) {
            if (!SellBoxEconomy.tryDeductBalance(serverPlayer, totalPrice)) return;
            ModNetwork.sendBalanceToPlayer(serverPlayer);
            ItemStack template = entry.createStack();
            int totalItems = quantity * entry.quantity();
            if (!entry.customName().isEmpty()) {
                template.setHoverName(Component.literal(entry.customName()));
            }
            int maxStack = template.getMaxStackSize();
            while (totalItems > 0) {
                int chunk = Math.min(totalItems, maxStack);
                serverPlayer.getInventory().placeItemBackInInventory(template.copyWithCount(chunk));
                totalItems -= chunk;
            }
        } else if (entry.tradeType() == ShopEntry.TradeType.SELL) {
            int qtyPerUnit = Math.max(1, entry.quantity());
            int needed = quantity * qtyPerUnit;
            int available = 0;
            for (ItemStack stack : serverPlayer.getInventory().items) {
                if (entry.matches(stack)) {
                    available += stack.getCount();
                }
            }
            int toSell = Math.min(needed, available);
            if (toSell <= 0) return;
            int toRemove = toSell;
            for (ItemStack stack : serverPlayer.getInventory().items) {
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
                SellBoxEconomy.addBalance(serverPlayer, earned);
                ModNetwork.sendBalanceToPlayer(serverPlayer);
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
