package org.stepbible.textconverter

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.miscellaneous.Zip

/******************************************************************************/
/**
 * Generates a package for uploading to the STEP text repository.
 *
 * @author ARA "Jamie" Jamieson
 */

object TextConverterProcessorRepositoryPackageHandler: TextConverterProcessor
 {
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "Generating package for repository"
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor) {}
  override fun prepare () {}
  override fun process () = doIt()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun doIt()
  {
    if ("release" != ConfigData["stepRunType"]!!.lowercase()) return

    val inputOsis = if (StandardFileLocations.getInputUsxFilesExist()) StandardFileLocations.getInputUsxFolderPath() else null
    val inputUsx = if (null == StandardFileLocations.getInputVlFilePath()) null else StandardFileLocations.getInputVlFolderPath()
    val inputVl  = if (null == StandardFileLocations.getInputVlFilePath()) null else StandardFileLocations.getInputVlFolderPath()

    val zipPath: String = StandardFileLocations.getRepositoryPackageFilePath()
    val inputs = mutableListOf(StandardFileLocations.getMetadataFolderPath(),
                               inputOsis,
                               inputUsx,
                               inputVl,
                               StandardFileLocations.getInternalOsisFilePath(),
                               StandardFileLocations.getSwordZipFilePath(ConfigData["stepModuleName"]!!)).filterNotNull()
    Zip.createZipFile(zipPath, 9, null, inputs)
  }
}