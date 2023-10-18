package com.example.lbs_app_for_poc;

import android.graphics.Color;
import android.util.Log;

import org.bouncycastle.asn1.dvcs.Data;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class ServingNodeQueryHandleThread extends Thread {

    // Map so that we serve at most MAX_SERVICES_PER_MINUTE_PER_PSEUDO_CERT
    public static HashMap< String , ServiceDetails > recentlyServedNodes = new HashMap<>();

    // The class below is used to store data that showcase how often a certain pseudo cert
    // has been used from a querying node and what service per minute rate it has received
    public class ServiceDetails{

        Queue<Long> requestTimestampQueue;
        public static final int MAX_SERVICES_PER_MINUTE_PER_PSEUDO_CERT = 4 * 100 * 1000;

        ServiceDetails(){
            this.requestTimestampQueue = new LinkedList<>();
            long timestamp = System.currentTimeMillis();
            requestTimestampQueue.offer(timestamp);
        }

        // @returns true if we should carry out the request (not too frequest) and false otherwise.
        public boolean addNewRequest(){
            long timestamp = System.currentTimeMillis();
            if(requestTimestampQueue.size() >= MAX_SERVICES_PER_MINUTE_PER_PSEUDO_CERT){

                Long new_timestamp = new Long(timestamp);
                if(moreThanAMinutePassed(requestTimestampQueue.peek(),new_timestamp)){
                    requestTimestampQueue.remove();
                    requestTimestampQueue.offer(new_timestamp);
                    return true;
                }
                else{
                    return false;
                }

            }
            requestTimestampQueue.offer(timestamp);
            return true;
        }

        public boolean moreThanAMinutePassed(Long time1, Long time2){
            long tim1 = time1.longValue();
            long tim2 = time2.longValue();
            if(tim2-tim1>60000){
                return true;
            }
            return false;
        }

    }

    public String peerIP;
    public Socket socket;
    public static char transmission_del;
    public static final String tradelTAG = "Expected transmission delimiter not found!";

    // Whenever the availability we advertise changes we will change the PSEUDO CREDS
    // that we use for serving (we pick another random pseudo certificate). This is handled by the
    // AvailabilityThread class. In this class we just need to use the lock when copying the current instance.
    public static X509Certificate my_cert_to_copy = null;
    public static PrivateKey my_key_to_copy = null;
    public static ReentrantLock my_PSEUDO_CREDS_TO_COPY_lock = null;

    public X509Certificate my_cert = null;
    public PrivateKey my_key = null;
    public X509Certificate peer_cert = null;
    public String peer_name = null;
    public boolean peerWasTooFrequent;

    public int QUERYING_PEER_CONNECTION_TIMEOUT_HELLO = 1000;

    public ServingNodeQueryHandleThread(Socket socket){
        peerWasTooFrequent = false;
        this.socket = socket;
        LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Accepted Service Request from " + this.socket.getInetAddress().getHostAddress() , Color.MAGENTA ) );
        LoggingFragment.mutexTvdAL.unlock();
        peerIP = socket.getInetAddress().getHostAddress();
        transmission_del = TCPServerControlClass.transmission_del;
        my_PSEUDO_CREDS_TO_COPY_lock.lock();
        my_cert = my_cert_to_copy;
        my_key = my_key_to_copy;
        my_PSEUDO_CREDS_TO_COPY_lock.unlock();
    }

    @Override
    public void run() {

        Random random = new Random();
        int random_result = random.nextInt(101);

        // For the experiments there is the probability that we won't answer to the querying user's hello
        if( random_result > SearchingNodeFragment.ANSWER_PROBABILITY_PCENT ){
            safe_close_socket(socket);
            return;
        }

        boolean introductionDone = configure_peer_connectivity(socket);
        if(!introductionDone){
            if(!peerWasTooFrequent) {
                LoggingFragment.mutexTvdAL.lock();
                LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Hello FAILURE with: " + peerIP, Color.RED));
                LoggingFragment.mutexTvdAL.unlock();
                safe_exit("Failure on the Hello phase", null, socket);
            }
            else{
                safe_close_socket(socket);
            }
            return;
        }
        /*LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Hello SUCCESS with: " + peer_name + "@ " + peerIP, Color.GREEN ) );
        LoggingFragment.mutexTvdAL.unlock();*/

        DataInputStream dis = null;
        DataOutputStream dos = null;
        try {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
        }
        catch (Exception e){
            safe_exit("Couldn't retrieve data streams from socket",null,socket);
            return;
        }

        // 3) CLIENT MESSAGE: RECEIVE A MESSAGE FROM CLIENT (QUERY,BYE)
        // ["QUERY"] [DEC_QUERY_LEN] | [API_CALL_ENC_BYTES_LENGTH] | [API_CALL_ENC_BYTES] | [API_CALL_SIGNED_BYTES]
        ByteArrayOutputStream baosClientMessage = new ByteArrayOutputStream();
        try{
            baosClientMessage = TCPhelpers.receiveBuffedBytesNoLimit(dis);
        }
        catch (Exception e){
            safe_exit("The Client Query Message couldn't be received!",e,socket);
            return;
        }
        Log.d(debug_tag(),"Client Message Received.");
        byte[] bytesClientMessage = baosClientMessage.toByteArray();
        Log.d(debug_tag(),"Client message size in bytes is " + bytesClientMessage.length);

        // separating the fields
        int ci = 0;
        int tempci = ci;

        // [QUERY]
        byte [] ClientMessageOption;
        ByteArrayOutputStream baosClientMessageOption = new ByteArrayOutputStream();
        for(int i=ci; (i<5) && (i<bytesClientMessage.length) && ((char)( bytesClientMessage[i] ) != transmission_del) ;i++){
            baosClientMessageOption.write( (byte) bytesClientMessage[i] );
            ci=i;
        }
        ClientMessageOption = baosClientMessageOption.toByteArray();
        if( !Arrays.equals(ClientMessageOption, "QUERY".getBytes()) ){
            safe_exit("The client didn't append the QUERY prefix in its request",null,socket);
            return;
        }

        // [DEC_QUERY_LEN]
        byte [] OriginalQueryArraySizeByes = new byte[4];
        for(int i=ci+1; (i<9) && (i<bytesClientMessage.length) && ((char)( bytesClientMessage[i] ) != transmission_del) ;i++){
            OriginalQueryArraySizeByes[i-5] = (bytesClientMessage[i]);
            ci=i;
        }
        int OriginalQueryArrayLength = TCPhelpers.byteArrayToIntBigEndian(OriginalQueryArraySizeByes);

        /*LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Expected Original Query Size: " + OriginalQueryArrayLength , Color.DKGRAY ) );
        LoggingFragment.mutexTvdAL.unlock();*/

        ci++;
        if( (char)(bytesClientMessage[ci]) != transmission_del ){
            safe_exit("ERROR: Expected " + TCPServerControlClass.transmission_del + " after the Client Message Option bytes. Found " + bytesClientMessage[ci],null,socket);
            return;
        }
        ci++;

        // [API_CALL_ENC_BYTES_LENGTH] | [API_CALL_ENC_BYTES] | [API_CALL_SIGNED_BYTES]
        byte [][] fieldsClientQuery = new byte[2][];

        // [API_CALL_ENC_BYTES_LENGTH]
        String APICallEncBytesLengthString = "";
        for(int i=ci;((char)(bytesClientMessage[i]) != transmission_del) && (i<bytesClientMessage.length);i++){
            APICallEncBytesLengthString += (char)(bytesClientMessage[i]);
            ci = i;
        }
        int APICallEncBytesLength = Integer.parseInt(APICallEncBytesLengthString);
        ci++;
        if( (char)(bytesClientMessage[ci]) != transmission_del ){
            safe_exit("ERROR: Expected " + TCPServerControlClass.transmission_del + " after the Client Query API Call Enc bytes. Found " + bytesClientMessage[ci],null,socket);
            return;
        }
        ci++;

        // [API_CALL_ENC_BYTES]
        ByteArrayOutputStream baosClientQueryAPICallEncBytes = new ByteArrayOutputStream();
        for(int i=ci;( i < ci+APICallEncBytesLength ) && ( i < bytesClientMessage.length );i++){
            baosClientQueryAPICallEncBytes.write(bytesClientMessage[i]);
            tempci = i;
        }
        ci = tempci;
        fieldsClientQuery[0] = baosClientQueryAPICallEncBytes.toByteArray();
        // delimiter needs to be checked
        ci++;
        if( (char)(bytesClientMessage[ci]) != transmission_del ){
            safe_exit("ERROR: Expected " + transmission_del +" after the Client Query API Call Enc bytes. Found " + bytesClientMessage[ci],null,socket);
            return;
        }
        ci++;

        // [API_CALL_SIGNED_BYTES]
        ByteArrayOutputStream baosClientQueryAPICallSignedBytes = new ByteArrayOutputStream();
        for(int i=ci;i < bytesClientMessage.length;i++){
            baosClientQueryAPICallSignedBytes.write(bytesClientMessage[i]);
        }
        fieldsClientQuery[1] = baosClientQueryAPICallSignedBytes.toByteArray();

        // CHECK QUERY FIELDS CRYPTOGRAPHY
        byte [] API_CALL_RAW_BYTES = null;
        try {
            API_CALL_RAW_BYTES = crypto_check_query(fieldsClientQuery,OriginalQueryArrayLength);
        }
        catch(Exception e){
            safe_exit("The cryptography checks on the Client Query did not pass",e,socket);
            return;
        }

        // Since we have query now we can proceed with talking to the signing server to complete it
        // [0]: the answer to the query encrypted with the querying peer key
        // [1]: the signature of the RAW query CONCATENATED with the response with the signing server key (CA key)
        // [2]: the SIZE of the original ANSWER DEC_ANS_LEN
        byte [][] ss_answer = null;
        try{
            ss_answer = SigningServerInterations.ProxyQuery(API_CALL_RAW_BYTES,my_cert,my_key,peer_cert);
        }
        catch (Exception e){
            safe_exit("Could not retrieve answer from signing server!",e,socket);
            return;
        }

        int enc_ans_len = ss_answer[0].length;
        byte [] enc_ans_len_bytes = TCPhelpers.intToByteArrayBigEndian(enc_ans_len);
        int ssqa_len = ss_answer[1].length;
        byte [] ssqa_len_bytes = TCPhelpers.intToByteArrayBigEndian(ssqa_len);

        // Now that we have the response fields we should sent them back to the querying peer
        try {
            ByteArrayOutputStream baosServingPeerAnswerFWD = new ByteArrayOutputStream();
            baosServingPeerAnswerFWD.write(enc_ans_len_bytes); // 4
            baosServingPeerAnswerFWD.write(ss_answer[0]); //
            baosServingPeerAnswerFWD.write(ssqa_len_bytes);
            baosServingPeerAnswerFWD.write(ss_answer[1]);
            baosServingPeerAnswerFWD.write(ss_answer[2]);
            byte [] ServingPeerAnswerFwd = baosServingPeerAnswerFWD.toByteArray();

            byte [] AnswerSizeDisclosureBytes = TCPhelpers.intToByteArray(ServingPeerAnswerFwd.length);
            dos.write(AnswerSizeDisclosureBytes);

            /*LoggingFragment.mutexTvdAL.lock();
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("SP_FWD Reply size length: " + ServingPeerAnswerFwd.length, Color.MAGENTA ) );
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("SP_FWD Reply bytes: " + TCPhelpers.byteArrayToDecimalStringFirst10(ServingPeerAnswerFwd), Color.MAGENTA ) );
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("SP_FWD ENC_ANS_LEN : " + enc_ans_len, Color.MAGENTA ) );
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("SP_FWD ENC_ANS: " + TCPhelpers.byteArrayToDecimalStringFirst10(ss_answer[0]), Color.MAGENTA ) );
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("SP_FWD QA_CONCATENATED LEN: " + ssqa_len, Color.MAGENTA ) );
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("SP_FWD QA_CONCATENATED: " + TCPhelpers.byteArrayToDecimalStringFirst10(ss_answer[1]), Color.MAGENTA ) );
            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("SP_FWD DEC_ANSWER_LENGTH_BYTES: " + TCPhelpers.byteArrayToDecimalStringFirst10(ss_answer[2]), Color.MAGENTA ) );
            LoggingFragment.mutexTvdAL.unlock();*/

            dos.write(ServingPeerAnswerFwd);

        }
        catch (Exception e){
            safe_exit("Could not finish the SERVING PEER ANSWER FWD phase",e,socket);
            return;
        }

        Log.d("Serving Node Query Handle Thread","SUCCESS: Peer " + peer_name + " @ " + peerIP + " was serviced!");
    }

    public byte [] crypto_check_query(byte [][] fields,int OriginalQueryArrayLength) throws NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException, SignatureException, NoSuchProviderException, InvalidAlgorithmParameterException, CertificateEncodingException {
        // Get raw query
        byte [] raw_query = InterNodeCrypto.decryptWithKey(fields[0],my_key,OriginalQueryArrayLength);
        Log.d("SNQHT","The decrypted RAW_QUERY is " + new String(raw_query, StandardCharsets.UTF_8) );

        /*LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Original API CALL: " + TCPhelpers.byteArrayToDecimalString(raw_query), Color.DKGRAY ) );
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("API CALL Signature: " + TCPhelpers.byteArrayToDecimalString(fields[1]), Color.DKGRAY ) );
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails( "Certificate: " + TCPhelpers.byteArrayToDecimalString(peer_cert.getEncoded()) , Color.DKGRAY ) );
        LoggingFragment.mutexTvdAL.unlock();*/

        Log.d("SNQHT_QUERY_IMPORTANT","The length of the signatuere is: " + fields[1].length );
        Log.d("SNQHT_QUERY_IMPORTANT","The signatuere is: " + TCPhelpers.byteArrayToDecimalString(fields[1]) );

        // Check signature
        boolean signature_ok = CryptoChecks.isSignedByCert(raw_query,fields[1],peer_cert);

        /*LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("signature check serving node = " + signature_ok, Color.DKGRAY ) );
        LoggingFragment.mutexTvdAL.unlock();*/

        if( !signature_ok ){
            Log.d(debug_tag(),"The signature of the Client Query is not valid!");
            // we throw an exception so that the serving peer will stop the connection
            throw new SignatureException();
        }
        return raw_query;
    }

    // Function to receive Client Hello
    // Function to send Server Hello
    public boolean configure_peer_connectivity(Socket s){

        DataInputStream dis = null;
        DataOutputStream dos = null;

        try{
            s.setSoTimeout(QUERYING_PEER_CONNECTION_TIMEOUT_HELLO);
        }
        catch (Exception e){
            safe_exit("Could not initialize the socket timeout",e,s);
            return false;
        }

        try {
            dis = new DataInputStream(s.getInputStream());
            dos = new DataOutputStream(s.getOutputStream());
        }
        catch (Exception e){
            safe_exit("Could not initialize input and output streams for Hello phase",e,s);
            return false;
        }

        // 1) HANDSHAKE STEP 1: RECEIVE CLIENT PSEUDO CREDS
        // [HELLO]:5 | [CERTIFICATE BYTES]:~2K | [timestamp]:8 | [signed_timestamp]: 256
        Log.d("SNQHT","Now started carrying out the HELLO phase!");
        ByteArrayOutputStream baosClientHello = null;
        try {
            baosClientHello = TCPhelpers.receiveBuffedBytesNoLimit(dis);
        }
        catch (Exception e){
            safe_exit("Error: On receiving the querying peer hello",e,s);
            return false;
        }
        byte[] bytesClientHello = baosClientHello.toByteArray();

        // SEPARATING THE FIELDS
        byte [][] fieldsClientHello = new byte[4][];
        int ci = 0; // current index on bytesClientHello
        int tempci = ci;

        // [HELLO]
        ByteArrayOutputStream baosClientHelloHello = new ByteArrayOutputStream();
        for(int i=ci;(char)( bytesClientHello[i] ) != TCPServerControlClass.transmission_del;i++){
            baosClientHelloHello.write( (byte) bytesClientHello[i] );
            ci=i;
        }
        fieldsClientHello[0] = baosClientHelloHello.toByteArray();
        ci++; // Now must be on delimiter
        if( (char)( bytesClientHello[ci] ) != transmission_del ){
            safe_exit(tradelTAG,null,s);
            return false;
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
            safe_exit(tradelTAG,null,s);
            return false;
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
            safe_exit(tradelTAG,null,s);
            return false;
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
            safe_exit(tradelTAG,null,s);
            return false;
        }
        ci++;

        // [SIGNED TIMESTAMP]
        ByteArrayOutputStream baosClientHelloSignedNonce = new ByteArrayOutputStream();
        for(int i=ci;i<bytesClientHello.length;i++){
            baosClientHelloSignedNonce.write((byte)(bytesClientHello[i]));
        }
        fieldsClientHello[3] = baosClientHelloSignedNonce.toByteArray();

        // CHECKING FIELDS
        if(!InterNodeCrypto.checkFieldsClientHello(fieldsClientHello,"Server")){
            safe_exit("QUERYING PEER HELLO: The received fields are incorrect! Closing the connection.",null,s);
            return false;
        }
        Log.d(debug_tag(),"SUCCESS: Client Hello received and has VALID fields!");

        // SAVING THE QUERYING NODE CREDENTIALS AND CARRYING OUT RECENCY CHECK
        try {
            // Saving into variables the data that we need from ClientHello
            peer_cert = InterNodeCrypto.CertFromByteArray(fieldsClientHello[1]);
            peer_name = peer_cert.getSubjectDN().toString();
            try {
                if (!check_receny(peer_name)) {
                    LoggingFragment.mutexTvdAL.lock();
                    LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Rejected request from " + peer_name + " (too frequent requests)", Color.MAGENTA));
                    LoggingFragment.mutexTvdAL.unlock();
                    Log.d(debug_tag(),"There have been too many requests recently from " + peer_name);
                    safe_close_socket(s);
                    peerWasTooFrequent = true;
                    // safe_exit("There have been too many requests recently from " + peer_name, null, s);
                    return false;
                }
            }
            catch (Exception e){
                safe_exit("Could not check recency of requests from peer " + peer_name,e,s);
                return false;
            }
        }
        catch (Exception e){
            safe_exit("Could not parse the peer certificate although the crypto checks with it passed!",e,s);
            return false;
        }

        Log.d(debug_tag(),"SUCCESS: Client pseudonym requests are NOT too frequent!");
        // 2) HANDSHAKE STEP 2: SEND SERVER CREDENTIALS TO THE CLIENT
        // [HELLO]:5 | [CERTIFICATE LENGTH] | [CERTIFICATE BYTES]:~2K | [timestamp]: 8 | [signed timestamp]: 256
        try {
            byte[] helloFieldServerHello = "HELLO".getBytes();
            byte[] certificateFieldServerHello = my_cert.getEncoded();
            byte[] certificateFieldServerHelloLength = ("" + certificateFieldServerHello.length).toString().getBytes();
            CryptoTimestamp cryptoTimestamp = InterNodeCrypto.getSignedTimestampWithKey(my_key);
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
            byte [] ServerHello = baosServerHello.toByteArray();
            dos.write(ServerHello);
        }
        catch (Exception e){
            safe_exit("Could not send serving peer Hello",e,s);
            return false;
        }

        return true;

    }

    // This is the function that will work with the HashMap to check for very frequent querying nodes
    // @returns true if the peer requesting for something hasn't done so more that 5 times in the last minute
    public boolean check_receny(String pseudo_name){
        // return true;
        try {
            if (recentlyServedNodes.containsKey(pseudo_name)) {
                boolean not_frequent_request = recentlyServedNodes.get(pseudo_name).addNewRequest();
                return not_frequent_request;
            }
            ServiceDetails new_record = new ServiceDetails();
            recentlyServedNodes.put(pseudo_name, new_record);
            return true;
        }
        catch (Exception e){
            Log.d("HashMapIssue","Error: There is something wrong witht he hash map of recency checks!");
            e.printStackTrace();
            return true;
        }
    }

    public String debug_tag(){
        return "Serving Node Query Handle Thread";
    }

    public void safe_exit(String message, Exception e, Socket s){
        Log.d("Serving Node Query Handle Thread -> Peer " + peerIP,message);
        LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Failed to serve: " + peerIP , Color.RED ) );
        LoggingFragment.mutexTvdAL.unlock();
        if(e!=null) {
            e.printStackTrace();
        }
        safe_close_socket(s);
        return;
    }

    public void safe_close_socket(Socket s){
        try{
            s.close();
        }
        catch (Exception e){
            Log.d("Serving NOde Query Handle -> Peer " + peerIP,"ERROR: Could not close socket with the other peer");
            e.printStackTrace();
        }
    }

}
