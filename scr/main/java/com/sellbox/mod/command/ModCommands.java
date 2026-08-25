package com.sellbox.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.config.SellBoxConfig;
import com.sellbox.mod.integration.SDMShopIntegration;
import com.sellbox.mod.network.ModNetwork;
import com.sellbox.mod.playershop.PlayerShopBlacklist;
import com.sellbox.mod.playershop.PlayerShopConfig;
import com.sellbox.mod.shop.SellBoxEconomy;
import com.sellbox.mod.shop.ShopConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

public class ModCommands {
    private static final String[][] HELP_DATA = {
        {"help [page]", "查看指令帮助", "0", ""},
        {"handprice", "查询手中物品的收购价", "0", ""},
        {"getselltime", "查看当前自动售货机出货时间", "2", ""},
        {"leaderboard <page>", "查看经济排行榜", "2", ""},
        {"sell", "直接按收购价出售手中物品", "0", ""},
        {"adminshop open", "打开管理员商店GUI", "0", "shop"},
        {"shoplist [page]", "查看玩家商店排行榜", "0", ""},
        {"handset <price>", "设置手中物品的收购价", "2", ""},
        {"handsetnbt <price>", "按手中物品 NBT 精确设置收购价（如酒品质）", "2", ""},
        {"handremove", "移除手中物品的收购价", "2", ""},
        {"handremovenbt", "移除手中物品的 NBT 精确收购价", "2", ""},
        {"delnbt", "清空所有 NBT 精确收购价", "2", ""},
        {"editpricing", "打开物品定价GUI", "2", ""},
        {"adminshop edit", "切换管理员商店编辑模式", "2", "shop"},
        {"adminshop <true/false>", "SDMShop经济下启用/禁用管理员商店", "2", "sdm"},
        {"eco give <player> <amount>", "给予玩家金钱", "2", ""},
        {"eco set <player> <amount>", "设置玩家金钱", "2", ""},
        {"SDMShop <true/false>", "切换SDMShop经济(true)或内置经济(false)", "2", ""},
        {"setselltime <tick>", "设置自动售货机出货时间", "2", ""},
        {"reload", "重载所有配置", "2", ""},
        {"blacklist add", "将手中物品加入黑名单", "2", ""},
        {"blacklist add <item>", "将指定物品加入黑名单", "2", ""},
        {"blacklist remove <item>", "从黑名单移除物品", "2", ""},
        {"blacklist list", "查看黑名单列表", "2", ""},
        {"playershop edit", "切换玩家商店编辑模式", "2", ""},
    };
    private static final int PER_PAGE = 5;

    private static String stripColor(String s) {
        return s.replaceAll("§.", "");
    }

    private static void sendHelpPage(CommandSourceStack source, int page) {
        boolean isOp = source.hasPermission(2);
        boolean isSdmActive = SDMShopIntegration.isLoaded() && SellBoxConfig.isVssCurrencyEnabled();
        boolean shopAllowed = !isSdmActive || SellBoxConfig.isShopOverrideEnabled();
        java.util.List<String[]> visible = new java.util.ArrayList<>();
        for (String[] entry : HELP_DATA) {
            int needPerm = Integer.parseInt(entry[2]);
            if (!(isOp || needPerm <= 0)) continue;
            String tag = entry.length > 3 ? entry[3] : "";
            if ("shop".equals(tag) && !shopAllowed) continue;
            if ("sdm".equals(tag) && !isSdmActive) continue;
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

    @SuppressWarnings("resource")
    private static void broadcastShopConfig(net.minecraft.server.MinecraftServer server) {
        boolean globalEditMode = ShopConfig.isEditMode();
        boolean sdm = SellBoxConfig.isVssCurrencyEnabled();
        boolean override = SellBoxConfig.isShopOverrideEnabled();
        for (var player : server.getPlayerList().getPlayers()) {
            boolean playerEdit = globalEditMode && player.hasPermissions(2);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new ModNetwork.SyncShopConfigPacket(sdm, override, playerEdit));
        }
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

                .then(Commands.literal("sell")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ItemStack held = player.getMainHandItem();
                            if (held.isEmpty()) {
                                ctx.getSource().sendFailure(Component.translatable("command.sellbox.no_item"));
                                return 0;
                            }
                            int price = SellBoxConfig.getPrice(held);
                            if (price <= 0) {
                                ctx.getSource().sendFailure(Component.translatable("command.sellbox.sell_no_price", BuiltInRegistries.ITEM.getKey(held.getItem()).toString()));
                                return 0;
                            }
                            int totalValue = price * held.getCount();
                            SellBoxEconomy.addBalance(player, totalValue);
                            ModNetwork.sendBalanceToPlayer(player);
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
                                    ModNetwork.broadcastAllPrices(ctx.getSource().getServer());
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
                                    if (held.getTag() == null) {
                                        ctx.getSource().sendFailure(Component.translatable("command.sellbox.no_nbt"));
                                        return 0;
                                    }
                                    int price = IntegerArgumentType.getInteger(ctx, "price");
                                    SellBoxConfig.setPriceNbt(held, price);
                                    ModNetwork.broadcastAllPrices(ctx.getSource().getServer());
                                    String nbtStr = held.getTag() != null ? held.getTag().toString() : "";
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.sellbox.price_set_nbt",
                                            BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), nbtStr, String.valueOf(price)), true);
                                    return 1;
                                })))

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
                            ModNetwork.broadcastAllPrices(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.sellbox.price_removed", BuiltInRegistries.ITEM.getKey(item).toString()), true);
                            return 1;
                        }))

                .then(Commands.literal("handremovenbt")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ItemStack held = player.getMainHandItem();
                            if (held.isEmpty()) {
                                ctx.getSource().sendFailure(Component.translatable("command.sellbox.no_item"));
                                return 0;
                            }
                            SellBoxConfig.removePriceNbt(held);
                            ModNetwork.broadcastAllPrices(ctx.getSource().getServer());
                            String nbtStr = held.getTag() != null ? held.getTag().toString() : "";
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.sellbox.price_removed_nbt",
                                    BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), nbtStr), true);
                            return 1;
                        }))

                .then(Commands.literal("delnbt")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            int cleared = SellBoxConfig.clearPricesNbt();
                            ModNetwork.broadcastAllPrices(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.sellbox.nbt_prices_cleared", String.valueOf(cleared)), true);
                            return 1;
                        }))

                .then(Commands.literal("handprice")
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayerOrException();
                            ItemStack held = player.getMainHandItem();
                            if (held.isEmpty()) {
                                ctx.getSource().sendFailure(Component.translatable("command.sellbox.no_item"));
                                return 0;
                            }
                            int price = SellBoxConfig.getPrice(held);
                            ResourceLocation id = BuiltInRegistries.ITEM.getKey(held.getItem());
                            if (id.toString().equals("minecraft:air")) {
                                ctx.getSource().sendFailure(Component.translatable("command.sellbox.no_item"));
                            } else {
                                ctx.getSource().sendSuccess(
                                        () -> Component.translatable("command.sellbox.handprice", id.toString(), price), false
                                );
                            }
                            return 1;
                        }))

                .then(Commands.literal("setselltime")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("tick", IntegerArgumentType.integer(0, 24000))
                                .executes(ctx -> {
                                    int tick = IntegerArgumentType.getInteger(ctx, "tick");
                                    SellBoxConfig.SELL_TIME.set(tick);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Auto-sell time set to tick " + tick), true
                                    );
                                    return 1;
                                })))

                .then(Commands.literal("getselltime")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            int tick = SellBoxConfig.SELL_TIME.get();
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
                            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                    new ModNetwork.OpenPricingPacket());
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
                                            mode ? "编辑模式已开启" : "编辑模式已关闭"), true);
                                    return 1;
                                }))
                        .then(Commands.literal("open")
                                .executes(ctx -> {
                                    if (SDMShopIntegration.isLoaded() && SellBoxConfig.isVssCurrencyEnabled() && !SellBoxConfig.isShopOverrideEnabled()) {
                                        ctx.getSource().sendFailure(Component.literal("SDMShop经济模式下管理员商店已禁用，使用 /sellbox adminshop true 启用"));
                                        return 0;
                                    }
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                            new ModNetwork.OpenShopScreenPacket());
                                    return 1;
                                }))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    if (!(SDMShopIntegration.isLoaded() && SellBoxConfig.isVssCurrencyEnabled())) {
                                        ctx.getSource().sendFailure(Component.literal("该指令仅在使用SDMShop经济时可用"));
                                        return 0;
                                    }
                                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                    SellBoxConfig.setShopOverrideEnabled(enabled);
                                    broadcastShopConfig(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal("管理员商店已" + (enabled ? "启用" : "禁用")), true);
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

                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            SellBoxConfig.loadPricing();
                            PlayerShopConfig.load();
                            PlayerShopConfig.loadStats();
                            ShopConfig.load();
                            com.sellbox.mod.playershop.PlayerShopBlacklist.reload();
                            ModNetwork.broadcastAllPrices(ctx.getSource().getServer());
                            broadcastBlacklist(ctx.getSource().getServer());
                            broadcastAdminEdit(ctx.getSource().getServer());
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
                                                    ModNetwork.sendBalanceToPlayer(target);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("已给予 " + target.getName().getString() + " " + SellBoxEconomy.formatMoneyStatic(amount)), true);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    SellBoxEconomy.setBalance(target, amount);
                                                    ModNetwork.sendBalanceToPlayer(target);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("已设置 " + target.getName().getString() + " 余额为 " + SellBoxEconomy.formatMoneyStatic(amount)), true);
                                                    return 1;
                                                })))))

                .then(Commands.literal("SDMShop")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    if (value && !SDMShopIntegration.isLoaded()) {
                                        ctx.getSource().sendFailure(Component.literal("未安装SDMShop模组，无法切换到SDMShop经济系统"));
                                        return 0;
                                    }
                                    SellBoxConfig.setVssCurrencyEnabled(value);
                                    if (value) {
                                        SellBoxConfig.setShopOverrideEnabled(false);
                                    } else {
                                        SellBoxConfig.setShopOverrideEnabled(true);
                                    }
                                    var server = ctx.getSource().getServer();
                                    for (var p : server.getPlayerList().getPlayers()) {
                                        ModNetwork.sendBalanceToPlayer(p);
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("SDMShop经济已" + (value ? "启用" : "禁用") + "，使用 " + (value ? "SDMShop" : "SellBox内置") + " 经济系统"), true);
                                    broadcastShopConfig(server);
                                    return 1;
                                })))

                .then(Commands.literal("blacklist")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ItemStack held = player.getMainHandItem();
                                    if (held.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("§c手中没有物品"));
                                        return 0;
                                    }
                                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(held.getItem());
                                    if (id.toString().equals("minecraft:air")) {
                                        ctx.getSource().sendFailure(Component.literal("§c手中物品无效"));
                                        return 0;
                                    }
                                    if (PlayerShopBlacklist.isBlacklisted(id)) {
                                        ctx.getSource().sendFailure(Component.literal("§c该物品已在黑名单中"));
                                        return 0;
                                    }
                                    PlayerShopBlacklist.add(id);
                                    broadcastBlacklist(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal("§a已将 §e" + id + " §a加入黑名单"), true);
                                    return 1;
                                })
                                .then(Commands.argument("item", com.mojang.brigadier.arguments.StringArgumentType.string())
                                        .executes(ctx -> {
                                            String itemStr = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item");
                                            ResourceLocation id = ResourceLocation.tryParse(itemStr);
                                            if (id == null) {
                                                ctx.getSource().sendFailure(Component.literal("§c无效的物品ID格式"));
                                                return 0;
                                            }
                                            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) {
                                                ctx.getSource().sendFailure(Component.literal("§c物品 §e" + id + " §c不存在"));
                                                return 0;
                                            }
                                            if (PlayerShopBlacklist.isBlacklisted(id)) {
                                                ctx.getSource().sendFailure(Component.literal("§c该物品已在黑名单中"));
                                                return 0;
                                            }
                                            PlayerShopBlacklist.add(id);
                                            broadcastBlacklist(ctx.getSource().getServer());
                                            ctx.getSource().sendSuccess(() -> Component.literal("§a已将 §e" + id + " §a加入黑名单"), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("item", com.mojang.brigadier.arguments.StringArgumentType.string())
                                        .executes(ctx -> {
                                            String itemStr = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item");
                                            ResourceLocation id = ResourceLocation.tryParse(itemStr);
                                            if (id == null) {
                                                ctx.getSource().sendFailure(Component.literal("§c无效的物品ID格式"));
                                                return 0;
                                            }
                                            if (!PlayerShopBlacklist.remove(id)) {
                                                ctx.getSource().sendFailure(Component.literal("§c该物品不在黑名单中"));
                                                return 0;
                                            }
                                            broadcastBlacklist(ctx.getSource().getServer());
                                            ctx.getSource().sendSuccess(() -> Component.literal("§a已将 §e" + id + " §a从黑名单移除"), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    var all = PlayerShopBlacklist.getAll();
                                    if (all.isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("§7黑名单为空"), false);
                                        return 1;
                                    }
                                    MutableComponent msg = Component.literal("§6=== 物品黑名单 (" + all.size() + ") ===\n");
                                    int i = 0;
                                    for (ResourceLocation id : all) {
                                        msg.append(Component.literal("§7" + (i + 1) + ". §e" + id + "\n"));
                                        i++;
                                    }
                                    ctx.getSource().sendSuccess(() -> msg, false);
                                    return 1;
                                })))

                .then(Commands.literal("playershop")
                        .then(Commands.literal("edit")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    ShopConfig.toggleAdminEdit();
                                    boolean enabled = ShopConfig.isAdminEditEnabled();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            enabled ? "§a管理员编辑模式已开启" : "§a管理员编辑模式已关闭"), true);
                                    broadcastAdminEdit(ctx.getSource().getServer());
                                    return 1;
                                }))));
    }

    @SuppressWarnings("resource")
    private static void broadcastBlacklist(net.minecraft.server.MinecraftServer server) {
        var payload = new ModNetwork.SyncBlacklistPacket(
                com.sellbox.mod.playershop.PlayerShopBlacklist.getAsStringSet());
        for (var player : server.getPlayerList().getPlayers()) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        }
    }

    private static void broadcastAdminEdit(net.minecraft.server.MinecraftServer server) {
        var payload = new ModNetwork.SyncAdminEditPacket(ShopConfig.isAdminEditEnabled());
        for (var player : server.getPlayerList().getPlayers()) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        }
    }
}