package ru.MjKey.chernushka.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import ru.MjKey.chernushka.Chernushka;
import ru.MjKey.chernushka.entity.ChernushkaEntity;
import ru.MjKey.chernushka.entity.ModAttachments;
import ru.MjKey.chernushka.entity.ModEntities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModNetworking {
    
    public static final Identifier TOGGLE_CHERNUSHKAS_ID = Identifier.of(Chernushka.MOD_ID, "toggle_chernushkas");
    
    public static void register() {
        // C2S пакеты
        PayloadTypeRegistry.playC2S().register(ToggleChernushkasPayload.ID, ToggleChernushkasPayload.CODEC);
        
        // S2C пакеты
        PayloadTypeRegistry.playS2C().register(ChernushkaLocatorPayload.ID, ChernushkaLocatorPayload.CODEC);
        
        ServerPlayNetworking.registerGlobalReceiver(ToggleChernushkasPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            
            context.server().execute(() -> {
                toggleChernushkas(player, world);
            });
        });
    }
    
    private static void toggleChernushkas(ServerPlayerEntity player, ServerWorld world) {
        UUID playerUuid = player.getUuid();
        
        // Получаем скрытых чернушек из Attachment API
        List<ModAttachments.HiddenChernushkaData> hiddenList = player.getAttachedOrCreate(
            ModAttachments.HIDDEN_CHERNUSHKAS, 
            ArrayList::new
        );
        
        // Проверяем, есть ли скрытые чернушки для этого игрока
        if (!hiddenList.isEmpty()) {
            // Восстанавливаем чернушек рядом с игроком с анимацией show
            BlockPos playerPos = player.getBlockPos();
            int index = 0;
            int total = hiddenList.size();
            for (ModAttachments.HiddenChernushkaData data : hiddenList) {
                ChernushkaEntity chernushka = ModEntities.CHERNUSHKA.create(world, net.minecraft.entity.SpawnReason.COMMAND);
                if (chernushka != null) {
                    chernushka.setOwnerUuid(playerUuid);
                    chernushka.setMergeLevel(data.mergeLevel());
                    // Спавним рядом с игроком по кругу
                    double angle = (2 * Math.PI * index) / total;
                    double offsetX = Math.cos(angle) * 2.0;
                    double offsetZ = Math.sin(angle) * 2.0;
                    chernushka.refreshPositionAndAngles(
                        playerPos.getX() + 0.5 + offsetX,
                        playerPos.getY(),
                        playerPos.getZ() + 0.5 + offsetZ,
                        0, 0
                    );
                    // Запускаем анимацию show
                    chernushka.setShowing(true);
                    world.spawnEntity(chernushka);
                    index++;
                }
            }
            Chernushka.LOGGER.info("Restored {} chernushkas for player {}", total, player.getName().getString());
            // Очищаем список скрытых чернушек
            player.setAttached(ModAttachments.HIDDEN_CHERNUSHKAS, new ArrayList<>());
        } else {
            // Скрываем чернушек (мгновенно)
            List<ChernushkaEntity> ownedChernushkas = world.getEntitiesByType(
                ModEntities.CHERNUSHKA,
                player.getBoundingBox().expand(64),
                entity -> playerUuid.equals(entity.getOwnerUuid())
            );
            
            if (ownedChernushkas.isEmpty()) return;
            
            List<ModAttachments.HiddenChernushkaData> dataList = new ArrayList<>();
            for (ChernushkaEntity chernushka : ownedChernushkas) {
                // Сохраняем уровень слияния
                dataList.add(new ModAttachments.HiddenChernushkaData(chernushka.getMergeLevel()));
                chernushka.discard();
            }
            // Сохраняем в Attachment API (персистентно)
            player.setAttached(ModAttachments.HIDDEN_CHERNUSHKAS, dataList);
            Chernushka.LOGGER.info("Hidden {} chernushkas for player {}", dataList.size(), player.getName().getString());
        }
    }
}
