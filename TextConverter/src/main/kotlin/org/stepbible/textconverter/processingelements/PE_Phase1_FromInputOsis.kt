/******************************************************************************/
package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.utils.OsisPhase1OutputDataCollection
import org.stepbible.textconverter.utils.Z_DataCollection


/******************************************************************************/
/**
 * Converts OSIS supplied as input to internal OSIS.  At the time of writing,
 * that's kinda boring -- all it does is copy the data to the relevant folder.
 * And the only reason for this existing as a class in its own right is that
 * it gives a measure of uniformity to the processing chain.
 *
 * Actually, the above is not quite correct.  The data is either copied to the
 * InternalOsis folder or not.  But either way, it ends up in _XmlData.
 * (I anticipate eventually deciding not to bother with the file-copy.)
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
  override fun pre () = StepFileUtils.deleteFolder(FileLocations.getInternalOsisFolderPath())


  /****************************************************************************/
  override fun process ()
  {
    OsisPhase1OutputDataCollection.recordDataFormat(Z_DataCollection.DataFormats.OsisPure)
    OsisPhase1OutputDataCollection.addFromFile(FileLocations.getInputOsisFilePath()!!, false)
  }
}