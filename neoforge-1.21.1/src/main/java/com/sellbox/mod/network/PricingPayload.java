package com.sellbox.mod.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.config.SellBoxConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public class PricingPayload {
    public static final ResourceLocation SET_PRICES_ID = ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "set_prices");
    public static final ResourceLocation OPEN_PRICING_ID = ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "open_pricing");

    public record OpenPricingPayload() implements CustomPacketPayload {
        public static final Type<OpenPricingPayload> TYPE = new Type<>(OPEN_PRICING_ID);
        public static final StreamCodec<FriendlyByteBuf, OpenPricingPayload> CODEC = StreamCodec.unit(new OpenPricingPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetPricesPayload(Map<String, Integer> prices) implements CustomPacketPayload {
        public static final Type<SetPricesPayload> TYPE = new Type<>(SET_PRICES_ID);

        public static final StreamCodec<FriendlyByteBuf, SetPricesPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.prices.size());
                    for (Map.Entry<String, Integer> entry : payload.prices.entrySet()) {
                        buf.writeUtf(entry.getKey());
                        buf.writeVarInt(entry.getValue());
                    }
                },
                buf -> {
                    int size = buf.readVarInt();
                    Map<String, Integer> map = new HashMap<>(size);
                    for (int i = 0; i < size; i++) {
                        map.put(buf.readUtf(), buf.readVarInt());
                    }
                    return new SetPricesPayload(map);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void handleSetPrices(SetPricesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
            if (!serverPlayer.hasPermissions(2) || !serverPlayer.isCreative()) return;
            var server = serverPlayer.getServer();
            if (server == null) return;
            var registries = server.registryAccess();

            for (Map.Entry<String, Integer> entry : payload.prices().entrySet()) {
                String key = entry.getKey();
                int pipeIdx = key.indexOf('|');
                if (pipeIdx >= 0) {
                    // NBT 精确级价格：itemId|完整组件SNBT（如 handsetnbt / 定价GUI NBT 分类）
                    ResourceLocation nid = ResourceLocation.tryParse(key.substring(0, pipeIdx));
                    if (nid != null && BuiltInRegistries.ITEM.containsKey(nid)) {
                        String snbt = key.substring(pipeIdx + 1);
                        try {
                            net.minecraft.nbt.CompoundTag comps = net.minecraft.nbt.TagParser.parseTag(snbt);
                            net.minecraft.nbt.CompoundTag stTag = new net.minecraft.nbt.CompoundTag();
                            stTag.putString("id", nid.toString());
                            if (!comps.isEmpty()) stTag.put("components", comps);
                            var parsed = net.minecraft.world.item.ItemStack.parse(registries, stTag);
                            if (parsed.isPresent()) {
                                SellBoxConfig.setPriceNbt(parsed.get(), entry.getValue(), registries);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } else {
                    ResourceLocation id = ResourceLocation.tryParse(key);
                    if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                        Item item = BuiltInRegistries.ITEM.get(id);
                        SellBoxConfig.setPrice(item, entry.getValue());
                    }
                }
            }

            ModPayloads.broadcastAllPrices(server);
        });
    }

    // ===== NBT 精确级价格删除（itemId|SNBT 键，来自定价 GUI 的"NBT定价"分类） =====
    public record DeleteNbtPricesPayload(java.util.List<String> keys) implements CustomPacketPayload {
        public static final Type<DeleteNbtPricesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "delete_nbt_prices"));

        public static final StreamCodec<FriendlyByteBuf, DeleteNbtPricesPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.keys().size());
                    for (String k : payload.keys()) buf.writeUtf(k);
                },
                buf -> {
                    int size = buf.readVarInt();
                    java.util.List<String> list = new java.util.ArrayList<>(size);
                    for (int i = 0; i < size; i++) list.add(buf.readUtf());
                    return new DeleteNbtPricesPayload(list);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void handleDeleteNbtPrices(DeleteNbtPricesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
            if (!serverPlayer.hasPermissions(2) || !serverPlayer.isCreative()) return;

            for (String key : payload.keys()) {
                SellBoxConfig.removePriceNbtKey(key);
            }

            var server = serverPlayer.getServer();
            if (server != null) {
                ModPayloads.broadcastAllPrices(server);
            }
        });
    }

    // ===== NBT 精确级价格设置（itemId|SNBT 键 → 价格，来自定价 GUI 的背包选择定价） =====
    public record SetNbtPricesPayload(Map<String, Integer> prices) implements CustomPacketPayload {
        public static final Type<SetNbtPricesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "set_nbt_prices"));
        public static final StreamCodec<FriendlyByteBuf, SetNbtPricesPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.prices().size());
                    for (Map.Entry<String, Integer> e : payload.prices().entrySet()) {
                        buf.writeUtf(e.getKey());
                        buf.writeVarInt(e.getValue());
                    }
                },
                buf -> {
                    int size = buf.readVarInt();
                    Map<String, Integer> map = new HashMap<>(size);
                    for (int i = 0; i < size; i++) map.put(buf.readUtf(), buf.readVarInt());
                    return new SetNbtPricesPayload(map);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void handleSetNbtPrices(SetNbtPricesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
            if (!serverPlayer.hasPermissions(2) || !serverPlayer.isCreative()) return;

            for (Map.Entry<String, Integer> entry : payload.prices().entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty()) continue;
                SellBoxConfig.setPriceNbtKey(entry.getKey().trim(), entry.getValue());
            }

            var server = serverPlayer.getServer();
            if (server != null) {
                ModPayloads.broadcastAllPrices(server);
            }
        });
    }
}
