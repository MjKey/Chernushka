package ru.MjKey.chernushka.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.render.entity.EntityRendererFactories;
import ru.MjKey.chernushka.client.entity.ChernushkaRenderer;
import ru.MjKey.chernushka.entity.ModEntities;
import ru.MjKey.chernushka.network.ChernushkaLocatorPayload;

public class ChernushkaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Регистрация GeckoLib рендерера через access widener
        EntityRendererFactories.register(ModEntities.CHERNUSHKA, ChernushkaRenderer::new);
        
        // Обработчик двойного приседания
        SneakHandler.register();
        
        // Локатор чернушек (частицы)
        LocatorAuraRenderer.register();
        
        // Обработчик пакета локатора
        ClientPlayNetworking.registerGlobalReceiver(ChernushkaLocatorPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                LocatorAuraRenderer.activate(
                    payload.found(),
                    payload.distance(),
                    payload.directionX(),
                    payload.directionZ(),
                    payload.maxDistance()
                );
            });
        });
    }
}
