package indi.lt.serialtool.service;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortTimeoutException;
import indi.lt.serialtool.component.MyStyleClassedTextArea;
import indi.lt.serialtool.constant.MessageDirection;
import indi.lt.serialtool.data.BufferedDisplayLine;
import indi.lt.serialtool.data.BufferedDisplayLine.DataType;
import indi.lt.serialtool.data.LogText;
import indi.lt.serialtool.utils.StringUtil;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 串口读取服务。
 */
public class SerialReadService extends Service<Void> {
    private static final Logger LOG = LogManager.getLogger(SerialReadService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();
    private static final int READ_BUF_SIZE = 2048;

    private final SerialPort comPort;
    private final MyStyleClassedTextArea targetTextArea;

    public interface HighlighterScheduler {
        void schedule();
    }

    private final HighlighterScheduler highlighterScheduler;
    private final SimpleBooleanProperty timeStampDisplayProperty = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty hexDisplayProperty = new SimpleBooleanProperty(false);
    private final int lineTimeoutMs;
    private final boolean showLogType;

    private final AtomicLong recvBytesCount = new AtomicLong(0);

    private Consumer<Long> onRecvBytesChanged;
    private Consumer<BufferedDisplayLine> onDisplayLineReceived;
    private Consumer<byte[]> onRawBytesReceived;

    private boolean internalAppendEnabled = true;
    private boolean decodeTextEnabled = true;

    public SerialReadService(SerialPort comPort,
                             MyStyleClassedTextArea targetTextArea,
                             BooleanProperty timeStampDisplayProperty,
                             HighlighterScheduler highlighter) {
        this(comPort, targetTextArea, timeStampDisplayProperty, null, highlighter, 0, false);
    }

    public SerialReadService(SerialPort comPort,
                             MyStyleClassedTextArea targetTextArea,
                             BooleanProperty timeStampDisplayProperty,
                             HighlighterScheduler highlighter,
                             boolean showLogType) {
        this(comPort, targetTextArea, timeStampDisplayProperty, null, highlighter, 0, showLogType);
    }

    public SerialReadService(SerialPort comPort,
                             MyStyleClassedTextArea targetTextArea,
                             BooleanProperty timeStampDisplayProperty,
                             BooleanProperty hexDisplayProperty,
                             HighlighterScheduler highlighter) {
        this(comPort, targetTextArea, timeStampDisplayProperty, hexDisplayProperty, highlighter, 0, false);
    }

    public SerialReadService(SerialPort comPort,
                             MyStyleClassedTextArea targetTextArea,
                             BooleanProperty timeStampDisplayProperty,
                             BooleanProperty hexDisplayProperty,
                             int lineTimeoutMs,
                             HighlighterScheduler highlighter) {
        this(comPort, targetTextArea, timeStampDisplayProperty, hexDisplayProperty, highlighter, lineTimeoutMs, false);
    }

    public SerialReadService(SerialPort comPort,
                             MyStyleClassedTextArea targetTextArea,
                             BooleanProperty timeStampDisplayProperty,
                             BooleanProperty hexDisplayProperty,
                             HighlighterScheduler highlighter,
                             boolean showLogType) {
        this(comPort, targetTextArea, timeStampDisplayProperty, hexDisplayProperty, highlighter, 0, showLogType);
    }

    public SerialReadService(SerialPort comPort,
                             MyStyleClassedTextArea targetTextArea,
                             BooleanProperty timeStampDisplayProperty,
                             BooleanProperty hexDisplayProperty,
                             HighlighterScheduler highlighter,
                             int lineTimeoutMs,
                             boolean showLogType) {
        this.comPort = Objects.requireNonNull(comPort);
        this.targetTextArea = Objects.requireNonNull(targetTextArea);
        this.timeStampDisplayProperty.bind(timeStampDisplayProperty);
        if (hexDisplayProperty != null) {
            this.hexDisplayProperty.bind(hexDisplayProperty);
        }
        this.highlighterScheduler = highlighter;
        this.lineTimeoutMs = Math.max(lineTimeoutMs, 0);
        this.showLogType = showLogType;
    }

    @Override
    protected Task<Void> createTask() {
        return new Task<>() {
            @Override
            protected Void call() {
                LineTimestampTracker tracker = new LineTimestampTracker();
                try (InputStream in = comPort.getInputStream()) {
                    CharsetDecoder decoder = createUtf8Decoder();

                    byte[] rawBuf = new byte[READ_BUF_SIZE];
                    ByteBuffer byteBuf = ByteBuffer.allocate(READ_BUF_SIZE * 2);
                    CharBuffer charBuf = CharBuffer.allocate(READ_BUF_SIZE * 2);
                    StringBuilder lineBuf = new StringBuilder();
                    boolean previousHexDisplay = hexDisplayProperty.get();

                    while (!isCancelled()) {
                        int n;
                        try {
                            n = in.read(rawBuf);
                        } catch (SerialPortTimeoutException timeoutException) {
                            flushLineBufferByTimeout(lineBuf, tracker, System.currentTimeMillis());
                            continue;
                        }

                        if (n < 0) {
                            break;
                        }
                        if (n == 0) {
                            flushLineBufferByTimeout(lineBuf, tracker, System.currentTimeMillis());
                            continue;
                        }

                        long receivedAtMillis = System.currentTimeMillis();
                        long totalBytes = recvBytesCount.addAndGet(n);
                        if (onRecvBytesChanged != null) {
                            onRecvBytesChanged.accept(totalBytes);
                        }
                        if (onRawBytesReceived != null) {
                            onRawBytesReceived.accept(Arrays.copyOf(rawBuf, n));
                        }
                        if (!decodeTextEnabled) {
                            continue;
                        }

                        boolean hexMode = hexDisplayProperty.get();
                        if (hexMode) {
                            if (!previousHexDisplay) {
                                flushTextTail(decoder, byteBuf, charBuf, lineBuf, tracker, receivedAtMillis);
                                decoder = createUtf8Decoder();
                            }
                            emitLine(StringUtil.bytesToHexString(rawBuf, n), receivedAtMillis);
                        } else {
                            if (previousHexDisplay) {
                                decoder = createUtf8Decoder();
                                byteBuf.clear();
                                charBuf.clear();
                                lineBuf.setLength(0);
                                tracker.reset();
                            }

                            if (lineBuf.isEmpty()) {
                                tracker.markIfAbsent(receivedAtMillis);
                            }
                            byteBuf = ensureWritable(byteBuf, n);
                            byteBuf.put(rawBuf, 0, n);
                            byteBuf.flip();
                            decodeBuffer(decoder, byteBuf, charBuf, lineBuf, false, receivedAtMillis, tracker);
                            byteBuf.compact();

                            // 数据持续进来时，检查当前行是否已超时
                            flushLineBufferByTimeout(lineBuf, tracker, System.currentTimeMillis());
                        }

                        previousHexDisplay = hexMode;
                    }

                    if (decodeTextEnabled && !previousHexDisplay) {
                        flushTextTail(decoder, byteBuf, charBuf, lineBuf, tracker, System.currentTimeMillis());
                    }
                } catch (CharacterCodingException e) {
                    LOG.error("UTF-8 decoding error", e);
                } catch (IOException e) {
                    if (!isCancelled()) {
                        LOG.error("Serial read error", e);
                    }
                } finally {
                    tryClosePort();
                }
                return null;
            }
        };
    }

    private CharsetDecoder createUtf8Decoder() {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    private void flushLineBufferByTimeout(StringBuilder lineBuf,
                                          LineTimestampTracker tracker,
                                          long nowMillis) {
        if (lineTimeoutMs <= 0 || lineBuf.isEmpty() || hexDisplayProperty.get()) {
            return;
        }
        long lineStart = tracker.currentOrFallback(nowMillis);
        if (nowMillis - lineStart < lineTimeoutMs) {
            return;
        }
        emitLine(lineBuf.toString(), tracker.currentOrFallback(nowMillis));
        lineBuf.setLength(0);
        tracker.reset();
    }

    private void flushTextTail(CharsetDecoder decoder,
                               ByteBuffer byteBuf,
                               CharBuffer charBuf,
                               StringBuilder lineBuf,
                               LineTimestampTracker tracker,
                               long fallbackMillis) throws CharacterCodingException {
        byteBuf.flip();
        decodeBuffer(decoder, byteBuf, charBuf, lineBuf, true, fallbackMillis, tracker);

        if (!lineBuf.isEmpty()) {
            emitLine(lineBuf.toString(), tracker.currentOrFallback(fallbackMillis));
            lineBuf.setLength(0);
            tracker.reset();
        }

        byteBuf.clear();
        charBuf.clear();
    }

    private void feedChars(CharBuffer chars,
                           StringBuilder lineBuf,
                           long receivedAtMillis,
                           LineTimestampTracker tracker) {
        while (chars.hasRemaining()) {
            char c = chars.get();
            tracker.markIfAbsent(receivedAtMillis);
            if (c == '\n') {
                int end = lineBuf.length();
                if (end > 0 && lineBuf.charAt(end - 1) == '\r') {
                    lineBuf.setLength(end - 1);
                }
                emitLine(lineBuf.toString(), tracker.currentOrFallback(receivedAtMillis));
                lineBuf.setLength(0);
                tracker.reset();
            } else {
                lineBuf.append(c);
            }
        }
    }

    private void decodeBuffer(CharsetDecoder decoder,
                              ByteBuffer byteBuf,
                              CharBuffer charBuf,
                              StringBuilder lineBuf,
                              boolean endOfInput,
                              long receivedAtMillis,
                              LineTimestampTracker tracker) throws CharacterCodingException {
        while (true) {
            CoderResult cr = decoder.decode(byteBuf, charBuf, endOfInput);
            charBuf.flip();
            if (charBuf.hasRemaining()) {
                feedChars(charBuf, lineBuf, receivedAtMillis, tracker);
            }
            charBuf.clear();

            if (cr.isError()) {
                cr.throwException();
            }
            if (cr.isOverflow()) {
                continue;
            }
            if (cr.isUnderflow()) {
                break;
            }
        }

        if (!endOfInput) {
            return;
        }

        while (true) {
            CoderResult cr = decoder.flush(charBuf);
            charBuf.flip();
            if (charBuf.hasRemaining()) {
                feedChars(charBuf, lineBuf, receivedAtMillis, tracker);
            }
            charBuf.clear();

            if (cr.isError()) {
                cr.throwException();
            }
            if (cr.isOverflow()) {
                continue;
            }
            if (cr.isUnderflow()) {
                break;
            }
        }
    }

    private ByteBuffer ensureWritable(ByteBuffer buffer, int incomingBytes) {
        if (buffer.remaining() >= incomingBytes) {
            return buffer;
        }

        int newCap = buffer.capacity();
        int minRequired = buffer.position() + incomingBytes;
        while (newCap < minRequired) {
            newCap <<= 1;
        }

        ByteBuffer enlarged = ByteBuffer.allocate(newCap);
        buffer.flip();
        enlarged.put(buffer);
        return enlarged;
    }

    private void emitLine(String raw, long receivedAtMillis) {
        boolean hexMode = hexDisplayProperty.get();
        String timeText = LocalDateTime.ofInstant(Instant.ofEpochMilli(receivedAtMillis), SYSTEM_ZONE)
                .format(TIME_FORMATTER);
        BufferedDisplayLine displayLine = BufferedDisplayLine.of(
                receivedAtMillis,
                timeText,
                hexMode ? DataType.HEX : DataType.TXT,
                MessageDirection.RECEIVE,
                raw
        );
        LogText line = formatLine(raw, receivedAtMillis);
        String rendered = line.getLogText(timeStampDisplayProperty.get(), showLogType) + "\n";

        if (onDisplayLineReceived != null) {
            onDisplayLineReceived.accept(displayLine);
        }

        if (internalAppendEnabled) {
            Platform.runLater(() -> {
                targetTextArea.appendText(rendered);
                if (highlighterScheduler != null) {
                    highlighterScheduler.schedule();
                }
            });
        }
    }

    private LogText formatLine(String raw, long receivedAtMillis) {
        String ts = LocalDateTime.ofInstant(Instant.ofEpochMilli(receivedAtMillis), SYSTEM_ZONE)
                .format(TIME_FORMATTER);
        return new LogText(ts, raw, MessageDirection.RECEIVE);
    }

    private void tryClosePort() {
        try {
            if (comPort.isOpen()) {
                comPort.closePort();
            }
        } catch (Exception ex) {
            LOG.warn("closePort failed", ex);
        }
    }

    @Override
    public void start() {
        super.start();
        LOG.info("串口 [{}] 读取服务开启", comPort);
    }

    @Override
    public boolean cancel() {
        boolean cancelled = super.cancel();
        tryClosePort();
        if (cancelled) {
            LOG.info("串口 [{}] 读取服务关闭", comPort);
            return true;
        }
        LOG.error("串口 [{}] 读取服务失败", comPort);
        return false;
    }

    public void setOnRecvBytesChanged(Consumer<Long> callback) {
        this.onRecvBytesChanged = callback;
    }

    public void setOnDisplayLineReceived(Consumer<BufferedDisplayLine> callback) {
        this.onDisplayLineReceived = callback;
    }

    public void setOnRawBytesReceived(Consumer<byte[]> callback) {
        this.onRawBytesReceived = callback;
    }

    public void setInternalAppendEnabled(boolean enabled) {
        this.internalAppendEnabled = enabled;
    }

    public void setDecodeTextEnabled(boolean enabled) {
        this.decodeTextEnabled = enabled;
    }

    public long getRecvBytesCount() {
        return recvBytesCount.get();
    }

    public void resetRecvBytesCount() {
        recvBytesCount.set(0);
        if (onRecvBytesChanged != null) {
            onRecvBytesChanged.accept(0L);
        }
    }

    private static final class LineTimestampTracker {
        private long currentLineStartMillis = -1L;

        private void markIfAbsent(long receivedAtMillis) {
            if (currentLineStartMillis < 0L) {
                currentLineStartMillis = receivedAtMillis;
            }
        }

        private long currentOrFallback(long fallbackMillis) {
            return currentLineStartMillis >= 0L ? currentLineStartMillis : fallbackMillis;
        }

        private void reset() {
            currentLineStartMillis = -1L;
        }
    }
}
