/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils


/******************************************************************************/
/**
 * Converts OSIS supplied as input to internal OSIS.  At the time of writing,
 * that's kinda boring -- all it does is copy the data to the relevant folder.
 * And the only reason for this existing as a class in its own right is that
 * it gives a measure of uniformity to the processing chain.
 *
 * @author ARA "Jamie" Jamieson
 */

object FileCreator_InputOsisToInternalOsis: ProcessingChainElement
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
  override fun takesInputFrom(): Pair<String, String> = Pair(FileLocations.getInputOsisFolderPath(), FileLocations.getFileExtensionForOsis())


 /****************************************************************************/
  override fun pre ()
  {
    StepFileUtils.deleteFolder(FileLocations.getInternalOsisFolderPath())
  }


  /****************************************************************************/
  override fun process ()
  {
    StepFileUtils.createFolderStructure(FileLocations.getInternalOsisFolderPath())
    StepFileUtils.copyFile(FileLocations.getInternalOsisFilePath(), FileLocations.getInputOsisFilePath()!!)
  }
}