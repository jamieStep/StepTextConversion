package org.stepbible.textconverter.builders

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.ConfigDataSupport


/******************************************************************************/
/**
  * Loads and checks the stepDbgConfigData, and, depending upon the value, may
  * just output an outline step.conf file.
  *
  * @author ARA "Jamie" Jamieson
  */

object SpecialBuilder_ConfigDataDebugging: SpecialBuilder
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
    CommandLineProcessor.CommandLineOption("dbgConfigData", 1, "Controls config data debugging.   Use generateStepConfig[All] / reportSet / reportMissingDebugInfo.",null, null, false),
  )





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun doIt () = ConfigDataSupport.initialise(ConfigData["stepDbgConfigData"])
}
