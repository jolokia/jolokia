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
import jolokiaRouter from "./app-jolokia.js"
import jolokiaNotificationsRouter from "./app-jolokia-notifications.js"
import jolokiaSimpleRouter from "./app-jolokia-simple.js"

const app = express()
app.use(express.json({ type: "*/json" }))

// main Jolokia endpoint which simulates full hawtio/hawtio server
app.use("/jolokia", jolokiaRouter)
app.use("/jolokia-notifications", jolokiaNotificationsRouter)
app.use("/jolokia-simple", jolokiaSimpleRouter)

// a Jolokia endpoint which can be used to test read timeouts
app.use("/jolokia-timeout", (_req, res) => {
  setTimeout(() => {
    res.status(200).json({
      request: { type: "version" },
      value: {
        agent: "2.1.0",
        protocol: "7.3"
      }
    })
  }, 800)
})

// a Jolokia endpoint that doesn't return proper JSON
app.use("/jolokia-bad-json", (_req, res) => {
  res.status(200)
  res.set("Content-Type", "text/json")
  res.send("<!doctype html><html lang='en'></html>")
})

// a Jolokia endpoint that doesn't pretend it returns a JSON
app.use("/jolokia-not-json", (_req, res) => {
  res.status(200)
  res.set("Content-Type", "text/html")
  res.send("<!doctype html><html lang='en'></html>")
})

// a Jolokia endpoint that returns values depending on some incoming HTTP headers
app.use("/jolokia-introspection", (req, res) => {
  const rcv = req.get("J-Return-Code")
  if (rcv) {
    const rc = parseInt(rcv)
    res.status(rc).end("Error: " + rcv)
    return
  }
  res.status(200).end()
})

export default app
