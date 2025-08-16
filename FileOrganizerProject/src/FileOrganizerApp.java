import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class FileOrganizerApp extends Application {
    private TextField pathField;
    private TextArea logArea;
    private ListView<String> categoryList;
    private ObservableList<String> categoryItems;
    private Map<String, String> customCategories;

    @Override
    public void start(Stage stage) {
        stage.setTitle("📂 File Organizer");

        customCategories = loadCategories();
        categoryItems = FXCollections.observableArrayList();
        customCategories.forEach((k, v) -> categoryItems.add(k + " → " + v));

        // 🔹 Top - Folder Path Input
        HBox topBox = new HBox(10);
        topBox.setPadding(new Insets(10));
        pathField = new TextField();
        pathField.setPromptText("Select a folder...");
        Button browseBtn = new Button("Browse...");
        topBox.getChildren().addAll(pathField, browseBtn);

        browseBtn.setOnAction(e -> chooseFolder(stage));

        // 🔹 Left - Categories
        VBox categoryBox = new VBox(10);
        categoryBox.setPadding(new Insets(10));
        categoryBox.setPrefWidth(250);
        categoryBox.setStyle("-fx-border-color: #ccc; -fx-border-radius: 8; -fx-border-width: 1;");

        Label catLabel = new Label("Custom Categories");
        categoryList = new ListView<>(categoryItems);

        HBox catBtns = new HBox(10);
        Button addBtn = new Button("Add");
        Button editBtn = new Button("Edit");
        Button removeBtn = new Button("Remove");
        catBtns.getChildren().addAll(addBtn, editBtn, removeBtn);

        addBtn.setOnAction(e -> addCategory());
        editBtn.setOnAction(e -> editCategory());
        removeBtn.setOnAction(e -> removeCategory());

        categoryBox.getChildren().addAll(catLabel, categoryList, catBtns);

        // 🔹 Right - Logs
        VBox logBox = new VBox(10);
        logBox.setPadding(new Insets(10));
        Label logLabel = new Label("Logs");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(300);
        logBox.getChildren().addAll(logLabel, logArea);

        // 🔹 Bottom - Sort Button
        Button sortBtn = new Button("Sort Files");
        sortBtn.setMaxWidth(Double.MAX_VALUE);
        sortBtn.setStyle("-fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white;");

        sortBtn.setOnAction(e -> sortFiles());

        // 🔹 Main Layout
        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setLeft(categoryBox);
        root.setCenter(logBox);
        root.setBottom(sortBtn);

        Scene scene = new Scene(root, 800, 500);
        stage.setScene(scene);
        stage.show();
    }

    private void chooseFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Target Folder");
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            pathField.setText(selected.getAbsolutePath());
        }
    }

    private void addCategory() {
        TextInputDialog extDialog = new TextInputDialog();
        extDialog.setHeaderText("Add New Category");
        extDialog.setContentText("Enter file extension (without dot):");
        extDialog.showAndWait().ifPresent(ext -> {
            TextInputDialog folderDialog = new TextInputDialog();
            folderDialog.setHeaderText("Assign Folder");
            folderDialog.setContentText("Enter folder name for ." + ext + " files:");
            folderDialog.showAndWait().ifPresent(folder -> {
                customCategories.put(ext, folder);
                categoryItems.add(ext + " → " + folder);
                saveCategories();
            });
        });
    }

    private void editCategory() {
        String selected = categoryList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        String ext = selected.split(" → ")[0];

        TextInputDialog dialog = new TextInputDialog(customCategories.get(ext));
        dialog.setHeaderText("Edit Category");
        dialog.setContentText("New folder name for ." + ext + " files:");
        dialog.showAndWait().ifPresent(newFolder -> {
            customCategories.put(ext, newFolder);
            categoryItems.set(categoryList.getSelectionModel().getSelectedIndex(), ext + " → " + newFolder);
            saveCategories();
        });
    }

    private void removeCategory() {
        String selected = categoryList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        String ext = selected.split(" → ")[0];
        customCategories.remove(ext);
        categoryItems.remove(selected);
        saveCategories();
    }

    private void sortFiles() {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            showAlert("Error", "Please select a folder!");
            return;
        }
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            showAlert("Error", "Invalid directory!");
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile()) {
                String ext = getExtension(file.getName());
                // Check custom categories first
                if (customCategories.containsKey(ext)) {
                    moveFile(file, Paths.get(dir.getAbsolutePath(), customCategories.get(ext)));
                    continue;
                }
                // Otherwise, check default categories
                for (Map.Entry<String, String> entry : defaultCategories.entrySet()) {
                    String folder = entry.getKey();
                    String[] extensions = entry.getValue().split(",");
                    for (String dExt : extensions) {
                        if (dExt.trim().equalsIgnoreCase(ext) || dExt.trim().equals("*")) {
                            moveFile(file, Paths.get(dir.getAbsolutePath(), folder));
                            break; // stop checking once matched
                        }
                    }
                }
            }
        }
    }

    private void moveFile(File file, Path targetDir) {
        try {
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(file.getName());
            Files.move(file.toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            log("Moved " + file.getName() + " → " + targetDir);
        } catch (IOException e) {
            log("Error moving " + file.getName() + ": " + e.getMessage());
        }
    }


    private void log(String message) {
        logArea.appendText(message + "\n");
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String getExtension(String name) {
        int i = name.lastIndexOf('.');
        return (i > 0) ? name.substring(i + 1).toLowerCase() : "";
    }

    private void saveCategories() {
        JSONArray arr = new JSONArray();
        customCategories.forEach((ext, folder) -> {
            JSONObject obj = new JSONObject();
            obj.put("extension", ext);
            obj.put("folder", folder);
            arr.put(obj);
        });

        try (FileWriter writer = new FileWriter("categories.json")) {
            writer.write(arr.toString(2));
        } catch (IOException e) {
            log("Error saving categories: " + e.getMessage());
        }
    }

    private Map<String, String> loadCategories() {
        Map<String, String> map = new HashMap<>();
        File file = new File("categories.json");
        if (file.exists()) {
            try {
                String content = new String(Files.readAllBytes(file.toPath()));
                JSONArray arr = new JSONArray(content);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    map.put(obj.getString("extension"), obj.getString("folder"));
                }
            } catch (IOException e) {
                log("Error loading categories: " + e.getMessage());
            }
        }
        return map;
    }

    private static final Map<String, String> defaultCategories = new HashMap<>();
    static {
        defaultCategories.put("Documents", "pdf,doc,docx,txt,xls,xlsx,ppt,pptx,csv");
        defaultCategories.put("Images", "jpg,jpeg,png,gif,bmp,svg");
        defaultCategories.put("Videos", "mp4,mkv,avi,mov,flv");
        defaultCategories.put("Music", "mp3,wav,aac,flac,m4a");
        defaultCategories.put("Executables", "exe,msi,bat,sh,jar");
        defaultCategories.put("Archives", "zip,rar,7z,tar,gz");
    }

    public static void main(String[] args) {
        launch(args);
    }
}