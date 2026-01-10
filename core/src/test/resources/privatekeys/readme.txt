!!! IMPORTANT ---------------------------------------------------------------- !!!
!!! These private keys are irrelevant, not used anywhere, except for the tests !!!
!!! IMPORTANT ---------------------------------------------------------------- !!!

Private Keys handled by Jolokia.

The keys are generated using org.jolokia.core.util.KeyGenerationTest.generatingAndEncodingKeys(). This generates
PEM/DER representations of all supported key types in:
 - PKCS#8 non-encrypted PrivateKeyInfo (-----BEGIN PRIVATE KEY-----)
 - PKCS#8 + PKCS#5 PBES1 EncryptedPrivateKeyInfo (-----BEGIN ENCRYPTED PRIVATE KEY-----)
 - PKCS#8 + PKCS#5 PBES2 EncryptedPrivateKeyInfo (-----BEGIN ENCRYPTED PRIVATE KEY-----)

To get _legacy_ non-encrypted keys:

$ for n in DSA EC RSA; do echo ================= $n; openssl pkey -inform der -in ${n}-pkcs8.der -traditional -outform pem -out ${n}-legacy.pem; done
$ for n in DSA EC RSA; do echo ================= $n; openssl pkey -inform der -in ${n}-pkcs8.der -traditional -outform der -out ${n}-legacy.der; done

To get _legacy_ encrypted keys (password: "jolokia", no quotes) (of course can't get DER representation):

$ for n in DSA EC RSA; do echo ================= $n; openssl pkey -inform der -in ${n}-pkcs8.der -traditional -outform pem -out ${n}-legacy-encrypted.pem -aes256; done

2 formats:
 - DER
 - PEM

11 algorithms (java.security.KeyFactory.getInstance(), java.security.KeyPairGenerator.getInstance())
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

1 unencrypted _standard_ format:
 - PKCS#8 PrivateKeyInfo structure (-----BEGIN PRIVATE KEY-----)

2 encrypted _standard_ formats (-----BEGIN ENCRYPTED PRIVATE KEY-----):
 - PKCS#8 EncryptedPrivateKeyInfo with PKCS#5 PBES1
 - PKCS#8 EncryptedPrivateKeyInfo with PKCS#5 PBES2

3 unencrypted _legacy_ (`openssl pkey -traditional`)
 - RSA (-----BEGIN RSA PRIVATE KEY-----)
 - DSA (-----BEGIN DSA PRIVATE KEY-----)
 - EC (-----BEGIN EC PRIVATE KEY-----)

3 encrypted _legacy_ (`openssl pkey -traditional -<enc-alg>`) formats (encryption outside of ASN.1 structure, only PEM):
 - RSA (-----BEGIN RSA PRIVATE KEY----- + Proc-Type: 4,ENCRYPTED)
 - DSA (-----BEGIN DSA PRIVATE KEY----- + Proc-Type: 4,ENCRYPTED)
 - EC (-----BEGIN EC PRIVATE KEY----- + Proc-Type: 4,ENCRYPTED)

(see https://docs.openssl.org/1.1.1/man3/PEM_read_bio_PrivateKey/#pem-encryption-format)
