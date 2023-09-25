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
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.InetAddress;
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

public class SearchingNodeFragment extends Fragment implements OnMapReadyCallback {

    private static final int MAX_PEER_RESPONSES = 10;
    private FragmentSecondBinding binding;
    private MapView mMapView;
    private GoogleMap mMap=null;
    private static final String MAPVIEW_BUNDLE_KEY = "MapViewBundleKey";
    public static Button search_button;
    private EditText search_keyword_input;
    public GeoJsonLayer results_layer = null;
    private MapSearchItem msi; // The fundamental object that defines the search as soon as the search button is pressed

    // updated by the PEER DISCOVERY Thread (aka QUERY Thread on P2P side) which runs in the background
    // if for some reason all the peers we were given are inactive we query the signing server directly
    public static ArrayList<ServingPeer> ServingPeerArrayList;
    public static final Lock mutextServingPeerArrayList = new ReentrantLock();
    public static LBSEntitiesConnectivity lbsEC4PeerDiscRestart;

    // The bytes arrays for the responses in case of peer querying
    public static byte [][] peerResponseDecJson = new byte[MAX_PEER_RESPONSES][];
    public static final Lock [] mutexPeerResponseDecJson = new ReentrantLock[MAX_PEER_RESPONSES];
    public static CountDownLatch peer_thread_entered_counter;

    public static class ServingPeer{
        // public String DistinguishedName; I don't know the name. I expect only the IP and Port to be of a SOME peer.
        public InetAddress PeerIP;
        public int PeerPort;
        public boolean faulty; // it either doesn't respond or it returns gibberish
        public ServingPeer(InetAddress IP, int Port){
            // DistinguishedName = DN; I don't know the name. I expect only the IP and Port to be of a SOME peer.
            PeerIP = IP;
            PeerPort = Port;
            faulty = false;
        }
    }

    // We call the following function IFF AND WHEN all of the peers in the ServingPeerArrayList are non-responsive
    public static void P2PThreadExplicitRestart(){
        P2PRelayServerInteractions.qThread.explicit_search_request = true; // setting this true so the previous instance will kill itself
        P2PRelayServerInteractions.qThread = new P2PRelayServerInteractions.PeerDiscoveryThread(lbsEC4PeerDiscRestart);
        P2PRelayServerInteractions.qThread.start();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {

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
        super.onViewCreated(view, savedInstanceState);

        Button LogsButton = view.findViewById(R.id.buttonToLog);
        LogsButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        NavHostFragment.findNavController(SearchingNodeFragment.this)
                                .navigate(R.id.action_SecondFragment_to_loggingFragment);
                    }
                }
        );

        // allowing for HTTPS connections
        network_permit();

        search_button = view.findViewById(R.id.button_search);
        search_button.setActivated(false);
        search_button = view.findViewById(R.id.button_search);
        search_button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

// ------------------------------------------------------------------------------- START OF PRESSING THE SEARCH BUTTON ----------------------------------------------------------------------

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
                            LoggingFragment.mutexTvdAL.lock();
                            LoggingFragment.tvdAL.add( new LoggingFragment.TextViewDetails("No Peers. Direct Request to Signing Server",Color.YELLOW));
                            byte [] decJson = SigningServerInterations.DirectQuery(APICallBytesClientQuery);
                            if( decJson == null ){
                                LoggingFragment.tvdAL.add( new LoggingFragment.TextViewDetails("Signing Server No Response",Color.RED));
                            }
                            else{
                                LoggingFragment.tvdAL.add( new LoggingFragment.TextViewDetails("Signing Server Responded",Color.GREEN));
                            }
                            apply_search_result(decJson);
                            mutextServingPeerArrayList.unlock();
                            LoggingFragment.mutexTvdAL.unlock();
                            search_button.setClickable(true);
                            search_button.setText("SEARCH");
                            return;
                        }

                        // In the case we have multiple peers that can answer our query
                        // We start the Threads for requesting answers from these peers.
                        peer_thread_entered_counter = new CountDownLatch(ServingPeerArrayList.size());
                        for(int i=0;i<ServingPeerArrayList.size();i++){
                            PeerInteractions.PeerInteractionThread pi = new PeerInteractions.PeerInteractionThread(i,
                                    ServingPeerArrayList.get(i).PeerIP,
                                    ServingPeerArrayList.get(i).PeerPort,
                                    APICallBytesClientQuery
                                    );
                            pi.start();
                        }

                        // We start our thread meant for receiving all the answers one by one
                        // We pass the current serving list as an argument for the logging by this thread
                        ResponseCollectionThread rct = new ResponseCollectionThread(ServingPeerArrayList);
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

    }

    // - thread which waits for all the answers from the peers to be received DONE
    // - checks and logs consensus status DONE
    // - logs which peers responded and which have not. If there were in-correct signatures (NOT OF THE SIGNING SERVER) we log this as well
    // - updates map based on one of the answers if we have consensus
    // - TODO: in the case of no consensus we look at the signed timestamps from the signing server and pick the newest one? -> Implement timestamping of rsponses
    public class ResponseCollectionThread extends Thread{

        private ArrayList<ServingPeer> spal; // The array list of peers when the requests where sent to them

        public ResponseCollectionThread(ArrayList<ServingPeer> sp){
            spal = sp;
        }
        @Override
        public void run() {
            try {

                // we ensure that all peer threads have entered and locked their respective reponse indexes
                peer_thread_entered_counter.await();
                // now we wait for all response index to be unlocked by their respective threads and thus become available
                // we consequently will lock them so that we ensure that they do not change during their processing in this thread
                int peers = spal.size();
                for(int i=0;i<peers;i++){
                    SearchingNodeFragment.mutexPeerResponseDecJson[i].lock();
                }
                LoggingFragment.mutexTvdAL.lock();

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

                // log the RESPONSE RATE
                LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Query Response Rate = [" + responded + " / " + peers + "]",(peers==responded)?Color.GREEN:Color.RED));

                if(responded == 0){
                    LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Forced peer discovery thread restart! Cause: 0 responses to query.",Color.YELLOW));

                    // TODO:
                    // - unlock everything that this thread has locked DONE
                    LoggingFragment.mutexTvdAL.unlock();
                    for(int i=0;i<peers;i++){
                        SearchingNodeFragment.mutexPeerResponseDecJson[i].unlock();
                    }

                    // - restart the peer discover thread

                    // - use a countdown lock to check that indeed we have received a new answer form the P2P server
                    // - if the new peer array list is not null call for a second try (basically do what happens when we click the button over again)
                    // - call this function for second try (have a boolean to check that it is a second try)
                    // - a second try should not retry with peers but instead directly connect to the signing server and log this.
                }
                if(!consensus){
                    LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("No consensus reached! Using answer of peer with index " + first_reponded + ".",Color.RED));
                    Log.d("ResponseCollectionThread","ERROR: Not all responses received consent with one another");
                }
                else {
                    LoggingFragment.tvdAL.add(new LoggingFragment.TextViewDetails("Consensus amongst ALL responses!",Color.GREEN));
                    Log.d("ResponseCollectionThread", "SUCCESS: All responses received consent with each other!");
                }

                // applying the result that we got from the peers
                apply_search_result(peerResponseDecJson[first_reponded]);

                LoggingFragment.mutexTvdAL.unlock();
                // unlocking the response bytes arrays for future searches
                for(int i=0;i<peers;i++){
                    SearchingNodeFragment.mutexPeerResponseDecJson[i].unlock();
                }
                // making the search button clickable again now that we know for sure the search is completed
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
                            Toast.makeText(null, "ERROR: No results for given keyword!", Toast.LENGTH_SHORT).show();
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

        binding = FragmentSecondBinding.inflate(inflater, container, false);
        View v = binding.getRoot();

        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }
        mMapView = (MapView) v.findViewById(R.id.mapViewforSecond);
        mMapView.onCreate(mapViewBundle);
        mMapView.getMapAsync(this);

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