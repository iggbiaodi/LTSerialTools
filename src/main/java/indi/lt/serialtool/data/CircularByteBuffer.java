package indi.lt.serialtool.data;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 环形字节缓冲区：用于限制接收数据的内存占用
 * 当数据达到容量上限时，丢弃最早的数据，保留最新的数据
 */
public class CircularByteBuffer {

    // 默认缓冲区容量：10MB
    public static final long DEFAULT_BUFFER_CAPACITY = 10 * 1024 * 1024;

    // 配置键
    public static final String KEY_BUFFER_CAPACITY = "main.form.bufferCapacity";
    public static final String KEY_BUFFER_CAPACITY_UNIT = "main.form.bufferCapacityUnit";

    // 缓冲区容量（字节）
    private long capacity;

    // 当前数据大小
    private final AtomicLong currentSize = new AtomicLong(0);

    // 环形缓冲区（使用ByteBuffer数组实现）
    private final StringBuilder buffer = new StringBuilder();

    // 溢出回调
    private Consumer<Boolean> overflowCallback;

    // 是否溢出
    private final AtomicBoolean isOverflow = new AtomicBoolean(false);

    // 溢出恢复阈值（低于容量的80%时恢复）
    private static final double RECOVERY_THRESHOLD = 0.8;

    public CircularByteBuffer() {
        this(DEFAULT_BUFFER_CAPACITY);
    }

    public CircularByteBuffer(long capacity) {
        this.capacity = Math.max(1024, capacity); // 最小1KB
    }

    /**
     * 设置溢出回调
     */
    public void setOverflowCallback(Consumer<Boolean> callback) {
        this.overflowCallback = callback;
    }

    /**
     * 追加数据
     */
    public synchronized void append(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        int dataSize = data.getBytes(StandardCharsets.UTF_8).length;

        // 检查是否需要移除旧数据
        while (currentSize.get() + dataSize > capacity && buffer.length() > 0) {
            removeOldestData();
        }

        // 如果单条数据就超过容量，只保留最后的部分
        if (dataSize > capacity) {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            int start = bytes.length - (int) capacity;
            String trimmed = new String(bytes, start, (int) capacity, StandardCharsets.UTF_8);
            buffer.setLength(0);
            buffer.append(trimmed);
            currentSize.set(capacity);
            triggerOverflow(true);
        } else {
            buffer.append(data);
            currentSize.addAndGet(dataSize);
        }

        // 检查溢出状态
        checkOverflowStatus();
    }

    /**
     * 移除最旧的数据
     */
    private void removeOldestData() {
        if (buffer.length() == 0) {
            return;
        }

        // 找到第一个换行符或删除一定比例的数据
        int removeIndex = buffer.indexOf("\n");
        if (removeIndex < 0) {
            // 没有换行符，删除前10%的数据
            removeIndex = Math.max(1, buffer.length() / 10);
        } else {
            removeIndex++; // 包含换行符
        }

        String removed = buffer.substring(0, removeIndex);
        buffer.delete(0, removeIndex);
        currentSize.addAndGet(-removed.getBytes(StandardCharsets.UTF_8).length);
    }

    /**
     * 检查并更新溢出状态
     */
    private void checkOverflowStatus() {
        boolean wasOverflow = isOverflow.get();
        double ratio = (double) currentSize.get() / capacity;

        if (ratio >= 1.0) {
            if (!wasOverflow) {
                isOverflow.set(true);
                triggerOverflow(true);
            }
        } else if (ratio < RECOVERY_THRESHOLD && wasOverflow) {
            isOverflow.set(false);
            triggerOverflow(false);
        }
    }

    /**
     * 触发溢出回调
     */
    private void triggerOverflow(boolean overflow) {
        if (overflowCallback != null) {
            overflowCallback.accept(overflow);
        }
    }

    /**
     * 获取当前是否溢出
     */
    public boolean isOverflow() {
        return isOverflow.get();
    }

    /**
     * 获取当前数据大小
     */
    public long getCurrentSize() {
        return currentSize.get();
    }

    /**
     * 获取缓冲区容量
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * 设置缓冲区容量
     */
    public void setCapacity(long capacity) {
        this.capacity = Math.max(1024, capacity);

        // 如果当前数据超过新容量，裁剪数据
        synchronized (this) {
            while (currentSize.get() > this.capacity && buffer.length() > 0) {
                removeOldestData();
            }
            checkOverflowStatus();
        }
    }

    /**
     * 获取缓冲区内容
     */
    public synchronized String getContent() {
        return buffer.toString();
    }

    /**
     * 清空缓冲区
     */
    public synchronized void clear() {
        buffer.setLength(0);
        currentSize.set(0);
        isOverflow.set(false);
        triggerOverflow(false);
    }

    /**
     * 获取使用率（0.0 - 1.0）
     */
    public double getUsageRatio() {
        return (double) currentSize.get() / capacity;
    }

    /**
     * 获取格式化的容量字符串
     */
    public static String formatCapacity(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
