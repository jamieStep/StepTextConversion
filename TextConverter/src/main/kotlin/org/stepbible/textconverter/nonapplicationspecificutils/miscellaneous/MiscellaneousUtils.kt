package org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_256
import org.jasypt.util.text.BasicTextEncryptor
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepStringUtils.quotify
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionBase
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.net.URL
import java.net.URLDecoder
import java.util.jar.Manifest
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance


/******************************************************************************/
/**
 * Miscellaneous utilities.
 *
 * @author ARA "Jamie" Jamieson
 */

object MiscellaneousUtils
 {
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

   /***************************************************************************/
   /**
    * Runs a given function, capturing stderr and returning anything sent to
    * stderr as a string.
    *
    * @param fn Function to run.
    * @return Anything sent to stderr.
    */

  fun capturingStderr (fn: () -> Any): String
  {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val printStream = PrintStream(byteArrayOutputStream)
    val save = System.err
    System.setErr(printStream)

    fn()

    System.setErr(save)
    return byteArrayOutputStream.toString()
  }


  /***************************************************************************/
  /**
   * Checks whether a list of items is arranged in strictly increasing order.
   *
   * @param data
   * @return The first element such that the item before it is in the wrong
   *   place, or null if all in order.
   */

  fun <T: Comparable<T>> checkInStrictlyAscendingOrder (data: List<T>): T?
  {
    for (i in 1 ..< data.size)
      if (data[i] <= data[i - 1]) return data[i]
    return null
  }


  /****************************************************************************/
  /**
   * Converts 1, 2, ... 26, 27, 28 ... to a, b, ... z, aa, ab ...
   *
   * @param n Number to be converted
   * @param lowChar First character in range of available characters.
   * @param highChar Last character in range of available characters.
   * @return Character string.
   */

  fun convertNumberToRepeatingString (n: Int, lowChar: Char, highChar: Char): String
  {
    val lowCharCode = lowChar.code
    val nElts = highChar.code - lowCharCode + 1
    var x = n
    var res = ""
    while (x > 0)
    {
      res = (lowCharCode + (x - 1) % nElts).toChar().toString() + res
      x = (x - 1) / nElts
    }

    return res
  }


  /****************************************************************************/
  /**
   * Does what it says on the tin.
   *
   * @param text Text to be copied to clipboard.
   */

  fun copyTextToClipboard (text: String)
  {
    val stringSelection = StringSelection(text)
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(stringSelection, null)
  }


  /****************************************************************************/
  /**
   * Converts eg a-z to 1-26, aa to 27, ab to 28, etc.
   *
   * @param text: String to be converted.
   * @param lowerBound Lowest character in range.
   * @param upperBound Highest character in range.
   * @return Converted value.
   */

  fun convertRepeatingStringToNumber (text: String, lowerBound: Char, upperBound: Char): Int
  {
    val lowerBoundCode = lowerBound.code
    val range = upperBound.code - lowerBoundCode + 1
    var res = 0
    text.forEach {
        val n = it.code - lowerBoundCode + 1
        if (n < 1 || n > range) throw StepExceptionBase("convertRepeatingStringToNumber: Bad text: $text")
        res = range * res + n
    }

    return res
  }


  /****************************************************************************/
  /**
   * Creates an instance of a class given the name of the class as a string.
   * This assumes that there is a constructor which takes no arguments.  Note
   * that the name must be fully qualified with package details.  You can obtain
   * the package name using
   *
   *   val packageName = object {}.javaClass.`package`.name
   *
   * within code in that package.
   *
   * @param className
   * @return Instance
   */

  fun createInstanceByClassName (className: String): Any
  {
    val klass = Class.forName(className).kotlin
    return klass.createInstance()
  }


  /****************************************************************************/
  /**
   * A do-nothing method for use eg in if statements where we want to do
   * nothing, but want to make it clear that this was a deliberate choice.
   */

  inline fun doNothing (@Suppress("UNUSED_PARAMETER") vararg x: Any) {}


  /****************************************************************************/
  /**
   * Generates the encrypted password needed by JSword to decrypt things.  For
   * more information about encryption, refer to TextConverterProcessorOsisToSword.
   *
   * @param osis2modPassword The password which was fed to osis2mod.
   * @param stepObfuscationKey The key used when obfuscating the password.
   * @return Obfuscated password.
   */

  fun generateStepEncryptionKey (osis2modPassword: String, stepObfuscationKey: String): String
  {
    val bte = BasicTextEncryptor()
    bte.setPassword(stepObfuscationKey)
    return bte.encrypt(osis2modPassword)
  }


  /****************************************************************************/
  /**
    * Generates a string nChars in length made up of a random collection of lower
    * case letters, upper case letters, digits and a set of special characters.
    *
    * @param nChars Number of characters required.
    */

  fun generateRandomString (nChars: Int): String
  {
    return List(nChars) { C_generateRandomString_Chars.random() }.joinToString("")
  }

  private val C_generateRandomString_Chars : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('!', '$', '%', '_', '#', '@')


  /***************************************************************************/
  /**
  * Somewhat implausible code which returns the name of the current JAR file.
  *
  * @return JAR file name.
  */

  fun getJarFileName(): String
  {
    return try {
      val jarFileUrl = this::class.java.protectionDomain?.codeSource?.location ?: return ""
      val decodedPath = URLDecoder.decode(jarFileUrl.path, "UTF-8")
      val res = File(decodedPath).name
      if ("main" == res) throw StepExceptionBase("")
      res
    }
    catch (_: Exception)
    {
      "IDE_PSEUDO_JAR_NAME"
    }
  }


  /***************************************************************************/
  /**
  * Gets the version number from the JAR manifest.  (This is available only
  * if we *are* actually running the JAR.  Otherwise I simply give back a
  * dummy value.)
  *
  * @return JAR version as string.
  */

  fun getJarVersion () = m_JarVersion


  /***************************************************************************/
  /**
  * Returns the name of the package containing a give class.
  *
  * @return Class whose package is required.  Pass as eg MyClass::class.
  */

  fun getPackageName (kClass: KClass<*>): String = kClass.java.getPackage().name


  /****************************************************************************/
  /**
   * Calculates a SHA256 digest for a file.
   *
   * @param filePath
   * @return Digest
   */

  fun getSha256 (filePath: String): String = DigestUtils(SHA_256).digestAsHex(File(filePath).readBytes())


  /****************************************************************************/
  /**
  * Gets all of the subtypes of a given class or interface.
  *
  * @param baseClass Base class or interface.
  * @return List of subtypes.
  */

  fun getSubtypes (baseClass: Class<*>): List<Class<*>>
  {
    val reflections = getReflections(baseClass.getPackage().name, Scanners.SubTypes)
    return reflections!!.getSubTypesOf(baseClass).toList()
  }


  /****************************************************************************/
  /**
    * Runs a (possibly empty) list of external commands.
    *
    * There are some issues to be aware of here.  First, don't be tempted to
    * pass a command which includes '>' to redirect output to a file --
    * ProcessBuilder can't cope with that.  To get round it, you can use the
    * redirectFile parameter here. If you do that, however, I have at least
    * some anecdotal evidence that not all of the output ends up in the file.
    * (This occurred when I was sending both stderr and stdout to the same file,
    * for which reason I presently redirect stderr only, but this may not be
    * acceptable.)
    *
    * @param prompt Written to System.out if non-null.
    * @param command Command to be run.
    * @param workingDirectory Directory to move to before running command.
    * @param errorFilePath If non-null, output is redirected to here
    * @return Return code from process which is being run.
    * @throws StepExceptionBase Any exception noticed while attempting to rub the commands.
    */

  fun runCommand (prompt: String?, command: List<String>, errorFilePath: String? = null, workingDirectory: String? = null): Int
  {
    if (null != prompt) Dbg.reportProgress(prompt + command.joinToString(" "){ quotify(it) })
    val pb = ProcessBuilder(command)
    if (null != errorFilePath) pb.redirectError(File(errorFilePath))
    if (null != workingDirectory) pb.directory(File(workingDirectory))
    val res = pb.start().waitFor()
    Dbg.reportProgress("- External command completed")
    return res
  }


  /***************************************************************************/
  /**
  * Checks if we are running from the IDE.
  *
  * @return True if running from IDE.
  */

  fun runningFromIde () = getJarVersion().startsWith("IDE")


  /***************************************************************************/
  /**
  * Checks if we are running from a JAR.
  *
  * @return True if running from a JAR.
  */

  fun runningFromJar () = !runningFromIde()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /***************************************************************************/
  /**
  * Gets the version number from the JAR manifest.  (This is available only
  * if we *are* actually running the JAR.  Otherwise I simply give back a
  * dummy value.)
  *
  * @return JAR version as string.
  */

  private fun getJarVersionInternal (): String
  {
    /**************************************************************************/
    /* I don't pretend to have investigated what all of the following does. */

    return try {
      val clazz: Class<*> = this::class.java
      val className = clazz.simpleName + ".class"
      val classPath = clazz.getResource(className)?.toString() ?: ""
      if (!classPath.startsWith("jar")) throw StepExceptionBase("")

      val manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF"
      val manifest = Manifest(URL(manifestPath).openStream())
      val attr = manifest.mainAttributes
      val value: String = attr.getValue("Implementation-Version")
      value
    }
    catch (_: Exception)
    {
      "IDE_VERSION"
    }
  }


  /****************************************************************************/
  /**
  * Uses the Reflections package.  Unfortunately this issues error messages
  * to do with some logging library, so to avoid having these appear in the
  * output, I need to redirect stderr temporarily.
  *
  * @param packageName
  * @param scanners
  * @return Reflection information.
  */

  private fun getReflections (packageName: String, scanners: Scanners): Reflections?
  {
    val savedErr = System.err
    System.setErr(PrintStream(object : OutputStream() { override fun write(b: Int) {} }))

    try {
      return Reflections(packageName, Scanners.SubTypes)
    }
    catch (e: Exception)
    {
      println(e.message)
    }
    finally {
      System.setErr(savedErr)
    }

    return null
  }


  /****************************************************************************/
  private val m_JarVersion: String by lazy { getJarVersionInternal() }
}
