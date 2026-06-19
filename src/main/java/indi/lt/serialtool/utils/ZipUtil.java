package indi.lt.serialtool.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZIP压缩工具类
 */
public class ZipUtil {

    /**
     * 将文本数据按指定大小分割并打包成ZIP
     *
     * @param data        要保存的数据
     * @param zipFile     目标ZIP文件
     * @param partSizeMB  每个分卷的大小（MB）
     * @param baseFileName 基础文件名（不含扩展名）
     * @throws IOException IO异常
     */
    public static void saveToZip(String data, File zipFile, int partSizeMB, String baseFileName) throws IOException {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("数据不能为空");
        }

        long partSizeBytes = (long) partSizeMB * 1024 * 1024;
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile), StandardCharsets.UTF_8)) {
            // 计算需要多少分卷
            int totalParts = (int) Math.ceil((double) dataBytes.length / partSizeBytes);

            for (int part = 0; part < totalParts; part++) {
                long start = (long) part * partSizeBytes;
                long end = Math.min(start + partSizeBytes, dataBytes.length);
                int length = (int) (end - start);

                String entryName;
                if (totalParts > 1) {
                    entryName = baseFileName + "_part" + (part + 1) + "_of" + totalParts + ".txt";
                } else {
                    entryName = baseFileName + ".txt";
                }

                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                zos.write(dataBytes, (int) start, length);
                zos.closeEntry();
            }
        }
    }

    /**
     * 将多个数据片段打包成ZIP
     *
     * @param dataArray   数据数组
     * @param zipFile     目标ZIP文件
     * @param baseFileName 基础文件名
     * @throws IOException IO异常
     */
    public static void saveMultipleToZip(String[] dataArray, File zipFile, String baseFileName) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile), StandardCharsets.UTF_8)) {
            for (int i = 0; i < dataArray.length; i++) {
                if (dataArray[i] == null || dataArray[i].isEmpty()) {
                    continue;
                }

                String entryName = baseFileName + "_" + (i + 1) + ".txt";
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                zos.write(dataArray[i].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    /**
     * 将多个文本条目打包成ZIP，每个条目可独立分卷
     *
     * @param entries    数据条目，key 为条目名称（不含扩展名），value 为内容
     * @param zipFile    目标ZIP文件
     * @param partSizeMB 每个分卷的大小（MB）
     * @throws IOException IO异常
     */
    public static void saveEntriesToZip(Map<String, String> entries, File zipFile, int partSizeMB) throws IOException {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("数据不能为空");
        }

        long partSizeBytes = (long) partSizeMB * 1024 * 1024;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                String name = entry.getKey();
                String content = entry.getValue();
                if (content == null || content.isEmpty()) {
                    continue;
                }

                byte[] dataBytes = content.getBytes(StandardCharsets.UTF_8);
                int totalParts = (int) Math.ceil((double) dataBytes.length / partSizeBytes);

                for (int part = 0; part < totalParts; part++) {
                    long start = (long) part * partSizeBytes;
                    long end = Math.min(start + partSizeBytes, dataBytes.length);
                    int length = (int) (end - start);

                    String safeName = name.replaceAll("[<>:\"/\\\\|?*]", "_");

                    String entryName;
                    if (totalParts > 1) {
                        entryName = safeName + "_part" + (part + 1) + "_of" + totalParts + ".txt";
                    } else {
                        entryName = safeName + ".txt";
                    }

                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zos.putNextEntry(zipEntry);
                    zos.write(dataBytes, (int) start, length);
                    zos.closeEntry();
                }
            }
        }
    }

    /**
     * 格式化文件大小
     */
    public static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
