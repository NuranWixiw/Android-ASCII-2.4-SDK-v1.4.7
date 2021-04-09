package com.uk.tsl.rfid.samples.licencekey;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

import com.uk.tsl.rfid.TSLBluetoothDeviceActivity;
import com.uk.tsl.rfid.ModelBase;
import com.uk.tsl.rfid.WeakHandler;
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander;
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand;
import com.uk.tsl.rfid.asciiprotocol.responders.LoggerResponder;
import com.uk.tsl.utils.StringHelper;

import android.os.Message;
import android.preference.PreferenceManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class LicenceKeyActivity extends TSLBluetoothDeviceActivity
{
	// Debug control
	private static final boolean D = BuildConfig.DEBUG;

	// Keys for preferences
	private static String SECRET_VALUE_PREFERENCE_KEY = "secret_key";
	private static String DEFAULT_SECRET_VALUE = "Setec Astronomy";

	// The model that performs all the actions
	private LicenceKeyModel mModel;

    // The list of results from actions
    private ArrayAdapter<String> mResultsArrayAdapter;
    private ListView mResultsListView;

    private EditText mSecretEditText;
    private TextView mAuthorisationBannerTextView;
    private CheckBox mOnlyAuthorisedReaderAllowedCheckBox;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_licence_key);

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

		mResultsArrayAdapter = new ArrayAdapter<String>(this,R.layout.result_item);

        // Find and set up the results ListView
        mResultsListView = (ListView) findViewById(R.id.resultListView);
        mResultsListView.setAdapter(mResultsArrayAdapter);
        mResultsListView.setFastScrollEnabled(true);

        mSecretEditText = (EditText)findViewById(R.id.secretEditText);
        mSecretEditText.addTextChangedListener(mSecretChangedTextWatcher);
        mSecretEditText.setText(preferences.getString(SECRET_VALUE_PREFERENCE_KEY, DEFAULT_SECRET_VALUE));
        

        // Hook up the reader action buttons
        Button sButton = (Button)findViewById(R.id.inventoryButton);
        sButton.setOnClickListener(mInventoryButtonListener);
        Button cButton = (Button)findViewById(R.id.clearButton);
        cButton.setOnClickListener(mClearButtonListener);
        Button bButton = (Button)findViewById(R.id.barcodeButton);
        bButton.setOnClickListener(mBarcodeButtonListener);

        // Hook up the authorise/deauthorise buttons
        Button authoriseButton = (Button)findViewById(R.id.authoriseButton);
        authoriseButton.setOnClickListener(mAuthoriseButtonListener);
        Button deAuthoriseButton = (Button)findViewById(R.id.deAuthoriseButton);
        deAuthoriseButton.setOnClickListener(mDeAuthoriseButtonListener);
        
        // Connect the Banner
        mAuthorisationBannerTextView = (TextView)findViewById(R.id.authorisationBannerTextView);

        // Connect the respond to authorised reader control
        mOnlyAuthorisedReaderAllowedCheckBox = (CheckBox)findViewById(R.id.onlyRespondToAuthorisedReadersCheckBox);
        mOnlyAuthorisedReaderAllowedCheckBox.setOnClickListener(new OnClickListener()
        {
			@Override
			public void onClick(View v)
			{
				CheckBox cb = (CheckBox)v;
				if( mModel != null )
				{
					mModel.setOnlyAuthorisedReaderAllowed(cb.isChecked());
				}
				UpdateUI();
			}
		});
        
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

        
        // Configure Model with commander and handler
        mModel = new LicenceKeyModel();
        mModel.setCommander(getCommander());
        mModel.setHandler(mLicenceKeyModelHandler);

        // Set the starting values for the model
		if( mModel != null )
		{
			mModel.setOnlyAuthorisedReaderAllowed(mOnlyAuthorisedReaderAllowedCheckBox.isChecked());
			mModel.setSecret(preferences.getString(SECRET_VALUE_PREFERENCE_KEY, DEFAULT_SECRET_VALUE));
		}
	}


    //----------------------------------------------------------------------------------------------
	// Pause & Resume life cycle
	//----------------------------------------------------------------------------------------------

    @Override
    public synchronized void onPause() {
        super.onPause();

        mModel.setEnabled(false);

        // Register to receive notifications from the AsciiCommander
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mCommanderMessageReceiver);
    }

    @Override
    public synchronized void onResume()
    {
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

    private final WeakHandler<LicenceKeyActivity> mLicenceKeyModelHandler = new WeakHandler<LicenceKeyActivity>(this) {

		@Override
		public void handleMessage(Message msg, LicenceKeyActivity thisActivity)
		{
			try
			{
				switch (msg.what)
				{
				case ModelBase.BUSY_STATE_CHANGED_NOTIFICATION:
					//TODO: process change in model busy state
					break;

				case ModelBase.MESSAGE_NOTIFICATION:
					// Examine the message for prefix
					String message = (String)msg.obj;
					if( message.startsWith("ER:")) {
						mResultsArrayAdapter.add( message.substring(3));
					}
					else if( message.startsWith("BC:")) {
						mResultsArrayAdapter.add(message.substring(3));
					} else {
						mResultsArrayAdapter.add(message);
					}
					scrollResultsListViewToBottom();
					UpdateUI();
					break;
					
				case LicenceKeyModel.AUTHORISATION_STATE_CHANGED_NOTIFICATION:
					// Show the message
					String aMessage = (String)msg.obj;
					if( !StringHelper.isNullOrEmpty(aMessage))
					{
						mResultsArrayAdapter.add(aMessage);
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
    	boolean isAuthorised = mModel != null && mModel.isReaderAuthorised();
    	mAuthorisationBannerTextView.setText(isAuthorised ? getString(R.string.banner_title_authorised) : getString(R.string.banner_title_not_authorised));
        mAuthorisationBannerTextView.setBackgroundColor(isAuthorised ? Color.parseColor("#00A000") : Color.parseColor("#808080"));
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
            Toast.makeText(context, connectionStateMsg, Toast.LENGTH_LONG).show();

            displayReaderState();

            if( getCommander().isConnected() )
            {
            	mModel.resetDevice();
                mModel.updateConfiguration();
            }
            else
            {
            	mModel.validateReader();
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
	// Secret changed event handler
	//----------------------------------------------------------------------------------------------

    private TextWatcher mSecretChangedTextWatcher = new TextWatcher() {
		
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {}
		
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
		
		@Override
		public void afterTextChanged(Editable s)
		{
			String secretValue = s.toString();
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(LicenceKeyActivity.this);
			preferences.edit().putString(SECRET_VALUE_PREFERENCE_KEY, secretValue).commit();
			if( mModel != null )
			{
				mModel.setSecret(secretValue);
				mModel.validateReader();
			}
		}
	};
    
    
	//----------------------------------------------------------------------------------------------
	// Button event handlers
	//----------------------------------------------------------------------------------------------

    // Scan action
    private OnClickListener mInventoryButtonListener = new OnClickListener() {
    	public void onClick(View v) {
    		try {
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

    			UpdateUI();

    		} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    };
    
    // Barcode scan action
    private OnClickListener mBarcodeButtonListener = new OnClickListener() {
    	public void onClick(View v) {
    		try {
    			// Perform a transponder scan
    			mModel.barcodeScan();

    			UpdateUI();

    		} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    };

    
    // Authorise action
    private OnClickListener mAuthoriseButtonListener = new OnClickListener() {
    	public void onClick(View v) {
    		try {
    			mResultsArrayAdapter.clear();

    			// Authorise the reader by writing the correct licence key to it
    			mModel.authoriseReader();

    			UpdateUI();

    		} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    };


    // De-authorise action
    private OnClickListener mDeAuthoriseButtonListener = new OnClickListener() {
    	public void onClick(View v) {
    		try {
    			mResultsArrayAdapter.clear();

    			// De-authorise the reader by removing the licence key from it
    			mModel.deAuthoriseReader();

    			UpdateUI();

    		} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    };


}
