package com.example.lbs_app_for_poc;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.lbs_app_for_poc.databinding.FragmentFirstBinding;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private static boolean CredsNoticeGiven = false; // We will give at most one time notice to the user when there are no certificates

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

        Button search_initiator_button = view.findViewById(R.id.button_first);
        search_initiator_button.setText("Search Initiator Node");
        binding.buttonFirst.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CredentialsNoticeCheck();
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
                        CredentialsNoticeCheck();
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
                        CredentialsNoticeCheck();
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

    }

    public void CredentialsNoticeCheck(){
        // We should also check whether there are certificates or not loaded and report it to the user the first time the app is
        try {
            InterNodeCrypto.LoadCertificates();
            if(InterNodeCrypto.checkMyCreds()) {
                Toast.makeText(getContext(), "The loaded credentials are valid!", Toast.LENGTH_SHORT).show();
            }
            else{
                Toast.makeText(getContext(), "The loaded credentials are invalid.\nConsider configuring the credentials first!", Toast.LENGTH_SHORT).show();
            }
        }
        catch (Exception e){
            // TODO: Give more verbose reason here why the credentials are not loaded successfully!
            Toast.makeText(getContext(), "No credentials loaded! Consider configuring them!", Toast.LENGTH_SHORT).show();
        }
        CredsNoticeGiven = true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}