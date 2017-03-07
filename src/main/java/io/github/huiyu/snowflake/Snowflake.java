package io.github.huiyu.snowflake;

/**
 * +-----------------+-----------+----------+
 * |    timestamp    | server id | sequence |
 * +-----------------+-----------+----------+
 */
public class Snowflake {

    public static final long DEFAULT_ID_BITS = 10L;
    public static final long DEFAULT_SEQUENCE_BITS = 12L;
    public static final long DEFAULT_EPOCH = 1288834974657L;

    private final long epoch;
    private final long id;
    private final long idShift;
    private final long timestampLeftShift;
    private final long sequenceMask;

    protected volatile long sequence = 0L;
    protected volatile long lastTimestamp = -1L;

    protected Snowflake(long id, long idBits, long sequenceBits, long epoch) {
        this.id = id;
        this.epoch = epoch;

        this.idShift = sequenceBits;
        this.timestampLeftShift = sequenceBits + idBits;
        this.sequenceMask = ~(-1 << sequenceBits);
    }

    public synchronized long nextId() {
        long timestamp = currentTime();
        if (timestamp < lastTimestamp) {
            String msg = String.format(
                    "Clock moved backwards. Refusing to generate for %d milliseconds",
                    (lastTimestamp - timestamp));
            throw new InvalidSystemClockException(msg);
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0L) {
                timestamp = untilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - epoch) << timestampLeftShift) | id << idShift | sequence;
    }

    protected long untilNextMillis(long lastTimestamp) {
        long timestamp = currentTime();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTime();
        }
        return timestamp;
    }

    protected long currentTime() {
        return System.currentTimeMillis();
    }

    public static class Builder {

        private static final long MAX_ID_BITS = 16L;
        private static final long MIN_ID_BITS = 0L;

        private final long id;
        private final long idBits;
        private long sequenceBits = DEFAULT_SEQUENCE_BITS;
        private long epoch = DEFAULT_EPOCH;

        public Builder(long id) {
            this(id, DEFAULT_ID_BITS);
        }

        public Builder(long id, long idBits) {
            if (idBits < MIN_ID_BITS || idBits > MAX_ID_BITS) {
                throw new IllegalArgumentException(
                        "ID bits must >= " + MIN_ID_BITS + " and <= " + MAX_ID_BITS);
            }

            long maxId = ~(-1 << idBits);
            if (id < 0 || id > maxId) {
                throw new IllegalArgumentException("ID must >= 0 and <= " + maxId);
            }
            this.id = id;
            this.idBits = idBits;
        }

        public Builder setSequenceBits(long sequenceBits) {
            long maxSequenceBits = DEFAULT_SEQUENCE_BITS + DEFAULT_ID_BITS - idBits;
            if (sequenceBits <= 0 || sequenceBits > maxSequenceBits) {
                throw new IllegalArgumentException(
                        "Sequence bits must > 0 and <= " + maxSequenceBits);
            }
            this.sequenceBits = sequenceBits;
            return this;
        }

        public Builder setEpoch(long epoch) {
            this.epoch = epoch;
            return this;
        }

        public Snowflake build() {
            return new Snowflake(id, idBits, sequenceBits, epoch);
        }
    }
}
