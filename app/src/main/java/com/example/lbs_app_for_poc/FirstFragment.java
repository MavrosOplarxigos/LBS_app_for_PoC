package com.example.lbs_app_for_poc;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.lbs_app_for_poc.databinding.FragmentFirstBinding;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;

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
        configure_button.setText("Configure Identity");
        binding.configureButton.setOnClickListener(
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}