package com.telenav.cactus.wordy;

import static com.telenav.cactus.wordy.BuildName.TELENAV_EPOCH_DAY;
import static com.telenav.cactus.wordy.WordLists.bits;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Tim Boudreau
 */
public class BitsBagTest {

    @Test
    public void testAdd() {
        BitsBag bag = new BitsBag();

        int sum = 0;
        for (int i = 1; i < 50; i++) {
            long allOnes = ones(i);
            bag.add(i, allOnes);
            sum += i;
//            System.out.println(i + ". " + sum + " card " + bag.cardinality() + " bits " + bag.bits());
            assertEquals(sum, bag.cardinality(), "Bag reports different number of bits than added");
            assertEquals(bag.bits(), bag.cardinality(), "With all ones, bits should be same as cursor");
            assertEquals(onesString(sum) + " @ " + sum, bag.toString());
        }

        List<Boolean> vals = new ArrayList<>();
        BitsConsumer bc = value -> {
            boolean isTrue = (value & 0b1L) != 0;
//            assertTrue(isTrue, "Value at " + vals.size() + " is false");
            vals.add(isTrue);
            return 1;
        };
        bag.consume(bc);
        assertEquals(sum, vals.size());
        assertEquals(bag.toString(), boolsToBits(vals) + " @ " + sum);
    }

    private String boolsToBits(List<Boolean> bls) {
        StringBuilder sb = new StringBuilder();
        for (Boolean b : bls) {
            sb.append(b ? "1" : "0");
        }
        return sb.toString();
    }

    @Test
    public void testBitsConsumer() {
        System.out.println(WordLists.POSESSIVES.words());
        BitsConsumer bc = WordLists.POSESSIVES.toBitsConsumer(str -> {
            assertEquals("its", str);
        });
        int consumed = bc.consume(ones(8));
        assertEquals(3, WordLists.POSESSIVES.bits());
        assertEquals(3, consumed);
    }

    private String onesString(int bits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bits; i++) {
            sb.append('1');
        }
        return sb.toString();
    }

    private static long ones(int ct) {
        long result = 0;
        for (int i = 0; i < ct; i++) {
            result |= 1 << i;
        }
        return result;
    }

    @Test
    public void test() {
        Instant start = Instant.parse("2021-06-01T15:00:00.000Z");
        ZonedDateTime date = ZonedDateTime.ofInstant(start, ZoneId.of("GMT"));
        String nm = BuildName.name(date);

        LocalDate loc = date.toLocalDate();
        long num = loc.toEpochDay() - TELENAV_EPOCH_DAY;

        long factor = (BuildName.adjectives.length * BuildName.nouns.length);

        System.out.println("BITS IN FACTOR " + bits(factor));

        BoundValue v = BoundValue.wrap(factor, () -> num);

        System.out.println("BITS IN V " + v.bits());

        String phr = Recipe.TELENAV_DEFAULT.createPhrase(v);

        System.out.println("REAL: " + nm);
        System.out.println("MINE: " + phr);
    }

}
