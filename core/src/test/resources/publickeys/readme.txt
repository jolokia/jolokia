Public Keys handled by Jolokia.

Generated from private keys using (in src/test/resource/privatekeys directory):

$ for n in DSA DiffieHellman EC Ed448 Ed25519 EdDSA RSA RSASSA-PSS X448 X25519 XDH; do echo ================= $n; openssl pkey -inform der -in ${n}-pkcs8.der -pubout -out ../publickeys/${n}-pub.pem -outform pem; done
$ for n in DSA DiffieHellman EC Ed448 Ed25519 EdDSA RSA RSASSA-PSS X448 X25519 XDH; do echo ================= $n; openssl pkey -inform der -in ${n}-pkcs8.der -pubout -out ../publickeys/${n}-pub.der -outform der; done
$ openssl rsa -inform der -in RSA-pkcs8.der -RSAPublicKey_out -outform der -out ../publickeys/RSA-pub-pkcs1.der
$ openssl rsa -inform der -in RSA-pkcs8.der -RSAPublicKey_out -outform pem -out ../publickeys/RSA-pub-pkcs1.pem

2 formats:
 - DER
 - PEM

11 algorithms (java.security.KeyFactory.getInstance())
 - DSA
 - RSA
 - RSASSA-PSS
 - EC
 - Ed25519
 - Ed448
 - EdDSA
 - X25519
 - X448
 - XDH
 - DiffieHellman

2 kinds of structures:
 - X.509 SubjectPublicKeyInfo - https://datatracker.ietf.org/doc/html/rfc5280#section-4.1 - with `openssl pkey -pubout`
   Always: "-----BEGIN PUBLIC KEY-----" for PEM
    - DSA
    - RSA
    - RSASSA-PSS
    - EC
    - Ed25519
    - Ed448
    - EdDSA
    - X25519
    - X448
    - XDH
    - DiffieHellman
 - "native"/"traditional" for RSA, DSA, EC, which have `openssl pkey -traditional` support:
    - RSA - https://www.rfc-editor.org/rfc/rfc8017.html#appendix-A.1.1, RSAPublicKey
      with `openssl pkey | openssl rsa -RSAPublicKey_out`
      "-----BEGIN RSA PUBLIC KEY-----"
    - DSA - https://www.rfc-editor.org/rfc/rfc5480.html#appendix-A, DSAPublicKey, DSS-Parms
      with `openssl pkey | openssl dsa -pubout | openssl asn1parse -strparse` I just get single INTEGER
    - EC -
      with `openssl pkey | openssl ec -pubout` we can't even `-strparse`, as the BIT STRING is "raw EC point encoding (uncompressed or compressed)"
