package com.sellbox.mod.integration;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

public class CuriosIntegration {
    private static boolean curiosLoaded = false;
    private static Class<?> curiosApiClass;
    private static Class<?> iCurioItemClass;
    private static Method registerCurioMethod;

    static {
        try {
            curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            iCurioItemClass = Class.forName("top.theillusivec4.curios.api.type.capability.ICurioItem");
            registerCurioMethod = curiosApiClass.getMethod("registerCurio", net.minecraft.world.item.Item.class, iCurioItemClass);
            curiosLoaded = true;
            SellBoxMod.LOGGER.info("Curios integration loaded successfully");
        } catch (Exception e) {
            SellBoxMod.LOGGER.info("Curios mod not found, curios integration disabled");
            curiosLoaded = false;
        }
    }

    public static boolean isLoaded() {
        return curiosLoaded;
    }

    /**
     * 通过 registerCurio 注册 ICurioItem，与 tag 文件配合确保服务端也识别此物品为饰品
     */
    public static void registerMoneyCurio() {
        if (!curiosLoaded) return;
        try {
            Object instance = java.lang.reflect.Proxy.newProxyInstance(
                    iCurioItemClass.getClassLoader(),
                    new Class[]{iCurioItemClass},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("canEquip") || name.equals("canUnequip") || name.equals("canEquipFromUse") || name.equals("canSync") || name.equals("hasCurioCapability")) return true;
                        if (method.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                        return null;
                    }
            );
            registerCurioMethod.invoke(null, ModItems.MONEY.get(), instance);
            SellBoxMod.LOGGER.info("Registered MoneyItem as curio via registerCurio");
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to register money curio", e);
        }
    }

    /**
     * 创建 ICurio capability provider，使物品在 Curios 反序列化时能通过 isItemValid 检查
     * 这是关键：没有这个 provider，readTag 中的 isItemValid 会返回 false，物品会被弹出到背包
     */
    @javax.annotation.Nullable
    public static net.minecraftforge.common.capabilities.ICapabilityProvider createCurioProvider(ItemStack stack) {
        if (!curiosLoaded) return null;
        try {
            Class<?> iCurioClass = Class.forName("top.theillusivec4.curios.api.type.capability.ICurio");
            java.lang.reflect.Method createProvider = curiosApiClass.getMethod("createCurioProvider", iCurioClass);
            Object iCurioInstance = java.lang.reflect.Proxy.newProxyInstance(
                    iCurioClass.getClassLoader(),
                    new Class[]{iCurioClass},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("getStack")) return stack;
                        if (name.equals("canEquip") || name.equals("canUnequip") || name.equals("canEquipFromUse") || name.equals("canSync")) return true;
                        if (name.equals("hasCurioCapability")) return true;
                        if (method.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                        return null;
                    }
            );
            return (net.minecraftforge.common.capabilities.ICapabilityProvider) createProvider.invoke(null, iCurioInstance);
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to create curio provider", e);
            return null;
        }
    }

    private static Object getCuriosInventory(LivingEntity entity) {
        if (!curiosLoaded) return null;
        try {
            Object result = curiosApiClass.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, entity);
            if (result instanceof Optional<?> opt) return opt.orElse(null);
            try {
                Optional<?> resolved = (Optional<?>) result.getClass().getMethod("resolve").invoke(result);
                return resolved.orElse(null);
            } catch (Exception e2) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasMoneyInCurios(LivingEntity entity) {
        return !getMoneyInCurios(entity).isEmpty();
    }

    public static ItemStack getMoneyInCurios(LivingEntity entity) {
        if (!curiosLoaded) return ItemStack.EMPTY;
        try {
            Object handler = getCuriosInventory(entity);
            if (handler == null) return ItemStack.EMPTY;
            java.lang.reflect.Method getEquipped = handler.getClass().getMethod("getEquippedCurios");
            Object curiosInv = getEquipped.invoke(handler);
            java.lang.reflect.Method getSize = curiosInv.getClass().getMethod("getSlots");
            java.lang.reflect.Method getItem = curiosInv.getClass().getMethod("getStackInSlot", int.class);
            int size = (int) getSize.invoke(curiosInv);
            for (int i = 0; i < size; i++) {
                ItemStack stack = (ItemStack) getItem.invoke(curiosInv, i);
                if (stack.is(ModItems.MONEY.get())) return stack;
            }
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to check curios money", e);
        }
        return ItemStack.EMPTY;
    }

    private static boolean cachedHasMoney = false;
    private static long lastMoneyCheck = 0;

    public static boolean hasMoneyEquipped(LivingEntity entity) {
        if (entity == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastMoneyCheck > 500) {
            lastMoneyCheck = now;
            cachedHasMoney = hasMoneyInCurios(entity) || entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.MONEY.get());
        }
        return cachedHasMoney;
    }

    public static boolean tryEquipToCurios(Player player, ItemStack stack) {
        if (!curiosLoaded || player == null || stack.isEmpty()) return false;
        try {
            Object handler = getCuriosInventory(player);
            if (handler == null) return false;
            java.lang.reflect.Method getEquipped = handler.getClass().getMethod("getEquippedCurios");
            Object curiosInv = getEquipped.invoke(handler);
            java.lang.reflect.Method getSize = curiosInv.getClass().getMethod("getSlots");
            java.lang.reflect.Method getItem = curiosInv.getClass().getMethod("getStackInSlot", int.class);
            int size = (int) getSize.invoke(curiosInv);
            for (int i = 0; i < size; i++) {
                ItemStack slotStack = (ItemStack) getItem.invoke(curiosInv, i);
                if (slotStack.isEmpty()) {
                    java.lang.reflect.Method setItem = curiosInv.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
                    setItem.invoke(curiosInv, i, stack.copy());
                    stack.setCount(0);
                    return true;
                }
            }
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to equip to curios slot", e);
        }
        return false;
    }

    public static boolean restoreMoneyOnLogin(ServerPlayer player) {
        if (!curiosLoaded || player == null) return false;
        if (hasMoneyInCurios(player)) return true;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.MONEY.get())) {
                if (tryEquipToCurios(player, stack)) {
                    SellBoxMod.LOGGER.info("Restored MoneyItem to curios slot for player {}", player.getName().getString());
                    return true;
                }
            }
        }
        return false;
    }
}