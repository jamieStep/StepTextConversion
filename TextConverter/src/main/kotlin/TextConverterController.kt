/**********************************************************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
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
      if (m_EvaluateSchemesOnly) exitProcess(0)
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
        CommandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
        CommandLineProcessor.addCommandLineOption("permitComplexChanges", 1, "Permit eg reversification Moves (may be ruled out by licensing conditions).", listOf("Yes", "No", "AsLicence"), "AsLicence", false)
        CommandLineProcessor.addCommandLineOption("debugLevel", 1, "Debug level -- 0 => no debug, larger numbers => increasing amounts of debug.", null, "0", false)
        CommandLineProcessor.addCommandLineOption("help", 0, "Get help.", null, null, false)
        m_Processors.forEach { it.getCommandLineOptions(CommandLineProcessor) }
        TextConverterProcessorEvaluateVersificationSchemes.getCommandLineOptions(CommandLineProcessor)
    }


    /******************************************************************************************************************/
    /* Runs over all processor permitting each to have a chance to set up its environment. */

    private fun doPre(): Boolean
    {
        m_Processors.filter { it.runMe() }.forEach { if (!it.pre()) return false }
        return true
    }


    /******************************************************************************************************************/
    /* Runs all of the processors. */

    private fun doProcess(): Boolean
    {
        m_Processors.forEach {
            if (it.runMe())
            {
                if (!doProcess(it)) return false
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
            Dbg.reportProgress("\n" + processor.banner())
            Logger.setPrefix(processor.banner())
            if (!processor.process()) return false
            Logger.setPrefix(null)
            Logger.announceAll(true)
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
        /* Use this to enable us to read the configuration files, and then also
           copy the command line parameters to the configuration data store.

           rootFolder is taken as-is if it specifies and absolute path.  Otherwise,
           we check to see if it makes sense if taken relative to the current
           working directory.  And if that fails, we assume there is an
           environment variable StepTextConverterDataRoot, and it is taken relative
           to that. */

        val rootFolderPathFromCommandLine = CommandLineProcessor.getOptionValue("rootFolder")!!
        val rootFolderPath: String =
          if (Paths.get(rootFolderPathFromCommandLine).isAbsolute)
            rootFolderPathFromCommandLine
          else
          {
            val x = Paths.get(System.getProperty("user.dir"), rootFolderPathFromCommandLine)
            if (File(x.toString()).exists())
              x.toString()
            else
              Paths.get(System.getenv("StepTextConverterDataRoot"), rootFolderPathFromCommandLine).toString()
          }

        StandardFileLocations.initialise(rootFolderPath)

        ConfigData.load(StandardFileLocations.getConfigFileName())
        CommandLineProcessor.copyCommandLineOptionsToConfigData("TextConverter")



        /**************************************************************************/
        Dbg.setDebugFlag(CommandLineProcessor.getOptionValue("debugLevel")!!)



        /**************************************************************************/
        /* Depending upon the parameter supplied, reversification processing may be
           driven either by the user's input on the command line, or by the
           converter's own assessment of the situation.  We will assume the former,
           but the processing invoked by the next paragraph may change that. */

        ConfigData.put("stepReversificationBasis", "Driven by command line", true)



        /**************************************************************************/
        /* This run may be intended purely to evaluate the available schemes, in
           which case we need to do that.  Or the user may have requested the
           processing to make up its own mind as to whether or not reversification
           is required, in which case we need to make that call and update the
           associated parameters accordingly.  Or it may be neither of these, in
           which case we need to do nothing apart from delete any output which
           the evaluation might otherwise produce, and which may have been left
           lying around from a previous run. */

        m_EvaluateSchemesOnly = ConfigData.getAsBoolean("stepEvaluateSchemesOnly") || ConfigData.get("stepReversificationType")!!.contains("?")
        if (m_EvaluateSchemesOnly)
        {
          TextConverterProcessorEvaluateVersificationSchemes.pre()
          TextConverterProcessorEvaluateVersificationSchemes.process()
          return
        }



        /**************************************************************************/
        StepFileUtils.deleteFile(StandardFileLocations.getConverterLogFilePath())
        Logger.setLogFile(StandardFileLocations.getConverterLogFilePath())
        Logger.announceAll(true)
   }


    /******************************************************************************************************************/
    /* Is this a run where we merely want to evaluate possible versification schemes, rather than actually do any text
       conversion? */

    private var m_EvaluateSchemesOnly = false


    /******************************************************************************************************************/
    /* List of processors in the order in which they run.  TextConverterProcessorEvaluateVersificationSchemes doesn't
       exactly fit here, since a given run _either_ does this _or_ it does the others.  However, I arrange that on a run
       which requires it, it will itself inform the present processing that nothing else should be run. */

    private val m_Processors : List<TextConverterProcessorBase> = listOf(
        TextConverterProcessorVLToEnhancedUsx,
        TextConverterProcessorUsxToEnhancedUsx1,
        TextConverterProcessorReversification,
        TextConverterProcessorUsxToEnhancedUsx2,
        TextConverterFeatureSummaryGenerator,
        TextConverterEnhancedUsxValidator,
        TextConverterProcessorEnhancedUsxToOsis,
        TextConverterProcessorOsisToSword
    )
}
