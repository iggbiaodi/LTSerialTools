package indi.lt.serialtool.data;

import com.fazecast.jSerialComm.SerialPort;

import java.io.Serializable;

/**
 * 串口参数设置数据类
 * 用于存储波特率、数据位、停止位、校验位、流控等参数
 */
public class SerialPortSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    // 默认参数值
    public static final int DEFAULT_BAUD_RATE = 1500000;
    public static final int DEFAULT_DATA_BITS = 8;
    public static final int DEFAULT_STOP_BITS = SerialPort.ONE_STOP_BIT;
    public static final int DEFAULT_PARITY = SerialPort.NO_PARITY;
    public static final int DEFAULT_FLOW_CONTROL = SerialPort.FLOW_CONTROL_DISABLED;

    // 波特率
    private int baudRate;

    // 数据位: 5, 6, 7, 8
    private int dataBits;

    // 停止位: SerialPort.ONE_STOP_BIT, ONE_POINT_FIVE_STOP_BITS, TWO_STOP_BITS
    private int stopBits;

    // 校验位: SerialPort.NO_PARITY, ODD_PARITY, EVEN_PARITY, MARK_PARITY, SPACE_PARITY
    private int parity;

    // 流控: SerialPort.FLOW_CONTROL_DISABLED, FLOW_CONTROL_RTS_ENABLED, FLOW_CONTROL_CTS_ENABLED,
    //       FLOW_CONTROL_DSR_ENABLED, FLOW_CONTROL_DTR_ENABLED, FLOW_CONTROL_XONXOFF_IN_ENABLED, FLOW_CONTROL_XONXOFF_OUT_ENABLED
    private int flowControl;

    public SerialPortSettings() {
        this.baudRate = DEFAULT_BAUD_RATE;
        this.dataBits = DEFAULT_DATA_BITS;
        this.stopBits = DEFAULT_STOP_BITS;
        this.parity = DEFAULT_PARITY;
        this.flowControl = DEFAULT_FLOW_CONTROL;
    }

    public SerialPortSettings(int baudRate, int dataBits, int stopBits, int parity, int flowControl) {
        this.baudRate = baudRate;
        this.dataBits = dataBits;
        this.stopBits = stopBits;
        this.parity = parity;
        this.flowControl = flowControl;
    }

    public int getBaudRate() {
        return baudRate;
    }

    public void setBaudRate(int baudRate) {
        this.baudRate = baudRate;
    }

    public int getDataBits() {
        return dataBits;
    }

    public void setDataBits(int dataBits) {
        this.dataBits = dataBits;
    }

    public int getStopBits() {
        return stopBits;
    }

    public void setStopBits(int stopBits) {
        this.stopBits = stopBits;
    }

    public int getParity() {
        return parity;
    }

    public void setParity(int parity) {
        this.parity = parity;
    }

    public int getFlowControl() {
        return flowControl;
    }

    public void setFlowControl(int flowControl) {
        this.flowControl = flowControl;
    }

    /**
     * 获取停止位的显示文本
     */
    public static String getStopBitsText(int stopBits) {
        switch (stopBits) {
            case SerialPort.ONE_STOP_BIT:
                return "1";
            case SerialPort.ONE_POINT_FIVE_STOP_BITS:
                return "1.5";
            case SerialPort.TWO_STOP_BITS:
                return "2";
            default:
                return "1";
        }
    }

    /**
     * 从显示文本获取停止位值
     */
    public static int getStopBitsFromText(String text) {
        switch (text) {
            case "1":
                return SerialPort.ONE_STOP_BIT;
            case "1.5":
                return SerialPort.ONE_POINT_FIVE_STOP_BITS;
            case "2":
                return SerialPort.TWO_STOP_BITS;
            default:
                return SerialPort.ONE_STOP_BIT;
        }
    }

    /**
     * 获取校验位的显示文本
     */
    public static String getParityText(int parity) {
        switch (parity) {
            case SerialPort.NO_PARITY:
                return "None";
            case SerialPort.ODD_PARITY:
                return "Odd";
            case SerialPort.EVEN_PARITY:
                return "Even";
            case SerialPort.MARK_PARITY:
                return "Mark";
            case SerialPort.SPACE_PARITY:
                return "Space";
            default:
                return "None";
        }
    }

    /**
     * 从显示文本获取校验位值
     */
    public static int getParityFromText(String text) {
        switch (text) {
            case "None":
                return SerialPort.NO_PARITY;
            case "Odd":
                return SerialPort.ODD_PARITY;
            case "Even":
                return SerialPort.EVEN_PARITY;
            case "Mark":
                return SerialPort.MARK_PARITY;
            case "Space":
                return SerialPort.SPACE_PARITY;
            default:
                return SerialPort.NO_PARITY;
        }
    }

    /**
     * 获取流控的显示文本
     */
    public static String getFlowControlText(int flowControl) {
        switch (flowControl) {
            case SerialPort.FLOW_CONTROL_DISABLED:
                return "None";
            case SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED:
                return "RTS/CTS";
            case SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED:
                return "XON/XOFF";
            default:
                // 检查单独的标志位
                if ((flowControl & SerialPort.FLOW_CONTROL_RTS_ENABLED) != 0 ||
                    (flowControl & SerialPort.FLOW_CONTROL_CTS_ENABLED) != 0) {
                    return "RTS/CTS";
                }
                if ((flowControl & SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED) != 0 ||
                    (flowControl & SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED) != 0) {
                    return "XON/XOFF";
                }
                return "None";
        }
    }

    /**
     * 从显示文本获取流控值
     */
    public static int getFlowControlFromText(String text) {
        switch (text) {
            case "None":
                return SerialPort.FLOW_CONTROL_DISABLED;
            case "RTS/CTS":
                return SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED;
            case "XON/XOFF":
                return SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED;
            default:
                return SerialPort.FLOW_CONTROL_DISABLED;
        }
    }

    @Override
    public String toString() {
        return "SerialPortSettings{" +
                "baudRate=" + baudRate +
                ", dataBits=" + dataBits +
                ", stopBits=" + getStopBitsText(stopBits) +
                ", parity=" + getParityText(parity) +
                ", flowControl=" + getFlowControlText(flowControl) +
                '}';
    }

    /**
     * 创建一个默认设置的副本
     */
    public static SerialPortSettings createDefault() {
        return new SerialPortSettings(
                DEFAULT_BAUD_RATE,
                DEFAULT_DATA_BITS,
                DEFAULT_STOP_BITS,
                DEFAULT_PARITY,
                DEFAULT_FLOW_CONTROL
        );
    }
}
