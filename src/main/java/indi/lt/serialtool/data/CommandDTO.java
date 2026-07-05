package indi.lt.serialtool.data;

import indi.lt.serialtool.component.CommandTableView;

// 普通 POJO，用于 JSON 序列化/反序列化
public class CommandDTO {
    String remark;
    String command;
    String commandType;
    int interval;
    boolean scheduled;

    private String id;

    CommandDTO() {
    }

    CommandDTO(CommandTableView.CommandItem item) {
        this.remark = item.getRemark();
        this.command = item.getCommand();
        this.commandType = item.getCommandType();
        this.interval = item.getInterval();
        this.scheduled = item.isScheduled();
        this.id = item.getId();
    }

    CommandTableView.CommandItem toCommandItem() {
        CommandTableView.CommandItem item = new CommandTableView.CommandItem(id, remark, command, commandType);
        item.setInterval(interval);
        item.setScheduled(scheduled);
        return item;
    }
}
