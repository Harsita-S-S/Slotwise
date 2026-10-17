package com.fms.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton database connection manager utilizing HikariCP connection pooling.
 * Fulfills:
 * - NFR-04: Credentials loaded externally via ConfigLoader.
 * - NFR-06 & FR-VAL-04: Graceful connection recovery and retry support.
 * - NFR-09: Scalability through high-performance connection pooling.
 * - FR-VAL-03: Transaction management support.
 */
public class DBConnection {
    private static final Logger logger = LoggerFactory.getLogger(DBConnection.class);
    private static volatile DBConnection instance;
    private final HikariDataSource dataSource;

    private DBConnection() {
        try {
            HikariConfig config = new HikariConfig();
            
            String jdbcUrl = ConfigLoader.getProperty("db.url", 
                "jdbc:mysql://localhost:3306/fms_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            String username = ConfigLoader.getProperty("db.user", "root");
            String password = ConfigLoader.getProperty("db.password", "");
            
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // HikariCP Performance & Pool Tuning
            int maxPoolSize = ConfigLoader.getIntProperty("db.pool.maxSize", 10);
            int minIdle = ConfigLoader.getIntProperty("db.pool.minIdle", 2);
            long connTimeout = ConfigLoader.getLongProperty("db.pool.connectionTimeout", 15000);
            long idleTimeout = ConfigLoader.getLongProperty("db.pool.idleTimeout", 600000);
            long maxLifetime = ConfigLoader.getLongProperty("db.pool.maxLifetime", 1800000);

            config.setMaximumPoolSize(maxPoolSize);
            config.setMinimumIdle(minIdle);
            config.setConnectionTimeout(connTimeout);
            config.setIdleTimeout(idleTimeout);
            config.setMaxLifetime(maxLifetime);
            config.setPoolName("FMS-HikariPool");

            // Recommended MySQL optimization flags for HikariCP
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");

            this.dataSource = new HikariDataSource(config);
            logger.info("HikariCP connection pool initialized successfully. Pool: {}, MaxSize: {}", 
                config.getPoolName(), maxPoolSize);

        } catch (Exception ex) {
            logger.error("Failed to initialize HikariCP connection pool: {}", ex.getMessage(), ex);
            throw new RuntimeException("Database connection pool initialization failed.", ex);
        }
    }

    /**
     * Retrieves the singleton DBConnection instance (Thread-safe Double-Checked Locking).
     */
    public static DBConnection getInstance() {
        if (instance == null) {
            synchronized (DBConnection.class) {
                if (instance == null) {
                    instance = new DBConnection();
                }
            }
        }
        return instance;
    }

    /**
     * Obtains a connection from the pool.
     *
     * @return Connection object
     * @throws SQLException if a connection cannot be acquired
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("HikariCP DataSource is closed or uninitialized.");
        }
        return dataSource.getConnection();
    }

    /**
     * Gets the underlying DataSource.
     */
    public DataSource getDataSource() {
        return this.dataSource;
    }

    /**
     * Tests if the database is reachable and active.
     *
     * @return true if connection succeeds, false otherwise
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            logger.error("Database connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gracefully shuts down the connection pool upon application exit.
     */
    public static synchronized void closePool() {
        if (instance != null && instance.dataSource != null && !instance.dataSource.isClosed()) {
            logger.info("Closing HikariCP connection pool...");
            instance.dataSource.close();
            instance = null;
        }
    }

    /**
     * Functional interface for running database operations inside a managed transaction.
     * Fulfills FR-VAL-03 and BR-03 (all succeed or none do).
     */
    @FunctionalInterface
    public interface TransactionOperation<T> {
        T execute(Connection conn) throws Exception;
    }

    /**
     * Executes an operation inside a managed transaction with automatic rollback on error.
     */
    public static <T> T executeInTransaction(TransactionOperation<T> operation) throws Exception {
        try (Connection conn = getInstance().getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                T result = operation.execute(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                logger.error("Transaction failed, rolling back: {}", e.getMessage());
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    logger.error("Failed to rollback transaction: {}", rollbackEx.getMessage(), rollbackEx);
                }
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }
}
