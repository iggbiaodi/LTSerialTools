package indi.lt.serialtool.data;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 按 UTF-8 字节数限制的显示缓存。
 */
public class BoundedDisplayBuffer {
    private static final double RECOVERY_THRESHOLD = 0.8;

    private final ArrayDeque<BufferedDisplayLine> lines = new ArrayDeque<>();
    private long capacityBytes;
    private long currentBytes;
    private boolean overflow;
    private OverflowListener overflowListener;

    public interface OverflowListener {
        void onOverflowChanged(boolean overflow);
    }

    public static final class AppendResult {
        private final List<BufferedDisplayLine> removedLines;

        public AppendResult(List<BufferedDisplayLine> removedLines) {
            this.removedLines = removedLines == null ? List.of() : List.copyOf(removedLines);
        }

        public List<BufferedDisplayLine> getRemovedLines() {
            return removedLines;
        }
    }

    public BoundedDisplayBuffer(long capacityBytes) {
        this.capacityBytes = Math.max(1024L, capacityBytes);
    }

    public synchronized AppendResult append(BufferedDisplayLine line) {
        if (line == null) {
            return new AppendResult(List.of());
        }

        BufferedDisplayLine normalized = normalizeLine(line);
        List<BufferedDisplayLine> removedLines = new ArrayList<>();
        while (currentBytes + normalized.getByteSize() > capacityBytes && !lines.isEmpty()) {
            BufferedDisplayLine removed = removeFirstLine();
            if (removed != null) {
                removedLines.add(removed);
            }
        }

        lines.addLast(normalized);
        currentBytes += normalized.getByteSize();
        updateOverflowState();
        return new AppendResult(removedLines);
    }

    public synchronized void replaceAll(List<BufferedDisplayLine> newLines) {
        clearInternal();
        if (newLines != null) {
            for (BufferedDisplayLine line : newLines) {
                append(line);
            }
        }
        updateOverflowState();
    }

    public synchronized List<BufferedDisplayLine> snapshot() {
        return new ArrayList<>(lines);
    }

    public synchronized void clear() {
        clearInternal();
        updateOverflowState();
    }

    public synchronized void setCapacity(long capacityBytes) {
        this.capacityBytes = Math.max(1024L, capacityBytes);
        while (currentBytes > this.capacityBytes && !lines.isEmpty()) {
            removeFirstLine();
        }
        updateOverflowState();
    }

    public synchronized void setOverflowListener(OverflowListener overflowListener) {
        this.overflowListener = overflowListener;
    }

    private void clearInternal() {
        lines.clear();
        currentBytes = 0L;
    }

    private BufferedDisplayLine removeFirstLine() {
        BufferedDisplayLine removed = lines.pollFirst();
        if (removed == null) {
            return null;
        }
        currentBytes -= removed.getByteSize();
        return removed;
    }

    private BufferedDisplayLine normalizeLine(BufferedDisplayLine line) {
        if (line.getByteSize() <= capacityBytes) {
            return line;
        }

        int reservedMetaBytes = (line.getTimestampText() + line.getDataTypeText() + '\n')
                .getBytes(StandardCharsets.UTF_8).length + 8;
        int maxMessageBytes = Math.max(0, (int) capacityBytes - reservedMetaBytes);
        return line.withMessageText(trimToLastBytes(line.getMessageText(), maxMessageBytes));
    }

    private String trimToLastBytes(String text, int maxBytes) {
        if (text == null || text.isEmpty() || maxBytes <= 0) {
            return "";
        }

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }

        int start = bytes.length - maxBytes;
        while (start < bytes.length && (bytes[start] & 0xC0) == 0x80) {
            start++;
        }
        if (start >= bytes.length) {
            start = Math.max(0, bytes.length - maxBytes);
        }
        return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
    }

    private void updateOverflowState() {
        boolean previous = overflow;
        double ratio = capacityBytes <= 0 ? 0.0 : (double) currentBytes / capacityBytes;
        if (ratio >= 1.0) {
            overflow = true;
        } else if (ratio < RECOVERY_THRESHOLD) {
            overflow = false;
        }
        if (previous != overflow && overflowListener != null) {
            overflowListener.onOverflowChanged(overflow);
        }
    }
}
