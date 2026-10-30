package com.sellbox.mod.item;

import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 当 Curios 模组加载时使用的 MoneyItem 子类，直接实现 ICurioItem 接口
 * 这样 Curios 系统在反序列化时能正确识别此物品为饰品，不会弹出到背包
 */
public class CuriosMoneyItem extends MoneyItem implements ICurioItem {
    public CuriosMoneyItem(Properties properties) {
        super(properties);
    }
}