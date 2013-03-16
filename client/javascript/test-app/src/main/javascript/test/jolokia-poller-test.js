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

// Poller tests

$(document).ready(function() {

    module("Poller");
    asyncTest("Simple registered request",function() {
        var counter1 = 1,
            counter2 = 1;
        var j4p = new Jolokia("/jolokia");

        j4p.register(function(resp) {
            counter1++;
        },{ type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used"});
        j4p.register(function(resp) {
            counter2++;
        },{ type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "max"});

        equals(j4p.jobs().length,2,"Two jobs registered");

        ok(!j4p.isRunning(),"Poller should not be running");
        j4p.start(100);
        ok(j4p.isRunning(),"Poller should be running");
        setTimeout(function() {
            j4p.stop();
            ok(!j4p.isRunning(),"Poller should be stopped");
            equals(counter1,3,"Request1 should have been called 3 times");
            equals(counter2,3,"Request2 should have been called 3 times");
            start();
        },280);
    });

    asyncTest("Starting and stopping",function() {
        var j4p = new Jolokia("/jolokia");
        var counter = 1;

        j4p.register(function(resp) {
            counter++;
            },{ type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used"},
            { type: "SEARCH", mbean: "java.lang:type=*"});
        j4p.start(100);
        setTimeout(function() {
            j4p.stop();
            setTimeout(function() {
                equals(counter,4,"Request should have been called 4 times")
                ok(!j4p.isRunning(),"Poller should be stopped");
                start();
            },300);
        },350);

    });

    asyncTest("Registering- and Deregistering",function() {
        var j4p = new Jolokia("/jolokia");
        var counter1 = 1,
            counter2 = 1;
        var id1 = j4p.register(function(resp) {
            counter1++;
        },{ type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used"});
        var id2 = j4p.register(function(resp) {
            counter2++;
        },{ type: "EXEC", mbean: "java.lang:type=Memory", operation: "gc"});
        j4p.start(300);
        equals(j4p.jobs().length,2,"2 jobs registered");
        setTimeout(function() {
            equals(counter1,3,"Req1 should be called 2 times");
            equals(counter2,3,"Req2 should be called 2 times");
            j4p.unregister(id1);
            equals(j4p.jobs().length,1,"1 job remaining");
            setTimeout(function() {
                equals(counter1,3,"Req1 stays at 2 times since it was unregistered");
                equals(counter2,5,"Req2 should continue to be requested, now for 4 times");
                j4p.unregister(id2);
                equals(j4p.jobs().length,0,"No job remaining");
                // Handles should stay stable, so the previous unregister of id1 should not change
                // the meaining of id2 (see http://jolokia.963608.n3.nabble.com/Possible-bug-in-the-scheduler-tp4023893.html
                // for details)
                setTimeout(function() {
                    j4p.stop();
                    equals(counter1,3,"Req1 stays at 3 times since it was unregistered");
                    equals(counter2,5,"Req2 stays at 4 times since it was unregistered");
                    start();
                },300);
            },650);
        },750)
    });

    asyncTest("Multiple requests",function() {
        var j4p = new Jolokia("/jolokia");
        var counter = 1;
        j4p.register(function(resp1,resp2,resp3,resp4) {
                equals(resp1.status,200);
                equals(resp2.status,200);
                ok(resp1.value > 0);
                ok(resp2.value > 0);
                equals(resp1.request.attribute,"HeapMemoryUsage");
                equals(resp2.request.attribute,"ThreadCount");
                equals(resp3.status,404);
                ok(!resp4);
                counter++
            },{ type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used"},
            { type: "READ", mbean: "java.lang:type=Threading", attribute: "ThreadCount"},
            { type: "READ", mbean: "bla.blu:type=foo", attribute: "blubber"});
        j4p.start(200);
        setTimeout(function() {
            j4p.stop();
            equals(counter,3,"Req should be called 3 times");
            start();
        },500);
    })

    asyncTest("Config merging",function() {
        var j4p = new Jolokia("/jolokia");
        j4p.register({
                callback: function(resp1,resp2) {
                    ok(!resp1.error_value);
                    ok(resp1.stacktrace);
                    ok(resp2.error_value);
                    ok(!resp2.stackTrace);
                },
                config: {
                    serializeException: true
                }
            },
            { type: "READ", mbean: "bla.blu:type=foo", attribute: "blubber", config: { serializeException: false}},
            { type: "READ", mbean: "bla.blu:type=foo", attribute: "blubber", config: { includeStackTrace: false}}
        );
        j4p.start(200);
        setTimeout(function() {
            j4p.stop();
            start();
        },300);
    });

    asyncTest("OnlyIfModified test - callback",function() {
        var j4p = new Jolokia("/jolokia");
        var counter = {
            1: 0,
            3: 0
        };
        j4p.register({
                callback: function() {
                    counter[arguments.length]++;
                },
                onlyIfModified: true
            },
            { type: "LIST", config: { maxDepth: 2}},
            { type: "LIST", config: { maxDepth: 1}},
            { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used"}
        );
        j4p.start(200);
        setTimeout(function() {
            j4p.stop();
            equals(counter[3],1);
            equals(counter[1],1);
            start();
        },500);
    });

    asyncTest("OnlyIfModified test - success and error ",function() {
        var j4p = new Jolokia("/jolokia");
        var counter = 0;
        j4p.register({
                success: function(resp1) {
                    counter++;
                },
                error: function(resp1) {
                    counter++;
                },
                onlyIfModified: true
            },
            { type: "LIST", config: { maxDepth: 2}}
        );
        j4p.start(200);
        setTimeout(function() {
            j4p.stop();
            // Should have been called only once
            equals(counter,1);
            start();
        },600);
    });

    asyncTest("Multiple requests with success/error callbacks",function() {
        var j4p = new Jolokia("/jolokia");
        var counterS = 1,
            counterE = 1;
        j4p.register({
                success: function(resp) {
                    counterS++;
                },
                error: function(resp) {
                    counterE++;
                    equals(resp.status,404);
                }
            },
            { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used"},
            { type: "READ", mbean: "java.lang:type=Threading", attribute: "ThreadCount"},
            { type: "READ", mbean: "bla.blu:type=foo", attribute: "blubber"});
        j4p.start(200);
        setTimeout(function() {
            j4p.stop();
            equals(counterS,5,"Req should be called 4 times successfully");
            equals(counterE,3,"One error request, twice");
            start();
        },500);
    });

});
