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

    QUnit.module("Simplified requests");

    QUnit.test("getAttribute (sync)", assert => {
        let value = j4p.getAttribute("java.lang:type=Memory", "HeapMemoryUsage", "used");
        assert.ok(value > 0, "Positive used HeapMemory");
        value = j4p.getAttribute("java.lang:type=Memory", "HeapMemoryUsage");
        assert.ok(value.used, "Composite Data returned");
        value = j4p.getAttribute("java.lang:type=Memory", ["HeapMemoryUsage", "NonHeapMemoryUsage"]);
        assert.ok(value["HeapMemoryUsage"].max, "Multi attribute read, HeapMemoryUsage.max");
        assert.ok(value["NonHeapMemoryUsage"].used, "Multi attribute read, HeapMemoryUsage.used");
        value = j4p.getAttribute("java.lang:type=Memory");
        assert.ok(value["HeapMemoryUsage"].max, "All attribute read, HeapMemoryUsage.max");
        assert.ok(value["NonHeapMemoryUsage"].used, "All attribute read, HeapMemoryUsage.used");
        value = j4p.getAttribute("java.lang:type=*");
        assert.ok(value["java.lang:type=Memory"]["HeapMemoryUsage"].max, "Pattern read, java.lang:type=Memory,HeapMemoryUsage.max");
        assert.throws(function () {
            j4p.getAttribute("bla:blub=x", { method: "get" })
        }, "Error call");
    });

    QUnit.test("getAttribute with strange name (sync)", assert => {
        assert.equal(j4p.getAttribute("jolokia.it:name=\",,/,,\",type=escape", "Ok"), "OK");
    });

    QUnit.test("getAttribute (sync with error)", assert => {
        let done = assert.async();
        let value = j4p.getAttribute("bla:blub=x", {
            error: function (resp) {
                assert.equal(resp.error_type, "javax.management.InstanceNotFoundException", "Exception type");
                done();
            }
        });
        assert.ok(value == null, "Error call");
    });

    QUnit.test("getAttribute (async)", assert => {
        let done = assert.async();
        j4p.getAttribute("java.lang:type=Memory", "HeapMemoryUsage", "used", {
            success: function (val) {
                assert.ok(val > 0, "Positive HeapMemory");
                done();
            }
        });
    });

    QUnit.test("setAttribute (sync)", assert => {
        let oldValue = j4p.getAttribute("java.lang:type=Threading", "ThreadCpuTimeEnabled");
        let value = j4p.setAttribute("java.lang:type=Threading", "ThreadCpuTimeEnabled", !oldValue);
        assert.equal(oldValue, value, "Old-Value should be returned");
        value = j4p.setAttribute("java.lang:type=Threading", "ThreadCpuTimeEnabled", !value);
        assert.ok(oldValue !== value, "Alternate state");
        value = j4p.setAttribute("jolokia.it:type=attribute", "ComplexNestedValue", 23, "Blub/1/numbers/0");
        assert.equal(value, 42);
        assert.equal(j4p.getAttribute("jolokia.it:type=attribute", "ComplexNestedValue", "Blub/1/numbers/0"), 23);
        j4p.execute("jolokia.it:type=attribute", "reset");
    });

    QUnit.test("setAttribute (sync with error)", assert => {
        let done = assert.async();
        let value = j4p.setAttribute("bla:blub=x", "x", 10, {
            error: function (resp) {
                assert.equal(resp.error_type, "javax.management.InstanceNotFoundException", "Exception type");
                done();
            }
        });
        assert.ok(value == null, "Error call");
    });

    QUnit.test("setAttribute (async)", assert => {
        let done = assert.async();
        let value = j4p.setAttribute("jolokia.it:type=attribute", "ComplexNestedValue", 23, "Blub/1/numbers/0", {
            success: function (val) {
                assert.equal(val, 42, "Old value returned");
                j4p.getAttribute("jolokia.it:type=attribute", "ComplexNestedValue", "Blub/1/numbers/0", {
                    success: function (nval) {
                        assert.equal(nval, 23, "New value set");
                        j4p.execute("jolokia.it:type=attribute", "reset");
                        done();
                    }
                })
            }
        });
        assert.equal(value, null, "No return value for async operations");
    });

    QUnit.test("execute (sync)", assert => {
        let value = j4p.execute("jolokia.it:type=operation", "fetchNumber", "inc");
        assert.equal(value, 0);
        value = j4p.execute("jolokia.it:type=operation", "fetchNumber", "inc");
        assert.equal(value, 1);
        value = j4p.execute("jolokia.it:type=operation", "overloadedMethod(java.lang.String,int)", "bla", 1);
        assert.equal(value, 2);
        value = j4p.execute("jolokia.it:type=operation", "arrayArguments", "Max\nMorlock,blub", "x", { method: "POST" });
        assert.equal(value, "Max\nMorlock");
        value = j4p.execute("jolokia.it:type=operation", "arrayArguments", ["Max\nMorlock", "blub"], "x", { method: "POST" });
        assert.equal(value, "Max\nMorlock");
        value = j4p.execute("jolokia.it:type=operation", "nullArgumentCheck", null, null);
        assert.equal(value, true);
        j4p.execute("jolokia.it:type=operation", "reset");
    });

    QUnit.test("execute (sync) with escape", assert => {
        assert.equal(j4p.execute("jolokia.it:type=operation", "echo", "blub!"), "blub!");
        assert.equal(j4p.execute("jolokia.it:type=operation", "echo", "blub!!"), "blub!!");
        assert.equal(j4p.execute("jolokia.it:type=operation", "echo", "blub!/!"), "blub!/!");
        assert.equal(j4p.execute("jolokia.it:type=operation", "echo", "blub!//!"), "blub!//!");
    });

    QUnit.test("execute (async with error)", assert => {
        let done = assert.async();
        j4p.execute("jolokia.it:type=operation", "throwCheckedException", {
            error: function (resp) {
                assert.equal(resp.error_type, "java.lang.Exception");
                done();
            }
        });
    });

    QUnit.test("execute (async)", assert => {
        let done = assert.async();
        let value = j4p.execute("jolokia.it:type=operation", "nullArgumentCheck", null, null, {
            success: function (value) {
                assert.equal(value, true);
                done()
            }
        });
        assert.equal(value, null);
    });

    QUnit.test("search (sync)", assert => {
        let value = j4p.search("jolokia.it:*");
        assert.ok($.isArray(value), "Return value from search must be an array");
        assert.ok(value.length > 2, "Array must contain mbeans");
        $.each(value, function (i, val) {
            assert.ok(typeof val == "string", "MBean name must be a string");
            assert.ok(val.match(/^jolokia\.it:.*/), "MBean name must start with domain name");
        });
    });

    QUnit.test("search (no result, sync)", assert => {
        let value = j4p.search("bla:notype=*");
        assert.ok($.isArray(value), "Return value from search must be an array");
        assert.equal(value.length, 0, "List must be empty");
    });

    QUnit.test("search (sync with error)", assert => {
        let done = assert.async();
        let value = j4p.search("jolokia.it:type=*=a*", {
            error: function (resp) {
                assert.ok(resp.error != null, "Error occured");
                done();
            }, success: log
        });
        assert.equal(value, null);
    });

    QUnit.test("search (async)", assert => {
        let done = assert.async();
        j4p.search("jolokia.it:*", {
            success: function (val) {
                assert.ok($.isArray(val), "Return value from search must be an array");
                assert.ok(val.length > 2, "Array must contain mbeans");
                done();
            }
        });
    });

    QUnit.test("version (sync)", assert => {
        let value = j4p.version({ method: "post" });
        assert.ok(value.protocol >= 6, "Protocol >= 6");
        assert.ok(minorVersionsMatch(j4p.CLIENT_VERSION, value["agent"]), "Client version: " + j4p.CLIENT_VERSION);
    });

    QUnit.test("version (async)", assert => {
        let done = assert.async();
        let value = j4p.version({
            jsonp: true, success: function (val) {
                assert.ok(minorVersionsMatch(j4p.CLIENT_VERSION, val["agent"]), "Agent version " + j4p.CLIENT_VERSION);
                done();
            }
        });
        assert.equal(value, null);
    });

    QUnit.test("list (sync)", assert => {
        let value = j4p.list("java.lang/type=Memory/op");
        assert.ok(value["gc"], "Garbage collection");
        value = j4p.list(["java.lang", "type=Memory", "op"]);
        assert.ok(value["gc"], "Garbage collection (with array path)");
        assert.equal(value["gc"].args, 0);
        // value = j4p.list("jolokia.it/name=n!!a!!m!!e with !!!/!!,type=naming!//attr");
        // ok(value["Ok"], "Path with /");
        value = j4p.list(["jolokia.it", "type=naming/,name=n!a!m!e with !/!", "attr"]);
        assert.ok(value["Ok"], "Path with / (path elements)");
        assert.throws(function () {
            j4p.list("java.lang/type=Bla");
        }, "Invalid path");
        assert.throws(function () {
            j4p.list("jolokia.it/type=naming,name=n!a!m!e with !/!/attr");
        }, "Invalid path with slashes");
    });

    QUnit.test("list (sync with error)", assert => {
        let done = assert.async();
        let value = j4p.list("java.lang/type=Bla", {
            error: function (resp) {
                assert.equal(resp.error_type, "java.lang.IllegalArgumentException", "java.lang.IllegalArgumentException");
                done();
            }
        });
        assert.equal(value, null);
    });

    QUnit.test("list (async)", assert => {
        let done = assert.async();
        let value = j4p.list("java.lang/type=Memory/attr", {
            success: function (val) {
                log(val);
                assert.ok(val["HeapMemoryUsage"] != null, "HeapMemory");
                done();
            }
        });
        assert.equal(value, null);
    });

    function log(response) {
        console.log(JSON.stringify(response));
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
