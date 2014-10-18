package org.jolokia.mule;

import org.mule.api.lifecycle.StartException;
import org.mule.api.lifecycle.StopException;

/**
 * Internal HTTP server interface For Mule Agent.
 * 
 * 
 * @author Michio Nakagawa
 * @since 10.10.14
 */
public interface MuleAgentHttpServer {

	/**
	 * Startup the internal HTTP server
	 *
	 *
	 * @throws StartException if starting fails
	 */
	void start() throws StartException;

	/**
	 * Stop the internal HTTP server
	 *
	 *
	 * @throws StopException when stopping fails
	 */
	void stop() throws StopException;

}