package com.telenav.cactus.git;

import com.telenav.cactus.git.Conflicts.Conflict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Tim Boudreau
 */
public class ConflictsTest
{

    /**
     * Test of hasHardConflicts method, of class Conflicts.
     */
    @Test
    public void testHasHardConflicts()
    {
        String txt = "changed in both\n"
                + "  base   100644 fcf6c086e40e2fb9bcbc4fa9c3880e972466187e anatomy/external/fur/pom.xml\n"
                + "  our    100644 6f8cda34fd648e540d6e643bb768a478c874ad64 anatomy/external/fur/pom.xml\n"
                + "  their  100644 90b2f94b6cd1f1937e2d516f3d134dc0b96087ed anatomy/external/fur/pom.xml\n"
                + "@@ -1,5 +1,9 @@\n"
                + " <?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "+<<<<<<< .our\n"
                + " <!-- This is a different comment -->\n"
                + "+=======\n"
                + "+<!-- a trivial comment -->\n"
                + "+>>>>>>> .their\n"
                + "\n"
                + " <project xmlns:xsi = \"http://www.w3.org/2001/XMLSchema-instance\" xmlns = \"http://maven.apache.org/POM/4.0.0\"\n"
                + "          xsi:schemaLocation = \"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">";
        Conflicts cf = Conflicts.parse(txt);
        assertFalse(cf.isEmpty());

        for (Conflict c : cf)
        {
            assertTrue(c.local().isPresent());
            Conflicts.ChangeInfo loc = c.local().get();
            assertEquals("6f8cda34fd648e540d6e643bb768a478c874ad64", loc.commit);
            assertEquals(100644, loc.fileMode);
            assertEquals("anatomy/external/fur/pom.xml", loc.relativePath);
            assertEquals("our", loc.in);

            assertTrue(c.remote().isPresent());
            Conflicts.ChangeInfo rem = c.remote().get();
            assertEquals("90b2f94b6cd1f1937e2d516f3d134dc0b96087ed", rem.commit);
            assertEquals(100644, rem.fileMode);
            assertEquals("anatomy/external/fur/pom.xml", rem.relativePath);
            assertEquals("their", rem.in);

            assertTrue(c.base().isPresent());
            Conflicts.ChangeInfo base = c.base().get();
            assertEquals("fcf6c086e40e2fb9bcbc4fa9c3880e972466187e", base.commit);
            assertEquals(100644, base.fileMode);
            assertEquals("anatomy/external/fur/pom.xml", base.relativePath);
            assertEquals("base", base.in);

            assertTrue(c.isHardConflict());
        }

        assertTrue(cf.hasHardConflicts());

        assertFalse(cf.filterHard().isEmpty());
    }

}
