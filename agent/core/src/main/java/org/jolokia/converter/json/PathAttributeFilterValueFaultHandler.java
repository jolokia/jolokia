package org.jolokia.converter.json;

import javax.management.AttributeNotFoundException;

/**
 * A fault handler used during wildcard path application if it detects that a given attribute is not
 * available. Used by collection/bean extractors to skip attribute which do not apply to paths.
 */
class PathAttributeFilterValueFaultHandler implements ValueFaultHandler {
    private final ValueFaultHandler origHandler;

    /**
     * Create a wrapping fault handler which dispatches on {@link AttributeNotFoundException} exceptions.
     * @param pOrigHandler the original handler to dispatch to
     */
    PathAttributeFilterValueFaultHandler(ValueFaultHandler pOrigHandler) {
        origHandler = pOrigHandler;
    }

    /** {@inheritDoc} */
    public <T extends Throwable> Object handleException(T exception) throws T {
        if (exception instanceof AttributeNotFoundException) {
            throw new AttributeFilteredException(exception.getMessage());
        } else {
            return origHandler.handleException(exception);
        }
    }
}
