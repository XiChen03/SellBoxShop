package com.sellbox.mod.shop.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.integration.FTBQuestsIntegration;
import com.sellbox.mod.shop.SellBoxEconomy;
import com.sellbox.mod.shop.ShopConfig;
import com.sellbox.mod.shop.ShopEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.ArrayList;

public record ShopOpenPayload(
        String category
) implements CustomPacketPayload {
    public static final Type<ShopOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "shop_open"));

    public static final StreamCodec<FriendlyByteBuf, ShopOpenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ShopOpenPayload::category,
            ShopOpenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShopOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                List<String> cats = ShopConfig.getCategoryNames();
                String cat = payload.category();
                if (cat.isEmpty() && !cats.isEmpty()) {
                    cat = cats.get(0);
                }
                var entries = ShopConfig.getCategory(cat);
                if (entries != null) {
                    // 填充缺失的任务名并保存到配置
                    if (FTBQuestsIntegration.isLoaded()) {
                        for (int i = 0; i < entries.size(); i++) {
                            ShopEntry e = entries.get(i);
                            if (e.hasPrerequisite() && e.requiredQuestName() == null) {
                                String name = FTBQuestsIntegration.getQuestNameById(e.requiredQuestId());
                                if (name != null && !name.isEmpty()) {
                                    ShopConfig.updateItem(cat, i, e.withPrerequisite(e.requiredQuestId(), name));
                                }
                            }
                        }
                    }
                    if (!ShopConfig.isEditMode() && FTBQuestsIntegration.isLoaded()) {
                        entries = entries.stream()
                                .filter(e -> !e.hasPrerequisite() || FTBQuestsIntegration.isQuestCompleted(sp, e.requiredQuestId()))
                                .toList();
                    }
                }
                int balance = SellBoxEconomy.getBalance(sp);
                PacketDistributor.sendToPlayer(sp, new ShopDataPayload(
                        cat, entries != null ? entries : java.util.List.of(), balance,
                        cats, com.sellbox.mod.shop.TradeLimitTracker.snapshotUsed(entries, cat, sp.getUUID())));
            }
        });
    }
}
