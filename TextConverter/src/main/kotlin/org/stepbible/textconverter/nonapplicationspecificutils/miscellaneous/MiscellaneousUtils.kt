package org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_256
import org.jasypt.util.text.BasicTextEncryptor
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.stepbible.textconverter.MainProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepStringUtils.quotify
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepStringUtils.quotifyIfContainsSpaces
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionNotReallyAnException
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.net.URL
import java.net.URLDecoder
import java.util.jar.Manifest
import java.util.stream.Collectors
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance


/******************************************************************************/
/**
 * Miscellaneous utilities.
 *
 * @author ARA "Jamie" Jamieson
 */

object MiscellaneousUtils: ObjectInterface
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
        if (n < 1 || n > range) throw StepExceptionWithStackTraceAbandonRun("convertRepeatingStringToNumber: Bad text: $text")
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

  fun doNothing (@Suppress("UNUSED_PARAMETER") vararg x: Any) {}


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
      if ("main" == res) throw StepExceptionNotReallyAnException("")
      res
    }
    catch (_: Exception)
    {
      "TextConverter"
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
  * Returns the name of the package containing a given class.
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
  *
  * @param forcePackageName By default, subtypes are located from the package
  *   in which the base class resides.  You can give forcePackageName to use
  *   a different package name.  Experience suggests that if you give this as
  *   the root package, then all subpackages are searched.
  *
  * @return List of subtypes.
  */

  fun getSubtypes (baseClass: Class<*>, forcePackageName: String? = null): List<Class<*>>
  {
    val reflections = getReflections(forcePackageName ?: baseClass.getPackage().name, Scanners.SubTypes)
    return reflections!!.getSubTypesOf(baseClass).toList()
  }


  /****************************************************************************/
  /**
  * Initialises all objects.  Or more accurately, initialises all objects which
  * have been defined as implementing ObjectInterface, so it behoves me to
  * remember to do this.
  *
  * The rationale for this method is that when using parallel processing, I'm
  * not quite sure what happens if two different threads both try to access an
  * object with extensive initialisation processing at the same time.  init
  * blocks are definitely not thread-safe, so you could certainly imagine there
  * being problems.
  *
  * The processing here assumes that the order in which things are initialised
  * is irrelevant.
  */

  fun initialiseAllObjectsBasedOnObjectInterfaceInheritance ()
  {
    val packageName = getPackageName(MainProcessor::class)
    val objects = getSubtypes(ObjectInterface::class.java, packageName)
    objects.forEach {
      //Rpt.report(1, "Initialising ${it.name}.")
      it.getField("INSTANCE").get(null)
    }
  }


  /****************************************************************************/
  /**
  * Initialises all objects, using Reflection to identify them, but, unlike
  * [initialiseAllObjectsBasedOnObjectInterfaceInheritance], not relying upon
  * me remembering to make the ones of interest inherit from ObjectInterface.
  * The processing here assumes that the order in which things are initialised
  * is irrelevant.
  *
  * <span class='important'>This works when called from the IDE.  However, it
  * does not work when run direct from the command line.  There was nothing
  * obviously relevant on the internet to help, so I have given up using this
  * method.  However, it is definitely superior to other objects, and so I have
  * retained it in case I ever want to go back to it, and have the oomph to be
  * bothered trying to fix things.</span>
  */

  fun initialiseAllObjectsBasedOnReflection ()
  {
    val packageName = getPackageName(MainProcessor::class)
    val r = Reflections(ConfigurationBuilder().setUrls(ClasspathHelper.forPackage(packageName)).setScanners(Scanners.SubTypes.filterResultsBy {c -> true}))
    val x = r.getSubTypesOf(Object::class.java).stream().collect(Collectors.toSet())
//    val reflections = Reflections(getPackageName(MainProcessor::class), SubTypesScanner(false))
//    val x = reflections.getSubTypesOf(Object::class.java).stream().collect(Collectors.toSet())
    x.forEach {
      try
      {
        if ("$" !in it.name)
        {
          val isObject = "INSTANCE" in it.getFields().toSet().map { it.name }
          if (isObject && "$" !in it.name)
          {
            it.getField("INSTANCE").get(null)
            //println("Initialised ${it.name}.")
          }
        }
      }
      catch (e: Exception)
      {
        System.err.println("Failed to initialise ${it.name}.")
        throw StepExceptionWithStackTraceAbandonRun(e)
      }
    }
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
    */

  fun runCommand (prompt: String?, command: List<String>, errorFilePath: String? = null, workingDirectory: String? = null): Int
  {
    if (null != prompt) Rpt.report(level = 1, prompt + command.map { quotifyIfContainsSpaces(it) }. joinToString(" "))
    val pb = ProcessBuilder(command)
    if (null != errorFilePath) pb.redirectError(File(errorFilePath))
    if (null != workingDirectory) pb.directory(File(workingDirectory))
    val res = pb.start().waitFor()
    Rpt.report(level = 1, "External command completed")
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
      if (!classPath.startsWith("jar")) throw StepExceptionNotReallyAnException("")

      val manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF"
      val manifest = Manifest(URL(manifestPath).openStream())
      val attr = manifest.mainAttributes
      val value: String = attr.getValue("Implementation-Version") + " (" + attr.getValue("Latest-Update-Reason") + ")"
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
