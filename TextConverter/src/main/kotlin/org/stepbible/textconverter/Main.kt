/**********************************************************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.stepexception.StepBreakOutOfProcessing
import org.stepbible.textconverter.support.stepexception.StepException



/**********************************************************************************************************************/
/**
 * The converter main program.
 *
 * @param args Command line arguments.
 */

fun main (args: Array<String>)
{
  //Dbg.setBooksToBeProcessed("1Sa")
  //ThrowAwayCode.validateXmlFileStructure()
  //ThrowAwayCode.tryXslt()
  //ThrowAwayCode.convertNivToVL()


  try
  {
    mainCommon(args)
    val majorWarnings = ProcessingController.getMajorWarningsAsBigCharacters()
    if (majorWarnings.isNotEmpty())
    {
      print(majorWarnings)
      Logger.specialMessage(majorWarnings)
      Logger.announceAll(false)
    }

    Dbg.endOfRun()
    println("Finished\n")
  }
  catch (_: StepBreakOutOfProcessing)
  {
    Dbg.endOfRun()
  }
  catch (e: StepException)
  {
    Dbg.endOfRun()
    if (null != e.message) println(e.message)
    if (!e.getSuppressStackTrace()) e.printStackTrace()
  }
  catch (e: Exception)
  {
    Dbg.endOfRun()
    if (null != e.message) println(e.message)
    e.printStackTrace()
  }
}


/******************************************************************************/
private fun mainCommon (args: Array<String>)
{
  ProcessingController.process(args)
  Logger.summariseResults()
}


