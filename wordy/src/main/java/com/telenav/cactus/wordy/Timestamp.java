package com.telenav.cactus.wordy;

import static java.lang.Thread.sleep;
import java.time.Instant;
import java.util.function.Consumer;

/**
 *
 * @author Tim Boudreau
 */
public class Timestamp implements BoundValue {

    public static final long SECONDS = 1000;
    public static final long MINUTES = SECONDS * 60;
    public static final long HOURS = MINUTES * 60;
    public static final long DAYS = HOURS * 24;
    public static final long WEEKS = DAYS * 7;
    public static final long YEARS = DAYS * 365;
    public static final long DECADES = YEARS * 10;

    private final long epochStart;
    private final long when;
    private final long divisor;
    private final long epochEnd;

    public Timestamp(long epochStart, long when, long divisor, long epochEnd) {
        this.epochStart = epochStart;
        this.when = when;
        this.divisor = divisor;
        this.epochEnd = epochEnd;
    }

    public Timestamp(Instant epochStart, long divisor, Instant epochEnd) {
        this(epochStart, Instant.now(), divisor, epochEnd);
    }

    public Timestamp(Instant epochStart, Instant when, long divisor, Instant epochEnd) {
        this(epochStart.toEpochMilli(), when.toEpochMilli(), divisor, epochEnd.toEpochMilli());
    }

    @Override
    public long of() {
        return (epochEnd - epochStart) / divisor;
    }

    public String value() {
        return value(Recipe.DOUBLE_ADJECTIVE_NOUN);
    }

    public String value(Recipe recipe) {
        return recipe.createPhrase("-", this);
    }

    public long n() {
        long offset = when - epochStart;
        long result = offset / divisor;
        if (result < 0) {
            throw new IllegalStateException("Invalid result " + result);
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        Instant endOfEra = Instant.parse("2120-01-01T15:00:00.000Z");

        Instant start = Instant.parse("2020-01-01T15:00:00.000Z");

        for (int i = 0; i < Integer.MAX_VALUE; i++) {

            Timestamp ts = new Timestamp(start, SECONDS, endOfEra);

//            System.out.println("tsmax " + ts.of());
//
//            System.out.println("tsnow " + ts.n());
//
            System.out.println("tsbits " + ts.bits());
//
            System.out.println("val " + ts.value());

            sleep(2000);
        }
    }
}
