package indi.lt.serialtool;

import javafx.application.Application;

/**
 * 添加主类调用 Application 子类，否则打包之后会报错缺少 javafx 组件
 * @author Nonoas
 * @date 2025/8/22
 * @since 1.0.0
 */
public class Main {
    public static void main(String[] args) {
        Application.launch(SerialApplication.class, args);
    }
}
