package com.uk.tsl.rfid.samples.licencekey;

import java.security.MessageDigest;
import java.util.Locale;

import android.os.Message;
import android.util.Log;

import com.uk.tsl.rfid.ModelBase;
import com.uk.tsl.rfid.asciiprotocol.commands.BarcodeCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.InventoryCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.LicenceKeyCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.VersionInformationCommand;
import com.uk.tsl.rfid.asciiprotocol.enumerations.DeleteConfirmation;
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState;
import com.uk.tsl.rfid.asciiprotocol.responders.IBarcodeReceivedDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.ICommandResponseLifecycleDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.ITransponderReceivedDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.TransponderData;
import com.uk.tsl.utils.HexEncoding;

public class LicenceKeyModel extends ModelBase
{
	// Model busy state changed message
	public static final int AUTHORISATION_STATE_CHANGED_NOTIFICATION = 100;

	public boolean enabled() { return mEnabled; }

	public void setEnabled(boolean state)
	{
		boolean oldState = mEnabled;
		mEnabled = state;

		// Update the commander for state changes
		if(oldState != state) {
			if( mEnabled ) {
				// Listen for transponders
				getCommander().addResponder(mInventoryResponder);
				// Listen for barcodes
				getCommander().addResponder(mBarcodeResponder);
			} else {
				// Stop listening for transponders
				getCommander().removeResponder(mInventoryResponder);
				// Stop listening for barcodes
				getCommander().removeResponder(mBarcodeResponder);
			}
		}
	}

	/**
	 * @return the readerAuthorised
	 */
	public final boolean isReaderAuthorised() {
		return mReaderAuthorised;
	}

	/**
	 * @param readerAuthorised the readerAuthorised to set
	 */
	public final void setReaderAuthorised(boolean readerAuthorised) {
		mReaderAuthorised = readerAuthorised;
	}

	/**
	 * @return the onlyAuthorisedReaderAllowed
	 */
	public final boolean isOnlyAuthorisedReaderAllowed() {
		return mOnlyAuthorisedReaderAllowed;
	}

	/**
	 * @param onlyAuthorisedReaderAllowed the onlyAuthorisedReaderAllowed to set
	 */
	public final void setOnlyAuthorisedReaderAllowed(
			boolean onlyAuthorisedReaderAllowed) {
		mOnlyAuthorisedReaderAllowed = onlyAuthorisedReaderAllowed;
	}

	/**
	 * @return the secret
	 */
	public final String getSecret() {
		return mSecret;
	}

	/**
	 * @param secret the secret to set
	 */
	public final void setSecret(String secret) {
		mSecret = secret;
	}

	//	 The minimum ASCII protocol version that suppports the licence key command
	public static final String MINIMUM_ASCII_PROTOCOL_VERSION_FOR_LICENCE_KEY_COMMAND = "2.2";

	// Backing fields for properties
	private boolean mEnabled;
	private boolean mReaderAuthorised;
	private boolean mOnlyAuthorisedReaderAllowed;
	private String mSecret;

	// Used to issue message for inventory commands that receive no tags
	private boolean mAnyTagSeen;

	// The command to use as a responder to capture incoming inventory responses
	private InventoryCommand mInventoryResponder;
	// The command used to issue commands
	private InventoryCommand mInventoryCommand;

	// The command to use as a responder to capture incoming barcode responses
	private BarcodeCommand mBarcodeResponder;
	// The command used to issue barcode commands
	private BarcodeCommand mBarcodeCommand;

	// The command used to extract version information from the reader
	private VersionInformationCommand mVersionCommand; 
	
	public LicenceKeyModel()
	{
		// This command is used to obtain information about the reader
		mVersionCommand = VersionInformationCommand.synchronousCommand();

		// This is the command that will be used to perform configuration changes and inventories
		mInventoryCommand = new InventoryCommand();

		// Configure the type of inventory
		mInventoryCommand.setIncludeTransponderRssi(TriState.YES);
		mInventoryCommand.setIncludeChecksum(TriState.YES);
		mInventoryCommand.setIncludePC(TriState.YES);
		
		// Use an InventoryCommand as a responder to capture all incoming inventory responses
		mInventoryResponder = new InventoryCommand();

		// Also capture the responses that were not from App commands 
		mInventoryResponder.setCaptureNonLibraryResponses(true);

		// Notify when each transponder is seen
		mInventoryResponder.setTransponderReceivedDelegate(new ITransponderReceivedDelegate()
		{

			int mTagsSeen = 0;
			@Override
			public void transponderReceived(TransponderData transponder, boolean moreAvailable)
			{
				if( isReaderAuthorised() || !isOnlyAuthorisedReaderAllowed() )
				{
					mAnyTagSeen = true;

					String infoMsg = String.format(Locale.US, "\nRSSI: %d  PC: %04X  CRC: %04X", transponder.getRssi(), transponder.getPc(), transponder.getCrc());
					sendMessageNotification("EPC: " + transponder.getEpc() + infoMsg );
					mTagsSeen++;
					if( !moreAvailable) {
						sendMessageNotification("");
						Log.d("TagCount",String.format("Tags seen: %s", mTagsSeen));
					}
				}
			}
		});

		mInventoryResponder.setResponseLifecycleDelegate( new ICommandResponseLifecycleDelegate() {
			
			@Override
			public void responseEnded()
			{
				if( isReaderAuthorised() || !isOnlyAuthorisedReaderAllowed() )
				{
					if( !mAnyTagSeen && mInventoryCommand.getTakeNoAction() != TriState.YES)
					{
						sendMessageNotification("No transponders seen");
					}
					mInventoryCommand.setTakeNoAction(TriState.NO);
				}
			}
			
			@Override
			public void responseBegan() { mAnyTagSeen = false; }
		});

		// This is the command that will be used to issue barcode scans (with default parameters)
		mBarcodeCommand = new BarcodeCommand();
		
		// This command is used to capture barcode responses
		mBarcodeResponder = new BarcodeCommand();
		mBarcodeResponder.setCaptureNonLibraryResponses(true);
		mBarcodeResponder.setUseEscapeCharacter(TriState.YES);
		mBarcodeResponder.setBarcodeReceivedDelegate(new IBarcodeReceivedDelegate()
		{
			@Override
			public void barcodeReceived(String barcode)
			{
				if( isReaderAuthorised() || !isOnlyAuthorisedReaderAllowed() )
				{
					sendMessageNotification("BC: " + barcode);
				}
			}
		});
	}

	
	//
	// Reset the reader configuration for the switch actions, inventory and barcode commands
	//
	public void resetDevice()
	{
		if(getCommander().isConnected()) {
			getCommander().executeCommand(new FactoryDefaultsCommand());
		}
	}

	/**
	 * @param versionString The string form of the version number
	 * @return a numeric value that allows version number strings to be compared
	 */
	private static int comparableVersionValue(String versionString)
	{
		String[] parts = versionString.split("\\.");
		if( parts.length == 0 || parts.length > 3) return -1;

		try
		{
			int scale = 1 << 16;
			int value = 0;
			for( String part : parts)
			{
				int digitValue = Integer.parseInt(part);
				value += digitValue * scale;
				scale >>= 8;
			}
			return value;
		}
		catch( Exception e)
		{
			return -1;
		}
		
	}
	
	//
	// Update the reader configuration from the command
	// Call this after each change to the model's command
	//
	public void updateConfiguration()
	{
		if(getCommander().isConnected())
		{
			// Configure the inventory operations
			mInventoryCommand.setTakeNoAction(TriState.YES);
			getCommander().executeCommand(mInventoryCommand);

			// Update the connected reader version information
			getCommander().executeCommand(mVersionCommand);
			
			if( comparableVersionValue(mVersionCommand.getAsciiProtocol())
					< comparableVersionValue(MINIMUM_ASCII_PROTOCOL_VERSION_FOR_LICENCE_KEY_COMMAND))
			{
				sendMessageNotification(String.format("Reader does not support licence keys\nRequires ASCII protocol: %s\nReader ASCII protocol: %s\n",
						MINIMUM_ASCII_PROTOCOL_VERSION_FOR_LICENCE_KEY_COMMAND,
						mVersionCommand.getAsciiProtocol()));
			}

			validateReader();
		}
	}

	
	//
	// Perform an inventory scan with the current command parameters
	//
	public void scan()
	{
		if( mCommander.isConnected())
		{
			if( isReaderAuthorised() || !isOnlyAuthorisedReaderAllowed() )
			{
				if(getCommander().isConnected())
				{
					mInventoryCommand.setTakeNoAction(TriState.NO);
					getCommander().executeCommand(mInventoryCommand);
				}
				else
				{
					sendMessageNotification("Reader not connected!");
				}
			}
			else
			{
				sendMessageNotification("Reader NOT authorised!");
			}
		}
		else
		{
			 sendMessageNotification("Reader not connected!\n");
		}
	}


	//
	// Perform a barcode scan with the current command parameters
	//
	public void barcodeScan()
	{
		if( mCommander.isConnected())
		{
			if( isReaderAuthorised() || !isOnlyAuthorisedReaderAllowed() )
			{
				if(getCommander().isConnected())
				{
					mBarcodeCommand.setTakeNoAction(TriState.NO);
					getCommander().executeCommand(mBarcodeCommand);
				}
			}
			else
			{
				sendMessageNotification("Reader NOT authorised!");
			}
		}
		else
		{
			 sendMessageNotification("Reader not connected!\n");
		}
	}


	/**
	 * Send a message to the client using the current Handler
	 * 
	 * @param message The message to send as String
	 */
	protected void sendAuthorisationStateNotification(String message)
	{
		if( mHandler != null )
		{
			Message msg = mHandler.obtainMessage(AUTHORISATION_STATE_CHANGED_NOTIFICATION, message);
			mHandler.sendMessage(msg);
		}
	}

	/**
	 * @param sourceText the text to be converted to a hash
	 * @param algorithm the hashing algorithm to use
	 * @return the ASCII hex encoded hash of sourceText using algorithm
	 */
	private String asciiHexEncodedHash(String sourceText, String algorithm)
	{
		MessageDigest digest;
		String hashedText = "";
		try
		{
			digest = MessageDigest.getInstance(algorithm);
			byte[] hash = digest.digest(sourceText.getBytes("US-ASCII"));
			hashedText = HexEncoding.bytesToString(hash);
		}
		catch (Exception e){}

		return hashedText;
	}
	
	/**
	 * @param serialNumber the serial number to include in the licence key
	 * @param bluetoothAddress the bluetooth address to include in the licence key
	 * @param secretValue the secret value to include in the licence key
	 * @return the licence key created from the input parameters 
	 */
	private String createLicenceKey(String serialNumber, String bluetoothAddress, String secretValue)
	{
		String licenceKey;
		String licenceKeySourceValue = String.format("%s%s%s", serialNumber, bluetoothAddress, secretValue);
		licenceKey = asciiHexEncodedHash(licenceKeySourceValue, "SHA-256");
		return licenceKey;
	}

	//
	// Check to see if the reader contains a valid licence key
	//
	// Note: This is just an example of a simple salted hash key and is in no way intended to represent
	// a lesson in cryptography or App security! This approach is susceptible to reverse engineering of the apk
	// since it contains the 'secret' value. Investigate asymmetric (public key) cryptographic solutions to overcome this.
	//
	public void validateReader()
	{
		boolean isAuthorisedReader = false;
		String message = null;

		if( mCommander.isConnected() )
		{
			// Retrieve the current licence key
			LicenceKeyCommand licenceKeyCommand = LicenceKeyCommand.synchronousCommand();
			mCommander.executeCommand(licenceKeyCommand);

			if( licenceKeyCommand.isSuccessful() )
			{
				message = String.format("Reader Licence Key:\n%s\n\n", licenceKeyCommand.getLicenceKey());

				// Generate the licence key that should be on the reader if it is authorised 
				String expectedLicenceKey = createLicenceKey(mVersionCommand.getSerialNumber(),
						mVersionCommand.getBluetoothAddress(), getSecret());

				message = String.format("%sExpected Licence key:\n%s\n\n", message, expectedLicenceKey);

				isAuthorisedReader = licenceKeyCommand.getLicenceKey().equals(expectedLicenceKey);
			}
			else
			{
				message = "Unable to validate reader!\n";
			}
		}

		setReaderAuthorised(isAuthorisedReader);
		sendAuthorisationStateNotification(message);
	}

	//
	// Write the licence key generated for the currently connected reader to the reader
	//
	public void authoriseReader()
	{
		if( mCommander.isConnected())
		{
			// Calculate the licence key based on the reader properties and the current value of the 'secret'
			String licenceKey = createLicenceKey(mVersionCommand.getSerialNumber(),
					mVersionCommand.getBluetoothAddress(), getSecret());

			// Set the licence key on the reader
			LicenceKeyCommand licenceKeyCommand = LicenceKeyCommand.synchronousCommand();
			licenceKeyCommand.setLicenceKey(licenceKey);
			licenceKeyCommand.setDeleteLicenceKey(DeleteConfirmation.YES);
			mCommander.executeCommand(licenceKeyCommand);
			
			if( licenceKeyCommand.isSuccessful() )
			{
				validateReader();
			}
			else
			{
				sendMessageNotification("Unable to authorise reader!\n");
			}
		}
		else
		{
			 sendMessageNotification("Reader not connected!\n");
		}
	}


	//
	// Remove ANY licence key from the reader
	//
	public void deAuthoriseReader()
	{
		if( mCommander.isConnected())
		{
			// Delete the licence key from the reader
			LicenceKeyCommand licenceKeyCommand = LicenceKeyCommand.synchronousCommand();
			licenceKeyCommand.setDeleteLicenceKey(DeleteConfirmation.YES);
			mCommander.executeCommand(licenceKeyCommand);
			
			if( licenceKeyCommand.isSuccessful() )
			{
				validateReader();
			}
			else
			{
				sendMessageNotification("Unable to de-authorise reader!\n");
			}
		}
		else
		{
			 sendMessageNotification("Reader not connected!\n");
		}
	}



}
