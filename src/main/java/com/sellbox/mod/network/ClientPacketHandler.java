package com.sellbox.mod.network;

import com.sellbox.mod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientPacketHandler {

    static void handleCloseScreen(ModNetwork.CloseScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(ModSounds.DIE.get(), 1.0F, 1.0F);
                mc.player.displayClientMessage(Component.translatable(msg.messageKey()), false);
            }
            mc.setScreen(null);
        });
        ctx.get().setPacketHandled(true);
    }

    static void handleActionMessage(ModNetwork.ActionMessagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(ModSounds.GOOD.get(), 1.0F, 1.0F);
                mc.player.displayClientMessage(Component.translatable(msg.messageKey()), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    static void handleShopData(ModNetwork.ShopDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof com.sellbox.mod.shop.ShopScreen shopScreen) {
                shopScreen.updateData(msg.category(), msg.entries(), msg.balance(), msg.categories());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    static void handleOpenShopScreen(ModNetwork.OpenShopScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.setScreen(new com.sellbox.mod.shop.ShopScreen(
                        new com.sellbox.mod.shop.ShopMenu(0, mc.player.getInventory()),
                        mc.player.getInventory(),
                        Component.translatable("gui.shop.title")
                ));
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.ShopOpenPacket(""));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    static void handlePlayerShopData(ModNetwork.PlayerShopDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof com.sellbox.mod.playershop.PlayerShopScreen screen) {
                screen.updateData(msg.shopNames(), msg.shopItems(), msg.stockCounts(), msg.unloadedOwners(), msg.ownerNames());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    static void handleOpenPlayerShopScreen(ModNetwork.OpenPlayerShopScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.setScreen(new com.sellbox.mod.playershop.PlayerShopScreen(msg.marketPos()));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    static void handleOpenPricing(ModNetwork.OpenPricingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().setScreen(new com.sellbox.mod.gui.PricingScreen());
        });
        ctx.get().setPacketHandled(true);
    }

    static void handleSyncQuestList(ModNetwork.SyncQuestListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ModNetwork.questNameCache.clear();
            for (var qe : msg.quests()) {
                ModNetwork.questNameCache.put(qe.questId(), qe.questName());
            }
            if (Minecraft.getInstance().screen instanceof com.sellbox.mod.shop.QuestPickerScreen qps) {
                qps.onQuestData(msg.quests());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    static void handleSyncAllShopEntries(ModNetwork.SyncAllShopEntriesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.sellbox.mod.integration.JEIIntegration.onShopEntriesUpdated(msg.categories());
        });
        ctx.get().setPacketHandled(true);
    }
}
