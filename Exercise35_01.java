import javafx.application.Application; // Base class for JavaFX applications

import javafx.geometry.Insets;          // Adds padding around layouts
import javafx.geometry.Pos;             // Aligns nodes inside layout containers

import javafx.scene.Scene;              // Holds the UI tree
import javafx.scene.control.*;          // UI controls (Button, Label, TextArea, etc.)
import javafx.scene.layout.*;           // Layout containers (VBox, HBox, GridPane, etc.)

import javafx.stage.Modality;           // Makes dialog block the main window
import javafx.stage.Stage;              // Top-level JavaFX window

import java.sql.Connection;             // Represents an open DB connection
import java.sql.DatabaseMetaData;       // Reads DB/driver capabilities
import java.sql.DriverManager;          // Creates DB connections
import java.sql.PreparedStatement;      // SQL with parameters + batch support
import java.sql.Statement;              // Executes simple SQL commands
import java.sql.SQLException;           // DB error type

/**
 * Exercise 35.1
 *
 * Inserts 1000 rows into Temp(num1, num2, num3) and compares
 * elapsed time with batch vs non-batch inserts.
 */
public class Exercise35_01 extends Application {

    // Output area for messages and results
    private final TextArea taOutput = new TextArea();

    // Buttons required by the assignment
    private final Button btConnect  = new Button("Connect to Database");
    private final Button btBatch    = new Button("Batch Update");
    private final Button btNonBatch = new Button("Non-Batch Update");

    // Reused DB connection after user connects
    private Connection connection;

    // Driver capability flag
    private boolean supportsBatch = false;

    @Override
    public void start(Stage stage) {

        // Output setup
        taOutput.setEditable(false);
        taOutput.setWrapText(true);
        taOutput.setPrefRowCount(10);

        // Updates disabled until a DB connection exists
        btBatch.setDisable(true);
        btNonBatch.setDisable(true);

        // Button actions
        btConnect.setOnAction(e -> openConnectionDialog(stage));
        btBatch.setOnAction(e -> runBatchInsert());
        btNonBatch.setOnAction(e -> runNonBatchInsert());

        // Layout
        HBox buttonRow = new HBox(10, btBatch, btNonBatch, btConnect);
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.setPadding(new Insets(10, 0, 0, 0));

        VBox root = new VBox(10, taOutput, buttonRow);
        root.setPadding(new Insets(12));

        stage.setTitle("Exercise35_01");
        stage.setScene(new Scene(root, 520, 220));
        stage.show();

        log("Click 'Connect to Database' to begin.\n");
    }

    // Opens the DB dialog that collects connection info
    private void openConnectionDialog(Stage owner) {
        DBConnectionDialog dialog = new DBConnectionDialog(owner);
        dialog.showAndWait();

        if (dialog.getConnection() != null) {
            connection = dialog.getConnection();
            btBatch.setDisable(false);
            btNonBatch.setDisable(false);

            try {
                DatabaseMetaData meta = connection.getMetaData();
                supportsBatch = meta.supportsBatchUpdates();

                log("Connected to database.");
                log("Batch updates supported: " + supportsBatch + "\n");
            } catch (Exception ex) {
                log("Connected, but failed to read metadata: " + ex.getMessage() + "\n");
            }
        } else {
            log("Not connected.\n");
        }
    }

    // Batch insert using PreparedStatement + one commit
    private void runBatchInsert() {
        if (!ensureConnected()) return;

        if (!supportsBatch) {
            log("WARNING: Driver reports batch updates are NOT supported. Running anyway...\n");
        }

        final int ROWS = 1000;

        try {
            // Clear table outside the timed section
            try (Statement cleanup = connection.createStatement()) {
                cleanup.executeUpdate("TRUNCATE TABLE Temp");
            }

            // One transaction for the whole batch
            connection.setAutoCommit(false);

            long start = System.currentTimeMillis();

            String sql = "INSERT INTO Temp (num1, num2, num3) VALUES (?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {

                for (int i = 0; i < ROWS; i++) {
                    ps.setDouble(1, Math.random());
                    ps.setDouble(2, Math.random());
                    ps.setDouble(3, Math.random());
                    ps.addBatch(); // queues this insert
                }

                ps.executeBatch(); // runs queued inserts
            }

            connection.commit();

            long end = System.currentTimeMillis();
            log("Batch update succeeded");
            log("The elapsed time is " + (end - start) + " ms\n");

        } catch (Exception ex) {
            try { connection.rollback(); } catch (Exception ignored) {}
            log("Batch update failed: " + ex.getMessage() + "\n");
        } finally {
            try { connection.setAutoCommit(true); } catch (Exception ignored) {}
        }
    }

    // Non-batch insert using PreparedStatement, one execute at a time
    private void runNonBatchInsert() {
        if (!ensureConnected()) return;

        final int ROWS = 1000;

        try {
            // Clear table outside the timed section
            try (Statement cleanup = connection.createStatement()) {
                cleanup.executeUpdate("TRUNCATE TABLE Temp");
            }

            long start = System.currentTimeMillis();

            String sql = "INSERT INTO Temp (num1, num2, num3) VALUES (?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {

                for (int i = 0; i < ROWS; i++) {
                    ps.setDouble(1, Math.random());
                    ps.setDouble(2, Math.random());
                    ps.setDouble(3, Math.random());
                    ps.executeUpdate(); // runs one insert at a time
                }
            }

            long end = System.currentTimeMillis();
            log("Non-batch update completed");
            log("The elapsed time is " + (end - start) + " ms\n");

        } catch (Exception ex) {
            log("Non-batch update failed: " + ex.getMessage() + "\n");
        }
    }

    // Adds text to the TextArea
    private void log(String msg) {
        taOutput.appendText(msg + "\n");
    }

    // Ensures connection exists before running DB code
    private boolean ensureConnected() {
        if (connection == null) {
            log("Please connect to a database first.\n");
            return false;
        }
        return true;
    }

    // Close connection on exit
    @Override
    public void stop() {
        try {
            if (connection != null) connection.close();
        } catch (Exception ignored) {
        }
    }

    // Dialog window for DB connection info
    private static class DBConnectionDialog extends Stage {

        private Connection connection;
        private final DBConnectionPanel panel = new DBConnectionPanel();

        DBConnectionDialog(Stage owner) {
            initOwner(owner);
            initModality(Modality.WINDOW_MODAL);
            setTitle("Connect to DB");

            Button btConnect = new Button("Connect to DB");
            Button btClose = new Button("Close Dialog");
            Label lblStatus = new Label("");

            btConnect.setOnAction(e -> {
                try {
                    String driver = panel.getDriver().trim();
                    if (!driver.isEmpty()) {
                        Class.forName(driver);
                    }

                    connection = DriverManager.getConnection(
                            panel.getUrl().trim(),
                            panel.getUsername().trim(),
                            panel.getPassword()
                    );

                    lblStatus.setText("Connected to database.");
                } catch (Exception ex) {
                    connection = null;
                    lblStatus.setText("Connection failed: " + ex.getMessage());
                }
            });

            btClose.setOnAction(e -> close());

            HBox buttonRow = new HBox(10, btClose, btConnect);
            buttonRow.setAlignment(Pos.CENTER_RIGHT);

            VBox root = new VBox(10, panel, lblStatus, buttonRow);
            root.setPadding(new Insets(12));

            setScene(new Scene(root, 430, 220));
        }

        Connection getConnection() {
            return connection;
        }
    }

    // Panel that collects driver/url/username/password
    private static class DBConnectionPanel extends GridPane {

        private final ComboBox<String> cbDriver = new ComboBox<>();
        private final TextField tfUrl = new TextField();
        private final TextField tfUsername = new TextField();
        private final PasswordField pfPassword = new PasswordField();

        DBConnectionPanel() {
            setHgap(10);
            setVgap(10);

            cbDriver.getItems().addAll(
                    "com.mysql.cj.jdbc.Driver",
                    "com.mysql.jdbc.Driver"
            );
            cbDriver.setEditable(true);

            cbDriver.setValue("com.mysql.cj.jdbc.Driver");

            // Default URL for XAMPP + MySQL (adds rewriteBatchedStatements)
            tfUrl.setText(
                    "jdbc:mysql://localhost:3306/javabook?useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true"
            );

            tfUsername.setText("root");
            pfPassword.setText("");

            add(new Label("JDBC Driver"), 0, 0);
            add(cbDriver, 1, 0);

            add(new Label("Database URL"), 0, 1);
            add(tfUrl, 1, 1);

            add(new Label("Username"), 0, 2);
            add(tfUsername, 1, 2);

            add(new Label("Password"), 0, 3);
            add(pfPassword, 1, 3);

            GridPane.setHgrow(cbDriver, Priority.ALWAYS);
            GridPane.setHgrow(tfUrl, Priority.ALWAYS);
            GridPane.setHgrow(tfUsername, Priority.ALWAYS);
            GridPane.setHgrow(pfPassword, Priority.ALWAYS);
        }

        String getDriver() { return cbDriver.getEditor().getText(); }
        String getUrl() { return tfUrl.getText(); }
        String getUsername() { return tfUsername.getText(); }
        String getPassword() { return pfPassword.getText(); }
    }

    public static void main(String[] args) {
        launch(args);
    }
}