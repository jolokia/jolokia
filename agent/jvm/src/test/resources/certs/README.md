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

# Howto create certs

```
# Start an Open SSL container
docker run -it --entrypoint sh -v `pwd`:/tmp/ nick81/openssl

# Add to /etc/ssl/openssl.conf

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

# Create CA Key
cd ca/
openssl genrsa -des3 -out key.pem 4096
openssl req -new -x509 -days 3650 -key key.pem -out cert.pem

# Create a Server Key & signing request & sign it (with no password on the key) [ note: validity must be *shorter* than CA's ]
cd server/
openssl genrsa -out key.pem -aes128 2048
openssl rsa -in key.pem -out key.pem
openssl req -new -key key.pem -out server.csr -nodes
openssl x509 -req  -extfile ../server.ext -days 2500 -in server.csr -CA ../ca/cert.pem -CAkey ../ca/key.pem -set_serial 01 -out cert.pem

# Create a Client Key & signing request & sign it
cd client/<new-dir>/
openssl genrsa -des3 -out key.pem 4096
openssl req -new -key key.pem -out client.csr
openssl x509 -extfile ../../client.ext -req -days 2500 -in client.csr -CA ../../ca/cert.pem -CAkey ../../ca/key.pem -set_serial 02 -out cert.pem

# Convert client key to PKCS
openssl pkcs12 -export -clcerts -in cert.pem -inkey key.pem -out cert.p12

# Import client key to Browser
```
