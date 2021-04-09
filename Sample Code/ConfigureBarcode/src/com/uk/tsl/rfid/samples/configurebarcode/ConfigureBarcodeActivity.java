//----------------------------------------------------------------------------------------------
// Copyright (c) 2013 Technology Solutions UK Ltd. All rights reserved.
//----------------------------------------------------------------------------------------------

package com.uk.tsl.rfid.samples.configurebarcode;

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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

import java.util.Locale;

public class ConfigureBarcodeActivity extends TSLBluetoothDeviceActivity {
	// Debug control
	private static final boolean D = BuildConfig.DEBUG;

    // The list of results from actions
    private ArrayAdapter<String> mBarcodeResultsArrayAdapter;
    private ListView mBarcodeResultsListView;

	// Error report
	private TextView mResultTextView;

	// All of the reader inventory tasks are handled by this class
	private ConfigureBarcodeModel mModel;

    // Symbology Controls
	private CheckBox mCheckBox1;

    private EditText mLengthOneEditText;
    private EditText mLengthTwoEditText;


    //----------------------------------------------------------------------------------------------
	// OnCreate life cycle
	//----------------------------------------------------------------------------------------------

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_configure_barcode);
    	
        mBarcodeResultsArrayAdapter = new ArrayAdapter<String>(this,R.layout.result_item);
        mBarcodeResultsListView = (ListView) findViewById(R.id.barcodeListView);
        mBarcodeResultsListView.setAdapter(mBarcodeResultsArrayAdapter);
        mBarcodeResultsListView.setFastScrollEnabled(true);

        mResultTextView = (TextView)findViewById(R.id.resultTextView);

        // Hook up the button actions
        Button pButton = (Button)findViewById(R.id.persistButton);
        pButton.setOnClickListener(mPersistButtonListener);
        Button rButton = (Button)findViewById(R.id.defaultButton);
        rButton.setOnClickListener(mRestoreButtonListener);
        Button sButton = (Button)findViewById(R.id.scanButton);
        sButton.setOnClickListener(mScanButtonListener);
        Button cButton = (Button)findViewById(R.id.clearButton);
        cButton.setOnClickListener(mClearButtonListener);

        // Set up Symbology check box listeners
        mCheckBox1 = (CheckBox)findViewById(R.id.checkBox);
        mCheckBox1.setOnClickListener(mCheckBox1Listener);

        mLengthOneEditText = (EditText)findViewById(R.id.editTextLengthOne);
        mLengthTwoEditText = (EditText)findViewById(R.id.editTextLengthTwo);

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
        mModel = new ConfigureBarcodeModel();
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

    private final WeakHandler<ConfigureBarcodeActivity> mGenericModelHandler = new WeakHandler<ConfigureBarcodeActivity>(this) {

		@Override
		public void handleMessage(Message msg, ConfigureBarcodeActivity thisActivity) {
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
					else {
							mBarcodeResultsArrayAdapter.add(message);
							scrollBarcodeListViewToBottom();
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
    	boolean isConnected = getCommander().isConnected();

        // Set up current Symbology setting state
        mCheckBox1.setChecked(mModel.isCode128Enabled());

        // Remove listeners for lengths change
        mLengthOneEditText.removeTextChangedListener(mValue1EditTextChangedListener);
        mLengthTwoEditText.removeTextChangedListener(mValue2EditTextChangedListener);

        // Set up lengths
        String lengthOne = String.format(Locale.US, "%d", mModel.getLengthOne());
        mLengthOneEditText.setText(lengthOne);
        String lengthTwo = String.format(Locale.US, "%d", mModel.getLengthTwo());
        mLengthTwoEditText.setText(lengthTwo);

        // Add listeners for lengths change
        mLengthOneEditText.addTextChangedListener(mValue1EditTextChangedListener);
        mLengthTwoEditText.addTextChangedListener(mValue2EditTextChangedListener);
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
            	mModel.resetDevice();

                if( mModel.isImagerSupported())
                {
                    // Enable controls
                    mCheckBox1.setEnabled(true);
                    mLengthOneEditText.setEnabled(true);
                    mLengthTwoEditText.setEnabled(true);

                }
                else
                {
                    // Disable controls
                    mCheckBox1.setEnabled(false);
                    mLengthOneEditText.setEnabled(false);
                    mLengthTwoEditText.setEnabled(false);

                    mBarcodeResultsArrayAdapter.add("!!! Imager not supported !!!");
                    scrollBarcodeListViewToBottom();
                }
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
	// Button event handlers
	//----------------------------------------------------------------------------------------------

    // Scan action
    private OnClickListener mPersistButtonListener = new OnClickListener() {
        public void onClick(View v) {
            try {
                mModel.makePersistent();

                UpdateUI();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    // Scan action
    private OnClickListener mRestoreButtonListener = new OnClickListener() {
        public void onClick(View v) {
            try {
                mModel.restoreDefaults();

                UpdateUI();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    // Scan action
    private OnClickListener mScanButtonListener = new OnClickListener() {
        public void onClick(View v) {
            try {
                mResultTextView.setText("");
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
    			mBarcodeResultsArrayAdapter.clear();

    			UpdateUI();

    		} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    };
    

	//----------------------------------------------------------------------------------------------
	// Handler for changes in Symbology selections
	//----------------------------------------------------------------------------------------------

    private OnClickListener mCheckBox1Listener = new OnClickListener() {
    	public void onClick(View v) {
    		try {
                if( getCommander() != null )
                {
                    mModel.toggleCode128(mCheckBox1.isChecked());
                }

    		} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    };


    //----------------------------------------------------------------------------------------------
    // Handler for changes in Symbology lengths
    //
    // Note: Could be improved as this makes calls to the reader each time the text changes
    //----------------------------------------------------------------------------------------------

    private TextWatcher mValue1EditTextChangedListener = new TextWatcher() {

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void afterTextChanged(Editable s) {
                int value = 0;
                try {
                    value = Integer.parseInt(s.toString());
                } catch (Exception e) {}

                mModel.setLengthOne(value);
            }
    };

    private TextWatcher mValue2EditTextChangedListener = new TextWatcher() {

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void afterTextChanged(Editable s) {
            int value = 0;
            try {
                value = Integer.parseInt(s.toString());
            } catch (Exception e) {}

            mModel.setLengthTwo(value);
        }
    };


}
