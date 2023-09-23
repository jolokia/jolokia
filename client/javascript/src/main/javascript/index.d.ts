/**
 * Jolokia client.
 */
export default class Jolokia {
    /**
     * Constructor for creating a client to the Jolokia agent.
     *
     * An object containing the default parameters can be provided as argument. For the possible parameters
     * see {@link #request()}.
     *
     * @param param either a string in which case it is used as the URL to the agent or
     *              an object with the default parameters as key-value pairs
     */
    constructor(url: string);
    constructor(param: BaseRequestOptions);

    /**
     * Jolokia Javascript Client version
     */
    CLIENT_VERSION: string;

    // ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    // Public methods

    /**
     * The request method using one or more JSON requests and sending it to the agent. Beside the
     * request a bunch of options can be given, which are merged with the options provided
     * at the constructor (where the options given here take precedence).
     *
     * Known options are:
     *
     * <dl>
     *   <dt>url</dt>
     *   <dd>Agent URL, which is mandatory</dd>
     *   <dt>method</dt>
     *   <dd>
     *     Either "post" or "get" depending on the desired HTTP method (case does not matter).
     *     Please note, that bulk requests are not possible with "get". On the other
     *     hand, JSONP requests are not possible with "post" (which obviously implies
     *     that bulk request cannot be used with JSONP requests). Also, when using a
     *     <code>read</code> type request for multiple attributes, this also can
     *     only be sent as "post" requests. If not given, a HTTP method is determined
     *     dynamically. If a method is selected which doesn't fit to the request, an error
     *     is raised.
     *   </dd>
     *   <dt>jsonp</dt>
     *   <dd>
     *     Whether the request should be sent via JSONP (a technique for allowing cross
     *     domain request circumventing the infamous "same-origin-policy"). This can be
     *     used only with HTTP "get" requests.
     *    </dd>
     *   <dt>success</dt>
     *   <dd>
     *     Callback function which is called for a successful request. The callback receives
     *     the response as single argument. If no <code>success</code> callback is given, then
     *     the request is performed synchronously and gives back the response as return
     *     value.
     *   </dd>
     *   <dt>error</dt>
     *   <dd>
     *     Callback in case a Jolokia error occurs. A Jolokia error is one, in which the HTTP request
     *     succeeded with a status code of 200, but the response object contains a status other
     *     than OK (200) which happens if the request JMX operation fails. This callback receives
     *     the full Jolokia response object (with a key <code>error</code> set). If no error callback
     *     is given, but an asynchronous operation is performed, the error response is printed
     *     to the Javascript console by default.
     *   </dd>
     *   <dt>ajaxError</dt>
     *   <dd>
     *     Global error callback called when the Ajax request itself failed. It obtains the same arguments
     *     as the error callback given for <code>jQuery.ajax()</code>, i.e. the <code>XmlHttpResponse</code>,
     *     a text status and an error thrown. Refer to the jQuery documentation for more information about
     *     this error handler.
     *   </dd>
     *   <dt>username</dt>
     *   <dd>A username used for HTTP authentication</dd>
     *   <dt>password</dt>
     *   <dd>A password used for HTTP authentication</dd>
     *   <dt>timeout</dt>
     *   <dd>Timeout for the HTTP request</dd>
     *   <dt>maxDepth</dt>
     *   <dd>Maximum traversal depth for serialization of complex return values</dd>
     *   <dt>maxCollectionSize</dt>
     *   <dd>
     *      Maximum size of collections returned during serialization.
     *      If larger, the collection is returned truncated.
     *   </dd>
     *   <dt>maxObjects</dt>
     *   <dd>
     *      Maximum number of objects contained in the response.
     *   </dd>
     *   <dt>ignoreErrors</dt>
     *   <dd>
     *     If set to true, errors during JMX operations and JSON serialization
     *     are ignored. Otherwise if a single deserialization fails, the whole request
     *     returns with an error. This works only for certain operations like pattern reads.
     *   </dd>
     * </dl>
     *
     * @param request the request to send
     * @param params parameters used for sending the request
     * @return the response object if called synchronously or nothing if called for asynchronous operation.
     */
    request(request: Request, params?: RequestOptions): unknown | null;
    request(request: Request[], params?: BulkRequestOptions): unknown[] | null;

    // ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    // Scheduler related methods

    /**
     * Register one or more requests for periodically polling the agent along with a callback to call on receipt
     * of the response.
     *
     * The first argument can be either an object or a function. The remaining arguments are interpreted
     * as Jolokia request objects
     *
     * If a function is given or an object with an attribute <code>callback</code> holding a function, then
     * this function is called with all responses received as argument, regardless whether the individual response
     * indicates a success or error state.
     *
     * If the first argument is an object with two callback attributes <code>success</code> and <code>error</code>,
     * these functions are called for <em>each</em> response separately, depending whether the response
     * indicates success or an error state. If multiple requests have been registered along with this callback object,
     * the callback is called multiple times, one for each request in the same order as the request are given.
     * As second argument, the handle which is returned by this method is given and as third argument the index
     * within the list of requests.
     *
     * If the first argument is an object, an additional 'config' attribute with processing parameters can
     * be given which is used as default for the registered requests.
     * Request with a 'config' section take precedence.
     *
     * @param callback and options specification.
     * @param request, request, .... One or more requests to be registered for this single callback
     * @return handle which can be used for unregistering the request again or for correlation purposes in the callbacks
     */
    register(callback: (...response: Response[]) => void, ...request: Request[]): number;
    register(params: RequestOptions, ...request: Request[]): number;

    /**
     * Unregister one or more request which has been registered with {@link #register}. As parameter
     * the handle returned during the registration process must be given
     * @param handle the job handle to unregister
     */
    unregister(handle: number): void;

    /**
     * Return an array of jobIds for currently registered jobs.
     * @return Array of job jobIds or an empty array
     */
    jobs(): number[];

    /**
     * Start the poller. The interval between two polling attempts can be optionally given or are taken from
     * the parameter <code>fetchInterval</code> given at construction time. If no interval is given at all,
     * 30 seconds is the default.
     *
     * If the poller is already running (i.e. {@link #isRunning()} is <code>true</code> then the scheduler
     * is restarted, but only if the new interval differs from the currently active one.
     *
     * @param interval interval in milliseconds between two polling attempts
     */
    start(interval: number): void;

    /**
     * Stop the poller. If the poller is not running, no operation is performed.
     */
    stop(): void;

    /**
     * Check whether the poller is running.
     * @return true if the poller is running, false otherwise.
     */
    isRunning(): boolean;

    // ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    // Notification handling

    addNotificationListener(opts: NotificationOptions): void;

    removeNotificationListener(handle: { id: string; mode: NotificationMode; }): void;

    unregisterNotificationClient(): void;

    // ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    // Simple API (jolokia-simple.js)

    /**
     * Get one or more attributes
     *
     * @param mbean objectname of MBean to query. Can be a pattern.
     * @param attribute attribute name. If an array, multiple attributes are fetched.
     *                  If <code>null</code>, all attributes are fetched.
     * @param path optional path within the return value. For multi-attribute fetch, the path
     *             is ignored.
     * @param opts options passed to Jolokia.request()
     * @return the value of the attribute, possibly a complex object
     */
    getAttribute(mbean: string, attribute: string, path: string, opts?: AttributeRequestOptions): unknown | null;
    getAttribute(mbean: string, attribute: string, opts?: AttributeRequestOptions): unknown | null;

    /**
     * Set an attribute on a MBean.
     *
     * @param mbean objectname of MBean to set
     * @param attribute the attribute to set
     * @param value the value to set
     * @param path an optional <em>inner path</em> which, when given, is used to determine
     *        an inner object to set the value on
     * @param opts additional options passed to Jolokia.request()
     * @return the previous value
     */
    setAttribute(mbean: string, attribute: string, value: unknown, path: string, opts?: AttributeRequestOptions): unknown | null;
    setAttribute(mbean: string, attribute: string, value: unknown, opts?: AttributeRequestOptions): unknown | null;

    /**
     * Execute a JMX operation and return the result value
     *
     * @param mbean objectname of the MBean to operate on
     * @param operation name of operation to execute. Can contain a signature in case overloaded
     *                  operations are to be called (comma separated fully qualified argument types
     *                  append to the operation name within parentheses)
     * @param arg1, arg2, ..... one or more argument required for executing the operation.
     * @param opts optional options for Jolokia.request() (must be an object)
     * @return the return value of the JMX operation.
     */
    execute(mbean: string, operation: string, ...arguments: unknown[]): unknown | null;

    /**
     * Search for MBean based on a pattern and return a reference to the list of found
     * MBeans names (as string). If no MBean can be found, <code>null</code> is returned. For
     * example,
     *
     * jolokia.search("*:j2eeType=J2EEServer,*")
     *
     * searches all MBeans whose name are matching this pattern, which are according
     * to JSR77 all application servers in all available domains.
     *
     * @param mbeanPattern pattern to search for
     * @param opts optional options for Jolokia.request()
     * @return an array with ObjectNames as string
     */
    search(mbeanPattern: string, opts?: SearchRequestOptions): string[] | null;

    /**
     * This method return the version of the agent and the Jolokia protocol
     * version as part of an object. If available, server specific information
     * like the application server's name are returned as wel.
     * A typical response looks like
     *
     * <pre>
     *  {
     *    protocol: "4.0",
     *    agent: "0.82",
     *    info: {
     *       product: "glassfish",
     *       vendor": "Sun",
     *       extraInfo: {
     *          amxBooted: false
     *       }
     *  }
     * </pre>
     *
     * @param opts optional options for Jolokia.request()
     * @param version and other meta information as object
     */
    version(opts?: VersionRequestOptions): VersionResponse | null;

    /**
     * Get all MBeans as registered at the specified server. A C<$path> can be
     * specified in order to fetch only a subset of the information. When no path is
     * given, the returned value has the following format
     *
     * <pre>
     * {
     *     &lt;domain&gt; :
     *     {
     *       &lt;canonical property list&gt; :
     *       {
     *           "attr" :
     *           {
     *              &lt;attribute name&gt; :
     *              {
     *                 desc : &lt;description of attribute&gt;
     *                 type : &lt;java type&gt;,
     *                 rw : true/false
     *              },
     *              ....
     *           },
     *           "op" :
     *           {
     *              &lt;operation name&gt; :
     *              {
     *                "desc" : &lt;description of operation&gt;
     *                "ret" : &lt;return java type&gt;
     *                "args" :
     *                [
     *                   {
     *                     "desc" : &lt;description&gt;,
     *                     "name" : &lt;name&gt;,
     *                     "type" : &lt;java type&gt;
     *                   },
     *                   ....
     *                ]
     *              },
     *              ....
     *       },
     *       ....
     *     }
     *     ....
     *  }
     * </pre>
     *
     * A complete path has the format &lt;domain&gt;/property
     * list&gt;/("attribute"|"operation")/&lt;index&gt;">
     * (e.g. <code>java.lang/name=Code Cache,type=MemoryPool/attribute/0</code>). A path can be
     * provided partially, in which case the remaining map/array is returned. The path given must
     * be already properly escaped (i.e. slashes must be escaped like <code>!/</code> and exclamation
     * marks like <code>!!</code>.
     * See also the Jolokia Reference Manual for a more detailed discussion of inner paths and escaping.
     *
     * @param path optional path for diving into the list
     * @param opts optional opts passed to Jolokia.request()
     */
    list(path: string | string[], opts?: ListRequestOptions): ListResponse | null;
    list(opts?: ListRequestOptions): ListResponse | null;
}

/**
 * Processing parameters that influence Jolokia operations.
 *
 * @see {@link https://jolokia.org/reference/html/protocol.html#processing-parameters}
 */
export interface ProcessParameters {
    /**
     * Maximum traversal depth for serialization of complex return values
     */
    maxDepth?: number;
    /**
     * Maximum size of collections returned during serialization.
     * If larger, the collection is returned truncated.
     */
    maxCollectionSize?: number;
    /**
     * Maximum number of objects contained in the response.
     */
    maxObjects?: number;
    /**
     * If set to true, errors during JMX operations and JSON serialization
     * are ignored.Otherwise if a single deserialization fails, the whole request
     * returns with an error. This works only for certain operations like pattern reads.
     */
    ignoreErrors?: boolean;
    /**
     * The MIME type to return for the response. By default, this is <code>text/plain</code>,
     * but it can be useful for some tools to change it to <code>application/json</code>.
     * Init parameters can be used to change the default mime type. Only <code>text/plain</code>
     * and <code>application/json</code> are allowed. For any other value Jolokia
     * will fallback to <code>text/plain</code>.
     */
    mimeType?: string;
    /**
     * Defaults to <code>true</code> to return the canonical format of property
     * lists. If set to <code>false</code> then the default unsorted property list
     * is returned.
     */
    canonicalNaming?: boolean;
    /**
     * If set to <code>true</code>, then in case of an error the stack trace is
     * included. With <code>false</code> no stack trace will be returned, and when
     * this parameter is set to <code>runtime</code> only for RuntimeExceptions
     * a stack trace is put into the error response. Default is <code>true</code>
     * if not set otherwise in the global agent configuration.
     */
    includeStackTrace?: "true" | "false" | "runtime";
    /**
     * If this parameter is set to <code>true</code> then a serialized version of
     * the exception is included in an error response. This value is put under the
     * key <code>error_value</code> in the response value. By default this is set
     * to <code>false</code> except when the agent global configuration option is
     * configured otherwise.
     */
    serializeException?: boolean;
    /**
     * If this parameter is given, its value is interpreted as epoch time (seconds
     * since 1.1.1970) and if the requested value did not change since this time,
     * an empty response (with no <code>value</code>) is returned and the response
     * status code is set to 304 ("Not modified"). This option is currently only
     * supported for <code>LIST</code> requests. The time value can be extracted
     * from a previous' response <code>timestamp</code>.
    */
    ifModifiedSince?: number;
}

/**
 * Base request options that influence a Jolokia request.
 *
 * @see {@link https://jolokia.org/reference/html/clients.html#js-request-options}
 */
export interface BaseRequestOptions extends ProcessParameters {
    /**
     * Agent URL, which is mandatory
     */
    url?: string;
    /**
     * Either "post" or "get" depending on the desired HTTP method (case does not matter).
     * Please note, that bulk requests are not possible with "get". On the other
     * hand, JSONP requests are not possible with "post" (which obviously implies
     * that bulk request cannot be used with JSONP requests). Also, when using a
     * <code>read</code> type request for multiple attributes, this also can
     * only be sent as "post" requests. If not given, a HTTP method is determined
     * dynamically. If a method is selected which doesn't fit to the request, an error
     * is raised.
     */
    method?: "get" | "post";
    /**
     * Whether the request should be sent via JSONP (a technique for allowing cross
     * domain request circumventing the infamous "same-origin-policy"). This can be
     * used only with HTTP "get" requests.
     */
    jsonp?: boolean;
    /**
     * Global error callback called when the Ajax request itself failed.It obtains the same arguments
     * as the error callback given for <code>jQuery.ajax()</code>, i.e. the <code>XmlHttpResponse</code>,
     * a text status and an error thrown.Refer to the jQuery documentation for more information about
     * this error handler.
     */
    ajaxError?: (xhr: JQueryXHR, text: string, error: string) => void;
    /**
     * A username used for HTTP authentication
     */
    username?: string;
    /**
     * A password used for HTTP authentication
     */
    password?: string;
    /**
     * Timeout for the HTTP request
     */
    timeout?: number;
}

/**
 * Request options for a single Jolokia request.
 */
export interface RequestOptions extends BaseRequestOptions {
    /**
     * Callback function which is called for a successful request. The callback receives
     * the response as single argument. If no <code>success</code> callback is given, then
     * the request is performed synchronously and gives back the response as return
     * value.
     */
    success?: (response: Response) => void;
    /**
     * Callback in case a Jolokia error occurs. A Jolokia error is one, in which the HTTP request
     * succeeded with a status code of 200, but the response object contains a status other
     * than OK (200) which happens if the request JMX operation fails. This callback receives
     * the full Jolokia response object (with a key <code>error</code> set). If no error callback
     * is given, but an asynchronous operation is performed, the error response is printed
     * to the Javascript console by default.
     */
    error?: (error: ErrorResponse) => void;
}

/**
 * Request options for a bulk Jolokia request.
 */
export interface BulkRequestOptions extends BaseRequestOptions {
    /**
     * Callback function which is called for a successful request. The callback receives
     * the response as single argument. If no <code>success</code> callback is given, then
     * the request is performed synchronously and gives back the response as return
     * value.
     */
    success?: ((response: Response) => void)[];
    /**
     * Callback in case a Jolokia error occurs. A Jolokia error is one, in which the HTTP request
     * succeeded with a status code of 200, but the response object contains a status other
     * than OK (200) which happens if the request JMX operation fails. This callback receives
     * the full Jolokia response object (with a key <code>error</code> set). If no error callback
     * is given, but an asynchronous operation is performed, the error response is printed
     * to the Javascript console by default.
     */
    error?: ((error: ErrorResponse) => void)[];
}

export type Request =
    | { type: "read"; mbean: string; attribute?: string | string[]; path?: string; }
    | { type: "write"; mbean: string; attribute: string; value: unknown; path?: string; }
    | { type: "exec"; mbean: string; operation: string; arguments?: unknown[]; }
    | { type: "search"; mbean: string; }
    | { type: "list"; path?: string; }
    | { type: "version"; }
    | {
        type: "notification";
        command: "register" | "unregister" | "add" | "remove" | "ping" | "open" | "list";
        client?: string;
        mode?: NotificationMode;
        filter?: string[];
        config?: Record<string, unknown>;
        handback?: unknown;
        handle?: string;
    };

export interface Response {
    status: number;
    timestamp: number;
    request: Request;
    value: unknown;
    history?: Response[];
}

export interface ErrorResponse extends Response {
    error_type: string;
    error: string;
    stacktrace: string;
}

export type NotificationMode = "sse" | "pull";

export interface NotificationOptions {
    mode?: NotificationMode;
    mbean?: string;
    filter?: string;
    config?: unknown;
    handback?: string;
}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Simple API (jolokia-simple.js)

/**
 * Request options for getting and setting an attribute.
 */
export interface AttributeRequestOptions extends BaseRequestOptions {
    success?: (value: unknown) => void;
    error?: (error: ErrorResponse) => void;
}

/**
 * Request options for searching MBeans.
 */
export interface SearchRequestOptions extends BaseRequestOptions {
    success?: (objectNames: string[]) => void;
    error?: (error: ErrorResponse) => void;
}

/**
 * Request options for version.
 */
export interface VersionRequestOptions extends BaseRequestOptions {
    success?: (version: VersionResponse) => void;
    error?: (error: ErrorResponse) => void;
}

export interface VersionResponse {
    protocol: string;
    agent: string;
    id?: string;
    details?: Record<string, unknown>;
    info: VersionInfo;
    config: Record<string, string>;
}

export interface VersionInfo {
    product?: string;
    vendor?: string;
    version?: string;
    extraInfo?: Record<string, unknown>;
}

/**
 * Request options for listing MBeans.
 */
export interface ListRequestOptions extends BaseRequestOptions {
    success?: (list: ListResponse) => void;
    error?: (error: ErrorResponse) => void;
}

export type ListResponse = JmxDomains | JmxDomain | MBeanInfo;

export type JmxDomains = Record<string, JmxDomain>;

export type JmxDomain = Record<string, MBeanInfo>;

export interface MBeanInfo {
    desc: string;
    class?: string;
    attr?: Record<string, MBeanAttribute>;
    op?: Record<string, MBeanOperation>;
    notif?: Record<string, MBeanNotification>;
}

export interface MBeanAttribute {
    type: string;
    desc: string;
    rw: boolean;
}

export interface MBeanOperation {
    args: MBeanOperationArgument[];
    ret: string;
    desc: string;
}

export interface MBeanOperationArgument {
    desc: string;
    name: string;
    type: string;
}

export interface MBeanNotification {
    name: string;
    desc: string;
    types: string[];
}
