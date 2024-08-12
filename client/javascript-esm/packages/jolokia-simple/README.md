# Jolokia JavaScript Simple Client library (ES Module)

<a href="https://jolokia.org"><img src="../../src/site/resources/images/jolokia_logo.png" /></a>

The Jolokia JavaScript Simple library provides a JavaScript Simple API to the to the [Jolokia agent](https://jolokia.org/). Refer to [Reference Manual](https://jolokia.org/reference/html/manual/clients.html#client-javascript) for more details on how to use the library.

## Installation

NPM:

```console
npm i @jolokia.js/simple
```

Yarn:

```console
yarn add @jolokia.js/simple
```

## Usage

```javascript
import Jolokia from "@jolokia.js/simple"

const jolokia = new Jolokia("/jolokia")
const value = await jolokia.getAttribute("java.lang:type=Memory", "HeapMemoryUsage", "used")
console.log("Heap Memory used:", value)
```
