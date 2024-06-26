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

import js from "@eslint/js"
// import jsdoc from "eslint-plugin-jsdoc"
import tseslint from "typescript-eslint"
import workspaces from "eslint-plugin-workspaces"
import globals from "globals"

export default [
  {
    ignores: [
      "**/rollup.config.js",
      "**/dist/*"
    ]
  },
  js.configs["recommended"],
  // jsdoc.configs["flat/recommended"],
  ...tseslint.configs["recommended"],
  {
    ...workspaces.configs["recommended"],
    files: [ "**/*.ts", "**/*.js" ],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      globals: globals.browser
    },
    plugins: {
      workspaces
    },
    rules: {
      "semi": [ "error", "never" ],
      "console": "off",
      // "no-unused-vars": [ "error", { "args": "after-used" } ]
      "no-unused-vars": "off",
      "@typescript-eslint/no-unused-vars": [ "error", {
        "argsIgnorePattern": "^_",
      }]
    }
  }
]
