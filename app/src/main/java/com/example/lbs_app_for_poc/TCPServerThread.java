package com.example.lbs_app_for_poc;

import android.util.Log;

import org.json.JSONObject;

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

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

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

            // TODO: Fix code so that we don't check for delimeter after the for loop exits when reading a field unless necessary
            // TODO: Add condition in for loops that we don't overstep the byte array we are reading from
            try {

                // COMMUNICATION PROTOCOL START

                Log.d("TCP server","Waiting for message from client!");
                Socket socket = serverSocket.accept();
                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                Log.d("TCP server","Received connection from " + socket.getInetAddress().getHostAddress() + " from port " + socket.getPort() );

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

                // We are going to keep answering QUERIES until the client says BYE
                // TODO: Add more reponsiveness from server side on faulty queries from client
                while(true) {

                    // 3) CLIENT MESSAGE: RECEIVE A MESSAGE FROM CLIENT (QUERY,BYE)
                    Log.d("TCP Server","Now waiting for a new query from the client!");

                    ByteArrayOutputStream baosClientMessage = new ByteArrayOutputStream();
                    byte [] bufferClientMessage = new byte[1000];
                    int bytesReadClientMessage;
                    int total_bytesClientMessage = 0;
                    while( (bytesReadClientMessage = inputStream.read(bufferClientMessage)) != -1 ){
                        baosClientMessage.write(bufferClientMessage,0,bytesReadClientMessage);
                        total_bytesClientMessage += bytesReadClientMessage;
                        if(bytesReadClientMessage < bufferClientMessage.length){
                            break; // The buffer is not filled up that means we have reached the EOF
                        }
                        if(total_bytesClientMessage > max_transmission_cutoff){
                            break;
                        }
                    }
                    Log.d("TCP server","Client Message Received.");
                    byte[] bytesClientMessage = baosClientMessage.toByteArray();
                    Log.d("TCP Server","Client message size in bytes is " + bytesClientMessage.length );

                    // CHECKING MESSAGE OPTION
                    ci = 0;
                    tempci = ci;

                    // READING TO SEE IF WE GET A BYE OR QUERY
                    byte [] ClientMessageOption;
                    ByteArrayOutputStream baosClientMessageOption = new ByteArrayOutputStream();
                    for(int i=ci;(char)( bytesClientMessage[i] ) != transmission_del;i++){
                        baosClientMessageOption.write( (byte) bytesClientMessage[i] );
                        ci=i;
                    }
                    ClientMessageOption = baosClientMessageOption.toByteArray();

                    boolean isQuery = false;
                    if( Arrays.equals(ClientMessageOption, "QUERY".getBytes()) ){
                        Log.d("TCP server","The client has sent a QUERY!");
                        isQuery = true;
                    }
                    else if( (!isQuery) && Arrays.equals(ClientMessageOption, "BYE".getBytes()) ){
                        // TODO: Verify that the BYE received is indeed from the client by having one more field (i.e. the nonce)
                        Log.d("TCP server","The client has sent a BYE thus terminating the connection!");
                        break;
                    }
                    else{
                        // TODO: Make server return a specific answer indicating to the client that the chosen option is not supported
                        Log.d("TCP server","Client Message Option unrecognized.");
                        continue;
                    }

                    if(isQuery){

                        // SERVICE STEP 1: RECEIVE AN API CALL AS A STRING
                        // ENCRYPTED WITH SERVERS PUBLIC KEY
                        // SIGNED WITH CLIENTS PRIVATE KEY FOR AUTHENTICATION AND NON-REPUDIATION
                        // [API_CALL_ENC_BYTES_LENGTH] | [API_CALL_ENC_BYTES] | [API_CALL_SIGNED_BYTES]

                        // Reading the delimiter byte after [QUERY]
                        ci++;
                        if( (char)(bytesClientMessage[ci]) != transmission_del ){
                            Log.d("TCP server","ERROR: Expected " + TCPServerThread.transmission_del +" after the Client Message Option bytes. Found " + bytesClientMessage[ci]);
                            return;
                        }
                        ci++;

                        // SEPARATING THE FIELDS
                        // [API_CALL_ENC_BYTES] | [API_CALL_SIGNED_BYTES]
                        byte [][] fieldsClientQuery = new byte[2][];

                        // API CALL ENCRYPTED BYTES LENGTH
                        String APICallEncBytesLengthString = "";
                        for(int i=ci;((char)(bytesClientMessage[i]) != transmission_del) && (i<total_bytesClientMessage);i++){
                            APICallEncBytesLengthString += (char)(bytesClientMessage[i]);
                            ci = i;
                        }
                        int APICallEncBytesLength = Integer.parseInt(APICallEncBytesLengthString);

                        Log.d("TCP server","The encrypted bytes length expected is " + APICallEncBytesLength );

                        // delimiter needs to be checked
                        ci++;
                        if( (char)(bytesClientMessage[ci]) != transmission_del ){
                            Log.d("TCP server","ERROR: Expected " + TCPServerThread.transmission_del +" after the Client Query API Call Enc bytes. Found " + bytesClientMessage[ci]);
                            return;
                        }
                        ci++;

                        // API CALL ENCRYPTED BYTES
                        ByteArrayOutputStream baosClientQueryAPICallEncBytes = new ByteArrayOutputStream();
                        for(int i=ci;( i < ci+APICallEncBytesLength ) && ( i < total_bytesClientMessage );i++){
                            baosClientQueryAPICallEncBytes.write(bytesClientMessage[i]);
                            tempci = i;
                        }
                        ci = tempci;
                        fieldsClientQuery[0] = baosClientQueryAPICallEncBytes.toByteArray();

                        // delimiter needs to be checked
                        ci++;
                        if( (char)(bytesClientMessage[ci]) != transmission_del ){
                            Log.d("TCP server","ERROR: Expected " + TCPServerThread.transmission_del +" after the Client Query API Call Enc bytes. Found " + bytesClientMessage[ci]);
                            return;
                        }
                        ci++;

                        // API CALL SIGNED BYTES
                        ByteArrayOutputStream baosClientQueryAPICallSignedBytes = new ByteArrayOutputStream();
                        for(int i=ci;i < total_bytesClientMessage;i++){
                            baosClientQueryAPICallSignedBytes.write(bytesClientMessage[i]);
                        }
                        fieldsClientQuery[1] = baosClientQueryAPICallSignedBytes.toByteArray();

                        // SERVICE STEP 2: a) CHECK THAT THE SIGNATURE CORRESPONDS TO THE ENCRYPTED API CALL BYTE ARRAY (USING CLIENT'S CERT)
                        if( !CryptoChecks.isSignedByCert(fieldsClientQuery[0],fieldsClientQuery[1],InterNodeCrypto.peer_cert) ){
                            Log.d("TCP server","ERROR: The received query signature is incorrect!");
                            return;
                        }
                        else{
                            Log.d("TCP server","SUCCESS: The received query signature is valid!");
                        }

                        // SERVICE STEP 2: b) DECRYPT THE ENCRYPTED API CALL (USING SERVERS PRIVATE KEY)
                        byte [] APICallBytes = InterNodeCrypto.decryptWithOwnKey(fieldsClientQuery[0]);
                        Log.d("TCP server","The received API call string after decryption is " + new String(APICallBytes,StandardCharsets.UTF_8) );

                        // SERVICE STEP 3: Contact the LBS server to get the answer to the query as a JSONObject
                        JSONObject answer = LBSServerInteractions.execute_api_call(new String(APICallBytes,StandardCharsets.UTF_8) );

                        if(answer == null){
                            Log.d("API EXEC RESULT","The JSONObject is null!\n");
                            // Toast.makeText(getContext(), "No response from LBS server!", Toast.LENGTH_SHORT).show();
                            // TODO: send [NO_ANSWER] [NONCE+1*NUM_OR_QUERIES_SO_FAR]
                            return;
                        }
                        else {
                            Log.d("API EXEC RESULT", "The JSONObject was created successfully! \n" + answer.toString());
                        }

                        // ---------------------------- POINT OF DISASTER -------------------------------------

                        // SERVICE STEP 4: SERVER RESPONSE

                        byte[] responseFieldServerResponse = "RESPONSE".getBytes();
                        // Now we make the JSONObject into a byte array
                        byte [] JSONObjectAnswerByteArray = answer.toString().getBytes(StandardCharsets.UTF_8);
                        byte [] JSONObjectAnswerByteArraySigned = InterNodeCrypto.signPrivateKeyByteArray(JSONObjectAnswerByteArray);
                        byte [] JSONObjectAnswerByteArraySize = ("" + JSONObjectAnswerByteArray.length).getBytes();

                        // 4.1) SERVER RESPONSE DECLARATION: DECLARE TO THE CLIENT THE SIZE OF THE RESPONSE SO HE CAN ACTIVELY WAIT FOR ALL BYTES TO ARRIVE
                        // RATHER THAN SKIPPING READING THEM AS SOON AS THE BUFFER GETS EMPTY DUE TO DATA TRASMISSION DELAYS (MOST PROBABLY).
                        // [RESPONSE] | [RESPONSE SIZE IN BYTES]

                        int ServerResponseSize = JSONObjectAnswerByteArraySize.length + 1 + JSONObjectAnswerByteArray.length + 1 + JSONObjectAnswerByteArraySigned.length;
                        Log.d("TCP Server","The ServerResponseSize has been declared to be " + ServerResponseSize + " bytes!");
                        byte [] ServerResponseSizeByteArray = ("" + ServerResponseSize).getBytes();

                        ByteArrayOutputStream baosServerResponseDeclaration = new ByteArrayOutputStream();
                        try {
                            baosServerResponseDeclaration.write(responseFieldServerResponse);
                            baosServerResponseDeclaration.write(transmission_del);
                            baosServerResponseDeclaration.write(ServerResponseSizeByteArray);
                        }
                        catch (Exception e){
                            Log.d("TCP Server","ERROR: Could not compose the byteArrayOutputStream for the ServerResponseDeclaration.");
                            throw e;
                        }

                        byte [] ServerResponseDeclaration = baosServerResponseDeclaration.toByteArray();
                        outputStream.write(ServerResponseDeclaration);

                        // 4.2) CLIENT DECLARATION ACCEPT
                        ByteArrayOutputStream baosClientDeclarationAccept = new ByteArrayOutputStream();
                        byte [] bufferClientDeclarationAccept = new byte[1000];
                        int bytesReadClientDeclarationAccept;
                        int total_bytesClientDeclarationAccept = 0;
                        while( (bytesReadClientDeclarationAccept = inputStream.read(bufferClientDeclarationAccept)) != -1 ){
                            baosClientDeclarationAccept.write(bufferClientDeclarationAccept,0,bytesReadClientDeclarationAccept);
                            total_bytesClientDeclarationAccept += bytesReadClientDeclarationAccept;
                            if(bytesReadClientDeclarationAccept < bufferClientDeclarationAccept.length){
                                break; // The buffer is not filled up that means we have reached the EOF
                            }
                            if(total_bytesClientDeclarationAccept > max_transmission_cutoff){
                                break;
                            }
                        }
                        Log.d("TCP server","Client Declaration Accept Received.");
                        byte [] ClientDeclarationAccept = baosClientDeclarationAccept.toByteArray();

                        if( !("ACK".equals( new String(ClientDeclarationAccept,StandardCharsets.UTF_8) )) ){
                            Log.d("TCP server","ERROR: The client did not respond with ACK to our Server Response Declaration!");
                            return;
                        }

                        // 4.3) SERVER RESPONSE: SEND THE ACTUAL RESPONSE BYTES TO THE CLIENT
                        // [JSONObjectAnswerByteArraySize] | [JSONObjectAnswerByteArray] | [JSONObjectAnswerByteArraySigned]

                        // 24238
                        Log.d("TCP Server","The JSONObjectAnswerByteArray size is " + JSONObjectAnswerByteArray.length );
                        Log.d("TCP Server","The JSONObjectAnswerByteArraySize is " + new String(JSONObjectAnswerByteArraySize,StandardCharsets.UTF_8) );
                        Log.d("TCP Server","The JSONObjectAnswerByteArraySigned size is " + JSONObjectAnswerByteArraySigned.length );

                        ByteArrayOutputStream baosServerRespone = new ByteArrayOutputStream();
                        try {
                            baosServerRespone.write(JSONObjectAnswerByteArraySize);
                            baosServerRespone.write(transmission_del);
                            baosServerRespone.write(JSONObjectAnswerByteArray);
                            baosServerRespone.write(transmission_del);
                            baosServerRespone.write(JSONObjectAnswerByteArraySigned);
                        }
                        catch (Exception e){
                            Log.d("TCP Server","ERROR: Could not compose the byteArrayOutputStream for the ServerResponse.");
                            throw e;
                        }

                        // 24510
                        byte [] ServerResponse = baosServerRespone.toByteArray();
                        Log.d("TCP Server","The entire ServerResponse size is " + ServerResponse.length );

                        try{
                            // here we will try to use a buffer when writing since the size of the ServerResponse can be big ~25K bytes
                            // maybe there is data loss if we write all bytes at once so we are going to use a buffer
                            int offset = 0;
                            int bufferSize = 1000;
                            while (offset < ServerResponse.length) {
                                Log.d("TCP SERVER","Now writing for the offset " + offset);
                                int length = Math.min(bufferSize, ServerResponse.length - offset);
                                outputStream.write(ServerResponse, offset, length);
                                offset += length;
                            }
                            outputStream.flush();
                            // outputStream.write(ServerResponse);
                        }catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        Log.d("TCP SERVER","The ServerResponse has been sent to the Client!");

                        // NOW WE ARE GOING TO REPEAT THE LOOP TO RECEIVE ANY POSSIBLE FOLLOWING QUERY OR RECEIVE END COMMUNICATION OPTION (BYE)
                        continue;

                    }

                    // Here we supposed that since the message received is neither BYE nor QUERY we just exit the loop
                    break;

                }

                /*
                // read from client
                String client_msg = inputStream.readUTF();
                Log.d("TCP server","Message from client is "+client_msg);

                // send response
                String my_response = "Hello from server!";
                outputStream.writeUTF(my_response);*/

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
            } catch (NoSuchPaddingException e) {
                throw new RuntimeException(e);
            } catch (IllegalBlockSizeException e) {
                throw new RuntimeException(e);
            } catch (BadPaddingException e) {
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
