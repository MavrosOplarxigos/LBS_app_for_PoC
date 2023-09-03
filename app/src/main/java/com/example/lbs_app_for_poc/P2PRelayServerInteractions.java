package com.example.lbs_app_for_poc;

import android.graphics.Color;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class P2PRelayServerInteractions {

    public static AvailabilityThread at;

    public static class AvailabilityThread extends Thread{

        LBSEntitiesConnectivity lbsEC;
        public static final int AVAILABILITY_DISCLOSURE_INTERVAL_MSEC = 5 * 1000; // 30000;
        public static final int AVAILABILITY_SOCKET_TIMEOUT = 1000;

        public AvailabilityThread(LBSEntitiesConnectivity lbsEC){
            this.lbsEC = lbsEC;
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
                        // reason: send our certificate to the remote server and double safety against timing attacks
                        client_hello(AvailabilitySocket);
                        Log.d("Availability Thread: ", "Sent Client Hello");
                        // now we can send the availability
                        send_availability(AvailabilitySocket);
                        Log.d("Availability Thread: ", "Sent Client Availability");
                        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Disclosed service to LBS manager.", Color.BLUE));
                        Thread.sleep(AVAILABILITY_DISCLOSURE_INTERVAL_MSEC); // Wait for 30 seconds
                        AvailabilitySocket.close();
                        AvailabilitySocket = new Socket();
                        AvailabilitySocket.connect(new InetSocketAddress(lbsEC.ENTITIES_MANAGER_IP, lbsEC.P2P_RELAY_AVAILABILITY_PORT), AVAILABILITY_SOCKET_TIMEOUT);
                    }
                } catch (Exception e) {
                    Log.d("Availability Thread: ", "Socket could NOT CONNECT!");
                    // maybe for some reason the server is flooded so we will wait some time before reconnecting to it
                    try {
                        Thread.sleep(AVAILABILITY_DISCLOSURE_INTERVAL_MSEC); // Wait for 30 seconds
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

    public static void send_availability(Socket socket) throws IOException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException, SignatureException, NoSuchProviderException {

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
        byte [] myServingPort = TCPhelpers.intToByteArray(TCPServerThread.MyServingPort); // 4 bytes

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
        return;

    }


}
