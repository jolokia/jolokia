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
import {
  JMXNotification,
  JolokiaSuccessResponse, NotificationAddResponseValue, NotificationBackendConfig, NotificationPullValue,
  NotificationRegisterResponseValue,
  PullNotificationClientConfig
} from "../src/jolokia-types.js"

const jolokiaRouter = express.Router()

jolokiaRouter.all("*", (_req, res, next) => {
  res.set("Content-Type", "application/json")
  next()
})

let clientId = 1000
let handleId = 3000
const clients: Record<string, Record<string, unknown>> = {}

const notification_1000_3000: JMXNotification[] = []

jolokiaRouter.post("/*", (req, res) => {
  let body = req.body

  if (Array.isArray(body)) {
    // bulk request - for notification pulling. We're expecting only one
    const req = body[0]
    if (req.type === "exec" && req.mbean === "org.jolokia:name=PullNotificationsGateway") {
      const args = req.arguments as string[]
      if ((clients[args[0]].handles as Record<string, Record<string, string>>)[args[1]].mbean === "org.jolokia:name=Hello") {
        // add one more
        notification_1000_3000.push({
          type: "x",
          message: "hello",
          sequenceNumber: 0,
          timeStamp: 0,
          userData: null
        } as JMXNotification)
        res.status(200).json({
          status: 200,
          request: body,
          value: {
            notifications: notification_1000_3000,
            handle: args[1],
            handback: (clients[args[0]].handles as Record<string, Record<string, string>>)[args[1]].handback
          } as NotificationPullValue
        })
        return
      }
    }
  } else {
    if (!body || !body.type) {
      body = { type: "?" }
    }
  }

  switch (body.type) {
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
    case "exec": {
      break
    }
    case "notification": {
      res.status(200)
      switch (body.command) {
        case "register": {
          // client registration
          clients[clientId] = {}
          res.json({
            status: 200,
            request: body,
            value: {
              id: clientId.toString(),
              backend: {
                "pull": {
                  maxEntries: 100,
                  store: "org.jolokia:name=PullNotificationsGateway"
                } as PullNotificationClientConfig
              } as Record<string, NotificationBackendConfig>
            } as NotificationRegisterResponseValue
          } as JolokiaSuccessResponse)
          clientId++
          break
        }
        case "unregister": {
          // client unregistration
          delete clients[body.client]
          res.json({
            status: 200,
            request: body,
            value: null
          })
          break
        }
        case "add": {
          const client = clients[body.client]
          client.handles = {
            [handleId]: {
              mbean: body.mbean,
              handback: body.handback
            }
          }
          res.json({
            status: 200,
            request: body,
            value: handleId.toString() as NotificationAddResponseValue
          })
          handleId++
          break
        }
        case "remove": {
          // listener removal
          const client = clients[body.client]
          const handle = body.handle
          delete (client.handles as Record<string, unknown>)[handle]
          res.json({
            status: 200,
            request: body,
            value: null
          })
          break
        }
      }
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
