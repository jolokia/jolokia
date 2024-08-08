/*
 * Copyright 2009-2013 Roland Huss
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

$(document).ready(function () {

    let j4p = new Jolokia("/jolokia");

    // Single requests
    const configs = [
        ["GET-Requests", { method: "GET" }],
        ["POST-Requests", { method: "POST" }]
    ];
    $.each(configs, function (idx, c) {
        singleRequestTest(c[0], c[1]);
        errorTest(c[0], c[1]);
    });

    // Bulk requests
    bulkRequestTests();

    // Synchronous requests
    syncTests();

    // Check for HTTP method detection
    httpMethodDetectionTests();

    // Advanced READ tests
    advancedReadTests();

    // Testing the list functionality
    listTest();

    // ==========================================================================================

    // Single Request Tests
    function singleRequestTest(label, extraParams) {
        QUnit.module("Async: " + label);
        QUnit.test("Simple Memory Read Request", assert => {
            const done = assert.async();
            j4p.request(
                { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage" },
                $.extend(extraParams,
                    {
                        success: function (response) {
                            assert.equal(response.request.type, "read", "Type must be read");
                            assert.ok(response.value != null, "Value must be set: " + JSON.stringify(response.value));
                            assert.ok(response.value.used != null, "Composite data returned: ");
                            done();
                        },
                        error: function (resp) {
                            console.error("error: " + resp);
                            done();
                        }
                    })
            );
        });
        QUnit.test("Simple Memory Read Request with path", assert => {
            const done = assert.async();
            j4p.request(
                { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used" },
                $.extend(extraParams,
                    {
                        success: function (response) {
                            assert.equal(response.request.type, "read", "Type must be read");
                            assert.ok(response.value != null, "Value must be set: " + response.value);
                            assert.ok(response.value.used == null, "No complex structure");
                            done();
                        }
                    })
            );
        });
        QUnit.test("Exec Request: garbage collection", assert => {
            const done = assert.async();
            j4p.request(
                { type: "exec", mbean: "java.lang:type=Memory", operation: "gc" },
                $.extend(extraParams,
                    {
                        success: function (response) {
                            assert.equal(response.request.type, "exec", "Type must be exec");
                            assert.ok(response.value == null, "Return Value is null ");
                            done();
                        }
                    })
            );
        });
        QUnit.test("Version Request", assert => {
            const done = assert.async();
            j4p.request(
                { type: "version" },
                $.extend(extraParams,
                    {
                        success: function (response) {
                            assert.equal(response.request.type, "version", "Type must be version");
                            assert.ok(response.value.protocol >= 6.0, "Protocol must be greater or equal 4.0");
                            assert.ok(minorVersionsMatch(j4p.CLIENT_VERSION, response.value["agent"]), "Agent version check");
                            done();
                        }
                    })
            );
        });
    }

    function errorTest(label, extraParams) {
        QUnit.module("Error-Test: " + label);
        QUnit.test("Unknown MBean for 'read'", assert => {
            const done = assert.async();
            j4p.request(
                { type: "read", mbean: "java.lang:name=bullshit" },
                $.extend(extraParams,
                    {
                        error: function (response) {
                            assert.equal(response.status, 404, "Instance not (404 status code)");
                            assert.equal(response["error_type"], "javax.management.InstanceNotFoundException", "javax.management.InstanceNotFoundException");
                            assert.ok(response["error_value"] != null, "Serialized exception should be contained");
                            assert.ok(response.error != null, "Error description");
                            assert.ok(response.stacktrace != null, "Stacktrace");
                            done();
                        },
                        serializeException: true
                    })
            );
        });
        QUnit.test("Invalid URL", assert => {
            const done = assert.async();
            // jQuery 3.x requires jsonp errors to be caught at window.onerror.
            // https://github.com/jquery/jquery/issues/5034
            if (extraParams.jsonp) {
                window.onerror = function (e) {
                    console.log('jsonp error:', e)
                }
            }
            new Jolokia({ url: "bla" }).request(
                { type: "version" },
                $.extend(extraParams,
                    {
                        success: function (response) {
                        },
                    })
            ).catch(e => {
                // e is https://developer.mozilla.org/en-US/docs/Web/API/Response
                assert.equal(e.statusText, "Not Found", "Fetch exception");
                assert.equal(e.status, 404, "Not found HTTP code")
                done();
            });
        });
    }

    // ==============================================================================

    function bulkRequestTests() {
        QUnit.module("Bulk requests");
        QUnit.test("Simple Bulk Request", assert => {
            const done = assert.async(2);
            j4p.request(
                [
                    { type: "version" },
                    { type: "read", mbean: "java.lang:type=Threading", attribute: "ThreadCount" }
                ],
                {
                    success: function (response, idx) {
                        switch (idx) {
                            case 0:
                                assert.ok(minorVersionsMatch(j4p.CLIENT_VERSION, response.value["agent"]), "Version request");
                                break;
                            case 1:
                                assert.equal(response.request.type, "read", "Read request");
                                break;
                        }
                        done();
                    }
                }
            );
        });
        QUnit.test("Bulk Request with dispatched functions", assert => {
            const done = assert.async(2);
            j4p.request(
                [
                    { type: "version" },
                    { type: "read", mbean: "java.lang:type=Runtime", attribute: "Name" }
                ],
                {
                    success: [
                        function (response) {
                            assert.ok(minorVersionsMatch(j4p.CLIENT_VERSION, response.value["agent"]), "Version request");
                            done();
                        },
                        function (response) {
                            assert.equal(response.request.type, "read", "Read request");
                            done();
                        }
                    ]
                }
            );
        });
        QUnit.test("Bulk Request with dispatched functions and error", assert => {
            const done = assert.async(2);
            j4p.request(
                [
                    { type: "version" },
                    { type: "exec", mbean: "Blub:type=Bla" },
                    { type: "read", mbean: "java.lang:type=Runtime", attribute: "Name" }
                ],
                {
                    success: [
                        function (response, idx) {
                            assert.equal(idx, 0, "Success 1st request");
                            assert.ok(minorVersionsMatch(j4p.CLIENT_VERSION, response.value["agent"]), "Version request");
                            done();
                        },
                        function () {
                            throw new Error("Must not be called");
                        },
                        function (response, idx) {
                            assert.equal(idx, 2, "Success 3rd request");
                            assert.equal(response.request.type, "read", "Read request");
                            done();
                        }
                    ],
                    error: function (response, idx) {
                        assert.equal(idx, 1, "Error for 2nd request");
                    }
                }
            );
        });
    }

    // ==============================================================================

    function syncTests() {
        QUnit.module("Sync Tests");
        QUnit.test("Simple Memory Read Request", async assert => {
            const done = assert.async();
            const responses = await j4p.request({ type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage" });
            const resp = responses[0]
            assert.equal(resp.request.type, "read", "Type must be read");
            assert.ok(resp.value != null, "Value must be set: " + JSON.stringify(resp.value));
            assert.ok(resp.value.used != null, "Composite data returned: ");
            done()

        });
        QUnit.test("Simple request with Jolokia Error", async assert => {
            const done = assert.async();
            let resp = await j4p.request({ type: "READ", mbean: "bla" });
            assert.equal(resp[0]["error_type"], "java.lang.IllegalArgumentException", "Illegal Argument");
            done()
        });
        QUnit.test("Simple request with HTTP Error", async assert => {
            const done = assert.async();
            let resp = await new Jolokia("/bla").request(
                { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage" },
                {
                    ajaxError: function (xhr) {
                        assert.equal(xhr.status, 404);
                    }
                }
            ).catch(_r => null);
            assert.ok(resp == null, "No response should be returned");
            done()
        });
        QUnit.test("GET Write test with newlines", async assert => {
            const done = assert.async();
            let value = "Max\nMorlock";
            let resp = await j4p.request({
                type: "WRITE",
                mbean: "jolokia.it:type=attribute",
                attribute: "Name",
                value: value
            }, { method: "GET" });
            assert.equal(resp[0].status, 200);
            resp = await j4p.request({ type: "READ", mbean: "jolokia.it:type=attribute", attribute: "Name" });
            assert.equal(resp[0].value, value);
            done()
        });
        QUnit.test("GET Exec test with newlines", async assert => {
            const done = assert.async();
            let args = [["Max\nMorlock", "dummy"], "extra"];
            let resp = await j4p.request({
                type: "EXEC",
                mbean: "jolokia.it:type=operation",
                operation: "arrayArguments",
                arguments: args
            }, { method: "GET" });
            assert.equal(resp[0].status, 200);
            assert.equal(resp[0].value, "Max\nMorlock");
            done()
        });
    }

    // ==========================================================================================

    // Invalid HTTP types for given requests
    function httpMethodDetectionTests() {
        QUnit.module("HTTP method detection");
        QUnit.test("Sanity HTTP Method detection checks", assert => {
            assert.rejects(j4p.request(
                [
                    { type: "version" },
                    { type: "list" }
                ],
                { method: "get" }
            ), "No GET for bulk requests");
            assert.rejects(j4p.request(
                {
                    type: "read",
                    mbean: "java.lang:type=Memory",
                    attribute: ["HeapMemoryUsage", "NonHeapMemoryUsage"]
                },
                { method: "get" }
            ), "No GET for read with array arguments");
            assert.rejects(j4p.request(
                {
                    type: "read", mbean: "java.lang:type=Memory", attribute: "NonHeapMemoryUsage",
                    target: { url: "service:jmx:hsp://njw810/default/jboss?shared=true" }
                },
                { method: "get" }
            ), "No 'target' config with GET requests");
        });
    }

    // ==========================================================================================================
    function advancedReadTests() {
        QUnit.module("Advanced READ");
        QUnit.test("Multiple Attribute Read Request", assert => {
            const done = assert.async();
            j4p.request(
                { type: "READ", mbean: "java.lang:type=Memory", attribute: ["HeapMemoryUsage", "NonHeapMemoryUsage"] },
                {
                    success: function (response) {
                        assert.equal(response.request.type, "read", "Type must be read");
                        assert.ok(response.value != null, "Value must be set: " + JSON.stringify(response.value));
                        assert.ok(response.value["HeapMemoryUsage"], "HeapMemoryUsage set");
                        assert.ok(response.value["HeapMemoryUsage"].used, "HeapMemoryUsage.used set");
                        assert.ok(response.value["NonHeapMemoryUsage"], "NonHeapMemoryUsage set");
                        done();
                    }
                }
            );
        });
        QUnit.test("All Attribute Read Request", assert => {
            const done = assert.async();
            j4p.request(
                { type: "READ", mbean: "java.lang:type=Memory" },
                {
                    success: function (response) {
                        assert.equal(response.request.type, "read", "Type must be read");
                        assert.ok(response.value != null, "Value must be set: " + JSON.stringify(response.value));
                        assert.ok($.isPlainObject(response.value), "Hash returned");
                        assert.ok(response.value["HeapMemoryUsage"], "HeapMemoryUsage set");
                        assert.ok(response.value["HeapMemoryUsage"].used, "HeapMemoryUsage.used set");
                        assert.ok(response.value["NonHeapMemoryUsage"], "NonHeapMemoryUsage set");
                        done();
                    }
                }
            );
        });
        QUnit.test("Pattern Attribute Read Request", assert => {
            const done = assert.async();
            j4p.request(
                { type: "READ", mbean: "java.lang:type=*" },
                {
                    success: function (response) {
                        assert.equal(response.request.type, "read", "Type must be read");
                        assert.ok(response.value != null, "Value must be set: " + JSON.stringify(response.value));
                        assert.ok($.isPlainObject(response.value), "Hash returned");
                        assert.ok(response.value["java.lang:type=Memory"]);
                        assert.ok(response.value["java.lang:type=Memory"]["HeapMemoryUsage"].used);
                        done();
                    },
                    error: function () {
                        done();
                    }
                }
            );
        });
        QUnit.test("Pattern Attribute Read Request with path", assert => {
            const done = assert.async();
            j4p.request(
                { type: "READ", mbean: "java.lang:type=*", path: "*/*/used" },
                {
                    success: function (response) {
                        assert.equal(response.request.type, "read", "Type must be read");
                        assert.ok(response.value != null, "Value must be returned: " + JSON.stringify(response.value));
                        assert.ok($.isPlainObject(response.value), "Hash returned");
                        assert.ok(response.value["java.lang:type=Memory"]);
                        assert.ok(response.value["java.lang:type=Memory"]["HeapMemoryUsage"]);
                        console.log(response);
                        assert.ok(!response.value["java.lang:type=Memory"]["HeapMemoryUsage"].max);
                        done();
                    }
                }
            );
        });
        QUnit.test("Complex name with GET", assert => {
            const done = assert.async();
            j4p.request(
                { type: "READ", mbean: "jolokia.it:type=naming/,name=\"jdbc/testDB\"", attribute: "Ok" },
                {
                    success: function (response) {
                        assert.equal(response.request.type, "read", "Type must be read");
                        assert.equal(response.value, "OK");
                        done();
                    },
                    error: function (resp) {
                        throw new Error("Cannot read attribute " + JSON.stringify(resp));
                    },
                    method: "get"
                }
            );
        });
    }

    // =================================================================================

    function listTest() {
        QUnit.module("LIST");
        QUnit.test("List with maxDepth 1", assert => {
            const done = assert.async();
            j4p.request(
                { type: "LIST" },
                {
                    maxDepth: 1,
                    success: function (response) {
                        assert.equal(response.request.type, "list", "Type must be 'list'");
                        assert.ok(response.value != null, "Value must be set");
                        for (const key in response.value) {
                            assert.equal(response.value[key], 1, "List must be truncated");
                        }
                        done();
                    }
                }
            );
        });
        QUnit.test("List with maxDepth 2", assert => {
            const done = assert.async();
            j4p.request(
                { type: "LIST" },
                {
                    maxDepth: 2,
                    success: function (response) {
                        assert.equal(response.request.type, "list", "Type must be 'list'");
                        assert.ok(response.value != null, "Value must be set");
                        for (let key1 in response.value) {
                            for (let key2 in response.value[key1]) {
                                assert.equal(response.value[key1][key2], 1);
                            }
                        }
                        done();
                    }
                }
            );
        });
    }

    /*
     * Checks whether v1 is ge than v2 up to minor version.
     * 2.0.0 should be ge 2.0.1, but 2.0.9 should not be ge 2.1.0
     */
    function minorVersionsMatch(v1, v2) {
        let t1 = v1.split('.')
        let t2 = v2.split('.')
        if (t1.length >= 3 && t2.length >= 3) {
            if (t1[0] < t2[0]) {
                return false
            }
            if (t1[0] > t2[0]) {
                return true
            }
            return t1[1] >= t2[1]
        } else {
            return semverLite.gte(v1, v2)
        }
    }

});
