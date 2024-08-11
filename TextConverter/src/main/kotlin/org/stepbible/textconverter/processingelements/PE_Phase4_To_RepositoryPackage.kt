package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.miscellaneous.Zip
import org.stepbible.textconverter.support.stepexception.StepException
import java.nio.file.Paths


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

   createSharedConfigZip()

    val zipPath: String = FileLocations.getRepositoryPackageFilePath()
    val inputs = mutableListOf(FileLocations.getMetadataFolderPath(),
                               FileLocations.getSharedConfigZipFilePath(),
                               inputOsis,
                               inputUsx,
                               inputVl,
                               FileLocations.getTextFeaturesFolderPath(),
                               FileLocations.getSwordZipFilePath()).filterNotNull()
    Zip.createZipFile(zipPath, 9, null, inputs)
  }


  /****************************************************************************/
  /* A zip file to contain the shared config data.  This is going to be a bit
     over the top, in that it will always contain all common shared data, such
     as the message-translation database, but I can't think of a better way of
     doing things. */

  private fun createSharedConfigZip()
  {
    val zipPath: String = FileLocations.getSharedConfigZipFilePath()
    val inputs = mutableListOf(Paths.get(FileLocations.getSharedConfigFolderPath(), "_Common_").toString())
    inputs.addAll(ConfigData.getSharedConfigFolderPathAccesses())
    Zip.createZipFile(zipPath, 9, FileLocations.getSharedConfigFolderPath(), inputs)
  }



}