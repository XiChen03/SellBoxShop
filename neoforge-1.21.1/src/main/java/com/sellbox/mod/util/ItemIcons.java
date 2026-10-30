package com.sellbox.mod.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public class ItemIcons {

    private ItemIcons() {}

    /**
     * 物品图标渲染统一入口。
     *
     * <p>不要直接用 {@code GuiGraphics#renderFakeItem}：它内部走的是
     * {@code renderItem(entity=null, level, stack, x, y, seed=0)}，而原版背包槽位走的是
     * {@code renderItem(minecraft.player, level, stack, x, y, 格位seed)}
     * （见 {@code AbstractContainerScreen#renderSlotContents}，全游戏只有合成产物槽
     * {@code ResultSlot#isFake()} 才返回 true 从而改用 fake）。
     *
     * <p>部分模组的物品模型 override 依赖 entity / seed 才能选中正确的模型，entity 为 null、
     * seed 为 0 时会回落到缺失模型，图标就变成黑框（森罗物语等带动态贴图的物品）。
     */
    public static void render(GuiGraphics g, ItemStack stack, int x, int y) {
        // 与原版槽位一致：用格位坐标生成 seed，保证同一界面里不同格位的 seed 不相同
        render(g, stack, x, y, x + y * 64);
    }

    /** 图标在经过 translate 的坐标系内绘制时，用调用方的绝对格位坐标作为 seed */
    public static void render(GuiGraphics g, ItemStack stack, int x, int y, int seed) {
        g.renderItem(stack, x, y, seed);
    }
}