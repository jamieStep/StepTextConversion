/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.stepexception.StepException
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
* - stepProcessingOriginalData will be VL, USX or OSIS.  This represents the
*   raw data upon which the run was based.  In other words, if InputVl exists,
*   it will be 'VL'; if InputUsx exists it will be USX< and if neither exists,
*   it will be OSIS.  With VL, however, the run may not have started from that
*   folder: VL is always subject to pre-processing to turn it into USX, and if
*   that USX already exists (and postdates the VL), that may have been used
*   instead if the command-line parameters permitted it.  Similarly InputUsx
*   *may* have been pre-processed to produce revised USX, and again if the
*   revised USX already exists, the run may have started with that.
*
* - stepProcessingOriginalDataAdditionalInfo contains additional text
*   explaining this issue of pre-processing where we have started from the
*   pre-processed text.  (The parameter will be undefined where we have
*   started from the raw text.)
*
* @author ARA "Jamie" Jamieson
*/

object TextConverterController
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
    deleteLogFilesEtc()
    initialise(args)
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
    if (!ConfigData.getAsBoolean("stepEncryptionApplied", "no")) res += C_NotEncrypted
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
  /* This goes on the end of every module we generate.  Some modules can be
     used only within STEP, and these get the suffix _sbOnly (ie for use only
     within STEPBible).  Some are intended for possible public consumption, and
     these get the suffix _sb (ie produced by STEPBible, and intended to make
     sure the names we give to them do not clash with the name of any existing
     module upon which they may have been based).

     At present 'for use only within STEPBible' and 'won't work outside of
     STEPBible' come down to the same thing.  A Bible is _sbOnly if:

     - It has been encrypted OR ...

     - We have run it through our own version of osis2mod.

     Stop press: We have just discovered that it's too painful to rename ESV
     to follow this new standard.  So I've added another option.  We are
     already using the root folder name to give us the language code and
      vernacular abbreviation (eg eng_ESV).  If the root folder name has a
      third element (eng_ESV_th), I take that as being the suffix.
  */

  private fun determineModuleNameAudienceRelatedSuffix ()
  {
    val forcedSuffix = ConfigData.parseRootFolderName("stepModuleSuffixOverride")
    if (forcedSuffix.isNotEmpty())
      ConfigData["stepModuleNameAudienceRelatedSuffix"] = "_$forcedSuffix"
    else
    {
      val isSbOnly = "step" == ConfigData["stepOsis2modType"] ||
                     ConfigData.getAsBoolean("stepEncryptionRequired")
       ConfigData["stepModuleNameAudienceRelatedSuffix"] = if (isSbOnly) "_sbOnly" else "_sb"
    }
  }


  /****************************************************************************/
  /* A potential further addition to module names.  On release runs, it adds
     nothing.  On non-release runs it adds a timestamp etc to the name, so that
     we can have multiple copies of a module lying around.  This method can be
     run safely at any time, because the parameters it looks at will normally
     come direct from the command line. */

  private fun determineModuleNameTestRelatedSuffix ()
  {
    ConfigData["stepModuleNameTestRelatedSuffix"] =
      if ("release" in ConfigData["stepRunType"]!!.lowercase())
        ""
      else
      {
        var x = ConfigData["stepRunType"]!!
        if ("evaluation" in x.lowercase()) x = "eval"
        "_" + x + "_" + ConfigData["stepBuildTimestamp"]!!
      }
  }


  /****************************************************************************/
  /* Yet another nasty fiddly bit ...

     I think we can distinguish the following alternative processes:

     1. Start from existing OSIS and create a module.

     2. Start from existing USX and then create OSIS and module.

     3. Start from other format (only VL at present) and then create USX and
        then OSIS and then module.

     4. Start from some point within the processing chain to avoid duplicating
        unnecessary duplication of effort.

     The user can stipulate which of these is to be applied, the default
     being option 4.

     The processing here has to validate the selection and then convert this
     into the actual processing steps to be carried out. */

  private fun determineProcessingSteps ()
  {
    /**************************************************************************/
    /* Things which are handled out of the main processing flow -- evaluations
       which we can carry out straight away and then exit.  */

    if (seeIfWeAreCheckingInputDataAgainstPrevious()) exitProcess(0)
    if (seeIfWeAreEvaluatingSchemes()) exitProcess(0)



    /**************************************************************************/
    determineWhereWeAreStartingFrom()



    /**************************************************************************/
    /* Clear all output files to make sure that if processing fails, we aren't
       left with previous outputs lying around which we might mistake for the
       real thing. */

    m_ElementChainKeys.forEach { m_ElementChain[it]!!.processorToNextElement!!.pre() }



   /**************************************************************************/
   /* Needed for later processing. */

    DetermineReversificationTypeEtc.process()
  }


  /****************************************************************************/
  /* Determines the starting point -- either user-requested or determined
     internally -- and validates etc. */

  private fun determineWhereWeAreStartingFrom ()
  {
    /**************************************************************************/
    /* See if we are being forced to start at a particular location, and if so,
       perform some rudimentary validation. */

    var whereWeAreStartingFrom =
      when ((ConfigData["startProcessFrom"] ?: "original+").lowercase().replace("+", ""))
      {
        "osis" -> // Forces OSIS -- cf option below.
        {
          if (null == m_ElementChain["InputOsis"]!!.folderPath)
            throw StepException("Requested processing should start with supplied OSIS, but we do not have any.")
          else
            "InputOsis"
        }

        "original" -> // Will accept OSIS, but only if there's no VL or USX.
        {
          if (null != m_ElementChain["InputVl"]!!.folderPath)
            "InputVl"
          else if (null != m_ElementChain["InputUsx"]!!.folderPath)
            "InputUsx"
          else if (null != m_ElementChain["InputOsis"]!!.folderPath)
            "InputOsis"
          else
            throw StepException("No valid input folders from which to take data.")
        }

        else -> throw StepException("Unexpected value for startProcessFrom") // Don't think we're ever going to get here.
      }



    /**************************************************************************/
    /* If the option was Original+, we need to decide between VL or USX and
       UsxA. */

    if ('+' in (ConfigData["startProcessFrom"] ?: "original+") && m_ElementChain[whereWeAreStartingFrom]!!.creationDate > m_ElementChain["UsxA"]!!.creationDate)
      whereWeAreStartingFrom = "UsxA"



    /**************************************************************************/
    /* Validate the starting point.  If something later in the chain has
       a later date, then it's just a warning that you started unnecessarily
       early.  If anything earlier in the chain has a later date, then it's an
       error that you're ignoring new data. */

    val folderShortNames = m_ElementChain.keys.toList()
    val dateOfItemSelected = m_ElementChain[whereWeAreStartingFrom]!!.creationDate
    val ix = folderShortNames.indexOf(whereWeAreStartingFrom)

    val badFolders = folderShortNames.subList(0, ix).filter { m_ElementChain[it]!!.creationDate > dateOfItemSelected }
    if (badFolders.isNotEmpty())
    {
      val msg = "*** Earlier data in the processing chain has a later datestamp than the item you selected as your starting point: " + badFolders.joinToString(", ") { StepFileUtils.getFileName(m_ElementChain[it]!!.folderPath!!) }
      Dbg.reportProgress(msg)
      exitProcess(0)
    }

    val somethingLaterInTheChainHasALaterDate = folderShortNames.subList(ix + 1, folderShortNames.size).find { m_ElementChain[it]!!.creationDate > dateOfItemSelected }
    if (null != somethingLaterInTheChainHasALaterDate)
      Logger.warning("Something later in the processing chain has a later datestamp that the item you started as your selecting point.  This isn't a problem, but you may want to know you started earlier than necessary.")



    /**************************************************************************/
    /* Limit the keys to items we actually intend to process. */

    val firstProcessor = m_ElementChainKeys.indexOf(whereWeAreStartingFrom)
    m_ElementChainKeys = m_ElementChainKeys.subList(firstProcessor, m_ElementChainKeys.size)
    if ("InputOsis" != whereWeAreStartingFrom) m_ElementChainKeys.remove("InputOsis")





    /**************************************************************************/
    /* Work out the original from which we _could_ have started, regardless of
       where we actually _have_ set out, and then convert this into a user-
       friendly representation for use later.  Be careful if you change this,
       because processing elsewhere checks for a value of "OSIS". */

    when (whereWeAreStartingFrom)
    {
      "InputVl"   -> ConfigData["stepProcessingOriginalData"] = "VL"
      "InputUsx"  -> ConfigData["stepProcessingOriginalData"] = "USX"
      "InputOsis" -> ConfigData["stepProcessingOriginalData"] = "OSIS"

      "UsxA" ->
      {
        ConfigData["stepProcessingOriginalData"] = if (StepFileUtils.isNonEmptyFolder(FileLocations.getInputVlFolderPath())) "VL" else "USX"
        ConfigData["stepProcessingOriginalDataAdditionalInfo"] = "Pre-processing was previously applied to this format to create USX, and the run used the latter data: it did not start from the raw data."
      }
    }
  }


  /****************************************************************************/
  /* The element chain was initially populated with just the processor objects.
     I now need to look at each element to see what folder it writes to, and
     copy the details.  I couldn't do this earlier, because this information
     relies upon the configuration details having been read, and I use the
     initial (empty-ish) version of m_ElementChain to get the command-line
     parameters to let me do that. */

  private fun fillInDetailsInElementChain ()
  {
    m_ElementChainKeys.forEach {
      val elementDetails = m_ElementChain[it]!!
      val inputDetails = elementDetails.processorToNextElement?.takesInputFrom()
      if (null != inputDetails)
      {
        if (StepFileUtils.isNonEmptyFolder(inputDetails.first))
        {
          elementDetails.folderPath = inputDetails.first
          elementDetails.extension = inputDetails.second
          elementDetails.creationDate = StepFileUtils.getLatestFileDate(elementDetails.folderPath!!, elementDetails.extension)
        }
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

     m_ElementChainKeys.forEach { m_ElementChain[it]!!.processorToNextElement!!.getCommandLineOptions(commandLineProcessor) }



    /*************************************************************************/
    /* Common or not otherwise available. */

    commandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
    commandLineProcessor.addCommandLineOption("startProcessFrom", 1, "Forces the processing to start from a given type of input -- see documentation.", listOf("OSIS", "Original", "Original+"), "Original", false)
    commandLineProcessor.addCommandLineOption("runType", 1, "Type of run.", listOf("Release", "MajorRelease", "MinorRelease", "EvalOnly", "EvaluationOnly"), "EvaluationOnly", true)
    commandLineProcessor.addCommandLineOption("checkInputsAgainstPreviousModule", 0, "Check whether the current inputs were used to build the existing module.", null, null, false)
    commandLineProcessor.addCommandLineOption("evaluateSchemesOnly", 0, "Evaluate alternative osis2mod versification schemes only.", null, null, false)



    /*************************************************************************/
    /* Debug. */

    commandLineProcessor.addCommandLineOption("dbgAddDebugAttributesToNodes", 0, "Add debug attributes to nodes.", null, "no", false)
    val commonText = ": 'No' or anything containing 'screen' (output to screen), 'file' (output to debugLog.txt), or both.  Include 'deferred' if you want screen output at the end of the run, rather than as it occurs.  Not case-sensitive."
    commandLineProcessor.addCommandLineOption("dbgDisplayReversificationRows", 1, "Display selected reversification rows$commonText", null, "no", false)
  }


  /****************************************************************************/
  /* General initialisation. */

  private fun initialise (args: Array<String>)
  {
    /**************************************************************************/
    makeElementChain()
    initialiseCommandLineArgsAndConfigData(args)



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
    determineModuleNameTestRelatedSuffix()                              // On test runs, adds to the module name something saying this version of the module is for evaluation only.
    determineModuleNameAudienceRelatedSuffix()                          // Gets a further suffix for the module name which says whether this can be used only within STEP, or more widely.

    ConfigData["stepModuleName"] = ConfigData.calc_stepModuleNameBase() + ConfigData["stepModuleNameTestRelatedSuffix"] + ConfigData["stepModuleNameAudienceRelatedSuffix"]

    if (null == ConfigData["stepVersificationScheme"])                  // Where we are doing runtime reversification, we have had to defer working out the scheme name because we use one of our own making and only now have all the inputs.
      ConfigData["stepVersificationScheme"] = "tbd"


    /**************************************************************************/
    fillInDetailsInElementChain()
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



    /**************************************************************************/
    Logger.setLogFile(FileLocations.getConverterLogFilePath())
    Logger.announceAll(true)
  }


  /****************************************************************************/
  /* Returns a collection of all relevant processors, ordered according to the
     processing order.  Later I also add information about the latest dates
     of any associated files, but I can't do that immediately -- I need the
     table below partly to determine what command-line parameters the program
     will accept, and until I have managed to parse the command line parameters,
     I can't locate the data folders in order to check on dates.

     The key for each element is something which identifies the folder which it
     uses as input (although I've made a deliberate decision to use logical
     names rather than actual folder names, in case I decide at some point to
     change the latter).  And each element writes data to the folder which
     serves as input to the next element.  (The one exception being InputOsis --
     that serves as a starting point and is not fed from the previous element.)

     Note that the table below remains fixed.  We may not necessarily want to
     run everything in the chain, but I handle that by maintaining a separate
     list of the keys for those elements we want to process. */

  private fun makeElementChain ()
  {
    m_ElementChain = mutableMapOf()
    m_ElementChain["InputVl"     ] = ElementDescriptor(FileCreator_InputVl_To_UsxA)
    m_ElementChain["InputUsx"    ] = ElementDescriptor(FileCreator_InputUsx_To_UsxA)
    m_ElementChain["UsxA"        ] = ElementDescriptor(FileCreator_UsxA_To_UsxB)
    m_ElementChain["UsxB"        ] = ElementDescriptor(FileCreator_UsxB_To_Osis)
    m_ElementChain["InputOsis"   ] = ElementDescriptor(FileCreator_InputOsisToInternalOsis)
    m_ElementChain["InternalOsis"] = ElementDescriptor(FileCreator_InternalOsis_To_SwordModule)
    m_ElementChain["SwordModule" ] = ElementDescriptor(FileCreator_SwordModuleEtc_To_RepositoryPackage)
    m_ElementChainKeys = m_ElementChain.keys.toMutableList()
  }


  /****************************************************************************/
  /* Runs a single processor and checks for issues. */

  private fun runProcessor (element: ElementDescriptor)
  {
    val processor = element.processorToNextElement ?: return

    try
    {
      if (processor.banner().isNotEmpty()) Dbg.reportProgress("\n" + processor.banner())
      Logger.setPrefix(processor.banner())
      processor.process()
      Logger.setPrefix(null)
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
    Logger.info("Running the following steps: " + m_ElementChainKeys.joinToString(", ") + ".")
    m_ElementChainKeys.forEach { runProcessor(m_ElementChain[it]!!) }
  }


  /****************************************************************************/
  /* Checks to see if we've been called just to check input data against that
     used on the previous run (if any).  If we have, carries out the necessary
     processing and exist the program. */

  private fun seeIfWeAreCheckingInputDataAgainstPrevious (): Boolean
  {
    if (ConfigData.getAsBoolean("stepCheckInputsAgainstPreviousModule", "no"))
    {
      DigestHandler.checkFileDigests()
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
    /* If we have VL available, we may need to convert it to USX if it doesn't
       already exist.  To that end we may also have to create the UsxA folder
       if that doesn't exist.  Then we need to do the conversion -- but only
       if we didn't already have stuff in UsxA.  If we did, we want to do the
       conversion only if teh relative dates of the VL and USX demand it. */

    var inputFolder: String? = null
    var createdUsxA = false

    if (StepFileUtils.fileOrFolderExists(FileLocations.getInputVlFolderPath()) &&
        !StepFileUtils.folderIsEmpty(FileLocations.getInputVlFolderPath()))
    {
      if (!StepFileUtils.fileOrFolderExists(FileLocations.getInternalUsxAFolderPath()))
      {
        createdUsxA = true
        StepFileUtils.createFolderStructure(FileLocations.getInternalUsxAFolderPath())
      }

      if (StepFileUtils.getLatestFileDate(FileLocations.getInputVlFolderPath(), FileLocations.getFileExtensionForVl()) >
          StepFileUtils.getLatestFileDate(FileLocations.getInternalUsxAFolderPath(), FileLocations.getFileExtensionForUsx()))
        FileCreator_InputVl_To_UsxA.process()

      inputFolder = FileLocations.getInternalUsxAFolderPath()
    }



    /**************************************************************************/
    /* Otherwise, much the same processing, with InputUsx assuming the role
       of InputVl.  */

    else if (StepFileUtils.fileOrFolderExists(FileLocations.getInputUsxFolderPath()) &&
             !StepFileUtils.folderIsEmpty(FileLocations.getInputUsxFolderPath()))
    {
      inputFolder = FileLocations.getInputUsxFolderPath()

      if (!StepFileUtils.fileOrFolderExists(FileLocations.getInternalUsxAFolderPath()))
      {
        createdUsxA = true
        StepFileUtils.createFolderStructure(FileLocations.getInternalUsxAFolderPath())
      }

      if (StepFileUtils.getLatestFileDate(FileLocations.getInputUsxFolderPath(), FileLocations.getFileExtensionForVl()) >
          StepFileUtils.getLatestFileDate(FileLocations.getInternalUsxAFolderPath(), FileLocations.getFileExtensionForUsx()))
      {
        FileCreator_InputUsx_To_UsxA.process()
        inputFolder = FileLocations.getInternalUsxAFolderPath()
      }
    }



    /**************************************************************************/
    /* By this stage we need inputFolder to have been set up.  If it has not,
       it implies that at best we have only OSIS to work with as an input, and
       the processing isn't set up to handle that. */

    if (null == inputFolder)
    {
      Dbg.reportProgress("Can evaluate versification schemes only where we have VL or USX available")
      return true
    }



    /**************************************************************************/
    Dbg.reportProgress("Evaluating fit with versification schemes")
    VersificationSchemesEvaluator_InputUsxOrUsxA.process()
    if (createdUsxA) StepFileUtils.deleteFolder(FileLocations.getInternalUsxAFolderPath()) // If we created the folder just for evaluation purposes, ditch it again.
    return true
  }


  /******************************************************************************/
  private data class ElementDescriptor (var processorToNextElement: ProcessingChainElement?)
  {
    var folderPath: String? = null
    var extension: String = ""
    var creationDate: Long = -1L
  }

  private lateinit var m_ElementChain: MutableMap<String, ElementDescriptor>
  private lateinit var m_ElementChainKeys: MutableList<String>



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



   // Use if we need to detect name-clashes here.
   //
   //   /**************************************************************************/
   //    /* Check to see if there are any clashes against the Crosswire module
   //       list. */
   //
   //    Dbg.reportProgress("Obtaining details of Crosswire modules so as to avoid name clashes")
   //    val crosswireModules = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
   //    val dataLocations = listOf(ConfigData["stepExternalDataPath_CrosswireModuleListBibles"]!!,
   //                               ConfigData["stepExternalDataPath_CrosswireModuleListCommentaries"]!!,
   //                               ConfigData["stepExternalDataPath_CrosswireModuleListDevotionals"]!!)
   //    dataLocations.forEach { url ->
   //      val lines = URL(url).readText().lines()
   //      var i = -1
   //      while (++i < lines.size)
   //      {
   //        var line = lines[i]
   //        var ix = line.indexOf("jsp?modName")
   //        if (ix < 0) continue
   //
   //        var name = line.substring(ix + "jsp?modName".length + 1)
   //        ix = name.indexOf("\"")
   //        name = name.substring(0, ix)
   //
   //        i += 2
   //        val description = lines[i].trim().replace("<td>", "").replace("</td>", "")
   //
   //        crosswireModules[name] = description
   //      } // for i
   //    } // For each dataLocation.
   //
   //
   //
   //    /**************************************************************************/
   //    //crosswireModules.keys.sorted().forEach { it -> Dbg.d(it + "\t" + crosswireModules[it] ) }
   //    val moduleNameWithoutSuffix = ConfigData["stepModuleNameBase"]!!
   //    m_DisambiguationSuffixForModuleNames = if (moduleNameWithoutSuffix in crosswireModules) suffix else ""
   //    return m_DisambiguationSuffixForModuleNames!!
   //  }
}
