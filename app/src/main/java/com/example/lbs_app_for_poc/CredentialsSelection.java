package com.example.lbs_app_for_poc;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResultRegistry;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public class CredentialsSelection extends Fragment {

    // Inner class for selecting files
    class MyLifeCycleObserver implements DefaultLifecycleObserver{
        public final ActivityResultRegistry mRegistry;
        public ActivityResultLauncher<String> GetContent;
        public MyLifeCycleObserver(@NonNull ActivityResultRegistry registry) {
            this.mRegistry = registry;
        }
        public void onCreate(@NonNull LifecycleOwner owner){
            GetContent = mRegistry.register("key",owner,new ActivityResultContracts.GetContent(),
                    new ActivityResultCallback<Uri>(){
                        @Override
                        public void onActivityResult(Uri uri){
                            // Handle the returned Uri
                            Log.d("CRED FILE LOADING","Now handling the selected URI");
                            if(uri == null){
                                Log.d("CRED FILE LOADING","The selected URI is null!");
                                return;
                            }
                            if(Objects.equals(target_file, "CAcert")){
                                Log.d("CA_CRED","We are trying to load the CAcert! uri = " + uri.getPath() );
                                try {

                                    String pathFromFileChooser = AnotherFileChooser.getPath(getContext(),uri);
                                    // String pathFromFileChooser = FileChooser.ContentProviderGetPath(getContext(),uri);
                                    // String pathFromFileChooser = FileChooser.getPath(getContext(),uri);
                                    Log.d("CA_CRED","The resolved path from the AnotherFileChooser class is "+pathFromFileChooser);

                                    CAcertFile = new File(pathFromFileChooser);
                                    if(CAcertFile==null){
                                        Log.d("CA_CRED","The CAcertFile from pathFromFileChooser is null");
                                    }

                                    /*allowAccess1stAttempt();

                                    if (ContextCompat.checkSelfPermission(getActivity(), android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                                        ActivityCompat.requestPermissions(getActivity(),
                                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                                MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                                    }*/

                                    Log.d("CA_CRED","Now will try to give the path to a FileInputStream instead to see if we can read it!");
                                    try {
                                        FileInputStream CAcertIFS = new FileInputStream(pathFromFileChooser);
                                        Log.d("CA_CRED","Successfully loaded path on FIS! Use FIS to read the file from path instead!");
                                    } catch (FileNotFoundException e) {
                                        Log.d("CA_CRED","Failing to open it with FIS directly as well!");
                                        throw new RuntimeException(e);
                                    }

                                    // CAcertFile = new File(uri.toString());
                                }
                                catch (RuntimeException e){
                                    Log.d("CRED FILE LOADING","Couldn't get a path from the URI selected!");
                                    e.printStackTrace();
                                    return;
                                }
                                Log.d("CA_CRED","The file is loaded! " + CAcertFile.toString() );
                                try {
                                    CAdetailsTV.setText( InterNodeCrypto.getCertDetails(CAcertFile) );
                                }
                                catch (Exception e){
                                    Log.d("CA_CRED","Failed to get certificate details!");
                                    e.printStackTrace();
                                }
                                Log.d("CRED FILE LOADING","Saved to CAcertFile!");
                                return;
                            }

                        }
                    });
        }

        private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 2123;
        public void allowAccess1stAttempt(){
            if (ContextCompat.checkSelfPermission(getActivity(), android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
            }
        }

        public void selectFile(){
            // Open this activity to select the file
            // the important part with this was the */* that allows you to choose any file
            GetContent.launch("*/*"); // TODO: find better location to begin with rather than the root directory of the filesystem (Maybe docs)
            Log.d("CRED FILE LOADING","Exiting the selectFile function!");
        }

    }
    // Drive link: https://drive.google.com/drive/folders/1KrQkxoJEx5A45Tdp6BbGhVqFQMYE5gKB?usp=sharing

    private String target_file;
    private File keyFile;
    private File certFile;
    private File CAcertFile;
    private MyLifeCycleObserver myLifeCycleObserver;
    private TextView CAdetailsTV;

    public CredentialsSelection() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // adding the lifecycle observer for selecting files from the filesystem
        myLifeCycleObserver = new MyLifeCycleObserver(requireActivity().getActivityResultRegistry());
        getLifecycle().addObserver(myLifeCycleObserver);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_credentials_selection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Based on whether the credentials are loaded or not we need to update the text views
        CAdetailsTV = (TextView) ( (ScrollView) view.findViewById(R.id.scroll_view_ca_cert) ).findViewById(R.id.ca_details_TV);
        if( InterNodeCrypto.CA_cert == null ){
            CAdetailsTV.setBackgroundColor(Color.RED);
            CAdetailsTV.setText("No CA certificate loaded!");
        }
        else{
            // TODO: Based on the certificate set the text to provide the details of the certificate
            CAdetailsTV.setText( InterNodeCrypto.getCertDetails(InterNodeCrypto.CA_cert) );
        }

        Button CACertButton = (Button) view.findViewById(R.id.ca_cert_button);
        CACertButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // When the button is clicked we need to pop-up a window for selection of the file to load
                        target_file = "CAcert";
                        myLifeCycleObserver.selectFile();
                    }
                }
        );

    }

}