package indi.lt.serialtool.data;

import indi.lt.serialtool.constant.MessageDirection;

public class LogText {
    private final String timeStamp;
    private final String text;

    private final MessageDirection logType;

    public LogText(String timeStamp, String text, MessageDirection logType) {
        this.logType = logType;
        this.timeStamp = timeStamp;
        this.text = text;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LogText)) {
            return false;
        }
        return String.valueOf(this).equals(String.valueOf(obj));
    }

    public String getText() {
        return text;
    }

    public String getLogText(boolean hasTimeStamp) {
        return getLogText(hasTimeStamp, false);
    }

    public String getLogText(boolean hasTimeStamp, boolean hasLogType) {
        if (!(hasLogType || hasTimeStamp)) {
            return text;
        }
        StringBuilder sb = new StringBuilder("[");
        if (hasTimeStamp) {
            sb.append(timeStamp);
            if (hasLogType) {
                sb.append(" ");
            }
        }
        if (hasLogType) {
            sb.append(logType.getType());
        }
        sb.append("] ").append(text);
        return sb.toString();
    }

    @Override
    public String toString() {
        return "[" + timeStamp + " " + logType.getType() + "] " + text;
    }
}
