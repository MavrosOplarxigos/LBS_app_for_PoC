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

    public TCPServerThread(ServerSocket s){
        serverSocket = s;
    }

    public void run(){

        Log.d("TPC server","The server thread is now running!");

        while(true){

            try {

                Log.d("TCP server","Waiting for message from client!");
                Socket socket = serverSocket.accept();

                // input and output stream
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
