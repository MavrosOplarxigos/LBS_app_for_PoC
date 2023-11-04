package com.example.lbs_app_for_poc;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.MGF1ParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.ExemptionMechanismException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/*
Generating pseudonymous certificates:

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
    public static final int MAX_NUMBER_OF_PSEUDONYMOUS_CERTS = 4;

    // The keys "once loaded" are static because we don't want to have a specific instance of the class
    // but rather just use the class overall to do all the crypto that we need.

    // NEW VERSION VARIABLES (where we don't save the credentials but instead download them every time)
    public static long NTP_TIME_OFFSET = 0; // NTP_SERVER_TIME - OLD_SYSTEM_TIME (we want to sync without changing the system time)
    public static final int TIMESTAMP_BYTES = 8; // We need 8 bytes for a timestamp

    public static PrivateKey my_key = null; // Only 1 private key
    public static X509Certificate my_cert = null; // Only 1 main certificate
    public static X509Certificate CA_cert = null; // Only 1 CA certificate

    // The node is using these pseudo creds to query not to serve.
    // For serving I can use my real certificate since I have nothing to hide.
    public static ArrayList<X509Certificate> pseudonymous_certificates = null; // Multiple pseudonymous certificates
    public static ArrayList<PrivateKey> pseudonymous_privates = null;

    // public static X509Certificate peer_cert = null; // The certificate now will be kept in TCPserverThread and ConnectivityConfiguration

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

    /*
    public static void save_peer_cert(byte [] s) throws CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance(CertificateStandard);
        peer_cert = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(s));
        return;
    }
    */

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

    public static byte [] encryptWithPeerKey(byte [] input, X509Certificate peer_cert) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {

        // RSA-OAEP / SHA-256 hashing / MGF1 mask generation
        // PKCS#1 v2.1 (RSA Cryptography Standard) by RSA Laboratories
        // RFC 8017 (PKCS #1: RSA Cryptography Specifications) published by the Internet Engineering Task Force (IETF).

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, peer_cert.getPublicKey());

        // Determine the maximum block size for encryption
        int blockSize = cipher.getBlockSize();
        // Log.d("CRYPTOBLOCKS","The ENCRYPT block size is " + blockSize);

        // Calculate the size of the output array
        int outputSize = (int) Math.ceil((double) input.length / blockSize) * cipher.getOutputSize(blockSize);
        byte[] enc_data = new byte[outputSize];

        // Encrypt block by block
        int inputOffset = 0;
        int outputOffset = 0;
        while (inputOffset < input.length) {
            int inputLength = Math.min(blockSize, input.length - inputOffset);
            byte[] inputBlock = new byte[inputLength];
            System.arraycopy(input, inputOffset, inputBlock, 0, inputLength);
            byte[] encryptedBlock = cipher.doFinal(inputBlock);
            System.arraycopy(encryptedBlock, 0, enc_data, outputOffset, encryptedBlock.length);
            inputOffset += blockSize;
            outputOffset += encryptedBlock.length;
        }

        return enc_data;

    }

    public static byte [] decryptWithOwnKey(byte [] input, int OriginalSize) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        return decryptWithKey(input,my_key,OriginalSize);
    }

    public static byte [] decryptWithKey(byte [] input, PrivateKey key_given, int OriginalSize) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {

        // Create the RSA cipher with OAEP padding
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        /*OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256",  // Hash algorithm for OAEP
                "MGF1",     // Mask Generation Function
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
        );
        // OAEP parameters with SHA-256 and MGF1 padding
        Log.d("OAEPdefault","Digest = " + OAEPParameterSpec.DEFAULT.getDigestAlgorithm());
        Log.d("OAEPdefault","MaskGeneration = " + OAEPParameterSpec.DEFAULT.getMGFAlgorithm());
        Log.d("OAEPdefault","MaskGenerationParams = " + OAEPParameterSpec.DEFAULT.getMGFParameters().toString() );
        Log.d("OAEPdefault","Psource = " + OAEPParameterSpec.DEFAULT.getPSource().toString() );*/
        cipher.init(Cipher.DECRYPT_MODE, key_given);

        // Determine the maximum block size for decryption
        int blockSize = cipher.getBlockSize();
        // Log.d("CRYPTOBLOCKS","The DECRYPT block size is " + blockSize);

        // Calculate the size of the output array
        int outputSize = (int) Math.ceil((double) input.length / blockSize) * cipher.getOutputSize(blockSize);
        byte[] dec_data = new byte[outputSize];

        // Decrypt block by block
        int inputOffset = 0;
        int outputOffset = 0;
        while (inputOffset < input.length) {
            int inputLength = Math.min(blockSize, input.length - inputOffset);
            byte[] inputBlock = new byte[inputLength];
            System.arraycopy(input, inputOffset, inputBlock, 0, inputLength);
            byte[] decryptedBlock = cipher.doFinal(inputBlock);
            System.arraycopy(decryptedBlock, 0, dec_data, outputOffset, decryptedBlock.length);
            inputOffset += blockSize;
            outputOffset += decryptedBlock.length;
        }

        byte [] DecryptedDataWithoutPadding = new byte[OriginalSize];
        System.arraycopy(dec_data,0,DecryptedDataWithoutPadding,0,OriginalSize);

        return DecryptedDataWithoutPadding;
    }

    public static CryptoTimestamp getSignedTimestamp() throws NoSuchAlgorithmException, SignatureException, NoSuchProviderException, InvalidKeyException {
        CryptoTimestamp answer = new CryptoTimestamp();
        try {
            long timestamp = System.currentTimeMillis() + NTP_TIME_OFFSET; // We want to use the NTP time
            byte [] timestampBytes = ByteBuffer.allocate(Long.BYTES).putLong(timestamp).array();
            answer.timestamp = timestampBytes;
            answer.signed_timestamp = signPrivateKeyByteArray(timestampBytes);
            return answer;
        }
        catch (Exception e){
            Log.d("Timestamp Signing","Could not sign the timestamp byte array!");
            throw e;
        }
    }

    public static CryptoTimestamp makeTimstampSignedWithConcatenationWithKey(CryptoTimestamp old_timestamp, byte [] toConcatenate, PrivateKey my_key) throws NoSuchAlgorithmException, SignatureException, NoSuchProviderException, InvalidKeyException {
        CryptoTimestamp answer = new CryptoTimestamp();
        try{
            answer.timestamp = old_timestamp.timestamp;
            int concatenation_size = toConcatenate.length + answer.timestamp.length;
            byte [] concatenation = new byte[concatenation_size];
            System.arraycopy(answer.timestamp,0,concatenation,0,answer.timestamp.length);
            System.arraycopy(toConcatenate,0,concatenation,answer.timestamp.length,toConcatenate.length);
            answer.signed_timestamp_conncatenated_with_info = signByteArrayWithPrivateKey(concatenation,my_key);
            return answer;
        }
        catch (Exception e){
            Log.d("makeTimstampSignedWithConcatenationWithKey","Could not sign the timestamp byte array!");
            throw e;
        }
    }

    public static CryptoTimestamp getSignedTimestampWithConcatenationWithKey(byte [] toConcatenate, PrivateKey my_key) throws NoSuchAlgorithmException, SignatureException, NoSuchProviderException, InvalidKeyException {
        CryptoTimestamp answer = new CryptoTimestamp();
        try {
            long timestamp = System.currentTimeMillis() + NTP_TIME_OFFSET; // We want to use the NTP time
            byte [] timestampBytes = ByteBuffer.allocate(Long.BYTES).putLong(timestamp).array();
            answer.timestamp = timestampBytes;
            int concatenation_size = toConcatenate.length + timestampBytes.length;
            byte [] concatenation = new byte[concatenation_size];
            System.arraycopy(timestampBytes, 0, concatenation, 0, timestampBytes.length);
            System.arraycopy(toConcatenate, 0, concatenation, timestampBytes.length, toConcatenate.length);
            answer.signed_timestamp_conncatenated_with_info = signByteArrayWithPrivateKey(concatenation,my_key);
            return answer;
        }
        catch (Exception e){
            Log.d("Timestamp Signing With concatenation","Could not sign the timestamp byte array!");
            throw e;
        }
    }

    public static CryptoTimestamp getSignedTimestampWithKey(PrivateKey key_to_use) throws NoSuchAlgorithmException, SignatureException, NoSuchProviderException, InvalidKeyException {
        CryptoTimestamp answer = new CryptoTimestamp();
        try {
            long timestamp = System.currentTimeMillis() + NTP_TIME_OFFSET; // We want to use the NTP time
            byte [] timestampBytes = ByteBuffer.allocate(Long.BYTES).putLong(timestamp).array();
            answer.timestamp = timestampBytes;
            answer.signed_timestamp = signByteArrayWithPrivateKey(timestampBytes,key_to_use);
            return answer;
        }
        catch (Exception e){
            Log.d("Timestamp Signing","Could not sign the timestamp byte array!");
            throw e;
        }
    }

    public static boolean isTimestampFresh(byte [] timestamp){

        if(SearchingNodeFragment.EXPERIMENT_IS_RUNNING){
            return true;
        }

        long currentTime = System.currentTimeMillis() + NTP_TIME_OFFSET; // NTP time
        long timestampValue = ByteBuffer.wrap(timestamp).getLong();

        // this should be impossible
        if( currentTime < timestampValue ){
            return false;
        }

        long difference = currentTime - timestampValue;
        // adapted the strictness of the timestamp based on whether we are running an experiment or not
        long freshnessThreshold = SearchingNodeFragment.EXPERIMENT_IS_RUNNING ? difference : 3500;

        return difference <= freshnessThreshold;
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

    public static byte [] signByteArrayWithPrivateKey(byte [] input, PrivateKey key_to_use) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {
        Signature signature = CryptoChecks.getSignatureInstanceByAlgorithm(key_to_use.getAlgorithm());
        if(signature == null){
            Log.d("Signing","ERROR: No signature instance from the key provided!");
        }
        signature.initSign(key_to_use);
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

    public static String getCommonName(X509Certificate certificate){
        String DN = certificate.getSubjectDN().getName();
        return getCommonName(DN);
    }

    public static String getCommonName(String DN){
        String[] dnComponents = DN.split(",");
        for (String dnComponent : dnComponents) {
            if (dnComponent.trim().startsWith("CN=")) {
                // Extract and return the CN value
                return dnComponent.trim().substring(3);
            }
        }
        return "None!";
    }

    public static boolean checkFieldsClientHello(byte [][] arr, String receiver){

        if(arr.length > 4){
            Log.d("TCP " + receiver,"CHECKS: More than 4 fields received in Hello. Dropping connection!");
            return false;
        }

        if(arr.length < 4){
            Log.d("TCP " + receiver,"CHECKS: Less than 4 fields received in Hello. Dropping connection!");
            return false;
        }

        // [HELLO]:5 | [CERTIFICATE BYTES]:~2K | [Timestamp]:8 | [Signed Timestamp]: 256

        // Check timestamp freshness
        if( !InterNodeCrypto.isTimestampFresh(arr[2]) ){
            Log.d("TCP " + receiver,"CHECKS: The timestamp is not fresh!");
            return false;
        }

        // HELLO
        if( !( Arrays.equals(arr[0], "HELLO".getBytes()) ) ) {
            return false;
        }

        // CERTIFICATE BYTES
        // THE FOLLOWING CHECK SHOULD NOT BE CARRIED OUT IF THE CERTIFICATE WE GET IS ONLY THE ENCODED PART
        /*if( !( arr[1].startsWith("-----BEGIN CERTIFICATE-----") && arr[1].endsWith("-----END CERTIFICATE-----") ) ){
            return false;
        }*/
        // Let's check that the certificate can be read
        X509Certificate cert;
        try{
            // cert = InterNodeCrypto.CertFromString(arr[1]);
            // For the certificate we get its own byte array
            cert = InterNodeCrypto.CertFromByteArray(arr[1]);
        }
        catch(Exception e){
            Log.d("TCP " + receiver,"CHECKS: Certificate Field invalid!");
            e.printStackTrace();
            return false;
        }

        // CHECK THAT THE CERTIFICATE IS SIGNED BY THE CA
        if( !(CryptoChecks.isCertificateSignedBy(cert,InterNodeCrypto.CA_cert)) ){
            Log.d("TCP " + receiver,"CHECKS: The received certificate is not signed by the CA!");
            return false;
        }

        // CHECK NONCE SIZE
        if( arr[2].length != InterNodeCrypto.TIMESTAMP_BYTES ){
            Log.d("TCP " + receiver,"CHECKS: Incorrect timestampe size!");
            return false;
        }

        // CHECK THAT THE timestamp IS SIGNED CORRECTLY
        try {
            if ( !(CryptoChecks.isSignedByCert(arr[2], arr[3], cert)) ) {
                Log.d("TCP " + receiver,"CHECKS: The signed timestamp is NOT SIGNED by the public key of the certificate!");
                return false;
            }
        }
        catch (Exception e){
            Log.d("TCP " + receiver,"CHECKS: Can't verify the timestamp signature!");
            e.printStackTrace();
            return false;
        }

        //Log.d("TCP_HELLO_CHECKS" + receiver, "The fields check out and they are the following: ");
        // [HELLO]:5 | [CERTIFICATE BYTES]:~2K | [timestamp]: 8 | [SIGNED_NONCE]: 256
        //Log.d("TCP_HELLO_CHECKS" + receiver, "HELLO = " + TCPhelpers.byteArrayToDecimalString(arr[0]) );
        //Log.d("TCP_HELLO_CHECKS" + receiver, "CERT = " + InterNodeCrypto.getCertDetails(cert) );
        //Log.d("TCP_HELLO_CHECKS" + receiver, "timestamp = " + TCPhelpers.byteArrayToDecimalString(arr[2]) );
        //Log.d("TCP_HELLO_CHECKS" + receiver, "signed timestamp = " + TCPhelpers.byteArrayToDecimalString(arr[3]) );

        return true;

    }

}