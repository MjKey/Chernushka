package ru.MjKey.chernushka;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.MjKey.chernushka.advancement.ModCriteria;
import ru.MjKey.chernushka.command.ModCommands;
import ru.MjKey.chernushka.config.ConfigManager;
import ru.MjKey.chernushka.config.ModConfig;
import ru.MjKey.chernushka.entity.ModAttachments;
import ru.MjKey.chernushka.entity.ModEntities;
import ru.MjKey.chernushka.event.BlockBreakHandler;
import ru.MjKey.chernushka.event.PlayerJoinHandler;
import ru.MjKey.chernushka.item.ModItems;
import ru.MjKey.chernushka.network.ModNetworking;

public class Chernushka implements ModInitializer {
    
    public static final String MOD_ID = "chernushka";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Чернушки загружается...");
        
        // Регистрируем конфиг (с поддержкой Cloth Config если есть)
        ConfigManager.init();
        
        ModAttachments.register();
        ModEntities.registerEntities();
        ModItems.register();
        ModCriteria.register();
        ModCommands.registerCommands();
        BlockBreakHandler.register();
        PlayerJoinHandler.register();
        ModNetworking.register();
        
        LOGGER.info("Чернушки готовы!");
    }
    
    public static ModConfig getConfig() {
        return ConfigManager.getConfig();
    }
}
