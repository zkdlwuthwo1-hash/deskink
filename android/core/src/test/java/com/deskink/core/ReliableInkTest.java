package com.deskink.core;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class ReliableInkTest {
    @Test public void aLateAcknowledgementCannotRescueExpiredPenInputBeforeTheTimerRuns() {
        ReliableInk ink=new ReliableInk(42,3); ink.offer(Ink.command("clear"),0);
        ink.acknowledge(ack(42,3,1),250);
        assertTrue(ink.expired(250)); assertNull(ink.offer(Ink.command("undo"),251));
    }
    @Test public void unacknowledgedInkFailsAt250MillisecondsInsteadOfReplayingLate() {
        ReliableInk ink=new ReliableInk(42,3);
        assertNotNull(ink.offer(Ink.command("clear"),100));
        assertFalse(ink.expired(349)); assertTrue(ink.expired(350));
        assertNull(ink.offer(Ink.command("undo"),351)); assertTrue(ink.retry(351).isEmpty());
    }
    @Test public void staleEpochAndImpossibleFutureAcknowledgementsCannotDiscardPendingInk() {
        ReliableInk ink=new ReliableInk(42,3); byte[] packet=ink.offer(Ink.command("clear"),0);
        ink.acknowledge(ack(42,2,1),1); ink.acknowledge(ack(42,3,2),1);
        assertArrayEquals(packet,ink.retry(20).get(0));
    }
    @Test public void thePendingWindowIsBoundedEvenWhenTheNetworkStopsReading() {
        ReliableInk ink=new ReliableInk(42,3);
        for(int i=0;i<256;i++) assertNotNull(ink.offer(Ink.command("clear"),0));
        assertNull(ink.offer(Ink.command("undo"),0)); assertTrue(ink.expired(0));
    }
    @Test public void lostInkIsRetriedUntilTheMatchingCumulativeAcknowledgement() {
        ReliableInk ink=new ReliableInk(42,3);
        byte[] sent=ink.offer(Ink.command("clear"),0);
        assertTrue(new String(sent,StandardCharsets.UTF_8).startsWith("DI02{"));
        assertTrue(ink.retry(19).isEmpty());
        assertArrayEquals(sent,ink.retry(20).get(0));
        ink.acknowledge(ack(99,3,1),20);
        assertArrayEquals(sent,ink.retry(40).get(0));
        ink.acknowledge(ack(42,3,1),40);
        assertTrue(ink.retry(60).isEmpty());
    }
    static byte[] ack(long session,long epoch,long sequence) {
        return ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            .put(new byte[]{'D','A','0','2'}).putInt((int)session).putInt((int)epoch).putLong(sequence).array();
    }
}
