package com.example.lbs_app_for_poc;

import android.graphics.Color;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class SigningServerInterations {

    public static final int SIGNING_SERVER_TIMEOUT = 2000;

    // Using the function bellow when the peers are non existent or irresponsive
    public static byte [] DirectQuery(byte [] APICallBytesClientQuery, X509Certificate my_cert, PrivateKey my_key) throws Exception{

        Log.d("DIREC","Direct Query Function enters!");

        Socket s = new Socket();
        try{
            InetAddress ssIP = TCPServerControlClass.lbsEC.ENTITIES_MANAGER_IP;
            int ssPort = TCPServerControlClass.lbsEC.SIGNING_FWD_SERVER_PORT;
            s.connect(new InetSocketAddress(ssIP,ssPort),SIGNING_SERVER_TIMEOUT);
        }
        catch (Exception e){
            e.printStackTrace();
            throw new Exception("Failed to establish connection with signing server!");
        }

        DataInputStream dis = null;
        DataOutputStream dos = null;

        try{
            dis = new DataInputStream(s.getInputStream());
            dos = new DataOutputStream(s.getOutputStream());
        }
        catch (Exception e){
            e.printStackTrace();
            throw new Exception("Could not retrieve the data streams from the socket with signing server");
        }

        Log.d("DIREC","Proxy Query Socket and Data Streams initialized!");

        // QUERYING PEER QUERY FORWARD
        // ["DIREC"] | [4_CERTIFICATE_LENGTH] | [QUERYING PEER CERTIFICATE] | [4_API_CALL_ENC_LEN] | [API_CALL_ENC_SSKEY]
        // [8_TIMESTAMP] | [SIGNATURE_TQ_LEN] | [SIGNATURE_TIMESTAMP_QUERY]
        try {

            byte[] DIREC_STRING = "DIREC".getBytes();
            byte[] QUERYING_PEER_CERTIFICATE = my_cert.getEncoded();
            byte[] CERTIFICATE_LENGTH = TCPhelpers.intToByteArray(QUERYING_PEER_CERTIFICATE.length);
            byte[] API_CALL_ENC_SSKEY = InterNodeCrypto.encryptWithPeerKey(APICallBytesClientQuery, InterNodeCrypto.CA_cert);
            byte[] API_CALL_ENC_LEN = TCPhelpers.intToByteArray(API_CALL_ENC_SSKEY.length);
            CryptoTimestamp ct = InterNodeCrypto.getSignedTimestampWithConcatenationWithKey(APICallBytesClientQuery,my_key);
            byte[] TIMESTAMP = ct.timestamp;
            byte[] SIGNATURE_TIMESTAMP_QUERY = ct.signed_timestamp_conncatenated_with_info;
            byte[] SIGNATURE_TQ_LEN = TCPhelpers.intToByteArray(SIGNATURE_TIMESTAMP_QUERY.length);

            ByteArrayOutputStream baosServingPeerQueryFwd = new ByteArrayOutputStream();
            baosServingPeerQueryFwd.write(DIREC_STRING);
            baosServingPeerQueryFwd.write(CERTIFICATE_LENGTH);
            baosServingPeerQueryFwd.write(QUERYING_PEER_CERTIFICATE);
            baosServingPeerQueryFwd.write(API_CALL_ENC_LEN);
            baosServingPeerQueryFwd.write(API_CALL_ENC_SSKEY);
            baosServingPeerQueryFwd.write(TIMESTAMP);
            baosServingPeerQueryFwd.write(SIGNATURE_TQ_LEN);
            baosServingPeerQueryFwd.write(SIGNATURE_TIMESTAMP_QUERY);

            byte [] QueryingPeerQuery = baosServingPeerQueryFwd.toByteArray();

            dos.write(QueryingPeerQuery);
        }
        catch (Exception e){
            Log.d("QUERYING PEER DIRECT QUERY","Could not generate the fields for QUERYING PEER DIRECT QUERY");
            throw e;
        }

        // READY HOW MANY BYTES WE SHOULD EXPECT FROM THE REMOTE SERVER
        byte [] reply_size_bytes = TCPhelpers.buffRead(4,dis);
        int reply_size = TCPhelpers.byteArrayToIntLittleEndian(reply_size_bytes);

        /*LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("DIRECT request expected answer size from SS: " + reply_size, Color.MAGENTA ) );
        LoggingFragment.mutexTvdAL.unlock();*/

        // SIGNING SERVER ANSWER FORWARD
        // [ENC_ANSWER_LENGTH] | [ENC_ANSWER] | [SIGNATURE_SS_QA_LEN] | [SIGNATURE_SS_QA] | [DEC_ANSWER_LEN]

        // ByteArrayOutputStream baosSS_AFWD = null;
        byte [] SS_AFWD = null;
        try {
            SS_AFWD = TCPhelpers.buffRead(reply_size,dis);
            // baosSS_AFWD = TCPhelpers.receiveBuffedBytesNoLimit(dis);
        }
        catch (Exception e){
            Log.d("SIGNING SERVER ANSWER FORWARD","Could not receive the answer from the SS");
            throw e;
        }
        // byte [] SS_AFWD = baosSS_AFWD.toByteArray();

        /*LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("DIRECT: the size of the SS_AFWD: " + SS_AFWD.length, Color.GREEN ) );
        LoggingFragment.mutexTvdAL.unlock();*/

        byte [] encAnsLenByteArray = new byte[4];
        for(int i=0;i<4;i++) { // 4 bytes
            if (i >= SS_AFWD.length) {
                throw new Exception("The size of the received data is too small. Couldn't get the size fo the ENC_ANSWER");
            }
            encAnsLenByteArray[i] = SS_AFWD[i];
        }
        int ENC_ANSWER_LENGTH = TCPhelpers.byteArrayToIntLittleEndian(encAnsLenByteArray);

        byte [] encAnsByteArray = new byte[ENC_ANSWER_LENGTH];
        for(int i=4;i<4+ENC_ANSWER_LENGTH;i++) { // ENC_ANSWER_LENGTH bytes
            if (i >= SS_AFWD.length) {
                throw new Exception("Direct: The size of the received data is too small. Couldn't get ENC_ANSWER");
            }
            encAnsByteArray[i-4] = SS_AFWD[i];
        }

        byte [] signatureSSQAByteArray = new byte[4];
        for(int i=4+ENC_ANSWER_LENGTH;i<4+ENC_ANSWER_LENGTH+4;i++) { // 4 bytes
            if (i >= SS_AFWD.length) {
                throw new Exception("DIRECT: The size of the received data is too small. Couldn't get the size fo the SS_QA_BYTE_ARRAY");
            }
            signatureSSQAByteArray[i-(4+ENC_ANSWER_LENGTH)] = SS_AFWD[i];
        }
        int SIGNATURE_SS_QA_LEN = TCPhelpers.byteArrayToIntLittleEndian(signatureSSQAByteArray);

        byte [] SIGNATURE_SS_QA = new byte[SIGNATURE_SS_QA_LEN];
        for(int i=4+ENC_ANSWER_LENGTH+4;i<4+ENC_ANSWER_LENGTH+4+SIGNATURE_SS_QA_LEN;i++){
            if (i >= SS_AFWD.length) {
                throw new Exception("The size of the received data is too small. Couldn't get the SS_QA_BYTE_ARRAY");
            }
            SIGNATURE_SS_QA[i-(4+ENC_ANSWER_LENGTH+4)] = SS_AFWD[i];
        }

        byte [] DecAnsLenByteArray = new byte[4];
        for(int i=4+ENC_ANSWER_LENGTH+4+SIGNATURE_SS_QA_LEN;i<SS_AFWD.length;i++){
            if(i > SS_AFWD.length){
                throw new Exception("The size of the received data is too small. Couldn't get the DEC_ANS_LEN");
            }
            DecAnsLenByteArray[i-(4+ENC_ANSWER_LENGTH+4+SIGNATURE_SS_QA_LEN)] = SS_AFWD[i];
        }
        int DEC_ANS_LEN = TCPhelpers.byteArrayToIntLittleEndian(DecAnsLenByteArray);
        byte [] ANSWER = InterNodeCrypto.decryptWithKey(encAnsByteArray,my_key,DEC_ANS_LEN);

        /*LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Decrypted answer ss expected length: " + DEC_ANS_LEN, Color.MAGENTA ) );
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Decrypted answer from signing server: " + TCPhelpers.byteArrayToDecimalStringFirst10(ANSWER), Color.MAGENTA ) );
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Decrypted answer from signing server last 10 bytes: " + TCPhelpers.byteArrayToDecimalStringLast10(ANSWER), Color.MAGENTA ) );
        LoggingFragment.mutexTvdAL.unlock();*/

        // We have the QUERY = APICallBytesClientQuery
        // We have the ANSWER = ANSWER
        // We can now verify the signature of the signing server
        byte [] QAconcatenation = new byte[APICallBytesClientQuery.length + ANSWER.length];
        System.arraycopy(APICallBytesClientQuery, 0, QAconcatenation, 0, APICallBytesClientQuery.length);
        System.arraycopy(ANSWER, 0, QAconcatenation, APICallBytesClientQuery.length, ANSWER.length);

        String hashOfConcatenation = TCPhelpers.calculateSHA256Hash(QAconcatenation);

        /*LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("APICallBytesClientQuery: " + TCPhelpers.byteArrayToDecimalStringFirst10(APICallBytesClientQuery), Color.MAGENTA ) );
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("APICallBytesClientQuery last 10: " + TCPhelpers.byteArrayToDecimalStringLast10(APICallBytesClientQuery), Color.MAGENTA ) );
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Hash of concatenatino: " + hashOfConcatenation, Color.MAGENTA ) );
        LoggingFragment.mutexTvdAL.unlock();*/

        boolean SSsignatureValid = CryptoChecks.isSignedByCert(QAconcatenation, SIGNATURE_SS_QA, InterNodeCrypto.CA_cert);
        if(!SSsignatureValid){
            throw new Exception("The signature of the SS for the concatenation of the query and the answer is invalid");
        }

        return ANSWER;

    }

    // using the function below when we are a serving peer
    // [0]: the answer to the query encrypted with the querying peer key
    // [1]: the signature of the RAW query CONCATENATED with the response with the signing server key (CA key)
    public static byte [][] ProxyQuery(byte [] APICallBytesClientQuery, X509Certificate my_cert, PrivateKey my_key, X509Certificate peer_key) throws Exception {

        Log.d("PXQRY","Proxy Query Function enters!");

        Socket s = new Socket();
        try{
            InetAddress ssIP = TCPServerControlClass.lbsEC.ENTITIES_MANAGER_IP;
            int ssPort = TCPServerControlClass.lbsEC.SIGNING_FWD_SERVER_PORT;
            s.connect(new InetSocketAddress(ssIP,ssPort),SIGNING_SERVER_TIMEOUT);
        }
        catch (Exception e){
            e.printStackTrace();
            throw new Exception("Failed to establish connection with signing server!");
        }

        DataInputStream dis = null;
        DataOutputStream dos = null;

        try{
            dis = new DataInputStream(s.getInputStream());
            dos = new DataOutputStream(s.getOutputStream());
        }
        catch (Exception e){
            e.printStackTrace();
            throw new Exception("Could not retrieve the data streams from the socket with signing server");
        }

        Log.d("PXQRY","Proxy Query Socket and Data Streams initialized!");

        // SERVING PEER QUERY FORWARD
        // ["PROXY"] | [4_CERTIFICATE_LENGTH] | [SERVING PEER CERTIFICATE] | [4_API_CALL_ENC_LEN] | [API_CALL_ENC_SSKEY]
        // [8_TIMESTAMP] | [SIGNATURE_TQ_LEN] | [SIGNATURE_TIMESTAMP_QUERY]
        try {

            byte[] PROXY_STRING = "PROXY".getBytes();
            byte[] SERVING_PEER_CERTIFICATE = my_cert.getEncoded();
            byte[] CERTIFICATE_LENGTH = TCPhelpers.intToByteArray(SERVING_PEER_CERTIFICATE.length);
            byte[] API_CALL_ENC_SSKEY = InterNodeCrypto.encryptWithPeerKey(APICallBytesClientQuery, InterNodeCrypto.CA_cert);
            byte[] API_CALL_ENC_LEN = TCPhelpers.intToByteArray(API_CALL_ENC_SSKEY.length);
            CryptoTimestamp ct = InterNodeCrypto.getSignedTimestampWithConcatenationWithKey(APICallBytesClientQuery, my_key);
            byte[] TIMESTAMP = ct.timestamp;
            byte[] SIGNATURE_TIMESTAMP_QUERY = ct.signed_timestamp_conncatenated_with_info;
            byte[] SIGNATURE_TQ_LEN = TCPhelpers.intToByteArray(SIGNATURE_TIMESTAMP_QUERY.length);

            ByteArrayOutputStream baosServingPeerQueryFwd = new ByteArrayOutputStream();
            baosServingPeerQueryFwd.write(PROXY_STRING);
            baosServingPeerQueryFwd.write(CERTIFICATE_LENGTH);
            baosServingPeerQueryFwd.write(SERVING_PEER_CERTIFICATE);
            baosServingPeerQueryFwd.write(API_CALL_ENC_LEN);
            baosServingPeerQueryFwd.write(API_CALL_ENC_SSKEY);
            baosServingPeerQueryFwd.write(TIMESTAMP);
            baosServingPeerQueryFwd.write(SIGNATURE_TQ_LEN);
            baosServingPeerQueryFwd.write(SIGNATURE_TIMESTAMP_QUERY);

            byte [] ServingPeerQueryFwd = baosServingPeerQueryFwd.toByteArray();

            dos.write(ServingPeerQueryFwd);
        }
        catch (Exception e){
            Log.d("SERVING PEER QUERY FWD","Could not generate the fields for SERVING PEER QUERY FWD");
            throw e;
        }

        // READY HOW MANY BYTES WE SHOULD EXPECT FROM THE REMOTE SERVER
        byte [] reply_size_bytes = TCPhelpers.buffRead(4,dis);
        int reply_size = TCPhelpers.byteArrayToIntLittleEndian(reply_size_bytes);

        /*LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Expected answer size from SS: " + reply_size, Color.MAGENTA ) );
        LoggingFragment.mutexTvdAL.unlock();*/

        // SIGNING SERVER ANSWER FORWARD
        // [ENC_ANSWER_LENGTH] | [ENC_ANSWER] | [SIGNATURE_SS_QA_LEN] | [SIGNATURE_SS_QA] | [DEC_ANSWER_LEN]

        // ByteArrayOutputStream baosSS_AFWD = null;
        byte [] SS_AFWD = null;
        try {
            SS_AFWD = TCPhelpers.buffRead(reply_size,dis);
            // baosSS_AFWD = TCPhelpers.receiveBuffedBytesNoLimit(dis);
        }
        catch (Exception e){
            Log.d("SIGNING SERVER ANSWER FORWARD","Could not receive the answer from the SS");
            throw e;
        }
        // byte [] SS_AFWD = baosSS_AFWD.toByteArray();

        /*LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("The size of the SS_AFWD: " + SS_AFWD.length, Color.GREEN ) );
        LoggingFragment.mutexTvdAL.unlock();*/

        byte [] encAnsLenByteArray = new byte[4];
        for(int i=0;i<4;i++) { // 4 bytes
            if (i >= SS_AFWD.length) {
                throw new Exception("The size of the received data is too small. Couldn't get the size fo the ENC_ANSWER");
            }
            encAnsLenByteArray[i] = SS_AFWD[i];
        }
        int ENC_ANSWER_LENGTH = TCPhelpers.byteArrayToIntLittleEndian(encAnsLenByteArray);

        byte [] encAnsByteArray = new byte[ENC_ANSWER_LENGTH];
        for(int i=4;i<4+ENC_ANSWER_LENGTH;i++) { // ENC_ANSWER_LENGTH bytes
            if (i >= SS_AFWD.length) {
                throw new Exception("The size of the received data is too small. Couldn't get ENC_ANSWER");
            }
            encAnsByteArray[i-4] = SS_AFWD[i];
        }

        byte [] signatureSSQAByteArray = new byte[4];
        for(int i=4+ENC_ANSWER_LENGTH;i<4+ENC_ANSWER_LENGTH+4;i++) { // 4 bytes
            if (i >= SS_AFWD.length) {
                throw new Exception("The size of the received data is too small. Couldn't get the size fo the SS_QA_BYTE_ARRAY");
            }
            signatureSSQAByteArray[i-(4+ENC_ANSWER_LENGTH)] = SS_AFWD[i];
        }
        int SIGNATURE_SS_QA_LEN = TCPhelpers.byteArrayToIntLittleEndian(signatureSSQAByteArray);

        byte [] SIGNATURE_SS_QA = new byte[SIGNATURE_SS_QA_LEN];
        for(int i=4+ENC_ANSWER_LENGTH+4;i<4+ENC_ANSWER_LENGTH+4+SIGNATURE_SS_QA_LEN;i++){
            if (i >= SS_AFWD.length) {
                throw new Exception("The size of the received data is too small. Couldn't get the SS_QA_BYTE_ARRAY");
            }
            SIGNATURE_SS_QA[i-(4+ENC_ANSWER_LENGTH+4)] = SS_AFWD[i];
        }

        byte [] DecAnsLenByteArray = new byte[4];
        for(int i=4+ENC_ANSWER_LENGTH+4+SIGNATURE_SS_QA_LEN;i<SS_AFWD.length;i++){
            if(i > SS_AFWD.length){
                throw new Exception("The size of the received data is too small. Couldn't get the DEC_ANS_LEN");
            }
            DecAnsLenByteArray[i-(4+ENC_ANSWER_LENGTH+4+SIGNATURE_SS_QA_LEN)] = SS_AFWD[i];
        }
        int DEC_ANS_LEN = TCPhelpers.byteArrayToIntLittleEndian(DecAnsLenByteArray);
        byte [] ANSWER = InterNodeCrypto.decryptWithKey(encAnsByteArray,my_key,DEC_ANS_LEN);

        /*LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Decrypted answer ss expected length: " + DEC_ANS_LEN, Color.MAGENTA ) );
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Decrypted answer from signing server: " + TCPhelpers.byteArrayToDecimalStringFirst10(ANSWER), Color.MAGENTA ) );
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Decrypted answer from signing server last 10 bytes: " + TCPhelpers.byteArrayToDecimalStringLast10(ANSWER), Color.MAGENTA ) );
        LoggingFragment.mutexTvdAL.unlock();*/

        // We have the QUERY = APICallBytesClientQuery
        // We have the ANSWER = ANSWER
        // We can now verify the signature of the signing server
        byte [] QAconcatenation = new byte[APICallBytesClientQuery.length + ANSWER.length];
        System.arraycopy(APICallBytesClientQuery, 0, QAconcatenation, 0, APICallBytesClientQuery.length);
        System.arraycopy(ANSWER, 0, QAconcatenation, APICallBytesClientQuery.length, ANSWER.length);

        // String hashOfConcatenation = TCPhelpers.calculateSHA256Hash(QAconcatenation);

        /*LoggingFragment.mutexTvdAL.lock();
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("APICallBytesClientQuery: " + TCPhelpers.byteArrayToDecimalStringFirst10(APICallBytesClientQuery), Color.MAGENTA ) );
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("APICallBytesClientQuery last 10: " + TCPhelpers.byteArrayToDecimalStringLast10(APICallBytesClientQuery), Color.MAGENTA ) );
        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Hash of concatenatino: " + hashOfConcatenation, Color.MAGENTA ) );
        LoggingFragment.mutexTvdAL.unlock();*/

        boolean SSsignatureValid = CryptoChecks.isSignedByCert(QAconcatenation, SIGNATURE_SS_QA, InterNodeCrypto.CA_cert);
        if(!SSsignatureValid){
            throw new Exception("The signature of the SS for the concatenation of the query and the answer is invalid");
        }

        // OK so now we have all the fields required to set the response fields
        byte [][] response_fields = new byte[3][];

        // [0]: the answer to the query encrypted with the querying peer key
        // [1]: the signature of the RAW query CONCATENATED with the response with the signing server key (CA key)
        // [2]: the length of the decrypted answer

        byte [] ENC_ANSWER_FOR_PEER = null;
        try {
            ENC_ANSWER_FOR_PEER = InterNodeCrypto.encryptWithPeerKey(ANSWER,peer_key);
        }
        catch (Exception e){
            Log.d("SERVING PEER ANSWER FORWARD","Could not sign the ANSWER with the peer public key!");
            throw e;
        }

        response_fields [0] = ENC_ANSWER_FOR_PEER;
        response_fields [1] = SIGNATURE_SS_QA;
        response_fields [2] = DecAnsLenByteArray;

        return response_fields;

    }

}
