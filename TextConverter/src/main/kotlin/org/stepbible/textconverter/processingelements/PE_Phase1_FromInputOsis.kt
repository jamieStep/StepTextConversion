/******************************************************************************/
package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.utils.Phase1TextOutput
import java.io.File


/******************************************************************************/
/**
 * Takes data from InputOsis.
 *
 * The result forms the text element of [OsisPhase1OutputDataCollection].  Note
 * that it is *not* fed into the parsed data structures of that item, nor its
 * associated BibleStructure.
 *
 * @author ARA "Jamie" Jamieson
 */

object PE_Phase1_FromInputOsis: PE
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
  override fun getCommandLineOptions(commandLineProcessor: CommandLineProcessor) {}
  override fun pre () { }


  /****************************************************************************/
  override fun process () { Phase1TextOutput = File(FileLocations.getInputOsisFilePath()).readText() }
}