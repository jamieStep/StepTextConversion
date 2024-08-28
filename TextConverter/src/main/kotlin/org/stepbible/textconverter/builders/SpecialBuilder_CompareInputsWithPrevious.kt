package org.stepbible.textconverter.builders

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.ConfigDataSupport
import org.stepbible.textconverter.support.stepexception.StepBreakOutOfProcessing
import org.stepbible.textconverter.utils.Digest


/******************************************************************************/
/**
  * Checks to see if all we are doing is to compare digests of current inputs
  * with the previous digests stored in the Sword configuration file.
  *
  * @author ARA "Jamie" Jamieson
  */

object SpecialBuilder_CompareInputsWithPrevious: SpecialBuilder
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = ""
  override fun commandLineOptions () = listOf(
    CommandLineProcessor.CommandLineOption("checkInputsAgainstPreviousModule", 0, "Check whether the current inputs were used to build the existing module.", null, null, false),
  )





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun doIt ()
  {
    if (!ConfigData.getAsBoolean("stepCheckInputsAgainstPreviousModule", "no"))
      return

    Digest.checkFileDigests()
    throw StepBreakOutOfProcessing("")
  }
}
