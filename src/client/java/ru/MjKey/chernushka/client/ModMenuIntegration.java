package ru.MjKey.chernushka.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.MjKey.chernushka.config.ConfigManager;
import ru.MjKey.chernushka.config.ModConfig;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
    
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Возвращаем экран конфига только если Cloth Config доступен
        if (ConfigManager.isClothConfigAvailable()) {
            return parent -> me.shedaniel.autoconfig.AutoConfig.getConfigScreen(ModConfig.class, parent).get();
        }
        return parent -> null;
    }
}
