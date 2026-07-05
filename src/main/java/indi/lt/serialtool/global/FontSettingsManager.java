package indi.lt.serialtool.global;

import indi.lt.serialtool.component.MyStyleClassedTextArea;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class FontSettingsManager {

    public static final String KEY_UI_FONT_FAMILY = "theme.font.uiFamily";
    public static final String KEY_TEXT_FONT_FAMILY = "theme.font.textFamily";

    private static final String MANAGED_ROOT_STYLE_KEY = "app.font.rootStyle";
    private static final String MANAGED_TEXT_STYLE_KEY = "app.font.textStyle";
    private static final String APP_STYLESHEET = "/css/style.css";
    private static final String APP_ICON = "/image/logo.png";
    private static final Set<String> TEXT_FONT_TARGET_IDS = Set.of("taRecvArea", "textAreaOrigin", "textAreaFilter");
    private static final String DEFAULT_UI_FONT_FAMILY = Font.getDefault().getFamily();
    private static final List<String> AVAILABLE_FONT_FAMILIES = loadAvailableFontFamilies();
    private static final String DEFAULT_TEXT_FONT_FAMILY = firstAvailable(
            "Consolas",
            "JetBrains Mono",
            "Cascadia Mono",
            "Courier New",
            "Microsoft YaHei UI",
            "Microsoft YaHei",
            DEFAULT_UI_FONT_FAMILY
    );

    private FontSettingsManager() {
    }

    public static List<String> getAvailableFontFamilies() {
        return AVAILABLE_FONT_FAMILIES;
    }

    public static String getUiFontFamily() {
        return sanitizeFontFamily(ConfigManager.get(KEY_UI_FONT_FAMILY, DEFAULT_UI_FONT_FAMILY), DEFAULT_UI_FONT_FAMILY);
    }

    public static String getTextFontFamily() {
        return sanitizeFontFamily(ConfigManager.get(KEY_TEXT_FONT_FAMILY, DEFAULT_TEXT_FONT_FAMILY), DEFAULT_TEXT_FONT_FAMILY);
    }

    public static void saveFontFamilies(String uiFontFamily, String textFontFamily) {
        ConfigManager.put(KEY_UI_FONT_FAMILY, sanitizeFontFamily(uiFontFamily, DEFAULT_UI_FONT_FAMILY));
        ConfigManager.put(KEY_TEXT_FONT_FAMILY, sanitizeFontFamily(textFontFamily, DEFAULT_TEXT_FONT_FAMILY));
        ConfigManager.save();
    }

    public static void applyToOpenWindows() {
        for (Window window : Window.getWindows()) {
            Scene scene = window.getScene();
            if (scene == null || scene.getRoot() == null) {
                continue;
            }
            applyTo(scene.getRoot());
        }
    }

    public static void applyTo(Parent root) {
        if (root == null) {
            return;
        }
        applyManagedStyle(root, buildFontStyle(getUiFontFamily()), MANAGED_ROOT_STYLE_KEY);
        applyTextFontRecursively(root, getTextFontFamily());
    }

    public static void configureDialog(Dialog<?> dialog) {
        if (dialog == null) {
            return;
        }
        DialogPane dialogPane = dialog.getDialogPane();
        if (dialogPane == null) {
            return;
        }
        String stylesheet = getAppStylesheetUrl();
        if (stylesheet != null && !dialogPane.getStylesheets().contains(stylesheet)) {
            dialogPane.getStylesheets().add(stylesheet);
        }
        dialogPane.sceneProperty().addListener((obs, oldScene, newScene) -> configureDialogScene(newScene));
        configureDialogScene(dialogPane.getScene());
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWING, event -> configureDialogScene(dialogPane.getScene()));
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWN, event -> configureDialogScene(dialogPane.getScene()));
        applyTo(dialogPane);
    }

    public static String getAppStylesheetUrl() {
        var resource = FontSettingsManager.class.getResource(APP_STYLESHEET);
        return resource == null ? null : resource.toExternalForm();
    }

    public static void applyAppIcon(Stage stage) {
        if (stage == null || !stage.getIcons().isEmpty()) {
            return;
        }
        stage.getIcons().add(loadAppIcon());
    }

    public static Image loadAppIcon() {
        return new Image(Objects.requireNonNull(FontSettingsManager.class.getResourceAsStream(APP_ICON)));
    }

    private static List<String> loadAvailableFontFamilies() {
        List<String> fontFamilies = new ArrayList<>(Font.getFamilies());
        Collections.sort(fontFamilies);
        return Collections.unmodifiableList(fontFamilies);
    }

    private static String firstAvailable(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && AVAILABLE_FONT_FAMILIES.contains(candidate)) {
                return candidate;
            }
        }
        return DEFAULT_UI_FONT_FAMILY;
    }

    private static String sanitizeFontFamily(String fontFamily, String fallback) {
        if (fontFamily != null && AVAILABLE_FONT_FAMILIES.contains(fontFamily)) {
            return fontFamily;
        }
        return fallback;
    }

    private static void applyTextFontRecursively(Parent parent, String fontFamily) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            applyTextFont(child, fontFamily);
            if (child instanceof Parent childParent) {
                applyTextFontRecursively(childParent, fontFamily);
            }
        }
    }

    private static void applyTextFont(Node node, String fontFamily) {
        if (node instanceof TextInputControl textInputControl) {
            clearManagedStyle(textInputControl, MANAGED_TEXT_STYLE_KEY);
            return;
        }
        if (node instanceof MyStyleClassedTextArea promptInlineCssTextArea) {
            if (TEXT_FONT_TARGET_IDS.contains(promptInlineCssTextArea.getId())) {
                promptInlineCssTextArea.applyTextFontFamily(fontFamily);
            } else {
                promptInlineCssTextArea.clearTextFontFamily();
            }
        }
    }

    private static void applyManagedStyle(Node node, String managedStyle, String propertyKey) {
        String previousStyle = (String) node.getProperties().get(propertyKey);
        String currentStyle = node.getStyle();
        String baseStyle = currentStyle == null ? "" : currentStyle;
        if (previousStyle != null && !previousStyle.isEmpty()) {
            baseStyle = baseStyle.replace(previousStyle, "").trim();
        }
        node.setStyle(baseStyle.isEmpty() ? managedStyle : (baseStyle + " " + managedStyle).trim());
        node.getProperties().put(propertyKey, managedStyle);
    }

    private static void clearManagedStyle(Node node, String propertyKey) {
        String previousStyle = (String) node.getProperties().get(propertyKey);
        if (previousStyle == null || previousStyle.isEmpty()) {
            return;
        }
        String currentStyle = node.getStyle();
        String baseStyle = currentStyle == null ? "" : currentStyle.replace(previousStyle, "").trim();
        node.setStyle(baseStyle);
        node.getProperties().remove(propertyKey);
    }

    private static void applyDialogWindowIcon(Window window) {
        if (!(window instanceof Stage stage)) {
            return;
        }
        applyAppIcon(stage);
    }

    private static void configureDialogScene(Scene scene) {
        if (scene == null) {
            return;
        }
        applyDialogWindowIcon(scene.getWindow());
        scene.windowProperty().addListener((windowObs, oldWindow, newWindow) -> applyDialogWindowIcon(newWindow));
    }

    private static String buildFontStyle(String fontFamily) {
        return "-fx-font-family: '" + escapeFontFamily(fontFamily) + "';";
    }

    private static String escapeFontFamily(String fontFamily) {
        return fontFamily.replace("\\", "\\\\").replace("'", "\\'");
    }

}
