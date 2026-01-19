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
package org.jolokia.converter.json;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.Converter;
import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.json.JSONArray;

/**
 * Accessor used for arbitrary collections. They are simply converted into {@link JSONArray}, although
 * the order is arbitrary. Setting to a collection is not allowed.
 *
 * This accessor must be called after all more specialized extractors (like the {@link ListAccessor} or {@link ArrayAccessor}).
 *
 * @author roland
 * @since 18.10.11
 */
public class CollectionAccessor implements org.jolokia.converter.json.ObjectAccessor {

    @Override
    public Class<?> getType() {
        return Collection.class;
    }

    /**
     * Converts a collection to an JSON array. Element at given index can be extracted, but the actual
     * {@link Collection} may not be ordered and the <em>index</em> is simulated by iterator counter.
     *
     * @param pConverter  the global converter to convert inner values and get serialization options
     * @param pCollection the value to convert (must be a {@link Collection})
     * @param pPathParts  if not empty, top value may be an index into the iterator loop over the collection
     *                    and remaining parts are passed recursively
     * @param pJsonify    whether to convert the collection into a {@link JSONArray} or its element to a relevant JSON representation
     * @return the extracted object or entire collection when the {@code pPathParts} stack is empty
     * @throws AttributeNotFoundException if the index is not specified as non-negative integer
     * @throws IndexOutOfBoundsException  if an index is used which points outside the given list
     */
    @Override
    public Object extractObject(ObjectToJsonConverter pConverter, Object pCollection, Deque<String> pPathParts, boolean pJsonify)
            throws AttributeNotFoundException {
        Collection<?> collection = (Collection<?>) pCollection;
        int length = pConverter.getCollectionLength(collection.size());
        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            return extractCollectionItem(pConverter, collection, pPathParts, pJsonify, pathPart, length);
        } else {
            return pJsonify ? collectionToJSON(pConverter, collection, pPathParts, length) : collection;
        }
    }

    @Override
    public boolean canSetValue() {
        return false;
    }

    /**
     * Setting of an object value is not supported for the collection converter
     */
    @Override
    public Object setObjectValue(Converter<String> pConverter, Object pObject, String pAttribute, Object pValue) {
        throw new UnsupportedOperationException("A collection (except Lists and Maps) cannot be modified");
    }

    /**
     * Serialize given {@link Collection} into a {@link JSONArray} recursively, limited by
     * {@link org.jolokia.core.service.serializer.SerializeOptions}
     *
     * @param pConverter
     * @param pCollection
     * @param pPath
     * @param pLength
     * @return
     * @throws AttributeNotFoundException
     */
    private Object collectionToJSON(ObjectToJsonConverter pConverter, Collection<?> pCollection, Deque<String> pPath, int pLength)
            throws AttributeNotFoundException {
        JSONArray ret = new JSONArray(pLength);
        int idx = 0;
        for (Iterator<?> it = pCollection.iterator(); it.hasNext() && idx < pLength; idx++) {
            Object item = it.next();
            // a copy passed (and drained) for each element
            Deque<String> path = new LinkedList<>(pPath);
            try {
                ret.add(pConverter.extractObject(item, path, true));
            } catch (ValueFaultHandler.AttributeFilteredException exp) {
                // This element is filtered out because there may bo no such attribute in an item of the list,
                // next one ...
            }
        }
        if (ret.isEmpty() && pLength > 0) {
            throw new ValueFaultHandler.AttributeFilteredException();
        }

        return ret;
    }

    /**
     * Extract single item of a collection - this is not very reliable, as the {@link Collection} may not be ordered.
     *
     * @param pConverter
     * @param pCollection the collection to get an item from
     * @param pPath       remaining path parts passed recursively when converting element of a collection
     * @param pJsonify
     * @param pPathPart
     * @param pLength     to ensure collection is not accessed out of bounds
     * @return
     * @throws AttributeNotFoundException
     */
    private Object extractCollectionItem(ObjectToJsonConverter pConverter, Collection<?> pCollection, Deque<String> pPath, boolean pJsonify, String pPathPart, int pLength)
            throws AttributeNotFoundException {
        try {
            int idx = Integer.parseInt(pPathPart);
            return pConverter.extractObject(getElement(pCollection, idx, pLength), pPath, pJsonify);
        } catch (NumberFormatException exp) {
            ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
            return faultHandler.handleException(
                new AttributeNotFoundException("Index '" + pPathPart + "' is not numeric for accessing a collection"));
        } catch (IndexOutOfBoundsException exp) {
            ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
            return faultHandler.handleException(
                new AttributeNotFoundException("Index '" + pPathPart + "' is out-of-bound for a collection of size " + pLength));
        }
    }

    /**
     * Simulate {@link List#get(int)} ensuring bounds constraints.
     *
     * @param pCollection
     * @param pIdx
     * @param pLength
     * @return
     */
    private Object getElement(Collection<?> pCollection, int pIdx, int pLength) {
        int i = 0;
        Iterator<?> it = pCollection.iterator();
        while (it.hasNext() && i < pLength) {
            Object val = it.next();
            if (i == pIdx) {
                return val;
            }
            i++;
        }
        throw new IndexOutOfBoundsException("Collection index " + pIdx + " larger than size " + pLength);
    }

}
