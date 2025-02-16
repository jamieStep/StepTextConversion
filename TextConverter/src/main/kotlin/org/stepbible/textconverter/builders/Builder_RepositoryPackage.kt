package org.stepbible.textconverter.builders

import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepFileUtils
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Zip
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import java.nio.file.Paths

/******************************************************************************/
/**
 * Builds a repository package.
 *
 * @author ARA "Jamie" Jamieson
 */

object Builder_RepositoryPackage: Builder(), ObjectInterface
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
    CommandLineProcessor.CommandLineOption("stepUpdateReason", 1, "Where a new release is being made because of changes _we_ have decided are needed, the reason for the update.  Must have either or both of this and supplierUpdateReason.", null, "N/A", false),
    CommandLineProcessor.CommandLineOption("supplierUpdateReason", 1, "Where a new release is being made because of changes the _supplier_ has made, the reason they gave for their changes.  Must have either or both of this and stepUpdateReason.", null, "N/A", false)
  )


  /****************************************************************************/
  override fun doIt ()
  {
    /**************************************************************************/
    Builder_Module.process()
    Rpt.report(level = 0, banner())
    StepFileUtils.deleteTemporaryFiles(FileLocations.getRootFolderPath())



    /**************************************************************************/
    val inputImp  = if (FileLocations.getInputImpFilesExist())  FileLocations.getInputImpFolderPath()  else null
    val inputOsis = if (FileLocations.getInputOsisFileExists()) FileLocations.getInputOsisFolderPath() else null
    val inputUsx  = if (FileLocations.getInputUsxFilesExist())  FileLocations.getInputUsxFolderPath()  else null
    val inputVl   = if (FileLocations.getInputVlFilesExist())   FileLocations.getInputVlFolderPath()   else null

    if (null == inputOsis) throw StepExceptionWithStackTraceAbandonRun("No OSIS available to store in repository package.")



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
    StepFileUtils.deleteFile(FileLocations.getSharedConfigZipFilePath()) // Don't need this any more, now that it's in the zip file.
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
    val inputs = mutableListOf(Paths.get(FileLocations.getSharedConfigFolderPath(), "_Common_").toString())
    inputs.addAll(ConfigData.getSharedConfigFolderPathAccesses())
    Zip.createZipFile(zipPath, 9, FileLocations.getSharedConfigFolderPath(), inputs)
    return inputs.isNotEmpty()
  }
}