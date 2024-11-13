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
  BaseRequest,
  ErrorCallback,
  ErrorCallbacks,
  ExecRequest,
  FetchErrorCallback,
  GenericCallback,
  GenericRequest,
  IJolokia,
  Job,
  JobCallback,
  JobRegistrationConfig,
  JolokiaConfiguration,
  JolokiaErrorResponse,
  JolokiaRequest,
  JolokiaResponse,
  JolokiaStatic,
  JolokiaSuccessResponse,
  ListRequest,
  NotificationAddResponseValue,
  NotificationHandle,
  NotificationMode,
  NotificationOptions,
  NotificationPullValue,
  NotificationRequest,
  ProcessingParameters,
  PullNotificationClientConfig,
  ReadRequest,
  RequestOptions,
  RequestType,
  ResponseCallback,
  ResponseCallbacks,
  SearchRequest,
  SseNotificationClientConfig,
  TextResponseCallback,
  VersionRequest,
  WriteRequest,
} from "./jolokia-types.js"

/**
 * Private type for pre-calculated {@link IJolokia#request} arguments
 */
type RequestArguments = {
  url: string
  fetchOptions: RequestInit
  dataType?: "json" | "text"
  resolve?: "default" | "response"
  successCb?: ResponseCallback | TextResponseCallback | ErrorCallback
  errorCb?: ResponseCallback | TextResponseCallback | ErrorCallback
  fetchErrorCb?: FetchErrorCallback
}

const CLIENT_VERSION = "2.1.8"

/**
 * Default parameters for GET and POST requests
 * @see https://developer.mozilla.org/en-US/docs/Web/API/fetch#options
 */
const DEFAULT_FETCH_PARAMS: RequestInit = {
  cache: "no-store",
  credentials: "same-origin",
  redirect: "error"
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
  "includeRequest",
  "ifModifiedSince",
  "listCache"
]

/**
 * Main Jolokia creation function/class.
 * For backward compatibility, it can be used both as function and as constructor.
 * @param config a string with URL of remote Jolokia agent or a configuration object
 * @returns Jolokia instance for remote Jolokia agent connection
 */
const Jolokia = function (this: IJolokia, config: JolokiaConfiguration | string): IJolokia | undefined {
  if (!new.target) {
    // when invoked as function, return properly created object with bound "this" reference
    return new (Jolokia as JolokiaStatic)(config) as IJolokia
  }

  // ++++++++++++++++++++++++++++++++++++++++++++++++++
  // Function-scoped state not exposed as properties of Jolokia class or instance

  // Registered requests for fetching periodically
  const jobs: Job[] = []

  // Our client id and notification backend config Is null as long as notifications are not used
  let client: NotificationClient

  // Options used for every request (we can override them when calling Jolokia.request())
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

  this.request = async function (request: JolokiaRequest | JolokiaRequest[], params?: RequestOptions):
    Promise<string | JolokiaSuccessResponse | JolokiaErrorResponse | (JolokiaSuccessResponse | JolokiaErrorResponse)[] | Response | undefined> {

    // calculation of arguments. jobs will have this function called once to skip it for consecutive calls
    const args = prepareRequest(request, agentOptions, params)

    // potientially time-consuming operation
    if (args.fetchOptions.method === "POST") {
      args.fetchOptions.body = JSON.stringify(request)
    }

    // In original jolokia.js at this stage there was different processing depending on the existence of
    // "success" option in passed params.
    // without such callback the request was treated as synchronus (ajaxSettings.async = false), but we can't do
    // this with Fetch API (which is good). So we simply resolve fetch' Promise to get
    // https://developer.mozilla.org/en-US/docs/Web/API/Response
    // end return new a promise resolving to string or an array of Jolokia responses (whether successful or failed ones)
    // user should handle transport errors with .catch() attached to returned promise
    //
    // if callbacks are specified (which is required for job registration, but we allow this for direct calls to),
    // we simply return a Promise resolving to Jolokia response(s)

    return performRequest(args)
  }

  this.register = function (callback: JobCallback | JobRegistrationConfig, ...requests: JolokiaRequest[]): number {
    if (!requests || requests.length == 0) {
      throw "At a least one request must be provided"
    }

    const job: Job = { requests }

    if (typeof callback === "function") {
      // treat it as a callback accepting an array of responses - intermixed successful and error responses for
      // each request
      job.callback = callback
    } else {
      // a configuration object for job registration
      if ("callback" in callback) {
        job.callback = callback.callback
      } else if ("success" in callback && "error" in callback) {
        job.success = callback.success
        job.error = callback.error
      } else {
        throw "Either 'callback' or ('success' and 'error') callback must be provided " +
        "when registering a Jolokia job"
      }
      job.config = callback.config
      job.onlyIfModified = callback.onlyIfModified
    }

    return addJob(job)
  }

  this.unregister = function (handle: number) {
    if (handle < jobs.length) {
      delete jobs[handle]
    }
  }

  this.jobs = function () {
    const ret: number[] = []
    for (let jobId = 0; jobId < jobs.length; jobId++) {
      if (jobs[jobId]) {
        ret.push(jobId)
      }
    }
    return ret
  }

  this.start = function (interval?) {
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
    timerId = setInterval(createJolokiaInvocation(jobs, agentOptions), interval)

    pollerIsRunning = true
  }

  this.stop = function () {
    if (!pollerIsRunning) {
      return
    }
    clearInterval(timerId as number)
    timerId = null
    pollerIsRunning = false
  }

  this.isRunning = function () {
    return pollerIsRunning
  }

  // ++++++++++++++++++++++++++++++++++++++++++++++++++
  // Public API - Notification handling

  this.addNotificationListener = async function (opts: NotificationOptions): Promise<NotificationHandle> {
    // Check that client is registered
    await ensureNotificationRegistration(this)

    // Notification mode. Typically "pull" or "sse"
    const mode = extractNotificationMode(client, opts)

    notificationHandlerFunc("lazy-init", mode)()

    // Send a notification-add request which returns String handle
    // See org.jolokia.service.jmx.handler.notification.NotificationListenerDelegate.addListener()
    return await this.request({
      type: "notification",
      command: "add",
      mode: mode,
      client: client.id,
      mbean: opts.mbean,
      filter: opts.filter,
      config: opts.config,
      handback: opts.handback
    }, { method: "post" }).then((responses): NotificationHandle => {
        const resp = responses as JolokiaSuccessResponse | JolokiaErrorResponse
        if (Jolokia.isError(resp)) {
          throw new Error("Cannot not add notification subscription for " + opts.mbean +
            " (client: " + client.id + "): " + resp.error)
        }
        const handle: NotificationHandle = {
          id: resp.value as NotificationAddResponseValue,
          mode: mode
        }
        notificationHandlerFunc("add", mode)(this, handle, opts)
        return handle
      })
  }

  this.removeNotificationListener = async function (handle: NotificationHandle): Promise<boolean> {
    notificationHandlerFunc("remove", handle.mode)(this, handle)
    // Unregister notification at server-side
    return await this.request({
      type: "notification",
      command: "remove",
      client: client.id,
      handle: handle.id
    }, { method: "post" }).then((responses) => {
      const resp = responses as JolokiaSuccessResponse | JolokiaErrorResponse
      return !Jolokia.isError(resp)
    })
  }

  this.unregisterNotificationClient = async function (): Promise<boolean> {
    const backends: { [ key: string ]: unknown } = client.backend || {}
    for (const mode in NOTIFICATION_HANDLERS) {
      if (NOTIFICATION_HANDLERS[mode as NotificationMode] && backends[mode]) {
        notificationHandlerFunc("unregister", mode as NotificationMode)()
      }
    }
    return await this.request({
      type: "notification",
      command: "unregister",
      client: client.id
    }, { method: "post" }).then((responses) => {
      const resp = responses as JolokiaSuccessResponse | JolokiaErrorResponse
      return !Jolokia.isError(resp)
    })
  }

  // ++++++++++++++++++++++++++++++++++++++++++++++++++
  // Private methods that need access to jolokia-scoped state (jobs, client, ...)

  /**
   * Add a job to the job queue
   * @param job a job to register
   */
  function addJob(job: Job): number {
    const id = jobs.length
    jobs[id] = job
    return id
  }

  /**
   * Check that client is registered
   * @param jolokia
   */
  async function ensureNotificationRegistration(jolokia: IJolokia): Promise<boolean> {
    if (!client) {
      return jolokia.request({
        type: "notification",
        command: "register"
      }, { method: "post" }).then(responses => {
        const resp = (responses as JolokiaSuccessResponse | JolokiaErrorResponse)
        if (Jolokia.isError(resp)) {
          throw new Error("Can not register client for notifications: "
            + resp.error
            + "\nTrace:\n" + resp.stacktrace)
        } else {
          client = resp.value as NotificationClient
        }
        return true
      })
    } else {
      return Promise.resolve(true)
    }
  }

  /**
   * Call a function from the handlers defined below, depending on the mode "this" is set to the handler object.
   * @param what notification operation
   * @param mode notification mode
   */
  function notificationHandlerFunc(what: NotificationOperation, mode: NotificationMode) {
    const notifHandler = NOTIFICATION_HANDLERS[mode]
    if (!notifHandler) {
      throw new Error("Unsupported notification mode '" + mode + "'")
    }
    return function (jolokia?: IJolokia, handle?: NotificationHandle, opts?: NotificationOptions) {
      // Set 'this' context to the notifHandler object which holds some state objects
      return notifHandler[what]?.apply(notifHandler, [ jolokia, handle, opts ])
    }
  }

  /**
   * A map of internal notification functions + state for each notification mode. The values of this map
   * are objects, so the functions need to be called with function.apply, so `this` is set to this object for
   * proper state management
   */
  const NOTIFICATION_HANDLERS: NotificationHandlersConfig = {
    "pull": {
      // "pull" handler is based on server-side MBean instance collecting notifications for all registered
      // clients. In order to get the notifications, we have to call this MBean's "pull" operation, passing
      // client ID and handle ID

      // --- operations of pull notification mechanism

      add: function (this: NotificationPullHandler, _jolokia?: IJolokia, handle?: NotificationHandle, opts?: NotificationOptions) {
        // Add a job for periodically fetching the value and calling the callback with the response
        const job: Job = {
          callback: function (...resp) {
            if (resp.length > 0 && !Jolokia.isError(resp[0])) {
              const notifs = resp[0].value as NotificationPullValue
              if (notifs && notifs.notifications && notifs.notifications.length > 0) {
                opts?.callback?.(notifs)
              }
            }
          },
          requests: [ {
            type: "exec",
            mbean: (client!.backend!["pull"] as PullNotificationClientConfig).store,
            operation: "pull",
            arguments: [ client.id, handle!.id ]
          } ]
        }
        this.jobIds[handle!.id] = addJob(job)
      },

      remove: function (this: NotificationPullHandler, jolokia?: IJolokia, handle?: NotificationHandle) {
        // Remove notification subscription from server
        const job = this.jobIds[handle!.id]
        if (job != undefined) {
          // Remove from scheduler
          jolokia!.unregister(job)
          delete this.jobIds[handle!.id]
        }
      },

      unregister: function (this: NotificationPullHandler, jolokia?: IJolokia) {
        // Remove all notification jobs from scheduler
        for (const handleId in this.jobIds) {
          if (this.jobIds[handleId] != undefined) {
            jolokia!.unregister(this.jobIds[handleId])
          }
        }
        this.jobIds = {}
      },

      // --- state of pull notification mechanism

      jobIds: {}
    },

    "sse": {
      // "sse" handler is based on https://developer.mozilla.org/en-US/docs/Web/API/EventSource
      // and continuous stream of events sent over single HTTP connection with text/event-stream MIME type
      // the messages sent are JSONified org.jolokia.server.core.service.notification.NotificationResult instances

      // --- operations of sse notification mechanism

      "lazy-init": function (this: NotificationSseHandler) {
        if (!this.eventSource) {
          this.eventSource = new EventSource(agentOptions.url + "/notification/open/" + client.id + "/sse")
          const dispatchers = this.dispatchMap
          this.eventSource.addEventListener("message", function (event) {
            const data: NotificationPullValue = JSON.parse(event.data)
            if (data.handle) {
              const callback = dispatchers[data.handle!]
              callback?.(data)
            }
          })
        }
      },

      add: function (this: NotificationSseHandler, _jolokia?: IJolokia, handle?: NotificationHandle, opts?: NotificationOptions) {
        this.dispatchMap[handle!.id] = opts!.callback
      },

      remove: function (this: NotificationSseHandler, _jolokia?: IJolokia, handle?: NotificationHandle) {
        delete this.dispatchMap[handle!.id]
      },

      unregister: function (this: NotificationSseHandler) {
        this.dispatchMap = {}
        this.eventSource?.close()
        this.eventSource = null
      },

      // --- state of sse notification mechanism

      // Map for dispatching SSE return notifications
      dispatchMap: {},

      // SSE event-source
      eventSource: null
    }
  }

}

// ++++++++++++++++++++++++++++++++++++++++++++++++++
// Public API defined in Jolokia.prototype (or Jolokia itself (static)) - no need to access "this"

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

Jolokia.escape = Jolokia.prototype.escape = function (part: string): string {
  return encodeURIComponent(part.replace(/!/g, "!!").replace(/\//g, "!/"))
}

Jolokia.escapePost = Jolokia.prototype.escape = function (part: string): string {
  return part.replace(/!/g, "!!").replace(/\//g, "!/")
}

Jolokia.isError = Jolokia.prototype.isError = function (resp: JolokiaResponse): resp is JolokiaErrorResponse {
  return resp == null || resp.status != 200
}

// ++++++++++++++++++++++++++++++++++++++++++++++++++
// Private/internal functions that don't have access to Jolokia() scope variables
// (unless these are passed explicitly)

// --- Request helper functions

/**
 * Helper function that prepares arguments used later with `fetch()` call. When using with jobs, we can prepare the
 * arguments once and call actual `fetch()` quicker. No `body` is set for POST request.
 * @param request request(s) to be sent to remote Jolokia agent
 * @param agentOptions global agent options
 * @param params parameters used for sending the request
 * @returns arguments for `fetch()` call and additional data to control the returned Promise
 */
function prepareRequest(request: JolokiaRequest | JolokiaRequest[], agentOptions: JolokiaConfiguration, params?: RequestOptions):
    RequestArguments {
  const opts: RequestOptions = Object.assign({}, agentOptions, { dataType: 'json' }, params)
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
    Object.assign(fetchOptions.headers, { "Content-Type": "text/json" })
  } else {
    fetchOptions.method = "GET"
    // request can't be an array
    url += constructGetUrlPath(request as JolokiaRequest)
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

  let successCb = undefined
  let errorCb = undefined
  let fetchErrorCb: FetchErrorCallback | undefined = undefined

  if ("success" in opts) {
    // we won't be returning anything useful (return Promise<undefined>) and the response will be
    // delivered via the callback
    successCb = constructCallbackDispatcher(opts.success)
    errorCb = constructCallbackDispatcher(opts.error)
  }
  if ("error" in opts && !opts.success) {
    // response is ignored, only error callback is used. This also turns off meaningfull Promise return value
    successCb = constructCallbackDispatcher("ignore")
    errorCb = constructCallbackDispatcher(opts.error)
  }

  // in both callback and promise mode we can have a global "fetch error handler"
  if (!("fetchError" in opts)) {
    // in promise mode however we don't provide a default handler
    if (successCb && errorCb) {
      fetchErrorCb = (_response: Response | null, reason: DOMException | TypeError | string | null) => {
        console.warn(reason)
      }
    }
  } else if (opts.fetchError === "ignore") {
    fetchErrorCb = (_response: Response | null, _reason: DOMException | TypeError | string | null) => { }
  } else {
    fetchErrorCb = opts.fetchError
  }

  return { url, fetchOptions, dataType: opts.dataType, resolve: opts.resolve, successCb, errorCb, fetchErrorCb }
}

/**
 * Actual `fetch()` call based on pre-calculated arguments
 * @param args arguments used to control `fetch()` call
 */
async function performRequest(args: RequestArguments):
  Promise<string | JolokiaSuccessResponse | JolokiaErrorResponse | (JolokiaSuccessResponse | JolokiaErrorResponse)[] | Response | undefined> {
  const { url, fetchOptions, dataType, resolve, successCb, errorCb, fetchErrorCb } = args

  if (successCb && errorCb) {
    // callback mode
    // we'll handle the promise and caller will get a promise resolving to `undefined` after
    // the callbacks are notified.
    // (even if user didn't pass `error` callback, it was configured by `prepareRequest()`)
    // user doesn't have to attach any .then() or .catch() like in promise mode and the returned promise
    // can be ignored
    return fetch(url, fetchOptions)
      .then(async (response: Response): Promise<undefined> => {
        if (response.status != 200) {
          // Jolokia sends its errors with HTTP 200, so any other HTTP code (even redirect - 30x) is actually an error.
          // with xhr and JQuery we were using ajaxError param, here we use fetchError callback
          fetchErrorCb?.(response, response.statusText)
          return undefined
        }
        const ct = response.headers.get("content-type")
        if (dataType === "text" || !ct || !(ct.startsWith("text/json") || ct.startsWith("application/json"))) {
          // text response - no parsing, single call
          const textResponse = await response.text()
          void (successCb as TextResponseCallback)(textResponse)
        } else {
          // JSON response
          const jsonResponse = await response.json()
          const responses: (JolokiaSuccessResponse | JolokiaErrorResponse)[] = Array.isArray(jsonResponse) ? jsonResponse : [ jsonResponse ]
          for (let n = 0; n < responses.length; n++) {
            const resp = responses[n]
            if (Jolokia.isError(resp)) {
              (errorCb as ErrorCallback)(resp, n)
            } else {
              (successCb as ResponseCallback)(resp, n)
            }
          }
        }
      })
      .catch(reason => {
        // this is a fetch() error (more serious than HTTP != 200) - we have no `Response` object to pass, just
        // an exception/reason
        fetchErrorCb?.(null, reason)
        return undefined
      })
  } else {
    // promise mode
    // return a promise to be handled by the caller - whatever the caller wants
    if (resolve === "response") {
      // low level Response handling - entirely at caller's side
      return fetch(url, fetchOptions)
    } else {
      // Jolokia response handling at caller's side (no access to response headers, status, etc. for HTTP 200)
      const promise = fetch(url, fetchOptions)
        .then(async (response: Response): Promise<string | JolokiaSuccessResponse | JolokiaErrorResponse | (JolokiaSuccessResponse | JolokiaErrorResponse)[] | Response> => {
          if (response.status != 200) {
            // all non-200 responses are thrown as exception to be handled in .catch()
            throw response
          }
          const ct = response.headers.get("content-type")
          if (dataType === "text" || !ct || !(ct.startsWith("text/json") || ct.startsWith("application/json"))) {
            return await response.text()
          }
          return await response.json()
        })
      if (fetchErrorCb) {
        // we handle serious fetch() exception for user's convenience
        return promise.catch(error => {
          if (error instanceof Response) {
            fetchErrorCb(error, null)
          } else {
            fetchErrorCb(null, error)
          }
          return undefined
        })
      } else {
        return promise
      }
    }
  }
}

/**
 * Prepare a ready to use callback to handle any kind (text, json, error) of response from Jolokia
 * @param cb a callback definition
 */
function constructCallbackDispatcher(cb?: "ignore" | ResponseCallback | TextResponseCallback | ResponseCallbacks | ErrorCallback | ErrorCallbacks):
  ResponseCallback | TextResponseCallback | ErrorCallback {
  if (!cb || (Array.isArray(cb) && cb.length == 0)) {
    return (response: string | JolokiaSuccessResponse | JolokiaErrorResponse, index: number): void => {
      let r
      if (typeof response === "string") {
        r = response
      } else {
        r = JSON.stringify(response)
      }
      if (r && r.length > 256) {
        r = r.substring(0, 253) + "..."
      }
      console.warn("Ignore response", "\"" + r + "\"", "index=", index)
    }
  }
  if (cb === "ignore") {
    // Ignore the return value entirely
    return () => {
    }
  }

  // turn a callback into round-robin array of callbacks called by response index
  const cbArray: (TextResponseCallback | ResponseCallback | ErrorCallback)[] = Array.isArray(cb) ? cb : [ cb ]
  return (response: string | JolokiaSuccessResponse | JolokiaErrorResponse, index: number): void => {
    if (typeof response === "string") {
      (cbArray[0] as TextResponseCallback)(response)
    } else {
      (cbArray[index % cbArray.length] as GenericCallback)(response, index)
    }
  }
}

// --- Job helper functions

/**
 * Prepares a function that will be invoked in poll-fetching for configured jobs and their requests. All jobs and
 * all requests for each job are handled by single, periodical {@link IJolokia#request} call.
 * Passed parameters come from the scope of {@link IJolokia} constructor function.
 * @param jobs Requests configured for poll-fetching
 * @param agentOptions global Jolokia configuration
 * @returns A function which can be passed to {@link window#setInterval}
 */
function createJolokiaInvocation(jobs: Job[], agentOptions: JolokiaConfiguration) {
  // the function called in window.setInterval needs a fresh state on each call (to operate only on current
  // set of responses for all the requests of each job, but we may prepare some configuration at this stage

  // we don't need actual request data to prepare the request as long as the method is given
  const requestArguments = prepareRequest([], agentOptions, { method: "post" })

  return function () {
    // while we can use single callback for single array of requests, when flattening request arrays of all
    // the jobs, we need matching arrays of callbacks to cover responses for single job
    const successCbs: ResponseCallback[] = []
    const errorCbs: ErrorCallback[] = []
    // array of all the requests for all the jobs - fresh copy for single invocation of window.setInterval's function
    const requests: JolokiaRequest[] = []

    // check all the jobs
    for (let jobId = 0; jobId < jobs.length; jobId++) {
      const job = jobs[jobId]
      if (job) {
        // and we'll prepare callbacks for all the requests within the job
        const reqsLen = job.requests.length
        if (job.success) {
          // we have separate job.success and job.error callbacks accepting the response (ok or error),
          // job ID and index of response within an array of requests for single Job
          const successCb = constructSuccessJobCallback(job, jobId)
          const errorCb = constructErrorJobCallback(job, jobId)
          for (let idx = 0; idx < reqsLen; idx++) {
            requests.push(prepareJobRequest(job, idx))
            successCbs.push(successCb)
            errorCbs.push(errorCb)
          }
        } else if (job.callback) {
          // single job.callback accepting an array of mixed successful and error Joloka responses
          // that's why, when a job has such a callback for N requests, N-1 requests should be handled simply
          // by collecting responses (or errors)  and N-th request should be handled by passing the collected
          // array to this callback
          const callbackConfiguration = constructJobCallbackConfiguration(job, jobId)
          // N-1 requests:
          for (let idx = 0; idx < reqsLen - 1; idx++) {
            requests.push(prepareJobRequest(job, idx))
            successCbs.push(callbackConfiguration.cb as ResponseCallback)
            errorCbs.push(callbackConfiguration.cb as ErrorCallback)
          }
          // last request
          requests.push(prepareJobRequest(job, reqsLen - 1))
          successCbs.push(callbackConfiguration.lcb as ResponseCallback)
          errorCbs.push(callbackConfiguration.lcb as ErrorCallback)
        }
      }
    }

    if (requests.length > 0) {
      // POST body is created on each request
      requestArguments.fetchOptions.body = JSON.stringify(requests)

      // callbacks are configured on each request
      requestArguments.successCb = function (response: JolokiaSuccessResponse, index: number) {
        successCbs[index](response, index)
      }
      requestArguments.errorCb = function (response: JolokiaErrorResponse, index: number) {
        errorCbs[index](response, index)
      }
      requestArguments.fetchErrorCb = function (response: Response | null, error: DOMException | TypeError | string | null) {
        if (response) {
          console.error("Problem executing registered jobs: ", response.status, response.statusText)
        } else {
          console.error("Problem executing registered jobs: ", error)
        }
      }

      // we can now call remote Jolokia agent
      performRequest(requestArguments)
    }
  }
}

/**
 * Prepare a callback for handling successful responses for registered jobs
 * @param job a Job with `success` and `error` callbacks configured
 * @param jobId job identifier to be passed to {@link JobResponseCallback}
 * @returns a normal {@link ResponseCallback} to be passed to {@link IJolokia#request}, but which is job-aware
 */
function constructSuccessJobCallback(job: Job, jobId: number): ResponseCallback {
  if (!job.success) {
    throw "Expected 'success' callback configured for the job with ID=" + jobId
  }
  return function (response: JolokiaSuccessResponse, index: number) {
    // Remember last success callback
    if (job.onlyIfModified) {
      job.lastModified = response.timestamp
    }
    job.success!(response, jobId, index)
  }
}

/**
 * Prepare a callback for handling error responses for registered jobs
 * @param job a Job with `success` and `error` callbacks configured
 * @param jobId job identifier to be passed to {@link JobErrorCallback}
 * @returns a normal {@link ErrorCallback} to be passed to {@link IJolokia#request}, but which is job-aware
 */
function constructErrorJobCallback(job: Job, jobId: number): ErrorCallback {
  if (!job.error) {
    throw "Expected 'error' callback configured for the job with ID=" + jobId
  }
  return function (response: JolokiaErrorResponse, index: number) {
    if (response.status === 304) {
      // If we get a "304 - Not Modified" 'error', we do nothing
      return
    } else {
      job.error!(response, jobId, index)
    }
  }
}

/**
 * Prepare a pair of callbacks for job callback that should receive all the responses. Callback for N-1
 * responses simply collect the responses, and callback for N-th responses collects the responsss and passes
 * all them collectively to {@link JobCallback}.
 * @param job a Job with `callback` configured
 * @param jobId job identifier
 * @returns a pair of callbacks to be used for: `cb`. N-1 responses, `lcb`. N-th (last) response
 */
function constructJobCallbackConfiguration(job: Job, jobId: number):
    { cb: ResponseCallback | ErrorCallback, lcb: ResponseCallback | ErrorCallback } {
  if (!job.callback) {
    throw "Expected 'callback' configured for the job with ID=" + jobId
  }
  const allResponses: (JolokiaSuccessResponse | JolokiaErrorResponse)[] = []
  let lastModified: number = 0
  return {
    cb: addResponse,
    lcb: function (response: JolokiaSuccessResponse | JolokiaErrorResponse, index: number) {
      addResponse(response, index)
      // Callback is called only if at least one non-cached response
      // is obtained. Update job's timestamp internally
      if (allResponses.length > 0) {
        job.lastModified = lastModified
        job.callback!(...allResponses)
      }
    }
  }

  function addResponse(response: JolokiaSuccessResponse | JolokiaErrorResponse, _index: number) {
    // Only remember responses with values and remember lowest timestamp, too.
    if (response.status !== 304) {
      if (lastModified === 0 || (response.timestamp && response.timestamp < lastModified)) {
        if (response.timestamp) {
          lastModified = response.timestamp
        }
      }
      allResponses.push(response)
    }
  }
}

/**
 * Add special configuration for single request of a single job based on current job's state (like lastModified)
 * @param job
 * @param requestIndex
 */
function prepareJobRequest(job: Job, requestIndex: number): JolokiaRequest {
  const request = job.requests[requestIndex]
  const config = job.config || {}
  const extraConfig: ProcessingParameters
    = job.onlyIfModified && job.lastModified ? { ifModifiedSince: job.lastModified } : {}

  request.config = Object.assign({}, config, request.config, extraConfig)

  return request
}

// --- Util functions

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
function extractMethod(request: BaseRequest | BaseRequest[], params: RequestOptions) {
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
    method = "post"
  } else {
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
 * For POST requests it is recommended to have a trailing slash at the URL in order to avoid a redirect which then
 * results in a GET request.
 * @see https://bugs.eclipse.org/bugs/show_bug.cgi?id=331194#c1
 * @param url Jolokia agent URL
 * @returns URL location with "/" appended
 */
function ensureTrailingSlash(url: string | URL): string {
  // Squeeze any URL to a single one, optionally adding one
  const v = typeof url === "string" ? url : url.href
  let trimEnd = v.length
  while (v[trimEnd - 1] === '/') {
    trimEnd--
  }
  return v.substring(0, trimEnd) + '/'
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
  PROCESSING_PARAMS.forEach(function (key) {
    const v = opts[key as keyof RequestOptions]
    if (v != null) {
      url += sep + key + "=" + v
      sep = "&"
    }
  })
  return url
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

// --- Functions and helpers for GET path construction

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

  const extractor: (r: GenericRequest) => GetPathConfiguration = GET_URL_EXTRACTORS[type]
  assertNotNull(extractor, "Unknown request type " + type)

  const result = extractor(request as GenericRequest)
  const parts = result.parts || []
  let url = type
  parts.forEach(function (v: string) {
    url += "/" + Jolokia.escape(v)
  })
  if (result.path) {
    url += (result.path[0] === '/' ? "" : "/") + result.path
  }
  return url
}

/**
 * A type to configure GET path for specified {@link JolokiaRequest}
 */
type GetPathConfiguration = {
  /** Initial segments of GET URI path */
  parts?: string[]
  /** Last segment of GET URI path */
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
const GET_URL_EXTRACTORS: { [key in RequestType]: (r: GenericRequest) => GetPathConfiguration } = {

  /**
   * Function to prepare GET URL for `read` Jolokia request
   * @param request Jolokia request object
   * @returns URL configuration object for Jolokia `read` GET request
   */
  "read": function (request: ReadRequest) {
    const path = Array.isArray(request.path) ? request.path.map(Jolokia.escape).join("/") : request.path
    if (request.attribute == null) {
      // Path gets ignored for multiple attribute fetch
      return { parts: [ request.mbean, "*" ], path }
    } else {
      // can't use attribute array with GET
      return { parts: [ request.mbean, request.attribute as string ], path }
    }
  },

  /**
   * Function to prepare GET URL for `write` Jolokia request
   * @param request Jolokia request object
   * @returns URL configuration object for Jolokia `write` GET request
   */
  "write": function (request: WriteRequest) {
    const path = Array.isArray(request.path) ? request.path.map(Jolokia.escape).join("/") : request.path
    return { parts: [ request.mbean, request.attribute as string, valueToString(request.value) ], path }
  },

  /**
   * Function to prepare GET URL for `exec` Jolokia request
   * @param request Jolokia request object
   * @returns URL configuration object for Jolokia `exec` GET request
   */
  "exec": function (request: ExecRequest) {
    const ret = [ request.mbean, request.operation ]
    if (request.arguments && request.arguments.length > 0) {
      request.arguments.forEach(function (value: unknown) {
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
  "version": function (_request: VersionRequest) {
    return {}
  },

  /**
   * Function to prepare GET URL for `search` Jolokia request
   * @param request Jolokia request object
   * @returns URL configuration object for Jolokia `search` GET request
   */
  "search": function (request: SearchRequest) {
    return { parts: [ request.mbean ] }
  },

  /**
   * Function to prepare GET URL for `list` Jolokia request
   * @param request Jolokia request object
   * @returns URL configuration object for Jolokia `list` GET request
   */
  "list": function (request: ListRequest) {
    return { path: request.path }
  },

  /**
   * Function to prepare GET URL for `notification` Jolokia request
   * @param request Jolokia request object
   * @returns URL configuration object for Jolokia `notification` GET request
   */
  "notification": function (request: NotificationRequest) {
    switch (request.command) {
      case "register":
        return { parts: [ "register" ] }
      case "add": {
        const ret = [ "add", valueToString(request.client), valueToString(request.mode), valueToString(request.mbean) ]
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

// --- Functions and helpers for notification handling

/**
 * Internal representation of notification client
 */
type NotificationClient = {
  /** ID (UUID) of the client registered at server side where MBean notification listeners are being registered */
  id: string
  /** Backends supported for the client with their configuration */
  backend?: { [ key in NotificationMode ]: PullNotificationClientConfig | SseNotificationClientConfig }
}

/**
 * Identifiers of internal notification-related functions invoked by Notification API
 */
type NotificationOperation = "add" | "remove" | "unregister" | "lazy-init"

/**
 * An internal function invoked from notification API
 */
type NotificationFunction = (jolokia?: IJolokia, handle?: NotificationHandle, opts?: NotificationOptions) => void

type NotificationModeOperations = { [ key in NotificationOperation ]?: NotificationFunction }
type NotificationModeHandler = NotificationModeOperations & { [ key: string ]: unknown }
type NotificationHandlersConfig = { [ key in NotificationMode ]: NotificationModeHandler }
type NotificationPullHandler = NotificationModeOperations & {
  jobIds: { [ key: string ]: number }
}
type NotificationSseHandler = NotificationModeOperations & {
  dispatchMap: { [ key: string ]: ((result: NotificationPullValue) => void) | undefined }
  eventSource: EventSource | null
}

/**
 * Get notification mode with a sane default based on which is provided by the backend
 */
function extractNotificationMode(client: NotificationClient, opts: NotificationOptions): NotificationMode {
  const backends: { [ key: string ]: unknown } = client.backend || {}
  // A mode given takes precedence
  let mode = opts.mode
  if (!mode) {
    // Try 'sse' first as default then 'pull'.
    mode = backends["sse"] ? "sse" : (backends["pull"] ? "pull" : undefined)
    // If only one backend is configured, that's the default
    if (!mode && backends.length === 1) {
      return Object.keys(backends)[0] as NotificationMode
    }
  }
  if (!mode || !backends[mode]) {
    throw new Error("Notification mode must be one of [" + Object.keys(backends) + "]"
      + (mode ? ", and not \"" + mode + "\"" : ""))
  }

  return mode
}

export * from "./jolokia-types.js"
export default Jolokia as JolokiaStatic
