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
 import {
   JolokiaErrorResponse,
   JolokiaSuccessResponse,
 } from "../src/jolokia.js"
 import app from "./app.js"
import { isResponseErrorType, isResponseSuccessType, isVersionResponseType, ParseResult, responseParse } from "../src/jolokia-utils.js"

 const port = 3001
 let server: http.Server

 beforeAll(() => {
   server = http.createServer({}, app).listen(port)
 })

 afterAll(() => {
   server.closeAllConnections()
   server.close()
 })

 describe("Jolokia Utils tests", () => {

    test("Test Version Response", async () => {
      return request(server)
        .get("/jolokia/version")
        .expect(200)
        .then((response) => {
          expect(isResponseSuccessType(response.body)).toBe(true)
          expect(isVersionResponseType(response.body.value)).toBe(true)
        })
    })

    test("Test Not a Version Response", async () => {
      return request(server)
        .post("/jolokia/hello")
        .send({type: 'list'})
        .expect(200)
        .then((response) => {
          expect(isResponseSuccessType(response.body)).toBe(true)
          expect(isVersionResponseType(response.body.value)).toBe(false)
        })
    })

    test("Test Error Response", async () => {
      return request(server)
        .post("/jolokia/hello")
        .expect(200)
        .then((response) => {
          expect(isResponseSuccessType(response.body)).toBe(false)
          expect(isResponseErrorType(response.body)).toBe(true)
        })
    })

    test("Test Response Parsing", async () => {
      const response = await request(server)
        .post("/jolokia/hello")
        .send({type: 'list'})

      expect(isResponseSuccessType(response.body)).toBe(true)

      const parsedResponse: ParseResult<JolokiaSuccessResponse | JolokiaErrorResponse> =
        await responseParse(response.body)

      expect(parsedResponse.hasError).toBe(false)
      if (! parsedResponse.hasError) {
        expect(isResponseSuccessType(parsedResponse.parsed))
      }
   })
 })
