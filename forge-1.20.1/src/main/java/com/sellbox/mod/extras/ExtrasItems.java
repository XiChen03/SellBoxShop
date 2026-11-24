package com.sellbox.mod.extras;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 扩展模块共用的物品序列化工具（1.20.1）。
 * <p>
 * 存储格式：{@code {"id":"模组:物品","count":N,"nbt":"<SNBT>"}}。
 * 1.20.1 没有组件补丁，直接用 {@code ItemStack.save(CompoundTag)} → SNBT 字符串，
 * 读取时 {@code ItemStack.of(tag)}，能完整保留附魔/自定义名/所有 NBT。
 */
public final class ExtrasItems {
    private ExtrasItems() {}

    /**
     * 物品 → JSON。
     *
     * @param stack 要保存的物品
     */
    public static JsonObject write(ItemStack stack) {
        JsonObject o = new JsonObject();
        if (stack == null || stack.isEmpty()) {
            o.addProperty("empty", true);
            return o;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        o.addProperty("id", id == null ? "minecraft:air" : id.toString());
        o.addProperty("count", Math.max(1, stack.getCount()));

        try {
            CompoundTag tag = stack.save(new CompoundTag());
            // 去掉 count/id 重复信息没必要，保留最稳
            o.addProperty("nbt", tag.toString());
        } catch (Throwable ignored) {
            // 保存失败就只存 id + count
        }
        return o;
    }

    /**
     * JSON → 物品。
     */
    public static ItemStack read(JsonObject o) {
        if (o == null || o.has("empty") || !o.has("id")) return ItemStack.EMPTY;

        int count = o.has("count") ? Math.max(1, o.get("count").getAsInt()) : 1;

        if (o.has("nbt")) {
            try {
                CompoundTag tag = TagParser.parseTag(o.get("nbt").getAsString());
                ItemStack decoded = ItemStack.of(tag);
                if (!decoded.isEmpty()) {
                    decoded.setCount(count);
                    return decoded;
                }
            } catch (Throwable ignored) {
                // 落回 id + count
            }
        }

        try {
            ResourceLocation id = ResourceLocation.tryParse(o.get("id").getAsString());
            if (id == null) return ItemStack.EMPTY;
            return new ItemStack(BuiltInRegistries.ITEM.get(id), count);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** 兑换表匹配用的键：物品 ID（不区分 NBT） */
    public static String keyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    public static String nameOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return stack.getHoverName().getString();
    }
}
