package jmri.jmrit.logix.configurexml;

import jmri.util.JUnitUtil;
import org.junit.*;

/**
 *
 * @author Paul Bender Copyright (C) 2017
 */
public class OBlockManagerXmlTest {

    @Test
    @Ignore("causes missing data for other tests?")
    public void testCTor() {
        OBlockManagerXml t = new OBlockManagerXml();
        Assert.assertNotNull("exists",t);
    }

    @Before
    public void setUp() {
        JUnitUtil.setUp();
    }

    @After
    public void tearDown() {
        JUnitUtil.tearDown();
    }

    // private final static Logger log = LoggerFactory.getLogger(OBlockManagerXmlTest.class);

}
