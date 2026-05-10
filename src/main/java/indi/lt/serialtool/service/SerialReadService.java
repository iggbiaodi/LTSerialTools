package indi.lt.serialtool.service;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortTimeoutException;
import indi.lt.serialtool.component.PromptInlineCssTextArea;
import indi.lt.serialtool.constant.LogType;
import indi.lt.serialtool.data.LogText;
import indi.lt.serialtool.utils.StringUtil;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 串口读取服务：
 * - 文本模式：按 UTF-8 增量解码，避免多字节拆包导致乱码
 * - HEX 模式：按原始字节转十六进制显示
 * - 按行切分，统一使用 '\n' 结尾；支持 \r\n
 * - 每行独立生成日志对象，避免多行被合并成一条
 * - 可选集成高亮器：每次追加后调度一次高亮刷新
 */
public class SerialReadService extends Service<LogText> {
    private static final Logger LOG = LogManager.getLogger(SerialReadService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final SerialPort comPort;
    private final PromptInlineCssTextArea targetTextArea;

    /**
     * 可选高亮调度器：例如你实现的 InlineCssRegexHighlighter，提供 schedule() 即可
     */
    public interface HighlighterScheduler {
        void schedule();
    }

    private final HighlighterScheduler highlighterScheduler; // 可为 null

    /**
     * 一次读取的缓冲大小（字节）
     */
    private static final int READ_BUF_SIZE = 2048;

    /**
     * 是否显示时间戳
     */
    private final SimpleBooleanProperty timeStampDisplayProperty = new SimpleBooleanProperty(false);

    /**
     * 是否 HEX 显示
     */
    private final SimpleBooleanProperty hexDisplayProperty = new SimpleBooleanProperty(false);

    /**
     * 文本模式下，行缓存超时（毫秒）。<= 0 表示不启用超时输出。
     */
    private final int lineTimeoutMs;

    /**
     * 接收字节数统计
     */
    private final AtomicLong recvBytesCount = new AtomicLong(0);

    /**
     * 接收字节数变化回调
     */
    private Consumer<Long> onRecvBytesChanged;

    /**
     * 数据接收回调（用于自动保存等）
     */
    private Consumer<String> onDataReceived;

    /**
     * 原始字节接收回调（用于协议解析、波形绘制等）
     */
    private Consumer<byte[]> onRawBytesReceived;

    /**
     * 是否启用内部文本追加（默认启用，可通过设置false由外部管理）
     */
    private boolean internalAppendEnabled = true;

    /**
     * 是否启用文本解码与分行逻辑。
     */
    private boolean decodeTextEnabled = true;

    public SerialReadService(SerialPort comPort,
                             PromptInlineCssTextArea targetTextArea,
                             BooleanProperty timeStampDisplayProperty,
                             HighlighterScheduler highlighter) {
        this(comPort, targetTextArea, timeStampDisplayProperty, null, highlighter, 0, false);
    }

    public SerialReadService(SerialPort comPort,
                             PromptInlineCssTextArea targetTextArea,
                             BooleanProperty timeStampDisplayProperty,
                             HighlighterScheduler highlighter,
                             boolean showLogType) {
        this(comPort, targetTextArea, timeStampDisplayProperty, null, highlighter, 0, showLogType);
    }

    public SerialReadService(SerialPort comPort,
                             PromptInlineCssTextArea targetTextArea,
                             BooleanProperty timeStampDisplayProperty,
                             BooleanProperty hexDisplayProperty,
                             HighlighterScheduler highlighter) {
        this(comPort, targetTextArea, timeStampDisplayProperty, hexDisplayProperty, highlighter, 0, false);
    }

    public SerialReadService(SerialPort comPort,
                             PromptInlineCssTextArea targetTextArea,
                             BooleanProperty timeStampDisplayProperty,
                             BooleanProperty hexDisplayProperty,
                             int lineTimeoutMs,
                             HighlighterScheduler highlighter) {
        this(comPort, targetTextArea, timeStampDisplayProperty, hexDisplayProperty, highlighter, lineTimeoutMs, false);
    }

    public SerialReadService(SerialPort comPort,
                             PromptInlineCssTextArea targetTextArea,
                             BooleanProperty timeStampDisplayProperty,
                             BooleanProperty hexDisplayProperty,
                             HighlighterScheduler highlighter,
                             boolean showLogType) {
        this(comPort, targetTextArea, timeStampDisplayProperty, hexDisplayProperty, highlighter, 0, showLogType);
    }

    public SerialReadService(SerialPort comPort,
                             PromptInlineCssTextArea targetTextArea,
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

        valueProperty().addListener((observable, oldValue, newValue) -> {
            // Service 结束时 value 可能为 null，避免监听器 NPE
            if (newValue == null) {
                return;
            }
            String logText = newValue.getLogText(timeStampDisplayProperty.get(), showLogType);
            appendText(logText + "\n");
        });
    }

    @Override
    protected Task<LogText> createTask() {
        return new Task<>() {
            @Override
            protected LogText call() {
                // updateValue(...) 只能在 Task 内调用，这里用 Consumer 传给解码流程
                Consumer<String> lineEmitter = line -> updateValue(formatLine(line));

                try (InputStream in = comPort.getInputStream()) {
                    CharsetDecoder decoder = createUtf8Decoder();

                    byte[] rawBuf = new byte[READ_BUF_SIZE];
                    // byteBuf 会保留“未解码完成的尾字节”，跨 read() 继续解码
                    ByteBuffer byteBuf = ByteBuffer.allocate(READ_BUF_SIZE * 2);
                    CharBuffer charBuf = CharBuffer.allocate(READ_BUF_SIZE * 2);

                    // 行缓冲：保证每行单独输出、单独时间戳
                    StringBuilder lineBuf = new StringBuilder();
                    boolean previousHexDisplay = hexDisplayProperty.get();

                    while (!isCancelled()) {
                        int n;
                        try {
                            n = in.read(rawBuf);
                        } catch (SerialPortTimeoutException timeoutException) {
                            // 串口读超时属于正常轮询事件，不中断读取服务
                            flushLineBufferByTimeout(lineBuf, lineEmitter);
                            continue;
                        }

                        if (n < 0) break;       // EOF
                        if (n == 0) {
                            // 读超时：文本模式下输出未换行缓存
                            flushLineBufferByTimeout(lineBuf, lineEmitter);
                            continue;
                        }

                        // 统计接收字节数
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
                                flushTextTail(decoder, byteBuf, charBuf, lineBuf, lineEmitter);
                                decoder = createUtf8Decoder();
                            }
                            lineEmitter.accept(StringUtil.bytesToHexString(rawBuf, n));
                        } else {
                            if (previousHexDisplay) {
                                // 从 HEX 切回文本模式后，重置解码器状态，避免跨模式污染
                                decoder = createUtf8Decoder();
                                byteBuf.clear();
                                charBuf.clear();
                                lineBuf.setLength(0);
                            }

                            byteBuf = ensureWritable(byteBuf, n);
                            byteBuf.put(rawBuf, 0, n);
                            byteBuf.flip();

                            // 增量解码：未消费字节通过 compact() 留给下一次 read
                            decodeBuffer(decoder, byteBuf, charBuf, lineBuf, false, lineEmitter);
                            byteBuf.compact();
                        }

                        previousHexDisplay = hexMode;
                    }

                    // 收尾：文本模式下 flush 解码器内部状态，避免尾部字符丢失
                    if (decodeTextEnabled && !previousHexDisplay) {
                        flushTextTail(decoder, byteBuf, charBuf, lineBuf, lineEmitter);
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

    private void flushLineBufferByTimeout(StringBuilder lineBuf, Consumer<String> lineEmitter) {
        if (lineTimeoutMs <= 0 || lineBuf.isEmpty() || hexDisplayProperty.get()) {
            return;
        }
        lineEmitter.accept(lineBuf.toString());
        lineBuf.setLength(0);
    }

    private void flushTextTail(CharsetDecoder decoder,
                               ByteBuffer byteBuf,
                               CharBuffer charBuf,
                               StringBuilder lineBuf,
                               Consumer<String> lineEmitter) throws CharacterCodingException {
        byteBuf.flip();
        decodeBuffer(decoder, byteBuf, charBuf, lineBuf, true, lineEmitter);

        if (!lineBuf.isEmpty()) {
            lineEmitter.accept(lineBuf.toString());
            lineBuf.setLength(0);
        }

        byteBuf.clear();
        charBuf.clear();
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

    private void appendText(String batch) {
        // 调用数据接收回调（用于自动保存等）
        if (onDataReceived != null) {
            onDataReceived.accept(batch);
        }
        // 内部文本追加
        if (internalAppendEnabled) {
            targetTextArea.appendText(batch);
            if (highlighterScheduler != null) {
                highlighterScheduler.schedule();
            }
        }
    }

    /**
     * 将解码后的字符流按行切分并逐行投递。
     * 支持 \n 与 \r\n；统一以 '\n' 结尾。
     */
    private void feedChars(CharBuffer chars, StringBuilder lineBuf, Consumer<String> lineEmitter) {
        while (chars.hasRemaining()) {
            char c = chars.get();
            if (c == '\n') {
                // 处理 \r\n：去掉行尾 \r
                int end = lineBuf.length();
                if (end > 0 && lineBuf.charAt(end - 1) == '\r') {
                    lineBuf.setLength(end - 1);
                }
                lineEmitter.accept(lineBuf.toString());
                lineBuf.setLength(0);
            } else {
                lineBuf.append(c);
            }
        }
    }

    /**
     * 执行一次解码流程；endOfInput=true 时会额外 flush 解码器。
     */
    private void decodeBuffer(CharsetDecoder decoder,
                              ByteBuffer byteBuf,
                              CharBuffer charBuf,
                              StringBuilder lineBuf,
                              boolean endOfInput,
                              Consumer<String> lineEmitter) throws CharacterCodingException {
        while (true) {
            CoderResult cr = decoder.decode(byteBuf, charBuf, endOfInput);
            charBuf.flip();
            if (charBuf.hasRemaining()) {
                feedChars(charBuf, lineBuf, lineEmitter);
            }
            charBuf.clear();

            if (cr.isError()) cr.throwException();
            if (cr.isOverflow()) continue;
            if (cr.isUnderflow()) break;
        }

        if (!endOfInput) {
            return;
        }

        while (true) {
            CoderResult cr = decoder.flush(charBuf);
            charBuf.flip();
            if (charBuf.hasRemaining()) {
                feedChars(charBuf, lineBuf, lineEmitter);
            }
            charBuf.clear();

            if (cr.isError()) cr.throwException();
            if (cr.isOverflow()) continue;
            if (cr.isUnderflow()) break;
        }
    }

    /**
     * 确保 byteBuf 有足够空间写入新字节，不足时按 2 倍扩容。
     */
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

    @Override
    public void start() {
        super.start();
        LOG.info("串口 [" + comPort + "] 读取服务开启");
    }

    @Override
    public boolean cancel() {
        boolean cancelled = super.cancel();
        // 主动关闭串口，确保阻塞 read() 能尽快退出
        tryClosePort();
        if (cancelled) {
            LOG.info("串口 [{}] 读取服务关闭", comPort);
            return true;
        }
        LOG.error("串口 [{}] 读取服务失败", comPort);
        return false;
    }

    /**
     * 设置接收字节数变化回调
     */
    public void setOnRecvBytesChanged(Consumer<Long> callback) {
        this.onRecvBytesChanged = callback;
    }

    /**
     * 设置数据接收回调
     */
    public void setOnDataReceived(Consumer<String> callback) {
        this.onDataReceived = callback;
    }

    /**
     * 设置原始字节接收回调
     */
    public void setOnRawBytesReceived(Consumer<byte[]> callback) {
        this.onRawBytesReceived = callback;
    }

    /**
     * 设置是否启用内部文本追加
     */
    public void setInternalAppendEnabled(boolean enabled) {
        this.internalAppendEnabled = enabled;
    }

    /**
     * 设置是否启用文本解码。
     */
    public void setDecodeTextEnabled(boolean enabled) {
        this.decodeTextEnabled = enabled;
    }

    /**
     * 获取接收字节数
     */
    public long getRecvBytesCount() {
        return recvBytesCount.get();
    }

    /**
     * 重置接收字节数
     */
    public void resetRecvBytesCount() {
        recvBytesCount.set(0);
        if (onRecvBytesChanged != null) {
            onRecvBytesChanged.accept(0L);
        }
    }

    /**
     * 根据是否带时间戳，格式化一行。
     */
    private LogText formatLine(String raw) {
        String ts = LocalDateTime.now().format(TIME_FORMATTER);
        return new LogText(ts, raw, LogType.RECEIVE);
    }
}
