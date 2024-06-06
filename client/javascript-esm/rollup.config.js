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

import { fileURLToPath } from 'node:url'

import pkg from "./package.json" assert { type: "json" }
import { nodeResolve } from "@rollup/plugin-node-resolve"
import terser from "@rollup/plugin-terser"

const jolokiaModuleURL = fileURLToPath(new URL("src/jolokia.js", import.meta.url))

const defaultOutput = {
  name: "Jolokia",
  indent: false,
  extend: true,
  format: "umd"
}
const defaultCbsmOutput = {
  ...defaultOutput,
  name: "JolokiaCbsm",
  globals: {
    [jolokiaModuleURL]: "Jolokia",
    d3: "d3"
  }
}

const configJolokia = {
  input: "src/jolokia.js",
  plugins: nodeResolve(),
  output: [
    {
      ...defaultOutput,
      file: `dist/jolokia-${pkg.version}.js`,
    },
    {
      ...defaultOutput,
      file: `dist/jolokia-${pkg.version}.min.js`,
      plugins: [ terser() ]
    },
    {
      ...defaultOutput,
      file: `dist/jolokia-${pkg.version}.mjs`,
      format: "esm"
    }
  ]
}
const configJolokiaCbsm = {
  input: "src/jolokia-cbsm.js",
  plugins: nodeResolve(),
  external: [ jolokiaModuleURL, "d3" ],
  output: [
    {
      ...defaultCbsmOutput,
      file: `dist/jolokia-cbsm-${pkg.version}.js`,
    },
    {
      ...defaultCbsmOutput,
      file: `dist/jolokia-cbsm-${pkg.version}.min.js`,
      plugins: [ terser() ]
    },
    {
      ...defaultCbsmOutput,
      file: `dist/jolokia-cbsm-${pkg.version}.mjs`,
      format: "esm"
    }
  ],
  onwarn(message, warn) {
    // required for d3
    if (message.code === "CIRCULAR_DEPENDENCY") return;
    warn(message);
  }
}

export default [
  configJolokia,
  configJolokiaCbsm
]
