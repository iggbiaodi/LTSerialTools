package indi.lt.serialtool.data;

import indi.lt.serialtool.constant.MessageDirection;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 接收区中的一行结构化日志。
 */
public final class BufferedDisplayLine {

    public enum DataType {
        TXT,
        HEX
    }

    private final long receivedAtMillis;
    private final String timestampText;
    private final DataType dataType;
    private final MessageDirection messageDirection;
    private final String messageText;
    private final String searchableText;
    private final int byteSize;

    private BufferedDisplayLine(long receivedAtMillis, String timestampText, DataType dataType, MessageDirection messageDirection, String messageText) {
        this.receivedAtMillis = receivedAtMillis;
        this.timestampText = timestampText == null ? "" : timestampText;
        this.dataType = dataType == null ? DataType.TXT : dataType;
        this.messageDirection = messageDirection;
        this.messageText = messageText == null ? "" : messageText;
        this.searchableText = (this.timestampText + " " + this.dataType.name() + " " + this.messageText)
                .toLowerCase(Locale.ROOT);
        this.byteSize = (this.timestampText + this.dataType.name() + this.messageText + '\n')
                .getBytes(StandardCharsets.UTF_8).length;
    }

    public static BufferedDisplayLine of(long receivedAtMillis, String timestampText, DataType dataType, MessageDirection messageDirection, String messageText) {
        return new BufferedDisplayLine(receivedAtMillis, timestampText, dataType, messageDirection, messageText);
    }

    public long getReceivedAtMillis() {
        return receivedAtMillis;
    }

    public String getTimestampText() {
        return timestampText;
    }

    public DataType getDataType() {
        return dataType;
    }

    public String getDataTypeText() {
        return dataType.name();
    }

    public String getMessageText() {
        return messageText;
    }

    public int getByteSize() {
        return byteSize;
    }

    public int getRenderedCharLength(boolean showTimestamp, boolean showDataType) {
        return render(showTimestamp, showDataType).length();
    }

    public String render(boolean showTimestamp, boolean showDataType) {
        StringBuilder sb = new StringBuilder(messageText.length() + 32);
        if (showTimestamp && !timestampText.isEmpty()) {
            sb.append('[').append(timestampText).append("] ");
        }
        if (showDataType) {
            sb.append('[').append(dataType.name()).append("] ");
        }
        sb.append(messageText).append('\n');
        return sb.toString();
    }

    public boolean matchesAny(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (!keyword.isEmpty() && searchableText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public BufferedDisplayLine withMessageText(String nextMessageText) {
        return new BufferedDisplayLine(receivedAtMillis, timestampText, dataType, messageDirection, nextMessageText);
    }
}
