/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.client.jmxadapter;

import javax.management.openmbean.ArrayType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.SimpleType;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class TypeHelperTest {

    @Test
    public void typeCaching() throws OpenDataException {
        assertSame(TypeHelper.cache("t1a", "Z", null).type(), Boolean.class);
        assertSame(TypeHelper.cache("t1b", "boolean", null).type(), Boolean.class);
        assertSame(TypeHelper.cache("t1c", "java.lang.Boolean", null).type(), Boolean.class);
        assertSame(TypeHelper.cache("t1c", "java.lang.Boolean", null).openType(), SimpleType.BOOLEAN);

        assertSame(TypeHelper.cache("t2a", "I", null).type(), Integer.class);
        assertSame(TypeHelper.cache("t2a", "I", null).openType(), SimpleType.INTEGER);
        assertSame(TypeHelper.cache("t2b", "int", null).type(), Integer.class);
        assertNull(TypeHelper.cache("t2c", "integer", null).type());
        assertSame(TypeHelper.cache("t2d", "java.lang.Integer", null).type(), Integer.class);

        assertSame(TypeHelper.cache("t3a", "[I", null).type(), int[].class);
        assertSame(TypeHelper.cache("t3b", "[[I", null).type(), int[][].class);
        assertSame(TypeHelper.cache("t3c", "[[[[I", null).type(), int[][][][].class);
        assertEquals(TypeHelper.cache("t3d", "[[[I", null).openType(), ArrayType.getPrimitiveArrayType(int[][][].class));

        assertEquals(TypeHelper.cache("t4a", "[Ljava.lang.Integer;", null).openType(), new ArrayType<>(1, SimpleType.INTEGER));
        assertEquals(TypeHelper.cache("t4b", "[[[[Ljavax.management.ObjectName;", null).openType(), new ArrayType<>(4, SimpleType.OBJECTNAME));
        assertEquals(TypeHelper.cache("t4c", "[[[[Ljava.lang.String;", null).openType(), new ArrayType<>(2, new ArrayType<>(2, SimpleType.STRING)));
        // yes...
        assertEquals(new ArrayType<>(7, SimpleType.STRING), new ArrayType<>(3, new ArrayType<>(2, new ArrayType<>(2, SimpleType.STRING))));

        assertNull(TypeHelper.cache("t5a", "java.lang.Object", null).openType());
        assertNull(TypeHelper.cache("t5b", "[Ljava.lang.Object;", null).openType());
        assertNotNull(TypeHelper.cache("t5c", "[Ljava.lang.Object;", null).type());
    }

}
