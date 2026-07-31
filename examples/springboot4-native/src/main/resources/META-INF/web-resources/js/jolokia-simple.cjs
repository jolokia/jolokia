(function (global, factory) {
typeof exports === 'object' && typeof module !== 'undefined' ? module.exports = factory(require('jolokia.js')) :
typeof define === 'function' && define.amd ? define(['jolokia.js'], factory) :
(global = typeof globalThis !== 'undefined' ? globalThis : global || self, global.JolokiaSimple = factory(global.Jolokia));
})(this, (function (Jolokia) { 'use strict';

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
// ++++++++++++++++++++++++++++++++++++++++++++++++++
// Public API defined in Jolokia.prototype. Most of the methods come from "jolokia.js", here we extend
// the interface (JS prototype) with "simple" methods which use jolokia.request() internally
Jolokia.prototype.getAttribute = async function (mbean, ...params) {
    const request = { type: "read", mbean: mbean };
    let options = {};
    if (params.length === 3 && !Array.isArray(params[2]) && typeof params[2] === "object") {
        // attribute: string | string[], path: string | string[], opts: RequestOptions
        request.attribute = params[0];
        addPath(request, params[1]);
        options = params[2];
    }
    else if (params.length === 2) {
        // attribute: string | string[], opts: RequestOptions, or
        // attribute: string | string[], path: string | string
        request.attribute = params[0];
        if (!Array.isArray(params[1]) && typeof params[1] === "object") {
            options = params[1];
        }
        else {
            addPath(request, params[1]);
        }
    }
    else if (params.length == 1) {
        // opts: RequestOptions, or
        // attribute: string | string[]
        if (!Array.isArray(params[0]) && typeof params[0] === "object") {
            options = params[0];
        }
        else {
            request.attribute = params[0];
        }
    }
    options.method = "post";
    createValueCallback(options);
    if ("success" in options || "error" in options) {
        // result delivered using callback
        await this.request(request, options);
        return null;
    }
    return await this.request(request, options)
        .then((r) => {
        const response = r;
        if (response) {
            // JolokiaSuccessResponse or JolokiaErrorResponse
            if (Jolokia.isError(response)) {
                throw response.error;
            }
            else {
                return response.value;
            }
        }
        else {
            return null;
        }
    });
};
Jolokia.prototype.setAttribute = async function (mbean, attribute, value, ...params) {
    const request = { type: "write", mbean, attribute, value };
    let options = {};
    if (params.length === 2 && !Array.isArray(params[1]) && typeof params[1] === "object") {
        addPath(request, params[0]);
        options = params[1];
    }
    else if (params.length === 1) {
        if (!Array.isArray(params[0]) && typeof params[0] === "object") {
            options = params[0];
        }
        else {
            addPath(request, params[0]);
        }
    }
    options.method = "post";
    createValueCallback(options);
    if ("success" in options || "error" in options) {
        // result delivered using callback
        await this.request(request, options);
        return null;
    }
    return await this.request(request, options)
        .then((r) => {
        const response = r;
        if (response) {
            // JolokiaSuccessResponse or JolokiaErrorResponse
            if (Jolokia.isError(response)) {
                throw response.error;
            }
            else {
                return response.value;
            }
        }
        else {
            return null;
        }
    });
};
Jolokia.prototype.execute = async function (mbean, operation, /*opts?: SimpleRequestOptions, */ ...params) {
    const parameters = params.length > 0 && params[params.length - 1] && !Array.isArray(params[params.length - 1])
        && typeof params[params.length - 1] === "object"
        ? params.slice(0, -1) : params;
    const request = { type: "exec", mbean, operation, arguments: parameters };
    const options = params.length > 0 && params[params.length - 1] && !Array.isArray(params[params.length - 1])
        && typeof params[params.length - 1] === "object"
        ? params[params.length - 1] : {};
    options.method = "post";
    createValueCallback(options);
    if ("success" in options || "error" in options) {
        // result delivered using callback
        await this.request(request, options);
        return null;
    }
    return await this.request(request, options)
        .then((r) => {
        const response = r;
        if (response) {
            // JolokiaSuccessResponse or JolokiaErrorResponse
            if (Jolokia.isError(response)) {
                throw response.error;
            }
            else {
                return response.value;
            }
        }
        else {
            return null;
        }
    });
};
Jolokia.prototype.search = async function (mbeanPattern, opts) {
    const request = { type: "search", mbean: mbeanPattern };
    const options = opts ? opts : {};
    options.method = "post";
    createValueCallback(options);
    if ("success" in options || "error" in options) {
        // result delivered using callback
        await this.request(request, options);
        // we need to return something, but it should be ignored
        return [];
    }
    return await this.request(request, options)
        .then((r) => {
        const response = r;
        if (response) {
            // JolokiaSuccessResponse or JolokiaErrorResponse
            if (Jolokia.isError(response)) {
                throw response.error;
            }
            else {
                return !response.value ? [] : response.value;
            }
        }
        else {
            return [];
        }
    });
};
Jolokia.prototype.version = async function (opts) {
    const request = { type: "version" };
    const options = opts ? opts : {};
    options.method = "post";
    createValueCallback(options);
    if ("success" in options || "error" in options) {
        // result delivered using callback
        await this.request(request, options);
        return null;
    }
    return await this.request(request, options)
        .then((r) => {
        const response = r;
        if (response) {
            // JolokiaSuccessResponse or JolokiaErrorResponse
            if (Jolokia.isError(response)) {
                throw response.error;
            }
            else {
                return response.value;
            }
        }
        else {
            return null;
        }
    });
};
Jolokia.prototype.list = async function (...params) {
    const request = { type: "list" };
    let options = {};
    if (params.length === 2 && !Array.isArray(params[1]) && typeof params[1] === "object") {
        addPath(request, params[0]);
        options = params[1];
    }
    else if (params.length === 1) {
        if (!Array.isArray(params[0]) && !Array.isArray(params[0]) && typeof params[0] === "object") {
            options = params[0];
        }
        else {
            addPath(request, params[0]);
        }
    }
    options.method = "post";
    createValueCallback(options);
    if ("success" in options || "error" in options) {
        // result delivered using callback
        await this.request(request, options);
        return null;
    }
    return await this.request(request, options)
        .then((r) => {
        const response = r;
        if (response) {
            // JolokiaSuccessResponse or JolokiaErrorResponse
            if (Jolokia.isError(response)) {
                throw response.error;
            }
            else {
                return response.value;
            }
        }
        else {
            return {};
        }
    });
};
Jolokia.isVersionResponse = function (resp) {
    if (!resp || typeof resp !== 'object')
        return false;
    return 'protocol' in resp && 'agent' in resp;
};
// ++++++++++++++++++++++++++++++++++++++++++++++++++
// Private/internal functions
/**
 * If path is an array, the elements get escaped. If not, it is taken directly
 * @param request
 * @param path
 */
function addPath(request, path) {
    if (path) {
        if (Array.isArray(path)) {
            request.path = path.map(Jolokia.escapePost).join("/");
        }
        else {
            request.path = path;
        }
    }
}
/**
 * For Jolokia simple, passed callbacks don't expect full response (array), but only its `value` field. If there's
 * no callback, we don't create anything and promises will be used
 * @param options
 */
function createValueCallback(options) {
    if (options.success && typeof options.success === "function") {
        const passedSuccessCb = options.success;
        options.success = (response, _index) => {
            passedSuccessCb(response.value);
        };
    }
}

return Jolokia;

}));
