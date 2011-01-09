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

if (Jolokia) {
    (function($) {
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
        function getAttribute(mbean,attribute,path,opts) {
            if (arguments.length === 3 && typeof path == "object") {
                opts = path;
                path = null;
            } else if (arguments.length == 2 && typeof attribute == "object") {
                opts = attribute;
                attribute = null;
                path = null;
            }
            var req = { type: "read", mbean: mbean, attribute: attribute };
            if (path != null) {
                req.path = path;
            }
            return extractValue(this.request(req,prepareSucessCallback(opts)));
        }

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
        function setAttribute(mbean,attribute,value,path,opts) {
            if (arguments.length === 4 && typeof path == "object") {
                opts = path;
                path = null;
            }
            var req = { type: "write", mbean: mbean, attribute: attribute, value: value };
            if (path != null) {
                req.path = path;
            }
            return extractValue(this.request(req,prepareSucessCallback(opts)));
        }

        /**
         * Execute a JMX operation and return the result value
         *  
         * @param mbean objectname of the MBean to operate on
         * @param operation name of operation to execute. Can contain a signature in case overloaded
         *                  operations are to be called (comma separated fully qualified argument types 
         *                  append to the operation name within parentheses)
         * @param opts optional options for Jolokia.request()
         * @param arg1, arg2, ..... one or more argument required for executing the operation.
         * @return the return value of the JMX operation.
         */
        function execute(mbean,operation,opts) {
            var req = { type: "exec", mbean: mbean, operation: operation };
            var start = opts == null || typeof opts != "object" ? 2 : 3;
            if (arguments.length > start) {
                var args = [];
                for (var i = start; i < arguments.length; i++) {
                    args[i-start] = arguments[i];
                }
                req.arguments = args;
            }
            return extractValue(this.request(req,prepareSucessCallback(opts)));
        }
        
        // =======================================================================

        function extractValue(response) {
            return response != null ? response.value : null;
        }

        // Prepare callback to receive directly the value (instead of the full blown response)
        function prepareSucessCallback(opts) {
            if (opts && opts.success) {
                var parm = $.extend({},opts);
                parm.success = function(resp) {
                    opts.success(resp.value);
                };
                return parm;
            } else {
                return opts;
            }
        }

        // Extend the Jolokia prototype with new functionality (mixin)
        $.extend(Jolokia.prototype,
                 {
                     "getAttribute" : getAttribute,
                     "setAttribute" : setAttribute,
                     "execute": execute
                 });
    })(jQuery);
} else {
    console.error("No Jolokia definition found. Please include jolokia.js before jolokia-simple.js");
}
