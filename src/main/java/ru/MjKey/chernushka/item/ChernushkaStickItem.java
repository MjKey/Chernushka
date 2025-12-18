package ru.MjKey.chernushka.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ru.MjKey.chernushka.entity.ChernushkaEntity;
import ru.MjKey.chernushka.entity.ModEntities;
import ru.MjKey.chernushka.network.ChernushkaLocatorPayload;

import java.util.Comparator;
import java.util.List;

/**
 * Палочка для управления чернушками.
 * ПКМ по блоку - задача для чернушек (в BlockBreakHandler)
 * ПКМ по воздуху - локатор диких чернушек
 */
public class ChernushkaStickItem extends Item {
    
    public ChernushkaStickItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
            activateLocator(serverPlayer, (ServerWorld) world);
            return ActionResult.SUCCESS;
        }
        return ActionResult.CONSUME;
    }
    
    private void activateLocator(ServerPlayerEntity player, ServerWorld world) {
        // Дальность поиска = дальность прорисовки сервера (в блоках)
        int viewDistance = world.getServer().getPlayerManager().getViewDistance();
        float maxDistance = viewDistance * 16f;
        
        Vec3d playerPos = player.getPos();
        Box searchBox = new Box(playerPos, playerPos).expand(maxDistance);
        
        // Ищем только диких (не прирученных) чернушек
        List<ChernushkaEntity> wildChernushkas = world.getEntitiesByType(
            ModEntities.CHERNUSHKA,
            searchBox,
            chernushka -> chernushka.getOwnerUuid() == null
        );
        
        if (wildChernushkas.isEmpty()) {
            // Не найдено - отправляем пакет с found=false
            ServerPlayNetworking.send(player, new ChernushkaLocatorPayload(
                false, 0f, 0f, 0f, maxDistance
            ));
        } else {
            // Находим ближайшую
            ChernushkaEntity nearest = wildChernushkas.stream()
                .min(Comparator.comparingDouble(c -> c.squaredDistanceTo(playerPos)))
                .orElse(null);
            
            if (nearest != null) {
                Vec3d chernushkaPos = nearest.getPos();
                Vec3d direction = chernushkaPos.subtract(playerPos).normalize();
                float distance = (float) playerPos.distanceTo(chernushkaPos);
                
                ServerPlayNetworking.send(player, new ChernushkaLocatorPayload(
                    true,
                    distance,
                    (float) direction.x,
                    (float) direction.z,
                    maxDistance
                ));
            }
        }
    }
}
