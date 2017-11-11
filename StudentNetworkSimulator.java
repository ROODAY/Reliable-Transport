import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator {
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B 
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity): 
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment): 
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData): 
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    private double startTime;
    
    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    // A
    private Packet[] senderWindow;
    private int[] ackStatus;
    private LinkedList<Packet> senderBuffer = new LinkedList<Packet>();
    private int CurSeqNo = 0;
    private int sendHead = 0;

    // B
    private int lastRcvSeqNum = -1;
    private Packet[] receiverWindow;
    private Packet lastRcvPacket;

    // Statistics
    private int originalTransmissions = 0;
    private int retransmissions = 0;
    private int deliveredPackets = 0;
    private int ackedPackets = 0;
    private int corruptedPackets = 0;

    private Map<Integer,Double> rttMap = new HashMap<Integer,Double>();
    private double rttSum = 0.0;
    private int rttCount = 0;

    private Map<Integer,Double> commMap = new HashMap<Integer,Double>();
    private double commSum = 0.0;
    private int commCount = 0;

    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay)
    {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize*2; // set appropriately; assumes SR here!
        RxmtInterval = delay;
        startTime = getTime();
    }

    private int checksum(Packet packet) {
      int chk = packet.getSeqnum() + packet.getAcknum();
      String payload = packet.getPayload();
      for(int i = 0; i < payload.length(); i++) {
            chk += (int) payload.charAt(i);
      }
      return chk;
    }

    
    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
      System.out.println("A Output: Start");
      // Create and buffer packet
      Packet aPacket = new Packet(CurSeqNo, -1, 0, message.getData());
      aPacket.setChecksum(checksum(aPacket));
      senderBuffer.add(aPacket);
      CurSeqNo++;

      // Add from buffer into window where space allows
      for (int i = sendHead; i < sendHead + WindowSize && i < senderBuffer.size(); i++) {
        if (senderWindow[i % WindowSize] == null) {
          senderWindow[i % WindowSize] = senderBuffer.get(i);
        }
      }

      // For every packet in window, check if unsent. If so, send 
      for (int i = 0; i < ackStatus.length; i ++) {
        if (senderWindow[i] != null && ackStatus[i] == -1) {
          ackStatus[i] = 0;
          toLayer3(A, senderWindow[i]);
          rttMap.put(senderWindow[i].getSeqnum(), getTime());
          commMap.put(senderWindow[i].getSeqnum(), getTime());
          originalTransmissions++;
          stopTimer(A);
          startTimer(A, RxmtInterval);
        }
      }
      System.out.println("A Output: End");
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet) {
      System.out.println("A Input: Start");
      if (packet.getChecksum() == checksum(packet)) {

        // If new ack, stop timer and update ack status
        if (ackStatus[packet.getAcknum() % WindowSize] == 0) {
          stopTimer(A);
          ackStatus[packet.getAcknum() % WindowSize] = 1;

          // If ack is head or greater, update head
          if (packet.getAcknum() >= sendHead) {
            if (packet.getAcknum() > sendHead) {
              System.out.printf("A Input: Received Cumulative ACK | Expected: %d Received: %d\n", sendHead, packet.getAcknum());
            } else {
              System.out.println("A Input: Received ACK: " + packet.getAcknum());
            }
            int oldHead = sendHead;
            sendHead = packet.getAcknum() + 1;

            // Slide window up then transmit new data packets from buffer
            for (int i = oldHead; i < sendHead; i++) {
              double time = rttMap.get(senderWindow[i % WindowSize].getSeqnum());
              if (time != -1.0) {
                rttSum += getTime() - time;
                rttMap.put(senderWindow[i % WindowSize].getSeqnum(), -1.0);
                rttCount++;
              }

              commSum += getTime() - commMap.get(senderWindow[i % WindowSize].getSeqnum());
              commCount++;

              ackStatus[i % WindowSize] = -1;
              senderWindow[i % WindowSize] = null;
            }

            // Add from buffer into window where space allows
            for (int i = sendHead; i < sendHead + WindowSize && i < senderBuffer.size(); i++) {
              if (senderWindow[i % WindowSize] == null) {
                senderWindow[i % WindowSize] = senderBuffer.get(i);
              }
            }

            // For every packet in window, check if unsent. If so, send 
            for (int i = 0; i < ackStatus.length; i ++) {
              if (senderWindow[i] != null && ackStatus[i] == -1) {
                ackStatus[i] = 0;
                toLayer3(A, senderWindow[i]);
                rttMap.put(senderWindow[i].getSeqnum(), getTime());
                originalTransmissions++;
                stopTimer(A);
                startTimer(A, RxmtInterval);
              }
            }

          }
        // If duplicate ack, resend next packet
        } else if (ackStatus[packet.getAcknum() % WindowSize] == 1) {
          System.out.printf("A Input: Received Duplicate ACK %d | Retransmitting %d\n", packet.getAcknum(), sendHead);
          for (int i = sendHead; i < sendHead + WindowSize; i++) {
            if (ackStatus[i % WindowSize] == 0) {
              toLayer3(A, senderWindow[i % WindowSize]);
              rttMap.put(senderWindow[i % WindowSize].getSeqnum(), getTime());
              retransmissions++;
              stopTimer(A);
              startTimer(A, RxmtInterval);
            } 
          }
        }
      } else {
        System.out.println("A Input: Received Corrupted Packet");
        corruptedPackets++;
      }
      System.out.println("A Input: End");
    }
    
    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt() {
      System.out.println("A Timer Interrupt | Retransmitting " + sendHead);
      for (int i = sendHead; i < sendHead + WindowSize; i++) {
        if (ackStatus[i % WindowSize] == 0) {
          toLayer3(A, senderWindow[i % WindowSize]);
          rttMap.put(senderWindow[i % WindowSize].getSeqnum(), getTime());
          retransmissions++;
          stopTimer(A);
          startTimer(A, RxmtInterval);
        } 
      }
    }
    
    // This routine will be called once, before any of your other A-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit(){
      senderWindow = new Packet[WindowSize];
      ackStatus = new int[WindowSize];
      Arrays.fill(ackStatus, -1);
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet) {
      System.out.println("B Input: Start");
      if (packet.getChecksum() == checksum(packet)) {
        // Set ack num and recompute checksum
        packet.setAcknum(packet.getSeqnum());
        packet.setChecksum(checksum(packet));

        // If packet is expected seq num, send data up to layer 5
        if (packet.getSeqnum() == (lastRcvSeqNum + 1)) {
          toLayer5(packet.getPayload());
          //toLayer3(B, packet);
          ackedPackets++;
          deliveredPackets++;
          lastRcvSeqNum++;
          lastRcvPacket = packet;

          // Check buffer for more packets
          while (receiverWindow[(lastRcvSeqNum + 1) % WindowSize] != null && receiverWindow[(lastRcvSeqNum + 1) % WindowSize].getSeqnum() == (lastRcvSeqNum + 1)) {
            toLayer5(receiverWindow[(lastRcvSeqNum + 1) % WindowSize].getPayload());
            //toLayer3(B, receiverWindow[(lastRcvSeqNum + 1) % WindowSize]);
            lastRcvPacket = receiverWindow[(lastRcvSeqNum + 1) % WindowSize];
            ackedPackets++;
            deliveredPackets++;
            lastRcvSeqNum++;
          }


          toLayer3(B, lastRcvPacket);

        // If packet is duplicate, send duplicate ack
        } else if (packet.getSeqnum() == lastRcvSeqNum) {
          System.out.println("B Input: Sending Duplicate ACK: " + lastRcvPacket.getSeqnum());
          toLayer3(B, lastRcvPacket);
          ackedPackets++;

        // If packet is not next but in window, buffer
        } else if (packet.getSeqnum() > (lastRcvSeqNum + 1) && packet.getSeqnum() < (lastRcvSeqNum + WindowSize)) {
          System.out.println("B Input: Sending Duplicate ACK: " + lastRcvPacket.getSeqnum());
          receiverWindow[packet.getSeqnum() % WindowSize] = packet;
          toLayer3(B, lastRcvPacket);
          ackedPackets++;
        }
      } else {
        System.out.println("B Input: Received Corrupted Packet");
        corruptedPackets++;
      }
      System.out.println("B Input: End");
    }
    
    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit() {
      receiverWindow = new Packet[WindowSize];
    }

    // Use to print final statistics
    protected void Simulation_done()
    {
      // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT

      double lostRatio = (retransmissions - corruptedPackets) / (double) ((originalTransmissions + retransmissions) + ackedPackets);
      double corruptionRatio = (corruptedPackets) / (double) ((originalTransmissions + retransmissions) + ackedPackets - (retransmissions - corruptedPackets));

      System.out.println("\n\n===============STATISTICS=======================");
      System.out.println("Number of original packets transmitted by A: " + originalTransmissions);
      System.out.println("Number of retransmissions by A: " + retransmissions);
      System.out.println("Number of data packets delivered to layer 5 at B: " + deliveredPackets);
      System.out.println("Number of ACK packets sent by B: " + ackedPackets);
      System.out.println("Number of corrupted packets: " + corruptedPackets);
      System.out.println("Ratio of lost packets: " + lostRatio);
      System.out.println("Ratio of corrupted packets: " + corruptionRatio);
      System.out.println("Average RTT: " + (rttSum/ (double) rttCount));
      System.out.println("Average communication time: " + (commSum/ (double) commCount));
      System.out.println("==================================================\n\n");
      System.out.println("Throughput (packets/sec): " + ((originalTransmissions + retransmissions) / ((getTime() - startTime) / 1000)));
      System.out.println("Goodput (packets/sec): " + (deliveredPackets / ((getTime() - startTime) / 1000)));
    } 

}