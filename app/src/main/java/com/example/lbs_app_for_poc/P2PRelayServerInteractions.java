package com.example.lbs_app_for_poc;

import android.graphics.Color;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class P2PRelayServerInteractions {

    public static AvailabilityThread aThread;
    public static PeerDiscoveryThread qThread;

    public static class PeerDiscoveryThread extends Thread{

        LBSEntitiesConnectivity lbsEntitiesConnectivity;
        public static final int QUERY_INTERVAL_MSEC = 20 * 1000;
        public static final int QUERY_SOCKET_TIMEOUT = 1000;
        public boolean explicit_search_request; // this is not static so that the previous instances of the thread kill themselves explicitly

        public PeerDiscoveryThread(LBSEntitiesConnectivity lbsEC){
            this.lbsEntitiesConnectivity = lbsEC;
            this.explicit_search_request = false;
        }

        @Override
        public void run() {
            while(true){
                try {
                    if(explicit_search_request){
                        Log.d("Peer Discovery Thread (Old): ", "Successful stop by searching node fragment!");
                        return;
                    }
                    Log.d("Peer Discovery Thread: ", "Thread initiated");
                    Socket PeerDiscoverySocket = new Socket();
                    Log.d("Peer Discovery Thread: ", "Socket constructed");
                    PeerDiscoverySocket.connect(new InetSocketAddress(lbsEntitiesConnectivity.ENTITIES_MANAGER_IP, lbsEntitiesConnectivity.P2P_RELAY_QUERY_PORT), QUERY_SOCKET_TIMEOUT);
                    Log.d("Peer Discovery Thread: ", "Socket constructed");
                    while(true){
                        if(explicit_search_request){
                            Log.d("Peer Discovery Thread (Old): ", "Successful stop by searching node fragment!");
                            return;
                        }
                        client_hello(PeerDiscoverySocket);
                        Log.d("Peer Discovery Thread","Sent Client Hello");

                        // now we can receive the peer list [ [num of records] [ IP_1 , port_1 ] .. [ IP_1 , port_1 ] ]
                        ArrayList<SearchingNodeFragment.ServingPeer> lista = null;
                        lista = new ArrayList<SearchingNodeFragment.ServingPeer>(peer_list(PeerDiscoverySocket));

                        // mutext for writing the newly received list of peers
                        SearchingNodeFragment.mutextServingPeerArrayList.lock();

                        SearchingNodeFragment.ServingPeerArrayList = lista;

                        if(lista != null) {
                            Log.d("Peer Discovery Thread: ", "Received & saved NEW Peer List: ");
                            LoggingFragment.mutexTvdAL.lock();
                            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("NEW PEER LIST: ",Color.CYAN));
                            for (int i = 0; i < SearchingNodeFragment.ServingPeerArrayList.size(); i++) {
                                Log.d("Peer Discovery Thread: ", "Peer #" + i + " @ " + SearchingNodeFragment.ServingPeerArrayList.get(i).PeerIP + ":" + SearchingNodeFragment.ServingPeerArrayList.get(i).PeerPort);
                                LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("peer record added @ " +
                                        SearchingNodeFragment.ServingPeerArrayList.get(i).PeerIP + ":" +
                                        SearchingNodeFragment.ServingPeerArrayList.get(i).PeerPort,Color.CYAN));
                            }
                            LoggingFragment.mutexTvdAL.unlock();
                        }
                        else{
                            Log.d("Peer Discovery Thread: ", "The received peer list is empty and thus now we have no peers!");
                            LoggingFragment.mutexTvdAL.lock();
                            LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("New peer list EMPTY", Color.RED));
                            LoggingFragment.mutexTvdAL.unlock();
                        }
                        SearchingNodeFragment.mutextServingPeerArrayList.unlock();

                        Thread.sleep(QUERY_INTERVAL_MSEC);
                        PeerDiscoverySocket.close();
                        PeerDiscoverySocket = new Socket();
                        PeerDiscoverySocket.connect(new InetSocketAddress(lbsEntitiesConnectivity.ENTITIES_MANAGER_IP, lbsEntitiesConnectivity.P2P_RELAY_QUERY_PORT), QUERY_SOCKET_TIMEOUT);
                    }
                }
                catch (Exception e){
                    Log.d("Peer discovery: ","Could not retrieve new peers!");
                    // maybe for some reason the server is flooded so we will wait some time before reconnecting to it
                    try{
                        Thread.sleep(QUERY_INTERVAL_MSEC);
                    }
                    catch (InterruptedException ex){
                        ex.printStackTrace();
                    }
                    e.printStackTrace();
                }
            }
        }

        public static ArrayList<SearchingNodeFragment.ServingPeer> peer_list(Socket socket) throws IOException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

            DataInputStream dis = new DataInputStream(socket.getInputStream());

            // read status code
            byte [] StatusCodeByteArray = buffRead(6,dis);
            String StatusCode = new String(StatusCodeByteArray, StandardCharsets.UTF_8);

            if(StatusCode == "TOOFRQ"){
                Log.d("Peer discovery: ","We have asked for new peers very frequently. That is below QUERY_MIN_INTERVAL_TOLERANCE_SECONDS");
                return null;
            }

            if(StatusCode == "EINVLD"){
                Log.d("Peer discovery: ","We have asked for new peers EARLY and the P2P relay server thinks that we should still have received service!");
                return null;
            }

            if(StatusCode == "EMPTYR"){
                Log.d("Peer discovery: ","The remote server thinks that our query is legit but has no records to send back to us");
                return null;
            }

            if(StatusCode == "OKRECV") {
                Log.d("Peer discovery: ", "SUCCESS: The remote server has records to send to us!");
                // receive the records

                byte [] numOfRecordsByteArray = buffRead(4,dis);
                int numOfRecords = byteArrayToInt(numOfRecordsByteArray);

                Log.d("Peer discovery: ", "We are expecting " + numOfRecords + " records from remote server!");

                byte [] sizeOfEncRecordsByteArray = buffRead(4,dis);
                int sizeOfEncRecords = byteArrayToInt(sizeOfEncRecordsByteArray);

                Log.d("Peer discovery: ", "The size of the encrypted records byte array is " + sizeOfEncRecords);

                byte [] ENC_records_byte_array = buffRead(sizeOfEncRecords,dis);
                byte [] recordsByteArray = InterNodeCrypto.decryptWithOwnKey(ENC_records_byte_array);

                if(numOfRecords * 4 * 2 != recordsByteArray.length){
                    Log.d("Peer discovery: ", "Error: since numOfRecords = " + numOfRecords + " the expected size of the records Byte" +
                            "Array is " + (4*2*numOfRecords) + " but we get " + recordsByteArray.length);
                    return null;
                }

                ArrayList<SearchingNodeFragment.ServingPeer> ANS = new ArrayList<SearchingNodeFragment.ServingPeer>();

                for(int i=0;i<numOfRecords;i++){

                    int ip_index = i*2; // the 4-byte block index
                    int port_index = i*2 + 1;

                    byte [] ip_byte_array = new byte[4];
                    for(int j=(ip_index*4);(j<ip_index*4+4);j++){
                        ip_byte_array[j-(ip_index*4)] = recordsByteArray[j];
                    }
                    byte [] port_byte_array = new byte [4];
                    for(int j=(port_index*4);(j<port_index*4+4);j++){
                        port_byte_array[j-port_index*4] = recordsByteArray[j];
                    }

                    int IP_value = byteArrayToInt(ip_byte_array);
                    int Port_value = byteArrayToInt(port_byte_array);
                    InetAddress IP_address = InetAddress.getByAddress(ip_byte_array);

                    Log.d("Peer disovery: ","Received peer " + IP_address.getHostAddress() + ":" + Port_value);

                    SearchingNodeFragment.ServingPeer servingPeer = new SearchingNodeFragment.ServingPeer(IP_address,Port_value);
                    ANS.add(servingPeer);

                }

                Log.d("Peer discovery: ","The list of peers has parsed successfully");
                return ANS;

            }

            Log.d("Peer discovery: ","There received status code from the remote server makes no sense!");
            return null;
        }

        public static int byteArrayToInt(byte[] byteArray) {
            ByteBuffer buffer = ByteBuffer.wrap(byteArray);
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            return buffer.getInt();
        }

        public static byte [] buffRead(int numOfBytes, DataInputStream dis) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int totalBytesRead = 0;
            while(true){
                int BytesLeft2Read = numOfBytes - totalBytesRead;
                int bufferSize = Math.min(100,BytesLeft2Read); // We read at most 100 bytes every time
                if (bufferSize != 100) {
                    Log.d("BuffRead", "Size now is " + bufferSize + " and BytesLeft2Read = " + BytesLeft2Read + " out of " + numOfBytes );
                }
                byte [] buffer = new byte[bufferSize];
                int tempBytesRead = dis.read(buffer);
                if(tempBytesRead!=bufferSize) {
                    if( tempBytesRead < bufferSize ){
                        // If they are less maybe we are still to receive them
                        Log.d("BuffRed","WARNING: We read less than what we expected!");
                    }
                    else {
                        throw new RuntimeException();
                    }
                }
                totalBytesRead += tempBytesRead;
                baos.write(buffer,0,tempBytesRead);
                if(totalBytesRead == numOfBytes){
                    break;
                }
            }
            byte [] readByteArray = baos.toByteArray();
            return readByteArray;
        }

    }

    public static class AvailabilityThread extends Thread{

        LBSEntitiesConnectivity lbsEC;
        public static final int AVAILABILITY_DISCLOSURE_INTERVAL_MSEC = 20 * 1000;
        public static final int AVAILABILITY_SOCKET_TIMEOUT = 1000;
        public String last_disclosure;

        public AvailabilityThread(LBSEntitiesConnectivity lbsEC){
            this.lbsEC = lbsEC;
            last_disclosure = ""; // we have no last disclosure initially
        }

        public void run(){
            while(true) {
                try {
                    Log.d("Availability Thread: ", "Thread initiated");
                    Socket AvailabilitySocket = new Socket();
                    Log.d("Availability Thread: ", "Socket constructed");
                    AvailabilitySocket.connect(new InetSocketAddress(lbsEC.ENTITIES_MANAGER_IP, lbsEC.P2P_RELAY_AVAILABILITY_PORT), AVAILABILITY_SOCKET_TIMEOUT);
                    Log.d("Availability Thread: ", "Socket connected!");
                    while (true) {
                        client_hello(AvailabilitySocket);
                        Log.d("Availability Thread: ", "Sent Client Hello");
                        // now we can send the availability
                        String disclosure = send_availability(AvailabilitySocket);

                        if(last_disclosure.length()!=0){
                            if(!last_disclosure.equals(disclosure)){
                                // change the certificate used for serving when our IP changes
                                if(ServingNodeQueryHandleThread.my_PSEUDO_CREDS_TO_COPY_lock!=null){
                                    ServingNodeQueryHandleThread.my_PSEUDO_CREDS_TO_COPY_lock.lock();

                                    int credsNum = InterNodeCrypto.pseudonymous_privates.size();
                                    Random random = new Random();
                                    int rIndex = random.nextInt(credsNum);

                                    ServingNodeQueryHandleThread.my_key_to_copy = InterNodeCrypto.pseudonymous_privates.get(rIndex);
                                    ServingNodeQueryHandleThread.my_cert_to_copy = InterNodeCrypto.pseudonymous_certificates.get(rIndex);

                                    ServingNodeQueryHandleThread.my_PSEUDO_CREDS_TO_COPY_lock.unlock();
                                }
                            }
                        }
                        last_disclosure = disclosure;

                        Log.d("Availability Thread: ", "Sent Client Availability");

                        LoggingFragment.mutexTvdAL.lock();
                        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("AVAILABILITY DISCLOSURE " + disclosure, Color.BLUE));
                        LoggingFragment.mutexTvdAL.unlock();

                        Thread.sleep(AVAILABILITY_DISCLOSURE_INTERVAL_MSEC); // Wait for 30 seconds
                        AvailabilitySocket.close();
                        AvailabilitySocket = new Socket();
                        AvailabilitySocket.connect(new InetSocketAddress(lbsEC.ENTITIES_MANAGER_IP, lbsEC.P2P_RELAY_AVAILABILITY_PORT), AVAILABILITY_SOCKET_TIMEOUT);
                    }
                } catch (Exception e) {
                    Log.d("Availability Thread: ", "Socket could NOT CONNECT!");
                    // maybe for some reason the server is flooded so we will wait some time before reconnecting to it
                    try {
                        Thread.sleep(AVAILABILITY_DISCLOSURE_INTERVAL_MSEC);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                    e.printStackTrace();
                }
            }
        }

    }

    public static void client_hello(Socket socket) throws CertificateEncodingException, IOException, NoSuchAlgorithmException, SignatureException, NoSuchProviderException, InvalidKeyException {

        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

        byte [] helloField = "HELLO".getBytes();
        byte [] certificateField = InterNodeCrypto.my_cert.getEncoded();
        byte [] certificateLengthField = TCPhelpers.intToByteArray(certificateField.length);
        CryptoTimestamp cryptoTimestamp = InterNodeCrypto.getSignedTimestamp();
        byte [] signedTimestampLength = TCPhelpers.intToByteArray(cryptoTimestamp.signed_timestamp.length);

        ByteArrayOutputStream baosCH = new ByteArrayOutputStream();
        baosCH.write(helloField);
        baosCH.write(certificateLengthField);
        baosCH.write(certificateField);
        baosCH.write(cryptoTimestamp.timestamp);
        baosCH.write(signedTimestampLength);
        baosCH.write(cryptoTimestamp.signed_timestamp);

        byte [] ClientHello = baosCH.toByteArray();
        dos.write(ClientHello);

    }

    public static String send_availability(Socket socket) throws IOException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException, SignatureException, NoSuchProviderException {

        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

        InetAddress myIpAddress = null;
        NetworkInterface wlan0 = NetworkInterface.getByName("wlan0");
        List<InetAddress> all_addresses = Collections.list(wlan0.getInetAddresses());
        for(InetAddress addr : all_addresses){
            if(addr.toString().contains(":")){
                // then it should be a MAC address
                // TODO: find a better way to check if an address is a MAC-ADDRESS
                continue;
            }
            else {
                Log.d("Availability Disclosure", "IP address found for wlan0 " + addr.toString());
                myIpAddress = addr;
                break;
            }
        }

        byte [] myIPAddressByteArray = myIpAddress.getAddress(); // 4 bytes
        byte [] myServingPort = TCPhelpers.intToByteArray(TCPServerControlClass.MyServingPort); // 4 bytes

        ByteArrayOutputStream baosDisclosure = new ByteArrayOutputStream();
        baosDisclosure.write(myIPAddressByteArray);
        baosDisclosure.write(myServingPort);

        byte [] Disclosure = baosDisclosure.toByteArray();
        // Encrypt the disclosure
        // We are using the CA certificate since we consider the CA and LBS entities to be the same actors
        byte [] EncDisclosure = InterNodeCrypto.encryptWithPeerKey(Disclosure,InterNodeCrypto.CA_cert);
        byte [] EncDisclosureLength = TCPhelpers.intToByteArray(EncDisclosure.length);
        CryptoTimestamp cryptoTimestamp = InterNodeCrypto.getSignedTimestamp();
        byte [] SignedTimestampLength = TCPhelpers.intToByteArray(cryptoTimestamp.signed_timestamp.length);

        ByteArrayOutputStream baosClientAvailability = new ByteArrayOutputStream();
        // [ EDISCLOSURE LEN ] | [    EDISCLOSURE  ] | [ TIMESTAMP ] | [ SIGNED TIMESTAMP LEN ] | [      SIGNED TIMESTAMP       ]
        // [       4         ] | [ EDISCLOSURE LEN ] | [     8     ] | [          4           ] | [   SIGNED TIMESTAMP LENGTH   ]

        baosClientAvailability.write(EncDisclosureLength);
        baosClientAvailability.write(EncDisclosure);
        baosClientAvailability.write(cryptoTimestamp.timestamp);
        baosClientAvailability.write(SignedTimestampLength);
        baosClientAvailability.write(cryptoTimestamp.signed_timestamp);

        byte [] ClientAvailability = baosClientAvailability.toByteArray();

        dos.write(ClientAvailability);
        return  myIpAddress.getHostAddress() + ":" + TCPServerControlClass.MyServingPort;

    }

}
