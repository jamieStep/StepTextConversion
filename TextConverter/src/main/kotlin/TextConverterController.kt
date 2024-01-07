/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
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
    initialise(args)
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

     1. Start from existing OSIS and 'just' create a module.

     2. Start from existing USX and then create OSIS and module.

     3. Start from other format (only VL at present) and then create USX and
        then OSIS and then module.

     2) and 3) are subject to a further complication, in that it may be
     necessary to break the process after creating the OSIS, so that DIB can
     modify it; and once that has happened, we then need to resume the
     processing in order to create the module.

     And it should not be assumed that in case 1) neither USX nor VL exists:
     we may have created OSIS previously, and simply be updating it without
     wishing to run the process all the way through from the USX or VL.

     And finally there is a further complication, because DIB is not the only
     one who may need to update the OSIS -- at least at present I may be
     updating it myself to cater for known issues.

     So what I need is a hopefully robust was of determining which steps are
     needed, and of handling this break -- a major complication, at least as
     I write these comments, being that the processing hasn't been written
     with such a break in mind, and therefore assumes that data remains
     available in memory from the very beginning right up to the point where
     a module is available.  (Hopefully by the time you read this, I'll
     have rejigged things so this is no longer a problem.)

     I deal here purely with determining which processing steps are required,
     something which I _think_ can be handled purely by a combination of
     existence and DateModified checks.  (The latter do rely upon it taking
     longer to carry out a processing step than the granularity of
     DateModified, but I think that's one second, and processing steps
     normally _do_ take longer than that.) */

  private fun determineProcessingSteps ()
  {
    /**************************************************************************/
    /* At present we can evaluate schemes only when starting from InputUsx or
       InputVl.  The evaluator actually requires USX to run from, so I may need
       to include TextConverterProcessorInputVlToUsxA here. */

    if (ConfigData.getAsBoolean("stepEvaluateSchemesOnly", "no"))
    {
      var inputProcessor: TextConverterProcessor? = TextConverterProcessorInputVlToUsxA
      if (!StepFileUtils.fileOrFolderExists(StandardFileLocations.getInputVlFolderPath()))
        inputProcessor = null
      else if (StepFileUtils.folderIsEmpty(StandardFileLocations.getInputVlFolderPath()))
        inputProcessor = null
      else if (!StepFileUtils.folderIsEmpty(StandardFileLocations.getInternalUsxAFolderPath()))
      {
        val usxFileDate = StepFileUtils.getLatestFileDate(StandardFileLocations.getInternalUsxAFolderPath(), "usx")
        val vlFileDate = StepFileUtils.getLatestFileDate(StandardFileLocations.getInputVlFolderPath(), "txt")
        if (usxFileDate > vlFileDate)
          inputProcessor = null
      }

      m_Processors = mutableListOf(DbgController, TestController, inputProcessor, TextConverterProcessorEvaluateVersificationSchemes).filterNotNull()
      return
    }



    /**************************************************************************/
    TextConverterProcessorXToSword.setInputSelector("internal") // We _have_ to tell this where to pick up its input -- see the class itself for an explanation.

    val standardFullList: MutableList<Pair<String, TextConverterProcessor>> =
      mutableListOf(Pair("toUsxAOrNot", TextConverterProcessorInputVlToUsxA), // Processing below may override this, depending upon what kind of input is available.
                    Pair("determineReversificationType", TextConverterProcessorDetermineReversificationTypeEtc),
                    Pair("toUsxB", TextConverterProcessorXToUsxB), // Fixed.
                    Pair("generateFeatureSummary", TextConverterFeatureSummaryGenerator), // Fixed.
                    Pair("validateUsx", TextConverterProcessorUsxBValidator), // Fixed.
                    Pair("toOsis", TextConverterProcessorUsxBToOsis), // Fixed.
                    Pair("toSword", TextConverterProcessorXToSword), // Processing below may need to change this, depending upon whether we want to use supplied OSIS or our own generated OSIS.
                    Pair("toRepositoryPackage", TextConverterProcessorRepositoryPackageHandler))



    /**************************************************************************/
    /* When we know we're starting from InputVl or InputUsx, it gives me
       slightly more confidence in later processing if I clear the internal
       folders. */
       
    fun deleteAndRecreateInternalFolders ()
    {
      StepFileUtils.deleteFolder(StandardFileLocations.getInternalUsxAFolderPath())
      StepFileUtils.createFolderStructure(StandardFileLocations.getInternalUsxAFolderPath())
      
      StepFileUtils.deleteFolder(StandardFileLocations.getInternalUsxBFolderPath())
      StepFileUtils.createFolderStructure(StandardFileLocations.getInternalUsxBFolderPath())
    }


    /**************************************************************************/
    /* Are we forcing things? */

    var force = ConfigData["stepStartProcessFrom"]?.lowercase()
    if (null != force && "none" != force)
    {
      val ix: Int

      if ("vl"   == force && (!StepFileUtils.fileOrFolderExists(StandardFileLocations.getInputVlFolderPath  ()) || StepFileUtils.folderIsEmpty(StandardFileLocations.getInputVlFolderPath  ()))) force = "usx"
      if ("usx"  == force && (!StepFileUtils.fileOrFolderExists(StandardFileLocations.getInputUsxFolderPath ()) || StepFileUtils.folderIsEmpty(StandardFileLocations.getInputUsxFolderPath  ()))) force = "osis"
      if ("osis" == force && (!StepFileUtils.fileOrFolderExists(StandardFileLocations.getInputOsisFolderPath()) || StepFileUtils.folderIsEmpty(StandardFileLocations.getInputOsisFolderPath ()))) return
      when (force)
      {
        "vl"   -> { ix = standardFullList.indexOfFirst { it.first == "toUsxAOrNot" }; deleteAndRecreateInternalFolders() }
        "usx"  -> { ix = standardFullList.indexOfFirst { it.first == "toUsxAOrNot" }; deleteAndRecreateInternalFolders(); standardFullList[ix] = Pair(standardFullList[ix].first, TextConverterProcessorInputUsxToUsxA) }
        "osis" -> { ix = standardFullList.indexOfFirst { it.first == "toSword" }; TextConverterProcessorXToSword.setInputSelector("input") }
        else -> throw StepException("Invalid setting for 'startProcessFrom'.")
      }

      m_Processors = mutableListOf(DbgController, TestController)
      (m_Processors as MutableList<TextConverterProcessor>).addAll(standardFullList.subList(ix, standardFullList.size).map { it.second })
      return
    }



    /**************************************************************************/
    /* We now do a series of date- / existence- based checks on various
       combinations of folder and files.  Recall in what follows that all
       timestamps are >= 0, and that a stamp of zero means that the
       corresponding item does not exist. */

    val moduleFileDate = StepFileUtils.getLatestFileDate(StandardFileLocations.getInternalSwordFolderPath(), "zip")
    val inputOsisFileDate = StepFileUtils.getLatestFileDate(StandardFileLocations.getInputOsisFolderPath(), "xml")
    val inputUsxFileDate = StepFileUtils.getLatestFileDate(StandardFileLocations.getInputUsxFolderPath(), "usx")
    val inputVlFileDate = StepFileUtils.getLatestFileDate(StandardFileLocations.getInputVlFolderPath(), "txt")
    val internalUsxAFileDate = StepFileUtils.getLatestFileDate(StandardFileLocations.getInternalUsxAFolderPath(), "usx")



    /**************************************************************************/
    fun makeSubListStartingAt (startAt: String)
    {
      val ix = standardFullList.indexOfFirst { it.first == startAt }
      m_Processors = mutableListOf(DbgController, TestController)
      (m_Processors as MutableList<TextConverterProcessor>).addAll(standardFullList.subList(ix, standardFullList.size).map { it.second })
    }



    /**************************************************************************/
    /* If we have both a module and an _input_ OSIS file, and if the module
       date exceeds the OSIS date, then there's nothing to do. */

    if (moduleFileDate > inputOsisFileDate && 0L != inputOsisFileDate)
    {
      Dbg.reportProgress("")
      Dbg.reportProgress("********** Module is more recent than OSIS file, so nothing to do. **********")
      Dbg.reportProgress("")
      exitProcess(0)
    }



    /**************************************************************************/
    /* If we have an _input_ OSIS file and either no module or an earlier one,
       then ignore everything else, and assume that we want to generate the
       module from the input OSIS. */

    if (inputOsisFileDate > moduleFileDate)
    {
      TextConverterProcessorXToSword.setInputSelector("input")
      makeSubListStartingAt("toSword")
      return
    }


    /**************************************************************************/
    /* VL goes to UsxA.  If we have a VL and no UsxA, or if the VL is later
       than the UsxA, we want to rerun everything starting with the VL. */

    if (inputVlFileDate > internalUsxAFileDate)
    {
      deleteAndRecreateInternalFolders()
      val ix = standardFullList.indexOfFirst { it.first == "toUsxAOrNot" }
      standardFullList[ix] = Pair(standardFullList[ix].first, TextConverterProcessorInputVlToUsxA)
      TextConverterProcessorXToSword.setInputSelector("internal")
      makeSubListStartingAt("toUsxAOrNot")
      return
    }



    /**************************************************************************/
    /* inputUsx also goes to UsxA, so same processing as previous paragraph. */

    if (inputUsxFileDate > internalUsxAFileDate)
    {
      deleteAndRecreateInternalFolders()
      val ix = standardFullList.indexOfFirst { it.first == "toUsxAOrNot" }
      standardFullList[ix] = Pair(standardFullList[ix].first, TextConverterProcessorInputUsxToUsxA)
      TextConverterProcessorXToSword.setInputSelector("internal")
      makeSubListStartingAt("toUsxAOrNot")
      return
    }



    /**************************************************************************/
    /* That's now considered all of the cases where we are taking into account
       supplied input.  In all cases, the output data post-dates the input data.
       so there's nothing to do. */

    Dbg.reportProgress("")
    Dbg.reportProgress("********** Module is more recent than OSIS file, so nothing to do. **********")
    Dbg.reportProgress("")
    exitProcess(0)
  }


  /****************************************************************************/
  private fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
    m_ProcessorsAll.forEach { it.getCommandLineOptions(commandLineProcessor) }
    commandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
    commandLineProcessor.addCommandLineOption("reversificationType", 1, "When reversification is to be applied (if at all)", listOf("None", "RunTime", "ConversionTime"), "None", false)
    commandLineProcessor.addCommandLineOption("reversificationFootnoteLevel", 1, "Type of reversification footnotes", listOf("Basic", "Academic"), "Basic", false)
    commandLineProcessor.addCommandLineOption("updateReason", 1, "A reason for creating this version of the module (required only if runType is Release and the release arises because of changes to the converter as opposed to a new release from he text suppliers).", null, "Unknown", false)
    commandLineProcessor.addCommandLineOption("startProcessFrom", 1, "Forces the processing to start from a given stage of input.", listOf("VL", "USX", "OSIS"), "None", false)
  }


  /****************************************************************************/
  /* General initialisation. */

  private fun initialise (args: Array<String>)
  {
    /**************************************************************************/
    initialiseCommandLineArgsAndConfigData(args)



    /**************************************************************************/
    ConfigData["stepBuildTimestamp"] = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd_HHmm")).replace("_", "T")
    determineModuleNameTestRelatedSuffix()                              // On test runs, adds to the module name something saying this version of the module is for evaluation only.
    determineModuleNameAudienceRelatedSuffix()                          // Gets a further suffix for the module name which says whether this can be used only within STEP, or more widely.

    ConfigData["stepModuleName"] = ConfigData.calc_stepModuleNameBase() + ConfigData["stepModuleNameTestRelatedSuffix"] + ConfigData["stepModuleNameAudienceRelatedSuffix"]

    if (null == ConfigData["stepVersificationScheme"])                  // Where we are doing runtime reversification, we have had to defer working out the scheme name because we use one of our own making and only now have all the inputs.
      ConfigData["stepVersificationScheme"] = "v11n_" + ConfigData["stepModuleName"]



    /**************************************************************************/
    determineProcessingSteps()

    if (m_Processors.isEmpty())
    {
      Logger.error("No processing steps selected.  Either you have no input data, or the module is up to date already.")
      return
    }
    else
    {
      val msg = "Running the following processing steps: " + m_Processors.map { it::class.simpleName } .joinToString(", ")
      Dbg.reportProgress(msg)
      Logger.info(msg)
    }
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

    StandardFileLocations.initialise(rootFolderPath)
    ConfigData.load(StandardFileLocations.getStepConfigFileName())
    CommandLineProcessor.copyCommandLineOptionsToConfigData("TextConverter")



    /**************************************************************************/
    Logger.setLogFile(StandardFileLocations.getConverterLogFilePath())
    Logger.announceAll(true)
  }


  /****************************************************************************/
  /* Runs a single processor and checks for issues. */

  private fun runProcessor (processor: TextConverterProcessor)
  {
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
    StepFileUtils.deleteFile(StandardFileLocations.getConverterLogFilePath())
    StepFileUtils.deleteFile(StandardFileLocations.getDebugOutputFilePath())
    StepFileUtils.deleteFile(StandardFileLocations.getOsisToModLogFilePath())
    StepFileUtils.deleteFile(StandardFileLocations.getVersificationFilePath())
    m_Processors.forEach { it.prepare() }
    m_Processors.forEach { runProcessor(it)}
  }


  /****************************************************************************/
  private val m_ProcessorsAll: List<TextConverterProcessor> = listOf(
    DbgController,
    TestController,
    TextConverterFeatureSummaryGenerator,
    TextConverterProcessorEvaluateVersificationSchemes,
    TextConverterProcessorXToSword,
    TextConverterProcessorInputUsxToUsxA,
    TextConverterProcessorInputVlToUsxA,
    TextConverterProcessorUsxBToOsis,
    TextConverterProcessorXToUsxB,
    TextConverterProcessorRepositoryPackageHandler,
    TextConverterProcessorUsxBValidator
  )

  private lateinit var m_Processors: List<TextConverterProcessor>


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
