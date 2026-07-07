package eu.decentsoftware.holograms.api.utils.scheduler;

import eu.decentsoftware.holograms.api.DecentHolograms;
import eu.decentsoftware.holograms.api.DecentHologramsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class S {

    private static final DecentHolograms DECENT_HOLOGRAMS = DecentHologramsAPI.get();
    private static final boolean FOLIA = isClassPresent("io.papermc.paper.threadedregions.RegionizedServer");
    private static final AtomicInteger FOLIA_TASK_IDS = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, BukkitTask> FOLIA_TASKS = new ConcurrentHashMap<>();
    private static final Class<?>[] NO_PARAMETERS = new Class<?>[0];
    private static final long TICK_MILLIS = 50L;

    private S() {
    }

    public static void stopTask(int id) {
        if (FOLIA) {
            BukkitTask task = FOLIA_TASKS.remove(id);
            if (task != null) {
                task.cancel();
            }
            return;
        }
        Bukkit.getScheduler().cancelTask(id);
    }

    public static void sync(Runnable runnable) {
        if (FOLIA) {
            runFoliaGlobal(runnable);
            return;
        }
        Bukkit.getScheduler().runTask(DECENT_HOLOGRAMS.getPlugin(), runnable);
    }

    public static BukkitTask sync(Runnable runnable, long delay) {
        if (FOLIA) {
            return wrapFoliaTask(runFoliaGlobal(runnable, delay), true);
        }
        return Bukkit.getScheduler().runTaskLater(DECENT_HOLOGRAMS.getPlugin(), runnable, delay);
    }

    public static BukkitTask syncTask(Runnable runnable, long interval) {
        if (FOLIA) {
            return wrapFoliaTask(runFoliaGlobalTask(runnable, interval), true);
        }
        return Bukkit.getScheduler().runTaskTimer(DECENT_HOLOGRAMS.getPlugin(), runnable, 0, interval);
    }

    public static void entity(Entity entity, Runnable runnable) {
        if (!FOLIA || entity == null) {
            sync(runnable);
            return;
        }
        if (isOwnedByCurrentRegion(entity)) {
            runnable.run();
            return;
        }
        Object scheduler = invoke(entity, "getScheduler", NO_PARAMETERS);
        invoke(scheduler, "run", new Class<?>[]{Plugin.class, Consumer.class, Runnable.class},
                DECENT_HOLOGRAMS.getPlugin(), foliaConsumer(runnable), null);
    }

    public static void async(Runnable runnable) {
        if (FOLIA) {
            runFoliaAsync(runnable);
            return;
        }
        try {
            Bukkit.getScheduler().runTaskAsynchronously(DECENT_HOLOGRAMS.getPlugin(), runnable);
        } catch (IllegalPluginAccessException e) {
            CompletableFuture.runAsync(runnable);
        }
    }

    public static void async(Runnable runnable, long delay) {
        if (FOLIA) {
            runFoliaAsync(runnable, delay);
            return;
        }
        try {
            Bukkit.getScheduler().runTaskLaterAsynchronously(DECENT_HOLOGRAMS.getPlugin(), runnable, delay);
        } catch (IllegalPluginAccessException e) {
            CompletableFuture.runAsync(runnable);
        }
    }

    public static BukkitTask asyncTask(Runnable runnable, long interval) {
        if (FOLIA) {
            return wrapFoliaTask(runFoliaAsyncTask(runnable, 0, interval), false);
        }
        return Bukkit.getScheduler().runTaskTimerAsynchronously(DECENT_HOLOGRAMS.getPlugin(), runnable, 0, interval);
    }

    public static BukkitTask asyncTask(Runnable runnable, long interval, long delay) {
        if (FOLIA) {
            return wrapFoliaTask(runFoliaAsyncTask(runnable, delay, interval), false);
        }
        return Bukkit.getScheduler().runTaskTimerAsynchronously(DECENT_HOLOGRAMS.getPlugin(), runnable, delay, interval);
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static boolean isOwnedByCurrentRegion(Entity entity) {
        if (!FOLIA) {
            return Bukkit.isPrimaryThread();
        }
        try {
            return (Boolean) Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class).invoke(null, entity);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            return false;
        }
    }

    private static Object runFoliaGlobal(Runnable runnable) {
        Object scheduler = getServerScheduler("getGlobalRegionScheduler");
        return invoke(scheduler, "run", new Class<?>[]{Plugin.class, Consumer.class},
                DECENT_HOLOGRAMS.getPlugin(), foliaConsumer(runnable));
    }

    private static Object runFoliaGlobal(Runnable runnable, long delay) {
        if (delay <= 0L) {
            return runFoliaGlobal(runnable);
        }
        Object scheduler = getServerScheduler("getGlobalRegionScheduler");
        return invoke(scheduler, "runDelayed", new Class<?>[]{Plugin.class, Consumer.class, long.class},
                DECENT_HOLOGRAMS.getPlugin(), foliaConsumer(runnable), delay);
    }

    private static Object runFoliaGlobalTask(Runnable runnable, long interval) {
        Object scheduler = getServerScheduler("getGlobalRegionScheduler");
        return invoke(scheduler, "runAtFixedRate", new Class<?>[]{Plugin.class, Consumer.class, long.class, long.class},
                DECENT_HOLOGRAMS.getPlugin(), foliaConsumer(runnable), 1L, Math.max(1L, interval));
    }

    private static Object runFoliaAsync(Runnable runnable) {
        Object scheduler = getServerScheduler("getAsyncScheduler");
        return invoke(scheduler, "runNow", new Class<?>[]{Plugin.class, Consumer.class},
                DECENT_HOLOGRAMS.getPlugin(), foliaConsumer(runnable));
    }

    private static Object runFoliaAsync(Runnable runnable, long delay) {
        if (delay <= 0L) {
            return runFoliaAsync(runnable);
        }
        Object scheduler = getServerScheduler("getAsyncScheduler");
        return invoke(scheduler, "runDelayed", new Class<?>[]{Plugin.class, Consumer.class, long.class, TimeUnit.class},
                DECENT_HOLOGRAMS.getPlugin(), foliaConsumer(runnable), ticksToMillis(delay), TimeUnit.MILLISECONDS);
    }

    private static Object runFoliaAsyncTask(Runnable runnable, long delay, long interval) {
        Object scheduler = getServerScheduler("getAsyncScheduler");
        return invoke(scheduler, "runAtFixedRate", new Class<?>[]{Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class},
                DECENT_HOLOGRAMS.getPlugin(), foliaConsumer(runnable), ticksToMillis(delay), ticksToMillis(Math.max(1L, interval)), TimeUnit.MILLISECONDS);
    }

    private static Object getServerScheduler(String methodName) {
        return invoke(Bukkit.getServer(), methodName, NO_PARAMETERS);
    }

    private static Consumer<Object> foliaConsumer(final Runnable runnable) {
        return ignored -> runnable.run();
    }

    private static long ticksToMillis(long ticks) {
        return Math.max(0L, ticks) * TICK_MILLIS;
    }

    private static BukkitTask wrapFoliaTask(Object scheduledTask, boolean sync) {
        int taskId = FOLIA_TASK_IDS.getAndIncrement();
        BukkitTask task = new FoliaBukkitTask(taskId, DECENT_HOLOGRAMS.getPlugin(), scheduledTask, sync);
        FOLIA_TASKS.put(taskId, task);
        return task;
    }

    private static void cancelFoliaTask(Object scheduledTask) {
        if (scheduledTask != null) {
            invoke(scheduledTask, "cancel", NO_PARAMETERS);
        }
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... parameters) {
        try {
            return target.getClass().getMethod(methodName, parameterTypes).invoke(target, parameters);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to invoke Folia scheduler method: " + methodName, e);
        }
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static final class FoliaBukkitTask implements BukkitTask {

        private final int taskId;
        private final Plugin owner;
        private final Object scheduledTask;
        private final boolean sync;
        private volatile boolean cancelled;

        private FoliaBukkitTask(int taskId, Plugin owner, Object scheduledTask, boolean sync) {
            this.taskId = taskId;
            this.owner = owner;
            this.scheduledTask = scheduledTask;
            this.sync = sync;
            this.cancelled = false;
        }

        @Override
        public int getTaskId() {
            return taskId;
        }

        @Override
        public Plugin getOwner() {
            return owner;
        }

        @Override
        public boolean isSync() {
            return sync;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
            FOLIA_TASKS.remove(taskId);
            cancelFoliaTask(scheduledTask);
        }
    }

}
