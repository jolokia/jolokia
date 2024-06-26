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
  BaseRequestOptions,
  ExecRequest,
  ExecResponseValue,
  IJolokia,
  JolokiaErrorResponse,
  JolokiaSuccessResponse, ListRequest,
  ListResponseValue,
  ReadRequest,
  ReadResponseValue,
  SearchRequest,
  SearchResponseValue,
  VersionRequest,
  VersionResponseValue,
  WriteRequest,
  WriteResponseValue
} from "jolokia.js"

import { IJolokiaSimple } from "./jolokia-simple-types.js"

// ++++++++++++++++++++++++++++++++++++++++++++++++++
// Public API defined in Jolokia.prototype. Most of the methods come from "jolokia.js", here we extend
// the interface (JS prototype) with "simple" methods which use jolokia.request() internally

Jolokia.prototype.getAttribute = async function (this: IJolokia, mbean: string, ...params: (string | string[] | BaseRequestOptions)[]):
    Promise<ReadResponseValue> {

  const request: ReadRequest = { type: "read", mbean: mbean }
  let options: BaseRequestOptions = {}

  if (params.length === 3 && typeof params[2] === "object") {
    // attribute: string | string[], path: string | string[], opts: AttributeRequestOptions
    request.attribute = params[0] as string | string[]
    addPath(request, params[1] as string | string[])
    options = params[2] as BaseRequestOptions
  } else if (params.length === 2) {
    // attribute: string | string[], opts: AttributeRequestOptions
    // attribute: string | string[], path: string | string
    request.attribute = params[0] as string | string[]
    if (typeof params[1] === "object") {
      options = params[1] as BaseRequestOptions
    } else {
      addPath(request, params[1] as string | string[])
    }
  } else if (params.length == 1) {
    // opts: AttributeRequestOptions
    // attribute: string | string[]
    if (typeof params[0] === "object") {
      options = params[0] as BaseRequestOptions
    } else {
      request.attribute = params[0] as string | string[]
    }
  }

  options.method = "post"

  return await this.request(request, options)
    .then((response): ReadResponseValue => {
      if (Array.isArray(response)) {
        // JolokiaSuccessResponse or JolokiaErrorResponse
        if (Jolokia.isError(response[0])) {
          throw (response[0] as JolokiaErrorResponse).error
        } else {
          return (response[0] as JolokiaSuccessResponse).value as ReadResponseValue
        }
      } else {
        return null
      }
    })
}

Jolokia.prototype.setAttribute = async function (this: IJolokia, mbean: string, attribute: string, value: unknown, ...params: (string | string[] | BaseRequestOptions)[]):
    Promise<WriteResponseValue> {

  const request: WriteRequest = { type: "write", mbean, attribute, value }
  let options: BaseRequestOptions = {}

  if (params.length === 2 && typeof params[1] === "object") {
    addPath(request, params[0] as string | string[])
    options = params[1] as BaseRequestOptions
  } else if (params.length === 1) {
    if (typeof params[0] === "object") {
      options = params[0] as BaseRequestOptions
    } else {
      addPath(request, params[0] as string | string[])
    }
  }

  options.method = "post"

  return await this.request(request, options)
    .then((response): WriteResponseValue => {
      if (Array.isArray(response)) {
        // JolokiaSuccessResponse or JolokiaErrorResponse
        if (Jolokia.isError(response[0])) {
          throw (response[0] as JolokiaErrorResponse).error
        } else {
          return (response[0] as JolokiaSuccessResponse).value as WriteResponseValue
        }
      } else {
        return null
      }
    })
}

Jolokia.prototype.execute = async function (this: IJolokia, mbean: string, operation: string, opts?: BaseRequestOptions, ...params: unknown[]):
    Promise<ExecResponseValue> {

  const request: ExecRequest = { type: "exec", mbean, operation, arguments: params }
  const options: BaseRequestOptions = opts ? opts : {}

  options.method = "post"

  return await this.request(request, options)
    .then((response): ExecResponseValue => {
      if (Array.isArray(response)) {
        // JolokiaSuccessResponse or JolokiaErrorResponse
        if (Jolokia.isError(response[0])) {
          throw (response[0] as JolokiaErrorResponse).error
        } else {
          return (response[0] as JolokiaSuccessResponse).value as ExecResponseValue
        }
      } else {
        return null
      }
    })
}

Jolokia.prototype.search = async function (this: IJolokia, mbeanPattern: string, opts?: BaseRequestOptions):
    Promise<SearchResponseValue> {

  const request: SearchRequest = { type: "search", mbean: mbeanPattern }
  const options: BaseRequestOptions = opts ? opts : {}

  options.method = "post"

  return await this.request(request, options)
    .then((response): SearchResponseValue => {
      if (Array.isArray(response)) {
        // JolokiaSuccessResponse or JolokiaErrorResponse
        if (Jolokia.isError(response[0])) {
          throw (response[0] as JolokiaErrorResponse).error
        } else {
          return !(response[0] as JolokiaSuccessResponse).value ? [] : (response[0] as JolokiaSuccessResponse).value as SearchResponseValue
        }
      } else {
        return []
      }
    })
}

Jolokia.prototype.version = async function (this: IJolokia, opts?: BaseRequestOptions):
    Promise<VersionResponseValue> {

  const request: VersionRequest = { type: "version" }
  const options: BaseRequestOptions = opts ? opts : {}

  options.method = "post"

  return await this.request(request, options)
    .then((response): VersionResponseValue => {
      if (Array.isArray(response)) {
        // JolokiaSuccessResponse or JolokiaErrorResponse
        if (Jolokia.isError(response[0])) {
          throw (response[0] as JolokiaErrorResponse).error
        } else {
          return (response[0] as JolokiaSuccessResponse).value as VersionResponseValue
        }
      } else {
        throw "Unexpected version response: " + JSON.stringify(response)
      }
    })
}

Jolokia.prototype.list = async function(this: IJolokia, ...params: (string | BaseRequestOptions)[]):
    Promise<ListResponseValue> {

  const request: ListRequest = { type: "list" }
  let options: BaseRequestOptions = {}

  if (params.length === 2 && typeof params[1] === "object") {
    request.path = params[0] as string
    options = params[1] as BaseRequestOptions
  } else if (params.length === 1) {
    if (typeof params[0] === "object") {
      options = params[0] as BaseRequestOptions
    } else {
      request.path = params[0] as string
    }
  }

  options.method = "post"

  return await this.request(request, options)
    .then((response): ListResponseValue => {
      if (Array.isArray(response)) {
        // JolokiaSuccessResponse or JolokiaErrorResponse
        if (Jolokia.isError(response[0])) {
          throw (response[0] as JolokiaErrorResponse).error
        } else {
          return (response[0] as JolokiaSuccessResponse).value as ListResponseValue
        }
      } else {
        return {}
      }
    })
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
      request.path = path.map(Jolokia.escape).join("/")
    } else {
      request.path = path
    }
  }
}

export * from "./jolokia-simple-types.js"
export default Jolokia as IJolokiaSimple
