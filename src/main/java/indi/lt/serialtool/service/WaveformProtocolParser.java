package indi.lt.serialtool.service;

import indi.lt.serialtool.data.WaveformDataType;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 协议: 0x03 0xFC xx 0xFC 0x03
 */
public class WaveformProtocolParser {
    private static final byte FRAME_HEAD_1 = 0x03;
    private static final byte FRAME_HEAD_2 = (byte) 0xFC;
    private static final byte FRAME_TAIL_1 = (byte) 0xFC;
    private static final byte FRAME_TAIL_2 = 0x03;

    private byte[] buffer = new byte[4096];
    private int bufferLength;
    private WaveformDataType dataType;
    private final Consumer<FrameData> frameConsumer;

    public WaveformProtocolParser(WaveformDataType dataType, Consumer<FrameData> frameConsumer) {
        this.dataType = Objects.requireNonNull(dataType);
        this.frameConsumer = Objects.requireNonNull(frameConsumer);
    }

    public synchronized void setDataType(WaveformDataType dataType) {
        this.dataType = Objects.requireNonNull(dataType);
        reset();
    }

    public synchronized void reset() {
        bufferLength = 0;
    }

    public synchronized void accept(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }

        ensureCapacity(bufferLength + bytes.length);
        System.arraycopy(bytes, 0, buffer, bufferLength, bytes.length);
        bufferLength += bytes.length;
        processFrames();
    }

    private void processFrames() {
        while (bufferLength >= 4) {
            int frameStart = findFrameHead();
            if (frameStart < 0) {
                keepPossibleHeadTail();
                return;
            }

            if (frameStart > 0) {
                discardBefore(frameStart);
            }

            int frameEnd = findFrameTail(2);
            if (frameEnd < 0) {
                return;
            }

            byte[] payload = Arrays.copyOfRange(buffer, 2, frameEnd);
            emitFrame(payload);
            discardBefore(frameEnd + 2);
        }
    }

    private int findFrameHead() {
        for (int i = 0; i <= bufferLength - 2; i++) {
            if (buffer[i] == FRAME_HEAD_1 && buffer[i + 1] == FRAME_HEAD_2) {
                return i;
            }
        }
        return -1;
    }

    private int findFrameTail(int fromIndex) {
        for (int i = fromIndex; i <= bufferLength - 2; i++) {
            if (buffer[i] == FRAME_TAIL_1 && buffer[i + 1] == FRAME_TAIL_2) {
                return i;
            }
        }
        return -1;
    }

    private void emitFrame(byte[] payload) {
        int valueSize = dataType.getByteLength();
        int rawValueCount = payload.length / valueSize;
        if (rawValueCount <= 0) {
            return;
        }

        int emitCount = Math.min(rawValueCount, 8);
        double[] values = new double[emitCount];
        for (int i = 0; i < emitCount; i++) {
            values[i] = dataType.read(payload, i * valueSize);
        }

        boolean truncated = rawValueCount > emitCount;
        boolean remainderIgnored = payload.length % valueSize != 0;
        frameConsumer.accept(new FrameData(values, rawValueCount, payload.length, truncated, remainderIgnored));
    }

    private void keepPossibleHeadTail() {
        if (bufferLength <= 0) {
            return;
        }
        if (buffer[bufferLength - 1] == FRAME_HEAD_1) {
            buffer[0] = FRAME_HEAD_1;
            bufferLength = 1;
            return;
        }
        bufferLength = 0;
    }

    private void discardBefore(int startIndex) {
        if (startIndex <= 0) {
            return;
        }
        int remain = bufferLength - startIndex;
        if (remain > 0) {
            System.arraycopy(buffer, startIndex, buffer, 0, remain);
        }
        bufferLength = Math.max(remain, 0);
    }

    private void ensureCapacity(int requiredLength) {
        if (requiredLength <= buffer.length) {
            return;
        }
        int newLength = buffer.length;
        while (newLength < requiredLength) {
            newLength <<= 1;
        }
        buffer = Arrays.copyOf(buffer, newLength);
    }

    public static final class FrameData {
        private final double[] values;
        private final int rawValueCount;
        private final int payloadLength;
        private final boolean truncated;
        private final boolean remainderIgnored;

        public FrameData(double[] values, int rawValueCount, int payloadLength, boolean truncated, boolean remainderIgnored) {
            this.values = values;
            this.rawValueCount = rawValueCount;
            this.payloadLength = payloadLength;
            this.truncated = truncated;
            this.remainderIgnored = remainderIgnored;
        }

        public double[] getValues() {
            return values;
        }

        public int getRawValueCount() {
            return rawValueCount;
        }

        public int getPayloadLength() {
            return payloadLength;
        }

        public boolean isTruncated() {
            return truncated;
        }

        public boolean isRemainderIgnored() {
            return remainderIgnored;
        }
    }
}
