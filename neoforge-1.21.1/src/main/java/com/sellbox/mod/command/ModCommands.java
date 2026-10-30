package com.sellbox.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.api.SellBoxPlaceholders;
import com.sellbox.mod.config.SellBoxConfig;

import java.util.UUID;
import com.sellbox.mod.integration.VSSIntegration;
import com.sellbox.mod.playershop.PlayerShopConfig;
import com.sellbox.mod.shop.SellBoxEconomy;import com.sellbox.mod.network.ModPayloads;
import com.sellbox.mod.network.PricingPayload;
import com.sellbox.mod.shop.ShopConfig;
import com.sellbox.mod.shop.network.OpenShopScreenPayload;
import com.sellbox.mod.shop.network.SyncShopConfigPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class ModCommands {
    // 第四列可见性标签：""=始终可见  "shop"=仅VSS未启用或商店覆盖开启时显示  "vss"=仅VSS启用时显示
    private static final String[][] HELP_DATA = {
        // 一、帮助
        {"help [page]", "查看指令帮助", "0", ""},
        // 二、查询
        {"handprice", "查询手中物品的收购价", "0", ""},
        {"getselltime", "查看当前自动售货机出货时间", "2", ""},
        {"leaderboard <page>", "查看经济排行榜", "2", ""},
        {"money", "查看自己的余额（占位符 %sellboxmoney%）", "0", ""},
        // 三、玩家操作
        {"sell", "直接按收购价出售手中物品", "0", ""},
        {"adminshop open", "打开管理员商店GUI", "0", "shop"},
        {"shoplist [page]", "查看玩家商店排行榜", "0", ""},
        // 四、管理 - 定价
        {"handset <price>", "设置手中物品的收购价", "2", ""},
        {"handsetnbt <price>", "按手中物品 NBT 精确设置收购价（如酒品质）", "2", ""},
        {"handremove", "移除手中物品的收购价", "2", ""},
        {"handremovenbt", "移除手中物品的 NBT 精确收购价", "2", ""},
        {"delnbt", "清除全部 NBT 精确收购价", "2", ""},
        {"editpricing", "打开物品定价GUI", "2", ""},
        // 五、管理 - 管理员商店
        {"adminshop edit", "切换管理员商店编辑模式", "2", "shop"},
        {"adminshop <true/false>", "VSS经济下启用/禁用管理员商店", "2", "vss"},
        {"playershop edit", "切换玩家商店管理员编辑模式(可编辑他人商店)", "2", ""},
        // 六、管理 - 物品黑名单
        {"blacklist add <item>", "将物品加入黑名单(禁止上架)", "2", ""},
        {"blacklist remove <item>", "将物品从黑名单移除", "2", ""},
        {"blacklist list", "查看黑名单列表", "2", ""},
        // 七、管理 - 经济
        {"eco give <player> <amount>", "给予玩家金钱", "2", ""},
        {"eco set <player> <amount>", "设置玩家金钱", "2", ""},
        {"ViScriptShop <true/false>", "切换VSS经济(true)或内置经济(false)", "2", ""},
        // 八、管理 - 系统
        {"setselltime <tick>", "设置自动售货机出货时间", "2", ""},
        {"reload", "重载所有配置", "2", ""},
        {"theme [name]", "查看/切换GUI主题", "0", ""},
    };
    private static final int PER_PAGE = 5;

    private static String stripColor(String s) {
        return s.replaceAll("§.", "");
    }

    private static void sendHelpPage(CommandSourceStack source, int page) {
        boolean isOp = source.hasPermission(2);
        boolean isVssActive = VSSIntegration.isLoaded() && SellBoxConfig.isVssCurrencyEnabled();
        boolean shopAllowed = !isVssActive || SellBoxConfig.isShopOverrideEnabled();
        java.util.List<String[]> visible = new java.util.ArrayList<>();
        for (String[] entry : HELP_DATA) {
            int needPerm = Integer.parseInt(entry[2]);
            if (!(isOp || needPerm <= 0)) continue;
            String tag = entry.length > 3 ? entry[3] : "";
            if ("shop".equals(tag) && !shopAllowed) continue;
            if ("vss".equals(tag) && !isVssActive) continue;
            visible.add(entry);
        }

        int total = visible.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / PER_PAGE));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        MutableComponent msg = Component.literal("");

        if (total <= PER_PAGE) {
            msg.append(Component.literal("§6=== SellBox指令 ===\n"));
        } else {
            msg.append(Component.literal("§6=== SellBox指令 (§e" + page + "/" + totalPages + "§6) ===\n"));
        }

        int maxLineLen = 0;
        int start = (page - 1) * PER_PAGE;
        int end = Math.min(start + PER_PAGE, total);
        java.util.List<String[]> pageEntries = new java.util.ArrayList<>();
        for (int i = start; i < end; i++) {
            pageEntries.add(visible.get(i));
            String usage = visible.get(i)[0];
            String lineStr = "/sellbox " + usage;
            int len = stripColor(lineStr).length();
            if (len > maxLineLen) maxLineLen = len;
        }

        for (String[] entry : pageEntries) {
            String usage = entry[0];
            String desc = entry[1];
            MutableComponent line = Component.literal("§7/sellbox " + usage + "\n")
                    .withStyle(Style.EMPTY
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§a" + desc)))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sellbox " + usage.split(" \\[")[0].split(" <")[0])));
            msg.append(line);
        }

        if (total <= PER_PAGE) {
            String footerText = "<<底部>>";
            int padding = Math.max(0, (maxLineLen - footerText.length()) / 2);
            msg.append(Component.literal("§7" + " ".repeat(padding) + footerText + "\n"));
        } else {
            MutableComponent nav = Component.literal("");
            if (page > 1) {
                int prev = page - 1;
                nav.append(Component.literal("§e<< 上一页")
                        .withStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sellbox help " + prev))));
            } else {
                nav.append(Component.literal("§7<< 上一页"));
            }
            nav.append(Component.literal(" §7| "));
            if (page < totalPages) {
                int next = page + 1;
                nav.append(Component.literal("§e下一页 >>")
                        .withStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sellbox help " + next))));
            } else {
                nav.append(Component.literal("§7下一页 >>"));
            }
            msg.append(nav);
        }

        source.sendSuccess(() -> msg, false);
    }

    private static void sendShopList(CommandSourceStack source, int page) {
        var allShops = PlayerShopConfig.getAllShops();
        var allStats = PlayerShopConfig.getAllStats();
        var server = source.getServer();
        java.util.List<Object[]> entries = new java.util.ArrayList<>();

        for (var e : allShops.entrySet()) {
            UUID uuid = e.getKey();
            var info = e.getValue();
            var stats = allStats.getOrDefault(uuid, new PlayerShopConfig.ShopStats(0, 0));
            String shopName = info.shopName() != null && !info.shopName().isEmpty() ? info.shopName() : "§7(未命名)";
            String playerName = server.getPlayerList().getPlayer(uuid) != null
                    ? server.getPlayerList().getPlayer(uuid).getName().getString()
                    : uuid.toString().substring(0, 8);
            entries.add(new Object[]{shopName, playerName, stats.totalEarnings(), stats.totalItemsSold()});
        }

        entries.sort((a, b) -> Long.compare((long) b[2], (long) a[2]));

        int total = entries.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / 5));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        MutableComponent msg = Component.literal("");
        msg.append(Component.literal("§6=== 玩家商店排行榜 (§e" + page + "/" + totalPages + "§6) ===\n"));

        int start = (page - 1) * 5;
        int end = Math.min(start + 5, total);
        for (int i = start; i < end; i++) {
            Object[] e = entries.get(i);
            msg.append(Component.literal("§7" + (i + 1) + ". §f" + e[0] + " §7- §f" + e[1]
                    + " §7- 赚取: §a" + e[2] + " §7- 物品: §b" + e[3] + "\n"));
        }

        if (totalPages > 1) {
            MutableComponent nav = Component.literal("");
            if (page > 1) {
                nav.append(Component.literal("§e<< 上一页")
                        .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sellbox shoplist " + (page - 1)))));
            } else {
                nav.append(Component.literal("§7<< 上一页"));
            }
            nav.append(Component.literal(" §7| "));
            if (page < totalPages) {
                nav.append(Component.literal("§e下一页 >>")
                        .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sellbox shoplist " + (page + 1)))));
            } else {
                nav.append(Component.literal("§7下一页 >>"));
            }
            msg.append(nav);
        }

        source.sendSuccess(() -> msg, false);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sellbox")
                .requires(source -> source.hasPermission(0))

                .then(Commands.literal("help")
                        .executes(ctx -> {
                            sendHelpPage(ctx.getSource(), 1);
                            return 1;
                        })
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int page = IntegerArgumentType.getInteger(ctx, "page");
                                    sendHelpPage(ctx.getSource(), page);
                                    return 1;
                                })))

                .then(Commands.literal("leaderboard")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1, 1000))
                                .executes(ctx -> {
                                    int page = IntegerArgumentType.getInteger(ctx, "page");
                                    SellBoxEconomy.sendLeaderboard(ctx.getSource().getPlayerOrException(), page);
                                    return 1;
                                })))

                .then(Commands.literal("money")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String resolved = SellBoxPlaceholders.resolve("%sellboxmoney%", player);
                            ctx.getSource().sendSuccess(() -> Component.literal("余额: " + resolved), true);
                            return 1;
                        }))

                .then(Commands.literal("sell")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ItemStack held = player.getMainHandItem();
                            if (held.isEmpty()) {
                                ctx.getSource().sendFailure(Component.translatable("command.sellbox.no_item"));
                                return 0;
                            }
                            int price = SellBoxConfig.getPrice(held, ctx.getSource().getLevel().registryAccess());
                            if (price <= 0) {
                                ctx.getSource().sendFailure(Component.translatable("command.sellbox.sell_no_price", BuiltInRegistries.ITEM.getKey(held.getItem()).toString()));
                                return 0;
                            }
                            int totalValue = price * held.getCount();
                            SellBoxEconomy.addBalance(player, totalValue);
                            ModPayloads.sendBalanceToPlayer(player);
                            player.getMainHandItem().shrink(held.getCount());
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.sellbox.sell_success", totalValue, String.valueOf(price)), true);
                            return 1;
                        }))

                .then(Commands.literal("handset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("price", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ItemStack held = player.getMainHandItem();
                                    if (held.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.translatable("command.sellbox.no_item"));
                                        return 0;
                                    }
                                    Item item = held.getItem();
                                    int price = IntegerArgumentType.getInteger(ctx, "price");
                                    SellBoxConfig.setPrice(item, price);
                                    ModPayloads.broadcastAllPrices(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.sellbox.price_set", BuiltInRegistries.ITEM.getKey(item).toString(), String.valueOf(price)), true);
                                    return 1;
                                })))

                .then(Commands.literal("handsetnbt")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("price", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ItemStack held = player.getMainHandItem();
                                    if (held.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.translatable("command.sellbox.no_item"));
                                        return 0;
                                    }
                                    String nbtStr = com.sellbox.mod.util.ItemVariantUtil.brewNbtSnbt(held, ctx.getSource().getLevel().registryAccess());
                                    if (nbtStr == null) {
                                        ctx.getSource().sendFailure(Component.translatable("command.sellbox.no_nbt"));
                                        return 0;
                                    }
                                    int price = IntegerArgumentType.getInteger(ctx, "price");
                                    SellBoxConfig.setPriceNbt(held, price, ctx.getSource().getLevel().registryAccess());
                                    ModPayloads.broadcastAllPrices(ctx.getSource().getServer());
                                    String finalNbt = nbtStr;
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.sellbox.price_set_nbt",
                                            BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), finalNbt, String.valueOf(price)), true);
                                    return 1;
                                })))

                .then(Commands.literal("handremovenbt")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ItemStack held = player.getMainHandItem();
                            if (held.isEmpty()) {
                                ctx.getSource().sendFailure(Component.translatable("command.sellbox.no_item"));
                                return 0;
                            }
                            SellBoxConfig.removePriceNbt(held, ctx.getSource().getLevel().registryAccess());
                            ModPayloads.broadcastAllPrices(ctx.getSource().getServer());
                            String nbtStr = com.sellbox.mod.util.ItemVariantUtil.brewNbtSnbt(held, ctx.getSource().getLevel().registryAccess());
                            String finalNbt = nbtStr == null ? "" : nbtStr;
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.sellbox.price_removed_nbt",
                                    BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), finalNbt), true);
                            return 1;
                        }))

                .then(Commands.literal("delnbt")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            int removed = SellBoxConfig.clearPricesNbt();
                            ModPayloads.broadcastAllPrices(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("已清除 " + removed + " 条 NBT 精确收购价"), true);
                            return 1;
                        }))

                .then(Commands.literal("handremove")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ItemStack held = player.getMainHandItem();
                            if (held.isEmpty()) {
                                ctx.getSource().sendFailure(Component.translatable("command.sellbox.no_item"));
                                return 0;
                            }
                            Item item = held.getItem();
                            SellBoxConfig.setPrice(item, 0);
                            ModPayloads.broadcastAllPrices(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.sellbox.price_removed", BuiltInRegistries.ITEM.getKey(item).toString()), true);
                            return 1;
                        }))

                .then(Commands.literal("handprice")
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayerOrException();
                            ItemStack handStack = player.getMainHandItem();
                            int price = SellBoxConfig.getPrice(handStack, ctx.getSource().getLevel().registryAccess());
                            ResourceLocation id = BuiltInRegistries.ITEM.getKey(handStack.getItem());
                            if (id.toString().equals("minecraft:air")) {
                                ctx.getSource().sendFailure(Component.translatable("command.sellbox.no_item"));
                            } else {
                                String nbtSuffix = (com.sellbox.mod.util.ItemVariantUtil.brewNbtSnbt(handStack, ctx.getSource().getLevel().registryAccess()) != null)
                                        ? " [" + com.sellbox.mod.util.ItemVariantUtil.brewNbtSnbt(handStack, ctx.getSource().getLevel().registryAccess()) + "]" : "";
                                String suffix = nbtSuffix;
                                ctx.getSource().sendSuccess(
                                        () -> Component.translatable("command.sellbox.handprice", id.toString() + suffix, price), false
                                );
                            }
                            return 1;
                        }))

                .then(Commands.literal("setselltime")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("tick", IntegerArgumentType.integer(0, 24000))
                                .executes(ctx -> {
                                    int tick = IntegerArgumentType.getInteger(ctx, "tick");
                                    SellBoxConfig.setSellTime(tick);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Auto-sell time set to tick " + tick), true
                                    );
                                    return 1;
                                })))

                .then(Commands.literal("getselltime")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            int tick = SellBoxConfig.getSellTime();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Auto-sell time: tick " + tick), true
                            );
                            return 1;
                        }))

                .then(Commands.literal("editpricing")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (!player.isCreative()) {
                                ctx.getSource().sendFailure(Component.translatable("command.sellbox.pricing_not_creative"));
                                return 0;
                            }
                            try {
                                player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(new PricingPayload.OpenPricingPayload()));
                            } catch (Exception e) {
                                ctx.getSource().sendFailure(Component.literal("Error: " + e.getClass().getSimpleName() + " - " + e.getMessage()));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.sellbox.pricing_opening"), false);
                            return 1;
                        }))

                .then(Commands.literal("adminshop")
                        .then(Commands.literal("edit")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    ShopConfig.toggleEditMode();
                                    boolean mode = ShopConfig.isEditMode();
                                    broadcastShopConfig(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            mode ? "管理员商店编辑模式已开启" : "管理员商店编辑模式已关闭"), true);
                                    return 1;
                                }))
                        .then(Commands.literal("open")
                                .executes(ctx -> {
                                    if (VSSIntegration.isLoaded() && SellBoxConfig.isVssCurrencyEnabled() && !SellBoxConfig.isShopOverrideEnabled()) {
                                        ctx.getSource().sendFailure(Component.literal("VSS经济模式下管理员商店已禁用，使用 /sellbox adminshop true 启用"));
                                        return 0;
                                    }
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    PacketDistributor.sendToPlayer(player, new OpenShopScreenPayload());
                                    return 1;
                                }))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    if (!(VSSIntegration.isLoaded() && SellBoxConfig.isVssCurrencyEnabled())) {
                                        ctx.getSource().sendFailure(Component.literal("该指令仅在使用VSS经济时可用"));
                                        return 0;
                                    }
                                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                    SellBoxConfig.setShopOverrideEnabled(enabled);
                                    broadcastShopConfig(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal("商店已" + (enabled ? "启用" : "禁用")), true);
                                    return 1;
                                })))

                .then(Commands.literal("shoplist")
                        .executes(ctx -> { sendShopList(ctx.getSource(), 1); return 1; })
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int page = IntegerArgumentType.getInteger(ctx, "page");
                                    sendShopList(ctx.getSource(), page);
                                    return 1;
                                })))

                .then(Commands.literal("playershop")
                        .then(Commands.literal("edit")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    PlayerShopConfig.toggleAdminEditMode();
                                    boolean mode = PlayerShopConfig.isAdminEditMode();
                                    broadcastShopConfig(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            mode ? "管理员编辑模式已开启 - 可编辑他人商店" : "管理员编辑模式已关闭"), true);
                                    return 1;
                                })))

                .then(Commands.literal("blacklist")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String itemId = StringArgumentType.getString(ctx, "item");
                                            ResourceLocation rl = ResourceLocation.tryParse(itemId);
                                            if (rl == null) {
                                                ctx.getSource().sendFailure(Component.literal("无效的物品ID: " + itemId));
                                                return 0;
                                            }
                                            if (!BuiltInRegistries.ITEM.containsKey(rl)) {
                                                ctx.getSource().sendFailure(Component.literal("物品不存在: " + itemId));
                                                return 0;
                                            }
                                            if (PlayerShopConfig.addToBlacklist(rl)) {
                                                ctx.getSource().sendSuccess(() -> Component.literal("已将 " + itemId + " 加入黑名单"), true);
                                            } else {
                                                ctx.getSource().sendSuccess(() -> Component.literal(itemId + " 已在黑名单中"), false);
                                            }
                                            return 1;
                                        })))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String itemId = StringArgumentType.getString(ctx, "item");
                                            ResourceLocation rl = ResourceLocation.tryParse(itemId);
                                            if (rl == null) {
                                                ctx.getSource().sendFailure(Component.literal("无效的物品ID: " + itemId));
                                                return 0;
                                            }
                                            if (PlayerShopConfig.removeFromBlacklist(rl)) {
                                                ctx.getSource().sendSuccess(() -> Component.literal("已将 " + itemId + " 从黑名单移除"), true);
                                            } else {
                                                ctx.getSource().sendSuccess(() -> Component.literal(itemId + " 不在黑名单中"), false);
                                            }
                                            return 1;
                                        })))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    var list = PlayerShopConfig.getBlacklistedItems();
                                    if (list.isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("黑名单为空"), false);
                                    } else {
                                        MutableComponent msg = Component.literal("§6=== 物品黑名单 (" + list.size() + ") ===\n");
                                        for (String id : list) {
                                            msg.append(Component.literal("§7- §f" + id + "\n"));
                                        }
                                        ctx.getSource().sendSuccess(() -> msg, false);
                                    }
                                    return 1;
                                })))

                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            SellBoxConfig.loadPricing();
                            PlayerShopConfig.load();
                            PlayerShopConfig.loadStats();
                            PlayerShopConfig.loadBlacklist();
                            ModPayloads.broadcastAllPrices(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("已重载所有配置"), true
                            );
                            return 1;
                        }))

                .then(Commands.literal("eco")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    SellBoxEconomy.addBalance(target, amount);
                                                    ModPayloads.sendBalanceToPlayer(target);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("已给予 " + target.getName().getString() + " " + SellBoxEconomy.formatMoney(amount)), true);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    SellBoxEconomy.setBalance(target, amount);
                                                    ModPayloads.sendBalanceToPlayer(target);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("已设置 " + target.getName().getString() + " 余额为 " + SellBoxEconomy.formatMoney(amount)), true);
                                                    return 1;
                                                })))))

                .then(Commands.literal("ViScriptShop")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    if (value && !VSSIntegration.isLoaded()) {
                                        ctx.getSource().sendFailure(Component.translatable("message.sellbox.vss_not_found"));
                                        return 0;
                                    }
                                    SellBoxConfig.setVssCurrencyEnabled(value);
                                    // 切换经济后同步所有在线玩家余额
                                    var server = ctx.getSource().getServer();
                                    for (var p : server.getPlayerList().getPlayers()) {
                                        ModPayloads.sendBalanceToPlayer(p);
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("VSS经济已" + (value ? "启用" : "禁用") + "，使用 " + (value ? "ViScriptShop" : "SellBox内置") + " 经济系统"), true);
                                    broadcastShopConfig(server);
                                    return 1;
                                })))
                .then(Commands.literal("theme")
                        .executes(ctx -> {
                            var source = ctx.getSource();
                            String current = com.sellbox.mod.gui.GuiTheme.getCurrentName();
                            source.sendSuccess(() -> Component.literal("§e当前主题: §f" + current), false);
                            source.sendSuccess(() -> Component.literal("§7可用主题: " + String.join(", ", com.sellbox.mod.gui.GuiTheme.getRegistry().keySet())), false);
                            source.sendSuccess(() -> Component.literal("§7使用 /theme <名称> 切换主题"), false);
                            return 1;
                        })
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (String name : com.sellbox.mod.gui.GuiTheme.getRegistry().keySet()) {
                                        builder.suggest(name);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    var registry = com.sellbox.mod.gui.GuiTheme.getRegistry();
                                    if (!registry.containsKey(name.toLowerCase())) {
                                        ctx.getSource().sendFailure(Component.literal("§c未知主题: " + name + "，可用: " + String.join(", ", registry.keySet())));
                                        return 0;
                                    }
                                    com.sellbox.mod.gui.GuiTheme.setByName(name.toLowerCase());
                                    ctx.getSource().sendSuccess(() -> Component.literal("§a主题已切换为: §f" + com.sellbox.mod.gui.GuiTheme.getCurrentName()), true);
                                    return 1;
                                }))));
    }

    @SuppressWarnings("resource")
    private static void broadcastShopConfig(net.minecraft.server.MinecraftServer server) {
        boolean globalEditMode = ShopConfig.isEditMode();
        boolean vss = SellBoxConfig.isVssCurrencyEnabled();
        boolean override = SellBoxConfig.isShopOverrideEnabled();
        boolean adminEdit = PlayerShopConfig.isAdminEditMode();
        for (var player : server.getPlayerList().getPlayers()) {
            boolean playerEdit = globalEditMode && player.hasPermissions(2);
            PacketDistributor.sendToPlayer(player, new SyncShopConfigPayload(vss, override, playerEdit, adminEdit && player.hasPermissions(2)));
        }
    }
}
