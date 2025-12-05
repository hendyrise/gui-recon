package org.example;


import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;

public class TransactionService {

  private static final String CANNOT_WRITE_TO_RESULT_FILE = "Cannot write to result file [%s]";
  private static final String COMPARISON_COMPLETE_PLEASE_CHECK_IN_FOLDER_DOWNLOAD_WITH_FILE_NAME = "Comparison complete please check in folder download with file name [%s]";
  private static final String CSV = ".csv";
  private static final String DELLIMITER = AppConfig.getProperties("app.delimiter");
  private static final String FAILED_TO_COMPARE_FILE_AND_FILE = "Failed to Compare File [%s] and File [%s]";
  private static final String INVALID_HEADER_FOR_FILE = "Invalid Header for file [%s]";
  private static final String PROVIDER_COUNT_HEADER_CONFIG = "provider.%s.header";
  private static final String PROVIDER_PROVIDER_REF_ID_PATH_CONFIG = "provider.%s.provider-ref-id-path";
  private static final int PROVIDER_REF_PATH = 12;
  private static final String PROVIDER_RUNNING_ID_CONFIG = "provider.%s.running-id-path";
  private static final String PROVIDER_SKIP_HEADER_CONFIG = "provider.%s.skip-header";
  private static final String PROVIDER_STATUS_CONFIG = "provider.%s.status-path";
  private static final String PROVIDER_STATUS_MESSAGE_CONFIG = "provider.%s.status-message";
  private static final String RESULT_CSV = "-result.csv";
  private static final String RESULT_FILE_FORMAT = "%s\n";
  private static final String RESULT_FILE_PATH = "%s/%s";
  private static final int RISE_RUNNING_ID_PATH = 8;
  private static final String ZERO_WITH_NO_BREAK_SPACE_FORMAT = "\uFEFF";

  @Inject
  AppConfig config;

  public TransactionService() {
  }

  private Set<ProviderRefDTO> extractTransactionIdFromProvider(File providerFile, ProviderConfig providerConfig) {
    final Set<ProviderRefDTO> providerRefList = new HashSet<>();
    try (BufferedReader br = new BufferedReader(new FileReader(providerFile))) {
      String line;
      for (int i = 1; i < providerConfig.skipHeader(); i++) {
        br.readLine();
      }
      final String header = br.readLine();
      if (validateHeader(providerConfig.header(), header)) {
        throw new IOException(String.format(INVALID_HEADER_FOR_FILE, providerFile.getName()));
      }
      int x = 0;
      while ((line = br.readLine()) != null) {
        final String[] columns = line.trim().split(DELLIMITER);
        x += 1;
        if (!providerConfig.statusSuccess().isBlank()) {
          final int path = Integer.parseInt(providerConfig.statusPath());
          final String status = columns[path].trim();
          if (!providerConfig.statusSuccess().equalsIgnoreCase(status)) {
            continue;
          }
        }
        String refId = "";
        if (providerConfig.runningIdPath().isBlank()) {
          refId = columns[Integer.parseInt(providerConfig.providerRefIdPath())];
        } else {
          refId = columns[Integer.parseInt(providerConfig.runningIdPath())];
        }
        providerRefList.add(new ProviderRefDTO(refId.trim(), x));
      }
      return providerRefList;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Set<ProviderRefDTO> extractTransactionIdFromRise(File file, int columnIndex) {
    Set<ProviderRefDTO> ids = new HashSet<>();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      String line;
      br.readLine();
      while ((line = br.readLine()) != null) {
        String[] parts = line.trim().split(DELLIMITER);
        ids.add(new ProviderRefDTO(parts[columnIndex].trim(), null));
      }
      return ids;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private ProviderConfig generateProviderConfig(String selectedProvider) {
    final String runningIdPath = AppConfig.getProperties(String.format(PROVIDER_RUNNING_ID_CONFIG, selectedProvider));
    final String statusPath = AppConfig.getProperties(String.format(PROVIDER_STATUS_CONFIG, selectedProvider));
    final int skipHeader = Integer.parseInt(
      AppConfig.getProperties(String.format(PROVIDER_SKIP_HEADER_CONFIG, selectedProvider)));
    final String header = AppConfig.getProperties(String.format(PROVIDER_COUNT_HEADER_CONFIG, selectedProvider));
    final String statusMessage = AppConfig.getProperties(
      String.format(PROVIDER_STATUS_MESSAGE_CONFIG, selectedProvider));
    final String providerRefIdPath = AppConfig.getProperties(
      String.format(PROVIDER_PROVIDER_REF_ID_PATH_CONFIG, selectedProvider));
    return new ProviderConfig(runningIdPath, statusPath, statusMessage, skipHeader, header, providerRefIdPath);
  }

  public String generateResultFile(File providerFile, File riseFile, String selectedProvider, String fileLocation) {
    final ProviderConfig providerConfig = generateProviderConfig(selectedProvider);
    try {
      final ForkJoinPool pool = new ForkJoinPool();
      final CompletableFuture<Set<ProviderRefDTO>> riseFutureRef = CompletableFuture.supplyAsync(
        () -> extractTransactionIdFromRise(riseFile, validateRefPath(providerConfig)), pool);
      final CompletableFuture<Set<ProviderRefDTO>> providerFutureRef = CompletableFuture.supplyAsync(
        () -> extractTransactionIdFromProvider(providerFile, providerConfig), pool);
      final Set<ProviderRefDTO> riseRefList = riseFutureRef.join();
      final Set<ProviderRefDTO> providerRefList = providerFutureRef.join();
      final Set<ProviderRefDTO> resultRefList = new HashSet<>(CollectionUtils.removeAll(providerRefList, riseRefList));

      return generateResultFile(resultRefList, fileLocation, providerFile, providerConfig.skipHeader());
    } catch (Exception e) {
      return String.format(FAILED_TO_COMPARE_FILE_AND_FILE, riseFile.getName(), providerFile.getName());
    }
  }

  private String generateResultFile(Set<ProviderRefDTO> resultRefList, String fileLocation, File providerFile,
    int skipHeader) {
    final File resultFile = new File(
      String.format(RESULT_FILE_PATH, fileLocation, providerFile.getName().replace(CSV, RESULT_CSV)));

    try (BufferedReader br = new BufferedReader(new FileReader(providerFile))) {
      final Set<Integer> resultLine = resultRefList.stream().map(ProviderRefDTO::getLine).collect(Collectors.toSet());
      String line;
      for (int i = 0; i < skipHeader; i++) {
        br.readLine();
      }
      int x = 1;
      while ((line = br.readLine()) != null) {
        if (resultLine.contains(x)) {
          Files.writeString(resultFile.toPath(), String.format(RESULT_FILE_FORMAT, line), StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
        }
        x += 1;
      }
      return String.format(COMPARISON_COMPLETE_PLEASE_CHECK_IN_FOLDER_DOWNLOAD_WITH_FILE_NAME, resultFile.getName());
    } catch (IOException e) {
      resultFile.deleteOnExit();
      return String.format(CANNOT_WRITE_TO_RESULT_FILE, resultFile.getName());
    }
  }

  private boolean validateHeader(String configHeader, String header) {
    final String finalHeader = Arrays.stream(header.toLowerCase().split(DELLIMITER))
      .map(data -> data.trim().replace(ZERO_WITH_NO_BREAK_SPACE_FORMAT, "")).collect(Collectors.joining(",")).trim();
    return !configHeader.equalsIgnoreCase(finalHeader);
  }

  private int validateRefPath(ProviderConfig providerConfig) {
    return providerConfig.runningIdPath().isBlank() ? PROVIDER_REF_PATH : RISE_RUNNING_ID_PATH;
  }
}

