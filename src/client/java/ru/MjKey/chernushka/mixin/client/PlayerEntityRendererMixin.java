package ru.MjKey.chernushka.mixin.client;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.MjKey.chernushka.entity.ChernushkaEntity;

/**
 * Миксин для изменения позы игрока когда он сидит на большой чернушке.
 * Игрок лежит вместо того чтобы сидеть.
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void chernushka$makePlayerLieOnChernushka(PlayerLikeEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        // Проверяем что игрок сидит на большой чернушке (level 5)
        if (player.getVehicle() instanceof ChernushkaEntity chernushka) {
            if (chernushka.getMergeLevel() >= ChernushkaEntity.MAX_MERGE_LEVEL) {
                // Меняем на лежачую позу (как при плавании)
                state.isSwimming = true;
                state.isInSneakingPose = false;
                state.hasVehicle = false;
                // Наклон тела вперёд (1.0 = полностью горизонтально)
                state.leaningPitch = 1.0f;
            }
        }
    }
}
