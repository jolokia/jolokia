* Use the following `jolokia.properties` to enable SSL authentication. 
  Adapt the path accordingly to the certs found in this directory:
 
```
protocol=https
caCert=cacert.pem
serverCert=servercert.pem
serverKey=jserverkey.pem
useSslClientAuthentication=true
discoveryEnabled=false
port=8778
host=0.0.0.0
```

* Then start process with Jolokia attached:

```
java -javaagent:jolokia.jar=config=jolokia.properties
```

* Import client certificate `client.p12` (or you could try it with `server.p12`, too)

* Start Java server and then go to `https://localhost:8778/jolokia/` with the browser (trailing slash important)

# Passwords

When required, the password is "1234"

The server key (serverkey.pem) doesn't have a password

# How to create certs

Start an Open SSL container:
```
docker run -it --entrypoint sh -v `pwd`:/tmp/ nick81/openssl
```

`openssl.conf` needs some sections to configure extensions added to certificates. Location of OpenSSL
configuration can be specified with `OPENSSL_CONF` environmental variable.

OpenSSL uses main commands like `req` (certificate request generation) or `x509` (certificate request signing) and relevant configuration is specified is section matching the commands:

```
[req]
# configuration for `openssl req` command
# see https://docs.openssl.org/master/man1/openssl-req/#configuration-file-format
```

For `openssl req` command we can specify the section to be used with `-section` options (defaults to `[req]`).
Extensions to be added are taken from:
 * `-reqexts <section>` (defaults to section pointed by `req_extensions` option in the section used) for requests
 * `-extensions <section>` (defaults to section pointed by `x509_extensions` option in the section used) for self-signed requests

For `openssl x509` (used to sign requests) command, we can specify the extensions to be used with `-extensions` option. There's no default `[x509]` section in OpenSSL configuration.

See https://docs.openssl.org/master/man5/x509v3_config/ for a list of available extensions.


Add to /etc/ssl/openssl.conf:
```
[ ssl_client ]
basicConstraints        = CA:FALSE
nsCertType              = client
keyUsage                = digitalSignature, keyEncipherment
extendedKeyUsage        = clientAuth

[ ssl_server ]
basicConstraints        = CA:FALSE
nsCertType              = server
keyUsage                = digitalSignature, keyEncipherment
extendedKeyUsage        = serverAuth, nsSGC, msSGC
```

# Create a CA self-signed certificate and key

Here are the OpenSSL configuration sections to be used when (re)generating Jolokia CA certificate:

```
[ req_jolokia_ca ]
default_bits = 4096
default_md = sha512
# https://docs.openssl.org/master/man1/openssl-req/#distinguished-name-and-attribute-section-format
prompt = no
distinguished_name = req_jolokia_ca_dn
x509_extensions = req_jolokia_ca_extensions

[ req_jolokia_ca_dn ]
CN = ca.test.jolokia.org
O = Jolokia
OU = Dev
L = Pegnitz
ST = Bavaria
C = DE
emailAddress = roland@jolokia.org

[ req_jolokia_ca_extensions ]
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer
basicConstraints = critical,CA:true
keyUsage = cRLSign, keyCertSign
```

CA certificate generation using the above sections:
```
cd ca/
openssl req -new -x509 -outform pem -out cert.pem -keyout key.pem -newkey rsa:4096 -days $((20*365+5)) -section req_jolokia_ca
```

# Create Server certificate requests and keys + sign the requests

note: validity should be *shorter* than CA's

We need two certificates with SHA1 (`cert.pem`) and SHA256 (`cert2.pem`) signatures. Extensions will be taken from a file specified with `-extfile` option.

OpenSSL configuration sections:

```
[ req_jolokia_server ]
default_bits = 2048
default_md = sha1
prompt = no
distinguished_name = req_jolokia_server_dn

[ req_jolokia_server_dn ]
CN = Server Cert signed and with extended key usage server
O = Jolokia
OU = Dev
L = Pegnitz
ST = Bavaria
C = DE

[ req_jolokia_server2 ]
default_bits = 2048
default_md = sha512
prompt = no
distinguished_name = req_jolokia_server2_dn

[ req_jolokia_server2_dn ]
CN = Server Certificate
O = Jolokia
OU = Dev
L = Pegnitz
ST = Bavaria
C = DE
```

Server certificate generation using the above sections:
```
cd server/
openssl req -new -outform pem -out server.csr -keyout key.pem -newkey rsa:2048 -noenc -section req_jolokia_server
openssl req -new -outform pem -out server2.csr -keyout key2.pem -newkey rsa:2048 -noenc -section req_jolokia_server2
```

Server certificate signing using CA certificate and extension file:
```
openssl x509 -req -extfile ../server.ext -days $((19*365+5)) -in server.csr -out cert.pem -CA ../ca/cert.pem -CAkey ../ca/key.pem -set_serial 1 -sha1
openssl x509 -req -extfile ../server.ext -days $((19*365+5)) -in server2.csr -out cert2.pem -CA ../ca/cert.pem -CAkey ../ca/key.pem -set_serial 2 -sha256
```

# Create a Client Key & signing request & sign it
cd client/<new-dir>/
openssl genrsa -des3 -out key.pem 4096
openssl req -new -key key.pem -out client.csr
openssl x509 -extfile ../../client.ext -req -days 2500 -in client.csr -CA ../../ca/cert.pem -CAkey ../../ca/key.pem -set_serial 02 -out cert.pem

# Convert client key to PKCS
openssl pkcs12 -export -clcerts -in cert.pem -inkey key.pem -out cert.p12

# Import client key to Browser
```
