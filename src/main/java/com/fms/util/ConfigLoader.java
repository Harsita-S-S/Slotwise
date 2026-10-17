package com.fms.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to load application and database configurations from properties files.
 * Aligns with NFR-04 (externalized database configuration).
 */
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final Properties properties = new Properties();
    private static final String DEFAULT_CONFIG_PATH = "com/fms/config/db.properties";

    static {
        loadProperties(DEFAULT_CONFIG_PATH);
    }

    private static synchronized void loadProperties(String resourcePath) {
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                logger.warn("Unable to find resource file: {}", resourcePath);
                // Also attempt loading from /db.properties or config/db.properties
                try (InputStream fallback = ConfigLoader.class.getClassLoader().getResourceAsStream("db.properties")) {
                    if (fallback != null) {
                        properties.load(fallback);
                        logger.info("Loaded configuration from fallback path: db.properties");
                    }
                }
            } else {
                properties.load(input);
                logger.info("Configuration successfully loaded from: {}", resourcePath);
            }
        } catch (IOException ex) {
            logger.error("Failed to load configuration properties from: {}", resourcePath, ex);
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static int getIntProperty(String key, int defaultValue) {
        String val = properties.getProperty(key);
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for key '{}': '{}'. Using default: {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    public static long getLongProperty(String key, long defaultValue) {
        String val = properties.getProperty(key);
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for key '{}': '{}'. Using default: {}", key, val, defaultValue);
            return defaultValue;
        }
    }
}
