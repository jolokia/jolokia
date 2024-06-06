/*
 * Copyright 2009-2023 Roland Huss
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

import { describe, expect, test } from "@jest/globals"
import Jolokia from "../src/jolokia.js"

describe("Jolokia", () => {

  test("Jolokia instance creation", () => {
    const jolokia1 = new Jolokia({ url: "http://localhost" })
    expect(typeof jolokia1).toBe("object")
    const jolokia2 = Jolokia({ url: "http://localhost" })
    expect(typeof jolokia2).toBe("object")
  })

  test("Jolokia client version", () => {
    const j = new Jolokia({ url: "http://localhost" })
    expect(j.CLIENT_VERSION).toMatch(/^2/)
    try {
      j.CLIENT_VERSION = "3.0.0"
      // noinspection ExceptionCaughtLocallyJS
      throw "Can't set CLIENT_VERSION"
    } catch (e) {
      expect(e instanceof TypeError).toBe(true)
    }
  })

  test("Jolokia members", () => {
    const j = new Jolokia({ url: "http://localhost" })
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Enumerability_and_ownership_of_properties#traversing_object_properties
    // check only enumerable properties in the object and prototype (for..in). Object.keys() doesn't include
    // properties from the prototype
    const expected = {}
    for (const k in j) {
      expected[k] = typeof j[k]
    }

    expect(Object.keys(expected).length).toBe(11)

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

    expect(expected["CLIENT_VERSION"]).toBe("string")
  })

})
