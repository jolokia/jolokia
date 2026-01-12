!!! IMPORTANT ---------------------------------------------------------------- !!!
!!! These private keys are irrelevant, not used anywhere, except for the tests !!!
!!! IMPORTANT ---------------------------------------------------------------- !!!

Private Keys handled by Jolokia.

The keys are first generated using org.jolokia.core.util.KeyGenerationTest.generatingAndEncodingKeys() and then
processed using `openssl`.

To get _legacy_ non-encrypted keys:

$ for n in DSA EC RSA; do echo ================= $n; openssl pkey -inform der -in ${n}-pkcs8.der -traditional -outform pem -out ${n}-legacy.pem; done
$ for n in DSA EC RSA; do echo ================= $n; openssl pkey -inform der -in ${n}-pkcs8.der -traditional -outform der -out ${n}-legacy.der; done

To get _legacy_ encrypted keys (password: "jolokia", no quotes) (of course can't get DER representation):

$ for n in DSA EC RSA; do echo ================= $n; openssl pkey -inform der -in ${n}-pkcs8.der -traditional -outform pem -out ${n}-legacy-encrypted.pem -aes256; done

(see https://docs.openssl.org/1.1.1/man3/PEM_read_bio_PrivateKey/#pem-encryption-format)

3 unencrypted _legacy_ (`openssl pkey -traditional`)
 - RSA (-----BEGIN RSA PRIVATE KEY-----)
 - DSA (-----BEGIN DSA PRIVATE KEY-----)
 - EC (-----BEGIN EC PRIVATE KEY-----)

3 encrypted _legacy_ (`openssl pkey -traditional -<enc-alg>`) formats (encryption outside of ASN.1 structure, only PEM):
 - RSA (-----BEGIN RSA PRIVATE KEY----- + Proc-Type: 4,ENCRYPTED)
 - DSA (-----BEGIN DSA PRIVATE KEY----- + Proc-Type: 4,ENCRYPTED)
 - EC (-----BEGIN EC PRIVATE KEY----- + Proc-Type: 4,ENCRYPTED)
