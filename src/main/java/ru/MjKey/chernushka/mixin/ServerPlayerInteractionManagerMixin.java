package ru.MjKey.chernushka.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.MjKey.chernushka.Chernushka;
import ru.MjKey.chernushka.config.ModConfig;
import ru.MjKey.chernushka.entity.ChernushkaEntity;
import ru.MjKey.chernushka.entity.ModEntities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mixin для ускорения ломания блоков когда рядом есть чернушки.
 * Каждая чернушка даёт +10% к скорости ломания.
 */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {
    
    @Shadow
    protected ServerPlayerEntity player;
    
    @Shadow
    protected ServerWorld world;
    
    @Shadow
    private BlockPos miningPos;
    
    @Shadow
    public abstract boolean tryBreakBlock(BlockPos pos);
    
    // Отслеживаем накопленный бонусный прогресс для каждого игрока
    @Unique
    private static final Map<UUID, Float> chernushka$bonusProgress = new HashMap<>();
    
    /**
     * Инжектим в continueMining чтобы отслеживать прогресс и ускорять ломание
     */
    @Inject(method = "continueMining", at = @At("RETURN"), cancellable = true)
    private void chernushka$boostMiningSpeed(BlockState state, BlockPos pos, int startTime, CallbackInfoReturnable<Float> cir) {
        if (this.world == null || this.player == null) return;
        
        ModConfig config = Chernushka.getConfig();
        if (!config.enableMiningHelp) return;
        
        float originalProgress = cir.getReturnValue();
        if (originalProgress <= 0) return;
        
        double helpRange = config.helpRange;
        
        // Ищем чернушек рядом с блоком
        Box searchBox = new Box(pos).expand(helpRange);
        List<ChernushkaEntity> nearbyChernushkas = this.world.getEntitiesByType(
            ModEntities.CHERNUSHKA,
            searchBox,
            c -> c.isTamed() && 
                 c.getOwnerUuid() != null && 
                 c.getOwnerUuid().equals(this.player.getUuid()) &&
                 c.isAlive() &&
                 !c.hasVehicle()
        );
        
        if (nearbyChernushkas.isEmpty()) return;
        
        // Считаем количество чернушек в радиусе
        int helperCount = 0;
        for (ChernushkaEntity chernushka : nearbyChernushkas) {
            double distSq = chernushka.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );
            
            if (distSq <= helpRange * helpRange) {
                helperCount++;
                chernushka.setHelping(true);
                chernushka.setBreakingBlockPos(pos);
                chernushka.setBreakingPlayer(this.player);
            }
        }
        
        if (helperCount > 0) {
            UUID playerId = this.player.getUuid();
            
            // Каждая чернушка даёт бонус к скорости из конфига
            float bonusPercent = config.miningSpeedBonusPercent / 100.0f;
            float speedMultiplier = 1.0f + (helperCount * bonusPercent);
            float bonusProgress = originalProgress * (speedMultiplier - 1.0f);
            
            // Накапливаем бонусный прогресс
            float bonus = chernushka$bonusProgress.getOrDefault(playerId, 0f);
            bonus += bonusProgress;
            
            // Если накопленный бонус >= 1.0 - ломаем блок
            if (bonus >= 1.0f) {
                chernushka$bonusProgress.remove(playerId);
                // Убираем трещины (используем отрицательный ID чтобы не конфликтовать с клиентом)
                this.world.setBlockBreakingInfo(-this.player.getId(), pos, -1);
                // Ломаем блок принудительно
                this.world.breakBlock(pos, true, this.player);
                clearChernushkaHelpers(pos, helpRange);
                cir.setReturnValue(1.0f);
                return;
            }
            
            chernushka$bonusProgress.put(playerId, bonus);
            
            // Отправляем визуал трещин клиенту с ускоренным прогрессом
            // Используем отрицательный ID игрока чтобы не конфликтовать с клиентским рендером
            int breakStage = (int) (bonus * 10.0f);
            if (breakStage > 9) breakStage = 9;
            this.world.setBlockBreakingInfo(-this.player.getId(), pos, breakStage);
            
            // Возвращаем ускоренный прогресс
            cir.setReturnValue(originalProgress * speedMultiplier);
        }
    }
    
    /**
     * Сбрасываем бонусный прогресс когда начинаем ломать новый блок
     */
    @Inject(method = "processBlockBreakingAction", at = @At("HEAD"))
    private void chernushka$resetOnNewBlock(BlockPos pos, net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action action, net.minecraft.util.math.Direction direction, int worldHeight, int sequence, CallbackInfo ci) {
        if (this.player != null && action == net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
            chernushka$bonusProgress.remove(this.player.getUuid());
        }
    }
    
    /**
     * Очищаем состояние чернушек когда блок сломан
     */
    @Inject(method = "finishMining", at = @At("HEAD"))
    private void chernushka$clearHelpers(BlockPos pos, int sequence, String reason, CallbackInfo ci) {
        if (this.player != null) {
            chernushka$bonusProgress.remove(this.player.getUuid());
        }
        clearChernushkaHelpers(pos, Chernushka.getConfig().helpRange);
    }
    
    @Unique
    private void clearChernushkaHelpers(BlockPos pos, double helpRange) {
        if (this.world == null) return;
        
        Box searchBox = new Box(pos).expand(helpRange);
        List<ChernushkaEntity> helpers = this.world.getEntitiesByType(
            ModEntities.CHERNUSHKA,
            searchBox,
            c -> c.isHelping() && pos.equals(c.getBreakingBlockPos())
        );
        
        for (ChernushkaEntity chernushka : helpers) {
            chernushka.setHelping(false);
            chernushka.setBreakingBlockPos(null);
            chernushka.setBreakingPlayer(null);
        }
    }
}
