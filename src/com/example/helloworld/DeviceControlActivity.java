package com.example.helloworld;

import com.firebase.client.Firebase;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import sample.ble.sensortag.sensor.*;
public class DeviceControlActivity extends Activity {
    private final static String TAG = "BETH"; //DeviceControlActivity.class.getSimpleName();
 
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
 
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
 
    boolean sensorOn = false;
    
    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
 
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }
 
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };
 
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }

    /*
     * When you touch the glass keypad, start listening for magnetometer data
     */
	@Override
    public boolean onKeyDown(int keycode, KeyEvent event) {
		Log.i("BLE", "on key down");
		TiAccelerometerSensor sensor = new TiAccelerometerSensor();
		//TiTemperatureSensor tempSensor = new TiTemperatureSensor();
		//TiHumiditySensor humiditySensor = new TiHumiditySensor();
		if (sensorOn == false) {
			SystemClock.sleep(1000);
			mBluetoothLeService.turnOnSensor(sensor.getServiceUUID(), sensor.getConfigUUID());
			SystemClock.sleep(1000);
			//mBluetoothLeService.turnOnSensor(tempSensor.getServiceUUID(), tempSensor.getConfigUUID());
			//SystemClock.sleep(1000);
			//mBluetoothLeService.turnOnSensor(humiditySensor.getServiceUUID(), humiditySensor.getConfigUUID());
			//SystemClock.sleep(1000);
			mBluetoothLeService.writePeriod(sensor.getServiceUUID(), sensor.UUID_PERIOD);
			SystemClock.sleep(1000);
			mBluetoothLeService.enableSensorNotifications(sensor.getServiceUUID(), sensor.getDataUUID());
			//SystemClock.sleep(1000);
			//mBluetoothLeService.enableSensorNotifications(tempSensor.getServiceUUID(), tempSensor.getDataUUID());
			//SystemClock.sleep(1000);
			//mBluetoothLeService.enableSensorNotifications(humiditySensor.getServiceUUID(), humiditySensor.getDataUUID());
			SystemClock.sleep(1000);
			Log.i("BLE", "turn on magnetometer");
			sensorOn = true;
		}
		else {
			
			Log.i("BLE", "enable notifications");
		}
        return false;
         
    }
	
	 @Override
	    public void onStop() {
	        super.onStop();
	    }
	
	

	
}