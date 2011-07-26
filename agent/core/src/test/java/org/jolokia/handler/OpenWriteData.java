package org.jolokia.handler;

import java.util.List;

public class OpenWriteData {
	private int intField;
	private String stringField;
	private Nested nestedClass;
	private String[] array;
	private List<String> list;
	
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

	public List<String> getList() {
		return list;
	}

	public void setList(List<String> list) {
		this.list = list;
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
