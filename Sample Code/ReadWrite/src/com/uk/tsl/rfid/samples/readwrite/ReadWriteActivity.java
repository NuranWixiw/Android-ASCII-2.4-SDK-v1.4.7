//----------------------------------------------------------------------------------------------
// Copyright (c) 2013 Technology Solutions UK Ltd. All rights reserved.
//----------------------------------------------------------------------------------------------

package com.uk.tsl.rfid.samples.readwrite;

import com.uk.tsl.rfid.TSLBluetoothDeviceActivity;
import com.uk.tsl.rfid.ModelBase;
import com.uk.tsl.rfid.WeakHandler;
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander;
import com.uk.tsl.rfid.asciiprotocol.DeviceProperties;
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand;
import com.uk.tsl.rfid.asciiprotocol.enumerations.Databank;
import com.uk.tsl.rfid.asciiprotocol.enumerations.EnumerationBase;
import com.uk.tsl.rfid.asciiprotocol.parameters.AntennaParameters;
import com.uk.tsl.rfid.asciiprotocol.responders.LoggerResponder;
import com.uk.tsl.utils.HexEncoding;

import android.os.Bundle;
import android.os.Message;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class ReadWriteActivity extends TSLBluetoothDeviceActivity {
	// Debug control
	private static final boolean D = BuildConfig.DEBUG;

	// The text view to display the RF Output Power used in RFID commands
	private TextView mPowerLevelTextView;
	// The seek bar used to adjust the RF Output Power for RFID commands
	private SeekBar mPowerSeekBar;
	// The current setting of the power level
	private int mPowerLevel = AntennaParameters.MaximumCarrierPower;

	// Custom adapter for the Ascii command enumerated parameter values to display the description rather than the toString() value
	public class ParameterEnumerationArrayAdapter<T extends EnumerationBase > extends ArrayAdapter<T> {
		private final T[] mValues;

		public ParameterEnumerationArrayAdapter(Context context, int textViewResourceId, T[] objects) {
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
	
	private Databank[] mDatabanks = new Databank[] {
		Databank.ELECTRONIC_PRODUCT_CODE,
		Databank.TRANSPONDER_IDENTIFIER,
		Databank.RESERVED,
		Databank.USER
	};
	private ParameterEnumerationArrayAdapter<Databank> mDatabankArrayAdapter;

    // The buttons that invoke actions
	private Button mReadActionButton;
	private Button mWriteActionButton;
	private Button mClearActionButton;

	//Create model class derived from ModelBase
	private ReadWriteModel mModel;

	// The text-based parameters
	private EditText mTargetTagEditText;
	private EditText mWordAddressEditText;
	private EditText mWordCountEditText;
	private EditText mDataEditText;

	private TextView mResultTextView;
	private ScrollView mResultScrollView;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_read_write);
    	

        // The SeekBar provides an integer value for the antenna power
        mPowerLevelTextView = (TextView)findViewById(R.id.powerTextView);
        mPowerSeekBar = (SeekBar)findViewById(R.id.powerSeekBar);
        mPowerSeekBar.setOnSeekBarChangeListener(mPowerSeekBarListener);

        // Set the seek bar current value to maximum and to cover the range of the power settings
        setPowerBarLimits();

        // Set up the spinner with the memory bank selections
        mDatabankArrayAdapter = new ParameterEnumerationArrayAdapter<Databank>(this, android.R.layout.simple_spinner_item, mDatabanks);
    	// Find and set up the sessions spinner
        Spinner spinner = (Spinner) findViewById(R.id.bankSpinner);
        mDatabankArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(mDatabankArrayAdapter);
        spinner.setOnItemSelectedListener(mBankSelectedListener);
        spinner.setSelection(mDatabanks.length - 1);

        
        // Set up the action buttons
    	mReadActionButton = (Button)findViewById(R.id.readButton);
    	mReadActionButton.setOnClickListener(mAction1ButtonListener);

    	mWriteActionButton = (Button)findViewById(R.id.writeButton);
    	mWriteActionButton.setOnClickListener(mAction2ButtonListener);

    	mClearActionButton = (Button)findViewById(R.id.clearButton);
    	mClearActionButton.setOnClickListener(mAction3ButtonListener);

    	// Set up the target EPC EditText
    	mTargetTagEditText = (EditText)findViewById(R.id.targetTagEditText);
		mTargetTagEditText.addTextChangedListener(mTargetTagEditTextChangedListener);

		mWordAddressEditText = (EditText)findViewById(R.id.wordAddressEditText);
		mWordAddressEditText.addTextChangedListener(mWordAddressEditTextChangedListener);

		mWordCountEditText = (EditText)findViewById(R.id.wordCountEditText);
		mWordCountEditText.addTextChangedListener(mWordCountEditTextChangedListener);

		mDataEditText = (EditText)findViewById(R.id.dataEditText);
		mDataEditText.addTextChangedListener(mDataEditTextChangedListener);

		// Set up the results area
		mResultTextView = (TextView)findViewById(R.id.resultTextView);
		mResultScrollView = (ScrollView)findViewById(R.id.resultScrollView);
		
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

        //TODO: Create a (custom) model and configure its commander and handler
        mModel = new ReadWriteModel();
        mModel.setCommander(getCommander());
        mModel.setHandler(mGenericModelHandler);

        // Use the model's values for the offset and length
        // Display the initial values
        int offset = mModel.getReadCommand().getOffset();
        int length = mModel.getReadCommand().getLength();
        mWordAddressEditText.setText(String.format("%d", offset));
        mWordCountEditText.setText(String.format("%d", length));
	}



    //----------------------------------------------------------------------------------------------
	// Pause & Resume life cycle
	//----------------------------------------------------------------------------------------------

    @Override
    public synchronized void onPause() {
        super.onPause();

        // Register to receive notifications from the AsciiCommander
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mCommanderMessageReceiver);
    }

    @Override
    public synchronized void onResume()
    {
    	super.onResume();

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

    private final WeakHandler<ReadWriteActivity> mGenericModelHandler = new WeakHandler<ReadWriteActivity>(this) {

		@Override
		public void handleMessage(Message msg, ReadWriteActivity thisActivity) {
			try {
				switch (msg.what) {
				case ModelBase.BUSY_STATE_CHANGED_NOTIFICATION:
					if( mModel.error() != null ) {
						mResultTextView.append("\n Task failed:\n" + mModel.error().getMessage() + "\n\n");
						mResultScrollView.post(new Runnable() { public void run() { mResultScrollView.fullScroll(View.FOCUS_DOWN); } });
						
					}
					UpdateUI();
					break;

				case ModelBase.MESSAGE_NOTIFICATION:
					String message = (String)msg.obj;
					mResultTextView.append(message);
					mResultScrollView.post(new Runnable() { public void run() { mResultScrollView.fullScroll(View.FOCUS_DOWN); } });
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
    	boolean isConnected = getCommander().isConnected();
    	boolean canIssueCommand = isConnected & !mModel.isBusy();
    	mReadActionButton.setEnabled(canIssueCommand);
    	// Only enable the write button when there is at least a partial EPC
    	mWriteActionButton.setEnabled(canIssueCommand && mTargetTagEditText.getText().length() != 0);
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
                mModel.getReadCommand().setOutputPower(mPowerLevel);
                mModel.getWriteCommand().setOutputPower(mPowerLevel);

                mModel.resetDevice();
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
			mModel.getReadCommand().setOutputPower(mPowerLevel);
			mModel.getWriteCommand().setOutputPower(mPowerLevel);
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
	// Handler for changes in databank
	//----------------------------------------------------------------------------------------------

    private AdapterView.OnItemSelectedListener mBankSelectedListener = new AdapterView.OnItemSelectedListener()
    {
		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
			Databank targetBank = (Databank)parent.getItemAtPosition(pos);
			if( mModel.getReadCommand() != null ) {
				mModel.getReadCommand().setBank(targetBank);
			}
			if( mModel.getWriteCommand() != null ) {
				mModel.getWriteCommand().setBank(targetBank);
			}

		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
		}
    };


	//----------------------------------------------------------------------------------------------
	// Handlers for the action buttons - invoke the currently selected actions
	//----------------------------------------------------------------------------------------------

    private OnClickListener mAction1ButtonListener = new OnClickListener() {
    	public void onClick(View v) {
    		mModel.read();
    	}
    };
	
    private OnClickListener mAction2ButtonListener = new OnClickListener() {
    	public void onClick(View v) {
    		mModel.write();
    	}
    };

    private OnClickListener mAction3ButtonListener = new OnClickListener() {
    	public void onClick(View v) {
			mResultTextView.setText("");
    	}
    };

    
	//----------------------------------------------------------------------------------------------
	// Handler for 
	//----------------------------------------------------------------------------------------------

    private TextWatcher mTargetTagEditTextChangedListener = new TextWatcher() {
		
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {}
		
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
		
		@Override
		public void afterTextChanged(Editable s) {
			String value = s.toString();
			if( mModel.getReadCommand() != null ) {
				mModel.getReadCommand().setSelectData(value);
			}
			if( mModel.getWriteCommand() != null ) {
				mModel.getWriteCommand().setSelectData(value);
			}
			UpdateUI();
		}
	};


    private TextWatcher mWordAddressEditTextChangedListener = new TextWatcher() {
		
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {}
		
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
		
		@Override
		public void afterTextChanged(Editable s) {
			int value = 0;
			try {
				value = Integer.parseInt(s.toString());

				if( mModel.getReadCommand() != null ) {
					mModel.getReadCommand().setOffset(value);
				}
				if( mModel.getWriteCommand() != null ) {
					mModel.getWriteCommand().setOffset(value);
				}
			} catch (Exception e) {
			}
			UpdateUI();
		}
	};


    private TextWatcher mWordCountEditTextChangedListener = new TextWatcher() {
		
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {}
		
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
		
		@Override
		public void afterTextChanged(Editable s) {
			int value = 0;
			try {
				value = Integer.parseInt(s.toString());

				if( mModel.getReadCommand() != null ) {
					mModel.getReadCommand().setLength(value);
				}
				if( mModel.getWriteCommand() != null ) {
					mModel.getWriteCommand().setLength(value);
				}
			} catch (Exception e) {
			}
			UpdateUI();
		}
	};


    private TextWatcher mDataEditTextChangedListener = new TextWatcher() {
		
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {}
		
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
		
		@Override
		public void afterTextChanged(Editable s) {
			String value = s.toString();
			if( mModel.getWriteCommand() != null ) {
				byte[] data = null;
				try {
					data = HexEncoding.stringToBytes(value);
					mModel.getWriteCommand().setData(data);
				} catch (Exception e) {
					// Ignore if invalid
				}
			}
			UpdateUI();
		}
	};


	
    

}
