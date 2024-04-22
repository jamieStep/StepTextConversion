/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.processingelements.*
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.utils.Digest
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess


/******************************************************************************/
/**
* Controls the whole of the processing.
*
* At the end of processing, two configuration parameters have been set up with
* information which will be useful later on:
*
* - stepOriginData will be VL, USX or OSIS.  This represents the
*   raw data upon which the run was based.  In other words, if InputVl exists,
*   it will be 'VL'; if InputUsx exists it will be USX< and if neither exists,
*   it will be OSIS.  With VL, however, the run may not have started from that
*   folder: VL is always subject to pre-processing to turn it into USX, and if
*   that USX already exists (and postdates the VL), that may have been used
*   instead if the command-line parameters permitted it.  Similarly InputUsx
*   *may* have been pre-processed to produce revised USX, and again if the
*   revised USX already exists, the run may have started with that.
*
* - stepOriginDataAdditionalInfo contains additional text
*   explaining this issue of pre-processing where we have started from the
*   pre-processed text.  (The parameter will be undefined where we have
*   started from the raw text.)
*
* @author ARA "Jamie" Jamieson
*/

object ProcessingController
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Handles everything.
   */

  fun process (args: Array<String>)
  {
    initialise(args)
    deleteLogFilesEtc()
    //ThrowAwayCode.testOsis() // $$$
    determineProcessingSteps()
    runProcessors()
  }


  /****************************************************************************/
  /**
   * For use at the end of a run.  Returns major warnings as simulated large
   * characters to output to stderr.
   */

  fun getMajorWarningsAsBigCharacters (): String
  {
    // The try below is required because if we start from OSIS, we won't have any reversification details.
    var res = ""
    try { if (!ConfigData["stepReversificationDataLocation"]!!.startsWith("http")) res += C_Local_ReversificationData } catch (_: Exception) {}
    if (!ConfigData.getAsBoolean("stepEncryptionRequired", "no")) res += C_NotEncrypted
    if ("evaluationonly" == ConfigData["stepRunType"]!!.lowercase()) res += C_NonRelease
    return res
  }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun deleteLogFilesEtc ()
  {
    StepFileUtils.deleteFile(FileLocations.getConverterLogFilePath())
    StepFileUtils.deleteFile(FileLocations.getDebugOutputFilePath())
    StepFileUtils.deleteFile(FileLocations.getOsisToModLogFilePath())
    StepFileUtils.deleteFile(FileLocations.getVersificationFilePath())
  }


  /****************************************************************************/
  private val C_ProcessingElementsStarters = listOf(PE_Phase1_FromInputVl, PE_Phase1_FromInputUsx, PE_Phase1_FromInputImp, PE_Phase1_FromInputOsis)
  private val C_ProcessingElementsFromInternalOsis = listOf(PE_Phase2_ToInternalOsis, PE_Phase3_To_SwordModule, PE_Phase4_To_RepositoryPackage)
  private val m_AllAvailableProcessingElements = listOf(C_ProcessingElementsStarters, C_ProcessingElementsFromInternalOsis).flatten()
  private val m_ProcessingElements = mutableListOf<PE>()


  /****************************************************************************/
  private fun determineProcessingSteps ()
  {
    /**************************************************************************/
    val haveImp   = FileLocations.getInputVlFilesExist()
    val haveOsis = FileLocations.getInputOsisFileExists()
    val haveUsx  = FileLocations.getInputUsxFilesExist()
    val haveVl   = FileLocations.getInputVlFilesExist()

    val startFrom =
      if (ConfigData.getAsBoolean("stepStartProcessFromOsis", "no"))
      {
        if (!haveOsis) throw StepException("Requested to start from OSIS, but no OSIS exists.")
        "osis"
      }
      else if (haveUsx)
        "usx"
      else if (haveVl)
        "vl"
      else
        "imp"



    /**************************************************************************/
    if ("osis" != startFrom && haveOsis)
    {
      if (StepFileUtils.getEarliestFileDate(FileLocations.getInputOsisFolderPath(), FileLocations.getFileExtensionForOsis()) >
          StepFileUtils.getLatestFileDate(FileLocations.getInputUsxFolderPath(), FileLocations.getFileExtensionForUsx()))
        Logger.warning("Starting from ${startFrom.uppercase()}, but the InputOsis data is later.")
    }


    if ("osis" == startFrom && haveUsx)
    {
      if (StepFileUtils.getEarliestFileDate(FileLocations.getInputUsxFolderPath(), FileLocations.getFileExtensionForUsx()) >
          StepFileUtils.getLatestFileDate(FileLocations.getInputOsisFolderPath(), FileLocations.getFileExtensionForOsis()))
        Logger.warning("Starting from OSIS, but the InputUsx data is later.")
    }


    if ("osis" == startFrom && haveVl)
    {
      if (StepFileUtils.getEarliestFileDate(FileLocations.getInputVlFolderPath(), FileLocations.getFileExtensionForVl()) >
          StepFileUtils.getLatestFileDate(FileLocations.getInputOsisFolderPath(), FileLocations.getFileExtensionForOsis()))
        Logger.warning("Starting from OSIS, but the InputVl data is later.")
    }

    ConfigData["stepOriginData"] = startFrom



    /**************************************************************************/
    if (seeIfWeAreCheckingInputDataAgainstPrevious()) exitProcess(0)
    if (seeIfWeAreEvaluatingSchemes()) exitProcess(0)
    StepFileUtils.deleteFileOrFolder(FileLocations.getOutputFolderPath())



    /**************************************************************************/
    when (startFrom)
    {
      "imp" ->
      {
        m_ProcessingElements.add(PE_Phase1_FromInputImp)
        m_ProcessingElements.addAll(C_ProcessingElementsFromInternalOsis)
      }

      "osis" ->
      {
        m_ProcessingElements.add(PE_Phase1_FromInputOsis)
        m_ProcessingElements.addAll(C_ProcessingElementsFromInternalOsis)
      }

      "usx" ->
      {
        m_ProcessingElements.add(PE_Phase1_FromInputUsx)
        m_ProcessingElements.addAll(C_ProcessingElementsFromInternalOsis)
      }

      "vl" ->
      {
        m_ProcessingElements.add(PE_Phase1_FromInputVl)
        m_ProcessingElements.addAll(C_ProcessingElementsFromInternalOsis)
      }
    }
  }


  /****************************************************************************/
  /* Gathers up command line options for all possible processors, plus adds
     some common ones which might not otherwise turn up. */

  private fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
     /*************************************************************************/
     /* From the processing elements. */

     m_AllAvailableProcessingElements.forEach { it.getCommandLineOptions(commandLineProcessor) }



    /*************************************************************************/
    /* Common or not otherwise available. */

    commandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
    commandLineProcessor.addCommandLineOption("startProcessFromOsis", 0, "Forces the processing to start from OSIS rather than VL / USX.", null, null, false)
    commandLineProcessor.addCommandLineOption("runType", 1, "Type of run.", listOf("Release", "MajorRelease", "MinorRelease", "EvalOnly", "EvaluationOnly"), "EvaluationOnly", true)
    commandLineProcessor.addCommandLineOption("checkInputsAgainstPreviousModule", 0, "Check whether the current inputs were used to build the existing module.", null, null, false)
    commandLineProcessor.addCommandLineOption("evaluateSchemesOnly", 0, "Evaluate alternative osis2mod versification schemes only.", null, null, false)



    /*************************************************************************/
    /* Debug. */

    commandLineProcessor.addCommandLineOption("dbgAddDebugAttributesToNodes", 0, "Add debug attributes to nodes.", null, "no", false)
    val commonText = ": 'No' or anything containing 'screen' (output to screen), 'file' (output to debugLog.txt), or both.  Include 'deferred' if you want screen output at the end of the run, rather than as it occurs.  Not case-sensitive."
    commandLineProcessor.addCommandLineOption("dbgDisplayReversificationRows", 1, "Display selected reversification rows$commonText", null, "no", false)
    commandLineProcessor.addCommandLineOption("dbgSelectBooks", 1, "Limits processing to selected books.  Either <, <=, -, >=, > followed by the USX abbreviation for a book, or else a comma-separated list of books.",null, null, false )
  }


  /****************************************************************************/
  /* General initialisation. */

  private fun initialise (args: Array<String>)
  {
    /**************************************************************************/
    initialiseCommandLineArgsAndConfigData(args)



    /**************************************************************************/
    /* If supplied, this could be something like   Isa   or it could be a
       comma-separated list of USX abbreviations.  */

    if (null != ConfigData["dbgSelectBooks"])
    {
      val regex = "(?<comparison>\\W*?)(?<books>.*)".toRegex()
      val mr = regex.matchEntire(ConfigData["dbgSelectBooks"]!!) ?: throw StepException("Invalid 'dbgSelectBooks' parameter")
      val books = mr.groups["books"]!!.value.replace("\\s+".toRegex(), "")
      val comparison = mr.groups["comparison"]!!.value.replace("\\s+".toRegex(), "")
      Dbg.setBooksToBeProcessed(books, if (comparison.isEmpty()) "=" else comparison)
    }



    /**************************************************************************/
    when (ConfigData["stepRunType"]!!.lowercase())
    {
      "release"      -> { ConfigData["stepReleaseType"] = "tbd"  ; ConfigData.delete("stepRunType"); ConfigData["stepRunType"] = "release" }
      "majorrelease" -> { ConfigData["stepReleaseType"] = "major"; ConfigData.delete("stepRunType"); ConfigData["stepRunType"] = "release" }
      "minorrelease" -> { ConfigData["stepReleaseType"] = "minor"; ConfigData.delete("stepRunType"); ConfigData["stepRunType"] = "release" }
      else           -> { val x = ConfigData["stepRunType"]!!.lowercase(); ConfigData.delete("stepRunType"); ConfigData["stepRunType"] = x }
    }



    /**************************************************************************/
    ConfigData["stepBuildTimestamp"] = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd_HHmm")).replace("_", "T")
  }


  /****************************************************************************/
  /* Reads the command line parameters, and then based upon that sets up all of
     the configuration data. */

  private fun initialiseCommandLineArgsAndConfigData (args: Array<String>)
  {
    /**************************************************************************/
    /* Determine what command line parameters are permitted and then parse the
       command line. */

    getCommandLineOptions(CommandLineProcessor)
    if (!CommandLineProcessor.parse(args, "TextConverter")) return



    /**************************************************************************/
    /* We look for a particular environment variable which may contain
       configuration settings.  These may be of the form key=val or key#=val,
       and are treated in the normal way.  Because they are loaded first, it
       means that anything else at all can override key=val settings. */

    ConfigData.loadFromEnvironmentVariable()



    /**************************************************************************/
    /* Use this to enable us to read the configuration files, and then also
       copy the command line parameters to the configuration data store.

       rootFolder is taken as-is if it specifies an absolute path.  Otherwise,
       we check to see if it makes sense if taken relative to the current
       working directory.  And if that fails, we assume there is an
       environment variable StepTextConverterDataRoot, and it is taken relative
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
          Paths.get(ConfigData["stepTextConverterDataRoot"]!!, rootFolderPathFromCommandLine).toString()
      }

    FileLocations.initialise(rootFolderPath)
    ConfigData.load(FileLocations.getStepConfigFileName())
    CommandLineProcessor.copyCommandLineOptionsToConfigData("TextConverter")
    ConfigData.extractDataFromModuleName()



    /**************************************************************************/
    Logger.setLogFile(FileLocations.getConverterLogFilePath())
    Logger.announceAll(true)
  }


  /****************************************************************************/
  /* Runs a single processor and checks for issues. */

  private fun runProcessor (processor: PE)
  {
    try
    {
      if (processor.banner().isNotEmpty()) Dbg.reportProgress("\n" + processor.banner())
      Dbg.setReportProgressPrefix(processor.banner())
      Logger.setPrefix(processor.banner())
      processor.process()
      Logger.setPrefix(null)
      Dbg.setReportProgressPrefix("")
      Logger.announceAll(true)
    }
    catch (e: StepException)
    {
      if (!e.getSuppressStackTrace()) e.printStackTrace(System.err)
      System.err.println("Fatal error: " + processor.banner() + ": " + e.toString())
      System.err.flush()
      exitProcess(1)
    }
    catch (e: Exception)
    {
      e.printStackTrace(System.err)
      System.err.println("Fatal error: " + processor.banner() + ": " + e.toString())
      System.err.flush()
      exitProcess(1)
    }
    finally
    {
      try { Logger.announceAll(false); } catch (_: Exception) {}
    }
  }


  /****************************************************************************/
  /* Prepares the ground for each processor in turn, and then runs it. */

  private fun runProcessors ()
  {
    Logger.info("Running the following steps: " + m_ProcessingElements.joinToString(" -> "){ it::class.simpleName!!.replace("FileCreator_", "") } + ".")
    m_ProcessingElements.forEach { it.pre() }
    m_ProcessingElements.forEach { runProcessor(it) }
  }


  /****************************************************************************/
  /* Checks to see if we've been called just to check input data against that
     used on the previous run (if any).  If we have, carries out the necessary
     processing and exist the program. */

  private fun seeIfWeAreCheckingInputDataAgainstPrevious (): Boolean
  {
    if (ConfigData.getAsBoolean("stepCheckInputsAgainstPreviousModule", "no"))
    {
      Digest.checkFileDigests()
      return true
    }

    return false
  }


  /****************************************************************************/
  /* Checks to see if we've been called just to evaluate versification
    schemes. */

  private fun seeIfWeAreEvaluatingSchemes (): Boolean
  {
    /**************************************************************************/
    if (!ConfigData.getAsBoolean("stepEvaluateSchemesOnly", "no"))
      return false



    /**************************************************************************/
    StepFileUtils.deleteFile(FileLocations.getVersificationFilePath())



    /**************************************************************************/
    Dbg.reportProgress("Evaluating fit with versification schemes")
    Dbg.resetBooksToBeProcessed() // Force all books to be included.
    PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation.process()
    return true
  }


  /******************************************************************************/
  // https://patorjk.com/software/taag/#p=display&f=Graffiti&t=Type%20Something%20  Font=Big.

  private const val C_Local_ReversificationData ="""
   _      ____   _____          _        _____  ________      ________ _____   _____ _____ ______ _____ _____       _______ _____ ____  _   _ 
  | |    / __ \ / ____|   /\   | |      |  __ \|  ____\ \    / /  ____|  __ \ / ____|_   _|  ____|_   _/ ____|   /\|__   __|_   _/ __ \| \ | |
  | |   | |  | | |       /  \  | |      | |__) | |__   \ \  / /| |__  | |__) | (___   | | | |__    | || |       /  \  | |    | || |  | |  \| |
  | |   | |  | | |      / /\ \ | |      |  _  /|  __|   \ \/ / |  __| |  _  / \___ \  | | |  __|   | || |      / /\ \ | |    | || |  | | . ` |
  | |___| |__| | |____ / ____ \| |____  | | \ \| |____   \  /  | |____| | \ \ ____) |_| |_| |     _| || |____ / ____ \| |   _| || |__| | |\  |
  |______\____/ \_____/_/    \_\______| |_|  \_\______|   \/   |______|_|  \_\_____/|_____|_|    |_____\_____/_/    \_\_|  |_____\____/|_| \_|                                                                                                                                                                                                                    
      
       """


  /******************************************************************************/
  private const val C_NonRelease = """
   _   _  ____  _   _     _____  ______ _      ______           _____ ______ 
  | \ | |/ __ \| \ | |   |  __ \|  ____| |    |  ____|   /\    / ____|  ____|
  |  \| | |  | |  \| |   | |__) | |__  | |    | |__     /  \  | (___ | |__   
  | . ` | |  | | . ` |   |  _  /|  __| | |    |  __|   / /\ \  \___ \|  __|  
  | |\  | |__| | |\  |   | | \ \| |____| |____| |____ / ____ \ ____) | |____ 
  |_| \_|\____/|_| \_|   |_|  \_\______|______|______/_/    \_\_____/|______|
                                                                                
  """


  /******************************************************************************/
  private const val C_NotEncrypted = """
   _   _    ___    _____     _____   _   _    ____   ____   __   __  ____    _____   _____   ____
  | \ | |  / _ \  |_   _|   | ____| | \ | |  / ___| |  _ \  \ \ / / |  _ \  |_   _| | ____| |  _ \
  |  \| | | | | |   | |     |  _|   |  \| | | |     | |_) |  \ V /  | |_) |   | |   |  _|   | | | |
  | |\  | | |_| |   | |     | |___  | |\  | | |___  |  _ <    | |   |  __/    | |   | |___  | |_| |
  |_| \_|  \___/    |_|     |_____| |_| \_|  \____| |_| \_\   |_|   |_|       |_|   |_____| |____/
                       
       """
}
