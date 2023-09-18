const Jolokia = require("./jolokia");
require("./jolokia-simple");
const $ = require("jquery");

describe("jolokia-simple", () => {
    beforeEach(() => {
        jest.resetAllMocks();
    });

    test("getAttribute", () => {
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
        const value = jolokia.getAttribute("java.lang:type=Memory", "HeapMemoryUsage", "used");

        expect(value).toEqual(12345);
    });
});
