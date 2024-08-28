package org.stepbible.textconverter.builders

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.ConfigDataSupport
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.stepexception.StepException
import java.io.File
import java.nio.file.Paths
import java.util.*

/******************************************************************************/
/**
 * Base class for builders.
 *
 * IMPORTANT: If making changes to the collection of builders, make sure you
 * keep the functions getSpecialBuilders and getNonSpecialBuilders up to date.
 *
 * @author ARA "Jamie" Jamieson
 */

object Builder_Root: Builder
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

      CommandLineProcessor.CommandLineOption("conversionTimeReversification", 0, "Use to force conversion time restructuring (you will seldom want this).", null, null, false),
      CommandLineProcessor.CommandLineOption("forceUpIssue", 0, "Normally up-issue is suppressed if the update reason has not changed.  This lets you override this.", null, null, false),
      CommandLineProcessor.CommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true),
      CommandLineProcessor.CommandLineOption("releaseType", 1, "Type of release.", listOf("Major", "Minor"), null, true, forceLc = true),
      CommandLineProcessor.CommandLineOption("startProcessFromOsis", 0, "Forces the processing to start from OSIS rather than VL / USX.", null, null, false),
      CommandLineProcessor.CommandLineOption("stepUpdateReason", 1, "The reason STEP is making the update (if the supplier has also supplied a reason, this will appear too).", null, null, false),
      CommandLineProcessor.CommandLineOption("supplierUpdateReason", 1, "The reason STEP is making the update (if the supplier has also supplied a reason, this will appear too).", null, null, false),
      CommandLineProcessor.CommandLineOption("targetAudience", 1, "If it is possible to build both STEP-only and public version, selects the one required.", listOf("Public", "Step"), null, false, forceLc = true),



      /***********************************************************************/
      /* Debug. */

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
  * Gets the banner for the builder which is currently active.  Returns null if
  * the processing stack is empty or if the banner at the top of the stack is
  * empty.
  *
  * @return banner
  */

  fun getProcessorName (): String?
  {
    if (m_ActiveProcessorNames.isEmpty()) return null
    val banner = m_ActiveProcessorNames.peek()
    return banner.ifEmpty { null }
  }


  /****************************************************************************/
  fun popProcessorName (): String = m_ActiveProcessorNames.pop()
  fun pushProcessorName (processorName: String) { m_ActiveProcessorNames.push(processorName) }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun doIt (args: Array<String>)
  {
    getCommandLineOptions()
    if (!CommandLineProcessor.parse(args, "TextConverter")) return
    handleConfigurationData()
    checkIfRunIsForSelectedBooksOnly()
    runProcess()
  }


  /****************************************************************************/
  private fun runProcess ()
  {
    deleteLogFilesEtc()
    getSpecialBuilders().forEach { it.process() }
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
    val mr = regex.matchEntire(ConfigData["dbgSelectBooks"]!!) ?: throw StepException("Invalid 'dbgSelectBooks' parameter")
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
  /**
  * I was automatically determining the lists of builders via the getSubtypes
  * method in MiscellaneousUtils.  However, for reasons I have been unable to
  * determine that works only when running within the IDE.  When running the
  * converter as a JAR, it no longer works.  As a result, you will need to
  * maintain the lists below manually. */

  private fun getAllBuilders () = getNonSpecialBuilders() union getSpecialBuilders()

  private fun getNonSpecialBuilders () = listOf(
    Builder_InitialOsisRepresentationFromImp,
    Builder_InitialOsisRepresentationFromOsis,
    Builder_InitialOsisRepresentationFromUsx,
    Builder_InitialOsisRepresentationFromVl,
    Builder_InitialOsisRepresentationOfInputs,
    Builder_InternalOsis,
    Builder_Module,
    Builder_RepositoryPackage,
    Builder_Root)

  private fun getSpecialBuilders () = listOf(
    SpecialBuilder_CompareInputsWithPrevious,
    SpecialBuilder_ConfigDataDebugging,
    SpecialBuilder_EvaluateSchemesOnly,
    SpecialBuilder_Help,
    SpecialBuilder_Version
  )
  //getSubtypes(SpecialBuilder::class.java).map { it.kotlin.objectInstance!! as SpecialBuilder }


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
    Logger.setLogFile(FileLocations.getConverterLogFilePath())
    ConfigData.extractDataFromRootFolderName()
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
    Logger.announceAll(true)
  }


  /****************************************************************************/
  private val m_ActiveProcessorNames: Stack<String> = Stack()
}
