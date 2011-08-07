package org.jolokia.handler;

import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Copyright 2009-2011 Roland Huss
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

public interface OpenExecDataMXBean {
	public OpenWriteData compositeData(OpenWriteData data);
	public OpenWriteData[] arrayData(OpenWriteData[] data);
	public List listData(List<OpenWriteData> data);
	public Map mapData(Map<String, OpenWriteData> data);
	public Set opSetData(Set<OpenWriteData> data);
	public OpenWriteData overloaded(OpenWriteData data);
	public String overloaded(String data);
}
