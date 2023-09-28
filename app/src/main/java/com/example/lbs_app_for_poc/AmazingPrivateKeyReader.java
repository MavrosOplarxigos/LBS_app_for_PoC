package com.example.lbs_app_for_poc;
import android.util.Base64;
import android.util.Log;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.util.UUID;

public class AmazingPrivateKeyReader {

    public static PrivateKey KeyFromFile(File input) throws Exception {

        Log.d("AMAZING PRIVATE KEY READER","Entered the original keyFromFile function!");
        Log.d("PKEYREADER->",input.getAbsolutePath());
        Log.d("PKEYREADER->","" + input.length());

        Security.addProvider(new BouncyCastleProvider());
        Provider provider = Security.getProvider("BC");
        if (provider == null) {
            Log.d("AMAZING PRIVATE KEY PROVIDER","PROVIDER NOT FOUND!");
            throw new IllegalStateException("Bouncy Castle provider not found");
        }

        // we first check if the PEMParser can parse the file as is
        try (PEMParser pemParser = new PEMParser(new FileReader(input))) {
            Object object = pemParser.readObject();
            if (object instanceof PEMKeyPair) {
                // For EC keys this method of using the PEMParser has worked sucessfully and thus we should get a PEMKeyPair
                PEMKeyPair keyPair = (PEMKeyPair) object;
                Log.d("AMAZING PRIVATE KEY PROVIDER","The input firle fsor the PrivateKey DOES contain a PEM format key pair!");
                Log.d("AMAZING PRIVATE KEY PROVIDER","The private key info is: " + (String)( keyPair.getPrivateKeyInfo().toString() ) );
                Log.d("AMAZING PRIVATE KEY PROVIDER","The public key info is: " + (String)( keyPair.getPublicKeyInfo().toString() ) );
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
                PrivateKey privateKey = converter.getPrivateKey(keyPair.getPrivateKeyInfo());
                Log.d("PRIVATE KEY READING","The PrivateKey object to string is " + privateKey.toString() );
                Log.d("PRIVATE KEY READING","The PrivateKey object algorithm is " + privateKey.getAlgorithm() );
                return privateKey;
            } else {
                // Handle error if the object is not a PEMKeyPair
                Log.d("AMAZING PRIVATE KEY PROVIDER","The input file for the PrivateKey doesn't contain a PEM format key pair according to built-in PEMParser! Will try removing headers method!");
                return myPEMparser(input);
            }
        } catch (IOException e) {
            // Handle file read error
            Log.d("AMAZING PRIVATE KEY PROVIDER","The input file for the PrivateKey could not be read!");
            throw e;
        }

    }

    public static RSAPrivateKey myPEMparser(File input) throws IOException {

        // Reading the file into a byte array
        byte [] key_bytes = new byte[(int)input.length()];
        FileInputStream CandidateKeyFileInputStream = new FileInputStream(input);
        CandidateKeyFileInputStream.read(key_bytes);
        CandidateKeyFileInputStream.close();

        RSAPrivateKey rsa_key = null;

        // try without removing any bytes from the file (aka with the header and tail of the key included)
        try{
            //Log.d("myPEMparser","Trying to parse key file unchanged!");
            rsa_key = callingKeyFactory(key_bytes);
            //Log.d("myPEMparser","SUCCESSFULLY: parsed the key as is!");
            return rsa_key;
        }
        catch (Exception e){
            //Log.d("myPEMparser","FAILURE: The key file can't be parsed without any changes!");
            //e.printStackTrace();
        }

        //Log.d("myPEMparser","OK we will try removing header and tail and then parse the key!");

        // removing the header and tail
        String key_string = new String(key_bytes, StandardCharsets.UTF_8);
        key_string = key_string.replace("-----BEGIN PRIVATE KEY-----\n", "");
        key_string = key_string.replace("-----END PRIVATE KEY-----", "");

        // encoding to base 64
        byte [] encoded64 = Base64.decode(key_string,Base64.DEFAULT);

        // trying to see if the key is parsed this way
        try{
            //Log.d("myPEMparser","Trying to parse key file encoded to base 64 and headers removed!");
            rsa_key = callingKeyFactory(encoded64);
            //Log.d("myPEMparser","SUCCESSFULLY: parsed the key with encoding to base 64!");
            return rsa_key;
        }
        catch (Exception e){
            //Log.d("myPEMparser","FAILURE: The key file can't be parsed after encoded to base 64!");
            //e.printStackTrace();
        }

        // effort too pass the key without encoding to base 64 but rather just removing the headers
        byte [] plain_key_bytes = key_string.getBytes();

        // trying to see if the key is parsed this way
        try{
            //Log.d("myPEMparser","Trying to parse key file only with headers removed!");
            rsa_key = callingKeyFactory(encoded64);
            //Log.d("myPEMparser","SUCCESSFULLY: parsed the key only with headers removed!");
            return rsa_key;
        }
        catch (Exception e){
            //Log.d("myPEMparser","FAILURE: The key file can't be parsed with just the headers revoved!");
            //e.printStackTrace();
        }

        // maybe try with PEM reader?

        return null;

    }

    // PEM key parser from byte array
    public static RSAPrivateKey myPEMparser(byte [] key_bytes) throws IOException {

        RSAPrivateKey rsa_key = null;

        // try without removing any bytes from the file (aka with the header and tail of the key included)
        try{
            Log.d("myPEMparser","Trying to parse key file unchanged!");
            rsa_key = callingKeyFactory(key_bytes);
            Log.d("myPEMparser","SUCCESSFULLY: parsed the key as is!");
            return rsa_key;
        }
        catch (Exception e){
            Log.d("myPEMparser","FAILURE: The key file can't be parsed without any changes!");
            e.printStackTrace();
        }

        Log.d("myPEMparser","OK we will try removing header and tail and then parse the key!");

        // removing the header and tail
        String key_string = new String(key_bytes, StandardCharsets.UTF_8);
        key_string = key_string.replace("-----BEGIN PRIVATE KEY-----\n", "");
        key_string = key_string.replace("-----END PRIVATE KEY-----", "");

        // encoding to base 64
        byte [] encoded64 = Base64.decode(key_string,Base64.DEFAULT);

        // trying to see if the key is parsed this way
        try{
            Log.d("myPEMparser","Trying to parse key file encoded to base 64 and headers removed!");
            rsa_key = callingKeyFactory(encoded64);
            Log.d("myPEMparser","SUCCESSFULLY: parsed the key with encoding to base 64!");
            return rsa_key;
        }
        catch (Exception e){
            Log.d("myPEMparser","FAILURE: The key file can't be parsed after encoded to base 64!");
            e.printStackTrace();
        }

        // effort too pass the key without encoding to base 64 but rather just removing the headers
        byte [] plain_key_bytes = key_string.getBytes();

        // trying to see if the key is parsed this way
        try{
            Log.d("myPEMparser","Trying to parse key file only with headers removed!");
            rsa_key = callingKeyFactory(encoded64);
            Log.d("myPEMparser","SUCCESSFULLY: parsed the key only with headers removed!");
            return rsa_key;
        }
        catch (Exception e){
            Log.d("myPEMparser","FAILURE: The key file can't be parsed with just the headers revoved!");
            e.printStackTrace();
        }

        // maybe try with PEM reader?
        return null;

    }

    public static RSAPrivateKey callingKeyFactory(byte [] key_bytes) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException, KeyStoreException, CertificateException {
        PrivateKey privateKey = null;
        try{
            KeyFactory kf = KeyFactory.getInstance("RSA","AndroidOpenSSL");
            EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key_bytes);
            privateKey = kf.generatePrivate(keySpec);
            try {
                RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) privateKey;
                return rsaPrivateKey;
            }
            catch (Exception e){
                Log.d("callingKeyFactory","Private key is generated but it can't be cast to RSA key!");
                throw e;
            }
        }
        catch (Exception e){
            Log.d("callingKeyFactory","The key parsed!");
            throw e;
        }
    }

}

/* OLD CODE FOR KEY FROM FILE FOR READING THE PRIVATE WHICH WAS NOT WORKING

public static PrivateKey KeyFromFile(File candidate_key_file) throws Exception, FileNotFoundException, IOException, InvalidKeySpecException, NoSuchAlgorithmException {

// java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        // byte [] key_bytes = parsePEMFile(candidate_key_file);
        /*byte [] candidate_key_bytes = new byte[(int)candidate_key_file.length()];
        FileInputStream CandidateKeyFileInputStream = new FileInputStream(candidate_key_file);
        CandidateKeyFileInputStream.read(candidate_key_bytes);
        CandidateKeyFileInputStream.close();

        /*
        PrivateKey privateKey = null;
        try {
            KeyFactory kf = KeyFactory.getInstance("EC","BC");
            EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key_bytes);
            privateKey = kf.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException e) {
            Log.d("SIMPLE KEY ENCODING","Could not reconstruct the private key, the given algorithm could not be found.");
            e.printStackTrace();
            throw new NoSuchAlgorithmException(e);
        } catch (InvalidKeySpecException e) {
            Log.d("SIMPLE KEY ENCODING","Could not reconstruct the private key");
            e.printStackTrace();
            throw new InvalidKeySpecException(e);
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        Log.d("SIMPLE KEY ENCODING","The private key is not = " + privateKey.toString() );

        return (ECPrivateKey) privateKey;


        /*
        // Problem: Here there is a parsing error if we try to get the keySpec with:
        // PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(my_key_bytes);
        // then the key factory comes to a parsing error for the command:
        // keyFactory = KeyFactory.getInstance(CryptoAlgorithm);


        // Solution #1: (Doesn't work) try to remove the bytes for key headers (first and last line)
        // -----BEGIN EC PRIVATE KEY----- = 31 bytes (with endline)
        // The actual key bytes were = 167 (with 3 endlines - 65 bytes per line and last line 37)
        // -----END EC PRIVATE KEY----- = 29 bytes (with end of file)

        Log.d("KEY PARSE ERROR DEBUG","The size of the key bytes array is "+candidate_key_bytes.length+" bytes!");
        // The key byte array size (debugged-as read from system) is: 227 same as in the file system

        String key_bytes_string = new String(candidate_key_bytes);
        Log.d("KEY PARSE ERROR DEBUG","The key bytes raw as read from the file:\n"+key_bytes_string);

        // so we want bytes from indexes [31..(31+167-1)] only!
        // maybe try reading from byte 31 until a byte of '-' is found signifying the last line
        byte [] clean_candidate_key_bytes = new byte[167];
        for(int i=0;i<167;i++){
            clean_candidate_key_bytes[i] = candidate_key_bytes[31+i];
        }
        String clean_key_base64String = new String(clean_candidate_key_bytes);
        Log.d("KEY PARSE ERROR DEBUG","The clean key bytes are:\n" + clean_key_base64String );

        // Solution 1.1: Trying to remove the whitespaces from the key as well
        byte [] nows_clean_key_bytes = new byte[164];
        int ckey_index = 0;
        for(int i=0;i<164;i++){
            while( (char)(clean_candidate_key_bytes[ckey_index]) == '\n' ){
                ckey_index++;
            }
            if(ckey_index >= 167){
                break;
            }
            nows_clean_key_bytes[i] = clean_candidate_key_bytes[ckey_index];
            ckey_index++;
        }

        String nows_clean_key_bytes_stirng = new String(nows_clean_key_bytes);
        Log.d("KEY PARSE ERROR DEBUG","The clean key bytes no ws are:\n" + nows_clean_key_bytes_stirng );

        // PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(candidate_key_bytes);
        // PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(clean_candidate_key_bytes); // removing headers
        // PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(nows_clean_key_bytes); // removing whitspaces

        ECPrivateKey result = null;
        // Log.d("Security Provider", "The provider is = " + java.security.Security.getProvider(CryptoAlgorith).toString() );
        // Solution #2: Try another security provider (i.e.Bouncy Castle) which might support the elliptic curve crypto
        try {
            Security.addProvider(new BouncyCastleProvider());
            PemReader pemReader = new PemReader(new FileReader(candidate_key_file));
            PemObject pemObject = pemReader.readPemObject();
            byte[] keyBytes = pemObject.getContent();
            PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(keyBytes);
            // X9ECParameters curveParams = org.bouncycastle.asn1.x9.ECNamedCurveTable.getByOID(org.bouncycastle.asn1.sec.SECObjectIdentifiers.secp256r1);
            X962Parameters params = X962Parameters.getInstance(privateKeyInfo.getPrivateKeyAlgorithm().getParameters());
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyInfo.parsePrivateKey().toASN1Primitive().getEncoded());
            // keyFactory = KeyFactory.getInstance(CryptoAlgorithm,provider_name);
            KeyFactory keyFactory = KeyFactory.getInstance(params.isNamedCurve() ? "EC" : "ECDSA", "BC");
            // PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(candidate_key_bytes);
            // ECNamedCurveParameterSpec curveSpec = ECNamedCurveTable.getParameterSpec(curveName); // needed only when generating key pairs

            // PrivateKey privateKey = keyFactory.generatePrivate(new ECPrivateKeySpec(keyBytes,null));
            //PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            //pemReader.close();

            // result = (ECPrivateKey) keyFactory.generatePrivate(new ECPrivateKeySpec(keyBytes)); // keyFactory.generatePrivate(keySpec);
            Log.d("KEY PARSE ERROR DEBUG","SUCCESS! The result is generated! The keyFactory result is "+ result );
            Log.d("KEY PARSE ERROR DEBUG","The keyFactory result.toString() is "+ result.toString() );
            Log.d("KeyFromFile","A private key has been loaded successfully from the file!");
        } catch (NoSuchAlgorithmException e) {
            Log.d("KeyFromFile","The algorithm requested is non-existent! Algorithm name " + CryptoAlgorithm);
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            Log.d("KeyFromFile","Invalid key specifications provided for private key from file!");
            throw new RuntimeException(e);
        } catch (NoSuchProviderException e) {
            Log.d("KeyFromFile","Security provider doesn't exist!");
            throw new RuntimeException(e);
        }

        return result;

 */


//implementation 'org.bouncycastle:bcprov-jdk18on:1.73'
//implementation 'org.bouncycastle:bcpkix-jdk18on:1.73'
//implementation 'org.bouncycastle:bcutil-jdk18on:1.73'

//try {
//
//        String curveName = "prime256v1";
//        ECParameterSpec ecParameterSpec = ECNamedCurveTable.getParameterSpec(curveName);
//
//        // Following code was successful when trying to generate a key on our own rather getting one from the system
//        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", provider);
//        KeyPair keyPair = keyPairGenerator.generateKeyPair();
//        Log.d("AMAZING PRIVATE KEY READER","Private key: " + keyPair.getPrivate());
//        Log.d("AMAZING PRIVATE KEY READER","Public key: " + keyPair.getPublic());
//        return keyPair.getPrivate();
//
//        }
//        catch (Exception e){
//        e.printStackTrace();
//        throw new Exception(e);
//        }
