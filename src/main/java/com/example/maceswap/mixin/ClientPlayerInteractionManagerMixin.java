package com.example.maceswap.mixin;

import com.example.maceswap.util.GlidingFallTracker;
import com.example.maceswap.util.SwapHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Unique
    private static final int CHEST_ARMOR_SLOT_INDEX = 6;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private ClientPlayNetworkHandler networkHandler;

    @Shadow
    public abstract void clickSlot(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player);

    @Unique
    private int maceswap_elytraHotbarSlot = -1;

    @Unique
    private int maceswap_originalWeaponSlot = -1;

    @Unique
    private int maceswap_windBurstMonitorTicks = 0;

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void maceswap_clientBeforeAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        this.maceswap_originalWeaponSlot = -1;

        if (!SwapHelper.canPerformMaceSwap(player)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int chestplateSlot = SwapHelper.findChestplateHotbarSlot(inventory);
        if (chestplateSlot == -1) {
            return;
        }

        int maceSlot = SwapHelper.findMaceHotbarSlot(inventory);
        boolean holdingMace = player.getMainHandStack().isOf(Items.MACE);
        if (!holdingMace && maceSlot == -1) {
            return;
        }

        float effectiveFall = ((GlidingFallTracker) player).maceswap_getEffectiveFallDistance();
        if (effectiveFall > 1.5F) {
            player.fallDistance = effectiveFall;
        }

        this.clickSlot(
                player.playerScreenHandler.syncId,
                CHEST_ARMOR_SLOT_INDEX,
                chestplateSlot,
                SlotActionType.SWAP,
                player
        );
        this.maceswap_elytraHotbarSlot = chestplateSlot;

        player.stopGliding();

        if (!holdingMace && maceSlot != -1) {
            this.maceswap_originalWeaponSlot = inventory.getSelectedSlot();
            inventory.setSelectedSlot(maceSlot);
            this.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(maceSlot));
        }

        this.maceswap_windBurstMonitorTicks = 8;

        player.sendMessage(
                Text.literal("Attribute Mace Smash! (Fall: " + String.format("%.1f", effectiveFall) + "m)")
                        .formatted(Formatting.GOLD),
                true
        );
    }

    @Inject(method = "attackEntity", at = @At("RETURN"))
    private void maceswap_clientAfterAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (this.maceswap_originalWeaponSlot != -1) {
            PlayerInventory inventory = player.getInventory();
            inventory.setSelectedSlot(this.maceswap_originalWeaponSlot);
            this.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(this.maceswap_originalWeaponSlot));
            this.maceswap_originalWeaponSlot = -1;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void maceswap_onTick(CallbackInfo ci) {
        if (this.maceswap_elytraHotbarSlot == -1) {
            return;
        }
        if (this.maceswap_windBurstMonitorTicks <= 0) {
            return;
        }

        PlayerEntity player = this.client.player;
        if (player == null) {
            this.maceswap_elytraHotbarSlot = -1;
            this.maceswap_windBurstMonitorTicks = 0;
            return;
        }

        this.maceswap_windBurstMonitorTicks--;

        double verticalVelocity = player.getVelocity().y;
        boolean windBursted = verticalVelocity > 0.15D;

        if (windBursted && player.currentScreenHandler == player.playerScreenHandler) {
            if (SwapHelper.isGlider(player.getInventory().getStack(this.maceswap_elytraHotbarSlot))) {
                this.clickSlot(
                        player.playerScreenHandler.syncId,
                        CHEST_ARMOR_SLOT_INDEX,
                        this.maceswap_elytraHotbarSlot,
                        SlotActionType.SWAP,
                        player
                );
                player.sendMessage(
                        Text.literal("Wind Burst Detected - Elytra Re-Equipped!")
                                .formatted(Formatting.AQUA),
                        true
                );
            }
            ((GlidingFallTracker) player).maceswap_resetTrackedFallDistance();
            this.maceswap_elytraHotbarSlot = -1;
            this.maceswap_windBurstMonitorTicks = 0;
        } else if (this.maceswap_windBurstMonitorTicks == 0) {
            ((GlidingFallTracker) player).maceswap_resetTrackedFallDistance();
            this.maceswap_elytraHotbarSlot = -1;
        }
    }
}
