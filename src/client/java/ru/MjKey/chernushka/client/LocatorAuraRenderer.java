package ru.MjKey.chernushka.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/**
 * Обработчик локатора чернушек.
 * Показывает направление к ближайшей дикой чернушке через сообщения.
 */
public class LocatorAuraRenderer {
    
    public static void activate(boolean chernushkaFound, float dist, float dirX, float dirZ, float maxDist) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        if (!chernushkaFound) {
            client.player.sendMessage(
                Text.literal("Дикие чернушки не найдены поблизости").formatted(Formatting.RED),
                true
            );
        } else {
            // Цвет по дистанции
            float ratio = MathHelper.clamp(dist / maxDist, 0f, 1f);
            Formatting color = ratio < 0.3f ? Formatting.GREEN : 
                               ratio < 0.6f ? Formatting.YELLOW : Formatting.GOLD;
            
            String direction = getDirectionName(dirX, dirZ);
            client.player.sendMessage(
                Text.literal("Чернушка найдена! " + direction + " (~" + (int)dist + " блоков)").formatted(color),
                true
            );
        }
    }
    
    private static String getDirectionName(float x, float z) {
        double angle = Math.atan2(z, x) * 180 / Math.PI;
        if (angle < 0) angle += 360;
        
        if (angle >= 337.5 || angle < 22.5) return "→ Восток";
        if (angle >= 22.5 && angle < 67.5) return "↘ Юго-восток";
        if (angle >= 67.5 && angle < 112.5) return "↓ Юг";
        if (angle >= 112.5 && angle < 157.5) return "↙ Юго-запад";
        if (angle >= 157.5 && angle < 202.5) return "← Запад";
        if (angle >= 202.5 && angle < 247.5) return "↖ Северо-запад";
        if (angle >= 247.5 && angle < 292.5) return "↑ Север";
        return "↗ Северо-восток";
    }
}
