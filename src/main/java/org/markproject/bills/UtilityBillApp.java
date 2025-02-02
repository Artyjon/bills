package org.markproject.bills;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.*;
import java.time.Month;
import java.util.Comparator;
import java.util.Optional;

public class UtilityBillApp extends Application {
    private Connection connection;

    private static final String DB_FILE = "utility_bills.sql";

    private String getDatabasePath() {
        try {
            // Получаем путь к базе данных в ресурсе .jar
            URL resource = getClass().getClassLoader().getResource(DB_FILE);

            if (resource != null) {
                // Если база данных существует как ресурс, копируем её во временную директорию
                File tempFile = new File(System.getProperty("user.home"), "Library/Application Support/YourAppName/utility_bills.db");
                if (!tempFile.exists()) {
                    tempFile.getParentFile().mkdirs(); // Создаем директорию, если не существует
                    try (InputStream inputStream = resource.openStream();
                         OutputStream outputStream = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, length);
                        }
                    }
                }
                return tempFile.getAbsolutePath();
            } else {
                // Если база данных не существует, создаем её в рабочей директории
                String sqlPath = new File(DB_FILE).getAbsolutePath();
                copyDatabaseFromResources(sqlPath); // Копирование из ресурсов
                return sqlPath;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Ошибка получения пути к базе данных", e);
        }
    }

    private void copyDatabaseFromResources(String sqlPath) throws IOException {
        // Копирование базы данных из ресурсов, если она не существует
        File sqlFile = new File(sqlPath);
        if (!sqlFile.exists()) {
            try (InputStream sqlStream = getClass().getClassLoader().getResourceAsStream(DB_FILE);
                 OutputStream out = new FileOutputStream(sqlPath)) {
                if (sqlStream == null) {
                    throw new FileNotFoundException("Файл базы данных не найден в ресурсах!");
                }
                sqlStream.transferTo(out);
                System.out.println("Файл базы данных скопирован из ресурсов.");
            }
        } else {
            System.out.println("База данных уже существует, копирование не требуется.");
        }
    }


    private void connectDatabase() {
        try {
            // Получаем путь к базе данных
            String sqlPath = getDatabasePath();

            // Подключаемся к базе данных
            connection = DriverManager.getConnection("jdbc:sqlite:" + sqlPath);
            System.out.println("База данных успешно подключена.");

            // Создание таблиц, если они не существуют
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS Tariffs (" +
                             "cold REAL, hot REAL, sewer REAL, electricity_day REAL, electricity_night REAL)");
                stmt.execute("CREATE TABLE IF NOT EXISTS history (" +
                             "month TEXT PRIMARY KEY, cold_water REAL, hot_water REAL, sewer REAL, " +
                             "electricity_day REAL, electricity_night REAL, total REAL)");
                System.out.println("Таблицы успешно созданы.");
            }

            // Обновление структуры таблицы history (если нужно)
            updateHistoryTable();

            // Инициализация тарифов по умолчанию
            initializeDefaultTariffs();

        } catch (SQLException e) {
            System.err.println("Ошибка при подключении или работе с базой данных:");
            e.printStackTrace();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        // Вывод переменных среды
        System.out.println("Environment Variables:");
        System.getenv().forEach((key, value) -> {
            System.out.println(key + " = " + value);
        });

        // Вывод системных свойств (например, рабочая директория)
        System.out.println("\nSystem Properties:");
        System.getProperties().forEach((key, value) -> {
            System.out.println(key + " = " + value);
        });

        // Подключение к базе данных
        connectDatabase();

        // Создание вкладок
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); // Отключаем закрытие вкладок

        Tab tariffsTab = new Tab("Тарифы");
        tariffsTab.setContent(createTariffsTab());

        Tab calculationTab = new Tab("Расчет");
        calculationTab.setContent(createCalculationTab());

        Tab historyTab = new Tab("История");
        historyTab.setContent(createHistoryTab());

        tabPane.getTabs().addAll(tariffsTab, calculationTab, historyTab);

        // Создание сцены
        Scene scene = new Scene(tabPane, 1000, 600); // Начальный размер окна
        primaryStage.setScene(scene);
        primaryStage.setTitle("Коммунальные платежи");

        // Установка минимальных размеров окна
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        // Отображение окна
        primaryStage.show();
    }

    private void initializeDefaultTariffs() throws SQLException {
        String query = "SELECT COUNT(*) FROM Tariffs";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            rs.next();
            int count = rs.getInt(1);

            if (count == 0) {
                System.out.println("Тарифы не найдены. Устанавливаются значения по умолчанию.");

                String insertQuery = "INSERT INTO Tariffs (cold, hot, sewer, electricity_day, electricity_night) " +
                                     "VALUES (30.0, 50.0, 20.0, 4.5, 3.0)";

                try (Statement insertStmt = connection.createStatement()) {
                    int rowsAffected = insertStmt.executeUpdate(insertQuery);
                    if (rowsAffected > 0) {
                        System.out.println("Тарифы по умолчанию установлены.");
                    } else {
                        System.err.println("Ошибка при установке тарифов по умолчанию.");
                    }
                }
            } else {
                System.out.println("Тарифы уже существуют в базе данных.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Ошибка при инициализации тарифов: " + e.getMessage());
        }
    }

    private void calculateBill(ComboBox<Month> month, TextField cold, TextField hot, TextField sewer,
                               TextField electricityDay, TextField electricityNight, Label result) {
        try {
            // Проверяем ввод на корректность
            double coldValue = parseDouble(cold.getText());
            double hotValue = parseDouble(hot.getText());
            double sewerValue = parseDouble(sewer.getText());
            double electricityDayValue = parseDouble(electricityDay.getText());
            double electricityNightValue = parseDouble(electricityNight.getText());

            // Получаем тарифы
            double coldTariff = getTariff("cold");
            double hotTariff = getTariff("hot");
            double sewerTariff = getTariff("sewer");
            double electricityDayTariff = getTariff("electricity_day");
            double electricityNightTariff = getTariff("electricity_night");

            // Выполняем расчет
            double total = coldValue * coldTariff +
                           hotValue * hotTariff +
                           sewerValue * sewerTariff +
                           electricityDayValue * electricityDayTariff +
                           electricityNightValue * electricityNightTariff;

            result.setText("Общая сумма: " + total + " руб.");

            // Сохраняем расчет в историю
            saveToHistory(month.getValue().toString(), coldValue, hotValue, sewerValue,
                    electricityDayValue, electricityNightValue, total);

            showAlert("Расчет выполнен успешно!");
        } catch (NumberFormatException e) {
            showAlert("Пожалуйста, введите числовые значения для расчета.");
        } catch (SQLException e) {
            e.printStackTrace(); // Выводим стек ошибки в консоль
            showAlert("Ошибка при расчете платежей.");
        }
    }

    private double parseDouble(String value) {
        try {
            // Заменяем запятую на точку
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Неверный формат числа. Используйте точку или запятую как разделитель.");
        }
    }

    private GridPane createTariffsTab() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        Label coldLabel = new Label("ХВС (руб/м3):");
        TextField coldField = new TextField();
        Label hotLabel = new Label("ГВС (руб/м3):");
        TextField hotField = new TextField();
        Label sewerLabel = new Label("Водоотведение (руб/м3):");
        TextField sewerField = new TextField();
        Label electricityDayLabel = new Label("Электроэнергия (день, руб/кВт*ч):");
        TextField electricityDayField = new TextField();
        Label electricityNightLabel = new Label("Электроэнергия (ночь, руб/кВт*ч):");
        TextField electricityNightField = new TextField();

        Button saveButton = new Button("Сохранить");
        saveButton.setOnAction(e -> {
            saveTariffs(coldField, hotField, sewerField, electricityDayField, electricityNightField);
            // Убираем showAlert отсюда, так как он уже есть в методе saveTariffs
        });

        grid.add(coldLabel, 0, 0);
        grid.add(coldField, 1, 0);
        grid.add(hotLabel, 0, 1);
        grid.add(hotField, 1, 1);
        grid.add(sewerLabel, 0, 2);
        grid.add(sewerField, 1, 2);
        grid.add(electricityDayLabel, 0, 3);
        grid.add(electricityDayField, 1, 3);
        grid.add(electricityNightLabel, 0, 4);
        grid.add(electricityNightField, 1, 4);
        grid.add(saveButton, 1, 5);

        return grid;
    }

    private GridPane createCalculationTab() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        Label monthLabel = new Label("Месяц:");
        ComboBox<Month> monthComboBox = new ComboBox<>();
        monthComboBox.getItems().addAll(Month.values());

        Label coldLabel = new Label("ХВС (м3):");
        TextField coldField = new TextField();
        Label hotLabel = new Label("ГВС (м3):");
        TextField hotField = new TextField();
        Label sewerLabel = new Label("Водоотведение (м3):");
        TextField sewerField = new TextField();
        Label electricityDayLabel = new Label("Электроэнергия (день, кВт*ч):");
        TextField electricityDayField = new TextField();
        Label electricityNightLabel = new Label("Электроэнергия (ночь, кВт*ч):");
        TextField electricityNightField = new TextField();
        Label resultLabel = new Label();

        Button calculateButton = new Button("Рассчитать");
        calculateButton.setOnAction(e -> {
            calculateBill(monthComboBox, coldField, hotField, sewerField, electricityDayField, electricityNightField, resultLabel);
            // Убираем showAlert отсюда, так как он уже есть в методе calculateBill
        });

        grid.add(monthLabel, 0, 0);
        grid.add(monthComboBox, 1, 0);
        grid.add(coldLabel, 0, 1);
        grid.add(coldField, 1, 1);
        grid.add(hotLabel, 0, 2);
        grid.add(hotField, 1, 2);
        grid.add(sewerLabel, 0, 3);
        grid.add(sewerField, 1, 3);
        grid.add(electricityDayLabel, 0, 4);
        grid.add(electricityDayField, 1, 4);
        grid.add(electricityNightLabel, 0, 5);
        grid.add(electricityNightField, 1, 5);
        grid.add(calculateButton, 1, 6);
        grid.add(resultLabel, 1, 7);

        return grid;
    }

    private Node createHistoryTab() {
        TableView<HistoryRecord> tableView = new TableView<>();
        tableView.setStyle("-fx-font-size: 14px;");

        // Колонка "Месяц" с сортировкой по календарному порядку
        TableColumn<HistoryRecord, String> monthColumn = new TableColumn<>("Месяц");
        monthColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getLocalizedMonth()));
        monthColumn.setComparator(Comparator.comparingInt(r -> Month.valueOf(r.toUpperCase()).getValue()));
        monthColumn.setPrefWidth(100);

        // Остальные колонки
        TableColumn<HistoryRecord, Double> coldColumn = new TableColumn<>("ХВС (м³)");
        coldColumn.setCellValueFactory(data -> data.getValue().coldWaterProperty().asObject());
        coldColumn.setPrefWidth(100);

        TableColumn<HistoryRecord, Double> hotColumn = new TableColumn<>("ГВС (м³)");
        hotColumn.setCellValueFactory(data -> data.getValue().hotWaterProperty().asObject());
        hotColumn.setPrefWidth(100);

        TableColumn<HistoryRecord, Double> sewerColumn = new TableColumn<>("Водоотведение (м³)");
        sewerColumn.setCellValueFactory(data -> data.getValue().sewerProperty().asObject());
        sewerColumn.setPrefWidth(120);

        TableColumn<HistoryRecord, Double> electricityDayColumn = new TableColumn<>("Электроэнергия (кВт⋅ч) Дн.");
        electricityDayColumn.setCellValueFactory(data -> data.getValue().electricityDayProperty().asObject());
        electricityDayColumn.setPrefWidth(150);

        TableColumn<HistoryRecord, Double> electricityNightColumn = new TableColumn<>("Электроэнергия (кВт⋅ч) Ноч.");
        electricityNightColumn.setCellValueFactory(data -> data.getValue().electricityNightProperty().asObject());
        electricityNightColumn.setPrefWidth(150);

        TableColumn<HistoryRecord, Double> totalColumn = new TableColumn<>("Сумма (руб.)");
        totalColumn.setCellValueFactory(data -> data.getValue().totalProperty().asObject());
        totalColumn.setPrefWidth(100);

        // Колонка с кнопкой удаления
        TableColumn<HistoryRecord, Void> actionColumn = new TableColumn<>("Действие");
        actionColumn.setPrefWidth(100);
        actionColumn.setCellFactory(param -> new TableCell<>() {
            private final Button deleteButton = new Button("Удалить");

            {
                deleteButton.setOnAction(event -> {
                    HistoryRecord r = getTableView().getItems().get(getIndex());
                    showDeleteConfirmation(r, tableView);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });

        tableView.getColumns().addAll(
                monthColumn, coldColumn, hotColumn, sewerColumn,
                electricityDayColumn, electricityNightColumn, totalColumn, actionColumn
        );

        // Кнопка "Обновить"
        Button refreshButton = new Button("Обновить");
        refreshButton.setOnAction(e -> tableView.setItems(loadHistoryData()));

        VBox layout = new VBox(10, tableView, refreshButton);
        layout.setPadding(new Insets(10));
        return layout;
    }

    private void showDeleteConfirmation(HistoryRecord r, TableView<HistoryRecord> tableView) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Подтверждение удаления");
        alert.setHeaderText("Вы точно хотите удалить запись?");
        alert.setContentText("Месяц: " + r.getMonth());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            deleteRecordFromDatabase(r);
            tableView.getItems().remove(r); // Удаляем запись из таблицы
        }
    }

    private void deleteRecordFromDatabase(HistoryRecord r) {
        String sqlPath = getDatabasePath();
        String query = "DELETE FROM history WHERE month = ?";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqlPath);
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, r.getMonth());
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Запись удалена: " + r.getMonth());
            } else {
                System.err.println("Запись с таким месяцем не найдена.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Ошибка удаления записи: " + e.getMessage());
        }
    }

    private ObservableList<HistoryRecord> loadHistoryData() {
        ObservableList<HistoryRecord> history = FXCollections.observableArrayList();
        String sqlPath = getDatabasePath();
        File sqlFile = new File(sqlPath);

        if (!sqlFile.exists()) {
            System.err.println("Файл базы данных не найден: " + sqlPath);
            return history;
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqlPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM history ORDER BY strftime('%m', month || '-01') DESC")) {

            while (rs.next()) {
                history.add(new HistoryRecord(
                        rs.getString("month"),
                        rs.getDouble("cold_water"),
                        rs.getDouble("hot_water"),
                        rs.getDouble("sewer"),
                        rs.getDouble("electricity_day"),
                        rs.getDouble("electricity_night"),
                        rs.getDouble("total")
                ));
            }
            System.out.println("Данные истории успешно загружены.");
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Ошибка загрузки истории: " + e.getMessage());
        }
        return history;
    }

    private void saveTariffs(TextField cold, TextField hot, TextField sewer, TextField electricityDay, TextField electricityNight) {
        try {
            // Проверяем ввод на корректность
            double coldValue = parseDouble(cold.getText());
            double hotValue = parseDouble(hot.getText());
            double sewerValue = parseDouble(sewer.getText());
            double electricityDayValue = parseDouble(electricityDay.getText());
            double electricityNightValue = parseDouble(electricityNight.getText());

            // Если все данные корректны, сохраняем их
            PreparedStatement checkStmt = connection.prepareStatement("SELECT COUNT(*) FROM Tariffs");
            ResultSet rs = checkStmt.executeQuery();
            rs.next();
            boolean exists = rs.getInt(1) > 0;
            rs.close();

            PreparedStatement stmt;
            if (exists) {
                stmt = connection.prepareStatement("UPDATE Tariffs SET cold=?, hot=?, sewer=?, electricity_day=?, electricity_night=?");
            } else {
                stmt = connection.prepareStatement("INSERT INTO Tariffs VALUES (?, ?, ?, ?, ?)");
            }

            stmt.setDouble(1, coldValue);
            stmt.setDouble(2, hotValue);
            stmt.setDouble(3, sewerValue);
            stmt.setDouble(4, electricityDayValue);
            stmt.setDouble(5, electricityNightValue);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                showAlert("Тарифы успешно сохранены!");
            } else {
                showAlert("Ошибка при сохранении тарифов.");
            }
        } catch (NumberFormatException e) {
            showAlert("Пожалуйста, введите числовые значения для тарифов.");
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Ошибка при сохранении тарифов.");
        }
    }

    private void saveToHistory(String month, double cold, double hot, double sewer,
                               double electricityDay, double electricityNight, double total) throws SQLException {
        String sqlUpdate = "UPDATE history SET cold_water = ?, hot_water = ?, sewer = ?, electricity_day = ?, electricity_night = ?, total = ? WHERE month = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sqlUpdate)) {
            stmt.setDouble(1, cold);
            stmt.setDouble(2, hot);
            stmt.setDouble(3, sewer);
            stmt.setDouble(4, electricityDay);
            stmt.setDouble(5, electricityNight);
            stmt.setDouble(6, total);
            stmt.setString(7, month);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected == 0) { // Если обновление не затронуло строки
                String sqlInsert = "INSERT INTO history (month, cold_water, hot_water, sewer, electricity_day, electricity_night, total) " +
                                   "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement insertStmt = connection.prepareStatement(sqlInsert)) {
                    insertStmt.setString(1, month);
                    insertStmt.setDouble(2, cold);
                    insertStmt.setDouble(3, hot);
                    insertStmt.setDouble(4, sewer);
                    insertStmt.setDouble(5, electricityDay);
                    insertStmt.setDouble(6, electricityNight);
                    insertStmt.setDouble(7, total);
                    insertStmt.executeUpdate();
                }
            } else {
                System.out.println("Данные успешно обновлены.");
            }
        }
    }

    private void updateHistoryTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Проверяем, существует ли столбец total в таблице history
            ResultSet rs = connection.getMetaData().getColumns(null, null, "history", "total");
            if (!rs.next()) {
                System.out.println("Столбец 'total' не найден. Добавляем его...");
                stmt.execute("ALTER TABLE history ADD COLUMN total REAL");
                System.out.println("Столбец 'total' успешно добавлен.");
            } else {
                System.out.println("Столбец 'total' уже существует.");
            }
        }
    }

    private double getTariff(String type) throws SQLException {
        String query = "SELECT " + type + " FROM Tariffs";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            if (!rs.next()) {
                throw new SQLException("Тарифы не найдены. Пожалуйста, установите тарифы.");
            }
            double tariff = rs.getDouble(1);
            if (tariff <= 0) {
                throw new SQLException("Некорректное значение тарифа для типа: " + type);
            }
            return tariff;
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Информация");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }


    public static void main(String[] args) {
        System.setProperty("prism.order", "sw");  // Использование программного рендеринга
        System.setProperty("prism.verbose", "true");

        launch(args);
    }
}
