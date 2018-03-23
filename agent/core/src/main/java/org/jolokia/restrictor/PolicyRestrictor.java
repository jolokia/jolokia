package org.jolokia.restrictor;

import java.io.IOException;
import java.io.InputStream;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jolokia.restrictor.policy.CorsChecker;
import org.jolokia.restrictor.policy.HttpMethodChecker;
import org.jolokia.restrictor.policy.MBeanAccessChecker;
import org.jolokia.restrictor.policy.NetworkChecker;
import org.jolokia.restrictor.policy.RequestTypeChecker;
import org.jolokia.util.HttpMethod;
import org.jolokia.util.RequestType;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

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
 * Restrictor, which is based on a policy file
 *
 * @author roland
 * @since Jul 28, 2009
 */
public class PolicyRestrictor implements Restrictor {

    // Checks HTTP method restrictions
    private HttpMethodChecker httpChecker;

    // Checks for certain request types
    private RequestTypeChecker requestTypeChecker;

    // Check for hosts and subnets
    private NetworkChecker networkChecker;

    // Check for CORS access
    private CorsChecker corsChecker;

    // Check for MBean access
    private MBeanAccessChecker mbeanAccessChecker;

    /**
     * Construct a policy restrictor from an input stream
     *
     * @param pInput stream from where to fetch the policy data
     */
    public PolicyRestrictor(InputStream pInput) {
        Exception exp = null;
        if (pInput == null) {
            throw new SecurityException("No policy file given");
        }
        try {
            Document doc = createDocument(pInput);
            requestTypeChecker = new RequestTypeChecker(doc);
            httpChecker = new HttpMethodChecker(doc);
            networkChecker = new NetworkChecker(doc);
            mbeanAccessChecker = new MBeanAccessChecker(doc);
            corsChecker = new CorsChecker(doc);
        }
        catch (SAXException e) { exp = e; }
        catch (IOException e) { exp = e; }
        catch (ParserConfigurationException e) { exp = e; }
        catch (MalformedObjectNameException e) { exp = e; }

        if (exp != null) {
            throw new SecurityException("Cannot parse policy file: " + exp,exp);
        }
    }

    private Document createDocument(InputStream pInput) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        String[] features = new String[] {
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd"
        };
        for (String feature : features) {
            try {
                factory.setFeature(feature, false);
            } catch (ParserConfigurationException exp) {
                // Silently ignore as the feature might not be available for the
                // given parser
            }
        }
        return factory.newDocumentBuilder().parse(pInput);
    }

    /** {@inheritDoc} */
    public boolean isHttpMethodAllowed(HttpMethod method) {
        return httpChecker.check(method);
    }

    /** {@inheritDoc} */
    public boolean isTypeAllowed(RequestType pType) {
        return requestTypeChecker.check(pType);
    }

    /** {@inheritDoc} */
    public boolean isRemoteAccessAllowed(String ... pHostOrAddress) {
        return networkChecker.check(pHostOrAddress);
    }

    /** {@inheritDoc} */
    public boolean isOriginAllowed(String pOrigin, boolean pIsStrictCheck) {
        return corsChecker.check(pOrigin,pIsStrictCheck);
    }

    /** {@inheritDoc} */
    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return check(RequestType.READ,pName,pAttribute);
    }

    /** {@inheritDoc} */
    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return check(RequestType.WRITE,pName, pAttribute);
    }

    /** {@inheritDoc} */
    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return check(RequestType.EXEC,pName, pOperation);
    }

    /** {@inheritDoc} */
    private boolean check(RequestType pType, ObjectName pName, String pValue) {
        return mbeanAccessChecker.check(new MBeanAccessChecker.Arg(isTypeAllowed(pType), pType, pName, pValue));
    }
}
