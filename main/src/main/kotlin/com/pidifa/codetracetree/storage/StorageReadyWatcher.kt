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
 * Case C (no bound project id): watch `<appDir>/signals/` for
 * `<projectId>.storage-ready` (no TTL).
 *
 * Uses [WatchService] plus a periodic directory poll — native watches often miss
 * creates on Windows when another process writes the signal files.
 *
 * On each signal, [onStorageReady] is invoked (must not block this watcher's threads —
 * typically posts work with `Application.invokeLater`). Call [clearSeen] if bind should
 * be retried later (e.g. local project id not ready yet).
 *
 * Once storage is bound, close this watcher and start [ExternalStorageWatcher].
 */
class StorageReadyWatcher(
    private val onStorageReady: (projectId: String) -> Unit,
) : AutoCloseable {
    private val log = Logger.getInstance(StorageReadyWatcher::class.java)
    private val closed = AtomicBoolean(false)
    private var watchService: WatchService? = null
    private val keys = mutableMapOf<WatchKey, Path>()
    private var pollThread: Thread? = null
    private var debounceExecutor: ScheduledExecutorService? = null
    private var pollFuture: ScheduledFuture<*>? = null
    private var pendingProjectId: String? = null
    /** Last observed mtime per projectId so poll re-fires when the agent overwrites. */
    private val lastSeenMtimeMs = ConcurrentHashMap<String, Long>()

    fun start() {
        if (closed.get()) return
        try {
            val ws = FileSystems.getDefault().newWatchService()
            watchService = ws
            debounceExecutor = Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "code-trace-tree-storage-ready-debounce").apply { isDaemon = true }
            }
            val dir = AgentSignalFiles.ensureSignalsDir()
            registerDir(ws, dir)
            scanExisting(dir)
            pollFuture = debounceExecutor?.scheduleWithFixedDelay(
                {
                    if (!closed.get()) {
                        scanExisting(dir)
                    }
                },
                POLL_MS,
                POLL_MS,
                TimeUnit.MILLISECONDS
            )
            val thread = Thread({ pollLoop(ws) }, "code-trace-tree-storage-ready-watch").apply {
                isDaemon = true
                start()
            }
            pollThread = thread
        } catch (e: Exception) {
            log.warn("Failed to start Code Trace Tree storage-ready watcher", e)
            close()
        }
    }

    /** Allow the poller to notify again for [projectId] (e.g. local id file not ready yet). */
    fun clearSeen(projectId: String) {
        lastSeenMtimeMs.remove(projectId)
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

    private fun scanExisting(dir: Path) {
        try {
            Files.list(dir).use { stream ->
                stream.forEach { path ->
                    val name = path.fileName?.toString() ?: return@forEach
                    val id = AgentSignalFiles.projectIdFromStorageReadyFileName(name) ?: return@forEach
                    if (!AgentSignalFiles.exists(path)) return@forEach
                    val mtime = try {
                        Files.getLastModifiedTime(path).toMillis()
                    } catch (_: Exception) {
                        return@forEach
                    }
                    val prev = lastSeenMtimeMs.put(id, mtime)
                    if (prev == mtime) return@forEach
                    scheduleReady(id)
                }
            }
        } catch (e: Exception) {
            log.debug("Failed to scan storage-ready signals", e)
        }
    }

    private fun registerDir(ws: WatchService, dir: Path) {
        val key = dir.register(
            ws,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY
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
        val projectId = AgentSignalFiles.projectIdFromStorageReadyFileName(fileName) ?: return
        val path = AgentSignalFiles.storageReadyPath(projectId)
        if (!AgentSignalFiles.exists(path)) return
        val mtime = try {
            Files.getLastModifiedTime(path).toMillis()
        } catch (_: Exception) {
            return
        }
        val prev = lastSeenMtimeMs.put(projectId, mtime)
        if (prev == mtime) return
        scheduleReady(projectId)
    }

    private fun scheduleReady(projectId: String) {
        pendingProjectId = projectId
        val executor = debounceExecutor ?: return
        executor.schedule({
            if (closed.get()) return@schedule
            val id = pendingProjectId ?: return@schedule
            pendingProjectId = null
            try {
                // Must not block this thread waiting on the EDT: closing this watcher
                // (shutdownNow) from EDT work would interrupt invokeAndWait here.
                onStorageReady(id)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                lastSeenMtimeMs.remove(id)
            } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                lastSeenMtimeMs.remove(id)
                throw e
            } catch (e: Exception) {
                lastSeenMtimeMs.remove(id)
                log.warn("Code Trace Tree storage-ready handler failed", e)
            }
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    companion object {
        private const val DEBOUNCE_MS = 400L
        private const val POLL_MS = 1000L
    }
}
