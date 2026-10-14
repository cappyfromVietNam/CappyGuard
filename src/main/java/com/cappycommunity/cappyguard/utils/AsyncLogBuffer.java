package com.cappycommunity.cappyguard.utils;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * High-performance non-blocking asynchronous log buffer.
 * Eliminates disk I/O lag on the server main thread by batch flushing logs in the background.
 */
public final class AsyncLogBuffer {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private final JavaPlugin plugin;
    private final File logFile;
    private BukkitTask flushTask;

    public AsyncLogBuffer(JavaPlugin plugin, String relativePath) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), relativePath != null ? relativePath : "logs/cappyguard.log");
        initLogFile();
        startFlushTask();
    }

    private void initLogFile() {
        try {
            File parent = logFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to initialize CappyGuard log file: " + e.getMessage());
        }
    }

    private void startFlushTask() {
        // Flushes every 5 seconds (100 ticks) asynchronously
        this.flushTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::flush, 100L, 100L);
    }

    /**
     * Enqueues a formatted log message without blocking the main game thread.
     */
    public void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        String entry = String.format("[%s] [%s] %s", timestamp, level, message);
        queue.offer(entry);
    }

    /**
     * Drains the queue and writes buffered entries to disk in a single batch.
     */
    public void flush() {
        if (queue.isEmpty()) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, StandardCharsets.UTF_8, true))) {
            String line;
            while ((line = queue.poll()) != null) {
                writer.write(line);
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write to CappyGuard log file: " + e.getMessage());
        }
    }

    /**
     * Cleanly cancels the flush task and performs a synchronous final flush on plugin disable.
     */
    public void shutdown() {
        if (flushTask != null && !flushTask.isCancelled()) {
            flushTask.cancel();
        }
        flush();
    }
}
