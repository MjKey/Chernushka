package ru.MjKey.chernushka.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;

/**
 * Обработчик локатора чернушек.
 * Показывает направление к ближайшей дикой чернушке через частицы.
 */
public class LocatorAuraRenderer {
    
    private static boolean active = false;
    private static long startTime = 0;
    private static final long DURATION_MS = 10000; // 10 секунд
    
    private static boolean found = false;
    private static float directionX = 0f;
    private static float directionZ = 0f;
    
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }
    
    public static void activate(boolean chernushkaFound, float dist, float dirX, float dirZ, float maxDist) {
        active = true;
        startTime = System.currentTimeMillis();
        found = chernushkaFound;
        directionX = dirX;
        directionZ = dirZ;
    }
    
    private static void tick() {
        if (!active) return;
        
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > DURATION_MS) {
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
        
        if (found) {
            // Зеленые частицы в направлении чернушки
            for (int i = 0; i < 5; i++) {
                double offset = 0.5 + i * 0.4;
                double x = px + directionX * offset + (Math.random() - 0.5) * 0.3;
                double y = py + (Math.random() - 0.5) * 0.5;
                double z = pz + directionZ * offset + (Math.random() - 0.5) * 0.3;
                
                client.particleManager.addParticle(
                    ParticleTypes.HAPPY_VILLAGER,
                    x, y, z,
                    directionX * 0.02, 0.01, directionZ * 0.02
                );
            }
        } else {
            // Красные/серые частицы вокруг игрока - не найдено
            for (int i = 0; i < 6; i++) {
                double angle = Math.random() * Math.PI * 2;
                double radius = 1.0 + Math.random() * 0.5;
                double x = px + Math.cos(angle) * radius;
                double y = py + (Math.random() - 0.5) * 0.5;
                double z = pz + Math.sin(angle) * radius;
                
                client.particleManager.addParticle(
                    ParticleTypes.SMOKE,
                    x, y, z,
                    0, 0.02, 0
                );
            }
        }
    }
}
