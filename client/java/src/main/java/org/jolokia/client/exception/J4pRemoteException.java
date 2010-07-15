package org.jolokia.client.exception;

import org.jolokia.client.request.J4pRequest;

/**
 * Exception occured on the remote side (i.e the server).
 *
 * @author roland
 * @since Jun 9, 2010
 */
public class J4pRemoteException extends J4pException {

    // Status code of the error
    private int status;

    // Stacktrace of a remote exception (optional)
    private String remoteStacktrace;

    // Request leading to this error
    private J4pRequest request;

    /**
     * Constructor for a remote exception
     *
     * @param pMessage error message of the exception occurred remotely
     * @param pStatus status code
     * @param pStacktrace stacktrace of the remote exception
     */
    public J4pRemoteException(J4pRequest pJ4pRequest,String pMessage,int pStatus,String pStacktrace) {
        super(pMessage);
        status = pStatus;
        remoteStacktrace = pStacktrace;
        request = pJ4pRequest;
    }

    /**
     * Get status of this response (similar in meaning of HTTP stati)
     *
     * @return status
     */
    public int getStatus() {
        return status;
    }

    /**
     * Get the server side stacktrace as string when {@link #isError()} is true. Return <code>null</code>
     * if no error has occured.
     *
     * @return server side stacktrace as string
     */
    public String getRemoteStackTrace() {
        return remoteStacktrace;
    }

    /**
     * Get the request leading to this exception
     *
     * @return request which caused this exception
     */
    public J4pRequest getRequest() {
        return request;
    }
}
