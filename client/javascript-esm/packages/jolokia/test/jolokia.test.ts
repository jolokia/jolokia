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

import { afterAll, beforeAll, describe, expect, test } from "@jest/globals"
import request from "supertest"

import http from "node:http"
import Jolokia, {
  JolokiaResponse,
  JolokiaSuccessResponse,
  NotificationPullValue,
  VersionRequest
} from "../src/jolokia.js"
import app from "./app.js"

const port = 3000
let server: http.Server

beforeAll(() => {
  server = http.createServer({}, app).listen(port)
})

afterAll(() => {
  server.closeAllConnections()
  server.close()
})

describe("Jolokia basic tests", () => {

  test("Jolokia instance creation", () => {
    const jolokia1 = new Jolokia({ url: "http://localhost" })
    expect(typeof jolokia1).toBe("object")
    const jolokia2 = Jolokia({ url: "http://localhost" })
    expect(typeof jolokia2).toBe("object")
  })

  test("Jolokia client version", () => {
    const j = new Jolokia({ url: "http://localhost" })
    expect(j.CLIENT_VERSION).toMatch(/^2/)
  })

  test("Jolokia members", () => {
    const j = new Jolokia({ url: "http://localhost" })
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Enumerability_and_ownership_of_properties#traversing_object_properties
    // check only enumerable properties in the object and prototype (for..in). Object.keys() doesn't include
    // properties from the prototype
    const expected: { [k: string]: string } = {}
    for (const k in j) {
      expected[k] = typeof j[k as keyof typeof Jolokia]
    }

    expect(Object.keys(expected).length).toBe(13)

    expect(expected["request"]).toBe("function")
    expect(expected["register"]).toBe("function")
    expect(expected["unregister"]).toBe("function")
    expect(expected["jobs"]).toBe("function")
    expect(expected["start"]).toBe("function")
    expect(expected["stop"]).toBe("function")
    expect(expected["isRunning"]).toBe("function")
    expect(expected["addNotificationListener"]).toBe("function")
    expect(expected["removeNotificationListener"]).toBe("function")
    expect(expected["unregisterNotificationClient"]).toBe("function")

    expect(expected["escape"]).toBe("function")
    expect(expected["isError"]).toBe("function")

    expect(expected["CLIENT_VERSION"]).toBe("string")
  })

  test("Managing jobs in a JS array", () => {
    expect.assertions(10)
    const tab: string[] = [ "Hello", "world", "of", "Jolokia" ]
    delete tab[2]
    for (let i = 0; i < tab.length; i++) {
      if (tab[i]) {
        expect(typeof tab[i]).toBe("string")
        expect(Object.prototype.hasOwnProperty.call(tab, i)).toBe(true)
      }
    }
    expect(Object.prototype.hasOwnProperty.call(tab, 0)).toBe(true)
    expect(Object.prototype.hasOwnProperty.call(tab, 1)).toBe(true)
    expect(Object.prototype.hasOwnProperty.call(tab, 2)).toBe(false)
    expect(Object.prototype.hasOwnProperty.call(tab, 3)).toBe(true)
  })

  test("Managing jobs in a JS array - for..of way", () => {
    expect.assertions(4)
    const tab: string[] = [ "Hello", "world", "of", "Jolokia" ]
    delete tab[2]
    for (const txt of tab) {
      if (txt) {
        expect(typeof txt).toBe("string")
      } else {
        expect(typeof txt).toBe("undefined")
      }
    }
  })

  test("Managing jobs in a JS array - for..in way", () => {
    expect.assertions(3)
    const tab: string[] = [ "Hello", "world", "of", "Jolokia" ]
    delete tab[2]
    for (const idx in tab) {
      if (tab[idx]) {
        expect(typeof tab[idx]).toBe("string")
      } else {
        // we WON'T get idx=2
        expect(true).toBeFalsy()
      }
    }
  })

  test("Trailing slash handling", () => {
    function ensureTrailingSlash(url: string): string {
      let trimEnd = url.length
      while (url[trimEnd - 1] === '/') {
        trimEnd--
      }
      return url.substring(0, trimEnd) + '/'
    }

    expect(ensureTrailingSlash("hello")).toBe("hello/")
    expect(ensureTrailingSlash("hello/")).toBe("hello/")
    expect(ensureTrailingSlash("hello//")).toBe("hello/")
    expect(ensureTrailingSlash("hello///")).toBe("hello/")
  })

})

describe("Jolokia HTTP tests", () => {

  test("Test express.js config", async () => {
    return request(server)
        .get("/jolokia/version")
        .expect(200)
        .then((response) => {
          expect(response.body.value.agent).toBe("2.1.0")
        })
  })

  // test("Test with connect timeout", async () => {
  //   // /usr/lib/node_modules_20/undici/lib/core/connect.js:buildConnector() has hardcoded connect timeout for 10000
  //   // see:
  //   //  - https://tools.ietf.org/html/rfc5737
  //   //  - https://en.wikipedia.org/wiki/Reserved_IP_addresses
  //   const jolokia = new Jolokia({ url: "http://192.0.2.0/jolokia", timeout: 500 })
  //   const response = await jolokia.request({ type: "version" })
  //       .catch(error => {
  //         console.info("GOT ERROR", error)
  //         return error
  //       })
  //   expect(response).toBeInstanceOf(DOMException)
  //   const ex = response as DOMException
  //   expect(ex.name).toBe("TimeoutError")
  // })

  test("Test with read timeout", async () => {
    let jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia-timeout`, timeout: 500 })
    let response = await jolokia.request({ type: "version" })
        .catch(error => error)
    expect(response).toBeInstanceOf(DOMException)
    const ex = response as DOMException
    expect(ex.name).toBe("TimeoutError")

    jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia-timeout`, timeout: 2000 })
    response = await jolokia.request({ type: "version" })
    expect(response).toBeInstanceOf(Array<Response>)
    expect(response[0].value.agent).toBe("2.1.0")
  })

  test("Jolokia version with bad JSON response", async () => {
    expect.assertions(2)
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia-bad-json` })
    try {
      await jolokia.request({ type: "version" }, { dataType: "json" })
    } catch (error) {
      expect((error as Error).name).toBe("SyntaxError")
    }
    const caughtInPromise = await jolokia.request({ type: "version" }, { dataType: "json" })
        .catch(error => error)
    expect((caughtInPromise as Error).name).toBe("SyntaxError")
  })

  test("Jolokia version with HTML response and forced \"text\" response", async () => {
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia-not-json` })
    const response = await jolokia.request({ type: "version" }, { dataType: "json" }) as string
    expect(response).toContain("!doctype")
  })

  test("Jolokia version with bad JSON response, but \"text\" type", async () => {
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia-bad-json` })
    const response = await jolokia.request({ type: "version" }, { dataType: "text" }) as string
    expect(response).toContain("!doctype")
  })

  test("HTTP 500 with successful Jolokia response", async () => {
    expect.assertions(1)
    const jolokia = new Jolokia({url: `http://localhost:${port}/jolokia-introspection`})
    return jolokia.request({type: "version"}, {
      headers: {
        "J-Return-Code": "500"
      }
    })
      .then(v => {
        expect(v).toBe("Error: 500")
      })
      .catch(e => {
        console.info(e)
      })
  })

  test("HTTP 500 and Fetch response", async () => {
    expect.assertions(2)
    const jolokia = new Jolokia({url: `http://localhost:${port}/jolokia-introspection`})
    return jolokia.request({type: "version"}, {
      headers: {
        "J-Return-Code": "542"
      },
      resolve: "response"
    })
      .then(async v => {
        expect((v as Response).status).toBe(542)
        const text = await (v as Response).text()
        expect(text).toBe("Error: 542")
      })
  })

  test("Fetch error", async () => {
    expect.assertions(2)
    const jolokia = new Jolokia({url: `http://127.240.240.240:8080/jolokia`})
    return jolokia.request({type: "version"})
      .catch(ex => {
        expect(ex.name).toBe("TypeError")
        expect(ex.cause.code).toBe("ECONNREFUSED")
      })
  })

})

describe("Jolokia Fetch API tests", () => {

  test("Jolokia GET version with \"json\" type", async () => {
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia` })
    const response = await jolokia.request({ type: "version" }, { dataType: "json" }) as JolokiaResponse[]
    expect(typeof response).toBe("object")
    expect(response).toBeInstanceOf(Array<Response>)
    expect(response.length).toBe(1)
    expect(((response[0] as JolokiaSuccessResponse).value as { [ key: string ]: unknown })["agent"]).toBe("2.1.0")
  })

  test("Jolokia POST version with \"json\" type", async () => {
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia` })
    const response = await jolokia.request({ type: "version" }, { method: "post", dataType: "json" }) as JolokiaResponse[]
    expect(((response[0] as JolokiaSuccessResponse).value as { [ key: string ]: unknown })["agent"]).toBe("2.1.0")
  })

  test("Jolokia version with \"text\" type", async () => {
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia` })
    const response = await jolokia.request({ type: "version" }, { dataType: "text" }) as string
    expect(typeof response).toBe("string")
    expect(response).toContain("2.1.0")
  })

})

describe("Jolokia callback tests", () => {

  test("Simple success callback", async () => {
    expect.assertions(4)
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia` })
    // the promise doesn't resolve to anything useful (undefined), but awaiting for it makes the test
    // check all the assertions
    await jolokia.request({ type: "version" }, {
      method: "post",
      success: (response, index) => {
        expect(Array.isArray(response)).toBe(false)
        expect(response.status).toBe(200)
        expect(index).toBe(0)
        expect((response.value as { [ key: string ]: unknown })["agent"]).toBe("2.1.0")
      }
    })
  })

  test("Simple success callback with \"text\" type", async () => {
    expect.assertions(2)
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia` })
    await jolokia.request({ type: "version" }, {
      method: "post",
      dataType: "text",
      success: (response: string) => {
        expect(typeof response).toBe("string")
        expect(response).toContain("2.1.0")
      }
    })
  })

  test("Success callback as array with one response", async () => {
    expect.assertions(4)

    const f1Results = []
    const f2Results = []

    const f1 = (response: JolokiaSuccessResponse, index: number) => {
      expect(Array.isArray(response)).toBe(false)
      expect(typeof response).toBe("object")
      expect(index).toBe(0)
      expect((response.value as { [ key: string ]: unknown })["agent"]).toBe("2.1.0")
      f1Results.push(response)
    }
    const f2 = (response: JolokiaSuccessResponse, _index: number) => {
      f2Results.push(response)
      expect(true).toBeFalsy()
    }
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia` })
    await jolokia.request({ type: "version" }, {
      method: "post",
      success: [ f1, f2 ]
    })
  })

  test("Success callback as array with 5 responses", async () => {
    expect.assertions(12)

    const f1Results: string[] = []
    const f2Results: string[] = []

    const f1 = (response: JolokiaSuccessResponse, index: number) => {
      expect(Array.isArray(response)).toBe(false)
      f1Results.push(response.value + ":" + index)
    }
    const f2 = (response: JolokiaSuccessResponse, index: number) => {
      expect(Array.isArray(response)).toBe(false)
      f2Results.push(response.value + ":" + index)
    }
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia` })

    const requests: VersionRequest[] = []
    for (let i = 0; i < 5; i++) {
      requests.push({
        // unknown type is returning "Hello" in test/app-jolokia.ts
        type: "hello" as "version"
      })
    }
    await jolokia.request(requests, {
      method: "post",
      success: [ f1, f2 ]
    })

    expect(f1Results.length).toBe(3)
    expect(f2Results.length).toBe(2)
    expect(f1Results[0]).toBe("Hello:0")
    expect(f1Results[1]).toBe("Hello:2")
    expect(f1Results[2]).toBe("Hello:4")
    expect(f2Results[0]).toBe("Hello:1")
    expect(f2Results[1]).toBe("Hello:3")
  })

  test("Success callback as array with 5 responses without requests", async () => {
    expect.assertions(17)

    const f1Results: string[] = []
    const f2Results: string[] = []

    const f1 = (response: JolokiaSuccessResponse, index: number) => {
      expect(Array.isArray(response)).toBe(false)
      expect(response.request).toBeUndefined()
      f1Results.push(response.value + ":" + index)
    }
    const f2 = (response: JolokiaSuccessResponse, index: number) => {
      expect(Array.isArray(response)).toBe(false)
      expect(response.request).toBeUndefined()
      f2Results.push(response.value + ":" + index)
    }
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia`, includeRequest: false })

    const requests: VersionRequest[] = []
    for (let i = 0; i < 5; i++) {
      requests.push({
        // unknown type is returning "Hello" in test/app-jolokia.ts
        type: "hello" as "version"
      })
    }
    await jolokia.request(requests, {
      method: "post",
      success: [ f1, f2 ]
    })

    expect(f1Results.length).toBe(3)
    expect(f2Results.length).toBe(2)
    expect(f1Results[0]).toBe("Hello:0")
    expect(f1Results[1]).toBe("Hello:2")
    expect(f1Results[2]).toBe("Hello:4")
    expect(f2Results[0]).toBe("Hello:1")
    expect(f2Results[1]).toBe("Hello:3")
  })

  test("Simple error callback", async () => {
    expect.assertions(5)
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia` })
    // it's not hard to trick TypeScript
    await jolokia.request({ type: "version2" as "version" }, {
      method: "post",
      success: (_response, _index) => {},
      error: (response, index) => {
        expect(Array.isArray(response)).toBe(false)
        expect(response.status).toBe(500)
        expect(index).toBe(0)
        expect(response.error_type).toBe("java.lang.UnsupportedOperationException")
        expect(response.error).toContain("No type with name 'version2' exists")
      }
    })
  })

  test("No success callback", async () => {
    const originalConsole = globalThis.console
    const messages = []
    globalThis.console = {
      ...originalConsole,
      warn: function(...args) {
        messages.push(args)
      }
    }
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia` })
    await jolokia.request({ type: "version" }, {
      method: "post",
      success: []
    })
    expect(messages.length).toBe(1)
    globalThis.console = originalConsole
  })

  test("Ignored success callback", async () => {
    expect.assertions(2)
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia` })
    const result: boolean[] = []
    await jolokia.request({ type: "version" }, {
      method: "post",
      success: "ignore"
    }).then(v => {
      result.push(typeof v === "undefined")
    }).catch(() => {
      result.push(false)
    })
    expect(result.length).toBe(1)
    expect(result[0]).toBe(true)
  })

})

describe("Jolokia job registration tests", () => {

  test("Register one job with one request, called 3 times", async () => {
    expect.assertions(2 * 3 + 2)
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia`, fetchInterval: 100 })
    const responses: string[] = []
    await new Promise((resolve, _reject) => {
      let jobId: number = -1
      const cb = (response: JolokiaSuccessResponse, jid: number, index: number) => {
        responses.push((response.value as { [ key: string ]: unknown })["agent"] as string)
        expect(index).toBe(0)
        expect(jid).toBe(jobId)
        if (responses.length === 3) {
          jolokia.stop()
          resolve(true)
        }
      }
      jobId = jolokia.register({ success: cb, error: () => {} }, { type: "version" })
      jolokia.start()
    })
    expect(responses.length).toBe(3)
    expect(responses).toEqual([ "2.1.0", "2.1.0", "2.1.0" ])
  })

})

describe("Jolokia notification tests", () => {

  test("Register a client with simple notification listener", async () => {
    expect.assertions(2 * 3 + 4)
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia-notifications`, fetchInterval: 100 })
    let threeTimes = false
    const handle = await jolokia.addNotificationListener({
      mode: "pull",
      mbean: "org.jolokia:name=Hello",
      handback: "42",
      callback: (result: NotificationPullValue) => {
        expect(result.handle!).toBe(handle.id)
        expect(result.handback).toBe("42")

        if (result.notifications.length == 3) {
          threeTimes = true
          jolokia.stop()
        }
      }
    })
    expect(jolokia.jobs().length).toBe(1)

    await new Promise((resolve, _reject) => {
      jolokia.start()
      const id = setInterval(() => {
        if (threeTimes) {
          resolve(true)
          clearInterval(id)
        }
      }, 100)
    })
    let success = await jolokia.removeNotificationListener(handle)
    expect(success).toBe(true)
    success = await jolokia.unregisterNotificationClient()
    expect(success).toBe(true)
    expect(jolokia.jobs().length).toBe(0)
  })

})
