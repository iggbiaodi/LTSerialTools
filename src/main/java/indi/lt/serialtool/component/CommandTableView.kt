package indi.lt.serialtool.component

import atlantafx.base.theme.Styles
import github.nonoas.jfx.flat.ui.concurrent.TaskHandler
import indi.lt.serialtool.component.CommandTableView.CommandItem
import indi.lt.serialtool.data.CommandRepository
import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger


/**
 * @author Nonoas
 * @date 2025/9/6
 * @since
 */
class CommandTableView : TableView<CommandItem?>(FXCollections.observableArrayList()) {
    private val logger: Logger = LogManager.getLogger(CommandTableView::class.java)

    /** 发送指令回调，由外部注入发送逻辑 */
    var onSendCommand: ((CommandItem) -> Unit)? = null


    init {
        columnResizePolicy = CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS

        // 列：备注
        val remarkCol = TableColumn<CommandItem?, String>("备注")
        remarkCol.cellValueFactory = PropertyValueFactory("remark")
        remarkCol.prefWidth = 150.0

        // 列：指令
        val commandCol = TableColumn<CommandItem?, String>("指令")
        commandCol.cellValueFactory = PropertyValueFactory("command")
        commandCol.prefWidth = 200.0

        // 列：操作
        val actionCol = TableColumn<CommandItem?, CommandItem?>("操作")
        actionCol.isResizable = false
        actionCol.setCellValueFactory { param: TableColumn.CellDataFeatures<CommandItem?, CommandItem?> ->
            ReadOnlyObjectWrapper(
                param.value
            )
        }
        actionCol.setCellFactory { col: TableColumn<CommandItem?, CommandItem?>? ->
            object : TableCell<CommandItem?, CommandItem?>() {
                private val typeLabel = Label()
                private val sendBtn = Button("发送")
                private val deleteBtn = Button("删除")
                private val box = HBox(5.0, typeLabel, sendBtn, deleteBtn)

                init {
                    HBox.setHgrow(typeLabel, Priority.ALWAYS)
                    typeLabel.maxWidth = Double.MAX_VALUE

                    deleteBtn.minWidth = 50.0
                    deleteBtn.styleClass.add(Styles.DANGER)
                    box.alignment = Pos.CENTER_LEFT
                    sendBtn.onAction = EventHandler {
                        val item = item
                        if (item != null) {
                            onSendCommand?.invoke(item)
                        }
                    }
                    sendBtn.styleClass.add(Styles.ACCENT)

                    deleteBtn.onAction = EventHandler {
                        val item = item
                        if (item != null) {
                            tableView.items.remove(item)
                            TaskHandler.backRun { CommandRepository.INSTANCE.remove(item) }
                        }
                    }
                }

                override fun updateItem(item: CommandItem?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        graphic = null
                    } else {
                        typeLabel.text = item.getCommandType()
                        graphic = box
                    }
                }
            }
        }
        actionCol.prefWidth = 155.0

        // 列：定时发送
        val scheduleCol = TableColumn<CommandItem?, CommandItem?>("定时发送")
        scheduleCol.setCellValueFactory { param: TableColumn.CellDataFeatures<CommandItem?, CommandItem?> ->
            ReadOnlyObjectWrapper(
                param.value
            )
        }
        scheduleCol.setCellFactory {
            object : TableCell<CommandItem?, CommandItem?>() {
                private val intervalField = TextField()
                private val unitLabel = Label("ms")
                private val enableCheck = CheckBox()
                private val box = HBox(5.0, intervalField, unitLabel, enableCheck)

                private var checkListener: javafx.beans.value.ChangeListener<Boolean>? = null
                private var intervalListener: javafx.beans.value.ChangeListener<String>? = null

                init {
                    unitLabel.minWidth = USE_PREF_SIZE
                    box.alignment = Pos.CENTER_LEFT
                    intervalField.prefWidth = 60.0
                    // 只允许输入正整数
                    intervalField.textProperty()
                        .addListener { _: ObservableValue<out String>?, oldV: String?, newV: String ->
                            if (!newV.matches("\\d*".toRegex())) {
                                intervalField.text = oldV
                            } else {
                                item?.setInterval(newV.toInt())
                            }
                        }
                }

                override fun updateItem(item: CommandItem?, empty: Boolean) {
                    // 先移除旧 listener，防止 TableView Cell 复用时 listener 堆积
                    checkListener?.let { enableCheck.selectedProperty().removeListener(it) }
                    intervalListener?.let { intervalField.textProperty().removeListener(it) }
                    checkListener = null
                    intervalListener = null

                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        graphic = null
                    } else {
                        // 先设值，再绑 listener，避免设值时触发 listener 扰乱其他 item
                        intervalField.text = item.getInterval().toString()
                        enableCheck.isSelected = item.isScheduled()

                        checkListener = javafx.beans.value.ChangeListener { _, _, nv ->
                            item.setScheduled(nv)
                        }
                        intervalListener = javafx.beans.value.ChangeListener { _, _, nv ->
                            if (nv.isNotEmpty()) {
                                item.setInterval(nv.toInt())
                            }
                        }
                        enableCheck.selectedProperty().addListener(checkListener)
                        intervalField.textProperty().addListener(intervalListener)

                        graphic = box
                    }
                }
            }
        }
        scheduleCol.prefWidth = 200.0

        columns.addAll(remarkCol, commandCol, actionCol, scheduleCol)
    }

    // 数据模型
    class CommandItem(id: String, remark: String, command: String, commandType: String) {
        private val id: StringProperty = SimpleStringProperty()

        private val remark: StringProperty = SimpleStringProperty()
        private val command: StringProperty = SimpleStringProperty()
        private val commandType: StringProperty = SimpleStringProperty("类型A")
        private val interval: IntegerProperty = SimpleIntegerProperty(1000)
        private val scheduled: BooleanProperty = SimpleBooleanProperty(false)

        init {
            this.setId(id)
            this.remark.set(remark)
            this.command.set(command)
            this.commandType.set(commandType)
        }

        fun getRemark(): String {
            return remark.get()
        }

        fun setRemark(v: String) {
            remark.set(v)
        }

        fun remarkProperty(): StringProperty {
            return remark
        }

        fun getCommand(): String {
            return command.get()
        }

        fun setCommand(v: String) {
            command.set(v)
        }

        fun commandProperty(): StringProperty {
            return command
        }

        fun getCommandType(): String {
            return commandType.get()
        }

        fun setCommandType(v: String) {
            commandType.set(v)
        }

        fun commandTypeProperty(): StringProperty {
            return commandType
        }

        fun getInterval(): Int {
            return interval.get()
        }

        fun setInterval(v: Int) {
            interval.set(v)
        }

        fun intervalProperty(): IntegerProperty {
            return interval
        }

        fun isScheduled(): Boolean {
            return scheduled.get()
        }

        fun setScheduled(v: Boolean) {
            scheduled.set(v)
        }

        fun scheduledProperty(): BooleanProperty {
            return scheduled
        }

        fun getId(): String {
            return id.get()
        }

        fun idProperty(): StringProperty {
            return id
        }

        fun setId(id: String) {
            this.id.set(id)
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun equals(obj: Any?): Boolean {
            if (obj !is CommandItem) {
                return false
            }
            return getId() == obj.getId()
        }
    }
}
