package com.example.lbs_app_for_poc;

import static java.lang.Math.min;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;

import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.lbs_app_for_poc.databinding.FragmentSecondBinding;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.data.Feature;
import com.google.maps.android.data.Layer;
import com.google.maps.android.data.geojson.GeoJsonFeature;
import com.google.maps.android.data.geojson.GeoJsonLayer;
import com.google.maps.android.data.geojson.GeoJsonPoint;
import com.google.maps.android.geometry.Point;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.HttpsURLConnection;
import java.util.Random;

public class SearchingNodeFragment extends Fragment implements OnMapReadyCallback {

    public static final int MAX_PEER_RESPONSES = 10;
    private FragmentSecondBinding binding;
    private MapView mMapView;
    private GoogleMap mMap=null;
    private static final String MAPVIEW_BUNDLE_KEY = "MapViewBundleKey";
    public static Button search_button;
    public static Button LogsButton;

    public static TextView experimentsTV;
    public static TextView experimentStatusTV;
    public static TextView answerProbabilityTV;
    public static TextView RetryOnMissTV;
    public static TextView hitVSmissTV;
    public static TextView totalRequestsTV;

    ArrayList <TextView> experimentViews;

    private EditText search_keyword_input;
    public GeoJsonLayer results_layer = null;
    private MapSearchItem msi; // The fundamental object that defines the search as soon as the search button is pressed

    // updated by the PEER DISCOVERY Thread (aka QUERY Thread on P2P side) which runs in the background
    // if for some reason all the peers we were given are inactive we query the signing server directly
    public static ArrayList<ServingPeer> ServingPeerArrayList;
    public static final Lock mutextServingPeerArrayList = new ReentrantLock();
    public static LBSEntitiesConnectivity lbsEC;

    // The bytes arrays for the responses in case of peer querying (normal case)
    public static byte [][] peerResponseDecJson = new byte[MAX_PEER_RESPONSES][];
    public static Lock [] mutexPeerResponseDecJson = new ReentrantLock[MAX_PEER_RESPONSES];
    public static CountDownLatch peer_thread_entered_counter; // To await for responses (or timeouts) from all peers that were queried.
    public static CountDownLatch peer_rediscovery_thread_entered; // To await at least one query completion from the peer discovery thread
    public static CountDownLatch experiment_query_completed;

    // EXPERIMENT CONFIGURATION VARIABLES
    public static boolean SHOULD_PEER_REASK = true;
    public static boolean eThread_has_started = false;
    public static int ANSWER_PROBABILITY_PCENT = 100;
    public static int NUMBER_OF_EXPERIMENTS = 0;
    public static int QUERIES_PER_EXPERIMENT = 10;

    public static Lock HIT_MISS_COUNTERS_LOCK = new ReentrantLock();
    public static int PEER_HITS = 0;
    public static int PEER_MISSES = 0;
    public static boolean EXPERIMENT_IS_RUNNING = false;
    public static boolean EXPERIMENT_READINESS_ONLY_WHEN_PEERS_AVAILABLE = true;
    public static boolean HAVE_HAD_0_PEERS_AT_SOME_POINT = false;

    public static class ServingPeer{
        // public String DistinguishedName; I don't know the name. I expect only the IP and Port to be of a SOME peer.
        public InetAddress PeerIP;
        public int PeerPort;
        // public boolean faulty; // it either doesn't respond or it returns gibberish
        public ServingPeer(InetAddress IP, int Port){
            // DistinguishedName = DN; I don't know the name. I expect only the IP and Port to be of a SOME peer.
            PeerIP = IP;
            PeerPort = Port;
            // faulty = false;
        }
    }

    // We call the following function IFF AND WHEN all of the peers in the ServingPeerArrayList are non-responsive
    public static void P2PThreadExplicitRestart(){
        P2PRelayServerInteractions.qThread.explicit_search_request = true; // setting this true so the previous instance will kill itself
        P2PRelayServerInteractions.qThread = new P2PRelayServerInteractions.PeerDiscoveryThread(lbsEC,true);
        P2PRelayServerInteractions.qThread.start();
    }

    void unset_experiment_screen(){
        getActivity().runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {

                        // mMapView.setActivated(true); // Check if this causes for the msi object to be not created correctly
                        search_button.setVisibility(View.VISIBLE);
                        search_button.setClickable(true);
                        search_button.setActivated(true);

                        LogsButton.setVisibility(View.VISIBLE);
                        LogsButton.setClickable(true);
                        LogsButton.setActivated(true);

                        search_keyword_input.setVisibility(View.VISIBLE);
                        search_keyword_input.setActivated(true);
                        search_keyword_input.setClickable(true);

                        for(int i=0;i<experimentViews.size();i++){
                            TextView curr = experimentViews.get(i);
                            curr.setVisibility(View.INVISIBLE);
                            curr.setEnabled(false);
                            curr.setBackgroundColor(Color.TRANSPARENT);
                        }

                    }
                }
        );
    }

    void set_experiment_screen(){
    getActivity().runOnUiThread(
    new Runnable() {
    @Override
    public void run() {

        search_button.setVisibility(View.INVISIBLE);
        search_button.setClickable(false);
        search_button.setActivated(false);

        LogsButton.setVisibility(View.INVISIBLE);
        LogsButton.setClickable(false);
        LogsButton.setActivated(false);

        search_keyword_input.setVisibility(View.INVISIBLE);
        search_keyword_input.setActivated(false);
        search_keyword_input.setClickable(false);

        for(int i=0;i<experimentViews.size();i++){
            TextView curr = experimentViews.get(i);
            curr.setVisibility(View.VISIBLE);
            curr.bringToFront();
            curr.setEnabled(true);
            curr.setBackgroundColor(Color.WHITE);
        }

    }
    }
    );
    }

    public static String interpolateColorToHex(double fraction) {
        fraction = Math.max(0, Math.min(1, fraction)); // Ensure fraction is in the [0, 1] range

        int red = (int) (255 * (1 - fraction));
        int green = (int) (255 * fraction);
        int blue = 0; // Blue component is set to 0

        int color = Color.rgb(red, green, blue);
        String hexColor = String.format("#%06X", (0xFFFFFF & color));

        return hexColor;
    }

    void update_experiment_tv(int current,int total){
    getActivity().runOnUiThread(
    new Runnable() {
        @Override
        public void run() {
            String new_text = "EXPERIMENT: " + current + "/" + total;
            int color = Color.parseColor(interpolateColorToHex((double)(current)/(double)(total)));
            experimentsTV.setBackgroundColor(color);
            experimentsTV.setText(new_text);
        }
    }
    );
    }

    void update_experiment_status_tv(String status){
        getActivity().runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        String new_text = "STATUS: " + status;
                        experimentStatusTV.setText(new_text);
                        if(status.equals("LOADING")){
                            experimentStatusTV.setBackgroundColor(Color.YELLOW);
                        }
                        if(status.equals("RUNNING")){
                            experimentStatusTV.setBackgroundColor(Color.CYAN);
                        }
                        if(status.equals("FINISHED")){
                            experimentStatusTV.setBackgroundColor(Color.GREEN);
                        }
                    }
                }
        );
    }

    void update_answer_probability_tv(){
        getActivity().runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        answerProbabilityTV.setText("ANSWER PROBABILITY: " + ANSWER_PROBABILITY_PCENT + "%");
                        int color = Color.parseColor(interpolateColorToHex(((double)(ANSWER_PROBABILITY_PCENT*100.0)/(double)(100.0))));
                        answerProbabilityTV.setBackgroundColor(color);
                    }
                }
        );
    }

    void update_peer_miss_second_try_tv(){
        getActivity().runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        if(SHOULD_PEER_REASK){
                            RetryOnMissTV.setText("RETRY ON MISS: YES");
                            RetryOnMissTV.setBackgroundColor(Color.GREEN);
                        }
                        else {
                            RetryOnMissTV.setText("RETRY ON MISS: NO");
                        }
                    }
                }
        );
    }

    void update_total_requests_tv(int request){

        getActivity().runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        totalRequestsTV.setText("TOTAL REQUESTS: " + request + "/" + QUERIES_PER_EXPERIMENT);
                        totalRequestsTV.setBackgroundColor(Color.parseColor(interpolateColorToHex((double)(request)/(double)(QUERIES_PER_EXPERIMENT))));
                    }
                }
        );


    }

    void update_hit_ratio_tv() {

        getActivity().runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        hitVSmissTV.setText("HITS/MISS: " + PEER_HITS + "/" + PEER_MISSES);
                        if (PEER_HITS + PEER_MISSES != 0) {
                            hitVSmissTV.setBackgroundColor(Color.parseColor(interpolateColorToHex((double) (PEER_HITS) / (double) (PEER_HITS + PEER_MISSES))));
                        }
                    }
                }
        );

    }

    void reset_experiment_counters(){
        HIT_MISS_COUNTERS_LOCK.lock();
        PEER_HITS = 0;
        PEER_MISSES = 0;
        HIT_MISS_COUNTERS_LOCK.unlock();
    }

    // Thread to run everything related to the experiment
    public ExperimentThread eThread = new ExperimentThread();
    public class ExperimentThread extends Thread{
        public ExperimentThread(){}
        public void run(){
            Log.d("ExperimentThread","Experiment Thread Entered!");
            try{

                // make sure that we get at least some serving peers before telling to the remote server that we are ready for the experiment
                while(EXPERIMENT_READINESS_ONLY_WHEN_PEERS_AVAILABLE) {
                    boolean got_peers = false;
                    mutextServingPeerArrayList.lock();
                    got_peers = (ServingPeerArrayList.size()!=0);
                    mutextServingPeerArrayList.unlock();
                    if(got_peers){
                        break;
                    }
                    Thread.sleep(1000);
                }

                // make sure that the NTP task is complete
                Log.d("ExperimentThread","Waiting on NTP task to complete!");
                while(!FirstFragment.NTP_TASK_COMPLETE){
                    Thread.sleep(1000);
                }

                Socket my_socket;
                try {
                    my_socket = new Socket();
                    my_socket.connect(new InetSocketAddress(lbsEC.ENTITIES_MANAGER_IP, lbsEC.ENTITIES_MANAGER_PORT), 0);
                }
                catch (Exception e){
                    Log.d("ExperimentThread","Couldn't establish connectivity with remote server for experiment readiness declaration!");
                    return;
                }
                DataInputStream dis;
                DataOutputStream dos;
                dis = new DataInputStream(my_socket.getInputStream());
                dos = new DataOutputStream(my_socket.getOutputStream());

                // Tell the experiment server that you are ready
                byte [] optionField = "EXPR".getBytes();
                dos.write(optionField);
                Log.d("RAFAIL OPTION FIELD FOR EXPERIMENT","Sent!");
                // byte [] nameLengthByteArray = TCPhelpers.intToByteArray((int)(lbsEC.MY_REAL_NODE_NAME.length())); // signed int
                // byte [] nameByteArray = lbsEC.MY_REAL_NODE_NAME.getBytes();
                // ByteArrayOutputStream baosExpr = new ByteArrayOutputStream();
                // baosExpr.write(optionField);
                // baosExpr.write(nameLengthByteArray);
                // baosExpr.write(nameByteArray);
                // byte[] exprMSG = baosExpr.toByteArray();

                // Read number of experiments
                byte [] number_of_experiments_bytes = TCPhelpers.buffRead(4,dis);
                int number_of_experiments = TCPhelpers.byteArrayToIntLittleEndian(number_of_experiments_bytes);
                Log.d("ExperimentThread","The remote server has declared " + number_of_experiments + " experiments to run!");
                set_experiment_screen();
                EXPERIMENT_IS_RUNNING = true;

                for(int i=0;i<number_of_experiments;i++) {

                    byte [] experiment_counter_bytes = TCPhelpers.buffRead(4,dis);
                    int experiment_counter_from_remote = TCPhelpers.byteArrayToIntLittleEndian(experiment_counter_bytes);
                    Log.d("ExperimentThread","The experiment number that we have received from the remote server is: " + experiment_counter_from_remote);

                    if(i+1 != experiment_counter_from_remote){
                        Log.d("ExperimentThread","Error: We have found indeed an error on the experiment index!");
                    }
                    else{
                        Log.d("ExperimentThread","The indexes of the experiment are fine for experiment index: " + i+1);
                    }

                    update_experiment_tv(i+1,number_of_experiments);
                    update_experiment_status_tv("LOADING");

                    // receive the answering probability for this experiment
                    byte [] answer_prob_byte = TCPhelpers.buffRead(4,dis);
                    Log.d("ExperimentThread","New answer probability is " + TCPhelpers.byteArrayToIntLittleEndian(answer_prob_byte) + "%");

                    // receive choice of whether we should re-ask in a case where we don't get an answer
                    byte [] reask_bytes = TCPhelpers.buffRead(3,dis);

                    // queries per experiment
                    byte [] queries_per_experiment_bytes = TCPhelpers.buffRead(4,dis);

                    // now we should wait for the time to start the experiment
                    byte[] start_signal = TCPhelpers.buffRead(5, dis);
                    String start_signal_string = new String(start_signal, StandardCharsets.UTF_8);
                    if (!(start_signal_string.equals("START"))) {
                        Log.d("ExperimentThread", "The experiment initiation code is invalid = " + start_signal.toString());
                        return;
                    }

                    update_experiment_status_tv("RUNNING");
                    ANSWER_PROBABILITY_PCENT = TCPhelpers.byteArrayToIntLittleEndian(answer_prob_byte);
                    update_answer_probability_tv();
                    SHOULD_PEER_REASK = ( (new String(reask_bytes,StandardCharsets.UTF_8) ).equals("YES") );
                    update_peer_miss_second_try_tv();
                    QUERIES_PER_EXPERIMENT = TCPhelpers.byteArrayToIntLittleEndian(queries_per_experiment_bytes);
                    reset_experiment_counters();
                    System.gc(); // Garbage collection lest this gathers the threads that we left before
                    Thread.sleep(2000); // This is to make sure that the rest of the devices have also managed to change the variables

                    for(int request=0;request<QUERIES_PER_EXPERIMENT;request++){

                        Log.d("ExperimentThread","Now on request " + (request+1) );
                        update_total_requests_tv(request+1);
                        experiment_query_completed = new CountDownLatch(1);

                        // for every request we are going to do what would be done if the search button where to be pressed

                        // set the msi object
                        msi.keyword = "restaurant";
                        byte [] APICallBytesClientQuery = msi.apicall().getBytes();

                        // Lock all resources needed for this:
                        mutextServingPeerArrayList.lock(); // nobody is changing the peer list while we are using the peers
                        if(ServingPeerArrayList.size() == 0) {
                            HAVE_HAD_0_PEERS_AT_SOME_POINT = true;
                            Log.d("ExperimentThread","Entering the case where we have NO serving peers from the beginning!");
                            byte[] decJson = null;
                            try {
                                decJson = SigningServerInterations.DirectQuery(APICallBytesClientQuery, InterNodeCrypto.my_cert, InterNodeCrypto.my_key);
                                if (decJson == null) {
                                    Log.d("ExperimentThread", "Signing Server No Response!");
                                    getActivity().runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(getContext(), "No Peers. Signing Server unresponsive.", Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                    );
                                } else {
                                    HIT_MISS_COUNTERS_LOCK.lock();
                                    PEER_MISSES++;
                                    experiment_query_completed.countDown();
                                    update_hit_ratio_tv();
                                    HIT_MISS_COUNTERS_LOCK.unlock();
                                }
                            } catch (Exception e) {
                                Log.d("ExperimentThread", "Signing Server error on communication!");
                                getActivity().runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getContext(), "No Peers. Signing Server unresponsive.", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                );
                            }
                        }
                        else {
                            peer_thread_entered_counter = new CountDownLatch(ServingPeerArrayList.size());
                            Random random_pseudo = new Random();
                            int pseudonym_choice = random_pseudo.nextInt(100);
                            for (int ix = 0; ix < ServingPeerArrayList.size(); ix++) {
                                // making sure the answer is null before we call the peer interaction thread
                                SearchingNodeFragment.peerResponseDecJson[ix] = null;
                                // instead of ix (the serving peer index) we use a random pseudo certificate for all guys
                                PeerInteractions.PeerInteractionThread pi = new PeerInteractions.PeerInteractionThread(ix,
                                        ServingPeerArrayList.get(ix).PeerIP,
                                        ServingPeerArrayList.get(ix).PeerPort,
                                        APICallBytesClientQuery,
                                        pseudonym_choice
                                );
                                pi.start();
                            }
                            Log.d("ExperimentThread","The peer communicationg threads have started!");
                            ResponseCollectionThread rct = new ResponseCollectionThread(ServingPeerArrayList, false, APICallBytesClientQuery);
                            rct.start();
                            Log.d("ExperimentThread","The response collection thread has started!");
                        }
                        Log.d("ExperimentThread","Unlocked the serving peers!");
                        mutextServingPeerArrayList.unlock();

                        Log.d("ExperimentThread","Awaiting for a response from either Direct or Proxy request!");
                        experiment_query_completed.await();
                        Log.d("ExperimentThread","Indeed received countdown and await is completed!");

                    }

                    update_experiment_status_tv("FINISHED");

                    // output sanity debugs
                    ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_TOTAL_REQUESTS_LOCK.lock();
                    Log.d("ExperimentThread","For experiment " + i + " I have received a TOTAL of  " + ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_TOTAL_REQUESTS + " requests.");
                    int queries_we_got_and_are_unaccounted_for = ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_TOTAL_REQUESTS - (ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_SERVICED_REQUESTS+ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_DROPPED_REQUESTS);
                    Log.d("ExperimentThread","For experiment " + i + " there are " + queries_we_got_and_are_unaccounted_for + " queries which are un-accounted for!");
                    ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_TOTAL_REQUESTS = 0;
                    ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_TOTAL_REQUESTS_LOCK.unlock();

                    ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_DROPPED_REQUESTS_LOCK.lock();
                    Log.d("ExperimentThread","For experiment " + i + " I have dropped " + ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_DROPPED_REQUESTS);
                    ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_DROPPED_REQUESTS = 0;
                    ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_DROPPED_REQUESTS_LOCK.unlock();

                    ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_SERVICED_REQUESTS_LOCK.lock();
                    Log.d("ExperimentThread","For experiment " + i + " I have serviced " + ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_SERVICED_REQUESTS);
                    ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_SERVICED_REQUESTS = 0;
                    ServingNodeQueryHandleThread.COUNTER_OF_EXPERIMENT_SERVICED_REQUESTS_LOCK.unlock();

                    byte[] done_bytes = "DONE".getBytes();
                    if(HAVE_HAD_0_PEERS_AT_SOME_POINT){
                        HAVE_HAD_0_PEERS_AT_SOME_POINT = false;
                        done_bytes = "FAIL".getBytes();
                    }
                    try {
                        dos.write(done_bytes);
                        if(done_bytes.length!=4){
                            Log.d("ExperimentThread","Error: The done bytes should be 4 and they are not! That's what's causing the error!");
                        }
                        Log.d("ExperimentThread","Wrote DONE successfully");
                    }
                    catch(Exception e){
                        Log.d("ExperimentThread","Here is the issue: We can't send the DONE bytes back to the experiment remote server!");
                    }

                    HIT_MISS_COUNTERS_LOCK.lock();
                    byte [] hits_bytes = TCPhelpers.intToByteArray(PEER_HITS);
                    Log.d("ExperimentThread","I have send " + PEER_HITS + "hits!");
                    dos.write(hits_bytes);
                    HIT_MISS_COUNTERS_LOCK.unlock();

                }

                unset_experiment_screen();
                EXPERIMENT_IS_RUNNING = false;

            }catch (Exception e) {
                Log.d("ExperimentThread","Stopped trying to conduct experiment because of exception!");
                e.printStackTrace();
                return;
            }

        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {

        // Log.d("RAFAIL_NAVIGATION","We have entered the onMapReady!");

        mMap = googleMap;

        // now let's configure the map style
        // First we set the map style: This is used to hide features (POIs and Transit)
        // that doesn't mean that the GoogleMap doesn't load them in the background proactively
        // Google could track where the user looks at the map since in order for the map tiles to appear
        // the GoogleMap carries out API calls in the background to download the maps tiles
        // From reading the documentation there was no way to cache a certain region and the load it to the GoogleMap object
        // as one can do in Google Maps (the default maps application of Google).
        // Since this application is just designed for PoC we can make the argument that somebody could find another tiling
        // api which allows for controlled caching of a certain area. Also the search keyword and request are not visible to the GoogleMap
        // rather it's the GeoJSON layer that is applied on to it. Still one can make the case that Google could infer the search based on
        // on the GeoJSON layer applied but for the sake of PoC we don't really care about that since as mentioned before one can use a different
        // map object to achieve the same (and if that map object allows for controlled caching and ensures that the GeoJSON layer applied is not
        // leaked back to the tiling service then that's a solution one can use).
        try {
            boolean style_set = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(getContext(), R.raw.no_poi));
            if(!style_set){
                Log.d("MapConfig","The map style could not be loaded");
            }
        } catch (Resources.NotFoundException e){
            Log.d("MapConfig","Map style file was not found to apply!");
        }

        search_keyword_input.setVisibility(View.VISIBLE);
        search_keyword_input.setHint("Search keyword");
        search_keyword_input.setText("");
        search_keyword_input.setBackgroundColor(Color.WHITE);
        search_keyword_input.setSingleLine();

        search_keyword_input.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                        if(i == EditorInfo.IME_ACTION_DONE || i==EditorInfo.IME_ACTION_GO){
                            msi.keyword = search_keyword_input.getText().toString();
                            // removing whitespaces
                            msi.keyword = msi.keyword.replaceAll("\\s","");
                            search_keyword_input.clearFocus();
                        }
                        return false;
                    }
                }
        );

        search_keyword_input.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        msi.keyword = search_keyword_input.getText().toString();
                        // removing whitespaces
                        msi.keyword = msi.keyword.replaceAll("\\s","");
                    }
                }
        );

        // Move Camera to the initial position and zoom in
        LatLng kth_kista = new LatLng(59.4045797, 17.950208);
        CameraPosition initial_camera_position = new CameraPosition.Builder()
                .target(kth_kista)
                .zoom((float)(13))
                .build();
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(initial_camera_position));

        mMap.setOnCameraMoveListener(
                new GoogleMap.OnCameraMoveListener() {
                    @Override
                    /*
                    Check out: https://developers.google.com/maps/documentation/android-sdk/views
                    for better understanding of the terminology used.
                     */
                    public void onCameraMove() {

                        // in case there is a zoom or unzoom the search diameter must be updated
                        update_search_diameter();

                        // updating the search location based on the new target of the map
                        msi.map_center = mMap.getCameraPosition().target;

                    }
                }
        );

        mMap.setOnMapClickListener(
                new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(@NonNull LatLng latLng) {
                        Log.d("ON MAP CLICK","entered function");
                        if(search_keyword_input.hasFocus()){
                            Log.d("ON MAP CLICK","the edit text has focus!");
                            // search_keyword_input.setImeOptions(EditorInfo.IME_ACTION_DONE);
                            search_keyword_input.onEditorAction(EditorInfo.IME_ACTION_DONE);
                            search_keyword_input.clearFocus();
                        }
                    }
                }
        );

        UiSettings uiSettings = mMap.getUiSettings();
        uiSettings.setMyLocationButtonEnabled(false);
        uiSettings.setMapToolbarEnabled(false);

        update_search_diameter();

    }

    void update_search_diameter(){

        VisibleRegion viewPort = mMap.getProjection().getVisibleRegion();
        double viewport_width = SphericalUtil.computeDistanceBetween(viewPort.nearLeft,viewPort.nearRight);

        msi.search_diameter = min(15000,(int)(viewport_width));
        if(viewport_width <= MapSearchItem.MAXIMUM_SEARCH_DIAMETER){
            search_button.setActivated(true);
            search_button.setClickable(true);
            search_button.setEnabled(true);
        }
        else {
            search_button.setActivated(false);
            search_button.setClickable(false);
            search_button.setEnabled(false);
        }

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        // Log.d("RAFAIL_NAVIGATION","Ate Ate onViewCreated!");
        super.onViewCreated(view, savedInstanceState);

        experimentViews = new ArrayList<TextView>();
        experimentsTV = view.findViewById(R.id.experimentsTV);
        experimentViews.add(experimentsTV);
        experimentStatusTV = view.findViewById(R.id.experimentStatusTV);
        experimentViews.add(experimentStatusTV);
        answerProbabilityTV = view.findViewById(R.id.answerProbabilityTV);
        experimentViews.add(answerProbabilityTV);
        RetryOnMissTV = view.findViewById(R.id.RetryOnMissTV);
        experimentViews.add(RetryOnMissTV);
        hitVSmissTV = view.findViewById(R.id.hitVSmissTV);
        experimentViews.add(hitVSmissTV);
        totalRequestsTV = view.findViewById(R.id.toatalRequestsTV);
        experimentViews.add(totalRequestsTV);

        LogsButton = view.findViewById(R.id.buttonToLog);
        LogsButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        NavHostFragment.findNavController(SearchingNodeFragment.this)
                                .navigate(R.id.action_SecondFragment_to_loggingFragment);
                    }
                }
        );

        // allowing for HTTPS connections (unnecessary)
        // network_permit();

        search_button = view.findViewById(R.id.button_search);
        search_button.setActivated(false);
        search_button = view.findViewById(R.id.button_search);
        search_button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        // Check UI
                        if(mMap == null){
                            return;
                        }
                        if(msi.keyword.isEmpty()){
                            Log.d("SEARCH CLICK","The search keyword is undefined");
                            Toast.makeText(getContext(), "No keyword", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if(search_keyword_input.hasFocus()){
                            search_keyword_input.setImeOptions(EditorInfo.IME_ACTION_DONE);
                            search_keyword_input.clearFocus();
                        }

                        // URL for querying the LBS preparation
                        String api_call_string = "";
                        try {
                            api_call_string = msi.apicall();
                            Log.d("API CALL STRING CREATION SUCCESS",api_call_string);
                        }
                        catch (PackageManager.NameNotFoundException e){
                            e.printStackTrace();
                            Log.d("API CALL STRING CREATION ERROR","Can't get meta-data and thus Places API key!");
                            return;
                        }
                        byte [] APICallBytesClientQuery = api_call_string.getBytes();

                        search_button.setText("...");

                        // Lock all resources needed for this:
                        mutextServingPeerArrayList.lock(); // nobody is changing the peer list while we are using the peers
                        search_button.setClickable(false); // can't search until this one is done

                        // If there is no peer then we directly contact the signing server
                        // This is what we call a PEER MISS: due to no peers discovered/given
                        if(ServingPeerArrayList.size() == 0){
                            HAVE_HAD_0_PEERS_AT_SOME_POINT = true;
                            LoggingFragment.mutexTvdAL.lock();
                            LoggingFragment.tvdAL.add( new LoggingFragment.TextViewDetails("No Peers. Direct Request to Signing Server.",Color.DKGRAY));
                            byte [] decJson = null;
                            try {
                                decJson = SigningServerInterations.DirectQuery(APICallBytesClientQuery,InterNodeCrypto.my_cert,InterNodeCrypto.my_key);
                                if (decJson == null) {
                                    LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Signing Server No Response!", Color.RED));
                                    getActivity().runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(getContext(), "No Peers. Signing Server unresponsive.", Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                    );
                                } else {
                                    LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("DIRECT REQUEST (no peers): Signing Server Responded.", Color.GREEN));
                                    apply_search_result(decJson);
                                }
                            }
                            catch (Exception e){
                                LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Signing Server error on communication!", Color.RED));
                                getActivity().runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getContext(), "No Peers. Signing Server unresponsive.", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                );
                            }
                            mutextServingPeerArrayList.unlock();
                            LoggingFragment.mutexTvdAL.unlock();
                            search_button.setClickable(true);
                            search_button.setText("SEARCH");
                            return;
                        }

                        // In the case we have multiple peers that can answer our query
                        // We start the Threads for requesting answers from these peers.
                        peer_thread_entered_counter = new CountDownLatch(ServingPeerArrayList.size());
                        Random random_pseudo = new Random();
                        int pseudonym_choice = random_pseudo.nextInt(100);
                        for(int i=0;i<ServingPeerArrayList.size();i++){
                            // making sure the answer is null before we call the peer interaction thread
                            SearchingNodeFragment.peerResponseDecJson[i] = null;
                            // instead of i (the serving peer index) we use a random pseudo certificate for all guys
                            PeerInteractions.PeerInteractionThread pi = new PeerInteractions.PeerInteractionThread(i,
                                    ServingPeerArrayList.get(i).PeerIP,
                                    ServingPeerArrayList.get(i).PeerPort,
                                    APICallBytesClientQuery,
                                    pseudonym_choice
                                    );
                            pi.start();
                        }

                        // We start our thread meant for receiving all the answers one by one
                        // We pass the current serving list as an argument for the logging by this thread
                        ResponseCollectionThread rct = new ResponseCollectionThread(ServingPeerArrayList,false,APICallBytesClientQuery);
                        rct.start();

                        // unlocking the resources
                        mutextServingPeerArrayList.unlock();
                        // we can return with no fear since for the button to be clickable again then it means that the request
                        // was completed and the rct finished up processing the results
                        return;

                    }
                }
        );

        // we are making the search edit text invisible until the map is loaded
        search_keyword_input = view.findViewById(R.id.search_keyword_input);
        search_keyword_input.setVisibility(View.INVISIBLE);

        // we can now initialize the map search item
        msi = new MapSearchItem(getContext());

        // Log.d("RAFAIL_NAVIGATION","We have reached the end of onViewCreated!");

        // Start thread for the experiment
        if(eThread_has_started == false) {
            Log.d("eThread","Magnificent!");
            eThread.start();
            eThread_has_started = true;
        }

    }

    // - thread which waits for all the answers from the peers to be received DONE
    // - checks and logs consensus status DONE
    // - logs which peers responded and which have not. If there were in-correct signatures (NOT OF THE SIGNING SERVER) we log this as well
    // - updates map based on one of the answers if we have consensus
    // - in the case of no consensus we get the first answer (they were all signed thus we can trust all of them)
    public class ResponseCollectionThread extends Thread{

        private ArrayList<ServingPeer> spal; // The array list of peers when the requests where sent to them
        boolean isReAttempt;
        byte [] APICallBytesClientQuery;

        public ResponseCollectionThread(ArrayList<ServingPeer> sp, boolean isReAttempt, byte [] queryArray){
            spal = sp;
            this.isReAttempt = isReAttempt;
            this.APICallBytesClientQuery = queryArray;
        }
        @Override
        public void run() {
            try {

                // Log.d("RAFAIL_NAVIGATION","response collectin thread entered!");
                // we ensure that all peer threads have entered and locked their respective reponse indexes
                Log.d("RCT","Now awaiting for peer communication threads to enter and lock their responses!");
                peer_thread_entered_counter.await();
                Log.d("RCT","Now we know that the peer connection threads have already locked their respective responses. Waiting on those now!");

                // now we wait for all response index to be unlocked by their respective threads and thus become available
                // we consequently will lock them so that we ensure that they do not change during their processing in this thread
                // lest another request happens for some reason (which should not since the search button is unclickable)
                int peers = spal.size();
                for(int i=0;i<peers;i++){
                    SearchingNodeFragment.mutexPeerResponseDecJson[i].lock();
                }
                Log.d("RCT","The peer responses have been determined and locked!");

                if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.mutexTvdAL.lock(); }

                // RESPONSE RATE and CONSENSUS checking
                int responded = 0;
                int first_reponded = -1;
                boolean consensus = true; // There is a consensus among the received answers 1 / there is not 0
                for(int i=0;i<peers;i++){
                    if(SearchingNodeFragment.peerResponseDecJson[i] != null) {
                        responded++;
                        if(first_reponded==-1){
                            first_reponded = i;
                        }
                        else{
                            consensus = consensus && ( Arrays.equals(peerResponseDecJson[i],peerResponseDecJson[first_reponded]) );
                        }
                    }
                }

                Log.d("RCT","The response rate for the latest queries has been determined!");

                // log the RESPONSE RATE
                if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Response Rate = [" + responded + " / " + peers + "]",(peers==responded)?Color.GREEN:Color.RED)); }

                // If we have 0 answers and this is the first time we have tried to get answers.
                if(responded == 0 && !this.isReAttempt && SHOULD_PEER_REASK){
                    if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Forced peer discovery thread restart! Cause: All peers unresponsive to query.",Color.RED)); }

                    // - unlock everything that this thread has locked DONE
                    if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.mutexTvdAL.unlock(); }
                    for(int i=0;i<peers;i++){
                        SearchingNodeFragment.mutexPeerResponseDecJson[i].unlock();
                    }

                    // - restart the peer discover thread
                    peer_rediscovery_thread_entered = new CountDownLatch(1); // we will await for 1 attempt from the new thread to be made
                    P2PThreadExplicitRestart();
                    peer_rediscovery_thread_entered.await();

                    // - we lock the new peers
                    mutextServingPeerArrayList.lock();

                    // if the new peer array has nothing in it
                    if(ServingPeerArrayList.size() == 0){
                        HAVE_HAD_0_PEERS_AT_SOME_POINT = true;
                        if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.mutexTvdAL.lock(); }
                        if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.tvdAL.add( new LoggingFragment.TextViewDetails("No peers from forced peer discovery request. Direct request to signing server to retrieve answer to search.",Color.DKGRAY));}
                        byte [] decJson = null;
                        try {
                            decJson = SigningServerInterations.DirectQuery(APICallBytesClientQuery,InterNodeCrypto.my_cert,InterNodeCrypto.my_key);
                            if (decJson == null) {
                                if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Signing Server No Response!", Color.RED)); }
                                getActivity().runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getContext(), "No Peers. Signing Server unresponsive.", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                );
                            } else {
                                if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("DIRECT REQUEST (no peers): Signing Server Responded.", Color.GREEN)); }
                                HIT_MISS_COUNTERS_LOCK.lock();
                                PEER_MISSES++;
                                experiment_query_completed.countDown();
                                update_hit_ratio_tv();
                                HIT_MISS_COUNTERS_LOCK.unlock();

                                if(!EXPERIMENT_IS_RUNNING) {
                                    byte[] copyDecJson = decJson;
                                    getActivity().runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    apply_search_result(copyDecJson);
                                                }
                                            }
                                    );
                                }

                            }
                        }
                        catch (Exception e){
                            if(!EXPERIMENT_IS_RUNNING){LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Signing Server error on communication!", Color.RED));}
                            getActivity().runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(getContext(), "No Peers. Signing Server unresponsive.", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                            );
                        }

                        // unlocking all resources we have locked
                        mutextServingPeerArrayList.unlock();
                        if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.mutexTvdAL.unlock(); }

                        if(!EXPERIMENT_IS_RUNNING) {
                            getActivity().runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            search_button.setClickable(true);
                                            search_button.setText("SEARCH");
                                        }
                                    }
                            );
                        }
                        return;
                    }

                    if(!EXPERIMENT_IS_RUNNING) {
                        LoggingFragment.mutexTvdAL.lock();
                        LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Retrying search query with NEW peers.", Color.DKGRAY));
                        LoggingFragment.mutexTvdAL.unlock();
                    }

                    // - if the new peer array list is not null call for a second try (basically do what happens when we click the button over again)
                    // In the case we have multiple peers that can answer our query
                    // We start the Threads for requesting answers from these peers.

                    peer_thread_entered_counter = new CountDownLatch(ServingPeerArrayList.size());
                    Random random_pseudo = new Random();
                    int pseudonym_choice = random_pseudo.nextInt(100);
                    for(int i=0;i<ServingPeerArrayList.size();i++){
                        // making sure the answer is null before we call the peer interaction thread
                        SearchingNodeFragment.peerResponseDecJson[i] = null;
                        PeerInteractions.PeerInteractionThread pi = new PeerInteractions.PeerInteractionThread(i,
                                ServingPeerArrayList.get(i).PeerIP,
                                ServingPeerArrayList.get(i).PeerPort,
                                APICallBytesClientQuery,
                                pseudonym_choice
                        );
                        pi.start();
                    }

                    // We start our thread meant for receiving all the answers one by one
                    // We pass the current serving list as an argument for the logging by this thread
                    ResponseCollectionThread rct = new ResponseCollectionThread(ServingPeerArrayList,true,APICallBytesClientQuery);
                    rct.start();

                    // unlocking the resources
                    mutextServingPeerArrayList.unlock();
                    // we can return with no fear since for the button to be clickable again then it means that the request
                    // was completed and the rct finished up processing the results
                    return;

                }
                if(responded == 0 && (this.isReAttempt || !SHOULD_PEER_REASK) ){

                    // unlock peer responses (FORGOT TO ADD THIS)
                    for(int i=0;i<peers;i++){
                        SearchingNodeFragment.mutexPeerResponseDecJson[i].unlock();
                    }

                    // if the new peer array has nothing in it
                    if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.mutexTvdAL.lock(); }
                    if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.tvdAL.add( new LoggingFragment.TextViewDetails("New peers were also unresponsive. Directly contacting the signing server.",Color.DKGRAY)); }
                    byte [] decJson = null;
                    try {
                        decJson = SigningServerInterations.DirectQuery(APICallBytesClientQuery,InterNodeCrypto.my_cert,InterNodeCrypto.my_key);
                        if (decJson == null) {
                            if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Signing Server No Response!", Color.RED)); }
                            getActivity().runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(getContext(), "Peers unresponsive. Signing Server unresponsive.", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                            );
                        }
                        else {
                            if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("DIRECT REQUEST (unresponsive peers): Signing Server Responded.", Color.GREEN)); }
                            if(!EXPERIMENT_IS_RUNNING) {
                                byte[] copyDecJson = decJson;
                                getActivity().runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                apply_search_result(copyDecJson);
                                            }
                                        }
                                );
                            }
                            HIT_MISS_COUNTERS_LOCK.lock();
                            PEER_MISSES++;
                            Log.d("Response Collection Thread","Indeed we DO count down!");
                            experiment_query_completed.countDown();
                            update_hit_ratio_tv();
                            HIT_MISS_COUNTERS_LOCK.unlock();
                        }
                    }
                    catch (Exception e){
                        if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Signing Server error on communication!", Color.RED)); }
                        getActivity().runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getContext(), "Peers unresponsive. Signing Server unresponsive.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            );
                    }

                    if(!EXPERIMENT_IS_RUNNING){LoggingFragment.mutexTvdAL.unlock();}
                    if(!EXPERIMENT_IS_RUNNING) {
                        getActivity().runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        search_button.setClickable(true);
                                        search_button.setText("SEARCH");
                                    }
                                }
                        );
                    }
                    return;
                }

                if(!consensus){
                    if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Answers from peers are ALL signed from signing server. However there are differences in the answers. We show peer " + first_reponded + "'s response.",Color.DKGRAY)); }
                    Log.d("ResponseCollectionThread","ERROR: Not all responses received consent with one another");
                }
                else {
                    if(!EXPERIMENT_IS_RUNNING){ LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Consensus amongst ALL peers that responded!",Color.GREEN)); }
                    Log.d("ResponseCollectionThread", "SUCCESS: All responses received consent with each other! And the EXPERIMENT_IS_RUNNING = " + EXPERIMENT_IS_RUNNING );
                }

                if(!EXPERIMENT_IS_RUNNING) {LoggingFragment.mutexTvdAL.unlock();}

                // count peer hit and update peer hit ratio text view
                HIT_MISS_COUNTERS_LOCK.lock();
                PEER_HITS++;
                experiment_query_completed.countDown();
                update_hit_ratio_tv();
                HIT_MISS_COUNTERS_LOCK.unlock();

                // applying the result that we got from the peers
                if(!EXPERIMENT_IS_RUNNING) {
                    int final_first_responded = first_reponded;
                    getActivity().runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    apply_search_result(peerResponseDecJson[final_first_responded]);
                                }
                            }
                    );
                }

                // unlocking the response bytes arrays for future searches
                for(int i=0;i<peers;i++){
                    SearchingNodeFragment.mutexPeerResponseDecJson[i].unlock();
                }

                // making the search button clickable again now that we know for sure the search is completed
                if(!EXPERIMENT_IS_RUNNING) {
                    getActivity().runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    SearchingNodeFragment.search_button.setClickable(true);
                                    search_button.setText("SEARCH");
                                }
                            }
                    );
                }

            }
            catch (Exception e){
                e.printStackTrace();
                getActivity().runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getContext(), "Response collection failed!", Toast.LENGTH_SHORT).show();
                            }
                        }
                );
                Log.d("ResponseCollectionThread","ERROR!");
            }

        }
    }

    /* The decryptedJSON byte array is the one we received either:
    *  1) From directly talking with the Signing server.
    *  2) From the consensus of the peer responses.
    * */
    void apply_search_result(byte [] decryptedJSON){

        String JSONObjectAnswer = new String(decryptedJSON,StandardCharsets.UTF_8);
        JSONObject answerJSON = null;

        try {
            answerJSON = new JSONObject(JSONObjectAnswer);
            Log.d("apply_search_result","SUCCESS: The response produced a JSON object successfully!");
        } catch (JSONException e) {
            Log.d("apply_search_result","ERROR: The response doesn't produce a JSON object as expected!");
            getActivity().runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(null, "Invalid Response: Not a JSON", Toast.LENGTH_SHORT).show();
                        }
                    }
            );
            e.printStackTrace();
        }

        if( answerJSON!=null && answerJSON.toString().contains("\"status\":\"ZERO_RESULTS\"") ) {
            getActivity().runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getContext(), "ERROR: No results for given keyword!", Toast.LENGTH_SHORT).show();
                        }
                    }
            );
            return;
        }
        Log.d("apply_search_result","JSON object is parsed successfully");
        JSONObject answer_geojsoned = json_to_geojson( answerJSON );
        Log.d("apply_search_result","Modified answer from JSON to GEO_JSON format!");
        apply_result_layer(answer_geojsoned);
        Log.d("MAP UPDATE","The GeoJSON object has been added to the map layer for display!");
    }

    void apply_result_layer(JSONObject geojson){
        // now rendering the resulting GeoJSON into the map after
        if(results_layer != null) {
            // we remove the previous results layer if it exists
            results_layer.removeLayerFromMap();
            mMap.clear();
        }
        results_layer = new GeoJsonLayer(mMap, geojson);

        // setting the listener to show the names of the locations
        results_layer.setOnFeatureClickListener(
                new GeoJsonLayer.GeoJsonOnFeatureClickListener() {
                    @Override
                    public void onFeatureClick(Feature feature) {
                        String name = feature.getProperty("name");
                        GeoJsonPoint geoJsonPoint = (GeoJsonPoint) feature.getGeometry();
                        LatLng latLng = geoJsonPoint.getCoordinates();

                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(latLng)
                                .title(name);

                        mMap.addMarker(markerOptions).showInfoWindow();
                    }
                }
        );

        results_layer.addLayerToMap();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        // Log.d("RAFAIL_NAVIGATION","onCreateView empiken re!");

        binding = FragmentSecondBinding.inflate(inflater, container, false);
        View v = binding.getRoot();

        // Log.d("RAFAIL_NAVIGATION","view root is in!");

        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }

        // Log.d("RAFAIL_NAVIGATION","BUNDLEKEY!");

        mMapView = (MapView) v.findViewById(R.id.mapViewforSecond);

        // Log.d("RAFAIL_NAVIGATION","MAPVIEWFORSECOND");

        mMapView.onCreate(mapViewBundle);

        // Log.d("RAFAIL_NAVIGATION","ON CREATE!");

        mMapView.getMapAsync(this);

        // Log.d("RAFAIL_NAVIGATION","GET ASYNC!");

        return v;

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle);
        }

        mMapView.onSaveInstanceState(mapViewBundle);
    }

    /*
    Function to transform the data we have received from the API call in the JSON format
    defined by the Places API into the GeoJSON format: https://geojson.org/ which is what
    GoogleMap can understand to render the results as markers on the map.
     */
    JSONObject json_to_geojson(JSONObject response){

        JSONObject full_geoJson = new JSONObject();

        try {

            full_geoJson.put("type","FeatureCollection");

            JSONArray features = new JSONArray();

            JSONArray arr = response.getJSONArray("results");
            for (int i=0; i < arr.length(); i++){

                JSONObject single_point_geojson = new JSONObject();

                // collecting the fields that we are interested in
                String name = arr.getJSONObject(i).getString("name");
                String lat = arr.getJSONObject(i).getJSONObject("geometry").getJSONObject("location").getString("lat");
                String lng = arr.getJSONObject(i).getJSONObject("geometry").getJSONObject("location").getString("lng");
                Log.d("JSON TO GEOjson",""+name);
                Log.d("JSON TO GEOjson",""+lat+" , "+lng);

                // constructing the geoJson to return
                // based on https://geojson.org/

                // type
                single_point_geojson.put("type","Feature");

                // geometry
                JSONObject geometry = new JSONObject();
                geometry.put("type","Point");
                JSONArray coords = new JSONArray();
                double lng_double = round_decimals( Double.parseDouble(lng) , 10 );
                double lat_double = round_decimals( Double.parseDouble(lat) , 10 );

                // DecimalFormat df = new DecimalFormat("0.00");
                // df.setMaximumFractionDigits(6);

                coords.put(lng_double);
                coords.put(lat_double);
                geometry.put("coordinates",coords);
                single_point_geojson.put("geometry",geometry);

                // properties
                JSONObject prop = new JSONObject();
                prop.put("name",name);
                single_point_geojson.put("properties",prop);

                String result = single_point_geojson.toString();
                Log.d("JSON TO GEOjson","Single resulting geoJSON: " + result);

                features.put(i,single_point_geojson);

            }

            full_geoJson.put("features",features);

            Log.d("JSON TO GEOjson","Returning the following GEOJSON: " + full_geoJson);

            return full_geoJson;

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    // from: https://stackoverflow.com/questions/2808535/round-a-double-to-2-decimal-places
    public static double round_decimals(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    // The following functions is to allow us establish HTTP connections
    public void network_permit(){
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onPause() {
        mMapView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

}