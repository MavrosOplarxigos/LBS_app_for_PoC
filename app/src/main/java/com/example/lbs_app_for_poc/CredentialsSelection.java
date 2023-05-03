package com.example.lbs_app_for_poc;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResultRegistry;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CredentialsSelection#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CredentialsSelection extends Fragment {

    // Inner class for selecting files
    class MyLifeCycleObserver implements DefaultLifecycleObserver{
        public final ActivityResultRegistry mRegistry;
        public ActivityResultLauncher<String> mGetContent;
        public MyLifeCycleObserver(@NonNull ActivityResultRegistry registry) {
            this.mRegistry = registry;
        }
        public void onCreate(@NonNull LifecycleOwner owner){
            mGetContent = mRegistry.register("key",owner,new ActivityResultContracts.GetContent(),
                    new ActivityResultCallback<Uri>(){
                        @Override
                        public void onActivityResult(Uri uri){
                            // Handle the returned Uri
                            Log.d("CRED FILE LOADING","Now handling the selected URI");
                            tempFile = null;
                            tempFile = new File(uri.getPath());
                            if(tempFile == null){
                                Log.d("CRED FILE LOADING","The Uri points to a null file!");
                            }
                        }
                    });
        }
        public void selectFile(){
            // Open this activity to select the file
            mGetContent.launch("/*"); // TODO: find better location to begin with rather than the root directory of the filesystem (Maybe docs)
            Log.d("CRED FILE LOADING","Exciting the selectFile function!");
        }

    }

    private File tempFile;
    private File keyFile;
    private File certFile;
    private File CAcertFile;
    private MyLifeCycleObserver myLifeCycleObserver;

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
        TextView CAdetailsTV = (TextView) ( (ScrollView) view.findViewById(R.id.scroll_view_ca_cert) ).findViewById(R.id.ca_details_TV);
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
                        myLifeCycleObserver.selectFile();
                        // After Loading the file we need to save it as temporary file to use as an input in the SaveCertificates function
                        // Now from the temporary file we just use the path to construct the certFile
                        certFile = new File(tempFile.getPath());
                        CAdetailsTV.setText( InterNodeCrypto.getCertDetails(certFile) );
                    }
                }
        );

    }

}