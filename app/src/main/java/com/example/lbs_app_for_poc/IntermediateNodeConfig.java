package com.example.lbs_app_for_poc;

import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;

public class IntermediateNodeConfig extends Fragment {

    public static InetAddress my_ip_address = null;
    public static int my_port = -1;
    public static ServerSocket serverSocket;
    public static TCPServerThread serverThread;
    public static ScrollView loggingSV;
    public static LinearLayout loggingLL;

    public IntermediateNodeConfig() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_intermediate_node_config, container, false);
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

            TextView my_ip_addr_value_TV = view.findViewById(R.id.ip_address_value_intermediate_node);
            my_ip_addr_value_TV.setText( (my_ip_address.toString().split("/") )[1] );

        }
        catch (Exception e){
            Log.d("NET CONFIG","Can't get my own IP address on the wireless interface");
        }

        // Now we show the port chosen
        if(my_port == -1){
            // no port chosen
            my_port = 55777;
        }

        TextView my_port_TV = (TextView) view.findViewById(R.id.server_port_value_intermediate_node);
        my_port_TV.setText( Integer.toString(my_port) );

        // Now we set the server status text view appearance
        TextView server_status = (TextView) view.findViewById(R.id.server_status);
        if( serverSocket == null ){
            server_status.setText("Serer not setup!");
            server_status.setBackgroundColor(Color.GRAY);
        } else if ( serverSocket.isClosed() ) {
            server_status.setText("Server closed!");
            server_status.setBackgroundColor(Color.RED);
        }
        else if( serverSocket.isBound() ){
            server_status.setText("Server Bound!");
            server_status.setBackgroundColor(Color.GREEN);
        }
        else {
            server_status.setText("Server open and unbound!");
            server_status.setBackgroundColor(Color.YELLOW);
        }

        // scroll view to log all the connections and queries the intermediate node receives
        loggingSV = (ScrollView) view.findViewById(R.id.server_log_SV);
        // add relative view to the scroll view
        loggingLL = new LinearLayout(getContext());
        loggingLL.setOrientation(LinearLayout.VERTICAL);
        loggingSV.addView(loggingLL);

        Handler tcpInfoHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {

                Bundle bundle = msg.getData();

                String ipAddress = bundle.getString("ipAddress");
                int port = bundle.getInt("port");
                TextView textView = new TextView(getContext());

                if( bundle.containsKey("isConnectionAccept") ) {
                    textView.setTextColor(Color.GREEN);
                    textView.setText("Accepted connection from " + ipAddress + ":" + port);
                } else if (bundle.containsKey("isDisconnection") ) {
                    textView.setTextColor(Color.RED);
                    textView.setText("Connection terminated by " + ipAddress + ":" + port);
                } else if ( bundle.containsKey("isQuery") ) {
                    textView.setTextColor(Color.CYAN);
                    textView.setText("Query received from" + ipAddress + ":" + port);
                } else if (bundle.containsKey("isAnswer")) {
                    textView.setTextColor(Color.BLUE);
                    textView.setText("Answered query from" + ipAddress + ":" + port);
                } else{
                    Log.d("TCP INFO LOGGER","ERROR: Title info not recognized!");
                    return;
                }
                loggingLL.addView(textView);

            }
        };

        Button save_and_start = (Button) view.findViewById(R.id.server_button_start);
        save_and_start.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        try {
                            serverSocket = new ServerSocket(my_port);
                            Log.d("TCP server","New server socket!");

                            if( serverSocket.isBound() ){
                                server_status.setText("Server Bound and Listening!");
                                server_status.setBackgroundColor(Color.GREEN);
                            }
                            else {
                                Log.d("TCP server","Server could not be bound!");
                                server_status.setText("Server open but unbound!");
                                server_status.setBackgroundColor(Color.RED);
                            }

                            if(serverThread != null){
                                serverThread.join();
                            }
                            // OK now we need to get connections on the server socket
                            // We would need a thread to do that right?

                            // TODO: figure out how to have a thread running even after the fragment is switched
                            serverThread = new TCPServerThread(serverSocket,tcpInfoHandler);
                            serverThread.start();

                        } catch (IOException e) {
                            Log.d("TCP server","Could not start TCP server!");
                            throw new RuntimeException(e);
                        } catch (InterruptedException e) {
                            Log.d("TCP server","Could not join server thread!");
                            throw new RuntimeException(e);
                        }

                    }
                }
        );

    }

}