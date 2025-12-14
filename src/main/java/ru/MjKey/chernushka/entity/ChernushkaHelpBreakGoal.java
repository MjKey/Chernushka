package ru.MjKey.chernushka.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/**
 * Цель: помогать игроку ломать блоки.
 * Чернушки подходят к блоку и ускоряют его ломание через mixin.
 * Эта цель только управляет движением и анимацией.
 */
public class ChernushkaHelpBreakGoal extends Goal {
    
    private static final int PATH_UPDATE_INTERVAL = 10;
    private static final double BASE_HELP_DISTANCE = 3.0; // Базовое расстояние для помощи
    
    private final ChernushkaEntity chernushka;
    private BlockPos targetBlock;
    private int helpingTicks = 0;
    private int pathUpdateTicks = 0;
    private int stuckTicks = 0;
    private BlockPos lastPosition;
    
    /**
     * Возвращает дистанцию помощи с учётом размера чернушки
     */
    private double getHelpDistance() {
        float scale = this.chernushka.getModelScale();
        return BASE_HELP_DISTANCE * scale;
    }
    
    public ChernushkaHelpBreakGoal(ChernushkaEntity chernushka) {
        this.chernushka = chernushka;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    public boolean canStart() {
        BlockPos breakingPos = this.chernushka.getBreakingBlockPos();
        if (breakingPos == null) {
            return false;
        }
        
        // Проверяем что блок ещё существует
        if (this.chernushka.getEntityWorld().getBlockState(breakingPos).isAir()) {
            return false;
        }
        
        double distance = this.chernushka.squaredDistanceTo(
            breakingPos.getX() + 0.5,
            breakingPos.getY(),
            breakingPos.getZ() + 0.5
        );
        
        // Начинаем помогать если в пределах 16 блоков
        if (distance < 256.0D) {
            this.targetBlock = breakingPos;
            return true;
        }
        
        return false;
    }

    @Override
    public boolean shouldContinue() {
        BlockPos breakingPos = this.chernushka.getBreakingBlockPos();
        
        if (breakingPos == null) {
            return false;
        }
        
        // Блок сломан
        if (this.chernushka.getEntityWorld().getBlockState(breakingPos).isAir()) {
            return false;
        }
        
        // Обновляем цель если игрок переключился на другой блок
        if (!breakingPos.equals(this.targetBlock)) {
            this.targetBlock = breakingPos;
            this.pathUpdateTicks = PATH_UPDATE_INTERVAL;
            this.stuckTicks = 0;
        }
        
        // Слишком долго застряли - показываем confused
        if (this.stuckTicks > 60) {
            this.chernushka.showConfusedAnimation();
            return false;
        }
        
        return this.helpingTicks < 600;
    }
    
    @Override
    public void start() {
        this.helpingTicks = 0;
        this.pathUpdateTicks = 0;
        this.stuckTicks = 0;
        this.lastPosition = this.chernushka.getBlockPos();
    }
    
    @Override
    public void stop() {
        this.chernushka.setHelping(false);
        this.targetBlock = null;
        this.helpingTicks = 0;
        this.stuckTicks = 0;
    }
    
    @Override
    public void tick() {
        if (this.targetBlock == null) return;
        
        this.helpingTicks++;
        
        // Смотрим на блок
        if (this.helpingTicks % 4 == 0) {
            this.chernushka.getLookControl().lookAt(
                this.targetBlock.getX() + 0.5,
                this.targetBlock.getY() + 0.5,
                this.targetBlock.getZ() + 0.5
            );
        }
        
        // Расстояние от чернушки до блока
        double dx = this.targetBlock.getX() + 0.5 - this.chernushka.getX();
        double dy = this.targetBlock.getY() + 0.5 - this.chernushka.getEyeY();
        double dz = this.targetBlock.getZ() + 0.5 - this.chernushka.getZ();
        double distanceSq = dx * dx + dy * dy + dz * dz;
        
        double helpDist = getHelpDistance();
        
        // Далеко - идём к блоку
        if (distanceSq > helpDist * helpDist) {
            moveToBlock();
            // Анимация помощи управляется mixin'ом
            return;
        }
        
        // Достаточно близко - останавливаемся и поворачиваемся к блоку
        this.chernushka.getNavigation().stop();
        this.stuckTicks = 0;
        rotateTowardsBlock();
        // Состояние helping устанавливается mixin'ом
    }
    
    /**
     * Поворачивает чернушку лицом к блоку
     */
    private void rotateTowardsBlock() {
        double dx = this.targetBlock.getX() + 0.5 - this.chernushka.getX();
        double dz = this.targetBlock.getZ() + 0.5 - this.chernushka.getZ();
        
        float targetYaw = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        
        float currentYaw = this.chernushka.getYaw();
        float diff = targetYaw - currentYaw;
        
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        
        float rotationSpeed = 15.0f;
        if (Math.abs(diff) > rotationSpeed) {
            diff = diff > 0 ? rotationSpeed : -rotationSpeed;
        }
        
        this.chernushka.setYaw(currentYaw + diff);
        this.chernushka.setBodyYaw(currentYaw + diff);
    }
    
    private void moveToBlock() {
        if (++this.pathUpdateTicks >= PATH_UPDATE_INTERVAL) {
            this.pathUpdateTicks = 0;
            
            boolean pathStarted = this.chernushka.getNavigation().startMovingTo(
                this.targetBlock.getX() + 0.5,
                this.targetBlock.getY(),
                this.targetBlock.getZ() + 0.5,
                1.2D
            );
            
            // Проверяем застревание
            BlockPos currentPos = this.chernushka.getBlockPos();
            if (currentPos.equals(this.lastPosition)) {
                this.stuckTicks++;
            } else {
                this.stuckTicks = 0;
                this.lastPosition = currentPos;
            }
            
            if (!pathStarted && this.chernushka.getNavigation().isIdle()) {
                this.stuckTicks += 5;
            }
        }
    }
}
