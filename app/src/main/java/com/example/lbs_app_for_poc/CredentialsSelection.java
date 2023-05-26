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
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
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
                                        Log.d("CA_CRED","Now will try to give the path to a FileInputStream instead to see if we can read it!");
                                        try {
                                            FileInputStream CAcertFIS = new FileInputStream(pathFromFileChooser);
                                            Log.d("CA_CRED","Successfully loaded path on FIS! Use FIS to read the file from path instead!");
                                        } catch (FileNotFoundException e) {
                                            Log.d("CA_CRED","Failing to open it with FIS directly as well!");
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    /*allowAccess1stAttempt();

                                    if (ContextCompat.checkSelfPermission(getActivity(), android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                                        ActivityCompat.requestPermissions(getActivity(),
                                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                                MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                                    }*/

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
                                    CAdetailsTV.setBackgroundColor(Color.GREEN);
                                }
                                catch (Exception e){
                                    CAdetailsTV.setText( "Failed to use the selected certificate file!" );
                                    CAdetailsTV.setBackgroundColor(Color.RED);
                                    Log.d("CA_CRED","Failed to get certificate details!");
                                    e.printStackTrace();
                                }
                                Log.d("CRED FILE LOADING","Saved to CAcertFile!");
                                return;
                            }
                            if(Objects.equals(target_file, "MYcert")){
                                Log.d("MY_CRED","We are trying to load MYcert! uri = " + uri.getPath() );
                                try {
                                    String pathFromFileChooser = AnotherFileChooser.getPath(getContext(),uri);
                                    Log.d("MY_CRED","The resolved path from the AnotherFileChooser class is "+pathFromFileChooser);
                                    certFile = new File(pathFromFileChooser);
                                    if(certFile==null){
                                        Log.d("MY_CRED","The certFile from pathFromFileChooser is null");
                                        Log.d("MY_CRED","Now will try to give the path to a FileInputStream instead to see if we can read it!");
                                        try {
                                            FileInputStream MYcertFIS = new FileInputStream(pathFromFileChooser);
                                            Log.d("MY_CRED","Successfully loaded path on FIS! Use FIS to read the file from path instead!");
                                        } catch (FileNotFoundException e) {
                                            Log.d("MY_CRED","Failing to open it with FIS directly as well!");
                                            throw new RuntimeException(e);
                                        }
                                    }
                                }
                                catch (RuntimeException e){
                                    Log.d("CRED FILE LOADING","Couldn't get a path from the URI selected!");
                                    e.printStackTrace();
                                    return;
                                }
                                Log.d("MY_CRED","The file is loaded! " + certFile.toString() );
                                try {
                                    MYdetailsTV.setText( InterNodeCrypto.getCertDetails(certFile) );
                                    MYdetailsTV.setBackgroundColor(Color.GREEN);
                                }
                                catch (Exception e){
                                    Log.d("MY_CRED","Failed to get certificate details!");
                                    MYdetailsTV.setText( "Could not use the selected certificate file!" );
                                    MYdetailsTV.setBackgroundColor(Color.RED);
                                    e.printStackTrace();
                                }
                                Log.d("CRED FILE LOADING","Saved to certFile!");
                                return;
                            }
                            if(Objects.equals(target_file, "MYkey")){
                                Log.d("MY_KEY","We are trying to load MYkey! uri = " + uri.getPath() );
                                try {
                                    String pathFromFileChooser = AnotherFileChooser.getPath(getContext(),uri);
                                    Log.d("MY_KEY","The resolved path from the AnotherFileChooser class is " + pathFromFileChooser);
                                    keyFile = new File(pathFromFileChooser);
                                    if(keyFile==null){
                                        Log.d("MY_KEY","The keyFile from pathFromFileChooser is null");
                                        Log.d("MY_KEY","Now will try to give the path to a FileInputStream instead to see if we can read it!");
                                        try {
                                            FileInputStream MYkeyFIS = new FileInputStream(pathFromFileChooser);
                                            Log.d("MY_KEY","Successfully loaded path on FIS! Use FIS to read the file from path instead!");
                                        } catch (FileNotFoundException e) {
                                            Log.d("MY_KEY","Failing to open it with FIS directly as well!");
                                            throw new RuntimeException(e);
                                        }
                                    }
                                }
                                catch (RuntimeException e){
                                    Log.d("CRED FILE LOADING","Couldn't get a path from the URI selected!");
                                    e.printStackTrace();
                                    return;
                                }
                                Log.d("MY_KEY","The file is loaded! " +  keyFile.toString() );
                                try {
                                    MYkeydetailsTV.setText( "Selected KEY loaded!" );
                                    MYkeydetailsTV.setBackgroundColor(Color.GREEN);
                                }
                                catch (Exception e){
                                    MYkeydetailsTV.setText( "Selected KEY could NOT be loaded!" );
                                    MYkeydetailsTV.setBackgroundColor(Color.RED);
                                    Log.d("MY_KEY","Failed to get Key details!");
                                    e.printStackTrace();
                                }
                                Log.d("CRED FILE LOADING","Saved to keyFile!");
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
    private TextView MYdetailsTV;
    private TextView MYkeydetailsTV;

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
            CAdetailsTV.setBackgroundColor(Color.GREEN);
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

        MYdetailsTV = (TextView) ( (ScrollView) view.findViewById(R.id.scroll_view_my_cert) ).findViewById(R.id.my_details_TV);
        if( InterNodeCrypto.my_cert == null ){
            MYdetailsTV.setBackgroundColor(Color.RED);
            MYdetailsTV.setText("No own certificate loaded!");
        }
        else{
            MYdetailsTV.setBackgroundColor(Color.GREEN);
            MYdetailsTV.setText( InterNodeCrypto.getCertDetails(InterNodeCrypto.my_cert) );
        }

        Button MYCertButton = (Button) view.findViewById(R.id.my_cert_button);
        MYCertButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // When the button is clicked we need to pop-up a window for selection of the file to load
                        target_file = "MYcert";
                        myLifeCycleObserver.selectFile();
                    }
                }
        );

        MYkeydetailsTV = (TextView) ( (ScrollView) view.findViewById(R.id.scroll_view_my_key) ).findViewById(R.id.my_key_details_TV);
        if( InterNodeCrypto.my_key == null ){
            MYkeydetailsTV.setBackgroundColor(Color.RED);
            MYkeydetailsTV.setText("No key loaded!");
        }
        else{
            MYkeydetailsTV.setBackgroundColor(Color.GREEN);
            // TODO: Add details for key (maybe the name on the key)
            MYkeydetailsTV.setText( "LOADED" );
        }

        Button MYKeyButton = (Button) view.findViewById(R.id.my_key_button);
        MYKeyButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // When the button is clicked we need to pop-up a window for selection of the file to load
                        target_file = "MYkey";
                        myLifeCycleObserver.selectFile();
                    }
                }
        );

        Button CheckAndSaveButton = (Button) view.findViewById(R.id.save_configured_credentials);
        CheckAndSaveButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // Get the certificates and key from the files
                        X509Certificate cand_CA_cert = null;
                        X509Certificate cand_MY_cert = null;
                        ECPrivateKey cand_MY_key = null;
                        try {
                            cand_CA_cert = InterNodeCrypto.CertFromFile(CAcertFile);
                            cand_MY_cert = InterNodeCrypto.CertFromFile(certFile);
                            cand_MY_key = InterNodeCrypto.KeyFromFile(keyFile);
                        } catch (IOException e) {
                            Log.d("CREDS CHECK","There was an error retrieving certificates and/or the key from the selected files!");
                            throw new RuntimeException(e);
                        }
                        // Check the credentials
                        if( InterNodeCrypto.checkCreds(cand_CA_cert,cand_MY_cert,cand_MY_key) ){
                            Log.d("CREDS SAVE","The credentials check out! Now saving the files!");
                            InterNodeCrypto.SaveCertificates(CAcertFile,certFile,keyFile);
                            Log.d("CREDS SAVE","The NEW credentials must have been saved!");
                            try {
                                InterNodeCrypto.LoadCertificates();
                                Log.d("CREDS LOADING","Credentials now successfully loaded from the new files!");
                                Toast.makeText(getContext(),"Success! The selected credentials will be used from now on!", Toast.LENGTH_LONG).show();
                            } catch (IOException e) {
                                Log.d("CREDS LOADING","Could not load the credentials from the newly saved files!");
                                throw new RuntimeException(e);
                            }
                        }
                        else{
                            Toast.makeText(getContext(), "The selected credentials are not valid with each other!", Toast.LENGTH_LONG).show();
                        }

                    }
                }
        );

    }

}