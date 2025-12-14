package ru.MjKey.chernushka.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.MjKey.chernushka.Chernushka;
import ru.MjKey.chernushka.config.ModConfig;

import java.util.EnumSet;

/**
 * Цель: следовать за владельцем.
 * Безопасная телепортация:
 * - Не телепортируется если игрок в воздухе
 * - Не телепортируется если игрок в лаве
 * - Телепортируется только на открытую местность
 * - Телепортируется только на безопасный блок
 */
public class ChernushkaFollowOwnerGoal extends Goal {
    
    private final ChernushkaEntity chernushka;
    private PlayerEntity owner;
    private final World world;
    private final double speed;
    private final EntityNavigation navigation;
    private int updateCountdownTicks;
    private final float maxDistance;
    private final float minDistance;
    
    // Кэш позиции владельца для оптимизации
    private double lastOwnerX, lastOwnerY, lastOwnerZ;
    private boolean needsPathUpdate = true;
    
    public ChernushkaFollowOwnerGoal(ChernushkaEntity chernushka, double speed, float maxDistance, float minDistance) {
        this.chernushka = chernushka;
        this.world = chernushka.getEntityWorld();
        this.speed = speed;
        this.navigation = chernushka.getNavigation();
        this.maxDistance = maxDistance;
        this.minDistance = minDistance;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    public boolean canStart() {
        PlayerEntity owner = this.chernushka.getOwner();
        
        if (owner == null || owner.isSpectator() || !owner.isAlive()) {
            return false;
        }
        if (this.chernushka.isInvisible()) {
            return false;
        }
        if (this.chernushka.squaredDistanceTo(owner) < (double)(this.minDistance * this.minDistance)) {
            return false;
        }
        if (this.chernushka.isHelping()) {
            return false;
        }
        
        this.owner = owner;
        return true;
    }
    
    @Override
    public boolean shouldContinue() {
        if (this.navigation.isIdle()) {
            return false;
        }
        if (this.chernushka.isHelping()) {
            return false;
        }
        return this.chernushka.squaredDistanceTo(this.owner) > (double)(this.minDistance * this.minDistance);
    }
    
    @Override
    public void start() {
        this.updateCountdownTicks = 0;
        this.needsPathUpdate = true;
        this.lastOwnerX = this.owner.getX();
        this.lastOwnerY = this.owner.getY();
        this.lastOwnerZ = this.owner.getZ();
    }
    
    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
        this.chernushka.setRunning(false);
    }

    
    @Override
    public void tick() {
        ModConfig config = Chernushka.getConfig();
        
        // Смотрим на владельца каждые 2 тика
        if (this.updateCountdownTicks % 2 == 0) {
            this.chernushka.getLookControl().lookAt(this.owner, 10.0F, (float)this.chernushka.getMaxLookPitchChange());
        }
        
        if (--this.updateCountdownTicks <= 0) {
            this.updateCountdownTicks = 4;
            
            if (this.chernushka.hasVehicle()) {
                return;
            }
            
            double distSq = this.chernushka.squaredDistanceTo(this.owner);
            double teleportDistSq = config.teleportDistance * config.teleportDistance;
            double runDistSq = config.runDistanceThreshold * config.runDistanceThreshold;
            
            // Телепортация если очень далеко
            if (distSq >= teleportDistSq) {
                if (canSafelyTeleport()) {
                    teleportNearOwner();
                }
                return;
            }
            
            // Бег если далеко, иначе ходьба
            boolean shouldRun = distSq >= runDistSq;
            this.chernushka.setRunning(shouldRun);
            double currentSpeed = shouldRun ? config.runSpeed : config.walkSpeed;
            
            // Обновляем путь только если владелец сдвинулся на 1+ блок
            double ownerMovedSq = 
                (this.owner.getX() - this.lastOwnerX) * (this.owner.getX() - this.lastOwnerX) +
                (this.owner.getY() - this.lastOwnerY) * (this.owner.getY() - this.lastOwnerY) +
                (this.owner.getZ() - this.lastOwnerZ) * (this.owner.getZ() - this.lastOwnerZ);
            
            if (ownerMovedSq > 1.0D || this.needsPathUpdate || this.navigation.isIdle()) {
                this.navigation.startMovingTo(this.owner, currentSpeed);
                this.lastOwnerX = this.owner.getX();
                this.lastOwnerY = this.owner.getY();
                this.lastOwnerZ = this.owner.getZ();
                this.needsPathUpdate = false;
            }
        }
    }
    
    /**
     * Проверяет, можно ли безопасно телепортироваться к владельцу
     */
    private boolean canSafelyTeleport() {
        // Не телепортируемся если игрок в воздухе (летит/падает)
        if (!this.owner.isOnGround() && !this.owner.isTouchingWater()) {
            return false;
        }
        
        // Не телепортируемся если игрок в лаве
        if (this.owner.isInLava()) {
            return false;
        }
        
        // Не телепортируемся если игрок под водой глубоко
        if (this.owner.isSubmergedIn(FluidTags.WATER) && !this.owner.isOnGround()) {
            return false;
        }
        
        return true;
    }
    
    private void teleportNearOwner() {
        BlockPos ownerPos = this.owner.getBlockPos();
        
        // Пробуем позиции вокруг владельца по спирали
        int[][] offsets = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2},
            {2, 1}, {-2, 1}, {2, -1}, {-2, -1},
            {1, 2}, {-1, 2}, {1, -2}, {-1, -2}
        };
        
        for (int[] offset : offsets) {
            for (int yOff = 0; yOff <= 3; yOff++) {
                BlockPos testPos = ownerPos.add(offset[0], yOff, offset[1]);
                
                if (isSafeTeleportPosition(testPos)) {
                    doTeleport(testPos);
                    return;
                }
            }
        }
        
        // Fallback: случайные позиции
        for (int i = 0; i < 10; ++i) {
            int x = ownerPos.getX() + getRandomOffset(-4, 4);
            int z = ownerPos.getZ() + getRandomOffset(-4, 4);
            
            for (int y = ownerPos.getY(); y <= ownerPos.getY() + 3; y++) {
                BlockPos testPos = new BlockPos(x, y, z);
                
                if (isSafeTeleportPosition(testPos)) {
                    doTeleport(testPos);
                    return;
                }
            }
        }
    }

    
    /**
     * Проверяет, безопасна ли позиция для телепортации
     */
    private boolean isSafeTeleportPosition(BlockPos pos) {
        // Проверяем блок под ногами - должен быть твёрдым
        BlockPos groundPos = pos.down();
        BlockState groundState = this.world.getBlockState(groundPos);
        
        // Блок должен быть твёрдым и не опасным
        if (!groundState.isSolidBlock(this.world, groundPos)) {
            return false;
        }
        
        // Не телепортируемся на опасные блоки
        if (isDangerousBlock(groundState, groundPos)) {
            return false;
        }
        
        // Проверяем что есть место для чернушки (2 блока в высоту)
        BlockState feetState = this.world.getBlockState(pos);
        BlockState headState = this.world.getBlockState(pos.up());
        
        if (!feetState.isAir() && !feetState.getFluidState().isEmpty()) {
            // Допускаем воду на уровне ног
            if (!feetState.getFluidState().isIn(FluidTags.WATER)) {
                return false;
            }
        }
        
        if (!headState.isAir() && !headState.getFluidState().isEmpty()) {
            if (!headState.getFluidState().isIn(FluidTags.WATER)) {
                return false;
            }
        }
        
        // Проверяем что нет лавы рядом
        if (hasLavaNearby(pos)) {
            return false;
        }
        
        // Проверяем что позиция "открытая" - есть доступ к небу или большое пространство
        if (!isOpenArea(pos)) {
            return false;
        }
        
        // Финальная проверка коллизии
        return this.world.isSpaceEmpty(this.chernushka, 
            this.chernushka.getBoundingBox().offset(
                pos.getX() + 0.5 - this.chernushka.getX(),
                pos.getY() - this.chernushka.getY(),
                pos.getZ() + 0.5 - this.chernushka.getZ()
            ));
    }
    
    /**
     * Проверяет, опасен ли блок
     */
    private boolean isDangerousBlock(BlockState state, BlockPos pos) {
        // Лава
        if (!state.getFluidState().isEmpty() && state.getFluidState().isIn(FluidTags.LAVA)) {
            return true;
        }
        
        // Кактус, магма, костёр и т.п.
        if (state.isIn(BlockTags.FIRE) || 
            state.isIn(BlockTags.CAMPFIRES)) {
            return true;
        }
        
        // Проверяем на магму по имени блока (нет тега)
        String blockName = state.getBlock().getTranslationKey();
        if (blockName.contains("magma") || blockName.contains("cactus")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Проверяет наличие лавы рядом
     */
    private boolean hasLavaNearby(BlockPos pos) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockState state = this.world.getBlockState(pos.add(x, y, z));
                    if (!state.getFluidState().isEmpty() && state.getFluidState().isIn(FluidTags.LAVA)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Проверяет, является ли область открытой (не в стене/пещере)
     */
    private boolean isOpenArea(BlockPos pos) {
        // Проверяем доступ к небу
        if (this.world.isSkyVisible(pos)) {
            return true;
        }
        
        // Или проверяем что есть достаточно воздуха вокруг (минимум 3 блока свободно по горизонтали)
        int airCount = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                if (this.world.getBlockState(pos.add(x, 0, z)).isAir()) {
                    airCount++;
                }
            }
        }
        
        return airCount >= 3;
    }
    
    private void doTeleport(BlockPos pos) {
        this.chernushka.refreshPositionAndAngles(
            pos.getX() + 0.5,
            pos.getY(),
            pos.getZ() + 0.5,
            this.chernushka.getYaw(),
            this.chernushka.getPitch()
        );
        this.navigation.stop();
        this.needsPathUpdate = true;
    }
    
    private int getRandomOffset(int min, int max) {
        return min + this.chernushka.getRandom().nextInt(max - min + 1);
    }
}
