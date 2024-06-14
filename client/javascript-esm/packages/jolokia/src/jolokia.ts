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

const CLIENT_VERSION = "2.1.0"

/**
 * Default parameters for GET and POST requests
 * @see https://developer.mozilla.org/en-US/docs/Web/API/fetch#options
 */
const DEFAULT_FETCH_PARAMS: RequestInit = {
  cache: "no-store",
  credentials: "same-origin",
  redirect: "error",
}

// Processing parameters which are added to the URL as query parameters if given as options
const PROCESSING_PARAMS: string[] = [
  "maxDepth",
  "maxCollectionSize",
  "maxObjects",
  "serializeLong",
  "ignoreErrors",
  "includeStackTrace",
  "serializeException",
  "canonicalNaming",
  "mimeType",
  "ifModifiedSince"
]

/**
 * Processing parameters that influence Jolokia operations.
 * See `org.jolokia.server.core.config.ConfigKey` enum values with `requestConfig=true`.
 * These values may be specified when creating Jolokia instance, but may be overriden for each request.
 * These are sent either as GET query parameters or within `config` key of JSON data for POST requests.
 * @see {https://jolokia.org/reference/html/manual/jolokia_protocol.html#processing-parameters Jolokia Processing Parameters}
 */
type ProcessingParameters = {
  /**
   * Maximum traversal depth for serialization of complex return values
   */
  maxDepth?: number
  /**
   * Maximum size of collections returned during serialization. If larger, the collection is returned truncated.
   */
  maxCollectionSize?: number
  /**
   * Maximum number of objects contained in the response.
   */
  maxObjects?: number
  /**
   * How to serialize long values in the JSON response: `number` or `string`.
   * The default `number` simply serializes longs as numbers in JSON.
   * If set to `string`, longs are serialized as strings.
   * It can be useful when a JavaScript client consumes the JSON response,
   * because numbers greater than the max safe integer don't retain their precision
   * in JavaScript.
   * @since 2.0.3
   */
  serializeLong?: "number" | "string"
  /**
   * If set to true, errors during JMX operations and JSON serialization
   * are ignored.Otherwise if a single deserialization fails, the whole request
   * returns with an error. This works only for certain operations like pattern reads.
   */
  ignoreErrors?: boolean
  /**
   * If set to `true`, then in case of an error the stack trace is
   * included. With `false` no stack trace will be returned, and when
   * this parameter is set to `runtime` only for `RuntimeException`
   * a stack trace is put into the error response. Default is `false`
   * if not set otherwise in the global agent configuration.
   */
  includeStackTrace?: boolean | "true" | "false" | "runtime"
  /**
   * If this parameter is set to `true` then a serialized version of
   * the exception is included in an error response. This value is put under the
   * key `error_value` in the response value. By default this is set
   * to `false` except when the agent global configuration option is
   * configured otherwise.
   */
  serializeException?: boolean
  /**
   * Defaults to `true` to return the canonical format of property
   * lists. If set to `false` then the default unsorted property list
   * is returned.
   */
  canonicalNaming?: boolean
  /**
   * The MIME type to return for the response. By default, this is `text/plain`,
   * but it can be useful for some tools to change it to `application/json`.
   * Init parameters can be used to change the default mime type. Only `text/plain`
   * and `application/json` are allowed. For any other value Jolokia
   * will fallback to `text/plain`.
   */
  mimeType?: "text/plain" | "application/json"
  /**
   * If this parameter is given, its value is interpreted as epoch time (seconds
   * since 1.1.1970) and if the requested value did not change since this time,
   * an empty response (with no `value`) is returned and the response
   * status code is set to 304 ("Not modified"). This option is currently only
   * supported for `LIST` requests. The time value can be extracted
   * from a previous response's `timestamp`.
   */
  ifModifiedSince?: number
}

/*
 * @property {Function} [success] I'll change to promise API. Callback function which is called for a successful request.
 * The callback receives the response as single argument.
 * @property {Function} [error] I'll change to promise API. Callback in case a Jolokia error occurs. A Jolokia error is
 * one, in which the HTTP requests ucceeded with a status code of 200, but the response object contains a status other
 * than `OK` (200) which happens if the request JMX operation fails. This callback receives the full Jolokia response
 * object (with a key `error` set). If no error callback is given, but an asynchronous operation is performed,
 * the error response is printed to the JavaScript console by default.
 */

/*
 * @typedef {object} Job Registered request or request batch to be sent periodically to remote Jolokia agent
 * @property {number} [id] Job identifier
 */

/**
 * Narrowed list of options that can be passed directly to `fetch()` `options` argument
 */
type UsedFetchOptions = Pick<RequestInit, "cache" | "credentials" | "headers" | "redirect">

/**
 * Base request and configuration options which include the ones handled at server-side by Jolokia agent, but also
 * the options used with Fetch API at client-side.
 * We're mixing-in only selected {@link https://developer.mozilla.org/en-US/docs/Web/API/fetch#options | RequestInit}
 * options.
 */
type BaseRequestOptions = ProcessingParameters & UsedFetchOptions & {
  /**
   * Agent URL, which is mandatory
   */
  url?: URL | string
  /**
   * Selects HTTP method to use (`post` or `get`).
   * Note that bulk requests can't be used with `get` method. Also, when using a `read` type request for multiple
   * attributes, this also can only be sent as `post` requests. If not given, a HTTP method is determined dynamically.
   * If a method is selected which doesn't fit to the request, an error is raised.
   */
  method?: "get" | "post"
  /**
   * The type of data specified to the Ajax request. The default value is `json`,
   * and the response is parsed as JSON to an object. If the value is `text`,
   * the response is returned as plain text without parsing. The client is then
   * responsible for parsing the response. This can be useful when a custom JSON
   * parsing is necessary.
   * Jolokia Simple API (jolokia-simple.js) doesn't support `text` as dataType.
   * @since 2.0.2
   */
  dataType?: "json" | "text"
  /**
   * A username used for HTTP Basic authentication. User may also pass value for `Authorization` header directly
   * with `headers` option.
   */
  username?: string
  /**
   * A password used for HTTP Basic authentication. User may also pass value for `Authorization` header directly
   * with `headers` option.
   */
  password?: string
  /**
   * Timeout for the HTTP request
   */
  timeout?: number
}

/**
 * Jolokia configuration object to specify default configuration values used when request-specific options are
 * not present.
 */
type JolokiaConfiguration = BaseRequestOptions & {
  /**
   * Interval for poll-fetching that can be changed during {@link IJolokia#start}
   */
  fetchInterval?: number
}

type RequestOptions = BaseRequestOptions & {
  x?: string
}

type RequestType = "read" | "write" | "exec" | "search" | "list" | "notification" | "version"

/**
 * A Jolokia base request object to be sent to remote Jolokia agent
 */
interface BaseRequest {
  /**
   * A type of Jolokia request according to
   * {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html Jolokia protocol}
   */
  type: RequestType
  /**
   * Request configuration object
   */
  config?: ProcessingParameters

/*
 * @property {RequestType} type
 * @property {object} target
 * {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html#protocol-proxy Jolokia proxy mode}
 */
}

interface ReadRequest extends BaseRequest {
  type: "read"
  /**
   * target MBean for attribute access
   */
  mbean: string
  /**
   * attribute(s) to read
   */
  attribute?: string | string[]
  /**
   * path within Jolokia traversal for MBean domains/names
   */
  path?: string
}

interface WriteRequest extends BaseRequest {
  type: "write"
  /**
   * target MBean for attribute access
   */
  mbean: string
  /**
   * attribute to write
   */
  attribute: string
  /**
   * Value to be set for the attribute
   */
  value: string
  /**
   * path within Jolokia traversal for MBean domains/names
   */
  path?: string
}

interface ExecRequest extends BaseRequest {
  type: "exec"
  /**
   * target MBean for invocation
   */
  mbean: string
  /**
   * Operation name for Jolokia `exec` request
   */
  operation: string
  /**
   * Arguments to use with Jolokia `exec` request
   */
  arguments?: unknown[]
}

interface SearchRequest extends BaseRequest {
  type: "search"
  /**
   * MBean pattern for searching
   */
  mbean: string
}

interface ListRequest extends BaseRequest {
  type: "list"
  /**
   * path within Jolokia traversal for MBean domains/names
   */
  path?: string
}

type NotificationMode = "sse" | "pull";

interface NotificationRequest extends BaseRequest {
  type: "notification"
  /**
   * operation command to use
   */
  command: "register" | "unregister" | "add" | "remove" | "ping" | "open" | "list"
  /**
   * client ID to use
   */
  client?: string
  /**
   * Notification mode to use
   */
  mode?: NotificationMode
  /**
   * MBean to use for registering notification handler
   */
  mbean: string
  /**
   * `filter` parameter
   */
  filter?: string[]
  /**
   * `handback` parameter
   */
  handback?: unknown
  /**
   * `handle` parameter
   */
  handle?: string
}

interface VersionRequest extends BaseRequest {
  type: "version"
}

/**
 * Generic Jolokia request object which combines all possible requests and can be used with tricky TypeScript
 * function matching when using functions as values in typed object/map.
 */
type JRequest = Partial<ReadRequest & WriteRequest & ExecRequest & SearchRequest & ListRequest & NotificationRequest & VersionRequest> & BaseRequest
// type JRequest = ReadRequest | WriteRequest | ExecRequest | SearchRequest | ListRequest | NotificationRequest | VersionRequest

type JResponse = {
  status: number
  timestamp: number
  value: any
}

/**
 * Main Jolokia client interface for communication with remote Jolokia agent.
 */
interface IJolokia {
  /**
   * Version of Jolokia JavaScript client library
   */
  readonly CLIENT_VERSION: string

  /**
   * The request method using one or more JSON requests and sending it to the agent. Beside the
   * request a bunch of options can be given, which are merged with the options provided
   * at the constructor (where the options given here take precedence).
   * @param request JSON request object (or array of objects) compliant with
   *        {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html|Jolokia protocol} representing
   *        the requests to send to remote Jolokia agent.
   * @param params parameters used for sending the request which may override default configuration.
   *        These are not options passed diretly (unchnaged) to `fetch()` call
   * @returns the response promise object
   */
  request(request: BaseRequest, params?: RequestOptions): Promise<string | JResponse[]>

  register(): void
  unregister(): void

  jobs(): void

  start(interval: number): void
  stop(): void
  isRunning(): boolean

  addNotificationListener(): void
  removeNotificationListener(): void
  unregisterNotificationClient(): void

  escape(part: string): string
}

interface JolokiaStatic {
  (config: JolokiaConfiguration | string): undefined
  new (config: JolokiaConfiguration | string): IJolokia

  escape(part: string): string
}

/**
 * Main Jolokia creation function/class.
 * For backward compatibility, it can be used both as function and as constructor.
 * @param config a string with URL of remote Jolokia agent or a configuration object
 * @returns Jolokia instance for remote Jolokia agent connection
 */
const j = function j(this: IJolokia, config: JolokiaConfiguration | string): IJolokia | undefined {
  if (!new.target) {
    // when invoked as function, return properly create object with bound "this" reference
    return new (<JolokiaStatic>j)(config)
  }

  // ++++++++++++++++++++++++++++++++++++++++++++++++++
  // Function-scoped state not exposed as properties of Jolokia class or instance

  // Registered requests for fetching periodically
  const jobs: string[] = []

  // Our client id and notification backend config Is null as long as notifications are not used
  let client = null

  // Options used for every request
  const agentOptions: JolokiaConfiguration = {}

  // State of the scheduler
  let pollerIsRunning = false

  // timer id for poller's setInterval call
  let timerId: NodeJS.Timeout | number | null = null

  if (typeof config === "string") {
    config = { url: config }
  }
  Object.assign(agentOptions, DEFAULT_FETCH_PARAMS, config)

  // ++++++++++++++++++++++++++++++++++++++++++++++++++
  // Public API (instance methods that may use function-scoped state)

  this.request = async function(request: JRequest, params?: RequestOptions): Promise<string | JResponse[]> {
    const opts: RequestOptions = Object.assign({}, agentOptions, params)
    assertNotNull(opts.url, "No URL given")

    // options object passed to fetch() (2nd argument)
    // https://developer.mozilla.org/en-US/docs/Web/API/fetch#options
    // - body
    // - cache
    // - credentials (omit|same-origin|include)
    // - headers (object with string values or Headers object)
    // - integrity (can be passed, but not handled here)
    // - keepalive (can be passed, but not handled here)
    // - method
    // - mode (can be passed, but not handled here)
    // - priority (can be passed, but not handled here)
    // - redirect (follow|error|manual) (can be passed, but not handled here)
    // - referrer (can be passed, but not handled here)
    // - referrerPolicy (can be passed, but not handled here)
    // - signal (created based on the timeout option)
    // some of the options are taken from agentOptions
    const fetchOptions: RequestInit = Object.assign({}, DEFAULT_FETCH_PARAMS)
    fetchOptions.headers = {}

    const method = extractMethod(request, opts)
    let url = ensureTrailingSlash(opts.url!)

    if (opts.headers) {
      // If user has specified `headers` option, these are copied here. Some headers
      // (Content-Type and Authorization) may be overriden below
      Object.assign(fetchOptions.headers, opts.headers)
    }

    if (method === "post") {
      fetchOptions.method = "POST"
      fetchOptions.body = JSON.stringify(request)
      Object.assign(fetchOptions.headers, { "Content-Type": "text/json" })
    } else {
      fetchOptions.method = "GET"
      url += constructGetUrlPath(request)
    }

    // Add processing parameters as query parameters for GET or POST URL
    url = addProcessingParameters(url, opts)

    // preemptive basic authentication if window.btoa is available
    // otherwise, if "WWW-Authenticate: Basic realm='realm-name'" is returned, native browser popup may be displayed
    if (opts.username && opts.password) {
      if (typeof window.btoa === "function") {
        fetchOptions.headers["Authorization"] = "Basic " + window.btoa(opts.username + ":" + opts.password)
      } else {
        console.warn("Can't set \"Authorization\" header - no btoa() function available")
      }
    }

    if (opts.timeout != null) {
      fetchOptions.signal = AbortSignal.timeout(opts.timeout)
    }

    // In original jolokia.js at this stage there was different processing depending on the existence of
    // "success" option in passed params.
    // without such callback the request was treated as synchronus (ajaxSettings.async = false), but we can't do
    // this with Fetch API (which is good)
    // callbacks (success and error) were wrapped, so:
    //  - if null, console.warn was used
    //  - if "ignore", noop callback was used
    //  - otherwise callback was turned into an array (not if already an array) and the actual callback was
    //    wrapped with function(response, idx) which called the callback(s) in round-robin fashion for each response
    //    (single if not bulk or multi-attribute read)
    // Now we simply handle a Promise resolving to https://developer.mozilla.org/en-US/docs/Web/API/Response
    // end return new promise resolving to string or an array of Jolokia responses (whether successful or failed ones)
    // user should handle transport errors with .catch()

    return fetch(url, fetchOptions)
        .then(async (response: Response): Promise<string | JResponse[]> => {
          const ct = response.headers.get("content-type")
          if (opts.dataType === 'text' || !ct || !(ct.startsWith("text/json") || ct.startsWith("application/json"))) {
            return response.text()
          }
          const json = await response.json()
          return Array.isArray(json) ? json : [ json ]
        })
  }

  this.register = function() {}

  this.unregister = function() {}

  this.jobs = function() {}

  /**
   * @param {number} interval Interval (in ms) for poll-fetching from remote Jolokia agent.
   */
  /**
   * Start the poller. The interval between two polling attempts can be optionally given (in milliseconds) or
   * is taken from the parameter `fetchInterval` given at construction time. If no interval is given at all,
   * 30 seconds is the default.
   *
   * If the poller is already running (i.e. {@link #isRunning} is `true` then the scheduler
   * is restarted, but only if the new interval differs from the currently active one.
   *
   * @param interval
   */
  this.start = function(interval) {
    interval = interval || agentOptions.fetchInterval || 30000
    if (pollerIsRunning) {
      if (interval === agentOptions.fetchInterval) {
        // Nothing to do
        return
      }
      // Re-start with new interval
      this.stop()
    }
    agentOptions.fetchInterval = interval
    timerId = setInterval(createJolokiaInvocation(this, jobs), interval)

    pollerIsRunning = true
  }

  /**
   * Stop the poller. If the poller is not running, no operation is performed.
   */
  this.stop = function() {
    if (!pollerIsRunning) {
      return
    }
    clearInterval(timerId as number)
    timerId = null
    pollerIsRunning = false
  }

  /**
   * Check whether the poller is running.
   * @returns true if the poller is running, false otherwise.
   */
  this.isRunning = function() {
    return pollerIsRunning
  }

  // ++++++++++++++++++++++++++++++++++++++++++++++++++
  // Public API - Notification handling

  this.addNotificationListener = function() {}
  this.removeNotificationListener = function() {}
  this.unregisterNotificationClient = function() {}
}

/**
 * Version of Jolokia JavaScript client library
 * @constant
 * @type {string}
 * @default
 */
Object.defineProperty(j.prototype, "CLIENT_VERSION", {
  value: CLIENT_VERSION,
  enumerable: true,
  writable: false
})

// ++++++++++++++++++++++++++++++++++++++++++++++++++
// Public API defined in Jolokia.prototype (or Jolokia itself (static)) - no need to access "this"

/**
 * Escape URL part (segment) according to
 * {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html#_escaping_rules_in_get_requests Jolokia escaping rules}
 * @param part An URL segment for Jolokia GET URL
 * @returns URL segment with Jolokia escaping rules applied
 */
j.escape = j.prototype.escape = function(part: string) {
  return encodeURIComponent(part.replace(/!/g, "!!").replace(/\//g, "!/"))
}

// ++++++++++++++++++++++++++++++++++++++++++++++++++
// Private/internal functions that don't have access to Jolokia() scope variables
// (unless these are passed explicitly)

/**
 * Prepares a function that will be invoked in poll-fetching for configured requests. Passed parameters come
 * from the scope of {@link IJolokia} constructor function.
 * @param _jolokia The Jolokia object used to communicate with remote Jolokia agent
 * @param _jobs Requests configured for poll-fetching
 * @returns A function which can be passed to {@link window#setInterval}
 */
function createJolokiaInvocation(_jolokia: IJolokia, _jobs: unknown[]) {
  return function() {

  }
}

/**
 * Asserts that the value is not null or undefined
 * @param value A value to assert
 * @param message An error message for thrown Error
 * @throws Error in case the value is null or undefined
 */
function assertNotNull(value: unknown, message: string) {
  if (value == null) {
    throw new Error(message)
  }
}

/**
 * Extract the HTTP-Method to use and make some sanity checks if the method was provided as part of the options,
 * but is not valid for the given request
 * @param request JSON request object (or array of objects) compliant with
 * {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html|Jolokia protocol} representing the requests
 * to send to remote Jolokia agent.
 * @param params parameters used for sending the request which may override default configuration.
 * @returns an HTTP method for given `request` and `params`
 */
function extractMethod(request: BaseRequest, params: RequestOptions) {
  const methodGiven = params?.method?.toLowerCase()
  let method

  if (!request) {
    // let's assume that it's a version GET request
    return "get"
  }

  if (methodGiven) {
    if (methodGiven === "get") {
      // can't be used for bulk or multireadrequests
      if (Array.isArray(request)) {
        throw new Error("Cannot use GET with bulk requests")
      }
      // if (request.type === "read" && Array.isArray(request.attribute)) {
      if (request.type?.toLowerCase() === "read" && "attribute" in request && Array.isArray(request.attribute)) {
        throw new Error("Cannot use GET for read with multiple attributes")
      }
      if ("target" in request) {
        throw new Error("Cannot use GET request with proxy mode")
      }
      if ("config" in request) {
        throw new Error("Cannot use GET with request specific config")
      }
      method = "get"
    } else if (methodGiven !== "post") {
      throw new Error("Illegal method \"" + methodGiven + "\"")
    }
  } else {request.type?.toLowerCase() === "read" && "attribute" in request && Array.isArray(request.attribute)
    // Determine method dynamically
    if (Array.isArray(request)
        || "config" in request
        || (request.type?.toLowerCase() === "read" && "attribute" in request && Array.isArray(request.attribute))
        || "target" in request) {
      method = "post"
    } else {
      method = "get"
    }
  }

  return method
}

/**
 * Create the URL used for a GET request
 * @param request request object sent using `GET`, where type, mbean and additional information is encoded
 * in URL GET request
 * @returns GET URL to be used with remote Jolokia agent
 */
function constructGetUrlPath(request: BaseRequest) {
  let type: RequestType = request.type
  assertNotNull(type, "No request type given for building a GET request")
  type = type.toLowerCase() as RequestType

  const extractor: (r: JRequest) => GetPathConfiguration = GET_URL_EXTRACTORS[type]
  assertNotNull(extractor, "Unknown request type " + type)

  const result = extractor(request as JRequest)
  const parts = result.parts || []
  let url = type
  parts.forEach(function (v: string) {
    url += "/" + j.escape(v)
  })
  if (result.path) {
    url += (result.path[0] === '/' ? "" : "/") + result.path
  }
  return url
}

/**
 * Add processing parameters given as request options
 * to an URL as GET query parameters
 * @param url URL for GET or POST request
 * @param opts Request configuration object
 * @returns url with query parameters applied
 */
function addProcessingParameters(url: string, opts: RequestOptions) {
  let sep = url.indexOf("?") > 0 ? "&" : "?"
  PROCESSING_PARAMS.forEach(function(key) {
    const v = opts[key as keyof RequestOptions]
    if (v != null) {
      url += sep + key + "=" + v
      sep = "&"
    }
  })
  return url
}

/**
 * For POST requests it is recommended to have a trailing slash at the URL in order to avoid a redirect which then
 * results in a GET request.
 * @see https://bugs.eclipse.org/bugs/show_bug.cgi?id=331194#c1
 * @param url Jolokia agent URL
 * @returns URL location with "/" appended
 */
function ensureTrailingSlash(url: string | URL): string {
  // Squeeze any URL to a single one, optionally adding one
  return (typeof url === "string" ? url : url.href).replace(/\/*$/, "/")
}

type GetPathConfiguration = {
  parts?: string[]
  path?: string
}

/**
 * Extractors used for preparing a GET request, i.e. for creating a stack
 * of arguments which gets appended to create the proper access URL.
 * key: lowercase request type.
 * The return value is an object with two properties: The 'parts' to glue together, where
 * each part gets escaped and a 'path' which is appended literally
 * Note: I could not create a good TS base type for ReadRequest, WriteRequest, ... and at the same time use it as
 * parameter of a function used as value type of GET_URL_EXTRACTORS - I had problems defining particular
 * functions (under keys `read`, `write`, ...) with types derived from BaseRequest...
 */
const GET_URL_EXTRACTORS: { [ key in RequestType ]: (r: JRequest) => GetPathConfiguration } = {

  /**
   * Function to prepare GET URL for `read` Jolokia request
   * @param request Jolokia request object
   * @returns URL configuration object for Jolokia `read` GET request
   */
  "read": function(request: ReadRequest)  {
    if (request.attribute == null) {
      // Path gets ignored for multiple attribute fetch
      return { parts: [ request.mbean, '*' ], path: request.path }
    } else {
      // can't use attribute array with GET
      return { parts: [ request.mbean, request.attribute as string ], path: request.path }
    }
  },

  /**
   * Function to prepare GET URL for `write` Jolokia request
   * @param request Jolokia request object
   * @returns URL configuration object for Jolokia `write` GET request
   */
  "write": function(request: WriteRequest) {
    return { parts: [ request.mbean, request.attribute, valueToString(request.value) ], path: request.path }
  },

  /**
   * Function to prepare GET URL for `exec` Jolokia request
   * @param request Jolokia request object
   * @returns URL configuration object for Jolokia `exec` GET request
   */
  "exec": function(request: ExecRequest) {
    const ret = [ request.mbean, request.operation ]
    if (request.arguments && request.arguments.length > 0) {
      request.arguments.forEach(function(value: unknown) {
        ret.push(valueToString(value))
      })
    }
    return { parts: ret }
  },

  /**
   * Function to prepare GET URL for `version` Jolokia request
   * @param _request Jolokia request object
   * @returns URL configuration object for Jolokia `version` GET request
   */
  "version": function(_request: VersionRequest) {
    return {}
  },

  /**
   * Function to prepare GET URL for `search` Jolokia request
   * @param request Jolokia request object
   * @returns URL configuration object for Jolokia `search` GET request
   */
  "search": function(request: SearchRequest) {
    return { parts: [ request.mbean ] }
  },

  /**
   * Function to prepare GET URL for `list` Jolokia request
   * @param request Jolokia request object
   * @returns URL configuration object for Jolokia `list` GET request
   */
  "list": function(request: ListRequest) {
    return { path: request.path }
  },

  /**
   * Function to prepare GET URL for `notification` Jolokia request
   * @param request Jolokia request object
   * @returns URL configuration object for Jolokia `notification` GET request
   */
  "notification": function(request: NotificationRequest) {
    switch (request.command) {
      case "register":
        return { parts: [ "register" ] }
      case "add": {
        const ret = [ "add", valueToString(request.client), valueToString(request.mode), request.mbean ]
        const extra: string[] = []
        if (request.handback) {
          extra.push(valueToString(request.handback))
        }
        if (request.config) {
          extra.push(valueToString(request.config))
        } else if (extra.length) {
          extra.push("{}")
        }
        if (request.filter) {
          extra.push(valueToString(request.filter))
        } else if (extra.length) {
          extra.push(" ")
        }
        return { parts: ret.concat(extra.reverse()) }
      }
      case "remove":
        return { parts: [ "remove", valueToString(request.client), valueToString(request.handle) ] }
      case "unregister":
        return { parts: [ "unregister", valueToString(request.client) ] }
      case "list":
        return { parts: [ "list", valueToString(request.client) ] }
      case "ping":
        return { parts: [ "ping", valueToString(request.client) ] }
      case "open":
        return { parts: [ "open", valueToString(request.client), valueToString(request.mode) ] }
      default:
        throw new Error("Unknown command '" + request.command + "'")
    }
  }
}

/**
 * Converts a value to a string for passing it to the Jolokia agent via
 * a `GET` request (`write`, `exec`). Value can be either a single object or an array
 * @param value A value to write or pass to `exec` operation
 * @returns Normalized value for Jolokia `write`/`exec` operation
 */
function valueToString(value: unknown) {
  if (value == null) {
    return "[null]"
  }
  if (Array.isArray(value)) {
    let ret = ""
    for (let i = 0; i < value.length; i++) {
      const v = value[i]
      ret += v == null ? "[null]" : singleValueToString(value[i])
      if (i < value.length - 1) {
        ret += ","
      }
    }
    return ret
  } else {
    return singleValueToString(value)
  }
}

/**
 * Single value conversion for write/exec GET requests
 * @param value A value to normalize
 * @returns normalized value
 */
function singleValueToString(value: string | object) {
  if (typeof value === "string" && value.length === 0) {
    return "\"\""
  } else {
    return value.toString()
  }
}

export type { IJolokia, JResponse as Response }
export default j as JolokiaStatic
