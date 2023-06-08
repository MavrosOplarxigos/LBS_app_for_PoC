package com.example.lbs_app_for_poc;
import android.util.Log;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

public class AmazingPrivateKeyReader {

    public static PrivateKey KeyFromFile(File input) throws Exception {

        Log.d("AMAZING PRIVATE KEY READER","Entered the original keyFromFile function!");
        // return KeyFactoryMethodPrivateKey(input);

        Security.addProvider(new BouncyCastleProvider());
        Provider provider = Security.getProvider("BC");
        if (provider == null) {
            Log.d("AMAZING PRIVATE KEY PROVIDER","PROVIDER NOT FOUND!");
            throw new IllegalStateException("Bouncy Castle provider not found");
        }

        try (PEMParser pemParser = new PEMParser(new FileReader(input))) {
            Object object = pemParser.readObject();
            if (object instanceof PEMKeyPair) {
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
                Log.d("AMAZING PRIVATE KEY PROVIDER","The input file for the PrivateKey doesn't contain a PEM format key pair according to pemParser! Will try keyfactory method!");
                return KeyFactoryMethodPrivateKey(input);
            }
        } catch (IOException e) {
            // Handle file read error
            Log.d("AMAZING PRIVATE KEY PROVIDER","The input file for the PrivateKey could not be read!");
            return null;
        }

    }

    public static PrivateKey KeyFactoryMethodPrivateKey(File input) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {
        try {
            byte[] privateKeyBytes = Files.readAllBytes(input.toPath());
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            Security.addProvider(new BouncyCastleProvider());
            KeyFactory keyFactory = KeyFactory.getInstance("RSA","BC");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            Log.d("AMAZING PRIVATE KEY PROVIDER","The key algorithm after it is read is " + privateKey.getAlgorithm() );
            return privateKey;
        } catch (Exception e) {
            throw e;
        }

    }

}

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
