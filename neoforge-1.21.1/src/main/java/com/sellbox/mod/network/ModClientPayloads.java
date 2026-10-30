package com.sellbox.mod.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.gui.PricingScreen;
import com.sellbox.mod.playershop.PlayerShopScreen;
import com.sellbox.mod.playershop.network.OpenPlayerShopScreenPayload;
import com.sellbox.mod.playershop.network.PlayerShopDataPayload;
import com.sellbox.mod.registry.ModSoundEvents;
import com.sellbox.mod.shop.ShopMenu;
import com.sellbox.mod.shop.ShopScreen;
import com.sellbox.mod.shop.QuestPickerScreen;
import com.sellbox.mod.shop.network.OpenShopScreenPayload;
import com.sellbox.mod.shop.network.ShopDataPayload;
import com.sellbox.mod.shop.network.SyncQuestListPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = SellBoxMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientPayloads {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        ModPayloads.clientHandlers.put(ModPayloads.CloseScreenPayload.TYPE.id(),
                (payload, context) -> context.enqueueWork(() -> {
                    var p = (ModPayloads.CloseScreenPayload) payload;
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.translatable(p.messageKey()), false);
                        if (p.messageKey().contains("success")) {
                            mc.player.playSound(ModSoundEvents.GOOD.get(), 1.0F, 1.0F);
                        } else {
                            mc.player.playSound(ModSoundEvents.DIE.get(), 1.0F, 1.0F);
                        }
                    }
                    mc.setScreen(null);
                }));

        ModPayloads.clientHandlers.put(PricingPayload.OpenPricingPayload.TYPE.id(),
                (payload, context) -> context.enqueueWork(() -> {
                    Minecraft.getInstance().setScreen(new PricingScreen());
                }));

        ModPayloads.clientHandlers.put(OpenShopScreenPayload.TYPE.id(),
                (payload, context) -> context.enqueueWork(() -> {
                    var p = (OpenShopScreenPayload) payload;
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.setScreen(new ShopScreen(
                                new ShopMenu(0, mc.player.getInventory()),
                                mc.player.getInventory(),
                                Component.translatable("gui.shop.title")
                        ));
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                new com.sellbox.mod.shop.network.ShopOpenPayload(""));
                    }
                }));

        ModPayloads.clientHandlers.put(ShopDataPayload.TYPE.id(),
                (payload, context) -> context.enqueueWork(() -> {
                    var p = (ShopDataPayload) payload;
                    if (Minecraft.getInstance().screen instanceof ShopScreen shopScreen) {
                        shopScreen.updateData(p.category(), p.entries(), p.balance(), p.categories(), p.usedCounts());
                    }
                }));

        ModPayloads.clientHandlers.put(OpenPlayerShopScreenPayload.TYPE.id(),
                (payload, context) -> context.enqueueWork(() -> {
                    var p = (OpenPlayerShopScreenPayload) payload;
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.setScreen(new PlayerShopScreen(p.marketPos()));
                    }
                }));

        ModPayloads.clientHandlers.put(PlayerShopDataPayload.TYPE.id(),
                (payload, context) -> context.enqueueWork(() -> {
                    var p = (PlayerShopDataPayload) payload;
                    if (Minecraft.getInstance().screen instanceof PlayerShopScreen screen) {
                        screen.updateData(p.shopNames(), p.shopItems(), p.stockCounts(), p.unloadedOwners(), p.ownerNames());
                    }
                }));

        ModPayloads.clientHandlers.put(SyncQuestListPacket.TYPE.id(),
                (payload, context) -> context.enqueueWork(() -> {
                    var p = (SyncQuestListPacket) payload;
                    if (Minecraft.getInstance().screen instanceof QuestPickerScreen qps) {
                        qps.onQuestData(p.groups());
                    }
                }));
    }
}
