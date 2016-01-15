package org.jolokia.history;

import java.io.Serializable;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.request.*;

/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * Key used in the {@link HistoryStore} for indexing historical values. The key has
 * a type reflecting the kind of JMX operataion (<code>attribute</code> or <code>operation</code>)
 * and can be used in proxy mode for different targets.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class HistoryKey implements Serializable {

    private static final long serialVersionUID = 42L;

    private String type;
    private ObjectName mBean;
    private String secondary;
    private String path;
    private String target;

    /**
     * Create a key from a read request from which all necessary information is extracted
     *
     * @param pJmxRequest read request
     */
    HistoryKey(JmxReadRequest pJmxRequest) {
        init(pJmxRequest);

        if (pJmxRequest.getAttributeNames() != null && pJmxRequest.getAttributeNames().size() > 1) {
            throw new IllegalArgumentException("A key cannot contain more than one attribute");
        }

        type = "attribute";
        secondary = pJmxRequest.isMultiAttributeMode() ? pJmxRequest.getAttributeNames().get(0) : pJmxRequest.getAttributeName();
        if (secondary == null) {
            secondary = "(all)";
        }
        path = pJmxRequest.getPath();
    }

    /**
     * Create a key from a write request from which all necessary information is extracted
     *
     * @param pJmxReq write request
     */
    HistoryKey(JmxWriteRequest pJmxReq) {
        init(pJmxReq);

        type = "attribute";
        secondary = pJmxReq.getAttributeName();
        path = pJmxReq.getPath();
        if (secondary == null) {
            throw new IllegalArgumentException(type + " name must not be null");
        }
    }

    /**
     * Create a key from an exec request from which all necessary information is extracted
     *
     * @param pJmxReq read request
     */
    HistoryKey(JmxExecRequest pJmxReq) {
        init(pJmxReq);

        type = "operation";
        secondary = pJmxReq.getOperation();
        path = null;
        if (secondary == null) {
            throw new IllegalArgumentException(type + " name must not be null");
        }
    }


    private void init(JmxObjectNameRequest pJmxReq) {

        if (pJmxReq.getObjectNameAsString() == null) {
            throw new IllegalArgumentException("MBean name must not be null");
        }
        if (pJmxReq.getObjectName().isPattern()) {
            throw new IllegalArgumentException("MBean name must not be a pattern");
        }
        if (pJmxReq.getTargetConfig() != null) {
            target = pJmxReq.getTargetConfig().getUrl();
        }
        mBean = pJmxReq.getObjectName();
    }


    /**
     * Constructor for type <code>operation</code>
     *
     * @param pMBean MBean name
     * @param pOperation operation name
     * @param pTarget optional target if used in proxy mode
     * @throws MalformedObjectNameException if the mbean name is invalid
     */
    public HistoryKey(String pMBean, String pOperation, String pTarget) throws MalformedObjectNameException {
        type = "operation";
        mBean = new ObjectName(pMBean);
        secondary = pOperation;
        path = null;
        target = sanitize(pTarget);
    }

    // Return null for an empty string
    private String sanitize(String pValue) {
        return "".equals(pValue) ? null : pValue;
    }

    /**
     * Constructor for type <code>attribute</code>
     *
     * @param pMBean MBean name
     * @param pAttribute attribute
     * @param pPath optional path
     * @param pTarget optional proxy target
     * @throws MalformedObjectNameException if the mbean name is invalid
     */
    public HistoryKey(String pMBean, String pAttribute, String pPath, String pTarget)
            throws MalformedObjectNameException {
        type = "attribute";
        mBean = new ObjectName(pMBean);
        secondary = pAttribute;
        path = sanitize(pPath);
        target = pTarget;
    }

    /**
     * Whether this key embraces a MBean pattern
     *
     * @return true if the the included MBean is a pattern
     */
    public boolean isMBeanPattern() {
        return mBean.isPattern();
    }

    /**
     * Whether the key matches the given MBean name
     *
     * @param pKey to match
     * @return true if the given mbean matches the Mbean encapsulated by this key
     */
    public boolean matches(HistoryKey pKey) {
        return mBean.apply(pKey.mBean);
    }

    // CHECKSTYLE:OFF
    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("PMD.IfStmtsMustUseBraces")
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HistoryKey that = (HistoryKey) o;

        if (!mBean.equals(that.mBean)) return false;
        if (path != null ? !path.equals(that.path) : that.path != null) return false;
        if (!secondary.equals(that.secondary)) return false;
        if (target != null ? !target.equals(that.target) : that.target != null)
            return false;
        if (!type.equals(that.type)) return false;

        return true;
    }
    // CHECKSTYLE:ON

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + mBean.hashCode();
        result = 31 * result + secondary.hashCode();
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + (target != null ? target.hashCode() : 0);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("HistoryKey");
        sb.append("{type='").append(type).append('\'');
        sb.append(", mBean=").append(mBean);
        sb.append(", secondary='").append(secondary).append('\'');
        sb.append(", path='").append(path).append('\'');
        sb.append(", target='").append(target).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
