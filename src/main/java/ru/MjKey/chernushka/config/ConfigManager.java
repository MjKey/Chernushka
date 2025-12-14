package ru.MjKey.chernushka.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ru.MjKey.chernushka.Chernushka;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Менеджер конфигурации с поддержкой Cloth Config (опционально)
 */
public class ConfigManager {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig config;
    private static boolean clothConfigAvailable = false;
    
    public static void init() {
        // Проверяем наличие Cloth Config
        clothConfigAvailable = FabricLoader.getInstance().isModLoaded("cloth-config");
        
        if (clothConfigAvailable) {
            try {
                initClothConfig();
                Chernushka.LOGGER.info("Cloth Config detected, using AutoConfig");
            } catch (Throwable e) {
                Chernushka.LOGGER.warn("Failed to init Cloth Config, falling back to JSON config", e);
                clothConfigAvailable = false;
                initJsonConfig();
            }
        } else {
            Chernushka.LOGGER.info("Cloth Config not found, using JSON config");
            initJsonConfig();
        }
    }
    
    private static void initClothConfig() {
        me.shedaniel.autoconfig.AutoConfig.register(ModConfig.class, 
            me.shedaniel.autoconfig.serializer.GsonConfigSerializer::new);
    }
    
    private static void initJsonConfig() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("chernushka.json");
        
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                config = GSON.fromJson(json, ModConfig.class);
                if (config == null) {
                    config = new ModConfig();
                }
            } catch (IOException e) {
                Chernushka.LOGGER.error("Failed to load config", e);
                config = new ModConfig();
            }
        } else {
            config = new ModConfig();
            saveJsonConfig();
        }
    }
    
    private static void saveJsonConfig() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("chernushka.json");
        try {
            Files.writeString(configPath, GSON.toJson(config));
        } catch (IOException e) {
            Chernushka.LOGGER.error("Failed to save config", e);
        }
    }
    
    public static ModConfig getConfig() {
        if (clothConfigAvailable) {
            try {
                return me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).getConfig();
            } catch (Throwable e) {
                // Fallback
            }
        }
        return config != null ? config : new ModConfig();
    }
    
    public static boolean isClothConfigAvailable() {
        return clothConfigAvailable;
    }
}
