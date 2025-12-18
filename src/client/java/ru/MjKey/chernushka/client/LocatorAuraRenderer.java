package ru.MjKey.chernushka.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Рендерер ауры локатора чернушек.
 * Показывает направление к ближайшей дикой чернушке.
 */
public class LocatorAuraRenderer {
    
    private static boolean active = false;
    private static long startTime = 0;
    private static final long DURATION_MS = 10000; // 10 секунд
    
    private static boolean found = false;
    private static float distance = 0f;
    private static float directionX = 0f;
    private static float directionZ = 0f;
    private static float maxDistance = 0f;
    
    public static void register() {
        WorldRenderEvents.LAST.register(LocatorAuraRenderer::render);
    }
    
    public static void activate(boolean chernushkaFound, float dist, float dirX, float dirZ, float maxDist) {
        active = true;
        startTime = System.currentTimeMillis();
        found = chernushkaFound;
        distance = dist;
        directionX = dirX;
        directionZ = dirZ;
        maxDistance = maxDist;
    }
    
    private static void render(WorldRenderContext context) {
        if (!active) return;
        
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > DURATION_MS) {
            active = false;
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        // Прозрачность уменьшается к концу
        float alpha = 1.0f - (elapsed / (float) DURATION_MS);
        alpha = MathHelper.clamp(alpha, 0.1f, 0.6f);
        
        // Цвет ауры
        float red, green, blue;
        if (!found) {
            // Красная аура - не найдено
            red = 1.0f;
            green = 0.2f;
            blue = 0.2f;
        } else {
            // От желтого (далеко) до зеленого (близко)
            float ratio = MathHelper.clamp(distance / maxDistance, 0f, 1f);
            red = ratio;
            green = 1.0f;
            blue = 0.2f;
        }
        
        Vec3d cameraPos = context.camera().getPos();
        Vec3d playerPos = client.player.getPos();
        
        // Смещение относительно камеры
        double offsetX = playerPos.x - cameraPos.x;
        double offsetY = playerPos.y - cameraPos.y + 1.0; // На уровне груди
        double offsetZ = playerPos.z - cameraPos.z;
        
        context.matrixStack().push();
        context.matrixStack().translate(offsetX, offsetY, offsetZ);
        
        Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        
        float radius = 1.5f;
        int segments = 32;
        
        if (found) {
            // Направленная аура - конус в сторону чернушки
            float angle = (float) Math.atan2(directionZ, directionX);
            float coneAngle = (float) Math.PI / 4; // 45 градусов
            
            // Центр
            buffer.vertex(matrix, 0, 0, 0).color(red, green, blue, alpha * 0.8f);
            
            // Дуга в направлении чернушки
            for (int i = 0; i <= segments / 2; i++) {
                float segmentAngle = angle - coneAngle + (2 * coneAngle * i / (segments / 2f));
                float x = (float) Math.cos(segmentAngle) * radius;
                float z = (float) Math.sin(segmentAngle) * radius;
                buffer.vertex(matrix, x, 0, z).color(red, green, blue, alpha * 0.3f);
            }
        } else {
            // Круговая красная аура
            buffer.vertex(matrix, 0, 0, 0).color(red, green, blue, alpha * 0.5f);
            
            for (int i = 0; i <= segments; i++) {
                float segmentAngle = (float) (2 * Math.PI * i / segments);
                float x = (float) Math.cos(segmentAngle) * radius;
                float z = (float) Math.sin(segmentAngle) * radius;
                buffer.vertex(matrix, x, 0, z).color(red, green, blue, alpha * 0.2f);
            }
        }
        
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        
        context.matrixStack().pop();
    }
}
