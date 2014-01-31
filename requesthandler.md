# RequestHandler User Story

## JmxRequest becomes JolokiaRequest

`JmxRequest` must be generalized to be usable in more generic environments. Currently every `JmxRequest` contains the following information:

* `RequestType` (ok)
* `ProcessingParameters` (ok)
* `ValueFaultHandler` (ok)
* `HttpMethod` (ok)
* path parts as list (ok)
* `ProxyTargetConfig` (**not ok**)

The very specific ProxyTargetConfig must be generalized (into something more generic) so that this sort of *extra* information might be valuable for other request dispatchers also.

## Dispatching on a realm

Many requests types hold an object name for specifying the target (read, exec, list, notify), as possible input parameters (search) or encoded in the return value (list, map keys). Indeed, it's the base naming scheme used nearly everywhere. It wouldn't be wise to change this, but to extend the concept. The idea is to introduce a 'realm' in front of the domain part of objectname with an unique separator. A good separator seems to be **`@`** since it seems not to be used currently in the wild and is valid characted according to the spec. 

`RequestHandler` can the easily dispatch on the realm part which should be directly available as a property on a parsed `JolokiaRequest`

Examples for object names with realms:

* Zookeeper:
  - `zk@zookeeper_domain:...` (don't know anythng about Zookeeper yet ;-)
* Spring:
   - `spring@:id=beanId`
   - `spring@:class=org.jolokia.JolokiaService,qualifier=request`
   - (anybody has a good idea what to use for the domain name for a spring dispatcher ?)
* JSR-160 Proxy
   - `proxy@java.lang:type=Memory`
   - 'think it would be good to have a way to distinguish proxied MBeans from local MBeans via this separation
* JMX
   - `jmx@java.lang:type=Threading`
   - JMX is the *default* realm for which the prefix can be skipped (e.g. `java.lang:type=Threading` above)
* Other Ideas
   - JNDI
   - SNMP (??)
   
BTW, is "realm" a good name anyway ? I would prefer "namespace", but this will be used in JMX 2.0, so its probably not so a good choice.

## Merged results

There are two kind of requests: One, which target an MBean (or entity) with a direct MBean name/pattern, so that a *single* `RequestHandler` only can serve the request. Request of this kind are `read`, `write`, `exec`, `notif` (for registering).

But there are also request types which should span multiple `RequestHandlers`. I.e. the `list` command should be able to list over all realms and a merged result should be returned (it should be possible via a request parameter to restrict to a single list, too). The same is true for `search` when not restricting to a single realm. The same is true for pattern read requests.

An interesting question for `list` is also, how the result should be returned in a way that is could be still backwards compatible. A flat completely merged map ? With realms as top-level ?

I think a flat list is fine here, see below.

## Backwards compatibility

For backwards compatibility, request with an objectname selecting a specific MBean are no problem wr backwards compatibility. For list requests a flat list suits better, the same for search results and read requests. However, new application using only the new model might find it better to have an extra level to order the data structures. But for the moment, I think it is ok to let the client parse the names.

It is important that without realm the JMX handler is depicted, and the names are also returned without realm prefix (for search and list).

Dr. Ansari


