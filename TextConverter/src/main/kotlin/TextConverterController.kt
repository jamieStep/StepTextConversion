/**********************************************************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleBookAndFileMapperRawUsx
import org.stepbible.textconverter.support.bibledetails.BibleStructure
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
      determineProcessorList()
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
        CommandLineProcessor.addCommandLineOption("help", 0, "Get help.", null, "no", false)
        CommandLineProcessor.addCommandLineOption("reversificationType", 1, "None / Basic / Academic (append '?' to Basic or Academic to have the converter decide whether to reversify", listOf("None", "Basic", "Academic", "Basic?", "Academic?"), "Basic?", false)
        GeneralEnvironmentHandler.getCommandLineOptions(CommandLineProcessor)

        (C_FullConversionProcessorList.map { it.processor}.toSet() union C_ProcessorsForEvaluationOnly.toSet()) .forEach { it.getCommandLineOptions(CommandLineProcessor) }
    }


    /******************************************************************************************************************/
    /* Determine the list of processors to be run. */

    private fun determineProcessorList ()
    {
      if (ConfigData.getAsBoolean("stepEvaluateSchemesOnly", "No"))
      {
        m_ProcessorsForThisRun = C_ProcessorsForEvaluationOnly
        return
      }

     TextConverterProcessorRawInputManager.runMe() // This call merely initialises the internal data structures.
     m_InputType = TextConverterProcessorRawInputManager.getRawInputFolderType()
     m_ProcessorsForThisRun = C_FullConversionProcessorList.filter { it.fn() }.map { it.processor }
    }



    /******************************************************************************************************************/
    /* Runs over all processors permitting each to have a chance to set up its environment. */

    private fun doPre(): Boolean
    {
        m_ProcessorsForThisRun.forEach { if (!it.pre()) return false }
        return true
    }


    /******************************************************************************************************************/
    /* Runs all of the processors. */

    private fun doProcess(): Boolean
    {
        m_ProcessorsForThisRun.forEach {
          if (!doProcess(it)) return false
            Logger.announceAll(true)
        }

        return true
    }


    /******************************************************************************************************************/
    /* Runs all of the individual processors.  If the processors throw an otherwise uncaught exception, it is caught and
       reported here, and the processing exists.  If any of the individual processors returns false, the method returns
       false.  Otherwise, it returns true. */

    private fun doProcess (processor: TextConverterProcessorBase): Boolean
    {
        if (!processor.runMe())
          return true

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
        BibleStructure.UsxUnderConstructionInstance().populateFromBookAndFileMapper(BibleBookAndFileMapperRawUsx, "raw", wantWordCount = false)
        GeneralEnvironmentHandler.onStartup()
        StepFileUtils.deleteFile(StandardFileLocations.getConverterLogFilePath())
        Logger.setLogFile(StandardFileLocations.getConverterLogFilePath())
        Logger.announceAll(true)
   }



   /******************************************************************************************************************/
   private lateinit var m_InputType: String


   /******************************************************************************************************************/
   /* Lists of processors in the order in which they run. */

   private var m_ProcessorsForThisRun: List<TextConverterProcessorBase> = listOf()


   /******************************************************************************************************************/
   /* What it says on the tin. */

   private val C_ProcessorsForEvaluationOnly = listOf(
     DbgController,
     TestController.instance(),
     TextConverterProcessorEvaluateVersificationSchemes
   )


   /******************************************************************************************************************/
   private data class ProcessorSelector (val processor: TextConverterProcessorBase, val fn: () -> Boolean)
   private val C_FullConversionProcessorList = listOf(
     ProcessorSelector(DbgController )                          { true },
     ProcessorSelector(TestController.instance())               { true },

     ProcessorSelector(TextConverterProcessorRawInputManager)   { "usx" != m_InputType },

     ProcessorSelector(TextConverterProcessorUsxToEnhancedUsx1) { "osis" != m_InputType},
     // $$$ Processor(TextConverterProcessorReversification), // Need to reconsider this?
     ProcessorSelector(TextConverterProcessorUsxToEnhancedUsx2) { "osis" != m_InputType },

     ProcessorSelector(TextConverterFeatureSummaryGenerator)    { "osis" != m_InputType },
     ProcessorSelector(TextConverterEnhancedUsxValidator)       { "osis" != m_InputType },
     ProcessorSelector(TextConverterProcessorEnhancedUsxToOsis) { "osis" != m_InputType },

     ProcessorSelector(TextConverterTaggingHandler)             { true },
     ProcessorSelector(TextConverterProcessorOsisToSword)       { true },
     ProcessorSelector(TextConverterRepositoryPackageHandler)   { true }
   )
}
