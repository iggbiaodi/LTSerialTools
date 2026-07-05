package indi.lt.serialtool.view

import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.transformation.FilteredList
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.scene.control.Button
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.TextField
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import java.util.*
import java.util.function.Predicate

class AsciiTablePane : VBox() {
    class AsciiEntry(code: Int, character: String?, description: String?) {
        private val code = SimpleIntegerProperty(code)
        private val character = SimpleStringProperty(character)
        private val hex = SimpleStringProperty(String.format("0x%02X", code))
        private val oct = SimpleStringProperty(String.format("0%03o", code))
        private val binary = SimpleStringProperty(String.format("%8s", Integer.toBinaryString(code)).replace(' ', '0'))
        private val description = SimpleStringProperty(description)

        fun getCode(): Int {
            return code.get()
        }

        fun getCharacter(): String {
            return character.get()
        }

        fun getHex(): String {
            return hex.get()
        }

        fun getOct(): String {
            return oct.get()
        }

        fun getBinary(): String {
            return binary.get()
        }

        fun getDescription(): String {
            return description.get()
        }
    }

    val tableView: TableView<AsciiEntry>
    private val searchField: TextField
    private val clearBtn: Button

    init {
        spacing = 8.0
        padding = Insets(10.0)

        val fullList = buildAsciiList()
        val filtered = FilteredList(fullList) { p: AsciiEntry? -> true }

        // --- 表格 ---
        tableView = TableView(filtered)
        tableView.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY

        val colDec = TableColumn<AsciiEntry, Int>("十进制")
        colDec.cellValueFactory = PropertyValueFactory("code")
        colDec.maxWidth = 80.0

        val colChar = TableColumn<AsciiEntry, String>("字符")
        colChar.cellValueFactory = PropertyValueFactory("character")
        colChar.minWidth = 80.0

        val colHex = TableColumn<AsciiEntry, String>("十六进制")
        colHex.cellValueFactory = PropertyValueFactory("hex")
        colHex.maxWidth = 90.0

        val colOct = TableColumn<AsciiEntry, String>("八进制")
        colOct.cellValueFactory = PropertyValueFactory("oct")
        colOct.maxWidth = 90.0

        val colBin = TableColumn<AsciiEntry, String>("二进制")
        colBin.cellValueFactory = PropertyValueFactory("binary")
        colBin.minWidth = 120.0

        val colDesc = TableColumn<AsciiEntry, String>("备注")
        colDesc.cellValueFactory = PropertyValueFactory("description")
        colDesc.minWidth = 150.0

        tableView.columns.addAll(colDec, colChar, colHex, colOct, colBin, colDesc)


        // --- 搜索控件 ---
        searchField = TextField()
        searchField.promptText = "输入字符或编码 (如 A, 65, 0x41, 41h)"
        clearBtn = Button("清除")
        val controls = HBox(8.0, searchField, clearBtn)

        children.addAll(controls, tableView)

        // --- 搜索逻辑 ---
        val doFilter = Runnable {
            val raw = searchField.text
            if (raw == null || raw.trim { it <= ' ' }.isEmpty()) {
                filtered.predicate = Predicate { p: AsciiEntry? -> true }
                return@Runnable
            }
            val s = raw.trim { it <= ' ' }
            try {
                if (s.length == 1) {
                    val c = s[0]
                    filtered.predicate = Predicate { entry: AsciiEntry -> entry.getCode() == c.code }
                    return@Runnable
                }
                if (s.matches("^0x[0-9a-fA-F]+$".toRegex())) {
                    val `val` = s.substring(2).toInt(16)
                    filtered.predicate = Predicate { entry: AsciiEntry -> entry.getCode() == `val` }
                    return@Runnable
                }
                if (s.matches("^[0-9]+$".toRegex())) {
                    val `val` = s.toInt()
                    filtered.predicate = Predicate { entry: AsciiEntry -> entry.getCode() == `val` }
                    return@Runnable
                }
                if (s.matches("^[0-9a-fA-F]+[hH]$".toRegex())) {
                    val `val` = s.substring(0, s.length - 1).toInt(16)
                    filtered.predicate = Predicate { entry: AsciiEntry -> entry.getCode() == `val` }
                    return@Runnable
                }

                val low = s.lowercase(Locale.getDefault())
                filtered.setPredicate { entry: AsciiEntry ->
                    entry.getCharacter().lowercase(Locale.getDefault()).contains(low) ||
                            entry.getHex().lowercase(Locale.getDefault()).contains(low) ||
                            entry.getOct().lowercase(Locale.getDefault()).contains(low) ||
                            entry.getDescription().lowercase(Locale.getDefault()).contains(low) ||
                            entry.getCode().toString().contains(low)
                }
            } catch (ex: NumberFormatException) {
                val low = s.lowercase(Locale.getDefault())
                filtered.setPredicate { entry: AsciiEntry ->
                    entry.getCharacter().lowercase(Locale.getDefault()).contains(low) ||
                            entry.getHex().lowercase(Locale.getDefault()).contains(low) ||
                            entry.getOct().lowercase(Locale.getDefault()).contains(low) ||
                            entry.getDescription().lowercase(Locale.getDefault()).contains(low) ||
                            entry.getCode().toString().contains(low)
                }
            }
        }

        searchField.onKeyPressed = EventHandler { ev: KeyEvent ->
            if (ev.code == KeyCode.ENTER) doFilter.run()
        }
        searchField.textProperty()
            .addListener { _: ObservableValue<out String?>?, _: String?, _: String? -> doFilter.run() }
        clearBtn.onAction = EventHandler {
            searchField.clear()
            filtered.setPredicate { true }
        }
    }

    private fun buildAsciiList(): ObservableList<AsciiEntry> {
        val list = FXCollections.observableArrayList<AsciiEntry>()

        // 控制字符说明
        val controlChars = arrayOf(
            arrayOf("NUL", "Null"),
            arrayOf("SOH", "Start of Header"),
            arrayOf("STX", "Start of Text"),
            arrayOf("ETX", "End of Text"),
            arrayOf("EOT", "End of Transmission"),
            arrayOf("ENQ", "Enquiry"),
            arrayOf("ACK", "Acknowledge"),
            arrayOf("BEL", "Bell"),
            arrayOf("BS", "Backspace"),
            arrayOf("TAB", "Horizontal Tab"),
            arrayOf("LF", "Line Feed"),
            arrayOf("VT", "Vertical Tab"),
            arrayOf("FF", "Form Feed"),
            arrayOf("CR", "Carriage Return"),
            arrayOf("SO", "Shift Out"),
            arrayOf("SI", "Shift In"),
            arrayOf("DLE", "Data Link Escape"),
            arrayOf("DC1", "Device Control 1"),
            arrayOf("DC2", "Device Control 2"),
            arrayOf("DC3", "Device Control 3"),
            arrayOf("DC4", "Device Control 4"),
            arrayOf("NAK", "Negative Ack"),
            arrayOf("SYN", "Synchronous Idle"),
            arrayOf("ETB", "End of Block"),
            arrayOf("CAN", "Cancel"),
            arrayOf("EM", "End of Medium"),
            arrayOf("SUB", "Substitute"),
            arrayOf("ESC", "Escape"),
            arrayOf("FS", "File Separator"),
            arrayOf("GS", "Group Separator"),
            arrayOf("RS", "Record Separator"),
            arrayOf("US", "Unit Separator"),
            arrayOf("DEL", "Delete")
        )

        // 0–31 控制符
        for (i in 0..31) {
            list.add(AsciiEntry(i, controlChars[i][0], controlChars[i][1]))
        }

        // 32 空格
        list.add(AsciiEntry(32, "SP", "Space (空格)"))

        // 33–126 可打印字符
        for (i in 33..126) {
            val c = i.toChar()
            val remark = if (Character.isUpperCase(c)) {
                "大写字母 $c"
            } else if (Character.isLowerCase(c)) {
                "小写字母 $c"
            } else if (Character.isDigit(c)) {
                "数字 $c"
            } else {
                // 标点符号描述
                when (c) {
                    '!' -> "感叹号"
                    '"' -> "双引号"
                    '#' -> "井号 / 井字符"
                    '$' -> "美元符号"
                    '%' -> "百分号"
                    '&' -> "和号 (Ampersand)"
                    '\'' -> "单引号"
                    '(' -> "左括号"
                    ')' -> "右括号"
                    '*' -> "星号"
                    '+' -> "加号"
                    ',' -> "逗号"
                    '-' -> "减号 / 连字符"
                    '.' -> "句号 / 点"
                    '/' -> "斜杠"
                    ':' -> "冒号"
                    ';' -> "分号"
                    '<' -> "小于号"
                    '=' -> "等号"
                    '>' -> "大于号"
                    '?' -> "问号"
                    '@' -> "艾特符号"
                    '[' -> "左方括号"
                    '\\' -> "反斜杠"
                    ']' -> "右方括号"
                    '^' -> "脱字符 (Caret)"
                    '_' -> "下划线"
                    '`' -> "反引号"
                    '{' -> "左花括号"
                    '|' -> "竖线"
                    '}' -> "右花括号"
                    '~' -> "波浪号"
                    else -> "符号 $c"
                }
            }
            list.add(AsciiEntry(i, c.toString(), remark))
        }

        // 127 DEL
        list.add(AsciiEntry(127, controlChars[32][0], controlChars[32][1]))

        return list
    }
}
