package com.sellbox.mod.network;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.config.SellBoxConfig;
import com.sellbox.mod.shop.network.ShopEditPayload;
import com.sellbox.mod.shop.network.ShopOpenPayload;
import com.sellbox.mod.shop.network.TradePayload;
import com.sellbox.mod.shop.network.SyncShopConfigPayload;
import com.sellbox.mod.shop.network.RequestQuestListPacket;
import com.sellbox.mod.shop.network.SyncQuestListPacket;
import com.sellbox.mod.shop.network.SetEntryLimitPacket;
import com.sellbox.mod.shop.network.SetEntryQuestPacket;
import com.sellbox.mod.playershop.network.PlayerShopOpenPayload;
import com.sellbox.mod.playershop.network.PlayerShopEditPayload;
import com.sellbox.mod.playershop.network.PlayerShopTradePayload;
import com.sellbox.mod.playershop.network.PlayerShopNamePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ModPayloads {
    /** null 安全：任何 String 字段透传前都过一遍，避免 EncoderException 把玩家踢下线 */
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public static final ResourceLocation SYNC_ALL_PRICES_ID = ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "sync_all_prices");
    public static final ResourceLocation SYNC_BALANCE_ID = ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "sync_balance");
    public static final ResourceLocation REQUEST_BALANCE_ID = ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "request_balance");

    public static Map<String, Integer> clientPrices = new HashMap<>();
    public static int clientBalance = -1;
    public static boolean clientConfigSynced = false;
    public static boolean clientVssEnabled = false;
    public static boolean clientShopOverride = false;
    public static boolean clientEditMode = false;
    public static boolean clientAdminEdit = false;
    public static boolean clientExtrasEnabled = true;

    /** 扩展模块：最近一次状态包（JSON） */
    public static String clientExtrasState = "";
    /** 扩展模块：最近一次响应包（JSON） */
    public static String clientExtrasResponse = "";

    /** 响应包是一次性的：界面处理完就清掉，避免重开界面时重播旧结果 */
    public static void clearExtrasResponse() {
        clientExtrasResponse = "";
    }

    static final Map<ResourceLocation, BiConsumer<CustomPacketPayload, IPayloadContext>> clientHandlers = new HashMap<>();

    public static final ResourceLocation CLOSE_SCREEN_ID = ResourceLocation.fromNamespaceAndPath(SellBoxMod.MODID, "close_screen");

    public record CloseScreenPayload(String messageKey) implements CustomPacketPayload {
        public static final Type<CloseScreenPayload> TYPE = new Type<>(CLOSE_SCREEN_ID);
        public static final StreamCodec<FriendlyByteBuf, CloseScreenPayload> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeUtf(safe(payload.messageKey)),
                buf -> new CloseScreenPayload(buf.readUtf())
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncBalancePayload(int balance) implements CustomPacketPayload {
        public static final Type<SyncBalancePayload> TYPE = new Type<>(SYNC_BALANCE_ID);
        public static final StreamCodec<FriendlyByteBuf, SyncBalancePayload> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeVarInt(payload.balance),
                buf -> new SyncBalancePayload(buf.readVarInt())
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RequestBalancePayload() implements CustomPacketPayload {
        public static final Type<RequestBalancePayload> TYPE = new Type<>(REQUEST_BALANCE_ID);
        public static final StreamCodec<FriendlyByteBuf, RequestBalancePayload> CODEC = StreamCodec.of(
                (buf, payload) -> {},
                buf -> new RequestBalancePayload()
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncAllPricesPayload(Map<String, Integer> prices) implements CustomPacketPayload {
        public static final Type<SyncAllPricesPayload> TYPE = new Type<>(SYNC_ALL_PRICES_ID);

        public static final StreamCodec<FriendlyByteBuf, SyncAllPricesPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.prices.size());
                    for (Map.Entry<String, Integer> entry : payload.prices.entrySet()) {
                        buf.writeUtf(safe(entry.getKey()));
                        buf.writeVarInt(entry.getValue());
                    }
                },
                buf -> {
                    int size = buf.readVarInt();
                    Map<String, Integer> map = new HashMap<>(size);
                    for (int i = 0; i < size; i++) {
                        map.put(buf.readUtf(), buf.readVarInt());
                    }
                    return new SyncAllPricesPayload(map);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(SellBoxMod.MODID).versioned("1.0.0");

        registrar.playToClient(SyncBalancePayload.TYPE, SyncBalancePayload.CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> clientBalance = payload.balance());
                });

        registrar.playToClient(SyncAllPricesPayload.TYPE, SyncAllPricesPayload.CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        clientConfigSynced = true;
                        clientPrices.clear();
                        clientPrices.putAll(payload.prices());
                    });
                });

        registrar.playToServer(PricingPayload.SetPricesPayload.TYPE, PricingPayload.SetPricesPayload.CODEC,
                PricingPayload::handleSetPrices);
        registrar.playToServer(PricingPayload.DeleteNbtPricesPayload.TYPE, PricingPayload.DeleteNbtPricesPayload.CODEC,
                PricingPayload::handleDeleteNbtPrices);
        registrar.playToServer(PricingPayload.SetNbtPricesPayload.TYPE, PricingPayload.SetNbtPricesPayload.CODEC,
                PricingPayload::handleSetNbtPrices);

        // ── Extras（签到/邮箱/转账/兑换/兑换码/好友）──
        registrar.playToServer(com.sellbox.mod.extras.net.ExtrasRequestPayload.TYPE,
                com.sellbox.mod.extras.net.ExtrasRequestPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        com.sellbox.mod.extras.ExtrasServer.handleRequest(sp, payload.json());
                    }
                }));
        registrar.playToClient(com.sellbox.mod.extras.net.ExtrasStatePayload.TYPE,
                com.sellbox.mod.extras.net.ExtrasStatePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> clientExtrasState = payload.json()));
        registrar.playToClient(com.sellbox.mod.extras.net.ExtrasResponsePayload.TYPE,
                com.sellbox.mod.extras.net.ExtrasResponsePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> clientExtrasResponse = payload.json()));
        // ── 扩展提醒（右下角 toast）──
        registrar.playToClient(com.sellbox.mod.extras.net.ExtrasNoticePayload.TYPE,
                com.sellbox.mod.extras.net.ExtrasNoticePayload.CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.sellbox.mod.extras.client.ExtrasNoticeQueue.push(payload.json())));

        registrar.playToServer(ShopOpenPayload.TYPE, ShopOpenPayload.CODEC, (payload, context) -> ShopOpenPayload.handle(payload, context));
        registrar.playToServer(TradePayload.TYPE, TradePayload.CODEC, (payload, context) -> TradePayload.handle(payload, context));
        registrar.playToServer(ShopEditPayload.TYPE, ShopEditPayload.CODEC, (payload, context) -> ShopEditPayload.handle(payload, context));
        registrar.playToServer(PlayerShopEditPayload.TYPE, PlayerShopEditPayload.CODEC,
                (payload, context) -> PlayerShopEditPayload.handle(payload, context));
        registrar.playToServer(PlayerShopOpenPayload.TYPE, PlayerShopOpenPayload.CODEC,
                (payload, context) -> PlayerShopOpenPayload.handle(payload, context));
        registrar.playToServer(PlayerShopTradePayload.TYPE, PlayerShopTradePayload.CODEC,
                (payload, context) -> PlayerShopTradePayload.handle(payload, context));
        registrar.playToServer(PlayerShopNamePayload.TYPE, PlayerShopNamePayload.CODEC,
                (payload, context) -> PlayerShopNamePayload.handle(payload, context));

        registrar.playToServer(RequestBalancePayload.TYPE, RequestBalancePayload.CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        sendBalanceToPlayer(sp);
                    }
                });

        registrar.playToClient(CloseScreenPayload.TYPE, CloseScreenPayload.CODEC,
                (payload, context) -> {
                    var h = clientHandlers.get(CloseScreenPayload.TYPE.id());
                    if (h != null) h.accept(payload, context);
                });

        registrar.playToClient(PricingPayload.OpenPricingPayload.TYPE, PricingPayload.OpenPricingPayload.CODEC,
                (payload, context) -> {
                    var h = clientHandlers.get(PricingPayload.OpenPricingPayload.TYPE.id());
                    if (h != null) h.accept(payload, context);
                });

        registrar.playToClient(com.sellbox.mod.shop.network.OpenShopScreenPayload.TYPE,
                com.sellbox.mod.shop.network.OpenShopScreenPayload.CODEC,
                (payload, context) -> {
                    var h = clientHandlers.get(com.sellbox.mod.shop.network.OpenShopScreenPayload.TYPE.id());
                    if (h != null) h.accept(payload, context);
                });

        registrar.playToClient(com.sellbox.mod.shop.network.ShopDataPayload.TYPE,
                com.sellbox.mod.shop.network.ShopDataPayload.CODEC,
                (payload, context) -> {
                    var h = clientHandlers.get(com.sellbox.mod.shop.network.ShopDataPayload.TYPE.id());
                    if (h != null) h.accept(payload, context);
                });

        registrar.playToClient(com.sellbox.mod.playershop.network.OpenPlayerShopScreenPayload.TYPE,
                com.sellbox.mod.playershop.network.OpenPlayerShopScreenPayload.CODEC,
                (payload, context) -> {
                    var h = clientHandlers.get(com.sellbox.mod.playershop.network.OpenPlayerShopScreenPayload.TYPE.id());
                    if (h != null) h.accept(payload, context);
                });

        registrar.playToClient(com.sellbox.mod.playershop.network.PlayerShopDataPayload.TYPE,
                com.sellbox.mod.playershop.network.PlayerShopDataPayload.CODEC,
                (payload, context) -> {
                    var h = clientHandlers.get(com.sellbox.mod.playershop.network.PlayerShopDataPayload.TYPE.id());
                    if (h != null) h.accept(payload, context);
                });

        registrar.playToClient(SyncShopConfigPayload.TYPE, SyncShopConfigPayload.CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        var p = (SyncShopConfigPayload) payload;
                        clientConfigSynced = true;
                        clientVssEnabled = p.vssCurrencyEnabled();
                        clientShopOverride = p.shopOverrideEnabled();
                        clientEditMode = p.editMode();
                        clientAdminEdit = p.adminEditMode();
                        clientExtrasEnabled = p.extrasEnabled();
                    });
                });

        registrar.playToServer(RequestQuestListPacket.TYPE, RequestQuestListPacket.CODEC,
                (payload, context) -> RequestQuestListPacket.handle(payload, context));
        registrar.playToClient(SyncQuestListPacket.TYPE, SyncQuestListPacket.CODEC,
                (payload, context) -> {
                    var h = clientHandlers.get(SyncQuestListPacket.TYPE.id());
                    if (h != null) h.accept(payload, context);
                });
        registrar.playToServer(SetEntryQuestPacket.TYPE, SetEntryQuestPacket.CODEC,
                (payload, context) -> SetEntryQuestPacket.handle(payload, context));
        registrar.playToServer(SetEntryLimitPacket.TYPE, SetEntryLimitPacket.CODEC,
                (payload, context) -> SetEntryLimitPacket.handle(payload, context));
    }

    public static void sendAllPricesToPlayer(net.minecraft.server.level.ServerPlayer player) {
        Map<Item, Integer> prices = SellBoxConfig.getAllPrices();
        Map<String, Integer> stringPrices = new HashMap<>();
        for (Map.Entry<Item, Integer> entry : prices.entrySet()) {
            stringPrices.put(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(), entry.getValue());
        }
        PacketDistributor.sendToPlayer(player, new SyncAllPricesPayload(stringPrices));
    }

    public static void sendCloseScreen(ServerPlayer player, String messageKey) {
        PacketDistributor.sendToPlayer(player, new CloseScreenPayload(messageKey));
    }

    public static void sendBalanceToPlayer(net.minecraft.server.level.ServerPlayer player) {
        int balance = com.sellbox.mod.shop.SellBoxEconomy.getBalance(player);
        PacketDistributor.sendToPlayer(player, new SyncBalancePayload(balance));
    }

    public static void broadcastAllPrices(net.minecraft.server.MinecraftServer server) {
        Map<String, Integer> stringPrices = new HashMap<>();
        for (Map.Entry<Item, Integer> entry : SellBoxConfig.getAllPrices().entrySet()) {
            stringPrices.put(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : SellBoxConfig.getAllPricesNbt().entrySet()) {
            stringPrices.put(entry.getKey(), entry.getValue());
        }
        SyncAllPricesPayload payload = new SyncAllPricesPayload(stringPrices);
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void sendExtrasState(net.minecraft.server.level.ServerPlayer player, String json) {
        PacketDistributor.sendToPlayer(player, new com.sellbox.mod.extras.net.ExtrasStatePayload(json));
    }

    public static void sendExtrasResponse(net.minecraft.server.level.ServerPlayer player, String json) {
        PacketDistributor.sendToPlayer(player, new com.sellbox.mod.extras.net.ExtrasResponsePayload(json));
    }

    public static void sendExtrasNotice(net.minecraft.server.level.ServerPlayer player, String json) {
        PacketDistributor.sendToPlayer(player, new com.sellbox.mod.extras.net.ExtrasNoticePayload(json));
    }

    public static void sendExtrasRequest(String json) {
        PacketDistributor.sendToServer(new com.sellbox.mod.extras.net.ExtrasRequestPayload(json));
    }

    public static void sendShopConfigToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncShopConfigPayload(
                SellBoxConfig.isVssCurrencyEnabled(),
                SellBoxConfig.isShopOverrideEnabled(),
                com.sellbox.mod.shop.ShopConfig.isEditMode(),
                com.sellbox.mod.playershop.PlayerShopConfig.isAdminEditMode(),
                SellBoxConfig.isExtrasEnabled()
        ));
    }
}
