// --- BEGIN LICENSE BLOCK ---
/* 
 * Copyright (c) 2009, Mikio L. Braun
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 * 
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 * 
 *     * Neither the name of the Technische Universität Berlin nor the
 *       names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior
 *       written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
// --- END LICENSE BLOCK ---
package org.jblas.util;

import java.io.*;

/**
 * Class which allows to load a dynamic file as resource (for example, from a 
 * jar-file)
 */
public class LibraryLoader {

    private Logger logger;
    private String libpath;
    
    public LibraryLoader() {
        logger = Logger.getLogger();
        libpath = null;
    }

    /**
     * <p>Find the library <tt>libname</tt> as a resource, copy it to a tempfile
     * and load it using System.load(). The name of the library has to be the
     * base name, it is mapped to the corresponding system name using
     * System.mapLibraryName(). For example, the library "foo" is called "libfoo.so"
     * under Linux and "foo.dll" under Windows, but you just have to pass "foo"
     * the loadLibrary().</p>
     *
     * <p>I'm not quite sure if this doesn't open all kinds of security holes. Any ideas?</p>
     *
     * <p>This function reports some more information to the "org.jblas" logger at
     * the FINE level.</p>
     *
     * @param libname basename of the library
     * @throws UnsatisfiedLinkError if library cannot be founds
     */
    public void loadLibrary(String libname, boolean withFlavor) {
        // preload flavor libraries
        String flavor = null;
        if (withFlavor) {
            logger.debug("Preloading ArchFlavor library.");
            flavor = ArchFlavor.archFlavor();
        }

        libname = System.mapLibraryName(libname);
        logger.debug("Attempting to load \"" + libname + "\".");

        String[] paths = {
            "/",
            "/bin/",
            fatJarLibraryPath("static", flavor),
            fatJarLibraryPathNonUnified("static", flavor),
            fatJarLibraryPath("dynamic", flavor),
            fatJarLibraryPathNonUnified("dynamic", flavor),
            fatJarLibraryPathNoLinkage(flavor),
            fatJarLibraryPathNoLinkageNoLib(flavor)
        };

        InputStream is = findLibrary(paths, libname);

        // Oh man, have to get out of here!
        if (is == null) {
        	String res = "";
        	for (String path: paths) {
        		res = res + path + "\n";
			}
            throw new UnsatisfiedLinkError("Couldn't find the resource " + libname + res + ".");
        }

        logger.config("Loading " + libname + " from " + libpath + ".");
        loadLibraryFromStream(libname, is);
    }

    private InputStream findLibrary(String[] paths, String libname) {
        InputStream is = null;
        for (String path: paths) {
            is = tryPath(path + libname);
            if (is != null) {
                libpath = path;
                break;
            }
        }
        return is;
    }

    /** Translate all those Windows to "Windows". ("Windows XP", "Windows Vista", "Windows 7", etc.) */
    private String unifyOSName(String osname) {
        if (osname.startsWith("Windows")) {
            return "Windows";
        }
        return osname;
    }

    /** Compute the path to the library. The path is basically
    "/" + os.name + "/" + os.arch + "/" + libname. */
    private String fatJarLibraryPath(String linkage, String flavor) {
        String sep = "/"; //System.getProperty("file.separator");
        String os_name = unifyOSName(System.getProperty("os.name"));
        String os_arch = System.getProperty("os.arch");
        String path = sep + "lib" + sep + linkage + sep + os_name + sep + os_arch + sep;
        if (null != flavor)
            path += flavor + sep;
        return path;
    }

    /** Full path without the OS name non-unified. */
    private String fatJarLibraryPathNonUnified(String linkage, String flavor) {
        String sep = "/"; //System.getProperty("file.separator");
        String os_name = System.getProperty("os.name");
        String os_arch = System.getProperty("os.arch");
        String path = sep + "lib" + sep + linkage + sep + os_name + sep + os_arch + sep;
        if (null != flavor)
            path += flavor + sep;
        return path;
    }

	/** Full path without linkages. */
    private String fatJarLibraryPathNoLinkage(String flavor) {
        String sep = "/"; //System.getProperty("file.separator");
        String os_name = System.getProperty("os.name");
        String os_arch = System.getProperty("os.arch");
        String path = sep + "lib" + sep + os_name + sep + os_arch + sep;
        if (null != flavor)
            path += flavor + sep;
        return path;
    }

	/** Full path without linkages or lib-prefix. */
    private String fatJarLibraryPathNoLinkageNoLib(String flavor) {
        String sep = "/"; //System.getProperty("file.separator");
        String os_name = System.getProperty("os.name");
        String os_arch = System.getProperty("os.arch");
        String path = sep + os_name + sep + os_arch + sep;
        if (null != flavor)
            path += flavor + sep;
        return path;
    }

    /** Try to open a file at the given position. */
    private InputStream tryPath(String path) {
        Logger.getLogger().debug("Trying path \"" + path + "\".");
        return getClass().getResourceAsStream(path);
    }

    /** Load a system library from a stream. Copies the library to a temp file
     * and loads from there.
     */
    private void loadLibraryFromStream(String libname, InputStream is) {
        try {
            File tempfile = File.createTempFile("jblas", libname);
            tempfile.deleteOnExit();
            OutputStream os = new FileOutputStream(tempfile);

            logger.debug("tempfile.getPath() = " + tempfile.getPath());

            long savedTime = System.currentTimeMillis();

            byte buf[] = new byte[1024];
            int len;
            while ((len = is.read(buf)) > 0) {
                os.write(buf, 0, len);
            }

            double seconds = (double) (System.currentTimeMillis() - savedTime) / 1e3;
            logger.debug("Copying took " + seconds + " seconds.");

            os.close();

            System.load(tempfile.getPath());
        } catch (IOException io) {
            logger.error("Could not create the temp file: " + io.toString() + ".\n");
        } catch (UnsatisfiedLinkError ule) {
            logger.error("Couldn't load copied link file: " + ule.toString() + ".\n");
        }
    }
}
