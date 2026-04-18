package indi.lt.serialtool.service;

import indi.lt.serialtool.global.ConfigManager;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 自动保存服务：定时将串口接收的数据保存到日志文件
 */
public class AutoSaveService extends Service<Void> {

    // 自动保存Logger，专门用于保存串口数据
    private static final Logger AUTO_SAVE_LOGGER = LogManager.getLogger("AutoSaveLogger");

    // 配置键
    public static final String KEY_AUTO_SAVE_ENABLED = "main.form.autoSave";
    public static final String KEY_AUTO_SAVE_INTERVAL = "main.form.autoSaveInterval";
    public static final String KEY_AUTO_SAVE_FILE_SIZE = "main.form.autoSaveFileSize";
    public static final String KEY_AUTO_SAVE_FILE_SIZE_UNIT = "main.form.autoSaveFileSizeUnit";

    // 默认值
    public static final int DEFAULT_AUTO_SAVE_INTERVAL = 5; // 默认5秒
    public static final int DEFAULT_AUTO_SAVE_FILE_SIZE = 10; // 默认10MB
    public static final String DEFAULT_AUTO_SAVE_FILE_SIZE_UNIT = "MB";

    // 数据队列
    private final BlockingQueue<String> dataQueue = new LinkedBlockingQueue<>();

    // 数据提供器（用于获取当前接收的所有数据）
    private Supplier<String> dataSupplier;

    // 运行标志
    private final AtomicBoolean running = new AtomicBoolean(false);

    static {
        applyAutoSaveFileSizeToLog4j(false);
    }

    public AutoSaveService() {
    }

    public void setDataSupplier(Supplier<String> supplier) {
        this.dataSupplier = supplier;
    }

    /**
     * 添加要保存的数据
     */
    public void appendData(String data) {
        if (data != null && !data.isEmpty() && running.get()) {
            dataQueue.offer(data);
        }
    }

    @Override
    protected Task<Void> createTask() {
        return new Task<>() {
            @Override
            protected Void call() {
                running.set(true);
                int intervalSeconds = getAutoSaveInterval();

                while (!isCancelled() && running.get()) {
                    try {
                        // 等待一段时间
                        Thread.sleep(intervalSeconds * 1000L);

                        if (isCancelled() || !running.get()) {
                            break;
                        }

                        // 批量保存队列中的数据
                        saveQueuedData();

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                return null;
            }
        };
    }

    /**
     * 保存队列中的数据
     */
    private void saveQueuedData() {
        StringBuilder batch = new StringBuilder();
        String data;
        int count = 0;
        int maxBatch = 1000; // 每次最多处理1000条

        while ((data = dataQueue.poll()) != null && count < maxBatch) {
            batch.append(data);
            count++;
        }

        if (!batch.isEmpty()) {
            AUTO_SAVE_LOGGER.info(batch.toString().trim());
        }
    }

    /**
     * 立即保存所有数据（用于程序退出时）
     */
    public void flush() {
        saveQueuedData();
    }

    @Override
    public void start() {
        if (getState() == State.RUNNING) {
            return;
        }
        running.set(true);
        super.start();
    }

    @Override
    public boolean cancel() {
        running.set(false);
        flush();
        return super.cancel();
    }

    // ========== 静态工具方法 ==========

    /**
     * 获取自动保存间隔（秒）
     */
    public static int getAutoSaveInterval() {
        return ConfigManager.get(KEY_AUTO_SAVE_INTERVAL, Integer.class, DEFAULT_AUTO_SAVE_INTERVAL);
    }

    /**
     * 设置自动保存间隔（秒）
     */
    public static void setAutoSaveInterval(int seconds) {
        ConfigManager.set(KEY_AUTO_SAVE_INTERVAL, Math.max(1, seconds));
    }

    /**
     * 获取自动保存文件大小数值
     */
    public static int getAutoSaveFileSize() {
        return ConfigManager.get(KEY_AUTO_SAVE_FILE_SIZE, Integer.class, DEFAULT_AUTO_SAVE_FILE_SIZE);
    }

    /**
     * 获取自动保存文件大小单位
     */
    public static String getAutoSaveFileSizeUnit() {
        String unit = ConfigManager.get(KEY_AUTO_SAVE_FILE_SIZE_UNIT, DEFAULT_AUTO_SAVE_FILE_SIZE_UNIT);
        return unit != null && (unit.equalsIgnoreCase("KB") || unit.equalsIgnoreCase("MB"))
                ? unit.toUpperCase()
                : DEFAULT_AUTO_SAVE_FILE_SIZE_UNIT;
    }

    /**
     * 获取自动保存文件大小（带单位字符串）
     */
    public static String getAutoSaveFileSizeWithUnit() {
        return getAutoSaveFileSize() + getAutoSaveFileSizeUnit();
    }

    /**
     * 设置自动保存文件大小
     */
    public static void setAutoSaveFileSize(int size, String unit) {
        if (size < 1) size = 1;
        String validUnit = (unit != null && unit.equalsIgnoreCase("KB")) ? "KB" : "MB";
        ConfigManager.set(KEY_AUTO_SAVE_FILE_SIZE, size);
        ConfigManager.set(KEY_AUTO_SAVE_FILE_SIZE_UNIT, validUnit);
        applyAutoSaveFileSizeToLog4j(true);
    }

    private static void applyAutoSaveFileSizeToLog4j(boolean reloadContext) {
        String fileSize = getAutoSaveFileSizeWithUnit();
        System.setProperty("autoSaveFileSize", fileSize);

        if (!reloadContext) {
            return;
        }

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.reconfigure();
    }

    /**
     * 检查自动保存是否启用
     */
    public static boolean isAutoSaveEnabled() {
        return ConfigManager.get(KEY_AUTO_SAVE_ENABLED, Boolean.class, false);
    }

    /**
     * 设置自动保存启用状态
     */
    public static void setAutoSaveEnabled(boolean enabled) {
        ConfigManager.set(KEY_AUTO_SAVE_ENABLED, enabled);
    }
}
