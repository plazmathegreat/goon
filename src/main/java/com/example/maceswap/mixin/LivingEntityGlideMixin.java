package com.example.maceswap.mixin;

import com.example.maceswap.util.GlidingFallTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityGlideMixin extends Entity implements GlidingFallTracker {

    @Unique
    private double maceswap_peakGlideY = Double.NaN;

    @Unique
    private float maceswap_trackedGlideFallDistance = 0.0F;

    public LivingEntityGlideMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void maceswap_trackGlideFallDistance(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        boolean activeTrack = self.isGliding();
        if (!activeTrack) {
            if (this.maceswap_trackedGlideFallDistance > 0.0F && !this.isOnGround()) {
                activeTrack = true;
            }
        }

        if (activeTrack) {
            double currentY = self.getY();
            if (Double.isNaN(this.maceswap_peakGlideY)) {
                this.maceswap_peakGlideY = currentY;
            } else if (currentY > this.maceswap_peakGlideY) {
                this.maceswap_peakGlideY = currentY;
            }
            float descended = (float) (this.maceswap_peakGlideY - currentY);
            if (descended > this.maceswap_trackedGlideFallDistance) {
                this.maceswap_trackedGlideFallDistance = descended;
            }
        }

        boolean shouldReset = false;
        if (this.isOnGround()) {
            shouldReset = true;
        } else if (this.isTouchingWater()) {
            shouldReset = true;
        } else if (self.isClimbing()) {
            shouldReset = true;
        }

        if (shouldReset) {
            this.maceswap_peakGlideY = Double.NaN;
            this.maceswap_trackedGlideFallDistance = 0.0F;
        }
    }

    @Override
    public float maceswap_getEffectiveFallDistance() {
        LivingEntity self = (LivingEntity) (Object) this;
        float fromPeak = Double.isNaN(this.maceswap_peakGlideY) ? 0.0F : (float) (this.maceswap_peakGlideY - self.getY());
        return Math.max((float) this.fallDistance, Math.max(this.maceswap_trackedGlideFallDistance, fromPeak));
    }

    @Override
    public void maceswap_resetTrackedFallDistance() {
        this.maceswap_peakGlideY = Double.NaN;
        this.maceswap_trackedGlideFallDistance = 0.0F;
    }
}
