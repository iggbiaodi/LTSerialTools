package indi.lt.serialtool.component;

import javafx.animation.FadeTransition;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * 状态指示灯组件：用于显示缓冲区溢出等状态
 * 支持红色闪烁效果
 */
public class StatusIndicator extends Label {

    private final Circle indicator;
    private FadeTransition blinkAnimation;

    // 颜色定义
    private static final Color COLOR_NORMAL = Color.web("#4CAF50");  // 绿色
    private static final Color COLOR_WARNING = Color.web("#FF9800"); // 橙色
    private static final Color COLOR_ERROR = Color.web("#F44336");   // 红色

    public StatusIndicator() {
        this(8); // 默认大小8px
    }

    public StatusIndicator(double size) {
        indicator = new Circle(size / 2);
        indicator.setFill(COLOR_NORMAL);

        setGraphic(indicator);
        setText(" 正常");
        getStyleClass().add("status-indicator");

        // 初始化闪烁动画
        initBlinkAnimation();
    }

    private void initBlinkAnimation() {
        blinkAnimation = new FadeTransition(Duration.millis(500), indicator);
        blinkAnimation.setFromValue(1.0);
        blinkAnimation.setToValue(0.2);
        blinkAnimation.setCycleCount(Timeline.INDEFINITE);
        blinkAnimation.setAutoReverse(true);
    }

    /**
     * 设置为正常状态（绿色）
     */
    public void setNormal() {
        stopBlink();
        indicator.setFill(COLOR_NORMAL);
        setText(" 正常");
    }

    /**
     * 设置为警告状态（橙色）
     */
    public void setWarning() {
        stopBlink();
        indicator.setFill(COLOR_WARNING);
        setText(" 警告");
    }

    /**
     * 设置为错误状态（红色闪烁）
     */
    public void setError() {
        indicator.setFill(COLOR_ERROR);
        setText(" 溢出");
        startBlink();
    }

    /**
     * 开始闪烁
     */
    public void startBlink() {
        if (blinkAnimation != null && !blinkAnimation.getStatus().equals(Timeline.Status.RUNNING)) {
            blinkAnimation.play();
        }
    }

    /**
     * 停止闪烁
     */
    public void stopBlink() {
        if (blinkAnimation != null) {
            blinkAnimation.stop();
            indicator.setOpacity(1.0);
        }
    }

    /**
     * 根据溢出状态设置
     */
    public void setOverflow(boolean overflow) {
        if (overflow) {
            setError();
        } else {
            setNormal();
        }
    }
}
