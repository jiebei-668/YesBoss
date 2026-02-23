package tech.yesboss.persistence.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Manual test class for debugging schema initialization.
 */
public class ManualSchemaTest {

    public static void main(String[] args) {
        try {
            SQLiteConnectionManager manager = SQLiteConnectionManager.inMemory();
            Connection connection = manager.getConnection();

            System.out.println("Connection created: " + connection);
            System.out.println("Auto-commit: " + connection.getAutoCommit());

            DatabaseInitializer initializer = new DatabaseInitializer(connection);
            System.out.println("\nInitializing schema...");

            // First, read and print the SQL script
            String sqlScript = readScript();
            System.out.println("SQL Script length: " + sqlScript.length() + " characters");
            System.out.println("First 200 chars:\n" + sqlScript.substring(0, Math.min(200, sqlScript.length())));

            initializer.initialize();

            System.out.println("\nSchema initialized successfully!");

            DatabaseMetaData meta = connection.getMetaData();
            System.out.println("\n=== Checking Tables ===");

            ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"});
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                System.out.println("Table: " + tableName);
            }
            tables.close();

            System.out.println("\n=== Checking Indexes ===");

            ResultSet indexes = meta.getIndexInfo(null, null, "task_session", false, false);
            System.out.println("\nIndexes on task_session:");
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                if (indexName != null && !indexName.startsWith("sqlite_")) {
                    System.out.println("  - " + indexName);
                }
            }
            indexes.close();

            System.out.println("\nTest completed successfully!");

            connection.close();
            manager.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String readScript() throws IOException {
        try (InputStream inputStream = DatabaseInitializer.class.getResourceAsStream("/db/migration/V1__init_schema.sql")) {
            if (inputStream == null) {
                throw new IOException("Migration script not found");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
