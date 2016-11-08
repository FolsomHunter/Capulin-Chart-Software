/******************************************************************************
* Title: EncoderValues.java
* Author: Mike Schoonover
* Date: 4/13/14
*
* Purpose:
*
* This class handles variables related to a position encoder.
*
* Open Source Policy:
*
* This source code is Public Domain and free to any interested party.  Any
* person, company, or organization may do with it as they please.
*
*/

package chart.mksystems.hardware;

//-----------------------------------------------------------------------------

import chart.mksystems.inifile.IniFile;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

//-----------------------------------------------------------------------------
// class Encoder Values
//
// Various encoder values returned by Control board as recorded at various
// stages in the inspection process.
//
// Value photoEyeToPhotoEyeDistance is the distance between the two spots
// on the tube of the vertical photo eyes.
//

public class EncoderValues extends Object{

    String inspectionDirection;
    
    public int encoderPosAtOnPipeSignal = 0;
    public int encoderPosAtOffPipeSignal = 0;
    public int encoderPosAtHead1DownSignal = 0;
    public int encoderPosAtHead1UpSignal = 0;
    public int encoderPosAtHead2DownSignal = 0;
    public int encoderPosAtHead2UpSignal = 0;
    public int encoderPosAtHead3DownSignal = 0;
    public int encoderPosAtHead3UpSignal = 0;

    public double photoEyeToPhotoEyeDistance = 0;

    private double encoder1CountsPerRev, encoder2CountsPerRev;
    
    private double encoder1CountsPerInch = 0;
    public void setEncoder1CountsPerInch(double pV){
        encoder1CountsPerInch = pV;
    }    
    public double getEncoder1CountsPerInch(){ return encoder1CountsPerInch;}
    
    private double encoder2CountsPerInch = 0;
    public void setEncoder2CountsPerInch(double pV){
        encoder2CountsPerInch = pV;
    }    
    public double getEncoder2CountsPerInch(){ return encoder2CountsPerInch;}
    
    private double encoder1InchesPerCount = 0;
    public void setEncoder1InchesPerCount(double pV){
        encoder1InchesPerCount = pV;
    }    
    public double getEncoder1InchesPerCount(){ return encoder1InchesPerCount;}
    
    private double encoder2InchesPerCount = 0;
    public void setEncoder2InchesPerCount(double pV){
        encoder2InchesPerCount = pV;
    }        
    public double getEncoder2InchesPerCount(){ return encoder2InchesPerCount;}    

    private double encoder1CountsPerSec = 0;
    public void setEncoder1CountsPerSec(double pV){
        encoder1CountsPerSec = pV;
    }    
    public double getEncoder1CountsPerSec(){ return encoder1CountsPerSec;}

    private double encoder1Helix = 0.0;
    public void setEncoder1Helix(double pV){ encoder1Helix = pV; }    
    public double getEncoder1Helix(){ return encoder1Helix;}
        
    private double encoder2CountsPerSec = 0;
    public void setEncoder2CountsPerSec(double pV){
        encoder2CountsPerSec = pV;
    }    
    public double getEncoder2CountsPerSec(){ return encoder2CountsPerSec;}

    private double encoder2Helix = 0.0;
    public void setEncoder2Helix(double pV){ encoder2Helix = pV; }    
    public double getEncoder2Helix(){ return encoder2Helix;}
    
    private String textMsg;
    public String getTextMsg(){ return textMsg; }
    public void setTextMsg(String pTextMsg){ textMsg = pTextMsg; }

    //length of the end stop at the "toward" end of the unit
    //if the away laser triggers on the start of this instead of the start of
    //the tube, specify the length in the config file so it can be accounted
    //for; if the away laser ignores the end stop and starts at the end of the
    //tube, set this value to 0.0 in the config file
        
    public double endStopLength = 0;
    
    public int numHeads = 3;
    public double photoEye1DistanceFrontOfHead[] = new double[numHeads];
    public double photoEye2DistanceFrontOfHead[] = new double[numHeads];    
        
    double photoEye1DistanceToEncoder1;
    double photoEye1DistanceToEncoder2;
    double distanceAfterEncoder2ToSwitchEncoders;
        
//-----------------------------------------------------------------------------
// EncoderValues::EncoderValues (constructor)
//

public EncoderValues()
{


}//end of EncoderValues::EncoderValues (constructor)
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::init
//
// Initializes the object.  MUST be called by sub classes after instantiation.
//

public void init()
{

    for(int i=0; i<numHeads; i++){
        
        photoEye1DistanceFrontOfHead[i] = 0.0;
        photoEye2DistanceFrontOfHead[i] = 0.0;
    
    }
    
}//end of EncoderValues::init
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::setAllToMaxValue
//
// Sets all member variables to max value.
//

public void setAllToMaxValue()
{

    encoderPosAtOnPipeSignal = Integer.MAX_VALUE;
    encoderPosAtOffPipeSignal = Integer.MAX_VALUE;
    encoderPosAtHead1DownSignal = Integer.MAX_VALUE;
    encoderPosAtHead1UpSignal = Integer.MAX_VALUE;
    encoderPosAtHead2DownSignal = Integer.MAX_VALUE;
    encoderPosAtHead2UpSignal = Integer.MAX_VALUE;
    encoderPosAtHead3DownSignal = Integer.MAX_VALUE;
    encoderPosAtHead3UpSignal = Integer.MAX_VALUE;
    
}//end of EncoderValues::setAllToMaxValue
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::convertBytesToInt
//
// Converts four bytes to an int value and returns the value.
//

public int convertBytesToInt(byte byte3, byte byte2, byte byte1, byte byte0)
{
    
    int value;
    
    value = ((byte3 << 24));
    value |= (byte2 << 16)& 0x00ff0000;
    value |= (byte1 << 8) & 0x0000ff00;
    value |= (byte0)      & 0x000000ff;

    return(value);

}//end of EncoderValues::convertBytesToInt
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::convertEncoder1CountsToFeet
//
// Converts pCounts number of encoder 1 counts to decimal feet and returns that
// value by scaling by the counts-per-inch value for that encoder.
//

public double convertEncoder1CountsToFeet(int pCounts)
{
    
    return(pCounts * encoder1InchesPerCount / 12);
    
}//end of EncoderValues::convertEncoder1CountsToFeet
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::convertEncoder1CountsToInches
//
// Converts pCounts number of encoder 1 counts to decimal inches and returns
// that value by scaling by the counts-per-inch value for that encoder.
//

public double convertEncoder1CountsToInches(int pCounts)
{
    
    return(pCounts * encoder1InchesPerCount);
    
}//end of EncoderValues::convertEncoder1CountsToInches
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::convertEncoder2CountsToFeet
//
// Converts pCounts number of encoder 2 counts to decimal feet and returns that
// value by scaling by the counts-per-inch value for that encoder.
//

public double convertEncoder2CountsToFeet(int pCounts)
{
    
    return(pCounts * encoder2InchesPerCount / 12);
    
}//end of EncoderValues::convertEncoder2CountsToFeet
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::convertEncoder2CountsToInches
//
// Converts pCounts number of encoder 2 counts to decimal inches and returns
// that value by scaling by the counts-per-inch value for that encoder.
//

public double convertEncoder2CountsToInches(int pCounts)
{
    
    return(pCounts * encoder2InchesPerCount);
    
}//end of EncoderValues::convertEncoder2CountsToInches
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::setEncoderCalValues
//
// Sets the various values which are related to calibration.
//

public void setEncoderCalValues(EncoderCalValues pEncoderCalValues)
    
{

    encoder1CountsPerInch = pEncoderCalValues.encoder1CountsPerInch;    
    encoder1InchesPerCount = pEncoderCalValues.encoder1InchesPerCount;
    encoder1CountsPerRev = pEncoderCalValues.encoder1CountsPerRev;    
    encoder1CountsPerSec = pEncoderCalValues.encoder1CountsPerSec;
    encoder1Helix = pEncoderCalValues.encoder1Helix;
    
    encoder2CountsPerInch = pEncoderCalValues.encoder2CountsPerInch;    
    encoder2InchesPerCount = pEncoderCalValues.encoder2InchesPerCount;
    encoder2CountsPerRev = pEncoderCalValues.encoder2CountsPerRev;        
    encoder2CountsPerSec = pEncoderCalValues.encoder2CountsPerSec;
    encoder2Helix = pEncoderCalValues.encoder2Helix;
    
    setTextMsg(pEncoderCalValues.getTextMsg());    
    
}//end of EncoderValues::setEncoderCalValues
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::getEncoderCalValues
//
// Returns all encoder calibrations via pEncoderCalValues. The function itself
// returns a reference to pEncoderValues.
//

public EncoderCalValues getEncoderCalValues(EncoderCalValues pEncoderCalValues)
{
 
    pEncoderCalValues.encoder1CountsPerInch = encoder1CountsPerInch;
    pEncoderCalValues.encoder1InchesPerCount = encoder1InchesPerCount;
    pEncoderCalValues.encoder1CountsPerRev = encoder1CountsPerRev;
    pEncoderCalValues.encoder1CountsPerSec = encoder1CountsPerSec;
    pEncoderCalValues.encoder1Helix = encoder1Helix;

    pEncoderCalValues.encoder2CountsPerInch = encoder2CountsPerInch;
    pEncoderCalValues.encoder2InchesPerCount = encoder2InchesPerCount;
    pEncoderCalValues.encoder2CountsPerRev = encoder2CountsPerRev;
    pEncoderCalValues.encoder2CountsPerSec = encoder2CountsPerSec;
    pEncoderCalValues.encoder2Helix = encoder2Helix;

    pEncoderCalValues.setTextMsg(textMsg);
    
    return(pEncoderCalValues);
    
}//end of EncoderValues::getEncoderCalValues
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::writeEncoderValuesToFile
//
// Writes all encoder values to a text file with name pFilename as the filename
// prefix.
//

public void writeEncoderValuesToFile(String pFilename, 
                                                String pInspectionDirection)
{

    inspectionDirection = pInspectionDirection;
    
    pFilename = pFilename + " ~ Wall Mapping Data ~ Encoder Values.dat";

    FileOutputStream fileOutputStream = null;
    OutputStreamWriter outputStreamWriter = null;
    BufferedWriter lOutFile = null;

    try{

        fileOutputStream = new FileOutputStream(pFilename);
        outputStreamWriter = new OutputStreamWriter(fileOutputStream);
        lOutFile = new BufferedWriter(outputStreamWriter);


        lOutFile.write("[Encoder Values]"); lOutFile.newLine();

        writeEncoderValuesToOpenFile(lOutFile);
        
        lOutFile.write("[End of Set]"); lOutFile.newLine();

    }
    catch(IOException e){ logSevere(e.getMessage() + " - Error: 218"); }
    finally{
        try{if (lOutFile != null) {lOutFile.close();}}
        catch(IOException e){ }
        try{if (outputStreamWriter != null) {outputStreamWriter.close();}}
        catch(IOException e){ }
        try{if (fileOutputStream != null) {fileOutputStream.close();}}
        catch(IOException e){ }
    }

}//end of EncoderValues::writeEncoderValuesToFile
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::writeEncoderValuesToOpenFile
//
// Writes all encoder values to already opened file pOutFile.
//

private void writeEncoderValuesToOpenFile(BufferedWriter pOutFile)
                                                        throws IOException
{
    
    pOutFile.write("Inspection Direction=" + inspectionDirection);
    pOutFile.newLine();
    
    pOutFile.write("Encoder Position at On Pipe Signal=" 
                                                + encoderPosAtOnPipeSignal);
    pOutFile.newLine();
    
    pOutFile.write("Encoder Position at Off Pipe Signal=" 
                                                + encoderPosAtOffPipeSignal);
    pOutFile.newLine();
    
    pOutFile.write("Encoder Position at Head 1 Down Signal=" 
                                                + encoderPosAtHead1DownSignal);
    pOutFile.newLine();
    
    pOutFile.write("Encoder Position at Head 1 Up Signal=" 
                                                + encoderPosAtHead1UpSignal);
    pOutFile.newLine();
    
    pOutFile.write("Encoder Position at Head 2 Down Signal=" 
                                                + encoderPosAtHead2DownSignal);
    pOutFile.newLine();
    
    pOutFile.write("Encoder Position at Head 2 Up Signal=" 
                                                + encoderPosAtHead2UpSignal);
    pOutFile.newLine();

    pOutFile.write("Encoder Position at Head 3 Down Signal=" 
                                                + encoderPosAtHead3DownSignal);
    pOutFile.newLine();
    
    pOutFile.write("Encoder Position at Head 3 Up Signal=" 
                                                + encoderPosAtHead3UpSignal);
    pOutFile.newLine();
        
    pOutFile.write("End Stop Length=" + endStopLength);
    pOutFile.newLine();
    
    pOutFile.write("Vertical Photo Eye to Vertical Photo Eye Distance=" 
                                                + photoEyeToPhotoEyeDistance);
    pOutFile.newLine();
    
    pOutFile.write("Encoder 1 Inches per Count=" + encoder1InchesPerCount);
    pOutFile.newLine();
    
    pOutFile.write("Encoder 2 Inches per Count=" + encoder2InchesPerCount);
    pOutFile.newLine();
    
}//end of EncoderValues::writeEncoderValuesToOpenFile
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::readEncoderValuesFromFile
//
// Reads all encoder values from a text file with name pFilename as the filename
// prefix.
//

public void readEncoderValuesFromFile(String pFilename)
{

    pFilename = pFilename + " ~ Wall Mapping Data ~ Encoder Values.dat";

    FileInputStream fileInputStream = null;
    InputStreamReader inputStreamReader = null;
    BufferedReader lInFile = null;

    try{

        fileInputStream = new FileInputStream(pFilename);
        inputStreamReader = new InputStreamReader(fileInputStream);
        lInFile = new BufferedReader(inputStreamReader);

        readEncoderValuesFromOpenFile(lInFile);
                
    }
    catch(IOException e){ logSevere(e.getMessage() + " - Error: 218"); }
    finally{
        try{if (lInFile != null) {lInFile.close();}}
        catch(IOException e){ }
        try{if (inputStreamReader != null) {inputStreamReader.close();}}
        catch(IOException e){ }
        try{if (fileInputStream != null) {fileInputStream.close();}}
        catch(IOException e){ }
    }

}//end of EncoderValues::readEncoderValuesFromFile
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::readNextValue
//
// Reads and returns the next value in pInFile from the next line containing
// a key/value pair (key=value). Any blank lines or lines starting with a
// '[', such as section tags, are skipped. Any lines which do not contain '='
// are also skipped.
//
// Returns null when end of file reached.
//

private String  readNextValue(BufferedReader pInFile) throws IOException
{        

    String line;
    
    while((line = pInFile.readLine()) != null){
    
        //skip all blank lines and section header lines
        if ("".equals(line.trim())) {continue;}
        if (line.trim().startsWith("[")) {continue;}
        
        //find '=' symbol
        int pos = line.lastIndexOf('=');
        if (pos == -1) {continue;} //skip line if '=' not present
        
        //return value after the '=' symbol
        line = line.substring(pos+1);
        if ("".equals(line.trim())) {continue;}
        
        return(line);
        
    }
    
    return(line);
    
}//end of EncoderValues::readNextValue
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::readEncoderValuesFromOpenFile
//
// Reads all encoder values from already opened file pInFile.
//

public void readEncoderValuesFromOpenFile(BufferedReader pInFile)
                                                        throws IOException
{

    String value;

    value = readNextValue(pInFile); if (value == null){return;}
    inspectionDirection = value;
        
    value = readNextValue(pInFile); if (value == null){return;}
    encoderPosAtOnPipeSignal = Integer.parseInt(value);

    value = readNextValue(pInFile); if (value == null){return;}
    encoderPosAtOffPipeSignal = Integer.parseInt(value);
    
    value = readNextValue(pInFile); if (value == null){return;}
    encoderPosAtHead1DownSignal = Integer.parseInt(value);

    value = readNextValue(pInFile); if (value == null){return;}
    encoderPosAtHead1UpSignal = Integer.parseInt(value);

    value = readNextValue(pInFile); if (value == null){return;}
    encoderPosAtHead2DownSignal = Integer.parseInt(value);

    value = readNextValue(pInFile); if (value == null){return;}
    encoderPosAtHead2UpSignal = Integer.parseInt(value);

    value = readNextValue(pInFile); if (value == null){return;}
    encoderPosAtHead3DownSignal = Integer.parseInt(value);

    value = readNextValue(pInFile); if (value == null){return;}
    encoderPosAtHead3UpSignal = Integer.parseInt(value);
        
    value = readNextValue(pInFile); if (value == null){return;}
    endStopLength = Double.parseDouble(value);
        
    value = readNextValue(pInFile); if (value == null){return;}
    photoEyeToPhotoEyeDistance = Double.parseDouble(value);

    value = readNextValue(pInFile); if (value == null){return;}
    encoder1InchesPerCount = Double.parseDouble(value);
    
    value = readNextValue(pInFile); if (value == null){return;}
    encoder2InchesPerCount = Double.parseDouble(value);            
            
}//end of EncoderValues::readEncoderValuesFromOpenFile
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::loadCalFile
//
// This loads the file used for storing calibration information pertinent to a
// job, such as gains, offsets, thresholds, etc.
//
// Each object is passed a pointer to the file so that they may load their
// own data.
//

public void loadCalFile(IniFile pCalFile)
{

    //if the value read from the cal file is the default, then don't overwrite
    //the value read from the config file -- the config file version will be
    //written to the cal file later and will be used from then on unless an
    //encoder calibration is performed to explicitly set new values -- new
    //jobs will end up with the config value being used until the first cal
    
    double encoderInchesPerCount;
    
    encoderInchesPerCount = pCalFile.readDouble("Hardware",
                            "Encoder 1 Inches per Count", Double.MIN_VALUE );
    
    if(encoderInchesPerCount != Double.MIN_VALUE){
        encoder1InchesPerCount = encoderInchesPerCount;
    }
    
    encoderInchesPerCount = pCalFile.readDouble("Hardware",
                            "Encoder 2 Inches per Count", Double.MIN_VALUE );

    if(encoderInchesPerCount != Double.MIN_VALUE){
        encoder2InchesPerCount = encoderInchesPerCount;
    }

    //wip mks -- need have a flag in the config file to always use
    // encoder values from the config and not allow cal file to override?
    // or store them in a special config file so the unit can be calibrated
    // only rarely?
    
    encoder1CountsPerInch = pCalFile.readDouble("Hardware",
                                            "Encoder 1 Counts per Inch", -1.0);

    encoder2CountsPerInch = pCalFile.readDouble("Hardware",
                                            "Encoder 2 Counts per Inch", -1.0);

    encoder1CountsPerRev = pCalFile.readDouble("Hardware",
                                        "Encoder 1 Counts per Revolution", -1);

    encoder2CountsPerRev = pCalFile.readDouble("Hardware",
                                        "Encoder 2 Counts per Revolution", -1);
        
    encoder1CountsPerSec = pCalFile.readDouble("Hardware",
                                            "Encoder 1 Counts per Second", -1);

    encoder2CountsPerSec = pCalFile.readDouble("Hardware",
                                            "Encoder 2 Counts per Second", -1);

    encoder1Helix = pCalFile.readDouble("Hardware",
                                  "Encoder 1 Helix Inches per Revolution", -1);
    
    encoder2Helix = pCalFile.readDouble("Hardware",
                                  "Encoder 2 Helix Inches per Revolution", -1);
        
    
}//end of EncoderValues::loadCalFile
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::saveCalFile
//
// This saves the file used for storing calibration information pertinent to a
// job, such as gains, offsets, thresholds, etc.
//
// Each object is passed a pointer to the file so that they may save their
// own data.
//

public void saveCalFile(IniFile pCalFile)
{

    pCalFile.writeDouble("Hardware", "Encoder 1 Inches per Count",
                                                    encoder1InchesPerCount);
    
    pCalFile.writeDouble("Hardware", "Encoder 2 Inches per Count",
                                                    encoder2InchesPerCount);

    pCalFile.writeDouble("Hardware", "Encoder 1 Counts per Inch", 
                                                        encoder1CountsPerInch);

     pCalFile.writeDouble("Hardware", "Encoder 2 Counts per Inch", 
                                                        encoder2CountsPerInch);

    pCalFile.writeDouble("Hardware",
                      "Encoder 1 Counts per Revolution", encoder1CountsPerRev);

    pCalFile.writeDouble("Hardware",
                      "Encoder 2 Counts per Revolution", encoder2CountsPerRev);
          
    pCalFile.writeDouble("Hardware", "Encoder 1 Counts per Second",
                                                        encoder1CountsPerSec);
    
    pCalFile.writeDouble("Hardware", "Encoder 2 Counts per Second",
                                                        encoder2CountsPerSec);

    pCalFile.writeDouble("Hardware",
                       "Encoder 1 Helix Inches per Revolution", encoder1Helix);

    pCalFile.writeDouble("Hardware",
                       "Encoder 2 Helix Inches per Revolution", encoder2Helix);

}//end of EncoderValues::saveCalFile
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::configure
//
// Loads configuration settings from the configuration.ini file.
//
// Only configuration data for this class are loaded here.  Each
// child object should be allowed to load its own data.
//

public void configure(IniFile pConfigFile)
{
    
    endStopLength = pConfigFile.readDouble("Hardware", "End Stop Length", 0.0);
        
    photoEye1DistanceFrontOfHead[0] = pConfigFile.readDouble("Hardware",
                          "Photo Eye 1 Distance to Front Edge of Head 1", 8.0);

    photoEye1DistanceFrontOfHead[1] = pConfigFile.readDouble("Hardware",
                         "Photo Eye 1 Distance to Front Edge of Head 2", 32.0);

    photoEye1DistanceFrontOfHead[2] = pConfigFile.readDouble("Hardware",
                         "Photo Eye 1 Distance to Front Edge of Head 3", 56.0);

    photoEye2DistanceFrontOfHead[0] = pConfigFile.readDouble("Hardware",
                         "Photo Eye 2 Distance to Front Edge of Head 1", 46.0);

    photoEye2DistanceFrontOfHead[1] = pConfigFile.readDouble("Hardware",
                         "Photo Eye 2 Distance to Front Edge of Head 2", 22.0);

    photoEye2DistanceFrontOfHead[2] = pConfigFile.readDouble("Hardware",
                         "Photo Eye 2 Distance to Front Edge of Head 3", 16.0);
        
    photoEye1DistanceToEncoder1 = pConfigFile.readDouble("Hardware",
                                "Photo Eye 1 To Encoder 1 Distance", 9.0);
     
    photoEye1DistanceToEncoder2 = pConfigFile.readDouble("Hardware",
                                "Photo Eye 1 To Encoder 2 Distance", 42.0);

    distanceAfterEncoder2ToSwitchEncoders = pConfigFile.readDouble(
       "Hardware", "Distance after Encoder 2 to Switch Between Encoders", 12.0);
    
    photoEyeToPhotoEyeDistance = pConfigFile.readDouble(
            "Hardware", "Distance Between Perpendicular Photo Eyes", 53.4375);

    //the control board sends packets every so many counts and is susceptible to
    //cumulative round off error, but the values below can be tweaked to give
    //accurate results over the length of the piece -- the packet send trigger
    //counts are often the same as the values below

    encoder1InchesPerCount =
        pConfigFile.readDouble("Hardware", "Encoder 1 Inches Per Count", 0.003);

    encoder2InchesPerCount =
        pConfigFile.readDouble("Hardware", "Encoder 2 Inches Per Count", 0.003);
            
}//end of EncoderValues::configure
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// EncoderValues::logSevere
//
// Logs pMessage with level SEVERE using the Java logger.
//

void logSevere(String pMessage)
{

    Logger.getLogger(getClass().getName()).log(Level.SEVERE, pMessage);

}//end of EncoderValues::logSevere
//-----------------------------------------------------------------------------

}//end of class EncoderValues
//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------
