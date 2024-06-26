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

import type { JestConfigWithTsJest } from "ts-jest"

const config: JestConfigWithTsJest = {
  // https://kulshekhar.github.io/ts-jest/docs/getting-started/presets/
  preset: "ts-jest/presets/default-esm",
  // https://www.typescriptlang.org/docs/handbook/modules/reference.html#file-extension-substitution
  //  - TypeScript always wants to resolve internally to a file that can provide type information
  //  - the runtime or bundler can use the same path to resolve to a file that provides a JavaScript implementation
  // https://github.com/kulshekhar/ts-jest/issues/1057#issuecomment-1441733977
  moduleNameMapper: {
    "^(\\.\\.?\\/.*)\\.js$": "$1"
  }
}

export default config
