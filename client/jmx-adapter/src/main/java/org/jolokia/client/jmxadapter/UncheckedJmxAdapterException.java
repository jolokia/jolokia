package org.jolokia.client.jmxadapter;

public class UncheckedJmxAdapterException extends RuntimeException {

  public UncheckedJmxAdapterException(Exception e) {
    super(e);
  }
}
