package org.example;

import java.util.Objects;

public class ProviderRefDTO {
  private String refId;
  private Integer line;

  public ProviderRefDTO(String refId, Integer line) {
    this.refId = refId;
    this.line = line;
  }

  public String getRefId() {
    return refId;
  }

  public Integer getLine() {
    return line;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProviderRefDTO that = (ProviderRefDTO) o;
    return Objects.equals(refId, that.refId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(refId);
  }
}
