package com.tbd.NetHack;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Baseline smoke test to ensure native library linkage and JNI connectivity.
 */
@RunWith(AndroidJUnit4.class)
public class SmokeTest {

    static {
        // This will fail if the .so is not in the correct location or has missing dependencies
        System.loadLibrary("nethack");
    }

    @Test
    public void testLibraryLoading() {
        // If we reach here, System.loadLibrary succeeded
        assertTrue(true);
    }

    @Test
    public void testNativeMethodLinkage() {
        // Verify that the critical JNI methods are present and linkable
        try {
            // We don't want to actually run the game (it's a blocking loop), 
            // but we can check if the class containing native methods is valid.
            Class<?> ioClass = Class.forName("com.tbd.forkfront.NetHackIO");
            assertNotNull("NetHackIO class should exist", ioClass);
            
            // Check for existence of RunNetHack method
            boolean hasRunMethod = false;
            for (java.lang.reflect.Method m : ioClass.getDeclaredMethods()) {
                if (m.getName().equals("RunNetHack")) {
                    hasRunMethod = true;
                    break;
                }
            }
            assertTrue("RunNetHack native method should be defined in NetHackIO", hasRunMethod);
            
        } catch (ClassNotFoundException e) {
            fail("Required NetHackIO class not found: " + e.getMessage());
        }
    }
}
