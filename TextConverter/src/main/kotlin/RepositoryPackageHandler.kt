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

object RepositoryPackageHandler: TextConverterProcessorBase()
 {
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner (): String
  {
    return "Generating package for repository"
  }


  /****************************************************************************/
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
  }


  /****************************************************************************/
  override fun pre (): Boolean
  {
    //deleteFile(Pair(StandardFileLocations.getRepositoryPackageFilePath(), null))
    return true
  }


  /****************************************************************************/
  override fun runMe (): Boolean
  {
    return "release" == ConfigData["stepRunType"]!!.lowercase()
  }


  /****************************************************************************/
  override fun process (): Boolean
  {
    createZip()
    return true
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun createZip()
  {
    val zipPath: String = StandardFileLocations.getRepositoryPackageFilePath()
    val inputs = mutableListOf(StandardFileLocations.getMetadataFolderPath(),
                               StandardFileLocations.getRawInputFolderPath(),
                               StandardFileLocations.getOsisFilePath(),
                               StandardFileLocations.getSwordZipFilePath(ConfigData["stepModuleName"]!!))
    Zip.createZipFile(zipPath, 9, null, inputs)
  }
}