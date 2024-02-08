/******************************************************************************/
package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.utils.OsisPhase1OutputDataCollection
import org.stepbible.textconverter.utils.X_DataCollection
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
  override fun process () = OsisPhase1OutputDataCollection.setText(File(FileLocations.getInputOsisFilePath()).readText())
}