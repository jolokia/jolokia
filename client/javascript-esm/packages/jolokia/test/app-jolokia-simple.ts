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

let valueToWrite: unknown = null

jolokiaRouter.post("/*", (req, res) => {
  let body = req.body

  if (!body || !body.type) {
    body = { type: "?" }
  }

  switch (body.type) {
    case "read": {
      if (body.mbean === "java.lang:type=Runtime") {
        if (!body.attribute) {
          res.status(200).json({
            status: 200,
            request: body,
            value: {
              "Name": "15699@everfree.forest"
            }
          })
        } else if (body.attribute === "Name") {
          res.status(200).json({
            status: 200,
            request: body,
            value: "15699@everfree.forest"
          })
        }
      }
      break
    }
    case "write": {
      if (body.mbean === "org.jolokia:name=Shrub" && body.attribute === "Value") {
        res.status(200).json({
          status: 200,
          request: body,
          value: valueToWrite
        })
        valueToWrite = body.value
      }
      break
    }
    case "exec": {
      if (body.mbean === "org.jolokia:name=Shrub" && body.operation === "cat") {
        res.status(200).json({
          status: 200,
          request: body,
          value: (body.arguments as string[]).join(" ")
        })
      }
      break
    }
    case "search": {
      res.status(200).json({
        status: 200,
        request: body,
        value: [
          "java.lang:type=Runtime",
          "java.lang:type=Threading"
        ]
      })
      break
    }
    case "list": {
      res.status(200).json({
        status: 200,
        request: body,
        value: {
          "JMImplementation": {
            "type=MBeanServerDelegate": {
              "attr": {
                "ImplementationName": {
                  "rw": false,
                  "type": "java.lang.String",
                  "desc": "The JMX implementation name (the name of this product)"
                }
              }
            }
          }
        }
      })
      break
    }
    case "version": {
      res.status(200).json({
        status: 200,
        request: body,
        value: {
          agent: "2.1.0",
          protocol: "7.3"
        }
      })
      break
    }
    default: {
      res.status(200).json({
        status: 500,
        request: body,
        error_type: "java.lang.UnsupportedOperationException",
        error: "java.lang.UnsupportedOperationException : No type with name '" + body.type + "' exists"
      })
      break
    }
  }

})

export default jolokiaRouter
