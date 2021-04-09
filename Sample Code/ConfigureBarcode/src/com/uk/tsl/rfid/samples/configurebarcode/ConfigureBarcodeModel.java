//----------------------------------------------------------------------------------------------
// Copyright (c) 2013 Technology Solutions UK Ltd. All rights reserved.
//----------------------------------------------------------------------------------------------

package com.uk.tsl.rfid.samples.configurebarcode;

import com.uk.tsl.barcodeconfiguration.BarcodeConfiguration;
import com.uk.tsl.barcodeconfiguration.BarcodeConfigurationEnableDisable;
import com.uk.tsl.barcodeconfiguration.Code128Symbology;
import com.uk.tsl.barcodeconfiguration.ReaderMemory;
import com.uk.tsl.rfid.ModelBase;
import com.uk.tsl.rfid.ModelException;
import com.uk.tsl.rfid.asciiprotocol.commands.BarcodeCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand;
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState;
import com.uk.tsl.rfid.asciiprotocol.responders.IBarcodeReceivedDelegate;

//
// This class demonstrates the use of the BarcodeConfiguration API to modify the Code128 support
// It demonstrates the use of the BarcodeConfigurationEnableDisable and BarcodeConfigurationLengths.
// All the modifications are done using the ReaderMemory.Temporary flag to ensure that changes are not permanent.
//
public class ConfigureBarcodeModel extends ModelBase
{

	// Control 
	private boolean mEnabled;
	public boolean enabled() { return mEnabled; }

    public boolean isImagerSupported()
    {
        return BarcodeConfiguration.isSupported(this.getCommander());
    }


    Code128Symbology getCode128Settings()
    {
        if( mCode128Settings == null && this.getCommander() != null)
        {
            mCode128Settings = new Code128Symbology(this.getCommander());
        }
        return mCode128Settings;
    }

    // Symbology Settings
    private Code128Symbology mCode128Settings;

    public int getLengthOne()
    {
        if( isImagerSupported() )
        {
            return getCode128Settings().getCode128Lengths().getLength1();
        }
        return -1;
    }

    public void setLengthOne(int lengthOne)
    {
        if( isImagerSupported() )
        {
            // Only make temporary modifications - change to ReaderMemory.Permanent to persist the changes
            getCode128Settings().getCode128Lengths().setLengths(lengthOne, getLengthTwo(), ReaderMemory.Temporary);
        }
    }

    public int getLengthTwo()
    {
        if( isImagerSupported() )
        {
            return getCode128Settings().getCode128Lengths().getLength2();
        }
        return -1;
    }

    public void setLengthTwo(int lengthTwo)
    {
        if( isImagerSupported() )
        {
            // Only make temporary modifications - change to ReaderMemory.Permanent to persist the changes
            getCode128Settings().getCode128Lengths().setLengths(getLengthOne(), lengthTwo, ReaderMemory.Temporary);
        }
    }


	public void setEnabled(boolean state)
	{
		boolean oldState = mEnabled;
		mEnabled = state;

		// Update the commander for state changes
		if(oldState != state) {
			if( mEnabled ) {
				// Listen for barcodes
				getCommander().addResponder(mBarcodeResponder);
			} else {
				// Stop listening for barcodes
				getCommander().removeResponder(mBarcodeResponder);
			}
			
		}
	}

	// The command to use as a responder to capture incoming barcode responses
	private BarcodeCommand mBarcodeResponder;
	
	public ConfigureBarcodeModel()
	{
		// This command is used to capture barcode responses
		mBarcodeResponder = new BarcodeCommand();
		mBarcodeResponder.setCaptureNonLibraryResponses(true);
		mBarcodeResponder.setUseEscapeCharacter(TriState.YES);
		mBarcodeResponder.setBarcodeReceivedDelegate(new IBarcodeReceivedDelegate() {
			@Override
			public void barcodeReceived(String barcode) {
				sendMessageNotification("> " + barcode);
			}
		});

	
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

	//
    // Initiate a barcode scan
    //
    public void scan()
    {
        try
        {
            performTask(new Runnable()
            {
                @Override
                public void run()
                {
                    BarcodeCommand bcCommand = new BarcodeCommand();
                    ConfigureBarcodeModel.this.getCommander().executeCommand(bcCommand);
                }
            });
        }
        catch (ModelException e)
        {
            sendMessageNotification("Unable to perform action: " + e.getMessage());
        }
    }

    //
    // Enabled state of the symbology
    //
    public boolean isCode128Enabled()
    {
        if( isImagerSupported() )
        {
            Code128Symbology symbologySetting = this.getCode128Settings();
            BarcodeConfigurationEnableDisable enabler = symbologySetting.getCode128();
            boolean isEnabled = enabler.isEnabled();
            return isEnabled;
        }
        return false;
    }


	//
    // Toggles the enabled state of the symbology temporarily
    //
	public void toggleCode128(boolean isEnabled)
    {
        if( isImagerSupported() )
        {
            // Only make temporary modifications - change to ReaderMemory.Permanent to persist the changes
            if (isEnabled)
            {
                getCode128Settings().getCode128().enable(ReaderMemory.Temporary);
            }
            else
            {
                getCode128Settings().getCode128().disable(ReaderMemory.Temporary);
            }
            sendMessageNotification("Code128: " + (getCode128Settings().getCode128().isEnabled() ? "Enabled" : "Disabled"));
        }
        else
        {
            sendMessageNotification("Not supported!");
        }
    }

    //
    // Make the temporary changes permanent
    //
    public void makePersistent()
    {
        if( isImagerSupported() )
        {
            if( getCode128Settings().getSaveRequired() )
            {
                getCode128Settings().makePermanent();
                sendMessageNotification("Code128: Settings now persistent.");
            }
            else
            {
                sendMessageNotification("Nothing to persist");
            }
        }
        else
        {
            sendMessageNotification("Not supported!");
        }

    }

    //
    // Reset the symbology to default settings
    //
    public void restoreDefaults()
    {
        if( isImagerSupported() )
        {
            getCode128Settings().setFactoryDefault();
            sendMessageNotification("Code128: Defaults restored.");
        }
        else
        {
            sendMessageNotification("Not supported!");
        }
    }

}
