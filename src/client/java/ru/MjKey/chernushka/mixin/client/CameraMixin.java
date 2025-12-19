package ru.MjKey.chernushka.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.MjKey.chernushka.entity.ChernushkaEntity;

/**
 * Миксин для понижения камеры когда игрок лежит на большой чернушке.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private Vec3d pos;

    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Inject(method = "update", at = @At("TAIL"))
    private void chernushka$lowerCameraOnGiantChernushka(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        // Только в режиме от первого лица
        if (thirdPerson) return;
        
        // Проверяем что это игрок на большой чернушке
        if (focusedEntity instanceof PlayerEntity player) {
            if (player.getVehicle() instanceof ChernushkaEntity chernushka) {
                if (chernushka.getMergeLevel() >= ChernushkaEntity.MAX_MERGE_LEVEL) {
                    // Игрок лежит на чернушке, камера должна быть ниже и чуть впереди
                    // Понижаем на разницу между стоящим и лежащим игроком
                    double offsetY = 1.22;
                    
                    // Смещаем камеру вперёд по направлению взгляда чернушки
                    float yaw = chernushka.getYaw();
                    double rad = Math.toRadians(yaw);
                    double offsetForward = 0.5; // Смещение вперёд
                    double offsetX = -Math.sin(rad) * offsetForward;
                    double offsetZ = Math.cos(rad) * offsetForward;
                    
                    setPos(new Vec3d(this.pos.x + offsetX, this.pos.y - offsetY, this.pos.z + offsetZ));
                }
            }
        }
    }
}
