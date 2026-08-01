/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.pidifa.codetracetree.storage

import com.intellij.openapi.diagnostic.Logger
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches the bound global project XML and this project's signal files under
 * `<appDir>/signals/` so external agents can edit storage, ask the IDE to reload,
 * or select/navigate trace points. Each watcher only reacts to
 * `<projectId>.request_refresh` / `<projectId>.select_trace_points`.
 */
class ExternalStorageWatcher(
    private val projectId: String,
    private val storageFileProvider: () -> Path?,
    private val shouldIgnore: () -> Boolean,
    private val onExternalChange: (reason: String) -> Unit,
    private val onSelectRequest: () -> Unit,
) : AutoCloseable {
    private val log = Logger.getInstance(ExternalStorageWatcher::class.java)
    private val closed = AtomicBoolean(false)
    private var watchService: WatchService? = null
    private val keys = mutableMapOf<WatchKey, Path>()
    private var pollThread: Thread? = null
    private var debounceExecutor: ScheduledExecutorService? = null
    private var pendingReason: String? = null
    private var selectPending = false

    fun start() {
        if (closed.get()) return
        try {
            val ws = FileSystems.getDefault().newWatchService()
            watchService = ws
            debounceExecutor = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "code-trace-tree-storage-debounce").apply { isDaemon = true }
            }
            registerSignalsDir(ws)
            registerStorageDir(ws)
            // Replay fresh signals written while the IDE was closed; drop stale ones.
            if (AgentSignalFiles.isFresh(AgentSignalFiles.refreshPath(projectId))) {
                scheduleReload("refresh-request")
            }
            if (AgentSignalFiles.isFresh(AgentSignalFiles.selectPath(projectId))) {
                scheduleSelect()
            }
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

    /** Re-register the storage directory if the bound file moved (normally stable). */
    fun refreshRegistrations() {
        val ws = watchService ?: return
        try {
            registerStorageDir(ws)
            registerSignalsDir(ws)
        } catch (e: Exception) {
            log.debug("Failed to refresh storage watch registrations", e)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
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
    }

    private fun registerSignalsDir(ws: WatchService) {
        val dir = AgentSignalFiles.signalsDir()
        Files.createDirectories(dir)
        if (keys.values.none { it == dir }) {
            registerDir(ws, dir)
        }
    }

    private fun registerStorageDir(ws: WatchService) {
        val storageFile = storageFileProvider() ?: return
        val dir = storageFile.parent ?: return
        Files.createDirectories(dir)
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
            val dir = keys[key] ?: continue
            for (event in key.pollEvents()) {
                val kind = event.kind()
                if (kind == StandardWatchEventKinds.OVERFLOW) continue
                val name = (event.context() as? Path)?.fileName?.toString() ?: continue
                handleEvent(dir, name)
            }
            if (!key.reset()) {
                keys.remove(key)
                if (keys.isEmpty()) break
            }
        }
    }

    private fun handleEvent(dir: Path, fileName: String) {
        // Signal file names include projectId, so filename match is enough.
        if (fileName == AgentSignalFiles.selectFileName(projectId)) {
            if (AgentSignalFiles.isFresh(AgentSignalFiles.selectPath(projectId))) {
                scheduleSelect()
            }
            return
        }

        if (shouldIgnore()) return

        if (fileName == AgentSignalFiles.refreshFileName(projectId)) {
            if (AgentSignalFiles.isFresh(AgentSignalFiles.refreshPath(projectId))) {
                scheduleReload("refresh-request")
            }
            return
        }

        val storageFile = storageFileProvider() ?: return
        if (dir == storageFile.parent && fileName == storageFile.fileName.toString()) {
            scheduleReload("storage-xml")
            return
        }
        // Atomic replace may write `file.xml.tmp` then rename — also catch `.tmp` siblings.
        if (dir == storageFile.parent && fileName == storageFile.fileName.toString() + ".tmp") {
            scheduleReload("storage-xml-tmp")
        }
    }

    private fun scheduleReload(reason: String) {
        pendingReason = reason
        val executor = debounceExecutor ?: return
        executor.schedule({
            if (closed.get() || shouldIgnore()) return@schedule
            val r = pendingReason ?: return@schedule
            pendingReason = null
            try {
                onExternalChange(r)
            } catch (e: Exception) {
                log.warn("Code Trace Tree external reload failed ($r)", e)
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
                log.warn("Code Trace Tree external select signal failed", e)
            }
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    companion object {
        private const val DEBOUNCE_MS = 400L
    }
}
