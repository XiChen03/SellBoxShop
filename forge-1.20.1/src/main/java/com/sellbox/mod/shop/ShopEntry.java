package com.sellbox.mod.shop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
        @Nullable CompoundTag nbt,
        boolean strictMatch,
        @Nullable CompoundTag barterNbt,
        boolean barterStrictMatch,
        int tradeLimit,
        boolean limitPerPlayer
) {
    /** 兼容旧调用点：等价于「无限限量」 */
    public ShopEntry(ResourceLocation itemId, TradeType tradeType, int price,
                     String entryType, String extraData, boolean requireOp, int quantity, String customName,
                     @Nullable ResourceLocation barterItemId, int barterItemQty,
                     @Nullable Long requiredQuestId, @Nullable String requiredQuestName,
                     @Nullable CompoundTag nbt, boolean strictMatch,
                     @Nullable CompoundTag barterNbt, boolean barterStrictMatch) {
        this(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName,
                barterItemId, barterItemQty, requiredQuestId, requiredQuestName,
                nbt, strictMatch, barterNbt, barterStrictMatch, 0, false);
    }

    public ShopEntry(ResourceLocation itemId, TradeType tradeType, int price) {
        this(itemId, tradeType, price, "item", "", false, 1, "", null, 1, null, null, null, false, null, false, 0, false);
    }

    public ShopEntry(ResourceLocation itemId, TradeType tradeType, int price,
                     String entryType, String extraData, boolean requireOp, int quantity, String customName) {
        this(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName, null, 1, null, null, null, false, null, false, 0, false);
    }

    public ShopEntry(ResourceLocation itemId, TradeType tradeType, int price,
                     String entryType, String extraData, boolean requireOp, int quantity, String customName,
                     @Nullable ResourceLocation barterItemId, int barterItemQty,
                     @Nullable Long requiredQuestId, @Nullable String requiredQuestName) {
        this(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName,
                barterItemId, barterItemQty, requiredQuestId, requiredQuestName, null, false, null, false, 0, false);
    }

    public ShopEntry(ResourceLocation itemId, TradeType tradeType, int price,
                     String entryType, String extraData, boolean requireOp, int quantity, String customName,
                     @Nullable ResourceLocation barterItemId, int barterItemQty,
                     @Nullable Long requiredQuestId, @Nullable String requiredQuestName,
                     @Nullable CompoundTag nbt, boolean strictMatch) {
        this(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName,
                barterItemId, barterItemQty, requiredQuestId, requiredQuestName, nbt, strictMatch, null, false, 0, false);
    }

    /** 设置交易限量：limit<=0 表示无限；perPlayer=true 表示每个玩家各自计算，false 表示全体共用 */
    public ShopEntry withTradeLimit(int limit, boolean perPlayer) {
        return new ShopEntry(itemId, tradeType, price, entryType, extraData, requireOp, quantity, customName,
                barterItemId, barterItemQty, requiredQuestId, requiredQuestName,
                nbt, strictMatch, barterNbt, barterStrictMatch, Math.max(0, limit), perPlayer);
    }

    public boolean hasTradeLimit() {
        return tradeLimit > 0;
    }

    public static ShopEntry item(ResourceLocation itemId, TradeType tradeType, int price) {
        return new ShopEntry(itemId, tradeType, price, "item", "", false, 1, "", null, 1, null, null, null, false, null, false, 0, false);
    }

    public static ShopEntry item(ResourceLocation itemId, TradeType tradeType, int price, int quantity) {
        return new ShopEntry(itemId, tradeType, price, "item", "", false, quantity, "", null, 1, null, null, null, false, null, false, 0, false);
    }

    public static ShopEntry item(ResourceLocation itemId, TradeType tradeType, int price, int quantity, String customName) {
        return new ShopEntry(itemId, tradeType, price, "item", "", false, quantity, customName, null, 1, null, null, null, false, null, false, 0, false);
    }

    public static ShopEntry itemWithNbt(ResourceLocation itemId, TradeType tradeType, int price, int quantity,
                                        String customName, @Nullable CompoundTag nbt, boolean strictMatch) {
        return new ShopEntry(itemId, tradeType, price, "item", "", false, quantity, customName, null, 1, null, null, nbt, strictMatch, null, false);
    }

    public static ShopEntry barter(ResourceLocation goodsItemId, int goodsQty, ResourceLocation offerItemId, int offerQty, String customName) {
        return new ShopEntry(goodsItemId, TradeType.SELL, 0, "barter", "", false, goodsQty, customName, offerItemId, offerQty, null, null, null, false, null, false, 0, false);
    }

    public static ShopEntry barterWithNbt(ResourceLocation goodsItemId, int goodsQty, @Nullable CompoundTag goodsNbt,
                                          ResourceLocation offerItemId, int offerQty, @Nullable CompoundTag offerNbt,
                                          String customName, boolean goodsStrictMatch, boolean offerStrictMatch) {
        return new ShopEntry(goodsItemId, TradeType.SELL, 0, "barter", "", false, goodsQty, customName,
                offerItemId, offerQty, null, null, goodsNbt, goodsStrictMatch, offerNbt, offerStrictMatch, 0, false);
    }

    public static ShopEntry command(String command, int price, boolean requireOp, ResourceLocation icon) {
        return new ShopEntry(icon, TradeType.BUY, price, "command", command, requireOp, 1, "", null, 1, null, null, null, false, null, false, 0, false);
    }

    public static ShopEntry command(String command, int price, boolean requireOp, ResourceLocation icon, String customName) {
        return new ShopEntry(icon, TradeType.BUY, price, "command", command, requireOp, 1, customName, null, 1, null, null, null, false, null, false, 0, false);
    }

    public static ShopEntry xp(int xpAmount, int price) {
        return new ShopEntry(new ResourceLocation("experience_bottle"), TradeType.BUY, price, "xp", String.valueOf(xpAmount), false, 1, "", null, 1, null, null, null, false, null, false, 0, false);
    }

    public enum TradeType {
        BUY, SELL;
    }

    public boolean isBarter() {
        return "barter".equals(entryType) && barterItemId != null;
    }

    public ItemStack createStack() {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        ItemStack stack = new ItemStack(item);
        if (nbt != null) {
            CompoundTag tag = nbt.copy();
            tag.remove("Count");
            stack.setTag(tag);
        }
        if (quantity > 1) stack.setCount(quantity);
        return stack;
    }

    /**
     * 判断给定物品是否匹配本商品。
     * strictMatch=true 时：物品 ID 与 NBT 都相同才算匹配（忽略数量）；
     * strictMatch=false 时：仅物品 ID 相同即匹配。
     */
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemStack template = createStack();
        if (!stack.is(template.getItem())) return false;
        if (!strictMatch) return true;
        return ItemStack.isSameItemSameTags(stack, template);
    }

    public ItemStack createBarterOfferStack() {
        if (barterItemId == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(barterItemId);
        ItemStack stack = new ItemStack(item);
        if (barterNbt != null) {
            CompoundTag tag = barterNbt.copy();
            tag.remove("Count");
            stack.setTag(tag);
        }
        int q = Math.max(1, barterItemQty);
        if (q > 1) stack.setCount(q);
        return stack;
    }

    /**
     * 判断玩家背包中的物品是否匹配本 barter 条目的出价物品（offer）。
     * barterStrictMatch=true 时：物品 ID 与 NBT 都相同才算匹配（忽略数量）；
     * barterStrictMatch=false 时：仅物品 ID 相同即匹配。
     */
    public boolean matchesOffer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemStack template = createBarterOfferStack();
        if (!stack.is(template.getItem())) return false;
        if (!barterStrictMatch) return true;
        return ItemStack.isSameItemSameTags(stack, template);
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
            return Component.literal("经验瓶 (" + extraData + " XP)");
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
