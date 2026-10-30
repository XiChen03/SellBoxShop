package com.sellbox.mod.playershop;

import com.sellbox.mod.network.ModPayloads;
import com.sellbox.mod.playershop.network.PlayerShopDataPayload;
import com.sellbox.mod.shop.SellBoxEconomy;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class PlayerShopMenu extends AbstractContainerMenu {
    private final BlockPos marketPos;
    private final Player player;

    public PlayerShopMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        super(PlayerShopRegistry.PLAYER_SHOP_MENU.get(), id);
        this.player = playerInv.player;
        this.marketPos = buf.readBlockPos();
    }

    public PlayerShopMenu(int id, Inventory playerInv, BlockPos marketPos) {
        super(PlayerShopRegistry.PLAYER_SHOP_MENU.get(), id);
        this.player = playerInv.player;
        this.marketPos = marketPos;
    }

    public BlockPos getMarketPos() {
        return marketPos;
    }

    /**
     * @deprecated Dead code. Actual trades are handled server-side via
     * {@link com.sellbox.mod.playershop.network.PlayerShopTradePayload}.
     * This method bypasses stock checks and creates items from thin air.
     */
    @Deprecated
    public void handleTrade(UUID shopOwnerUUID, ShopEntry entry, int quantity) {
        // No-op: trading is handled by PlayerShopTradePayload which performs proper stock verification.
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
