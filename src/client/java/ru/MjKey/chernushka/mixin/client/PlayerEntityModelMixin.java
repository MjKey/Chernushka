package ru.MjKey.chernushka.mixin.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Миксин для изменения позы рук игрока когда он лежит на большой чернушке.
 * Руки вытянуты вперёд и немного в стороны, как будто держится.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin extends BipedEntityModel<PlayerEntityRenderState> {

    public PlayerEntityModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
            at = @At("TAIL"))
    private void chernushka$setRidingArms(PlayerEntityRenderState state, CallbackInfo ci) {
        // Проверяем флаг лежания на чернушке (isSwimming + leaningPitch = 1.0)
        if (state.isSwimming && state.leaningPitch >= 0.99f && !state.hasVehicle) {
            // Руки в стороны и вперёд (как будто держится за чернушку)
            // pitch - наклон вперёд/назад, roll - отведение в сторону
            
            // Правая рука: в сторону и немного вперёд
            this.rightArm.pitch = -0.3f;  // Немного вперёд
            this.rightArm.yaw = 0.0f;
            this.rightArm.roll = 1.2f;    // Отведена вправо (положительное = вправо)
            
            // Левая рука: в сторону и немного вперёд
            this.leftArm.pitch = -0.3f;   // Немного вперёд
            this.leftArm.yaw = 0.0f;
            this.leftArm.roll = -1.2f;    // Отведена влево (отрицательное = влево)
            
            // Ноги прямые
            this.rightLeg.pitch = 0.0f;
            this.rightLeg.yaw = 0.0f;
            this.rightLeg.roll = 0.0f;
            
            this.leftLeg.pitch = 0.0f;
            this.leftLeg.yaw = 0.0f;
            this.leftLeg.roll = 0.0f;
        }
    }
}
