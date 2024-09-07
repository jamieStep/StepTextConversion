package org.stepbible.textconverter.builders

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepFileUtils
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionBase


/******************************************************************************/
/**
 * Arranges to turn whatever input is available to us into an initial OSIS
 * textual representation.
 *
 * We can accept a number of alternative forms of input.  This object
 * determines what is available, along with any user-defined overrides,
 * and invokes the appropriate builder.
 *
 * @author ARA "Jamie" Jamieson
 */

object Builder_InitialOsisRepresentationOfInputs: Builder()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "@Selecting type of input"
  override fun commandLineOptions () = null


  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Protected                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun doIt ()
  {
    Dbg.withReportProgressMain(banner()) {
      StepFileUtils.deleteFileOrFolder(FileLocations.getOutputFolderPath())
      determineInput().second.process()
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/
  /****************************************************************************/
  private fun determineInput (): Pair<String, BuilderRoot>
  {
    /**************************************************************************/
    val haveImp  = FileLocations.getInputImpFilesExist()
    val haveOsis = FileLocations.getInputOsisFileExists()
    val haveUsx  = FileLocations.getInputUsxFilesExist()
    val haveVl   = FileLocations.getInputVlFilesExist()

    val res =
      if (ConfigData.getAsBoolean("stepStartProcessFromOsis", "no"))
      {
        if (!haveOsis) throw StepExceptionBase("Requested to start from OSIS, but no OSIS exists.")
        Pair("osis", Builder_InitialOsisRepresentationFromOsis)
      }
      else if (haveUsx)
        Pair("usx", Builder_InitialOsisRepresentationFromUsx)
      else if (haveVl)
        Pair("vl", Builder_InitialOsisRepresentationFromVl)
      else if (haveImp)
        Pair("imp", Builder_InitialOsisRepresentationFromImp)
      else
        Pair("osis", Builder_InitialOsisRepresentationFromOsis)



    /**************************************************************************/
    val startFrom = res.first
    if ("osis" != startFrom && haveOsis)
    {
      if (StepFileUtils.getEarliestFileDate(FileLocations.getInputOsisFolderPath(), FileLocations.getFileExtensionForOsis()) >
          StepFileUtils.getLatestFileDate(FileLocations.getInputUsxFolderPath(), FileLocations.getFileExtensionForUsx()))
        Logger.warning("Starting from ${startFrom.uppercase()}, but the InputOsis data is later.")
    }


    if ("osis" == startFrom && haveUsx)
    {
      if (StepFileUtils.getEarliestFileDate(FileLocations.getInputUsxFolderPath(), FileLocations.getFileExtensionForUsx()) >
          StepFileUtils.getLatestFileDate(FileLocations.getInputOsisFolderPath(), FileLocations.getFileExtensionForOsis()))
        Logger.warning("Starting from OSIS, but the InputUsx data is later.")
    }


    if ("osis" == startFrom && haveVl)
    {
      if (StepFileUtils.getEarliestFileDate(FileLocations.getInputVlFolderPath(), FileLocations.getFileExtensionForVl()) >
          StepFileUtils.getLatestFileDate(FileLocations.getInputOsisFolderPath(), FileLocations.getFileExtensionForOsis()))
        Logger.warning("Starting from OSIS, but the InputVl data is later.")
    }

    ConfigData["stepOriginData"] = startFrom



    /**************************************************************************/
    return res
  }
}