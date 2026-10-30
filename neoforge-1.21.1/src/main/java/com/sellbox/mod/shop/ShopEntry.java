package com.sellbox.mod.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record ShopEntry(
        ResourceLocation itemId,
        TradeType tradeType,
        int price,
        String entryType,
        String extraData,
        boolean requireOp,
        int quantity,
        String customName,
        @Nullable ResourceLocation barterItemId,
        int barterItemQty,
        @Nullable Long requiredQuestId,
        @Nullable String requiredQuestName,
        boolean strictMatch,
        @Nullable String nbt,
        boolean barterOfferStrict,
        @Nullable String barterOfferNbt,
        boolean barterGoodsStrict,
        int tradeLimit,
        boolean limitPerPlayer
) {
    /** 兼容旧调用点：等价于「无限限量」 */
    public ShopEntry(ResourceLocation itemId, TradeType tradeType, int price,
                     String entryType, String extraData, boolean requireOp, int quantity, String customName,
                     @Nullable ResourceLocation barterItemId, int barterItemQty,
                     @Nullable Long requiredQuestId, @Nullable String requiredQuestName,
                     boolean strictMatch, @Nullable String nbt,
                     boolean barterOfferStrict, @Nullable String barterOfferNbt, boolean barterGoodsStrict) {
        this(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName,
                barterItemId, barterItemQty, requiredQuestId, requiredQuestName,
                strictMatch, nbt, barterOfferStrict, barterOfferNbt, barterGoodsStrict, 0, false);
    }

    public ShopEntry(ResourceLocation itemId, TradeType tradeType, int price) {
        this(itemId, tradeType, price, "item", "", false, 1, "", null, 1, null, null, false, null, false, null, false, 0, false);
    }

    public ShopEntry(ResourceLocation itemId, TradeType tradeType, int price,
                     String entryType, String extraData, boolean requireOp, int quantity, String customName) {
        this(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName, null, 1, null, null, false, null, false, null, false, 0, false);
    }

    public ShopEntry(ResourceLocation itemId, TradeType tradeType, int price,
                     String entryType, String extraData, boolean requireOp, int quantity, String customName,
                     @Nullable ResourceLocation barterItemId, int barterItemQty,
                     @Nullable Long requiredQuestId, @Nullable String requiredQuestName) {
        this(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName,
                barterItemId, barterItemQty, requiredQuestId, requiredQuestName, false, null, false, null, false, 0, false);
    }

    public ShopEntry(ResourceLocation itemId, TradeType tradeType, int price,
                     String entryType, String extraData, boolean requireOp, int quantity, String customName,
                     @Nullable ResourceLocation barterItemId, int barterItemQty,
                     @Nullable Long requiredQuestId, @Nullable String requiredQuestName,
                     boolean strictMatch, @Nullable String nbt) {
        this(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName,
                barterItemId, barterItemQty, requiredQuestId, requiredQuestName, strictMatch, nbt, false, null, false, 0, false);
    }

    public static ShopEntry item(ResourceLocation itemId, TradeType tradeType, int price) {
        return new ShopEntry(itemId, tradeType, price, "item", "", false, 1, "", null, 1, null, null);
    }

    public static ShopEntry item(ResourceLocation itemId, TradeType tradeType, int price, int quantity) {
        return new ShopEntry(itemId, tradeType, price, "item", "", false, quantity, "", null, 1, null, null);
    }

    public static ShopEntry item(ResourceLocation itemId, TradeType tradeType, int price, int quantity, String customName) {
        return new ShopEntry(itemId, tradeType, price, "item", "", false, quantity, customName, null, 1, null, null);
    }

    public static ShopEntry barter(ResourceLocation goodsItemId, int goodsQty, ResourceLocation offerItemId, int offerQty, String customName) {
        return new ShopEntry(goodsItemId, TradeType.SELL, 0, "barter", "", false, goodsQty, customName, offerItemId, offerQty, null, null);
    }

    public static ShopEntry command(String command, int price, boolean requireOp, ResourceLocation icon) {
        return new ShopEntry(icon, TradeType.BUY, price, "command", command, requireOp, 1, "", null, 1, null, null);
    }

    public static ShopEntry xp(int xpAmount, int price) {
        return new ShopEntry(ResourceLocation.withDefaultNamespace("experience_bottle"), TradeType.BUY, price, "xp", String.valueOf(xpAmount), false, 1, "", null, 1, null, null);
    }

    public enum TradeType {
        BUY, SELL;

        public static final Codec<TradeType> CODEC = Codec.STRING.xmap(
                s -> valueOf(s.toUpperCase()),
                TradeType::name
        );
    }


    public ShopEntry withPrerequisite(@Nullable Long questId, @Nullable String questName) {
        return new ShopEntry(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName, barterItemId, barterItemQty, questId, questName, strictMatch, nbt, barterOfferStrict, barterOfferNbt, barterGoodsStrict, tradeLimit, limitPerPlayer);
    }

    public ShopEntry withPrerequisite(@Nullable Long questId) {
        return new ShopEntry(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName, barterItemId, barterItemQty, questId, null, strictMatch, nbt, barterOfferStrict, barterOfferNbt, barterGoodsStrict, tradeLimit, limitPerPlayer);
    }

    /** 设置交易限量：limit<=0 表示无限；perPlayer=true 表示每个玩家各自计算，false 表示全体共用 */
    public ShopEntry withTradeLimit(int limit, boolean perPlayer) {
        return new ShopEntry(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName, barterItemId, barterItemQty, requiredQuestId, requiredQuestName, strictMatch, nbt, barterOfferStrict, barterOfferNbt, barterGoodsStrict, Math.max(0, limit), perPlayer);
    }

    public boolean hasTradeLimit() {
        return tradeLimit > 0;
    }

    public boolean hasPrerequisite() {
        return requiredQuestId != null;
    }

    public boolean isBarter() {
        return "barter".equals(entryType) && barterItemId != null;
    }

    public ItemStack createStack() {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        ItemStack stack = new ItemStack(item);
        if (quantity > 1) stack.setCount(quantity);
        return stack;
    }

    /** 严格匹配模式：按注册表数据应用 NBT(组件补丁)，否则等同 createStack()。barter 条目按 barterGoodsStrict 应用商品物 NBT */
    public ItemStack createStack(RegistryAccess access) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        ItemStack stack = new ItemStack(item);
        boolean applyNbt = "barter".equals(entryType) ? (barterGoodsStrict && nbt != null) : (strictMatch && nbt != null);
        if (applyNbt && access != null) {
            DataComponentPatch patch = componentPatchFromJson(nbt, access);
            if (!patch.isEmpty()) stack.applyComponents(patch);
        }
        if (quantity > 1) stack.setCount(quantity);
        return stack;
    }

    /** 判断候选物品是否满足本条目要求：ID相同模式只看物品，严格匹配模式需组件完全一致 */
    public boolean matchesStack(ItemStack candidate, RegistryAccess access) {
        if (candidate == null || candidate.isEmpty()) return false;
        if (!candidate.is(BuiltInRegistries.ITEM.get(itemId))) return false;
        if (!strictMatch) return true;
        ItemStack template = createStack(access);
        return ItemStack.isSameItemSameComponents(candidate, template);
    }

    /** 组件补丁 → JSON 字符串（用于网络/配置传输） */
    @Nullable
    public static String componentPatchToJson(@Nullable DataComponentPatch patch, RegistryAccess access) {
        if (patch == null || patch.isEmpty() || access == null) return null;
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
            return DataComponentPatch.CODEC.encodeStart(ops, patch).result()
                    .map(JsonElement::toString).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** JSON 字符串 → 组件补丁（解析失败降级为空补丁） */
    @Nullable
    public static DataComponentPatch componentPatchFromJson(@Nullable String json, RegistryAccess access) {
        if (json == null || json.isEmpty() || access == null) return DataComponentPatch.EMPTY;
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
            return DataComponentPatch.CODEC.parse(ops, JsonParser.parseString(json))
                    .result().orElse(DataComponentPatch.EMPTY);
        } catch (Exception e) {
            return DataComponentPatch.EMPTY;
        }
    }

    public ItemStack createBarterOfferStack() {
        if (barterItemId == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(barterItemId);
        ItemStack stack = new ItemStack(item);
        int q = Math.max(1, barterItemQty);
        if (q > 1) stack.setCount(q);
        return stack;
    }

    /** 以物易物出价物模板：严格匹配时按注册表应用保存的 NBT */
    public ItemStack createBarterOfferStack(RegistryAccess access) {
        if (barterItemId == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(barterItemId);
        ItemStack stack = new ItemStack(item);
        if (barterOfferStrict && barterOfferNbt != null && access != null) {
            DataComponentPatch patch = componentPatchFromJson(barterOfferNbt, access);
            if (!patch.isEmpty()) stack.applyComponents(patch);
        }
        int q = Math.max(1, barterItemQty);
        if (q > 1) stack.setCount(q);
        return stack;
    }

    /** 以物易物商品物模板：barterGoodsStrict(严格NBT)时应用上架时从背包选中的 NBT，物品ID模式为普通物品 */
    public ItemStack createBarterGoodsStack(RegistryAccess access) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        ItemStack stack = new ItemStack(item);
        boolean applyNbt = barterGoodsStrict && nbt != null;
        if (applyNbt && access != null) {
            DataComponentPatch patch = componentPatchFromJson(nbt, access);
            if (!patch.isEmpty()) stack.applyComponents(patch);
        }
        if (quantity > 1) stack.setCount(quantity);
        return stack;
    }

    /** 以物易物出价物匹配：受 barterOfferStrict 控制，严格匹配时需组件完全一致 */
    public boolean matchesOffer(ItemStack candidate, RegistryAccess access) {
        if (candidate == null || candidate.isEmpty() || barterItemId == null) return false;
        if (!candidate.is(BuiltInRegistries.ITEM.get(barterItemId))) return false;
        if (!barterOfferStrict) return true;
        ItemStack template = createBarterOfferStack(access);
        return ItemStack.isSameItemSameComponents(candidate, template);
    }

    /** 以物易物商品物匹配：受 barterGoodsStrict 控制，严格匹配时需组件完全一致（备货台取货时用） */
    public boolean matchesBarterGoods(ItemStack candidate, RegistryAccess access) {
        if (candidate == null || candidate.isEmpty()) return false;
        if (!candidate.is(BuiltInRegistries.ITEM.get(itemId))) return false;
        if (!barterGoodsStrict) return true;
        ItemStack template = createBarterGoodsStack(access);
        return ItemStack.isSameItemSameComponents(candidate, template);
    }

    private String barterOfferDisplayName() {
        ItemStack offerStack = createBarterOfferStack();
        if (offerStack.isEmpty()) return "?";
        return offerStack.getHoverName().getString();
    }

    public Component getDisplayName() {
        if (!customName.isEmpty()) return Component.literal(customName);
        if ("command".equals(entryType)) {
            return Component.literal("/" + extraData);
        }
        if ("xp".equals(entryType)) {
            return Component.literal("经验 xp" + extraData);
        }
        return createStack().getHoverName();
    }

    public Component getBarterDisplay() {
        if (!isBarter()) return Component.empty();
        String goodsName = getDisplayName().getString();
        String offerName = barterOfferDisplayName();
        int goodsQ = Math.max(1, quantity);
        int offerQ = Math.max(1, barterItemQty);
        String text = offerName + " x" + offerQ + " → " + goodsName + " x" + goodsQ;
        return Component.literal(text);
    }
}
