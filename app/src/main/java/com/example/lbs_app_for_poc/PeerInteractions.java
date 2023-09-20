package com.example.lbs_app_for_poc;

import android.graphics.Color;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.X509Certificate;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class PeerInteractions {

    public static class PeerInteractionThread extends Thread{

        public static final int SERVING_PEER_CONNECTION_TIMEOUT_MSEC = 2000; // How much time do we wait for a peer to respond
        int index4AnswerStoring;
        InetAddress peerIP;
        int peerPort;
        public byte [] APICallBytesClientQuery = null; // the query is passed by reference (ensure that the data is not lost due to the search button listener function finishing)

        public X509Certificate my_cert = null; // We get our certificate based on our index4AnswerStoring
        public X509Certificate peer_cert = null; // Retrieved from SERVER HELLO (serving peer hello)
        public String peer_name = null;
        public PrivateKey my_key = null; // We get our key based on our index4AnswerStoring

        public PeerInteractionThread(int i, InetAddress ip, int port, byte [] APICallBytesClientQuery){
            index4AnswerStoring = i;
            my_cert = InterNodeCrypto.pseudonymous_certificates.get(i%InterNodeCrypto.pseudonymous_certificates.size());
            my_key = InterNodeCrypto.pseudonymous_privates.get(i%InterNodeCrypto.pseudonymous_privates.size());
            peerIP = ip;
            peerPort = port;
            this.APICallBytesClientQuery = APICallBytesClientQuery;
        }

        @Override
        public void run() {
            SearchingNodeFragment.mutexPeerResponseDecJson[index4AnswerStoring].lock(); // locking my response index
            SearchingNodeFragment.peer_thread_entered_counter.countDown(); // notifying the collection thread that this peer has entered
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(this.peerIP, this.peerPort), SERVING_PEER_CONNECTION_TIMEOUT_MSEC);
            }
            catch (SocketTimeoutException socketTimeoutException){
                safe_exit("ERROR: Socket connect timeout!",socketTimeoutException,s);
                return;
            }
            catch (IOException e) {
                safe_exit("ERROR: Socket connect failure unrelated to timeout!",e,s);
                return;
            }
            // HELLO phase
            boolean introductionsDone = configure_peer_connectivity(s);
            if(!introductionsDone){
                LoggingFragment.mutexTvdAL.lock();
                LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Hello FAILURE with " + peerIP, Color.RED) );
                LoggingFragment.mutexTvdAL.unlock();
                safe_exit("ERROR: HELLO interaction after socket connection failure!",null,s);
                return;
            }
            LoggingFragment.mutexTvdAL.lock();
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Hello SUCCESS with: " + peer_name + "@ " + peerIP, Color.GREEN ) );
            LoggingFragment.mutexTvdAL.unlock();

            DataInputStream dis = null;
            DataOutputStream dos = null;
            try {
                dis = new DataInputStream(s.getInputStream());
                dos = new DataOutputStream(s.getOutputStream());
            }
            catch (Exception e){
                safe_exit("ERROR: Could not retrieve the data strreams from the socket",e,s);
                return;
            }

            // OK the introduction phase (HELLO exchange) is done and we have all the credentials we need

            // 3) CLIENT MESSAGE: SENT MESSAGE TO THE SERVER (QUERY)
            // [QUERY] | [API_CALL_ENC_BYTES_LENGTH] | [API_CALL_ENC_BYTES] | [API_CALL_SIGNED_BYTES]
            byte [] queryBytesClientQuery = "QUERY".getBytes();
            byte [] APICallEncryptedBytesClientQuery;
            try {
                APICallEncryptedBytesClientQuery = InterNodeCrypto.encryptWithPeerKey(APICallBytesClientQuery,peer_cert);
            } catch (Exception e) {
                safe_exit("ERROR: Could not encrypt the API call with peer key!",e,s);
                return;
            }
            byte [] APICallEncryptedBytesClientQueryLength = ("" + APICallEncryptedBytesClientQuery.length).getBytes();
            Log.d(debug_tag_peer(),"The size of the query bytes should be " + new String(APICallEncryptedBytesClientQueryLength,StandardCharsets.UTF_8) );

            byte [] APICallBytesSignedClientQuery;
            try {
                // Chaged from:
                // APICallBytesSignedClientQuery = InterNodeCrypto.signByteArrayWithPrivateKey(APICallEncryptedBytesClientQuery, my_key);
                // To: (TODO: fix on the TCP Server maybe to sign the raw request instead to save memory)
                APICallBytesSignedClientQuery = InterNodeCrypto.signByteArrayWithPrivateKey(APICallBytesClientQuery, my_key);
            }
            catch (Exception e){
                safe_exit("ERROR: Could not sign the encrypted bytes client query with my private key",e,s);
                return;
            }

            ByteArrayOutputStream baosClientQuery = new ByteArrayOutputStream();
            try {
                baosClientQuery.write(queryBytesClientQuery);
                baosClientQuery.write(TCPServerControlClass.transmission_del);
                baosClientQuery.write(APICallEncryptedBytesClientQueryLength);
                baosClientQuery.write(TCPServerControlClass.transmission_del);
                baosClientQuery.write(APICallEncryptedBytesClientQuery);
                baosClientQuery.write(TCPServerControlClass.transmission_del);
                baosClientQuery.write(APICallBytesSignedClientQuery);
            } catch (IOException e) {
                safe_exit("ERROR: Could not put client query fields in the byte array output stream",e,s);
                return;
            }

            byte [] ClientQuery = baosClientQuery.toByteArray();
            Log.d(debug_tag_peer(),"The size of the entire query message in bytes should be " + ClientQuery.length );
            try{
                dos.write(ClientQuery);
            }
            catch (Exception e){
                safe_exit("Error: Could not write the client query in output sream!",e,s);
                return;
            }
            Log.d(debug_tag_peer(),"SUCCESS: Client Query sent to serving peer " + peer_name + " @ " + peerIP  );

            // 4.1) SERVER RESPONSE DECLARATION: RECEIVE THE RESPONSE SIZE IN BYTES
            // [RESPONSE] | [RESPONSE SIZE IN BYTES]

            ByteArrayOutputStream baosServerResponseDeclaration;
            try {
                baosServerResponseDeclaration = new ByteArrayOutputStream();
                byte[] bufferServerResponseDeclaration = new byte[1000];
                int bytesReadServerResponseDeclaration;
                int total_bytesServerResponseDeclaration = 0;
                while( (bytesReadServerResponseDeclaration = dis.read(bufferServerResponseDeclaration)) != -1 ) {
                    Log.d(debug_tag_peer(),"Now read " + bytesReadServerResponseDeclaration + " bytes!");
                    baosServerResponseDeclaration.write(bufferServerResponseDeclaration, 0, bytesReadServerResponseDeclaration);
                    total_bytesServerResponseDeclaration += bytesReadServerResponseDeclaration;
                    if (bytesReadServerResponseDeclaration < bufferServerResponseDeclaration.length) {
                        break; // The buffer is not filled up that means we have reached the EOF
                    }
                    if (total_bytesServerResponseDeclaration > TCPServerThread.max_transmission_cutoff) {
                        Log.d(debug_tag_peer(),"The maximum transmission cutoff is reached in Server Response Declaration!");
                        throw new IOException();
                    }
                }
            } catch (IOException e) {
                safe_exit("Could not receive server response declaration",e,s);
                return;
            }

            byte [] bytesServerResponseDeclartion = baosServerResponseDeclaration.toByteArray();
            Log.d(debug_tag_peer(),"Received Server Response Declaration!");

            // SEPARATING THE FIELDS OF SERVER RESPONSE DECLARATION

            // First let's read the prefix RESPONSE
            int ci = 0; // current index bytesServerResponseDeclaration
            int tempci = ci;

            // [RESPONSE]
            ByteArrayOutputStream baosServerResponseDeclarationResponse = new ByteArrayOutputStream();
            for(int i=ci;(i < bytesServerResponseDeclartion.length) && ((char)( bytesServerResponseDeclartion[i] ) != TCPServerThread.transmission_del);i++){
                baosServerResponseDeclarationResponse.write( (byte) bytesServerResponseDeclartion[i] );
                ci=i;
            }
            // check that the message has the prefix RESPONSE
            if( !( "RESPONSE".equals( new String(baosServerResponseDeclarationResponse.toByteArray(), StandardCharsets.UTF_8) ) ) ){
                safe_exit("ERROR: The prefix of the received message is " + new String(baosServerResponseDeclarationResponse.toByteArray(), StandardCharsets.UTF_8),null,s);
                return;
            }
            Log.d(debug_tag_peer(),"SUCCESS: the prefix of the received message from the intermediate node is " + new String(baosServerResponseDeclarationResponse.toByteArray(), StandardCharsets.UTF_8) );
            ci++; // Now must be on delimiter
            if( (char)( bytesServerResponseDeclartion[ci] ) != TCPServerThread.transmission_del ){
                safe_exit("Expected " + TCPServerThread.transmission_del +" after the RESPONSE bytes. Found " + bytesServerResponseDeclartion[ci],null,s);
                return;
            }
            ci++;

            // Then let's read how many bytes the response will be
            // [RESPONSE SIZE IN BYTES]
            String ResponseSizeInBytesString = "";
            for(int i=ci;(i< bytesServerResponseDeclartion.length) && ((char)( bytesServerResponseDeclartion[i] ) != TCPServerThread.transmission_del); i++){
                ResponseSizeInBytesString += (char)( bytesServerResponseDeclartion[i] );
                ci = i;
            }
            Log.d(debug_tag_peer(),"The ResponseSizeInBytesString is " + ResponseSizeInBytesString);
            int ResponseSizeInBytes = Integer.parseInt(ResponseSizeInBytesString);

            // 4.2) CLIENT DECLARATION ACCEPT
            try {
                dos.write("ACK".getBytes());
                Log.d(debug_tag_peer(),"ACKNOWLEDGMENT OF SERVER RESPONSE DECLARATION SENT SUCCESSFULLY");
            } catch (IOException e) {
                safe_exit("Could not sent ACK to the server response declaration",e,s);
                return;
            }

            // 4.3) SERVER RESPONSE: RECEIVE THE ACTUAL RESPONSE BYTES FROM THE SERVER
            // [JSONObjectAnswerByteArraySize] | [JSONObjectAnswerByteArray] | [JSONObjectAnswerByteArraySigned]
            // NOW BASED ON KNOWING HOW MUCH BYTES WE SHOULD EXPECT WE CAN READ THE ACTUAL RESPONSE

            ByteArrayOutputStream baosServerResponse;
            try {
                baosServerResponse = new ByteArrayOutputStream();
                byte[] bufferServerResponse = new byte[1000]; // we will attempt using a bigger buffer here
                int bytesReadServerResponse;
                int total_bytesServerResponse = 0;
                int times_waited = 0;
                while( total_bytesServerResponse < ResponseSizeInBytes ) {
                    bytesReadServerResponse = dis.read(bufferServerResponse);
                    total_bytesServerResponse += bytesReadServerResponse;
                    if( (bytesReadServerResponse == -1) && (total_bytesServerResponse < ResponseSizeInBytes) ){
                        if(times_waited == 0) {
                            Log.d(debug_tag_peer(), "Waiting for bytes from intermediate node!");
                        }
                        if(times_waited == 100) {
                            Log.d(debug_tag_peer(), "Waited more thatn 100 times for bytes to reach the client! " +
                                    "So far we have read only " + total_bytesServerResponse + " bytes!");
                        }
                        times_waited++;
                        continue;
                    }
                    Log.d(debug_tag_peer(),"Now read " + bytesReadServerResponse + " bytes!");
                    baosServerResponse.write(bufferServerResponse, 0, bytesReadServerResponse);
                    if (bytesReadServerResponse < bufferServerResponse.length) {
                        Log.d(debug_tag_peer(),"A segment was read that had only " + bytesReadServerResponse + " bytes!" +
                                " So far " + total_bytesServerResponse + "have been read!");
                        if(total_bytesServerResponse < ResponseSizeInBytes){
                            Log.d(debug_tag_peer(),"We won't brake the loop because not all of the expected bytes were read!");
                            continue;
                        }
                        else {
                            Log.d(debug_tag_peer(),"Since it seems that we have read all the bytes we will break the loop!");
                            break; // The buffer is not filled up that means we have reached the EOF
                        }
                    }
                    if (total_bytesServerResponse > TCPServerThread.max_transmission_cutoff) {
                        Log.d(debug_tag_peer(),"ERROR: The maximum transmission cutoff is reached! We did not expect messages more than " + TCPServerThread.max_transmission_cutoff + " bytes!");
                        throw new IOException();
                    }
                }
            } catch (IOException e) {
                safe_exit("Error when reading the Server Response",e,s);
                return;
            }
            byte [] bytesServerResponse = baosServerResponse.toByteArray();
            Log.d(debug_tag_peer(),"SUCCESS: Received ServerResponse. Size of byte array is " + bytesServerResponse.length);

            // SEPARATING THE FIELDS
            // [EncryptedJSONObjectAnswerByteArraySize] | [EncryptedJSONObjectAnswerByteArray] | [EncryptedJSONObjectAnswerByteArraySigned]
            byte [][] fieldsServerResponse = new byte[2][]; // [EncryptedJSONObjectAnswerByteArray] | [EncryptedJSONObjectAnswerByteArraySigned]
            ci = 0; // current index bytesServerResponse
            tempci = ci;

            // EncryptedJSONObjectAnswerByteArraySize
            String EncryptedJSONObjectAnswerByteArraySizeString = "";
            for(int i=ci;(char)( bytesServerResponse[i] ) != TCPServerThread.transmission_del; i++){
                EncryptedJSONObjectAnswerByteArraySizeString += (char)( bytesServerResponse[i] );
                ci = i;
            }
            Log.d(debug_tag_peer(),"The EncryptedJSONObjectAnswerByteArraySizeString is " + EncryptedJSONObjectAnswerByteArraySizeString);
            int EncryptedJSONObjectAnswerByteArraySize = Integer.parseInt(EncryptedJSONObjectAnswerByteArraySizeString);

            ci++; // Now must be on delimiter
            if( (char)(bytesServerResponse[ci]) != TCPServerThread.transmission_del ){
                safe_exit("Expected " + TCPServerThread.transmission_del +" after the server response json object ansewr bytes array size. Found " + bytesServerResponse[ci],null,s);
                return;
            }
            ci++;

            // EncryptedJSONObjectAnswerByteArray
            tempci = ci;
            ByteArrayOutputStream baosServerResponseJSONObjectAnswerByteArray = new ByteArrayOutputStream();
            for(int i=ci;i<ci+EncryptedJSONObjectAnswerByteArraySize;i++){
                baosServerResponseJSONObjectAnswerByteArray.write((byte)bytesServerResponse[i]);
                tempci = i;
            }
            fieldsServerResponse[0] = baosServerResponseJSONObjectAnswerByteArray.toByteArray();
            ci = tempci;

            ci++; // Now must be on delimiter
            if( (char)(bytesServerResponse[ci]) != TCPServerThread.transmission_del ){
                safe_exit("Expected " + TCPServerThread.transmission_del +" after the server response json object ansewr bytes. Found " + bytesServerResponse[ci],null,s);
                return;
            }
            ci++;

            // JSONObjectAnswerByteArraySigned
            ByteArrayOutputStream baosServerResponseJSONObjectAnswerByteArraySigned = new ByteArrayOutputStream();
            for(int i=ci;i< bytesServerResponse.length;i++){
                baosServerResponseJSONObjectAnswerByteArraySigned.write((byte)(bytesServerResponse[i]));
            }
            fieldsServerResponse[1] = baosServerResponseJSONObjectAnswerByteArraySigned.toByteArray();

            Log.d(debug_tag_peer(),"SUCCESS: The response has been received by the intermediate node! Now performing checks!");

            // Client: Success/Failure ← Verpub_server(Er,Sr)
            // Check that the JSON array is indeed signed by the peer server
            try {
                if( !CryptoChecks.isSignedByCert(fieldsServerResponse[0],fieldsServerResponse[1],this.peer_cert) ){
                    safe_exit("ERROR: The received response signature is INCORRECT!",null,s);
                    return;
                }
            } catch (Exception e) {
                safe_exit("ERROR: Failure on the cryptographic checks on Server Response",e,s);
                return;
            }
            Log.d(debug_tag_peer(),"SUCCESS: The received response's signature is correct!");

            // Now we will decrypt the encrypted JSON object
            byte [] decryptedJSON;
            try {
                decryptedJSON = InterNodeCrypto.decryptWithKey(fieldsServerResponse[0],this.my_key); // we decrypt with the pseudo key
            } catch (Exception e) {
                safe_exit("ERROR: Decrypting the server response with our own key failure!",e,s);
                return;
            }
            Log.d(debug_tag_peer(),"SUCCESS: The decryption of the response has finished! The response is stored in index " + index4AnswerStoring);
            SearchingNodeFragment.peerResponseDecJson[index4AnswerStoring] = decryptedJSON;
            // unlocking the response for the collection thread
            safe_close_socket(s);
            SearchingNodeFragment.mutexPeerResponseDecJson[index4AnswerStoring].unlock();
            return;
        }

        public String debug_tag_peer(){
            return (String)("PeerInteractionThread -> Peer " + peerIP);
        }

        public void safe_exit(String message, Exception e, Socket s){
            Log.d("PeerInteractionThread -> Peer " + peerIP,message);
            if(e!=null) {
                e.printStackTrace();
            }
            safe_close_socket(s);
            SearchingNodeFragment.peerResponseDecJson[index4AnswerStoring] = null;
            SearchingNodeFragment.mutexPeerResponseDecJson[index4AnswerStoring].unlock();
            return;
        }

        public void safe_close_socket(Socket s){
            try{
                s.close();
            }
            catch (Exception e){
                Log.d("PeerInteractionThread -> Peer " + peerIP,"ERROR: Could not close socket with the other peer");
                e.printStackTrace();
            }
        }

        /*
        * Function to carry out and collect the data from:
        * CLIENT HELLO (querying node sends hello to the serving peer)
        * SERVER HELLO (serving node send hello to the querying peer)
        * */
        public boolean configure_peer_connectivity(Socket s){
            try {

                DataInputStream dis = new DataInputStream(s.getInputStream());
                DataOutputStream dos = new DataOutputStream(s.getOutputStream());

                // 1) HANDSHAKE STEP 1: SEND CLIENT PSEUDO CREDS
                // [HELLO]:5 | [CERTIFICATE BYTES]:~2K | [timestamp]:8 | [signed_timestamp]: 256

                byte[] helloField = "HELLO".getBytes();
                byte[] certificateFieldClientHello = my_cert.getEncoded();
                byte[] certificateFieldClientHelloLength = ("" + certificateFieldClientHello.length).toString().getBytes();
                CryptoTimestamp cryptoTimestamp = InterNodeCrypto.getSignedTimestampWithKey(my_key);

                ByteArrayOutputStream baosClientHello = new ByteArrayOutputStream();
                baosClientHello.write(helloField);
                baosClientHello.write((byte)(TCPServerControlClass.transmission_del));
                baosClientHello.write(certificateFieldClientHelloLength);
                baosClientHello.write((byte)(TCPServerControlClass.transmission_del));
                baosClientHello.write(certificateFieldClientHello);
                baosClientHello.write((byte)(TCPServerControlClass.transmission_del));
                baosClientHello.write(cryptoTimestamp.timestamp);
                baosClientHello.write((byte)(TCPServerControlClass.transmission_del));
                baosClientHello.write(cryptoTimestamp.signed_timestamp);

                byte [] ClientHello = baosClientHello.toByteArray();
                String ClientHelloDebugString = new String(ClientHello, StandardCharsets.UTF_8);
                Log.d("PeerConnectivity", "ClientHelloDebugString: " + ClientHelloDebugString);
                dos.write(ClientHello);
                Log.d("PeerConnectivity","SUCCESS: Sent Client Hello!");

                // 2) HANDSHAKE STEP 2: RECEIVE SERVER CREDENTIALS
                // [HELLO]:5 | [CERTIFICATE BYTES]:~2K | [timestamp]:8 | [signed_timestamp]: 256
                ByteArrayOutputStream baosServerHello = new ByteArrayOutputStream();
                baosServerHello = TCPhelpers.receiveBuffedBytesNoLimit(dis);
                Log.d("Peer Connectivity","Server Hello Received");
                byte[] bytesServerHello = baosServerHello.toByteArray();

                // SEPARATING THE FIELDS
                byte [][] fieldsServerHello = new byte[4][];
                int ci = 0; // current index on bytesServerHello
                int tempci = ci;

                // HELLO
                ByteArrayOutputStream baosServerHelloHello = new ByteArrayOutputStream();
                for(int i=ci;(char)( bytesServerHello[i] ) != TCPServerControlClass.transmission_del;i++){
                    baosServerHelloHello.write( (byte) bytesServerHello[i] );
                    ci=i;
                }
                fieldsServerHello[0] = baosServerHelloHello.toByteArray();

                ci++; // Now must be on delimiter
                if( (char)( bytesServerHello[ci] ) != TCPServerControlClass.transmission_del ){
                    Log.d("Peer Connectivity","Expected " + TCPServerControlClass.transmission_del +" after the HELLO bytes. Found " + bytesServerHello[ci]);
                    return false;
                }
                ci++;

                // SERVER CERTIFICATE LENGTH
                String certificateServerHelloLength = "";
                for(int i=ci;(char)( bytesServerHello[i] ) != TCPServerControlClass.transmission_del;i++){
                    certificateServerHelloLength += (char) bytesServerHello[i];
                    ci = i;
                }
                int certificateServerHelloLengthInt = Integer.parseInt(certificateServerHelloLength);

                ci++; // Now must be on delimiter
                if( (char)(bytesServerHello[ci]) != TCPServerControlClass.transmission_del ){
                    Log.d("Peer Connectivity","Expected " + TCPServerControlClass.transmission_del +" after the Server CERT LENGTH bytes. Found " + bytesServerHello[ci]);
                    return false;
                }
                ci++;

                // SERVER CERTIFICATE BYTES
                tempci = ci;
                ByteArrayOutputStream baosServerHelloCertificate = new ByteArrayOutputStream();
                for(int i=ci;i<ci+certificateServerHelloLengthInt;i++){
                    baosServerHelloCertificate.write((byte)bytesServerHello[i]);
                    tempci = i;
                }
                fieldsServerHello[1] = baosServerHelloCertificate.toByteArray();
                ci = tempci;

                ci++; // Now must be on delimiter
                if(bytesServerHello[ci] != TCPServerControlClass.transmission_del){
                    Log.d("Peer Connectivity","Expected " + TCPServerControlClass.transmission_del + " after the server CERTIFICATE bytes. Found " + bytesServerHello[ci]);
                    return false;
                }
                ci++;

                // timestamp
                tempci = ci;
                ByteArrayOutputStream baosServerHelloNonce = new ByteArrayOutputStream();
                for(int i=ci;i<ci+InterNodeCrypto.TIMESTAMP_BYTES;i++){
                    baosServerHelloNonce.write((byte)(bytesServerHello[i]));
                    tempci=i;
                }
                fieldsServerHello[2] = baosServerHelloNonce.toByteArray();
                ci = tempci;

                ci++; // Now must be on delimiter
                if( (char)( bytesServerHello[ci] ) != TCPServerControlClass.transmission_del){
                    Log.d("Peer Connectivity","Expected " + TCPServerControlClass.transmission_del + " after the NONCE bytes. Found " + bytesServerHello[ci]);
                    return false;
                }
                ci++;

                // signed timestamp
                ByteArrayOutputStream baosServerHelloSignedNonce = new ByteArrayOutputStream();
                for(int i=ci;i<bytesServerHello.length;i++){
                    baosServerHelloSignedNonce.write((byte)(bytesServerHello[i]));
                }
                fieldsServerHello[3] = baosServerHelloSignedNonce.toByteArray();

                if(!checkFieldsHelloServer(fieldsServerHello)){
                    Log.d("Peer Connectivity","The received fields are incorrect! Closing connection!");
                    return false;
                }

                Log.d("Peer Connectivity","The received fields are CORRECT!");
                // storing the peer certificate for the rest of the communication
                peer_cert = InterNodeCrypto.CertFromByteArray(fieldsServerHello[1]);
                peer_name = InterNodeCrypto.getCommonName(peer_cert);
                Log.d("Peer Connectivity","SUCCESS THE PEER CERTIFICATE IS NOW READY TO USE!");
                return true;
            } catch (Exception e) {
                Log.d("Peer Connectivity","Error on the HELLO phase");
                e.printStackTrace();
                return false;
            }
        }

        public boolean checkFieldsHelloServer(byte [][] arr) {
            // For now client and server use the same fields in the hello messages
            // So we can use the same function to check that the fields received are correct
            return InterNodeCrypto.checkFieldsClientHello(arr,"Client");
        }

    }

}
