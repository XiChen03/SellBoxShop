package com.sellbox.mod.integration;

import com.sellbox.mod.SellBoxMod;
import com.sellbox.mod.registry.ModItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

public class CuriosIntegration {
    private static boolean curiosLoaded = false;
    private static Class<?> curiosApiClass;
    private static Class<?> iCurioItemClass;
    private static Class<?> slotContextClass;

    static {
        try {
            curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            iCurioItemClass = Class.forName("top.theillusivec4.curios.api.type.capability.ICurioItem");
            slotContextClass = Class.forName("top.theillusivec4.curios.api.SlotContext");
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

    public static void registerMoneyCurio() {
        if (!curiosLoaded) return;
        try {
            Object iCurioItemInstance = Proxy.newProxyInstance(
                    iCurioItemClass.getClassLoader(),
                    new Class[]{iCurioItemClass},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("canEquip")) return true;
                        if (name.equals("canEquipFromUse")) return false;
                        if (name.equals("canSync")) return true;
                        if (name.equals("hasCurioCapability")) return true;
                        if (method.isDefault()) {
                            return InvocationHandler.invokeDefault(proxy, method, args);
                        }
                        return null;
                    }
            );
            Method registerCurio = curiosApiClass.getMethod("registerCurio", Item.class, iCurioItemClass);
            registerCurio.invoke(null, ModItems.MONEY.get(), iCurioItemInstance);
            SellBoxMod.LOGGER.info("Registered MoneyItem as curios for all slots");
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to register money curio", e);
        }
    }

    public static boolean hasMoneyInCurios(LivingEntity entity) {
        return !getMoneyInCurios(entity).isEmpty();
    }

    public static ItemStack getMoneyInCurios(LivingEntity entity) {
        if (!curiosLoaded) return ItemStack.EMPTY;
        try {
            Optional<?> inventory = (Optional<?>) curiosApiClass.getMethod("getCuriosInventory", LivingEntity.class)
                    .invoke(null, entity);
            if (inventory.isPresent()) {
                Object handler = inventory.get();
                Method getEquipped = handler.getClass().getMethod("getEquippedCurios");
                Object curiosInv = getEquipped.invoke(handler);
                Method getSize = curiosInv.getClass().getMethod("getSlots");
                Method getItem = curiosInv.getClass().getMethod("getStackInSlot", int.class);
                int size = (int) getSize.invoke(curiosInv);
                for (int i = 0; i < size; i++) {
                    ItemStack stack = (ItemStack) getItem.invoke(curiosInv, i);
                    if (stack.is(ModItems.MONEY.get())) {
                        return stack;
                    }
                }
            }
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to check curios money", e);
        }
        return ItemStack.EMPTY;
    }

    public static boolean hasMoneyEquipped(LivingEntity entity) {
        if (entity == null) return false;
        if (hasMoneyInCurios(entity)) return true;
        // 无 Curios 时检查头盔栏是否有钞票（用于显示余额 HUD）
        ItemStack helmet = entity.getItemBySlot(EquipmentSlot.HEAD);
        return helmet.is(ModItems.MONEY.get());
    }

    public static boolean tryEquipToCurios(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        if (!curiosLoaded || player == null || stack.isEmpty()) return false;
        try {
            Optional<?> inventory = (Optional<?>) curiosApiClass.getMethod("getCuriosInventory", LivingEntity.class)
                    .invoke(null, player);
            if (inventory.isPresent()) {
                Object handler = inventory.get();
                Method getEquipped = handler.getClass().getMethod("getEquippedCurios");
                Object curiosInv = getEquipped.invoke(handler);
                Method getSize = curiosInv.getClass().getMethod("getSlots");
                Method getItem = curiosInv.getClass().getMethod("getStackInSlot", int.class);
                int size = (int) getSize.invoke(curiosInv);
                for (int i = 0; i < size; i++) {
                    ItemStack slotStack = (ItemStack) getItem.invoke(curiosInv, i);
                    if (slotStack.isEmpty()) {
                        Method setItem = curiosInv.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
                        setItem.invoke(curiosInv, i, stack.copy());
                        stack.setCount(0);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            SellBoxMod.LOGGER.error("Failed to equip to curios slot", e);
        }
        return false;
    }
}
