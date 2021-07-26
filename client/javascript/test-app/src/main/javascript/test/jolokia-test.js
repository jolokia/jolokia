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

$(document).ready(function() {

    var j4p = new Jolokia("/jolokia");

    // Single requests
    $.each([
               ["GET-Requests", { method: "GET" } ],
               ["POST-Requests", { method: "POST" }],
               ["GET-Requests with jsonp", { jsonp: true }]
           ], function(i, o) {

        singleRequestTest(o[0], o[1]);
        errorTest(o[0], o[1]);

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
        module("Async: " + label);
        asyncTest("Simple Memory Read Request", function() {
            j4p.request(
                { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage"},
                $.extend(extraParams,
                     {
                         success: function(response) {
                             equals(response.request.type, "read", "Type must be read");
                             ok(response.value != null, "Value must be set: " + JSON.stringify(response.value));
                             ok(response.value.used != null, "Composite data returned: ");
                             start();
                         },
                         error: function(resp) {
                            fail("error");
                             start();
                         }
                     })
                    );
        });
        asyncTest("Simple Memory Read Request with path", function() {
            j4p.request(
                { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used" },
                $.extend(extraParams,
                    {
                        success: function(response) {
                            equals(response.request.type, "read", "Type must be read");
                            ok(response.value != null, "Value must be set: " + response.value);
                            ok(response.value.used == null, "No complex structure");
                            start();
                        }
                    })
            );
        });
        asyncTest("Exec Request: garbage collection", function() {
            j4p.request(
            { type: "exec", mbean: "java.lang:type=Memory", operation: "gc"},
            $.extend(extraParams,
                     {
                         success: function(response) {
                             equals(response.request.type, "exec", "Type must be exec");
                             ok(response.value == null, "Return Value is null ");
                             start();
                         }
                     }));
        });
        asyncTest("Version Request", function() {
            j4p.request(
            { type: "version" },
            $.extend(extraParams,
                     {
                         success: function(response) {
                             equals(response.request.type, "version", "Type must be version");
                             ok(response.value.protocol >= 6.0, "Protocol must be greater or equals 4.0");
                             ok(response.value.agent == j4p.CLIENT_VERSION, "Agent version check");
                             start();
                         }
                     }));
        });
    }

    function errorTest(label, extraParams) {
        module("Error-Test: " + label);
        asyncTest("Unknown MBean for 'read'", function() {
            j4p.request(
            { type: "read", mbean: "java.lang:name=bullshit"},
            $.extend(extraParams,
                     {
                         error: function(response) {
                             equals(response.status, 404, "Instance not (404 status code)");
                             equals(response.error_type, "javax.management.InstanceNotFoundException", "javax.management.InstanceNotFoundException");
                             ok(response.error_value != null,"Serialized exception should be contained");
                             ok(response.error != null, "Error description");
                             ok(response.stacktrace != null, "Stacktrace");
                             start();
                         },
                         serializeException: true
                     }));
        });
        asyncTest("Invalid URL", function() {
            Jolokia({url: "bla"}).request(
            { type: "version" },
            $.extend(extraParams,
                     {
                         success: function(response) {},
                         ajaxError: function(xhr, textStatus, errorThrown) {
                             equals(textStatus, "error", "Ajax Error");
                             equals(xhr.status, 404, "Not found HTTP code")
                             start();
                         }
                     }));
        });
    }

    // ==============================================================================

    function bulkRequestTests() {
        module("Bulk requests");
        asyncTest("Simple Bulk Request", 2, function() {
            j4p.request(
                    [
                        {type: "version"},
                        {type: "read",mbean: "java.lang:type=Threading",attribute: "ThreadCount"}
                    ],
                    {
                        success: function(response, idx) {
                            switch (idx) {
                                case 0:
                                    ok(response.value.agent == j4p.CLIENT_VERSION, "Version request");
                                    break;
                                case 1:
                                    equals(response.request.type, "read", "Read request");
                                    start();
                                    break;
                            }
                        }
                    });
        });

        asyncTest("Bulk Request with dispatched functions", 2, function() {
            j4p.request(
                    [
                        {type: "version"},
                        {type: "read",mbean: "java.lang:type=Runtime",attribute: "Name"}
                    ],
                    {
                        success: [
                                 function(response, idx) {
                                     ok(response.value.agent == j4p.CLIENT_VERSION, "Version request");
                                 },
                                 function(response, idx) {
                                     equals(response.request.type, "read", "Read request");
                                     start();
                                 }]
                    });
        });

        asyncTest("Bulk Request with dispatched functions and error", 5, function() {
            j4p.request(
                    [
                        {type: "version"},
                        {type: "exec",mbean: "Blub:type=Bla"},
                        {type: "read",mbean: "java.lang:type=Runtime",attribute: "Name"}
                    ],
                    {
                        success: [
                                 function(response, idx) {
                                     equals(idx, 0, "Success 1st request");
                                     ok(response.value.agent == j4p.CLIENT_VERSION, "Version request");
                                 },
                                 function(response, idx) {
                                     throw new Error("Must not be called");
                                 },
                                 function(response, idx) {
                                     equals(idx, 2, "Success 3rd request");
                                     equals(response.request.type, "read", "Read request");
                                     start();
                                 }],
                        error: function(response, idx) {
                            equals(idx, 1, "Error for 2nd request");
                        }
                    });
        });
    }

    // ==============================================================================

    function syncTests() {
        module("Sync Tests");
        test("Simple Memory Read Request", function() {
            var resp = j4p.request({ type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage"});
            equals(resp.request.type, "read", "Type must be read");
            ok(resp.value != null, "Value must be set: " + JSON.stringify(resp.value));
            ok(resp.value.used != null, "Composite data returned: ");
        });

        test("Simple request with Jolokia Error", function() {
            var resp = j4p.request({ type: "READ", mbean: "bla"});
            equals(resp.error_type, "java.lang.IllegalArgumentException", "Illegal Argument");
        });

        test("Simple request with HTTP Error", function() {
            var resp = new Jolokia("/bla").request(
            { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage"},
            {
                ajaxError: function(xhr) {
                    equals(xhr.status,404);
                }
            });
            ok(resp == null, "No response should be returned");
        });

        test("No JSONP with sync requests", function() {
            raises(function() {
                var resp = j4p.request(
                { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage"},
                { jsonp: true });
            }, "Must throw an ERROR");
        });

        test("GET Write test with newlines", function() {
            var value = "Max\nMorlock";
            var resp = j4p.request({ type: "WRITE", mbean: "jolokia.it:type=attribute", attribute: "Name", value: value},{method: "GET"});
            equals(resp.status,200);
            resp = j4p.request({type: "READ", mbean: "jolokia.it:type=attribute", attribute: "Name"});
            equals(resp.value,value);
        });

        test("GET Exec test with newlines", function() {
            var args = [ [ "Max\nMorlock", "dummy"] ,"extra"];
            var resp = j4p.request({ type: "EXEC", mbean: "jolokia.it:type=operation", operation: "arrayArguments", arguments: args},{method: "GET"});
            equals(resp.status,200);
            equals(resp.value,"Max\nMorlock");
        });

    }

    // ==========================================================================================

    // Invalid HTTP types for given requests
    function httpMethodDetectionTests() {
        module("HTTP method detection");
        test("Sanity HTTP Method detection checks", function() {
            raises(function() {
                j4p.request(
                        [
                            {type: "version"},
                            {type: "list"}
                        ],
                        { method: "get" }
                        );
            }, "No GET for bulk requests");
            raises(function() {
                j4p.request(
                { type: "read", mbean: "java.lang:type=Memory", attribute: [ "HeapMemoryUsage", "NonHeapMemoryUsage"]},
                { method: "get" }
                        );
            }, "No GET for read with array arguments");
            raises(function() {
                j4p.request(
                { type: "version"},
                { method: "post", jsonp: true }
                        );
            }, "No POST for JSONP");
            raises(function() {
                j4p.request(
                        [
                            {type: "version"},
                            {type: "list"}
                        ],
                        { jsonp: true }
                        );
            }, "No JSONP with bulk requests");
            raises(function() {
                j4p.request(
                { type: "read", mbean: "java.lang:type=Memory", attribute: "NonHeapMemoryUsage",
                  target: { url: "service:jmx:hsp://njw810/default/jboss?shared=true" }},
                { method: "get" }
                        );
            }, "No 'target' config with GET requests");
        });
    }

    // ==========================================================================================================
    function advancedReadTests() {
        module("Advanced READ");
        asyncTest("Multiple Attribute Read Request", function() {
            j4p.request(
                { type: "READ", mbean: "java.lang:type=Memory", attribute: ["HeapMemoryUsage","NonHeapMemoryUsage"]},
                {
                    success: function(response) {
                        equals(response.request.type, "read", "Type must be read");
                        ok(response.value != null, "Value must be set: " + JSON.stringify(response.value));
                        ok(response.value.HeapMemoryUsage, "HeapMemoryUsage set");
                        ok(response.value.HeapMemoryUsage.used, "HeapMemoryUsage.used set");
                        ok(response.value.NonHeapMemoryUsage, "NonHeapMemoryUsage set");
                        start();
                    }
                });
        });

        asyncTest("All Attribute Read Request", function() {
            j4p.request(
            { type: "READ", mbean: "java.lang:type=Memory"},
            {
                success: function(response) {
                    equals(response.request.type, "read", "Type must be read");
                    ok(response.value != null, "Value must be set: " + JSON.stringify(response.value));
                    ok($.isPlainObject(response.value), "Hash returned");
                    ok(response.value.HeapMemoryUsage, "HeapMemoryUsage set");
                    ok(response.value.HeapMemoryUsage.used, "HeapMemoryUsage.used set");
                    ok(response.value.NonHeapMemoryUsage, "NonHeapMemoryUsage set");
                    start();
                }
            });
        });
        asyncTest("Pattern Attribute Read Request", function() {
            j4p.request(
            { type: "READ", mbean: "java.lang:type=*"},
            {
                success: function(response) {
                    equals(response.request.type, "read", "Type must be read");
                    ok(response.value != null, "Value must be set: " + JSON.stringify(response.value));
                    ok($.isPlainObject(response.value), "Hash returned");
                    ok(response.value["java.lang:type=Memory"]);
                    ok(response.value["java.lang:type=Memory"].HeapMemoryUsage.used);
                    start();
                },
                 error: function(response, idx) {
                     start();
                 }
            });
        });
        asyncTest("Pattern Attribute Read Request with path", function() {
            j4p.request(
            { type: "READ", mbean: "java.lang:type=*", path: "*/*/used"},
            {
                success: function(response) {
                    equals(response.request.type, "read", "Type must be read");
                    ok(response.value != null, "Value must be returned: " + JSON.stringify(response.value));
                    ok($.isPlainObject(response.value), "Hash returned");
                    ok(response.value["java.lang:type=Memory"]);
                    ok(response.value["java.lang:type=Memory"].HeapMemoryUsage);
                    console.log(response);
                    ok(!response.value["java.lang:type=Memory"].HeapMemoryUsage.max);
                    start();
                }
            });
        });

        asyncTest("Complex name with GET", function() {
            j4p.request(
                { type: "READ", mbean: "jolokia.it:type=naming/,name=\"jdbc/testDB\"", attribute: "Ok"},
                {
                    success: function(response) {
                        equals(response.request.type, "read", "Type must be read");
                        equals(response.value,"OK");
                        start();
                    },
                    error: function(resp) {
                        throw new Error("Cannot read attribute " + JSON.stringify(resp));
                    },
                    method: "get"
                });
        });

    }

    // =================================================================================

    function listTest() {
        module("LIST");
        asyncTest("List with maxDepth 1", function() {
            j4p.request(
                { type: "LIST" },
                {
                    maxDepth: 1,
                    success: function(response) {
                        equals(response.request.type, "list", "Type must be 'list'");
                        ok(response.value != null, "Value must be set");
                        for (var key in response.value) {
                            equals(response.value[key],1,"List must be truncated");
                        }
                        start();
                    }
                });
        });

        asyncTest("List with maxDepth 2", function() {
            j4p.request(
                { type: "LIST" },
                {
                    maxDepth: 2,
                    success: function(response) {
                        equals(response.request.type, "list", "Type must be 'list'");
                        ok(response.value != null, "Value must be set");
                        for (var key1 in response.value) {
                            for (var key2 in response.value[key1]) {
                                equals(response.value[key1][key2],1);
                            }
                        }
                        //log(response);
                        start();
                    }
                });
        });

    }


    function log(response) {
        console.log(JSON.stringify(response));
    }
});
