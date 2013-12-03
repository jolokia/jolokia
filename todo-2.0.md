
# Done

* History Handling
* MBean registration 
* org.jolokia.backend.BackendManagerTest -> RequestDispatcherTest must
  be moved
* JolokiaService for the OSGi agent
* Converters as a service
* LogHandler for the SpringJolokiaAgent
* Rename "JmxRequest" to "JolokiaRequest" (and all subclasses, too)

# Open tasks

* Configuration for Pull Backend for Max Entries
* Extract services from the core module into own modules/bundles
* Special request dispatcher for giving access to a Spring Context
* Restrictor must be able to handle realms
* OrderId for services must be simplified since not every service has
  an order (i.e. allow an order id of '0')

# Optional

* ConfigurationService usage in OSGi
* ExecHandler: Allow for a path expression to be applied on the return
  value

# Documentation

* Spring "log" configuration
* Spring "lookupServices" config
* Spring "exposeApplicationContext" config
* "version" command should not be dispatched
* "version" should include a list of all available services.
