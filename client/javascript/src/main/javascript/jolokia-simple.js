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
         * @param mbean objectname of MBean to query. Can be a pattern
         * @param attribute attribute name. If an array, multiple attributes are fetched
         * @param path optional path within the return value
         * @param opts options passed to Jolokia.request()
         */
        function getAttribute(mbean,attribute,path,opts) {
            if (arguments.length === 3 && typeof path == "object") {
                opts = path;
                path = null;
            }
            var req = { type: "read", mbean: mbean, attribute: attribute };
            if (path != null) {
                req.path = path;
            }

            return extractValue(this.request(req,prepareSucessCallback(opts)));
        }

        // =======================================================================

        function extractValue(response) {
            return response != null ? response.value : null;
        }

        function prepareSucessCallback(opts) {
            if (opts && opts.success) {
                var parm = $.extend({},opts);
                parm.success = new function(resp) {
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
                     "getAttribute" : getAttribute
                 });
    })(jQuery);
} else {
    console.error("No Jolokia definition found. Please include jolokia.js before jolokia-simple.js");
}
