package com.example.helloworld;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.bluetooth.BluetoothManager;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends Activity {
	
	private BluetoothAdapter mBluetoothAdapter;
  
	 
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
	    @Override
	    public void onLeScan(final BluetoothDevice device, int rssi,
	            byte[] scanRecord) {
	        runOnUiThread(new Runnable() {
	           @Override
	           public void run() {
	        	  
	        	   mBluetoothAdapter.stopLeScan(mLeScanCallback);

	        	   final Intent intent = new Intent(getApplicationContext(), DeviceControlActivity.class);
	               intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
	               intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
	            
	               startActivity(intent);

	           }
	       });
	   }

	};
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {	
        super.onCreate(savedInstanceState);

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothAdapter.startLeScan(mLeScanCallback);
         
    }
    
    

}
