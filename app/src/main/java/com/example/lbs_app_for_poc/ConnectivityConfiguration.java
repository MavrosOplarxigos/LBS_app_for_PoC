package com.example.lbs_app_for_poc;

import android.net.InetAddresses;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

public class ConnectivityConfiguration extends Fragment {

    // public String caller_fragment;
    public InetAddress my_ip_address = null;
    public InetAddress my_peer_ip_address = null;

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
                my_peer_ip_address = InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException e) {
                Log.d("NET CONFIG","Peer address not found from given name!");
                throw new RuntimeException(e);
            }
        }
        TextView my_peer_ip_address_TV = (TextView) view.findViewById(R.id.peerIPaddress_value);
        my_peer_ip_address_TV.setText(my_peer_ip_address.toString().split("/")[1]);

        // Displaying my own port

        // Displaying peer's port

        // Adding listener to save button to update the values
            // retry establishing a new TCP connection when it is clicked
            // update the user on the status of the connectivity with the connectivity status text view



    }

}