package com.example.lbs_app_for_poc;

import android.graphics.Color;
import android.net.InetAddresses;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class ConnectivityConfiguration extends Fragment {

    // TCP connection configuration
    // Variables should be static
    public static InetAddress my_ip_address = null;
    public static InetAddress my_peer_ip_address = null;
    // public static int my_port = -1;
    public static int peer_port = -1;
    public static Socket my_client_socket = null;
    public static TextView connectivity_status_TV;
    public static DataInputStream inputStream = null;
    public static DataOutputStream outputStream = null;

    public ConnectivityConfiguration() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*if (getArguments() != null) {
            caller_fragment = savedInstanceState.getString("caller_fragment");
            Log.d("NET CONFIG FRAG","The caller fragment is " + caller_fragment);
        }*/
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_connectivity_configuration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Retrieving my own IP address on wlan0
        try {

            my_ip_address = null;
            NetworkInterface wlan0 = NetworkInterface.getByName("wlan0");
            List<InetAddress> all_addresses = Collections.list(wlan0.getInetAddresses());
            for(InetAddress addr : all_addresses){
                if(addr.toString().contains(":")){
                    // then it should be a MAC address
                    // TODO: find a better way to check if an address is a MAC-ADDRESS
                    continue;
                }
                else {
                    Log.d("NET CONFIG", "IP address found for wlan0 " + addr.toString());
                    my_ip_address = addr;
                }
            }
            if(my_ip_address == null){
                Log.d("NET CONFIG","Error couldn't retrieved wlan0 ip address");
            }

            TextView my_ip_addr_value_TV = view.findViewById(R.id.myIPaddress_value);
            my_ip_addr_value_TV.setText( (my_ip_address.toString().split("/") )[1] );

        }
        catch (Exception e){
            Log.d("NET CONFIG","Can't get my own IP address on the wireless interface");
        }

        // Displaying peer's IP address (configure the initial one)

        // if it's null we fill it in
        if(my_peer_ip_address == null){
            try {
                // TODO: if we are going to use a coordinator then here we must request from him a new peer
                my_peer_ip_address = InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException e) {
                Log.d("NET CONFIG","Peer address not found from given name!");
                throw new RuntimeException(e);
            }
        }
        EditText my_peer_ip_address_TV = (EditText) view.findViewById(R.id.peerIPaddress_value);
        my_peer_ip_address_TV.setText(my_peer_ip_address.toString().split("/")[1]);
        // if the edit text is changed then the changes will take place only when the save button is pressed

        // Displaying my own port
        /*if(my_port == -1){
            my_port = 55777;
        }
        EditText my_port_TV = (EditText) view.findViewById(R.id.myTCPport_value);
        my_port_TV.setText(Integer.toString(my_port));*/

        // Displaying peer's port
        if(peer_port == -1){
            // TODO: if we are going to use a coordinator then here we must request from him a new peer
            peer_port = 55777;
        }
        EditText peer_port_TV = (EditText) view.findViewById(R.id.peerTCPport_value);
        peer_port_TV.setText(Integer.toString(peer_port));

        // Check if connectivity is established
        connectivity_status_TV = (TextView) view.findViewById(R.id.connectivity_status);
        if( (my_client_socket == null) || (!my_client_socket.isConnected()) ){
            // This will change if the user clicks the save & connect button
            connectivity_status_TV.setText("No connection!");
            connectivity_status_TV.setBackgroundColor(Color.RED);
        }
        else{
            // a connection is already established
            connectivity_status_TV.setText("Connected");
            connectivity_status_TV.setBackgroundColor(Color.GREEN);
        }

        // Adding listener to save button to update the values
        Button save_button = (Button) view.findViewById(R.id.netconfig_save);
        // retry establishing a new TCP connection when it is clicked
        save_button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        connectivity_status_TV.setText("Connecting...");
                        connectivity_status_TV.setBackgroundColor(Color.CYAN);

                        // update the values

                        // my own ip address is already retrieved
                        // peer ip address (so we change it only after the save button is pressed)
                        String new_peer_ipaddress = String.valueOf( (my_peer_ip_address_TV.getText()) );
                        try {
                            my_peer_ip_address = InetAddress.getByAddress(str_to_ip_array(new_peer_ipaddress));
                        } catch (UnknownHostException e) {
                            Toast.makeText(null, "Peer ip address invalid", Toast.LENGTH_SHORT).show();
                            Log.d("NET CONFIG","Couldn't get the IP address from user's input");
                            throw new RuntimeException(e);
                        }
                        // my tcp port
                        // same with the ports we change them only when the save button is pressed


                        // Log.d("NET CONFIG",""+my_port_TV.getText());
                        // Log.d("VALUEOF",""+String.valueOf(my_port_TV.getText()));
                        // Log.d("VALUEOF",""+Integer.valueOf( String.valueOf(my_port_TV.getText()) ));

                        // my_port = Integer.valueOf( String.valueOf(my_port_TV.getText()) );
                        // peer port
                        peer_port = Integer.valueOf(String.valueOf(peer_port_TV.getText()));

                        // ok now we need to restart connectivity
                        if(my_client_socket != null) {
                            try {
                                my_client_socket.close();
                            } catch (IOException e) {
                                Log.d("NET CONFIG", "Couldn't close client socket!");
                                throw new RuntimeException(e);
                            }
                        }

                        // Here we consider the server is already ON
                        // TODO: Fix code so that we don't check for delimeter after the for loop exits when reading a field unless necessary
                        // TODO: Add condition in for loops that we don't overstep the byte array we are reading from
                        try {

                            // COMMUNICATION PROTOCOL START
                            my_client_socket = new Socket(my_peer_ip_address,peer_port);

                            inputStream = new DataInputStream(my_client_socket.getInputStream());
                            outputStream = new DataOutputStream(my_client_socket.getOutputStream());

                            Log.d("TCP CLIENT","Input/Output strreams on Client socket are ready!");

                            // 1) HANDSHAKE STEP 1: SEND CLIENT CREDS
                            // OK so now the client must sent his credentials to the server
                            // Server expects the following format
                            // [HELLO]:5 | [CERTIFICATE BYTES]:~2K | [NONCE]:20 | [SIGNED_NONCE]: 20

                            // HELLO
                            byte[] helloField = "HELLO".getBytes();
                            // CERTIFICATE
                            byte[] certificateFieldClientHello = InterNodeCrypto.my_cert.getEncoded();
                            byte[] certificateFieldClientHelloLength = ("" + certificateFieldClientHello.length).toString().getBytes();
                            SecureRandom secureRandom;
                            byte [] nonceFieldClientHello;
                            byte [] signedNonceFieldClientHello;
                            // NONCE
                            secureRandom = new SecureRandom();
                            nonceFieldClientHello = new byte[20];
                            secureRandom.nextBytes(nonceFieldClientHello);
                            // SINGED NONCE
                            signedNonceFieldClientHello = InterNodeCrypto.signPrivateKeyByteArray(nonceFieldClientHello);

                            ByteArrayOutputStream baosClientHello = new ByteArrayOutputStream();
                            baosClientHello.write(helloField);
                            baosClientHello.write((byte)(TCPServerThread.transmission_del));
                            baosClientHello.write(certificateFieldClientHelloLength);
                            baosClientHello.write((byte)(TCPServerThread.transmission_del));
                            // the certificate filed might contain | already so we just save the size of it and that's it
                            baosClientHello.write(certificateFieldClientHello);
                            baosClientHello.write((byte)(TCPServerThread.transmission_del));
                            // the nonce field is always 20 bytes
                            baosClientHello.write(nonceFieldClientHello);
                            baosClientHello.write((byte)(TCPServerThread.transmission_del));
                            baosClientHello.write(signedNonceFieldClientHello);
                            // Here maybe add AES key exchange as well?

                            byte [] ClientHello = baosClientHello.toByteArray();
                            String ClientHelloDebugString = new String(ClientHello,StandardCharsets.UTF_8);
                            Log.d("ClientHelloDebugString",ClientHelloDebugString);
                            outputStream.write(ClientHello);

                            Log.d("TCP CLIENT","Sent Client Hello!");

                            // 2) HANDSHAKE STEP 2: RECEIVE SERVER CREDENTIALS
                            // [HELLO]:5 | [CERTIFICATE BYTES]:~2K | [NONCE]:20 | [SIGNED_NONCE]: 20

                            ByteArrayOutputStream baosServerHello = new ByteArrayOutputStream();
                            byte[] buffer = new byte[1000];
                            int bytesRead;
                            int total_bytes = 0;
                            while( (bytesRead = inputStream.read(buffer)) != -1 ){
                                baosServerHello.write(buffer,0,bytesRead);
                                total_bytes += bytesRead;
                                if(bytesRead < buffer.length){
                                    break; // The buffer is not filled up that means we have reached the EOF
                                }
                                if(total_bytes > TCPServerThread.max_transmission_cutoff){
                                    break;
                                }
                            }
                            Log.d("TCP CLIENT","Server Hello Received");
                            byte[] bytesServerHello = baosServerHello.toByteArray();

                            // SEPARATING THE FIELDS
                            byte [][] fieldsServerHello = new byte[4][];
                            int ci = 0; // current index on bytesServerHello
                            int tempci = ci;

                            // HELLO
                            ByteArrayOutputStream baosServerHelloHello = new ByteArrayOutputStream();
                            for(int i=ci;(char)( bytesServerHello[i] ) != TCPServerThread.transmission_del;i++){
                                baosServerHelloHello.write( (byte) bytesServerHello[i] );
                                ci=i;
                            }
                            fieldsServerHello[0] = baosServerHelloHello.toByteArray();

                            ci++; // Now must be on delimiter
                            if( (char)( bytesServerHello[ci] ) != TCPServerThread.transmission_del ){
                                Log.d("TCP client","Expected " + TCPServerThread.transmission_del +" after the HELLO bytes. Found " + bytesServerHello[ci]);
                                protocol_failure_exit();
                                return;
                            }
                            ci++;

                            // SERVER CERTIFICATE LENGTH
                            String certificateServerHelloLength = "";
                            for(int i=ci;(char)( bytesServerHello[i] ) != TCPServerThread.transmission_del;i++){
                                certificateServerHelloLength += (char) bytesServerHello[i];
                                ci = i;
                            }
                            int certificateServerHelloLengthInt = Integer.parseInt(certificateServerHelloLength);

                            ci++; // Now must be on delimiter
                            if( (char)(bytesServerHello[ci]) != TCPServerThread.transmission_del ){
                                Log.d("TCP client","Expected " + TCPServerThread.transmission_del +" after the Server CERT LENGTH bytes. Found " + bytesServerHello[ci]);
                                protocol_failure_exit();
                                return;
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
                            if(bytesServerHello[ci] != TCPServerThread.transmission_del){
                                Log.d("TCP client","Expected " + TCPServerThread.transmission_del + " after the server CERTIFICATE bytes. Found " + bytesServerHello[ci]);
                                protocol_failure_exit();
                                return;
                            }
                            ci++;

                            // NONCE
                            tempci = ci;
                            ByteArrayOutputStream baosServerHelloNonce = new ByteArrayOutputStream();
                            for(int i=ci;i<ci+20;i++){
                                baosServerHelloNonce.write((byte)(bytesServerHello[i]));
                                tempci=i;
                            }
                            fieldsServerHello[2] = baosServerHelloNonce.toByteArray();
                            ci = tempci;

                            ci++; // Now must be on delimiter
                            if( (char)( bytesServerHello[ci] ) != TCPServerThread.transmission_del){
                                Log.d("TCP client","Expected " + TCPServerThread.transmission_del + " after the NONCE bytes. Found " + bytesServerHello[ci]);
                                protocol_failure_exit();
                                return;
                            }
                            ci++;

                            // SIGNED NONCE UNTIL THE END NOW
                            ByteArrayOutputStream baosServerHelloSignedNonce = new ByteArrayOutputStream();
                            for(int i=ci;i<bytesServerHello.length;i++){
                                baosServerHelloSignedNonce.write((byte)(bytesServerHello[i]));
                            }
                            fieldsServerHello[3] = baosServerHelloSignedNonce.toByteArray();

                            if(!checkFieldsHelloServer(fieldsServerHello)){
                                Log.d("TCP CLIENT","The received fields are incorrect! Closing connection!");
                                protocol_failure_exit();
                                return;
                            }

                            Log.d("TCP CLIENT","The received fields are CORRECT!");

                            connectivity_status_TV.setText("Connected!");
                            connectivity_status_TV.setBackgroundColor(Color.GREEN);

                            // now we will use the socket for communication from now on
                            InterNodeCrypto.save_peer_cert(fieldsServerHello[1]);
                            Log.d("TCP CLIENT","SUCCESS THE PEER CERTIFICATE IS NOW READY TO USE!");

                        } catch (IOException e) {
                            Log.d("NET CONFIG","Could not create/connect the socket!");
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
                        }

                    }
                }
        );

        // that way we don't need to
        // update the user on the status of the connectivity with the connectivity status text view

        // TODO: Check here if we have asked for a new peer from the coordinator then we should try to establish connectivity as a client.
        // this  check should not be triggered by the clicking of the Save button but rather should be done automatically initially
        // After the first time we should only ask for a new peer from the coordinator if and only if the connectivity is lost
        // this request should be done when the user makes a new search for example

    }

    public static void protocol_failure_exit() throws IOException {
        my_client_socket.close();
        connectivity_status_TV.setText("Incorrect fields received from Server");
        connectivity_status_TV.setBackgroundColor(Color.RED);
    }
    public boolean checkFieldsHelloServer(byte [][] arr) {
        // For now client and server use the same fields in the hello messages
        // So we can use the same function to check that the fields received are correct
        return TCPServerThread.checkFieldsHello(arr,"Client");
    }

    byte [] str_to_ip_array(String input) throws InvalidParameterException {
        Log.d("STRTOIP",""+input);
        input = input.replace(" ","");
        input = input.replace("\n","");
        Log.d("STRTOIP with white-space replacement",""+input);
        byte [] peer_addr_array = new byte[4];
        String [] input_arr = input.split("\\.");
        if(input_arr.length != 4){
            throw new InvalidParameterException();
        }
        for(int i=0;i<=3;i++) {
            peer_addr_array[i] = (byte) (Integer.parseInt(input_arr[i]) & 0xFF);
        }
        return peer_addr_array;
    }

}