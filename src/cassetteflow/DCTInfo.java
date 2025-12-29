package cassetteflow;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * This object is used to store information of the current Dynamic Content Track
 * (DCT) objects to make loading the most recent DCT records from the command
 * line
 * possible. Primarily used for testing purposes
 * 
 * @author Nathan
 * @date 12/29/2025
 */
public class DCTInfo implements Serializable {
    // String arrays use by the dynamic content track
    private ArrayList<String> sideADCTList;
    private ArrayList<String> sideBDCTList;
    private String tapeID;
    private ArrayList<AudioInfo> sideA;
    private ArrayList<AudioInfo> sideB;

    /**
     * Default constructor used to serialize object
     */
    public DCTInfo() {
    }

    /**
     * Main constructor
     * 
     * @param sideADCTList
     * @param sideBDCTList
     * @param tapeID
     * @param sideA
     * @param sideB
     */
    public DCTInfo(ArrayList<String> sideADCTList, ArrayList<String> sideBDCTList, String tapeID,
            ArrayList<AudioInfo> sideA, ArrayList<AudioInfo> sideB) {
        this.sideADCTList = sideADCTList;
        this.sideBDCTList = sideBDCTList;
        this.tapeID = tapeID;
        this.sideA = sideA;
        this.sideB = sideB;
    }

    public ArrayList<String> getSideADCTList() {
        return sideADCTList;
    }

    public void setSideADCTList(ArrayList<String> sideADCTList) {
        this.sideADCTList = sideADCTList;
    }

    public ArrayList<String> getSideBDCTList() {
        return sideBDCTList;
    }

    public void setSideBDCTList(ArrayList<String> sideBDCTList) {
        this.sideBDCTList = sideBDCTList;
    }

    public String getTapeID() {
        return tapeID;
    }

    public void setTapeID(String tapeID) {
        this.tapeID = tapeID;
    }

    public ArrayList<AudioInfo> getSideA() {
        return sideA;
    }

    public void setSideA(ArrayList<AudioInfo> sideA) {
        this.sideA = sideA;
    }

    public ArrayList<AudioInfo> getSideB() {
        return sideB;
    }

    public void setSideB(ArrayList<AudioInfo> sideB) {
        this.sideB = sideB;
    }

    @Override
    public String toString() {
        return "DCTInfo{" + "tapeID=" + tapeID + "} Side A: " + sideA.size() + " Side B: " + sideB.size();
    }
}
