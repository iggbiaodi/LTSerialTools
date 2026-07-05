package indi.lt.serialtool.global

import com.google.gson.Gson
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * 保存键值对
 */
object ConfigManager {
    private val LOG: Logger = LogManager.getLogger(ConfigManager::class.java)

    private val CONFIG_DIR = "${System.getProperty("user.home")}${File.separator}.serialtool"
    private val CONFIG_FILE = "$CONFIG_DIR${File.separator}config.properties"

    private val props = Properties()
    private val gson = Gson()

    // 常量定义
    const val KEY_RECEIVE_SPLIT_PANE_DIVIDER_POSITIONS: String = "receive.splitPane.dividerPositions"
    const val KEY_RECEIVE_TIMEOUT_MS: String = "receive.timeoutMs"
    const val DEFAULT_RECEIVE_TIMEOUT_MS: Int = 500

    init {
        load()
    }

    private fun load() {
        try {
            val dirPath = Path.of(CONFIG_DIR)
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath)
            }
            val file = File(CONFIG_FILE)
            if (file.exists()) {
                InputStreamReader(file.inputStream(), StandardCharsets.UTF_8).use { reader ->
                    props.load(reader)
                }
            }
        } catch (e: IOException) {
            LOG.error(e)
        }
    }

    @JvmStatic
    @Synchronized
    fun save() {
        try {
            val dirPath = Path.of(CONFIG_DIR)
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath)
            }
            OutputStreamWriter(File(CONFIG_FILE).outputStream(), StandardCharsets.UTF_8).use { writer ->
                props.store(writer, "SerialTool Configuration")
            }
        } catch (e: IOException) {
            LOG.error(e)
        }
    }

    @JvmStatic
    @Synchronized
    fun put(key: String?, value: String?) {
        if (key.isNullOrBlank()) {
            return
        }
        props.setProperty(key, value ?: "")
    }

    @JvmStatic
    fun set(key: String?, value: String?) {
        put(key, value)
        save()
    }

    @JvmStatic
    @Synchronized
    fun get(key: String?, defaultValue: String?): String {
        return props.getProperty(key, defaultValue)
    }

    @JvmStatic
    @Synchronized
    fun get(key: String?): String {
        return props.getProperty(key)
    }

    @JvmStatic
    @Synchronized
    fun <T> put(key: String?, value: T) {
        if (key.isNullOrBlank()) {
            return
        }
        props.setProperty(key, value.toString())
    }

    @JvmStatic
    fun <T> set(key: String?, value: T) {
        put(key, value)
        save()
    }

    @JvmStatic
    @Synchronized
    fun <T> get(key: String?, clazz: Class<T>, defaultValue: T): T {
        val value = props.getProperty(key)
        return if (value != null) {
            parseTypedValue(value, clazz) ?: defaultValue
        } else {
            defaultValue
        }
    }

    @JvmStatic
    @Synchronized
    fun <T> get(key: String?, clazz: Class<T>): T? {
        val value = props.getProperty(key)
        return if (value != null) {
            parseTypedValue(value, clazz)
        } else {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> parseTypedValue(value: String, clazz: Class<T>): T? {
        return try {
            when (clazz) {
                Int::class.java, java.lang.Integer::class.java -> value.toInt() as T
                Long::class.java, java.lang.Long::class.java -> value.toLong() as T
                Boolean::class.java, java.lang.Boolean::class.java -> value.toBoolean() as T
                Float::class.java, java.lang.Float::class.java -> value.toFloat() as T
                Double::class.java, java.lang.Double::class.java -> value.toDouble() as T
                String::class.java -> value as T
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将对象序列化为 JSON 并保存
     */
    @JvmStatic
    @Synchronized
    fun <T> putObject(key: String?, value: T) {
        if (key.isNullOrBlank()) {
            return
        }
        try {
            val json = gson.toJson(value)
            props.setProperty(key, json)
        } catch (e: Exception) {
            LOG.error("Failed to serialize object for key: $key", e)
        }
    }

    /**
     * 将对象序列化为 JSON 并保存
     */
    @JvmStatic
    fun <T> setObject(key: String?, value: T) {
        putObject(key, value)
        save()
    }

    /**
     * 从 JSON 反序列化对象
     */
    @JvmStatic
    @Synchronized
    fun <T> getObject(key: String?, clazz: Class<T>, defaultValue: T): T {
        val json = props.getProperty(key)
        return if (json != null) {
            try {
                gson.fromJson(json, clazz)
            } catch (e: Exception) {
                LOG.error("Failed to deserialize object for key: $key", e)
                defaultValue
            }
        } else {
            defaultValue
        }
    }
}

