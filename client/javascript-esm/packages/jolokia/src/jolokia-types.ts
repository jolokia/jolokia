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

// --- Types related to configuration options - global and request-related

/**
 * Narrowed list of options that can be passed directly to `fetch()` `options` argument
 */
export type UsedFetchOptions = Pick<RequestInit, "cache" | "credentials" | "headers" | "redirect">

/**
 * Processing parameters that influence Jolokia operations.
 * See `org.jolokia.server.core.config.ConfigKey` enum values with `requestConfig=true`.
 * These values may be specified when creating Jolokia instance, but may be overriden for each request.
 * These are sent either as GET query parameters or within `config` key of JSON data for POST requests.
 * @see {https://jolokia.org/reference/html/manual/jolokia_protocol.html#processing-parameters Jolokia Processing Parameters}
 */
export type ProcessingParameters = {
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

/**
 * Base request and configuration options which include the ones handled at server-side by Jolokia agent, but also
 * the options used with Fetch API at client-side.
 * We're mixing-in only selected {@link https://developer.mozilla.org/en-US/docs/Web/API/fetch#options | RequestInit}
 * options.
 */
export type BaseRequestOptions = ProcessingParameters & UsedFetchOptions & {
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
   * The type of data specified that impact response processing. The default value is `json`,
   * and the response is parsed as JSON to an object. If the value is `text`,
   * the response is returned as plain text without parsing (and without splitting to individual responses).
   * The client is then responsible for parsing the response. This can be useful when a custom JSON
   * parsing is necessary.
   * Jolokia Simple API (jolokia-simple.js) doesn't support `text` as dataType.
   * @since 2.0.2
   */
  dataType?: "json" | "text"
  /**
   * A username used for HTTP Basic authentication. User may also pass value for `Authorization` header directly
   * with `headers` option ({@link UsedFetchOptions#headers}).
   */
  username?: string
  /**
   * A password used for HTTP Basic authentication. User may also pass value for `Authorization` header directly
   * with `headers` option ({@link UsedFetchOptions#headers}).
   */
  password?: string
  /**
   * Timeout for the HTTP request
   */
  timeout?: number
}

/**
 * Configuration of remote JMX Agent accessed using
 * {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html#protocol-proxy Jolokia proxy mode}
 */
export type RemoteAgentOptions = {
  /** Remote JMX Agent URL, for example `service:jmx:rmi:///jndi/rmi://targethost:9999/jmxrmi` */
  url: string
  /** Remote JMX usernam */
  user?: string
  /** Remote JMX password */
  password?: string
}

/**
 * Jolokia configuration object to specify default configuration values used when request-specific options are
 * not present.
 */
export type JolokiaConfiguration = BaseRequestOptions & {
  /**
   * Interval for poll-fetching (in ms) that can be changed during {@link IJolokia#start}
   */
  fetchInterval?: number
}

/**
 * Options that can be passed to a {@link IJolokia#request} method, which include success and error callback. When
 * not specified {@link IJolokia#request} method returns a Promise resolving to an array of responses. Previously,
 * lack of specified callbacks meant "synchronous AJAX request". Now it's "Fetch request returning a Promise".
 * A callback always receives a response and its index (when using bulk requests or multi-read attribute requests)
 */
export type RequestOptions = BaseRequestOptions & {
  /**
   * A hint about the value we want the returned promise to resolve to. `default` (or when not specified) means
   * a promise resolving to an array of Jolokia (good and error) responses is returned. Also, when `dataType=text`
   * this promise resolves to a string value for the response.
   * When specifying `response`, we're returning a Fetch API response directly, so user may do whatever is needed.
   */
  resolve?: "default" | "response"
  /**
   * A callback (which may be called with successful JSON response or a string) or an array of callbacks (JSON only)
   * to be used by {@link IJolokia#request} method
   */
  success?: "ignore" | ResponseCallback | TextResponseCallback | ResponseCallbacks
  /**
   * A callback (which may be called with error JSON response) or an array of callbacks
   * to be used by {@link IJolokia#request} method for error response
   */
  error?: "ignore" | ErrorCallback | ErrorCallbacks
}

// --- Types related to Jolokia requests

/**
 * An enumeration of supported Jolokia operation types
 */
export type RequestType = "read" | "write" | "exec" | "search" | "list" | "notification" | "version"

/**
 * A Jolokia base request object to be sent to remote Jolokia agent
 */
export type BaseRequest = {
  /**
   * A type of Jolokia request according to
   * {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html Jolokia protocol}
   */
  type: RequestType
  /**
   * Request configuration object
   */
  config?: ProcessingParameters
  /**
   * Remote/proxied JMX Agent configuration
   */
  target?: RemoteAgentOptions
}

/**
 * Jolokia {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html#read read request}
 */
export type ReadRequest = BaseRequest & {
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

/**
 * Jolokia {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html#write write request}
 */
export type WriteRequest = BaseRequest & {
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

/**
 * Jolokia {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html#exec exec request}
 */
export type ExecRequest = BaseRequest & {
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

/**
 * Jolokia {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html#search search request}
 */
export type SearchRequest = BaseRequest & {
  type: "search"
  /**
   * MBean pattern for searching
   */
  mbean: string
}

/**
 * Jolokia {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html#list list request}
 */
export type ListRequest = BaseRequest & {
  type: "list"
  /**
   * path within Jolokia traversal for MBean domains/names
   */
  path?: string
}

/**
 * Type of notification mode
 */
export type NotificationMode = "sse" | "pull";

/**
 * Jolokia {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html#notification notification request}
 */
export type NotificationRequest = BaseRequest & {
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
  mbean?: string
  /**
   * `filter` parameter
   */
  filter?: string
  /**
   * `handback` parameter
   */
  handback?: unknown
  /**
   * `handle` parameter
   */
  handle?: string
}

/**
 * Jolokia {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html#version version request}
 */
export type VersionRequest = BaseRequest & {
  type: "version"
}

/**
 * Generic Jolokia request object which combines all possible requests and can be used with tricky TypeScript
 * function matching when using functions as values in typed object/map.
 */
export type GenericRequest =
  Partial<ReadRequest & WriteRequest & ExecRequest & SearchRequest & ListRequest & NotificationRequest & VersionRequest>
  & BaseRequest

/**
 * Jolokia request - one of supported request types
 */
export type JolokiaRequest =
  | ReadRequest
  | WriteRequest
  | ExecRequest
  | SearchRequest
  | ListRequest
  | NotificationRequest
  | VersionRequest

// --- Types related to Jolokia responses

/**
 * A Jolokia base response received from Jolokia agent
 */
export type JolokiaResponse = {
  /**
   * Jolokia response status based on HTTP statuses, but not necessarily matching the HTTP status of HTTP response
   */
  status: number
  /** Timestamp of the response added by History interceptor (if available) */
  timestamp?: number
}

/**
 * Jolokia successful response. This is kind of an _envelope_ of all possible successful responses where the details
 * (specific to given request type) are contained in `value` field.
 */
export type JolokiaSuccessResponse = JolokiaResponse & {
  /** Original request for this response */
  request: JolokiaRequest
  /** Value returned for the request. Can be null for WriteRequest, but still the value should be available */
  value: string | number | JolokiaResponseValue | null
  /** History of previous responses for given reques (if History interceptor is available) */
  history?: JolokiaResponse
}

/**
 * Jolokia error response
 */
export type JolokiaErrorResponse = JolokiaResponse & {
  /** Original request for this response if it could be parsed */
  request?: JolokiaRequest
  /** Short error message */
  error: string
  /** Exception class name that caused this error response */
  error_type: string
  /** Stack trace as string value when `allowErrorDetails` is `true` and `includeStackTrace` is `true` or `runtime` */
  stacktrace?: string
  /** JSON-ified value of Exception created by bean extractor (using `java.lang.Exception` getters) */
  error_value?: Record<string, unknown>
}

// --- Types related to Jolokia response values - specific to Jolokia request types, but can differ
//     also within single type. For example "notification" requests have different "command" fields with
//     distinct responses (or no responses at all)

type JolokiaResponseValue = VersionResponseValue | NotificationResponseValue | NotificationPullValue

// ------ Version response

/**
 * Response matching the result of `org.jolokia.server.core.service.impl.VersionRequestHandler.handleRequest()`
 */
export type VersionResponseValue = {
  /** Agent version used */
  agent: string;
  /** Protocol version used */
  protocol: string;
  /** agentId (configurable at server side) */
  id?: string;
  /** Details about the agent */
  details?: AgentDetails
  /**
   * Additional information about the agent - for each
   * `org.jolokia.server.core.service.request.RequestHandler.getProvider()`
   */
  info: Record<string, AgentInfo>
  /** Configuration options for the agent */
  config: Record<string, string>
}

/**
 * Details about the agent. See `org.jolokia.server.core.service.api.AgentDetails.toJSONObject()`
 */
export type AgentDetails = {
  // Agent URL as the agent sees itself
  url: string
  // Whether the agent is secured and an authentication is required (0,1). If not given, this info is not known
  secured: boolean
  // Vendor of the detected container
  server_vendor: string
  // The product in which the agent is running
  server_product: string
  // Version of the server
  server_version: string
  // Version of the agent
  agent_version: string
  // The agent id
  agent_id: string
  // Description of the agent (if any)
  agent_description: string
}

/**
 * Additional information about single Jolokia `RequestHandler`
 */
export type AgentInfo = {
  product?: string
  vendor?: string
  version?: string
  extraInfo?: Record<string, unknown>
}

// ------ Notification response
//        See: org.jolokia.service.jmx.handler.notification.NotificationDispatcher.dispatch()

/**
 * Notification response values depend on the command of Notification request ({@link NotificationRequest#command})
 * Commands `unregister`, `remove`, `ping` and `open` do not return any value.
 */
export type NotificationResponseValue =
  | NotificationRegisterResponseValue
  | NotificationAddResponseValue
  | NotificationListResponseValue
  | null

/**
 * Resulf of notification `register` command for registration of a new client
 */
export type NotificationRegisterResponseValue = {
  /** Client id for the registered notification client (global for single Jolokia instance) */
  id: string
  /** Map of all `org.jolokia.server.core.service.notification.NotificationBackend` configurations */
  backend: Record<string, NotificationBackendConfig>
}

/**
 * Configuration if a single `org.jolokia.server.core.service.notification.NotificationBackend`
 */
export type NotificationBackendConfig = PullNotificationClientConfig | SseNotificationClientConfig

/**
 * A handle identifying the registered listener for given client
 */
export type NotificationAddResponseValue = string

/**
 * Result of notification `list` command that lists handles for given client
 */
export type NotificationListResponseValue = Record<string, NotificationOptions>

/**
 * Type describing a result of `pull` operation on "pull" notification MBean.
 * See `org.jolokia.server.core.service.notification.NotificationResult`.
 * While it's de-facto a response to generic Jolokia `exec` operation on particular bean, we provide a type
 * definition for it.
 */
export type NotificationPullValue = {
  /** Array of `javax.management.Notification` objects */
  notifications: JMXNotification[]
  handback: unknown
  dropped: number
  handle?: string
}

/**
 * Type representing a `javax.management.Notification` object
 */
export type JMXNotification = {
  message: string
  sequenceNumber: number
  timeStamp: number
  type: string
  userData: unknown
}

// --- Types related to callbacks
//     Even if we switched from xhr to Fetch API, we didn't go full-Promises. Callbacks are needed for registered
//     (background) tasks/jobs, so we allow then for jolokia.request() as well (promise is not returned in this case)
//     when using jolokia.request(), we can pass either a request or an array of requests (bulk request).
//
//     In both cases we can specify a callback or an array of callbacks and for bulk requests callbacks will be called
//     in round-robin fashion (for bulk-request of 3 and array of 2 callbacks, first callback will be called with
//     response 0 and 2, while callback 1 will be called with response 1)
//
//     for registered jobs, we can specify and generic callback, which will be called with all the responses. This
//     is not possible with normal jolokia.request() call

export type GenericCallback = (response: JolokiaResponse, index: number) => void

/**
 * Single response callback receiving JSON response and its index (when calling Jolokia with an array
 * of requests).
 */
export type ResponseCallback = (response: JolokiaSuccessResponse, index: number) => void

/**
 * Single response callback receiving entire text response.
 */
export type TextResponseCallback = (response: string) => void

/**
 * An array of callbacks, each accepting JSON response (successfull) with response index. Only JSON type is handled.
 */
export type ResponseCallbacks = ((response: JolokiaSuccessResponse, index: number) => void)[]

/**
 * A callback interface to be notified about error response from Jolokia agent.
 */
export type ErrorCallback = (response: JolokiaErrorResponse, index: number) => void

/**
 * An array of callbacks, each accepting JSON response (error) with response index.
 */
export type ErrorCallbacks = ((response: JolokiaErrorResponse, index: number) => void)[]

/**
 * A response callback used for registered jobs called periodically. In addition to standard {@link ResponseCallback},
 * this method receives registered job ID as 2nd argument and index of the response is passed as 3rd argument.
 * Only JSON responses are handled.
 */
export type JobResponseCallback = (response: JolokiaSuccessResponse, jobId: number, index: number) => void

/**
 * An error response callback used for registered jobs called periodically. In addition to standard
 * {@link ErrorCallback}, this method receives registered job ID as 2nd argument and index of the response is passed
 * as 3rd argument.
 */
export type JobErrorCallback = (response: JolokiaErrorResponse, jobId: number, index: number) => void

/**
 * Special job-related callback, which simply receives all the responses/errors in single array parameter
 */
export type JobCallback = (...responses: (JolokiaSuccessResponse | JolokiaErrorResponse)[]) => void

// --- Types related to job management

/**
 * Object used with {@link IJolokia#register} to setup periodically invoked Jolokia job
 */
export type JobRegistrationConfig = {
  /** Generic callback accepting intermixed successful and error responses in single array */
  callback?: JobCallback
  /** Success callback called for each successful response */
  success?: JobResponseCallback
  /** Error callback called for each error response */
  error?: JobErrorCallback
  /** Parameters to be send under `config` key of POST data */
  config?: ProcessingParameters
  /** Flag to handle HTTP 304 (not modified) headers */
  onlyIfModified?: boolean
}

/**
 * Definition of regitered Jolokia job - combines requests to send periodically and callbacks to notify when
 * the response is received
 */
export type Job = {
  requests: JolokiaRequest[]
  callback?: JobCallback
  success?: JobResponseCallback
  error?: JobErrorCallback
  config?: ProcessingParameters
  onlyIfModified?: boolean
  lastModified?: number
}

// --- Types related to notifications

/**
 * Pull notification configuration options
 */
export type PullNotificationClientConfig = {
  /** MBean name which will collect the notifications - need to be fetched explicitly */
  store: string
  /** Size of backend storage for the notifications */
  maxEntries: number
}

/**
 * SSE notification configuration options
 */
export type SseNotificationClientConfig = {
  /** Content type for SSE notification mechanism */
  "backChannel.contentType": string
  /** Encoding used for SSE notification mechanism */
  "backChannel.encoding": string
}

/**
 * Configuration options for notification handling
 */
export type NotificationOptions = {
  /** Backend mode - one of the supported modes */
  mode?: NotificationMode
  /** MBean on which to register a notification listener */
  mbean?: string
  /** List of filter on notification types which are OR-ed together */
  filter?: string
  /** Additional configuration used for notifications */
  config?: Record<string, unknown>
  /** Any value that's returned with the notification for corelation purposes */
  handback?: string
  /** A callback for notificaiton handling */
  callback?: (result: NotificationPullValue) => void
}

/**
 * Representation of registered notification listener
 */
export type NotificationHandle = {
  /** Handle ID within a given client */
  id: string
  /** A notification mode for this handle */
  mode: NotificationMode
}

// --- Jolokia interfaces - public API

/**
 * Main Jolokia client interface for communication with remote Jolokia agent.
 */
interface IJolokia {

  /** Version of Jolokia JavaScript client library */
  readonly CLIENT_VERSION: string

  /**
   * The request method using one or more JSON requests and sending it to the agent. Beside the
   * request a bunch of options can be given, which are merged with the options provided
   * at the constructor (where the options given here take precedence).
   * @param request JSON request object (or array of objects) compliant with
   *        {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html|Jolokia protocol} representing
   *        the request(s) to send to remote Jolokia agent.
   * @param params parameters used for sending the request which may override default configuration.
   *        These are not options passed diretly (unchnaged) to `fetch()` call
   * @returns the response promise object resolving to desired value:<ul>
   *          <li>a string response (when `dataType=text`)</li>
   *          <li>an array of Jolokia responses (when `dataType=json`, default)</li>
   *          <li>a Fetch API Response object, when `resolve=response` is specified in {@link RequestOptions}</li>
   *          <li>When callbacks are specified in {@link RequestOptions}, the returned promise
   *          resolves to undefined, but user can attach `.catch()` for Fetch API error handling. The response is
   *          delivered via the callbacks</li></ul>
   */
  request(request: JolokiaRequest | JolokiaRequest[], params?: RequestOptions):
    Promise<string | (JolokiaSuccessResponse | JolokiaErrorResponse)[] | Response | undefined>

  /**
   * Register one or more requests for periodically polling the agent along with a callback to call on receipt
   * of the response.
   *
   * The first argument can be either an object or a function. The remaining arguments are interpreted
   * as Jolokia request objects.
   *
   * If a function is given or an object with an attribute `callback` holding a function, then
   * this function is called with all responses received as argument, regardless whether the individual response
   * indicates a success or error state.
   *
   * If the first argument is an object with two callback attributes `success` and `error`,
   * these functions are called for _each_ response separately, depending whether the response
   * indicates success or an error state. If multiple requests have been registered along with this callback object,
   * the callback is called multiple times, one for each response in the same order as the requests are given.
   * As second argument, the handle (job ID) which is returned by this method is given and as third argument the index
   * within the list of requests.
   *
   * If the first argument is an object, an additional 'config' attribute with processing parameters can
   * be given which is used as default for the registered requests.
   * Request with a 'config' section take precedence.
   *
   * @param callback a callback or job configuration (params + callbacks) for the registered job
   * @param requests an array of requests to be called periodically
   * @returns handle which can be used for unregistering the request again or for correlation purposes in the callbacks
   */
  register(callback: JobCallback | JobRegistrationConfig, ...requests: JolokiaRequest[]): number

  /**
   * Unregisters one or more request which has been registered with {@link #register}. As parameter
   * the handle returned during the registration process must be given
   * @param handle the job handle to unregister
   */
  unregister(handle: number): void

  /**
   * Return an array of jobIds for currently registered jobs.
   * @return Array of job jobIds or an empty array
   */
  jobs(): number[]

  /**
   * Start the poller. The interval between two polling attempts can be optionally given (in milliseconds) or
   * is taken from the parameter `fetchInterval` given at construction time. If no interval is given at all,
   * 30 seconds is the default.
   *
   * If the poller is already running (i.e. {@link #isRunning} is `true` then the scheduler
   * is restarted, but only if the new interval differs from the currently active one.
   *
   * Jolokia uses single function passed to `setInterval()` which calls remote agent with single bulk request
   * containing all registered jobs and each job's requests.
   *
   * @param interval Interval (in ms) for poll-fetching from remote Jolokia agent.
   */
  start(interval?: number): void

  /**
   * Stop the poller. If the poller is not running, no operation is performed.
   */
  stop(): void

  /**
   * Check whether the poller is running.
   * @returns true if the poller is running, false otherwise.
   */
  isRunning(): boolean

  /**
   * Registers a listener for single MBean returning a handle which can be used to cancel the listener
   * registration. Passed options are used to configure the handler for notifications.
   * @param opts
   * @returns a promise of {@link NotificationHandle} representing an MBean notification listener for single
   *          client (created during registration of first listener) for entire Jolokia instance
   */
  addNotificationListener(opts: NotificationOptions): Promise<NotificationHandle>

  /**
   * Unregisters previously registered listener
   * @param handle a listener registration handler created by {@link #addNotificationListener}.
   * @returns a promise resolving to true if listener was successfully unregistered at server side
   */
  removeNotificationListener(handle: NotificationHandle): Promise<boolean>

  /**
   * Removes the notification client, which is created for all the registrations.
   * @returns a promise resolving to true if client was successfully removed at server side
   */
  unregisterNotificationClient(): Promise<boolean>

  // --- Utility functions available statically and in Jolokia.prototype (instance methods)

  /**
   * Escape URL part (segment) according to
   * {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html#_escaping_rules_in_get_requests Jolokia escaping rules}
   * @param part An URL segment for Jolokia GET URL
   * @returns URL segment with Jolokia escaping rules applied
   */
  escape(part: string): string

  /**
   * Utility method which checks whether a response is an error or a success (from Jolokia, not HTTP perspective)
   * @param resp response to check
   * @return true if response is an error, false otherwise
   */
  isError(resp: JolokiaResponse): boolean
}

interface JolokiaStatic {
  (config: JolokiaConfiguration | string): undefined

  new(config: JolokiaConfiguration | string): IJolokia

  // --- Utility functions available statically and in Jolokia.prototype (instance methods)

  escape(part: string): string

  isError(resp: JolokiaResponse): boolean
}

export type { IJolokia, JolokiaStatic }
