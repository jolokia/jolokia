package org.jolokia.converter.json.simplifier;

import java.math.BigInteger;

/*
 * Copyright 2009-2018 Roland Huss
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
 * Simplifier for BigIntegers which result in a map with a single key <code>bigint</code>
 *
 * @author Neven Radovanović
 * @since June 27, 2018
 */
public class BigIntegerSimplifier extends SimplifierExtractor<BigInteger> {

    /**
     * No arg constructor as required for simplifiers
     */
    public BigIntegerSimplifier() {
        super(BigInteger.class);
        addExtractor("bigint", new BigIntegerAttributeExtractor());
    }

    private static class BigIntegerAttributeExtractor implements AttributeExtractor<BigInteger> {
        /** {@inheritDoc} */
        public Object extract(BigInteger pBigInt) { return pBigInt.toString(); }
    }
}