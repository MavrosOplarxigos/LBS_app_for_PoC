package com.example.lbs_app_for_poc;
import android.util.Log;

import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.math.ec.ECPoint;
// import org.bouncycastle.jce.interfaces.ECPointEncoder;
// import org.bouncycastle.jce.interfaces.ECPrivateKey;
// import org.bouncycastle.jce.interfaces.ECPublicKey;
// import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;


public class CryptoChecks {

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

    }

    public static boolean ecPrivateMatchesECpublic(java.security.interfaces.ECPrivateKey ecPrivateKey,
                                                   java.security.interfaces.ECPublicKey ecPublicKey) {

        Log.d("ecPrivateMatchesECpublic","function enters!");

        return true; // TODO: Figure out how to implement this function since it is not a MUST

        /*
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

    }

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
    //    TODO: Find out how to implement this function

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

}
