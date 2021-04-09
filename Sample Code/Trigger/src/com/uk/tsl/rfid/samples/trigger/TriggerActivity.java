//----------------------------------------------------------------------------------------------
// Copyright (c) 2013 Technology Solutions UK Ltd. All rights reserved.
//----------------------------------------------------------------------------------------------

package com.uk.tsl.rfid.samples.trigger;

import com.uk.tsl.rfid.TSLBluetoothDeviceActivity;
import com.uk.tsl.rfid.ModelBase;
import com.uk.tsl.rfid.WeakHandler;
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander;
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SwitchState;
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
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

public class TriggerActivity extends TSLBluetoothDeviceActivity {
	// Debug control
	private static final boolean D = BuildConfig.DEBUG;

	// The model that performs actions with the reader
	private TriggerModel mModel;

	// Switch state display elements 
	private TextView mAsyncSinglePressedTextView;
	private TextView mAsyncDoublePressedTextView;
	private TextView mPolledSinglePressedTextView;
	private TextView mPolledDoublePressedTextView;

	// Async / polling Controls
	private CheckBox mAsyncEnabledCheckBox;
	private CheckBox mPollingEnabledCheckBox;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_trigger);

		// Set the switch state display elements 
		mAsyncSinglePressedTextView = (TextView)findViewById(R.id.asyncSingleTextView);
		mAsyncDoublePressedTextView = (TextView)findViewById(R.id.asyncDoubleTextView);
		mPolledSinglePressedTextView = (TextView)findViewById(R.id.polledSingleTextView);
		mPolledDoublePressedTextView = (TextView)findViewById(R.id.polledDoubleTextView);

		// Set the asynchronous & polling controls
		mAsyncEnabledCheckBox = (CheckBox)findViewById(R.id.asynchronousCheckBox);
		mAsyncEnabledCheckBox.setOnClickListener(mAsynchronousCheckBoxListener);

		mPollingEnabledCheckBox = (CheckBox)findViewById(R.id.pollingCheckBox);
		mPollingEnabledCheckBox.setOnClickListener(mPolledCheckBoxListener);
		
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

        // Create a (custom) model and configure its commander and handler
        //
        mModel = new TriggerModel();
        mModel.setCommander(getCommander());
        mModel.setHandler(mGenericModelHandler);
        mModel.initialise();
	}



    //----------------------------------------------------------------------------------------------
	// Pause & Resume life cycle
	//----------------------------------------------------------------------------------------------

    @Override
    public synchronized void onPause() {
        super.onPause();

        // Register to receive notifications from the AsciiCommander
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }

    @Override
    public synchronized void onResume()
    {
    	super.onResume();

        // Register to receive notifications from the AsciiCommander
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
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

    private final WeakHandler<TriggerActivity> mGenericModelHandler = new WeakHandler<TriggerActivity>(this) {

		@Override
		public void handleMessage(Message msg, TriggerActivity thisActivity) {
			try {
				switch (msg.what) {
				case ModelBase.BUSY_STATE_CHANGED_NOTIFICATION:
					//TODO: process change in model busy state
					break;

				case ModelBase.MESSAGE_NOTIFICATION:
					//TODO: process a message from the model 
					break;
					
				case TriggerModel.ASYNC_SWITCH_STATE_NOTIFICATION:
		    		if (D) { Log.d(getClass().getName(), "Async: " + (SwitchState)msg.obj); }
					updateAsynchronousStateDisplay((SwitchState)msg.obj);
					break;
					
				case TriggerModel.POLLED_SWITCH_STATE_NOTIFICATION:
		    		if (D) { Log.d(getClass().getName(), "Polled: " + (SwitchState)msg.obj); }
					updatePolledStateDisplay((SwitchState)msg.obj);
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

    private void displayReaderState()
    {
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
    private void UpdateUI()
    {
    	//boolean isConnected = getCommander().isConnected();
    	//TODO: configure UI control state
    }

    //
    // Show the current switch state on the display
    //
    private void updateAsynchronousStateDisplay(SwitchState state)
    {
    	if( state == SwitchState.OFF ) {

    		mAsyncSinglePressedTextView.setVisibility(View.INVISIBLE);
    		mAsyncDoublePressedTextView.setVisibility(View.INVISIBLE);

    	} else if(state == SwitchState.SINGLE) {

    		mAsyncSinglePressedTextView.setVisibility(View.VISIBLE);
    		mAsyncDoublePressedTextView.setVisibility(View.INVISIBLE);

    	} else if(state == SwitchState.DOUBLE) {

    		mAsyncSinglePressedTextView.setVisibility(View.INVISIBLE);
    		mAsyncDoublePressedTextView.setVisibility(View.VISIBLE);

    	}
    }
    

    //
    // Show the current switch state on the display
    //
    private void updatePolledStateDisplay(SwitchState state)
    {
    	if( state == SwitchState.OFF ) {

    		mPolledSinglePressedTextView.setVisibility(View.INVISIBLE);
    		mPolledDoublePressedTextView.setVisibility(View.INVISIBLE);

    	} else if(state == SwitchState.SINGLE) {

    		mPolledSinglePressedTextView.setVisibility(View.VISIBLE);
    		mPolledDoublePressedTextView.setVisibility(View.INVISIBLE);

    	} else if(state == SwitchState.DOUBLE) {

    		mPolledSinglePressedTextView.setVisibility(View.INVISIBLE);
    		mPolledDoublePressedTextView.setVisibility(View.VISIBLE);

    	}
    }
    

    //----------------------------------------------------------------------------------------------
	// AsciiCommander message handling
	//----------------------------------------------------------------------------------------------

    //
    // Handle the messages broadcast from the AsciiCommander
    //
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		if (D) { Log.d(getClass().getName(), "AsciiCommander state changed - isConnected: " + getCommander().isConnected()); }
    		
    		String connectionStateMsg = intent.getStringExtra(AsciiCommander.REASON_KEY);
            Toast.makeText(context, connectionStateMsg, Toast.LENGTH_LONG).show();

            if( getCommander().isConnected()) {
            	resetReader();
            }
            displayReaderState();

            UpdateUI();
    	}
    };

    //----------------------------------------------------------------------------------------------
	// Reader reset
	//----------------------------------------------------------------------------------------------

    //
    // Handle reset controls
    //
    private void resetReader()
    {
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
	// Handler for change of asynchronous or polling controls
	//----------------------------------------------------------------------------------------------

    private OnClickListener mAsynchronousCheckBoxListener = new OnClickListener() {
    	public void onClick(View v) {
    		try {
    			boolean isChecked = ((CheckBox)v).isChecked();
				if (mModel.setAsyncReportingEnabled(isChecked) ) {
					
				} else {
					mAsyncEnabledCheckBox.setChecked(!isChecked);
				}
    			
    			UpdateUI();

    		} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    };
 
    
    private OnClickListener mPolledCheckBoxListener = new OnClickListener() {
    	public void onClick(View v) {
    		try {
    			boolean isChecked = ((CheckBox)v).isChecked();
				if (mModel.setPolledReportingEnabled(isChecked) ) {
					
				} else {
					mPollingEnabledCheckBox.setChecked(!isChecked);
				}
    			
    			UpdateUI();

    		} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    };
 
    

}
