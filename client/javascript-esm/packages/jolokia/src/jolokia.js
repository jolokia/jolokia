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

// Default parameters for GET and POST requests
// See: https://developer.mozilla.org/en-US/docs/Web/API/fetch#options
const DEFAULT_FETCH_PARAMS = {
  method: "POST",
  cache: "no-store",
  credentials: "same-origin",
  redirect: "error"
}

// Processing parameters which are added to the URL as query parameters if given as options
const PROCESSING_PARAMS = [
  "maxDepth",
  "maxCollectionSize",
  "maxObjects",
  "ignoreErrors",
  "canonicalNaming",
  "serializeException",
  "includeStackTrace",
  "ifModifiedSince"
]

/**
 * @typedef {object} RequestParams Request configuration object to specify additional or changed properties which
 * override Jolokia default configuration values specified at creation time.
 * @property {string} url Agent URL, which is mandatory
 * @property {string} [method] Selects HTTP method to use (`post` or `get`). Note that bulk requests can't be used with
 * `get` method. Also, when using a `read` type request for multiple attributes, this also can only be sent as `post`
 * requests. If not given, a HTTP method is determined dynamically. If a method is selected which doesn't fit to the
 * request, an error is raised.
 * @property {boolean} [jsonp] Deprecated
 * @property {Function} [success] I'll change to promise API. Callback function which is called for a successful request.
 * The callback receives the response as single argument.
 * @property {Function} [error] I'll change to promise API. Callback in case a Jolokia error occurs. A Jolokia error is
 * one, in which the HTTP requests ucceeded with a status code of 200, but the response object contains a status other
 * than `OK` (200) which happens if the request JMX operation fails. This callback receives the full Jolokia response
 * object (with a key `error` set). If no error callback is given, but an asynchronous operation is performed,
 * the error response is printed to the JavaScript console by default.
 * @property {Function} [ajaxError] I'll change to promise API. Global error callback called when the Ajax request
 * itself failed. It obtains the same arguments as the error callback given for `jQuery.ajax()`, i.e. the
 * `XmlHttpResponse`, a text status and an error thrown. Refer to the jQuery documentation for more information about
 * this error handler.
 * @property {string} [username] A username used for HTTP authentication
 * @property {string} [password] A password used for HTTP authentication
 * @property {number} [timeout=30000] Timeout for the HTTP request
 * @property {number} [maxDepth] Maximum traversal depth for serialization of complex return values
 * @property {number} [maxCollectionSize] Maximum size of collections returned during serialization. If larger,
 * the collection is returned truncated.
 * @property {number} [maxObjects] Maximum number of objects contained in the response.
 * @property {boolean} [ignoreErrors] If set to true, errors during JMX operations and JSON serialization are ignored.
 * Otherwise if a single deserialization fails, the whole request returns with an error. This works only for certain
 * operations like pattern reads.
 * @property {string} [credentials] A value for `credentials` option for `fetch()` call. Defaults to `same-origin` for
 * security reasons. If user wants cross-origin requests, this value has to be set to `include`
 * @property {object} [headers] A map of additional headers that may be pased along remote Jolokia call
 */

/**
 * @typedef {RequestParams} JolokiaConfiguration Jolokia configuration object to specify default configuration values, which
 * may be changed for each request
 * @augments RequestParams
 * @property {number} [fetchInterval] interval for poll-fetching that can be changed during {@link Jolokia#start}
 */

/**
 * @typedef {'read'|'write'} RequestType A type of Jolokia request
 */

/**
 * @typedef {object} Request A Jolokia request object to be sent to remote Jolokia agent
 * @property {RequestType} type A type of Jolokia request according to
 * {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html Jolokia protocol}
 * @property {object} config Request configuration object
 * @property {string|string[]} attribute attribute(s) to read
 * @property {string} mbean target MBean for invocation or attribute access
 * @property {string} path path within Jolokia traversal for MBean domains/names
 * @property {string|object[]} [value] Value to be set with `write` request
 * @property {string} [operation] Operation name for Jolokia `exec` request
 * @property {object|object[]} [arguments] Arguments to use with Jolokia `exec` request
 * @property {string} [command] A `notification` operation command to use
 * @property {string} [client] A `notification` client ID to use
 * @property {string} [handback] A `notification` `handback` parameter
 * @property {string} [filter] A `notification` `filter` parameter
 * @property {string} [handle] A `notification` `handle` parameter
 * @property {object} target Target configuration object for
 * {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html#protocol-proxy Jolokia proxy mode}
 */

/**
 * @typedef {object} Job Registered request or request batch to be sent periodically to remote Jolokia agent
 * @property {number} [id] Job identifier
 */

/**
 * Main Jolokia creation function/class.
 * For backward compatibility, it can be used both as function and as constructor.
 * @param {string|JolokiaConfiguration} config a string with URL of remote Jolokia agent or a configuration object
 * @returns {Jolokia} Jolokia object to access remote Jolokia agent
 * @class
 */
function Jolokia(config) {
  if (!new.target) {
    // when invoked as function, return properly create object with bound "this" reference
    return new Jolokia(config)
  }

  // ++++++++++++++++++++++++++++++++++++++++++++++++++
  // Function-scoped state not exposed as properties of Jolokia class or instance

  // Registered requests for fetching periodically
  let jobs = []

  // Our client id and notification backend config Is null as long as notifications are not used
  let client = null

  // Options used for every request
  let agentOptions = {}

  // State of the scheduler
  let pollerIsRunning = false

  // timer id for poller's setInterval call
  let timerId = null

  if (typeof config === "string") {
    config = { url: config }
  }
  Object.assign(agentOptions, DEFAULT_FETCH_PARAMS, config)

  // ++++++++++++++++++++++++++++++++++++++++++++++++++
  // Public API (instance methods that may use function-scoped state)

  /**
   * The request method using one or more JSON requests and sending it to the agent. Beside the
   * request a bunch of options can be given, which are merged with the options provided
   * at the constructor (where the options given here take precedence).
   * @param {Request|Request[]} request JSON request object (or array of objects) compliant with
   * {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html|Jolokia protocol} representing the requests
   * to send to remote Jolokia agent.
   * @param {RequestParams} params parameters used for sending the request which may override default configuration.
   * These are not options passed diretly (unchnaged) to `fetch()` call
   * @returns {Promise} the response promise object
   */
  // TODO: prepare related method that uses pre-configured fetch() parameters (for bulk requests)
  this.request = async function(request, params) {
    const opts = Object.assign({}, agentOptions, params)
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
    const fetchOptions = Object.assign({}, DEFAULT_FETCH_PARAMS)
    fetchOptions.headers = {}

    let method = extractMethod(request, opts)
    let url = ensureTrailingSlash(opts.url)

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
    if (opts["username"] && opts["password"]) {
      if (typeof window.btoa === "function") {
        fetchOptions.headers["Authorization"] = "Basic " + window.btoa(opts["username"] + ":" + opts["password"])
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
    // it (which is good) with Fetch API
    // callbacks (success and error) were wrapped, so:
    //  - if null, console.warn was used
    //  - if === "ignore", noop was used
    //  - otherwise callback was turned into an array (not if already an array) and the actual callback was
    //    wrapped with function(response, idx) which called the callback(s) in round-robin fashion

    return fetch(url, fetchOptions)
        .then(response => {
          return response.json()
        })
        .then(json => {
          return JSON.stringify(json)
        })
        // .catch(reason => {
        //
        // })
  }

  this.register = function() {}

  this.unregister = function() {}

  this.jobs = function() {}

  /**
   * Start the poller. The interval between two polling attempts can be optionally given (in milliseconds) or
   * is taken from the parameter `fetchInterval` given at construction time. If no interval is given at all,
   * 30 seconds is the default.
   *
   * If the poller is already running (i.e. {@link #isRunning} is `true` then the scheduler
   * is restarted, but only if the new interval differs from the currently active one.
   * @param {number} interval Interval (in ms) for poll-fetching from remote Jolokia agent.
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
    clearInterval(timerId)
    timerId = null
    pollerIsRunning = false
  }

  /**
   * Check whether the poller is running.
   * @returns {boolean} true if the poller is running, false otherwise.
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
Object.defineProperty(Jolokia.prototype, "CLIENT_VERSION", {
  value: CLIENT_VERSION,
  enumerable: true,
  writable: false
})

// ++++++++++++++++++++++++++++++++++++++++++++++++++
// Public API defined in Jolokia.prototype (or Jolokia itself (static)) - no need to access "this"

/**
 * Escape URL part (segment) according to
 * {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html#_escaping_rules_in_get_requests Jolokia escaping rules}
 * @param {string} part An URL segment for Jolokia GET URL
 * @returns {string} URL segment with Jolokia escaping rules applied
 */
Jolokia.escape = Jolokia.prototype.escape = function(part) {
  return encodeURIComponent(part.replace(/!/g, "!!").replace(/\//g, "!/"))
}

// ++++++++++++++++++++++++++++++++++++++++++++++++++
// Private/internal functions that don't have access to Jolokia() scope variables
// (unless these are passed explicitly)

/**
 * Prepares a function that will be invoked in poll-fetching for configured requests. Passed parameters come
 * from the scope of {@link Jolokia} constructor function.
 * @param {Jolokia} jolokia The Jolokia object used to communicate with remote Jolokia agent
 * @param {Job[]} jobs Requests configured for poll-fetching
 * @returns {Function} A function which can be passed to {@link window#setInterval}
 */
function createJolokiaInvocation(jolokia, jobs) {
  return function() {

  }
}

/**
 * Asserts that the value is not null or undefined
 * @param {object} value A value to assert
 * @param {string} message An error message for thrown Error
 * @throws {Error} Error in case the value is null or undefined
 */
function assertNotNull(value, message) {
  if (value == null) {
    throw new Error(message)
  }
}

/**
 * Extract the HTTP-Method to use and make some sanity checks if the method was provided as part of the options,
 * but is not valid for the given request
 * @param {Request|Request[]} request JSON request object (or array of objects) compliant with
 * {@link https://jolokia.org/reference/html/manual/jolokia_protocol.html|Jolokia protocol} representing the requests
 * to send to remote Jolokia agent.
 * @param {RequestParams} params parameters used for sending the request which may override default configuration.
 * These are not options passed diretly (unchnaged) to `fetch()` call
 * @returns {string} an HTTP method for given `request` and `params`
 */
function extractMethod(request, params) {
  let methodGiven = params?.method?.toLowerCase()
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
      if (request.type?.toLowerCase() === "read" && Array.isArray(request.attribute)) {
        throw new Error("Cannot use GET for read with multiple attributes")
      }
      if (request.target) {
        throw new Error("Cannot use GET request with proxy mode")
      }
      if (request.config) {
        throw new Error("Cannot use GET with request specific config")
      }
      method = "get"
    } else if (methodGiven !== "post") {
      throw new Error("Illegal method \"" + methodGiven + "\"")
    }
  } else {
    // Determine method dynamically
    if (Array.isArray(request)
        || request.config
        || (request.type?.toLowerCase() === "read" && Array.isArray(request.attribute))
        || request.target) {
      method = "post"
    } else {
      method = "get"
    }
  }

  return method
}

/**
 * Create the URL used for a GET request
 * @param {Request|Request[]} request request object sent using `GET`, where type, mbean and additional information is encoded
 * in URL GET request
 * @returns {string} GET URL to be used with remote Jolokia agent
 */
function constructGetUrlPath(request) {
  let type = request.type
  assertNotNull(type, "No request type given for building a GET request")
  type = type.toLowerCase()

  const extractor = GET_URL_EXTRACTORS[type]
  assertNotNull(extractor, "Unknown request type " + type)

  const result = extractor(request)
  const parts = result.parts || []
  let url = type
  parts.forEach(function (v) {
    url += "/" + Jolokia.escape(v)
  })
  if (result.path) {
    url += (result.path[0] === '/' ? "" : "/") + result.path
  }
  return url
}

/**
 * Add processing parameters given as request options
 * to an URL as GET query parameters
 * @param {string} url URL for GET or POST request
 * @param {RequestParams} opts Request configuration object
 * @returns {string} url with query parameters applied
 */
function addProcessingParameters(url, opts) {
  let sep = url.indexOf("?") > 0 ? "&" : "?"
  PROCESSING_PARAMS.forEach(function(key) {
    if (opts[key] != null) {
      url += sep + key + "=" + opts[key]
      sep = "&"
    }
  })
  return url
}

/**
 * For POST requests it is recommended to have a trailing slash at the URL in order to avoid a redirect which then
 * results in a GET request.
 * @see https://bugs.eclipse.org/bugs/show_bug.cgi?id=331194#c1
 * @param {string} url asd
 * @returns {string} URL location with "/" appended
 */
function ensureTrailingSlash(url) {
  // Squeeze any URL to a single one, optionally adding one
  return url.replace(/\/*$/, "/")
}

/**
 * Extractors used for preparing a GET request, i.e. for creating a stack
 * of arguments which gets appended to create the proper access URL.
 * key: lowercase request type.
 * The return value is an object with two properties: The 'parts' to glue together, where
 * each part gets escaped and a 'path' which is appended literally
 */
const GET_URL_EXTRACTORS = {

  /**
   * Function to prepare GET URL for `read` Jolokia request
   * @param {Request} request Jolokia request object
   * @returns {object} URL configuration object for Jolokia `read` GET request
   */
  "read": function(request) {
    if (request.attribute == null) {
      // Path gets ignored for multiple attribute fetch
      return { parts: [ request.mbean, '*' ], path: request.path }
    } else {
      return { parts: [ request.mbean, request.attribute ], path: request.path }
    }
  },

  /**
   * Function to prepare GET URL for `write` Jolokia request
   * @param {Request} request Jolokia request object
   * @returns {object} URL configuration object for Jolokia `write` GET request
   */
  "write": function(request) {
    return { parts: [ request.mbean, request.attribute, valueToString(request.value) ], path: request.path }
  },

  /**
   * Function to prepare GET URL for `exec` Jolokia request
   * @param {Request} request Jolokia request object
   * @returns {object} URL configuration object for Jolokia `exec` GET request
   */
  "exec": function(request) {
    const ret = [ request.mbean, request.operation ]
    if (request.arguments && request.arguments.length > 0) {
      request.arguments.forEach(function(value) {
        ret.push(valueToString(value))
      })
    }
    return { parts: ret }
  },

  /**
   * Function to prepare GET URL for `version` Jolokia request
   * @param {Request} _request Jolokia request object
   * @returns {object} URL configuration object for Jolokia `version` GET request
   */
  // eslint-disable-next-line no-unused-vars
  "version": function(_request) {
    return {}
  },

  /**
   * Function to prepare GET URL for `search` Jolokia request
   * @param {Request} request Jolokia request object
   * @returns {object} URL configuration object for Jolokia `search` GET request
   */
  "search": function(request) {
    return { parts: [ request.mbean ] }
  },

  /**
   * Function to prepare GET URL for `list` Jolokia request
   * @param {Request} request Jolokia request object
   * @returns {object} URL configuration object for Jolokia `list` GET request
   */
  "list": function(request) {
    return { path: request.path }
  },

  /**
   * Function to prepare GET URL for `notification` Jolokia request
   * @param {Request} request Jolokia request object
   * @returns {object} URL configuration object for Jolokia `notification` GET request
   */
  "notification": function(request) {
    switch (request.command) {
      case "register":
        return { parts: [ "register" ] }
      case "add": {
        const ret = [ "add", request.client, request.mode, request.mbean ]
        const extra = []
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
        return { parts: [ "remove", request.client, request.handle ] }
      case "unregister":
        return { parts: [ "unregister", request.client ] }
      case "list":
        return { parts: [ "list", request.client ] }
      case "ping":
        return { parts: [ "ping", request.client ] }
    }
    throw new Error("Unknown command '" + request.command + "'")
  }
}

/**
 * Converts a value to a string for passing it to the Jolokia agent via
 * a `GET` request (`write`, `exec`). Value can be either a single object or an array
 * @param {string|object|object[]} value A value to write or pass to `exec` operation
 * @returns {string|object|object[]} Normalized value for Jolokia `write`/`exec` operation
 */
function valueToString(value) {
  if (value == null) {
    return "[null]"
  }
  if (Array.isArray(value)) {
    let ret = ""
    for (let i = 0; i < value.length; i++) {
      let v = value[i]
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
 * @param {string|object} value A value to normalize
 * @returns {string} normalized value
 */
function singleValueToString(value) {
  if (typeof value === "string" && value.length === 0) {
    return "\"\""
  } else {
    return value.toString()
  }
}

export default Jolokia
