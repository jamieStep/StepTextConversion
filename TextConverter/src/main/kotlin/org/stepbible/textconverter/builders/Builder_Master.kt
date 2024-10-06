package org.stepbible.textconverter.builders

import org.stepbible.textconverter.applicationspecificutils.Osis_FileProtocol
import org.stepbible.textconverter.applicationspecificutils.Usx_FileProtocol
import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigDataSupport
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils
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

object Builder_Master: Builder()
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
      /* Common or not otherwise available. */

      //CommandLineProcessor.CommandLineOption("conversionTimeReversification", 0, "Use to force conversion time restructuring (you will seldom want this).", null, null, false),
      CommandLineProcessor.CommandLineOption("forceUpIssue", 0, "Normally up-issue is suppressed if the update reason has not changed.  This lets you override this.", null, null, false),
      CommandLineProcessor.CommandLineOption("permitParallelRunning", 1, "Permits parallel running where the processing supports it (you may want to turn it off while debugging).", listOf("yes", "no"), "yes", false),
      CommandLineProcessor.CommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true),
      CommandLineProcessor.CommandLineOption("releaseType", 1, "Type of release.", listOf("Major", "Minor"), null, true, forceLc = true),
      CommandLineProcessor.CommandLineOption("stepUpdateReason", 1, "The reason STEP is making the update (if the supplier has also supplied a reason, this will appear too).", null, null, false),
      CommandLineProcessor.CommandLineOption("supplierUpdateReason", 1, "The reason STEP is making the update (if the supplier has also supplied a reason, this will appear too).", null, null, false),
      CommandLineProcessor.CommandLineOption("targetAudience", 1, "If it is possible to build both STEP-only and public version, selects the one required.", listOf("Public", "Step"), null, false, forceLc = true),
      CommandLineProcessor.CommandLineOption("useExistingOsis", 1, "Ignore other inputs and start from OSIS.  asInput => Use existing OSIS as input but change it as necessary; asOutput => Use existing OSIS unchanged as far as possible.", listOf("asInput", "asOutput"), null, false),



      /***********************************************************************/
      CommandLineProcessor.CommandLineOption("help", 0, "Get help information.", null, null, false),
      CommandLineProcessor.CommandLineOption("version", 0, "Get version information.", null, null, false),



      /***********************************************************************/
      /* Debug. */

      CommandLineProcessor.CommandLineOption("dbgAddDebugAttributesToNodes", 0, "Add debug attributes to nodes.", listOf("yes", "no"), "no", false),
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
    /**************************************************************************/
    getCommandLineOptions()
    if (!CommandLineProcessor.parse(args)) return



    /**************************************************************************/
    handleConfigurationData()
    checkIfRunIsForSelectedBooksOnly()
    runProcess()
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
