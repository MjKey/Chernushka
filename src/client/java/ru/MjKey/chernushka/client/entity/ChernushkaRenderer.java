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
 * 
 * Состояние глаз хранится в ChernushkaRenderState для каждой сущности отдельно.
 */
public class ChernushkaRenderer extends GeoEntityRenderer<ChernushkaEntity, ChernushkaRenderState> {
    
    private static final String EYES_BONE = "Глаза";
    private static final float MAX_EYE_OFFSET = 0.5f;
    private static final float EYE_LERP_SPEED = 0.15f;
    
    // Кэш состояний глаз для каждой сущности (по entity ID)
    private static final java.util.Map<Integer, EyeState> eyeStates = new java.util.HashMap<>();
    
    // Класс для хранения состояния глаз конкретной сущности
    private static class EyeState {
        float currentEyeX = 0f;
        float currentEyeY = 0f;
        float randomLookTimer = 0f;
        float randomTargetX = 0f;
        float randomTargetY = 0f;
        long lastUsedTime = System.currentTimeMillis();
    }
    
    // Счётчик для периодической очистки
    private static int cleanupCounter = 0;
    private static final int CLEANUP_INTERVAL = 1000; // Каждые ~1000 рендеров
    private static final long STALE_THRESHOLD_MS = 30000; // 30 секунд без использования
    
    public ChernushkaRenderer(EntityRendererFactory.Context context) {
        super(context, new ChernushkaModel());
    }
    
    /**
     * Получает или создаёт состояние глаз для конкретной сущности
     */
    private EyeState getEyeState(int entityId) {
        // Периодическая очистка старых записей
        if (++cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanupStaleEyeStates();
        }
        
        EyeState state = eyeStates.computeIfAbsent(entityId, id -> new EyeState());
        state.lastUsedTime = System.currentTimeMillis();
        return state;
    }
    
    /**
     * Удаляет состояния глаз для сущностей, которые давно не рендерились
     */
    private static void cleanupStaleEyeStates() {
        long now = System.currentTimeMillis();
        eyeStates.entrySet().removeIf(entry -> 
            now - entry.getValue().lastUsedTime > STALE_THRESHOLD_MS
        );
    }
    
    /**
     * Очищает состояние глаз для конкретной сущности (вызывать при удалении)
     */
    public static void removeEyeState(int entityId) {
        eyeStates.remove(entityId);
    }
    
    @Override
    public void addRenderData(ChernushkaEntity animatable, Void relatedObject, 
                              ChernushkaRenderState renderState, float partialTick) {
        super.addRenderData(animatable, relatedObject, renderState, partialTick);
        
        if (animatable == null || renderState == null) return;
        
        // Сохраняем ID сущности для кэширования состояния глаз
        renderState.entityId = animatable.getId();
        
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
        
        // Получаем состояние глаз для этой конкретной сущности
        EyeState eyes = getEyeState(state.entityId);
        
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
            eyes.randomLookTimer += 0.05f;
            
            if (eyes.randomLookTimer > 1f) {
                eyes.randomLookTimer = 0f;
                // Новая случайная цель с небольшой вероятностью
                if (Math.random() < 0.3) {
                    eyes.randomTargetX = (float)(Math.random() * 2 - 1) * MAX_EYE_OFFSET * 0.7f;
                    eyes.randomTargetY = (float)(Math.random() * 2 - 1) * MAX_EYE_OFFSET * 0.3f;
                }
            }
            
            targetX = eyes.randomTargetX;
            targetY = eyes.randomTargetY;
        }
        
        // Плавная интерполяция
        eyes.currentEyeX = MathHelper.lerp(EYE_LERP_SPEED, eyes.currentEyeX, targetX);
        eyes.currentEyeY = MathHelper.lerp(EYE_LERP_SPEED, eyes.currentEyeY, targetY);
        
        // Применяем смещение к кости глаз
        snapshots.ifPresent(EYES_BONE, eyeSnapshot -> {
            eyeSnapshot.setTranslation(eyes.currentEyeX, eyes.currentEyeY, 0f);
        });
    }
    
    @Override
    public ChernushkaRenderState createRenderState(ChernushkaEntity animatable, Void relatedObject) {
        return new ChernushkaRenderState();
    }
    
}
