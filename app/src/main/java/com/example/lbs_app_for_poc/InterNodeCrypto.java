package com.example.lbs_app_for_poc;
import android.util.Log;
import androidx.annotation.NonNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Scanner;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;


/*
TODO: Make the certificate generation into a bash script so that the user can test PoC.
This class will contain all the cryptography functions that we need to establish secure communication between the nodes of the scheme.
We have used OpenSSL to generate the credentials and ECDSA is the crypto-system that was selected.
The process to create the credentials and certificates was the following:
1) The CA's certificate/key were generated with the following command:
$ openssl req -x509 -newkey ec:<(openssl ecparam -name prime256v1) -keyout ca.key -out ca.crt -days 365 -nodes
2) The certificates/creds for the nodes were generated as follows:
$ openssl ecparam -name prime256v1 -genkey -noout -out node.key
$ openssl req -new -key node.key -out node.csr
$ openssl x509 -req -in node.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out node.crt -days 365
 */
public class InterNodeCrypto {

    public static File absolute_path;

    // The following filenames are set to be the standard ones that we will use that's why they are final.
    private static final String my_key_path = "my.key";
    private static final String my_cert_path = "my.crt";
    private static final String ca_cert_path = "ca.crt";
    private static final String CryptoAlgorithm = "ECDSA";
    private static final String curveName = "prime256v1";
    private static final String provider_name = "BC";
    private static final String CertificateStandard = "X.509";

    // The keys once loaded are static because we don't want to have a specific instance of the class
    // but rather just use the class overall to do all the crypto that we need.
    public static ECPrivateKey my_key = null;
    public static X509Certificate my_cert = null; // TODO: Don't use X509 But rather use a customized certificate class
                                                  // TODO: Ideally implement with both X509 for the case of standard compliance and future development AND
                                                  //  with a smaller version of a certificate with less data to communicate between nodes when communicating
    public static X509Certificate CA_cert = null;

    private static void copyFile(File source, File destFile){

        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            Log.d("CRED LOADING","Problem when trying to copy the file " + source.getAbsolutePath() );
            e.printStackTrace();
        }

    }

    /*
    This function is called when the user selects the certificate files from the system.
    TODO: Because of the copying we need to call this function within a Thread.
    We should also add a timeout to the thread and based on that timeout or the thread exiting output something
    to inform of whether the credentials were saved/updated successfully or not.
    Then this function copies these files to a standard path and calls LoadCertificates to load them.
     */
    public static void SaveCertificates(File key, File cert, File caCert) {

        // Copy the files to the locations we want them to be
        copyFile(key,new File(absolute_path,my_key_path));
        copyFile(cert,new File(absolute_path,my_cert_path));
        copyFile(caCert,new File(absolute_path,ca_cert_path));

        Log.d("CRED LOADING","The files were copied to the standard locations successfully!");

        // Load the files all over again
        try{
            LoadCertificates();
        } catch (FileNotFoundException e) {
            Log.d("CRED LOADING","A file is missing!");
            throw new RuntimeException(e);
        } catch (IOException e) {
            Log.d("CRED LOADING","A file could not be read!");
            throw new RuntimeException(e);
        }

    }

    public static X509Certificate CertFromFile(File candidate_cert_file) throws FileNotFoundException, IOException{
        FileInputStream certFileInputStream = new FileInputStream(candidate_cert_file);
        X509Certificate result = null;
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance(CertificateStandard);
            result = (X509Certificate) certificateFactory.generateCertificate(certFileInputStream);
            certFileInputStream.close();
            Log.d("CertFromFile","A certificate can be loaded successfully from the file!");
        } catch (CertificateException e) {
            Log.d("CertFromFile","The certificate standard requested from CertificateFactory doesn't exist: " + CertificateStandard);
            throw new RuntimeException(e);
        }
        return result;
    }

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

    public static ECPrivateKey KeyFromFile(File candidate_key_file) throws FileNotFoundException, IOException{

        byte [] key_bytes = parsePEMFile(candidate_key_file);
        /*byte [] candidate_key_bytes = new byte[(int)candidate_key_file.length()];
        FileInputStream CandidateKeyFileInputStream = new FileInputStream(candidate_key_file);
        CandidateKeyFileInputStream.read(candidate_key_bytes);
        CandidateKeyFileInputStream.close();*/

        PrivateKey privateKey = null;
        try {
            KeyFactory kf = KeyFactory.getInstance("EC");
            EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key_bytes);
            privateKey = kf.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException e) {
            Log.d("SIMPLE KEY ENCODING","Could not reconstruct the private key, the given algorithm could not be found.");
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            Log.d("SIMPLE KEY ENCODING","Could not reconstruct the private key");
            e.printStackTrace();
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

        return result;*/

    }

    /*
    This function tries to load the certificates from standard file locations.
    If those files do not exist an exception is thrown to indicate that.

    This function should run when the app starts and whenever the user picks other credentials and clicks to save the new ones.
     */
    public static void LoadCertificates() throws FileNotFoundException, IOException {

        // loading my private key
        /*
        File my_key_file = new File(absolute_path,my_key_path);
        byte [] my_key_bytes = new byte[(int)my_key_file.length()];
        FileInputStream keyFileInputStream = new FileInputStream(my_key_file);
        keyFileInputStream.read(my_key_bytes);
        keyFileInputStream.close();
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(my_key_bytes);
        KeyFactory keyFactory;
        try {
            keyFactory = KeyFactory.getInstance(CryptoAlgorithm);
            my_key = (ECPrivateKey) keyFactory.generatePrivate(keySpec);
            Log.d("CRED LOADING","The private key has been loaded successfully!");
        } catch (NoSuchAlgorithmException e) {
            Log.d("CRED LOADING","The algorithm requested is non-existent! Algorithm name " + CryptoAlgorithm);
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            Log.d("CRED LOADING","Invalid key specifications provided for user's private key!");
            throw new RuntimeException(e);
        }
        */

        // loading my certificate
        File my_cert_file = new File(absolute_path,my_cert_path);
        FileInputStream certFileInputStream = new FileInputStream(my_cert_file);
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance(CertificateStandard);
            my_cert = (X509Certificate) certificateFactory.generateCertificate(certFileInputStream);
            certFileInputStream.close();
            Log.d("CRED LOADING","The user certificate has been loaded successfully!");
        } catch (CertificateException e) {
            Log.d("CRED LOADING","The certificate standard requested from CertificateFactory doesn't exist: " + CertificateStandard);
            throw new RuntimeException(e);
        }

        // loading the CA's certificate
        File ca_cert_file = new File(absolute_path,ca_cert_path);
        FileInputStream caCertFileInputStream = new FileInputStream(ca_cert_file);
        try{
            CertificateFactory caCertificateFactory = CertificateFactory.getInstance(CertificateStandard);
            CA_cert = (X509Certificate) caCertificateFactory.generateCertificate(caCertFileInputStream);
            caCertFileInputStream.close();
            Log.d("CRED LOADING","The CA certificate has been loaded successfully!");
        } catch (CertificateException e){
            Log.d("CRED LOADING","The certificate standard requested from CertificateFactory doesn't exist: " + CertificateStandard);
            throw  new RuntimeException(e);
        }

    }

    /*
    * TODO: Implement this function to check certificate issuer and CN and private key matching cert
    * */
    public static boolean checkMyCreds(){
        return checkCreds(InterNodeCrypto.CA_cert,InterNodeCrypto.my_cert,InterNodeCrypto.my_key);
    }

    public static boolean checkCreds(X509Certificate CA_certificate, X509Certificate MY_certificate, ECPrivateKey MY_key){
        // TODO: check if the certificate is signed by the CA

        // TODO: check if the key can sign the certificate

        return true;
    }

    public static String getCertDetails(@NonNull File certificate) throws FileNotFoundException {
        X509Certificate temp_cert = null;
        try{
            // File certFile = new File(certificate.toString());
            // We can't rread this using input stream?
            FileInputStream fileInputStream = new FileInputStream(certificate);
            CertificateFactory caCertificateFactory = CertificateFactory.getInstance(CertificateStandard);
            temp_cert = (X509Certificate) caCertificateFactory.generateCertificate(fileInputStream);
            fileInputStream.close();
            Log.d("CRED DETAILS","The certificate has been loaded successfully!");
        } catch (CertificateException e){
            Log.d("CRED DETAILS","The certificate standard requested from CertificateFactory doesn't exist: " + CertificateStandard);
            throw  new RuntimeException(e);
        } catch (FileNotFoundException e){
            Log.d("CRED DETAILS","The input file could not be retrieved!");
            throw new FileNotFoundException();
        } catch (IOException e) {
            Log.d("CRED DETAILS","The file input stream on the certificate could not be closed!");
            throw new RuntimeException(e);
        }

        return getCertDetails(temp_cert);
    }

    /*
    TODO: Implement this function so that the details of the certificate are return as a String (issuer, owner, etc.)
    */
    public static String getCertDetails(@NonNull X509Certificate certificate){
        if (certificate == null){
            Log.d("CRED DETAILS","How does null certificate reach this point!");
            return "";
        }
        if (certificate.getSubjectDN() == null){
            Log.d("CRED DETAILS", "This certificate has no prinicpal! Attempting to send issuer instead!");
            if( certificate.getIssuerDN() != null ) {
                return certificate.getIssuerDN().toString();
            }
            else{
                Log.d("CRED DETAILS","This certificate has now issuer either!");
                return "";
            }
        }
        // Now we need to put the details in a String
        // TODO: put more details other than the issuer DN
        String answer = "";
        answer += "Subject: " + certificate.getSubjectDN().toString() + '\n';

        Log.d("CRED DETAILS","Successfully retrieved subject DN");

        return answer;
    }

}
