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

import { nodeResolve } from "@rollup/plugin-node-resolve"
import typescript from "@rollup/plugin-typescript"
import terser from "@rollup/plugin-terser"

const defaultSimpleOutput = {
  name: "JolokiaSimple",
  indent: false,
  extend: true,
  format: "umd",
  sourcemap: false,
  globals: {
    "jolokia.js": "Jolokia"
  }
}

const configJolokiaSimple = {
  input: "src/jolokia-simple.ts",
  plugins: [ nodeResolve(), typescript({ include: [ "src/*" ] }) ],
  external: [ "jolokia.js" ],
  output: [
    {
      ...defaultSimpleOutput,
      file: `dist/jolokia-simple.cjs`,
    },
    {
      ...defaultSimpleOutput,
      file: `dist/jolokia-simple.min.cjs`,
      plugins: [ terser() ]
    },
    {
      ...defaultSimpleOutput,
      file: `dist/jolokia-simple.mjs`,
      format: "esm"
    }
  ]
}

export default [ configJolokiaSimple ]
