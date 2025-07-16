/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.builders.Builder_Master
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.Issues
import org.stepbible.textconverter.nonapplicationspecificutils.debug.*
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepFileUtils
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionBase
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess


/******************************************************************************/
/**
* Main program.
*
* I could perfectly well have left this code inside Main.kt.  However, it is
* useful having a class (or object) here in the root package, because things
* can then refer to it in order to obtain the root package name.
*
* @author ARA "Jamie" Jamieson
*/

class MainProcessor
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * The converter main program.
   *
   * @param args Command line arguments.
   */

  fun process (args: Array<String>)
  {
    /****************************************************************************/
    var returnCode = 999
    try
    {
      ConfigData.commandLineWas(args)

      Builder_Master.process(args)
      Logger.summariseResults()

      val majorWarnings = getMajorWarningsAsBigCharacters()
      if (majorWarnings.isNotEmpty())
      {
        println()
        print(majorWarnings)
        Logger.specialMessage(majorWarnings)
        Logger.announceAll(false)
      }

      Dbg.endOfRun()

      returnCode = 0
    }



    /**************************************************************************/
    catch (e: StepExceptionBase)
    {
      returnCode = 1
      e.terminate()
    }


    /**************************************************************************/
    catch (e: Exception)
    {
      returnCode = 2
      Dbg.endOfRun()
      if (null != e.message) println(e.message)
      e.printStackTrace()
      var reportStack = Rpt.getStack()
      if (reportStack.isNotEmpty()) reportStack += ": "
      System.err.println("Fatal error: $reportStack$e")
      System.err.flush()
    }



    /**************************************************************************/
    /* If logging to a file, sort so that errors come out before warnings,
       etc. */

    finally
    {
      val moduleName = ConfigData["calcModuleName"] ?: "UNKNOWN MODULE"
      if (0 != returnCode) System.err.println("\n!!!!! RUN FAILED: " + args.joinToString(" "))
      Rpt.reportEol()
      Rpt.report(level = -1, ">>>>>>>>>> End of processing for $moduleName (${ConfigData["stepTargetAudience"]} use).")
      Rpt.report(level = -1, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n\n")

      if (File(FileLocations.getOutputFolderPath()).exists()) // If a given text can be used to generate both a public and a STEP module, the log files will get overwritten, so save copies in the _Output_X folders.
      {
        if (File(FileLocations.getConverterLogFilePath()).exists())
          StepFileUtils.copyFile(Paths.get(FileLocations.getOutputFolderPath(), FileLocations.getConverterLogFileName()).toString(), FileLocations.getConverterLogFilePath())

        if (File(FileLocations.getOsisToModLogFilePath()).exists())
          StepFileUtils.copyFile(Paths.get(FileLocations.getOutputFolderPath(), FileLocations.getOsisToModLogFileName()).toString(), FileLocations.getOsisToModLogFilePath())
      }

      FileLogger.close() // In case we've been logging to a file, which indeed will be the norm.

      exitProcess(returnCode)
    } // finally
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /**************************************************************************/
  /**
   * For use at the end of a run.  Returns major warnings as simulated large
   * characters to output to stderr.
   */

  private fun getMajorWarningsAsBigCharacters (): String
  {
    // The try below is required because if we start from OSIS, we won't have any reversification details.
    var res = ""
    try { if (!ConfigData["constExternalDataPath_ReversificationData"]!!.startsWith("http")) res += C_LocalReversificationData } catch (_: Exception) {}
    if (!ConfigData.getAsBoolean("calcEncrypted", "no")) res += C_NotEncrypted
    if (!ConfigData.getAsBoolean("calcUpIssued", "no")) res += C_NotUpIssued
    val issues = Issues.getRemedialActions()
    if (issues.isNotEmpty()) res += C_HaveYouChecked + "\n" + issues.joinToString("\n")
    return res
  }


  /****************************************************************************/
  // https://patorjk.com/software/taag/#p=display&f=Graffiti&t=Type%20Something%20  Font=Big.


  /****************************************************************************/
  private val C_HaveYouChecked = """
  _    _                                            _               _            _             
 | |  | |                                          | |             | |          | |            
 | |__| | __ ___   _____   _   _  ___  _   _    ___| |__   ___  ___| | _____  __| |            
 |  __  |/ _` \ \ / / _ \ | | | |/ _ \| | | |  / __| '_ \ / _ \/ __| |/ / _ \/ _` |            
 | |  | | (_| |\ V /  __/ | |_| | (_) | |_| | | (__| | | |  __/ (__|   <  __/ (_| |  _   _   _ 
 |_|  |_|\__,_| \_/ \___|  \__, |\___/ \__,_|  \___|_| |_|\___|\___|_|\_\___|\__,_| (_) (_) (_)
                            __/ |                                                              
                           |___/                                                               
  """



  /****************************************************************************/
private val C_LocalReversificationData ="""
   _      ____   _____          _        _____  ________      ________ _____   _____ _____ ______ _____ _____       _______ _____ ____  _   _ 
  | |    / __ \ / ____|   /\   | |      |  __ \|  ____\ \    / /  ____|  __ \ / ____|_   _|  ____|_   _/ ____|   /\|__   __|_   _/ __ \| \ | |
  | |   | |  | | |       /  \  | |      | |__) | |__   \ \  / /| |__  | |__) | (___   | | | |__    | || |       /  \  | |    | || |  | |  \| |
  | |   | |  | | |      / /\ \ | |      |  _  /|  __|   \ \/ / |  __| |  _  / \___ \  | | |  __|   | || |      / /\ \ | |    | || |  | | . ` |
  | |___| |__| | |____ / ____ \| |____  | | \ \| |____   \  /  | |____| | \ \ ____) |_| |_| |     _| || |____ / ____ \| |   _| || |__| | |\  |
  |______\____/ \_____/_/    \_\______| |_|  \_\______|   \/   |______|_|  \_\_____/|_____|_|    |_____\_____/_/    \_\_|  |_____\____/|_| \_|                                                                                                                                                                                                                    
      
"""


/******************************************************************************/
private val C_NotEncrypted = """
   _   _    ___    _____     _____   _   _    ____   ____   __   __  ____    _____   _____   ____
  | \ | |  / _ \  |_   _|   | ____| | \ | |  / ___| |  _ \  \ \ / / |  _ \  |_   _| | ____| |  _ \
  |  \| | | | | |   | |     |  _|   |  \| | | |     | |_) |  \ V /  | |_) |   | |   |  _|   | | | |
  | |\  | | |_| |   | |     | |___  | |\  | | |___  |  _ <    | |   |  __/    | |   | |___  | |_| |
  |_| \_|  \___/    |_|     |_____| |_| \_|  \____| |_| \_\   |_|   |_|       |_|   |_____| |____/
                       
"""

/******************************************************************************/
private val C_NotUpIssued = """
   _   _  ____ _______   _    _ _____             _____  _____ _____ _    _ ______ _____  
  | \ | |/ __ \__   __| | |  | |  __ \           |_   _|/ ____/ ____| |  | |  ____|  __ \ 
  |  \| | |  | | | |    | |  | | |__) |  ______    | | | (___| (___ | |  | | |__  | |  | |
  | . ` | |  | | | |    | |  | |  ___/  |______|   | |  \___ \\___ \| |  | |  __| | |  | |
  | |\  | |__| | | |    | |__| | |                _| |_ ____) |___) | |__| | |____| |__| |
  |_| \_|\____/  |_|     \____/|_|               |_____|_____/_____/ \____/|______|_____/ 
                       
"""
}
