/******************************************************************************
* Title: Map2DData.java
* Author: Mike Schoonover
* Date: 8/15/13
*
* Purpose:
*
* This class handles data for a 2D map. It has synchronized functions (or other
* protection mechanisms) to allow data to be inserted by one thread and read
* for display by a different thread.
*
* Only two threads should access this class -- a producer thread which adds
* or removes data and a consumer thread which reads data.
*
* All methods which alter the flagBuffer should be synchronized. Those that
* only read it do not have to be synchronized.
*
* Open Source Policy:
*
* This source code is Public Domain and free to any interested party.  Any
* person, company, or organization may do with it as they please.
*
*/

//-----------------------------------------------------------------------------


package chart.mksystems.stripchart;

//-----------------------------------------------------------------------------

import chart.Viewer;
import chart.Xfer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

//-----------------------------------------------------------------------------
// class Map2DData
//
// This class creates and controls a data set for a 2D map.
//

public class Map2DData extends PlotterData{

    int mapDataBuffer[][];

//-----------------------------------------------------------------------------
// Map2DData::Map2DData (constructor)
//
//

public Map2DData(final int pBufferSize, int pWidthOfDataBuffer,
                                    final int pPlotStyle, int pPeakDirection)
{

    sizeOfDataBuffer = pBufferSize; widthOfDataBuffer = pWidthOfDataBuffer;
    plotStyle = pPlotStyle; peakDirection = pPeakDirection;

}//end of Map2DData::Map2DData (constructor)
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::init
//
// Initializes the object.  MUST be called by sub classes after instantiation.
//

@Override
public void init()
{

    super.init();

    mapDataBuffer = new int [sizeOfDataBuffer][widthOfDataBuffer];

}//end of Map2DData::init
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::resetAll
//
// Resets everything back to default values.
//
// Insertion and extraction related pointers all set to 0. Plotting will
// begin after insertionPoint moves twice, which will allow extractionPoint to
// move once -- it only moves and returns data if the next position shows
// DATA_VALID flag. This sequence allows good data to placed at index 0, so
// the first time data is extracted it will have valid data in index 0 and
// index 1.
//

@Override
synchronized public void resetAll()
{

    super.resetAll();

    if (mapDataBuffer != null) {
        for (int i = 0; i < sizeOfDataBuffer; i++){

            flagBuffer[i] = CLEAR_ALL_FLAGS;

            for (int j = 0; j < widthOfDataBuffer; j++){

                mapDataBuffer[i][j] = DEFAULT_DATA;

            }
        }
    }

}//end of Map2DData::resetAll
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::storeDataAtInsertionPoint
//
// Stores data in pData array in mapDataBuffer column at the current insertion
// point.
//
// If no data has already been stored at that point, pData is stored without
// comparison.
//
// If data has already been stored at that point, the old data is only
// overwritten if pData is greater than or less than the old data, depending
// on the value of peakDirection. Thus the dataset can collect the max or min
// peak of the incoming data depending on whether peakDirection is MAX or MIN.
//
// Returns true if the data point was stored, false if it was not stored due
// to not being greater or less than the existing data point.
//

public boolean storeDataAtInsertionPoint(int pData[])
{

    boolean dataStored = false;

    //if no data has yet been stored, force store the first value; afterwards
    //value will be overwritten if new data is a peak

    if ((flagBuffer[insertionPoint] & IN_PROCESS) == 0){
        setFlags(insertionPoint, IN_PROCESS);
        System.arraycopy(
              pData, 0, mapDataBuffer[insertionPoint], 0, widthOfDataBuffer);
        dataStored = true;
    }
    else{
        //only store if new data is greater than old data
        if (peakDirection == MAX){
            for(int i = 0; i < widthOfDataBuffer; i++){
                if (pData[i] > mapDataBuffer[insertionPoint][i]){
                    mapDataBuffer[insertionPoint][i] = pData[i];
                    dataStored = true;
                }
            }
        }
        else{
            //only store if new data is less than old data
            for(int i = 0; i < widthOfDataBuffer; i++){
                if (pData[i] < mapDataBuffer[insertionPoint][i]){
                    mapDataBuffer[insertionPoint][i] = pData[i];
                    dataStored = true;
                }
            }
        }
    }

    return(dataStored);

}//end of Map2DData::storeDataAtInsertionPoint
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::setFlags
//
// The value in flagBuffer at pPosition is ORed with pMask to set one or more
// flags.
//

@Override
synchronized void setFlags(int pPosition, int pMask)
{

    flagBuffer[pPosition] |= pMask;

}//end of TraceData::setFlags
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::clearFlags
//
// The value in flagBuffer at pPosition is ANDed with pMask to clear one or more
// flags.
//

@Override
synchronized void clearFlags(int pPosition, int pMask)
{

    flagBuffer[pPosition] &= pMask;

}//end of Map2DData::clearFlags
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::getNewData
//
// If the DATA_ERASED flag for the current extractionPoint data position is
// set, the extractionPoint is decremented and the column of data at that
// position is returned as newDataColumn while the data at the previous position
// is returned as prevDataClumn in pDatum.
//
//
// NOTE: If the producer thread has added new data before the consumer thread
// can respond to any data erasures, the newData* and prevData* may
// not reflect the expected values.
//
// If the DATA_VALID flag is set on the next buffer position, the
// extractionPoint is incremented and the data at that position is returned
// as newData* while the data at the previous position is returned as prevData*
// in pDatum.
//
// The check for DATA_ERASED always overrides the check for DATA_VALID. If data
// is erased and then new data inserted before the consumer thread can react,
// the consumer thread will get all the erasure notices before forward reading
// of new data is resumed. This ensures that plotting can be reversed as far
// as the data was erased.
//
// Returns:
//
//  NO_NEW_DATA -- no data has been added or erased since the last call
//  FORWARD -- new data has been added
//  REVERSE -- data at current position has been erased
//
// The new column of data points, its clock position, and other information
// such as flags are returned in pDatum along with the column of data points for
// the previous position.
//

synchronized public int getNewData(Map2DDatum pDatum)
{

    if ((flagBuffer[extractionPoint] & DATA_ERASED) != 0){

        clearFlags(extractionPoint, CLEAR_DATA_ERASED);

        pDatum.prevDataColumn = mapDataBuffer[extractionPoint];

        retractExtractionPoint();

        segmentLength--;

        pDatum.newDataColumn = mapDataBuffer[extractionPoint];

        pDatum.flags = flagBuffer[extractionPoint];

        return(REVERSE);

    }

    if ((flagBuffer[lookAhead] & DATA_VALID) != 0){

        pDatum.prevDataColumn = mapDataBuffer[extractionPoint];

        advanceExtractionPoint();

        segmentLength++;

        pDatum.newDataColumn = mapDataBuffer[extractionPoint];

        pDatum.flags = flagBuffer[extractionPoint];

        return(FORWARD);

    }

    return(NO_NEW_DATA);

}//end of Map2DData::getNewData
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::advanceInsertionPoint
//
// Moves the insertion point forward one buffer position and makes the
// necessary preparations to the previous and new locations.
//
// The data from the previous point is copied to the new location so that if
// the position is advanced multiple times before data is stored to the buffer,
// the skipped locations will have valid data. Since the IN_PROCESS flag is
// cleared, this copied value will be overwritten by the first new data added
// without checking to see if the new data is a peak
//
// The DATA_VALID flag is set for the previous data location to indicate that
// it may be used by the consumer thread -- even if it is nothing more than a
// copy of the previous data. Note that the IN_PROCESS flag is left set.
//
// This method should only be called by the producer thread.
//

    @Override
    synchronized public void advanceInsertionPoint()
{

    super.advanceInsertionPoint();

   //copy previous data column to new buffer location -- see notes above

    System.arraycopy(mapDataBuffer[prevInsertionPoint], 0,
                        mapDataBuffer[insertionPoint], 0, widthOfDataBuffer);

}//end of Map2DData::advanceInsertionPoint
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::get
//
// Returns a reference to mapDataBuffer.
//

public int[][] getMapDataBuffer()
{

    return(mapDataBuffer);

}//end of Map2DData::getMapDataBuffer1
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::saveSegment
//
// Saves the data for a segment to the open file pOut.
//
// This function should be called whenever a new segment is completed - each
// segment could represent a piece being monitored, a time period, etc.
//
// This function should be called after the segment end has been marked and
// before the next segment start has been marked so that the end points
// of the data to be saved are known.
//

public void saveSegment(BufferedWriter pOut) throws IOException
{

/*

    //catch unexpected case where start/stop are invalid and bail
    if (lastSegmentStartIndex < 0 || lastSegmentEndIndex < 0){
        pOut.write("Segment start and/or start invalid - no data saved.");
        pOut.newLine(); pOut.newLine();
        return;
    }

    //save all the data and flags in the segment

    int i = lastSegmentStartIndex;

    pOut.write("[Data Set 1]"); pOut.newLine(); //save the first data set

    while (i != lastSegmentEndIndex){
        pOut.write(Integer.toString(dataBuffer1[i])); //save the data set 1
        pOut.newLine();
        //increment to next buffer slot, wrap around because buffer is circular
        if (++i == sizeOfDataBuffer) {i = 0;}
    }

    pOut.write("[End of Set]"); pOut.newLine();

    //save the second data set if it exists
    //the second data set is only used for certain styles of plotting
    if (dataBuffer2 != null){

        i = lastSegmentStartIndex;

        pOut.write("[Data Set 2]"); pOut.newLine();

        //save the second data set if it exists
        while (i != lastSegmentEndIndex){
            pOut.write(Integer.toString(dataBuffer2[i])); //save the data set 2
            pOut.newLine();
            //increment to next buffer slot, wrap around as buffer is circular
            if (++i == sizeOfDataBuffer) {i = 0;}
        }

        pOut.write("[End of Set]"); pOut.newLine();

    }//if (dataBuffer2 != null)

    i = lastSegmentStartIndex;

    pOut.write("[Flags]"); pOut.newLine(); //save the flags

    while (i != lastSegmentEndIndex){
        pOut.write(Integer.toString(flagBuffer[i])); //save the flags
        pOut.newLine();
        //increment to next buffer slot, wrap around because buffer is circular
        if (++i == sizeOfDataBuffer) {i = 0;}
    }

    pOut.write("[End of Set]"); pOut.newLine();

    pOut.newLine(); //blank line

*
*/


}//end of Map2DData::saveSegment
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::loadSegment
//
// Loads the data points and flags for a segment from pIn.  It is expected that
// the Trace tag and meta data has already been read.
//
// Returns the last line read from the file so that it can be passed to the
// next process.
//

public String loadSegment(BufferedReader pIn, String pLastLine)
                                                            throws IOException
{

 /*

    //read in "Data Set 1"
    String line =
            processDataSeries(pIn, pLastLine, "[Data Set 1]", dataBuffer1);

    //if "Data Set 2" is in use, read it in
    if (dataBuffer2 != null) {
        line = processDataSeries(pIn, line, "[Data Set 2]", dataBuffer2);
    }

    //read in "Flags"
    line = processDataSeries(pIn, line, "[Flags]", flagBuffer);


    return(line);

   *
*/


    return("debug mks -- remove this");

}//end of Map2DData::loadSegment
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::processDataSeries
//
// Processes a data series from pIn.  The series could be "Data Set 1",
// "Data Set 2", or "Flags" depending on the parameters passed in.
//
// The pStartTag string specifies the section start tag for the type of data
// expected and could be: "[Data Set 1]", "[Data Set 2]", or "[Flags]".  The
// pBuffer pointer should be set to the buffer associated with the data type.
//
// Returns the last line read from the file so that it can be passed to the
// next process.
//
// For these sections, the [xxx] section start tag may or may not have already
// been read from the file by the code handling the previous section.  If it has
// been read, the line containing the tag should be passed in via pLastLine.
//

private String processDataSeries(BufferedReader pIn, String pLastLine,
                            String pStartTag, int[] pBuffer) throws IOException
{

    String line;
    boolean success = false;
    Xfer matchSet = new Xfer(); //for receiving data from function calls

    //if pLastLine contains the [xxx] tag, then skip ahead else read until
    // end of file reached or "[xxx]" section tag reached

    if (Viewer.matchAndParseString(pLastLine, pStartTag, "",  matchSet)) {
        success = true;  //tag already found
    }
    else {
        while ((line = pIn.readLine()) != null){  //search for tag
            if (Viewer.matchAndParseString(line, pStartTag, "",  matchSet)){
                success = true; break;
            }
        }//while
    }//else

    if (!success) {
        throw new IOException(
           "The file could not be read - section not found for " + pStartTag);
    }

    //scan the first part of the section and parse its entries
    //these entries apply to the chart group itself

    int i = 0;
    success = false;
    while ((line = pIn.readLine()) != null){

        //stop when next section end tag reached (will start with [)
        if (Viewer.matchAndParseString(line, "[", "",  matchSet)){
            success = true; break;
        }

        try{

            //convert the text to an integer and save in the buffer
            int data = Integer.parseInt(line);
            pBuffer[i++] = data;

            //catch buffer overflow
            if (i == pBuffer.length) {
                throw new IOException(
                 "The file could not be read - too much data for " + pStartTag
                                                       + " at data point " + i);
            }

        }
        catch(NumberFormatException e){
            //catch error translating the text to an integer
            throw new IOException(
             "The file could not be read - corrupt data for " + pStartTag
                                                       + " at data point " + i);
        }

    }//while ((line = pIn.readLine()) != null)

    if (!success) {
        throw new IOException(
         "The file could not be read - missing end of section for "
                                                                + pStartTag);
    }

    return(line); //should be "[xxxx]" tag on success, unknown value if not

}//end of Map2DData::processDataSeries
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::prepareForRepaint
//
// Sets up variable(s) in preparation to extract data to repaint the trace.
//
// The repaintPoint is set to pStart.
//

public void prepareForRepaint(int pStart){

    repaintPoint = pStart;

}//end of Map2DData::prepareForRepaint
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::getDataAtRepaintPoint
//
// Returns the data and flags at the buffer location of repaintPoint. Advances
// repaintPoint to the next position.
//

public int getDataAtRepaintPoint(TraceDatum pDatum){

/*
    pDatum.prevData1 = dataBuffer1[repaintPoint];

    if (dataBuffer2 != null){
        pDatum.prevData2 = dataBuffer2[repaintPoint];
    }

    advanceRepaintPoint();

    pDatum.newData1 = dataBuffer1[repaintPoint];

    if (dataBuffer2 != null){
        pDatum.newData2 = dataBuffer2[repaintPoint];
    }

    pDatum.flags = flagBuffer[repaintPoint];

*
*/


    return(FORWARD);

}//end of Map2DData::getDataAtRepaintPoint
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Map2DData::advanceRepaintPoint
//
// Moves the repaintPoint pointer forward one buffer position.
//

private void advanceRepaintPoint()
{

    //the buffer is circular - start over at beginning
    repaintPoint++;
    if (repaintPoint == sizeOfDataBuffer) {repaintPoint = 0;}

}//end of Map2DData::advanceRepaintPoint
//-----------------------------------------------------------------------------


}//end of class Map2DData
//-----------------------------------------------------------------------------
//-----------------------------------------------------------------------------