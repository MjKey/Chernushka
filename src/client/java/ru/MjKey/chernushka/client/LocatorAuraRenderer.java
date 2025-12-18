package ru.MjKey.chernushka.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.MathHelper;

/**
 * Обработчик локатора чернушек.
 * Показывает направление к ближайшей дикой чернушке через dust частицы.
 * Цвет: красный - не найдено, желтый-зеленый по дальности.
 */
public class LocatorAuraRenderer {
    
    private static boolean active = false;
    private static long startTime = 0;
    private static final long DURATION_FOUND_MS = 5000; // 5 секунд если найдено
    private static final long DURATION_NOT_FOUND_MS = 2000; // 2 секунды если не найдено
    
    private static boolean found = false;
    private static float distance = 0f;
    private static float maxDistance = 0f;
    private static float directionX = 0f;
    private static float directionZ = 0f;
    
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }
    
    public static void activate(boolean chernushkaFound, float dist, float dirX, float dirZ, float maxDist) {
        active = true;
        startTime = System.currentTimeMillis();
        found = chernushkaFound;
        distance = dist;
        maxDistance = maxDist;
        directionX = dirX;
        directionZ = dirZ;
    }
    
    private static void tick() {
        if (!active) return;
        
        long elapsed = System.currentTimeMillis() - startTime;
        long duration = found ? DURATION_FOUND_MS : DURATION_NOT_FOUND_MS;
        if (elapsed > duration) {
            active = false;
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        
        // Спавним частицы каждые 2 тика
        if (client.world.getTime() % 2 != 0) return;
        
        double px = client.player.getX();
        double py = client.player.getY() + 1.0;
        double pz = client.player.getZ();
        
        // Создаем dust частицу с нужным цветом
        DustParticleEffect dustParticle;
        
        if (found) {
            // Цвет от желтого (далеко) до зеленого (близко)
            float ratio = MathHelper.clamp(distance / maxDistance, 0f, 1f);
            // ratio 0 = близко = зеленый, ratio 1 = далеко = желтый
            int color = lerpColor(0x00FF00, 0xFFFF00, ratio); // зеленый -> желтый
            dustParticle = new DustParticleEffect(color, 1.0f);
            
            // Частицы в направлении чернушки
            for (int i = 0; i < 5; i++) {
                double offset = 0.5 + i * 0.4;
                double x = px + directionX * offset + (Math.random() - 0.5) * 0.3;
                double y = py + (Math.random() - 0.5) * 0.5;
                double z = pz + directionZ * offset + (Math.random() - 0.5) * 0.3;
                
                client.particleManager.addParticle(
                    dustParticle,
                    x, y, z,
                    directionX * 0.02, 0.01, directionZ * 0.02
                );
            }
        } else {
            // Красные частицы вокруг игрока - не найдено
            dustParticle = new DustParticleEffect(0xFF0000, 1.0f);
            
            for (int i = 0; i < 6; i++) {
                double angle = Math.random() * Math.PI * 2;
                double radius = 1.0 + Math.random() * 0.5;
                double x = px + Math.cos(angle) * radius;
                double y = py + (Math.random() - 0.5) * 0.5;
                double z = pz + Math.sin(angle) * radius;
                
                client.particleManager.addParticle(
                    dustParticle,
                    x, y, z,
                    0, 0.02, 0
                );
            }
        }
    }
    
    /**
     * Линейная интерполяция между двумя цветами
     */
    private static int lerpColor(int color1, int color2, float ratio) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);
        
        return (r << 16) | (g << 8) | b;
    }
}
