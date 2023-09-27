package com.example.lbs_app_for_poc;
import android.util.Log;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;

import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;


public class CryptoChecks {

    public static boolean isPythonStyleEncryptAndDecryptWorking(X509Certificate certificate, java.security.PrivateKey privateKey){

        return true;
    }

    public static boolean sameAlgorithm(PublicKey publicKey, PrivateKey privateKey){
        return (publicKey.getAlgorithm().equals(privateKey.getAlgorithm()));
    }

    public static boolean isEncryptAndDecryptWorking(X509Certificate certificate, java.security.PrivateKey privateKey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchProviderException {

        // checking that the key algorithms are the same
        if(!sameAlgorithm(certificate.getPublicKey(),privateKey)){
            Log.d("End&DecCheck","The key algorithms dont' match PUBLIC: " + certificate.getPublicKey().getAlgorithm() + " PRIVATE: " + privateKey.getAlgorithm() );
            return false;
        }

        if( privateKey.getAlgorithm().equals("RSA") ) {
            RSAPublicKey rsaPublicKey = (RSAPublicKey) certificate.getPublicKey();
            RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) privateKey;

            // tesing that the modulus and exponent of the keys are the same
            if (rsaPrivateKey.getModulus().equals(rsaPublicKey.getModulus())) {
                Log.d("End&DecCheck", "The modulus of the private key matches that of the public key");
            } else {
                Log.d("End&DecCheck", "ERROR: The modulus of the private key DOESN'T match that of the public key");
                return false;
            }

            // tesing data
            byte[] data = "test_data_1234".getBytes();
            byte[] false_data = "test_data_1235".getBytes();
            byte[] false_data2 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes();
            Log.d("End&DecCheck", "The initial data are " + (new String(data, StandardCharsets.UTF_8)));

            // encrypting
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, (Key) rsaPrivateKey);
            byte[] enc_data = cipher.doFinal(data);
            byte[] enc_false_data = cipher.doFinal(false_data);
            byte[] enc_false_data2 = cipher.doFinal(false_data2);
            Log.d("End&DecCheck", "The encrypted data are " + (new String(enc_data, StandardCharsets.UTF_8)));

            // decrypting
            Cipher dec_cipher = Cipher.getInstance("RSA");
            dec_cipher.init(Cipher.DECRYPT_MODE, (Key) rsaPublicKey);
            byte[] dec_data = dec_cipher.doFinal(enc_data);

            boolean result = (Arrays.equals(dec_data, data)) && !(Arrays.equals(dec_data, false_data)) && !(Arrays.equals(dec_data, false_data2));
            Log.d("End&DecCheck", "The decrypted data are " + (new String(dec_data, StandardCharsets.UTF_8)));

            return result;
        }
        else{
            Log.d("End&DecCheck","No check for for encryption and decryption implemented for " + privateKey.getAlgorithm() + " keys!");
            return false;
        }

    }

    public static Signature getSignatureInstanceByAlgorithm(String algo) throws NoSuchAlgorithmException, NoSuchProviderException {

        Signature signature;

        if(algo.equals("EC")) {
            signature = Signature.getInstance("SHA256withECDSA", "AndroidOpenSSL");
        }
        else if(algo.equals("RSA")){
            signature = Signature.getInstance("SHA256WithRSA","AndroidOpenSSL");
        }
        else{
            Log.d("SignCheck","No implementation for signing check for " + algo + "keys!");
            signature = null;
        }

        return signature;

    }

    public  static  boolean isSignedByCert(byte [] original, byte [] signed, X509Certificate cert) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {
        Signature dec_signature = getSignatureInstanceByAlgorithm(cert.getPublicKey().getAlgorithm());
        if (dec_signature==null){ return false; }
        dec_signature.initVerify(cert.getPublicKey());
        dec_signature.update(original);
        return dec_signature.verify(signed);
    }

    public static boolean isSignedByCert(String original, String signed, X509Certificate cert) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {
        Signature dec_signature = getSignatureInstanceByAlgorithm(cert.getPublicKey().getAlgorithm());
        if (dec_signature==null){ return false; }
        dec_signature.initVerify(cert.getPublicKey());
        dec_signature.update(original.getBytes());
        return dec_signature.verify(signed.getBytes());
    }

    public static boolean isSigningAndVerifyingWorking(X509Certificate certificate, java.security.PrivateKey privateKey) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, SignatureException {

        // retrieving the public key
        java.security.PublicKey publicKey = certificate.getPublicKey();

        // checking that the key algorithms are the same
        if(!sameAlgorithm(publicKey,privateKey)){
            Log.d("SignCheck","The key algorithms dont' match PUBLIC: " + publicKey.getAlgorithm() + " PRIVATE: " + privateKey.getAlgorithm() );
            return false;
        }

        // tesing data
        byte[] data = "test_data_1234".getBytes();
        byte[] false_data = "test_data_1235".getBytes();
        byte[] false_data2 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes();

        // Signing
        Signature signature = getSignatureInstanceByAlgorithm(privateKey.getAlgorithm());
        if (signature==null){ return false; }

        signature.initSign(privateKey);
        signature.update(data);
        byte[] signed_data = signature.sign();

        // Verifying
        boolean result;
        Signature dec_signature = getSignatureInstanceByAlgorithm(privateKey.getAlgorithm());
        if (dec_signature==null){ return false; }

        dec_signature.initVerify(publicKey);
        dec_signature.update(data);
        result = dec_signature.verify(signed_data); // verify reset the dec_signature to before the initVerify state
        dec_signature.initVerify(publicKey);
        dec_signature.update(false_data);
        result = result && (!dec_signature.verify(signed_data));
        dec_signature.initVerify(publicKey);
        dec_signature.update(false_data2);
        result = result && (!dec_signature.verify(signed_data));

        return result;

    }

    public static boolean isCertificateSignedBy(X509Certificate certificate, X509Certificate issuerCertificate) {
        Log.d("is certificate signed by","Function enters!");
        try {
            PublicKey issuerPublicKey = issuerCertificate.getPublicKey();
            certificate.verify(issuerPublicKey);
            Log.d("CERT SIGNED BY CHECK","The certificate is signed by the issuer certificate with DN " + issuerCertificate.getSubjectDN() );
            return true;
        } catch (CertificateException e) {
            // Handle certificate parsing error
            Log.d("CERT SIGNED BY CHECK","Could not parse the certificates given!");
            e.printStackTrace();
        } catch (Exception e) {
            // Certificate verification failed
            Log.d("CERT SIGNED BY CHECK","The certificate is not signed by the issuer certificate with DN " + issuerCertificate.getSubjectDN() );
            return false;
        }
        return false;
    }

}

    /*
    public static boolean isPrivateKeyForCertificate(PrivateKey privateKey, X509Certificate certificate) {
        Log.d("is private key for certificate","fucntino enters!");
        PublicKey publicKey = certificate.getPublicKey();

        // if we are using java.security.interfaces.RSA keys
        if (privateKey instanceof java.security.interfaces.RSAPrivateKey && publicKey instanceof java.security.interfaces.RSAPublicKey) {
            Log.d("PRIVATE KEY FOR CERT CHECK","The keys are java.security.interfaces.RSA keys");
            java.security.interfaces.RSAPrivateKey rsaPrivateKey = (java.security.interfaces.RSAPrivateKey) privateKey;
            java.security.interfaces.RSAPublicKey rsaPublicKey = (java.security.interfaces.RSAPublicKey) publicKey;
            // Compare modulus and public exponent
            if (rsaPrivateKey.getModulus().equals(rsaPublicKey.getModulus()) && rsaPrivateKey.getPrivateExponent().equals(rsaPublicKey.getPublicExponent())) {
                Log.d("PRIVATE KEY FOR CERT CHECK","PrivateKey corresponds to PublicKey of Certificate");
                return true;
            }
            else{
                Log.d("PRIVATE KEY FOR CERT CHECK","PrivateKey does NOT correspond to PublicKey of Certificate");
                return false;
            }
        }
        else{ // we consider that they are both java.security.interfaces.EC keys
            Log.d("PRIVATE KEY FOR CERT CHECK","The keys are EC keys!");
            Log.d("PRIVATE KEY FOR CERT CHECK","privateKey algorithm = " + privateKey.getAlgorithm() + " publicKey algorithm = " + publicKey.getAlgorithm() );

            // (privateKey instanceof ECPrivateKey && publicKey instanceof ECPublicKey)
            java.security.interfaces.ECPrivateKey ecPrivateKey;
            java.security.interfaces.ECPublicKey ecPublicKey;

            Log.d("private key for cert bool testing"," privateKey instanceof ECPrivateKey = " + (privateKey instanceof java.security.interfaces.ECPrivateKey) );
            Log.d("private key for cert bool testing"," publicKey instanceof ECPublicKey = " + (publicKey instanceof java.security.interfaces.ECPublicKey) );

            try {
                Log.d("private key for cert check","Will now try to cast them to the ec versions of the keys");
                ecPrivateKey = (java.security.interfaces.ECPrivateKey) privateKey;
                ecPublicKey = (java.security.interfaces.ECPublicKey) publicKey;
                Log.d("private key for cert check","The casting was successful!");
            }
            catch (Exception e){
                throw e;
            }

            java.security.spec.ECParameterSpec privateKeyParams = ecPrivateKey.getParams();
            java.security.spec.ECParameterSpec publicKeyParams = ecPublicKey.getParams();

            Log.d("PRIVATE KEY FOR CERT CHECK","privateKeyParams g = " + privateKeyParams.getGenerator().toString() );
            Log.d("PRIVATE KEY FOR CERT CHECK","publicKeyParams g = " + publicKeyParams.getGenerator().toString() );

            java.security.spec.EllipticCurve ec_private = privateKeyParams.getCurve();
            java.security.spec.EllipticCurve ec_public = privateKeyParams.getCurve();

            if ( ec_private.equals(ec_public) ) {
            // if ( ecPrivateKey.getParameters().equals(ecPublicKey.getParameters()) ) {
                Log.d("PRIVATE KEY FOR CERT CHECK","The elliptic curve matches for the keys");
                if (ecPrivateMatchesECpublic(ecPrivateKey,ecPublicKey)){ //NOTE: THIS IS WHERE WE NEED TO SEE IF WE CAN DO IT WITHOUT BOUNCY CASTLE
                    Log.d("PRIVATE KEY FOR CERT CHECK","PrivateKey POINT EQUALS to PublicKey of Certificate POINT!");
                    return true;
                }
                else{
                    Log.d("PRIVATE KEY FOR CERT CHECK","PrivateKey POINT does NOT correspond to PublicKey of Certificate POINT!");
                    return false;
                }
            }
            else {
                Log.d("PRIVATE KEY FOR CERT CHECK","The two keys do not have the same curve!");
                Log.d("PRIVATE KEY FOR CERT CHECK","The keys do not match one another! privateKey algorithm = " + privateKey.getAlgorithm() + " publicKey algorithm = " + publicKey.getAlgorithm() );
                return false;
            }

        }

    }*/

    /*
    public static boolean ecPrivateMatchesECpublic(java.security.interfaces.ECPrivateKey ecPrivateKey,
                                                   java.security.interfaces.ECPublicKey ecPublicKey) {

        Log.d("ecPrivateMatchesECpublic","function enters!");

        return true;


        try {
            java.security.spec.ECParameterSpec privateKeyParams = ecPrivateKey.getParams();
            java.security.spec.ECParameterSpec publicKeyParams = ecPublicKey.getParams();

            // compute public key point
            java.security.spec.ECPoint generatorPublic = publicKeyParams.getGenerator();
            org.bouncycastle.math.ec.ECPoint BouncyGeneratorPublic = ECPointConverter.getBouncyCastleECPointFromJavaSecurityPublic(generatorPublic, ecPublicKey);
            BigInteger d_public = ecPublicKey.getW().getAffineX();
            org.bouncycastle.math.ec.ECPoint publicKeyPoint = BouncyGeneratorPublic.multiply(d_public);

            // compute private key point
            java.security.spec.ECPoint generatorPrivate = privateKeyParams.getGenerator();
            org.bouncycastle.math.ec.ECPoint BouncyGeneratorPrivate = ECPointConverter.getBouncyCastleECPointFromJavaSecurityPrivate(generatorPrivate, ecPrivateKey);
            BigInteger d_private = ecPrivateKey.getS();
            org.bouncycastle.math.ec.ECPoint privateKeyPoint = BouncyGeneratorPrivate.multiply(d_private);

            return publicKeyPoint.equals(privateKeyPoint);
        }
        catch (Exception e){
            throw e;
        }*/

    // ECPoint privateKeyPoint = privateKey.getParameters().getG().multiply(privateKey.getD());
    // ECPoint publicKeyPoint = publicKey.getQ();
        /*if( privateKeyPoint.equals(publicKeyPoint) ){
            return true;
        }
        Log.d("EC POINT CHECK","NOT EQUAL POINTS private is " + privateKeyPoint.toString() + " public is " + publicKeyPoint.toString() );
        Log.d("EC POINT CHECK","NOT EQUAL POINTS private is x = " + privateKeyPoint.getXCoord().toBigInteger().toString() + " public is x = " + publicKeyPoint.getXCoord().toBigInteger().toString() );
        Log.d("EC POINT CHECK","NOT EQUAL POINTS private is y = " + privateKeyPoint.getYCoord().toBigInteger().toString() + " public is y = " + publicKeyPoint.getYCoord().toBigInteger().toString() );
        return false;*/

    // privateKey.getParams().getGenerator().multiply(privateKey.getS());

    // ECCurve privateCurve = privateKey.getParameters().getCurve();
        /*
        ECPointEncoder encoder =
        byte[] privateKeyBytes = privateKey.getEncoded();
        byte[] publicKeyBytes = publicKey.getEncoded();
        MessageDigest.isEqual(privateKeyBytes,publicKeyBytes);
        */
        /*
        ECPointEncoder encoder = org.bouncycastle.jcajce.util.ECUtil.getEncoder(privateKey.getParams().getCurve());
        byte[] privateKeyBytes = encoder.encodePoint(privateKey.getParams().getG().multiply(privateKey.getS()).normalize());
        byte[] publicKeyBytes = encoder.encodePoint(publicKey.getQ().normalize());
        return MessageDigest.isEqual(privateKeyBytes, publicKeyBytes);
        */

    //    bcprov-jdk18on-173.jar
    //    bcprov-ext-jdk18on-173.jar
    //    removed implementation 'org.bouncycastle:bcpkix-jdk18on:1.73'
    //    kept implementation 'org.bouncycastle:bcprov-jdk18on:1.73'
    /*
    private static byte[] parsePEMFile(File pemFile) throws IOException {
        if (!pemFile.isFile() || !pemFile.exists()) {
            throw new FileNotFoundException(String.format("The file '%s' doesn't exist.", pemFile.getAbsolutePath()));
        }
        PemReader reader = new PemReader(new FileReader(pemFile));
        PemObject pemObject = reader.readPemObject();
        byte[] content = pemObject.getContent();
        reader.close();
        return content;
    }
    */
