/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.builders.Builder_Master
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionBase
import kotlin.system.exitProcess


/******************************************************************************/
/**
* Main program.
*
* @author ARA "Jamie" Jamieson
*/


/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                                  Public                                  **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/**
 * The converter main program.
 *
 * @param args Command line arguments.
 */

fun main (args: Array<String>)
{
  /****************************************************************************/
  //Dbg.setBooksToBeProcessed("3Jn")
  //ThrowAwayCode.testGetVersion()



  /****************************************************************************/
  var returnCode = 1
  try
  {
    Builder_Master.process(args)
    Logger.summariseResults()

    val majorWarnings = getMajorWarningsAsBigCharacters()
    if (majorWarnings.isNotEmpty())
    {
      print(majorWarnings)
      majorWarnings.split("\n").forEach { Logger.specialMessage(it) } // Split because otherwise we get double blank lines.
      Logger.announceAll(false)
    }

    Dbg.endOfRun()
    println("\nFinished\n")

    returnCode = 0
  }



  /****************************************************************************/
  catch (e: StepExceptionBase)
  {
    e.terminate()
  }


  /****************************************************************************/
  catch (e: Exception)
  {
   Dbg.endOfRun()
    if (null != e.message) println(e.message)
    e.printStackTrace()
    System.err.println("Fatal error: " + Dbg.getActiveProcessingId() + ": " + e.toString())
    System.err.flush()
    returnCode = 1
  }



  /****************************************************************************/
  /* If logging to a file, sort so that errors come out before warnings, etc. */

  finally
  {
    val moduleName = ConfigData["stepModuleName"] ?: "UNKNOWN MODULE"
    val targetAudience = ConfigData["stepTargetAudience"] ?: "step"
    if (0 != returnCode) Dbg.reportProgress("\n!!!!! RUN FAILED: " + args.joinToString(" "))
    Dbg.reportProgress("\n>>>>>>>>>> End of processing for $moduleName (${ConfigData["stepReadableTargetAudience"]} use).")
    Dbg.reportProgress(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n\n")

    Logger.sortLogFile()
    exitProcess(returnCode)
  }
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                                 Private                                  **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/****************************************************************************/
/**
 * For use at the end of a run.  Returns major warnings as simulated large
 * characters to output to stderr.
 */

private fun getMajorWarningsAsBigCharacters (): String
{
  // The try below is required because if we start from OSIS, we won't have any reversification details.
  var res = ""
  try { if (!ConfigData["stepExternalDataPath_ReversificationData"]!!.startsWith("http")) res += C_Local_ReversificationData } catch (_: Exception) {}
  if (!ConfigData.getAsBoolean("stepEncrypted", "no")) res += C_NotEncrypted
  if (!ConfigData.getAsBoolean("stepUpIssued", "no")) res += C_NotUpIssued
  return res
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
private const val C_NotEncrypted = """
   _   _    ___    _____     _____   _   _    ____   ____   __   __  ____    _____   _____   ____
  | \ | |  / _ \  |_   _|   | ____| | \ | |  / ___| |  _ \  \ \ / / |  _ \  |_   _| | ____| |  _ \
  |  \| | | | | |   | |     |  _|   |  \| | | |     | |_) |  \ V /  | |_) |   | |   |  _|   | | | |
  | |\  | | |_| |   | |     | |___  | |\  | | |___  |  _ <    | |   |  __/    | |   | |___  | |_| |
  |_| \_|  \___/    |_|     |_____| |_| \_|  \____| |_| \_\   |_|   |_|       |_|   |_____| |____/
                       
"""

/******************************************************************************/
private const val C_NotUpIssued = """
   _   _  ____ _______   _    _ _____             _____  _____ _____ _    _ ______ _____  
  | \ | |/ __ \__   __| | |  | |  __ \           |_   _|/ ____/ ____| |  | |  ____|  __ \ 
  |  \| | |  | | | |    | |  | | |__) |  ______    | | | (___| (___ | |  | | |__  | |  | |
  | . ` | |  | | | |    | |  | |  ___/  |______|   | |  \___ \\___ \| |  | |  __| | |  | |
  | |\  | |__| | | |    | |__| | |                _| |_ ____) |___) | |__| | |____| |__| |
  |_| \_|\____/  |_|     \____/|_|               |_____|_____/_____/ \____/|______|_____/ 
                       
"""




