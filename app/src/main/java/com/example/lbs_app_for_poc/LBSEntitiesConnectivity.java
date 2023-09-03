package com.example.lbs_app_for_poc;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class LBSEntitiesConnectivity implements Serializable {

    private static final String FILE_PATH = "LBSEntitiesConnectivity.ser";
    public static File absolute_path;

    // STANDARD VARIABLES
    public static final int ENTITIES_MANAGER_PORT = 55444; // Hardcoded the PORT number for the entities manager
    public static final int maxTransmissionCutOff = 300000; // 300 KBs per transmission

    // VARIABLES THAT ARE EITHER SAVED OR GIVEN BY THE USER
    public InetAddress ENTITIES_MANAGER_IP; // The IP of the manager server of the LBS entities and THUS THE HOST OF THE LBS ENTITIES
    public String MY_REAL_NODE_NAME; // The real name of the Node

    // VARIABLES TO FILL AFTER CONTACTING THE ENTITIES MANAGER SERVER
    transient public int P2P_RELAY_QUERY_PORT;
    transient public int P2P_RELAY_AVAILABILITY_PORT;
    transient public int SIGNING_FWD_SERVER_PORT;
    transient public boolean entitiesOnline;

    // certificates retrieved by the CA:

    // The main certificate to talk with the LBS entities with

    // The pseudonyms which will be used in rotation as the certificate for InterNodeCrypto

    // variables to control inter class communication fragment - thread classes
    transient public Activity activity;
    transient public FirstFragment fragment;

    // thread classes for INFO, CRDS messages
    transient public ConnectivityEstablish establish;
    transient public CredsDownloading credsDownloading;

    public LBSEntitiesConnectivity(Activity activity, FirstFragment fragment){
        establish = new ConnectivityEstablish();
        credsDownloading = new CredsDownloading();
        this.fragment = fragment;
        this.activity = activity;
        this.entitiesOnline = false;
        if(doesFileExist()){
            LBSEntitiesConnectivity saved = readObjectFile();
            this.ENTITIES_MANAGER_IP = saved.ENTITIES_MANAGER_IP;
            this.MY_REAL_NODE_NAME = saved.MY_REAL_NODE_NAME;
        }
        else {
            this.ENTITIES_MANAGER_IP = null; // TODO: Hardcode this value instead maybe?
            this.MY_REAL_NODE_NAME = null;
        }
    }

    public static LBSEntitiesConnectivity readObjectFile(){
        LBSEntitiesConnectivity obj = null;
        try {
            FileInputStream fis = new FileInputStream(FILE_PATH);
            ObjectInputStream ois = new ObjectInputStream(fis);
            obj = (LBSEntitiesConnectivity) ois.readObject();
            ois.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return obj;
    }
    public void writeObjectFile(){
        try {
            File fa = new File(absolute_path,FILE_PATH);
            FileOutputStream fos = new FileOutputStream(fa);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this);
            oos.close();
            Log.d("LBSEntitiesConnectivity"," Saved configuration to file!");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static boolean doesFileExist(){
        File file = new File(FILE_PATH);
        return file.exists();
    }

    // Thread for the INFO message exchange
    public class ConnectivityEstablish extends Thread implements Serializable{

        public ConnectivityEstablish(){
        }

        public void run(){

            Log.d("LBS entities connectivity","Entered the connectivity establishment thread!");

            // Set the status image view to loading
            LBSEntitiesConnectivity.this.activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            Drawable loadingDrawable = ContextCompat.getDrawable(activity, R.drawable.loading_as_drawable);
                            LBSEntitiesConnectivity.this.fragment.Remote_Services_Online_STATUS_IV.setImageDrawable(loadingDrawable);
                        }
                    }
            );

            Log.d("LBS entities connectivity","Button figure set to loading!");

            // contact the remote server
            try {

                Log.d("LBS entities connectivity","Entered the try statement!");

                // setup socket
                Socket my_socket;
                try {
                    my_socket = new Socket();
                    my_socket.connect(new InetSocketAddress(LBSEntitiesConnectivity.this.ENTITIES_MANAGER_IP, LBSEntitiesConnectivity.ENTITIES_MANAGER_PORT), 1000);
                }
                catch (Exception e){
                    Log.d("LBS entities connectivity","Couldn't establish connectivity with remote server!");
                    LBSEntitiesConnectivity.this.activity.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    Drawable failurDrawable = ContextCompat.getDrawable(activity, R.drawable.error);
                                    LBSEntitiesConnectivity.this.fragment.Remote_Services_Online_STATUS_IV.setImageDrawable(failurDrawable);
                                    Toast.makeText(LBSEntitiesConnectivity.this.fragment.getActivity(), "Remote server unresponsive!", Toast.LENGTH_SHORT).show();
                                }
                            }
                    );
                    return;
                }

                // set up streams
                DataInputStream dis;
                DataOutputStream dos;
                dis = new DataInputStream(my_socket.getInputStream());
                dos = new DataOutputStream(my_socket.getOutputStream());

                Log.d("LBS entities connectivity","Data streams initialized!");
                Log.d("LBS entities connectivity","Socket initialized!");
                my_socket.setSoTimeout(5000); // We wait at most 5 seconds for the remote side to respond
                Log.d("LBS entities connectivity","Socket timeout enforced!");

                // compose message
                byte[] optionField = "INFO".getBytes();
                ByteArrayOutputStream baosInfo = new ByteArrayOutputStream();
                baosInfo.write(optionField);
                byte[] infoMSG = baosInfo.toByteArray();
                // send message
                Log.d("LBS entities connectivity","the lenght of the mssages bout to send " + infoMSG.length);
                dos.write(infoMSG);

                Log.d("LBS entities connectivity","Wrote the message!");

                // wait for an answer
                ByteArrayOutputStream baosResponse = new ByteArrayOutputStream();
                byte[] buffer = new byte[100];
                int bytesRead;
                int totalBytesRead = 0;
                Log.d("LBS entities connectivity","Waiting for response!");

                while( (bytesRead = dis.read(buffer)) != -1 ) {
                    Log.d("LBS entities connectivity","Now read " + bytesRead + " bytes!");
                    baosResponse.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    if (bytesRead < buffer.length) {
                        break; // The buffer is not filled up that means we have reached the EOF
                    }
                    if (totalBytesRead > LBSEntitiesConnectivity.maxTransmissionCutOff) {
                        Log.d("LBS entities connectivity","Maximum transmission cutoff reached!");
                        break;
                    }
                }

                byte[] bytesResponse = baosResponse.toByteArray();
                Log.d("LBS entities connectivity","INFO message received from remote server!");

                // [ONLINE] | [P2P_RELAY_QUERY_PORT] | [P2P_RELAY_AVAILABILITY_PORT] | [SIGNING_FWD_SERVER_PORT]
                if( bytesResponse.length != 18 ){
                    Log.d("LBS entities connectivity","Error: Incorrect length of server response " + bytesResponse.length );
                    LBSEntitiesConnectivity.this.activity.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    Drawable errorDrawable = ContextCompat.getDrawable(activity, R.drawable.error);
                                    LBSEntitiesConnectivity.this.fragment.Remote_Services_Online_STATUS_IV.setImageDrawable(errorDrawable);
                                }
                            }
                    );
                    return;
                }

                String hexFormbytesResponse = "";
                for (byte b : bytesResponse) {
                    String hexValue = Integer.toHexString(b & 0xFF); // Convert to hex and handle sign extension
                    hexFormbytesResponse += "0x" + hexValue;
                }
                Log.d("LBS entities connectivity","Received response in hexes: " + hexFormbytesResponse);

                // OK so we have the expected number of bytes
                byte [] fieldOnline = new byte[6];
                byte [] fieldP2Pquery = new byte[4];
                byte [] fieldP2PAvailability = new byte[4];
                byte [] fieldSigning = new byte [4];
                System.arraycopy(bytesResponse,0,fieldOnline,0,6);
                System.arraycopy(bytesResponse,6,fieldP2Pquery,0,4);
                System.arraycopy(bytesResponse,10,fieldP2PAvailability,0,4);
                System.arraycopy(bytesResponse,14,fieldSigning,0,4);

                // Using the byte arrays to create the actual data types needed
                String online = new String(fieldOnline, StandardCharsets.UTF_8);
                Log.d("LBS entities connectivity","online string = " + online);
                int P2PQueryPort = byteArrayToInt(fieldP2Pquery);
                Log.d("LBS entities connectivity","P2PQueryPort = " + P2PQueryPort);
                int P2PAvailabilityPort = byteArrayToInt(fieldP2PAvailability);
                Log.d("LBS entities connectivity","P2PAvailabilityPort = " + P2PAvailabilityPort);
                int SigningPort = byteArrayToInt(fieldSigning);
                Log.d("LBS entities connectivity","SigningPort = " + SigningPort);

                if(!online.equals("ONLINE")){
                    Log.d("LBS entities connectivity","Error: Prefix ONLINE was not received!" + bytesResponse.length );
                    LBSEntitiesConnectivity.this.activity.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    Drawable errorDrawable = ContextCompat.getDrawable(activity, R.drawable.error);
                                    LBSEntitiesConnectivity.this.fragment.Remote_Services_Online_STATUS_IV.setImageDrawable(errorDrawable);
                                }
                            }
                    );
                    return;
                }

                LBSEntitiesConnectivity.this.P2P_RELAY_QUERY_PORT = P2PQueryPort;
                LBSEntitiesConnectivity.this.P2P_RELAY_AVAILABILITY_PORT = P2PAvailabilityPort;
                LBSEntitiesConnectivity.this.SIGNING_FWD_SERVER_PORT = SigningPort;

                Log.d("LBS entities connectivity","Success: INFO message received from Remote server!");
                LBSEntitiesConnectivity.this.activity.runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                Drawable successDrawable = ContextCompat.getDrawable(activity, R.drawable.tick);
                                LBSEntitiesConnectivity.this.fragment.Remote_Services_Online_STATUS_IV.setImageDrawable(successDrawable);
                            }
                        }
                );

                LBSEntitiesConnectivity.this.entitiesOnline = true;

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Now we are going to the credentials Thread and let it finish the process
            Thread credsThread = new Thread(LBSEntitiesConnectivity.this.credsDownloading);
            credsThread.start();

        }

        public int byteArrayToInt(byte[] byteArray) {
            ByteBuffer buffer = ByteBuffer.wrap(byteArray);
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            return buffer.getInt();
        }

    }

    // Thread for the CRDS message
    public class CredsDownloading extends Thread implements Serializable{

        public CredsDownloading(){
        }
        public void run()
        {
            Log.d("LBS entities connectivity","Entered the Credentials downloading thread!");

            // Set the status image view to loading
            LBSEntitiesConnectivity.this.activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            Drawable loadingDrawable = ContextCompat.getDrawable(activity, R.drawable.loading_as_drawable);
                            LBSEntitiesConnectivity.this.fragment.Credentials_Loaded_STATUS_IV.setImageDrawable(loadingDrawable);
                        }
                    }
            );

            Log.d("LBS entities connectivity","Set credentials status to loading!");

            try{
                Log.d("LBS entities connectivity","Entered the try statement for credentials!");
                // setup socket
                Socket my_socket;
                try {
                    my_socket = new Socket();
                    my_socket.connect(new InetSocketAddress(LBSEntitiesConnectivity.this.ENTITIES_MANAGER_IP, LBSEntitiesConnectivity.ENTITIES_MANAGER_PORT), 1000);
                }
                catch (Exception e){
                    Log.d("LBS entities connectivity","Couldn't establish connectivity with remote server for credentials downloading!");
                    LBSEntitiesConnectivity.this.activity.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    Drawable failurDrawable = ContextCompat.getDrawable(activity, R.drawable.error);
                                    LBSEntitiesConnectivity.this.fragment.Credentials_Loaded_STATUS_IV.setImageDrawable(failurDrawable);
                                    Toast.makeText(LBSEntitiesConnectivity.this.fragment.getActivity(), "Remote server unresponsive!", Toast.LENGTH_SHORT).show();
                                }
                            }
                    );
                    return;
                }

                // set up streams
                DataInputStream dis;
                DataOutputStream dos;
                dis = new DataInputStream(my_socket.getInputStream());
                dos = new DataOutputStream(my_socket.getOutputStream());

                Log.d("LBS entities connectivity","Creds Data streams initialized!");
                Log.d("LBS entities connectivity","Creds Socket initialized!");
                my_socket.setSoTimeout(5000); // We wait at most 5 seconds for the remote side to respond
                Log.d("LBS entities connectivity","Creds Socket timeout enforced!");

                // compose message
                byte [] optionField = "CRDS".getBytes();
                byte [] nameLengthByteArray = intToByteArray((int)(LBSEntitiesConnectivity.this.MY_REAL_NODE_NAME.length())); // signed int
                byte [] nameByteArray = LBSEntitiesConnectivity.this.MY_REAL_NODE_NAME.getBytes();

                ByteArrayOutputStream baosCrds = new ByteArrayOutputStream();
                baosCrds.write(optionField);
                baosCrds.write(nameLengthByteArray);
                baosCrds.write(nameByteArray);
                byte[] crdsMSG = baosCrds.toByteArray();
                // send message
                Log.d("LBS entities connectivity","the lenght of the mssages bout to send " + crdsMSG.length);
                dos.write(crdsMSG);
                Log.d("LBS entities connectivity","Wrote the CRDS message!");

                // Receive VALID or INVLD (only 5 bytes)
                byte [] validityByteArray = new byte[5];
                int bytesReadValidity = dis.read(validityByteArray);
                String validityCode = new String(validityByteArray, StandardCharsets.UTF_8);
                Log.d("LBS entities connectivity","Validity code: " + validityCode + " and length of it " + validityCode.length() );

                if( (bytesReadValidity != 5) || ( (!validityCode.equals("INVLD")) && (!validityCode.equals("VALID")) ) ){
                    LBSEntitiesConnectivity.this.activity.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    Drawable failurDrawable = ContextCompat.getDrawable(activity, R.drawable.error);
                                    LBSEntitiesConnectivity.this.fragment.Credentials_Loaded_STATUS_IV.setImageDrawable(failurDrawable);
                                    Toast.makeText(LBSEntitiesConnectivity.this.fragment.getActivity(), "Incorrect name validity check code!", Toast.LENGTH_SHORT).show();
                                }
                            }
                    );
                    return;
                }

                // EXIT IF INVLD otherwise it must be VALID from the above check
                if (validityCode.equals("INVLD")){
                    LBSEntitiesConnectivity.this.activity.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    Drawable failurDrawable = ContextCompat.getDrawable(activity, R.drawable.error);
                                    LBSEntitiesConnectivity.this.fragment.Credentials_Loaded_STATUS_IV.setImageDrawable(failurDrawable);
                                    Toast.makeText(LBSEntitiesConnectivity.this.fragment.getActivity(), "Name not registered!", Toast.LENGTH_SHORT).show();
                                }
                            }
                    );
                    return;
                }
                Log.d("LBS entities connectivity","Remote Server: Name is valid");

                // Receive the credentials one by one and check them.

                // 1) My own RSA private KEY: Yes one should create his own but here we only care about showing how the node uses its credentials
                // rather how it will generate them and register with the CA.
                // Saved in: InterNodeCrypto -> my_key
                byte [] nodePrivateKeyLength = new byte[4];
                int bytesReadPrivateKeyLength = dis.read(nodePrivateKeyLength);
                if(bytesReadPrivateKeyLength!=4){throw new RuntimeException();}
                int PrivateKeyLength = byteArrayToInt(nodePrivateKeyLength);
                Log.d("LBS entities connectivity","The length of the private key is: " + PrivateKeyLength);

                byte [] nodePrivateKeyByteArray = buffRead(PrivateKeyLength,dis);
                int bytesReadPrivateKey = nodePrivateKeyByteArray.length;
                if(bytesReadPrivateKey!=PrivateKeyLength){throw new RuntimeException();}
                PrivateKey privateKey = AmazingPrivateKeyReader.myPEMparser(nodePrivateKeyByteArray);
                InterNodeCrypto.my_key = privateKey;
                Log.d("LBS entities connectivity","Private key bytes " + bytesReadPrivateKey + " have been received fro the remote server!");

                /*byte[] EncodedForDebug = privateKey.getEncoded();
                for (byte b : EncodedForDebug) {
                    Log.d("Debug Private from Remote: " , String.format("%02X ", b));
                }*/

                // 2) My own RSA certificate signed by the CA which I will be using for talking with the LBS entities
                byte [] nodeCertificateLength = new byte[4];
                int bytesReadCertfificateLength = dis.read(nodeCertificateLength);
                if(bytesReadCertfificateLength!=4){throw new RuntimeException();}
                int CertificateLength = byteArrayToInt(nodeCertificateLength);
                Log.d("LBS entities connectivity","The length of the node's certificate is: " + CertificateLength);

                byte [] nodeCertificateByteArray = buffRead(CertificateLength,dis);
                int bytesReadCertificate = nodeCertificateByteArray.length;
                if(bytesReadCertificate!=CertificateLength){throw new RuntimeException();}
                X509Certificate nodeCertificate = InterNodeCrypto.CertFromByteArray(nodeCertificateByteArray);
                InterNodeCrypto.my_cert = nodeCertificate;
                String details = InterNodeCrypto.getCertDetails(InterNodeCrypto.my_cert);
                Log.d("LBS entities connectivity","Certificate:\n" + details);

                // 3) CA certificate
                byte [] CACertificateLengthBytes = new byte[4];
                int bytesReadCACertificateLength = dis.read(CACertificateLengthBytes);
                if(bytesReadCACertificateLength!=4){throw new RuntimeException();}
                int CACertificateLength = byteArrayToInt(CACertificateLengthBytes);
                Log.d("LBS entities connectivity","The length of the CA certificate is: " + CACertificateLength);

                byte [] CACertificateByteArray = buffRead(CACertificateLength,dis);
                int bytesReadCACertificate = CACertificateByteArray.length;
                if(bytesReadCACertificate!=CACertificateLength){throw new RuntimeException();}
                X509Certificate CAcertificate = InterNodeCrypto.CertFromByteArray(CACertificateByteArray);
                InterNodeCrypto.CA_cert = CAcertificate;
                String CAdetails = InterNodeCrypto.getCertDetails(CAcertificate);
                Log.d("LBS entities connectivity","CA Certificate:\n" + CAdetails);

                // 4) Pseudonymous certificates and their private keys
                if(InterNodeCrypto.pseudonymous_certificates == null) {
                    InterNodeCrypto.pseudonymous_certificates = new ArrayList<X509Certificate>();
                }
                else{
                    InterNodeCrypto.pseudonymous_certificates.clear();
                }
                if(InterNodeCrypto.pseudonymous_privates == null){
                    InterNodeCrypto.pseudonymous_privates = new ArrayList<PrivateKey>();
                }
                else {
                    InterNodeCrypto.pseudonymous_privates.clear();
                }

                for(int i=1;i<InterNodeCrypto.MAX_NUMBER_OF_PSEUDONYMOUS_CERTS;i++){

                    // [ NODE PCERT LENGTH]
                    byte [] PcertLengthByteArray = new byte[4];
                    int bytesReadPcertLength = dis.read(PcertLengthByteArray);
                    if(bytesReadPcertLength!=4){throw new RuntimeException();}
                    int PcertLength = byteArrayToInt(PcertLengthByteArray);
                    Log.d("LBS entities connectivity","The length of the incoming PCERT is: " + PcertLength);

                    // [ NODE PCERT ]
                    byte [] nodePcertByteArray = buffRead(PcertLength,dis);
                    int bytesReadNodePcert = nodePcertByteArray.length;
                    if(bytesReadNodePcert!=PcertLength){throw new RuntimeException();}
                    X509Certificate Pcert = InterNodeCrypto.CertFromByteArray(nodePcertByteArray);
                    String PcertDetails = InterNodeCrypto.getCertDetails(Pcert);
                    Log.d("LBS entities connectivity","Pseudonymous certificate " + i + " is: " + PcertDetails);

                    // [ NODE PPKEY LENGTH ]
                    byte [] PpkeyLengthByteArray = new byte[4];
                    int bytesReadPpkeyLength = dis.read(PpkeyLengthByteArray);
                    if(bytesReadPpkeyLength!=4){throw new RuntimeException();}
                    int PpkeyLength = byteArrayToInt(PpkeyLengthByteArray);

                    // [ NODE PPKEY ]
                    byte [] PpkeyByteArray = buffRead(PpkeyLength,dis);
                    int bytesReadPpkey = PpkeyByteArray.length;
                    if(bytesReadPpkey!=PpkeyLength){throw new RuntimeException();}
                    PrivateKey Ppkey = AmazingPrivateKeyReader.myPEMparser(PpkeyByteArray);
                    // Saving pseudo credentials
                    InterNodeCrypto.pseudonymous_certificates.add(Pcert);
                    InterNodeCrypto.pseudonymous_privates.add(Ppkey);
                }

                // DONE: implement checks on all the credentials received to see if they are valid if not Toast
                if( !InterNodeCrypto.checkMyCreds() ){
                    Log.d("LBS entities connectivity","Invalid main credentials!");
                    Drawable failureDrawable = ContextCompat.getDrawable(activity, R.drawable.error);
                    LBSEntitiesConnectivity.this.fragment.Credentials_Loaded_STATUS_IV.setImageDrawable(failureDrawable);
                    Toast.makeText(LBSEntitiesConnectivity.this.fragment.getActivity(), "Main certificates invalid", Toast.LENGTH_SHORT).show();
                    return;
                }
                for(int i=0;i<InterNodeCrypto.pseudonymous_privates.size();i++){
                    if( !InterNodeCrypto.checkCreds( InterNodeCrypto.CA_cert, InterNodeCrypto.pseudonymous_certificates.get(i), InterNodeCrypto.pseudonymous_privates.get(i) ) ){
                        Log.d("LBS entities connectivity","Pseudo credentials #" + i + " not valid!");
                        Drawable failureDrawable = ContextCompat.getDrawable(activity, R.drawable.error);
                        LBSEntitiesConnectivity.this.fragment.Credentials_Loaded_STATUS_IV.setImageDrawable(failureDrawable);
                        Toast.makeText(LBSEntitiesConnectivity.this.fragment.getActivity(), "Pseudo Credentials #" + i + " invalid!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                Log.d("LBS entities connectivity","SUCCESS: All credentials received and validity checked!");

                // If we reached this point it means we have received all the credentials
                LBSEntitiesConnectivity.this.activity.runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                Drawable successDrawable = ContextCompat.getDrawable(activity, R.drawable.tick);
                                LBSEntitiesConnectivity.this.fragment.Credentials_Loaded_STATUS_IV.setImageDrawable(successDrawable);
                                LBSEntitiesConnectivity.this.fragment.search_initiator_button.setClickable(true);
                                LBSEntitiesConnectivity.this.fragment.search_initiator_button.setEnabled(true);
                            }
                        }
                );

            }catch (Exception e) {
                e.printStackTrace();
                tryagain();
            }

        }

        void tryagain(){
            LBSEntitiesConnectivity.this.activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(LBSEntitiesConnectivity.this.fragment.getActivity(), "Error: Try again!", Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        }

        public byte[] intToByteArray(int number) {
            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
            buffer.putInt(number);
            return buffer.array();
        }

        public int byteArrayToInt(byte[] byteArray) {
            ByteBuffer buffer = ByteBuffer.wrap(byteArray);
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            return buffer.getInt();
        }

        byte [] buffRead(int numOfBytes, DataInputStream dis) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int totalBytesRead = 0;
            while(true){
                int BytesLeft2Read = numOfBytes - totalBytesRead;
                int bufferSize = Math.min(100,BytesLeft2Read); // We read at most 100 bytes every time
                if (bufferSize != 100) {
                    Log.d("BuffRead", "Size now is " + bufferSize + " and BytesLeft2Read = " + BytesLeft2Read + " out of " + numOfBytes );
                }
                byte [] buffer = new byte[bufferSize];
                int tempBytesRead = dis.read(buffer);
                if(tempBytesRead!=bufferSize) {
                    if( tempBytesRead < bufferSize ){
                        // If they are less maybe we are still to receive them
                        Log.d("BuffRed","WARNING: We read less than what we expected!");
                    }
                    else {
                        throw new RuntimeException();
                    }
                }
                totalBytesRead += tempBytesRead;
                baos.write(buffer,0,tempBytesRead);
                if(totalBytesRead == numOfBytes){
                    break;
                }
            }
            byte [] readByteArray = baos.toByteArray();
            return readByteArray;
        }

    }

}
