package ru.MjKey.chernushka.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import ru.MjKey.chernushka.network.ToggleChernushkasPayload;

public class SneakHandler {
    
    private static boolean wasSneaking = false;
    private static int sneakCount = 0;
    private static long lastSneakTime = 0;
    private static final long DOUBLE_SNEAK_TIMEOUT = 500; // мс между приседаниями
    
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            boolean isSneaking = client.player.isSneaking();
            
            // Детектим начало приседания (переход из не-приседания в приседание)
            if (isSneaking && !wasSneaking) {
                long currentTime = System.currentTimeMillis();
                
                if (currentTime - lastSneakTime < DOUBLE_SNEAK_TIMEOUT) {
                    sneakCount++;
                } else {
                    sneakCount = 1;
                }
                
                lastSneakTime = currentTime;
                
                // Двойное приседание - отправляем пакет
                if (sneakCount >= 2) {
                    ClientPlayNetworking.send(new ToggleChernushkasPayload());
                    sneakCount = 0;
                }
            }
            
            wasSneaking = isSneaking;
        });
    }
}
