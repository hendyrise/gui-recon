package org.example;

import java.math.BigDecimal;
import java.util.Set;

public record ResultFileDto(BigDecimal totalPrice, Set<ProviderRefDTO> refList) {
}
