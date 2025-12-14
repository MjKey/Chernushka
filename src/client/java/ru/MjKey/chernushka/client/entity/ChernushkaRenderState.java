package ru.MjKey.chernushka.client.entity;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.Map;

public class ChernushkaRenderState extends LivingEntityRenderState implements GeoRenderState {
    
    private final Map<DataTicket<?>, Object> dataMap = new Reference2ObjectOpenHashMap<>();
    
    // Данные для движения глаз
    public float eyeOffsetX = 0f;
    public float eyeOffsetY = 0f;
    public boolean isLookingAtOwner = false;
    public boolean isMoving = false;
    public float lookYaw = 0f;
    public float lookPitch = 0f;
    
    // Масштаб модели (от уровня слияния)
    public float modelScale = 1.0f;
    
    // Состояния прыжка/падения
    public boolean isJumpingUp = false;
    public boolean isFalling = false;
    public boolean isLanding = false;
    public boolean isDead = false;
    
    @Override
    public Map<DataTicket<?>, Object> getDataMap() {
        return this.dataMap;
    }
    
    @Override
    public <D> void addGeckolibData(DataTicket<D> dataTicket, @Nullable D data) {
        this.dataMap.put(dataTicket, data);
    }
    
    @Override
    public boolean hasGeckolibData(DataTicket<?> dataTicket) {
        return this.dataMap.containsKey(dataTicket);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <D> @Nullable D getGeckolibData(DataTicket<D> dataTicket) {
        return (D) this.dataMap.get(dataTicket);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <D> D getOrDefaultGeckolibData(DataTicket<D> dataTicket, D defaultValue) {
        Object value = this.dataMap.get(dataTicket);
        return value != null ? (D) value : defaultValue;
    }
}
