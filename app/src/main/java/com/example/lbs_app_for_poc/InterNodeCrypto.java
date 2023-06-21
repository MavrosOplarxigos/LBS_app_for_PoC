package com.example.lbs_app_for_poc;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Objects;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

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
    public static final String my_key_path = "my.key";
    public static final String my_cert_path = "my.crt";
    public static final String ca_cert_path = "ca.crt";
    private static final String CryptoAlgorithm = "RSA";
    // private static final String curveName = "prime256v1";
    private static final String provider_name = "BC";
    private static final String CertificateStandard = "X509";

    // The keys "once loaded" are static because we don't want to have a specific instance of the class
    // but rather just use the class overall to do all the crypto that we need.
    public static PrivateKey my_key = null;
    public static X509Certificate my_cert = null; // TODO: Don't use X509 But rather use a customized certificate class
                                                  // TODO: Ideally implement with both X509 for the case of standard compliance and future development AND
                                                  //  with a smaller version of a certificate with less data to communicate between nodes when communicating
    public static X509Certificate CA_cert = null;
    public static X509Certificate peer_cert = null;

    private static void copyFile(File source, File destFile){

        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[(int) source.length()];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.flush();

        } catch (IOException e) {
            Log.d("CRED LOADING","Problem when trying to copy the file " + source.getAbsolutePath() );
            e.printStackTrace();
        }

        // Compare file sizes
        long sourceSize = source.length();
        long destSize = destFile.length();
        if (sourceSize == destSize) {
            Log.d("CRED LOADING", "File copied successfully");
        } else {
            Log.d("CRED LOADING", "File copy failed. Source size: " + sourceSize + ", Destination size: " + destSize);
        }

    }

    /*
    This function is called when the user selects the certificate files from the system.
    NOTE: Because of the copying operations we may need to call this function within a Thread. Since files are small though no problem was encountered so far.
    We should also add a timeout to the thread and based on that timeout or the thread exiting output something
    to inform of whether the credentials were saved/updated successfully or not.
    Then this function copies these files to a standard path and calls LoadCertificates to load them.
     */
    public static void SaveCredentials(File key, File cert, File caCert) {

        Log.d("Save Certs","Entering the save certficates function!");

        // Copy the files to the locations we want them to be
        copyFile(key,new File(absolute_path,my_key_path));
        copyFile(cert,new File(absolute_path,my_cert_path));
        copyFile(caCert,new File(absolute_path,ca_cert_path));

        Log.d("CRED LOADING","The files were copied to the standard locations successfully!");

        // Load the files all over again for testing that loading them is successful
        try{
            LoadCredentials();
        } catch (FileNotFoundException e) {
            Log.d("CRED LOADING","A file is missing!");
            throw new RuntimeException(e);
        } catch (IOException e) {
            Log.d("CRED LOADING","A file could not be read!");
            throw new RuntimeException(e);
        }

    }

    public static void save_peer_cert(byte [] s) throws CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance(CertificateStandard);
        peer_cert = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(s));
        return;
    }

    public static X509Certificate CertFromByteArray(byte [] s) throws CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance(CertificateStandard);
        return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(s));
    }

    public static X509Certificate CertFromString(String s) throws CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance(CertificateStandard);
        byte [] certBytes = s.getBytes();
        return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certBytes));
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

    public static PrivateKey KeyFromFile(File candidate_key_file) throws Exception, FileNotFoundException, IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        return AmazingPrivateKeyReader.KeyFromFile(candidate_key_file);
    }

    /*
    This function tries to load the certificates from standard file locations. If they don't exist then we throw an exception.
     */
    public static void LoadCredentials() throws FileNotFoundException, IOException {

        Log.d("Load Certificates function!","Entered the load certificates funciton!");

        // loading my private key
        File my_key_file = new File(absolute_path,my_key_path);
        try {
            my_key = AmazingPrivateKeyReader.KeyFromFile(my_key_file);
            Log.d("CRED LOADING","The private key has been loaded successfully!");
        } catch (Exception e) {
            Log.d("CRED LOADING","The private key could not be loaded from the file!");
            throw new RuntimeException(e);
        }

        // loading my certificate
        File my_cert_file = new File(absolute_path,my_cert_path);
        FileInputStream certFileInputStream = new FileInputStream(my_cert_file);
        try {
            Log.d("CRED LOADING","Will now try load the user certificate using X509 standard!");
            // CertificateFactory.getInstance("X509");
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
    public static boolean checkMyCreds() throws NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, InvalidKeySpecException, BadPaddingException, InvalidKeyException, SignatureException {
        return checkCreds(InterNodeCrypto.CA_cert,InterNodeCrypto.my_cert,InterNodeCrypto.my_key);
    }

    public static boolean checkCreds(X509Certificate CA_certificate, X509Certificate MY_certificate, PrivateKey MY_key) throws NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, InvalidKeySpecException, BadPaddingException, InvalidKeyException, SignatureException {
        boolean result = true;
        // DONE: check if the certificate is signed by the CA
        result = result && CryptoChecks.isCertificateSignedBy(MY_certificate,CA_certificate);
        if(Objects.equals(MY_key.getAlgorithm(), "EC")){
            result = result && CryptoChecks.isSigningAndVerifyingWorking(MY_certificate,MY_key);
        } else if (Objects.equals(MY_key.getAlgorithm(), "RSA")) {
            result = result && CryptoChecks.isSigningAndVerifyingWorking(MY_certificate,MY_key);
            result = result && CryptoChecks.isEncryptAndDecryptWorking(MY_certificate,MY_key);
        }
        else{
            Log.d("CredentialCheck","No check implemented for " + MY_key.getAlgorithm() + "! We have to implement one!");
        }
        return result;
    }

    public static byte [] signPrivateKeyByteArray(byte [] input) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {

        if(my_cert == null){
            Log.d("Cert Signing","My certificate is null how am I supposed to sign data???");
        }

        Signature signature = CryptoChecks.getSignatureInstanceByAlgorithm(my_key.getAlgorithm());
        if(signature == null){
            Log.d("Cert signing","The signatuere I receive is null!");
        }

        signature.initSign(my_key);
        signature.update(input);
        return signature.sign();

    }

    public static String getCertDetails(@NonNull File certificate) throws FileNotFoundException {
        X509Certificate temp_cert = null;
        try{
            // File certFile = new File(certificate.toString());
            // We can't read this using input stream?
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
        if (certificate.getSubjectDN() == null){
            Log.d("CRED DETAILS", "This certificate has no principal! Attempting to send issuer instead!");
            if( certificate.getIssuerDN() != null ) {
                return "Issuer: " + certificate.getIssuerDN().toString() + '\n';

            }
            else{
                Log.d("CRED DETAILS","This certificate has now issuer either!");
                return "";
            }
        }
        else{
            Log.d("CRED DETAILS","Successfully retrieved Subject DN");
            return "Subject: " + certificate.getSubjectDN().toString() + '\n' + "Algorithm: " + certificate.getPublicKey().getAlgorithm();
        }
    }

}