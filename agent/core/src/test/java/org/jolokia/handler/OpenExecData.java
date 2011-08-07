package org.jolokia.handler;

import java.util.List;
import java.util.Map;
import java.util.Set;


public class OpenExecData implements OpenExecDataMXBean {

	public OpenWriteData compositeData(OpenWriteData data) {
        return data;
    }

	public OpenWriteData[] arrayData(OpenWriteData[] data) {
        return data;
    }

	public List listData(List<OpenWriteData> data) {
        return data;
    }

	public Map mapData(Map<String, OpenWriteData> data) {
        return data;
    }

	public Set opSetData(Set<OpenWriteData> data) {
        return data;
    }
	
	public OpenWriteData overloaded(OpenWriteData data) {

        return data;
    }

	public String overloaded(String data) {
        return data;
    }
	

}
