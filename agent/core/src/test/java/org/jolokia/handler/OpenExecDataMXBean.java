package org.jolokia.handler;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface OpenExecDataMXBean {
	public void compositeData(OpenWriteData data);
	public void arrayData(OpenWriteData[] data);
	public void listData(List<OpenWriteData> data);
	public void mapData(Map<String, OpenWriteData> data);
	public void opSetData(Set<OpenWriteData> data);
}
