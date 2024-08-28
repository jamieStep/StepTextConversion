package org.stepbible.textconverter.builders

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.getJarFileName
import kotlin.system.exitProcess


/******************************************************************************/
/**
  * Shows version number information for the JAR if requested to do so, and
  * exits.
  *
  * @author ARA "Jamie" Jamieson
  */

object SpecialBuilder_Version: SpecialBuilder
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
    CommandLineProcessor.CommandLineOption("version", 0, "Get version information.", null, null, false),
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
    if (ConfigData.getAsBoolean("stepVersion", "no"))
    {
      println("\n${getJarFileName()}: Version ${ConfigData["stepJarVersion"]!!}.\n")
      exitProcess(0)
    }
  }
}
