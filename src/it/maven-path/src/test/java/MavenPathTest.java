
/*
 * #%L
 * Alta Maven Plugin
 * %%
 * Copyright (C) 2014 - 2016 Andreas Veithen
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import javax.xml.soap.SOAPElement;

import org.junit.Test;
import org.w3c.dom.Node;

public class MavenPathTest {

    /**
     * Tests for %mavenPath% property, which generates classpath for UNIX:
     * Slash is used as directory separator,
     * Colon is used as classpath separator
     */
    @Test
    public void testMavenPathProperty() {
        String expectedPath = ".m2/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar:"
                + ".m2/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4-sources.jar";
        assertEquals(expectedPath, System.getProperty("maven.path.linux"));
    }
    
    /**
     * Tests for %mavenPathWindows% property, which generates classpath for Windows:
     * BackSlash is used as directory separator,
     * Semicolon is used as classpath separator
     */
    @Test
    public void testMavenPathWindowsProperty() {
        String expectedPath = ".m2\\org\\apache\\commons\\commons-lang3\\3.4\\commons-lang3-3.4.jar;"
                + ".m2\\org\\apache\\commons\\commons-lang3\\3.4\\commons-lang3-3.4-sources.jar";
        assertEquals(expectedPath, System.getProperty("maven.path.windows"));
    }
    
}