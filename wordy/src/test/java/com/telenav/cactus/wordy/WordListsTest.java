package com.telenav.cactus.wordy;

import java.util.ArrayList;
import static java.util.Collections.sort;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 *
 * @author Tim Boudreau
 */
public class WordListsTest {

    /**
     * Test of values method, of class WordLists.
     */
//    @Test
    @ParameterizedTest
    @EnumSource(WordLists.class)
    public void testNoDuplicates(WordLists list) {
        assertNoDuplicates(list);

        for (int i = 0; i < list.size(); i++) {
            String wd = list.word(i);
            assertNotNull(wd);
            assertEquals(i, list.indexOf(wd));
        }
    }

    @ParameterizedTest
    @EnumSource(WordLists.class)

    public void testSizes(WordLists list) {
        int st = list.staticSize;
        if (st > 0) {
            assertEquals(st, list.words().size(), list.name() + " sizes do not match");
        }
    }

    private static void assertNoDuplicates(WordLists list) {
        List<String> in = new ArrayList<>(list.words());
        sort(in);

        Set<String> set = new HashSet<>(in);
        if (set.size() < in.size()) {
            Set<String> duplicates = new TreeSet<>();
            String last = null;
            for (String s : in) {
                if (s.equals(last)) {
                    duplicates.add(s);
                }
                last = s;
            }
            StringBuilder dupList = new StringBuilder();
            for (String d : duplicates) {
                if (dupList.length() > 0) {
                    dupList.append(' ');
                }
                dupList.append('\'').append(d).append('\'');
            }

            dupList.append("\nDistinct ").append(list.name());
            for (String s : new TreeSet<>(set)) {
                dupList.append('\n').append(s);
            }

            fail(list.name() + " contains " + duplicates.size()
                    + " duplicates - orig size " + in.size()
                    + " distinct size " + set.size()
                    + " - "
                    + dupList);
        }
    }

}
