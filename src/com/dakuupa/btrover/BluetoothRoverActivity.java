/*
 * Copyright (C) 2013 Dakuupa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dakuupa.btrover;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class BluetoothRoverActivity extends Activity {
	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	private static TextView statusView;

	// Name of the connected device
	private String mConnectedDeviceName = null;

	/**
	 * Set to true to add debugging code and logging.
	 */
	public static final boolean DEBUG = true;


	/**
	 * The tag we use when logging, so that our messages can be distinguished
	 * from other messages in the log. Public because it's used by several
	 * classes.
	 */
	public static final String LOG_TAG = "BTROVER";

	// Message types sent from the BluetoothReadService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	// Key names received from the BluetoothChatService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	private BluetoothAdapter mBluetoothAdapter = null;


	private static BluetoothSerialService mSerialService = null;

	private static InputMethodManager mInputManager;

	private boolean mEnablingBT;

	private MenuItem mMenuItemConnect;
	
	/*command building*/
	private int steerSpeedValue = 0;
	private int driveSpeedValue = 0;
	private String direction;
	
	public static final String STEER_MOTOR = "S";
	public static final String DRIVE_MOTOR = "D";
	public static final String HALT_ACCEL_MOTOR = "H";
	public static final String HALT_TURN_MOTOR = "T";
	public static final String LEFT_DIRECTION = "L";
	public static final String RIGHT_DIRECTION = "R";
	public static final String FORWARD_DIRECTION = "F";
	public static final String REVERSE_DIRECTION = "B";
	public static final String END_COMMAND = "X";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (DEBUG)
			Log.e(LOG_TAG, "+++ ON CREATE +++");

		mInputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		// Set up the window layout
		//requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.main);
		//getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,R.layout.custom_title);

		/*
		// Set up the custom title
		mTitle = (TextView) findViewById(R.id.title_left_text);
		mTitle.setText(R.string.app_name);
		mTitle = (TextView) findViewById(R.id.title_right_text);*/

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		if (mBluetoothAdapter == null) {
			finishDialogNoBluetooth();
			return;
		}

		setContentView(R.layout.rover_activity);

		mSerialService = new BluetoothSerialService(this, mHandlerBT);
		
		statusView = (TextView) findViewById(R.id.statusTextView);

		//handle changes to speed
		final SeekBar steerSpeed = (SeekBar) findViewById(R.id.steerSpeedBar);
		steerSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

		    @Override
		    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		    	steerSpeedValue = progress;
		    }

		    @Override
		    public void onStartTrackingTouch(SeekBar seekBar) {

		    }

		    @Override
		    public void onStopTrackingTouch(SeekBar seekBar) {

		    }
		});
		
		final SeekBar driveSpeed = (SeekBar) findViewById(R.id.driveSpeedBar);
		driveSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

		    @Override
		    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		    	driveSpeedValue = progress;
		    }

		    @Override
		    public void onStartTrackingTouch(SeekBar seekBar) {

		    }

		    @Override
		    public void onStopTrackingTouch(SeekBar seekBar) {

		    }
		});

		//handle button presses
		final Button fbutton = (Button) findViewById(R.id.forwardButton);
		fbutton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				direction = FORWARD_DIRECTION;
				sendCommand();
			}
		});
		
		final Button bbutton = (Button) findViewById(R.id.reverseButton);
		bbutton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				direction = REVERSE_DIRECTION;
				sendCommand();
			}
		});
		
		final Button lbutton = (Button) findViewById(R.id.leftButton);
		lbutton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				direction = LEFT_DIRECTION;
				sendCommand();
			}
		});
		
		final Button rbutton = (Button) findViewById(R.id.rightButton);
		rbutton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				direction = RIGHT_DIRECTION;
				sendCommand();
			}
		});
		
		final Button stopbutton = (Button) findViewById(R.id.stopButton);
		stopbutton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				direction = HALT_ACCEL_MOTOR;
				sendCommand();
			}
		});
		
		final Button stopturnbutton = (Button) findViewById(R.id.stopTurn);
		stopturnbutton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				direction = HALT_TURN_MOTOR;
				sendCommand();
			}
		});

	}
	
	private void sendCommand(){
		
        String moto = "";
        String motodir = "";

        if (direction.equals(HALT_ACCEL_MOTOR)) {
            moto = HALT_ACCEL_MOTOR;
            motodir = HALT_ACCEL_MOTOR;
        } 
        if (direction.equals(HALT_TURN_MOTOR)) {
            moto = HALT_TURN_MOTOR;
            motodir = HALT_TURN_MOTOR;
        }
        else if (direction.equals(LEFT_DIRECTION)) {
            moto = STEER_MOTOR;
            motodir = LEFT_DIRECTION;
        } else if (direction.equals(RIGHT_DIRECTION)) {
            moto = STEER_MOTOR;
            motodir = RIGHT_DIRECTION;
        }
        if (direction.equals(FORWARD_DIRECTION)) {
            moto = DRIVE_MOTOR;
            motodir = FORWARD_DIRECTION;
        }
        if (direction.equals(REVERSE_DIRECTION)) {
            moto = DRIVE_MOTOR;
            motodir = REVERSE_DIRECTION;
        }

        int speed = 0;
        if (direction.equals(LEFT_DIRECTION) || direction.equals(RIGHT_DIRECTION)) {
            speed = steerSpeedValue;
        } else {
            speed = driveSpeedValue;
        }


        String command = moto + motodir + String.format("%03d", speed) + "x";
        
        System.out.println ( command );
		
		mSerialService.write(command.getBytes());
	}

	@Override
	public void onStart() {
		super.onStart();
		if (DEBUG)
			Log.e(LOG_TAG, "++ ON START ++");

		mEnablingBT = false;
	}

	@Override
	public synchronized void onResume() {
		super.onResume();

		if (DEBUG) {
			Log.e(LOG_TAG, "+ ON RESUME +");
		}

		if (!mEnablingBT) { // If we are turning on the BT we cannot check if
							// it's enable
			if ((mBluetoothAdapter != null) && (!mBluetoothAdapter.isEnabled())) {

				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(R.string.alert_dialog_turn_on_bt)
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setTitle(R.string.alert_dialog_warning_title)
						.setCancelable(false)
						.setPositiveButton(R.string.alert_dialog_yes,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										mEnablingBT = true;
										Intent enableIntent = new Intent(
												BluetoothAdapter.ACTION_REQUEST_ENABLE);
										startActivityForResult(enableIntent,
												REQUEST_ENABLE_BT);
									}
								})
						.setNegativeButton(R.string.alert_dialog_no,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										finishDialogNoBluetooth();
									}
								});
				AlertDialog alert = builder.create();
				alert.show();
			}

			if (mSerialService != null) {
				// Only if the state is STATE_NONE, do we know that we haven't
				// started already
				if (mSerialService.getState() == BluetoothSerialService.STATE_NONE) {
					// Start the Bluetooth chat services
					mSerialService.start();
				}
			}

			if (mBluetoothAdapter != null) {
				/*readPrefs();
				updatePrefs();*/
			}
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public synchronized void onPause() {
		super.onPause();
		if (DEBUG)
			Log.e(LOG_TAG, "- ON PAUSE -");
	}

	@Override
	public void onStop() {
		super.onStop();
		if (DEBUG)
			Log.e(LOG_TAG, "-- ON STOP --");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (DEBUG)
			Log.e(LOG_TAG, "--- ON DESTROY ---");

		if (mSerialService != null)
			mSerialService.stop();

	}


	public int getConnectionState() {
		return mSerialService.getState();
	}

	public void send(byte[] out) {
		mSerialService.write(out);
	}

	public void toggleKeyboard() {
		mInputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
	}


	// The Handler that gets information back from the BluetoothService
	private final Handler mHandlerBT = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				if (DEBUG)
					Log.i(LOG_TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1) {
				case BluetoothSerialService.STATE_CONNECTED:
					if (mMenuItemConnect != null) {
						mMenuItemConnect
								.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
						mMenuItemConnect.setTitle(R.string.disconnect);
					}

					statusView.setText(R.string.title_connected_to);
					statusView.append(mConnectedDeviceName);
					break;

				case BluetoothSerialService.STATE_CONNECTING:
					statusView.setText(R.string.title_connecting);
					break;

				case BluetoothSerialService.STATE_LISTEN:
				case BluetoothSerialService.STATE_NONE:
					if (mMenuItemConnect != null) {
						mMenuItemConnect
								.setIcon(android.R.drawable.ic_menu_search);
						mMenuItemConnect.setTitle(R.string.connect);
					}

					statusView.setText(R.string.title_not_connected);

					break;
				}
				break;
			case MESSAGE_WRITE:
				break;
			/*
			 * case MESSAGE_READ: byte[] readBuf = (byte[]) msg.obj;
			 * mEmulatorView.write(readBuf, msg.arg1);
			 * 
			 * break;
			 */
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Toast.makeText(getApplicationContext(),
						"Connected to " + mConnectedDeviceName,
						Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
			}
		}
	};

	public void finishDialogNoBluetooth() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.alert_dialog_no_bt)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(R.string.app_name)
				.setCancelable(false)
				.setPositiveButton(R.string.alert_dialog_ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								finish();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (DEBUG)
			Log.d(LOG_TAG, "onActivityResult " + resultCode);
		switch (requestCode) {

		case REQUEST_CONNECT_DEVICE:

			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				// Get the device MAC address
				String address = data.getExtras().getString(
						DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				// Get the BLuetoothDevice object
				BluetoothDevice device = mBluetoothAdapter
						.getRemoteDevice(address);
				// Attempt to connect to the device
				mSerialService.connect(device);
			}
			break;

		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				Log.d(LOG_TAG, "BT not enabled");

				finishDialogNoBluetooth();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		mMenuItemConnect = menu.getItem(0);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.connect:

			if (getConnectionState() == BluetoothSerialService.STATE_NONE) {
				// Launch the DeviceListActivity to see devices and do scan
				Intent serverIntent = new Intent(this, DeviceListActivity.class);
				startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			} else if (getConnectionState() == BluetoothSerialService.STATE_CONNECTED) {
				mSerialService.stop();
				mSerialService.start();
			}
			return true;
		case R.id.menu_help:
			showHelp();
			return true;
		}
		return false;
	}


	private void showHelp() {
		
		new AlertDialog.Builder(this)
				.setTitle("Help")
				.setMessage("help message here")
				.show();
	}
}

