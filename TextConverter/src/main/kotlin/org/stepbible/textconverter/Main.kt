/**********************************************************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.stepexception.StepBreakOutOfProcessing
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.utils.ThrowAwayCode



/**********************************************************************************************************************/
/**
 * The converter main program.
 *
 * @param args Command line arguments.
 */

fun main (args: Array<String>)
{
  /****************************************************************************/
  //Dbg.setBooksToBeProcessed("Hab")
  //ThrowAwayCode.testFindNodesByAttributeValue()



  /****************************************************************************/
  try
  {
    ProcessingController.process(args)
    Logger.summariseResults()

    val majorWarnings = ProcessingController.getMajorWarningsAsBigCharacters()
    if (majorWarnings.isNotEmpty())
    {
      print(majorWarnings)
      majorWarnings.split("\n").forEach { Logger.specialMessage(it) } // Split because otherwise we get double blank lines.
      Logger.announceAll(false)
    }

    Dbg.endOfRun()
    println("Finished\n")
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
  }



  /****************************************************************************/
  catch (e: Exception)
  {
    Dbg.endOfRun()
    if (null != e.message) println(e.message)
    e.printStackTrace()
  }
}

