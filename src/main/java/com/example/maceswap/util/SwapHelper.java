package com.example.maceswap.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

public final class SwapHelper {

    private SwapHelper() {}

    public static boolean canPerformMaceSwap(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        if (player.isSpectator()) {
            return false;
        }

        ItemStack chestStack = player.getEquippedStack(EquipmentSlot.CHEST);
        if (!isGlider(chestStack)) {
            return false;
        }

        // Trigger if actively gliding OR airborne with an Elytra equipped
        boolean airborneOrGliding = player.isGliding() || !player.isOnGround() || ((GlidingFallTracker) player).maceswap_getEffectiveFallDistance() > 1.5F;
        if (!airborneOrGliding) {
            return false;
        }

        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.isOf(Items.MACE)) {
            return true;
        }
        return findMaceHotbarSlot(player.getInventory()) != -1;
    }

    public static boolean isGlider(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.isOf(Items.ELYTRA)) {
            return true;
        }
        return stack.contains(DataComponentTypes.GLIDER);
    }

    public static boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isGlider(stack)) {
            return false;
        }
        if (stack.isIn(ItemTags.CHEST_ARMOR)) {
            return true;
        }
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    public static int findChestplateHotbarSlot(PlayerInventory inventory) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (isChestplate(stack)) {
                return i;
            }
        }
        return -1;
    }

    public static int findMaceHotbarSlot(PlayerInventory inventory) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(Items.MACE)) {
                return i;
            }
        }
        return -1;
    }

    public static void swapChestWithHotbar(PlayerEntity player, int hotbarSlot) {
        if (hotbarSlot < 0) {
            return;
        }
        if (hotbarSlot > 8) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack chestStack = player.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack hotbarStack = inventory.getStack(hotbarSlot);

        player.equipStack(EquipmentSlot.CHEST, hotbarStack);
        inventory.setStack(hotbarSlot, chestStack);

        inventory.markDirty();
        if (!player.getEntityWorld().isClient()) {
            player.playerScreenHandler.sendContentUpdates();
        }
    }
}
