package indi.lt.serialtool.component;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.paint.Color;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InlineCssRegexHighlighter {
    private static final String HIGHLIGHT_STYLE_CLASS = "keyword-highlight";

    private final MyStyleClassedTextArea area;

    private final StringProperty patternText = new SimpleStringProperty("");
    private final ObjectProperty<Color> highlightColor = new SimpleObjectProperty<>(Color.web("#fff176"));
    private final BooleanProperty caseInsensitive = new SimpleBooleanProperty(true);
    private final DoubleProperty alpha = new SimpleDoubleProperty(0.45);

    private int lastLength = 0;

    public InlineCssRegexHighlighter(MyStyleClassedTextArea area) {
        this.area = area;
    }

    public void highlightNewAppend(int from, int to) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> highlightNewAppend(from, to));
            return;
        }

        String text = area.getText();
        if (text == null) {
            lastLength = 0;
            return;
        }

        int safeFrom = Math.max(0, Math.min(from, text.length()));
        int safeTo = Math.max(safeFrom, Math.min(to, text.length()));
        lastLength = safeTo;
        if (safeFrom >= safeTo) {
            return;
        }

        Pattern pattern = compilePattern();
        if (pattern == null) {
            return;
        }

        String delta = text.substring(safeFrom, safeTo);
        Matcher matcher = pattern.matcher(delta);
        while (matcher.find()) {
            if (matcher.start() == matcher.end()) {
                continue;
            }
            area.getArea().setStyleClass(
                    safeFrom + matcher.start(),
                    safeFrom + matcher.end(),
                    HIGHLIGHT_STYLE_CLASS
            );
        }
    }

    public void resetTracking() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::resetTracking);
            return;
        }
        String text = area.getText();
        lastLength = text == null ? 0 : text.length();
    }

    public void schedule() {
        String text = area.getText();
        int currentLength = text == null ? 0 : text.length();
        if (currentLength < lastLength) {
            lastLength = currentLength;
            return;
        }
        highlightNewAppend(lastLength, currentLength);
    }

    public void refreshAll() {
        resetTracking();
    }

    private Pattern compilePattern() {
        String pattern = patternText.get();
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }
        try {
            int flags = caseInsensitive.get() ? (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) : 0;
            return Pattern.compile(pattern, flags);
        } catch (Exception ignore) {
            return null;
        }
    }
    public StringProperty patternTextProperty() {
        return patternText;
    }

    public ObjectProperty<Color> highlightColorProperty() {
        return highlightColor;
    }

    public BooleanProperty caseInsensitiveProperty() {
        return caseInsensitive;
    }

    public DoubleProperty alphaProperty() {
        return alpha;
    }

    public void setPatternText(String s) {
        patternText.set(s);
    }

    public void setHighlightColor(Color c) {
        highlightColor.set(c);
    }

    public void setCaseInsensitive(boolean v) {
        caseInsensitive.set(v);
    }

    public void setAlpha(double v) {
        alpha.set(v);
    }
}
