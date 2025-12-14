package ru.MjKey.chernushka.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.entity.EntityRendererFactories;
import ru.MjKey.chernushka.client.entity.ChernushkaRenderer;
import ru.MjKey.chernushka.entity.ModEntities;

public class ChernushkaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Регистрация GeckoLib рендерера через access widener
        EntityRendererFactories.register(ModEntities.CHERNUSHKA, ChernushkaRenderer::new);
        
        // Обработчик двойного приседания
        SneakHandler.register();
    }
}
