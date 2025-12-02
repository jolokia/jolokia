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

$(document).ready(function () {

    QUnit.module("Poller");

    QUnit.test("Simple registered request", assert => {
        let done = assert.async();
        let counter1 = 1,
            counter2 = 1;
        let j4p = new Jolokia("/jolokia");

        j4p.register(function () {
            counter1++;
        }, { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used" });
        j4p.register(function () {
            counter2++;
        }, { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "max" });

        assert.equal(j4p.jobs().length, 2, "Two jobs registered");

        assert.ok(!j4p.isRunning(), "Poller should not be running");
        j4p.start(100);
        assert.ok(j4p.isRunning(), "Poller should be running");
        setTimeout(function () {
            j4p.stop();
            assert.ok(!j4p.isRunning(), "Poller should be stopped");
            assert.equal(counter1, 4, "Request1 should have been called 4 times (including the initial call)");
            assert.equal(counter2, 4, "Request2 should have been called 4 times (including the initial call)");
            done();
        }, 280);
    });

    QUnit.test("Starting and stopping", assert => {
        let done = assert.async();
        let j4p = new Jolokia("/jolokia");
        let counter = 1;

        j4p.register(function () {
                counter++;
            }, { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used" },
            { type: "SEARCH", mbean: "java.lang:type=*" });
        j4p.start(100);
        setTimeout(function () {
            j4p.stop();
            setTimeout(function () {
                assert.equal(counter, 5, "Request should have been called 5 times")
                assert.ok(!j4p.isRunning(), "Poller should be stopped");
                done();
            }, 300);
        }, 350);
    });

    QUnit.test("Registering- and Deregistering", assert => {
        let done = assert.async();
        let j4p = new Jolokia("/jolokia");
        let counter1 = 1,
            counter2 = 1;
        let id1 = j4p.register(function () {
            counter1++;
        }, { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used" });
        let id2 = j4p.register(function () {
            counter2++;
        }, { type: "EXEC", mbean: "java.lang:type=Memory", operation: "gc" });
        j4p.start(300);
        assert.equal(j4p.jobs().length, 2, "2 jobs registered");
        setTimeout(function () {
            assert.equal(counter1, 4, "Req1 should be called 2 times");
            assert.equal(counter2, 4, "Req2 should be called 2 times");
            j4p.unregister(id1);
            assert.equal(j4p.jobs().length, 1, "1 job remaining");
            setTimeout(function () {
                assert.equal(counter1, 4, "Req1 stays at 2 times since it was unregistered");
                assert.equal(counter2, 6, "Req2 should continue to be requested, now for 4 times");
                j4p.unregister(id2);
                assert.equal(j4p.jobs().length, 0, "No job remaining");
                // Handles should stay stable, so the previous unregister of id1 should not change
                // the meaning of id2 (see http://jolokia.963608.n3.nabble.com/Possible-bug-in-the-scheduler-tp4023893.html
                // for details)
                setTimeout(function () {
                    j4p.stop();
                    assert.equal(counter1, 4, "Req1 stays at 4 times since it was unregistered");
                    assert.equal(counter2, 6, "Req2 stays at 6 times since it was unregistered");
                    done();
                }, 300);
            }, 650);
        }, 750)
    });

    QUnit.test("Multiple requests", assert => {
        let done = assert.async();
        let j4p = new Jolokia("/jolokia");
        let counter = 1;
        j4p.register(function (resp1, resp2, resp3, resp4) {
                assert.equal(resp1.status, 200);
                assert.equal(resp2.status, 200);
                assert.ok(resp1.value > 0);
                assert.ok(resp2.value > 0);
                assert.equal(resp1.request.attribute, "HeapMemoryUsage");
                assert.equal(resp2.request.attribute, "ThreadCount");
                assert.equal(resp3.status, 404);
                assert.ok(!resp4);
                counter++
            }, { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used" },
            { type: "READ", mbean: "java.lang:type=Threading", attribute: "ThreadCount" },
            { type: "READ", mbean: "bla.blu:type=foo", attribute: "blubber" });
        j4p.start(200);
        setTimeout(function () {
            j4p.stop();
            assert.equal(counter, 4, "Req should be called 4 times");
            done();
        }, 500);
    })

    QUnit.test("Config merging", assert => {
        let done = assert.async();
        let j4p = new Jolokia("/jolokia");
        j4p.register({
                callback: function (resp1, resp2) {
                    assert.ok(!resp1.error_value);
                    assert.ok(resp1.stacktrace);
                    assert.ok(resp2.error_value);
                    assert.ok(!resp2.stackTrace);
                },
                config: {
                    serializeException: true
                }
            },
            { type: "READ", mbean: "bla.blu:type=foo", attribute: "blubber", config: { serializeException: false } },
            { type: "READ", mbean: "bla.blu:type=foo", attribute: "blubber", config: { includeStackTrace: false } }
        );
        j4p.start(200);
        setTimeout(function () {
            j4p.stop();
            done();
        }, 300);
    });

    QUnit.test("OnlyIfModified test - callback", assert => {
        let done = assert.async();
        let j4p = new Jolokia("/jolokia");
        let counter = {
            1: 0,
            3: 0
        };
        j4p.register({
                callback: function () {
                    counter[arguments.length]++;
                },
                onlyIfModified: true
            },
            { type: "LIST", config: { maxDepth: 2 } },
            { type: "LIST", config: { maxDepth: 1 } },
            { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used" }
        );
        j4p.start(200);
        setTimeout(function () {
            j4p.stop();
            assert.equal(counter[3], 1);
            assert.equal(counter[1], 2);
            done();
        }, 500);
    });

    QUnit.test("OnlyIfModified test - success and error ", assert => {
        let done = assert.async();
        let j4p = new Jolokia("/jolokia");
        let counter = 0;
        j4p.register({
                success: function () {
                    counter++;
                },
                error: function () {
                    counter++;
                },
                onlyIfModified: true
            },
            { type: "LIST", config: { maxDepth: 2 } }
        );
        j4p.start(200);
        setTimeout(function () {
            j4p.stop();
            // Should have been called only once
            assert.equal(counter, 1);
            done();
        }, 600);
    });

    QUnit.test("Multiple requests with success/error callbacks", assert => {
        let done = assert.async();
        let j4p = new Jolokia("/jolokia");
        let counterS = 1,
            counterE = 1;
        j4p.register({
                success: function () {
                    counterS++;
                },
                error: function (resp) {
                    counterE++;
                    assert.equal(resp.status, 404);
                }
            },
            { type: "READ", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used" },
            { type: "READ", mbean: "java.lang:type=Threading", attribute: "ThreadCount" },
            { type: "READ", mbean: "bla.blu:type=foo", attribute: "blubber" });
        j4p.start(200);
        setTimeout(function () {
            j4p.stop();
            assert.equal(counterS, 7, "Req should be called 7 times successfully");
            assert.equal(counterE, 4, "One error request, twice");
            done();
        }, 500);
    });

});
