!!! IMPORTANT ---------------------------------------------------------------- !!!
!!! These private keys are irrelevant, not used anywhere, except for the tests !!!
!!! IMPORTANT ---------------------------------------------------------------- !!!

The keys are generated using org.jolokia.core.util.KeyGenerationTest.rsaPbeEncryption().
We try to check all PBE encryption algorithms.

Somehow:
 - Java: PBEWithMD5AndTripleDES is not supported by javax.crypto.EncryptedPrivateKeyInfo
 - OpenSSL: only PBEWithSHA1AndDESede is supported

For openssl, to use PKCS#5 1.5 (PBES1) we can use something like `openssl pkcs8 -in RSA-pkcs8.pem -topk8 -v1 PBE-SHA1-3DES`
``-v1 PBE-SHA1-3DES` works, but other algorithms fail, like:

$ openssl pkcs8 -in RSA-pkcs8.pem -topk8 -v1 PBE-SHA1-RC2-128
Enter Encryption Password:
Verifying - Enter Encryption Password:
Error encrypting key
C06285EB1C7F0000:error:0308010C:digital envelope routines:inner_evp_generic_fetch:unsupported:crypto/evp/evp_fetch.c:375:Global default library context, Algorithm (RC2-CBC : 50), Properties ()
C06285EB1C7F0000:error:11800067:PKCS12 routines:PKCS12_item_i2d_encrypt_ex:encrypt error:crypto/pkcs12/p12_decr.c:199:
C06285EB1C7F0000:error:11800067:PKCS12 routines:PKCS8_set0_pbe_ex:encrypt error:crypto/pkcs12/p12_p8e.c:80:

Probably that's why only PBEWithSHA1AndDESede is interoperable...
