package ru.MjKey.chernushka.entity;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import ru.MjKey.chernushka.Chernushka;

public class ModEntities {
    
    public static final EntityType<ChernushkaEntity> CHERNUSHKA = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(Chernushka.MOD_ID, "chernushka"),
            EntityType.Builder.create(ChernushkaEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.6F, 0.7F)
                    .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Chernushka.MOD_ID, "chernushka")))
    );
    
    public static void registerEntities() {
        FabricDefaultAttributeRegistry.register(CHERNUSHKA, ChernushkaEntity.createChernushkaAttributes());
        
        // Спавн во всех биомах (очень редко)
        // weight=1 - очень редкий, minGroupSize=1, maxGroupSize=1
        BiomeModifications.addSpawn(
            BiomeSelectors.all(),
            SpawnGroup.CREATURE,
            CHERNUSHKA,
            1,  // weight - очень низкий для редкого спавна
            1,  // minGroupSize
            1   // maxGroupSize
        );
        
        // Ограничения спавна - на поверхности
        SpawnRestriction.register(
            CHERNUSHKA,
            SpawnLocationTypes.ON_GROUND,
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            (type, world, spawnReason, pos, random) -> true
        );
    }
}
