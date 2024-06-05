/*
 * Copyright 2009-2012 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* =================================
 * Jolokia JavaScript Client Library
 * =================================
 *
 * Requires jquery.js.
 */
"use strict";

// Uses Node, AMD or browser globals to create a module.
(function (root, factory) {
    if (typeof define === "function" && define.amd) {
        // AMD. Register as an anonymous module.
        define(["jquery"], factory);
    } else if (typeof module === "object" && module.exports) {
        // Node. Does not work with strict CommonJS, but
        // only CommonJS-like environments that support module.exports,
        // like Node.
        var jquery = require("jquery");
        // To get along with jest-environment-jsdom
        if (typeof jquery.fn !== "undefined") {
            module.exports = factory(jquery);
        } else {
            var jsdom = require("jsdom");
            var dom = new jsdom.JSDOM("");
            module.exports = factory(jquery(dom.window));
        }
    } else {
        // Browser globals
        root.Jolokia = factory(root.jQuery);
    }
}(typeof self !== "undefined" ? self : this, function (jQuery) {

    var _jolokiaConstructorFunc = function ($) {

        // Default parameters for GET and POST requests
        var DEFAULT_CLIENT_PARAMS = {
            type:"POST",
            jsonp:false
        };

        var GET_AJAX_PARAMS = {
            type:"GET"
        };

        var POST_AJAX_PARAMS = {
            type:"POST",
            processData:false,
            contentType:"text/json"
        };

        // Processing parameters which are added to the
        // URL as query parameters if given as options
        var PROCESSING_PARAMS = ["maxDepth", "maxCollectionSize", "maxObjects", "ignoreErrors", "canonicalNaming",
                                 "serializeException", "includeStackTrace", "ifModifiedSince"];

        /**
         * Constructor for creating a client to the Jolokia agent.
         *
         * An object containing the default parameters can be provided as argument. For the possible parameters
         * see {@link #request()}.
         *
         * @param param either a string in which case it is used as the URL to the agent or
         *              an object with the default parameters as key-value pairs
         */
        function Jolokia(param) {
            // If called without 'new', we are constructing an object
            // nevertheless
            if (typeof this === "undefined") {
                return new Jolokia(param);
            }

            // Jolokia JavaScript Client version
            this.CLIENT_VERSION = "2.0.1";

            // Registered requests for fetching periodically
            var jobs = [];

            // Our client id and notification backend config
            // Is null as long as notifications are not used
            var client = null;

            // Options used for every request
            var agentOptions = {};

            // State of the scheduler
            var pollerIsRunning = false;

            // Seal this in a closure so that it can be referenced from unnamed functions easily
            var jolokia = this;

            // Allow a single URL parameter as well
            if (typeof param === "string") {
                param = {url:param};
            }
            Jolokia.assignObject(agentOptions, DEFAULT_CLIENT_PARAMS, param);

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
             *   <dt>dataType</dt>
             *   <dd>
             *     The type of data specified to the Ajax request. The default value is "json",
             *     and the response is parsed as JSON to an object. If the value is "text",
             *     the response is returned as plain text without parsing. The client is then
             *     responsible for parsing the response. This can be useful when a custom JSON
             *     parsing is necessary.
             *     Jolokia Simple API (jolokia-simple.js) doesn't support "text" as dataType.
             *     This option is available since jolokia.js 2.0.2.
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
             *     to the JavaScript console by default.
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
            jolokia.request = function (request, params) {
                var opts = mergeInDefaults(params);
                assertNotNull(opts.url, "No URL given");

                var ajaxParams = {};

                // Copy over direct params for the jQuery ajax call
                ["dataType", "username", "password", "timeout"].forEach(function (key) {
                    if (opts[key]) {
                        ajaxParams[key] = opts[key];
                    }
                });

                if (ajaxParams['username'] && ajaxParams['password']) {
                    // If we have btoa() then we set the authentication preemptively,

                    // Otherwise (e.g. for IE < 10) an extra roundtrip might be necessary
                    // when using 'username' and 'password' in xhr.open(..)
                    // See http://stackoverflow.com/questions/5507234/how-to-use-basic-auth-and-jquery-and-ajax
                    // for details
                    if (window.btoa) {
                        ajaxParams.beforeSend = function (xhr) {
                            var tok = ajaxParams['username'] + ':' + ajaxParams['password'];
                            xhr.setRequestHeader('Authorization', "Basic " + window.btoa(tok));
                        };
                    }

                    // Add appropriate field for CORS access
                    ajaxParams.xhrFields = {
                        // Please note that for CORS access with credentials, the request
                        // must be asynchronous (see https://dvcs.w3.org/hg/xhr/raw-file/tip/Overview.html#the-withcredentials-attribute)
                        // It works synchronously in Chrome nevertheless, but fails in Firefox.
                        withCredentials: true
                    };
                }

                if (extractMethod(request, opts) === "post") {
                    Jolokia.assignObject(ajaxParams, POST_AJAX_PARAMS);
                    if (!ajaxParams.dataType) {
                        ajaxParams.dataType = "json";
                    }
                    ajaxParams.data = JSON.stringify(request);
                    ajaxParams.url = ensureTrailingSlash(opts.url);
                } else {
                    Jolokia.assignObject(ajaxParams, GET_AJAX_PARAMS);
                    if (opts.jsonp) {
                        ajaxParams.dataType = "jsonp";
                    }
                    if (!ajaxParams.dataType) {
                        ajaxParams.dataType = "json";
                    }
                    ajaxParams.url = opts.url + "/" + constructGetUrlPath(request);
                }

                // Add processing parameters as query parameters
                ajaxParams.url = addProcessingParameters(ajaxParams.url, opts);

                // Global error handler
                if (opts.ajaxError) {
                    ajaxParams.error = opts.ajaxError;
                }

                // Dispatch Callbacks to error and success handlers
                if (opts.success) {
                    var success_callback = constructCallbackDispatcher(opts.success);
                    var error_callback = constructCallbackDispatcher(opts.error);
                    ajaxParams.success = function (data) {
                        if (ajaxParams.dataType === "text") {
                            success_callback(data, 0);
                        } else {
                            var responses = Array.isArray(data) ? data : [data];
                            for (var idx = 0; idx < responses.length; idx++) {
                                var resp = responses[idx];
                                if (Jolokia.isError(resp)) {
                                    error_callback(resp, idx);
                                } else {
                                    success_callback(resp, idx);
                                }
                            }
                        }
                    };

                    // Perform the request
                    $.ajax(ajaxParams);
                    return null;
                } else {
                    // Synchronous operation requested (i.e. no callbacks provided)
                    if (opts.jsonp) {
                        throw Error("JSONP is not supported for synchronous requests");
                    }
                    ajaxParams.async = false;
                    var xhr = $.ajax(ajaxParams);
                    if (httpSuccess(xhr)) {
                        return ajaxParams.dataType === "text" ? xhr.responseText : JSON.parse(xhr.responseText);
                    } else {
                        return null;
                    }
                }
            };

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
            jolokia.register = function() {
                if (arguments.length < 2) {
                    throw "At a least one request must be provided";
                }
                var callback = arguments[0],
                    requests = Array.prototype.slice.call(arguments,1),
                    job;
                if (typeof callback === 'object') {
                    if (callback.success && callback.error) {
                        job = {
                            success: callback.success,
                            error: callback.error
                        };
                    } else if (callback.callback) {
                        job = {
                            callback: callback.callback
                        };
                    } else {
                        throw "Either 'callback' or ('success' and 'error') callback must be provided " +
                              "when registering a Jolokia job";
                    }
                    job = Jolokia.assignObject(job,{
                        config: callback.config,
                        onlyIfModified: callback.onlyIfModified
                    });
                } else if (typeof callback === 'function') {
                    // Simplest version without config possibility
                    job = {
                        success: null,
                        error: null,
                        callback: callback
                    };
                } else {
                    throw "First argument must be either a callback func " +
                          "or an object with 'success' and 'error' attributes";
                }
                if (!requests) {
                    throw "No requests given";
                }
                job.requests = requests;
                return addJob(job);
            };


            /**
             * Unregister one or more request which has been registered with {@link #register}. As parameter
             * the handle returned during the registration process must be given
             * @param handle the job handle to unregister
             */
            jolokia.unregister = function(handle) {
                if (handle < jobs.length) {
                    delete jobs[handle];
                }
            };


            /**
             * Return an array of jobIds for currently registered jobs.
             * @return Array of job jobIds or an empty array
             */
            jolokia.jobs = function() {
                var ret = [],
                    len = jobs.length;
                for (var i = 0; i < len; i++) {
                    if (jobs[i]) {
                        ret.push(i);
                    }
                }
                return ret;
            };

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
            jolokia.start = function(interval) {
                interval = interval || agentOptions.fetchInterval || 30000;
                if (pollerIsRunning) {
                    if (interval === agentOptions.fetchInterval) {
                        // Nothing to do
                        return;
                    }
                    // Re-start with new interval
                    jolokia.stop();
                }
                agentOptions.fetchInterval = interval;
                jolokia.timerId = setInterval(callJolokia(jolokia,jobs), interval);

                pollerIsRunning = true;
            };

            /**
             * Stop the poller. If the poller is not running, no operation is performed.
             */
            jolokia.stop = function() {
                if (!pollerIsRunning && jolokia.timerId != undefined) {
                    return;
                }
                clearInterval(jolokia.timerId);
                jolokia.timerId = null;

                pollerIsRunning = false;
            };

            /**
             * Check whether the poller is running.
             * @return true if the poller is running, false otherwise.
             */
            jolokia.isRunning = function() {
                return pollerIsRunning;
            };

            // ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
            // Notification handling

            jolokia.addNotificationListener = function(opts) {
                // Check that client is registered
                ensureNotificationRegistration();

                // Notification mode. Typically "pull" or "sse"
                var mode = extractNotificationMode(opts);

                notificationHandlerFunc("lazy-init",mode)();

                // Add a notification request
                var resp = jolokia.request({
                    type: "notification",
                    command: "add",
                    mode: mode,
                    client: client.id,
                    mbean: opts.mbean,
                    filter: opts.filter,
                    config: opts.config,
                    handback: opts.handback
                });
                if (Jolokia.isError(resp)) {
                    throw new Error("Cannot not add notification subscription for " + opts.mbean +
                                    " (client: " + client.id + "): " + resp.error);
                }
                var handle = { id: resp.value, mode: mode };
                notificationHandlerFunc("add",mode)(handle, opts);
                return handle;
            };

            jolokia.removeNotificationListener = function(handle) {
                notificationHandlerFunc("remove",handle.mode)(handle);
                // Unregister notification
                jolokia.request({
                    type:    "notification",
                    command: "remove",
                    client:  client.id,
                    handle:  handle.id
                });
            };

            jolokia.unregisterNotificationClient = function() {
                var backends = client.backend || {};
                for (mode in NOTIFICATION_HANDLERS) {
                    if (NOTIFICATION_HANDLERS.hasOwnProperty(mode) && backends[mode]) {
                        notificationHandlerFunc("unregister")()
                    }
                }
                jolokia.request({
                    type:    "notification",
                    command: "unregister",
                    client: client.id
                });
            };

            // ===================================================================
            // Private methods

            // Merge a set of parameters with the defaults values
            function mergeInDefaults(params) {
                return Jolokia.assignObject({}, agentOptions, params);
            }

            // Add a job to the job queue
            function addJob(job) {
                var idx = jobs.length;
                jobs[idx] = job;
                return idx;
            }

            // Check that this agent is registered as a notification client.
            // If not, do a register call
            function ensureNotificationRegistration() {
                if (!client) {
                    var resp = jolokia.request({
                        type:    "notification",
                        command: "register"
                    });
                    if (Jolokia.isError(resp)) {
                        throw new Error("Can not register client for notifications: " + resp.error +
                                        "\nTrace:\n" + resp.stacktrace);
                    } else {
                        client = resp.value;
                    }
                }
            }

            // Get notification mode with a sane default based on which is provided
            // by the backend
            function extractNotificationMode(opts) {
                var backends = client.backend || {};
                // A mode given takes precedence
                var mode = opts.mode;
                if (!mode) {
                    // Try 'sse' first as default then 'pull'.
                    mode = backends["sse"] ? "sse" : (backends["pull"] ? "pull" : undefined);
                    // If only one backend is configured, that's the default
                    if (!mode && backends.length == 1) {
                        return backends[0];
                    }
                }
                if (!mode || !backends[mode]) {
                    throw new Error("Notification mode must be one of " + Object.keys(backends) + (mode ? " and not " + mode : ""));
                }
                return mode
            }

            // Call a function from the handlers defined below, depending on the mode
            // "this" is set to the handler object.
            function notificationHandlerFunc(what, mode) {
                var notifHandler = NOTIFICATION_HANDLERS[mode];
                if (!notifHandler) {
                    throw new Error("Unsupported notification mode '" + mode + "'");
                }
                return function() {
                    // Fix 'this' context to the notifHandler object which holds some state objects
                    return notifHandler[what].apply(notifHandler,Array.prototype.slice.call(arguments));
                }
            }

            // ===== Notification handler state vars and functions ...
            // Notification handler definition for various notification modes
            var NOTIFICATION_HANDLERS = {
                // Pull mode for notifications
                pull : {
                    add: function(handle, opts) {
                        // Add a job for periodically fetching the value and calling the callback with the response
                        var job = {
                            callback: function (resp) {
                                if (!Jolokia.isError(resp)) {
                                    var notifs = resp.value;
                                    if (notifs && notifs.notifications && notifs.notifications.length > 0) {
                                        opts.callback(notifs);
                                    }
                                }
                            },
                            requests: [{
                                type:      "exec",
                                mbean:     client.backend.pull.store,
                                operation: "pull",
                                arguments: [client.id, handle.id]
                            }]
                        };
                        this.jobIds[handle.id] = addJob(job);
                    },
                    remove: function(handle) {
                        // Remove notification subscription from server
                        var job = this.jobIds[handle.id];
                        if (job) {
                            // Remove from scheduler
                            jolokia.unregister(job);
                            delete this.jobIds[handle.id];
                        }
                    },
                    unregister: function() {
                        // Remove all notification jobs from scheduler
                        for (var handleId in this.jobIds) {
                            if (this.jobIds.hasOwnProperty(handleId)) {
                                var jobId = this.jobIds[handleId];
                                jolokia.unregister(jobId);
                            }
                        }
                        this.jobIds = {}
                    },
                    jobIds: {}
                },

                // Server sent event mode
                sse : {
                    "lazy-init": function() {
                        if (!this.eventSource) {
                            this.eventSource = new EventSource(agentOptions.url + "/notification/open/" + client.id + "/sse");
                            var dispatcher = this.dispatchMap;
                            this.eventSource.addEventListener("message", function (event) {
                                var data = JSON.parse(event.data);
                                var callback = dispatcher[data.handle];
                                if (callback != null) {
                                    callback(data);
                                }
                            });
                        }
                    },
                    add: function(handle, opts) {
                        this.dispatchMap[handle.id] = opts.callback;
                    },
                    remove: function(handle) {
                        delete this.dispatchMap[handle.id];
                    },
                    unregister: function() {
                        this.dispatchMap = {};
                        this.eventSource = null;
                    },

                    // Map for dispatching SSE return notifications
                    dispatchMap : {},

                    // SSE event-source
                    eventSource : null
                }
            };
        }

        // ========================================================================
        // Private Functions:

        // Create a function called by a timer, which requests the registered requests
        // calling the stored callback on receipt. jolokia and jobs are put into the closure
        function callJolokia(jolokia,jobs) {
            return function() {
                var errorCbs = [],
                    successCbs = [],
                    i, j;
                var requests = [];
                for (i in jobs) {
                    if (!jobs.hasOwnProperty(i)) {
                        continue;
                    }
                    var job = jobs[i];
                    var reqsLen = job.requests.length;
                    if (job.success) {
                        // Success/error pair of callbacks. For multiple request,
                        // these callback will be called multiple times
                        var successCb = cbSuccessClosure(job,i);
                        var errorCb = cbErrorClosure(job,i);
                        for (j = 0; j < reqsLen; j++) {
                            requests.push(prepareRequest(job,j));
                            successCbs.push(successCb);
                            errorCbs.push(errorCb);
                        }
                    } else {
                        // Job should have a single callback (job.callback) which will be
                        // called once with all responses at once as an array
                        var callback = cbCallbackClosure(job,jolokia);
                        // Add callbacks which collect the responses
                        for (j = 0; j < reqsLen - 1; j++) {
                            requests.push(prepareRequest(job,j));
                            successCbs.push(callback.cb);
                            errorCbs.push(callback.cb);
                        }
                        // Add final callback which finally will call the job.callback with all
                        // collected responses.
                        requests.push(prepareRequest(job,reqsLen-1));
                        successCbs.push(callback.lcb);
                        errorCbs.push(callback.lcb);
                    }
                }
                var opts = {
                    // Dispatch to the build up callbacks, request by request
                    success: function(resp, j) {
                        return successCbs[j].apply(jolokia, [resp, j]);
                    },
                    error: function(resp, j) {
                        return errorCbs[j].apply(jolokia, [resp, j]);
                    }
                };
                return jolokia.request(requests, opts);
            };
        }

        // Prepare a request with the proper configuration
        function prepareRequest(job,idx) {
            var request = job.requests[idx],
                config = job.config || {},
                // Add the proper ifModifiedSince parameter if already called at least once
                extra = job.onlyIfModified && job.lastModified ? { ifModifiedSince: job.lastModified } : {};

            request.config = Jolokia.assignObject({}, config, request.config, extra);
            return request;
        }

        // Closure for a full callback which stores the responses in an (closed) array
        // which the finally is feed in to the callback as array
        function cbCallbackClosure(job,jolokia) {
            var responses = [],
                callback = job.callback,
                lastModified = 0;

            return {
                cb : addResponse,
                lcb : function(resp,j) {
                    addResponse(resp);
                    // Callback is called only if at least one non-cached response
                    // is obtained. Update job's timestamp internally
                    if (responses.length > 0) {
                        job.lastModified = lastModified;
                        callback.apply(jolokia,responses);
                    }
                }
            };

            function addResponse(resp,j) {
                // Only remember responses with values and remember lowest timestamp, too.
                if (resp.status != 304) {
                    if (lastModified == 0 || resp.timestamp < lastModified ) {
                        lastModified = resp.timestamp;
                    }
                    responses.push(resp);
                }
            }
        }

        // Own function for creating a closure to avoid reference to mutable state in the loop
        function cbErrorClosure(job, i) {
            var callback = job.error;
            return function(resp,j) {
                // If we get a "304 - Not Modified" 'error', we do nothing
                if (resp.status == 304) {
                    return;
                }
                if (callback) {
                    callback(resp,i,j)
                }
            }
        }

        function cbSuccessClosure(job, i) {
            var callback = job.success;
            return function(resp,j) {
                if (callback) {
                    // Remember last success callback
                    if (job.onlyIfModified) {
                        job.lastModified = resp.timestamp;
                    }
                    callback(resp,i,j)
                }
            }
        }

        // Construct a callback dispatcher for appropriately dispatching
        // to a single callback or within an array of callbacks
        function constructCallbackDispatcher(callback) {
            if (callback == null) {
                return function (response) {
                    console.warn("Ignoring response " + JSON.stringify(response));
                };
            } else if (callback === "ignore") {
                // Ignore the return value
                return function () {
                };
            }
            var callbackArray = Array.isArray(callback) ? callback : [ callback ];
            return function (response, idx) {
                callbackArray[idx % callbackArray.length](response, idx);
            }
        }

        // Extract the HTTP-Method to use and make some sanity checks if
        // the method was provided as part of the options, but don't fit
        // to the request given
        function extractMethod(request, opts) {
            var methodGiven = opts && opts.method ? opts.method.toLowerCase() : null,
                    method;
            if (methodGiven) {
                if (methodGiven === "get") {
                    if (Array.isArray(request)) {
                        throw new Error("Cannot use GET with bulk requests");
                    }
                    if (request.type.toLowerCase() === "read" && Array.isArray(request.attribute)) {
                        throw new Error("Cannot use GET for read with multiple attributes");
                    }
                    if (request.target) {
                        throw new Error("Cannot use GET request with proxy mode");
                    }
                    if (request.config) {
                        throw new Error("Cannot use GET with request specific config");
                    }
                }
                method = methodGiven;
            } else {
                // Determine method dynamically
                method = Array.isArray(request) ||
                         request.config ||
                         (request.type.toLowerCase() === "read" && Array.isArray(request.attribute)) ||
                         request.target ?
                        "post" : "get";
            }
            if (opts.jsonp && method === "post") {
                throw new Error("Can not use JSONP with POST requests");
            }
            return method;
        }

        // Add processing parameters given as request options
        // to an URL as GET query parameters
        function addProcessingParameters(url, opts) {
            var sep = url.indexOf("?") > 0 ? "&" : "?";
            PROCESSING_PARAMS.forEach(function (key) {
                if (opts[key] != null) {
                    url += sep + key + "=" + opts[key];
                    sep = "&";
                }
            });
            return url;
        }

        // ========================================================================
        // GET-Request handling

        // Create the URL used for a GET request
        function constructGetUrlPath(request) {
            var type = request.type;
            assertNotNull(type, "No request type given for building a GET request");
            type = type.toLowerCase();
            var extractor = GET_URL_EXTRACTORS[type];
            assertNotNull(extractor, "Unknown request type " + type);
            var result = extractor(request);
            var parts = result.parts || [];
            var url = type;
            parts.forEach(function (v) {
                url += "/" + Jolokia.escape(v)
            });
            if (result.path) {
                url += (result.path[0] == '/' ? "" : "/") + result.path;
            }
            return url;
        }

        // For POST requests it is recommended to have a trailing slash at the URL
        // in order to avoid a redirect which then results in a GET request.
        // See also https://bugs.eclipse.org/bugs/show_bug.cgi?id=331194#c1
        // for an explanation
        function ensureTrailingSlash(url) {
            // Squeeze any URL to a single one, optionally adding one
            return url.replace(/\/*$/, "/");
        }

        // Extractors used for preparing a GET request, i.e. for creating a stack
        // of arguments which gets appended to create the proper access URL
        // key: lowercase request type.
        // The return value is an object with two properties: The 'parts' to glue together, where
        // each part gets escaped and a 'path' which is appended literally
        var GET_URL_EXTRACTORS = {
            "read": function(request) {
                if (request.attribute == null) {
                    // Path gets ignored for multiple attribute fetch
                    return { parts:[ request.mbean, '*' ], path:request.path };
                } else {
                    return { parts:[ request.mbean, request.attribute ], path:request.path };
                }
            },
            "write": function(request) {
                return { parts:[request.mbean, request.attribute, valueToString(request.value)], path:request.path};
            },
            "exec": function(request) {
                var ret = [ request.mbean, request.operation ];
                if (request.arguments && request.arguments.length > 0) {
                    request.arguments.forEach(function (value) {
                        ret.push(valueToString(value));
                    });
                }
                return {parts:ret};
            },
            "version": function() {
                return {};
            },
            "search": function(request) {
                return { parts:[request.mbean]};
            },
            "list": function(request) {
                return { path:request.path};
            },
            "notification": function(request) {
                switch(request.command) {
                    case "register":
                        return { parts: [ "register" ] };
                    case "add":
                        var ret = [ "add", request.client, request.mode, request.mbean];
                        var extra = [];
                        if (request.handback) {
                            extra.push(valueToString(request.handback));
                        }
                        if (request.config) {
                            extra.push(valueToString(request.config));
                        } else if (extra.length) {
                            extra.push("{}");
                        }
                        if (request.filter) {
                            extra.push(valueToString(request.filter));
                        } else if (extra.length) {
                            extra.push(" ");
                        }
                        return { parts: ret.concat(extra.reverse()) };
                    case "remove":
                        return { parts: [ "remove", request.client, request.handle ]};
                    case "unregister":
                        return { parts: [ "unregister", request.client ]};
                    case "list":
                        return { parts: [ "list", request.client ]};
                    case "ping":
                        return { parts: [ "ping", request.client ]};
                }
                throw new Error("Unknown command '" + request.command + "'");
            }
        };

        // Convert a value to a string for passing it to the Jolokia agent via
        // a get request (write, exec). Value can be either a single object or an array
        function valueToString(value) {
            if (value == null) {
                return "[null]";
            }
            if (Array.isArray(value)) {
                var ret = "";
                for (var i = 0; i < value.length; i++) {
                    ret += value == null ? "[null]" : singleValueToString(value[i]);
                    if (i < value.length - 1) {
                        ret += ",";
                    }
                }
                return ret;
            } else {
                return singleValueToString(value);
            }
        }

        // Single value conversion for write/exec GET requests
        function singleValueToString(value) {
            if (typeof value === "string" && value.length == 0) {
                return "\"\"";
            } else {
                return value.toString();
            }
        }

        // Check whether a synchronous request was a success or not
        // Taken from jQuery 1.4
        function httpSuccess(xhr) {
            try {
                return !xhr.status && location.protocol === "file:" ||
                       xhr.status >= 200 && xhr.status < 300 ||
                       xhr.status === 304 || xhr.status === 1223;
            } catch (e) {
            }
            return false;
        }

        // ===============================================================================================
        // Utility methods:

        function assertNotNull(object, message) {
            if (object == null) {
                throw new Error(message);
            }
        }

        // ================================================================================================

        // Escape a path part, can be used as a static method outside this function too
        Jolokia.prototype.escape = Jolokia.escape = function (part) {
            // TODO: review GET URL path encoding
            return encodeURIComponent(part.replace(/!/g, "!!").replace(/\//g, "!/"));
        };

        /**
         * Utility method which checks whether a response is an error or a success
         * @param resp response to check
         * @return true if response is an error, false otherwise
         */
        Jolokia.prototype.isError = Jolokia.isError = function(resp) {
            return resp.status == null || resp.status != 200;
        };

        /**
         * Polyfill method for $.extend and Object.assign.
         */
        Jolokia.prototype.assignObject = Jolokia.assignObject = function() {
            /*
            if (typeof Object.assign === "function") {
                return Object.assign.apply(Object, arguments);
            }
            */
            var target = arguments[0]
            var sources = Array.prototype.slice.call(arguments, 1);
            if (target === undefined || target === null) {
                throw new Error("Cannot assign object to undefined or null");
            }

            sources.forEach(function (source) {
                if (source === undefined || source === null) {
                    return;
                }
                Object.keys(source).forEach(function (key) {
                    if (Object.prototype.hasOwnProperty.call(source, key)) {
                        target[key] = source[key];
                    }
                });
            })

            return target;
        }

        // Return back exported function/constructor
        return Jolokia;
    };

    return _jolokiaConstructorFunc(jQuery);
}));

