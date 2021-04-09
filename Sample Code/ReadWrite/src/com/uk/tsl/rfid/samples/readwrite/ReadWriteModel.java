//----------------------------------------------------------------------------------------------
// Copyright (c) 2013 Technology Solutions UK Ltd. All rights reserved.
//----------------------------------------------------------------------------------------------

package com.uk.tsl.rfid.samples.readwrite;

import com.uk.tsl.rfid.ModelBase;
import com.uk.tsl.rfid.ModelException;
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.ReadTransponderCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.WriteTransponderCommand;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QuerySelect;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QuerySession;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QueryTarget;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SelectAction;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SelectTarget;
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState;
import com.uk.tsl.rfid.asciiprotocol.responders.AsciiSelfResponderCommandBase;
import com.uk.tsl.rfid.asciiprotocol.responders.ITransponderReceivedDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.TransponderData;
import com.uk.tsl.utils.HexEncoding;

public class ReadWriteModel extends ModelBase {

	// The instances used to issue commands
	private final ReadTransponderCommand mReadCommand = ReadTransponderCommand.synchronousCommand();
	private final WriteTransponderCommand mWriteCommand = WriteTransponderCommand.synchronousCommand();

	// The inventory command configuration
	public ReadTransponderCommand getReadCommand() { return mReadCommand; }
	public WriteTransponderCommand getWriteCommand() { return mWriteCommand; }


	private int mTransponderCount;

	/**
	 * A class to demonstrate the use of the AsciiProtocol library to read and write to transponders
	 */
	public ReadWriteModel()
	{
		mReadCommand.setOffset(0);
		mReadCommand.setLength(1);
		mWriteCommand.setOffset(0);
		mWriteCommand.setLength(1);
	}

    //
    // Reset the reader configuration to default command values
    //
    public void resetDevice()
    {
        if(getCommander().isConnected()) {
            getCommander().executeCommand(new FactoryDefaultsCommand());
        }
    }

    //----------------------------------------------------------------------------------------------
	// Read
	//----------------------------------------------------------------------------------------------

	// Set the parameters that are not user-specified
	private void setFixedReadParameters()
	{
		mReadCommand.setResetParameters(TriState.YES);

		// Configure the select to match the given EPC
		// EPC is in hex and length is in bits
		String epcHex = mReadCommand.getSelectData();

		if( epcHex == null || epcHex.length() == 0) {
			// Match anything by not selecting tags and querying the default A state
			mReadCommand.setInventoryOnly(TriState.YES);

			mReadCommand.setQuerySelect(QuerySelect.ALL);
			mReadCommand.setQuerySession(QuerySession.SESSION_0);
			mReadCommand.setQueryTarget(QueryTarget.TARGET_A);

			// Reset other properties used when matching
			mReadCommand.setSelectData(null);
			mReadCommand.setSelectOffset(-1);
			mReadCommand.setSelectLength(-1);
			mReadCommand.setSelectAction(SelectAction.NOT_SPECIFIED);
			mReadCommand.setSelectTarget(SelectTarget.NOT_SPECIFIED);
		} else {
			mReadCommand.setInventoryOnly(TriState.NO);

			// Only match the EPC value not the CRC or PC
			mReadCommand.setSelectOffset(0x20);
			mReadCommand.setSelectLength(epcHex.length() * 4);

			// Use session with long persistence and select tags away from default state
			mReadCommand.setSelectAction(SelectAction.DEASSERT_SET_B_NOT_ASSERT_SET_A);
			mReadCommand.setSelectTarget(SelectTarget.SESSION_2);

			mReadCommand.setQuerySelect(QuerySelect.ALL);
			mReadCommand.setQuerySession(QuerySession.SESSION_2);
			mReadCommand.setQueryTarget(QueryTarget.TARGET_B);
		}

		
		mReadCommand.setTransponderReceivedDelegate(new ITransponderReceivedDelegate() {
			
			@Override
			public void transponderReceived(TransponderData transponder, boolean moreAvailable) {
				byte[] data = transponder.getReadData();
				String dataMessage = ( data == null ) ? "No data!" : HexEncoding.bytesToString(data);
				String eaMsg = transponder.getAccessErrorCode() == null ? "" : "\n" + transponder.getAccessErrorCode().getDescription() + " (EA)";
				String ebMsg = transponder.getBackscatterErrorCode() == null ? "" : "\n" + transponder.getBackscatterErrorCode().getDescription() + " (EB)";
				String errorMsg = eaMsg + ebMsg;
				if (errorMsg.length() > 0 ) {
					errorMsg = "Error: " + errorMsg + "\n";
				}

				sendMessageNotification(String.format(
						"\nEPC: %s\nData: %s\n%s",
						transponder.getEpc(),
						dataMessage,
						errorMsg
						));
				++mTransponderCount;

				if( !moreAvailable) {
					sendMessageNotification("\n");
				}
			}
		});
	}


	public void read()
	{
		try {
			sendMessageNotification("\nReading...\n");

			setFixedReadParameters();
			mTransponderCount = 0;

			performTask(new Runnable() {
				@Override
				public void run() {
				
					getCommander().executeCommand(mReadCommand);

					sendMessageNotification("\nTransponders seen: " + mTransponderCount +"\n");
					reportErrors(mReadCommand);
					sendMessageNotification( String.format("Time taken: %.2fs", getTaskExecutionDuration()) );
					
				}
			});
			
		} catch (ModelException e) {
			sendMessageNotification("Unable to perform action: " + e.getMessage());
		}
	
	}

	
    //----------------------------------------------------------------------------------------------
	// Write
	//----------------------------------------------------------------------------------------------

	// Set the parameters that are not user-specified
	private void setFixedWriteParameters()
	{
		mWriteCommand.setResetParameters(TriState.YES);

		// Set the data length
		if( mWriteCommand.getData() == null ) {
			mWriteCommand.setLength(0);
		} else {
			mWriteCommand.setLength(mWriteCommand.getData().length / 2);
		}
		
		// Configure the select to match the given EPC
		// EPC is in hex and length is in bits
		String epcHex = mWriteCommand.getSelectData();

		if( epcHex != null ) {
			// Only match the EPC value not the CRC or PC
			mWriteCommand.setSelectOffset(0x20);
			mWriteCommand.setSelectLength(epcHex.length() * 4);
		}

		mWriteCommand.setSelectAction(SelectAction.DEASSERT_SET_B_NOT_ASSERT_SET_A);
		mWriteCommand.setSelectTarget(SelectTarget.SESSION_2);

		mWriteCommand.setQuerySelect(QuerySelect.ALL);
		mWriteCommand.setQuerySession(QuerySession.SESSION_2);
		mWriteCommand.setQueryTarget(QueryTarget.TARGET_B);

		mWriteCommand.setTransponderReceivedDelegate(new ITransponderReceivedDelegate() {
			
			@Override
			public void transponderReceived(TransponderData transponder, boolean moreAvailable) {
				String eaMsg = transponder.getAccessErrorCode() == null ? "" : "\n" + transponder.getAccessErrorCode().getDescription() + " (EA)";
				String ebMsg = transponder.getBackscatterErrorCode() == null ? "" : "\n" + transponder.getBackscatterErrorCode().getDescription() + " (EB)";
				String errorMsg = eaMsg + ebMsg;
				if (errorMsg.length() > 0 ) {
					errorMsg = "Error: " + errorMsg + "\n";
				}

				sendMessageNotification(String.format(
						"\nEPC: %s\nWords Written: %d of %d\n%s",
						transponder.getEpc(),
						transponder.getWordsWritten(), mWriteCommand.getLength(),
						errorMsg
						));
				++mTransponderCount;

				if( !moreAvailable) {
					sendMessageNotification("\n");
				}
			}
		});
	}


	public void write()
	{
		try {
			sendMessageNotification("\nWriting...\n");

			setFixedWriteParameters();
			mTransponderCount = 0;

			performTask(new Runnable() {
				@Override
				public void run() {
				
					getCommander().executeCommand(mWriteCommand);

					sendMessageNotification("\nTransponders seen: " + mTransponderCount +"\n");
					reportErrors(mWriteCommand);
					sendMessageNotification( String.format("Time taken: %.2fs", getTaskExecutionDuration()) );
					
				}
			});
			
		} catch (ModelException e) {
			sendMessageNotification("Unable to perform action: " + e.getMessage());
		}
	
	}

	/**
	 * Check the given command for errors and report them via the model message system
	 * @param command The command to check
	 */
	private void reportErrors(AsciiSelfResponderCommandBase command)
	{
		if( !command.isSuccessful() ) {
			sendMessageNotification(String.format(
					"%s failed!\nError code: %s\n", command.getClass().getSimpleName(), command.getErrorCode()));
			for (String message : command.getMessages()) {
				sendMessageNotification(message + "\n");
			}
		}
		
	}
	
}
