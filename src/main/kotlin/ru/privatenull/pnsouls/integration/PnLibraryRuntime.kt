package ru.privatenull.pnsouls.integration

import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import java.util.logging.Level

/** Подключение pnSouls к общему рантайму pnLibrary: политика, метрики, диагностика и обновления. */
class PnLibraryRuntime private constructor(
    private val owner: JavaPlugin,
    private val runtime: PnLibrary,
) : AutoCloseable {
    private var registration: PluginRegistration? = null
    private var module: ModuleContext? = null

    fun register(souls: () -> Int) {
        check(registration == null) { "pnSouls уже зарегистрирован в pnLibrary" }
        val registered = runtime.plugins.register(owner)
        registration = registered
        try {
            module = registered.registerModule(MODULE_ID) { builder ->
                builder
                    // Политику, которую нельзя проверить (нет сети, JRE без компилятора), плагин игнорирует:
                    // его останавливает только явный запрет.
                    .remotePolicy(
                        RemotePolicySettings.builder()
                            .source(POLICY_SOURCE)
                            .onError(PolicyErrorAction.KEEP_RUNNING)
                            .support(PnLibraryIdentity.SUPPORT_URL)
                            .build(),
                    )
                    .metrics { metrics -> metrics.bStats(BSTATS_PROJECT_ID).fastStats(FASTSTATS_TOKEN) }
                    .diagnostics(
                        owner.dataFolder.toPath(),
                        DiagnosticContainer.builder("pnsouls")
                            .snapshot { mapOf("souls" to souls().toString(), "version" to owner.description.version) }
                            .configuration(
                                DiagnosticConfiguration.builder("config.yml")
                                    .secretKeyRegex("(?i).*(password|passwd|secret|token|uri|url|user(name)?)$")
                                    .build(),
                            )
                            .build(),
                    )
                    .updates(UPDATE_OWNER, UPDATE_REPOSITORY) { updater ->
                        updater
                            .channel(UpdateChannel.STABLE)
                            .automaticDownload(false)
                            .artifact("(?i)^pnSouls-[0-9].*\\.jar$", 17)
                    }
            }
        } catch (failure: RuntimeException) {
            closeRegistration()
            throw failure
        }
    }

    fun enabled(souls: Int) {
        module?.lifecycle?.enabled()
            ?.ok("Души", souls.toString())
            ?.show()
    }

    override fun close() {
        val closing = module
        module = null
        if (closing != null && !closing.isClosed) {
            try {
                closing.lifecycle.disabled().show()
            } catch (failure: RuntimeException) {
                owner.logger.log(Level.FINE, "Не удалось вывести сообщение об остановке pnSouls", failure)
            }
        }
        closeRegistration()
    }

    private fun closeRegistration() {
        val closing = registration
        registration = null
        if (closing != null && !closing.isClosed) closing.close()
    }

    companion object {
        const val MINIMUM_VERSION = "0.0.1-dev.6"
        private const val MODULE_ID = "pnsouls"
        private const val BSTATS_PROJECT_ID = 33269
        private const val FASTSTATS_TOKEN = "2a3f012394da142017577b5873c882ba"
        private const val POLICY_SOURCE =
            "https://raw.githubusercontent.com/pnFolder/pnlibrary-policy/main/.pnlibrary/policies/PnSoulsPolicy.java"
        private const val UPDATE_OWNER = "pnFolder"
        private const val UPDATE_REPOSITORY = "pnSouls"

        fun connect(owner: JavaPlugin): PnLibraryRuntime {
            val runtime = PnLibraryProvider.get()
            check(runtime.isAtLeastVersion(MINIMUM_VERSION)) {
                "pnSouls требует pnLibrary $MINIMUM_VERSION или новее, установлена ${runtime.version}"
            }
            return PnLibraryRuntime(owner, runtime)
        }
    }
}
