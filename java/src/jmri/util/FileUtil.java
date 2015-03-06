// FileUtil.java
package jmri.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common utility methods for working with Files.
 * <P>
 * We needed a place to refactor common File-processing idioms in JMRI code, so
 * this class was created. It's more of a library of procedures than a real
 * class, as (so far) all of the operations have needed no state information.
 *
 * @author Bob Jacobsen Copyright 2003, 2005, 2006
 * @author Randall Wood Copyright 2012, 2013, 2014
 * @version $Revision$
 */
public final class FileUtil {

    /**
     * Portable reference to items in the JMRI program directory.
     */
    static public final String PROGRAM = "program:"; // NOI18N
    /**
     * Portable reference to the JMRI user's preferences directory.
     */
    static public final String PREFERENCES = "preference:"; // NOI18N
    /**
     * Portable reference to the JMRI applications preferences directory.
     */
    static public final String SETTINGS = "settings:"; // NOI18N
    /**
     * Portable reference to the user's home directory.
     */
    static public final String HOME = "home:"; // NOI18N
    /**
     * Portable reference to the current profile directory.
     */
    static public final String PROFILE = "profile:"; // NOI18N
    /**
     * Portable reference to the current scripts directory.
     */
    static public final String SCRIPTS = "scripts:"; // NOI18N
    /**
     * Replaced with {@link #PROGRAM}.
     *
     * @see #PROGRAM
     * @deprecated
     */
    @Deprecated
    static public final String RESOURCE = "resource:"; // NOI18N
    /**
     * Replaced with {@link #PREFERENCES}.
     *
     * @see #PREFERENCES
     * @deprecated
     */
    @Deprecated
    static public final String FILE = "file:"; // NOI18N
    /**
     * The portable file path component separator.
     */
    static public final char SEPARATOR = '/'; // NOI18N
    /*
     * User's home directory
     */
    private static final String homePath = System.getProperty("user.home") + File.separator; // NOI18N
    /*
     * Settable directories
     */
    /* JMRI program path, defaults to directory JMRI is executed from */
    static private String programPath = null;
    /* path to jmri.jar */
    static private String jarPath = null;
    /* path to the jython scripts directory */
    static private String scriptsPath = null;
    /* path to the user's files directory */
    static private String userFilesPath = null;
    /* path to the current profile */
    static private String profilePath = null;
    // initialize logging
    private static final Logger log = LoggerFactory.getLogger(FileUtil.class.getName());

    /**
     * The types of locations to use when falling back on default locations in {@link #findURL(java.lang.String, java.lang.String...)
     * }.
     */
    static public enum Location {

        INSTALLED, USER, ALL, NONE
    }

    /**
     * Get the {@link java.io.File} that path refers to. Throws a
     * {@link java.io.FileNotFoundException} if the file cannot be found instead
     * of returning null (as File would). Use {@link #getURI(java.lang.String) }
     * or {@link #getURL(java.lang.String) } instead of this method if possible.
     *
     * @param path
     * @return {@link java.io.File} at path
     * @throws java.io.FileNotFoundException
     * @see #getURI(java.lang.String)
     * @see #getURL(java.lang.String)
     */
    static public File getFile(String path) throws FileNotFoundException {
        try {
            return new File(FileUtil.pathFromPortablePath(path));
        } catch (NullPointerException ex) {
            throw new FileNotFoundException("Cannot find file at " + path);
        }
    }

    /**
     * Get the {@link java.io.File} that path refers to. Throws a
     * {@link java.io.FileNotFoundException} if the file cannot be found instead
     * of returning null (as File would).
     *
     * @param path
     * @return {@link java.io.File} at path
     * @throws java.io.FileNotFoundException
     * @see #getFile(java.lang.String)
     * @see #getURL(java.lang.String)
     */
    static public URI getURI(String path) throws FileNotFoundException {
        return FileUtil.getFile(path).toURI();
    }

    /**
     * Get the {@link java.net.URL} that path refers to. Throws a
     * {@link java.io.FileNotFoundException} if the URL cannot be found instead
     * of returning null.
     *
     * @param path
     * @return {@link java.net.URL} at path
     * @throws FileNotFoundException
     * @see #getFile(java.lang.String)
     * @see #getURI(java.lang.String)
     */
    static public URL getURL(String path) throws FileNotFoundException {
        try {
            return FileUtil.getURI(path).toURL();
        } catch (MalformedURLException ex) {
            throw new FileNotFoundException("Cannot create URL for file at " + path);
        }
    }

    /*
     * Get the canonical path for a portable path. There are nine cases:
     * <ul>
     * <li>Starts with "resource:", treat the rest as a pathname relative to the
     * program directory (deprecated; see "program:" below)</li>
     * <li>Starts with "program:", treat the rest as a relative pathname below
     * the program directory</li>
     * <li>Starts with "preference:", treat the rest as a relative path below
     * the user's files directory</li>
     * <li>Starts with "settings:", treat the rest as a relative path below the
     * JMRI system preferences directory</li>
     * <li>Starts with "home:", treat the rest as a relative path below the
     * user.home directory</li>
     * <li>Starts with "file:", treat the rest as a relative path below the
     * resource directory in the preferences directory (deprecated; see
     * "preference:" above)</li>
     * <li>Starts with "profile:", treat the rest as a relative path below the
     * profile directory as specified in the
     * active{@link jmri.profile.Profile}</li>
     * <li>Starts with "scripts:", treat the rest as a relative path below the
     * scripts directory</li>
     * <li>Otherwise, treat the name as a relative path below the program
     * directory</li>
     * </ul>
     * In any case, absolute pathnames will work.
     *
     * @param path The name string, possibly starting with file:, home:,
     * profile:, program:, preference:, scripts:, settings, or resource:
     * @return Canonical path to use, or null if one cannot be found.
     * @since 2.7.2
     */
    static private String pathFromPortablePath(@Nonnull String path) {
        if (path.startsWith(PROGRAM)) {
            if (new File(path.substring(PROGRAM.length())).isAbsolute()) {
                path = path.substring(PROGRAM.length());
            } else {
                path = path.replaceFirst(PROGRAM, Matcher.quoteReplacement(FileUtil.getProgramPath()));
            }
        } else if (path.startsWith(PREFERENCES)) {
            if (new File(path.substring(PREFERENCES.length())).isAbsolute()) {
                path = path.substring(PREFERENCES.length());
            } else {
                path = path.replaceFirst(PREFERENCES, Matcher.quoteReplacement(FileUtil.getUserFilesPath()));
            }
        } else if (path.startsWith(PROFILE)) {
            if (new File(path.substring(PROFILE.length())).isAbsolute()) {
                path = path.substring(PROFILE.length());
            } else {
                path = path.replaceFirst(PROFILE, Matcher.quoteReplacement(FileUtil.getProfilePath()));
            }
        } else if (path.startsWith(SCRIPTS)) {
            if (new File(path.substring(SCRIPTS.length())).isAbsolute()) {
                path = path.substring(SCRIPTS.length());
            } else {
                path = path.replaceFirst(SCRIPTS, Matcher.quoteReplacement(FileUtil.getScriptsPath()));
            }
        } else if (path.startsWith(SETTINGS)) {
            if (new File(path.substring(SETTINGS.length())).isAbsolute()) {
                path = path.substring(SETTINGS.length());
            } else {
                path = path.replaceFirst(SETTINGS, Matcher.quoteReplacement(FileUtil.getPreferencesPath()));
            }
        } else if (path.startsWith(HOME)) {
            if (new File(path.substring(HOME.length())).isAbsolute()) {
                path = path.substring(HOME.length());
            } else {
                path = path.replaceFirst(HOME, Matcher.quoteReplacement(FileUtil.getHomePath()));
            }
        } else if (path.startsWith(RESOURCE)) {
            if (new File(path.substring(RESOURCE.length())).isAbsolute()) {
                path = path.substring(RESOURCE.length());
            } else {
                path = path.replaceFirst(RESOURCE, Matcher.quoteReplacement(FileUtil.getProgramPath()));
            }
        } else if (path.startsWith(FILE)) {
            if (new File(path.substring(FILE.length())).isAbsolute()) {
                path = path.substring(FILE.length());
            } else {
                path = path.replaceFirst(FILE, Matcher.quoteReplacement(FileUtil.getUserFilesPath() + "resources" + File.separator));
            }
        } else if (!new File(path).isAbsolute()) {
            return null;
        }
        try {
            // if path cannot be converted into a canonical path, return null
            log.debug("Using {}", path);
            return new File(path.replace(SEPARATOR, File.separatorChar)).getCanonicalPath();
        } catch (IOException ex) {
            log.warn("Cannot convert {} into a usable filename.", path, ex);
            return null;
        }
    }

    /**
     * Get the resource file corresponding to a name. There are five cases:
     * <ul>
     * <li>Starts with "resource:", treat the rest as a pathname relative to the
     * program directory (deprecated; see "program:" below)</li>
     * <li>Starts with "program:", treat the rest as a relative pathname below
     * the program directory</li>
     * <li>Starts with "preference:", treat the rest as a relative path below
     * the user's files directory</li>
     * <li>Starts with "settings:", treat the rest as a relative path below the
     * JMRI system preferences directory</li>
     * <li>Starts with "home:", treat the rest as a relative path below the
     * user.home directory</li>
     * <li>Starts with "file:", treat the rest as a relative path below the
     * resource directory in the preferences directory (deprecated; see
     * "preference:" above)</li>
     * <li>Starts with "profile:", treat the rest as a relative path below the
     * profile directory as specified in the
     * active{@link jmri.profile.Profile}</li>
     * <li>Starts with "scripts:", treat the rest as a relative path below the
     * scripts directory</li>
     * <li>Otherwise, treat the name as a relative path below the program
     * directory</li>
     * </ul>
     * In any case, absolute pathnames will work.
     *
     * @param pName The name string, possibly starting with file:, home:,
     *              profile:, program:, preference:, scripts:, settings, or
     *              resource:
     * @return Absolute file name to use, or null.
     * @since 2.7.2
     */
    static public String getExternalFilename(String pName) {
        String filename = FileUtil.pathFromPortablePath(pName);
        return (filename != null) ? filename : pName;
    }

    /**
     * Convert a portable filename into an absolute filename.
     *
     * @param path
     * @return An absolute filename
     */
    static public String getAbsoluteFilename(String path) {
        return FileUtil.pathFromPortablePath(path);
    }

    /**
     * Convert a File object's path to our preferred storage form.
     *
     * This is the inverse of {@link #getFile(String pName)}. Deprecated forms
     * are not created.
     *
     * @param file File at path to be represented
     * @return Filename for storage in a portable manner
     * @since 2.7.2
     */
    static public String getPortableFilename(File file) {
        return FileUtil.getPortableFilename(file, false, false);
    }

    /**
     * Convert a File object's path to our preferred storage form.
     *
     * This is the inverse of {@link #getFile(String pName)}. Deprecated forms
     * are not created.
     *
     * This method supports a specific use case concerning profiles and other
     * portable paths that are stored within the User files directory, which
     * will cause the {@link jmri.profile.ProfileManager} to write an incorrect
     * path for the current profile or
     * {@link apps.configurexml.FileLocationPaneXml} to write an incorrect path
     * for the Users file directory. In most cases, the use of
     * {@link #getPortableFilename(java.io.File)} is preferable.
     *
     * @param file                File at path to be represented
     * @param ignoreUserFilesPath true if paths in the User files path should be
     *                            stored as absolute paths, which is often not
     *                            desirable.
     * @param ignoreProfilePath   true if paths in the profile should be stored
     *                            as absolute paths, which is often not
     *                            desirable.
     * @return Storage format representation
     * @since 3.5.5
     */
    static public String getPortableFilename(File file, boolean ignoreUserFilesPath, boolean ignoreProfilePath) {
        // compare full path name to see if same as preferences
        String filename = file.getAbsolutePath();

        // append separator if file is a directory
        if (file.isDirectory()) {
            filename = filename + File.separator;
        }

        // compare full path name to see if same as preferences
        if (!ignoreUserFilesPath) {
            if (filename.startsWith(getUserFilesPath())) {
                return PREFERENCES + filename.substring(getUserFilesPath().length(), filename.length()).replace(File.separatorChar, SEPARATOR);
            }
        }

        if (!ignoreProfilePath) {
            // compare full path name to see if same as profile
            if (filename.startsWith(getProfilePath())) {
                return PROFILE + filename.substring(getProfilePath().length(), filename.length()).replace(File.separatorChar, SEPARATOR);
            }
        }

        // compare full path name to see if same as settings
        if (filename.startsWith(getPreferencesPath())) {
            return SETTINGS + filename.substring(getPreferencesPath().length(), filename.length()).replace(File.separatorChar, SEPARATOR);
        }

        if (!ignoreUserFilesPath) {
            /*
             * The tests for any portatable path that could be within the
             * UserFiles locations needs to be within this block. This prevents
             * the UserFiles or Profile path from being set to another portable
             * path that is user settable.
             *
             * Note that this test should be after the UserFiles, Profile, and
             * Preferences tests.
             */
            // check for relative to scripts dir
            if (filename.startsWith(getScriptsPath()) && !filename.equals(getScriptsPath())) {
                return SCRIPTS + filename.substring(getScriptsPath().length(), filename.length()).replace(File.separatorChar, SEPARATOR);
            }
        }

        // now check for relative to program dir
        if (filename.startsWith(getProgramPath())) {
            return PROGRAM + filename.substring(getProgramPath().length(), filename.length()).replace(File.separatorChar, SEPARATOR);
        }

        // compare full path name to see if same as home directory
        // do this last, in case preferences or program dir are in home directory
        if (filename.startsWith(getHomePath())) {
            return HOME + filename.substring(getHomePath().length(), filename.length()).replace(File.separatorChar, SEPARATOR);
        }

        return filename.replace(File.separatorChar, SEPARATOR);   // absolute, and doesn't match; not really portable...
    }

    /**
     * Convert a filename string to our preferred storage form.
     *
     * This is the inverse of {@link #getExternalFilename(String pName)}.
     * Deprecated forms are not created.
     *
     * @param filename Filename to be represented
     * @return Filename for storage in a portable manner
     * @since 2.7.2
     */
    static public String getPortableFilename(String filename) {
        return FileUtil.getPortableFilename(filename, false, false);
    }

    /**
     * Convert a filename string to our preferred storage form.
     *
     * This is the inverse of {@link #getExternalFilename(String pName)}.
     * Deprecated forms are not created.
     *
     * This method supports a specific use case concerning profiles and other
     * portable paths that are stored within the User files directory, which
     * will cause the {@link jmri.profile.ProfileManager} to write an incorrect
     * path for the current profile or
     * {@link apps.configurexml.FileLocationPaneXml} to write an incorrect path
     * for the Users file directory. In most cases, the use of
     * {@link #getPortableFilename(java.io.File)} is preferable.
     *
     * @param filename            Filename to be represented
     * @param ignoreUserFilesPath true if paths in the User files path should be
     *                            stored as absolute paths, which is often not
     *                            desirable.
     * @param ignoreProfilePath   true if paths in the profile path should be
     *                            stored as absolute paths, which is often not
     *                            desirable.
     * @return Storage format representation
     * @since 3.5.5
     */
    static public String getPortableFilename(String filename, boolean ignoreUserFilesPath, boolean ignoreProfilePath) {
        if (FileUtil.isPortableFilename(filename)) {
            // if this already contains prefix, run through conversion to normalize
            return getPortableFilename(getExternalFilename(filename), ignoreUserFilesPath, ignoreProfilePath);
        } else {
            // treat as pure filename
            return getPortableFilename(new File(filename), ignoreUserFilesPath, ignoreProfilePath);
        }
    }

    /**
     * Test if the given filename is a portable filename.
     *
     * Note that this method may return a false positive if the filename is a
     * file: URL.
     *
     * @param filename
     * @return true if filename is portable
     */
    static public boolean isPortableFilename(String filename) {
        return (filename.startsWith(PROGRAM)
                || filename.startsWith(HOME)
                || filename.startsWith(PREFERENCES)
                || filename.startsWith(SCRIPTS)
                || filename.startsWith(PROFILE)
                || filename.startsWith(SETTINGS)
                || filename.startsWith(FILE)
                || filename.startsWith(RESOURCE));
    }

    /**
     * Get the user's home directory.
     *
     * @return User's home directory as a String
     */
    static public String getHomePath() {
        return homePath;
    }

    /**
     * Get the user's files directory. If not set by the user, this is the same
     * as the profile path.
     *
     * @see #getProfilePath()
     * @return User's files directory as a String
     */
    static public String getUserFilesPath() {
        return (FileUtil.userFilesPath != null) ? FileUtil.userFilesPath : FileUtil.getProfilePath();
    }

    /**
     * Set the user's files directory.
     *
     * @see #getUserFilesPath()
     * @param path The path to the user's files directory
     */
    static public void setUserFilesPath(String path) {
        if (!path.endsWith(File.separator)) {
            path = path + File.separator;
        }
        FileUtil.userFilesPath = path;
    }

    /**
     * Get the profile directory. If not set, this is the same as the
     * preferences path.
     *
     * @see #getPreferencesPath()
     * @return Profile directory as a String
     */
    static public String getProfilePath() {
        return (FileUtil.profilePath != null) ? FileUtil.profilePath : FileUtil.getPreferencesPath();
    }

    /**
     * Set the profile directory.
     *
     * @see #getProfilePath()
     * @param path The path to the profile directory
     */
    static public void setProfilePath(String path) {
        if (path != null && !path.endsWith(File.separator)) {
            path = path + File.separator;
        }
        FileUtil.profilePath = path;
    }

    /**
     * Get the preferences directory. This directory is set based on the OS and
     * is not normally settable by the user. <ul><li>On Microsoft Windows
     * systems, this is JMRI in the User's home directory.</li> <li>On OS X
     * systems, this is Library/Preferences/JMRI in the User's home
     * directory.</li> <li>On Linux, Solaris, and othe UNIXes, this is .jmri in
     * the User's home directory.</li> <li>This can be overridden with by
     * setting the jmri.prefsdir Java property when starting JMRI.</li></ul> Use
     * {@link #getHomePath()} to get the User's home directory.
     *
     * @see #getHomePath()
     * @return Path to the preferences directory.
     */
    static public String getPreferencesPath() {
        // return jmri.prefsdir property if present
        String jmriPrefsDir = System.getProperty("jmri.prefsdir", ""); // NOI18N
        if (!jmriPrefsDir.isEmpty()) {
            return jmriPrefsDir + File.separator;
        }
        String result;
        switch (SystemType.getType()) {
            case SystemType.MACOSX:
                // Mac OS X
                result = FileUtil.getHomePath() + "Library" + File.separator + "Preferences" + File.separator + "JMRI" + File.separator; // NOI18N
                break;
            case SystemType.LINUX:
            case SystemType.UNIX:
                // Linux, so use an invisible file
                result = FileUtil.getHomePath() + ".jmri" + File.separator; // NOI18N
                break;
            case SystemType.WINDOWS:
            default:
                // Could be Windows, other
                result = FileUtil.getHomePath() + "JMRI" + File.separator; // NOI18N
                break;
        }
        // logging here merely throws warnings since we call this method to setup logging
        // uncomment below to print OS default to console
        // System.out.println("preferencesPath defined as \"" + result + "\" based on os.name=\"" + SystemType.getOSName() + "\"");
        return result;
    }

    /**
     * Get the JMRI program directory.
     *
     * @return JMRI program directory as a String.
     */
    static public String getProgramPath() {
        if (programPath == null) {
            FileUtil.setProgramPath("."); // NOI18N
        }
        return programPath;
    }

    /**
     * Set the JMRI program directory.
     *
     * Convenience method that calls
     * {@link FileUtil#setProgramPath(java.io.File)} with the passed in path.
     *
     * @param path
     */
    static public void setProgramPath(String path) {
        FileUtil.setProgramPath(new File(path));
    }

    /**
     * Set the JMRI program directory.
     *
     * If set, allows JMRI to be loaded from locations other than the directory
     * containing JMRI resources. This must be set very early in the process of
     * loading JMRI (prior to loading any other JMRI code) to be meaningfully
     * used.
     *
     * @param path
     */
    static public void setProgramPath(File path) {
        try {
            programPath = (path).getCanonicalPath() + File.separator;
        } catch (IOException ex) {
            log.error("Unable to get JMRI program directory.", ex);
        }
    }

    /**
     * Get the URL of a portable filename if it can be located using
     * {@link #findURL(java.lang.String)}
     *
     * @param path
     * @return URL of portable or absolute path
     */
    static public URL findExternalFilename(String path) {
        return FileUtil.findURL(FileUtil.getExternalFilename(path));
    }

    /**
     * Search for a file or JAR resource by name and return the
     * {@link java.io.InputStream} for that file. Search order is defined by
     * {@link #findURL(java.lang.String, jmri.util.FileUtil.Location, java.lang.String...) }.
     * No limits are placed on search locations.
     *
     * @param path The relative path of the file or resource
     * @return InputStream or null.
     * @see #findInputStream(java.lang.String, java.lang.String...)
     * @see #findInputStream(java.lang.String, jmri.util.FileUtil.Location,
     * java.lang.String...)
     * @see #findURL(java.lang.String)
     * @see #findURL(java.lang.String, java.lang.String...)
     * @see #findURL(java.lang.String, jmri.util.FileUtil.Location,
     * java.lang.String...)
     */
    static public InputStream findInputStream(String path) {
        return FileUtil.findInputStream(path, new String[]{});
    }

    /**
     * Search for a file or JAR resource by name and return the
     * {@link java.io.InputStream} for that file. Search order is defined by
     * {@link #findURL(java.lang.String, jmri.util.FileUtil.Location, java.lang.String...) }.
     * No limits are placed on search locations.
     *
     * @param path        The relative path of the file or resource
     * @param searchPaths a list of paths to search for the path in
     * @return InputStream or null.
     * @see #findInputStream(java.lang.String)
     * @see #findInputStream(java.lang.String, jmri.util.FileUtil.Location,
     * java.lang.String...)
     */
    static public InputStream findInputStream(String path, @Nonnull String... searchPaths) {
        return FileUtil.findInputStream(path, Location.ALL, searchPaths);
    }

    /**
     * Search for a file or JAR resource by name and return the
     * {@link java.io.InputStream} for that file. Search order is defined by
     * {@link #findURL(java.lang.String, jmri.util.FileUtil.Location, java.lang.String...) }.
     *
     * @param path      The relative path of the file or resource
     * @param locations The type of locations to limit the search to
     * @return InputStream or null.
     * @see #findInputStream(java.lang.String)
     * @see #findInputStream(java.lang.String, jmri.util.FileUtil.Location,
     * java.lang.String...)
     */
    static public InputStream findInputStream(String path, Location locations) {
        return FileUtil.findInputStream(path, locations, new String[]{});
    }

    /**
     * Search for a file or JAR resource by name and return the
     * {@link java.io.InputStream} for that file. Search order is defined by
     * {@link #findURL(java.lang.String, jmri.util.FileUtil.Location, java.lang.String...) }.
     *
     * @param path        The relative path of the file or resource
     * @param locations   The type of locations to limit the search to
     * @param searchPaths a list of paths to search for the path in
     * @return InputStream or null.
     * @see #findInputStream(java.lang.String)
     * @see #findInputStream(java.lang.String, java.lang.String...)
     */
    static public InputStream findInputStream(String path, Location locations, @Nonnull String... searchPaths) {
        URL file = FileUtil.findURL(path, locations, searchPaths);
        if (file != null) {
            try {
                return file.openStream();
            } catch (IOException ex) {
                log.error(ex.getLocalizedMessage(), ex);
            }
        }
        return null;
    }

    /**
     * Get the resources directory within the user's files directory.
     *
     * @return path to [user's file]/resources/
     */
    static public String getUserResourcePath() {
        return FileUtil.getUserFilesPath() + "resources" + File.separator; // NOI18N
    }

    /**
     * Search for a file or JAR resource by name and return the
     * {@link java.net.URL} for that file. Search order is defined by
     * {@link #findURL(java.lang.String, jmri.util.FileUtil.Location, java.lang.String...)}.
     * No limits are placed on search locations.
     *
     * @param path The relative path of the file or resource.
     * @return The URL or null.
     * @see #findURL(java.lang.String, java.lang.String...)
     * @see #findURL(java.lang.String, jmri.util.FileUtil.Location)
     * @see #findURL(java.lang.String, jmri.util.FileUtil.Location,
     * java.lang.String...)
     */
    static public URL findURL(String path) {
        return FileUtil.findURL(path, new String[]{});
    }

    /**
     * Search for a file or JAR resource by name and return the
     * {@link java.net.URL} for that file. Search order is defined by
     * {@link #findURL(java.lang.String, jmri.util.FileUtil.Location, java.lang.String...)}.
     * No limits are placed on search locations.
     *
     * @param path        The relative path of the file or resource
     * @param searchPaths a list of paths to search for the path in
     * @return The URL or null
     * @see #findURL(java.lang.String)
     * @see #findURL(java.lang.String, jmri.util.FileUtil.Location)
     * @see #findURL(java.lang.String, jmri.util.FileUtil.Location,
     * java.lang.String...)
     */
    static public URL findURL(String path, @Nonnull String... searchPaths) {
        return FileUtil.findURL(path, Location.ALL, searchPaths);
    }

    /**
     * Search for a file or JAR resource by name and return the
     * {@link java.net.URL} for that file. Search order is defined by
     * {@link #findURL(java.lang.String, jmri.util.FileUtil.Location, java.lang.String...)}.
     *
     * @param path      The relative path of the file or resource
     * @param locations The types of locations to limit the search to
     * @return The URL or null
     * @see #findURL(java.lang.String)
     * @see #findURL(java.lang.String, java.lang.String...)
     * @see #findURL(java.lang.String, jmri.util.FileUtil.Location,
     * java.lang.String...)
     */
    static public URL findURL(String path, Location locations) {
        return FileUtil.findURL(path, locations, new String[]{});
    }

    /**
     * Search for a file or JAR resource by name and return the
     * {@link java.net.URL} for that file.
     * <p>
     * Search order is:
     * <ol><li>For any provided searchPaths, iterate over the searchPaths by
     * prepending each searchPath to the path and following the following search
     * order:
     * <ol><li>As a {@link java.io.File} in the user preferences directory</li>
     * <li>As a File in the current working directory (usually, but not always
     * the JMRI distribution directory)</li> <li>As a File in the JMRI
     * distribution directory</li> <li>As a resource in jmri.jar</li></ol></li>
     * <li>If the file or resource has not been found in the searchPaths, search
     * in the four locations listed without prepending any path</li></ol>
     * <p>
     * The <code>locations</code> parameter limits the above logic by limiting
     * the location searched.
     * <ol><li>{@link Location#ALL} will not place any limits on the
     * search</li><li>{@link Location#NONE} effectively requires that
     * <code>path</code> be a portable
     * pathname</li><li>{@link Location#INSTALLED} limits the search to the
     * {@link #PROGRAM} directory and JARs in the class
     * path</li><li>{@link Location#USER} limits the search to the
     * {@link #PROFILE} directory</li></ol>
     *
     * @param path        The relative path of the file or resource
     * @param locations   The types of locations to limit the search to
     * @param searchPaths a list of paths to search for the path in
     * @return The URL or null
     * @see #findURL(java.lang.String)
     * @see #findURL(java.lang.String, jmri.util.FileUtil.Location)
     * @see #findURL(java.lang.String, java.lang.String...)
     */
    static public URL findURL(String path, Location locations, @Nonnull String... searchPaths) {
        if (log.isDebugEnabled()) { // avoid the Arrays.toString call unless debugging
            log.debug("Attempting to find {} in {}", path, Arrays.toString(searchPaths));
        }
        if (FileUtil.isPortableFilename(path)) {
            return FileUtil.findExternalFilename(path);
        }
        URL resource = null;
        for (String searchPath : searchPaths) {
            resource = FileUtil.findURL(searchPath + File.separator + path);
            if (resource != null) {
                return resource;
            }
        }
        try {
            File file;
            if (locations == Location.ALL || locations == Location.USER) {
                // attempt to return path from preferences directory
                file = new File(FileUtil.getUserFilesPath() + path);
                if (file.exists()) {
                    return file.toURI().toURL();
                }
            }
            if (locations == Location.ALL || locations == Location.INSTALLED) {
                // attempt to return path from current working directory
                file = new File(path);
                if (file.exists()) {
                    return file.toURI().toURL();
                }
                // attempt to return path from JMRI distribution directory
                file = new File(FileUtil.getProgramPath() + path);
                if (file.exists()) {
                    return file.toURI().toURL();
                }
            }
        } catch (MalformedURLException ex) {
            log.warn("Unable to get URL for {}", path, ex);
            return null;
        }
        if (locations == Location.ALL || locations == Location.INSTALLED) {
            // return path if in jmri.jar or null
            // The ClassLoader needs paths to start with /
            path = path.replace(File.separatorChar, '/');
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            resource = FileUtil.class.getClassLoader().getResource(path);
            if (resource == null) {
                resource = FileUtil.class.getResource(path);
                if (resource == null) {
                    log.debug("{} not found in classpath", path);
                }
            }
        }
        return resource;
    }

    /**
     * Return the {@link java.net.URI} for a given URL
     *
     * @param url
     * @return a URI or null if the conversion would have caused a
     *         {@link java.net.URISyntaxException}
     */
    static public URI urlToURI(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException ex) {
            log.error("Unable to get URI from URL", ex);
            return null;
        }
    }

    /**
     * Return the {@link java.net.URL} for a given {@link java.io.File}. This
     * method catches a {@link java.net.MalformedURLException} and returns null
     * in its place, since we really do not expect a File object to ever give a
     * malformed URL. This method exists solely so implementing classes do not
     * need to catch that exception.
     *
     * @param file The File to convert.
     * @return a URL or null if the conversion would have caused a
     *         MalformedURLException
     */
    static public URL fileToURL(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException ex) {
            log.error("Unable to get URL from file", ex);
            return null;
        }
    }

    /**
     * Get the JMRI distribution jar file.
     *
     * @return a {@link java.util.jar.JarFile} pointing to jmri.jar or null
     */
    static public JarFile jmriJarFile() {
        if (jarPath == null) {
            CodeSource sc = FileUtil.class.getProtectionDomain().getCodeSource();
            if (sc != null) {
                jarPath = sc.getLocation().toString();
                // 9 = length of jar:file:
                jarPath = jarPath.substring(9, jarPath.lastIndexOf("!"));
                log.debug("jmri.jar path is {}", jarPath);
            }
            if (jarPath == null) {
                log.error("Unable to locate jmri.jar");
                return null;
            }
        }
        try {
            return new JarFile(jarPath);
        } catch (IOException ex) {
            log.error("Unable to open jmri.jar", ex);
            return null;
        }
    }

    /**
     * Log all paths at the INFO level.
     */
    static public void logFilePaths() {
        log.info("File path {} is {}", FileUtil.PROGRAM, FileUtil.getProgramPath());
        log.info("File path {} is {}", FileUtil.PREFERENCES, FileUtil.getUserFilesPath());
        log.info("File path {} is {}", FileUtil.PROFILE, FileUtil.getProfilePath());
        log.info("File path {} is {}", FileUtil.SETTINGS, FileUtil.getPreferencesPath());
        log.info("File path {} is {}", FileUtil.HOME, FileUtil.getHomePath());
        log.info("File path {} is {}", FileUtil.SCRIPTS, FileUtil.getScriptsPath());
    }

    /**
     * Get the path to the scripts directory.
     *
     * @return the scriptsPath
     */
    public static String getScriptsPath() {
        if (scriptsPath != null) {
            return scriptsPath;
        }
        // scriptsPath not set by user, return default if it exists
        File file = new File(FileUtil.getProgramPath() + File.separator + "jython" + File.separator); // NOI18N
        if (file.exists()) {
            return file.getPath();
        }
        // if default does not exist, return user's files directory
        return FileUtil.getUserFilesPath();
    }

    /**
     * Set the path to python scripts.
     *
     * @param path the scriptsPath to set
     */
    public static void setScriptsPath(String path) {
        if (!path.endsWith(File.separator)) {
            path = path + File.separator;
        }
        scriptsPath = path;
    }

    /**
     * Read a text file into a String.
     *
     * @param file The text file.
     * @return The contents of the file.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static String readFile(File file) throws IOException {
        return FileUtil.readURL(FileUtil.fileToURL(file));
    }

    /**
     * Read a text URL into a String. Would be significantly simpler with Java
     * 7.
     *
     * @param url The text URL.
     * @return The contents of the file.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static String readURL(URL url) throws IOException {
        try {
            InputStreamReader in = new InputStreamReader(url.openStream());
            BufferedReader reader = new BufferedReader(in);
            StringBuilder builder = new StringBuilder();
            String aux;
            while ((aux = reader.readLine()) != null) {
                builder.append(aux);
            }
            reader.close();
            in.close();
            return builder.toString();
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Replaces most non-alphanumeric characters in name with an underscore.
     *
     * @param name The filename to be sanitized.
     * @return The sanitized filename.
     */
    public static String sanitizeFilename(String name) {
        name = name.trim().replaceAll(" ", "_").replaceAll("[.]+", ".");
        StringBuilder filename = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c == '.' || Character.isJavaIdentifierPart(c)) {
                filename.append(c);
            }
        }
        return filename.toString();
    }

    /**
     * Create a directory if required. Any parent directories will also be
     * created.
     *
     * @param path
     */
    public static void createDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            log.warn("Creating directory: {}", path);
            if (!dir.mkdirs()) {
                log.error("Failed to create directory: {}", path);
            }
        }
    }

    /**
     * Recursively delete a path. Not needed in Java 1.7.
     *
     * @param path
     * @return true if path was deleted, false otherwise
     */
    public static boolean delete(File path) {
        if (path.isDirectory()) {
            for (File file : path.listFiles()) {
                FileUtil.delete(file);
            }
        }
        return path.delete();
    }

    /**
     * Copy a file. Not needed in Java 1.7.
     *
     * @param source
     * @param dest   must be the file, not the destination directory.
     * @throws IOException
     */
    public static void copy(File source, File dest) throws IOException {
        if (!source.exists()) {
            return;
        }
        if (!dest.exists()) {
            if (source.isDirectory()) {
                boolean ok = dest.mkdirs();
                if (!ok) {
                    throw new IOException("Could not use mkdirs to create destination directory");
                }
            } else {
                boolean ok = dest.createNewFile();
                if (!ok) {
                    throw new IOException("Could not create destination file");
                }
            }
        }
        if (source.isDirectory()) {
            for (File file : source.listFiles()) {
                FileUtil.copy(file, new File(dest, file.getName()));
            }
        } else {
            FileInputStream sourceIS = null;
            FileChannel sourceChannel = null;
            FileOutputStream destIS = null;
            FileChannel destChannel = null;
            try {
                sourceIS = new FileInputStream(source);
                sourceChannel = sourceIS.getChannel();
                destIS = new FileOutputStream(dest);
                destChannel = destIS.getChannel();
                if (destChannel != null && sourceChannel != null) {
                    destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
                }
            } catch (IOException ex) {
                throw ex;
            } finally {
                try {
                    if (sourceChannel != null) {
                        sourceChannel.close();
                    }
                    if (destChannel != null) {
                        destChannel.close();
                    }
                    if (sourceIS != null) {
                        sourceIS.close();
                    }
                    if (destIS != null) {
                        destIS.close();
                    }
                } catch (IOException ex) {
                    throw ex;
                }
            }
        }
    }

    /**
     * Simple helper method to just append a text string to the end of the given
     * filename. The file will be created if it does not exist.
     *
     * @param file File to append text to
     * @param text Text to append
     * @throws java.io.IOException if file cannot be written to
     */
    public static void appendTextToFile(File file, String text) throws IOException {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8")); // NOI18N
        pw.println(text);
        pw.close();
    }

    /* Private default constructor to ensure it's not documented. */
    private FileUtil() {
    }
}
