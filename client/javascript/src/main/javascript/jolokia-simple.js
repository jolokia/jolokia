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
    Jolokia.prototype.getAttribute = function(mbean,attribute,opts) {
        this.request(
        { type: "read", mbean: mbean, attribute: attribute },
        {
            success: function(resp) {
                console.log(JSON.stringify(resp));
            }
        });
    }
} else {
    console.error("No Jolokia definition found. Please include jolokia.js before jolokia-simple.js");
}
