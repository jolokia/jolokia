const cubism = require("cubism");
require("./jolokia");
require("./jolokia-cubism");
const $ = require("jquery");

describe("jolokia-cubism", () => {
    beforeEach(() => {
        jest.resetAllMocks();
    });

    test("basic metric", () => {
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

        const context = cubism.context();
        const jolokia = context.jolokia("/jolokia");
        const metricMem = jolokia.metric({
            type: "read",
            mbean: "java.lang:type=Memory",
            attribute: "HeapMemoryUsage",
            path: "used"
        }, "HeapMemory Usage");

        expect(metricMem).not.toBeNull();
    });
});
