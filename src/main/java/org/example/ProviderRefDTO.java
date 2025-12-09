package org.example;

import java.util.Objects;

public class ProviderRefDTO {
  private String refId;
  private Integer lineNumber;

  public ProviderRefDTO(String refId, Integer lineNumber) {
    this.refId = refId;
    this.lineNumber = lineNumber;
  }

  public String getRefId() {
    return refId;
  }

  public Integer getLineNumber() {
    return lineNumber;
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
