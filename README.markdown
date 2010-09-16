![Jolokia - JMX on Capsaicin][1]

Jolokia is a fresh way for accessing JMX instrumented MBeans
remotely. It is different to JSR-160 connectors as it is an agent
based approach which uses JSON over HTTP for its communications in a
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
  Since all communication is over HTTP, proxying
  through firewalls becomes a none-issue (in contrast to RMI
  communication, which is the default mode for JSR-160) 
* __Polyglot__ 
  No Java installation is required on the client
  side. E.g. [Jmx4Perl][4] provides a rich Perl library and Perl based
  tools for accessing the agents.
* __Simple Setup__ 
  Setup is done by a simple agent
  deployment. Exporting JMX via JSR-160 can be remarkable complicated
  (see these blog posts for setting up [Weblogic][5] and [JBoss][6]
  for native remote JMX exposure setup)

Additionally, the agents provide extra features not available with
JSR-160 connectors:

* __Bulk requests__
* __Fine grained security__
* __Proxy mode__

Resources
---------

More information on jolokia can be found at [www.jolokia.org][5]
(coming soon)

 [1]: http://github.com/rhuss/jolokia/raw/master/src/site/resources/images/jolokia_logo.png "Jolokia"
 [2]: http://www.osgi.org
 [3]: http://www.mulesoft.org
 [4]: http://www.jmx4perl.org
 [5]: http://www.jolokia.org
 [6]: http://labs.consol.de/blog/jmx4perl/configuring-remote-jmx-access-for-weblogic   
 [7]: http://labs.consol.de/blog/jmx4perl/jboss-remote-jmx

