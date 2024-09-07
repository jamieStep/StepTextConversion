package org.stepbible.textconverter.builders

import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepFileUtils
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Zip
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionBase
import java.nio.file.Paths

/******************************************************************************/
/**
 * Builds a repository package.
 *
 * @author ARA "Jamie" Jamieson
 */

object Builder_RepositoryPackage: Builder()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "Creating repository package"


  /****************************************************************************/
  override fun commandLineOptions () = listOf(
    CommandLineProcessor.CommandLineOption("stepUpdateReason", 1, "Where a new release is being made because of changes _we_ have decided are needed, the reason for the update.  Must have either or both of this and supplierUpdateReason.", null, "Unknown", false),
    CommandLineProcessor.CommandLineOption("supplierUpdateReason", 1, "Where a new release is being made because of changes the _supplier_ has made, the reason they gave for their changes.  Must have either or both of this and stepUpdateReason.", null, "Unknown", false)
  )


  /****************************************************************************/
  override fun doIt ()
  {
    Builder_Module.process()
    Dbg.withReportProgressMain(banner(), ::doIt1)
  }


  /****************************************************************************/
  private fun doIt1 ()
  {
    /**************************************************************************/
    StepFileUtils.deleteTemporaryFiles(FileLocations.getRootFolderPath())



    /**************************************************************************/
    val inputImp  = if (FileLocations.getInputImpFilesExist())  FileLocations.getInputImpFolderPath()  else null
    val inputOsis = if (FileLocations.getInputOsisFileExists()) FileLocations.getInputOsisFolderPath() else null
    val inputUsx  = if (FileLocations.getInputUsxFilesExist())  FileLocations.getInputUsxFolderPath()  else null
    val inputVl   = if (FileLocations.getInputVlFilesExist())   FileLocations.getInputVlFolderPath()   else null

    if (null == inputOsis) throw StepExceptionBase("No OSIS available to store in repository package.")



    /**************************************************************************/
    val haveSharedConfig = createSharedConfigZip()



    /**************************************************************************/
    val zipPath: String = FileLocations.getRepositoryPackageFilePath()
    val inputs = mutableListOf(FileLocations.getMetadataFolderPath(),
                               if (haveSharedConfig) FileLocations.getSharedConfigZipFilePath() else null,
                               inputOsis,
                               inputUsx,
                               inputVl,
                               inputImp,
                               FileLocations.getTextFeaturesFolderPath(),
                               FileLocations.getSwordZipFilePath()).filterNotNull()
    Zip.createZipFile(zipPath, 9, null, inputs)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* A zip file to contain the shared config data.  This is going to be a bit
     over the top, in that it will always contain all common shared data, such
     as the message-translation database, but I can't think of a better way of
     doing things. */

  private fun createSharedConfigZip (): Boolean
  {
    val zipPath: String = FileLocations.getSharedConfigZipFilePath()
    val inputs = if (null == FileLocations.getSharedConfigFolderPath()) mutableListOf() else mutableListOf(Paths.get(FileLocations.getSharedConfigFolderPath(), "_Common_").toString())
    inputs.addAll(ConfigData.getSharedConfigFolderPathAccesses())
    Zip.createZipFile(zipPath, 9, FileLocations.getSharedConfigFolderPath(), inputs)
    return inputs.isNotEmpty()
  }
}