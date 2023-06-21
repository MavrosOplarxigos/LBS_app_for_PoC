package com.example.lbs_app_for_poc;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.regex.Pattern;

public class TCPServerThread extends Thread{

    public ServerSocket serverSocket;
    public static final char transmission_del = '|';
    public static final int max_transmission_cutoff = 300000; // 301K bytes per message exchange
    // maybe a scroll view will be given as input to the constructor and we can report logs to that scroll view with text views

    public TCPServerThread(ServerSocket s){
        serverSocket = s;
    }

    public void run(){

        Log.d("TPC server","The server thread is now running!");

        while(true){

            try {

                // COMMUNICATION PROTOCOL START

                Log.d("TCP server","Waiting for message from client!");
                Socket socket = serverSocket.accept();
                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

                // 1) HANDSHAKE STEP 1: RECEIVE CLIENT CREDS
                // OK so now the client must sent his credentials to us
                // We should expect the following format
                // [HELLO]:5 | [CERTIFICATE BYTES]:~2K | [NONCE]:20 | [SIGNED_NONCE]: 20
                ByteArrayOutputStream baosClientHello = new ByteArrayOutputStream();
                byte[] buffer = new byte[1000];
                int bytesRead;
                int total_bytes = 0;
                while( (bytesRead = inputStream.read(buffer)) != -1 ){
                    baosClientHello.write(buffer,0,bytesRead);
                    total_bytes += bytesRead;
                    if(bytesRead < buffer.length){
                        break; // The buffer is not filled up that means we have reached the EOF
                    }
                    if(total_bytes > max_transmission_cutoff){
                        break;
                    }
                }
                Log.d("TCP server","Client Hello Received");
                byte[] bytesClientHello = baosClientHello.toByteArray();

                // SEPARATING THE FIELDS
                byte [][] fieldsClientHello = new byte[4][];
                int ci = 0; // current index on bytesClientHello
                int tempci = ci;

                // HELLO
                ByteArrayOutputStream baosClientHelloHello = new ByteArrayOutputStream();
                for(int i=ci;(char)( bytesClientHello[i] ) != transmission_del;i++){
                    baosClientHelloHello.write( (byte) bytesClientHello[i] );
                    ci=i;
                }
                fieldsClientHello[0] = baosClientHelloHello.toByteArray();

                ci++; // Now must be on delimiter
                if( (char)( bytesClientHello[ci] ) != transmission_del ){
                    Log.d("TCP server","Expected " + transmission_del +" after the HELLO bytes. Found " + bytesClientHello[ci]);
                    socket.close();
                    continue;
                }
                ci++;

                // CLIENT CERTIFICATE LENGTH
                String certificateClientHelloLength = "";
                for(int i=ci;(char)( bytesClientHello[i] ) != transmission_del;i++){
                    certificateClientHelloLength += (char) bytesClientHello[i];
                    ci = i;
                }
                int certificateClientHelloLengthInt = Integer.parseInt(certificateClientHelloLength);

                ci++; // Now must be on delimiter
                if( (char)(bytesClientHello[ci]) != transmission_del){
                    Log.d("TCP server","Expected " + transmission_del +" after the CLIENT CERT LENGTH bytes. Found " + bytesClientHello[ci]);
                    socket.close();
                    continue;
                }
                ci++;

                // CERTIFICATE BYTES
                tempci = ci;
                ByteArrayOutputStream baosClientHelloCertificate = new ByteArrayOutputStream();
                for(int i=ci;i<ci+certificateClientHelloLengthInt;i++){
                    baosClientHelloCertificate.write((byte)bytesClientHello[i]);
                    tempci = i;
                }
                fieldsClientHello[1] = baosClientHelloCertificate.toByteArray();
                ci = tempci;

                ci++; // Now must be on delimiter
                if(bytesClientHello[ci] != transmission_del){
                    Log.d("TCP server","Expected " + transmission_del + " after the CERTIFICATE bytes. Found " + bytesClientHello[ci]);
                    socket.close();
                    continue;
                }
                ci++;

                // NONCE
                tempci = ci;
                ByteArrayOutputStream baosClientHelloNonce = new ByteArrayOutputStream();
                for(int i=ci;i<ci+20;i++){
                    baosClientHelloNonce.write((byte)(bytesClientHello[i]));
                    tempci=i;
                }
                fieldsClientHello[2] = baosClientHelloNonce.toByteArray();
                ci = tempci;

                ci++; // Now must be on delimiter
                if( (char)( bytesClientHello[ci] ) != transmission_del){
                    Log.d("TCP server","Expected " + transmission_del + " after the NONCE bytes. Found " + bytesClientHello[ci]);
                    socket.close();
                    continue;
                }
                ci++;

                // SIGNED NONCE UNTIL THE END NOW
                ByteArrayOutputStream baosClientHelloSignedNonce = new ByteArrayOutputStream();
                for(int i=ci;i<bytesClientHello.length;i++){
                    baosClientHelloSignedNonce.write((byte)(bytesClientHello[i]));
                }
                fieldsClientHello[3] = baosClientHelloSignedNonce.toByteArray();

                // CHECKING FIELDS
                if(!checkFieldsHello(fieldsClientHello,"Server")){
                    Log.d("TCP server","The received fields are incorrect! Closing the connection.");
                    socket.close();
                    continue;
                }

                Log.d("TCP server","The received fields are CORRECT!");

                // 2) HANDSHAKE STEP 2: SEND SERVER CREDENTIALS TO THE CLIENT
                // [HELLO]:5 | [CERTIFICATE BYTES]:~2K | [NONCE]:20 | [SIGNED_NONCE]: 20

                // HELLO
                byte[] helloFieldServerHello = "HELLO".getBytes();
                // CERTIFICATE
                byte[] certificateFieldServerHello = InterNodeCrypto.my_cert.getEncoded();
                byte[] certificateFieldServerHelloLength = ("" + certificateFieldServerHello.length).toString().getBytes();
                SecureRandom secureRandom;
                byte [] nonceFieldServerHello;
                byte [] signedNonceFieldServerHello;
                // NONCE
                secureRandom = new SecureRandom();
                nonceFieldServerHello = new byte[20];
                secureRandom.nextBytes(nonceFieldServerHello);
                // SINGED NONCE
                signedNonceFieldServerHello = InterNodeCrypto.signPrivateKeyByteArray(nonceFieldServerHello);

                ByteArrayOutputStream baosServerHello = new ByteArrayOutputStream();
                baosServerHello.write(helloFieldServerHello);
                baosServerHello.write((byte)(transmission_del));
                baosServerHello.write(certificateFieldServerHelloLength);
                baosServerHello.write((byte)(transmission_del));
                baosServerHello.write(certificateFieldServerHello);
                baosServerHello.write((byte)(transmission_del));
                baosServerHello.write(nonceFieldServerHello);
                baosServerHello.write((byte)(transmission_del));
                baosServerHello.write(signedNonceFieldServerHello);
                // Here maybe add AES key exchange as well?

                byte [] ServerHello = baosServerHello.toByteArray();
                outputStream.write(ServerHello);
                // OK now the client should change its status to be connected

                InterNodeCrypto.save_peer_cert(fieldsClientHello[1]);
                Log.d("TCP server","SUCCESS THE PEER CERTIFICATE IS NOW READY TO USE!");

                // We are going to keep answering queries until the client says BYE
                while(true) {

                    // 3) SERVICE STEP 1: RECEIVE A MAP SEARCH ITEM AS A STRING ENCRYPTED WITH SERVERS PUBLIC KEY AND CLIENTS PRIVATE KEY FOR AUTHENTICATION AND NON-REPUDIATION


                    // 4) SERVICE STEP 2: DECRYPT THE STRING USING CLIENT'S PUBLIC KEY AND SERVER'S PRIVATE KEY (MAYBE FOR THIS ONE WE SHOULD USE BYTE ARRAYS INSTEAD OF STRINGS)
                    // 5) SERVICE STEP 3: CONVERT FROM STRING TO JAVA OBJECT (MAP SEARCH ITEM)

                    // 6) SERVICE STEP 4: CARRY OUT THE apicall function on the MapSearchItem

                    // 7) SERVICE STEP 5: ENCRYPT THE RESULTING STRING WITH THE SERVER'S PRIVATE KEY FOR AUTH AND NONREP AND ENCRYPT IT WITH CLIENT'S PUBLIC KEY

                    // 8) SERVICE STEP 6: SEND THE ENCRYPTED STRING BACK TO THE CLIENT

                    // 9) ACKNOWLEDGEMENT STEP 1: RECEIVE A STRING WHICH IS ESSENTIALLY THE HAS OF THE RESPONSE STRING SIGNED WITH THE CLIENT'S KEY AND THEN ENCRYPTED WITH THE SERVER'S KEY
                    // 10) ACKNOLEDGEMENT STEP 2: VERIFY THAT EVERYTHING IS RIGHT AND UPDATE THE SCROLL VIEW TO INDICATE THE STATUS OF THE REQUEST AS COMPLETED AND ACKNOWLEDGED

                    break;

                }

                // read from client
                String client_msg = inputStream.readUTF();
                Log.d("TCP server","Message from client is "+client_msg);

                // send response
                String my_response = "Hello from server!";
                outputStream.writeUTF(my_response);


                socket.close();
                Log.d("TCP server","Connection closed with " + socket.getInetAddress().getHostName() );

            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (CertificateEncodingException e) {
                throw new RuntimeException(e);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (SignatureException e) {
                throw new RuntimeException(e);
            } catch (NoSuchProviderException e) {
                throw new RuntimeException(e);
            } catch (InvalidKeyException e) {
                throw new RuntimeException(e);
            } catch (CertificateException e) {
                throw new RuntimeException(e);
            }

        }

    }

    public static boolean checkFieldsHello(byte [][] arr, String receiver){

        if(arr.length > 4){
            Log.d("TCP " + receiver,"More than 4 fields received in Client Hello. Dropping connection!");
            return false;
        }

        if(arr.length < 4){
            Log.d("TCP " + receiver,"Less than 4 fields received in Client Hello. Dropping connection!");
            return false;
        }

        // [HELLO]:5 | [CERTIFICATE BYTES]:~2K | [NONCE]:20 | [SIGNED_NONCE]: 20

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
            Log.d("TCP " + receiver,"Certificate Field invalid!");
            e.printStackTrace();
            return false;
        }

        // CHECK THAT THE CERTIFICATE IS SIGNED BY THE CA
        if( !(CryptoChecks.isCertificateSignedBy(cert,InterNodeCrypto.CA_cert)) ){
            Log.d("TCP " + receiver,"The received certificate is not signed by the CA!");
            return false;
        }

        // CHECK NONCE SIZE
        if( arr[2].length != 20 ){
            Log.d("TCP " + receiver,"Incorrect nonce size!");
            return false;
        }

        // CHECK THAT THE NONCE IS SIGNED CORRECTLY
        try {
            if ( !(CryptoChecks.isSignedByCert(arr[2], arr[3], cert)) ) {
                Log.d("TCP " + receiver,"The signed Nonce is NOT SIGNED by the public key of the certificate!");
                return false;
            }
        }
        catch (Exception e){
            Log.d("TCP " + receiver,"Can't verify the nonce signature!");
            e.printStackTrace();
            return false;
        }

        Log.d("TCP " + receiver, "The fields check out and they are the following: ");
        // [HELLO]:5 | [CERTIFICATE BYTES]:~2K | [NONCE]:20 | [SIGNED_NONCE]: 20
        Log.d("TCP " + receiver, "HELLO = " + arr[0] );
        Log.d("TCP " + receiver, "CERT = " + InterNodeCrypto.getCertDetails(cert) );
        Log.d("TCP " + receiver, "NONCE = " + arr[2] );
        Log.d("TCP " + receiver, "SIGNED NONCE = " + arr[3] );

        return true;

    }

}
