package indi.lt.serialtool.data;

import java.util.Map;

/**
 * 提供可保存到 ZIP 压缩包的数据条目。
 * <p>
 * 实现该接口的控制器/组件，在 "保存为压缩包" 操作时会自动被收集并写入 ZIP。
 */
public interface ZipDataProvider {

    /**
     * 返回要保存到 ZIP 的数据条目。
     *
     * @return key 为条目名称（将用于生成文件名），value 为条目内容；可返回空 Map，但不能返回 null
     */
    Map<String, String> provideZipEntries();
}
