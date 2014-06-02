/*
 *  Copyright 2012 Marcin Plonka
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


/**
 * @author mplonka
 */
package org.jolokia.management.j2ee.simplifier;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.json.Extractor;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.converter.object.StringToObjectConverter;
import org.json.simple.JSONObject;

public abstract class AbstractSimplifier<T, E extends AbstractAttributeExtractor<? extends Object, ? extends Object>>
		implements Extractor {
	private Map<String, E> extractorMap = new HashMap<String, E>();
	private Class<T> type;

	protected AbstractSimplifier(Class<T> type) {
		super();
		this.type = type;
		init(extractorMap);
	}

	protected void init(Map<String, E> extractorMap) {
	}

	protected void addExtractor(String attributeName, E extractor) {
		extractorMap.put(attributeName, extractor);
	}

	@SuppressWarnings("unchecked")
	public final Object extractObject(ObjectToJsonConverter pConverter,
			Object pValue, Stack<String> pExtraArgs, boolean jsonify)
			throws AttributeNotFoundException {
		if (pExtraArgs.size() > 0) {
			String element = pExtraArgs.pop();
			AbstractAttributeExtractor<Object, Object> extractor = (AbstractAttributeExtractor<Object, Object>) extractorMap
					.get(element);
			if (extractor == null) {
				throw new IllegalArgumentException("Illegal path element "
						+ element + " for object " + pValue);
			}
			Object attributeValue = extractor.extract(pValue);
			return pConverter
					.extractObject(attributeValue, pExtraArgs, jsonify);
		} else {
			if (jsonify) {
				JSONObject ret = new JSONObject();
				for (Map.Entry<String, E> entry : extractorMap.entrySet()) {
					AbstractAttributeExtractor<Object, Object> extractor = (AbstractAttributeExtractor<Object, Object>) entry
							.getValue();
					Object value = extractor.extract(pValue);
					ret.put(entry.getKey(), pConverter.extractObject(value,
							pExtraArgs, jsonify));
				}
				return ret;
			} else {
				return pValue;
			}
		}
	}

	public final boolean canSetValue() {
		return false;
	}

	public final Object setObjectValue(StringToObjectConverter pConverter,
			Object pInner, String pAttribute, Object pValue)
			throws IllegalAccessException, InvocationTargetException {
		// never called
		throw new IllegalArgumentException(
				"A simplify handler can't set a value");
	}

	@Override
	public Class<T> getType() {
		return type;
	}
}
