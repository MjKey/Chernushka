package ru.MjKey.chernushka.item;

import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import ru.MjKey.chernushka.entity.ModEntities;

/**
 * Яйцо спавна чернушки
 */
public class ChernushkaSpawnEggItem extends Item {
    
    public ChernushkaSpawnEggItem(Settings settings) {
        super(settings);
        
        // Регистрируем поведение для диспенсера
        DispenserBlock.registerBehavior(this, new ItemDispenserBehavior() {
            @Override
            protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
                Direction direction = pointer.state().get(DispenserBlock.FACING);
                EntityType<?> entityType = ModEntities.CHERNUSHKA;
                
                if (pointer.world() instanceof ServerWorld serverWorld) {
                    entityType.spawnFromItemStack(
                        serverWorld,
                        stack,
                        null,
                        pointer.pos().offset(direction),
                        SpawnReason.DISPENSER,
                        direction != Direction.UP,
                        false
                    );
                }
                
                stack.decrement(1);
                pointer.world().emitGameEvent(GameEvent.ENTITY_PLACE, pointer.pos(), GameEvent.Emitter.of(pointer.state()));
                return stack;
            }
        });
    }
    
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.SUCCESS;
        }
        
        ItemStack stack = context.getStack();
        BlockPos pos = context.getBlockPos();
        Direction side = context.getSide();
        
        BlockPos spawnPos = pos.offset(side);
        
        MobEntity entity = ModEntities.CHERNUSHKA.create(serverWorld, SpawnReason.MOB_SUMMONED);
        if (entity != null) {
            entity.refreshPositionAndAngles(
                spawnPos.getX() + 0.5,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5,
                0, 0
            );
            serverWorld.spawnEntity(entity);
            entity.initialize(serverWorld, serverWorld.getLocalDifficulty(spawnPos), SpawnReason.MOB_SUMMONED, null);
            
            stack.decrementUnlessCreative(1, context.getPlayer());
            world.emitGameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, spawnPos);
        }
        
        return ActionResult.SUCCESS;
    }
    
    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        // Использование в воздухе - ничего не делаем
        return ActionResult.PASS;
    }
}
