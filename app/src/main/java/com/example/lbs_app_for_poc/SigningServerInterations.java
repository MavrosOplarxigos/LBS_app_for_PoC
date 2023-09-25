package com.example.lbs_app_for_poc;

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
    public static byte [] DirectQuery(byte [] APICallBytesClientQuery){
        return null;
    }


    // using the function below when we are a serving peer
    // [0]: the answer to the query encrypted with the querying peer key
    // [1]: the signature of the RAW query CONCATENATED with the response with the signing server key (CA key)
    public static byte [][] ProxyQuery(byte [] APICallBytesClientQuery, X509Certificate my_cert, PrivateKey my_key, X509Certificate peer_key) throws Exception {

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

        // SIGNING SERVER ANSWER FORWARD
        // [ENC_ANSWER_LENGTH] | [ENC_ANSWER] | [SIGNATURE_SS_QA_LEN] | [SIGNATURE_SS_QA]

        ByteArrayOutputStream baosSS_AFWD = null;
        try {
            baosSS_AFWD = TCPhelpers.receiveBuffedBytesNoLimit(dis);
        }
        catch (Exception e){
            Log.d("SIGNING SERVER ANSWER FORWARD","Could not receive the answer from the SS");
            throw e;
        }
        byte [] SS_AFWD = baosSS_AFWD.toByteArray();

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
            encAnsByteArray[i] = SS_AFWD[i];
        }
        byte [] ANSWER = InterNodeCrypto.decryptWithKey(encAnsByteArray,my_key);

        byte [] signatureSSQAByteArray = new byte[4];
        for(int i=4+ENC_ANSWER_LENGTH;i<4+ENC_ANSWER_LENGTH+4;i++) { // 4 bytes
            if (i >= SS_AFWD.length) {
                throw new Exception("The size of the received data is too small. Couldn't get the size fo the SS_QA_BYTE_ARRAY");
            }
            signatureSSQAByteArray[i] = SS_AFWD[i];
        }
        int SIGNATURE_SS_QA_LEN = TCPhelpers.byteArrayToIntLittleEndian(signatureSSQAByteArray);

        byte [] SIGNATURE_SS_QA = new byte[SIGNATURE_SS_QA_LEN];
        for(int i=4+ENC_ANSWER_LENGTH+4;i<4+ENC_ANSWER_LENGTH+4+SIGNATURE_SS_QA_LEN;i++){
            if (i >= SS_AFWD.length) {
                throw new Exception("The size of the received data is too small. Couldn't get the SS_QA_BYTE_ARRAY");
            }
            SIGNATURE_SS_QA[i] = SS_AFWD[i];
        }












        return null;


    }

}
