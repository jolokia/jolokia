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

import pkg from "./package.json" assert { type: "json" }
import { nodeResolve } from "@rollup/plugin-node-resolve"
import typescript from "@rollup/plugin-typescript"
import terser from "@rollup/plugin-terser"

const defaultD3Output = {
  name: "JolokiaD3",
  indent: false,
  extend: true,
  format: "umd",
  globals: {
    "jolokia.js": "Jolokia",
    d3: "d3"
  }
}

const configJolokiaD3 = {
  input: "src/jolokia-d3.ts",
  plugins: [ nodeResolve(), typescript() ],
  external: [ "jolokia.js", "d3" ],
  output: [
    {
      ...defaultD3Output,
      file: `dist/jolokia-d3-${pkg.version}.js`,
    },
    {
      ...defaultD3Output,
      file: `dist/jolokia-d3-${pkg.version}.min.js`,
      plugins: [ terser() ]
    },
    {
      ...defaultD3Output,
      file: `dist/jolokia-d3.mjs`,
      format: "esm"
    }
  ],
  onwarn(message, warn) {
    // required for d3
    if (message.code === "CIRCULAR_DEPENDENCY") return;
    warn(message);
  }
}

export default [ configJolokiaD3 ]
