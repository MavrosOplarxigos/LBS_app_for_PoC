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
    // [1]: the signature of the RAW query CONNCATENATED with the response with the signing server key (CA key)
    public static byte [][] ProxyQuery(byte [] APICallBytesClientQuery, X509Certificate my_cert, PrivateKey my_key) throws Exception {

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
        // [8_TIMESTAMP] | [SIGNATURE_TIMESTAMP_QUERY]
        try {

            byte[] PROXY_STRING = "PROXY".getBytes();
            byte[] SERVING_PEER_CERTIFICATE = my_cert.getEncoded();
            byte[] CERTIFICATE_LENGTH = TCPhelpers.intToByteArray(SERVING_PEER_CERTIFICATE.length);
            byte[] API_CALL_ENC_SSKEY = InterNodeCrypto.encryptWithPeerKey(APICallBytesClientQuery, InterNodeCrypto.CA_cert);
            byte[] API_CALL_ENC_LEN = TCPhelpers.intToByteArray(API_CALL_ENC_SSKEY.length);
            CryptoTimestamp ct = InterNodeCrypto.getSignedTimestampWithConcatenationWithKey(APICallBytesClientQuery, my_key);
            byte[] TIMESTAMP = ct.timestamp;
            byte[] SIGNATURE_TIMESTAMP_QUERY = ct.signed_timestamp_conncatenated_with_info;

            ByteArrayOutputStream baosServingPeerQueryFwd = new ByteArrayOutputStream();
            baosServingPeerQueryFwd.write(PROXY_STRING);
            baosServingPeerQueryFwd.write(CERTIFICATE_LENGTH);
            baosServingPeerQueryFwd.write(SERVING_PEER_CERTIFICATE);
            baosServingPeerQueryFwd.write(API_CALL_ENC_LEN);
            baosServingPeerQueryFwd.write(API_CALL_ENC_SSKEY);
            baosServingPeerQueryFwd.write(TIMESTAMP);
            baosServingPeerQueryFwd.write(SIGNATURE_TIMESTAMP_QUERY);

            byte [] ServingPeerQueryFwd = baosServingPeerQueryFwd.toByteArray();

            dos.write(ServingPeerQueryFwd);
        }
        catch (Exception e){
            Log.d("SERVING PEER QUERY FWD","Could not generate the fields for SERVING PEER QUERY FWD");
            throw e;
        }







        return null;


    }

}
