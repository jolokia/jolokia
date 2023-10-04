package org.jolokia.service.jmx.handler;

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

@SuppressWarnings("rawtypes")
public interface OpenExecDataMXBean {
	OpenWriteData compositeData(OpenWriteData data);
	OpenWriteData[] arrayData(OpenWriteData[] data);
	List listData(List<OpenWriteData> data);
	Map mapData(Map<String, OpenWriteData> data);
	Set opSetData(Set<OpenWriteData> data);
	OpenWriteData overloaded(OpenWriteData data);
	String overloaded(String data);
}
