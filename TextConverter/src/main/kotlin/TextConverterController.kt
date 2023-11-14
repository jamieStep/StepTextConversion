/**********************************************************************************************************************/
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
import kotlin.system.exitProcess


/**********************************************************************************************************************/
/**
* Controls the whole of the processing.
*
* @author ARA "Jamie" Jamieson
*/

class TextConverterController
{
    /******************************************************************************************************************/
    /**
     * Handles everything
     */

    fun process (args: Array<String>): Boolean
    {
      initialiseCommandLineArgsAndConfigData(args)
      if (!doPre()) return false
      return doProcess()
    }


    /******************************************************************************************************************/
    /* Ideally I'd only want to collect command line arguments from processors which we know are going to run.
       However, I have to be able to parse the command line in order to know what will run, and I can't do that
       without establishing what command line arguments I need.  So I just have to proceed as though _everything_ was
       going to run. */

    private fun collectPermittedCommandLineParameters ()
    {
        //CommandLineProcessor.addCommandLineOption("permitComplexChanges", 1, "Permit eg reversification Moves (may be ruled out by licensing conditions).", listOf("Yes", "No", "AsLicence"), "AsLicence", false)

        CommandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
        CommandLineProcessor.addCommandLineOption("help", 0, "Get help.", null, null, false)
        GeneralEnvironmentHandler.getCommandLineOptions(CommandLineProcessor)
        TextConverterProcessorEvaluateVersificationSchemes.getCommandLineOptions(CommandLineProcessor)

        m_Processors.forEach { it.second.getCommandLineOptions(CommandLineProcessor) }
    }


    /******************************************************************************************************************/
    /* Runs over all processors permitting each to have a chance to set up its environment. */

    private fun doPre(): Boolean
    {
        if (!runControlTypeContains(RunControlType.OsisInput)) VersionAndHistoryHandler.createHistoryFileIfNecessaryAndWorkOutVersionDetails()
        m_Processors.filter { runProcessor(it) }.forEach { if (!it.second.pre()) return false }
        return true
    }


    /******************************************************************************************************************/
    /* Runs all of the processors. */

    private fun doProcess(): Boolean
    {
        m_Processors.forEach {
            if (runProcessor(it))
            {
                if (!doProcess(it.second)) return false
                Logger.announceAll(true)
            }
        }

        return true
    }


    /******************************************************************************************************************/
    /* Runs all of the individual processors.  If the processors throw an otherwise uncaught exception, it is caught and
       reported here, and the processing exists.  If any of the individual processors returns false, the method returns
       false.  Otherwise, it returns true. */

    private fun doProcess (processor: TextConverterProcessorBase): Boolean
    {
        try
        {
            if (processor.banner().isNotEmpty()) Dbg.reportProgress("\n" + processor.banner())
            Logger.setPrefix(processor.banner())
            if (!processor.process()) return false
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

        return true
    }


    /******************************************************************************************************************/
    /* Reads the command line parameters, and then based upon that sets up all of the configuration data. */

    private fun initialiseCommandLineArgsAndConfigData (args: Array<String>)
    {
        /**************************************************************************/
        /* Determine what command line parameters are permitted and then parse the
           command line. */

        collectPermittedCommandLineParameters()
        if (!CommandLineProcessor.parse(args, "TextConverter")) return



        /**************************************************************************/
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

        if (File(StandardFileLocations.getConfigFilePath()).exists())
          ConfigData.load(StandardFileLocations.getConfigFileName())
        else if (!runControlTypeContains(RunControlType.OsisInput))
          throw StepException("Can't find config file: ${StandardFileLocations.getConfigFilePath()}.")

        CommandLineProcessor.copyCommandLineOptionsToConfigData("TextConverter")
        if (ConfigData.getAsBoolean("stepEvaluateSchemesOnly", "No"))
          C_RunControlType = RunControlType.EvaluateSchemesOnly



        /**************************************************************************/
        GeneralEnvironmentHandler.onStartup()
        StepFileUtils.deleteFile(StandardFileLocations.getConverterLogFilePath())
        Logger.setLogFile(StandardFileLocations.getConverterLogFilePath())
        Logger.announceAll(true)
   }


   /******************************************************************************************************************/
   /* Determines whether a particular processor should run. */

   private fun runProcessor (selector: Pair<RunControlType, TextConverterProcessorBase>): Boolean
   {
     return 0 != (selector.first.type and C_RunControlType.type) && selector.second.runMe()
   }


   /******************************************************************************************************************/
   /* Lists of processors in the order in which they run. */

   private val C_ProcessorsForFullConversionRun = listOf(
       Pair(RunControlType.All,                 DbgController),
       Pair(RunControlType.All,                 TestController.instance()),
       Pair(RunControlType.EvaluateSchemesOnly, TextConverterProcessorEvaluateVersificationSchemes),
       Pair(RunControlType.UsxInput,            TextConverterProcessorVLToEnhancedUsx),
       Pair(RunControlType.UsxInput,            TextConverterProcessorUsxToEnhancedUsx1),
       Pair(RunControlType.UsxInput,            TextConverterProcessorReversification), // Need to reconsider this?
       Pair(RunControlType.UsxInput,            TextConverterProcessorUsxToEnhancedUsx2),
       Pair(RunControlType.UsxInput,            TextConverterFeatureSummaryGenerator),
       Pair(RunControlType.UsxInput,            TextConverterEnhancedUsxValidator),
       Pair(RunControlType.UsxInput,            TextConverterProcessorEnhancedUsxToOsis),
       Pair(RunControlType.ModuleGenerator,     TextConverterTaggingHandler),
       Pair(RunControlType.ModuleGenerator,     TextConverterProcessorOsisToSword),
       Pair(RunControlType.ModuleGenerator,     TextConverterRepositoryPackageHandler)
   )


    /******************************************************************************************************************/
    private var m_Processors = C_ProcessorsForFullConversionRun
}
