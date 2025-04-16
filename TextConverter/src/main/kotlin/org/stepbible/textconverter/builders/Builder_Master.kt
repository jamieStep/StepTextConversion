package org.stepbible.textconverter.builders

import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigDataSupport
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ParallelRunning
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepFileUtils
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefCollection
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import java.io.File
import java.nio.file.Paths

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
      CommandLineProcessor.CommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true),
      CommandLineProcessor.CommandLineOption("targetAudience", 1, "If it is possible to build both STEP-only and public version, selects the one required.", listOf("Public", "Step"), null, false, forceLc = true),



      /***********************************************************************/
      CommandLineProcessor.CommandLineOption("configFromZip", 0, "Take config from previous zip file rather than current data.", null, "no", false),



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
      CommandLineProcessor.CommandLineOption("dbgSelectBooks", 1, "Limits processing to selected books.  Either <, <=, -, >=, > followed by the USX abbreviation for a book, or else a comma-separated list of books.",null, null, false )
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
    val argsWithEnvironmentVariablesExpanded = args.map { expandEnvironmentVariable(it) } .toTypedArray()
    getCommandLineOptions()
    if (!CommandLineProcessor.parse(argsWithEnvironmentVariablesExpanded)) return



    /**************************************************************************/
    handleConfigurationData()
    checkIfRunIsForSelectedBooksOnly()
    runProcess()
  }


    /**************************************************************************/
    /* Expands %...% on the assumption that these represent references to
       environment variables. */

    private fun expandEnvironmentVariable (s: String): String
    {
      val regex = Regex("%([^%]+)%")
      return regex.replace(s) { matchResult ->
          val varName = matchResult.groupValues[1]
        System.getenv(varName) ?: matchResult.value
      }
    }


  /****************************************************************************/
  private fun runProcess ()
  {
    getSpecialBuilders().forEach { it.process() } // These aren't supposed to generate a repository package, and will exit after processing if they are invoked.

    Rpt.report(level = 0, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    Rpt.report(level = -1, ">>>>>>>>>> Start of processing for ${ConfigData["stepModuleName"]} (${ConfigData["stepTargetAudience"]} use).")

    if (ParallelRunning.isPermitted())
    {
      Rpt.report(level = -1, "\nParallel running: Books may legitimately be reported out of order.  Some screen output may be interleaved.")
      //MiscellaneousUtils.initialiseAllObjectsBasedOnReflection() // Early initialisation is needed only on parallel runs.  On sequential runs, everything should just work (!)
      MiscellaneousUtils.initialiseAllObjectsBasedOnObjectInterfaceInheritance()
    }

    deleteLogFilesEtc()
    StepFileUtils.deleteFileOrFolder(FileLocations.getOutputFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getOutputFolderPath())

    Builder_RepositoryPackage.process()
  }


  /****************************************************************************/
  /* If supplied, this argument could be something like   Isa   or it could be a
     comma-separated list of USX abbreviations.  */

  private fun checkIfRunIsForSelectedBooksOnly ()
  {
    if (null == ConfigData["dbgSelectBooks"]) return
    val regex = "(?<comparison>\\W*?)(?<books>.*)".toRegex()
    val mr = regex.matchEntire(ConfigData["dbgSelectBooks"]!!) ?: throw StepExceptionWithStackTraceAbandonRun("Invalid 'dbgSelectBooks' parameter")
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


  /****************************************************************************/
  /* Reads the command line parameters, and then based upon that sets up all of
     the configuration data. */

  private fun handleConfigurationData ()
  {
    /**************************************************************************/
    /* If we are being asked to investigate what happens to the config data, we
       need to know that before we actually start doing anything with it. */

    val dbgConfigData = CommandLineProcessor.getOptionValue("dbgConfigData")
    ConfigDataSupport.initialise(dbgConfigData ?: "")



    /**************************************************************************/
    /* Extract settings from the STEP environment variable. */

    ConfigData.loadFromEnvironmentVariable()



    /**************************************************************************/
    /* Use this to enable us to read the configuration files, and then also
       copy the command line parameters to the configuration data store.

       rootFolder is taken as-is if it specifies an absolute path.  Otherwise,
       we check to see if it makes sense if taken relative to the current
       working directory.  And if that fails, we assume there is an
       environment variable stepTextConverterOverallDataRoot, and it is taken relative
       to that. */

    val rootFolderPathFromCommandLine = CommandLineProcessor.getOptionValue("rootFolder")!!
    val rootFolderPath =
      if (Paths.get(rootFolderPathFromCommandLine).isAbsolute)
        rootFolderPathFromCommandLine
      else
      {
        val x = Paths.get(System.getProperty("user.dir"), rootFolderPathFromCommandLine)
        if (File(x.toString()).exists())
          x.toString()
        else
          Paths.get(ConfigData["stepTextConverterOverallDataRoot"]!!, rootFolderPathFromCommandLine).toString()
      }

    FileLocations.initialise(rootFolderPath)
    ConfigData.extractDataFromRootFolderName()
    Logger.setLogFile(FileLocations.getConverterLogFilePath())
    ConfigData.load(FileLocations.getStepConfigFileName())
    CommandLineProcessor.copyCommandLineOptionsToConfigData()



    /**************************************************************************/
    /* I'd rather not do this here, because it seems a bit specialist for this
       context.  But it needs to be done early, because other things need to
       know whether they can generate footnotes or not. */

    val isCopyrightText = ConfigData.getAsBoolean("stepIsCopyrightText", "yes") // Default to text being copyright -- that's safer.
    if (null == ConfigData["stepOkToGenerateFootnotes"]) // Unless we've specifically been told we can generate footnotes, derive the setting from the copyright setting.
      ConfigData.put("stepOkToGenerateFootnotes", if (isCopyrightText) "no" else "yes", force = true)



    /**************************************************************************/
    ConfigDataSupport.checkMandatories()
    Logger.announceAllAndTerminateImmediatelyIfErrors()
  }
}
