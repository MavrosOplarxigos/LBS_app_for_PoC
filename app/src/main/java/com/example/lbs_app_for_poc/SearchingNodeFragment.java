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
import com.google.maps.android.data.geojson.GeoJsonLayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class SearchingNodeFragment extends Fragment implements OnMapReadyCallback {

    private FragmentSecondBinding binding;
    private MapView mMapView;
    private GoogleMap mMap=null;
    private static final String MAPVIEW_BUNDLE_KEY = "MapViewBundleKey";
    private Button search_button;
    private EditText search_keyword_input;
    public GeoJsonLayer results_layer = null;
    private MapSearchItem msi;

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

        binding.buttonSecond.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(SearchingNodeFragment.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment);
            }
        });

        Button back_button = view.findViewById(R.id.button_second);
        back_button.setVisibility(View.INVISIBLE);
        back_button.setActivated(false);

        // allowing for HTTPS connections
        network_permit();

        search_button = view.findViewById(R.id.button_search);
        search_button.setActivated(false);
        search_button = view.findViewById(R.id.button_search);
        search_button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        if(mMap == null){
                            return;
                        }

                        if(msi.keyword.isEmpty()){
                            Log.d("SEARCH CLICK","The search keyword is undefined");
                            Toast.makeText(getContext(), "Please specify a keyword first", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if(search_keyword_input.hasFocus()){
                            search_keyword_input.setImeOptions(EditorInfo.IME_ACTION_DONE);
                            search_keyword_input.clearFocus();
                        }

                        // OK so the map is already prepared so now we can carry out the search
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

                        // Now we need to instead of carrying out the request ourselves establish connectivity to the server and send msi as a byte array or string

                        // 1) HANDSHAKE STEP 1: SEND CLIENT CREDS TO SERVER
                        // [8 bytes][ ? ECDSA CERT LENGHT AFTER ENCRYPTED WITH CA PRIV KEY ]
                        // [CLIENT ID][ECDSA_ID_AND_PUBKEY_ENCRYPTED_WITH_CA_PRIVATE_KEY]

                        // 2) HANDSHAKE STEP 2: RECEIVE SERVER CREDS
                        // [8 bytes][ ? ECDSA CERT LENGHT AFTER ENCRYPTED WITH CA PRIV KEY ]
                        // [SERVER ID][ECDSA_ID_AND_SERVER_PUBKEY_ENC_WITH_CA_PRIVATE_KEY]

                        // VERIFY SERVER ID = ID IN THE CERTIFICATE

                        // 3) SERVICE STEP 1: SERIALIZE OR STRINGIFY msi (or make into a byte array)
                        // 4) SERVICE STEP 2: ENCRYPT WITH PRIV KEY OF CLIENT AND THEN WITH PUB KEY OF SERVER
                        // 5) SERVICE STEP 3: SEND THE STRING TO THE SERVER

                        // 6) SERVICE STEP 4: RECEIVE A STRING (or byte array from the server)
                        // 7) SERVICE STEP 5: DECRYPT FIRST USING PRIV KEY OF CLIENT AND THEN PUBLIC KEY OF SERVER

                        // 8) SERVICE STEP 6: USE THE RESPONSE AND DISPLAY IT

                        // 9) ACKNOWLEDGEMENT STEP 1: HASH THE RECEIVED STRING AND SEND IT ENCRYPTED WITH CLIENT KEY AND THEN SERVER PUB KEY

                        /*

                        // TODO: change this to send the api_call_url to the intermediate node instead steps are above
                        // the intermediate node should run the following code after receiving the request
                        JSONObject answer = execute_api_call(api_call_string);

                        if(answer == null){
                            Log.d("API EXEC RESULT","The JSON file is null!\n");
                            Toast.makeText(getContext(), "No response from LBS server!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        else {
                            Log.d("API EXEC RESULT", "The JSON file was created! \n" + answer.toString());
                        }

                        // In case no results were found we report it to the user and return
                        if( answer.toString().contains("\"status\":\"ZERO_RESULTS\"") ) {
                            Toast.makeText(getContext(), "No results for given keyword!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // After the transformation is carried out we should be left with a GeoJSON
                        JSONObject answer_geojsoned = json_to_geojson( answer );
                        apply_result_layer(answer_geojsoned);
                        */

                    }
                }
        );

        // we are making the search edit text invisible until the map is loaded
        search_keyword_input = view.findViewById(R.id.search_keyword_input);
        search_keyword_input.setVisibility(View.INVISIBLE);

        // we can now initialize the map search item
        msi = new MapSearchItem(getContext());

    }

    void apply_result_layer(JSONObject geojson){
        // now rendering the resulting GeoJSON into the map after
        if(results_layer != null) {
            // we remove the previous results layer if it exists
            results_layer.removeLayerFromMap();
        }
        results_layer = new GeoJsonLayer(mMap, geojson );
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

    // TODO: MOVE THIS FUNCTION TO A SEPARATE CLASS (LBS_DIRECT_INTERACTIONS) LET'S CALL IT
    JSONObject execute_api_call(String call_string){

        try {
            JSONObject answer = null;

            // Here the integrity of the answer when returned from the intermediate node
            // can only be checked if the public key used every time by the LBS is the same
            // and thus it can be known by both parties (search initiator node and intermediate node)
            // In our case we have a new HTTPS connection every time and thus a different handshake takes place

            URL api_call_url = new URL(call_string);
            HttpsURLConnection connection = (HttpsURLConnection) api_call_url.openConnection();
            connection.setRequestMethod("GET");

            // Here we can print the certificate chain of the server
            // TODO: Discover why when trying to just print the certificate chain the connection establishment fails
            /*
            String server_certificat_chain = "";
            for(int i=0;i< connection.getServerCertificates().length; i++){
                server_certificat_chain += "\n";
                server_certificat_chain += connection.getServerCertificates()[i].toString();
            }
            Log.d("API CALL EXEC",server_certificat_chain);
            */

            // TODO: As the intermediate node see if I can extract the encrypted version of the answer and forward that to the search initiator
            // so along with the my own keys it can verify the integrity of the answer.
            // TODO: Ask Hongyu if here I could simply fabricate the action of the LBS signing the response it sends
            // to the intermediate node and therefore the integrity of the answer could also be proven
            // because with the HTTP connection and a BufferedReader it doesn't seem that I can returned the encrypted
            // answer. Instead it seems that it is read in plaintext.

            // from reasearch on the documentation we can't really request that the answer text we receive
            // is encrypted https://developers.google.com/maps/documentation/places/web-service/search-nearby

            int responseCode = connection.getResponseCode();
            Log.d("API CALL EXEC","HTTPS Response Code Received = " + responseCode);

            if(responseCode == HttpURLConnection.HTTP_OK ){
                Log.d("API CALL EXEC","HTTPS Response is OK!");
                BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(connection.getInputStream()) );
                StringBuilder sb = new StringBuilder();
                String line;
                while( (line=bufferedReader.readLine()) != null){
                    sb.append(line);
                }
                answer = new JSONObject(sb.toString());
            }
            else{
                Log.d("API CALL EXEC ERROR","RESPONSE NOT OK FROM HTTP SERVER");
            }

            return answer;
        }
        catch (Exception e){
            e.printStackTrace();
            Log.d("API CALL EXEC","Could not retrieve JSONobject");
            return null;
        }

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