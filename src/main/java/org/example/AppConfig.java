package org.example;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {

  private static final Properties PROPERTIES = new Properties();

  private static final String FAILED_TO_LOAD_APPLICATION_PROPERTIES = "Failed to load application.properties";

  static {
    String externalPath = System.getProperty("config.path");
    if (externalPath != null) {
      try (InputStream fis = new FileInputStream(externalPath)) {
        PROPERTIES.load(fis);
      } catch (IOException e) {
        throw new RuntimeException(FAILED_TO_LOAD_APPLICATION_PROPERTIES, e);
      }
    }
  }


  public static String getProperties(String key) {
    return PROPERTIES.getProperty(key);
  }
}
