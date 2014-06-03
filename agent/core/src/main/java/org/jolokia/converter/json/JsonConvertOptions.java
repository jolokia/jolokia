package org.jolokia.converter.json;

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
 * Options object influencing the serializing of JSON objects.
 * E.g. the max serialization depth when serializing a complex object.
 *
 * JsonConvertOptions are create via a Builder. This Builder can get hard limits
 * during construction time and can be reused. After each "build()" the
 * Builder is reset (except for the hard limits). Hard limits can be exceeded
 * when setting the actual values and can be used to ensure, that serialization
 * does not goe crazy.
 *
 * A limit of 0 means, that there is no limit set at all for this value.
 *
 * @author roland
 * @since 15.01.13
 */
public final class JsonConvertOptions {

    /**
     * Default JsonConvertOptions filled with the default values as defined in ConfigKey
     */
    public static final JsonConvertOptions DEFAULT = new Builder().build();

    // Maximum depth used for serialization
    private int maxDepth;

    // Maximal size of collections returned
    private int maxCollectionSize;

    // Maximum number of objects to return
    private int maxObjects;

    // Handler which determines what should be done when
    // extracting of an value fails
    private ValueFaultHandler faultHandler;

    // Use a builder to construct this object
    private JsonConvertOptions(int pMaxDepth, int pMaxCollectionSize, int pMaxObjects,
                               ValueFaultHandler pFaultHandler) {
        maxDepth = pMaxDepth;
        maxCollectionSize = pMaxCollectionSize;
        maxObjects = pMaxObjects;
        faultHandler = pFaultHandler;
    }

    /**
     * Check whether the maximum depth has been reached
     * @param pDepth current depth to check
     * @return true if the maximum depth has been exceeded, false otherwise
     */
    public boolean maxDepthReached(int pDepth) {
        return maxDepth != 0 && pDepth >= maxDepth;
    }

    /**
     * Check whether the maximum number of objects has been exceeded
     *
     * @param pObjectCount count to check
     * @return true if the maximum number of objects has been exceeded, false otherwise
     */
    public boolean maxObjectExceeded(int pObjectCount) {
        return maxObjects != 0 && pObjectCount > maxObjects;
    }

    /**
     * Get the size of the collection taking into account the maximum size of a collection allowed.
     *
     * @param pCollectionSize collection size to check
     * @return the original collection size if is smalled than the maximum collections, the maximum itself otherwise.
     */
    public int getCollectionSizeTruncated(int pCollectionSize) {
        return maxCollectionSize != 0 && pCollectionSize > maxCollectionSize ?
                maxCollectionSize :
                pCollectionSize;
    }

    /**
     * Get the configure fault handler which determines, how extractions fault are dealt with
     *
     * @return the configured fault handler
     */
    public ValueFaultHandler getValueFaultHandler() {
        return faultHandler;
    }

    // ===================================================================================

    /**
     * Builder for constructing a convert options objects
     */
    public static class Builder {

        private int hardMaxDepth;
        private int hardMaxCollectionSize;
        private int hardMaxObjects;

        private int maxDepth;
        private int maxCollectionSize;
        private int maxObjects;

        private ValueFaultHandler faultHandler;
        private boolean useAttributeFilter;

        /**
         * Default constructor using default hard limits
         */
        public Builder() {
            this(0,0,0);
        }

        /**
         * Constructor with hard limits. No value set later on this builder can be larger
         * than these limits.
         *
         * @param pHardMaxDepth hard limit for maxDepth
         * @param pHardMaxCollectionSize hard limit for maxCollectionSize
         * @param pHardMaxObjects hard limit for maxObjects.
         */
        public Builder(int pHardMaxDepth,int pHardMaxCollectionSize,int pHardMaxObjects) {
            // Default values
            hardMaxDepth = pHardMaxDepth;
            hardMaxCollectionSize = pHardMaxCollectionSize;
            hardMaxObjects = pHardMaxObjects;
            faultHandler = ValueFaultHandler.THROWING_VALUE_FAULT_HANDLER;
        }

        /**
         * Set the maximum depth for how deep serialization should go. The number cannot
         * be set larger than the hard limit given in the constructor
         *
         * @param pMaxDepth maximal depth when traversing an object tree during serialization.
         * @return this builder
         */
        public Builder maxDepth(int pMaxDepth) {
            maxDepth = checkWithHardLimit(pMaxDepth,hardMaxDepth);
            return this;
        }

        /**
         * Set the maximal size of collections when serializing collections. The number cannot
         * be set larger than the hard limit given in the constructor.
         *
         * @param pMaxCollectionSize maximum size of objects returned in a serialized collection
         * @return this builder
         */
        public Builder maxCollectionSize(int pMaxCollectionSize) {
            maxCollectionSize = checkWithHardLimit(pMaxCollectionSize,hardMaxCollectionSize);
            return this;
        }

        /**
         * Set the maximum number of objects to serialize. The number cannot be set larger than the
         * hard limit given in the constructor.
         *
         * @param pMaxObjects maximum number of objects
         * @return this builder
         */
        public Builder maxObjects(int pMaxObjects) {
            maxObjects = checkWithHardLimit(pMaxObjects,hardMaxObjects);
            return this;
        }

        /**
         * Set the handler which determines what should be done when
         * extracting of an value fails.
         *
         * @param pFaultHandler handler to use which can be either {@link ValueFaultHandler#THROWING_VALUE_FAULT_HANDLER}
         *                      or {@link ValueFaultHandler#THROWING_VALUE_FAULT_HANDLER}.
         *                      If argument is null, it is ignored
         * @return this builder
         */
        public Builder faultHandler(ValueFaultHandler pFaultHandler) {
            if (pFaultHandler != null) {
                faultHandler = pFaultHandler;
            }
            return this;
        }

        /**
         * Whether an attribute filter should be used to ignore missing attributes when a path is
         * applied
         *
         * @param pUseFilter if a filter should be used or not
         * @return this builder
         */
        public Builder useAttributeFilter(boolean pUseFilter) {
            useAttributeFilter = pUseFilter;
            return this;
        }
        /**
         * Build the convert options and reset this builder
         *
         * @return the options created.
         */
        public JsonConvertOptions build() {
            ValueFaultHandler handler = useAttributeFilter ?
                    new PathAttributeFilterValueFaultHandler(faultHandler) :
                    faultHandler;
            JsonConvertOptions opts = new JsonConvertOptions(maxDepth,maxCollectionSize,maxObjects,handler);
            maxDepth = 0;
            maxCollectionSize = 0;
            maxObjects = 0;
            return opts;
        }

        // =================================================================================================

        // Check with the given hard limit
        private int checkWithHardLimit(int pLimit, int pHardLimit) {
            return pLimit < pHardLimit || pHardLimit == 0 ? pLimit : pHardLimit;
        }

    }
}
