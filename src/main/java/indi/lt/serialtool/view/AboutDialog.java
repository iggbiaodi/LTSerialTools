package indi.lt.serialtool.view;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import indi.lt.serialtool.global.FontSettingsManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Desktop;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class AboutDialog extends Dialog<Void> {

    private static final Logger LOG = LogManager.getLogger(AboutDialog.class);
    private static final String GITHUB_URL = "https://github.com/iggbiaodi/LTSerialTools";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/iggbiaodi/LTSerialTools/releases/latest";
    private static final String RELEASES_URL = GITHUB_URL + "/releases";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final Label versionLabel = createAboutLabel("版本: 正在获取...");
    private final Label updateDateLabel = createAboutLabel("更新日期: 正在获取...");
    private final Button checkUpdateButton = new Button("检查更新");

    private ReleaseInfo latestRelease;

    public AboutDialog(Window owner) {
        setTitle("关于" + BaseStage.APP_NAME);
        setHeaderText(null);
        setGraphic(null);
        setResizable(false);
        if (owner != null) {
            initOwner(owner);
        }

        DialogPane dialogPane = getDialogPane();
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        Node closeButton = dialogPane.lookupButton(ButtonType.CLOSE);
        if (closeButton != null) {
            closeButton.setVisible(false);
            closeButton.setManaged(false);
        }

        dialogPane.setContent(createContent());
        dialogPane.setMinWidth(700);
        dialogPane.setPrefWidth(700);
        FontSettingsManager.configureDialog(this);

        refreshReleaseInfo(false);
    }

    private VBox createContent() {
        VBox content = new VBox(8);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(10, 12, 0, 12));
        content.setPrefWidth(670);
        content.setStyle("-fx-background-color: -color-bg-default;");

        Label titleLabel = new Label(BaseStage.APP_NAME);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: -color-fg-default;");

        Separator separator = new Separator();
        separator.setMaxWidth(Double.MAX_VALUE);

        ImageView logoView = new ImageView(FontSettingsManager.loadAppIcon());
        logoView.setFitWidth(40);
        logoView.setFitHeight(40);
        logoView.setPreserveRatio(true);
        VBox.setMargin(logoView, new Insets(0, 0, 4, 0));

        VBox infoBox = new VBox(12);
        infoBox.setAlignment(Pos.TOP_CENTER);
        infoBox.setPadding(new Insets(6, 18, 12, 18));
        infoBox.setMaxWidth(Double.MAX_VALUE);
        infoBox.setStyle("-fx-background-color: -color-bg-default; -fx-border-color: -color-border-default; -fx-border-width: 1 0 0 0;");

        Label authorTitle = createAboutLabel("关于作者:");
        authorTitle.setStyle(authorTitle.getStyle() + " -fx-font-weight: bold;");

        Hyperlink githubLink = new Hyperlink(GITHUB_URL);
        githubLink.setMaxWidth(Double.MAX_VALUE);
        githubLink.setAlignment(Pos.CENTER);
        githubLink.setOnAction(event -> openUrl(GITHUB_URL));

        Button openGithubButton = new Button("打开 GitHub");
        openGithubButton.setOnAction(event -> openUrl(GITHUB_URL));
        checkUpdateButton.setOnAction(event -> checkForUpdates());

        HBox actionBox = new HBox(10, openGithubButton, checkUpdateButton);
        actionBox.setAlignment(Pos.CENTER);

        infoBox.getChildren().addAll(
                createAboutLabel("LTSerialTool是一款功能实用的串口调试助手"),
                createAboutLabel("支持多串口接收、关键字过滤&&高亮、自定义背景、串口发送、自定义添加指令、定时发送、接收&&发送数据量统计、波形图实时绘制等功能。"),
                authorTitle,
                createAboutLabel("开发者: DaBiaoDi"),
                createAboutLabel("联系方式: 1397018103@qq.com"),
                githubLink,
                versionLabel,
                updateDateLabel,
                actionBox
        );

        content.getChildren().addAll(titleLabel, separator, logoView, infoBox);
        return content;
    }

    private void refreshReleaseInfo(boolean showResult) {
        versionLabel.setText("版本: 正在获取...");
        updateDateLabel.setText("更新日期: 正在获取...");
        checkUpdateButton.setDisable(true);

        fetchLatestRelease().whenComplete((releaseInfo, throwable) -> Platform.runLater(() -> {
            checkUpdateButton.setDisable(false);
            if (throwable != null) {
                LOG.warn("获取 GitHub Release 信息失败", throwable);
                versionLabel.setText("版本: 获取失败");
                updateDateLabel.setText("更新日期: 获取失败");
                if (showResult) {
                    showInfoAlert("检查更新失败", "无法获取 GitHub 最新版本信息");
                }
                return;
            }
            latestRelease = releaseInfo;
            updateReleaseLabels(releaseInfo);
            if (showResult) {
                showUpdateResult(releaseInfo);
            }
        }));
    }

    private CompletableFuture<ReleaseInfo> fetchLatestRelease() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", BaseStage.APP_NAME)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("GitHub API status: " + response.statusCode());
                    }
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String tagName = getJsonString(json, "tag_name");
                    String publishedAt = getJsonString(json, "published_at");
                    String htmlUrl = getJsonString(json, "html_url");
                    if (isBlank(tagName)) {
                        throw new IllegalStateException("GitHub Release tag_name is empty");
                    }
                    return new ReleaseInfo(tagName, formatPublishedDate(publishedAt), isBlank(htmlUrl) ? RELEASES_URL : htmlUrl);
                });
    }

    private void updateReleaseLabels(ReleaseInfo releaseInfo) {
        versionLabel.setText("版本: " + releaseInfo.tagName());
        updateDateLabel.setText("更新日期: " + releaseInfo.publishedDate());
    }

    private void checkForUpdates() {
        refreshReleaseInfo(true);
    }

    private void showUpdateResult(ReleaseInfo releaseInfo) {
        int compareResult = compareVersions(releaseInfo.tagName(), BaseStage.APP_VERSION);
        if (compareResult > 0) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("检查更新");
            alert.setHeaderText("发现新版本: " + releaseInfo.tagName());
            alert.setContentText("当前版本: " + BaseStage.APP_VERSION + "\n更新日期: " + releaseInfo.publishedDate());
            ButtonType openRelease = new ButtonType("打开 Release");
            alert.getButtonTypes().setAll(openRelease, ButtonType.CLOSE);
            initChildDialog(alert);
            alert.showAndWait().ifPresent(buttonType -> {
                if (buttonType == openRelease) {
                    openUrl(releaseInfo.htmlUrl());
                }
            });
            return;
        }
        showInfoAlert("检查更新", "当前已是最新版本");
    }

    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        initChildDialog(alert);
        alert.showAndWait();
    }

    private void initChildDialog(Dialog<?> dialog) {
        Window owner = getDialogPane().getScene() == null ? null : getDialogPane().getScene().getWindow();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        FontSettingsManager.configureDialog(dialog);
    }

    private Label createAboutLabel(String text) {
        Label label = new Label(text);
        label.setAlignment(Pos.CENTER);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(true);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setStyle("-fx-text-fill: -color-fg-default;");
        return label;
    }

    private void openUrl(String url) {
        try {
            if (!Desktop.isDesktopSupported()) {
                showInfoAlert("打开链接失败", "当前系统不支持打开浏览器");
                return;
            }
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            LOG.warn("打开链接失败: {}", url, ex);
            showInfoAlert("打开链接失败", url);
        }
    }

    private static String getJsonString(JsonObject json, String name) {
        if (json == null || !json.has(name) || json.get(name).isJsonNull()) {
            return "";
        }
        return json.get(name).getAsString();
    }

    private static String formatPublishedDate(String publishedAt) {
        if (isBlank(publishedAt)) {
            return "未知";
        }
        try {
            return OffsetDateTime.parse(publishedAt).format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ex) {
            return publishedAt;
        }
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = normalizeVersion(left).split("\\.");
        String[] rightParts = normalizeVersion(right).split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftValue = i < leftParts.length ? parseVersionPart(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? parseVersionPart(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int suffixIndex = normalized.indexOf('-');
        if (suffixIndex >= 0) {
            normalized = normalized.substring(0, suffixIndex);
        }
        return normalized;
    }

    private static int parseVersionPart(String part) {
        if (part == null || part.isBlank()) {
            return 0;
        }
        String digits = part.replaceAll("[^0-9].*$", "");
        if (digits.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ReleaseInfo(String tagName, String publishedDate, String htmlUrl) {
    }
}
