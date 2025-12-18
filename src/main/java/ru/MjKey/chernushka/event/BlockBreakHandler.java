package ru.MjKey.chernushka.event;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import ru.MjKey.chernushka.entity.ModEntities;
import ru.MjKey.chernushka.entity.MiningTaskManager;
import ru.MjKey.chernushka.item.ModItems;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlockBreakHandler {
    
    // Храним информацию о том, какой блок ломает каждый игрок
    private static final Map<UUID, BlockPos> playerBreakingBlocks = new HashMap<>();
    private static final Map<UUID, Long> lastBreakTime = new HashMap<>();
    
    public static void register() {
        System.out.println("[Chernushka] BlockBreakHandler registered!");
        
        
        
            // Событие когда игрок кликает ПКМ по блоку с палочкой для чернушек - добавляем задачу
            UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
                if (!world.isClient() && player.getStackInHand(hand).isOf(ModItems.CHERNUSHKA_STICK) && !player.isSneaking()) {
                    BlockPos pos = hitResult.getBlockPos();
                    
                    // Проверяем что блок не воздух
                    if (!world.getBlockState(pos).isAir()) {
                        assignMiningTaskToChernushka(world, player, pos);
                        return ActionResult.SUCCESS;
                    }
                }
                return ActionResult.PASS;
                
            });
        
        // Событие когда игрок начинает ломать блок (каждый тик атаки)
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient()) {
                // Палочка для чернушек - пропускаем, чтобы можно было ломать блоки
                if (player.getStackInHand(hand).isOf(ModItems.CHERNUSHKA_STICK)) {
                    return ActionResult.PASS;
                }
                
                // Запоминаем какой блок ломает игрок
                playerBreakingBlocks.put(player.getUuid(), pos);
                lastBreakTime.put(player.getUuid(), world.getTime());
                
                // Уведомляем всех Чернушек рядом
                notifyNearbyChernushkas(world, player, pos);
            }
            return ActionResult.PASS;
        });
        
        // Событие когда блок сломан - очищаем состояние
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            clearPlayerBreaking(player.getUuid());
            clearNearbyChernushkas(world, pos);
        });
    }
    
    private static void notifyNearbyChernushkas(World world, PlayerEntity player, BlockPos pos) {
        // Ищем всех Чернушек в радиусе 16 блоков
        Box searchBox = new Box(pos).expand(16.0D);
        
        var chernushkas = world.getEntitiesByType(ModEntities.CHERNUSHKA, searchBox, chernushka -> true);
        System.out.println("[Chernushka] Found " + chernushkas.size() + " chernushkas near block " + pos);
        
        chernushkas.forEach(chernushka -> {
            chernushka.setBreakingBlockPos(pos);
            chernushka.setBreakingPlayer(player);
            System.out.println("[Chernushka] Notified chernushka at " + chernushka.getBlockPos());
        });
    }
    
    private static void clearNearbyChernushkas(World world, BlockPos pos) {
        Box searchBox = new Box(pos).expand(16.0D);
        
        world.getEntitiesByType(ModEntities.CHERNUSHKA, searchBox, chernushka -> true)
            .forEach(chernushka -> {
                if (pos.equals(chernushka.getBreakingBlockPos())) {
                    chernushka.setBreakingBlockPos(null);
                    chernushka.setBreakingPlayer(null);
                }
            });
    }
    
    public static void clearPlayerBreaking(UUID playerId) {
        playerBreakingBlocks.remove(playerId);
        lastBreakTime.remove(playerId);
    }
    
    public static BlockPos getPlayerBreakingBlock(UUID playerId) {
        return playerBreakingBlocks.get(playerId);
    }
    
    public static long getLastBreakTime(UUID playerId) {
        return lastBreakTime.getOrDefault(playerId, 0L);
    }
    
    /**
     * Добавить задачу в глобальную очередь
     */
    private static void assignMiningTaskToChernushka(World world, PlayerEntity player, BlockPos pos) {
        // Проверяем есть ли Чернушки рядом
        Box searchBox = new Box(player.getBlockPos()).expand(32.0D);
        
        var chernushkas = world.getEntitiesByType(
            ModEntities.CHERNUSHKA, 
            searchBox, 
            c -> true
        );
        
        if (chernushkas.isEmpty()) {
            if (ru.MjKey.chernushka.Chernushka.getConfig().showMiningMessages) {
                player.sendMessage(Text.translatable("message.chernushka.no_nearby"), true);
            }
            return;
        }
        
        // Добавляем в глобальную очередь
        MiningTaskManager.AddTaskResult result = MiningTaskManager.addTask(pos);
        if (ru.MjKey.chernushka.Chernushka.getConfig().showMiningMessages) {
            switch (result) {
                case SUCCESS -> player.sendMessage(Text.translatable("message.chernushka.task_added", MiningTaskManager.getQueueSize()), true);
                case ALREADY_IN_QUEUE -> player.sendMessage(Text.translatable("message.chernushka.already_queued"), true);
                case QUEUE_FULL -> player.sendMessage(Text.translatable("message.chernushka.queue_full", MiningTaskManager.MAX_QUEUE_SIZE), true);
            }
        }
    }
}
