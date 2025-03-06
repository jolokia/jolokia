/*
 * Copyright 2009-2024 Roland Huss
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

import {
  BaseRequestOptions,
  ErrorCallback,
  ExecResponseValue,
  FetchErrorCallback,
  IJolokia,
  JolokiaConfiguration,
  JolokiaResponseValue,
  JolokiaStatic,
  ListResponseValue,
  ReadResponseValue,
  SearchResponseValue,
  VersionResponseValue,
  WriteResponseValue
} from "jolokia.js"

// --- Jolokia  Simple interfaces - public API

/**
 * IJolokiaSimple creation interface - either using `new` or using function call syntax.
 */
interface JolokiaSimpleStatic extends JolokiaStatic {
  /** Creating {@link IJolokiaSimple} using function call syntax */
  (config: JolokiaConfiguration | string): IJolokiaSimple

  /** Creating {@link IJolokiaSimple} using object creation syntax with `new` */
  new(config: JolokiaConfiguration | string): IJolokiaSimple

  /**
   * Utility method which checks whether a response is a version response
   * @param resp response to check
   * @return true if response is a success
   */
  isVersionResponse(resp: unknown): resp is VersionResponseValue

}

/**
 * Jolokia client interface API extension with "simple" methods for selected Jolokia operations. These methods
 * return (or pass via callbacks) the `value` from full Jolokia response.
 */
interface IJolokiaSimple extends IJolokia {

  /**
   * Reetrieves selected attributes of given `mbean`
   * @param mbean MBean to get attributes from (for example `java.lang:type=Memory`)
   * @param params attribute to get and possibly additional path parameters to further navigate into MBean's attribute
   *               (for example `committed`). If last parameter is an object, it is treated as SimpleRequestOptions
   *               used for request
   * @returns attribute value (single or multiple, depending on the request)
   */
  getAttribute(mbean: string, ...params: (string | string[] | SimpleRequestOptions)[]): Promise<ReadResponseValue>

  /**
   * Sets an attribute on an MBean with additional `path` for nested value access
   *
   * @param mbean objectname of MBean to set
   * @param attribute the attribute to set
   * @param value the value to set
   * @param params an optional _inner path_ which, when given, is used to determine an inner object to set the value on.
   *               If last parameter is an object, it is treated as SimpleRequestOptions used for request
   * @return the previous value
   */
  setAttribute(mbean: string, attribute: string, value: unknown, ...params: (string | string[] | SimpleRequestOptions)[]): Promise<WriteResponseValue>

  /**
   * Executes a JMX operation and returns the result value
   *
   * @param mbean objectname of the MBean to operate on
   * @param operation name of operation to execute. Can contain a signature in case overloaded
   *                  operations are to be called (comma separated fully qualified argument types
   *                  append to the operation name within parentheses)
   * @param params one or more argument required for executing the operation. If last parameter is an object,
   *               it is treated as SimpleRequestOptions used for request
   * @return the return value of the JMX operation.
   */
  execute(mbean: string, operation: string, ...params: (unknown | SimpleRequestOptions)[]): Promise<ExecResponseValue>

  /**
   * Search for MBean based on a pattern and return a reference to the list of found
   * MBeans names (as string). If no MBean can be found, `null` is returned. For example,
   *
   *     jolokia.search("java.lang:type=MemoryPool,*")
   *
   * searches all MBeans whose name are matching this pattern
   *
   * @param mbeanPattern pattern to search for
   * @param opts opts options for `IJolokia.request()`
   * @return an array with ObjectNames as string
   */
  search(mbeanPattern: string, opts?: SimpleRequestOptions): Promise<SearchResponseValue>

  /**
   * This method return the version of the agent and the Jolokia protocol
   * version as part of an object. If available, server specific information
   * like the application server's name are returned as well.
   *
   * @param opts
   */
  version(opts?: SimpleRequestOptions): Promise<VersionResponseValue>

  /**
   * Get all MBeans as registered at the specified server. A `path` can be
   * specified in order to fetch only a subset of the information. When no path is
   * given, the returned value has the following format
   *
   *     {
   *       "<domain of the MBean>": {
   *         "<canonical property list of the MBean>": {
   *           "op": {
   *             "operation name": {
   *               "operation name": {
   *                 "args": ["description", "of", "arguments"],
   *                 "ret": "return type",
   *                 "desc": "operation description"
   *               }
   *             }
   *           },
   *           "notif": {
   *             "notification name": {
   *               ...
   *             }
   *           },
   *           "attr": {
   *             "attribute name": {
   *               "rw": "false or true",
   *               "type": "attribute type",
   *               "desc": "attribute description"
   *             }
   *           },
   *           "class": "fully qualified class name",
   *           "descr": "description of the MBEan"
   *         }
   *       }
   *     }
   *
   * A complete path has the format:
   *
   *     <domain>/<property list>/("attr"|"op"|"notif")/...
   *
   * (e.g. `java.lang/type=Memory/op/gc`). A path can be
   * provided partially, in which case the remaining map/array is returned. The path given must
   * be already properly escaped (i.e. slashes must be escaped like `!/` and exclamation
   * marks like `!!`.
   * See also the Jolokia Reference Manual for a more detailed discussion of inner paths and escaping.
   *
   * @param params optional path and parameters to be passed to `Jolokia.request()` (params is the last object argument)
   */
  list(...params: (string[] | string | SimpleRequestOptions)[]): Promise<ListResponseValue>

  /**
   * Utility method which checks whether a response is a version response
   * @param resp response to check
   * @return true if response is a success
   */
  isVersionResponse(resp: unknown): resp is VersionResponseValue
}

/**
 * Simple version (where the value is passed instead of full response) of single response callback receiving
 * JSON response (no index is passed)
 */
export type SimpleResponseCallback = (response: JolokiaResponseValue) => void

/**
 * "Simple" version of full {@link RequestOptions} type, where callback receives only the value instead of full
 * response and doesn't receive response index.
 */
export type SimpleRequestOptions = BaseRequestOptions & {
  /**
   * A callback to be used by {@link IJolokia#request} method expecting "simple" value instead of full response. Also
   * no response index is passed.
   */
  success?: SimpleResponseCallback
  /**
   * A callback (which may be called with error JSON response)
   * to be used by {@link IJolokia#request} method for error response
   */
  error?: "ignore" | ErrorCallback
  /**
   * Fetch API error callback configured for single `request()` call
   */
  fetchError?: "ignore" | FetchErrorCallback
}

export type { IJolokiaSimple, JolokiaSimpleStatic }
