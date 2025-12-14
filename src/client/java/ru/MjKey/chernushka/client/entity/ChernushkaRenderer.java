package ru.MjKey.chernushka.client.entity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import ru.MjKey.chernushka.entity.ChernushkaEntity;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.internal.BoneSnapshots;
import software.bernie.geckolib.renderer.internal.RenderPassInfo;

/**
 * Рендерер Чернушки с динамическим движением глаз.
 * Глаза двигаются в направлении:
 * - Куда идёт чернушка
 * - На владельца если рядом
 * - Случайно оглядываются когда стоят
 */
public class ChernushkaRenderer extends GeoEntityRenderer<ChernushkaEntity, ChernushkaRenderState> {
    
    private static final String EYES_BONE = "Глаза";
    private static final float MAX_EYE_OFFSET = 0.5f;
    private static final float EYE_LERP_SPEED = 0.15f;
    
    // Для плавной анимации глаз
    private float currentEyeX = 0f;
    private float currentEyeY = 0f;
    private float randomLookTimer = 0f;
    private float randomTargetX = 0f;
    private float randomTargetY = 0f;
    
    public ChernushkaRenderer(EntityRendererFactory.Context context) {
        super(context, new ChernushkaModel());
    }
    
    @Override
    public void addRenderData(ChernushkaEntity animatable, Void relatedObject, 
                              ChernushkaRenderState renderState, float partialTick) {
        super.addRenderData(animatable, relatedObject, renderState, partialTick);
        
        if (animatable == null || renderState == null) return;
        
        // Собираем данные для движения глаз
        PlayerEntity owner = animatable.getOwner();
        
        // Проверяем смотрит ли на владельца
        renderState.isLookingAtOwner = false;
        if (owner != null) {
            double distToOwner = animatable.squaredDistanceTo(owner);
            renderState.isLookingAtOwner = distToOwner < 64.0; // 8 блоков
        }
        
        // Направление взгляда
        renderState.lookYaw = animatable.getYaw();
        renderState.lookPitch = animatable.getPitch();
        renderState.isMoving = animatable.getVelocity().horizontalLengthSquared() > 0.001;
        
        // Масштаб от уровня слияния
        renderState.modelScale = animatable.getModelScale();
        
        // Состояния прыжка/падения
        renderState.isJumpingUp = animatable.isJumpingUp();
        renderState.isFalling = animatable.isFalling();
        renderState.isLanding = animatable.isLanding();
    }
    
    @Override
    public void scaleModelForRender(RenderPassInfo<ChernushkaRenderState> renderPassInfo, 
                                    float widthScale, float heightScale) {
        ChernushkaRenderState state = renderPassInfo.renderState();
        float scale = state != null ? state.modelScale : 1.0f;
        super.scaleModelForRender(renderPassInfo, widthScale * scale, heightScale * scale);
    }

    
    @Override
    public void adjustModelBonesForRender(RenderPassInfo<ChernushkaRenderState> renderPassInfo, 
                                          BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        
        ChernushkaRenderState state = renderPassInfo.renderState();
        if (state == null) return;
        
        // Вычисляем целевую позицию глаз
        float targetX = 0f;
        float targetY = 0f;
        
        if (state.isMoving) {
            // При движении - глаза смотрят в направлении движения
            // Используем разницу между yaw головы и тела
            float yawDiff = MathHelper.wrapDegrees(state.lookYaw - state.bodyYaw);
            
            // Конвертируем в смещение глаз (-1 до 1)
            targetX = MathHelper.clamp(yawDiff / 45f, -1f, 1f) * MAX_EYE_OFFSET;
            
            // Немного вниз при движении
            targetY = -0.1f;
        } else if (state.isLookingAtOwner) {
            // Смотрим на владельца
            float yawDiff = MathHelper.wrapDegrees(state.lookYaw - state.bodyYaw);
            
            targetX = MathHelper.clamp(yawDiff / 30f, -1f, 1f) * MAX_EYE_OFFSET;
            targetY = MathHelper.clamp(-state.lookPitch / 45f, -1f, 1f) * MAX_EYE_OFFSET * 0.5f;
        } else {
            // Случайное оглядывание когда стоим
            randomLookTimer += 0.05f;
            
            if (randomLookTimer > 1f) {
                randomLookTimer = 0f;
                // Новая случайная цель с небольшой вероятностью
                if (Math.random() < 0.3) {
                    randomTargetX = (float)(Math.random() * 2 - 1) * MAX_EYE_OFFSET * 0.7f;
                    randomTargetY = (float)(Math.random() * 2 - 1) * MAX_EYE_OFFSET * 0.3f;
                }
            }
            
            targetX = randomTargetX;
            targetY = randomTargetY;
        }
        
        // Плавная интерполяция
        currentEyeX = MathHelper.lerp(EYE_LERP_SPEED, currentEyeX, targetX);
        currentEyeY = MathHelper.lerp(EYE_LERP_SPEED, currentEyeY, targetY);
        
        // Применяем смещение к кости глаз
        snapshots.ifPresent(EYES_BONE, eyeSnapshot -> {
            eyeSnapshot.setTranslation(currentEyeX, currentEyeY, 0f);
        });
    }
    
    @Override
    public ChernushkaRenderState createRenderState(ChernushkaEntity animatable, Void relatedObject) {
        return new ChernushkaRenderState();
    }
    
}
