package org.stepbible.textconverter.builders

import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefCollection
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun


/******************************************************************************/
/**
 * Controls the build process.
 *
 * <span class='important'>IMPORTANT:</span> If making changes to the collection
 * of builders, make sure you keep the functions getSpecialBuilders and
 * getNonSpecialBuilders up to date.
 *
 * @author ARA "Jamie" Jamieson
 */

object Builder_Master: Builder(), ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = ""


  /****************************************************************************/
  override fun commandLineOptions (): List<CommandLineProcessor.CommandLineOption>
  {
    val commonText = ": 'No' or anything containing 'screen' (output to screen), 'file' (output to debugLog.txt), or both.  Include 'deferred' if you want screen output at the end of the run, rather than as it occurs.  Not case-sensitive."

    return listOf(
      /*************************************************************************/
      CommandLineProcessor.CommandLineOption("rootFolder", 1, "Root folder of Bible text structure (defaults to current working directory).", null, System.getProperty("user.dir"), false),
      CommandLineProcessor.CommandLineOption("targetAudience", 1, "If it is possible to build both STEP-only and public version, selects the one required.", listOf("Public", "Step"), null, false, forceLc = true),



      /***********************************************************************/
      CommandLineProcessor.CommandLineOption("history", 1, "The text to be used for the history record, or the special values FromMetadata or AsPrevious.", null, null, true),
      CommandLineProcessor.CommandLineOption("releaseNumber", 1, "An explicit version number (eg 1.0, 2.1); or + for a dot release, leaving the processing to work out the actual value; or ++ for a whole number release; or = to keep previous number.  The supplied value is overridden and treated as = if historyText is AsPrevious", null, null, true),



      /***********************************************************************/
      CommandLineProcessor.CommandLineOption("help", 0, "Get help information.", null, null, false),
      CommandLineProcessor.CommandLineOption("version", 0, "Get version information.", null, null, false),



      /***********************************************************************/
      /* Debug. */

      CommandLineProcessor.CommandLineOption("permitParallelRunning", 1, "Permits parallel running where the processing supports it (you may want to turn it off while debugging).", listOf("yes", "no"), "yes", false),
      CommandLineProcessor.CommandLineOption("dbgAddDebugAttributesToNodes", 0, "Add debug attributes to nodes.", null, "no", false),
      CommandLineProcessor.CommandLineOption("dbgDisplayReversificationRows", 0, "Display selected reversification rows$commonText", null, "no", false),
      CommandLineProcessor.CommandLineOption("dbgSelectBooks", 1, "Limits processing to selected books.  Either <, <=, -, >=, > followed by the USX abbreviation for a book, or else a comma-separated list of books.",null, null, false ),
      CommandLineProcessor.CommandLineOption("dbgConfigData", 1, "Controls config data debugging.  Use reportSet to give details of what is set where.",listOf("reportSet"), null, false),
    )
  }


  /****************************************************************************/
  override fun doIt () {} // We need this to satisfy the interface from which we inherit, but in this case we can't have this do anything useful.
  fun process (args: Array<String>) = doIt(args)


  /****************************************************************************/
  /**
  * Applies a defined collection of regexes to a chunk of text.
  *
  * @param inputText
  * @param regexes List of regexes (possibly empty or null).
  * @return Modified text.
  */

  fun processRegexes (inputText: String, regexes: List<Pair<Regex, String>>?): String
  {
    if (regexes.isNullOrEmpty()) return inputText

    var revisedText = inputText

    regexes.forEach {
      revisedText = applyRegex(it, revisedText)
    }

    return revisedText
  }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Applies any regex processing to the input text.  regexDetails is a pair,
     the first part of which should be a regex pattern, and the second of which
     is a replacement.

     I'm having a little trouble working out how to do this consistently, simply
     and with a reasonable degree of flexibility.  As things stand, unless
     the replacement contains @convertRef, I assume that the pattern and
     replacement are mutually compatible in terms of capturing groups, and apply
     a simple replacement.

     If it does contain @convertRef, I assume that the pattern contains a single
     capturing group which is a reference in vernacular form, and that the
     replacement is made up purely of @convertRef.  In this case I take the
     capturing group and convert it to USX form.

     Actually, it's not @convertRef -- it's either @convertRefVernacularToUsx
     or @convertRefVernacularToOsis.
   */

  private fun applyRegex (regexDetails: Pair<Regex, String>, inputText: String): String
  {
    /**************************************************************************/
    fun convertRefVernacularToOsis (s: String) = RefCollection.rdVernacular(s).toStringUsx()
    fun convertRefVernacularToUsx (s: String) = RefCollection.rdVernacular(s).toStringUsx()
    var converter: ((String) -> String)? = null

    if ("@convertRefVernacularToUsx" in regexDetails.second)
      converter = ::convertRefVernacularToUsx
    else if ("@convertRefVernacularToOsis" in regexDetails.second)
      converter = ::convertRefVernacularToOsis



    /**************************************************************************/
    return if (null == converter)
      inputText.replace(regexDetails.first, regexDetails.second)
    else
      regexDetails.first.replace(inputText) { matchResult -> converter(matchResult.groupValues[1]) }
  }


  /****************************************************************************/
  private fun doIt (args: Array<String>)
  {
    getCommandLineOptions()
    if (!CommandLineProcessor.parse(args)) return
    ConfigData.load()
    checkIfRunIsForSelectedBooksOnly()
    runProcess()
  }


  /****************************************************************************/
  private fun runProcess ()
  {
    getSpecialBuilders().forEach { it.process() } // These aren't supposed to generate a repository package, and will exit after processing if they are invoked.

    Rpt.report(level = 0, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    Rpt.report(level = -1, ">>>>>>>>>> Start of processing for ${ConfigData["calcModuleName"]} (${ConfigData["stepTargetAudience"]} use).")

    if (ParallelRunning.isPermitted())
    {
      Rpt.report(level = -1, "\nParallel running: Books may legitimately be reported out of order.  Some screen output may be interleaved.")
      //MiscellaneousUtils.initialiseAllObjectsBasedOnReflection() // Early initialisation is needed only on parallel runs.  On sequential runs, everything should just work (!)
      MiscellaneousUtils.initialiseAllObjectsBasedOnObjectInterfaceInheritance()
    }

    deleteLogFilesEtc()
    StepFileUtils.deleteFileOrFolder(FileLocations.getOutputFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getOutputFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getInternalOsisFolderPath())

    Builder_RepositoryPackage.process()
  }


  /****************************************************************************/
  /* If supplied, this argument could be something like   Isa   or it could be a
     comma-separated list of USX abbreviations.  */

  private fun checkIfRunIsForSelectedBooksOnly ()
  {
    if (null == ConfigData["stepDbgSelectBooks"]) return
    val regex = "(?<comparison>\\W*?)(?<books>.*)".toRegex()
    val mr = regex.matchEntire(ConfigData["stepDbgSelectBooks"]!!) ?: throw StepExceptionWithStackTraceAbandonRun("Invalid 'stepDbgSelectBooks' parameter")
    val books = mr.groups["books"]!!.value.replace("\\s+".toRegex(), "")
    val comparison = mr.groups["comparison"]!!.value.replace("\\s+".toRegex(), "")
    Dbg.setBooksToBeProcessed(books, comparison.ifEmpty { "=" })
  }


  /****************************************************************************/
  private fun deleteLogFilesEtc ()
  {
    StepFileUtils.deleteFile(FileLocations.getConverterLogFilePath())
    StepFileUtils.deleteFile(FileLocations.getDebugOutputFilePath())
    StepFileUtils.deleteFile(FileLocations.getOsisToModLogFilePath())
    StepFileUtils.deleteFile(FileLocations.getVersificationFilePath())
  }


  /****************************************************************************/
  /* Only the classes in this present package are capable of requiring command
     line options.  In fact certain runs require only some of the options, so
     strictly what I do below -- where I pick up all possible options -- is
     wrong.  However, I need to parse the command line in order to work out
     what we're doing, and in order to parse the command line I must already
     have specified all of the possible options. */

  private fun getCommandLineOptions ()
  {
    getAllBuilders().forEach {
            val options = it.commandLineOptions()
            options?.forEach { option -> CommandLineProcessor.addCommandLineOption(option) }
    }
//    getSubtypes(Builder::class.java).forEach {
//      val builder = try { (it.kotlin.objectInstance!! as Builder) }  catch (e: Exception) { return@forEach }
//      val options = builder.commandLineOptions()
//      options?.forEach { option -> CommandLineProcessor.addCommandLineOption(option) }
//    }
  }


  /****************************************************************************/
  private fun getBuilders () = MiscellaneousUtils.getSubtypes(Builder::class.java).map { it.kotlin.objectInstance!! as Builder }
  private fun getSpecialBuilders () = MiscellaneousUtils.getSubtypes(SpecialBuilder::class.java).map { it.kotlin.objectInstance!! as SpecialBuilder }
  private fun getAllBuilders () = getBuilders() union getSpecialBuilders()
}
