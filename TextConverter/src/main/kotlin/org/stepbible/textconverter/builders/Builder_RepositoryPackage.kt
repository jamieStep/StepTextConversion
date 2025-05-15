package org.stepbible.textconverter.builders

import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigArchiver
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
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
 * Repository packages hold -- as far as possible -- absolutely everything
 * needed both to deploy a module and also
 *
 * The repository package itself is a single zip file, containing a number
 * of other zip files and individual files / folders as follows.  It will
 * probably be easiest if I used an actual example -- the text mar_MRCV,
 * which comes in both public and STEPBible-only forms.
 *
 * For the public version, the repository us forRepository_Mar_MRCV_public.zip.
 * (For the STEPBible-only version, 'public' is replaced by 'step'.)
 *
 * Within this we have:
 *
 * - InputOsis: A folder containing MarMRCV.xml.  This is an OSIS version of
 *   the input, in a suitable form to make available to third parties (should
 *   we wish to do so and have the necessary permissions), or to use as a
 *   basis of our own tagging, if we decide that it would be easier to apply
 *   tagging to OSIS rather than USX or whatever.
 *
 * - InputUsx: A folder containing all of the USX files (assuming that we
 *   are starting from USX).  One USX file per book.  In theory, if starting
 *   from something other than USX, there should be additional Input* folders
 *   as necessary, although at the time of writing I haven't tried this.
 *
 * - Mar_MRCV: A folder containing runFeatures.json and textFeatures.json.
 *   These two file characterise the inputs (for example, indicating if any
 *   elided verses are present in the raw data).  They are included purely
 *   for admin purposes, in case, for instance, we discover that some
 *   particular feature has not been handled correctly in the past, and want
 *   to locate all texts which exhibit that feature, so that we can rebuild
 *   their modules.
 *
 * - Metadata: The Metadata folder associated with the text.  This will always
 *   contain step.conf.  It may also contain eg metadata.xml / license.xml
 *   where we are working with DBL texts and these files are available.
 *
 * - Mar_MRCV.zip: The actual module -- the files we would deploy so that the
 *   module can be made available in STEPBible.
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
  override fun commandLineOptions(): List<CommandLineProcessor.CommandLineOption>?
  {
    return null
  }


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

    val inputIssuesList = if (StepFileUtils.fileOrFolderExists(FileLocations.getIssuesFilePath()) )FileLocations.getIssuesFilePath() else null

    if (null == inputOsis) throw StepExceptionWithStackTraceAbandonRun("No OSIS available to store in repository package.")



    /**************************************************************************/
    ConfigArchiver.createZip()



    /**************************************************************************/
    /* In the past I've been in the habit, with DBL files, of taking any
       odds and ends associated with the metadata and storing them under a
       Miscellaneous folder in the Metadata folder.  I don't think we really
       need this, and if I leave it there it will end up in the repository
       package where it will take up space and confuse things. */

    StepFileUtils.deleteFolder(Paths.get(FileLocations.getMetadataFolderPath(), "Miscellaneous").toString())



    /**************************************************************************/
    /* Save the current state of the log file, so we can include it in the
       repository package. */

    Logger.announceAllForRepositoryPackage()



    /**************************************************************************/
    val zipPath: String = FileLocations.getRepositoryPackageFilePath()
    val inputs = mutableListOf(FileLocations.getMetadataFolderPath(),
                               FileLocations.getNewArchivedConfigZipFilePath(),
                               inputOsis,
                               inputUsx,
                               inputVl,
                               inputImp,
                               inputIssuesList,
                               FileLocations.getTextFeaturesFolderPath(),
                               FileLocations.getConverterLogFilePath(),
                               FileLocations.getOsisToModLogFilePath(),
                               FileLocations.getSwordZipFilePath()).filterNotNull()
    Zip.createZipFile(zipPath, 9, null, inputs)
    StepFileUtils.deleteFile(FileLocations.getNewArchivedConfigZipFilePath())
  }
}