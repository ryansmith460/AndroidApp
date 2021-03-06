package com.example.rysm4200.androidapp;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.RadialGradient;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import java.io.IOException;
import android.view.View;
import android.graphics.Bitmap;
import java.util.UUID;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.os.Handler;
import android.os.Message;
import android.content.Context;
import android.bluetooth.BluetoothProfile;

public class MainActivity extends Activity {
    boolean debug = true;



    //Bluetooth
    private BluetoothProfile mBluetoothProfile;
    private BluetoothAdapter adapter = null;
    private BluetoothSocket socket = null;
    private static final UUID bT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static String address = "30:14:10:15:02:96";
    BluetoothThread bT, bT2;
    public boolean bluetoothRunning = false;


    //Hard-coded image size
    int width = 320;
    int height = 240;
    int alpha = 255;

    int downSample;
    int numImagePts;

    int[] imageIntegers;
    byte[] imageBytes;
    byte[] coordinateBytes;
    byte[] robotCoordinateBytes;

    int[] intColors;

    //Region Selection Activity
    RegionSelectionActivity regionSelection;
    int GET_COORDINATES_ID = 1;

    //Settings Activity
    SettingsActivity settingsActivity;
    int GET_BOARD_COORDINATES_ID = 2;

    //Robot activity
    RobotActivity robotActivity;
    int GET_ROBOT_COORDINATES_ID = 3;

    //New Board Region
    byte[] boardRegionCode = {0};
    //Erase All
    boolean eraseAll = false;
    byte[] eraseAllCode = {1};

    //Emergency Stop
    boolean emergencyStop = true;
    byte[] emergencyStopCode = {2};

    //New Regions
    byte[] regionSelectionCode = {3};

    //Board region request
    byte[] requestBoardRegionCode = {4};

    byte[] robotRegionCode = {3};

    //request Image
    byte[] requestImageCode = {5};

    boolean failed = false;
    boolean done = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Set up Bluetooth Communication
        adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice bTDevice = adapter.getRemoteDevice(address);
        if (!debug) {
            try {
                socket = bTDevice.createRfcommSocketToServiceRecord(bT_UUID);
            } catch (IOException e) {
                failed = true;
            }

            adapter.cancelDiscovery();

            // Establish the connection.  This will block until it connects.
            try {
                socket.connect();
            } catch (IOException e) {
                failed = true;
                try {
                    socket.close();
                } catch (IOException e2) {
                    failed = true;
                }
            }
        }

            //Create Region selection activity
            regionSelection = new RegionSelectionActivity();
            regionSelection.Init(GET_COORDINATES_ID);

            //Create Settings Activity
            settingsActivity = new SettingsActivity();
            settingsActivity.Init(GET_BOARD_COORDINATES_ID);

            //create robot activity
            robotActivity = new RobotActivity();
            robotActivity.Init(GET_ROBOT_COORDINATES_ID);

    }


    @Override
    protected void onStart() {
        super.onStart();
        if (debug)
        {
           failed = false;
        }
        if (!failed && !done) {
           //tell the user that it is connected
           Context context = getApplicationContext();
           CharSequence text = "The bluetooth connection has been established.";
           int duration = Toast.LENGTH_LONG;
           Toast toast = Toast.makeText(context, text, duration);
           toast.show();
           done = true;
           }
                else {
            if (!done) {
                //tell the user that they need to get bluetooth connection
                Context context = getApplicationContext();
                CharSequence text = "Bluetooth connection FAILED.";
                int duration = Toast.LENGTH_LONG;
                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
                CharSequence text2 = "Check that Bluetooth is ON on the Android device.";
                Toast toast2 = Toast.makeText(context, text2, duration);
                toast2.show();
                CharSequence text3 = "Check that the camera module is on.";
                Toast toast3 = Toast.makeText(context, text3, duration);
                toast3.show();
                CharSequence text4 = "Then reopen the Eraser-Bot App.";
                Toast toast4 = Toast.makeText(context, text4, duration);
                toast4.show();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    //need to work on this, change whiteboard to fit robot
    public void robotButtonHandler(View view) {
        //Set up the image arrays
        Boolean isDownsampled = createImageArrays();

        //Get an image of the board
        if(!debug) {
            Context context = getApplicationContext();
            CharSequence text = "An image of the whiteboard is sending.";
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();

            bT = new BluetoothThread();
            bT.InitBluetoothThread(socket, numImagePts);
            bT.start();
            bT.startSaving();

            int code;
            if(isDownsampled)
                code =  0x01 << 4 | (int)requestImageCode[0];
            else
                code =  (int)requestImageCode[0];
            byte [] imageRequestCode = {(byte)code};

            bT.sendData(imageRequestCode);
            //Wait until image is ready, then get the image
            while (bT.getSaveStatus() == true);
            imageBytes = bT.getImage();

            bT = null;

            // another bluetooth thread for the robot coordinates
            bT = new BluetoothThread();


            //work on this
            bT.robot();
            bT.InitBluetoothThread(socket, numImagePts);
            bT.start();

            bT.startSaving();

            //robot code is 7
            code = 7;
            byte [] robotCoordinateRequestCode = {(byte)code};
            bT.sendData(robotCoordinateRequestCode);
            while (bT.getSaveStatus() == true);
            robotCoordinateBytes = bT.getRobotCoordinates();


        }

        byte tempValue;
        //Convert negative values to positives
        for (int i = 0; i < imageBytes.length; i++) {
            tempValue = imageBytes[i];
            if (tempValue < 0)
                imageIntegers[i] = (int) tempValue + 256;
            else
                imageIntegers[i] = (int) tempValue;
        }

        //Check width and height are consistent with array size
        if ((numImagePts / 3) != (width * height / downSample)) {
            throw new ArrayStoreException();
        }

        //Convert to bitmap
        for (int intIndex = 0; intIndex < numImagePts/3; intIndex = intIndex + 1) {
            intColors[intIndex] = (alpha << 24) | (imageIntegers[intIndex] << 16) | (imageIntegers[numImagePts/3+intIndex ] << 8) | imageIntegers[2*numImagePts/3+intIndex];
        }

        //Use the Robot Activity
        Intent intent = new Intent(this, robotActivity.getClass());
        intent.putExtra("COLORS", intColors);
        intent.putExtra("ROBOTCOORDINATES", robotCoordinateBytes);
        //isDownsampled = true;

        if(isDownsampled) {
            intent.putExtra("WIDTH", width / 2);
            intent.putExtra("HEIGHT", height / 2);
        }
        else
        {
            intent.putExtra("WIDTH", width);
            intent.putExtra("HEIGHT", height);
        }

        startActivityForResult(intent, GET_ROBOT_COORDINATES_ID);
    }


    //attempted button handler
    /*
    public void attemptedButtonHandler(View view) {
        //Set up the image arrays
        Boolean isDownsampled = createImageArrays();

        //Get an image of the board
        if(!debug) {
            bT = new BluetoothThread();
            bT.InitBluetoothThread(socket, numImagePts);
            bT.start();
            bT.startSaving();

            int code;
            if(isDownsampled)
                code =  0x01 << 4 | (int)requestImageCode[0];
            else
                code =  (int)requestImageCode[0];
            byte [] imageRequestCode = {(byte)code};

            bT.sendData(imageRequestCode);
            //Wait until image is ready, then get the image
            while (bT.getSaveStatus() == true);
            imageBytes = bT.getImage();

            bT = null;

            // another bluetooth thread for the coordinates
            bT = new BluetoothThread();
            bT.settings();
            bT.board();
            bT.InitBluetoothThread(socket, numImagePts);
            bT.start();
            bT.startSaving();
            code = 4;
            byte [] coordinateRequestCode = {(byte)code};
            bT.sendData(coordinateRequestCode);
            while (bT.getSaveStatus() == true);
            coordinateBytes = bT.getCoordinates();
            bT = null;

            Context context = getApplicationContext();
            CharSequence text = "An image of the whiteboard is sending.";
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();

            //new code number needed = 7
            // another bluetooth thread for the coordinates
            bT = new BluetoothThread();
            bT.settings();
            bT.InitBluetoothThread(socket, numImagePts);
            bT.start();
            bT.startSaving();
            code = 7;
            byte [] robotCoordinateRequestCode = {(byte)code};
            bT.sendData(robotCoordinateRequestCode);
            while (bT.getSaveStatus() == true);
            robotCoordinateBytes = bT.getRobotCoordinates();
            bT = null;
        }

        byte tempValue;
        //Convert negative values to positives
        for (int i = 0; i < imageBytes.length; i++) {
            tempValue = imageBytes[i];
            if (tempValue < 0)
                imageIntegers[i] = (int) tempValue + 256;
            else
                imageIntegers[i] = (int) tempValue;
        }

        //Check width and height are consistent with array size
        if ((numImagePts / 3) != (width * height / downSample)) {
            throw new ArrayStoreException();
        }

        //Convert to bitmap
        for (int intIndex = 0; intIndex < numImagePts/3; intIndex = intIndex + 1) {
            intColors[intIndex] = (alpha << 24) | (imageIntegers[intIndex] << 16) | (imageIntegers[numImagePts/3+intIndex ] << 8) | imageIntegers[2*numImagePts/3+intIndex];
        }

        //Use the Settings Activity
        Intent intent = new Intent(this, settingsActivity.getClass());
        intent.putExtra("COLORS", intColors);
        intent.putExtra("COORDINATES", coordinateBytes);
        intent.putExtra("ROBOTCOORDINATES",robotCoordinateBytes);

        if(isDownsampled) {
            intent.putExtra("WIDTH", width / 2);
            intent.putExtra("HEIGHT", height / 2);
        }
        else
        {
            intent.putExtra("WIDTH", width);
            intent.putExtra("HEIGHT", height);
        }

        startActivityForResult(intent, GET_BOARD_COORDINATES_ID);
    }
*/


    //THIS CODE WORKS-- OLD CODE (settings button)
    //Settings button handler
    public void whiteboardButtonHandler(View view) {
        //Set up the image arrays
        Boolean isDownsampled = createImageArrays();

        //Get an image of the board
        if(!debug) {
            Context context = getApplicationContext();
            CharSequence text = "An image of the whiteboard is sending.";
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();

            bT = new BluetoothThread();
            bT.InitBluetoothThread(socket, numImagePts);
            bT.start();
            bT.startSaving();

            int code;
            if(isDownsampled)
                code =  0x01 << 4 | (int)requestImageCode[0];
            else
                code =  (int)requestImageCode[0];
            byte [] imageRequestCode = {(byte)code};

            bT.sendData(imageRequestCode);
            //Wait until image is ready, then get the image
            while (bT.getSaveStatus() == true);
            imageBytes = bT.getImage();

            bT = null;

            // another bluetooth thread for the coordinates
            bT = new BluetoothThread();

            bT.whiteboard();
            bT.InitBluetoothThread(socket, numImagePts);
            bT.start();

            bT.startSaving();


            code = 4;
            byte [] coordinateRequestCode = {(byte)code};
            bT.sendData(coordinateRequestCode);
            while (bT.getSaveStatus() == true);
            coordinateBytes = bT.getCoordinates();


        }

        byte tempValue;
        //Convert negative values to positives
        for (int i = 0; i < imageBytes.length; i++) {
            tempValue = imageBytes[i];
            if (tempValue < 0)
                imageIntegers[i] = (int) tempValue + 256;
            else
                imageIntegers[i] = (int) tempValue;
        }

        //Check width and height are consistent with array size
        if ((numImagePts / 3) != (width * height / downSample)) {
            throw new ArrayStoreException();
        }

        //Convert to bitmap
        for (int intIndex = 0; intIndex < numImagePts/3; intIndex = intIndex + 1) {
            intColors[intIndex] = (alpha << 24) | (imageIntegers[intIndex] << 16) | (imageIntegers[numImagePts/3+intIndex ] << 8) | imageIntegers[2*numImagePts/3+intIndex];
        }

        //Use the Settings Activity
        Intent intent = new Intent(this, settingsActivity.getClass());
        intent.putExtra("COLORS", intColors);
        intent.putExtra("COORDINATES", coordinateBytes);

        if(isDownsampled) {
            intent.putExtra("WIDTH", width / 2);
            intent.putExtra("HEIGHT", height / 2);
        }
        else
        {
            intent.putExtra("WIDTH", width);
            intent.putExtra("HEIGHT", height);
        }

        startActivityForResult(intent, GET_BOARD_COORDINATES_ID);
    }
    //THIS CODE WORKS

    //Region selection button handler
    public void regionSelectionButtonHandler(View view) {

        //Set up the image arrays
        Boolean isDownsampled = createImageArrays();
        if(!debug) {
            bT = new BluetoothThread();
            bT.InitBluetoothThread(socket, numImagePts);
            bT.start();
            //    bluetoothRunning = true;
            //}
            bT.startSaving();

            int code;
            if(isDownsampled)
                code =  0x01 << 4 | (int)requestImageCode[0];
            else
                code =  (int)requestImageCode[0];
            byte [] imageRequestCode = {(byte)code};

            bT.sendData(imageRequestCode);

            //Wait until image is ready, then get the image

            while (bT.getSaveStatus() == true) ;
            imageBytes = bT.getImage();
        }

        byte tempValue;
        //Convert negative values to positives
        for (int i = 0; i < imageBytes.length; i++) {
            tempValue = imageBytes[i];
            if (tempValue < 0)
                imageIntegers[i] = (int) tempValue + 256;
            else
                imageIntegers[i] = (int) tempValue;
        }

        //Check width and height are consistent with array size
        if ((numImagePts / 3) != (width * height / downSample)) {
            throw new ArrayStoreException();
        }

        //Convert to bitmap
        for (int intIndex = 0; intIndex < numImagePts/3; intIndex = intIndex + 1) {
            intColors[intIndex] = (alpha << 24) | (imageIntegers[intIndex] << 16) | (imageIntegers[numImagePts/3+intIndex ] << 8) | imageIntegers[2*numImagePts/3+intIndex];
        }

        //Use the Region Selection Activity to get the regions
        Intent intent = new Intent(this, regionSelection.getClass());
        intent.putExtra("COLORS", intColors);

        if(isDownsampled) {
            intent.putExtra("WIDTH", width / 2);
            intent.putExtra("HEIGHT", height / 2);
        }
        else
        {
            intent.putExtra("WIDTH", width);
            intent.putExtra("HEIGHT", height);
        }
        startActivityForResult(intent, GET_COORDINATES_ID);

        Context context = getApplicationContext();
        CharSequence text = "An image of the whiteboard is sending.";
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    //Used to send coordinates defined in RegionSelectionActivity
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if(requestCode == GET_ROBOT_COORDINATES_ID)
        {
            if(resultCode == RESULT_OK)
            {
                //Set up the image arrays
                Boolean isDownsampled = createImageArrays();

                //Get an image of the board
                if(!debug) {
                    Context context = getApplicationContext();
                    CharSequence text = "An image of the whiteboard is sending.";
                    int duration = Toast.LENGTH_SHORT;
                    Toast toast = Toast.makeText(context, text, duration);
                    toast.show();

                    bT = new BluetoothThread();
                    bT.InitBluetoothThread(socket, numImagePts);
                    bT.start();
                    bT.startSaving();

                    int code;
                    if(isDownsampled)
                        code =  0x01 << 4 | (int)requestImageCode[0];
                    else
                        code =  (int)requestImageCode[0];
                    byte [] imageRequestCode = {(byte)code};

                    bT.sendData(imageRequestCode);
                    //Wait until image is ready, then get the image
                    while (bT.getSaveStatus() == true);
                    imageBytes = bT.getImage();

                    bT = null;

                    // another bluetooth thread for the robot coordinates
                    bT = new BluetoothThread();


                    //work on this
                    bT.robot();
                    bT.InitBluetoothThread(socket, numImagePts);
                    bT.start();

                    bT.startSaving();

                    //robot code is 7
                    code = 7;
                    byte [] robotCoordinateRequestCode = {(byte)code};
                    bT.sendData(robotCoordinateRequestCode);
                    while (bT.getSaveStatus() == true);
                    robotCoordinateBytes = bT.getRobotCoordinates();


                }

                byte tempValue;
                //Convert negative values to positives
                for (int i = 0; i < imageBytes.length; i++) {
                    tempValue = imageBytes[i];
                    if (tempValue < 0)
                        imageIntegers[i] = (int) tempValue + 256;
                    else
                        imageIntegers[i] = (int) tempValue;
                }

                //Check width and height are consistent with array size
                if ((numImagePts / 3) != (width * height / downSample)) {
                    throw new ArrayStoreException();
                }

                //Convert to bitmap
                for (int intIndex = 0; intIndex < numImagePts/3; intIndex = intIndex + 1) {
                    intColors[intIndex] = (alpha << 24) | (imageIntegers[intIndex] << 16) | (imageIntegers[numImagePts/3+intIndex ] << 8) | imageIntegers[2*numImagePts/3+intIndex];
                }

                //Use the Robot Activity
                Intent intent = new Intent(this, robotActivity.getClass());
                intent.putExtra("COLORS", intColors);
                intent.putExtra("ROBOTCOORDINATES", robotCoordinateBytes);
                //isDownsampled = true;

                if(isDownsampled) {
                    intent.putExtra("WIDTH", width / 2);
                    intent.putExtra("HEIGHT", height / 2);
                }
                else
                {
                    intent.putExtra("WIDTH", width);
                    intent.putExtra("HEIGHT", height);
                }

                startActivityForResult(intent, GET_ROBOT_COORDINATES_ID);

                //Use the Robot Activity
                //byte [] robotCoordinates = data.getByteArrayExtra("robotCoordinates");
                //int nRegions = data.getIntExtra("nRegions", 0);
                //Send the coordinates to the Camera module

                //int code = nRegions << 4 | (int)regionSelectionCode[0];
                //byte [] regionCode = {(byte)code};

                if(!debug) {
                    //bT.sendData(robotRegionCode);

                    //if (!bT.sendData(robotCoordinateBytes))
                    {
                        //Display error message
                    }
                }
            }
        }
        if(requestCode == GET_COORDINATES_ID)
        {
            if(resultCode == RESULT_OK)
            {
                byte [] coordinates = data.getByteArrayExtra("Coordinates");
                int nRegions = data.getIntExtra("nRegions", 0);
                //Send the coordinates to the Camera module

                int code = nRegions << 4 | (int)regionSelectionCode[0];
                byte [] regionCode = {(byte)code};

                if(!debug) {
                    bT.sendData(regionCode);

                    if (!bT.sendData(coordinates)) {
                        //Display error message
                    }
                }
            }
        }

        if(requestCode == GET_BOARD_COORDINATES_ID)
        {
            if(resultCode == RESULT_OK)
            {
                byte [] coordinates = data.getByteArrayExtra("Coordinates");
                //Send the coordinates to the Camera module

                if(!debug) {
                    bT.sendData(boardRegionCode);

                    if (!bT.sendData(coordinates)) {
                        //Display error message
                    }
                }
            }
        }

        //close out the thread
        if(!debug) {
            bT = null;
            //bT2 = null;
        }
    }

    public void eraseAllButtonHandler(View view)
    {
        if(!debug) {
            bT = new BluetoothThread();
            bT.InitBluetoothThread(socket, numImagePts);
            bT.start();
        }
        eraseAll = true;
        if (eraseAll == true)
        {
            if(!debug) {
                bT.sendData(eraseAllCode);
            }
            else {
                Log.i("EraseAll", "The command for erasing all writing has been sent.");
            }
            eraseAll = false;
        }
        if(!debug) {
            bT=null;
        }
        Context context = getApplicationContext();
        CharSequence text = "The robot will now erase the entire whiteboard.";
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();

    }

    public void emergencyStopButtonHandler(View view) {
        if(!debug) {
            bT = new BluetoothThread();
            bT.InitBluetoothThread(socket, numImagePts);
            bT.start();
        }
        emergencyStop = true;
        if (emergencyStop == true)
        {
            if(!debug) {
                bT.sendData(emergencyStopCode);
            }
            else {
                Log.i("EmergencyStop", "The command for emergency stop has been sent.");
            }
            emergencyStop = false;
        }
        if(!debug) {
            bT=null;
        }
        Context context = getApplicationContext();
        CharSequence text = "The robot will now STOP.";
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }


    private Boolean createImageArrays()
    {
        CheckBox cb = (CheckBox)findViewById(R.id.cbDownSample);
        if(cb.isChecked() == true)
            downSample = 4;
        else
            downSample = 1;

        numImagePts = width * height * 3 / downSample;


        imageIntegers = new int[numImagePts];
        imageBytes = new byte[numImagePts];
        //bar.setMax(imageBytes.length);
        intColors = new int[numImagePts / 3];

        return cb.isChecked();
    }


}
