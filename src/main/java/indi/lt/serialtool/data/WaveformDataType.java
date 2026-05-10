package indi.lt.serialtool.data;

/**
 * 波形数据类型。
 */
public enum WaveformDataType {
    SIGNED_8("有符号8位", 1) {
        @Override
        public double read(byte[] payload, int offset) {
            return payload[offset];
        }
    },
    UNSIGNED_8("无符号8位", 1) {
        @Override
        public double read(byte[] payload, int offset) {
            return payload[offset] & 0xFF;
        }
    },
    SIGNED_16("有符号16位", 2) {
        @Override
        public double read(byte[] payload, int offset) {
            return (short) (((payload[offset] & 0xFF) << 8) | (payload[offset + 1] & 0xFF));
        }
    },
    UNSIGNED_16("无符号16位", 2) {
        @Override
        public double read(byte[] payload, int offset) {
            return ((payload[offset] & 0xFF) << 8) | (payload[offset + 1] & 0xFF);
        }
    },
    SIGNED_32("有符号32位", 4) {
        @Override
        public double read(byte[] payload, int offset) {
            return ((payload[offset] & 0xFF) << 24)
                    | ((payload[offset + 1] & 0xFF) << 16)
                    | ((payload[offset + 2] & 0xFF) << 8)
                    | (payload[offset + 3] & 0xFF);
        }
    },
    UNSIGNED_32("无符号32位", 4) {
        @Override
        public double read(byte[] payload, int offset) {
            return ((long) (payload[offset] & 0xFF) << 24)
                    | ((long) (payload[offset + 1] & 0xFF) << 16)
                    | ((long) (payload[offset + 2] & 0xFF) << 8)
                    | (payload[offset + 3] & 0xFFL);
        }
    };

    private final String displayName;
    private final int byteLength;

    WaveformDataType(String displayName, int byteLength) {
        this.displayName = displayName;
        this.byteLength = byteLength;
    }

    public int getByteLength() {
        return byteLength;
    }

    public abstract double read(byte[] payload, int offset);

    @Override
    public String toString() {
        return displayName;
    }
}
