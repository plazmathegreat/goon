package com.example.maceswap.mixin;

import com.example.maceswap.util.GlidingFallTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityAttackMixin extends LivingEntity {

    protected PlayerEntityAttackMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void maceswap_beforeServerAttack(Entity target, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (player.getEntityWorld().isClient()) {
            return;
        }

        if (player.getMainHandStack().isOf(Items.MACE)) {
            float effectiveFall = ((GlidingFallTracker) this).maceswap_getEffectiveFallDistance();
            if (effectiveFall > 1.5F) {
                this.fallDistance = effectiveFall;
                player.stopGliding();
            }
        }
    }
}
