# Reliable Transport Protocol

## Design

This protocol implements Selective Repeat with Cumulative ACKs,
utilizing a static retransmission timer (timeout length is defined
as an argument when running the program).

The sender keeps a LinkedList buffer, an array of Packets as the window, 
and an array of ints to keep track of which packets have been acked.
The int array is size `WindowSize`, and each slot corresponds to a packet
in the sender window. Each slot in the int array can be -1, 0, or 1, to indicate
no packet, transmitted packet, and ACKed packet, respectively.

The receiver keeps an array of Packets as the receiver window, as well
as an int for the last received sequence number, and a Packet for the 
last received packet (to quickly submit duplicate ACKs).

Whenever data from layer 5 is given to the sender, the sender creates a new 
packet with sequence number `CurSeqNo`, adds it to the buffer, and increments
`CurSeqNo`.
Then, the sender fills its window with packets from the buffer until either
the buffer holds no more packets or the window is at max size.
Then the sender iterates through the window and transmits all packets that 
have yet to be transmitted (a status of -1).

Next, the packet is received at B.
If the packet is uncorrupted, its sequence number is checked to see if
it's the next expected packet, or at least in the receiver window.
If it's neither, the packet is dropped and an ACK is sent for the last
received packet. 
If the packet is not the next expected packet, but in the window size, it 
is saved to a buffer (array of packets) at B, and an ACK is sent for the last 
received packet.
If the packet is the next expected packet, the data is sent up to layer 5, and
the packet is saved as the last received packet.
Then the receiver window is shifted down, and B checks to see if the next
packet is in the buffer. This repeats until the buffer doesn't have the next
packet.
Then, B will send an ACK for the last received packet. If the buffer didn't contain
any next sequential packets, this will be a normal ACK, else it will be a 
cumulative ACK.

Finally, the packet is received back at A to be ACKed.
If the packet is uncorrupted, its sequence number is checked to see if it is
the next expected ACK (the head of the sender window).
If it's not, that means it's a duplicate ACK, and the receiver  is still
missing the packet at the head of the sender window, so the sender will
retransmit that packet.
If the packet has the expected ACK, the sender window slides up. 
Then the sender checks to see if the window contains any untransmitted 
packets, which will then be sent.

If the timer at A ever times out, it resends the packet at the head
of the sender window.

## Testing

To run the test, first compile with `javac Project.java`.
Then run `java Project.java`.

To use default values, just press ENTER at each prompt, except for the seed.
Seed must be a value greater than 0.

To view statistical figures on the relationship between communication time 
and loss/corruption probability, view `Communication Time Figures.xlsx`.

## Extensions and Tradeoffs

Currently, the protocol using infinitely growing sequence numbers for the packets.
This makes it easy to check whether a packet is in order, as you can directly
compare the values of the sequence numbers. However, this can't work in the real
world as there are a limited number of bits with which you can store the sequence 
number. A better way to implement this would be to use wrapping sequence numbers,
with % `LimitSeqNo`, to ensure that we don't run out of bits in the representation.