package com.dev.huertix.domoticac;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.BatteryManager;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity implements SensorEventListener{

    private static final String GPS_SIGNAL_SOURCE = "gps";
    private static final int MIN_TIME_BW_UPDATES_GPS = 500;
    private static final int MIN_DISTANCE_CHANGE_FOR_UPDATES_GPS = 0;
    private static final String SERVER_URL  = "http://hmkcode.appspot.com/jsont\"";
    private LocationManager locationManager;
    private TextView gpsTextView;
    private TextView gpsStatusView;
    private TextView acelerometerView;

    private Location mloc;
    private Location fixedLocation;
    private LocationListener gpslocationListener;

    private DataObject dataObject;


    private static final int UPDATE_THRESHOLD_ACELEROMETER = 100;
    private float x, y, z;
    private float maxY;
    private SensorManager sensorManager;
    private Sensor acelerometer;
    private Context context;
    private long mLastUpdate;

    private boolean isSearching;


    private BroadcastReceiver  bcr = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra("level", 0);

            dataObject.setBatteryLevel(String.valueOf(level));

            ProgressBar pb = (ProgressBar) findViewById(R.id.progressBar1);
            TextView tv = (TextView) findViewById(R.id.textView1);

            pb.setProgress(level);
            tv.setText("Battery Level: " + Integer.toString(level) + "%");

            // Are we charging / charged?
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            TextView chargerStatus = (TextView) findViewById(R.id.textView_bat_status);
            String statusString = (isCharging? "Charging" : "Discharging");

            dataObject.setCharger(statusString);
            chargerStatus.setText("Charger: "+statusString);



            // How are we charging?
            int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
            boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;



        }
    };




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dataObject = new DataObject();

        gpsTextView = (TextView) findViewById(R.id.textView3);

        gpsStatusView = (TextView) findViewById(R.id.textView_gps_status);

        acelerometerView = (TextView) findViewById(R.id.textView_acelerometer);


        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        acelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, acelerometer, SensorManager.SENSOR_DELAY_UI);

        registerReceiver(bcr, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                maxY = 0;

                if(mloc != null){

                    fixedLocation = new Location(mloc);

                }
            }
        });


    }

    @Override
    public void onResume() throws SecurityException{
        super.onResume();

        boolean isGPSEnabled;

        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);

        isGPSEnabled = locationManager
                .isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (isGPSEnabled)
        {
            if (locationManager != null)
            {

                // Register GPSStatus listener for events
                locationManager.addGpsStatusListener(mGPSStatusListener);
                gpslocationListener = new LocationListener()
                {
                    public void onLocationChanged(Location loc) {

                        mloc = loc;

                        if (loc.getProvider().equals("gps")) {
                            //FROM GPS
                            if(true){//GPS called first time after Enable

                                isSearching=false;
                                //gpsStatusView.setText("GPS Locked ("+getSatsNum()+")");

                                Calendar c = Calendar.getInstance();
                                int min = c.get(Calendar.MINUTE);
                                int seconds = c.get(Calendar.SECOND);

                                dataObject.setLat(loc.getLatitude());
                                dataObject.setLng(loc.getLongitude());

                                String string = "Lat: "+ String.valueOf(loc.getLatitude())+
                                        "\nLng: "+ String.valueOf(loc.getLongitude())+
                                        "\nBearing: "+ String.valueOf(loc.getBearing())+
                                        "\nAlt: "+ String.valueOf(loc.getAltitude())+
                                        "\nSpeed: "+ String.valueOf(loc.getSpeed())+
                                        "\nAccuracy: "+ String.valueOf(loc.getAccuracy())+
                                        "\nUpdated: "+min+":"+seconds;

                                if(fixedLocation != null){
                                    string += "\nDist: "+(String.valueOf(loc.distanceTo(fixedLocation)));
                                }else{
                                    string += "\nDist: "+("null");
                                }


                                gpsTextView.setText(string);
                            }
                        } else {
                            //FROM NETWORK

                        }


                    }

                    public void onStatusChanged(String provider, int status, Bundle extras) {

                    }
                    public void onProviderEnabled(String provider) {

                    }
                    public void onProviderDisabled(String provider) {

                    }
                };

                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        MIN_TIME_BW_UPDATES_GPS, MIN_DISTANCE_CHANGE_FOR_UPDATES_GPS,
                        gpslocationListener);
            }
        }else{

                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));

        }

        new MyAsyncTask().execute(SERVER_URL);


    }

    @Override
    protected void onPause(){

        super.onPause();
    }

    public int getSatsNum(){

        int satellites = 0;

        if(locationManager != null) {
            GpsStatus gpsStatus = locationManager.getGpsStatus(null);
            Iterable<GpsSatellite> gpsSatellite = gpsStatus.getSatellites();
            Iterator<GpsSatellite> satIterator = gpsSatellite.iterator();

            while (satIterator.hasNext()) {

                GpsSatellite sat = (GpsSatellite) satIterator.next();

                if (sat.usedInFix()) {
                    satellites++;
                }
            }
        }

        return satellites;

    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){

            long actualtime = System.currentTimeMillis();

            if(actualtime - mLastUpdate > UPDATE_THRESHOLD_ACELEROMETER){

                mLastUpdate = actualtime;

                x = event.values[0];
                y = event.values[1];
                z = event.values[2];

                if(y > maxY ){
                    maxY = y;
                }

                acelerometerView.setText("Y: "+y+"\nmaxY: "+maxY);

            }


        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }



    public GpsStatus.Listener mGPSStatusListener = new GpsStatus.Listener(){

        String  gpsString = "";

        public void onGpsStatusChanged(int event)
        {
            switch(event)
            {
                case GpsStatus.GPS_EVENT_STARTED:
                    Toast.makeText(getBaseContext(), "GPS_SEARCHING", Toast.LENGTH_SHORT).show();
                    //System.out.println("TAG - GPS searching: ");

                    break;
                case GpsStatus.GPS_EVENT_STOPPED:
                    //System.out.println("TAG - GPS Stopped");

                    break;
                case GpsStatus.GPS_EVENT_FIRST_FIX:

                /*
                 * GPS_EVENT_FIRST_FIX Event is called when GPS is locked
                 */
                    Toast.makeText(getBaseContext(), "GPS_LOCKED", Toast.LENGTH_SHORT).show();

                    try {

                        Location gpslocation = locationManager
                                .getLastKnownLocation(LocationManager.GPS_PROVIDER);

                        if(gpslocation != null)
                        {

                            gpsString = "Locked";
                        /*
                         * Removing the GPS status listener once GPS is locked
                         */
                            locationManager.removeGpsStatusListener(mGPSStatusListener);
                        }

                        break;
                    }catch(SecurityException e){
                        e.printStackTrace();
                    }
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    //                 System.out.println("TAG - GPS_EVENT_SATELLITE_STATUS");


                    int satsN = getSatsNum();

                    if(satsN < 4){
                        gpsString = "Searching ";
                        gpsTextView.setText("");
                    }

                    gpsStatusView.setText(gpsString+"("+getSatsNum()+")");
                    dataObject.setGpsStatus(gpsString);
                    break;
            }
        }
    };

    public static String POST(String urlString, DataObject data){

        String result = "";
        try {


            URL url = new URL(urlString);

            String json = "";

            // 3. build jsonObject
            JSONObject jsonObject = new JSONObject();
            jsonObject.accumulate("lat", data.getLat());
            jsonObject.accumulate("lng", data.getLng());
            jsonObject.accumulate("name", data.getGpsStatus());
            jsonObject.accumulate("country", data.getCharger());
            jsonObject.accumulate("twitter", data.getBatteryLevel());

            // 4. convert JSONObject to JSON to String
            json = jsonObject.toString();


            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout( 10000 /*milliseconds*/ );
            conn.setConnectTimeout( 15000 /* milliseconds */ );
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(json.getBytes().length);

            //make some HTTP header nicety
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");

            //open
            conn.connect();

            //setup send
            OutputStream os = new BufferedOutputStream(conn.getOutputStream());
            os.write(json.getBytes());
            //clean up
            os.flush();

            //do somehting with response
            InputStream is = conn.getInputStream();
            String contentAsString = convertInputStreamToString(is);




            // 10. convert inputstream to string
            if(is != null)
                result = contentAsString;
            else
                result = "Did not work!";

            os.close();
            is.close();
            conn.disconnect();

        } catch (Exception e) {
            Log.d("InputStream", e.getLocalizedMessage());
        }

        // 11. return result
        return result;
    }

    private static String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();
        return result;

    }

    public boolean isConnected(){
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Activity.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected())
            return true;
        else
            return false;
    }


    private class MyAsyncTask  extends AsyncTask<String, Void, String> {


        @Override
        protected void onProgressUpdate(Void... values) {
            // TODO Auto-generated method stub
            if (!isCancelled()) {

            }

        }

        @Override
        protected String doInBackground(String... urls) {
            // TODO Auto-generated method stub

            while(!isCancelled()){


                String aux = POST(urls[0], dataObject );
                return aux;
            }

            return null;
        }


        @Override
        protected void onPostExecute(String result) {
            // TODO Auto-generated method stub
            Log.d("url",result);
            super.onPostExecute(result);
        }

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
        }


    }


}
