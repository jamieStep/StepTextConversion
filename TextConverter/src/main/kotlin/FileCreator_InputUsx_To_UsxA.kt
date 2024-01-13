package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.TextStructureUsxForUseWhenConvertingToEnhancedUsx
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.runCommand
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.miscellaneous.Zip
import org.stepbible.textconverter.support.stepexception.StepException
import org.w3c.dom.Document
import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.nio.file.Paths
import kotlin.collections.ArrayList


/******************************************************************************/
/**
 * Handles preprocessing.
 *
 *
 *
 *
 * ## Introduction
 *
 * USX being a rather complex standard (and at the same time lacking facilities
 * which translators commonly seem to require), it quite often happens that
 * we receive texts which are incorrect in some manner (or at the very least,
 * which do not fit with our processing).
 *
 * There are various ways of doing this.  You can create an external program
 * which runs over the input files and creates revised versions of them; or
 * you can specify, via a configuration parameter, an XSLT fragment; or you can
 * create a JAR file which conforms to a particular API, and this will be called
 * from within the general flow of control.
 *
 * The latter two options are probably preferred because they do not involve
 * creating additional files -- they simply apply changes to the Document object
 * which is currently being processed.  However, there may be cases where it is
 * simpler to create an external pre-processor, and this present class handles
 * the invocation of that.
 *
 * This class handles this pre-processing.  If no pre-processing is required,
 * then it simply copies all input files to the UsxA folder (except that if
 * the UsxA folder is already fully populated with exact copies of the input
 * USX files, it doesn't bother).  I'd prefer not to do this copy, but it
 * gives a measure of uniformity for later processing; without it, there are
 * just too many potentially fiddly situations to take on board.
 * 
 *
 * 
 * 
 * 
 * ## External preprocessing
 *
 * In standalone preprocessing, you create a runnable JAR, or any other
 * program (.jar, .js. .exe, .py, .bat etc) which is capable of taking
 * arguments from the command line.
 *
 * You inform the converter of the requirement to run the preprocessor by
 * setting the configuration parameter stepRunnablePreprocessorFilePath to give
 * the path to the program.
 *
 * Particularly with scripting languages, you may also need to give details of
 * how to run the interpreter, to which end you can use the configuration
 * parameter stepRunnablePreprocessorCommandPrefix.
 *
 * Thus, for example, if you have a Perl script C:\Users\Me\Documents\perl.pl,
 * you give stepRunnablePreprocessorFilePath as 'C:\Users\Me\Documents\perl.pl'
 * and stepRunnablePreprocessorCommandPrefix as 'perl', and the converter will
 * run this as 'perl "C:\Users\Me\Documents\perl.pl" <args>'.
 *
 * There is no need to use stepRunnablePreprocessorCommandPrefix with .jar
 * files, .py files or .js files, unless you need to do something odd: the
 * converter will assume 'java -jar', 'python' or 'node.js' respectively.
 *
 * stepRunnablePreprocessorFilePath works in much the same way as $include
 * configuration statements, so you can give the path as relative to the
 * root folder of the text (using $root), relative to the metadata folder,
 * etc.
 *
 * Including arguments, a command line might look like:
 *
 *   C:\Users\Me\Documents\preprocessor.exe <outputFolderPath> <inputFolderPath> [<fileDetails>]
 *
 * The arguments are as follows:
 *
 * - inputFolderPath: The folder in which the raw USX files are stored.  The
 *     processing should run over the files listed in <fileDetails> and
 *     create revised versions as necessary -- see next bullet point.
 *
 * - outputFolderPath: The folder in which to store output files (all of which
 *     must be valid USX).  There is no need to store a file in this folder
 *     unless the processing has changed it (but no harm in storing it
 *     regardless).  Files should be stored under the same name as the
 *     corresponding input file.  The output folder will be empty at the time
 *     the preprocessing is invoked, so there is no need to clear it before
 *     starting work.
 *
 * - fileDetails: This lists the files to be processed (on some runs we may not
 *     particularly want to process *all* files, because we may, for instance,
 *     be debugging the processing for a particular file, and may not want to
 *     bother handling all of the others.  This is a '||'-separated list of
 *     entries.  Each entry is of the form eg gen::01gen.usx where the first
 *     portion is the lowercase book abbreviation, and the remainder is the name
 *     of the file, relative to the input folder.
 *
 *
 * It is unlikely that you will want to create in the output folder a file for
 * which there is no input counterpart, but you can do so if there is a need
 * for it.  In his case you can give it any name you like, but it must have
 * the .usx extension.
 *
 * When carrying out the actual conversion, the converter will pick up
 * preprocessed versions of files in preference to raw versions where both
 * exist.
 *
 * There are several advantages to using a standalone preprocessor:
 *
 * - In general it is easy to debug, because you can indeed run it standalone.
 *
 * - The converter validates its own output against the input which it used.
 *   With standalone preprocessing, this is easy, because it is as though
 *   the converter had been passed the revised input in the first place --
 *   there is no need to worry about differences between the original and
 *   revised text.
 *
 *
 * There are downsides, however, of which probably the main one is the
 * increased file io in creating revised copies of the input files.
 *
 * @author ARA Jamieson
 */

object FileCreator_InputUsx_To_UsxA: ProcessingChainElement
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = ""
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor) {}
  override fun pre () {} // We don't delete anything in advance, because if it turns out there is no pre-processing, we want to retain any files already in UsxA.
  override fun process () = doIt()
  override fun takesInputFrom () = Pair(FileLocations.getInputUsxFolderPath(), FileLocations.getFileExtensionForUsx())





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  private fun doIt ()
  {
    /*************************************************************************/
    if (!ConfigData["stepRunnablePreprocessorFilePath"].isNullOrBlank())
    {
      StepFileUtils.deleteFolder(FileLocations.getInternalUsxAFolderPath())
      StepFileUtils.createFolderStructure(FileLocations.getInternalUsxAFolderPath())
      Dbg.reportProgress("Pre-processing to USX")
      TextStructureUsxForUseWhenConvertingToEnhancedUsx // Force initialisation.
      applyRunnablePreprocessor()
    }
    else if (initialiseCallablePreprocessor())
    {
      StepFileUtils.deleteFolder(FileLocations.getInternalUsxAFolderPath())
      StepFileUtils.createFolderStructure(FileLocations.getInternalUsxAFolderPath())
      Dbg.reportProgress("Pre-processing to USX")
      TextStructureUsxForUseWhenConvertingToEnhancedUsx.iterateOverSelectedFiles(::applyCallablePreprocessor)
    }
    else if (initialiseXsltStylesheets())
    {
      StepFileUtils.deleteFolder(FileLocations.getInternalUsxAFolderPath())
      StepFileUtils.createFolderStructure(FileLocations.getInternalUsxAFolderPath())
      Dbg.reportProgress("Pre-processing to USX")
      TextStructureUsxForUseWhenConvertingToEnhancedUsx.iterateOverSelectedFiles(::applyXslt)
    }
    else
      copyFilesIfDifferent()


    /**************************************************************************/
    fillInMissingFiles()
  }


  /****************************************************************************/
  private fun applyCallablePreprocessor (bookName: String, filePath: String)
  {
    val document = Dom.getDocument(filePath)
    Dbg.reportProgress("  Preprocessing $bookName")
    val messages = m_MethodPreprocess!!.invoke(m_PreprocessorInstance!!, document) as List<String>?
    processMessages(messages)
    val outFilePath = Paths.get(FileLocations.getInternalUsxAFolderPath(), Paths.get(filePath).fileName.toString()).toString()
    Dom.outputDomAsXml(document, outFilePath, null)
  }


  /****************************************************************************/
  private fun applyXslt (bookName: String, filePath: String)
  {
    val stylesheetContent = m_Stylesheets[bookName.lowercase()] ?: m_Stylesheets[""] ?: return

    val document = Dom.getDocument(filePath)

    if ("xsl:stylesheet" in stylesheetContent)
      Dom.applyStylesheet(document, stylesheetContent)
    else
      Dom.applyBasicStylesheet(document, stylesheetContent)

    val outFilePath = Paths.get(FileLocations.getInternalUsxAFolderPath(), Paths.get(filePath).fileName.toString()).toString()
    Dom.outputDomAsXml(document, outFilePath, null)
  }


  /****************************************************************************/
  private fun initialiseCallablePreprocessor (): Boolean
  {
    /**************************************************************************/
    var jarPath = ConfigData["stepCallablePreprocessorFilePath"] ?: return false
    if (jarPath.isEmpty()) return false
    jarPath = FileLocations.getInputPath(jarPath, null)



    /**************************************************************************/
    if (!File(jarPath).exists()) throw StepException("Couldn't find callable JAR pre-processor: $jarPath.")



    /**************************************************************************/
    try
    {
      val jarUrl = File(jarPath).toURI().toURL()
      val classLoader = URLClassLoader(arrayOf(jarUrl), Thread.currentThread().contextClassLoader)
      val loadedClass = classLoader.loadClass("org.stepbible.preprocessor.Preprocessor")
      m_PreprocessorInstance = loadedClass.getDeclaredConstructor().newInstance()
      val classToLoad = Class.forName("org.stepbible.preprocessor.Preprocessor", true, classLoader)
      m_MethodPreprocess = classToLoad.getDeclaredMethod("preprocess", Document::class.java)
      try { m_MethodGetTextForValidation = classToLoad.getDeclaredMethod("getTextForValidation", String::class.java) } catch (_: Exception) {}
      return true
    }
    catch (e: Exception)
    {
      throw StepException("Failed to initialise callable preprocessor: ${e.message}")
    }
  }


  /****************************************************************************/
  private fun initialiseXsltStylesheets (): Boolean
  {
    var res = false
    ConfigData.getKeys()
      .filter { it.lowercase().startsWith("stepxsltstylesheet") }
      .forEach {
        val value = ConfigData[it]
        if (!value.isNullOrBlank())
        {
          res = true

          if ("_" in it)
            m_Stylesheets[it.split("_")[1].lowercase()] = value
          else
            m_Stylesheets[""] = value
        }
      }

    return res
  }


  /****************************************************************************/
  private fun processMessages (messages: List<String>?)
  {
    if (null == messages) return

    messages.forEach {
      val ix = it.indexOf(':')
      val text = it.substring(ix + 2)
      when (it.substring(0, ix))
      {
        "ERROR"       -> Logger.error(text)
        "WARNING"     -> Logger.warning(text)
        "INFORMATION" -> Logger.info(text)
      }
    }
  }


  /****************************************************************************/
  /**
  * Applies a standalone preprocessor where this is appropriate.  For more
  * details about standalone preprocessors, see the head-of-class comments.
  *
  * @return True if anything was done.
  */

  private fun applyRunnablePreprocessor ()
  {
    /**************************************************************************/
    val inFolder = FileLocations.getInputUsxFolderPath()
    val outFolder = FileLocations.getInternalUsxAFolderPath()



    /**************************************************************************/
    val preprocessorPrefix = ConfigData["stepRunnablePreprocessorCommandPrefix"] ?: ""
    var preprocessorFilePath = ConfigData["stepRunnablePreprocessorFilePath"] ?: ""
    if (preprocessorFilePath.isEmpty()) return
    preprocessorFilePath = FileLocations.getInputPath(preprocessorFilePath, null) // Expand out things like $root etc.



   /**************************************************************************/
    val command: MutableList<String> = ArrayList()
    if (preprocessorPrefix.isNotEmpty())
      command.add(preprocessorPrefix.trim())
    else when (File(preprocessorFilePath).extension.lowercase())
    {
      "js" ->
      {
        command.add("node")
        command.add("\"$preprocessorFilePath\"")
      }


      "py" ->
      {
        command.add("python")
        command.add("\"$preprocessorFilePath\"")
      }


      "jar" ->
      {
        command.add("java"); command.add("-jar")
        val mainClass = Zip.getInputStream(preprocessorFilePath, "META-INF/MANIFEST.MF")!!.first.bufferedReader().readLines().first { it.contains("Main-Class") }.split(" ")[1]
        command.add(mainClass) // Not sure we actually need this.
        command.add("\"$preprocessorFilePath\"")
      }
    }



    /**************************************************************************/
    /* Sort out the list of books / files to be handled. */

    val bookAbbreviations = getBookNamesToBeProcessed()
    val bookDetails = bookAbbreviations.joinToString("||") {
      it + "::" + File(TextStructureUsxForUseWhenConvertingToEnhancedUsx.getFilePathForBook(it)!!).name
    }

    command.add("\"$outFolder\"")
    command.add("\"$inFolder\"")
    command.add("\"$bookDetails\"")
    runCommand("  Preprocessing: ", command)
  }


  /****************************************************************************/
  /* For use where we are applying no pre-processing.  Checks to see if the
     files in the InputUsx and UsxA folders are different.  If so, clears out
     UsxA and copies all of the files across.  I'd prefer not to have to do
     this, but it's just too complicated trying to work out the implications
     where we either may or may not have UsxA. */

  private fun copyFilesIfDifferent ()
  {
    val inputUsxFiles = StepFileUtils.getMatchingFilesFromFolder(FileLocations.getInputUsxFolderPath(), (".*\\." + FileLocations.getFileExtensionForUsx()).toRegex())
      .map { StepFileUtils.getFileName(it)}
      .toSet()

    val usxAFiles: Set<String> =
      if (StepFileUtils.fileOrFolderExists(FileLocations.getInternalUsxAFolderPath()))
        StepFileUtils.getMatchingFilesFromFolder(FileLocations.getInternalUsxAFolderPath(), (".*\\." + FileLocations.getFileExtensionForUsx()).toRegex())
          .map { StepFileUtils.getFileName(it)}
          .toSet()
      else
       setOf()

    var needToCopy = (usxAFiles - inputUsxFiles).isNotEmpty() || (inputUsxFiles - usxAFiles).isNotEmpty()
    if (!needToCopy)
      needToCopy = usxAFiles.any { MiscellaneousUtils.getSha256(Paths.get(FileLocations.getInternalUsxAFolderPath(), it).toString()) !=
                                   MiscellaneousUtils.getSha256(Paths.get(FileLocations.getInputUsxFolderPath(), it).toString()) }

    if (needToCopy)
    {
      StepFileUtils.deleteFolder(FileLocations.getInternalUsxAFolderPath())
      StepFileUtils.createFolderStructure(FileLocations.getInternalUsxAFolderPath())

      inputUsxFiles.forEach { StepFileUtils.copyFile(Paths.get(FileLocations.getInternalUsxAFolderPath(), it).toString(),
                                                     Paths.get(FileLocations.getInputUsxFolderPath(), it).toString())}

    }
  }


  /****************************************************************************/
  /* Copies from input to output any files which were requested but which the
     pre-processor has not created. */

  private fun fillInMissingFiles ()
  {
    val bookDetails = getBookNamesToBeProcessed()
    val outFolder = FileLocations.getInternalUsxAFolderPath()
    bookDetails.map { Pair(it, TextStructureUsxForUseWhenConvertingToEnhancedUsx.getFilePathForBook(it)) } // Book abbreviation plus input file path if the file exists.
               .filter { null != it.second } // Remove entries for books which do not exist in the input.
               .map { Pair(Paths.get(outFolder, Paths.get(it.second!!).fileName.toString()).toString(), it.second) } // Input file path, output file path.
               .filterNot { StepFileUtils.fileOrFolderExists(it.first) } // Remove entries where the output file exists.
               .forEach { StepFileUtils.copyFile(it.first, it.second!!) }
  }


  /****************************************************************************/
  /* Returns a list of lower case book abbreviations for books to be processed,
     sorted by Bible order. */

  private fun getBookNamesToBeProcessed (): List<String>
  {
    val allAvailableBooks = TextStructureUsxForUseWhenConvertingToEnhancedUsx.getBookNumbersInOrder()

    val bookNumbersToBeProcessed =
      if (Dbg.getBooksToBeProcessed().isEmpty())
        allAvailableBooks
      else
      {
        val booksSelectedForDebug = Dbg.getBooksToBeProcessed().map { BibleBookNamesUsx.abbreviatedNameToNumber(it) } .toSet()
        allAvailableBooks.toSet().intersect(booksSelectedForDebug)
      }

    return bookNumbersToBeProcessed.sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it).lowercase() }
  }


  /****************************************************************************/
  private var m_MethodGetTextForValidation: Method? = null
  private var m_MethodPreprocess: Method? = null
  private var m_PreprocessorInstance: Any? = null



  /****************************************************************************/
  /* Stylesheet information is held in ConfigData as stepXsltStylesheet, or as
     eg stepXsltStylesheet_Gen.  Empty or null values are regarded as flagging
     non-existent information. */

  private val m_Stylesheets: MutableMap<String, String?> = mutableMapOf()
}