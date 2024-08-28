/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.builders.Builder_Root
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.stepexception.StepBreakOutOfProcessing
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.utils.ThrowAwayCode
import kotlin.system.exitProcess


/******************************************************************************/
public const val C_JarFileName = "TextConverter"
public const val C_ReleaseVersion = "1.0"
public const val C_TestSubversion = ""


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



  /****************************************************************************/
  var returnCode = 0
  try
  {
    Builder_Root.process(args)
    //ProcessingController.process(args)
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
  }



  /****************************************************************************/
  catch (_: StepBreakOutOfProcessing)
  {
    Dbg.endOfRun()
  }



  /****************************************************************************/
  catch (e: StepException)
  {
    Dbg.endOfRun()
    if (null != e.message) println(e.message)
    if (!e.getSuppressStackTrace()) e.printStackTrace()
    System.err.println("Fatal error: " + Builder_Root.getProcessorName() + ": " + e.toString())
    System.err.flush()
    returnCode = 1
  }



  /****************************************************************************/
  catch (e: Exception)
  {
    Dbg.endOfRun()
    if (null != e.message) println(e.message)
    e.printStackTrace()
    System.err.println("Fatal error: " + Builder_Root.getProcessorName() + ": " + e.toString())
    System.err.flush()
    returnCode = 1
  }



  /****************************************************************************/
  /* If logging to a file, sort so that errors come out before warnings, etc. */

  finally
  {
    Logger.sortLogFile()
    exitProcess(returnCode)
  }
}


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




