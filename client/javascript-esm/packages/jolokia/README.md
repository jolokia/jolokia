# Jolokia JavaScript Client library (ES Module)

<a href="https://jolokia.org"><img src="../../src/site/resources/images/jolokia_logo.png" /></a>

The Jolokia JavaScript library provides a JavaScript API to the to the [Jolokia agent](https://jolokia.org/). Refer to [Reference Manual](https://jolokia.org/reference/html/manual/clients.html#client-javascript) for more details on how to use the library.

## Installation

NPM:

```console
npm i jolokia.js
```

Yarn:

```console
yarn add jolokia.js
```

## Usage

```javascript
import Jolokia from "jolokia.js";

const jolokia = new Jolokia("/jolokia")
const response = await jolokia.request({
    type: "read",
    mbean: "java.lang:type=Memory",
    attribute: "HeapMemoryUsage",
    path: "used"
})
console.log("Heap Memory used:", response.value)
```
