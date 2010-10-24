Glassfish admin console:

Configuration -> Virtual Servers -> server -> Add Property:

key: alternatedocroot_1
value: from=/src/* dir=/path/to/jolokia/client/js

The dir must contain a subpath matching the "from" argument.

