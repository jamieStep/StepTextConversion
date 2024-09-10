package org.stepbible.textconverter.builders

import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigDataSupport


/******************************************************************************/
/**
  * Loads and checks the stepDbgConfigData, and, depending upon the value, may
  * just output an outline step.conf file.
  *
  * @author ARA "Jamie" Jamieson
  */

object SpecialBuilder_ConfigDataDebugging: SpecialBuilder()
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
    CommandLineProcessor.CommandLineOption("dbgConfigData", 1, "Controls config data debugging.   Use generateStepConfig[All] to generate a template step.config / reportSet to give details of what is set where / reportMissingDebugInfo to check the program has a comprehensive list of config parameters.",listOf("generateStepConfig", "generateStepConfigAll", "reportSet", "reportMissingDebugInfo"), null, false),
  )


  /****************************************************************************/
  override fun doIt () = ConfigDataSupport.initialise(ConfigData["stepDbgConfigData"])
}
