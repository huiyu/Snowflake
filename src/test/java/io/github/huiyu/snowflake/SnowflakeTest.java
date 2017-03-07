package io.github.huiyu.snowflake;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class SnowflakeTest {

    @Test
    public void testGenerateId() throws Exception {
        Snowflake snowflake = new Snowflake.Builder(0).build();
        long id = snowflake.nextId();
        assertTrue(id > 0);
    }

    @Test
    public void testGetTimeStamp() {
        Snowflake snowflake = new Snowflake.Builder(0).build();
        long currentTime = snowflake.currentTime();
        long elapsed = System.currentTimeMillis() - currentTime;
        assertTrue(elapsed >= 0);
        assertTrue(elapsed <= 50L);
    }

    @Test
    public void testCheckServerId() {
        // default 10 bits
        testCheckServerIdExceptionCaught(-1L);
        testCheckServerIdExceptionCaught(1024L);
        // 9 bits
        new Snowflake.Builder(511, 9);
        testCheckServerIdExceptionCaught(512, 9);
        // 0 bits
        new Snowflake.Builder(0, 0).build();
        testCheckServerIdExceptionCaught(1, 0);
        // illegal id bits between [0, 16]
        testCheckServerIdExceptionCaught(0, -1);
        testCheckServerIdExceptionCaught(0, 17);
        new Snowflake.Builder(0, 16).build();
    }

    private void testCheckServerIdExceptionCaught(long id) {
        try {
            new Snowflake.Builder(id).build();
            fail();
        } catch (Throwable e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }
    }

    private void testCheckServerIdExceptionCaught(long id, long idBits) {
        try {
            new Snowflake.Builder(id, idBits).build();
            fail();
        } catch (Throwable e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }
    }

    @Test
    public void testCheckSetSequenceBits() {
        testCheckSetSequenceBitsExceptionCaught(-1L);
        testCheckSetSequenceBitsExceptionCaught(13L);
        new Snowflake.Builder(0L).setSequenceBits(12L).build();

        // sequenceBits + idBits <= 22
        new Snowflake.Builder(0L, 9L).setSequenceBits(13L).build();
        new Snowflake.Builder(0L, 9L).setSequenceBits(10L).build();
        testCheckSetSequenceBitsExceptionCaught(9L, 14L);
    }

    private void testCheckSetSequenceBitsExceptionCaught(long idBits, long sequenceBits) {
        try {
            new Snowflake.Builder(0L, idBits).setSequenceBits(sequenceBits).build();
            fail();
        } catch (Throwable e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }
    }

    private void testCheckSetSequenceBitsExceptionCaught(long sequenceBits) {
        testCheckSetSequenceBitsExceptionCaught(Snowflake.DEFAULT_ID_BITS, sequenceBits);
    }

    @Test
    public void testMaskServerId() {
        int serverId = 0x1F;
        long sequenceBits = 12L; // default
        long workerMask = 0x00000000003FF000L; // idBits = 10L
        Snowflake snowflake = new Snowflake.Builder(serverId).build();
        for (int i = 0; i < 1000; i++) {
            long id = snowflake.nextId();
            assertEquals(serverId, (id & workerMask) >> sequenceBits);
        }

        long idBits = 11L;
        workerMask = 0x00000000007FF000L;
        snowflake = new Snowflake.Builder(serverId, idBits).build();
        for (int i = 0; i < 1000; i++) {
            long id = snowflake.nextId();
            assertEquals(serverId, (id & workerMask) >> sequenceBits);
        }

        idBits = 9L;
        sequenceBits = 10L;
        workerMask = 0x000000000007FC00L;
        snowflake = new Snowflake.Builder(serverId, idBits)
                .setSequenceBits(sequenceBits)
                .build();
        for (int i = 0; i < 1000; i++) {
            long id = snowflake.nextId();
            assertEquals(serverId, (id & workerMask) >> sequenceBits);
        }
    }

    @Test
    public void testMaskTimestamp() {
        EasySnowflake snowflake = new EasySnowflake(0x1F);
        long timestampMask = 0xFFFFFFFFFFC00000L;
        for (int i = 0; i < 100; i++) {
            long currentTimeMillis = System.currentTimeMillis();
            snowflake.setTimeGenerator(() -> currentTimeMillis);
            long id = snowflake.nextId();
            assertEquals(currentTimeMillis - Snowflake.DEFAULT_EPOCH,
                    (id & timestampMask) >> 22);
        }
    }

    @Test
    public void testRolloverSequence() {
        long serverId = 0x1F;
        long sequenceBits = 12L;
        long workerMask = 0x00000000003FF000L; // idBits = 10L
        Snowflake snowflake = new Snowflake.Builder(serverId).build();
        long sequenceStart = 0xFFFFFF - 20;
        long sequenceEnd = 0xFFFFFF + 20;

        for (long i = sequenceStart; i < sequenceEnd; i++) {
            long id = snowflake.nextId();
            assertEquals(serverId, (id & workerMask) >> sequenceBits);
        }
    }

    @Test
    public void testGenerateIncreasingIds() {
        Snowflake snowflake = new Snowflake.Builder(0L).build();
        long lastId = 0L;
        for (int i = 0; i < 1000; i++) {
            long id = snowflake.nextId();
            assertTrue(id > lastId);
            lastId = id;
        }
    }

    @Test
    public void testPerformance() {
        long start = System.currentTimeMillis();
        Snowflake snowflake = new Snowflake.Builder(0L).build();
        int times = 1000000;
        for (int i = 0; i < times; i++) {
            snowflake.nextId();
        }
        long end = System.currentTimeMillis();
        System.out.println(String.format("Generated %d ids in %d ms", times, (end - start)));
    }

    @Test
    public void testGenerateUniqueIds() {
        Set<Long> ids = new HashSet<>();
        int times = 1000000;
        Snowflake snowflake = new Snowflake.Builder(0L).build();
        for (int i = 0; i < times; i++) {
            ids.add(snowflake.nextId());
        }
        assertEquals(times, ids.size());
    }

    @Test
    public void testGenerateUniqueIdEvenWhenTimeGoesBackward() {
        long sequenceMask = ~(-1L << 12);
        StaticSnowflake snowflake = new StaticSnowflake(0);

        // first we generate 2 ids with the same time, so that we get the sequence to 1
        assertEquals(0, snowflake.sequence);
        assertEquals(1, snowflake.time);

        long id1 = snowflake.nextId();
        assertEquals(1, id1 >> 22);
        assertEquals(0, id1 & sequenceMask);

        assertEquals(0, snowflake.sequence);
        assertEquals(1, snowflake.time);

        long id2 = snowflake.nextId();
        assertEquals(1, id2 >> 22);
        assertEquals(1, id2 & sequenceMask);

        // then we set the time backwards
        snowflake.time = 0L;
        assertEquals(1, snowflake.sequence);
        try {
            snowflake.nextId();
            fail();
        } catch (Throwable t) {
            assertEquals(InvalidSystemClockException.class, t.getClass());
        }

        assertEquals(1, snowflake.sequence);

        snowflake.time = 1L;
        long id3 = snowflake.nextId();
        assertEquals(1, id3 >> 22);
        assertEquals(2, id3 & sequenceMask);
    }

    private static class EasySnowflake extends Snowflake {

        Supplier<Long> timeGenerator;

        protected EasySnowflake(long id) {
            super(id, DEFAULT_ID_BITS, DEFAULT_SEQUENCE_BITS, DEFAULT_EPOCH);
        }

        public void setTimeGenerator(Supplier<Long> timeGenerator) {
            this.timeGenerator = timeGenerator;
        }

        @Override
        protected long currentTime() {
            return timeGenerator.get();
        }
    }

    private static class StaticSnowflake extends Snowflake {

        private long time = 1L;

        protected StaticSnowflake(long id) {
            super(id, DEFAULT_ID_BITS, DEFAULT_SEQUENCE_BITS, DEFAULT_EPOCH);
        }

        @Override
        protected long currentTime() {
            return time + DEFAULT_EPOCH;
        }
    }
}