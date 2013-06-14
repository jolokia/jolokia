package org.jolokia.service;

/**
 * @author roland
 * @since 13.06.13
 */
abstract public class JolokiaServiceFactoryBase<T extends JolokiaService> implements JolokiaServiceFactory<T> {

    // The context used for dynamic service factories
    protected JolokiaContext jolokiaContext;

    // Our own type
    private Class<T> type;

    protected JolokiaServiceFactoryBase(Class<T> pType) {
        type = pType;
    }

    /**
     * By default, there are no dynamic services which can popup during runtime
     * @return false
     */
    public boolean hasDynamicServices() {
        return false;
    }

    /**
     * Do nothing by default
     */
    public void destroy() {
    }

    /** {@inheritDoc} */
    public Class<T> getType() {
        return type;
    }

    /** {@inheritDoc} */
    public void init(JolokiaContext pJolokiaContext) {
        jolokiaContext = pJolokiaContext;
    }
}
