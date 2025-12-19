package ru.MjKey.chernushka.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class ChernushkaEntity extends PathAwareEntity implements GeoEntity {
    
    private static final TrackedData<Boolean> HELPING = DataTracker.registerData(ChernushkaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> CONFUSED = DataTracker.registerData(ChernushkaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<String> OWNER_UUID_STRING = DataTracker.registerData(ChernushkaEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> MERGE_LEVEL = DataTracker.registerData(ChernushkaEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> MERGE_TARGET_ID = DataTracker.registerData(ChernushkaEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> RUNNING = DataTracker.registerData(ChernushkaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> SHOWING = DataTracker.registerData(ChernushkaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> MINING = DataTracker.registerData(ChernushkaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    
    // Размножение
    private static final TrackedData<Integer> BREEDING_TARGET_ID = DataTracker.registerData(ChernushkaEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> LOVE_TICKS = DataTracker.registerData(ChernushkaEntity.class, TrackedDataHandlerRegistry.INTEGER);

    
    // Уровни слияния: 0=1x, 1=1.5x, 2=2x, 3=2.5x, 4=3x, 5=5x (максимум 5 чернушек в одной)
    public static final int MAX_MERGE_LEVEL = 5;
    public static final float[] SCALE_BY_LEVEL = {1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 5.0f};
    
    // Статический кэш для системы слияния: playerId -> UUID первой выбранной чернушки
    private static final java.util.Map<UUID, UUID> pendingMergeSelections = new java.util.HashMap<>();
    
    // Статический кэш для размножения: playerId -> UUID первой выбранной чернушки
    private static final java.util.Map<UUID, UUID> pendingBreedingSelections = new java.util.HashMap<>();
    
    // Кулдаун размножения
    private int breedingCooldown = 0;
    private static final int BREEDING_COOLDOWN_TICKS = 5 * 60 * 20; // 5 минут
    
    // Таймер бурения блоков при езде
    private int drillCooldown = 0;
    private static final int DRILL_INTERVAL = 2; // Каждые 2 тика ломаем блок (быстрее)
    
    // Прогресс ломания твёрдого блока
    @Nullable
    private BlockPos hardBlockPos = null;
    private int hardBlockProgress = 0;
    private static final int BEDROCK_BREAK_TICKS = 3 * 60 * 20; // 3 минуты для бедрока
    private static final int HARD_BLOCK_BREAK_TICKS = 10 * 20;  // 10 секунд для обсидиана и т.п.
    
    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    protected static final RawAnimation STOP_WALK_ANIM = RawAnimation.begin().thenPlay("stop_walk");
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("Idle");
    protected static final RawAnimation LOMKA_ANIM = RawAnimation.begin().thenLoop("lomka");
    protected static final RawAnimation OGLYADKA_ANIM = RawAnimation.begin().thenPlay("oglyadka");
    protected static final RawAnimation START_JUMP_ANIM = RawAnimation.begin().thenPlay("start_jump");
    protected static final RawAnimation JUMP_ANIM = RawAnimation.begin().thenLoop("jump");
    protected static final RawAnimation END_JUMP_ANIM = RawAnimation.begin().thenPlay("end_jump");
    protected static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("run");
    protected static final RawAnimation SHOW_ANIM = RawAnimation.begin().thenPlay("show");
    
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    
    @Nullable
    private PlayerEntity ownerCache;
    
    @Nullable
    private BlockPos breakingBlockPos;
    @Nullable
    private PlayerEntity breakingPlayer;
    private int breakingTimeout = 0;
    

    
    // Таймер для анимации oglyadka (confused)
    private int confusedTicks = 0;
    private static final int CONFUSED_DURATION = 40; // 2 секунды
    
    // Состояния прыжка/падения
    private boolean wasOnGround = true;
    private boolean wasFalling = false;
    private boolean isJumpingUp = false;
    private boolean isLanding = false;
    private int jumpUpTicks = 0;
    private int landingTicks = 0;
    private static final int JUMP_UP_DURATION = 6; // 0.3 секунды для start_jump
    private static final int LANDING_DURATION = 10; // 0.5 секунды для end_jump
    
    // Состояние остановки после ходьбы
    private boolean wasMoving = false;
    private boolean isStopping = false;
    private int stoppingTicks = 0;
    private static final int STOPPING_DURATION = 5; // 0.25 секунды для stop_walk
    
    // Состояние show анимации
    private int showingTicks = 0;
    private static final int SHOW_DURATION = 20; // 1 секунда (длина анимации show)
    
    // Деспавн диких чернушек
    private int despawnTimer = -1; // -1 = не инициализирован
    private static final int MIN_DESPAWN_TICKS = 5 * 60 * 20; // 5 минут в тиках
    private static final int MAX_DESPAWN_TICKS = 20 * 60 * 20; // 20 минут в тиках

    
    public ChernushkaEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        // Разрешаем ходить по воде (по дну) без штрафа
        this.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
        this.setPathfindingPenalty(PathNodeType.WATER_BORDER, 0.0F);
    }
    
    /**
     * Вызывается после загрузки entity для восстановления owner
     */
    private void syncOwnerFromAttachment() {
        if (!this.getEntityWorld().isClient()) {
            String currentUuid = this.dataTracker.get(OWNER_UUID_STRING);
            if (currentUuid == null || currentUuid.isEmpty()) {
                String attached = this.getAttachedOrElse(ModAttachments.OWNER_UUID, "");
                if (!attached.isEmpty()) {
                    this.dataTracker.set(OWNER_UUID_STRING, attached);
                }
            }
        }
    }
    
    // Флаг для однократной загрузки owner при первом tick
    private boolean ownerLoaded = false;
    
    @Override
    public void tick() {
        super.tick();
        
        if (!this.getEntityWorld().isClient()) {
            // Загружаем owner из attachment при первом tick после загрузки мира
            if (!ownerLoaded) {
                ownerLoaded = true;
                loadOwnerFromAttachment();
            }
            
            // Обработка таймера confused анимации
            if (this.confusedTicks > 0) {
                this.confusedTicks--;
                if (this.confusedTicks <= 0) {
                    setConfused(false);
                }
            }
            
            // Обработка состояний прыжка/падения
            updateJumpState();
            
            // Обработка состояния остановки после ходьбы
            updateStoppingState();
            
            // Обработка процесса слияния
            processMergeMovement();
            
            // Обработка процесса размножения
            processBreedingMovement();
            
            // Кулдаун размножения
            if (this.breedingCooldown > 0) {
                this.breedingCooldown--;
            }
            
            // Love ticks (сердечки)
            int loveTicks = getLoveTicks();
            if (loveTicks > 0) {
                setLoveTicks(loveTicks - 1);
                // Спавним сердечки
                if (loveTicks % 10 == 0 && this.getEntityWorld() instanceof ServerWorld serverWorld) {
                    serverWorld.spawnParticles(
                        ParticleTypes.HEART,
                        this.getX(), this.getY() + this.getHeight(), this.getZ(),
                        1, 0.3, 0.3, 0.3, 0
                    );
                }
            }
            
            // Бурение блоков при езде (живой бур!)
            if (this.hasPassengers() && getMergeLevel() >= MAX_MERGE_LEVEL) {
                processDrilling();
            } else {
                // Сбрасываем анимацию бурения если нет пассажира
                if (isHelping() && this.breakingBlockPos == null) {
                    setHelping(false);
                }
            }
            
            // Обработка анимации show
            if (this.showingTicks > 0) {
                this.showingTicks--;
                if (this.showingTicks <= 0) {
                    setShowing(false);
                }
            }
            
            if (this.breakingBlockPos != null) {
                this.breakingTimeout++;
                
                // Оптимизация: проверяем блок каждые 10 тиков
                if (this.breakingTimeout % 10 == 0) {
                    if (this.getEntityWorld().getBlockState(this.breakingBlockPos).isAir()) {
                        this.clearBreakingState();
                        return;
                    }
                }
                
                if (this.breakingTimeout > 40) {
                    this.clearBreakingState();
                }
            }
        }
    }
    
    /**
     * Загружает owner UUID и merge level из attachment и синхронизирует в DataTracker
     * Вызывается при первом tick после загрузки мира
     */
    private void loadOwnerFromAttachment() {
        // Загружаем owner - ВСЕГДА перезаписываем из attachment если есть
        String attached = this.getAttachedOrElse(ModAttachments.OWNER_UUID, "");
        if (!attached.isEmpty()) {
            this.dataTracker.set(OWNER_UUID_STRING, attached);
        }
        
        // Загружаем merge level - ВСЕГДА перезаписываем из attachment
        int savedLevel = this.getAttachedOrElse(ModAttachments.MERGE_LEVEL, 0);
        this.dataTracker.set(MERGE_LEVEL, savedLevel);
    }
    
    @Override
    public boolean canBreatheInWater() {
        // Может дышать под водой, но не плавает - ходит по дну
        return true;
    }
    
    @Override
    public boolean isPushedByFluids() {
        // Не сносит течением - стоит на дне
        return false;
    }
    
    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        // Иммунитет к урону от утопления, удушения и падения
        if (source.isIn(DamageTypeTags.IS_DROWNING) || 
            source.isOf(DamageTypes.IN_WALL) || 
            source.isIn(DamageTypeTags.IS_FALL)) {
            return false;
        }
        return super.damage(world, source, amount);
    }
    
    @Override
    public boolean isPushable() {
        return true;
    }
    
    @Override
    protected void pushAway(Entity entity) {
        if (!(entity instanceof PlayerEntity)) {
            super.pushAway(entity);
        }
    }
    
    @Override
    public boolean canAvoidTraps() {
        return true;
    }
    
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(HELPING, false);
        builder.add(CONFUSED, false);
        builder.add(OWNER_UUID_STRING, "");
        builder.add(MERGE_LEVEL, 0);
        builder.add(MERGE_TARGET_ID, -1);
        builder.add(RUNNING, false);
        builder.add(SHOWING, false);
        builder.add(BREEDING_TARGET_ID, -1);
        builder.add(LOVE_TICKS, 0);
        builder.add(MINING, false);
    }
    

    
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.getEntityWorld().isClient()) {
            ItemStack stack = player.getStackInHand(hand);
            
            // Приручение - просто кликнуть пустой рукой
            if (getOwnerUuid() == null && stack.isEmpty()) {
                setOwnerUuid(player.getUuid());
                this.ownerCache = player;
                // Показываем сердечки
                spawnHeartParticles();
                // Триггер достижения
                if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                    ru.MjKey.chernushka.advancement.ModCriteria.TAME_CHERNUSHKA.trigger(serverPlayer);
                    checkArmyAchievement(serverPlayer);
                }
                return ActionResult.SUCCESS;
            }
            
            // Слияние - кликаем углём или кремнем по двум чернушкам
            if (isTamed() && (stack.isOf(Items.COAL) || stack.isOf(Items.FLINT))) {
                if (handleMergeClick(player, stack)) {
                    return ActionResult.SUCCESS;
                }
            }
            
            // Разделение - пером по большой чернушке
            if (isTamed() && stack.isOf(Items.FEATHER) && getMergeLevel() > 0) {
                splitIntoMultiple(player);
                if (!player.isCreative()) {
                    stack.decrement(1);
                }
                return ActionResult.SUCCESS;
            }
            
            // Щекотка - пером по обычной чернушке
            if (isTamed() && stack.isOf(Items.FEATHER) && getMergeLevel() == 0) {
                // Триггер достижения
                if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                    ru.MjKey.chernushka.advancement.ModCriteria.TICKLE_CHERNUSHKA.trigger(serverPlayer);
                }
                // Показываем частицы смеха
                if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
                    for (int i = 0; i < 5; i++) {
                        serverWorld.spawnParticles(
                            ParticleTypes.HAPPY_VILLAGER,
                            this.getX() + random.nextGaussian() * 0.3,
                            this.getY() + 0.5 + random.nextDouble() * 0.5,
                            this.getZ() + random.nextGaussian() * 0.3,
                            1, 0, 0, 0, 0
                        );
                    }
                }
                return ActionResult.SUCCESS;
            }
            
            // Размножение - аметистом по двум чернушкам
            if (isTamed() && stack.isOf(Items.AMETHYST_SHARD) && getMergeLevel() == 0) {
                if (handleBreedingClick(player, stack)) {
                    return ActionResult.SUCCESS;
                }
            }
            
            // Верховая езда - на гигантской чернушке (level 5)
            if (isTamed() && stack.isEmpty() && getMergeLevel() >= MAX_MERGE_LEVEL) {
                UUID ownerUuid = getOwnerUuid();
                if (ownerUuid != null && ownerUuid.equals(player.getUuid())) {
                    player.startRiding(this);
                    return ActionResult.SUCCESS;
                }
            }
        }
        return super.interactMob(player, hand);
    }
    
    /**
     * Спавнит частицы сердечек при приручении
     */
    private void spawnHeartParticles() {
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            for (int i = 0; i < 7; i++) {
                serverWorld.spawnParticles(
                    ParticleTypes.HEART,
                    this.getX() + random.nextGaussian() * 0.3,
                    this.getY() + 0.8 + random.nextDouble() * 0.5,
                    this.getZ() + random.nextGaussian() * 0.3,
                    1, 0, 0, 0, 0
                );
            }
        }
    }
    
    /**
     * Обрабатывает клик для слияния (система двух кликов)
     */
    private boolean handleMergeClick(PlayerEntity player, ItemStack stack) {
        UUID playerId = player.getUuid();
        UUID firstChernushkaUuid = pendingMergeSelections.get(playerId);
        
        if (firstChernushkaUuid == null) {
            // Первый клик - запоминаем эту чернушку по UUID
            pendingMergeSelections.put(playerId, this.getUuid());
            // Показываем что чернушка выбрана (частицы)
            if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    this.getX(), this.getY() + 1, this.getZ(),
                    5, 0.3, 0.3, 0.3, 0
                );
            }
            return true;
        }
        
        // Второй клик
        if (firstChernushkaUuid.equals(this.getUuid())) {
            // Кликнули по той же чернушке - отменяем выбор
            pendingMergeSelections.remove(playerId);
            return true;
        }
        
        // Ищем первую чернушку по UUID
        ChernushkaEntity firstChernushka = findChernushkaByUuid(firstChernushkaUuid);
        if (firstChernushka == null || !firstChernushka.isAlive()) {
            // Первая чернушка не найдена - начинаем заново с этой
            pendingMergeSelections.put(playerId, this.getUuid());
            if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    this.getX(), this.getY() + 1, this.getZ(),
                    5, 0.3, 0.3, 0.3, 0
                );
            }
            return true;
        }
        
        // Проверяем что обе принадлежат этому игроку (или обе дикие)
        UUID firstOwner = firstChernushka.getOwnerUuid();
        UUID secondOwner = this.getOwnerUuid();
        boolean bothTamed = firstOwner != null && secondOwner != null && firstOwner.equals(playerId) && secondOwner.equals(playerId);
        boolean bothWild = firstOwner == null && secondOwner == null;
        
        if (!bothTamed && !bothWild) {
            pendingMergeSelections.remove(playerId);
            return false;
        }
        
        // Проверяем лимит слияния
        int totalLevel = firstChernushka.getMergeLevel() + this.getMergeLevel() + 1;
        if (totalLevel > MAX_MERGE_LEVEL) {
            pendingMergeSelections.remove(playerId);
            return false;
        }
        
        // Очищаем выбор
        pendingMergeSelections.remove(playerId);
        
        // Запускаем процесс слияния - чернушки идут друг к другу
        startMergeWalking(firstChernushka, this, player, stack);
        return true;
    }
    
    /**
     * Ищет чернушку по UUID в мире
     */
    @Nullable
    private ChernushkaEntity findChernushkaByUuid(UUID uuid) {
        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) return null;
        Entity entity = serverWorld.getEntity(uuid);
        if (entity instanceof ChernushkaEntity chernushka) {
            return chernushka;
        }
        return null;
    }
    
    /**
     * Запускает процесс слияния - чернушки идут друг к другу
     */
    private void startMergeWalking(ChernushkaEntity first, ChernushkaEntity second, PlayerEntity player, ItemStack stack) {
        // Устанавливаем цели движения друг к другу (по UUID)
        first.setMergeTargetId(second.getId());
        second.setMergeTargetId(first.getId());
        
        // Тратим предмет
        if (!player.isCreative()) {
            stack.decrement(1);
        }
        
        // Показываем частицы на обеих
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, first.getX(), first.getY() + 1, first.getZ(), 3, 0.3, 0.3, 0.3, 0);
            serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, second.getX(), second.getY() + 1, second.getZ(), 3, 0.3, 0.3, 0.3, 0);
        }
    }
    
    /**
     * Обрабатывает движение к цели слияния (вызывается в tick)
     */
    private void processMergeMovement() {
        int targetId = getMergeTargetId();
        if (targetId < 0) return;
        
        Entity targetEntity = this.getEntityWorld().getEntityById(targetId);
        if (!(targetEntity instanceof ChernushkaEntity target) || !target.isAlive()) {
            setMergeTargetId(-1);
            return;
        }
        
        double distSq = this.squaredDistanceTo(target);
        
        // Если достаточно близко - сливаемся
        if (distSq < 4.0) {
            // Только одна чернушка выполняет слияние (с меньшим ID)
            if (this.getId() < target.getId()) {
                performMerge(target);
            }
            return;
        }
        
        // Идём к цели
        this.getNavigation().startMovingTo(target.getX(), target.getY(), target.getZ(), 1.2);
    }
    
    /**
     * Выполняет слияние двух чернушек
     */
    private void performMerge(ChernushkaEntity other) {
        // Сбрасываем цели
        setMergeTargetId(-1);
        other.setMergeTargetId(-1);
        
        // Вычисляем новый уровень
        int newLevel = Math.min(MAX_MERGE_LEVEL, this.getMergeLevel() + other.getMergeLevel() + 1);
        
        // Устанавливаем новый уровень
        setMergeLevel(newLevel);
        
        // Триггеры достижений
        PlayerEntity owner = getOwner();
        if (owner instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            if (newLevel >= 2) {
                ru.MjKey.chernushka.advancement.ModCriteria.BIG_CHERNUSHKA.trigger(serverPlayer);
            }
            if (newLevel >= MAX_MERGE_LEVEL) {
                ru.MjKey.chernushka.advancement.ModCriteria.GIANT_CHERNUSHKA.trigger(serverPlayer);
            }
        }
        
        // Спавним частицы
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            double x = (this.getX() + other.getX()) / 2;
            double y = (this.getY() + other.getY()) / 2 + 0.5;
            double z = (this.getZ() + other.getZ()) / 2;
            
            for (int i = 0; i < 20; i++) {
                serverWorld.spawnParticles(
                    ParticleTypes.SMOKE,
                    x + random.nextGaussian() * 0.5,
                    y + random.nextGaussian() * 0.5,
                    z + random.nextGaussian() * 0.5,
                    1, 0, 0, 0, 0.05
                );
            }
        }
        
        // Удаляем другую чернушку
        other.discard();
    }
    
    /**
     * Разделяет большую чернушку на несколько маленьких
     */
    private void splitIntoMultiple(PlayerEntity player) {
        int level = getMergeLevel();
        if (level <= 0) return;
        
        // Количество чернушек = уровень + 1 (т.к. уровень 1 = 2 слитых)
        int count = level + 1;
        
        UUID ownerUuid = getOwnerUuid();
        ServerWorld serverWorld = (ServerWorld) this.getEntityWorld();
        
        // Спавним частицы
        for (int i = 0; i < 30; i++) {
            serverWorld.spawnParticles(
                ParticleTypes.POOF,
                this.getX() + random.nextGaussian() * 0.5,
                this.getY() + 0.5 + random.nextGaussian() * 0.5,
                this.getZ() + random.nextGaussian() * 0.5,
                1, 0, 0, 0, 0.05
            );
        }
        
        // Спавним новых чернушек
        for (int i = 0; i < count; i++) {
            ChernushkaEntity newChernushka = ModEntities.CHERNUSHKA.create(serverWorld, SpawnReason.MOB_SUMMONED);
            if (newChernushka != null) {
                // Позиция с небольшим разбросом
                double offsetX = (random.nextDouble() - 0.5) * 2;
                double offsetZ = (random.nextDouble() - 0.5) * 2;
                
                newChernushka.refreshPositionAndAngles(
                    this.getX() + offsetX,
                    this.getY(),
                    this.getZ() + offsetZ,
                    random.nextFloat() * 360,
                    0
                );
                
                // Устанавливаем владельца
                if (ownerUuid != null) {
                    newChernushka.setOwnerUuid(ownerUuid);
                }
                
                // Уровень слияния = 0 (обычная)
                newChernushka.setMergeLevel(0);
                
                serverWorld.spawnEntity(newChernushka);
            }
        }
        
        // Удаляем эту большую чернушку
        this.discard();
    }
    
    public int getMergeTargetId() {
        return this.dataTracker.get(MERGE_TARGET_ID);
    }
    
    public void setMergeTargetId(int id) {
        this.dataTracker.set(MERGE_TARGET_ID, id);
    }
    
    // ===== Система размножения =====
    
    /**
     * Обрабатывает клик аметистом для размножения (система двух кликов)
     */
    private boolean handleBreedingClick(PlayerEntity player, ItemStack stack) {
        // Проверяем кулдаун
        if (this.breedingCooldown > 0) {
            return false;
        }
        
        UUID playerId = player.getUuid();
        UUID firstChernushkaUuid = pendingBreedingSelections.get(playerId);
        
        if (firstChernushkaUuid == null) {
            // Первый клик - запоминаем эту чернушку и активируем режим любви
            pendingBreedingSelections.put(playerId, this.getUuid());
            setLoveTicks(60); // 3 секунды сердечек
            return true;
        }
        
        // Второй клик
        if (firstChernushkaUuid.equals(this.getUuid())) {
            // Кликнули по той же чернушке - отменяем выбор
            pendingBreedingSelections.remove(playerId);
            setLoveTicks(0);
            return true;
        }
        
        // Ищем первую чернушку по UUID
        ChernushkaEntity firstChernushka = findChernushkaByUuid(firstChernushkaUuid);
        if (firstChernushka == null || !firstChernushka.isAlive()) {
            // Первая чернушка не найдена - начинаем заново с этой
            pendingBreedingSelections.put(playerId, this.getUuid());
            setLoveTicks(60);
            return true;
        }
        
        // Проверяем что обе принадлежат этому игроку
        UUID firstOwner = firstChernushka.getOwnerUuid();
        UUID secondOwner = this.getOwnerUuid();
        if (firstOwner == null || secondOwner == null || !firstOwner.equals(playerId) || !secondOwner.equals(playerId)) {
            pendingBreedingSelections.remove(playerId);
            return false;
        }
        
        // Проверяем что обе обычные (не слитые)
        if (firstChernushka.getMergeLevel() > 0 || this.getMergeLevel() > 0) {
            pendingBreedingSelections.remove(playerId);
            return false;
        }
        
        // Проверяем кулдаун у первой
        if (firstChernushka.breedingCooldown > 0) {
            pendingBreedingSelections.remove(playerId);
            return false;
        }
        
        // Очищаем выбор
        pendingBreedingSelections.remove(playerId);
        
        // Запускаем процесс размножения - чернушки идут друг к другу
        startBreedingWalking(firstChernushka, this, player, stack);
        return true;
    }
    
    /**
     * Запускает процесс размножения - чернушки идут друг к другу
     */
    private void startBreedingWalking(ChernushkaEntity first, ChernushkaEntity second, PlayerEntity player, ItemStack stack) {
        // Устанавливаем цели движения друг к другу
        first.setBreedingTargetId(second.getId());
        second.setBreedingTargetId(first.getId());
        
        // Активируем сердечки у обеих
        first.setLoveTicks(200);
        second.setLoveTicks(200);
        
        // Тратим предмет
        if (!player.isCreative()) {
            stack.decrement(1);
        }
    }
    
    /**
     * Обрабатывает движение к цели размножения (вызывается в tick)
     */
    private void processBreedingMovement() {
        int targetId = getBreedingTargetId();
        if (targetId < 0) return;
        
        Entity targetEntity = this.getEntityWorld().getEntityById(targetId);
        if (!(targetEntity instanceof ChernushkaEntity target) || !target.isAlive()) {
            setBreedingTargetId(-1);
            setLoveTicks(0);
            return;
        }
        
        double distSq = this.squaredDistanceTo(target);
        
        // Если достаточно близко - размножаемся
        if (distSq < 4.0) {
            // Только одна чернушка выполняет размножение (с меньшим ID)
            if (this.getId() < target.getId()) {
                performBreeding(target);
            }
            return;
        }
        
        // Идём к цели
        this.getNavigation().startMovingTo(target.getX(), target.getY(), target.getZ(), 1.0);
        
        // Продлеваем сердечки пока идём
        if (getLoveTicks() < 20) {
            setLoveTicks(100);
        }
    }
    
    /**
     * Выполняет размножение двух чернушек
     */
    private void performBreeding(ChernushkaEntity other) {
        // Сбрасываем цели
        setBreedingTargetId(-1);
        other.setBreedingTargetId(-1);
        setLoveTicks(0);
        other.setLoveTicks(0);
        
        // Устанавливаем кулдаун
        this.breedingCooldown = BREEDING_COOLDOWN_TICKS;
        other.breedingCooldown = BREEDING_COOLDOWN_TICKS;
        
        // Спавним малыша!
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            ChernushkaEntity baby = ModEntities.CHERNUSHKA.create(serverWorld, SpawnReason.BREEDING);
            if (baby != null) {
                double x = (this.getX() + other.getX()) / 2;
                double y = Math.max(this.getY(), other.getY());
                double z = (this.getZ() + other.getZ()) / 2;
                
                baby.refreshPositionAndAngles(x, y, z, random.nextFloat() * 360, 0);
                
                // Малыш принадлежит тому же владельцу
                UUID ownerUuid = this.getOwnerUuid();
                if (ownerUuid != null) {
                    baby.setOwnerUuid(ownerUuid);
                }
                
                serverWorld.spawnEntity(baby);
                
                // Много сердечек!
                for (int i = 0; i < 15; i++) {
                    serverWorld.spawnParticles(
                        ParticleTypes.HEART,
                        x + random.nextGaussian() * 0.5,
                        y + 0.5 + random.nextDouble() * 0.5,
                        z + random.nextGaussian() * 0.5,
                        1, 0, 0, 0, 0
                    );
                }
            }
        }
    }
    
    public int getBreedingTargetId() {
        return this.dataTracker.get(BREEDING_TARGET_ID);
    }
    
    public void setBreedingTargetId(int id) {
        this.dataTracker.set(BREEDING_TARGET_ID, id);
    }
    
    public int getLoveTicks() {
        return this.dataTracker.get(LOVE_TICKS);
    }
    
    public void setLoveTicks(int ticks) {
        this.dataTracker.set(LOVE_TICKS, ticks);
    }
    
    public boolean isInLove() {
        return getLoveTicks() > 0;
    }
    
    // ===== Верховая езда (живой бур!) =====
    
    /**
     * Проверяет может ли чернушка быть управляемой наездником
     */
    public boolean canBeControlledByRider() {
        return getMergeLevel() >= MAX_MERGE_LEVEL && this.getControllingPassenger() instanceof PlayerEntity;
    }
    
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity passenger = this.getFirstPassenger();
        if (passenger instanceof PlayerEntity player) {
            UUID ownerUuid = getOwnerUuid();
            if (ownerUuid != null && ownerUuid.equals(player.getUuid())) {
                return player;
            }
        }
        return null;
    }
    
    @Override
    protected Vec3d getControlledMovementInput(PlayerEntity controllingPlayer, Vec3d movementInput) {
        // Управление как на лошади
        float forward = controllingPlayer.forwardSpeed;
        float strafe = controllingPlayer.sidewaysSpeed * 0.5f;
        
        if (forward <= 0.0f) {
            forward *= 0.25f; // Медленнее назад
        }
        
        return new Vec3d(strafe, 0.0, forward);
    }
    
    @Override
    protected float getSaddledSpeed(PlayerEntity controllingPlayer) {
        // Скорость гигантской чернушки
        return (float) this.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) * 0.8f;
    }
    
    @Override
    protected void tickControlled(PlayerEntity controllingPlayer, Vec3d movementInput) {
        super.tickControlled(controllingPlayer, movementInput);
        
        // Поворачиваем чернушку в направлении взгляда игрока
        this.setRotation(controllingPlayer.getYaw(), controllingPlayer.getPitch() * 0.5f);
        this.bodyYaw = this.getYaw();
        this.headYaw = this.bodyYaw;
        
        // Прыжок когда игрок прыгает
        if (controllingPlayer.isJumping() && this.isOnGround()) {
            this.jump();
        }
    }
    
    @Override
    protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
        // Лежачий игрок на гигантской чернушке - ниже и немного вперёд
        // if (passenger instanceof PlayerEntity && getMergeLevel() >= MAX_MERGE_LEVEL) {
        //     return new Vec3d(0.0, dimensions.height() * 0.92, 0.0);
        // } // нет в этом обходимости!
        return new Vec3d(0.0, dimensions.height() * 1.05, 0.0);
    }
    
    /**
     * Бурение блоков при езде - живой бур!
     * Работает только если игрок держит палку для чернушек в любой руке
     * Ломает по одному блоку за раз, туннель 5x5, глубина 3 блока
     * Порядок: снизу вверх, потом следующий ряд вперёд
     */
    private void processDrilling() {
        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) return;
        
        Entity passenger = this.getFirstPassenger();
        if (!(passenger instanceof PlayerEntity player)) return;
        
        // Проверяем что игрок держит палку для чернушек в любой руке
        boolean hasStick = player.getMainHandStack().getItem() instanceof ru.MjKey.chernushka.item.ChernushkaStickItem
                        || player.getOffHandStack().getItem() instanceof ru.MjKey.chernushka.item.ChernushkaStickItem;
        
        if (!hasStick) {
            setHelping(false);
            return;
        }
        
        drillCooldown--;
        if (drillCooldown > 0) return;
        drillCooldown = DRILL_INTERVAL;
        
        // Направление движения (куда смотрит чернушка)
        float yaw = this.getYaw();
        double dirX = -MathHelper.sin(yaw * ((float) Math.PI / 180F));
        double dirZ = MathHelper.cos(yaw * ((float) Math.PI / 180F));
        
        // Базовая позиция - край хитбокса
        double baseReach = this.getWidth() / 2.0;
        int baseY = MathHelper.floor(this.getY());
        
        // Размер туннеля: 5 в ширину, 5 в высоту, 3 в глубину
        int tunnelWidth = 5;   // -2, -1, 0, +1
        int tunnelHeight = 5;  // 0, 1, 2, 3, 4
        int tunnelDepth = 3;   // 1, 2, 3 блока вперёд от края
        
        // Порядок: сначала ближний ряд снизу вверх, потом следующий ряд и т.д.
        for (int depth = 1; depth <= tunnelDepth; depth++) {
            double reach = baseReach + depth;
            double frontX = this.getX() + dirX * reach;
            double frontZ = this.getZ() + dirZ * reach;
            
            // Снизу вверх
            for (int dy = 0; dy < tunnelHeight; dy++) {
                // По ширине
                for (int dxIdx = 0; dxIdx < tunnelWidth; dxIdx++) {
                    int dx = dxIdx - 2; // -2, -1, 0, +1
                    
                    // Смещение по бокам перпендикулярно направлению
                    double sideOffsetX = -dirZ * dx;
                    double sideOffsetZ = dirX * dx;
                    
                    BlockPos pos = new BlockPos(
                        MathHelper.floor(frontX + sideOffsetX),
                        baseY + dy,
                        MathHelper.floor(frontZ + sideOffsetZ)
                    );
                    
                    if (tryBreakBlock(serverWorld, pos, player)) {
                        setHelping(true);
                        return; // Ломаем только один блок за раз
                    }
                }
            }
        }
        
        // Нечего ломать - выключаем анимацию
        setHelping(false);
    }
    
    /**
     * Пытается сломать блок
     * @return true если блок был сломан или идёт процесс ломания
     */
    private boolean tryBreakBlock(ServerWorld world, BlockPos pos, PlayerEntity player) {
        BlockState state = world.getBlockState(pos);
        
        // Пропускаем воздух
        if (state.isAir()) return false;
        
        float hardness = state.getHardness(world, pos);
        
        // Бедрок (hardness < 0) - ломаем за 3 минуты
        if (hardness < 0) {
            return processHardBlock(world, pos, player, BEDROCK_BREAK_TICKS);
        }
        
        // Очень твёрдые блоки (обсидиан = 50) - ломаем за 10 секунд
        if (hardness >= 50) {
            return processHardBlock(world, pos, player, HARD_BLOCK_BREAK_TICKS);
        }
        
        // Обычные блоки - ломаем сразу
        // Сбрасываем прогресс твёрдого блока если переключились
        if (hardBlockPos != null && !hardBlockPos.equals(pos)) {
            resetHardBlockProgress(world);
        }
        
        world.breakBlock(pos, true, player);
        
        // Частицы
        world.spawnParticles(
            ParticleTypes.SMOKE,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            3, 0.2, 0.2, 0.2, 0.01
        );
        
        return true;
    }
    
    /**
     * Обрабатывает ломание твёрдого блока с прогрессом
     */
    private boolean processHardBlock(ServerWorld world, BlockPos pos, PlayerEntity player, int totalTicks) {
        // Если это новый блок - начинаем заново
        if (hardBlockPos == null || !hardBlockPos.equals(pos)) {
            resetHardBlockProgress(world);
            hardBlockPos = pos;
            hardBlockProgress = 0;
        }
        
        hardBlockProgress++;
        
        // Показываем прогресс разрушения (0-9)
        int stage = (int) ((hardBlockProgress / (float) totalTicks) * 10);
        stage = Math.min(stage, 9);
        world.setBlockBreakingInfo(this.getId(), pos, stage);
        
        // Частицы каждые 10 тиков
        if (hardBlockProgress % 10 == 0) {
            world.spawnParticles(
                ParticleTypes.SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                2, 0.3, 0.3, 0.3, 0.01
            );
        }
        
        // Блок сломан!
        if (hardBlockProgress >= totalTicks) {
            world.breakBlock(pos, true, player);
            resetHardBlockProgress(world);
            
            // Больше частиц при ломании твёрдого блока
            world.spawnParticles(
                ParticleTypes.EXPLOSION,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                1, 0, 0, 0, 0
            );
            return true;
        }
        
        return true; // Блок ещё ломается
    }
    
    /**
     * Сбрасывает прогресс ломания твёрдого блока
     */
    private void resetHardBlockProgress(ServerWorld world) {
        if (hardBlockPos != null) {
            world.setBlockBreakingInfo(this.getId(), hardBlockPos, -1);
            hardBlockPos = null;
            hardBlockProgress = 0;
        }
    }
    
    @Override
    public void handleStatus(byte status) {
        // Status 7 = taming hearts - handled by client renderer
        if (status != 7) {
            super.handleStatus(status);
        }
    }
    
    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new ChernushkaMineBlockGoal(this));
        this.goalSelector.add(2, new ChernushkaHelpBreakGoal(this));
        this.goalSelector.add(3, new ChernushkaFollowOwnerGoal(this, 1.0D, 20.0F, 5.0F));
        this.goalSelector.add(4, new WanderAroundFarGoal(this, 0.8D));
        this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(6, new LookAroundGoal(this));
    }
    
    public static DefaultAttributeContainer.Builder createChernushkaAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0D)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3D)
                .add(EntityAttributes.FOLLOW_RANGE, 48.0D);
    }
    
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Один контроллер для всех анимаций с приоритетами
        controllers.add(new AnimationController<>("movement", 5, state -> {
            // Высший приоритет: show анимация (подъём из земли)
            if (this.isShowing()) {
                return state.setAndContinue(SHOW_ANIM);
            }
            
            // Приоритет: специальные анимации > движение > idle
            if (this.isLanding()) {
                return state.setAndContinue(END_JUMP_ANIM);
            }
            if (this.isFalling()) {
                return state.setAndContinue(JUMP_ANIM);
            }
            if (this.isJumpingUp()) {
                return state.setAndContinue(START_JUMP_ANIM);
            }
            if (this.isConfused()) {
                return state.setAndContinue(OGLYADKA_ANIM);
            }
            if (this.isMining() || this.isHelping()) {
                return state.setAndContinue(LOMKA_ANIM);
            }
            
            // Движение - используем state.isMoving() из GeckoLib
            if (state.isMoving()) {
                return state.setAndContinue(this.isRunning() ? RUN_ANIM : WALK_ANIM);
            }
            
            // Анимация остановки после ходьбы
            if (this.isStopping()) {
                return state.setAndContinue(STOP_WALK_ANIM);
            }
            
            return state.setAndContinue(IDLE_ANIM);
        }));
    }
    
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
    
    private void clearBreakingState() {
        this.breakingBlockPos = null;
        this.breakingPlayer = null;
        this.breakingTimeout = 0;
    }
    
    @Nullable
    public UUID getOwnerUuid() {
        // Сначала проверяем DataTracker (для синхронизации клиент-сервер)
        String uuidStr = this.dataTracker.get(OWNER_UUID_STRING);
        if ((uuidStr == null || uuidStr.isEmpty()) && !this.getEntityWorld().isClient()) {
            // Пробуем загрузить из attachment (для персистентности) - только на сервере
            String attached = this.getAttachedOrElse(ModAttachments.OWNER_UUID, "");
            if (!attached.isEmpty()) {
                // Синхронизируем в DataTracker
                this.dataTracker.set(OWNER_UUID_STRING, attached);
                uuidStr = attached;
            }
        }
        if (uuidStr == null || uuidStr.isEmpty()) return null;
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    public void setOwnerUuid(@Nullable UUID uuid) {
        String uuidStr = uuid != null ? uuid.toString() : "";
        this.dataTracker.set(OWNER_UUID_STRING, uuidStr);
        // Сохраняем в attachment для персистентности
        if (uuid != null) {
            this.setAttached(ModAttachments.OWNER_UUID, uuidStr);
        } else {
            this.removeAttached(ModAttachments.OWNER_UUID);
        }
    }
    
    public void setOwner(PlayerEntity player) {
        this.ownerCache = player;
        if (player != null) {
            setOwnerUuid(player.getUuid());
        }
    }
    
    @Nullable
    public PlayerEntity getOwner() {
        UUID uuid = getOwnerUuid();
        if (uuid == null) return null;
        
        if (ownerCache != null && ownerCache.getUuid().equals(uuid) && ownerCache.isAlive()) {
            return ownerCache;
        }
        
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            // Используем getPlayerByUuid для поиска игрока, а не getEntity
            PlayerEntity player = serverWorld.getPlayerByUuid(uuid);
            if (player != null) {
                ownerCache = player;
                return player;
            }
        }
        return null;
    }
    
    public boolean isTamed() {
        return getOwnerUuid() != null;
    }
    
    @Override
    public void checkDespawn() {
        // Прирученные чернушки никогда не деспавнятся
        if (isTamed()) {
            despawnTimer = -1;
            return;
        }
        
        // Дикие чернушки деспавнятся только если далеко от игрока (128+ блоков)
        // и прошло от 5 до 20 минут (рандомно)
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            PlayerEntity nearest = serverWorld.getClosestPlayer(this, -1.0);
            if (nearest != null) {
                double distSq = this.squaredDistanceTo(nearest);
                
                // Если игрок ближе 128 блоков - сбрасываем таймер
                if (distSq < 128 * 128) {
                    despawnTimer = -1;
                    return;
                }
                
                // Игрок далеко - запускаем/продолжаем таймер
                if (despawnTimer < 0) {
                    // Инициализируем рандомный таймер от 5 до 20 минут
                    despawnTimer = MIN_DESPAWN_TICKS + random.nextInt(MAX_DESPAWN_TICKS - MIN_DESPAWN_TICKS);
                }
                
                despawnTimer--;
                
                if (despawnTimer <= 0) {
                    this.discard();
                }
            }
        }
    }
    
    public boolean isHelping() {
        return this.dataTracker.get(HELPING);
    }
    
    public void setHelping(boolean helping) {
        this.dataTracker.set(HELPING, helping);
    }
    
    public boolean isMining() {
        return this.dataTracker.get(MINING);
    }
    
    public void setMining(boolean mining) {
        this.dataTracker.set(MINING, mining);
    }
    
    public boolean isConfused() {
        return this.dataTracker.get(CONFUSED);
    }
    
    public void setConfused(boolean confused) {
        this.dataTracker.set(CONFUSED, confused);
        if (confused) {
            this.confusedTicks = CONFUSED_DURATION;
        }
    }
    
    public boolean isRunning() {
        return this.dataTracker.get(RUNNING);
    }
    
    public void setRunning(boolean running) {
        this.dataTracker.set(RUNNING, running);
    }
    
    public boolean isShowing() {
        return this.dataTracker.get(SHOWING);
    }
    
    public void setShowing(boolean showing) {
        this.dataTracker.set(SHOWING, showing);
        if (showing) {
            this.showingTicks = SHOW_DURATION;
        }
    }
    
    /**
     * Вызывается когда чернушка не может достать до блока
     */
    public void showConfusedAnimation() {
        setConfused(true);
        setHelping(false);
    }
    
    /**
     * Обновляет состояние прыжка/падения
     */
    private void updateJumpState() {
        boolean onGround = this.isOnGround();
        boolean goingUp = this.getVelocity().y > 0.1;
        boolean currentlyFalling = !onGround && this.getVelocity().y < -0.1;
        
        // Обработка таймера start_jump
        if (this.isJumpingUp) {
            this.jumpUpTicks--;
            if (this.jumpUpTicks <= 0 || currentlyFalling) {
                this.isJumpingUp = false;
            }
        }
        
        // Обработка таймера end_jump (приземление)
        if (this.isLanding) {
            this.landingTicks--;
            if (this.landingTicks <= 0) {
                this.isLanding = false;
            }
        }
        
        // Если только что оторвались от земли вверх - start_jump
        if (this.wasOnGround && !onGround && goingUp) {
            this.isJumpingUp = true;
            this.jumpUpTicks = JUMP_UP_DURATION;
        }
        
        // Если только что приземлились после падения - end_jump
        if (this.wasFalling && onGround) {
            this.isLanding = true;
            this.landingTicks = LANDING_DURATION;
        }
        
        this.wasOnGround = onGround;
        this.wasFalling = currentlyFalling;
    }
    
    /**
     * Обновляет состояние остановки после ходьбы
     */
    private void updateStoppingState() {
        boolean currentlyMoving = this.getVelocity().horizontalLengthSquared() > 0.001;
        
        // Обработка таймера stop_walk
        if (this.isStopping) {
            this.stoppingTicks--;
            if (this.stoppingTicks <= 0) {
                this.isStopping = false;
            }
        }
        
        // Если только что остановились после движения - stop_walk
        if (this.wasMoving && !currentlyMoving && !this.isFalling() && !this.isJumpingUp) {
            this.isStopping = true;
            this.stoppingTicks = STOPPING_DURATION;
        }
        
        this.wasMoving = currentlyMoving;
    }
    
    /**
     * Проверяет останавливается ли чернушка после ходьбы
     */
    public boolean isStopping() {
        return this.isStopping;
    }
    
    /**
     * Проверяет начинает ли чернушка прыжок (отрыв от земли вверх)
     */
    public boolean isJumpingUp() {
        return this.isJumpingUp;
    }
    
    /**
     * Проверяет падает ли чернушка
     */
    public boolean isFalling() {
        return !this.isOnGround() && this.getVelocity().y < -0.1 && !this.isJumpingUp;
    }
    
    /**
     * Проверяет приземляется ли чернушка (после падения)
     */
    public boolean isLanding() {
        return this.isLanding;
    }
    
    @Nullable
    public BlockPos getBreakingBlockPos() {
        return this.breakingBlockPos;
    }
    
    public void setBreakingBlockPos(@Nullable BlockPos pos) {
        this.breakingBlockPos = pos;
        this.breakingTimeout = 0;
    }
    
    @Nullable
    public PlayerEntity getBreakingPlayer() {
        return this.breakingPlayer;
    }
    
    public void setBreakingPlayer(@Nullable PlayerEntity player) {
        this.breakingPlayer = player;
    }
    
    // ===== Система слияния =====
    
    public int getMergeLevel() {
        return this.dataTracker.get(MERGE_LEVEL);
    }
    
    public void setMergeLevel(int level) {
        level = Math.max(0, Math.min(MAX_MERGE_LEVEL, level));
        this.dataTracker.set(MERGE_LEVEL, level);
        // Сохраняем в attachment
        this.setAttached(ModAttachments.MERGE_LEVEL, level);
    }
    
    /**
     * Возвращает масштаб модели на основе уровня слияния
     */
    public float getModelScale() {
        int level = getMergeLevel();
        if (level >= 0 && level < SCALE_BY_LEVEL.length) {
            return SCALE_BY_LEVEL[level];
        }
        return 1.0f;
    }
    
    /**
     * Масштабирует хитбокс в зависимости от уровня слияния
     */
    @Override
    public EntityDimensions getBaseDimensions(EntityPose pose) {
        EntityDimensions base = super.getBaseDimensions(pose);
        float scale = getModelScale();
        return base.scaled(scale);
    }
    
    /**
     * Пересчитывает хитбокс при изменении уровня слияния
     */
    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        
        if (OWNER_UUID_STRING.equals(data)) {
            syncOwnerFromAttachment();
        }
        
        // Пересчитываем размеры при изменении уровня слияния
        if (MERGE_LEVEL.equals(data)) {
            this.calculateDimensions();
        }
    }
    
    /**
     * Проверяет достижение "Армия" - 10 прирученных чернушек
     */
    private void checkArmyAchievement(net.minecraft.server.network.ServerPlayerEntity player) {
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            long count = serverWorld.getEntitiesByType(
                ModEntities.CHERNUSHKA,
                player.getBoundingBox().expand(100),
                c -> c.isTamed() && player.getUuid().equals(c.getOwnerUuid())
            ).size();
            
            if (count >= 10) {
                ru.MjKey.chernushka.advancement.ModCriteria.CHERNUSHKA_ARMY.trigger(player);
            }
        }
    }
    
}
