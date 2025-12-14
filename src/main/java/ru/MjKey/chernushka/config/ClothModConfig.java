package ru.MjKey.chernushka.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

/**
 * Cloth Config версия конфигурации.
 * Используется только когда Cloth Config установлен.
 */
@Config(name = "chernushka")
public class ClothModConfig implements ConfigData {
    
    // === General ===
    public boolean enableMiningHelp = true;
    public int miningSpeedBonusPercent = 10;
    public int helpRange = 4;
    public boolean showMiningMessages = true;
    
    // === Follow Behavior ===
    public int followDistance = 10;
    public int stopDistance = 2;
    public int teleportDistance = 24;
    public double walkSpeed = 1.0;
    public double runSpeed = 1.5;
    public int runDistanceThreshold = 8;
    
    /**
     * Конвертирует в обычный ModConfig
     */
    public ModConfig toModConfig() {
        ModConfig config = new ModConfig();
        config.enableMiningHelp = this.enableMiningHelp;
        config.miningSpeedBonusPercent = this.miningSpeedBonusPercent;
        config.helpRange = this.helpRange;
        config.showMiningMessages = this.showMiningMessages;
        config.followDistance = this.followDistance;
        config.stopDistance = this.stopDistance;
        config.teleportDistance = this.teleportDistance;
        config.walkSpeed = this.walkSpeed;
        config.runSpeed = this.runSpeed;
        config.runDistanceThreshold = this.runDistanceThreshold;
        return config;
    }
}
