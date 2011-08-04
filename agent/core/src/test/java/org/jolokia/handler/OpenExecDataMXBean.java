package org.jolokia.handler;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface OpenExecDataMXBean {
	public OpenWriteData compositeData(OpenWriteData data);
	public OpenWriteData[] arrayData(OpenWriteData[] data);
	public List listData(List<OpenWriteData> data);
	public Map mapData(Map<String, OpenWriteData> data);
	public Set opSetData(Set<OpenWriteData> data);
	public OpenWriteData overloaded(OpenWriteData data);
	public String overloaded(String data);
}
