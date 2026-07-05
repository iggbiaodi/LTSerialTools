package indi.lt.serialtool.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import indi.lt.serialtool.component.CommandTableView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CommandRepository {
    private final Logger LOG = LogManager.getLogger(CommandRepository.class);

    public static final CommandRepository INSTANCE = new CommandRepository(
            Path.of(System.getProperty("user.home"), ".serialtool", "commands.json")
    );


    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path filePath;
    private final Type listType = new TypeToken<List<CommandDTO>>() {}.getType();

    public CommandRepository(Path filePath) {
        this.filePath = filePath;
    }

    /** 读取所有指令 */
    public List<CommandTableView.CommandItem> loadAll() {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }
        try (FileReader reader = new FileReader(filePath.toFile())) {
            List<CommandDTO> dtos = gson.fromJson(reader, listType);
            if (dtos == null) return new ArrayList<>();
            List<CommandTableView.CommandItem> items = new ArrayList<>();
            for (CommandDTO dto : dtos) {
                items.add(dto.toCommandItem());
            }
            return items;
        } catch (IOException e) {
            LOG.error(e);
            return new ArrayList<>();
        }
    }

    /** 保存所有指令 */
    public void saveAll(List<CommandTableView.CommandItem> items) {
        List<CommandDTO> dtos = new ArrayList<>();
        for (CommandTableView.CommandItem item : items) {
            dtos.add(new CommandDTO(item));
        }
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            gson.toJson(dtos, writer);
        } catch (IOException e) {
            LOG.error(e);
        }
    }

    /** 新增 */
    public void add(CommandTableView.CommandItem item) {
        List<CommandTableView.CommandItem> items = loadAll();
        items.add(item);
        saveAll(items);
    }

    /** 删除 */
    public void remove(CommandTableView.CommandItem item) {
        List<CommandTableView.CommandItem> items = loadAll();
        items.remove(item);
        saveAll(items);
    }

    /** 更新 */
    public void update(CommandTableView.CommandItem oldItem, CommandTableView.CommandItem newItem) {
        List<CommandTableView.CommandItem> items = loadAll();
        for (int i = 0; i < items.size(); i++) {
            CommandTableView.CommandItem iItem = items.get(i);
            if (iItem.getCommand().equals(oldItem.getCommand())
                    && iItem.getRemark().equals(oldItem.getRemark())) {
                items.set(i, newItem);
                break;
            }
        }
        saveAll(items);
    }
}
