package indi.lt.serialtool.service;


import com.fazecast.jSerialComm.SerialPort;
import indi.lt.serialtool.component.CommandTableView;
import indi.lt.serialtool.component.MyStyleClassedTextArea;
import indi.lt.serialtool.constant.MessageDirection;
import indi.lt.serialtool.data.BufferedDisplayLine;
import indi.lt.serialtool.utils.StringUtil;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.control.CheckBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static indi.lt.serialtool.data.BufferedDisplayLine.DataType.HEX;
import static indi.lt.serialtool.data.BufferedDisplayLine.DataType.TXT;


/**
 * @author Nonoas
 * @date 2025/9/23
 * @since
 */
public class SerialSenderService extends Service<BufferedDisplayLine> {

    private final Logger LOG = LogManager.getLogger(SerialSenderService.class);

    private final List<CommandTableView.CommandItem> commands;

    private final SerialPort serialPort;

    private final MyStyleClassedTextArea taRecvArea;
    private final CheckBox cbHexDisplay;
    private final CheckBox cbTimeStampDisplay;
    private final CheckBox lineBreak;

    private Consumer<Long> onSentBytesChanged;

    public SerialSenderService(List<CommandTableView.CommandItem> commands,
                               SerialPort selectedPort, MyStyleClassedTextArea taRecvArea, CheckBox cbHexDisplay,
                               CheckBox cbTimeStampDisplay, CheckBox lineBreak) {
        this.commands = commands;
        this.serialPort = selectedPort;
        this.taRecvArea = taRecvArea;
        this.cbHexDisplay = cbHexDisplay;
        this.cbTimeStampDisplay = cbTimeStampDisplay;
        this.lineBreak = lineBreak;

        valueProperty().addListener((observableValue, unused, newVal) -> {
            if (null != newVal) {
                taRecvArea.appendLogLine(newVal, true, true);
            }
        });
    }

    @Override
    protected Task<BufferedDisplayLine> createTask() {
        return new Task<>() {
            @Override
            protected BufferedDisplayLine call() throws Exception {
                int commandIndex = 0;
                while (true) {
                    // 检查任务是否被取消
                    if (isCancelled()) {
                        LOG.info("任务已取消。");
                        break;
                    }

                    // 如果循环结束，可以重新开始或停止
                    if (commandIndex >= commands.size()) {
                        commandIndex = 0; // 重新开始循环
                    }

                    CommandTableView.CommandItem currentCommand = commands.get(commandIndex);

                    // 1. 更新UI（线程安全）
                    updateMessage("正在发送指令: " + currentCommand.getCommand());

                    // 2. 模拟耗时的串口发送
                    Thread.sleep(currentCommand.getInterval());

                    try {
                        String command = currentCommand.getCommand().trim();
                        boolean isHexCommand = "HEX".equalsIgnoreCase(currentCommand.getCommandType());

                        byte[] data;
                        String textToSend = command;
                        if (isHexCommand) {
                            data = StringUtil.hexStringToBytes(command);
                            if (lineBreak.isSelected()) {
                                data = Arrays.copyOf(data, data.length + 1);
                                data[data.length - 1] = (byte) '\n';
                            }
                        } else {
                            textToSend = lineBreak.isSelected() ? command + "\n" : command;
                            data = textToSend.getBytes(StandardCharsets.UTF_8);
                        }

                        int written = serialPort.writeBytes(data, data.length);
                        if (written > 0) {
                            if (onSentBytesChanged != null) {
                                onSentBytesChanged.accept((long) written);
                            }
                        }
                        if (written != data.length) {
                            LOG.warn("定时发送超时: {}/{} 字节, cmd={}", written, data.length, currentCommand.getCommand());
                        } else {
                            LOG.info("已发送: {}", currentCommand.getCommand());
                        }

                        String logBody = cbHexDisplay.isSelected() ? StringUtil.bytesToHexString(data) : textToSend;
                        BufferedDisplayLine.DataType dataType;
                        ;
                        if (isHexCommand) {
                            dataType = HEX;
                        } else {
                            dataType = TXT;
                        }

                        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                        BufferedDisplayLine displayLine = BufferedDisplayLine.of(0L, ts, dataType, MessageDirection.SEND, logBody);
                        updateValue(displayLine);
                    } catch (Exception e) {
                        LOG.error("指令[{}]发送失败", currentCommand.getCommand(), e);
                    }
                    // 3. 准备下一个指令
                    commandIndex++;
                }
                return null;
            }
        };
    }

    public void setOnSentBytesChanged(Consumer<Long> callback) {
        this.onSentBytesChanged = callback;
    }

}
