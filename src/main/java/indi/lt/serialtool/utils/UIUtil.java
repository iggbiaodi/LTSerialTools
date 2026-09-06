package indi.lt.serialtool.utils;

import github.nonoas.jfx.flat.ui.AppState;
import github.nonoas.jfx.flat.ui.stage.ToastQueue;
import javafx.scene.control.ComboBox;

/**
 * @author Nonoas
 * @date 2025/9/20
 * @since
 */
public class UIUtil {

    /**
     * 从一个 ComboBox 获取一个 int 类型的值。
     * * @param comboBox 目标 ComboBox。
     *
     * @param defaultValue 如果转换失败或值为 null，则返回的默认值。
     * @return 一个 int 类型的值。
     */
    public static int getSelectedInt(ComboBox<?> comboBox, int defaultValue) {
        Object selectedValue = comboBox.getValue();

        if (selectedValue instanceof Integer) {
            return (Integer) selectedValue;
        }

        if (selectedValue instanceof String) {
            try {
                return Integer.parseInt((String) selectedValue);
            } catch (NumberFormatException e) {
                // 如果字符串无法转换为 int，捕获异常并返回默认值
                return defaultValue;
            }
        }

        // 如果 ComboBox 的值为 null 或其他类型，返回默认值
        return defaultValue;
    }

    /**
     * 显示提示信息
     * @param message 提示信息
     */
    public static void showToast(String message) {
        ToastQueue.show(AppState.getStage(), message, 800);
    }
}
