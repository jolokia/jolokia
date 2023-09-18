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
                request: {
                    type: "read",
                    mbean: "java.lang:type=Memory",
                    attribute: "HeapMemoryUsage",
                    path: "used",
                },
                status: 200,
                value: 12345,
                timestamp: 1694682372,
            })
        }));

        const jolokia = new Jolokia("/jolokia");
        const response = jolokia.request({
            type: "read",
            mbean: "java.lang:type=Memory",
            attribute: "HeapMemoryUsage",
            path: "used",
        });

        expect(response.status).toEqual(200);
        expect(response.value).toEqual(12345);
    });

    test("basic request: exec", () => {
        $.ajax = jest.fn(() => ({
            status: 200,
            responseText: JSON.stringify({
                request: {
                    type: "exec",
                    mbean: "java.lang:type=Memory",
                    operation: "gc()",
                },
                status: 200,
                value: null,
                timestamp: 1694682372,
            })
        }));

        const jolokia = new Jolokia("/jolokia");
        const response = jolokia.request({
            type: "exec",
            mbean: "java.lang:type=Memory",
            operation: "gc()",
        });

        expect(response.status).toEqual(200);
        expect(response.value).toBeNull();
    });
});
