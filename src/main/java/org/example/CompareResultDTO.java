package org.example;

import java.math.BigDecimal;

public record CompareResultDTO(BigDecimal riseTotalPrice, BigDecimal providerTotalPrice, String message) {

}
