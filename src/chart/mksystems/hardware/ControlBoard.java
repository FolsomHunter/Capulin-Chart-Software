/******************************************************************************
* Title: ControlBoard.java
* Author: Mike Schoonover
* Date: 5/24/09
*
* Purpose:
*
* This class interfaces with a Capulin Control board.
*
* Open Source Policy:
*
* This source code is Public Domain and free to any interested party.  Any
* person, company, or organization may do with it as they please.
*
*/

//-----------------------------------------------------------------------------

package chart.mksystems.hardware;

import java.io.*;
import java.net.*;
import javax.swing.*;

import chart.MessageLink;
import chart.mksystems.inifile.IniFile;

//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
// class ControlBoard
//
// This class creates and handles an interface to a Control board.
//

public class ControlBoard extends Board implements MessageLink{

byte[] monitorBuffer;
static int MONITOR_PACKET_SIZE = 20;

int packetRequestTimer = 0;

int runtimePacketSize;

MessageLink mechSimulator = null;

int pktID;
boolean encoderDataPacketProcessed = false;
boolean reSynced;
int reSyncCount = 0, reSyncPktID;
int timeOutProcess = 0; //use this one in the packet process functions
int timeOutWFP = 0; //used by processDataPackets

boolean newInspectPacketReady = false;

int encoder1, prevEncoder1;
int encoder2, prevEncoder2;
int encoder1Dir, encoder2Dir;

int inspectPacketCount = 0;

boolean onPipeFlag = false;
boolean inspectFlag = false;
boolean tdcFlag = false;
boolean unused1Flag = false;
boolean unused2Flag = false;
boolean unused3Flag = false;
byte controlPortA, controlPortE;

//number of counts each encoder moves to trigger an inspection data packet
//these values are read later from the config file
int encoder1DeltaTrigger, encoder2DeltaTrigger;

//Commands for Control boards
//These should match the values in the code for those boards.

static byte NO_ACTION = 0;
static byte GET_INSPECT_PACKET_CMD = 1;
static byte ZERO_ENCODERS_CMD = 2;
static byte GET_MONITOR_PACKET_CMD = 3;
static byte PULSE_OUTPUT_CMD = 4;
static byte TURN_ON_OUTPUT_CMD = 5;
static byte TURN_OFF_OUTPUT_CMD = 6;
static byte SET_ENCODERS_DELTA_TRIGGER_CMD = 7;
static byte START_INSPECT_CMD = 8;
static byte STOP_INSPECT_CMD = 9;
static byte START_MONITOR_CMD = 10;
static byte STOP_MONITOR_CMD = 11;
static byte GET_STATUS_CMD = 12;
static byte LOAD_FIRMWARE_CMD = 13;
static byte SEND_DATA_CMD = 14;
static byte DATA_CMD = 15;

static byte ERROR = 125;
static byte DEBUG_CMD = 126;
static byte EXIT_CMD = 127;

//Status Codes for Control boards
//These should match the values in the code for those boards.

static byte NO_STATUS = 0;

static int RUNTIME_PACKET_SIZE = 2048;

//Masks for the Control Board inputs

static byte UNUSED1_MASK = (byte)0x10;	// bit on Port A
static byte UNUSED2_MASK = (byte)0x20;	// bit on Port A
static byte INSPECT_MASK = (byte)0x40;	// bit on Port A
static byte ON_PIPE_MASK = (byte)0x80;	// bit on Port A
static byte TDC_MASK = (byte)0x01;    	// bit on Port E
static byte UNUSED3_MASK = (byte)0x20;	// bit on Port E

//-----------------------------------------------------------------------------
// UTBoard::UTBoard (constructor)
//
// The parameter configFile is used to load configuration data.  The IniFile
// should already be opened and ready to access.
//

public ControlBoard(IniFile pConfigFile, String pBoardName, int pBoardIndex,
                      int pRuntimePacketSize, boolean pSimulate, JTextArea pLog)
{

configFile = pConfigFile; 
boardName = pBoardName;
boardIndex = pBoardIndex;
runtimePacketSize = pRuntimePacketSize;
simulate = pSimulate;

log = pLog;
threadSafeMessage = new String[NUMBER_THREADSAFE_MESSAGES];

monitorBuffer = new byte[MONITOR_PACKET_SIZE];

//read the configuration file and create/setup the charting/control elements
configure(configFile);

}//end of UTBoard::UTBoard (constructor)
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::run
//
// This thread loads the board with FPGA and DSP code.  Using a thread allows
// multiple boards to be loaded simultaneously.
//

@Override
public void run() { 
         
//link with all the remotes
connect();

//Since the sockets and associated streams were created by this
//thread, it cannot be closed without disrupting the connections. If
//other threads try to read from the socket after the thread which
//created the socket finishes, an exception will be thrown.  This
//thread just waits() after performing the connect function.  The
//alternative is to close the socket and allow another thread to
//reopen it, but this results in a lot of overhead.

waitForever();

}//end of ControlBoard::run
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::waitForever
//
// Puts the thread in wait mode forever.
//

public synchronized void waitForever()
{

while (true){
    try{wait();}
    catch (InterruptedException e) { }
    }

}//end of ControlBoard::waitForever
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::connect
//
// Opens a TCP/IP connection with the Control Board.
//

public synchronized void connect()
{

//displays message on bottom panel of IDE
threadSafeLog("Opening connection with Control board...\n"); 
  
try {

    threadSafeLog("Control Board IP Address: " + ipAddr.toString() + "\n");

    if (!simulate) socket = new Socket(ipAddr, 23);
    else {
        
        socket = new ControlSimulator(ipAddr, 23, 
                                   encoder1DeltaTrigger, encoder2DeltaTrigger);
        //when simulating, the socket is a ControlSimulator class object which
        //is also a MessageLink implementor, so cast it for use as such so that
        //messages can be sent to the object
        mechSimulator = (MessageLink)socket;
        }

    //set amount of time in milliseconds that a read from the socket will
    //wait for data - this prevents program lock up when no data is ready
    socket.setSoTimeout(250);
    
    out = new PrintWriter(socket.getOutputStream(), true);

    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

    byteOut = new DataOutputStream(socket.getOutputStream());
    byteIn =  new DataInputStream(socket.getInputStream());

    }//try
    catch (IOException e) {
        threadSafeLog("Couldn't get I/O for " + ipAddrS + "\n");
        return;
        }

try {
    //display the greeting message sent by the remote
    threadSafeLog(ipAddrS + " says " + in.readLine() + "\n");
    }
catch(IOException e){}

//flag that board setup has been completed - whether it failed or not
setupComplete = true;

//flag that setup was successful and board is ready for use
ready = true;

//debug mks -- chassisSlotAddr is still null -- need to add code like utBoard
//which retrieves the slot address from the remote

threadSafeLog("Control " + chassisSlotAddr + " is ready." + "\n");

notifyAll(); //wake up all threads that are waiting for this to complete

}//end of ControlBoard::connect
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard:initialize
//
// Sets up various settings on the board.
//

public void initialize()
{

setEncodersDeltaTrigger();

}//end of ControlBoard::initialize
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::startMonitor
//
// Places the Control board in Monitor status and displays the status of
// various I/O as sent back from the Control board.
//

public void startMonitor()
{

sendBytes2(START_MONITOR_CMD, (byte) 0);

}//end of ControlBoard::startMonitor
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::stopMonitor
//
// Takes the Control board out of monitor mode.
//

public void stopMonitor()
{
    
sendBytes2(STOP_MONITOR_CMD, (byte) 0);

}//end of ControlBoard::stopMonitor
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::getMonitorPacket
//
// Returns in a byte array I/O status data which has already been received and
// stored from the remote.
// If pRequestPacket is true, then a packet is requested every so often.
// If false, then packets are only received when the remote computer sends
// them.
//
// NOTE: This function is often called from a different thread than the one
// transferring the data from the input buffer -- erroneous values for some of
// the multibyte values may occur due to thread collision but they are for
// display/debugging only and an occasional glitch in the displayed values
// should not be of major concern.
//

public byte[] getMonitorPacket(boolean pRequestPacket)
{

if (pRequestPacket)
        //request a packet be sent if the counter has timed out
        //this packet will arrive in the future and be processed by another
        //function so it can be retrieved by another call to this function
        if (packetRequestTimer++ == 50){
            packetRequestTimer = 0;
            sendBytes2(GET_MONITOR_PACKET_CMD, (byte) 0);
            }

return monitorBuffer;

}//end of ControlBoard::getMonitorPacket
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::processMonitorPacket
//
// Transfers I/O status received from the remote into an array.
//
// Returns number of bytes retrieved from the socket.
//

public int processMonitorPacket()
{

try{
    timeOutProcess = 0;
    while(timeOutProcess++ < TIMEOUT){
        if (byteIn.available() >= MONITOR_PACKET_SIZE) break;
        waitSleep(10);
        }
    if (byteIn.available() >= MONITOR_PACKET_SIZE)
        return   byteIn.read(monitorBuffer, 0, MONITOR_PACKET_SIZE);

    }// try
catch(IOException e){}

return 0;

}//end of ControlBoard::processMonitorPacket
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::requestInspectPacket
//
// Normally, the Control board sends Inspect packets as necessary.  This
// function is used to force the send of an Inspect packet so that all local
// flags and values will be updated.
//
// Returns number of bytes retrieved from the socket.
//

public void requestInspectPacket()
{

sendBytes2(GET_INSPECT_PACKET_CMD, (byte) 0);

}//end of ControlBoard::requestInspectPacket
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::processInspectPacket
//
// Processes and Inspect packet from the remote with flags and encoder values.
//
// Returns number of bytes retrieved from the socket.
//

public int processInspectPacket()
{

int x = 0, cnt = 0;
int pktSize = 12;

try{
    timeOutProcess = 0;
    while(timeOutProcess++ < TIMEOUT){
        if (byteIn.available() >= pktSize) break;
        waitSleep(10);
        }
    if (byteIn.available() >= pktSize)
        cnt = byteIn.read(inBuffer, 0, pktSize);

    inspectPacketCount = (int)((inBuffer[x++]<<8) & 0xff00)
                                               + (int)(inBuffer[x++] & 0xff);

    // combine four bytes each to make the encoder counts

    int encoder1Count = 0, encoder2Count = 0;

    // create integer from four bytes in buffer
    encoder1Count = ((inBuffer[x++] << 24));
    encoder1Count |= (inBuffer[x++] << 16) & 0x00ff0000;
    encoder1Count |= (inBuffer[x++] << 8)  & 0x0000ff00;
    encoder1Count |= (inBuffer[x++])       & 0x000000ff;

    // create integer from four bytes in buffer
    encoder2Count = ((inBuffer[x++] << 24));
    encoder2Count |= (inBuffer[x++] << 16) & 0x00ff0000;
    encoder2Count |= (inBuffer[x++] << 8)  & 0x0000ff00;
    encoder2Count |= (inBuffer[x++])       & 0x000000ff;

    //transfer to the class variables in one move -- this will be an atomic
    //copy so it is safe for other threads to access those variables
    encoder1 = encoder1Count; encoder2 = encoder2Count;

    //flag if encoder count was increased or decreased
    //a no change case should not occur since packets are sent when there has
    //been a change of encoder count

    if (encoder1 > prevEncoder1) encoder1Dir = InspectControlVars.INCREASING;
    else encoder1Dir = InspectControlVars.DECREASING;

    //flag if encoder count was increased or decreased
    if (encoder2 > prevEncoder2) encoder2Dir = InspectControlVars.INCREASING;
    else encoder2Dir = InspectControlVars.DECREASING;

    //update the previous encoder values for use next time
    prevEncoder1 = encoder1; prevEncoder2 = encoder2;

    //transfer the status of the Control board input ports
    controlPortA = inBuffer[x++];
    controlPortE = inBuffer[x++];

    if ((controlPortA & ON_PIPE_MASK) == 0)
        onPipeFlag = true; else onPipeFlag = false;

    if ((controlPortA & INSPECT_MASK) == 0)
        inspectFlag = true; else inspectFlag = false;

    if ((controlPortE & TDC_MASK) == 0)
        tdcFlag = true; else tdcFlag = false;

    if ((controlPortA & UNUSED1_MASK) == 0)
        unused1Flag = true; else unused1Flag = false;

    if ((controlPortA & UNUSED2_MASK) == 0)
        unused2Flag = true; else unused2Flag = false;

    if ((controlPortE & UNUSED3_MASK) == 0)
        unused3Flag = true; else unused3Flag = false;

    newInspectPacketReady = true; //signal other objects

    return(cnt);

    }// try
catch(IOException e){}

return(0);

}//end of ControlBoard::processInspectPacket
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::zeroEncoderCounts
//
// Sends command to zero the encoder counts.
//

public void zeroEncoderCounts()
{
    
sendBytes2(ZERO_ENCODERS_CMD, (byte) 0);

}//end of ControlBoard::zeroEncoderCounts
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::pulseOutput
//
// Pulses the specified output(s).
//
// WIP MKS - need to add parameter to pass in which output to be fired.
// Currently the Rabbit code simply fires output 1 with this call.
//

public void pulseOutput()
{

sendBytes2(PULSE_OUTPUT_CMD, (byte) 0);

}//end of ControlBoard::pulseOutput
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::turnOnOutput
//
// Turns on the specified output(s).
//
// WIP MKS - need to add parameter to pass in which output to be fired.
// Currently the Rabbit code simply fires output 1 with this call.
//

public void turnOnOutput()
{

sendBytes2(TURN_ON_OUTPUT_CMD, (byte) 0);

}//end of ControlBoard::turnOnOutput
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::turnOffOutput
//
// Turns off the specified output(s).
//
// WIP MKS - need to add parameter to pass in which output to be fired.
// Currently the Rabbit code simply fires output 1 with this call.
//

public void turnOffOutput()
{

sendBytes2(TURN_OFF_OUTPUT_CMD, (byte) 0);

}//end of ControlBoard::turnOffOutput
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::setEncodersDeltaTrigger
//
// Tells the Control board how many encoder counts to wait before sending
// an encoder value update.  The trigger value for each encoder is sent.
//
// Normally, this value will be set to something reasonable like 1 inch of
// travel of the piece being inspected.
//

public void setEncodersDeltaTrigger()
{

sendBytes5(SET_ENCODERS_DELTA_TRIGGER_CMD,
            (byte)((encoder1DeltaTrigger >> 8) & 0xff),
            (byte)(encoder1DeltaTrigger & 0xff),
            (byte)((encoder2DeltaTrigger >> 8) & 0xff),
            (byte)(encoder2DeltaTrigger & 0xff)
            );

}//end of ControlBoard::setEncodersDeltaTrigger
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::startInspect
//
// Puts Control board in the inspect mode.  In this mode the Control board
// will monitor encoder and status signals and return position information to
// the host.
//

public void startInspect()
{

sendBytes2(START_INSPECT_CMD, (byte) 0);

}//end of ControlBoard::startInspect
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::stopInspect
//
// Takes Control board out of the inspect mode.
//

public void stopInspect()
{

sendBytes2(STOP_INSPECT_CMD, (byte) 0);

}//end of ControlBoard::stopInspect
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::prepareData
//
// Retrieves a data packet from the incoming data buffer and distributes it
// to the newData variables in each gate.
//
// Returns true if new data is available, false if not.
//

public boolean prepareData()
{

if (byteIn != null)
    try {
 
        int c = byteIn.available();

        //if a full packet is not ready, return false
        if (c < runtimePacketSize) return false;
        
        byteIn.read(inBuffer, 0, runtimePacketSize);

        //wip mks - distribute the data to the gate's newData variables here
        
        }
    catch(EOFException eof){log.append("End of stream.\n"); return false;}
    catch(IOException e){return false;}

return true;

}//end of ControlBoard::prepareData
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::processDataPacketsUntilFirstEncoderPacket
//
// Processes incoming data packets until the first Encoder data packet has been
// processed.
//
// Returns 1 if an Encoder data packet has been processed, -1 if all available
// packets have been processed but no peak data packet was present.
//
// See processOneDataPacket notes for more info.
//

public int processDataPacketsUntilFirstEncoderPacket()
{

int x = 0;

//this flag will be set true if a Peak Data packet is processed
encoderDataPacketProcessed = false;

//process packets until there is no more data available or until a Peak Data
//packet has been processed

while ((x = processOneDataPacket(false, TIMEOUT)) > 0
                                      && encoderDataPacketProcessed == false){}


if (encoderDataPacketProcessed == true) return 1;
else return -1;

}//end of ControlBoard::processDataPacketsUntilFirstEncoderPacket
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::processOneDataPacket
//
// This function processes a single data packet if it is available.  If
// pWaitForPkt is true, the function will wait until data is available.
//
// The amount of time the function is to wait for a packet is specified by
// pTimeOut.  Each count of pTimeOut equals 10 ms.
//
// This function should be called often to allow processing of data packets
// received from the remotes and stored in the socket buffer.
//
// All packets received from the remote devices should begin with
// 0xaa, 0x55, 0xbb, 0x66, followed by the packet identifier, the DSP chip
// identifier, and the DSP core identifier.
//
// Returns number of bytes retrieved from the socket, not including the
// 4 header bytes, the packet ID, the DSP chip ID, and the DSP core ID.
// Thus, if a non-zero value is returned, a packet was processed.  If zero
// is returned, some bytes may have been read but a packet was not successfully
// processed due to missing bytes or header corruption.
// A return value of -1 means that the buffer does not contain a packet.
//

public int processOneDataPacket(boolean pWaitForPkt, int pTimeOut)
{

if (byteIn == null) return -1;  //do nothing if the port is closed

try{

    //wait a while for a packet if parameter is true
    if (pWaitForPkt){
        timeOutWFP = 0;
        while(byteIn.available() < 5 && timeOutWFP++ < pTimeOut){waitSleep(10);}
        }

    //wait until 5 bytes are available - this should be the 4 header bytes, and
    //the packet identifier
    if (byteIn.available() < 5) return -1;

    //read the bytes in one at a time so that if an invalid byte is encountered
    //it won't corrupt the next valid sequence in the case where it occurs
    //within 3 bytes of the invalid byte

    //check each byte to see if the first four create a valid header
    //if not, jump to resync which deletes bytes until a valid first header
    //byte is reached

    //if the reSynced flag is true, the buffer has been resynced and an 0xaa
    //byte has already been read from the buffer so it shouldn't be read again

    //after a resync, the function exits without processing any packets

    if (!reSynced){
        //look for the 0xaa byte unless buffer just resynced
        byteIn.read(inBuffer, 0, 1);
        if (inBuffer[0] != (byte)0xaa) {reSync(); return 0;}
        }
    else reSynced = false;

    byteIn.read(inBuffer, 0, 1);
    if (inBuffer[0] != (byte)0x55) {reSync(); return 0;}
    byteIn.read(inBuffer, 0, 1);
    if (inBuffer[0] != (byte)0xbb) {reSync(); return 0;}
    byteIn.read(inBuffer, 0, 1);
    if (inBuffer[0] != (byte)0x66) {reSync(); return 0;}

    //read in the packet identifier
    byteIn.read(inBuffer, 0, 1);

    //store the ID of the packet (the packet type)
    pktID = inBuffer[0];

    if ( pktID == GET_INSPECT_PACKET_CMD) return processInspectPacket();
    else
    if ( pktID == GET_MONITOR_PACKET_CMD) return processMonitorPacket();

    }
catch(IOException e){}

return 0;

}//end of ControlBoard::processOneDataPacket
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::reSync
//
// Clears bytes from the socket buffer until 0xaa byte reached which signals
// the *possible* start of a new valid packet header or until the buffer is
// empty.
//
// If an 0xaa byte is found, the flag reSynced is set true to that other
// functions will know that an 0xaa byte has already been removed from the
// stream, signalling the possible start of a new packet header.
//
// There is a special case where a 0xaa is found just before the valid 0xaa
// which starts a new packet - the first 0xaa is the last byte of the previous
// packet (usually the checksum).  In this case, the next packet will be lost
// as well.  This should happen rarely.
//

public void reSync()
{

reSynced = false;

//track the number of times this function is called, even if a resync is not
//successful - this will track the number of sync errors
reSyncCount++;

//store info pertaining to what preceded the reSync - these values will be
//overwritten by the next reSync, so they only reflect the last error
//NOTE: when a reSync occurs, these values are left over from the PREVIOUS good
// packet, so they indicate what PRECEDED the sync error.

reSyncPktID = pktID;

try{
    while (byteIn.available() > 0) {
        byteIn.read(inBuffer, 0, 1);
        if (inBuffer[0] == (byte)0xaa) {reSynced = true; break;}
        }
    }
catch(IOException e){}

}//end of ControlBoard::reSync
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::driveSimulation
//
// Drive any simulation functions if they are active.  This function is usually
// called from a thread.
//

public void driveSimulation()
{

if (simulate && socket != null)
    ((ControlSimulator)socket).processDataPackets(false);

}//end of ControlBoard::driveSimulation
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::getInspectControlVars
//
// Transfers local variables related to inspection control signals and encoder
// counts.
//

public void getInspectControlVars(InspectControlVars pICVars)
{

pICVars.onPipeFlag = onPipeFlag;

pICVars.inspectFlag = inspectFlag;

pICVars.encoder1 = encoder1; pICVars.prevEncoder1 = prevEncoder1;

pICVars.encoder2 = encoder2; pICVars.prevEncoder2 = prevEncoder2;

pICVars.encoder1Dir = encoder1Dir;
pICVars.encoder2Dir = encoder2Dir;

}//end of ControlBoard::getInspectControlVars
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::installNewRabbitFirmware
//
// Transmits the Rabbit firmware image to the Control board to replace the
// existing code.  
//
// See corresponding function in the parent class Board.
//

public void installNewRabbitFirmware()
{

//create an object to hold codes specific to the UT board for use by the
//firmware installer method
    
InstallFirmwareSettings settings = new InstallFirmwareSettings();
settings.loadFirmwareCmd = LOAD_FIRMWARE_CMD;
settings.noAction = NO_ACTION;
settings.error = ERROR;
settings.sendDataCmd = SEND_DATA_CMD;
settings.dataCmd = DATA_CMD;
settings.exitCmd = EXIT_CMD;
    
super.installNewRabbitFirmware("Control", "Rabbit\\CAPULIN CONTROL BOARD.bin",
                                                                    settings);
    
}//end of ControlBoard::installNewRabbitFirmware
//-----------------------------------------------------------------------------

//----------------------------------------------------------------------------
// ControlBoard::xmtMessage
//
// This method allows an outside class to send a message and a value to this
// class and receive a status value back.
//
// In this class, this is mainly used to pass messages to the ControlSimulator
// class so that it can be controlled via messages.
//

@Override
public int xmtMessage(int pMessage, int pValue)
{

if (mechSimulator == null) return MessageLink.NULL;    
    
//pass the message on to the mechanical simulation object
return mechSimulator.xmtMessage(pMessage, pValue);

}//end of ControlBoard::xmtMessage
//----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::various get/set functions
//

public boolean getOnPipeFlag(){return onPipeFlag;}

public boolean getInspectFlag(){return inspectFlag;}

public boolean getNewInspectPacketReady(){return newInspectPacketReady;}

public void setNewInspectPacketReady(boolean pValue)
    {newInspectPacketReady = pValue;}

//end of ControlBoard::various get/set functions
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// ControlBoard::configure
//
// Loads configuration settings from the configuration.ini file.
// The various child objects are then created as specified by the config data.
//

private void configure(IniFile pConfigFile)
{

inBuffer = new byte[RUNTIME_PACKET_SIZE];
outBuffer = new byte[RUNTIME_PACKET_SIZE];


encoder1DeltaTrigger = 
          pConfigFile.readInt("Hardware", "Encoder 1 Delta Count Trigger", 553);

encoder2DeltaTrigger = 
          pConfigFile.readInt("Hardware", "Encoder 2 Delta Count Trigger", 553);

}//end of ControlBoard::configure
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// UTBoard::shutDown
//
// This function should be called before exiting the program.  Overriding the
// "finalize" method does not work as it does not get called reliably upon
// program exit.
//

protected void shutDown()
{

//close everything - the order of closing may be important

try{

    if (byteOut != null) byteOut.close();
    if (byteIn != null) byteIn.close();
    if (out != null) out.close();
    if (in != null) in.close();
    if (socket != null) socket.close();
    
    }
catch(IOException e){}

}//end of ControlBoard::shutDown
//-----------------------------------------------------------------------------

}//end of class ControlBoard
//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------    
