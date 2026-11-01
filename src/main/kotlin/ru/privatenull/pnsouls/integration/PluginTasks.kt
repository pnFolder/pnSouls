package ru.privatenull.pnsouls.integration

import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSpec

/**
 * Единственное место, где pnSouls запускает задачи: планировщик pnLibrary (`ModuleContext.tasks`).
 * Задачи выполняются в глобальном потоке сервера и отменяются вместе с модулем при отключении плагина.
 */
object PluginTasks {
    @Volatile
    private var scope: TaskScope? = null

    internal fun bind(tasks: TaskScope?) {
        scope = tasks
    }

    private fun schedule(delayTicks: Long, periodTicks: Long, action: Runnable): TaskHandle {
        val tasks = checkNotNull(scope) { "Планировщик pnLibrary недоступен" }
        val spec = TaskSpec.builder()
            .name("pnsouls")
            .execution(TaskExecution.global())
            .delayTicks(delayTicks)
            .action { action.run() }
        if (periodTicks > 0L) spec.intervalTicks(periodTicks)
        return tasks.schedule(spec.build())
    }

    /** Запускает [action] в потоке сервера на следующем тике. */
    @JvmStatic
    fun run(action: Runnable): TaskHandle = schedule(1L, 0L, action)

    /** Запускает [action] один раз через [delayTicks] тиков. */
    @JvmStatic
    fun later(delayTicks: Long, action: Runnable): TaskHandle = schedule(delayTicks, 0L, action)

    /** Повторяет [action] каждые [periodTicks] тиков после задержки [delayTicks]. */
    @JvmStatic
    fun timer(delayTicks: Long, periodTicks: Long, action: Runnable): TaskHandle =
        schedule(delayTicks, periodTicks, action)
}
