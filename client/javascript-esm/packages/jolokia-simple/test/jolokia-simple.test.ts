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

import http from "node:http"
import Jolokia from "../src/jolokia-simple.js"
import app from "../../jolokia/test/app.js"
import { IJolokiaSimple } from "../src/jolokia-simple-types.js"
import { ListResponseValue, VersionResponseValue } from "jolokia.js"

const port = 3000
let server: http.Server

beforeAll(() => {
  server = http.createServer({}, app).listen(port)
})

afterAll(() => {
  server.closeAllConnections()
  server.close()
})

describe("Jolokia Simple interface tests", () => {

  test("Jolokia prototype merging", () => {
    const jolokia1 = new Jolokia({ url: "http://localhost" }) as IJolokiaSimple
    expect(typeof jolokia1).toBe("object")
    const jolokia2 = Jolokia({ url: "http://localhost" })
    expect(typeof jolokia2).toBe("object")

    // methods from "jolokia.js"
    expect(typeof jolokia1.request).toBe("function")
    expect(typeof jolokia1.isError).toBe("function")
    expect(typeof jolokia1.escape).toBe("function")
    // methods from "jolokia-simple.js"
    expect(typeof jolokia1.getAttribute).toBe("function")
    expect(typeof jolokia1.setAttribute).toBe("function")
    expect(typeof jolokia1.execute).toBe("function")
  })

})

describe("Jolokia simple API", () => {

  test("Simple read value", async () => {
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia-simple` }) as IJolokiaSimple
    let v = await jolokia.getAttribute("java.lang:type=Runtime")
    expect(typeof v).toBe("object")
    expect(typeof (v as Record<string, unknown>)["Name"]).toBe("string")
    expect((v as Record<string, unknown>)["Name"]).toBe("15699@everfree.forest")

    v = await jolokia.getAttribute("java.lang:type=Runtime", "Name")
    expect(typeof v).toBe("string")
    expect(v).toBe("15699@everfree.forest")
  })

  test("Simple write value", async () => {
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia-simple` }) as IJolokiaSimple
    let v = await jolokia.setAttribute("org.jolokia:name=Shrub", "Value", 42)
    expect(v).toBe(null)

    v = await jolokia.setAttribute("org.jolokia:name=Shrub", "Value", 43)
    expect(v).toBe(42)

    v = await jolokia.setAttribute("org.jolokia:name=Shrub", "Value", null)
    expect(v).toBe(43)
  })

  test("Simple execution", async () => {
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia-simple` }) as IJolokiaSimple
    const v = await jolokia.execute("org.jolokia:name=Shrub", "cat", {}, "red", "hot", "chili", "peppers")
    expect(v).toBe("red hot chili peppers")
  })

  test("Simple search", async () => {
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia-simple` }) as IJolokiaSimple
    const v = await jolokia.search("*")
    expect(Array.isArray(v)).toBe(true)
    expect((v as string[]).length).toBe(2)
    expect((v as string[])[0]).toBe("java.lang:type=Runtime")
  })

  test("Simple version check", async () => {
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia-simple` }) as IJolokiaSimple
    const v = await jolokia.version()
    expect((v as VersionResponseValue).protocol).toBe("8.0")
  })

  test("Simple list", async () => {
    const jolokia = new Jolokia({ url: `http://localhost:${port}/jolokia-simple` }) as IJolokiaSimple
    const v = await jolokia.list()
    expect(Object.keys(v as ListResponseValue)[0]).toBe("JMImplementation")
  })

})
