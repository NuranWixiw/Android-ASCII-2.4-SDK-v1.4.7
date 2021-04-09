//----------------------------------------------------------------------------------------------
// Copyright (c) 2013 Technology Solutions UK Ltd. All rights reserved.
//----------------------------------------------------------------------------------------------

package com.uk.tsl.rfid.samples.inventory;

import com.uk.tsl.rfid.TSLBluetoothDeviceActivity;
import com.uk.tsl.rfid.ModelBase;
import com.uk.tsl.rfid.WeakHandler;
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander;
import com.uk.tsl.rfid.asciiprotocol.DeviceProperties;
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QuerySession;
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState;
import com.uk.tsl.rfid.asciiprotocol.parameters.AntennaParameters;
import com.uk.tsl.rfid.asciiprotocol.responders.LoggerResponder;

import android.os.Bundle;
import android.os.Message;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class InventoryActivity extends TSLBluetoothDeviceActivity {
	// Debug control
	private static final boolean D = BuildConfig.DEBUG;

    // The list of results from actions
    private ArrayAdapter<String> mResultsArrayAdapter;
    private ListView mResultsListView;
    private ArrayAdapter<String> mBarcodeResultsArrayAdapter;
    private ListView mBarcodeResultsListView;

	// The text view to display the RF Output Power used in RFID commands
	private TextView mPowerLevelTextView;
	// The seek bar used to adjust the RF Output Power for RFID commands
	private SeekBar mPowerSeekBar;
	// The current setting of the power level
	private int mPowerLevel = AntennaParameters.MaximumCarrierPower;

	// Error report
	private TextView mResultTextView;

	// Custom adapter for the session values to display the description rather than the toString() value
	public class SessionArrayAdapter extends ArrayAdapter<QuerySession> {
		private final QuerySession[] mValues;

		public SessionArrayAdapter(Context context, int textViewResourceId, QuerySession[] objects) {
			super(context, textViewResourceId, objects);
			mValues = objects;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView view = (TextView)super.getView(position, convertView, parent);
			view.setText(mValues[position].getDescription());
			return view;
		}

		@Override
		public View getDropDownView(int position, View convertView, ViewGroup parent) {
			TextView view = (TextView)super.getDropDownView(position, convertView, parent);
			view.setText(mValues[position].getDescription());
			return view;
		}
	}
	
	// The session
	private QuerySession[] mSessions = new QuerySession[] {
			QuerySession.SESSION_0,
			QuerySession.SESSION_1,
			QuerySession.SESSION_2,
			QuerySession.SESSION_3
	};
    // The list of sessions that can be selected
    private SessionArrayAdapter mSessionArrayAdapter;

	// All of the reader inventory tasks are handled by this class
	private InventoryModel mModel;
	
    //----------------------------------------------------------------------------------------------
	// OnCreate life cycle
	//----------------------------------------------------------------------------------------------

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_inventory);
    	
        mResultsArrayAdapter = new ArrayAdapter<String>(this,R.layout.result_item);
        mBarcodeResultsArrayAdapter = new ArrayAdapter<String>(this,R.layout.result_item);

        mResultTextView = (TextView)findViewById(R.id.resultTextView);

        // Find and set up the results ListView
        mResultsListView = (ListView) findViewById(R.id.resultListView);
        mResultsListView.setAdapter(mResultsArrayAdapter);
        mResultsListView.setFastScrollEnabled(true);

        mBarcodeResultsListView = (ListView) findViewById(R.id.barcodeListView);
        mBarcodeResultsListView.setAdapter(mBarcodeResultsArrayAdapter);
        mBarcodeResultsListView.setFastScrollEnabled(true);

        // Hook up the button actions
        Button sButton = (Button)findViewById(R.id.scanButton);
        sButton.setOnClickListener(mScanButtonListener);
        Button cButton = (Button)findViewById(R.id.clearButton);
        cButton.setOnClickListener(mClearButtonListener);

        // The SeekBar provides an integer value for the antenna power
        mPowerLevelTextView = (TextView)findViewById(R.id.powerTextView);
        mPowerSeekBar = (SeekBar)findViewById(R.id.powerSeekBar);
        mPowerSeekBar.setOnSeekBarChangeListener(mPowerSeekBarListener);

        // Set the seek bar current value to maximum and to cover the range of the power settings
        setPowerBarLimits();

        mSessionArrayAdapter = new SessionArrayAdapter(this, android.R.layout.simple_spinner_item, mSessions);
    	// Find and set up the sessions spinner
        Spinner spinner = (Spinner) findViewById(R.id.sessionSpinner);
        mSessionArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(mSessionArrayAdapter);
        spinner.setOnItemSelectedListener(mActionSelectedListener);
        spinner.setSelection(0);

        // Set up Fast Id check box listener
        CheckBox cb = (CheckBox)findViewById(R.id.fastIdCheckBox);
        cb.setOnClickListener(mFastIdCheckBoxListener);
        
        //
		// An AsciiCommander has been created by the base class
		//
    	AsciiCommander commander = getCommander();

		// Add the LoggerResponder - this simply echoes all lines received from the reader to the log
        // and passes the line onto the next responder
        // This is added first so that no other responder can consume received lines before they are logged.
        commander.addResponder(new LoggerResponder());

        // Add a synchronous responder to handle synchronous commands
        commander.addSynchronousResponder();

        //Create a (custom) model and configure its commander and handler
        mModel = new InventoryModel();
        mModel.setCommander(getCommander());
        mModel.setHandler(mGenericModelHandler);
	}

    //----------------------------------------------------------------------------------------------
	// Pause & Resume life cycle
	//----------------------------------------------------------------------------------------------

    @Override
    public synchronized void onPause() {
        super.onPause();

        mModel.setEnabled(false);

        // Unregister to receive notifications from the AsciiCommander
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mCommanderMessageReceiver);
    }

    @Override
    public synchronized void onResume() {
    	super.onResume();

        mModel.setEnabled(true);

        // Register to receive notifications from the AsciiCommander
        LocalBroadcastManager.getInstance(this).registerReceiver(mCommanderMessageReceiver,
        	      new IntentFilter(AsciiCommander.STATE_CHANGED_NOTIFICATION));

        displayReaderState();
        UpdateUI();
    }


    //----------------------------------------------------------------------------------------------
	// Menu
	//----------------------------------------------------------------------------------------------

	private MenuItem mReconnectMenuItem;
	private MenuItem mConnectMenuItem;
	private MenuItem mDisconnectMenuItem;
	private MenuItem mResetMenuItem;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.reader_menu, menu);

		mResetMenuItem = menu.findItem(R.id.reset_reader_menu_item);
		mReconnectMenuItem = menu.findItem(R.id.reconnect_reader_menu_item);
		mConnectMenuItem = menu.findItem(R.id.insecure_connect_reader_menu_item);
		mDisconnectMenuItem= menu.findItem(R.id.disconnect_reader_menu_item);
		return true;
	}


	/**
	 * Prepare the menu options
	 */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        boolean isConnecting = getCommander().getConnectionState() == AsciiCommander.ConnectionState.CONNECTING;
        boolean isConnected = getCommander().isConnected();
        mResetMenuItem.setEnabled(isConnected);
        mDisconnectMenuItem.setEnabled(isConnected);

        mReconnectMenuItem.setEnabled(!(isConnecting || isConnected));
        mConnectMenuItem.setEnabled(!(isConnecting || isConnected));

        return super.onPrepareOptionsMenu(menu);
    }
    
	/**
	 * Respond to menu item selections
	 */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

        case R.id.reconnect_reader_menu_item:
            Toast.makeText(this.getApplicationContext(), "Reconnecting...", Toast.LENGTH_LONG).show();
        	reconnectDevice();
        	UpdateUI();
        	return true;

        case R.id.insecure_connect_reader_menu_item:
            // Choose a device and connect to it
        	selectDevice();
            return true;

        case R.id.disconnect_reader_menu_item:
            Toast.makeText(this.getApplicationContext(), "Disconnecting...", Toast.LENGTH_SHORT).show();
        	disconnectDevice();
        	displayReaderState();
        	return true;

        case R.id.reset_reader_menu_item:
        	resetReader();
        	UpdateUI();
        	return true;
        }
        return super.onOptionsItemSelected(item);
    }


    //----------------------------------------------------------------------------------------------
	// Model notifications
	//----------------------------------------------------------------------------------------------

    private final WeakHandler<InventoryActivity> mGenericModelHandler = new WeakHandler<InventoryActivity>(this) {

		@Override
		public void handleMessage(Message msg, InventoryActivity thisActivity) {
			try {
				switch (msg.what) {
				case ModelBase.BUSY_STATE_CHANGED_NOTIFICATION:
					//TODO: process change in model busy state
					break;

				case ModelBase.MESSAGE_NOTIFICATION:
					// Examine the message for prefix
					String message = (String)msg.obj;
					if( message.startsWith("ER:")) {
						mResultTextView.setText( message.substring(3));
					}
					else if( message.startsWith("BC:")) {
							mBarcodeResultsArrayAdapter.add(message);
							scrollBarcodeListViewToBottom();
					} else {
						mResultsArrayAdapter.add(message);
						scrollResultsListViewToBottom();
					}
					UpdateUI();
					break;
					
				default:
					break;
				}
			} catch (Exception e) {
			}
			
		}
	};

	
    //----------------------------------------------------------------------------------------------
	// UI state and display update
	//----------------------------------------------------------------------------------------------

    private void displayReaderState() {

        String connectionMsg = "Reader: ";
        switch( getCommander().getConnectionState())
        {
            case CONNECTED:
                connectionMsg += getCommander().getConnectedDeviceName();
                break;
            case CONNECTING:
                connectionMsg += "Connecting...";
                break;
            default:
                connectionMsg += "Disconnected";
        }
        setTitle(connectionMsg);
    }
	
    
    //
    // Set the state for the UI controls
    //
    private void UpdateUI() {
    	//boolean isConnected = getCommander().isConnected();
    	//TODO: configure UI control state
    }


    private void scrollResultsListViewToBottom() {
    	mResultsListView.post(new Runnable() {
            @Override
            public void run() {
                // Select the last row so it will scroll into view...
            	mResultsListView.setSelection(mResultsArrayAdapter.getCount() - 1);
            }
        });
    }

    private void scrollBarcodeListViewToBottom() {
    	mBarcodeResultsListView.post(new Runnable() {
            @Override
            public void run() {
                // Select the last row so it will scroll into view...
            	mBarcodeResultsListView.setSelection(mBarcodeResultsArrayAdapter.getCount() - 1);
            }
        });
    }

	
    //----------------------------------------------------------------------------------------------
	// AsciiCommander message handling
	//----------------------------------------------------------------------------------------------

    //
    // Handle the messages broadcast from the AsciiCommander
    //
    private BroadcastReceiver mCommanderMessageReceiver = new BroadcastReceiver() {
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		if (D) { Log.d(getClass().getName(), "AsciiCommander state changed - isConnected: " + getCommander().isConnected()); }
    		
    		String connectionStateMsg = intent.getStringExtra(AsciiCommander.REASON_KEY);
            Toast.makeText(context, connectionStateMsg, Toast.LENGTH_SHORT).show();

            displayReaderState();
            if( getCommander().isConnected() )
            {
            	// Update for any change in power limits
                setPowerBarLimits();
                // This may have changed the current power level setting if the new range is smaller than the old range
                // so update the model's inventory command for the new power value
    			mModel.getCommand().setOutputPower(mPowerLevel);
    			
            	mModel.resetDevice();
                mModel.updateConfiguration();
            }

            UpdateUI();
    	}
    };

    //----------------------------------------------------------------------------------------------
	// Reader reset
	//----------------------------------------------------------------------------------------------

    //
    // Handle reset controls
    //
    private void resetReader() {
		try {
			// Reset the reader
			FactoryDefaultsCommand fdCommand = FactoryDefaultsCommand.synchronousCommand();
			getCommander().executeCommand(fdCommand);
			String msg = "Reset " + (fdCommand.isSuccessful() ? "succeeded" : "failed");
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
			
			UpdateUI();

		} catch (Exception e) {
			e.printStackTrace();
		}
    }


	//----------------------------------------------------------------------------------------------
	// Power seek bar
	//----------------------------------------------------------------------------------------------

	//
    // Set the seek bar to cover the range of the currently connected device
	// The power level is set to the new maximum power
	//
    private void setPowerBarLimits()
	{
		DeviceProperties deviceProperties = getCommander().getDeviceProperties();

        mPowerSeekBar.setMax(deviceProperties.getMaximumCarrierPower() - deviceProperties.getMinimumCarrierPower());
        mPowerLevel = deviceProperties.getMaximumCarrierPower();
        mPowerSeekBar.setProgress(mPowerLevel - deviceProperties.getMinimumCarrierPower());
	}


    //
    // Handle events from the power level seek bar. Update the mPowerLevel member variable for use in other actions
    //
    private OnSeekBarChangeListener mPowerSeekBarListener = new OnSeekBarChangeListener() {
		
		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			// Nothing to do here
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {

			// Update the reader's setting only after the user has finished changing the value
			updatePowerSetting(getCommander().getDeviceProperties().getMinimumCarrierPower() + seekBar.getProgress());
			mModel.getCommand().setOutputPower(mPowerLevel);
			mModel.updateConfiguration();
		}
		
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
			updatePowerSetting(getCommander().getDeviceProperties().getMinimumCarrierPower() + progress);
		}
	};

	private void updatePowerSetting(int level)	{
		mPowerLevel = level;
		mPowerLevelTextView.setText( mPowerLevel + " dBm");
	}


	//----------------------------------------------------------------------------------------------
	// Button event handlers
	//----------------------------------------------------------------------------------------------

    // Scan action
    private OnClickListener mScanButtonListener = new OnClickListener() {
    	public void onClick(View v) {
    		try {
    			mResultTextView.setText("");
    			// Perform a transponder scan
    			mModel.scan();

    			UpdateUI();

    		} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    };

    // Clear action
    private OnClickListener mClearButtonListener = new OnClickListener() {
    	public void onClick(View v) {
    		try {
    			// Clear the list
    			mResultsArrayAdapter.clear();
    			mBarcodeResultsArrayAdapter.clear();

    			UpdateUI();

    		} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    };
    
	//----------------------------------------------------------------------------------------------
	// Handler for changes in session
	//----------------------------------------------------------------------------------------------

    private AdapterView.OnItemSelectedListener mActionSelectedListener = new AdapterView.OnItemSelectedListener()
    {
		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
			if( mModel.getCommand() != null ) {
				QuerySession targetSession = (QuerySession)parent.getItemAtPosition(pos);
				mModel.getCommand().setQuerySession(targetSession);
				mModel.updateConfiguration();
			}

		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
		}
    };


	//----------------------------------------------------------------------------------------------
	// Handler for changes in FastId
	//----------------------------------------------------------------------------------------------

    private OnClickListener mFastIdCheckBoxListener = new OnClickListener() {
    	public void onClick(View v) {
    		try {
    			CheckBox fastIdCheckBox = (CheckBox)v;
				mModel.getCommand().setUsefastId(fastIdCheckBox.isChecked() ? TriState.YES : TriState.NO);
				mModel.updateConfiguration();
    			
    			UpdateUI();

    		} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    };
 


}
