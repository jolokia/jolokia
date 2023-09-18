# Jolokia Javascript Client library

The Jolokia Javascript library provides a Javascript API to the to the Jolokia agent. Refer to [Reference Manual](https://jolokia.org/reference/html/clients.html#client-javascript) for more details on how to use the library.

This client provides the following three libraries:

- `jolokia.js`: Base library containing the `Jolokia` object definition which carries the `request()`
- `jolokia-simple.js`: Library containing the Jolokia simple API and which builds up on `jolokia.js`. It must be included after `jolokia.js` since it adds methods to the `Jolokia` object definition.
- `jolokia-cubism.js`: Jolokia comes with a plugin for [Cubism](https://square.github.io/cubism/) and can act as a data source.

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

### Base library

```javascript
import Jolokia from "jolokia.js";

const jolokia = new Jolokia("/jolokia");
const response = jolokia.request({
    type: "read",
    mbean: "java.lang:type=Memory",
    attribute: "HeapMemoryUsage",
    path: "used"
});
console.log("Heap Memory used:", response.value);
```

### Simple API

```javascript
import Jolokia from "jolokia.js";
import "jolokia.js/simple";

const jolokia = new Jolokia("/jolokia");
const value = jolokia.getAttribute("java.lang:type=Memory", "HeapMemoryUsage", "used");
console.log("Heap Memory used:", value);
```

### Cubism source

```javascript
import cubism from "cubism";
import "jolokia.js";
import "jolokia.js/cubism";

const context = cubism.context();
const jolokia = context.jolokia("/jolokia");
const metricMem = jolokia.metric({
    type: "read",
    mbean: "java.lang:type=Memory",
    attribute: "HeapMemoryUsage",
    path: "used"
}, "HeapMemory Usage");
...
```
