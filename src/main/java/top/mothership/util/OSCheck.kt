package top.mothership.util

import top.mothership.util.OSCheck.OSType.*
import java.util.Locale

/**
 * helper class to check the operating system this Java VM runs in
 *
 * please keep the notes below as a pseudo-license
 *
 * http://stackoverflow.com/questions/228477/how-do-i-programmatically-determine-operating-system-in-java
 * compare to http://svn.terracotta.org/svn/tc/dso/tags/2.6.4/code/base/common/src/com/tc/util/runtime/Os.java
 * http://www.docjar.com/html/api/org/apache/commons/lang/SystemUtils.java.html
 */

object OSCheck {
    // cached result of OS detection
    private var detectedOS: OSType? = null

    /**
     * detect the operating system from the os.name System property and cache
     * the result
     *
     * @returns - the operating system detected
     */
    fun getOperatingSystemType(): OSType {
        if (detectedOS == null) {
            val os: String = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
            detectedOS = if (os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0) {
                MacOS
            } else if (os.indexOf("win") >= 0) {
                Windows
            } else if (os.indexOf("nux") >= 0) {
                Linux
            } else {
                Other
            }
        }
        return detectedOS!!
    }

    /**
     * types of Operating Systems
     */
    enum class OSType {
        Windows, MacOS, Linux, Other
    }
}
