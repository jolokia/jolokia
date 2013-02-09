package org.jolokia.handler;


import java.util.List;
import java.util.Map;
import java.util.Set;

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


public class OpenWriteData {
	private int intField;
	private String stringField;
	private Nested nestedClass;
	private String[] array;
	private List<Integer> list;
	private Map<Integer, Nested> map;
	private Set<String> set;
	
	public OpenWriteData() {
	}
	
	public int getIntField() {
		return intField;
	}
	
	public void setIntField(int intField) {
		this.intField = intField;
	}
	
	public String getStringField() {
		return stringField;
	}
	
	public void setStringField(String stringField) {
		this.stringField = stringField;
	}

	public Nested getNestedClass() {
		return nestedClass;
	}

	public void setNestedClass(Nested nestedClass) {
		this.nestedClass = nestedClass;
	}

	public String[] getArray() {
		return array;
	}

	public void setArray(String[] array) {
		this.array = array;
	}

	public List<Integer> getList() {
		return list;
	}

	public void setList(List<Integer> list) {
		this.list = list;
	}
	
	public Map<Integer, Nested> getMap() {
		return map;
	}

	public void setMap(Map<Integer, Nested> map) {
		this.map = map;
	}
	
	public Set<String> getSet() {
		return set;
	}

	public void setSet(Set<String> set) {
		this.set = set;
	}
	

	public static class Nested {
		private String nestedField;

		public String getNestedField() {
			return nestedField;
		}

		public void setNestedField(String nestedField) {
			this.nestedField = nestedField;
		}
	}
}
