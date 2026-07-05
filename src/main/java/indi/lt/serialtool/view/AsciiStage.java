package indi.lt.serialtool.view;

/**
 * @author Nonoas
 * @date 2025/9/14
 * @since
 */
public class AsciiStage extends BaseStage {

    private static final AsciiStage INSTANCE = new AsciiStage();

    private AsciiStage() {
        setContentView(new AsciiTablePane());
        setMinHeight(500);
        setMinWidth(600);
        setResizable(false);
    }

    public synchronized static void showStage() {
        INSTANCE.show();
    }
}
