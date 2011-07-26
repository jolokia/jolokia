package org.jolokia.handler;

import java.util.List;

public interface OpenExecDataMXBean {
	public void compositeData(OpenWriteData data);
	public void arrayData(OpenWriteData[] data);
	public void listData(List<OpenWriteData> data);
}
