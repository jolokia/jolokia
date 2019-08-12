package org.jolokia.jvmagent.client.command;

public class AgentException extends Exception {

	public AgentException(String message) {
		super(message);
	}

	public AgentException(String message, Throwable cause) {
		super(message, cause);
	}

}
