package com.yingxiya.healthws.mixin;

import com.yingxiya.healthws.client.HealthwsClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {
    private static final Logger LOG = LoggerFactory.getLogger("healthws-mixin");

    @Inject(method = "damage", at = @At("HEAD"))
    public void damage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (entity instanceof PlayerEntity player) {
            if (HealthwsClient.debug) {
                LOG.info("Mixin 被调用! 实体: {}, 伤害来源: {}, 伤害值: {}",
                        player.getName().getString(),
                        source != null ? source.getType().msgId() : "null",
                        amount);
            }

            HealthwsClient.recordDamage(source, amount);
        }
    }
}
