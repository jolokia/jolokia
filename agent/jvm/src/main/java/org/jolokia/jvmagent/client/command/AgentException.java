// Copyright (C) 2019 Interactions Corporation
// All rights reserved
/*
 * Created on 2019-08-07
 */
package org.jolokia.jvmagent.client.command;

/**
 * @author dheitzman
 */
public class AgentException extends Exception {

	public AgentException(String message) {
		super(message);
	}

	public AgentException(String message, Throwable cause) {
		super(message, cause);
	}

}
