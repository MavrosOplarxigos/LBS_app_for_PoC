package com.example.lbs_app_for_poc;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

// This thread is going to spawn the threads for handling querying peers that connect to the
// serving port defined in this class
public class TCPServerControlClass{

    public static int MyServingPort; // The number of the port to listen for connection to
    public static TCPServerAcceptingThread AcceptThread;
    // public static final int max_transmission_cutoff = 300000; // 301K bytes per message exchange
    public static final char transmission_del = '|';
    public static final int MAX_WAIT_QUERYING_NODE_MSEC = 60000;
    public static final int MAX_SEND_BUFFER_SIZE = 100000;
    public static final int MAX_RECEIVE_BUFFER_SIZE = 64000;
    public static LBSEntitiesConnectivity lbsEC = null;
    public static Queue<Socket> to_cleanup = new LinkedList<>();

    public static void old_socket_cleanup(){
        while(!to_cleanup.isEmpty()){
            Socket td = to_cleanup.poll();
            if(td == null){
                continue;
            }
            if(!td.isClosed()){
                try {
                    td.close();
                }
                catch (Exception e){
                    Log.d("SocketClosing","Can't close socket that is not closed!");
                }
            }
            td = null;
        }
    }

    public static void initServingPort(){
        int MIN_PORT = 56000;
        int MAX_PORT = 58000;
        Random random = new Random();
        MyServingPort = random.nextInt(MAX_PORT - MIN_PORT + 1) + MIN_PORT;
        // initialize serving pseudo credentials
        ServingNodeQueryHandleThread.my_PSEUDO_CREDS_TO_COPY_lock = new ReentrantLock();
        ServingNodeQueryHandleThread.my_PSEUDO_CREDS_TO_COPY_lock.lock();
        ServingNodeQueryHandleThread.my_cert_to_copy = InterNodeCrypto.pseudonymous_certificates.get(0);
        ServingNodeQueryHandleThread.my_key_to_copy = InterNodeCrypto.pseudonymous_privates.get(0);
        ServingNodeQueryHandleThread.my_PSEUDO_CREDS_TO_COPY_lock.unlock();
    }

    public static class TCPServerAcceptingThread extends Thread{

        public static ServerSocket serverSocket;
        public static final int WAIT_AFTER_ACCEPT_FAILURE_MSEC = 300;
        public static final int MAX_SIMULTANEOUS_ACCEPTS = 2000;

        public TCPServerAcceptingThread(){
            try {
                serverSocket = new ServerSocket(TCPServerControlClass.MyServingPort,MAX_SIMULTANEOUS_ACCEPTS);
            }
            catch (Exception e){
                Log.d("TCPServerAcceptingThread","Could not construct the socket for listenting!");
                e.printStackTrace();
                throw new RuntimeException();
            }
        }

        @Override
        public void run() {
            while(true){
                try {
                    Socket s = serverSocket.accept();

                    try {
                        s.setTcpNoDelay(true); // NO - DELAY
                        s.setSendBufferSize(MAX_SEND_BUFFER_SIZE);
                        s.setReceiveBufferSize(MAX_RECEIVE_BUFFER_SIZE);
                    } catch (SocketException e) {
                        throw new RuntimeException("NEW IMPROVEMENT EFFORT" + e);
                    }

                    to_cleanup.offer(s);
                    s.setSoTimeout(MAX_WAIT_QUERYING_NODE_MSEC);
                    // spawn the thread for handling the new client querying node
                    ServingNodeQueryHandleThread neo_nima = new ServingNodeQueryHandleThread(s);
                    neo_nima.start();
                }
                catch (IOException e) {
                    e.printStackTrace();
                    Log.d("TCPServerAcceptingThread","Error while accepting connection on serving socket!");
                    LoggingFragment.mutexTvdAL.lock();
                    LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("ERROR: Serving Socket Accept Failure!", Color.RED));
                    LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Accepting cooldown for " + WAIT_AFTER_ACCEPT_FAILURE_MSEC + " msec.", Color.BLUE));
                    LoggingFragment.mutexTvdAL.unlock();
                    try {
                        Thread.sleep(WAIT_AFTER_ACCEPT_FAILURE_MSEC);
                    }
                    catch (InterruptedException ex) {
                        Log.d("AcceptingThread","Sleeping Error!");
                        ex.printStackTrace();
                    }
                }
                catch (Exception e){
                    Log.d("PARANOIA_SPAWNER","The nima spawner could not accept the reason is: ");
                    e.printStackTrace();
                }
            }
        }

    }

    /*
    public void run(){

        Log.d("TPC server","The server thread is now running!");

        while(true){

            try {

                // COMMUNICATION PROTOCOL START

                Log.d("TCP server","Waiting for message from client!");
                Socket socket = serverSocket.accept();
                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                Log.d("TCP server","Received connection from " + socket.getInetAddress().getHostAddress() + " from port " + socket.getPort() );

                // updating the server log
                Message connectionMessage = uiHandler.obtainMessage();
                Bundle connectionBundle = new Bundle();
                connectionBundle.putBoolean("isConnectionAccept",true);
                connectionBundle.putString("ipAddress",socket.getInetAddress().getHostAddress());
                connectionBundle.putInt("port",socket.getPort());

                // 1) HANDSHAKE STEP 1: RECEIVE CLIENT CREDS
                // OK so now the client must sent his credentials to us
                // We should expect the following format
                // [HELLO]:5 | [CLIENT CERT LENGHT BYTES] | [CERTIFICATE BYTES]:~2K | [Timestamp]:8 | [Signed_Timestamp]: 256
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

                // [HELLO]
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

                // [CLIENT CERT LENGHT BYTES]
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

                // [CERTIFICATE BYTES]
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

                // [timestamp]
                tempci = ci;
                ByteArrayOutputStream baosClientHelloNonce = new ByteArrayOutputStream();
                for(int i=ci;i<ci+InterNodeCrypto.TIMESTAMP_BYTES;i++){
                    baosClientHelloNonce.write((byte)(bytesClientHello[i]));
                    tempci=i;
                }
                fieldsClientHello[2] = baosClientHelloNonce.toByteArray();
                ci = tempci;

                ci++; // Now must be on delimiter
                if( (char)( bytesClientHello[ci] ) != transmission_del){
                    Log.d("TCP server","Expected " + transmission_del + " after the timestamp bytes. Found " + bytesClientHello[ci]);
                    socket.close();
                    continue;
                }
                ci++;

                // [SIGNED TIMESTAMP]
                ByteArrayOutputStream baosClientHelloSignedNonce = new ByteArrayOutputStream();
                for(int i=ci;i<bytesClientHello.length;i++){
                    baosClientHelloSignedNonce.write((byte)(bytesClientHello[i]));
                }
                fieldsClientHello[3] = baosClientHelloSignedNonce.toByteArray();

                // CHECKING FIELDS
                if(!checkFieldsClientHello(fieldsClientHello,"Server")){
                    Log.d("TCP server","The received fields are incorrect! Closing the connection.");
                    socket.close();
                    continue;
                }
                Log.d("TCP server","Client Hello: The received fields are CORRECT!");

                // Saving into variables the data that we need from ClientHello
                current_peer_cert = InterNodeCrypto.CertFromByteArray(fieldsClientHello[1]);
                Log.d("TCP server","Client Hello: SUCCESS the peer certificate is now saved and ready to use!");
                current_peer_id = current_peer_cert.getSubjectDN().toString();

                // Logging connection now that we know the ID of the user
                connectionBundle.putInt("MyPort",serverSocket.getLocalPort());
                connectionBundle.putString("PeerID",current_peer_id);
                connectionMessage.setData(connectionBundle);
                uiHandler.sendMessage(connectionMessage);

                // 2) HANDSHAKE STEP 2: SEND SERVER CREDENTIALS TO THE CLIENT
                // [HELLO]:5 | [CERTIFICATE LENGTH] | [CERTIFICATE BYTES]:~2K | [timestamp]: 8 | [signed timestamp]: 256

                // HELLO
                byte[] helloFieldServerHello = "HELLO".getBytes();
                // CERTIFICATE
                byte[] certificateFieldServerHello = InterNodeCrypto.my_cert.getEncoded();
                byte[] certificateFieldServerHelloLength = ("" + certificateFieldServerHello.length).toString().getBytes();

                CryptoTimestamp cryptoTimestamp = InterNodeCrypto.getSignedTimestamp();

                ByteArrayOutputStream baosServerHello = new ByteArrayOutputStream();
                baosServerHello.write(helloFieldServerHello);
                baosServerHello.write((byte)(transmission_del));
                baosServerHello.write(certificateFieldServerHelloLength);
                baosServerHello.write((byte)(transmission_del));
                baosServerHello.write(certificateFieldServerHello);
                baosServerHello.write((byte)(transmission_del));
                baosServerHello.write(cryptoTimestamp.timestamp);
                baosServerHello.write((byte)(transmission_del));
                baosServerHello.write(cryptoTimestamp.signed_timestamp);
                // Here maybe add AES key exchange as well?

                byte [] ServerHello = baosServerHello.toByteArray();
                outputStream.write(ServerHello);
                // OK now the client should change its status to be connected

                // We are going to keep answering QUERIES until the client says BYE
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
                    for(int i=ci; (i<bytesClientMessage.length) && ((char)( bytesClientMessage[i] ) != transmission_del) ;i++){
                        baosClientMessageOption.write( (byte) bytesClientMessage[i] );
                        ci=i;
                    }
                    ClientMessageOption = baosClientMessageOption.toByteArray();

                    boolean isQuery = false;
                    if( Arrays.equals(ClientMessageOption, "QUERY".getBytes()) ){
                        Log.d("TCP server","The client has sent a QUERY!");
                        isQuery = true;
                    }
                    else if( Arrays.equals(ClientMessageOption, "BYE".getBytes()) ){
                        Log.d("TCP server","The client has sent a BYE thus terminating the connection!");
                        break;
                    }
                    else if( ClientMessageOption.length == 0 ){
                        Log.d("TCP server","The client terminated the connection suddenly without the BYE option.");
                        break;
                    }
                    else{
                        Log.d("TCP server","Client Message Option unrecognized. Option = " + new String(ClientMessageOption,StandardCharsets.UTF_8));
                        break;
                    }

                    if(isQuery){

                        // Logging that we received a Query from the peer
                        Message QueryMessage = uiHandler.obtainMessage();
                        Bundle QueryBundle = new Bundle();
                        QueryBundle.putBoolean("isQuery",true);
                        QueryBundle.putString("ipAddress",socket.getInetAddress().getHostAddress());
                        QueryBundle.putInt("port",socket.getPort());
                        QueryBundle.putString("PeerID",current_peer_id);

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

                        // [API_CALL_ENC_BYTES]
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

                        // [API_CALL_SIGNED_BYTES]
                        ByteArrayOutputStream baosClientQueryAPICallSignedBytes = new ByteArrayOutputStream();
                        for(int i=ci;i < total_bytesClientMessage;i++){
                            baosClientQueryAPICallSignedBytes.write(bytesClientMessage[i]);
                        }
                        fieldsClientQuery[1] = baosClientQueryAPICallSignedBytes.toByteArray();

                        // SERVICE STEP 2: a) CHECK THAT THE SIGNATURE CORRESPONDS TO THE ENCRYPTED API CALL BYTE ARRAY (USING CLIENT'S CERT)
                        if( !CryptoChecks.isSignedByCert(fieldsClientQuery[0],fieldsClientQuery[1],current_peer_cert) ){
                            Log.d("TCP server","ERROR: The received query signature is incorrect!");
                            return;
                        }
                        else{
                            Log.d("TCP server","SUCCESS: The received query signature is valid!");
                        }

                        // SERVICE STEP 2: b) DECRYPT THE ENCRYPTED API CALL (USING SERVERS PRIVATE KEY)
                        byte [] APICallBytes = InterNodeCrypto.decryptWithOwnKey(fieldsClientQuery[0]);
                        Log.d("TCP server","The received API call string after decryption is " + new String(APICallBytes,StandardCharsets.UTF_8) );

                        // Update the logger with the query details and send the message to the logs handler
                        QueryMessage.setData(QueryBundle);
                        uiHandler.sendMessage(QueryMessage);

                        // SERVICE STEP 3: Contact the LBS server to get the answer to the query as a JSONObject
                        JSONObject answer = LBSServerInteractions.execute_api_call(new String(APICallBytes,StandardCharsets.UTF_8) );

                        if(answer == null){
                            Log.d("API EXEC RESULT","The JSONObject is null!\n");
                            // Toast.makeText(getContext(), "No response from LBS server!", Toast.LENGTH_SHORT).show();
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
                        // Now encrypt the array

                        byte [] EncryptedJSONObjectAnswerByteArray = null;
                        try {
                            EncryptedJSONObjectAnswerByteArray = InterNodeCrypto.encryptWithPeerKey(JSONObjectAnswerByteArray,current_peer_cert);
                        }
                        catch (Exception e){
                            Log.d("TCP Server","For some reason we can't encrypt the JSONObjectAnswerByteArray!");
                        }

                        Log.d("TCP Server","The length of the JSONObjectAnswerByteArray is " + JSONObjectAnswerByteArray.length);
                        Log.d("TCP Server","The length of the EncryptedJSONObjectAnswerByteArray is " + EncryptedJSONObjectAnswerByteArray.length);

                        byte [] EncryptedJSONObjectAnswerByteArraySigned = InterNodeCrypto.signPrivateKeyByteArray(EncryptedJSONObjectAnswerByteArray);
                        byte [] EncryptedJSONObjectAnswerByteArraySize = ("" + EncryptedJSONObjectAnswerByteArray.length).getBytes();

                        // 4.1) SERVER RESPONSE DECLARATION: DECLARE TO THE CLIENT THE SIZE OF THE RESPONSE SO HE CAN ACTIVELY WAIT FOR ALL BYTES TO ARRIVE
                        // RATHER THAN SKIPPING READING THEM AS SOON AS THE BUFFER GETS EMPTY DUE TO DATA TRASMISSION DELAYS (MOST PROBABLY).
                        // [RESPONSE] | [RESPONSE SIZE IN BYTES]

                        int ServerResponseSize = EncryptedJSONObjectAnswerByteArraySize.length + 1 + EncryptedJSONObjectAnswerByteArray.length
                                + 1 + EncryptedJSONObjectAnswerByteArraySigned.length;
                        Log.d("TCP Server","The ServerResponseSize has been calculated to be " + ServerResponseSize + " bytes!");
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
                        Log.d("TCP Server","The server response declaration message is: " + new String(ServerResponseDeclaration,StandardCharsets.UTF_8) );
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
                        // [EncryptedJSONObjectAnswerByteArraySize] |
                        // [EncryptedJSONObjectAnswerByteArray] | [EncryptedJSONObjectAnswerByteArraySigned]

                        Log.d("TCP Server","The EncryptedJSONObjectAnswerByteArray size is " + EncryptedJSONObjectAnswerByteArray.length );
                        Log.d("TCP Server","The EncryptedJSONObjectAnswerByteArraySize is " + new String(EncryptedJSONObjectAnswerByteArraySize,StandardCharsets.UTF_8) );
                        Log.d("TCP Server","The EncryptedJSONObjectAnswerByteArraySigned size is " + EncryptedJSONObjectAnswerByteArraySigned.length );

                        ByteArrayOutputStream baosServerRespone = new ByteArrayOutputStream();
                        try {
                            baosServerRespone.write(EncryptedJSONObjectAnswerByteArraySize);
                            baosServerRespone.write(transmission_del);
                            baosServerRespone.write(EncryptedJSONObjectAnswerByteArray);
                            baosServerRespone.write(transmission_del);
                            baosServerRespone.write(EncryptedJSONObjectAnswerByteArraySigned);
                        }
                        catch (Exception e){
                            Log.d("TCP Server","ERROR: Could not compose the byteArrayOutputStream for the ServerResponse.");
                            throw e;
                        }

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

                        Message QueryAnsweredMessage = uiHandler.obtainMessage();
                        Bundle QueryAnsweredBundle = new Bundle();
                        QueryAnsweredBundle.putBoolean("isAnswer",true);
                        QueryAnsweredBundle.putString("ipAddress",socket.getInetAddress().getHostAddress());
                        QueryAnsweredBundle.putInt("port",socket.getPort());
                        QueryAnsweredBundle.putString("PeerID",current_peer_id);
                        QueryAnsweredMessage.setData(QueryAnsweredBundle);
                        uiHandler.sendMessage(QueryAnsweredMessage);

                        Log.d("TCP SERVER","The ServerResponse has been sent to the Client!");

                        // NOW WE ARE GOING TO REPEAT THE LOOP TO RECEIVE ANY POSSIBLE FOLLOWING QUERY OR RECEIVE END COMMUNICATION OPTION (BYE)
                        continue;

                    }

                    // Here we supposed that since the message received is neither BYE nor QUERY we just exit the loop
                    break;

                }

                socket.close();
                Log.d("TCP server","Connection closed with " + socket.getInetAddress().getHostName() );

                // updating the log on the server side
                Message disconnectionMessage = uiHandler.obtainMessage();
                Bundle disconnectionBundle = new Bundle();
                disconnectionBundle.putBoolean("isDisconnection",true);
                disconnectionBundle.putString("ipAddress",socket.getInetAddress().getHostAddress());
                disconnectionBundle.putInt("port",socket.getPort());
                disconnectionBundle.putString("PeerID",current_peer_id);
                disconnectionMessage.setData(disconnectionBundle);
                uiHandler.sendMessage(disconnectionMessage);

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
    */

}
