/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.storage

import com.intellij.openapi.diagnostic.Logger
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches this project's signal files under `<appDir>/signals/` so external agents
 * can ask the IDE to reload storage or select/navigate trace points.
 *
 * Signals (no XML file watch — agents must write refresh signals after edits):
 * - `<projectId>.request_refresh`
 * - `<projectId>.request_refresh_profile`
 * - `<projectId>.request_refresh_settings`
 * - `<projectId>.select_trace_points`
 *
 * Uses [WatchService] plus a periodic mtime poll — native watches often miss repeated
 * overwrites of the same signal file on Windows (e.g. rapid `trace_tree add`).
 *
 * Call only when a project id is already bound. For Case C (unbound), use
 * [StorageReadyWatcher] until `<projectId>.storage-ready` binds storage.
 */
class ExternalStorageWatcher(
    private val projectId: String,
    private val onFullRefresh: (reason: String) -> Unit,
    private val onProfileRefresh: () -> Unit,
    private val onSettingsRefresh: () -> Unit,
    private val onSelectRequest: () -> Unit,
) : AutoCloseable {
    private val log = Logger.getInstance(ExternalStorageWatcher::class.java)
    private val closed = AtomicBoolean(false)
    private var watchService: WatchService? = null
    private val keys = mutableMapOf<WatchKey, Path>()
    private var pollThread: Thread? = null
    private var debounceExecutor: ScheduledExecutorService? = null
    private var pollFuture: ScheduledFuture<*>? = null
    private var pendingReason: String? = null
    private var profilePending = false
    private var settingsPending = false
    private var selectPending = false
    /** Last observed mtime per signal file name; poll re-fires when the agent overwrites. */
    private val lastSeenMtimeMs = ConcurrentHashMap<String, Long>()

    /**
     * @param replayExistingRefresh when true, schedule fresh request_refresh / _profile /
     *   _settings already on disk (IDE was closed or late start). Pass false after a Case C
     *   storage-ready bind — document was just loaded; refresh would be redundant.
     *   Fresh select signals are still replayed either way.
     */
    fun start(replayExistingRefresh: Boolean = true) {
        if (closed.get()) return
        try {
            val ws = FileSystems.getDefault().newWatchService()
            watchService = ws
            debounceExecutor = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "code-trace-tree-storage-debounce").apply { isDaemon = true }
            }
            registerSignalsDir(ws)
            if (replayExistingRefresh) {
                considerSignal(
                    AgentSignalFiles.refreshPath(projectId),
                    AgentSignalFiles.refreshFileName(projectId)
                ) { scheduleFullReload("refresh-request") }
                considerSignal(
                    AgentSignalFiles.refreshProfilePath(projectId),
                    AgentSignalFiles.refreshProfileFileName(projectId)
                ) { scheduleProfileReload() }
                considerSignal(
                    AgentSignalFiles.refreshSettingsPath(projectId),
                    AgentSignalFiles.refreshSettingsFileName(projectId)
                ) { scheduleSettingsReload() }
            } else {
                // Do not reload again, but remember mtimes so poll does not immediately replay.
                rememberMtime(AgentSignalFiles.refreshPath(projectId))
                rememberMtime(AgentSignalFiles.refreshProfilePath(projectId))
                rememberMtime(AgentSignalFiles.refreshSettingsPath(projectId))
            }
            considerSignal(
                AgentSignalFiles.selectPath(projectId),
                AgentSignalFiles.selectFileName(projectId)
            ) { scheduleSelect() }

            pollFuture = debounceExecutor?.scheduleWithFixedDelay(
                {
                    if (!closed.get()) pollSignals()
                },
                POLL_MS,
                POLL_MS,
                TimeUnit.MILLISECONDS
            )
            val thread = Thread({ pollLoop(ws) }, "code-trace-tree-storage-watch").apply {
                isDaemon = true
                start()
            }
            pollThread = thread
        } catch (e: Exception) {
            log.warn("Failed to start Code Trace Tree external storage watcher", e)
            close()
        }
    }

    /** Re-register the signals directory if needed. */
    fun refreshRegistrations() {
        val ws = watchService ?: return
        try {
            registerSignalsDir(ws)
        } catch (e: Exception) {
            log.debug("Failed to refresh signal watch registrations", e)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            pollFuture?.cancel(false)
        } catch (_: Exception) {
        }
        pollFuture = null
        try {
            debounceExecutor?.shutdownNow()
        } catch (_: Exception) {
        }
        debounceExecutor = null
        try {
            watchService?.close()
        } catch (_: Exception) {
        }
        watchService = null
        keys.clear()
        pollThread = null
        lastSeenMtimeMs.clear()
    }

    private fun pollSignals() {
        considerSignal(
            AgentSignalFiles.refreshPath(projectId),
            AgentSignalFiles.refreshFileName(projectId)
        ) { scheduleFullReload("refresh-request") }
        considerSignal(
            AgentSignalFiles.refreshProfilePath(projectId),
            AgentSignalFiles.refreshProfileFileName(projectId)
        ) { scheduleProfileReload() }
        considerSignal(
            AgentSignalFiles.refreshSettingsPath(projectId),
            AgentSignalFiles.refreshSettingsFileName(projectId)
        ) { scheduleSettingsReload() }
        considerSignal(
            AgentSignalFiles.selectPath(projectId),
            AgentSignalFiles.selectFileName(projectId)
        ) { scheduleSelect() }
    }

    /**
     * If [path] exists, is fresh (TTL), and mtime changed since last see, run [onUpdated].
     */
    private fun considerSignal(path: Path, fileName: String, onUpdated: () -> Unit) {
        if (!AgentSignalFiles.isFresh(path)) return
        val mtime = try {
            Files.getLastModifiedTime(path).toMillis()
        } catch (_: Exception) {
            return
        }
        val prev = lastSeenMtimeMs.put(fileName, mtime)
        if (prev == mtime) return
        onUpdated()
    }

    private fun rememberMtime(path: Path) {
        if (!Files.isRegularFile(path)) return
        val name = path.fileName.toString()
        try {
            lastSeenMtimeMs[name] = Files.getLastModifiedTime(path).toMillis()
        } catch (_: Exception) {
        }
    }

    private fun clearSeen(fileName: String) {
        lastSeenMtimeMs.remove(fileName)
    }

    private fun registerSignalsDir(ws: WatchService) {
        val dir = AgentSignalFiles.ensureSignalsDir()
        if (keys.values.none { it == dir }) {
            registerDir(ws, dir)
        }
    }

    private fun registerDir(ws: WatchService, dir: Path) {
        val key = dir.register(
            ws,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE
        )
        keys[key] = dir
    }

    private fun pollLoop(ws: WatchService) {
        while (!closed.get()) {
            val key = try {
                ws.take()
            } catch (_: InterruptedException) {
                break
            } catch (_: Exception) {
                break
            }
            keys[key] ?: continue
            for (event in key.pollEvents()) {
                val kind = event.kind()
                if (kind == StandardWatchEventKinds.OVERFLOW) continue
                val name = (event.context() as? Path)?.fileName?.toString() ?: continue
                handleEvent(name)
            }
            if (!key.reset()) {
                keys.remove(key)
                if (keys.isEmpty()) break
            }
        }
    }

    private fun handleEvent(fileName: String) {
        when (fileName) {
            AgentSignalFiles.selectFileName(projectId) ->
                considerSignal(
                    AgentSignalFiles.selectPath(projectId),
                    fileName
                ) { scheduleSelect() }
            AgentSignalFiles.refreshFileName(projectId) ->
                considerSignal(
                    AgentSignalFiles.refreshPath(projectId),
                    fileName
                ) { scheduleFullReload("refresh-request") }
            AgentSignalFiles.refreshProfileFileName(projectId) ->
                considerSignal(
                    AgentSignalFiles.refreshProfilePath(projectId),
                    fileName
                ) { scheduleProfileReload() }
            AgentSignalFiles.refreshSettingsFileName(projectId) ->
                considerSignal(
                    AgentSignalFiles.refreshSettingsPath(projectId),
                    fileName
                ) { scheduleSettingsReload() }
        }
    }

    private fun scheduleFullReload(reason: String) {
        pendingReason = reason
        val executor = debounceExecutor ?: return
        executor.schedule({
            if (closed.get()) return@schedule
            val r = pendingReason ?: return@schedule
            pendingReason = null
            try {
                onFullRefresh(r)
            } catch (e: Exception) {
                clearSeen(AgentSignalFiles.refreshFileName(projectId))
                log.warn("Code Trace Tree external reload failed ($r)", e)
            }
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    private fun scheduleProfileReload() {
        profilePending = true
        val executor = debounceExecutor ?: return
        executor.schedule({
            if (closed.get() || !profilePending) return@schedule
            profilePending = false
            try {
                onProfileRefresh()
            } catch (e: Exception) {
                clearSeen(AgentSignalFiles.refreshProfileFileName(projectId))
                log.warn("Code Trace Tree external profile refresh failed", e)
            }
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    private fun scheduleSettingsReload() {
        settingsPending = true
        val executor = debounceExecutor ?: return
        executor.schedule({
            if (closed.get() || !settingsPending) return@schedule
            settingsPending = false
            try {
                onSettingsRefresh()
            } catch (e: Exception) {
                clearSeen(AgentSignalFiles.refreshSettingsFileName(projectId))
                log.warn("Code Trace Tree external settings refresh failed", e)
            }
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    private fun scheduleSelect() {
        selectPending = true
        val executor = debounceExecutor ?: return
        executor.schedule({
            if (closed.get() || !selectPending) return@schedule
            selectPending = false
            try {
                onSelectRequest()
            } catch (e: Exception) {
                clearSeen(AgentSignalFiles.selectFileName(projectId))
                log.warn("Code Trace Tree external select signal failed", e)
            }
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    companion object {
        private const val DEBOUNCE_MS = 400L
        private const val POLL_MS = 1000L
    }
}
