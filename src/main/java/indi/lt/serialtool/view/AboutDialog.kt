package indi.lt.serialtool.view

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import indi.lt.serialtool.global.FontSettingsManager
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Hyperlink
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.text.TextAlignment
import javafx.stage.Window
import org.apache.logging.log4j.LogManager
import java.awt.Desktop
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.math.max

class AboutDialog(owner: Window?) : Dialog<Void?>() {
    private val versionLabel = createAboutLabel(versionText())
    private val updateDateLabel = createAboutLabel(buildTimeText())
    private val checkUpdateButton = Button("检查更新")

    init {
        title = "关于${BaseStage.APP_NAME}"
        headerText = null
        graphic = null
        isResizable = false
        owner?.let(::initOwner)

        dialogPane.apply {
            buttonTypes += ButtonType.CLOSE
            lookupButton(ButtonType.CLOSE)?.apply {
                isVisible = false
                isManaged = false
            }
            content = createContent()
            minWidth = 700.0
            prefWidth = 700.0
        }

        FontSettingsManager.configureDialog(this)
    }

    private fun createContent() = VBox(6.0).apply {
        alignment = Pos.TOP_LEFT
        padding = Insets(10.0, 12.0, 8.0, 12.0)
        prefWidth = 670.0
        style = "-fx-background-color: -color-bg-default;"

        val logoView = ImageView(FontSettingsManager.loadAppIcon()).apply {
            fitWidth = 36.0
            fitHeight = 36.0
            isPreserveRatio = true
        }

        val titleLabel = Label(BaseStage.APP_NAME).apply {
            alignment = Pos.CENTER_LEFT
            style = "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: -color-fg-default;"
        }

        val titleBox = HBox(10.0, logoView, titleLabel).apply {
            alignment = Pos.CENTER_LEFT
            maxWidth = Double.MAX_VALUE
        }

        val authorTitle = createAboutLabel("关于作者:").apply {
            style += " -fx-font-weight: bold;"
        }

        val githubLink = Hyperlink(GITHUB_URL).apply {
            maxWidth = Double.MAX_VALUE
            alignment = Pos.CENTER_LEFT
            setOnAction { openUrl(GITHUB_URL) }
        }

        val openGithubButton = Button("打开 GitHub").apply {
            setOnAction { openUrl(GITHUB_URL) }
        }
        checkUpdateButton.setOnAction { checkForUpdates() }

        val actionBox = HBox(10.0, openGithubButton, checkUpdateButton).apply {
            alignment = Pos.CENTER_LEFT
        }

        val textInfoBox = VBox(9.0).apply {
            alignment = Pos.TOP_LEFT
            maxWidth = Double.MAX_VALUE
            HBox.setHgrow(this, Priority.ALWAYS)
            children.addAll(
                titleBox,
                createAboutLabel("LTSerialTool是一款功能实用的串口调试助手"),
                createAboutLabel("支持多串口接收、关键字过滤&&高亮、自定义背景、串口发送、自定义添加指令、定时发送、接收&&发送数据量统计、波形图实时绘制等功能。"),
                authorTitle,
                createAboutLabel("开发者: iggbiaodi"),
                createAboutLabel("联系方式: 1397018103@qq.com"),
                githubLink,
                versionLabel,
                updateDateLabel,
                actionBox
            )
        }

        val infoBox = HBox(18.0, textInfoBox, createFeedbackQrBox()).apply {
            alignment = Pos.TOP_LEFT
            padding = Insets(4.0, 16.0, 12.0, 16.0)
            maxWidth = Double.MAX_VALUE
            style = "-fx-background-color: -color-bg-default;"
        }

        children += infoBox
    }

    private fun createFeedbackQrBox(): VBox {
        val qrImageUrl = checkNotNull(AboutDialog::class.java.getResource(FEEDBACK_QR_IMAGE)) {
            "缺少微信公众号二维码图片: $FEEDBACK_QR_IMAGE"
        }

        val qrView = ImageView(Image(qrImageUrl.toExternalForm())).apply {
            fitWidth = FEEDBACK_QR_SIZE
            fitHeight = FEEDBACK_QR_SIZE
            isPreserveRatio = true
            isSmooth = false
        }

        val titleLabel = createCenteredAboutLabel("微信公众号").apply {
            style += " -fx-font-size: 12px; -fx-font-weight: bold;"
        }

        val promptLabel = createCenteredAboutLabel("反馈与更新通知").apply {
            style += " -fx-font-size: 12px;"
        }

        return VBox(4.0, qrView, titleLabel, promptLabel).apply {
            alignment = Pos.TOP_CENTER
            minWidth = 122.0
            prefWidth = 122.0
            maxWidth = 122.0
            padding = Insets(38.0, 0.0, 0.0, 10.0)
            style = "-fx-border-color: -color-border-default; -fx-border-width: 0 0 0 1;"
        }
    }

    private fun refreshReleaseInfo(showResult: Boolean) {
        resetLocalVersionLabels()
        checkUpdateButton.isDisable = true

        fetchLatestRelease().whenComplete { releaseInfo, throwable ->
            Platform.runLater {
                checkUpdateButton.isDisable = false

                if (throwable != null) {
                    val errorMessage = getReleaseErrorMessage(throwable)
                    LOG.warn("获取 GitHub Release 信息失败: {}", errorMessage)
                    LOG.debug("获取 GitHub Release 信息失败", unwrapCompletionException(throwable))
                    resetLocalVersionLabels()
                    if (showResult) {
                        showInfoAlert("检查更新失败", "无法获取 GitHub 最新版本信息\n$errorMessage")
                    }
                } else {
                    resetLocalVersionLabels()
                    if (showResult && releaseInfo != null) {
                        showUpdateResult(releaseInfo)
                    }
                }
            }
        }
    }

    private fun fetchLatestRelease(): CompletableFuture<ReleaseInfo> =
        fetchLatestReleaseFromApi().exceptionallyCompose { apiThrowable ->
            LOG.warn("GitHub API 获取失败，改用 Release 页面检查更新: {}", getReleaseErrorMessage(apiThrowable))

            fetchLatestReleaseFromPage().exceptionallyCompose { pageThrowable ->
                val apiError = getReleaseErrorMessage(apiThrowable)
                val pageError = getReleaseErrorMessage(pageThrowable)
                CompletableFuture.failedFuture(
                    ReleaseFetchException("GitHub API 和 Release 页面均获取失败。API: $apiError；页面: $pageError")
                )
            }
        }

    private fun fetchLatestReleaseFromApi(): CompletableFuture<ReleaseInfo> {
        val request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", BaseStage.APP_NAME)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                if (response.statusCode() !in 200..299) {
                    throw ReleaseFetchException(formatHttpError(response.statusCode(), response.body()))
                }

                val json = JsonParser.parseString(response.body()).asJsonObject
                val tagName = json.stringValue("tag_name")
                val publishedAt = json.stringValue("published_at")
                val htmlUrl = json.stringValue("html_url").ifBlank { RELEASES_URL }

                if (tagName.isBlank()) {
                    throw ReleaseFetchException("GitHub Release 信息缺少版本号")
                }

                ReleaseInfo(tagName, formatPublishedDate(publishedAt), htmlUrl)
            }
    }

    private fun fetchLatestReleaseFromPage(): CompletableFuture<ReleaseInfo> {
        val request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_PAGE))
            .header("Accept", "text/html")
            .header("User-Agent", BaseStage.APP_NAME)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                if (response.statusCode() !in 200..299) {
                    throw ReleaseFetchException("Release 页面返回状态码: ${response.statusCode()}")
                }

                val tagName = extractReleaseTagFromUri(response.uri())
                    .ifBlank { extractReleaseTagFromHtml(response.body()) }

                if (tagName.isBlank()) {
                    throw ReleaseFetchException("Release 页面缺少版本号")
                }

                ReleaseInfo(tagName, extractReleaseDateFromHtml(response.body()), response.uri().toString())
            }
    }

    private fun checkForUpdates() {
        refreshReleaseInfo(true)
    }

    private fun resetLocalVersionLabels() {
        versionLabel.text = versionText()
        updateDateLabel.text = buildTimeText()
    }

    private fun showUpdateResult(releaseInfo: ReleaseInfo) {
        if (compareVersions(releaseInfo.tagName, BaseStage.APP_VERSION) > 0) {
            val openRelease = ButtonType("打开 Release")
            Alert(Alert.AlertType.INFORMATION).apply {
                title = "检查更新"
                headerText = "发现新版本: ${releaseInfo.tagName}"
                contentText = "当前版本: ${BaseStage.APP_VERSION}\n最新版本日期: ${releaseInfo.publishedDate}"
                buttonTypes.setAll(openRelease, ButtonType.CLOSE)
                initChildDialog(this)
            }.showAndWait().ifPresent { buttonType ->
                if (buttonType == openRelease) {
                    openUrl(releaseInfo.htmlUrl)
                }
            }
            return
        }

        showInfoAlert("检查更新", "当前已是最新版本")
    }

    private fun showInfoAlert(title: String, message: String) {
        Alert(Alert.AlertType.INFORMATION).apply {
            this.title = title
            headerText = null
            contentText = message
            initChildDialog(this)
        }.showAndWait()
    }

    private fun initChildDialog(dialog: Dialog<*>) {
        dialogPane.scene?.window?.let(dialog::initOwner)
        FontSettingsManager.configureDialog(dialog)
    }

    private fun createAboutLabel(text: String) = Label(text).apply {
        alignment = Pos.CENTER_LEFT
        maxWidth = Double.MAX_VALUE
        isWrapText = true
        textAlignment = TextAlignment.LEFT
        style = "-fx-text-fill: -color-fg-default;"
    }

    private fun createCenteredAboutLabel(text: String) = createAboutLabel(text).apply {
        alignment = Pos.CENTER
        textAlignment = TextAlignment.CENTER
    }

    private fun openUrl(url: String) {
        try {
            if (!Desktop.isDesktopSupported()) {
                showInfoAlert("打开链接失败", "当前系统不支持打开浏览器")
                return
            }
            Desktop.getDesktop().browse(URI(url))
        } catch (ex: Exception) {
            LOG.warn("打开链接失败: {}", url, ex)
            showInfoAlert("打开链接失败", url)
        }
    }

    private data class ReleaseInfo(
        val tagName: String,
        val publishedDate: String,
        val htmlUrl: String
    )

    private class ReleaseFetchException(message: String) : RuntimeException(message)

    companion object {
        private val LOG = LogManager.getLogger(AboutDialog::class.java)
        private const val GITHUB_URL = "https://github.com/iggbiaodi/LTSerialTools"
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/iggbiaodi/LTSerialTools/releases/latest"
        private const val RELEASES_URL = "https://github.com/iggbiaodi/LTSerialTools/releases"
        private const val LATEST_RELEASE_PAGE = "https://github.com/iggbiaodi/LTSerialTools/releases/latest"
        private const val RELEASE_TAG_MARKER = "/releases/tag/"
        private const val FEEDBACK_QR_IMAGE = "/image/wechat-feedback-qr.jpg"
        private const val FEEDBACK_QR_SIZE = 96.0
        private val RELEASE_TAG_LINK_REGEX = Regex("""/iggbiaodi/LTSerialTools/releases/tag/([^"?#]+)""")
        private val RELEASE_DATETIME_REGEX = Regex("""datetime="([^"]+)"""")
        private val HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        private fun versionText() = "版本:\t ${BaseStage.APP_VERSION}"

        private fun buildTimeText() = "更新日期:\t ${BaseStage.APP_BUILD_TIME}"

        private fun formatHttpError(statusCode: Int, responseBody: String?): String {
            val githubMessage = getGithubErrorMessage(responseBody)

            return when {
                statusCode == 403 && githubMessage.lowercase(Locale.ROOT).contains("rate limit exceeded") ->
                    "GitHub API 访问频率受限，请稍后再试"
                statusCode == 403 ->
                    "GitHub API 返回 403，可能已触发访问限制"
                githubMessage.isNotBlank() ->
                    "GitHub API 返回状态码: $statusCode，$githubMessage"
                else ->
                    "GitHub API 返回状态码: $statusCode"
            }
        }

        private fun getGithubErrorMessage(responseBody: String?): String =
            responseBody
                ?.takeIf(String::isNotBlank)
                ?.let { body ->
                    runCatching {
                        JsonParser.parseString(body).asJsonObject.stringValue("message")
                    }.getOrDefault("")
                }
                .orEmpty()

        private fun getReleaseErrorMessage(throwable: Throwable?): String {
            val cause = throwable?.let(::unwrapCompletionException) ?: return "未知错误"
            return cause.message?.takeIf(String::isNotBlank) ?: cause.javaClass.simpleName
        }

        private fun unwrapCompletionException(throwable: Throwable): Throwable {
            var current = throwable
            while (current is CompletionException && current.cause != null) {
                current = current.cause!!
            }
            return current
        }

        private fun formatPublishedDate(publishedAt: String?): String =
            publishedAt
                ?.takeIf(String::isNotBlank)
                ?.let { value ->
                    runCatching {
                        OffsetDateTime.parse(value).format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }.getOrDefault(value)
                }
                ?: "未知"

        private fun extractReleaseTagFromUri(uri: URI): String =
            uri.path
                ?.substringAfter(RELEASE_TAG_MARKER, missingDelimiterValue = "")
                ?.takeIf(String::isNotBlank)
                ?.decodeUrl()
                .orEmpty()

        private fun extractReleaseTagFromHtml(html: String?): String =
            html
                ?.takeIf(String::isNotBlank)
                ?.let { RELEASE_TAG_LINK_REGEX.find(it)?.groupValues?.getOrNull(1) }
                ?.decodeUrl()
                .orEmpty()

        private fun extractReleaseDateFromHtml(html: String?): String =
            html
                ?.takeIf(String::isNotBlank)
                ?.let { RELEASE_DATETIME_REGEX.find(it)?.groupValues?.getOrNull(1) }
                ?.let(::formatPublishedDate)
                ?: "未知"

        private fun compareVersions(left: String?, right: String?): Int {
            val leftParts = normalizedVersionParts(left)
            val rightParts = normalizedVersionParts(right)
            val length = max(leftParts.size, rightParts.size)

            for (index in 0 until length) {
                val leftValue = leftParts.getOrElse(index) { 0 }
                val rightValue = rightParts.getOrElse(index) { 0 }

                if (leftValue != rightValue) {
                    return leftValue.compareTo(rightValue)
                }
            }

            return 0
        }

        private fun normalizedVersionParts(version: String?): List<Int> =
            version
                ?.trim()
                ?.let { if (it.startsWith("v", ignoreCase = true)) it.drop(1) else it }
                ?.substringBefore('-')
                ?.split('.')
                ?.filter(String::isNotBlank)
                ?.map(::parseVersionPart)
                .orEmpty()

        private fun parseVersionPart(part: String): Int =
            part.takeWhile(Char::isDigit).toIntOrNull() ?: 0

        private fun String.decodeUrl(): String =
            URLDecoder.decode(this, StandardCharsets.UTF_8)
    }
}

private fun JsonObject.stringValue(name: String): String =
    takeIf { has(name) && !get(name).isJsonNull }
        ?.get(name)
        ?.asString
        .orEmpty()
