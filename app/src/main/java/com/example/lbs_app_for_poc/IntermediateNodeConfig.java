package com.example.lbs_app_for_poc;

import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class IntermediateNodeConfig extends Fragment {
/*
    // ip address for informing the client
    public static InetAddress my_ip_address = null;

    public static int single_port_to_open;
    public HashSet < Integer > usedPorts;
    // public static ArrayList < TCPServerThread > serverThreadsArrayList;
    // message for max connections
    boolean MessageMaxConns = false;
    // 15 was chosen arbitrarily but a small number of nodes should be chosen for the safety of the serving node
    int MAX_CONNECTIONS_LIMIT = 15;

    // logger views
    public static ScrollView loggingSV;
    public static LinearLayout loggingLL;
    public static Handler tcpInfoHandler;

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

        if(usedPorts == null){
            usedPorts = new HashSet<Integer>();
        }
        if(serverThreadsArrayList == null){
            serverThreadsArrayList = new ArrayList<TCPServerThread>();
        }

        // Retrieving my own IP address on wlan0
        try {

            my_ip_address = null;
            NetworkInterface wlan0 = NetworkInterface.getByName("wlan0");
            List<InetAddress> all_addresses = Collections.list(wlan0.getInetAddresses());
            for(InetAddress addr : all_addresses){
                if(addr.toString().contains(":")){
                    // then it should be a MAC address
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

        EditText my_port_TV = (EditText) view.findViewById(R.id.server_port_value_intermediate_node);
        single_port_to_open = 55777;
        my_port_TV.setText(Integer.toString(single_port_to_open));
        my_port_TV.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        // do nothing
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        single_port_to_open = Integer.parseInt(s.toString());
                        Log.d("Intermediate Node Config","The single port has changed to " + single_port_to_open);
                    }
                }
        );

        // scroll view to log all the connections and queries the intermediate node receives
        loggingSV = (ScrollView) view.findViewById(R.id.server_log_SV);
        // add relative view to the scroll view
        loggingLL = new LinearLayout(getContext());
        loggingLL.setOrientation(LinearLayout.VERTICAL);
        loggingSV.addView(loggingLL);
        // the handler for the log scroll view
        tcpInfoHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {

                Bundle bundle = msg.getData();
                TextView textView = new TextView(getContext());

                if( bundle.containsKey("isPortOpening") ){
                    textView.setTextColor(Color.GREEN);
                    textView.setText( "Port opened: " + bundle.getInt("MyPort") );
                }
                else {
                    String ipAddress = bundle.getString("ipAddress");
                    int port = bundle.getInt("port");
                    String peerID = bundle.getString("PeerID");

                    if (bundle.containsKey("isConnectionAccept")) {
                        textView.setTextColor(Color.GREEN);
                        int MyPort = bundle.getInt("MyPort");
                        textView.setText("Accepted connection from " + ipAddress + ":" + port + " (" + peerID + ") " + " on port " + MyPort);
                    } else if (bundle.containsKey("isDisconnection")) {
                        textView.setTextColor(Color.RED);
                        textView.setText("Connection terminated by " + ipAddress + ":" + port + " (" + peerID + ")");
                    } else if (bundle.containsKey("isQuery")) {
                        textView.setTextColor(Color.CYAN);
                        textView.setText("Query received from" + ipAddress + ":" + port + " (" + peerID + ")");
                    } else if (bundle.containsKey("isAnswer")) {
                        textView.setTextColor(Color.BLUE);
                        textView.setText("Answered query from" + ipAddress + ":" + port + " (" + peerID + ")");
                    } else {
                        Log.d("TCP INFO LOGGER", "ERROR: Title info not recognized!");
                        return;
                    }
                }
                loggingLL.addView(textView);

                loggingLL.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        loggingLL.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        loggingSV.post(() -> loggingSV.fullScroll(ScrollView.FOCUS_DOWN));
                    }
                });
                /*loggingSV.post(()-> {
                    loggingSV.fullScroll(ScrollView.FOCUS_DOWN);
                });

            }
        };

        // single port start button
        Button save_and_start = (Button) view.findViewById(R.id.server_button_start);
        save_and_start.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        try {

                            Log.d("TCP SERVER","Now trying to open port " + single_port_to_open);

                            if( usedPorts.contains(single_port_to_open) ){
                                Toast.makeText(getContext(), "Port " + single_port_to_open + " already used!", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            Log.d("TCP SERVER","Port " + single_port_to_open + " not on use.");

                            // Open the thread on the unused port
                            ServerSocket serverSocket = new ServerSocket(single_port_to_open);
                            Log.d("TCP server","New server socket on port " + single_port_to_open);
                            TCPServerThread serverThread = new TCPServerThread(serverSocket,tcpInfoHandler);
                            Log.d("TCP server","TCP server thread on " + single_port_to_open + " running");
                            // put into the array list
                            serverThreadsArrayList.add(serverThread);

                            // start the thread
                            (serverThreadsArrayList.get(serverThreadsArrayList.size()-1)).start();

                            // log it
                            Message portOpeningMessage = tcpInfoHandler.obtainMessage();
                            Bundle portOpeningBundle = new Bundle();
                            portOpeningBundle.putBoolean("isPortOpening",true);
                            Log.d("MY PORT INC",""+(serverThreadsArrayList.get(serverThreadsArrayList.size()-1)).serverSocket.getLocalPort());
                            portOpeningBundle.putInt("MyPort",(serverThreadsArrayList.get(serverThreadsArrayList.size()-1)).serverSocket.getLocalPort());
                            portOpeningMessage.setData(portOpeningBundle);
                            tcpInfoHandler.sendMessage(portOpeningMessage);

                            // mark port as used
                            usedPorts.add(single_port_to_open);

                        } catch (IOException e) {
                            Log.d("TCP server","Could not start TCP server on port " + single_port_to_open);
                            throw new RuntimeException(e);
                        }

                    }
                }
        );

        // multiport start button
        Button multiport_button = (Button) view.findViewById(R.id.multiport_button_start);
        multiport_button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        for(int i=0;i<10;i++){

                            int port = -1;
                            while( (port == -1) || ( (port!=-1) && ( usedPorts.contains(port) ) ) ){
                                SecureRandom secureRandomPort = new SecureRandom();
                                // using ports within the application level ranges: 49152–65535
                                // reference: https://datatracker.ietf.org/doc/html/rfc6335
                                int min_port = 49152;
                                int max_port = 65535;
                                int range = max_port - min_port + 1;
                                port = secureRandomPort.nextInt(range) + min_port;
                            }

                            openPort(port);

                        }

                    }
                }
        );

    }

    // Returns true iff we are below the max connections limit
    public boolean CheckMaxConnectionsLimit(){

        if(usedPorts.size() == MAX_CONNECTIONS_LIMIT){
            if(!MessageMaxConns){
                Toast.makeText(getContext(), "Maximum number of " + MAX_CONNECTIONS_LIMIT + " connections reached!", Toast.LENGTH_SHORT).show();
                MessageMaxConns = true;
            }
            return false;
        }

        // resetting the message notification if we have been to 15 then dropped and then might reach 15 again.
        if(MessageMaxConns){
            MessageMaxConns = false;
        }
        return true;

    }

    public void openPort(int port){

        if(!CheckMaxConnectionsLimit()){
            return;
        }

        try {

            // Open the thread on the unused port
            ServerSocket serverSocket = new ServerSocket(port);
            Log.d("TCP server","New server socket on port " + port);
            TCPServerThread serverThread = new TCPServerThread(serverSocket,tcpInfoHandler);
            Log.d("TCP server","TCP server thread on " + port + " running");
            // put into the array list
            serverThreadsArrayList.add(serverThread);

            // start the thread
            (serverThreadsArrayList.get(serverThreadsArrayList.size()-1)).start();

            // log it
            Message portOpeningMessage = tcpInfoHandler.obtainMessage();
            Bundle portOpeningBundle = new Bundle();
            portOpeningBundle.putBoolean("isPortOpening",true);
            portOpeningBundle.putInt("MyPort",(serverThreadsArrayList.get(serverThreadsArrayList.size()-1)).serverSocket.getLocalPort());
            portOpeningMessage.setData(portOpeningBundle);
            tcpInfoHandler.sendMessage(portOpeningMessage);

            // mark port as used
            usedPorts.add(port);

        } catch (IOException e) {
            Log.d("TCP server","Could not start TCP server on port " + port);
            throw new RuntimeException(e);
        }

    }
*/
}