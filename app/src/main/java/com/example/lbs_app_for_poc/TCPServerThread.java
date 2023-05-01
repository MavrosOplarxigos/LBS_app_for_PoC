package com.example.lbs_app_for_poc;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServerThread extends Thread{

    public ServerSocket serverSocket;
    // maybe a scroll view will be given as input to the constructor and we can report logs to that scroll view with text views

    public TCPServerThread(ServerSocket s){
        serverSocket = s;
    }

    public void run(){

        Log.d("TPC server","The server thread is now running!");

        while(true){

            try {

                Log.d("TCP server","Waiting for message from client!");
                Socket socket = serverSocket.accept();

                // 1) HANDSHAKE STEP 1: RECEIVE CLIENT CREDS
                // OK so now the client must sent his credentials to us

                // We should expect the following format
                // [8 bytes][ ? ECDSA CERT LENGHT AFTER ENCRYPTED WITH CA PRIV KEY ]
                // [CLIENT ID][ECDSA_ID_AND_PUBKEY_ENCRYPTED_WITH_CA_PRIVATE_KEY]

                // VERIFY THAT THE ID = ID IN THE CERTIFICATE

                // 2) HANDSHAKE STEP 2: SEND SERVER CREDS TO THE CLIENT
                // [8 bytes][ ? ECDSA CERT LENGHT AFTER ENCRYPTED WITH CA PRIV KEY ]
                // [SERVER ID][ECDSA_ID_AND_SERVER_PUBKEY_ENC_WITH_CA_PRIVATE_KEY]

                // 3) SERVICE STEP 1: RECEIVE A MAP SEARCH ITEM AS A STRING ENCRYPTED WITH SERVERS PUBLIC KEY AND CLIENTS PRIVATE KEY FOR AUTHENTICATION AND NON-REPUDIATION
                // 4) SERVICE STEP 2: DECRYPT THE STRING USING CLIENT'S PUBLIC KEY AND SERVER'S PRIVATE KEY (MAYBE FOR THIS ONE WE SHOULD USE BYTE ARRAYS INSTEAD OF STRINGS)
                // 5) SERVICE STEP 3: CONVERT FROM STRING TO JAVA OBJECT (MAP SEARCH ITEM)

                // 6) SERVICE STEP 4: CARRY OUT THE apicall function on the MapSearchItem

                // 7) SERVICE STEP 5: ENCRYPT THE RESULTING STRING WITH THE SERVER'S PRIVATE KEY FOR AUTH AND NONREP AND ENCRYPT IT WITH CLIENT'S PUBLIC KEY

                // 8) SERVICE STEP 6: SEND THE ENCRYPTED STRING BACK TO THE CLIENT

                // 9) ACKNOWLEDGEMENT STEP 1: RECEIVE A STRING WHICH IS ESSENTIALLY THE HAS OF THE RESPONSE STRING SIGNED WITH THE CLIENT'S KEY AND THEN ENCRYPTED WITH THE SERVER'S KEY
                // 10) ACKNOLEDGEMENT STEP 2: VERIFY THAT EVERYTHING IS RIGHT AND UPDATE THE SCROLL VIEW TO INDICATE THE STATUS OF THE REQUEST AS COMPLETED AND ACKNOWLEDGED

                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

                // read from client
                String client_msg = inputStream.readUTF();
                Log.d("TCP server","Message from client is "+client_msg);

                // send response
                String my_response = "Hello from server!";
                outputStream.writeUTF(my_response);


                socket.close();
                Log.d("TCP server","Connection closed with " + socket.getInetAddress().getHostName() );

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }

    }

}
