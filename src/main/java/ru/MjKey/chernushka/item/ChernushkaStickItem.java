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
import net.minecraft.util.math.MathHelper;
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
 * Shift + ПКМ по воздуху - локатор диких чернушек
 */
public class ChernushkaStickItem extends Item {
    
    private static final int NOT_FOUND_COOLDOWN_TICKS = 10 * 20; // 10 секунд
    private static final int MIN_FOUND_COOLDOWN_TICKS = 3 * 60 * 20; // 3 минуты (близко)
    private static final int MAX_FOUND_COOLDOWN_TICKS = 1 * 60 * 20; // 1 минута (далеко)
    
    public ChernushkaStickItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        // Локатор работает только при приседании
        if (!user.isSneaking()) {
            return ActionResult.PASS;
        }
        
        ItemStack stack = user.getStackInHand(hand);
        
        // Проверяем ванильный кулдаун
        if (user.getItemCooldownManager().isCoolingDown(stack)) {
            return ActionResult.FAIL;
        }
        
        if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
            activateLocator(serverPlayer, (ServerWorld) world, stack);
            return ActionResult.SUCCESS;
        }
        return ActionResult.CONSUME;
    }
    
    private void activateLocator(ServerPlayerEntity player, ServerWorld world, ItemStack stack) {
        // Дальность поиска = дальность прорисовки сервера (в блоках)
        int viewDistance = world.getServer().getPlayerManager().getViewDistance();
        float maxDistance = viewDistance * 16f;
        
        Vec3d playerPos = player.getEntityPos();
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
            // Ванильный кулдаун 10 секунд
            player.getItemCooldownManager().set(stack, NOT_FOUND_COOLDOWN_TICKS);
        } else {
            // Находим ближайшую
            ChernushkaEntity nearest = wildChernushkas.stream()
                .min(Comparator.comparingDouble(c -> c.squaredDistanceTo(playerPos)))
                .orElse(null);
            
            if (nearest != null) {
                Vec3d chernushkaPos = nearest.getEntityPos();
                Vec3d direction = chernushkaPos.subtract(playerPos).normalize();
                float distance = (float) playerPos.distanceTo(chernushkaPos);
                
                ServerPlayNetworking.send(player, new ChernushkaLocatorPayload(
                    true,
                    distance,
                    (float) direction.x,
                    (float) direction.z,
                    maxDistance
                ));
                
                // Кулдаун от 1 до 3 минут в зависимости от расстояния
                // Близко = короткий кулдаун, далеко = длинный кулдаун
                float distanceRatio = MathHelper.clamp(distance / maxDistance, 0f, 1f);
                int cooldownTicks = MIN_FOUND_COOLDOWN_TICKS + 
                    (int) ((MAX_FOUND_COOLDOWN_TICKS - MIN_FOUND_COOLDOWN_TICKS) * distanceRatio);
                player.getItemCooldownManager().set(stack, cooldownTicks);
            }
        }
    }
}
