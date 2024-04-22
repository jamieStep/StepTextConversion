package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.miscellaneous.Zip
import org.stepbible.textconverter.support.stepexception.StepException


/******************************************************************************/
/**
 * Generates a package for uploading to the STEP text repository.
 *
 * @author ARA "Jamie" Jamieson
 */

object PE_Phase4_To_RepositoryPackage: PE
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
  override fun pre () {} // No need to delete the repository file here, because earlier processing will have deleted the entire output folder structure.
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

    StepFileUtils.deleteTemporaryFiles(FileLocations.getRootFolderPath())

    val inputOsis = if (FileLocations.getInputOsisFileExists()) FileLocations.getInputOsisFolderPath() else null
    val inputUsx  = if (FileLocations.getInputUsxFilesExist())  FileLocations.getInputUsxFolderPath()  else null
    val inputVl   = if (FileLocations.getInputVlFilesExist())   FileLocations.getInputVlFolderPath()   else null

    if (null == inputOsis) throw StepException("No OSIS available to store in repository package.")

    val zipPath: String = FileLocations.getRepositoryPackageFilePath()
    val inputs = mutableListOf(FileLocations.getMetadataFolderPath(),
                               inputOsis,
                               inputUsx,
                               inputVl,
                               FileLocations.getTextFeaturesFolderPath(),
                               FileLocations.getSwordZipFilePath()).filterNotNull()
    Zip.createZipFile(zipPath, 9, null, inputs)
  }
}