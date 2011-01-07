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
 * Jolokia Javascript Client library
 * =================================
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
        dataType: "json"
    };

    function Jolokia(param) {

        // If called without 'new', we are constructing an object
        // nevertheless
        if ( !(this instanceof arguments.callee) ) {
            return new Jolokia(param);
        }

        if (param == null || param.url == null) {
            throw new SyntaxError("No URL given");
        }
        $.extend(this,DEFAULT_CLIENT_PARAMS,param);

        /**
         * The request method using one or more JSON requests
         * and sending it to the target URL.
         */
        this.request = function(request,params) {
            var opts = $.extend({},this,params);
            var ajax_params = {};

            if (opts.method == "POST" || $.isArray(request)) {
                $.extend(ajax_params,POST_AJAX_PARAMS);
                ajax_params.data = JSON.stringify(request);
                ajax_params.url = opts.url;
            } else {
                $.extend(ajax_params,GET_AJAX_PARAMS);
                ajax_params.dataType = opts.jsonp ? "jsonp" : "json";
                ajax_params.url = createGetUrl(opts.url,request);
            }

            // Callbacks
            if (params.success) {
                var success_callbacks = $.isArray(params.success) ? params.success : [ params.success ],
                        idx = 0,
                        length = success_callbacks.length;
                ajax_params.success = function(data,textStatus,xmlRequest) {
                    success_callbacks[idx++ % length](data,textStatus,xmlRequest);
                };
                $.ajax(ajax_params);
            }
        };
    }

    // ======================================================================================================
    // GET-Request handling

    /**
     * Create the URL used for a GET request
     *
     * @param base_url to request to
     * @param request the request to convert to URL format
     */
    function createGetUrl(base_url, request) {
        var type = request.type;
        if (type == null) {
            throw new Error("No request type given for building a GET request");
        }
        type = type.toLowerCase();
        var extractor = GET_URL_EXTRACTORS[type];
        if (extractor == null) {
            throw new Error("Unknown request type " + type);
        }
        var parts = extractor(request);
        var url = type;
        for (var i = 0; i < parts.length; i++) {
            url += "/" + escapePart(parts[i]);
        }
        return base_url + "/" + url;
    }

    // Extractors used for preparing a GET request, i.e. for creating a stack
    // of arguments which gets appended to create the proper access URL
    // key: lowercase request type
    var GET_URL_EXTRACTORS = {
        "read" : function(request) {
            return appendPath([ request.mbean, request.attribute ],request.path);
        },
        "write" : function(request) {
            return appendPath([ request.mbean, request.attribute, valueToString(request.value) ],request.path);
        },
        "exec" : function(request) {
            var ret = [ request.mbean, request.operation ];
            if (request.arguments && request.arguments.length > 0) {
                $.each(request.arguments,function(index,value) {
                    ret.push(valueToString(value));
                });
            }
            return ret;
        },
        "version": function() {
            return [];
        },
        "search": function(request) {
            return [ request.mbean ];
        },
        "list": function(request) {
            return appendPath([],request.path);
        }
    };

    function escapePart(part) {
        // TODO: Escaping of slashes
        return part;
    }

    // Split up a path and append it to a given array
    function appendPath(array,path) {
         return path ? $.merge(array,path.split(/\//)) : array;
    }

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

    // ===============================================================================================

    // Return back constructor function
    return Jolokia;

})(jQuery);
