const Jolokia = require("./jolokia");
const $ = require("jquery");

describe("jolokia", () => {
    beforeEach(() => {
        jest.resetAllMocks();
    });

    test("basic request: read", () => {
        $.ajax = jest.fn(() => ({
            status: 200,
            responseText: JSON.stringify({
                request: {type: "read", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used"},
                status: 200,
                value: 12345,
                timestamp: 1694682372,
            })
        }));

        const jolokia = new Jolokia("/jolokia");
        const response = jolokia.request({type: "read", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used"});

        expect(response.status).toEqual(200);
        expect(response.value).toEqual(12345);
    });

    test("basic request: exec", () => {
        $.ajax = jest.fn(() => ({
            status: 200,
            responseText: JSON.stringify({
                request: {type: "exec", mbean: "java.lang:type=Memory", operation: "gc()"},
                status: 200,
                value: null,
                timestamp: 1694682372,
            })
        }));

        const jolokia = new Jolokia("/jolokia");
        const response = jolokia.request({type: "exec", mbean: "java.lang:type=Memory", operation: "gc()"});

        expect(response.status).toEqual(200);
        expect(response.value).toBeNull();
    });

    test("basic request with dataType=text", () => {
        $.ajax = jest.fn(() => ({
            status: 200,
            responseText: `{
                "request": {"type": "read", "mbean": "java.lang:type=Memory", "attribute": "HeapMemoryUsage", "path": "used"},
                "status": 200,
                "value": 900719925474099123,
                "timestamp": 1694682372
            }`
        }));

        const request = {type: "read", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used"};
        const jolokia = new Jolokia("/jolokia");
        const response1 = jolokia.request(request);
        expect(response1.status).toEqual(200);
        expect(String(response1.value)).not.toEqual("900719925474099123");
        const response2 = jolokia.request(request, {dataType: 'text'});
        expect(typeof response2).toBe("string");
        expect(response2).toContain("900719925474099123");
    });

    test("bulk request (sync)", () => {
        $.ajax = jest.fn(() => ({
            status: 200,
            responseText: JSON.stringify([
                {
                    request: {type: "read", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used"},
                    status: 200,
                    value: 12345,
                    timestamp: 1694682372,
                },
                {
                    request: {type: "exec", mbean: "java.lang:type=Memory", operation: "gc()"},
                    status: 200,
                    value: null,
                    timestamp: 1694682372,
                }
            ])
        }));

        const jolokia = new Jolokia("/jolokia");
        const responses = jolokia.request([
            {type: "read", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used"},
            {type: "exec", mbean: "java.lang:type=Memory", operation: "gc()"},
        ]);

        expect(Array.isArray(responses)).toBe(true);
        responses.forEach(response => {
            expect(response.status).toEqual(200);
            if (response.request.type === "read") {
                expect(response.value).toEqual(12345);
            } else {
                expect(response.value).toBeNull();
            }
        });
    });

    test("bulk request (async)", () => {
        $.ajax = jest.fn(ajaxParams => {
            ajaxParams.success([
                {
                    request: {type: "read", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used"},
                    status: 200,
                    value: 12345,
                    timestamp: 1694682372,
                },
                {
                    request: {type: "exec", mbean: "java.lang:type=Memory", operation: "gc()"},
                    status: 200,
                    value: null,
                    timestamp: 1694682372,
                }
            ]);
        });

        const success1 = jest.fn();
        const success2 = jest.fn();

        const jolokia = new Jolokia("/jolokia");
        const response = jolokia.request(
            [
                {type: "read", mbean: "java.lang:type=Memory", attribute: "HeapMemoryUsage", path: "used"},
                {type: "exec", mbean: "java.lang:type=Memory", operation: "gc()"},
            ],
            {
                success: [
                    response => {
                        expect(response.status).toEqual(200);
                        expect(response.request.type).toEqual("read");
                        expect(response.value).toEqual(12345);
                        success1();
                    },
                    response => {
                        expect(response.status).toEqual(200);
                        expect(response.request.type).toEqual("exec");
                        expect(response.value).toBeNull();
                        success2();
                    },
                ]
            }
        );

        expect(response).toBeNull();
        expect(success1).toHaveBeenCalled();
        expect(success2).toHaveBeenCalled();
    });

    test("assignObject", () => {
        expect(Jolokia.assignObject({}, {a: 1, b: 2, c: 3})).toEqual({a: 1, b: 2, c: 3});
        expect(Jolokia.assignObject({a: 1, b: 2}, {b: 3, c: 5})).toEqual({a: 1, b: 3, c: 5});
        expect(Jolokia.assignObject({a: 1}, {a: 2, b: 3}, {b: 4, c: 6})).toEqual({a: 2, b: 4, c: 6});
        expect(Jolokia.assignObject({a: 1, b: 2, c: 3})).toEqual({a: 1, b: 2, c: 3});
        expect(() => Jolokia.assignObject(undefined, {})).toThrow();
        expect(() => Jolokia.assignObject(null, {})).toThrow();
    });
});
