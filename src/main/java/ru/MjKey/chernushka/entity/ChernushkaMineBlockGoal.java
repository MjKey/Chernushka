package ru.MjKey.chernushka.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/**
 * Цель: добывать блоки из очереди MiningTaskManager.
 * Если блок недостижим - показывает анимацию oglyadka и возвращается к владельцу.
 */
public class ChernushkaMineBlockGoal extends Goal {
    
    private static final float BASE_MINING_SPEED = 5.0f;
    private static final int BEDROCK_MINING_TIME = 6000;
    private static final double BASE_REACH_DISTANCE = 3.0;
    private static final int PATH_UPDATE_INTERVAL = 8;
    private static final int MAX_STUCK_TICKS = 100; // 5 секунд застревания = недостижимо
    
    /**
     * Возвращает дистанцию достижимости с учётом размера чернушки
     */
    private double getReachDistance() {
        float scale = this.chernushka.getModelScale();
        return BASE_REACH_DISTANCE * scale;
    }
    
    private final ChernushkaEntity chernushka;
    private BlockPos targetBlock;
    private int miningTicks = 0;
    private int totalMiningTime = 0;
    private float blockBreakProgress = 0;
    
    private boolean isMovingToBlock = false;
    private int pathUpdateTicks = 0;
    private int stuckTicks = 0;
    private BlockPos lastPosition;
    
    public ChernushkaMineBlockGoal(ChernushkaEntity chernushka) {
        this.chernushka = chernushka;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    public boolean canStart() {
        // Не начинаем если в состоянии confused
        if (this.chernushka.isConfused()) {
            return false;
        }
        
        BlockPos task = MiningTaskManager.assignTaskToChernushka(
            this.chernushka, 
            this.chernushka.getEntityWorld()
        );
        
        if (task == null) {
            return false;
        }
        
        BlockState state = this.chernushka.getEntityWorld().getBlockState(task);
        if (state.isAir()) {
            MiningTaskManager.completeTask(this.chernushka.getId(), task);
            return false;
        }
        
        this.targetBlock = task;
        return true;
    }


    @Override
    public boolean shouldContinue() {
        if (this.targetBlock == null) {
            return false;
        }
        
        // Прерываем если в состоянии confused
        if (this.chernushka.isConfused()) {
            return false;
        }
        
        // Проверяем блок каждые 20 тиков
        if (this.miningTicks % 20 == 0) {
            BlockState state = this.chernushka.getEntityWorld().getBlockState(this.targetBlock);
            if (state.isAir()) {
                return false;
            }
        }
        
        // Слишком долго застряли - блок недостижим
        if (this.stuckTicks >= MAX_STUCK_TICKS) {
            handleUnreachableBlock();
            return false;
        }
        
        return this.miningTicks < 7200; // Максимум 6 минут на блок
    }

    @Override
    public void start() {
        this.miningTicks = 0;
        this.blockBreakProgress = MiningTaskManager.getMiningProgress(this.targetBlock);
        this.isMovingToBlock = false;
        this.pathUpdateTicks = 0;
        this.stuckTicks = 0;
        this.lastPosition = this.chernushka.getBlockPos();
        
        // Включаем анимацию ломки сразу при получении задачи
        this.chernushka.setMining(true);
        
        calculateMiningTime();
    }
    
    private void calculateMiningTime() {
        BlockState state = this.chernushka.getEntityWorld().getBlockState(this.targetBlock);
        
        if (state.isOf(Blocks.BEDROCK)) {
            this.totalMiningTime = BEDROCK_MINING_TIME;
        } else {
            float hardness = state.getHardness(this.chernushka.getEntityWorld(), this.targetBlock);
            if (hardness < 0) {
                this.totalMiningTime = BEDROCK_MINING_TIME;
            } else {
                // Учитываем множитель от количества работников
                float speedMultiplier = MiningTaskManager.getSpeedMultiplier(this.targetBlock);
                this.totalMiningTime = Math.max(10, (int)(hardness * 30 / (BASE_MINING_SPEED * speedMultiplier)));
            }
        }
    }
    
    @Override
    public void stop() {
        // Убираем визуал прогресса
        if (this.targetBlock != null && this.chernushka.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.setBlockBreakingInfo(this.chernushka.getId(), this.targetBlock, -1);
        }
        
        this.chernushka.setMining(false);
        
        // Освобождаем чернушку от задачи
        MiningTaskManager.releaseChernushka(this.chernushka.getId());
        
        this.targetBlock = null;
        this.miningTicks = 0;
        this.blockBreakProgress = 0;
        this.stuckTicks = 0;
    }
    
    /**
     * Обработка недостижимого блока
     */
    private void handleUnreachableBlock() {
        // Помечаем блок как недостижимый
        MiningTaskManager.markUnreachable(this.targetBlock, this.chernushka.getEntityWorld());
        
        // Показываем анимацию oglyadka
        this.chernushka.showConfusedAnimation();
        
        // Убираем визуал
        if (this.chernushka.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.setBlockBreakingInfo(this.chernushka.getId(), this.targetBlock, -1);
        }
        
        this.targetBlock = null;
    }

    
    @Override
    public void tick() {
        if (this.targetBlock == null) return;
        
        this.miningTicks++;
        
        // Смотрим на блок каждые 4 тика
        if (this.miningTicks % 4 == 0) {
            this.chernushka.getLookControl().lookAt(
                this.targetBlock.getX() + 0.5,
                this.targetBlock.getY() + 0.5,
                this.targetBlock.getZ() + 0.5
            );
        }
        
        // Расстояние от глаз чернушки до центра блока (учитываем высоту)
        double eyeHeight = this.chernushka.getEyeY();
        double dx = this.targetBlock.getX() + 0.5 - this.chernushka.getX();
        double dy = this.targetBlock.getY() + 0.5 - eyeHeight;
        double dz = this.targetBlock.getZ() + 0.5 - this.chernushka.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        
        // Если далеко - идём к блоку (учитываем размер чернушки)
        double reachDist = getReachDistance();
        if (distSq > reachDist * reachDist) {
            moveToBlock();
            return;
        }
        
        // Достигли блока - сбрасываем счётчик застревания
        this.stuckTicks = 0;
        this.isMovingToBlock = false;
        
        // Можем достать - ломаем!
        this.chernushka.getNavigation().stop();
        
        // Поворачиваем тело к блоку
        rotateTowardsBlock();

        
        this.chernushka.setMining(true);
        
        // Добавляем прогресс с учётом множителя скорости
        float speedMultiplier = MiningTaskManager.getSpeedMultiplier(this.targetBlock);
        float progressPerTick = speedMultiplier / this.totalMiningTime;
        this.blockBreakProgress += progressPerTick;
        
        // Сохраняем прогресс в менеджере для совместной работы
        MiningTaskManager.addMiningProgress(this.targetBlock, progressPerTick);
        
        // Обновляем визуал прогресса каждые 5 тиков
        if (this.miningTicks % 5 == 0 && this.chernushka.getEntityWorld() instanceof ServerWorld serverWorld) {
            int stage = Math.min(9, (int)(this.blockBreakProgress * 10.0f));
            serverWorld.setBlockBreakingInfo(this.chernushka.getId(), this.targetBlock, stage);
        }
        
        if (this.blockBreakProgress >= 1.0f) {
            breakBlock();
        }
    }
    
    /**
     * Поворачивает чернушку лицом к блоку
     */
    private void rotateTowardsBlock() {
        double dx = this.targetBlock.getX() + 0.5 - this.chernushka.getX();
        double dz = this.targetBlock.getZ() + 0.5 - this.chernushka.getZ();
        
        float targetYaw = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        
        // Плавный поворот
        float currentYaw = this.chernushka.getYaw();
        float diff = targetYaw - currentYaw;
        
        // Нормализуем разницу в диапазон -180..180
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        
        // Поворачиваем на 15 градусов за тик максимум
        float rotationSpeed = 15.0f;
        if (Math.abs(diff) > rotationSpeed) {
            diff = diff > 0 ? rotationSpeed : -rotationSpeed;
        }
        
        this.chernushka.setYaw(currentYaw + diff);
        this.chernushka.setBodyYaw(currentYaw + diff);
    }
    
    private void moveToBlock() {
        // Показываем анимацию ломки даже когда идём к блоку
        this.chernushka.setMining(true);
        
        if (++this.pathUpdateTicks >= PATH_UPDATE_INTERVAL) {
            this.pathUpdateTicks = 0;
            
            boolean pathStarted = this.chernushka.getNavigation().startMovingTo(
                this.targetBlock.getX() + 0.5,
                this.targetBlock.getY(),
                this.targetBlock.getZ() + 0.5,
                1.0D
            );
            
            // Проверяем застревание
            BlockPos currentPos = this.chernushka.getBlockPos();
            if (currentPos.equals(this.lastPosition)) {
                this.stuckTicks += PATH_UPDATE_INTERVAL;
            } else {
                this.stuckTicks = 0;
                this.lastPosition = currentPos;
            }
            
            // Не можем построить путь
            if (!pathStarted && this.chernushka.getNavigation().isIdle()) {
                this.stuckTicks += PATH_UPDATE_INTERVAL * 2;
            }
            
            this.isMovingToBlock = pathStarted;
        }
    }
    
    private void breakBlock() {
        if (this.chernushka.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.breakBlock(this.targetBlock, true, this.chernushka);
            serverWorld.setBlockBreakingInfo(this.chernushka.getId(), this.targetBlock, -1);
        }
        
        MiningTaskManager.completeTask(this.chernushka.getId(), this.targetBlock);
        this.targetBlock = null;
    }
}
