package com.example.helloworld;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
 
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import sample.ble.sensortag.sensor.TiAccelerometerSensor;
import sample.ble.sensortag.sensor.TiHumiditySensor;
import sample.ble.sensortag.sensor.TiTemperatureSensor;

import com.firebase.client.Firebase;

import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_SINT8;
import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = "BLE";
 
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    private int numVals =0;
    private int numVals2 =0;
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
 
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
	private final long START_TIME = System.nanoTime();
	private double previousReading = System.nanoTime();
    Firebase accData = new Firebase("https://labapp.firebaseio.com/accData");
    Firebase tempData = new Firebase("https://labapp.firebaseio.com/tempData");
    Firebase humidityData = new Firebase("https://labapp.firebaseio.com/humidityData");
    public static abstract class ServiceAction {
        public enum ActionType {
            NONE,
            READ,
            NOTIFY,
            WRITE
        }

        public static final ServiceAction NULL = new ServiceAction(ActionType.NONE) {
            @Override
            public boolean execute(BluetoothGatt bluetoothGatt) {
                // it is null action. do nothing.
                return true;
            }
        };

        private final ActionType type;

        public ServiceAction(ActionType type) {
            this.type = type;
        }

        public ActionType getType() {
            return type;
        }

        /***
         * Executes action.
         * @param bluetoothGatt
         * @return true - if action was executed instantly. false if action is waiting for
         *         feedback.
         */
        public abstract boolean execute(BluetoothGatt bluetoothGatt);
    }
    

    
    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
    	
    	private final LinkedList<BluetoothLeService.ServiceAction> queue = new LinkedList<ServiceAction>();
        private volatile ServiceAction currentAction;
            
        public void execute(BluetoothGatt gatt) {
            if (currentAction != null)
                return;

            boolean next = !queue.isEmpty();
            while (next) {
                final BluetoothLeService.ServiceAction action = queue.pop();
                currentAction = action;
                if (!action.execute(gatt))
                    break;

                currentAction = null;
                next = !queue.isEmpty();
            }
        }
        
        
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        	Log.i(TAG, "onConnectionStateChange");
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
 
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                queue.clear();
            }
        }
 
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        	Log.i(TAG, "onServicesDiscovered");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }
 
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
        	Log.i(TAG, "onCharacteristicRead");
        	
        	// wait for onCharacteristicWrite for write action before execution of any other actions
            if (currentAction != null && currentAction.getType() == ServiceAction.ActionType.WRITE)
                return;

            currentAction = null;
            execute(gatt);
        	
        }
 
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
        	Log.i(TAG, "onCharacteristicWrite");
        	super.onCharacteristicWrite(gatt, characteristic, status);
        	currentAction = null;
            execute(gatt);
            
        }
        
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        	Log.i(TAG, "onDescriptorWrite");
            super.onDescriptorWrite(gatt, descriptor, status);
            
            // wait for onCharacteristicWrite for write action before execution of any other actions
            if (currentAction != null && currentAction.getType() == ServiceAction.ActionType.WRITE)
                return;

            currentAction = null;
            execute(gatt);
        }

        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
        	
        	String sensorUUID = characteristic.getService().getUuid().toString();
        	Log.i(TAG, "onCharacteristicChanged: " + sensorUUID);
        	TiAccelerometerSensor accSensor = new TiAccelerometerSensor();
        	TiTemperatureSensor tempSensor = new TiTemperatureSensor();
        	TiHumiditySensor humiditySensor = new TiHumiditySensor();
        	Log.i(TAG, sensorUUID + " is equal to? " + accSensor.getServiceUUID() + " " + tempSensor.getServiceUUID());
        	if (sensorUUID.equals(accSensor.getServiceUUID())) {
        		float[] data = accSensor.parse(characteristic);
            //double ambient = extractAmbientTemperature(characteristic);
            //double target = extractTargetTemperature(c, ambient);
            //Log.i(TAG, "Temperature: " + data[0] + " degress");
        		numVals+=1;
        		double elapsedSecondsTotal = (System.nanoTime() - START_TIME)*(Math.pow(10,-9));
   		 		accData.child(numVals+"").setValue(elapsedSecondsTotal + " , " + data[0] + " , " + data[1] + " , " + data[2]);
        	}
        	else if (sensorUUID.equals(tempSensor.getServiceUUID())) {
        	
        		double temp = tempSensor.parse(characteristic)[0];
        		tempData.setValue(temp);
        	}
        	else if (sensorUUID.equals(humiditySensor.getServiceUUID())) {
        		double temp = humiditySensor.parse(characteristic);
        		humidityData.setValue(temp);
        	}
            //accXFB.setValue(data[0]);
            //accYFB.setValue(data[1]);
            //accZFB.setValue(data[2]);

        }

        private double extractAmbientTemperature(BluetoothGattCharacteristic c) {
            int offset = 2;
            return shortUnsignedAtOffset(c, offset) / 128.0;
        }

        /**
         * Gyroscope, Magnetometer, Barometer, IR temperature
         * all store 16 bit two's complement values in the awkward format
         * LSB MSB, which cannot be directly parsed as getIntValue(FORMAT_SINT16, offset)
         * because the bytes are stored in the "wrong" direction.
         *
         * This function extracts these 16 bit two's complement values.
         * */
        private  Integer shortSignedAtOffset(BluetoothGattCharacteristic c, int offset) {
            Integer lowerByte = c.getIntValue(FORMAT_UINT8, offset);
            Integer upperByte = c.getIntValue(FORMAT_SINT8, offset + 1); // Note: interpret MSB as signed.

            return (upperByte << 8) + lowerByte;
        }

        private  Integer shortUnsignedAtOffset(BluetoothGattCharacteristic c, int offset) {
            Integer lowerByte = c.getIntValue(FORMAT_UINT8, offset);
            Integer upperByte = c.getIntValue(FORMAT_UINT8, offset + 1); // Note: interpret MSB as unsigned.

            return (upperByte << 8) + lowerByte;
        }
        
        
    };
 	
    
    public void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }
 
    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }
 
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
 
    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }
 
    private final IBinder mBinder = new LocalBinder();
 
    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }
 
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
 
        return true;
    }
 
    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
 
        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }
 
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        //final BleGattExecutor executor = new BleGattExecutor();
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }
 
    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }
 
    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }
 
    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
    	Log.i(TAG, "setCharacteristicNotification");
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }
 
    
	/**
	* This is a self-contained function for turning on the magnetometer
	* sensor. It must be called AFTER the onServicesDiscovered callback
	* is received.
	*/
	public void turnOnSensor(String serviceUUID, String configUUID) {
	    UUID magnetServiceUuid = UUID.fromString(serviceUUID);
	    UUID magnetConfigUuid = UUID.fromString(configUUID);

	    BluetoothGattService magnetService = mBluetoothGatt.getService(magnetServiceUuid);
	    Log.i("BLE", "this is probably null"+ magnetService.getUuid());
	    BluetoothGattCharacteristic config = magnetService.getCharacteristic(magnetConfigUuid);
	    
	    config.setValue(new byte[]{1}); //NB: the config value is different for the Gyroscope
	    mBluetoothGatt.writeCharacteristic(config);
	}
	
	public BluetoothGattCharacteristic getSensorCharacteristic(String serviceUUID, String configUUID) {
	    UUID magnetServiceUuid = UUID.fromString(serviceUUID);
	    UUID magnetConfigUuid = UUID.fromString(configUUID);

	    BluetoothGattService magnetService = mBluetoothGatt.getService(magnetServiceUuid);
	    BluetoothGattCharacteristic config = magnetService.getCharacteristic(magnetConfigUuid);
	    return config;
	
	}
	
	public void enableSensorNotifications(String serviceUUID, String dataUUID) {
	    UUID magnetServiceUuid = UUID.fromString(serviceUUID);
	    UUID magnetDataUuid = UUID.fromString(dataUUID);
	    UUID CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	    BluetoothGattService magnetService = mBluetoothGatt.getService(magnetServiceUuid);
	    BluetoothGattCharacteristic magnetDataCharacteristic = magnetService.getCharacteristic(magnetDataUuid);
	    mBluetoothGatt.setCharacteristicNotification(magnetDataCharacteristic, true); //Enabled locally

	    BluetoothGattDescriptor config = magnetDataCharacteristic.getDescriptor(CCC);
	    config.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
	    boolean status = mBluetoothGatt.writeDescriptor(config); //Enabled remotely
	    Log.i(TAG, "status of enabling magnetometer notifications: "+ status);
	}
	
	public void writePeriod(String serviceUUID, String periodUUID) {
		UUID magnetServiceUuid = UUID.fromString(serviceUUID);
		UUID sensorperiodUUID = UUID.fromString(periodUUID);
	    BluetoothGattService magnetService = mBluetoothGatt.getService(magnetServiceUuid);
	    BluetoothGattCharacteristic config = magnetService.getCharacteristic(sensorperiodUUID);
	    config.setValue(new byte[]{(byte) 10}); //NB: the config value is different for the Gyroscope
	    mBluetoothGatt.writeCharacteristic(config);
		
		
	}
}