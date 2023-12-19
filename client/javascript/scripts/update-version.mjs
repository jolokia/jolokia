import replace from "replace";
import pkg from "../package.json" assert {type: "json"};

console.log("PACKAGE VERSION:", pkg.version);

const srcDir = "./src/main/javascript";

// jolokia.js
replace({
    regex: "this[.]CLIENT_VERSION = \".+\";\n",
    replacement: `this.CLIENT_VERSION = "${pkg.version}";\n`,
    paths: [`${srcDir}/jolokia.js`],
    recursive: false,
    silent: false,
});

// jolokia-cubism.js
replace({
    regex: "var VERSION = \".+\";\n",
    replacement: `var VERSION = "${pkg.version}";\n`,
    paths: [`${srcDir}/jolokia-cubism.js`],
    recursive: false,
    silent: false,
});
