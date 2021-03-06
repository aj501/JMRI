package jmri.jmrix.cmri.serial.diagnostic;

import java.awt.GraphicsEnvironment;
import jmri.jmrix.cmri.CMRISystemConnectionMemo;
import jmri.util.JUnitUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Test simple functioning of DiagnosticAction
 *
 * @author Paul Bender Copyright (C) 2016
 */
public class DiagnosticActionTest {

    @Test
    public void testStringCtor() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        DiagnosticAction action = new DiagnosticAction("CMRI test Action",new CMRISystemConnectionMemo()); 
        Assert.assertNotNull("exists", action);
    }

    @Test
    public void testCtor() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        DiagnosticAction action = new DiagnosticAction(new CMRISystemConnectionMemo()); 
        Assert.assertNotNull("exists", action);
    }

    @Before
    public void setUp() {
        JUnitUtil.setUp();
    }

    @After
    public void tearDown() {        JUnitUtil.tearDown();    }
}
