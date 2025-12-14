package ru.MjKey.chernushka.event;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import ru.MjKey.chernushka.Chernushka;
import ru.MjKey.chernushka.entity.ChernushkaEntity;
import ru.MjKey.chernushka.entity.ModAttachments;
import ru.MjKey.chernushka.entity.ModEntities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Обработчик входа игрока - телепортирует чернушек из выгруженных чанков
 */
public class PlayerJoinHandler {
    
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            
            // Задержка чтобы игрок полностью загрузился
            server.execute(() -> {
                server.execute(() -> {
                    teleportChernushkasToPlayer(player);
                });
            });
        });
    }
    
    private static void teleportChernushkasToPlayer(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        
        // Ищем всех чернушек игрока в текущем мире
        List<ChernushkaEntity> ownedChernushkas = new ArrayList<>(world.getEntitiesByType(
            ModEntities.CHERNUSHKA,
            c -> c.isTamed() && playerUuid.equals(c.getOwnerUuid())
        ));
        
        if (ownedChernushkas.isEmpty()) {
            return;
        }
        
        BlockPos playerPos = player.getBlockPos();
        int teleported = 0;
        
        for (ChernushkaEntity chernushka : ownedChernushkas) {
            double distSq = chernushka.squaredDistanceTo(player);
            
            // Телепортируем только если далеко (>50 блоков)
            if (distSq > 2500) {
                // Телепортируем рядом с игроком
                double angle = (2 * Math.PI * teleported) / Math.max(1, ownedChernushkas.size());
                double offsetX = Math.cos(angle) * 2.0;
                double offsetZ = Math.sin(angle) * 2.0;
                
                chernushka.refreshPositionAndAngles(
                    playerPos.getX() + 0.5 + offsetX,
                    playerPos.getY(),
                    playerPos.getZ() + 0.5 + offsetZ,
                    chernushka.getYaw(),
                    chernushka.getPitch()
                );
                teleported++;
            }
        }
        
        if (teleported > 0) {
            Chernushka.LOGGER.info("Teleported {} chernushkas to player {}", teleported, player.getName().getString());
        }
    }
}
