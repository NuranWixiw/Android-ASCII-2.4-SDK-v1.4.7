//----------------------------------------------------------------------------------------------
// Copyright (c) 2013 Technology Solutions UK Ltd. All rights reserved.
//----------------------------------------------------------------------------------------------

package com.uk.tsl.rfid.samples.trigger;

import android.os.Message;

import com.uk.tsl.rfid.ModelBase;
import com.uk.tsl.rfid.asciiprotocol.commands.AlertCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.SwitchActionCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.SwitchStateCommand;
import com.uk.tsl.rfid.asciiprotocol.enumerations.AlertDuration;
import com.uk.tsl.rfid.asciiprotocol.enumerations.BuzzerTone;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SwitchAction;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SwitchState;
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState;
import com.uk.tsl.rfid.asciiprotocol.responders.ISwitchStateReceivedDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.SwitchResponder;

/**
 * 
 *
 */
public class TriggerModel extends ModelBase
{
	/**
	 * Message id for an asynchronous switch state notification
	 */
	public static final int ASYNC_SWITCH_STATE_NOTIFICATION = 100;
	/**
	 * Message id for a polled switch state notification
	 */
	public static final int POLLED_SWITCH_STATE_NOTIFICATION = 101;


	private final int POLL_INTERVAL_IN_MS = 300;

	/**
	 * Getter for asynchronous reporting state enabled
	 */
	public boolean isAsyncReportingEnabled() { return mAsyncReportingEnabled; }

	/**
	 *  Change the asynchronous reporting state and adjust switch action accordingly
	 *  This may fail if the reader cannot be configured
	 *  
	 * @param isEnabled true if asynchronous reporting is to be enabled
	 * @return true if the command succeeded
	 */
	public boolean setAsyncReportingEnabled(boolean isEnabled)
	{
		boolean succeeded = false;
		// Configure the switch actions
		SwitchActionCommand saCommand = SwitchActionCommand.synchronousCommand();

		if( isEnabled ) {

			/// Enable asynchronous switch state reporting
			saCommand.setAsynchronousReportingEnabled(TriState.YES);
			// Disable the default switch actions
			saCommand.setSinglePressAction(SwitchAction.OFF);
			saCommand.setDoublePressAction(SwitchAction.OFF);

		} else {

			if( !mPolledReportingEnabled ) {

				// Reset default switch actions
				saCommand.setResetParameters(TriState.YES);

				resetReaderAlert();

			} else {

				// Just turn the asynchronous reporting off
				saCommand.setAsynchronousReportingEnabled(TriState.NO);
			}
		}

		try {
			getCommander().executeCommand(saCommand);

			mAsyncReportingEnabled = isEnabled;
			succeeded = true;

		} catch (Exception e) {
			// Unable to change the state
		}
		return succeeded;
	}
	private boolean mAsyncReportingEnabled;
	
	/**
	 * Getter for the polled reporting enabled
	 */
	public boolean isPolledReportingEnabled() { return mPolledReportingEnabled; }

	/**
	 *  Change the polled reporting state and adjust switch action accordingly
	 *  This may fail if the reader cannot be configure
	 *  
	 * @param isEnabled true if polled reporting is to be enabled
	 * @return true if the command succeeded
	 */
	public boolean setPolledReportingEnabled(boolean isEnabled) {
		boolean succeeded = false;
		// Configure the switch actions
		SwitchActionCommand saCommand = SwitchActionCommand.synchronousCommand();

		if( isEnabled ) {
			// Disable the default switch actions
			saCommand.setSinglePressAction(SwitchAction.OFF);
			saCommand.setDoublePressAction(SwitchAction.OFF);

			mHandler.postDelayed(mPollingUpdateRunnable, POLL_INTERVAL_IN_MS);

		} else {
			mHandler.removeCallbacks(mPollingUpdateRunnable);

			if( !mAsyncReportingEnabled ) {
				// Reset default switch actions
				saCommand.setResetParameters(TriState.YES);

				resetReaderAlert();
			}
		}

		try {
			getCommander().executeCommand(saCommand);

			mPolledReportingEnabled = isEnabled;
			succeeded = true;

		} catch (Exception e) {
			mHandler.removeCallbacks(mPollingUpdateRunnable);
		}
		return succeeded;
	}
	private boolean mPolledReportingEnabled;

	/**
	 * Set the reader's switch and alert back to default settings
	 */
	private void resetReaderAlert()
	{
		// Reset alerts
		AlertCommand aCommand = new AlertCommand();
		aCommand.setResetParameters(TriState.YES);
		aCommand.setTakeNoAction(TriState.YES);
		getCommander().executeCommand(aCommand);
	}
	/**
	 * 
	 */
	public TriggerModel()
	{
		super();
		mAsyncReportingEnabled = false;
		mPolledReportingEnabled = false;
	}

	/**
	 * Prepare the model for first use
	 */
	public void initialise()
	{
		// Use the SwitchResponder to monitor asynchronous switch reports
		SwitchResponder switchResponder = new SwitchResponder();
		switchResponder.setSwitchStateReceivedDelegate(mSwitchDelegate);
		mCommander.addResponder(switchResponder);
	}

	// Forward the state changes to the current Handler (UI thread)
	private final ISwitchStateReceivedDelegate mSwitchDelegate = new ISwitchStateReceivedDelegate() {
		
		@Override
		public void switchStateReceived(SwitchState state) {
			sendStatusNotification(state, true);

			// Use the alert command to indicate the type of asynchronous switch press
			// No vibration just vary the tone & duration 
			if( !SwitchState.OFF.equals(state)) {
				AlertCommand aCommand = new AlertCommand();
				aCommand.setResetParameters(TriState.YES);
				aCommand.setEnableVibrator(TriState.NO);
				aCommand.setDuration(AlertDuration.SHORT);
				if( SwitchState.SINGLE.equals(state)) {
					aCommand.setTone(BuzzerTone.HIGH);
				} else if(SwitchState.DOUBLE.equals(state)) {
					aCommand.setTone(BuzzerTone.MEDIUM);
				}
				getCommander().executeCommand(aCommand);
			}
		}
	};
	

	private final Runnable mPollingUpdateRunnable = new Runnable() {
		
		@Override
		public void run() {
			// Report polled switch state
			try {
				SwitchStateCommand ssCommand = SwitchStateCommand.synchronousCommand();
				getCommander().executeCommand(ssCommand);

				sendStatusNotification(ssCommand.getState(), false);

			} catch (Exception e) {	/* failed to read - ignore */ }

			// Schedule the next poll
			mHandler.postDelayed(this, POLL_INTERVAL_IN_MS);
		}
	};
	

	/**
	 * Send a switch state message to the client using the current Handler
	 * 
	 * @param switchState the state of the switch
	 * @param isAsync true if the message was received asynchronously (otherwise it was from polling)
	 */
	protected void sendStatusNotification(SwitchState switchState, boolean isAsync)
	{
		if( mHandler != null )
		{
			Message msg = mHandler.obtainMessage(isAsync ? ASYNC_SWITCH_STATE_NOTIFICATION : POLLED_SWITCH_STATE_NOTIFICATION, switchState);
			mHandler.sendMessage(msg);
		}
	}


}
