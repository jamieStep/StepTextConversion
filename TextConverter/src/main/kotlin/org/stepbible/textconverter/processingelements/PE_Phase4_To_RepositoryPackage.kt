package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.miscellaneous.StepStringUtils
import org.stepbible.textconverter.support.miscellaneous.Zip
import org.stepbible.textconverter.support.stepexception.StepException
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
  override fun process () = doIt()
  override fun pre () {}





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
    createRepositoryPackage()
  }

  /****************************************************************************/
  private fun createRepositoryPackage ()
  {
    if ("release" != ConfigData["stepRunType"]!!.lowercase()) return

    val inputOsis = if (FileLocations.getInputOsisFileExists()) FileLocations.getInputOsisFolderPath() else null
    val inputUsx  = if (FileLocations.getInputUsxFilesExist()) FileLocations.getInputUsxFolderPath()   else null
    val inputVl   = if (FileLocations.getInputVlFilesExist()) FileLocations.getInputVlFolderPath()     else null

    if (null == inputOsis) throw StepException("No OSIS available to store in repository package.")

    makeReadMe(inputVlExists = null != inputVl, inputUsxExists = null != inputUsx)

    val zipPath: String = FileLocations.getRepositoryPackageFilePath()
    val inputs = mutableListOf(FileLocations.getMetadataFolderPath(),
                               inputOsis,
                               inputUsx,
                               inputVl,
                               FileLocations.getRepositoryReadMeFilePath(),
                               FileLocations.getTextFeaturesFolderPath(),
                               FileLocations.getSwordZipFilePath()).filterNotNull()
    Zip.createZipFile(zipPath, 9, null, inputs)
  }


  /****************************************************************************/
  private fun makeReadMe (inputVlExists: Boolean, inputUsxExists: Boolean)
  {
    val inputVl  = if (inputVlExists)  "InputVl"  else null
    val inputUsx = if (inputUsxExists) "InputUsx" else null
    val alternativeFormat = inputVl ?: inputUsx

    val dateTimeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm").format(Date())

    val originalSource = "Input" + StepStringUtils.sentenceCaseFirstLetter(ConfigData["stepOriginData"]!!)

    val indirectly = if (null == ConfigData["stepOriginDataAdditionalInfo"]) " indirectly " else ""

    var indirectlyExplanation: String? = null
    if (indirectly.isNotEmpty())
      indirectlyExplanation = """(Indirectly because this input was previously pre-processed to create
                                 USX amenable to processing, and the datestamps suggested that that
                                 USX was ok to use.  If you believe this not to be the case, re-run
                                 the converter using '-startProcessFrom original' (not 'original+').
                                 """.trimIndent()

    var osisExplanation: String? = null
    if ("OSIS" == ConfigData["stepOriginData"]!! && null != alternativeFormat)
      osisExplanation = """Originally this module was probably built from the data in $alternativeFormat.  However,
                           this run began with the data in InputOSIS.
      """.trimMargin()

    File(FileLocations.getRepositoryReadMeFilePath()).printWriter().use {
      it.println("This package was generated at $dateTimeStamp, ${indirectly}from the data in $originalSource.\n")
      if (null != indirectlyExplanation) it.println(indirectlyExplanation)
      if (null != osisExplanation) it.println(osisExplanation)
      it.println("If you need to regenerate the module at some point by manually revising the OSIS,")
      it.println("use the OSIS which you will find here in InputOsis.\n")

      if (StepFileUtils.fileOrFolderExists(FileLocations.getTextFeaturesFolderPath()))
        it.println("The textFeatures folder contains information about the text and about the run which generated this module.\n")

      it.println("The zip file is the module zip file.")
    }
  }
}