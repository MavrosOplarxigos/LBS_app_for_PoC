package com.example.lbs_app_for_poc;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Environment;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import android.Manifest;
import android.view.WindowManager;

import com.example.lbs_app_for_poc.databinding.ActivityMainBinding;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 123;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 122;
    private static final int MY_PERMISSIONS_REQUEST_MANAGE_EXTERNAL_STORAGE = 121;
    public static final Object lockForCredsCheck = new Object();
    public static boolean flagForCredsCheck;
    public static boolean hasManageAllFilesPermissionActivityLaunched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // To keep screen on for experiments!

        flagForCredsCheck = false;
        hasManageAllFilesPermissionActivityLaunched = false;

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        getSupportActionBar().hide();

        // Trying to allow access to the FileSystem specifically the EXTERNAL_STORAGE
        /*if (ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        }*/
        /*if (ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }*/
        //if (ContextCompat.checkSelfPermission(this,Manifest.permission.MANAGE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.MANAGE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_MANAGE_EXTERNAL_STORAGE);
        //}

        // We communicate the Files directory to the crypto class so that the credentials can be loaded from their respective paths
        InterNodeCrypto.absolute_path = getFilesDir();
        LBSEntitiesConnectivity.absolute_path = getFilesDir();
        Log.d("MAIN_ACTIVITY_INIT","The file directory is "+InterNodeCrypto.absolute_path);

        if (!Environment.isExternalStorageManager()){

            Intent getpermission = new Intent();
            getpermission.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            // getpermission.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            ActivityResultLauncher<Intent> allFilesPermissionARL = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            setFlag();
                        }
                    }
            );

            allFilesPermissionARL.launch(getpermission);

            // Working: startActivity(getpermission);
            // startActivityForResult()

        }else{
            Log.d("INITIAL_CREDENTIAL_CHECK","We already have the environment external storage manager flag so we set the flag here!");
            setFlag(); // The credential check should be carried out immediately this way
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("FILESYSTEM ACCESS","permission to READ files GRANTED!");
            } else {
                Log.d("FILESYSTEM ACCESS","permission to READ files DENIED!");
            }
        }
        else if (requestCode == MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("FILESYSTEM ACCESS","permission to WRITE files GRANTED!");
            } else {
                Log.d("FILESYSTEM ACCESS","permission to WRITE files DENIED!");
            }
        }
        else if (requestCode == MY_PERMISSIONS_REQUEST_MANAGE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("FILESYSTEM ACCESS","permission to MANAGE files GRANTED from PackageManager check!");
            } else {
                Log.d("FILESYSTEM ACCESS", "permission to MANAGE files DENIED from PackageManger check!");
            }

        }
    }

    public void setFlag() {
        synchronized (lockForCredsCheck) {
            flagForCredsCheck = true;
            lockForCredsCheck.notify(); // Notify any waiting threads that the flag has changed
        }
    }

    public static void waitForCredsFlag() throws InterruptedException {
        Log.d("INITIAL_CREDENTIAL_CHECK","Entered waitForCredsFlag.");
        synchronized (lockForCredsCheck) {
            Log.d("INITIAL_CREDENTIAL_CHECK","Synchonized for lockForCredsCheck in waitForCredsFlag.");
            while (!flagForCredsCheck) {
                lockForCredsCheck.wait(); // Wait until the flag becomes true
            }
            Log.d("INITIAL_CREDENTIAL_CHECK","WaitForCredsFlag waiting loop is done!");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}