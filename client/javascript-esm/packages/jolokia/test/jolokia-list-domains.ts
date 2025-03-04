
export const domains = {
  domains: {
    hawtio: {
      "type\u003dAbout": {
        attr: {
          HawtioVersion: {
            rw: false,
            type: "java.lang.String",
            desc: "Attribute exposed for management"
          }
        },
        class: "io.hawt.jmx.About",
        desc: "Information on the management interface of the MBean"
      }
    },
    jolokia: {
      "type\u003dServerHandler,agent\u003d\u003cmasked\u003e-9-7b96a65f-servlet": {
        op: {
          mBeanServersInfo: {
            args: [],
            ret: "java.lang.String",
            desc: "Operation exposed for management"
          }
        },
        class: "org.jolokia.server.core.backend.MBeanServerHandler",
        desc: "Information on the management interface of the MBean"
      }
    }
  }
}
