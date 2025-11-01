package com.aleksandrmakarov.journals.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.TestPropertySource;

/**
 * Test database initializer that automatically creates the test database before Spring context loads.
 * This uses a static initializer to ensure the database exists before DataSource initialization.
 */
@TestConfiguration
@TestPropertySource(properties = {"spring.test.database.replace=none"})
public class TestDatabaseInitializer {

  private static final String DB_NAME = "test_journals";
  private static volatile boolean initialized = false;

  static {
    initializeDatabase();
  }

  private static synchronized void initializeDatabase() {
    if (initialized) {
      return;
    }

    String username = System.getProperty("DB_USERNAME");
    if (username == null) {
      username = System.getenv("DB_USERNAME");
    }
    if (username == null) {
      username = "postgres";
    }

    String password = System.getProperty("DB_PASSWORD");
    if (password == null) {
      password = System.getenv("DB_PASSWORD");
    }
    if (password == null) {
      password = "postgres";
    }

    String postgresUrl = "jdbc:postgresql://localhost:5432/postgres";

    try (Connection conn = DriverManager.getConnection(postgresUrl, username, password);
        Statement stmt = conn.createStatement()) {

      // Check if test database exists
      try (ResultSet rs =
          stmt.executeQuery(
              "SELECT 1 FROM pg_database WHERE datname = '" + DB_NAME + "'")) {
        if (!rs.next()) {
          // Database doesn't exist, create it
          stmt.executeUpdate("CREATE DATABASE " + DB_NAME);
          System.out.println("✅ Created test database: " + DB_NAME);
        } else {
          System.out.println("✅ Test database already exists: " + DB_NAME);
        }
      }
      initialized = true;
    } catch (SQLException e) {
      throw new RuntimeException(
          "❌ Failed to create test database '" + DB_NAME + 
          "'. " + e.getMessage() + 
          " (Check your PostgreSQL server is running and that your user '" + username +
          "' has permission to create databases.)", e);
    }
  }
}

