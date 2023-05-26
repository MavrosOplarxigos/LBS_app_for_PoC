package com.example.lbs_app_for_poc;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Scanner;

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
    private static final String CryptoAlgorith = "EC";
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

    /*
    This function tries to load the certificates from standard file locations.
    If those files do not exist an exception is thrown to indicate that.

    This function should run when the app starts and whenever the user picks other credentials and clicks to save the new ones.
     */
    public static void LoadCertificates() throws FileNotFoundException, IOException {

        // loading my private key
        File my_key_file = new File(absolute_path,my_key_path);
        byte [] my_key_bytes = new byte[(int)my_key_file.length()];
        FileInputStream keyFileInputStream = new FileInputStream(my_key_file);
        keyFileInputStream.read(my_key_bytes);
        keyFileInputStream.close();
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(my_key_bytes);
        KeyFactory keyFactory;
        try {
            keyFactory = KeyFactory.getInstance(CryptoAlgorith);
            my_key = (ECPrivateKey) keyFactory.generatePrivate(keySpec);
            Log.d("CRED LOADING","The private key has been loaded successfully!");
        } catch (NoSuchAlgorithmException e) {
            Log.d("CRED LOADING","The algorithm requested is non-existent! Algorithm name " + CryptoAlgorith);
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            Log.d("CRED LOADING","Invalid key specifications provided for user's private key!");
            throw new RuntimeException(e);
        }

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
