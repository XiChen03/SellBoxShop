package com.sellbox.mod.shop;

import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

public class ShopEditHandler {

    private static ShopEntry buildItemEntry(ResourceLocation id, ShopEntry.TradeType type, ModNetwork.ShopEditPacket msg) {
        CompoundTag nbt = null;
        if (msg.nbt() != null && !msg.nbt().isEmpty()) {
            try { nbt = TagParser.parseTag(msg.nbt()); } catch (Exception ignored) { }
        }
        return ShopEntry.itemWithNbt(id, type, msg.price(), msg.quantity() > 0 ? msg.quantity() : 1,
                msg.customName(), nbt, msg.strictMatch());
    }

    @SuppressWarnings("deprecation")
    public static void handle(ServerPlayer player, ModNetwork.ShopEditPacket msg) {
        if (!player.hasPermissions(2)) return;

        String resultCategory = msg.category();

        switch (msg.action()) {
            case "add_category" -> {
                ShopConfig.addCategory(msg.extraData());
                resultCategory = msg.extraData();
            }
            case "remove_category" -> ShopConfig.removeCategory(msg.category());
            case "rename_category" -> {
                ShopConfig.renameCategory(msg.category(), msg.extraData());
                resultCategory = msg.extraData();
            }
            case "move_category_up" -> ShopConfig.moveCategoryUp(msg.category());
            case "move_category_down" -> ShopConfig.moveCategoryDown(msg.category());
            case "add_item" -> {
                ResourceLocation id = ResourceLocation.tryParse(msg.extraData());
                if (id != null) {
                    ShopEntry.TradeType type = "sell".equalsIgnoreCase(msg.tradeType()) ? ShopEntry.TradeType.SELL : ShopEntry.TradeType.BUY;
                    ShopConfig.addItem(msg.category(), buildItemEntry(id, type, msg));
                }
            }
            case "edit_item" -> {
                ResourceLocation id = ResourceLocation.tryParse(msg.extraData());
                if (id != null) {
                    ShopEntry.TradeType type = "sell".equalsIgnoreCase(msg.tradeType()) ? ShopEntry.TradeType.SELL : ShopEntry.TradeType.BUY;
                    ShopConfig.updateItem(msg.category(), msg.index(), buildItemEntry(id, type, msg));
                }
            }
            case "add_barter_item", "edit_barter_item" -> {
                ResourceLocation goodsId = ResourceLocation.tryParse(msg.extraData());
                ResourceLocation offerId = msg.barterItemId() != null ? ResourceLocation.tryParse(msg.barterItemId()) : null;
                if (goodsId != null && offerId != null) {
                    int goodsQty = msg.price() > 0 ? msg.price() : 1;
                    int offerQty = msg.quantity() > 0 ? msg.quantity() : 1;
                    CompoundTag goodsNbt = null;
                    if (msg.nbt() != null && !msg.nbt().isEmpty()) {
                        try { goodsNbt = TagParser.parseTag(msg.nbt()); } catch (Exception ignored) { }
                    }
                    CompoundTag offerNbt = null;
                    if (msg.barterNbt() != null && !msg.barterNbt().isEmpty()) {
                        try { offerNbt = TagParser.parseTag(msg.barterNbt()); } catch (Exception ignored) { }
                    }
                    ShopEntry entry = ShopEntry.barterWithNbt(goodsId, goodsQty, goodsNbt,
                            offerId, offerQty, offerNbt, msg.customName(), msg.strictMatch(), msg.barterStrictMatch());
                    if ("edit_barter_item".equals(msg.action())) {
                        ShopConfig.updateItem(msg.category(), msg.index(), entry);
                    } else {
                        ShopConfig.addItem(msg.category(), entry);
                    }
                }
            }
            case "edit_xp" -> {
                int xpAmount;
                try { xpAmount = Integer.parseInt(msg.extraData()); } catch (NumberFormatException e) { return; }
                ShopConfig.updateItem(msg.category(), msg.index(), ShopEntry.xp(xpAmount, msg.price()));
            }
            case "remove_item" -> ShopConfig.removeItem(msg.category(), msg.index());
            case "move_item_up" -> ShopConfig.moveItemUp(msg.category(), msg.index());
            case "move_item_down" -> ShopConfig.moveItemDown(msg.category(), msg.index());
            case "add_command" -> {
                ResourceLocation icon = ResourceLocation.tryParse(msg.icon());
                if (icon == null) icon = new ResourceLocation("minecraft", "command_block");
                ShopConfig.addItem(msg.category(), ShopEntry.command(msg.extraData(), msg.price(), msg.requireOp(), icon, msg.customName()));
            }
            case "edit_command" -> {
                ResourceLocation icon = ResourceLocation.tryParse(msg.icon());
                if (icon == null) icon = new ResourceLocation("minecraft", "command_block");
                ShopConfig.updateItem(msg.category(), msg.index(), ShopEntry.command(msg.extraData(), msg.price(), msg.requireOp(), icon, msg.customName()));
            }
            case "add_xp" -> {
                int xpAmount;
                try { xpAmount = Integer.parseInt(msg.extraData()); } catch (NumberFormatException e) { return; }
                ShopConfig.addItem(msg.category(), ShopEntry.xp(xpAmount, msg.price()));
            }
            case "copy_item" -> {
                var cat = ShopConfig.getCategory(msg.category());
                if (cat != null && msg.index() >= 0 && msg.index() < cat.size()) {
                    ShopEntry original = cat.get(msg.index());
                    ShopConfig.addItem(msg.category(), original);
                }
            }
            case "copy_category" -> {
                var source = ShopConfig.getCategory(msg.category());
                if (source != null) {
                    String newName = msg.category() + " - 副本";
                    ShopConfig.addCategory(newName);
                    for (ShopEntry entry : source) {
                        ShopConfig.addItem(newName, entry);
                    }
                    resultCategory = newName;
                }
            }
        }

        var entries = ShopConfig.getCategory(resultCategory);
        int balance = SellBoxEconomy.getBalance(player);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ModNetwork.ShopDataPacket(
                resultCategory, entries != null ? entries : List.of(), balance,
                ShopConfig.getCategoryNames(),
                com.sellbox.mod.shop.TradeLimitTracker.snapshotUsed(entries, resultCategory, player.getUUID())));
    }
}
