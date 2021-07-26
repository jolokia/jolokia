package org.jolokia.client.exception;

public class UncheckedJmxAdapterException extends RuntimeException {

  public UncheckedJmxAdapterException(Exception e) {
    super(e);
  }
}
