package org.example;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TransactionService {

  private static final String CANNOT_CONSTRUCT_REF_FROM_RISE_FILE = "Cannot Construct Ref from rise file [%s]";
  private static final String DELLIMITER = AppConfig.getProperties("app.delimiter");
  private static final String FAILED_TO_READ_PROVIDER_FILE = "Failed to Read provider file[%s]";
  private static final String PROVIDER_COUNT_HEADER_CONFIG = "provider.%s.header";
  private static final String PROVIDER_PROVIDER_REF_ID_PATH_CONFIG = "provider.%s.provider-ref-id-path";
  private static final String PROVIDER_SKIP_HEADER_CONFIG = "provider.%s.skip-header";
  private static final String RESULT_FILE_FORMAT = "%s\n";
  private static final String COMPARISON_COMPLETE_PLEASE_CHECK_IN_FOLDER_DOWNLOAD_WITH_FILE_NAME = "Comparison complete please check in folder download with file name [%s]";
  private static final String PROVIDER_RUNNING_ID_CONFIG = "provider.%s.running-id-path";
  private static final String PROVIDER_STATUS_CONFIG = "provider.%s.status-path";
  private static final String PROVIDER_STATUS_MESSAGE_CONFIG = "provider.%s.status-message";
  private static final String RESULT_FILE_PATH = "%s/%s";
  private static final int RISE_RUNNING_ID_PATH = 8;
  private static final int PROVIDER_REF_PATH = 12;
  private static final String WRONG_FORMAT_DETECTED_FOR_THIS_PROVIDER = "Wrong format Detected for this provider[%s]";
  private static final String ZERO_WITH_NO_BREAK_SPACE_FORMAT = "\uFEFF";

  public TransactionService() {
  }

  private Set<String> extractTransactionIdFromRise(File file, int columnIndex) throws Exception {
    Set<String> ids = new HashSet<>();

    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      String line;
      br.readLine();
      while ((line = br.readLine()) != null) {
        String[] parts = line.trim().split(DELLIMITER);
        ids.add(parts[columnIndex].trim());
      }
    }
    return ids;
  }

  public String generateResultFile(File providerFile, File riseFile, String selectedProvider, String fileLocation) {
    final String runningIdPath = AppConfig.getProperties(String.format(PROVIDER_RUNNING_ID_CONFIG, selectedProvider));
    final String statusPath = AppConfig.getProperties(String.format(PROVIDER_STATUS_CONFIG, selectedProvider));
    final int skipHeader = Integer.parseInt(
      AppConfig.getProperties(String.format(PROVIDER_SKIP_HEADER_CONFIG, selectedProvider)));
    final String header = AppConfig.getProperties(String.format(PROVIDER_COUNT_HEADER_CONFIG, selectedProvider));
    final String statusMessage = AppConfig.getProperties(
      String.format(PROVIDER_STATUS_MESSAGE_CONFIG, selectedProvider));
    final String providerRefIdPath = AppConfig.getProperties(
      String.format(PROVIDER_PROVIDER_REF_ID_PATH_CONFIG, selectedProvider));

    final ProviderConfig providerConfig = new ProviderConfig(runningIdPath, statusPath, statusMessage, skipHeader,
      header, providerRefIdPath);
    try {
      final Set<String> riseRefIds = extractTransactionIdFromRise(riseFile, validateRefPath(providerConfig));
      return compareRunningId(providerFile, riseRefIds, providerConfig, fileLocation);
    } catch (Exception e) {
      return String.format(CANNOT_CONSTRUCT_REF_FROM_RISE_FILE, riseFile.getName());
    }
  }

  private int validateRefPath(ProviderConfig providerConfig) {
    return providerConfig.runningIdPath().isBlank() ? PROVIDER_REF_PATH : RISE_RUNNING_ID_PATH;
  }

  private boolean validateHeader(String configHeader, String header) {
    final String finalHeader = Arrays.stream(header.toLowerCase().split(DELLIMITER))
      .map(data -> data.trim().replace(ZERO_WITH_NO_BREAK_SPACE_FORMAT, ""))
      .collect(Collectors.joining(",")).trim();
    return !configHeader.equalsIgnoreCase(finalHeader);
  }

  private String compareRunningId(File providerFile, Set<String> refIds, ProviderConfig providerConfig,
    String fileLocation) {
    final File resultFile = new File(
      String.format(RESULT_FILE_PATH, fileLocation, providerFile.getName().replace(".csv", "-result.csv")));

    try (BufferedReader br = new BufferedReader(new FileReader(providerFile))) {
      String line;
      for (int i = 1; i < providerConfig.skipHeader(); i++) {
        br.readLine();
      }
      final String header = br.readLine();
      if (validateHeader(providerConfig.header(), header)) {
        throw new RuntimeException();
      }
      Files.writeString(resultFile.toPath(), String.format(RESULT_FILE_FORMAT, header), StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
      while ((line = br.readLine()) != null) {
        final String[] columns = line.trim().split(DELLIMITER);
        String refId = "";
        if (providerConfig.runningIdPath().isBlank()) {
          refId = columns[Integer.parseInt(providerConfig.providerRefIdPath())];
        } else {
          refId = columns[Integer.parseInt(providerConfig.runningIdPath())];
        }
        if (!providerConfig.statusSuccess().isBlank()) {
          final int path = Integer.parseInt(providerConfig.statusPath());
          final String status = columns[path].trim();
          if (!providerConfig.statusSuccess().equalsIgnoreCase(status)) {
            continue;
          }
        }

        if (!refId.isBlank() && !refIds.contains(refId)) {
          Files.writeString(resultFile.toPath(), String.format(RESULT_FILE_FORMAT, line), StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
        }
      }
      return String.format(COMPARISON_COMPLETE_PLEASE_CHECK_IN_FOLDER_DOWNLOAD_WITH_FILE_NAME, resultFile.getName());
    } catch (RuntimeException e) {
      resultFile.deleteOnExit();
      return String.format(WRONG_FORMAT_DETECTED_FOR_THIS_PROVIDER, providerFile.getName());
    } catch (IOException e) {
      resultFile.deleteOnExit();
      return String.format(FAILED_TO_READ_PROVIDER_FILE, providerFile.getName());
    }

  }
}
