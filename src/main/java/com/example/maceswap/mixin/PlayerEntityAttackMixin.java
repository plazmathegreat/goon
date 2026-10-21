package com.example.maceswap.mixin;

import com.example.maceswap.util.GlidingFallTracker;
import com.example.maceswap.util.SwapHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityAttackMixin extends LivingEntity {

    @Unique
    private int maceswap_serverOriginalWeaponSlot = -1;

    @Unique
    private int maceswap_serverElytraHotbarSlot = -1;

    @Unique
    private int maceswap_serverWindBurstTicks = 0;

    protected PlayerEntityAttackMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void maceswap_beforeServerAttack(Entity target, CallbackInfo ci) {
        this.maceswap_serverOriginalWeaponSlot = -1;
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (player.getEntityWorld().isClient()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack chestStack = player.getEquippedStack(EquipmentSlot.CHEST);

        // If wearing an Elytra on the server thread, perform direct server-side swap
        if (SwapHelper.isGlider(chestStack)) {
            int chestplateSlot = SwapHelper.findChestplateHotbarSlot(inventory);
            int maceSlot = SwapHelper.findMaceHotbarSlot(inventory);
            boolean holdingMace = player.getMainHandStack().isOf(Items.MACE);

            if (chestplateSlot != -1 && (holdingMace || maceSlot != -1)) {
                // 1. Swap Elytra -> Chestplate directly on server
                SwapHelper.swapChestWithHotbar(player, chestplateSlot);
                this.maceswap_serverElytraHotbarSlot = chestplateSlot;
                this.maceswap_serverWindBurstTicks = 10;

                // 2. Attribute Swap: Switch Sword/Axe -> Mace on server before damage calculation
                if (!holdingMace && maceSlot != -1) {
                    this.maceswap_serverOriginalWeaponSlot = inventory.getSelectedSlot();
                    inventory.setSelectedSlot(maceSlot);
                }

                // 3. Ensure fallDistance > 1.5F and cancel glide so Mace smash is 100% guaranteed
                float effectiveFall = ((GlidingFallTracker) this).maceswap_getEffectiveFallDistance();
                this.fallDistance = Math.max(effectiveFall, 5.0F);
                player.stopGliding();

                player.sendMessage(
                        Text.literal("Attribute Mace Smash! (Fall: " + String.format("%.1f", this.fallDistance) + "m)")
                                .formatted(Formatting.GOLD),
                        true
                );
                return;
            }
        }

        // Fallback if client packet already swapped the armor/slot prior to this method
        if (player.getMainHandStack().isOf(Items.MACE)) {
            float effectiveFall = ((GlidingFallTracker) this).maceswap_getEffectiveFallDistance();
            if (effectiveFall > 1.5F) {
                this.fallDistance = effectiveFall;
            }
            if (player.isGliding()) {
                player.stopGliding();
            }
        }
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void maceswap_afterServerAttack(Entity target, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.getEntityWorld().isClient()) {
            return;
        }

        // Immediately restore the original weapon (Sword/Axe) after the Mace hit resolves
        if (this.maceswap_serverOriginalWeaponSlot != -1) {
            PlayerInventory inventory = player.getInventory();
            inventory.setSelectedSlot(this.maceswap_serverOriginalWeaponSlot);
            if (player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(this.maceswap_serverOriginalWeaponSlot));
            }
            this.maceswap_serverOriginalWeaponSlot = -1;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void maceswap_onServerTick(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.getEntityWorld().isClient()) {
            return;
        }

        if (this.maceswap_serverElytraHotbarSlot == -1 || this.maceswap_serverWindBurstTicks <= 0) {
            return;
        }

        this.maceswap_serverWindBurstTicks--;

        // Check if Wind Burst bounced the player upward
        if (player.getVelocity().y > 0.12D) {
            if (SwapHelper.isGlider(player.getInventory().getStack(this.maceswap_serverElytraHotbarSlot))) {
                SwapHelper.swapChestWithHotbar(player, this.maceswap_serverElytraHotbarSlot);
                player.sendMessage(
                        Text.literal("Wind Burst Detected - Elytra Re-Equipped!")
                                .formatted(Formatting.AQUA),
                        true
                );
            }
            ((GlidingFallTracker) this).maceswap_resetTrackedFallDistance();
            this.maceswap_serverElytraHotbarSlot = -1;
            this.maceswap_serverWindBurstTicks = 0;
        } else if (this.maceswap_serverWindBurstTicks == 0) {
            ((GlidingFallTracker) this).maceswap_resetTrackedFallDistance();
            this.maceswap_serverElytraHotbarSlot = -1;
        }
    }
}
