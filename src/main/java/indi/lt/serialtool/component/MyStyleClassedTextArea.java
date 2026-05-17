package indi.lt.serialtool.component;

import atlantafx.base.theme.Styles;
import indi.lt.serialtool.data.BufferedDisplayLine;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.reactfx.collection.LiveList;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyStyleClassedTextArea extends StackPane {
    private static final String FONT_STYLE_KEY = "app.font.promptAreaStyle";
    private static final String LOG_META_STYLE_CLASS = "log-meta-text";
    private static final double EPS = 1e-3;
    private static final Pattern PLAIN_META_PATTERN = Pattern.compile("(?m)^(\\[[^\\]\\r\\n]+\\]\\s*)+");

    private final StyleClassedTextArea area = new StyleClassedTextArea();
    private final VirtualizedScrollPane<StyleClassedTextArea> vsPane;
    private final SimpleBooleanProperty autoScroll = new SimpleBooleanProperty(true);
    private final StringProperty promptText = new SimpleStringProperty(this, "promptText", "");

    private ScrollBar verticalBar;
    private int maxLines = 500;

    public MyStyleClassedTextArea() {
        setPadding(new Insets(2, 2, 2, 2));
        Label promptLabel = new Label();

        area.getStyleClass().addAll(Styles.BG_DEFAULT, "styled-text-area");
        vsPane = new VirtualizedScrollPane<>(area);
        getChildren().addAll(vsPane, promptLabel);

        promptLabel.getStyleClass().add("prompt-text");
        promptLabel.setMouseTransparent(true);
        StackPane.setAlignment(promptLabel, Pos.TOP_LEFT);
        promptLabel.textProperty().bind(promptTextProperty());
        promptLabel.visibleProperty().bind(
                Bindings.createBooleanBinding(
                        () -> area.getText().isEmpty(),
                        area.textProperty(), area.focusedProperty()
                )
        );

        area.setWrapText(true);
        area.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) {
                area.setParagraphGraphicFactory(null);
            } else if (area.getParagraphGraphicFactory() == null) {
                area.setParagraphGraphicFactory(idx -> {
                    Label label = new Label(String.valueOf(idx + 1));
                    label.getStyleClass().add("lineno");
                    label.setAlignment(Pos.CENTER_RIGHT);
                    label.setMinWidth(30);
                    label.setPrefWidth(30);
                    label.setMaxWidth(30);

                    StackPane wrapper = new StackPane(label);
                    wrapper.getStyleClass().add("lineno-wrap");
                    return wrapper;
                });

            }
        });

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(this::installScrollBarListenerIfNeeded);
            }
        });
        vsPane.layoutBoundsProperty().addListener((o, ov, nv) -> installScrollBarListenerIfNeeded());
    }

    private void installScrollBarListenerIfNeeded() {
        if (verticalBar != null || getScene() == null) {
            return;
        }

        vsPane.applyCss();
        vsPane.layout();

        area.addEventFilter(ScrollEvent.SCROLL, e -> setAutoScroll(false));
        vsPane.addEventFilter(ScrollEvent.SCROLL, e -> setAutoScroll(false));

        Set<Node> bars = vsPane.lookupAll(".scroll-bar");
        for (Node node : bars) {
            if (node instanceof ScrollBar sb && sb.getOrientation() == Orientation.VERTICAL) {
                verticalBar = sb;
                verticalBar.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> setAutoScroll(false));
                verticalBar.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> setAutoScroll(false));
                verticalBar.addEventFilter(ScrollEvent.SCROLL, e -> setAutoScroll(false));
                break;
            }
        }
    }

    private static boolean isAtBottom(ScrollBar sb) {
        return sb.getValue() >= sb.getMax() - EPS;
    }

    public StringProperty promptTextProperty() {
        return promptText;
    }

    public String getPromptText() {
        return promptText.get();
    }

    public void setPromptText(String value) {
        promptText.set(value);
    }

    public BooleanProperty editableProperty() {
        return area.editableProperty();
    }

    public boolean isEditable() {
        return area.isEditable();
    }

    public void setEditable(boolean value) {
        area.setEditable(value);
    }

    public BooleanProperty wrapTextProperty() {
        return area.wrapTextProperty();
    }

    public boolean isWrapText() {
        return area.isWrapText();
    }

    public void setWrapText(boolean value) {
        area.setWrapText(value);
    }

    public String getText() {
        return area.getText();
    }

    public void setText(String value) {
        area.replaceText(value == null ? "" : value);
        applyMetaStyleToPlainText(0, area.getText());
        if (verticalBar != null) {
            autoScroll.set(isAtBottom(verticalBar));
        }
    }

    public void setLogLines(List<BufferedDisplayLine> lines, boolean showTimestamp, boolean showDataType) {
        area.clear();
        appendLogLines(lines, showTimestamp, showDataType);
    }

    public void appendLogLines(Collection<BufferedDisplayLine> lines, boolean showTimestamp, boolean showDataType) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (BufferedDisplayLine line : lines) {
            appendLogLine(line, showTimestamp, showDataType);
        }
        trimToMaxLines();
        if (autoScroll.get()) {
            moveToEnd();
        }
    }

    public void appendLogLine(BufferedDisplayLine line, boolean showTimestamp, boolean showDataType) {
        appendLogLine(line, showTimestamp, showDataType, false);
    }

    public void appendLogLine(BufferedDisplayLine line, boolean showTimestamp, boolean showDataType,boolean showMsgDirection) {
        if (line == null) {
            return;
        }
        if (showTimestamp && !line.getTimestampText().isEmpty()) {
            appendStyledText("[" + line.getTimestampText() + "] ", LOG_META_STYLE_CLASS);
        }
        if (showMsgDirection) {
            appendStyledText("[" + line.getMessageDirection().getType() + "] ", LOG_META_STYLE_CLASS);
        }
        if (showDataType) {
            appendStyledText("[" + line.getDataTypeText() + "] ", LOG_META_STYLE_CLASS);
        }
        appendStyledText(line.getMessageText(), null);
        appendStyledText("\n", null);
    }


    private void appendStyledText(String text, String styleClass) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (styleClass != null && !styleClass.isBlank()) {
            area.append(text, styleClass);
            return;
        }
        // 必须加速第二个参数“List.of()“，否则后续样式会沿用之前的样式
        area.append(text, List.of());
    }

    public StyleClassedTextArea getArea() {
        return area;
    }

    public void appendText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int start = area.getLength();
        area.appendText(text);
        applyMetaStyleToPlainText(start, text);
        trimToMaxLines();
        if (autoScroll.get()) {
            moveToEnd();
        }
    }

    private void trimToMaxLines() {
        if (maxLines <= 0) {
            return;
        }
        int totalParagraphs = area.getParagraphs().size();
        if (totalParagraphs <= maxLines) {
            return;
        }
        int remove = totalParagraphs - maxLines;
        int cutOffset = area.getAbsolutePosition(remove, 0);
        area.replaceText(0, cutOffset, "");
    }

    public void replaceText(int start, int end, String text) {
        area.replaceText(start, end, text);
    }

    public int getMaxLines() {
        return maxLines;
    }

    public void setMaxLines(int maxLines) {
        this.maxLines = Math.max(0, maxLines);
    }

    public int getAbsolutePosition(int paragraphIndex, int columnPosition) {
        return area.getAbsolutePosition(paragraphIndex, columnPosition);
    }

    public LiveList<?> getParagraphs() {
        return area.getParagraphs();
    }

    public ObservableValue<String> textProperty() {
        return area.textProperty();
    }

    public void moveToEnd() {
        area.moveTo(area.getLength());
        area.requestFollowCaret();
    }

    public void restoreAutoScrollToEnd() {
        setAutoScroll(true);
        moveToEnd();
        Platform.runLater(this::moveToEnd);
    }

    public boolean isAutoScroll() {
        return autoScroll.get();
    }

    public SimpleBooleanProperty autoScrollProperty() {
        return autoScroll;
    }

    public void setAutoScroll(boolean autoScroll) {
        this.autoScroll.set(autoScroll);
    }

    public void applyTextFontFamily(String fontFamily) {
        String fontStyle = "-fx-font-family: '" + escapeFontFamily(fontFamily) + "';";
        applyManagedStyle(this, fontStyle);
        applyManagedStyle(area, fontStyle);
    }

    public void clearTextFontFamily() {
        clearManagedStyle(this);
        clearManagedStyle(area);
    }

    private void applyManagedStyle(Node node, String managedStyle) {
        String previousStyle = (String) node.getProperties().get(FONT_STYLE_KEY);
        String currentStyle = node.getStyle();
        String baseStyle = currentStyle == null ? "" : currentStyle;
        if (previousStyle != null && !previousStyle.isEmpty()) {
            baseStyle = baseStyle.replace(previousStyle, "").trim();
        }
        node.setStyle(baseStyle.isEmpty() ? managedStyle : (baseStyle + " " + managedStyle).trim());
        node.getProperties().put(FONT_STYLE_KEY, managedStyle);
    }

    private void clearManagedStyle(Node node) {
        String previousStyle = (String) node.getProperties().get(FONT_STYLE_KEY);
        if (previousStyle == null || previousStyle.isEmpty()) {
            return;
        }
        String currentStyle = node.getStyle();
        String baseStyle = currentStyle == null ? "" : currentStyle.replace(previousStyle, "").trim();
        node.setStyle(baseStyle);
        node.getProperties().remove(FONT_STYLE_KEY);
    }

    private String escapeFontFamily(String fontFamily) {
        return fontFamily == null ? "" : fontFamily.replace("\\", "\\\\").replace("'", "\\'");
    }

    private void applyMetaStyleToPlainText(int baseOffset, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher matcher = PLAIN_META_PATTERN.matcher(text);
        while (matcher.find()) {
            area.setStyleClass(baseOffset + matcher.start(), baseOffset + matcher.end(), LOG_META_STYLE_CLASS);
        }
    }
}
