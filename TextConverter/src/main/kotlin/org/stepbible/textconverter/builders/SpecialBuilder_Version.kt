package org.stepbible.textconverter.builders

import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils.getJarFileName
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import kotlin.system.exitProcess


/******************************************************************************/
/**
  * Shows version number information for the JAR if requested to do so, and
  * exits.
  *
  * @author ARA "Jamie" Jamieson
  */

object SpecialBuilder_Version: SpecialBuilder(), ObjectInterface
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
  override fun doIt ()
  {
    if (ConfigData.getAsBoolean("stepVersion", "no"))
    {

      exitProcess(0)
    }
  }
}
