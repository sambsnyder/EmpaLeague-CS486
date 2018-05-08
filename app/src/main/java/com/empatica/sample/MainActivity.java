package com.empatica.sample;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;
// added imports
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.*;
import android.view.KeyEvent;


public class MainActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSION_ACCESS_COARSE_LOCATION = 1;

    // Variables added
    private int counter = 0;
    private int counter1 = 0 ;
    private int counter2 = 0;

    private float heart_rate;
    private float f = 0;
    private float simple_moving_average = 0;
    private float threshold;
    // Array list for hear rate values for SMA
    ArrayList<Float> list = new ArrayList<>(Collections.nCopies(15, f));
    // String array for writing HR's to csv
    String hrCSV[] = new String[5000];
    // A counter for adding the strings to hrCSV to write to txt file.
    int i = 0 ;
    // All the variables for creating and making txt file
    String path = Environment.getExternalStorageDirectory().getAbsolutePath()+"/test";
    File file;
    FileOutputStream fos = null;
    // Boolean to make sure the first SMA calculation is done on full list
    // Other booleans for writing the song start and song stop markers to the txt file.
    private boolean listFilledOnce, songStart, songStop;

    // date format for the timestamps of HR values in txt file
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss");
    // Stops streaming in 20 min.
    // 1200000ms = 20 min
    private static final long STREAMING_TIME = 1500000;

    private static final String EMPATICA_API_KEY = ""; // TODO insert your API Key here

    private EmpaDeviceManager deviceManager = null;

    private TextView accel_xLabel;
    private TextView accel_yLabel;
    private TextView accel_zLabel;
    private TextView bvpLabel;
    private TextView edaLabel;
    private TextView ibiLabel;
    private TextView temperatureLabel;
    private TextView batteryLabel;
    private TextView statusLabel;
    private TextView deviceNameLabel;
    private RelativeLayout dataCnt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // TXT file and path creation
        File dir = new File(path);
        dir.mkdirs();
        file = new File(path + "/savedFile.txt");

        // Initialize vars that reference UI components
        statusLabel = (TextView) findViewById(R.id.status);
        dataCnt = (RelativeLayout) findViewById(R.id.dataArea);
        accel_xLabel = (TextView) findViewById(R.id.accel_x);
        accel_yLabel = (TextView) findViewById(R.id.accel_y);
        accel_zLabel = (TextView) findViewById(R.id.accel_z);
        bvpLabel = (TextView) findViewById(R.id.bvp);
        edaLabel = (TextView) findViewById(R.id.eda);
        ibiLabel = (TextView) findViewById(R.id.ibi);
        temperatureLabel = (TextView) findViewById(R.id.temperature);
        batteryLabel = (TextView) findViewById(R.id.battery);
        deviceNameLabel = (TextView) findViewById(R.id.deviceName);

        initEmpaticaDeviceManager();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_ACCESS_COARSE_LOCATION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted, yay!
                    initEmpaticaDeviceManager();
                } else {
                    // Permission denied, boo!
                    final boolean needRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION);
                    new AlertDialog.Builder(this)
                            .setTitle("Permission required")
                            .setMessage("Without this permission bluetooth low energy devices cannot be found, allow it in order to connect to the device.")
                            .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // try again
                                    if (needRationale) {
                                        // the "never ask again" flash is not set, try again with permission request
                                        initEmpaticaDeviceManager();
                                    } else {
                                        // the "never ask again" flag is set so the permission requests is disabled, try open app settings to enable the permission
                                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                                        intent.setData(uri);
                                        startActivity(intent);
                                    }
                                }
                            })
                            .setNegativeButton("Exit application", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // without permission exit is the only way
                                    finish();
                                }
                            })
                            .show();
                }
                break;
        }
    }

    private void initEmpaticaDeviceManager() {
        // Android 6 (API level 23) now require ACCESS_COARSE_LOCATION permission to use BLE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_COARSE_LOCATION }, REQUEST_PERMISSION_ACCESS_COARSE_LOCATION);
        } else {
            // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
            deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);

            if (TextUtils.isEmpty(EMPATICA_API_KEY)) {
                new AlertDialog.Builder(this)
                        .setTitle("Warning")
                        .setMessage("Please insert your API KEY")
                        .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // without permission exit is the only way
                                finish();
                            }
                        })
                        .show();
                return;
            }
            // Initialize the Device Manager using your API key. You need to have Internet access at this point.
            deviceManager.authenticateWithAPIKey(EMPATICA_API_KEY);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (deviceManager != null) {
            deviceManager.stopScanning();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceManager != null) {
            deviceManager.cleanUp();
        }

    }

    @Override
    public void didDiscoverDevice(BluetoothDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
        // Check if the discovered device can be used with your API key. If allowed is always false,
        // the device is not linked with your API key. Please check your developer area at
        // https://www.empatica.com/connect/developer.php
        if (allowed) {
            // Stop scanning. The first allowed device will do.
            deviceManager.stopScanning();
            try {
                // Connect to the device
                deviceManager.connectDevice(bluetoothDevice);
                updateLabel(deviceNameLabel, "To: " + deviceName);
            } catch (ConnectionNotAllowedException e) {
                // This should happen only if you try to connect when allowed == false.
                Toast.makeText(MainActivity.this, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void didRequestEnableBluetooth() {
        // Request the user to enable Bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The user chose not to enable Bluetooth
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // You should deal with this
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void didUpdateSensorStatus(EmpaSensorStatus status, EmpaSensorType type) {
        // No need to implement this right now
    }

    @Override
    public void didUpdateStatus(EmpaStatus status) {
        // Update the UI
        updateLabel(statusLabel, status.name());

        // The device manager is ready for use
        if (status == EmpaStatus.READY) {
            updateLabel(statusLabel, status.name() + " - Turn on your device");
            // Start scanning
            deviceManager.startScanning();
            // The device manager has established a connection
        } else if (status == EmpaStatus.CONNECTED) {
            // Stop streaming after STREAMING_TIME
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dataCnt.setVisibility(View.VISIBLE);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // WHEN THE DEVICE DC THE INFORMATION IS WRITTEN TO FILE
                            writeHRtoCSV(hrCSV);
                            // Disconnect device
                            deviceManager.disconnect();
                        }
                    }, STREAMING_TIME);
                }
            });
            // The device manager disconnected from a device
        } else if (status == EmpaStatus.DISCONNECTED) {
            updateLabel(deviceNameLabel, "");
        }
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        updateLabel(accel_xLabel, "" + x);
        updateLabel(accel_yLabel, "" + y);
        updateLabel(accel_zLabel, "" + z);
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        updateLabel(bvpLabel, "" + bvp);
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        updateLabel(batteryLabel, String.format("%.0f %%", battery * 100));
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        updateLabel(edaLabel, "" + gsr);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        // If the list is full and the threshold hasn't already been calculated
        // calculate it.
        System.out.println("Recieved");
        updateLabel(ibiLabel, "" + ibi);
        // calculate and print the heart rate to console.
        heart_rate = 60000/(ibi*1000);
        // new time everytime we get a HR Value
        String time = simpleDateFormat.format(new Date());
        // if the calculated HR value is not 0 add to the array list and also to the string array
        if(heart_rate>0) {
            hrCSV[i] = Float.toString(heart_rate) +";"+ time;
            if(songStart){
                songStart=false;
                hrCSV[i]+=(";song start"); // Concatenate to the end of the string a marker for the song stopping
                System.out.println(hrCSV[i].toString());
            }
            if(songStop){
                songStop=false;
                hrCSV[i]+=(";song stop"); // Concatenate to the end of the string a marker for the song stopping
                System.out.println(hrCSV[i].toString());
            }
            // Storing heart rate into the array list for SMA
            list.set(counter, heart_rate);
        }
        // Calculate threshold if the list has been filled and the threshold hasnt already been calculated.
        if(listFilledOnce && counter2<1 && list.get(14)>f){
            counter2++;
            threshold = (float) ((simple_moving_average*0.12)+simple_moving_average); // Currently at a 12% increase in HR
            System.out.println("THRESHOLD CALCULATED =" +threshold);
        }
        // Calculate HR and then check if greater than threshold
        // If true increment counter1 and start playing music from spotify
        // Counter makes sure that spotify will not be launched repeatedly.
        if(simple_moving_average>threshold && counter1<1 && counter2 > 0){
            counter1++;
            songStart=true;
            String track = "spotify:track:6A6vSsLkXoTJZ8cA4vtznl";
            Intent launcher = new Intent(Intent.ACTION_VIEW, Uri.parse(track));
            System.out.println("Increase in heart Rate confirmed: Music START");
            // launch spotify
            startActivityForResult(launcher,1001);
        }
        // after the song plays pause the music and kill the activity
        // this block of code checks to see if the music is playing
        // if the music is playing it starts a timer to stop the music and kill Spotify after the song finishes
        // The counter1 makes sure we only do this once.
        if(isSpotifyRunning()&&counter1==1) {
            counter1++;
            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            songStop=true;
                            if (isSpotifyRunning()) {
                                int keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
                                Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
                                i.setPackage("com.spotify.music");
                                synchronized (this) {
                                    i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
                                    sendOrderedBroadcast(i, null);
                                    i.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, keyCode));
                                    sendOrderedBroadcast(i, null);
                                }
                            }
                            finishActivity(1001);
                        }
                    },
                    287000 // the length of the song 287000
            );

        }
        // Increment i to go to the next element in string array
        // Increment the counter for adding values to the array list
        i++;
        counter++;
        // Reset the counter to the beginning of the list if we are at capacity
        // This allows us to start replacing the values at the beginning of the list
        // aka replacing the oldest HR data with newest ones
        if(counter >= 15){
            System.out.println("LIST FILLED!!!!!!");
            // This boolean is set to true in order to know when to calculate the threshold for the first time
            listFilledOnce = true;
            counter = 0;
        }
        // get the moving average only if there is a value in the list and the list is full
        // I make sure the list is full and the first value is not 0
        if(listFilledOnce && list.get(0)>f){
            simpleMovingAverage(list);
            System.out.println("SMA = "+simple_moving_average);
        }
    }
    // method used to write to the new file on the device
    // it may be called write to CSV but it really just writes to a txt file
    // Takes the String array input and writes to the file
    private void writeHRtoCSV(String[] data){
        // try a new file stream
        try{
            fos = new FileOutputStream(file);
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }
        try{
            try{
                // Up to the length of the string array write everything
                // If null break
                for(int i = 0; i<data.length; i++){
                    if(data[i]==null){
                        break;
                    }else{
                        fos.write((data[i]+"\n").getBytes());
                    }
                }
            }
            catch (IOException e) {e.printStackTrace();}
        }finally{
            try{
                fos.close();
            }
            catch (IOException e) {e.printStackTrace();}
        }
    }

    // This function checks if spotify is currently running on the device and returns a boolean
    // Fairly straight forward
    private boolean isSpotifyRunning() {
        Process ps = null;
        String TAG = "RIP";
        try {
            String[] cmd = {
                    "sh",
                    "-c",
                    "ps | grep com.spotify.music"
            };
            ps = Runtime.getRuntime().exec(cmd);
            ps.waitFor();

            return ps.exitValue() == 0;
        } catch (IOException e) {
            Log.e(TAG, "Could not execute ps", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "Could not execute ps", e);
        } finally {
            if (ps != null) {
                ps.destroy();
            }
        }
        return false;
    }


    // Simple moving average for the heart rate values
    // This method is NOT Implemented yet, need to change threshold as well
    public float simpleMovingAverage(ArrayList<Float> list){
        // calculating the average
        simple_moving_average = average(list);
        // if the threshold has been reached
        if(simple_moving_average>threshold && threshold  >f){
            System.out.println("Threshold is:" +threshold+"THRESHOLD REACHED SMA --------------------------------------------------");
        }
        // return
        return simple_moving_average;

    }

    // calculates the average of an arraylist of floats and returns a double
    public float average(ArrayList<Float> list){
        float average = 0;
        float sum = 0;
        // Calculating the sum
        for (float f : list) {
            sum += f;
        }
        // calculating the average
        average = sum / list.size();
        return average;
    }

    @Override
    public void didReceiveTemperature(float temp, double timestamp) {
        updateLabel(temperatureLabel, "" + temp);
    }

    // Update a label with some text, making sure this is run in the UI thread
    private void updateLabel(final TextView label, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                label.setText(text);
            }
        });
    }
}
