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

import Jolokia, {
  BaseRequest,
  ExecRequest,
  ExecResponseValue,
  IJolokia,
  JolokiaErrorResponse,
  JolokiaResponseValue,
  JolokiaSuccessResponse,
  ListRequest,
  ListResponseValue,
  ReadRequest,
  ReadResponseValue,
  RequestOptions,
  SearchRequest,
  SearchResponseValue,
  VersionRequest,
  VersionResponseValue,
  WriteRequest,
  WriteResponseValue
} from "jolokia.js"

import {
  JolokiaSimpleStatic,
  SimpleRequestOptions
} from "./jolokia-simple-types.js"

// ++++++++++++++++++++++++++++++++++++++++++++++++++
// Public API defined in Jolokia.prototype. Most of the methods come from "jolokia.js", here we extend
// the interface (JS prototype) with "simple" methods which use jolokia.request() internally

Jolokia.prototype.getAttribute = async function (this: IJolokia, mbean: string, ...params: (string | string[] | SimpleRequestOptions)[]):
    Promise<ReadResponseValue> {

  const request: ReadRequest = { type: "read", mbean: mbean }
  let options: SimpleRequestOptions = {}

  if (params.length === 3 && !Array.isArray(params[2]) && typeof params[2] === "object") {
    // attribute: string | string[], path: string | string[], opts: RequestOptions
    request.attribute = params[0] as string | string[]
    addPath(request, params[1] as string | string[])
    options = params[2] as SimpleRequestOptions
  } else if (params.length === 2) {
    // attribute: string | string[], opts: RequestOptions, or
    // attribute: string | string[], path: string | string
    request.attribute = params[0] as string | string[]
    if (!Array.isArray(params[1]) && typeof params[1] === "object") {
      options = params[1] as SimpleRequestOptions
    } else {
      addPath(request, params[1] as string | string[])
    }
  } else if (params.length == 1) {
    // opts: RequestOptions, or
    // attribute: string | string[]
    if (!Array.isArray(params[0]) && typeof params[0] === "object") {
      options = params[0] as SimpleRequestOptions
    } else {
      request.attribute = params[0] as string | string[]
    }
  }

  options.method = "post"
  createValueCallback(options)

  if ("success" in options || "error" in options) {
    // result delivered using callback
    await this.request(request, options)
    return null
  }
  return await this.request(request, options)
    .then((r): ReadResponseValue => {
      const response = r as JolokiaSuccessResponse | JolokiaErrorResponse
      if (response) {
        // JolokiaSuccessResponse or JolokiaErrorResponse
        if (Jolokia.isError(response)) {
          throw response.error
        } else {
          return response.value as ReadResponseValue
        }
      } else {
        return null
      }
    })
}

Jolokia.prototype.setAttribute = async function (this: IJolokia, mbean: string, attribute: string, value: unknown, ...params: (string | string[] | SimpleRequestOptions)[]):
    Promise<WriteResponseValue> {

  const request: WriteRequest = { type: "write", mbean, attribute, value }
  let options: SimpleRequestOptions = {}

  if (params.length === 2 && !Array.isArray(params[1]) && typeof params[1] === "object") {
    addPath(request, params[0] as string | string[])
    options = params[1] as SimpleRequestOptions
  } else if (params.length === 1) {
    if (!Array.isArray(params[0]) && typeof params[0] === "object") {
      options = params[0] as SimpleRequestOptions
    } else {
      addPath(request, params[0] as string | string[])
    }
  }

  options.method = "post"
  createValueCallback(options)

  if ("success" in options || "error" in options) {
    // result delivered using callback
    await this.request(request, options)
    return null
  }
  return await this.request(request, options)
    .then((r): WriteResponseValue => {
      const response = r as JolokiaSuccessResponse | JolokiaErrorResponse
      if (response) {
        // JolokiaSuccessResponse or JolokiaErrorResponse
        if (Jolokia.isError(response)) {
          throw response.error
        } else {
          return response.value as WriteResponseValue
        }
      } else {
        return null
      }
    })
}

Jolokia.prototype.execute = async function (this: IJolokia, mbean: string, operation: string, /*opts?: SimpleRequestOptions, */...params: (unknown | SimpleRequestOptions)[]):
    Promise<ExecResponseValue> {

  const parameters = params.length > 0 && params[params.length - 1] && !Array.isArray(params[params.length - 1])
    && typeof params[params.length - 1] === "object"
    ? params.slice(0, -1) : params
  const request: ExecRequest = { type: "exec", mbean, operation, arguments: parameters }
  const options: SimpleRequestOptions = params.length > 0 && params[params.length - 1] && !Array.isArray(params[params.length - 1])
    && typeof params[params.length - 1] === "object"
    ? params[params.length - 1] as SimpleRequestOptions : {}

  options.method = "post"
  createValueCallback(options)

  if ("success" in options || "error" in options) {
    // result delivered using callback
    await this.request(request, options)
    return null
  }
  return await this.request(request, options)
    .then((r): ExecResponseValue => {
      const response = r as JolokiaSuccessResponse | JolokiaErrorResponse
      if (response) {
        // JolokiaSuccessResponse or JolokiaErrorResponse
        if (Jolokia.isError(response)) {
          throw response.error
        } else {
          return response.value as ExecResponseValue
        }
      } else {
        return null
      }
    })
}

Jolokia.prototype.search = async function (this: IJolokia, mbeanPattern: string, opts?: SimpleRequestOptions):
    Promise<SearchResponseValue> {

  const request: SearchRequest = { type: "search", mbean: mbeanPattern }
  const options: SimpleRequestOptions = opts ? opts : {}

  options.method = "post"
  createValueCallback(options)

  if ("success" in options || "error" in options) {
    // result delivered using callback
    await this.request(request, options)
    // we need to return something, but it should be ignored
    return []
  }
  return await this.request(request, options)
    .then((r): SearchResponseValue => {
      const response = r as JolokiaSuccessResponse | JolokiaErrorResponse
      if (response) {
        // JolokiaSuccessResponse or JolokiaErrorResponse
        if (Jolokia.isError(response)) {
          throw response.error
        } else {
          return !response.value ? [] : response.value as SearchResponseValue
        }
      } else {
        return []
      }
    })
}

Jolokia.prototype.version = async function (this: IJolokia, opts?: SimpleRequestOptions):
    Promise<VersionResponseValue | null> {

  const request: VersionRequest = { type: "version" }
  const options: SimpleRequestOptions = opts ? opts : {}

  options.method = "post"
  createValueCallback(options)

  if ("success" in options || "error" in options) {
    // result delivered using callback
    await this.request(request, options)
    return null
  }
  return await this.request(request, options)
    .then((r): VersionResponseValue | null => {
      const response = r as JolokiaSuccessResponse | JolokiaErrorResponse
      if (response) {
        // JolokiaSuccessResponse or JolokiaErrorResponse
        if (Jolokia.isError(response)) {
          throw response.error
        } else {
          return response.value as VersionResponseValue
        }
      } else {
        return null
      }
    })
}

Jolokia.prototype.list = async function(this: IJolokia, ...params: (string[] | string | SimpleRequestOptions)[]):
    Promise<ListResponseValue> {

  const request: ListRequest = { type: "list" }
  let options: SimpleRequestOptions = {}

  if (params.length === 2 && !Array.isArray(params[1]) && typeof params[1] === "object") {
    addPath(request, params[0] as string | string[])
    options = params[1] as SimpleRequestOptions
  } else if (params.length === 1) {
    if (!Array.isArray(params[0]) && !Array.isArray(params[0]) && typeof params[0] === "object") {
      options = params[0] as SimpleRequestOptions
    } else {
      addPath(request, params[0] as string | string[])
    }
  }

  options.method = "post"
  createValueCallback(options)

  if ("success" in options || "error" in options) {
    // result delivered using callback
    await this.request(request, options)
    return null
  }
  return await this.request(request, options)
    .then((r): ListResponseValue => {
      const response = r as JolokiaSuccessResponse | JolokiaErrorResponse
      if (response) {
        // JolokiaSuccessResponse or JolokiaErrorResponse
        if (Jolokia.isError(response)) {
          throw response.error
        } else {
          return response.value as ListResponseValue
        }
      } else {
        return {}
      }
    })
}

;(Jolokia as JolokiaSimpleStatic).isVersionResponse = function (resp: unknown): resp is VersionResponseValue {
  if (!resp || typeof resp !== 'object') return false
  return 'protocol' in resp && 'agent' in resp
}

// ++++++++++++++++++++++++++++++++++++++++++++++++++
// Private/internal functions

/**
 * If path is an array, the elements get escaped. If not, it is taken directly
 * @param request
 * @param path
 */
function addPath(request: BaseRequest & Pick<ReadRequest, "path">, path: string | string[]) {
  if (path) {
    if (Array.isArray(path)) {
      request.path = path.map(Jolokia.escapePost).join("/")
    } else {
      request.path = path
    }
  }
}

/**
 * For Jolokia simple, passed callbacks don't expect full response (array), but only its `value` field. If there's
 * no callback, we don't create anything and promises will be used
 * @param options
 */
function createValueCallback(options: SimpleRequestOptions): void {
  if (options.success && typeof options.success === "function") {
    const passedSuccessCb = options.success as (value: JolokiaResponseValue) => void
    (options as RequestOptions).success = (response: JolokiaSuccessResponse, _index: number) => {
      passedSuccessCb(response.value as JolokiaResponseValue)
    }
  }
}

export * from "./jolokia-simple-types.js"
export default Jolokia as JolokiaSimpleStatic
