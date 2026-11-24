package com.sellbox.mod.extras;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 扫地女仆：定时清理掉落物/额外实体，物品进垃圾箱。 */
public final class ExtrasSweeper {
    private static int remain = -1;
    private static int lastInterval = -1;

    private ExtrasSweeper() {
    }

    public static void tick(MinecraftServer server) {
        if (!com.sellbox.mod.config.SellBoxConfig.isExtrasEnabled()) {
            remain = -1;
            return;
        }
        if (!ExtrasConfigFile.isSweeperEnabled()) {
            remain = -1;
            return;
        }
        int interval = ExtrasConfigFile.getSweeperInterval();
        if (interval <= 0) {
            remain = -1;
            return;
        }
        int total = interval * 20;
        if (remain < 0 || remain > total || interval != lastInterval) {
            remain = total;
            lastInterval = interval;
        }
        remain--;
        int sec = remain / 20;
        if (remain == 60 * 20 || remain == 30 * 20 || remain == 15 * 20
                || (remain % 20 == 0 && sec >= 1 && sec <= 10)) {
            String msg = ExtrasConfigFile.getSweeperCountdownMessage()
                    .replace("$1", String.valueOf(sec)).replace("%s", String.valueOf(sec));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.GRAY), true);
            }
        }
        if (remain <= 0) {
            remain = total;
            sweep(server);
        }
    }

    public static void cleanNow(MinecraftServer server) {
        if (!com.sellbox.mod.config.SellBoxConfig.isExtrasEnabled()) return;
        remain = ExtrasConfigFile.getSweeperInterval() * 20;
        sweep(server);
    }

    private static void sweep(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        SweeperSavedData data = SweeperSavedData.get(overworld);
        data.ensure(ExtrasConfigFile.getSweeperDustbins());

        List<String> blacklist = ExtrasConfigFile.getSweeperBlacklist();
        List<String> whitelist = ExtrasConfigFile.getSweeperWhitelist();
        List<String> extra = ExtrasConfigFile.getSweeperExtraEntities();
        int dropped = 0;
        int killedExtra = 0;
        Map<String, Integer> overload = new HashMap<>();

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity itemEntity) {
                    ItemStack stack = itemEntity.getItem();
                    if (stack.isEmpty()) continue;
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (whitelist.contains(id)) continue;
                    if (!blacklist.contains(id)) {
                        data.add(stack);
                        dropped += stack.getCount();
                        LevelChunk chunk = level.getChunkAt(entity.blockPosition());
                        String key = level.dimension().location() + ":" + chunk.getPos().x + ":" + chunk.getPos().z + ":" + id;
                        overload.merge(key, stack.getCount(), Integer::sum);
                    } else {
                        dropped += stack.getCount();
                    }
                    entity.discard();
                } else if (entity != null && !(entity instanceof Player)) {
                    String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                    if (extra.contains(id)) {
                        entity.discard();
                        killedExtra++;
                    }
                }
            }
        }

        String after = ExtrasConfigFile.getSweeperAfterMessage()
                .replace("$1", String.valueOf(dropped)).replaceFirst("%s", String.valueOf(dropped))
                .replace("$2", String.valueOf(killedExtra)).replaceFirst("%s", String.valueOf(killedExtra));
        MutableComponent chat = Component.literal(ExtrasConfigFile.getSweeperChatMessage());
        boolean any = false;
        for (int i = 0; i < data.dustbins.size(); i++) {
            if (data.dustbins.get(i).isEmpty()) continue;
            any = true;
            final int idx = i;
            chat.append(Component.literal("垃圾桶 [" + (i + 1) + "]")
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sellbox dustbin " + (idx + 1)))));
            chat.append(Component.literal(" "));
        }
        if (!any) chat.append(Component.literal("（本次没有可回收物品）").withStyle(ChatFormatting.GRAY));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.displayClientMessage(Component.literal(after).withStyle(ChatFormatting.AQUA), true);
            p.sendSystemMessage(chat.copy());
        }
        int threshold = ExtrasConfigFile.getSweeperOverload();
        for (var e : overload.entrySet()) {
            if (e.getValue() > threshold) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p.hasPermissions(2)) {
                        p.sendSystemMessage(Component.literal("[扫地女仆]：区域 " + e.getKey()
                                + " 掉落物过多，共 " + e.getValue() + " 个。").withStyle(ChatFormatting.BLUE));
                    }
                }
            }
        }
        data.setDirty();
    }

    public static void openDustbin(ServerPlayer player, int index) {
        if (!com.sellbox.mod.config.SellBoxConfig.isExtrasEnabled()) {
            player.displayClientMessage(Component.literal("扩展功能已关闭"), true);
            return;
        }
        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        SweeperSavedData data = SweeperSavedData.get(overworld);
        SimpleContainer bin = data.bin(index);
        if (bin == null) {
            player.displayClientMessage(Component.literal("没有这个垃圾箱"), true);
            return;
        }
        final int idx = index;
        player.openMenu(new net.minecraft.world.MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("垃圾箱 " + (idx + 1));
            }

            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                    int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
                return ChestMenu.sixRows(id, inv, bin);
            }
        });
    }
}
