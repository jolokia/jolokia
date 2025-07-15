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

import express from "express"

const jolokiaRouter = express.Router()

jolokiaRouter.all(/.*/, (_req, res, next) => {
  res.set("Content-Type", "application/json")
  next()
})

jolokiaRouter.get(/\/version/, (_req, res) => {
  res.status(200).json({
    status: 200,
    timestamp: Date.now(),
    request: { type: "version" },
    value: {
      agent: "2.1.0",
      protocol: "8.0"
    }
  })
})

jolokiaRouter.post(/\/*/, (req, res) => {
  let body = req.body

  if (Array.isArray(body)) {
    // bulk request
    const response: { [ key: string ]: unknown }[] = []
    body.forEach((v) => {
      if (v.type === "version") {
        response.push({
          status: 200,
          timestamp: Date.now(),
          request: v,
          value: {
            agent: "2.1.0",
            protocol: "8.0"
          }
        })
      } else {
        response.push({
          status: 200,
          timestamp: Date.now(),
          request: v,
          value: "Hello"
        })
      }
    })
    if (req.query["includeRequest"] === "false") {
      response.forEach(r => {
        delete r["request"]
      })
    }
    res.status(200).json(response)
    return
  }

  if (!body || !body.type) {
    body = { type: "?" }
  }

  switch (body.type) {
    case "version": {
      res.status(200).json({
        status: 200,
        timestamp: Date.now(),
        request: { type: "version" },
        value: {
          agent: "2.1.0",
          protocol: "8.0"
        }
      })
      break
    }
    default: {
      res.status(200).json({
        status: 500,
        timestamp: Date.now(),
        request: body,
        error_type: "java.lang.UnsupportedOperationException",
        error: "java.lang.UnsupportedOperationException : No type with name '" + body.type + "' exists"
      })
      break
    }
  }

})

export default jolokiaRouter
