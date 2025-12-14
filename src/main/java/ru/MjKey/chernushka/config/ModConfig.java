package ru.MjKey.chernushka.config;

/**
 * Конфигурация мода Chernushka.
 * Поддерживает Cloth Config (если установлен) или простой JSON файл.
 */
@me.shedaniel.autoconfig.annotation.Config(name = "chernushka")
public class ModConfig implements me.shedaniel.autoconfig.ConfigData {
    
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
}
