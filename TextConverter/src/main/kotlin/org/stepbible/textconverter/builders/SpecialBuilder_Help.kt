package org.stepbible.textconverter.builders

import org.stepbible.textconverter.support.bibledetails.VersificationSchemesSupportedByOsis2mod
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.stepexception.StepBreakOutOfProcessing
import org.stepbible.textconverter.utils.*
import java.io.File


/******************************************************************************/
/**
  * Shows help information if requested to do so, and exits.
  *
  * @author ARA "Jamie" Jamieson
  */

object SpecialBuilder_Help: SpecialBuilder
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
    CommandLineProcessor.CommandLineOption("help", 0, "Get help information.", null, null, false),
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
    if (ConfigData.getAsBoolean("help", "no"))
      CommandLineProcessor.showHelpAndExit()
  }
}
