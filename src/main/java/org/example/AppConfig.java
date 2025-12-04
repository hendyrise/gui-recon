package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {

  private static final Properties PROPERTIES = new Properties();

  static {
    try (InputStream is = AppConfig.class.getClassLoader()
      .getResourceAsStream("application.properties")) {

      if (is == null) {
        throw new RuntimeException("application.properties not found!");
      }

      PROPERTIES.load(is);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load application.properties", e);
    }
  }

  public static String getProperties(String key) {
    return PROPERTIES.getProperty(key);
  }
}
