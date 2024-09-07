package org.stepbible.textconverter.builders

import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.applicationspecificutils.Digest
import kotlin.system.exitProcess


/******************************************************************************/
/**
  * Checks to see if all we are doing is to compare digests of current inputs
  * with the previous digests stored in the Sword configuration file.
  *
  * @author ARA "Jamie" Jamieson
  */

object SpecialBuilder_CompareInputsWithPrevious: SpecialBuilder()
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
  override fun doIt ()
  {
    if (!ConfigData.getAsBoolean("stepCheckInputsAgainstPreviousModule", "no"))
      return

    Digest.checkFileDigests()
    exitProcess(0)
  }
}
