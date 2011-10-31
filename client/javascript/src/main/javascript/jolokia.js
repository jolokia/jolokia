/*
 * Copyright 2009-2010 Roland Huss
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
 * Jolokia Javascript Client Library
 * =================================
 *
 * Requires jquery.js and json2.js
 * (if no native JSON.stringify() support is available)
 */

var Jolokia = (function($) {

    // Default paramerters for GET and POST requests
    var DEFAULT_CLIENT_PARAMS = {
        type: "POST",
        jsonp: false
    };

    var GET_AJAX_PARAMS = {
        type: "GET"
    };

    var POST_AJAX_PARAMS = {
        type: "POST",
        processData: false,
        dataType: "json",
        contentType: "text/json"
    };

    // Processing parameters which are added to the
    // URL as query parameters if given as options
    var PROCESSING_PARAMS = ["maxDepth","maxCollectionSize","maxObjects","ignoreErrors"];

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
        if ( !(this instanceof arguments.callee) ) {
            return new Jolokia(param);
        }

        // Jolokia Javascript Client version
        this.CLIENT_VERSION = "1.0.1";

        // Allow a single URL parameter as well
        if (typeof param === "string") {
            param = {url: param};
        }
        $.extend(this,DEFAULT_CLIENT_PARAMS,param);

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
         *     dyamically. If a method is selected which doesn't fit to the request, an error
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
         *     suceeded with a status code of 200, but the response object contains a status other
         *     than OK (200) which happens if the request JMX operation fails. This callback receives
         *     the full Jolokia response object (with a key <code>error</code> set). If no error callback
         *     is given, but an asynchronous operation is performed, the error response is printed
         *     to the Javascript console by default.
         *   </dd>
         *   <dt>ajaxError</dt>
         *   <dd>
         *     Global error callback called when the Ajax request itself failed. It obtains the same arguments
         *     as the error callback given for <code>jQuery.ajax()</code>, i.e. the <code>XmlHttpResonse</code>,
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
         *     returns with an error. This works only for certain operations like pattern reads..
         *   </dd>
         * </dl>
         *
         * @param request the request to send
         * @param params parameters used for sending the request
         * @return the response object if called synchronously or nothing if called for asynchronous operation.
         */
        this.request = function(request,params) {
            var opts = $.extend({},this,params);
            assertNotNull(opts.url,"No URL given");

            var ajaxParams = {};

            // Copy over direct params for the jQuery ajax call
            $.each(["username", "password", "timeout"],function(i,key) {
                if (opts[key]) {
                    ajaxParams[key] = opts[key];
                }
            });

            if (extractMethod(request,opts) === "post") {
                $.extend(ajaxParams,POST_AJAX_PARAMS);
                ajaxParams.data = JSON.stringify(request);
                ajaxParams.url = ensureTrailingSlash(opts.url);
            } else {
                $.extend(ajaxParams,GET_AJAX_PARAMS);
                ajaxParams.dataType = opts.jsonp ? "jsonp" : "json";
                ajaxParams.url = opts.url + "/" + constructGetUrlPath(request);
            }

            // Add processing parameters as query parameters
            ajaxParams.url = addProcessingParameters(ajaxParams.url,opts);

            // Global error handler
            if (opts.ajaxError) {
                ajaxParams.error = opts.ajaxError;
            }

            // Dispatch Callbacks to error and success handlers
            if (opts.success) {
                var success_callback = constructCallbackDispatcher(opts.success);
                var error_callback = constructCallbackDispatcher(opts.error);
                ajaxParams.success = function(data) {
                    var responses = $.isArray(data) ? data : [ data ];
                    for (var idx = 0; idx < responses.length; idx++) {
                        var resp = responses[idx];
                        if (resp.status == null || resp.status != 200) {
                            error_callback(resp,idx);
                        } else {
                            success_callback(resp,idx);
                        }
                    }
                };

                // Perform the request
                $.ajax(ajaxParams);
            } else {
                // Synchronous operation requested (i.e. no callbacks provided)
                if (opts.jsonp) {
                    throw Error("JSONP is not supported for synchronous requests");
                }
                ajaxParams.async = false;
                var xhr = $.ajax(ajaxParams);
                if (httpSuccess(xhr)) {
                    return $.parseJSON(xhr.responseText);
                } else {
                    return null;
                }
            }
        };

        // ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    }

    // Private Methods:

    // ========================================================================

    // Construct a callback dispatcher for appropriately dispatching
    // to a single callback or within an array of callbacks
    function constructCallbackDispatcher(callback) {
        if (callback == null) {
            return function(response) {
                console.log("Ignoring response " + JSON.stringify(response));
            };
        } else if (callback === "ignore") {
            // Ignore the return value
            return function() {};
        }
        var callbackArray = $.isArray(callback) ? callback : [ callback ];
        return function(response,idx) {
            callbackArray[idx % callbackArray.length](response,idx);
        }
    }

    // Extract the HTTP-Method to use and make some sanity checks if
    // the method was provided as part of the options, but dont fit
    // to the request given
    function extractMethod(request,opts) {
        var methodGiven = opts && opts.method ? opts.method.toLowerCase() : null,
                method;
        if (methodGiven) {
            if (methodGiven === "get") {
                if ($.isArray(request)) {
                    throw new Error("Cannot use GET with bulk requests");
                }
                if (request.type.toLowerCase() === "read" && $.isArray(request.attribute)) {
                    throw new Error("Cannot use GET for read with multiple attributes");
                }
                if (request.target) {
                    throw new Error("Cannot use GET request with proxy mode");
                }
            }
            method = methodGiven;
        } else {
            // Determine method dynamically
            method = $.isArray(request) ||
                    (request.type.toLowerCase() === "read" && $.isArray(request.attribute)) ||
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
        $.each(PROCESSING_PARAMS,function(i,key) {
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
        assertNotNull(type,"No request type given for building a GET request");
        type = type.toLowerCase();
        var extractor = GET_URL_EXTRACTORS[type];
        assertNotNull(extractor,"Unknown request type " + type);
        var result = extractor(request);
        var parts = result.parts || {};
        var url = type;
        $.each(parts,function(i,v) { url += "/" + Jolokia.escape(v) });
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
        return url.replace(/\/*$/,"/");
    }

    // Extractors used for preparing a GET request, i.e. for creating a stack
    // of arguments which gets appended to create the proper access URL
    // key: lowercase request type.
    // The return value is an object with two properties: The 'parts' to glue together, where
    // each part gets escaped and a 'path' which is appended literally
    var GET_URL_EXTRACTORS = {
        "read" : function(request) {
            if (request.attribute == null) {
                // Path gets ignored for multiple attribute fetch
                return { parts: [ request.mbean ] };
            } else {
                return { parts: [ request.mbean, request.attribute ], path: request.path };
            }
        },
        "write" : function(request) {
            return { parts: [request.mbean, request.attribute, valueToString(request.value)], path: request.path};
        },
        "exec" : function(request) {
            var ret = [ request.mbean, request.operation ];
            if (request.arguments && request.arguments.length > 0) {
                $.each(request.arguments,function(index,value) {
                    ret.push(valueToString(value));
                });
            }
            return {parts: ret};
        },
        "version": function() {
            return {};
        },
        "search": function(request) {
            return { parts: [request.mbean]};
        },
        "list": function(request) {
            return { path: request.path};
        }
    };

    // Convert a value to a string for passing it to the Jolokia agent via
    // a get request (write, exec). Value can be either a single object or an array
    function valueToString(value) {
        if (value == null) {
            return "[null]";
        }
        if ($.isArray(value)) {
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
        if ( typeof value === "string" && value.length == 0) {
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
        } catch(e) {}
		return false;
	}

    // ===============================================================================================
    // Utility methods:

    function assertNotNull(object,message) {
        if (object == null) {
            throw new Error(message);
        }
    }

    // ================================================================================================

    // Escape a path part, can be used as a static method outside this function too
    Jolokia.escape = function(part) {
        return part.replace(/!/g,"!!").replace(/\//g,"!/");
    };

    // Return back exported function/constructor
    return Jolokia;
})(jQuery);
