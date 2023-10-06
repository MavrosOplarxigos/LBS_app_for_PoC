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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class PeerInteractions {

    public static class PeerInteractionThread extends Thread{

        // How much time do we wait for a peer to respond every time. We have to consider that it will talk to the signing server and that server will talk to the LBS server
        // so realistically we should expect a descent amount of latency after we send the client query.
        public static final int SERVING_PEER_CONNECTION_TIMEOUT_MSEC = 5000;
        public static final int SERVING_PEER_CONNECTION_TIMEOUT_MSEC_HELLO = 1000;
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

            // setting pre-hello socket timeout
            try {
                s.setSoTimeout(SERVING_PEER_CONNECTION_TIMEOUT_MSEC_HELLO);
            } catch (SocketException e) {
                LoggingFragment.mutexTvdAL.lock();
                LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("COULD NOT set client socket timeout pre HELLO!", Color.RED) );
                LoggingFragment.mutexTvdAL.unlock();
                safe_exit("ERROR: Could not set socket timeout!",null,s);
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
            /*LoggingFragment.mutexTvdAL.lock();
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Hello SUCCESS with: " + peer_name + "@ " + peerIP, Color.GREEN ) );
            LoggingFragment.mutexTvdAL.unlock();*/

            // setting larger socket timeout
            try {
                s.setSoTimeout(SERVING_PEER_CONNECTION_TIMEOUT_MSEC);
            } catch (SocketException e) {
                LoggingFragment.mutexTvdAL.lock();
                LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("COULD NOT set client socket timeout for AFTER hello phase with peer!", Color.RED) );
                LoggingFragment.mutexTvdAL.unlock();
                safe_exit("ERROR: Could not set socket timeout!",null,s);
                return;
            }

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
                // Changed from:
                // APICallBytesSignedClientQuery = InterNodeCrypto.signByteArrayWithPrivateKey(APICallEncryptedBytesClientQuery, my_key);
                // To: (TODO: fix on the TCP Server maybe to sign the raw request instead to save memory)
                APICallBytesSignedClientQuery = InterNodeCrypto.signByteArrayWithPrivateKey(APICallBytesClientQuery, my_key);
            }
            catch (Exception e){
                safe_exit("ERROR: Could not sign the encrypted bytes client query with my private key",e,s);
                return;
            }

            int OriginalQueryArraySize = APICallBytesClientQuery.length;
            byte [] OriginalQueryArraySizeBytes = TCPhelpers.intToByteArray(OriginalQueryArraySize);

            /*LoggingFragment.mutexTvdAL.lock();
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Original Query Size: " + OriginalQueryArraySize , Color.DKGRAY ) );
            LoggingFragment.mutexTvdAL.unlock();*/

            ByteArrayOutputStream baosClientQuery = new ByteArrayOutputStream();
            try {
                baosClientQuery.write(queryBytesClientQuery); // The string QUERY
                baosClientQuery.write(OriginalQueryArraySizeBytes); // The Decrytped Query Array Size
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

            // Log.d("CLIENT_QUERY_IMPORTANT","The length of the signatuere is: " + APICallBytesSignedClientQuery.length );
            // Log.d("CLIENT_QUERY_IMPORTANT","The signatuere is: " + TCPhelpers.byteArrayToDecimalString(APICallBytesSignedClientQuery) );
            // LoggingFragment.mutexTvdAL.lock();
            // LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails( "Original API CALL: " + TCPhelpers.byteArrayToDecimalString(APICallBytesClientQuery) , Color.DKGRAY ) );
            // LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails( "API CALL Signature: " + TCPhelpers.byteArrayToDecimalString(APICallBytesSignedClientQuery) , Color.DKGRAY ) );
            /*try {
                byte [] test_encoded = my_cert.getEncoded();
                // LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails( "Certificate: " + TCPhelpers.byteArrayToDecimalString(my_cert.getEncoded()) , Color.DKGRAY ) );
            } catch (CertificateEncodingException e) {
                safe_exit("ERROR: Can't get the certificate encoded!",e,s);
                return;
            }
            try {
                boolean cert_check_sanity = CryptoChecks.isSignedByCert(APICallBytesClientQuery,APICallBytesSignedClientQuery,my_cert);
                // LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails( "My isSignedByCert = " + CryptoChecks.isSignedByCert(APICallBytesClientQuery,APICallBytesSignedClientQuery,my_cert) , Color.DKGRAY ) );
            } catch (Exception e) {
                safe_exit("ERROR: could not even check my own query!",e,s);
                return;
            }*/
            // LoggingFragment.mutexTvdAL.unlock();

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

            // 4.1.1) GET ANSWER length
            byte [] reply_size_bytes = new byte[0];
            try {
                reply_size_bytes = TCPhelpers.buffRead(4,dis);
            } catch (IOException e) {
                safe_exit("Error: Could not read the reply size from remote peer",e,s);
                return;
            }
            int reply_size = TCPhelpers.byteArrayToIntBigEndian(reply_size_bytes);
            Log.d(debug_tag_peer(),"The size expected from the SERVING PEER ANSWER FORWARD message is: " + reply_size);

            /*LoggingFragment.mutexTvdAL.lock();
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Expected answer size from Peer: " + reply_size, Color.MAGENTA ) );
            LoggingFragment.mutexTvdAL.unlock();*/

            // 4.1) SERVING PEER ANSWR FORWARD
            // [ENC_ANS_LEN: 4] | [ENC_ANS] | [SSQA_LEN: 4] | [SSQA] | [DEC_ANS_LEN]

            // ByteArrayOutputStream baosSP_AFWD = null;
            byte [] SP_AFWD = null;
            try{
                // baosSP_AFWD = TCPhelpers.receiveBuffedBytesNoLimit(dis);
                SP_AFWD = TCPhelpers.buffRead(reply_size,dis);
            }
            catch (Exception e){
                safe_exit("Error: Could not retrieve the response from the SERVING PEER ANSWER FWD phase",e,s);
                return;
            }
            // byte [] SP_AFWD = baosSP_AFWD.toByteArray();

            // Log.d(debug_tag_peer(),"SP_AFWD real length: " + SP_AFWD.length);
            // Log.d(debug_tag_peer(),"SP_AFWD first 10 bytes: " + TCPhelpers.byteArrayToDecimalStringFirst10(SP_AFWD) );

            /*LoggingFragment.mutexTvdAL.lock();
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Answer size from Peer: " + SP_AFWD.length, Color.MAGENTA ) );
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Answer from Peer bytes: " + TCPhelpers.byteArrayToDecimalStringFirst10(SP_AFWD), Color.MAGENTA ) );
            LoggingFragment.mutexTvdAL.unlock();*/

            int afci = 0; // The index we are looking at in the answer forward byte array

            // Parse the response
            byte [] encAnsLenByteArray = new byte[4];
            for(int i=0;i<4;i++){
                if(afci+i >= SP_AFWD.length){
                    safe_exit("Error: The serving peer answer is too small. Could not get encAnsLenByteArray.",null,s);
                    return;
                }
                encAnsLenByteArray[i] = SP_AFWD[afci+i];
            }
            afci+=4;

            int ENC_ANS_LEN = TCPhelpers.byteArrayToIntBigEndian(encAnsLenByteArray);

            // Log.d(debug_tag_peer(),"SP_FWD ENC_ANS_LEN: " + ENC_ANS_LEN);

            /*LoggingFragment.mutexTvdAL.lock();
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("SP_FWD ENC_ANS_LEN: " + ENC_ANS_LEN, Color.MAGENTA ) );
            LoggingFragment.mutexTvdAL.unlock();*/

            byte [] encAns = new byte[ENC_ANS_LEN];
            for(int i=0;i<ENC_ANS_LEN;i++){
                if(afci + i >= SP_AFWD.length){
                    safe_exit("Error: The serving peer answer is too small. Could not get ENC ANS.",null,s);
                    return;
                }
                encAns[i] = SP_AFWD[afci+i];
            }
            afci+=ENC_ANS_LEN;

            // Log.d(debug_tag_peer(),"SP_FWD encAns : " + TCPhelpers.byteArrayToDecimalStringFirst10(encAns));

            /*LoggingFragment.mutexTvdAL.lock();
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("SP_FWD encAns : " + TCPhelpers.byteArrayToDecimalStringFirst10(encAns), Color.MAGENTA ) );
            LoggingFragment.mutexTvdAL.unlock();*/

            // checking signature
            byte [] SSQALenByteArray = new byte[4];
            for(int i=0;i<4;i++){
                if(afci + i >= SP_AFWD.length){
                    safe_exit("Error: The serving peer answer is too small. Could not get SSQALenByteArray.",null,s);
                    return;
                }
                SSQALenByteArray[i] = SP_AFWD[afci+i];
            }
            afci+=4;
            int SSQA_LEN = TCPhelpers.byteArrayToIntBigEndian(SSQALenByteArray);

            // Log.d(debug_tag_peer(),"SP_FWD SSQA_LEN: " + SSQA_LEN);

            /*LoggingFragment.mutexTvdAL.lock();
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("SSQA LEN: " + SSQA_LEN, Color.MAGENTA ) );
            LoggingFragment.mutexTvdAL.unlock();*/

            byte [] SSQA = new byte[SSQA_LEN];
            for(int i=0;i<SSQA_LEN;i++){
                if(afci + i >= SP_AFWD.length){
                    safe_exit("Error: The serving peer answer is too small. Could not get SSQA.",null,s);
                    return;
                }
                SSQA[i] = SP_AFWD[afci + i];
            }
            afci += SSQA_LEN;

            // Log.d(debug_tag_peer(),"SSQA concatenated : " + TCPhelpers.byteArrayToDecimalStringFirst10(SSQA));

            /*LoggingFragment.mutexTvdAL.lock();
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("SSQA: " + TCPhelpers.byteArrayToDecimalStringFirst10(SSQA), Color.MAGENTA ) );
            LoggingFragment.mutexTvdAL.unlock();*/

            byte [] DEC_ANS_LEN_BYTES = new byte[4];
            for(int i=0;i<4;i++){
                if(afci + i >= SP_AFWD.length){
                    safe_exit("Error: The serving peer answer is too small. Could not get DEC_ANS_LEN.",null,s);
                    return;
                }
                DEC_ANS_LEN_BYTES[i] = SP_AFWD[afci + i];
            }
            afci += 4;
            int DEC_ANS_LEN = TCPhelpers.byteArrayToIntLittleEndian(DEC_ANS_LEN_BYTES);

            // Log.d(debug_tag_peer(),"DEC_ANS_LEN: " + DEC_ANS_LEN);

            /*LoggingFragment.mutexTvdAL.lock();
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("DEC_ANS_LEN: " + DEC_ANS_LEN, Color.MAGENTA ) );
            LoggingFragment.mutexTvdAL.unlock();*/

            byte [] ANSWER = null;
            try {
                ANSWER = InterNodeCrypto.decryptWithKey(encAns, my_key,DEC_ANS_LEN);
                // Log.d(debug_tag_peer(),"DECRYPTED ANSWER: " + TCPhelpers.byteArrayToDecimalStringFirst10(ANSWER));
                // Log.d(debug_tag_peer(),"DECRYPTED ANSWER last 10: " + TCPhelpers.byteArrayToDecimalStringLast10(ANSWER));
                /*LoggingFragment.mutexTvdAL.lock();
                LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("DECRYPTED ANSWER first 10: " + TCPhelpers.byteArrayToDecimalStringFirst10(ANSWER), Color.MAGENTA ) );
                LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("DECRYPTED ANSWER last 10: " + TCPhelpers.byteArrayToDecimalStringLast10(ANSWER), Color.MAGENTA ) );
                LoggingFragment.mutexTvdAL.unlock();*/
            }
            catch (Exception e){
                safe_exit("Error: Could not decrypt the answer with our own private key!",e,s);
            }

            // Log.d(debug_tag_peer(),"APICallBytesClientQuery: " + TCPhelpers.byteArrayToDecimalStringFirst10(APICallBytesClientQuery));
            // Log.d(debug_tag_peer(),"APICallBytesClientQuery last 10: " + TCPhelpers.byteArrayToDecimalStringLast10(APICallBytesClientQuery));

            /*LoggingFragment.mutexTvdAL.lock();
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("APICallBytesClientQuery: " + TCPhelpers.byteArrayToDecimalStringFirst10(APICallBytesClientQuery), Color.MAGENTA ) );
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("APICallBytesClientQuery last 10: " + TCPhelpers.byteArrayToDecimalStringLast10(APICallBytesClientQuery), Color.MAGENTA ) );
            LoggingFragment.mutexTvdAL.unlock();*/

            // OK now we should check the concatenation
            byte [] QAconcatenation = new byte[APICallBytesClientQuery.length + ANSWER.length];
            System.arraycopy(APICallBytesClientQuery,0,QAconcatenation,0,APICallBytesClientQuery.length);
            System.arraycopy(ANSWER,0,QAconcatenation,APICallBytesClientQuery.length,ANSWER.length);

            // String hashOfConcatenation = TCPhelpers.calculateSHA256Hash(QAconcatenation);

            /*LoggingFragment.mutexTvdAL.lock();
            Log.d(debug_tag_peer(),"Hash of concatenatino = " + hashOfConcatenation);
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Hash of concatenatino: " + hashOfConcatenation, Color.MAGENTA ) );
            LoggingFragment.mutexTvdAL.unlock();*/

            boolean SSsignatureValid = true;
            try {
                SSsignatureValid = CryptoChecks.isSignedByCert(QAconcatenation, SSQA, InterNodeCrypto.CA_cert);
            }
            catch (Exception e) {
                safe_exit("Error: Could not check the signature of the SS on the concatenated QA",e,s);
                return;
            }

            if(!SSsignatureValid){
                safe_exit("Error: The signature on the concatenated QA from the SS is not valid!",null,s);
                return;
            }

            Log.d("Peer Interaction","SUCCESS: send query and received/verified answer with " + peer_name + " @ " + peerIP);
            SearchingNodeFragment.peerResponseDecJson[index4AnswerStoring] = ANSWER;

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
                // Log.d("PeerConnectivity", "ClientHelloDebugString: " + ClientHelloDebugString);
                dos.write(ClientHello);
                Log.d("PeerConnectivity","SUCCESS: Sent Client Hello!");

                // 2) HANDSHAKE STEP 2: RECEIVE SERVER CREDENTIALS
                // [HELLO]:5 | [CERTIFICATE BYTES]:~2K | [timestamp]:8 | [signed_timestamp]: 256
                ByteArrayOutputStream baosServerHello = new ByteArrayOutputStream();
                baosServerHello = TCPhelpers.receiveBuffedBytesNoLimit(dis);
                Log.d("Peer Connectivity","Serving Peer Hello Received");
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
