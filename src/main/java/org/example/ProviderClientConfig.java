package org.example;

public record ProviderClientConfig(String runningIdPath, String statusPath,
   String statusSuccess,
   int skipHeader,
   String header,
   String providerRefIdPath, String pricePath,String feePath ) {

}
