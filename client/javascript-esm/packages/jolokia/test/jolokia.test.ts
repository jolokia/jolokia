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

import { afterAll, beforeAll, describe, expect, it, test } from "@jest/globals"
import request from "supertest"

import http from "node:http"
import Jolokia from "../src/jolokia.js"
import app from "./app.js"

const port = 3000
let server: http.Server

beforeAll(() => {
  server = http.createServer({}, app).listen(port, () => {
    console.info(`Listening at http://localhost:${port}`)
  })
})

afterAll(() => {
  server.close()
})

describe("Jolokia Tests", () => {

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

    expect(Object.keys(expected).length).toBe(12)

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

    expect(expected["CLIENT_VERSION"]).toBe("string")
  })

})

describe("Jolokia HTTP tests", () => {

  it("Test express.js config", async() => {
    return request(server)
        .get("/jolokia/version")
        .expect(200)
        .then((response) => {
          expect(response.body.value.agent).toBe("2.1.0")
        })
  })

  test("Jolokia GET version", async () => {
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia` })
    const response = await jolokia.request({ type: "version" })
    expect(JSON.parse(response).value.agent).toBe("2.1.0")
  })

})
