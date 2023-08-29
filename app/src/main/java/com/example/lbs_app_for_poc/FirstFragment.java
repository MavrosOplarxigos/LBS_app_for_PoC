package com.example.lbs_app_for_poc;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.lbs_app_for_poc.databinding.FragmentFirstBinding;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private static boolean CredsNoticeGiven = false;
    public static LBSEntitiesConnectivity lbsEC;
    public ImageView Remote_Services_Online_STATUS_IV;
    public static String Remote_Services_Online_STATUS_String; // Online, Connecting, Offline
    public ImageView Credentials_Loaded_STATUS_IV;
    public static String Credentials_Loaded_STATUS_String;
    public static final Pattern ipAddressPattern = Pattern.compile("^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

    public Thread connThread = null;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Status image views
        Remote_Services_Online_STATUS_IV = view.findViewById(R.id.Remote_Services_Online_STATUS);
        Remote_Services_Online_STATUS_String = "Offline";
        Credentials_Loaded_STATUS_IV = view.findViewById(R.id.Credentials_Loaded_STATUS);
        Credentials_Loaded_STATUS_String = "Offline";
        Drawable errorDrawable = ContextCompat.getDrawable(getActivity(), R.drawable.error);
        Remote_Services_Online_STATUS_IV.setImageDrawable(errorDrawable);
        Credentials_Loaded_STATUS_IV.setImageDrawable(errorDrawable);

        // running the NTP synchronization task
        NtpSyncTask ntpSyncTask = new NtpSyncTask();
        ntpSyncTask.execute();

        // Button for using the LBS after the configuration is done!
        Button search_initiator_button = view.findViewById(R.id.button_first);
        search_initiator_button.setText("USE LBS");
        search_initiator_button.setEnabled(false);
        search_initiator_button.setClickable(false);
        search_initiator_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                lbsEC.writeObjectFile(); // We save the current configuration for later usages of the application
                // NOTE: if the above blocks run it in a thread although highly unlikely since we are just saving 2 small strings essentially

                // TODO: 3) Start a thread for disclosing our IP address and port for accepting incoming connections to the P2P AVAILABILITY every 30 seconds
                // TODO: 4) Implement a Log Fragment in which the various threads are going to be adding logs add button to search fragment
                // TODO: 5) Implement Thread for AVAILABILITY disclosure (yellow color)
                // TODO: 6) Thread for PEER DISCOVERY (which runs once our records are either not fresh, non-existent or non repsonsive 1 time)
                // TODO: 7) Thread for SERVING peers that communicate their requests to us
                // TODO: 8) The listener on the SEARCH BUTTON adding all the records from our requests to other peers, consensus report and so forth, timeouts for irresponsive peers

                // going to the map fragment
                NavHostFragment.findNavController(FirstFragment.this)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment);
            }
        });

        // Initialize LBS entities connectivity
        lbsEC = new LBSEntitiesConnectivity(getActivity(),this);

        EditText LBSemIP_ET = view.findViewById(R.id.LBS_entities_manager_IP_ET);
        EditText LBSemNAME_ET = view.findViewById(R.id.Node_Name_ET);

        if(lbsEC.ENTITIES_MANAGER_IP != null && lbsEC.MY_REAL_NODE_NAME != null ){
            LBSemIP_ET.setText(lbsEC.ENTITIES_MANAGER_IP.toString());
            LBSemIP_ET.setBackgroundColor(Color.parseColor("#008000"));
            LBSemNAME_ET.setText(lbsEC.MY_REAL_NODE_NAME.toString());
            LBSemNAME_ET.setBackgroundColor(Color.parseColor("#008000"));
            // run the thread for retrieving data from the entities manager

            if(connThread.isAlive()){
                Log.d("connThread","The thread is already running! This is unexpected behavior!");
                try {
                    connThread.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            // if we already have the fields we try to connect to the remote server immediately
            connThread = new Thread(lbsEC.establish);
            connThread.start();

        }

        // add listener for when the IP address is changed
        LBSemIP_ET.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        // Before the text is changed
                        LBSemIP_ET.setBackgroundColor(Color.TRANSPARENT);
                    }
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count)
                    {
                        LBSemIP_ET.setBackgroundColor(Color.TRANSPARENT);
                    }
                    @Override
                    public void afterTextChanged(Editable s) {
                        String newText = s.toString();
                        // check if the IP address is valid
                        if(!ipAddressPattern.matcher(newText).matches()){
                            LBSemIP_ET.setBackgroundColor(Color.parseColor("#FFAAAA"));
                            return;
                        }
                        else{
                            LBSemIP_ET.setBackgroundColor(Color.parseColor("#008000"));
                        }
                    }
                }
        );

        LBSemNAME_ET.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        LBSemNAME_ET.setBackgroundColor(Color.TRANSPARENT);
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        LBSemNAME_ET.setBackgroundColor(Color.TRANSPARENT);
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        // Let's just set a limit of 5 symbols at least entered
                        String newText = s.toString();
                        if(s.length() < 5){
                            LBSemNAME_ET.setBackgroundColor(Color.parseColor("#FFAAAA"));
                        }
                        else{
                            LBSemNAME_ET.setBackgroundColor(Color.parseColor("#008000"));
                        }
                    }
                }
        );

        Button configureButton = view.findViewById(R.id.configure_button);
        configureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        Log.d("Save/Update configuration button","Button pressed now checking the fields to be valid");
                        LBSemIP_ET.clearFocus();
                        LBSemNAME_ET.clearFocus();
                        if( !( (LBSemNAME_ET.getText().toString().length()>=5) && (ipAddressPattern.matcher(LBSemIP_ET.getText().toString()).matches()) ) ){
                            Log.d("Save/Update configuration button","One of the fields is invalid!");
                            if( !(LBSemNAME_ET.getText().toString().length()>=5) ) {
                                Toast.makeText(getContext(), "Very short name chosen!", Toast.LENGTH_SHORT).show();
                            }
                            else {
                                Toast.makeText(getContext(), "Incorrect IP address given!", Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        Log.d("Save/Update configuration button","The IP & name given are of valid format!");

                        // Stop the already running Threads if they are running.

                        try {
                            lbsEC.ENTITIES_MANAGER_IP = InetAddress.getByName(LBSemIP_ET.getText().toString());
                            lbsEC.MY_REAL_NODE_NAME = LBSemNAME_ET.getText().toString();
                        } catch (UnknownHostException e) {
                            Log.d("Save/Update configuration button","Could not retrieve the data from the EditText to the LBSConnectivity class!");
                            throw new RuntimeException(e);
                        }

                        if(connThread!=null && connThread.isAlive()){
                            Log.d("connThread","The thread is already running the user must have pressed the button very fast!");
                            try {
                                Toast.makeText(getContext(), "Previous configuration still loading!", Toast.LENGTH_SHORT).show();
                                connThread.join();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        // We start the standard Thread and we let to it the responsibility of enabling the "USE LBS" button
                        connThread = new Thread(lbsEC.establish);
                        Log.d("Save/Update configuration button","Thread initialized");
                        connThread.start();
                        Log.d("Save/Update configuration button","establish thread started!");

                    }
                }
        );

        // Debug block size
        /*
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            int blockSize = cipher.getBlockSize();
            Log.d("RSA_encyption_block_size","size = " + blockSize);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }*/

        /*Button check_algo_button = view.findViewById(R.id.check_algo);
        check_algo_button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            CryptoTimestamp ct = InterNodeCrypto.getSignedTimestamp();
                            long value = ByteBuffer.wrap(ct.timestamp).getLong();
                            check_algo_button.setText(String.valueOf(value));
                            // Toast.makeText(getContext(), "Timestamp: " + value, Toast.LENGTH_SHORT).show();
                        } catch (NoSuchAlgorithmException e) {
                            throw new RuntimeException(e);
                        } catch (SignatureException e) {
                            throw new RuntimeException(e);
                        } catch (NoSuchProviderException e) {
                            throw new RuntimeException(e);
                        } catch (InvalidKeyException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
        );*/
        // check_algo_button.setEnabled(false);
        // check_algo_button.setVisibility(View.INVISIBLE);
        // check_algo_button.setClickable(false);

        /*Button proxy_node_button = view.findViewById(R.id.proxy_node_button);
        proxy_node_button.setText("Proxy Node");
        proxy_node_button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        NavHostFragment.findNavController(FirstFragment.this)
                                .navigate(R.id.action_FirstFragment_to_intermediateNodeConfig);
                    }
                }
        );*/

        /*Button configure_button = view.findViewById(R.id.configure_button);
        configure_button.setText("Configure Client Node");
        binding.configureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //Bundle bundle = new Bundle();
                        //bundle.putString("caller_fragment","main");
                        //NavHostFragment.findNavController(FirstFragment.this)
                        //               .navigate(R.id.action_FirstFragment_to_connectivityConfiguration,bundle);
                        NavHostFragment.findNavController(FirstFragment.this)
                                .navigate(R.id.action_FirstFragment_to_connectivityConfiguration);
                    }
                }
        );*/

        /*Button configure_creds_button = view.findViewById(R.id.configure_credentials_button);
        configure_creds_button.setText("Configure Identity");
        configure_creds_button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //Bundle bundle = new Bundle();
                        //bundle.putString("caller_fragment","main");
                        //NavHostFragment.findNavController(FirstFragment.this)
                        //               .navigate(R.id.action_FirstFragment_to_connectivityConfiguration,bundle);
                        NavHostFragment.findNavController(FirstFragment.this)
                                .navigate(R.id.action_FirstFragment_to_credentialsSelection);
                    }
                }
        );*/

        // view.setBackgroundColor(Color.DKGRAY);

        // CredsNoticeGiven = false;
        // check credentials and tell the user if it is necessary to configure them!
        /*Thread credsCheckThread = new Thread(new Runnable() {
            @Override
            public void run() {

                Log.d("INITIAL_CREDENTIAL_CHECK","entered thread!");
                try{
                    MainActivity.waitForCredsFlag();
                    CredentialsNoticeCheck();
                }
                catch (Exception e){
                    e.printStackTrace();
                }

            }
        });*/
        // credsCheckThread.start();

    }

    public void CredentialsNoticeCheck(){
        if (!FirstFragment.CredsNoticeGiven) {
            Log.d("CREDS NOTICE CHECK","startedcredentialnoticecheck");
            // We should also check whether there are certificates or not loaded and report it to the user the first time the app is
            try {
                InterNodeCrypto.LoadCredentials();
                if (InterNodeCrypto.checkMyCreds()) {
                    Log.d("Initial credentials check!", "The credentials are loaded & ready!");
                    getActivity().runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getContext(), "Credentials Ready!", Toast.LENGTH_SHORT).show();
                                }
                            }
                    );
                } else {
                    getActivity().runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getContext(), "Credentials Invalid! Configure them!", Toast.LENGTH_SHORT).show();
                                }
                            }
                    );
                }
            } catch (Exception e) {
                // TODO: Give more verbose reason here why the credentials are not loaded successfully!
                getActivity().runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getContext(), "No credentials loaded! Consider configuring them!", Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            }
            CredsNoticeGiven = true;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}