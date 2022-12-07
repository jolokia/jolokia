![Jolokia - JMX on Capsaicin][1]

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.jolokia/jolokia-parent/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/org.jolokia/jolokia-parent/)
[![Build Status](https://github.com/rhuss/jolokia/actions/workflows/ci.yaml/badge.svg)](https://github.com/rhuss/jolokia/actions/workflows/ci.yaml)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=org.jolokia%3Ajolokia&metric=coverage)](https://sonarcloud.io/summary/new_code?id=org.jolokia%3Ajolokia)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=org.jolokia%3Ajolokia&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=org.jolokia%3Ajolokia)
[![Gitter](https://badges.gitter.im/Join+Chat.svg)](https://gitter.im/rhuss/jolokia?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Code Quality: Java](https://img.shields.io/lgtm/grade/java/g/rhuss/jolokia.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/rhuss/jolokia/context:java)
[![Total Alerts](https://img.shields.io/lgtm/alerts/g/rhuss/jolokia.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/rhuss/jolokia/alerts)

Jolokia is a fresh way to access JMX MBeans remotely. It is
different from JSR-160 connectors in that it is an agent-based
approach which uses JSON over HTTP for its communication in a
REST-stylish way.

Multiple agents are provided for different environments:

* **WAR Agent** for deployment as web application in a Java EE Server. 
* **OSGi Agent** for deployment in an [OSGi][2] container. This agent
  is packaged as a bundle and comes in two flavors (minimal,
  all-in-one).
* **Mule Agent** for usage within a [Mule][3] ESB
* **JVM JDK6 Agent** which can be used with any Oracle/Sun JVM,
  Version 6 or later and which is able to attach to a running Java process 
  dynamically. 


## Features

The agent approach as several advantages:

* **Firewall friendly**

  Since all communication is over HTTP, proxying through firewalls
  becomes mostly a none-issue (in contrast to RMI communication, which
  is the default mode for JSR-160)

* **Polyglot**

  No Java installation is required on the client
  side. E.g. [Jmx4Perl][4] provides a rich Perl client library and
  Perl based tools for accessing the agents.

* **Simple Setup**

  The Setup is done by a simple agent deployment. In contrast,
  exporting JMX via JSR-160 can be remarkable complicated (see these
  blog posts for setting up [Weblogic][6] and [JBoss][7] for native
  remote JMX exposure setup)

Additionally, the agents provide extra features not available with
JSR-160 connectors:

* **Bulk requests**

  In contrast to JSR-160 remoting, Jolokia can process many JMX
  requests with a single round trip. A single HTTP POST request puts
  those requests in its JSON payload which gets dispatched on the
  agent side. These bulk requests can increase performance drastically,
  especially for monitoring solutions. The Nagios plugin
  [check_jmx4perl][8] uses bulk requests for its multi-check feature.
  
* **Fine grained security**

  In addition to standard HTTP security (SSL, HTTP-Authentication)
  Jolokia supports a custom policy with fine grained restrictions
  based on multiple properties like the client's IP address or subnet,
  and the MBean names, attributes, and operations. The policy is
  defined in an XML format with support for allow/deny sections and
  wildcards.

* **Proxy mode**

  Jolokia can operate in an agentless mode where the only requirement
  on the target platform is the standard JSR-160 export of its
  MBeanServer. A proxy listens on the front side for Jolokia requests
  via JSON/HTTP and propagates these to the target server through
  remote JSR-160 JMX calls. Bulk requests get dispatched into
  multiple JSR-160 requests on the proxy transparently.

## Resources

* The [Jolokia Forum][9] can be used for questions about Jolokia 
  (and Jmx4perl).

* For bug reports, please use the [Github Issue tracker][10].

* Most of the time, I'm hanging around at [Freenode][11] in 
  `#jolokia`, too.

Even more information on Jolokia can be found at [www.jolokia.org][5], including
a complete [reference manual][12].

## Contributions

Contributions in form of pull requests are highly appreciated. All your work must be donated under the 
Apache Public License, too. Please sign-off your work before 
doing a pull request. The sign-off is a simple line at the end of the patch description, 
which certifies that you wrote it or otherwise have the right to
pass it on as an open-source patch.  The rules are very simple: if you
can certify the below (from
[developercertificate.org](http://developercertificate.org/)):

```
Developer Certificate of Origin
Version 1.1

Copyright (C) 2004, 2006 The Linux Foundation and its contributors.
660 York Street, Suite 102,
San Francisco, CA 94110 USA

Everyone is permitted to copy and distribute verbatim copies of this
license document, but changing it is not allowed.

Developer's Certificate of Origin 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the best
    of my knowledge, is covered under an appropriate open source
    license and I have the right under that license to submit that
    work with modifications, whether created in whole or in part
    by me, under the same open source license (unless I am
    permitted to submit under a different license), as indicated
    in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including all
    personal information I submit with it, including my sign-off) is
    maintained indefinitely and may be redistributed consistent with
    this project or the open source license(s) involved.
```

Then you just add a line to every git commit message:

    Signed-off-by: Max Morlock <max.morlock@fcn.de>

Using your real name (sorry, no pseudonyms or anonymous contributions.)

If you set your `user.name` and `user.email` git configs, you can sign your
commit automatically with `git commit -s`.

If you fix some documentation (typos, formatting, ...) you are not required to sign-off. 
It is possible to sign your commits in retrospective, [too](http://stackoverflow.com/questions/13043357/git-sign-off-previous-commits) 
if you forgot it the first time. 

 [1]: https://jolokia.org/images/jolokia_logo.png "Jolokia"
 [2]: http://www.osgi.org
 [3]: http://www.mulesoft.org
 [4]: http://www.jmx4perl.org
 [5]: https://www.jolokia.org
 [6]: http://labs.consol.de/blog/jmx4perl/configuring-remote-jmx-access-for-weblogic   
 [7]: http://labs.consol.de/blog/jmx4perl/jboss-remote-jmx
 [8]: http://search.cpan.org/~roland/jmx4perl/scripts/check_jmx4perl
 [9]: https://jolokia.org/forum.html
 [10]: https://github.com/rhuss/jolokia/issues
 [11]: http://webchat.freenode.net/?channels=jolokia
 [12]: https://www.jolokia.org/reference/html/index.html
