package indi.lt.serialtool.view;

import indi.lt.serialtool.global.FontSettingsManager;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public class SaveProgressStage {

    private final Stage stage = new Stage(StageStyle.UTILITY);
    private final Label messageLabel = new Label("正在准备保存...");
    private final ProgressBar progressBar = new ProgressBar();

    public SaveProgressStage(Window owner) {
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("正在保存");
        stage.setResizable(false);
        stage.setAlwaysOnTop(true);
        stage.setOnCloseRequest(event -> event.consume());
        FontSettingsManager.applyAppIcon(stage);

        progressBar.setPrefWidth(280);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        VBox root = new VBox(12, messageLabel, progressBar);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(16));

        stage.setScene(new Scene(root, 320, 96));
    }

    public void bind(Task<?> task) {
        messageLabel.textProperty().bind(task.messageProperty());
        progressBar.progressProperty().bind(task.progressProperty());
    }

    public void show() {
        stage.show();
    }

    public void close() {
        stage.close();
    }
}
