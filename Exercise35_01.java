import javafx.application.Application; //Base import for all JavaFX apps

// For spacing and alignments
import javafx.geometry.Insets;
import javafx.geometry.Pos;

// UI Controls
import javafx.scene.Scene;
import javafx.scene.control.*; // Import all common JavaFX controls

import javafx.stage.Modality; // Helps with window behavior ( current window must close before returning to main window )

import javafx.scene.layout.*; // Import all JavaFX layout containers: VBox, HBox, GridPane, BorderPane, etc

import javafx.stage.Stage; // Top-Level window

// JDBC (Database) Imports
import java.sql.Connection; // An active connection/communication chanel between program and database
import java.sql.DatabaseMetaData; // Information about database and drivers (like if updates are supported)
import java.sql.DriverManager; // Connects database via JDBC URL, Username, and password
import java.sql.Statement; // Used to send SQL commands (INSERT, DELETE, etc...)

/**
 **This Program inserts 1000 records into Temp(num1, num2, num3) using batch processing
 **BATCH PROCESSING: Handling many operations together, instead of processing one at a time
 */

public class Exercise35_01 extends Application {

    // UI CONTROLS FOR MAIN WINDOW
    // Prints status messages and elapsed time results in text area
    private final TextArea taOutput = new TextArea();

    // Required Buttons
    private final Button btConnect = new Button("Connect to Database");
    private final Button btBatch = new Button("Batch Update");
    private final Button btNonBatch = new Button("Non-Batch Update");

    // Keeps connection open after user connects in the dialog.
    // Database runs operations without reconnecting each time.
    private Connection connection;

    // Store whether the DB supports batch updates
    private boolean supportsBatch = false;

    @Override
    public void start(Stage stage) {

        // OUTPUT WINDOW UI
        taOutput.setEditable(false);
        taOutput.setWrapText(true);
        taOutput.setPrefRowCount(10);

        // At launch, users do NOT run updates until connected
        btBatch.setDisable(true);
        btNonBatch.setDisable(true);

        // BUTTON CLICK BEHAVIOR

        // Clicking connect opens the window
        btConnect.setOnAction(e -> openConnectionDialog(stage));

        // Clicking batch update runs the fast batch version
        btBatch.setOnAction(e -> runBatchInsert());

        // Clicking non-batch runs program one-by-one
        btNonBatch.setOnAction(e -> runNonBatchInsert());

        // WINDOW LAYOUT
        HBox buttonRow = new HBox(10, btBatch, btNonBatch, btConnect);
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.setPadding(new Insets(10, 0, 0, 0));

        VBox root = new VBox(10, taOutput, buttonRow);
        root.setPadding(new Insets(12));

        stage.setTitle("Exercise35_01");
        stage.setScene(new Scene(root, 520, 220));
        stage.show();

        // Friendly startup message
        log("Click 'Connect to Database' to begin.\n");
    }

    // Opens connection dialog that contains DBConnectionPanel.
    private void openConnectionDialog(Stage owner) {
        DBConnectionDialog dialog = new DBConnectionDialog(owner);

        // Shows dialog until user closes window
        dialog.showAndWait();

        // If user successfully connected, retrieve the connection
        if (dialog.getConnection() != null) {
            connection = dialog.getConnection();

            // Once connected, enable the update buttons
            btBatch.setDisable(false);
            btNonBatch.setDisable(false);

            // Ask the database what it supports
            try {
                DatabaseMetaData meta = connection.getMetaData();
                supportsBatch = meta.supportsBatchUpdates();

                log("Connected to database.");
                log("Batch updates supported: " + supportsBatch + "\n");
            } catch (Exception ex) {
                log("Connected, but failed to read data: " + ex.getMessage() + "\n");
            }
        } else {
            log("Not connected.\n");
        }
    }

    // Runs with BATCH, and collects eclipse time

    private void runBatchInsert() {
        if (!ensureConnected()) return;

        // If DB/driver doesn't support batch, we can still try but warn the user.
        if (!supportsBatch) {
            log("WARNING: Driver reports batch updates are NOT supported. Running anyway...\n");
        }

        // We'll insert exactly 1000 rows as the exercise requires
        final int ROWS = 1000;

        // We time the operation using System.currentTimeMillis()
        long start = System.currentTimeMillis();

        try (Statement stmt = connection.createStatement()) {

            // Optional cleanup:
            // We delete all existing rows so each test is fair and comparable.
            stmt.executeUpdate("DELETE FROM Temp");

            // We'll build an INSERT statement string for each record.
            // The assignment says use Math.random() for each record.
            for (int i = 0; i < ROWS; i++) {

                double n1 = Math.random();
                double n2 = Math.random();
                double n3 = Math.random();

                // We build SQL text for each row.
                // Example: INSERT INTO Temp VALUES (0.12, 0.45, 0.78)
                String sql = "INSERT INTO Temp (num1, num2, num3) VALUES (" +
                        n1 + ", " + n2 + ", " + n3 + ")";

                // Instead of executing now, we add it to the batch “queue”
                stmt.addBatch(sql);
            }

            // This sends all queued SQL inserts to the database at once
            stmt.executeBatch();

            long end = System.currentTimeMillis();
            long elapsed = end - start;

            log("Batch update succeeded");
            log("The elapsed time is " + elapsed + " ms\n");

        } catch (Exception ex) {
            log("Batch update failed: " + ex.getMessage() + "\n");
        }
    }

    // Runs without BATCH, and collects eclipse time
    private void runNonBatchInsert() {
        if (!ensureConnected()) return;

        final int ROWS = 1000;
        long start = System.currentTimeMillis();

        try (Statement stmt = connection.createStatement()) {

            // Cleanup for a fair comparison
            stmt.executeUpdate("DELETE FROM Temp");

            for (int i = 0; i < ROWS; i++) {

                double n1 = Math.random();
                double n2 = Math.random();
                double n3 = Math.random();

                String sql = "INSERT INTO Temp (num1, num2, num3) VALUES (" +
                        n1 + ", " + n2 + ", " + n3 + ")";

                // This sends ONE SQL command at a time.
                // That means 1000 separate database “round trips.”
                stmt.executeUpdate(sql);
            }

            long end = System.currentTimeMillis();
            long elapsed = end - start;

            log("Non-batch update completed");
            log("The elapsed time is " + elapsed + " ms\n");

        } catch (Exception ex) {
            log("Non-batch update failed: " + ex.getMessage() + "\n");
        }
    }

    // UTILITY: Adds text to the TextArea output.
    private void log(String msg) {
        taOutput.appendText(msg + "\n");
    }

    // DEFENSIVE CHECK: ensures connection exists before running DB logic.
    private boolean ensureConnected() {
        if (connection == null) {
            log("Please connect to a database first.\n");
            return false;
        }
        return true;
    }

    // Close the database connection when the app exits.
    @Override
    public void stop() {
        try {
            if (connection != null) connection.close();
        } catch (Exception ignored) {
        }
    }

    // DBConnectionDialog + DBConnectionPanel
    private static class DBConnectionDialog extends Stage {

        private Connection connection; // set after successful connection

        // The "panel" holding the text fields
        private final DBConnectionPanel panel = new DBConnectionPanel();

        DBConnectionDialog(Stage owner) {
            initOwner(owner);

            // User must close it before returning to main window
            initModality(Modality.WINDOW_MODAL);

            setTitle("Connect to DB");

            // Buttons inside the dialog
            Button btConnect = new Button("Connect to DB");
            Button btClose = new Button("Close Dialog");

            // This label shows connection status (Connected / Error message)
            Label lblStatus = new Label("");

            // When user clicks Connect:
            btConnect.setOnAction(e -> {
                try {
                    // Load the driver class if provided (MySQL driver is commonly required)
                    // If driver class is empty, we skip loading and attempt connection directly.
                    String driver = panel.getDriver().trim();
                    if (!driver.isEmpty()) {
                        Class.forName(driver);
                    }

                    // Open the connection using URL, username, password
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

            // Close button simply closes the dialog window
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

    // DBConnectionPanel: reusable panel that collects DB connection details.
    private static class DBConnectionPanel extends GridPane {

        // UI fields used by the dialog
        private final ComboBox<String> cbDriver = new ComboBox<>();
        private final TextField tfUrl = new TextField();
        private final TextField tfUsername = new TextField();
        private final PasswordField pfPassword = new PasswordField();

        DBConnectionPanel() {
            setHgap(10);
            setVgap(10);

            // Populates driver options
            cbDriver.getItems().addAll(
                    "com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver"
            );
            cbDriver.setEditable(true);

            // Provide sensible defaults (for xampp)
            cbDriver.setValue("com.mysql.cj.jdbc.Driver");
            tfUrl.setText("jdbc:mysql://localhost:3306/javabook?useSSL=false&serverTimezone=UTC");
            tfUsername.setText("root");
            pfPassword.setText("");

            // Build panel rows
            add(new Label("JDBC Driver"), 0, 0);
            add(cbDriver, 1, 0);

            add(new Label("Database URL"), 0, 1);
            add(tfUrl, 1, 1);

            add(new Label("Username"), 0, 2);
            add(tfUsername, 1, 2);

            add(new Label("Password"), 0, 3);
            add(pfPassword, 1, 3);

            // Make input fields stretch nicely
            GridPane.setHgrow(cbDriver, Priority.ALWAYS);
            GridPane.setHgrow(tfUrl, Priority.ALWAYS);
            GridPane.setHgrow(tfUsername, Priority.ALWAYS);
            GridPane.setHgrow(pfPassword, Priority.ALWAYS);
        }

        // Getter methods so the dialog can read user inputs cleanly
        String getDriver() { return cbDriver.getEditor().getText(); }
        String getUrl() { return tfUrl.getText(); }
        String getUsername() { return tfUsername.getText(); }
        String getPassword() { return pfPassword.getText(); }
    }

    public static void main(String[] args) {
        launch(args);
    }

}
