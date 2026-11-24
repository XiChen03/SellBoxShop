package com.sellbox.mod.extras;

import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 扩展模块共用的物品序列化工具。
 * <p>
 * 存储格式：{@code {"id":"模组:物品","count":N,"nbt":"<NBT 字符串>"}}。
 * {@code nbt} 是 {@link ItemStack#CODEC} 编码的完整栈（含组件/附魔/自定义名），
 * 只在拿到 {@link RegistryAccess} 时写入；读取时优先用它还原，失败则退回 id + count。
 */
public final class ExtrasItems {
    private ExtrasItems() {}

    /**
     * 物品 → JSON。
     *
     * @param stack  要保存的物品
     * @param access 注册表访问（服务端用 {@code level.registryAccess()}）；无则只存 id + count
     */
    public static JsonObject write(ItemStack stack, RegistryAccess access) {
        JsonObject o = new JsonObject();
        if (stack == null || stack.isEmpty()) {
            o.addProperty("empty", true);
            return o;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        o.addProperty("id", id == null ? "minecraft:air" : id.toString());
        o.addProperty("count", Math.max(1, stack.getCount()));

        if (access != null) {
            try {
                ItemStack single = stack.copy();
                single.setCount(1);
                Tag tag = ItemStack.CODEC
                        .encodeStart(access.createSerializationContext(NbtOps.INSTANCE), single)
                        .getOrThrow();
                o.addProperty("nbt", tag.getAsString());
            } catch (Throwable ignored) {
                // 编码失败就只存 id + count，功能不受影响
            }
        }
        return o;
    }

    /**
     * JSON → 物品。
     *
     * @param o      保存时的 JSON
     * @param access 注册表访问；为 null 时忽略 nbt 字段
     */
    public static ItemStack read(JsonObject o, RegistryAccess access) {
        if (o == null || o.has("empty") || !o.has("id")) return ItemStack.EMPTY;

        int count = o.has("count") ? Math.max(1, o.get("count").getAsInt()) : 1;

        if (access != null && o.has("nbt")) {
            try {
                Tag tag = net.minecraft.nbt.TagParser.parseTag(o.get("nbt").getAsString());
                ItemStack decoded = ItemStack.CODEC
                        .parse(access.createSerializationContext(NbtOps.INSTANCE), tag)
                        .getOrThrow();
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
