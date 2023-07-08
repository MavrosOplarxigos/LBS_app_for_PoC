package com.example.lbs_app_for_poc;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.lbs_app_for_poc.databinding.FragmentFirstBinding;

import javax.crypto.Cipher;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private static boolean CredsNoticeGiven = false;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        CredsNoticeGiven = false;

        Button check_algo_button = view.findViewById(R.id.check_algo);
        check_algo_button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try{
                            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                            Toast.makeText(getContext(), "YES!!!!", Toast.LENGTH_SHORT).show();
                            Log.d("CATCH","SUCCESS: algorithm exists!");
                        }
                        catch (Exception e){
                            e.printStackTrace();
                            Log.d("CATCH","UNLUCKY: The algoritm doesn't exist!");
                            Toast.makeText(getContext(), "NOxxxxx", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
        check_algo_button.setEnabled(false);
        check_algo_button.setVisibility(View.INVISIBLE);
        check_algo_button.setClickable(false);

        Button search_initiator_button = view.findViewById(R.id.button_first);
        search_initiator_button.setText("Search Initiator Node");
        binding.buttonFirst.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(FirstFragment.this)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment);
            }
        });

        Button proxy_node_button = view.findViewById(R.id.proxy_node_button);
        proxy_node_button.setText("Proxy Node");
        proxy_node_button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        NavHostFragment.findNavController(FirstFragment.this)
                                .navigate(R.id.action_FirstFragment_to_intermediateNodeConfig);
                    }
                }
        );

        Button configure_button = view.findViewById(R.id.configure_button);
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
        );

        Button configure_creds_button = view.findViewById(R.id.configure_credentials_button);
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
        );

        view.setBackgroundColor(Color.DKGRAY);

        // check credentials and tell the user if it is necessary to configure them!
        Thread credsCheckThread = new Thread(new Runnable() {
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
        });
        credsCheckThread.start();

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