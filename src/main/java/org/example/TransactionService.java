package org.example;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
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
  private static final String CLIENT = "client";
  private static final String COMPARISON_COMPLETE_PLEASE_CHECK_IN_FOLDER_DOWNLOAD_WITH_FILE_NAME = "Comparison complete please check in folder download with file name [%s]";
  private static final String CSV = ".csv";
  private static final String DELLIMITER = AppConfig.getProperties("app.delimiter");
  private static final String FAILED_TO_COMPARE_FILE_AND_FILE = "Failed to Compare File [%s] and File [%s]";
  private static final String INVALID_HEADER_FOR_FILE = "Invalid Header for file [%s]";
  private static final String COUNT_HEADER_CONFIG = "%s.%s.header";
  private static final String PROVIDER = "provider";
  private static final String REF_ID_PATH_CONFIG = "%s.%s.ref-id-path";
  private static final String PRICE_PATH_CONFIG = "%s.%s.price";
  private static final String FEE_PATH_CONFIG = "%s.%s.fee";
  private static final int REF_ID_PATH = 4;
  private static final int PRICE_PATH = 5;
  private static final int FEE_PATH = 6;
  private static final String RUNNING_ID_CONFIG = "%s.%s.running-id-path";
  private static final String SKIP_HEADER_CONFIG = "%s.%s.skip-header";
  private static final String STATUS_CONFIG = "%s.%s.status-path";
  private static final String STATUS_MESSAGE_CONFIG = "%s.%s.status-message";
  private static final String RESULT_CSV = "-result.csv";
  private static final String RESULT_FILE_FORMAT = "%s\n";
  private static final String RESULT_FILE_PATH = "%s/%s";
  private static final int RISE_RUNNING_ID_PATH = 3;
  private static final String ZERO_WITH_NO_BREAK_SPACE_FORMAT = "\uFEFF";

  public TransactionService() {
  }

  private ResultFileDto extractTransactionIdFromClientProvider(File providerFile, ProviderClientConfig providerConfig) {
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
      int lineNumber = 0;
      BigDecimal totalPrice = BigDecimal.ZERO;
      while ((line = br.readLine()) != null) {
        if (!line.isBlank()) {
          final String[] columns = line.trim().split(DELLIMITER);
          lineNumber += 1;
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
          final BigDecimal fee =
            !providerConfig.feePath().isBlank() ? new BigDecimal(columns[Integer.parseInt(providerConfig.feePath())])
              : BigDecimal.ZERO;
          final BigDecimal price = validatePrice(columns[Integer.parseInt(providerConfig.pricePath())]);
          totalPrice = totalPrice.add(price).add(fee);
          providerRefList.add(new ProviderRefDTO(refId.trim(), lineNumber));
        }
      }
      return new ResultFileDto(totalPrice, providerRefList);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private BigDecimal validatePrice(String price) {
    String finalPrice = price;
    if (price.contains("Rp") || price.contains(".") || price.contains("-")) {
      finalPrice = price.replace("Rp", "").replace(".", "").replace("-", "");
    }
    return new BigDecimal(finalPrice.trim());
  }

  private ResultFileDto extractTransactionIdFromRise(File file, int refIndex, boolean lineNumber) {
    Set<ProviderRefDTO> refList = new HashSet<>();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      String line;
      br.readLine();
      BigDecimal totalPrice = BigDecimal.ZERO;
      int number = 0;
      while ((line = br.readLine()) != null) {
        final String[] parts = line.trim().split(DELLIMITER);
        number += 1;
        totalPrice = totalPrice.add(new BigDecimal(parts[PRICE_PATH])).add(new BigDecimal(parts[FEE_PATH]));
        if (lineNumber) {
          refList.add(new ProviderRefDTO(parts[refIndex].trim(), number));
        } else {
          refList.add(new ProviderRefDTO(parts[refIndex].trim(), null));
        }
      }
      return new ResultFileDto(totalPrice, refList);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private ProviderClientConfig generateProviderClientConfig(String selectedData, int tab) {
    final String data = tab == 0 ? PROVIDER : CLIENT;
    final String runningIdPath = AppConfig.getProperties(String.format(RUNNING_ID_CONFIG, data, selectedData));
    final String statusPath = AppConfig.getProperties(String.format(STATUS_CONFIG, data, selectedData));
    final int skipHeader = Integer.parseInt(
      AppConfig.getProperties(String.format(SKIP_HEADER_CONFIG, data, selectedData)));
    final String header = AppConfig.getProperties(String.format(COUNT_HEADER_CONFIG, data, selectedData));
    final String statusMessage = AppConfig.getProperties(String.format(STATUS_MESSAGE_CONFIG, data, selectedData));
    final String providerRefIdPath = AppConfig.getProperties(String.format(REF_ID_PATH_CONFIG, data, selectedData));
    final String pricePath = AppConfig.getProperties(String.format(PRICE_PATH_CONFIG, data, selectedData));
    final String feePath = AppConfig.getProperties(String.format(FEE_PATH_CONFIG, data, selectedData));
    return new ProviderClientConfig(runningIdPath, statusPath, statusMessage, skipHeader, header, providerRefIdPath,
      pricePath, feePath);
  }

  public CompareResultDTO generateResultFile(File firstFile, File secondFile, String selectedData,
    String fileLocation, int tab) {
    final ProviderClientConfig providerClientConfig = generateProviderClientConfig(selectedData, tab);
    try {
      final ForkJoinPool pool = new ForkJoinPool();
      final CompletableFuture<ResultFileDto> riseFutureRef = CompletableFuture.supplyAsync(() -> {
        if (tab == 0) {
          return extractTransactionIdFromRise(firstFile, validateRefPath(providerClientConfig), false);
        } else {
          return extractTransactionIdFromClientProvider(firstFile, providerClientConfig);
        }
      }, pool);
      final CompletableFuture<ResultFileDto> providerFutureRef = CompletableFuture.supplyAsync(() -> {
        if (tab == 0) {
          return extractTransactionIdFromClientProvider(secondFile, providerClientConfig);
        } else {
          return extractTransactionIdFromRise(secondFile, validateRefPath(providerClientConfig), true);
        }
      }, pool);
      final ResultFileDto riseRefList = riseFutureRef.join();
      final ResultFileDto providerRefList = providerFutureRef.join();
      final Set<ProviderRefDTO> resultRefList = new HashSet<>(
        CollectionUtils.removeAll(providerRefList.refList(), riseRefList.refList()));
      final String header = tab == 0 ? providerClientConfig.header() : "Local Time,client_code,customer_id,running_id,client_ref";
      final String message = compareFile(resultRefList, fileLocation, secondFile, providerClientConfig.skipHeader(),
        header);
      return new CompareResultDTO(riseRefList.totalPrice(), providerRefList.totalPrice(), message);
    } catch (Exception e) {
      return new CompareResultDTO(BigDecimal.ZERO, BigDecimal.ZERO,
        String.format(FAILED_TO_COMPARE_FILE_AND_FILE, firstFile.getName(), secondFile.getName()));
    }
  }

  private String compareFile(Set<ProviderRefDTO> resultRefList, String fileLocation, File providerFile, int skipHeader,
    String header) {
    final File resultFile = new File(
      String.format(RESULT_FILE_PATH, fileLocation, providerFile.getName().replace(CSV, RESULT_CSV)));
    try (BufferedReader br = new BufferedReader(new FileReader(providerFile))) {
      Files.writeString(resultFile.toPath(), String.format(RESULT_FILE_FORMAT, header.replace(",", DELLIMITER)),
        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      final Set<Integer> resultLine = resultRefList.stream().map(ProviderRefDTO::getLineNumber)
        .collect(Collectors.toSet());
      String line;
      for (int i = 0; i < skipHeader; i++) {
        br.readLine();
      }
      int lineNumber = 1;
      while ((line = br.readLine()) != null) {
        if (resultLine.contains(lineNumber)) {
          Files.writeString(resultFile.toPath(), String.format(RESULT_FILE_FORMAT, line), StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
        }
        lineNumber += 1;
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

  private int validateRefPath(ProviderClientConfig providerConfig) {
    return providerConfig.runningIdPath().isBlank() ? REF_ID_PATH : RISE_RUNNING_ID_PATH;
  }
}

