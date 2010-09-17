![Jolokia - JMX on Capsaicin][1]

Jolokia is a fresh way for accessing JMX MBeans remotely. It is
different to JSR-160 connectors in so far as it is an agent based
approach which uses JSON over HTTP for its communication in a
REST-stylish way.

Multiple agents are provided for different environments:

* __WAR Agent__ for deployment as web application in a JEE Server 
* __OSGi Agent__ for deployment in an [OSGi][2] container. This agent
  is packaged as a bundle and comes in two flavors (minimal,
  all-in-one) 
* __Mule Agent__ for usage within a [Mule][3] ESB
* __JVM JDK6 Agent__ which can be used with any Oracle/Sun JVM,
  Version 6 

Features
--------

The agent approach as several advantages:

* __Firewall friendly__

  Since all communication is over HTTP, proxying through firewalls
  becomes mostly a none-issue (in contrast to RMI communication, which
  is the default mode for JSR-160)

* __Polyglot__

  No Java installation is required on the client
  side. E.g. [Jmx4Perl][4] provides a rich Perl library and Perl based
  tools for accessing the agents.

* __Simple Setup__

  The Setup is done by a simple agent deployment. In contrast,
  exporting JMX via JSR-160 can be remarkable complicated (see these
  blog posts for setting up [Weblogic][5] and [JBoss][6] for native
  remote JMX exposure setup)

Additionally, the agents provide extra features not available with
JSR-160 connectors:

* __Bulk requests__

  In contrast to JSR-160 remoting, Jolokia can process many JMX
  requests with a single roundtrip. A single HTTP POST request puts
  those requests in its JSON payload which gets dispatched on the
  agent side. These bulk requests can increase performance drastically
  especially for monitoring solutions. The Nagios plugin
  [check_jmx4perl][8] uses bulk requests for its multi check feature.
  
* __Fine grained security__

  In addition to standard HTTP security (SSL, HTTP-Authentication)
  Jolokia supports a custom policy with fine grained restrictions
  based on multiple properties like the client's IP address or subnet,
  the MBean names and their attributes and operations. The policy is
  defined in an XML format with support for allow/deny sections and
  wildcards.

* __Proxy mode__

  Jolokia can operate in an agentless mode where the only requirement
  on the target platform is the standard JSR-160 export of its
  MBeanServer. A proxy listens on the front side for Jolokia requests
  via JSON/HTTP and propagates these to the target server through
  remote JSR-160 JMX calls. Bulk requests gets dispatched into
  multiple JSR-160 requests on the proxy transparently.

Resources
---------

More information on jolokia can be found at [www.jolokia.org][5]
(coming soon). 

Until the launch of the website (expected in fall 2010), you can
already use the [support forum][9] or contact me directly via GitHub. 

 [1]: http://github.com/rhuss/jolokia/raw/master/src/site/resources/images/jolokia_logo.png "Jolokia"
 [2]: http://www.osgi.org
 [3]: http://www.mulesoft.org
 [4]: http://www.jmx4perl.org
 [5]: http://www.jolokia.org
 [6]: http://labs.consol.de/blog/jmx4perl/configuring-remote-jmx-access-for-weblogic   
 [7]: http://labs.consol.de/blog/jmx4perl/jboss-remote-jmx
 [8]: http://search.cpan.org/~roland/jmx4perl/scripts/check_jmx4perl
 [9]: http://forum.jolokia.org
