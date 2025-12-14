package ru.MjKey.chernushka.entity;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Умный менеджер задач добычи для Чернушек.
 * Поддерживает:
 * - Приоритизацию задач по расстоянию
 * - Совместную работу нескольких чернушек над одним блоком
 * - Отслеживание недостижимых блоков
 * - Автоматическую очистку завершённых задач
 */
public class MiningTaskManager {
    
    public static final int MAX_QUEUE_SIZE = 128;
    public static final int MAX_HELPERS_PER_BLOCK = 3;
    public static final int UNREACHABLE_COOLDOWN = 200; // 10 секунд
    
    // Основная очередь задач с приоритетом
    private static final LinkedHashMap<BlockPos, MiningTask> tasks = new LinkedHashMap<>();
    
    // Назначения: chernushkaId -> задача
    private static final Map<Integer, BlockPos> assignments = new HashMap<>();
    
    // Помощники для каждого блока: blockPos -> список ID помощников
    private static final Map<BlockPos, Set<Integer>> helpers = new HashMap<>();
    
    // Недостижимые блоки: blockPos -> время когда можно снова попробовать
    private static final Map<BlockPos, Long> unreachableBlocks = new HashMap<>();
    
    // Прогресс добычи для совместной работы
    private static final Map<BlockPos, Float> miningProgress = new HashMap<>();
    
    /**
     * Данные о задаче добычи
     */
    public static class MiningTask {
        public final BlockPos pos;
        public final long createdAt;
        public int assignedCount = 0;
        
        public MiningTask(BlockPos pos) {
            this.pos = pos;
            this.createdAt = System.currentTimeMillis();
        }
    }

    
    /**
     * Результат добавления задачи
     */
    public enum AddTaskResult {
        SUCCESS,
        ALREADY_IN_QUEUE,
        QUEUE_FULL
    }
    
    /**
     * Добавить задачу на добычу блока
     */
    public static AddTaskResult addTask(BlockPos pos) {
        if (tasks.containsKey(pos)) {
            return AddTaskResult.ALREADY_IN_QUEUE;
        }
        if (tasks.size() >= MAX_QUEUE_SIZE) {
            return AddTaskResult.QUEUE_FULL;
        }
        
        // Очищаем из недостижимых если время прошло
        unreachableBlocks.remove(pos);
        
        tasks.put(pos, new MiningTask(pos));
        return AddTaskResult.SUCCESS;
    }
    
    /**
     * Получить размер очереди
     */
    public static int getQueueSize() {
        return tasks.size();
    }
    
    /**
     * Очистить все задачи
     */
    public static void clearAllTasks() {
        tasks.clear();
        assignments.clear();
        helpers.clear();
        unreachableBlocks.clear();
        miningProgress.clear();
    }
    
    /**
     * Назначить задачу чернушке - выбирает ближайший доступный блок
     */
    @Nullable
    public static BlockPos assignTaskToChernushka(ChernushkaEntity chernushka, World world) {
        int id = chernushka.getId();
        
        // Уже есть назначение?
        BlockPos existing = assignments.get(id);
        if (existing != null) {
            if (!world.getBlockState(existing).isAir()) {
                return existing;
            }
            // Блок сломан - завершаем
            completeTask(id, existing);
        }
        
        // Уже помогает кому-то?
        if (isHelper(id)) {
            return null;
        }
        
        if (tasks.isEmpty()) {
            return null;
        }
        
        // Ищем ближайшую доступную задачу
        BlockPos bestTask = findBestTask(chernushka, world);
        
        if (bestTask != null) {
            assignments.put(id, bestTask);
            MiningTask task = tasks.get(bestTask);
            if (task != null) {
                task.assignedCount++;
            }
        }
        
        return bestTask;
    }
    
    /**
     * Найти лучшую задачу для чернушки (ближайшую и достижимую)
     */
    @Nullable
    private static BlockPos findBestTask(ChernushkaEntity chernushka, World world) {
        double posX = chernushka.getX();
        double posY = chernushka.getY();
        double posZ = chernushka.getZ();
        long currentTime = world.getTime();
        
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        
        for (Map.Entry<BlockPos, MiningTask> entry : tasks.entrySet()) {
            BlockPos taskPos = entry.getKey();
            
            // Пропускаем недостижимые блоки
            Long cooldownEnd = unreachableBlocks.get(taskPos);
            if (cooldownEnd != null && currentTime < cooldownEnd) {
                continue;
            }
            
            // Проверяем что блок ещё существует
            if (world.getBlockState(taskPos).isAir()) {
                continue;
            }
            
            // Считаем расстояние
            double dx = taskPos.getX() - posX;
            double dy = taskPos.getY() - posY;
            double dz = taskPos.getZ() - posZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            
            // Предпочитаем блоки с меньшим количеством назначенных
            MiningTask task = entry.getValue();
            if (task.assignedCount > 0) {
                distSq *= 1.5; // Штраф за занятые блоки
            }
            
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = taskPos;
            }
        }
        
        return best;
    }

    
    /**
     * Пометить блок как недостижимый
     */
    public static void markUnreachable(BlockPos pos, World world) {
        unreachableBlocks.put(pos, world.getTime() + UNREACHABLE_COOLDOWN);
        
        // Освобождаем всех назначенных на этот блок
        Iterator<Map.Entry<Integer, BlockPos>> it = assignments.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, BlockPos> entry = it.next();
            if (entry.getValue().equals(pos)) {
                it.remove();
            }
        }
        
        // Освобождаем помощников
        helpers.remove(pos);
        
        // Удаляем задачу из очереди
        tasks.remove(pos);
        miningProgress.remove(pos);
    }
    
    /**
     * Проверить, недостижим ли блок
     */
    public static boolean isUnreachable(BlockPos pos, World world) {
        Long cooldownEnd = unreachableBlocks.get(pos);
        if (cooldownEnd == null) {
            return false;
        }
        if (world.getTime() >= cooldownEnd) {
            unreachableBlocks.remove(pos);
            return false;
        }
        return true;
    }
    
    /**
     * Завершить задачу (блок сломан)
     */
    public static void completeTask(int chernushkaId, BlockPos pos) {
        assignments.remove(chernushkaId);
        tasks.remove(pos);
        helpers.remove(pos);
        miningProgress.remove(pos);
        unreachableBlocks.remove(pos);
        
        // Удаляем всех кто был назначен на этот блок
        assignments.values().removeIf(p -> p.equals(pos));
    }
    
    /**
     * Освободить чернушку от всех задач
     */
    public static void releaseChernushka(int chernushkaId) {
        BlockPos assignedPos = assignments.remove(chernushkaId);
        if (assignedPos != null) {
            MiningTask task = tasks.get(assignedPos);
            if (task != null) {
                task.assignedCount = Math.max(0, task.assignedCount - 1);
            }
        }
        
        // Удаляем из помощников
        for (Set<Integer> helperSet : helpers.values()) {
            helperSet.remove(chernushkaId);
        }
    }
    
    /**
     * Проверить, является ли чернушка помощником
     */
    public static boolean isHelper(int chernushkaId) {
        for (Set<Integer> helperSet : helpers.values()) {
            if (helperSet.contains(chernushkaId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Запросить помощника для блока
     */
    @Nullable
    public static ChernushkaEntity requestHelper(ChernushkaEntity requester, BlockPos targetBlock, World world) {
        // Проверяем лимит помощников
        Set<Integer> currentHelpers = helpers.get(targetBlock);
        if (currentHelpers != null && currentHelpers.size() >= MAX_HELPERS_PER_BLOCK) {
            return null;
        }
        
        // Ищем свободную чернушку поблизости
        Box searchBox = new Box(targetBlock).expand(8.0D);
        List<ChernushkaEntity> candidates = world.getEntitiesByType(
            ModEntities.CHERNUSHKA,
            searchBox,
            c -> c.getId() != requester.getId() &&
                 !isHelper(c.getId()) &&
                 assignments.get(c.getId()) == null &&
                 c.isTamed() &&
                 !c.hasVehicle()
        );
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        // Выбираем ближайшую
        ChernushkaEntity helper = null;
        double minDistSq = Double.MAX_VALUE;
        
        for (ChernushkaEntity c : candidates) {
            double distSq = c.squaredDistanceTo(
                targetBlock.getX() + 0.5,
                targetBlock.getY() + 0.5,
                targetBlock.getZ() + 0.5
            );
            if (distSq < minDistSq) {
                minDistSq = distSq;
                helper = c;
            }
        }
        
        if (helper != null) {
            helpers.computeIfAbsent(targetBlock, k -> new HashSet<>()).add(helper.getId());
        }
        
        return helper;
    }

    
    /**
     * Получить количество помощников для блока
     */
    public static int getHelperCount(BlockPos targetBlock) {
        Set<Integer> helperSet = helpers.get(targetBlock);
        return helperSet != null ? helperSet.size() : 0;
    }
    
    /**
     * Проверить, помогает ли чернушка конкретному блоку
     */
    public static boolean isHelperFor(int chernushkaId, BlockPos targetBlock) {
        Set<Integer> helperSet = helpers.get(targetBlock);
        return helperSet != null && helperSet.contains(chernushkaId);
    }
    
    /**
     * Получить текущую задачу чернушки
     */
    @Nullable
    public static BlockPos getCurrentTask(int chernushkaId) {
        return assignments.get(chernushkaId);
    }
    
    /**
     * Получить блок которому помогает чернушка
     */
    @Nullable
    public static BlockPos getHelpingBlock(int chernushkaId) {
        for (Map.Entry<BlockPos, Set<Integer>> entry : helpers.entrySet()) {
            if (entry.getValue().contains(chernushkaId)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Добавить прогресс добычи (для совместной работы)
     * @return true если блок должен быть сломан
     */
    public static boolean addMiningProgress(BlockPos pos, float progress) {
        float current = miningProgress.getOrDefault(pos, 0f);
        current += progress;
        
        if (current >= 1.0f) {
            miningProgress.remove(pos);
            return true;
        }
        
        miningProgress.put(pos, current);
        return false;
    }
    
    /**
     * Получить текущий прогресс добычи
     */
    public static float getMiningProgress(BlockPos pos) {
        return miningProgress.getOrDefault(pos, 0f);
    }
    
    /**
     * Получить множитель скорости добычи на основе количества работников
     */
    public static float getSpeedMultiplier(BlockPos pos) {
        int workerCount = 1; // Основной работник
        Set<Integer> helperSet = helpers.get(pos);
        if (helperSet != null) {
            workerCount += helperSet.size();
        }
        
        // Каждый дополнительный работник добавляет 50% скорости (diminishing returns)
        // 1 работник = 1.0x, 2 = 1.5x, 3 = 1.85x, 4 = 2.1x
        float multiplier = 1.0f;
        for (int i = 1; i < workerCount; i++) {
            multiplier += 0.5f / i;
        }
        return multiplier;
    }
    
    /**
     * Очистка устаревших данных (вызывать периодически)
     */
    public static void cleanup(World world) {
        long currentTime = world.getTime();
        
        // Очищаем истёкшие недостижимые блоки
        unreachableBlocks.entrySet().removeIf(entry -> currentTime >= entry.getValue());
        
        // Очищаем задачи для уже сломанных блоков
        tasks.entrySet().removeIf(entry -> world.getBlockState(entry.getKey()).isAir());
        
        // Очищаем прогресс для несуществующих задач
        miningProgress.keySet().removeIf(pos -> !tasks.containsKey(pos));
    }
}
