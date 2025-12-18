package ru.MjKey.chernushka.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import ru.MjKey.chernushka.Chernushka;
import ru.MjKey.chernushka.entity.ModEntities;

public class ModItems {
    
    public static final Item CHERNUSHKA_STICK = registerItem("chernushka_stick",
        new ChernushkaStickItem(new Item.Settings()
            .maxCount(1)
            .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Chernushka.MOD_ID, "chernushka_stick")))
        )
    );
    
    public static final Item CHERNUSHKA_SPAWN_EGG = registerItem("ch_egg",
        new ChernushkaSpawnEggItem(new Item.Settings()
            .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Chernushka.MOD_ID, "ch_egg")))
        )
    );
    
    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(Chernushka.MOD_ID, name), item);
    }
    
    public static void register() {
        Chernushka.LOGGER.info("Registering items...");
        
        // Добавляем в креативную вкладку инструментов
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(CHERNUSHKA_STICK);
        });
        
        // Добавляем яйцо спавна в креативную вкладку
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
            entries.add(CHERNUSHKA_SPAWN_EGG);
        });
    }
}
