package ru.MjKey.chernushka.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.MjKey.chernushka.entity.ChernushkaEntity;
import ru.MjKey.chernushka.entity.ModEntities;

public class ModCommands {
    
    // Требуется OP level 2 (gamemode, give и т.д.) или читы в одиночке
    private static final Permission ADMIN_PERMISSION = new Permission.Level(PermissionLevel.fromLevel(2));
    
    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerChernushkaCommand(dispatcher);
        });
    }
    
    private static void registerChernushkaCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("chernushka")
                .requires(source -> source.getPermissions().hasPermission(ADMIN_PERMISSION))
                .executes(ModCommands::spawnChernushka)
        );
    }
    
    private static int spawnChernushka(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            var world = source.getWorld();
            ChernushkaEntity chernushka = new ChernushkaEntity(ModEntities.CHERNUSHKA, world);
            
            // Спавним рядом с игроком
            double x = player.getX() + (player.getRandom().nextDouble() - 0.5) * 3;
            double y = player.getY();
            double z = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 3;
            
            chernushka.refreshPositionAndAngles(x, y, z, player.getYaw(), 0);
            // Не привязываем к игроку - нужно приручить кликом
            
            world.spawnEntity(chernushka);
            
            source.sendFeedback(() -> Text.literal("§aДикая чернушка появилась! Кликни на неё чтобы приручить."), false);
            return 1;
        }
        
        source.sendError(Text.literal("§cКоманда доступна только для игроков!"));
        return 0;
    }
}
