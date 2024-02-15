/******************************************************************************/
package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.osisinputonly.Osis_Osis2modInterface
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.runCommand
import org.stepbible.textconverter.support.shared.FeatureIdentifier
import org.stepbible.textconverter.utils.*
import java.io.File
import java.nio.file.Paths
import java.util.*

/******************************************************************************/
/**
 * Main program which handles the conversion of the OSIS to a Sword module.
 *
 * @author ARA "Jamie" Jamieson
 */

object PE_Phase3_To_SwordModule : PE
 {
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "Converting OSIS to Sword"


  /****************************************************************************/
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
    commandLineProcessor.addCommandLineOption("manualOsis2mod", 0, "Run osis2mod manually (useful where osis2mod fails to complete under control of the converter).", null, "n", false)
    commandLineProcessor.addCommandLineOption("forceOsis2modType", 0, "Force a particular version of osis2mod to be used (as opposed to letting the converter decide).", listOf("crosswire", "step"), null, false)
    commandLineProcessor.addCommandLineOption("updateReason", 1, "A reason for creating this version of the module (required only if runType is Release and the release arises because of changes to the converter as opposed to a new release from he text suppliers).", null, "Unknown", false)
  }


  /****************************************************************************/
  override fun pre ()
  {
    StepFileUtils.deleteFolder(FileLocations.getInternalSwordFolderPath())
  }


  /****************************************************************************/
  override fun process ()
  {
    /**************************************************************************/
    if (!ConfigData.getAsBoolean("stepEncryptionRequired", "no")) Logger.warning("********** NOT ENCRYPTED **********")
    StepFileUtils.createFolderStructure(FileLocations.getInternalSwordFolderPath())
    PackageContentHandler.processPreOsis2mod()
    handleOsis2modCall()



    /**************************************************************************/
    /* Now would be a good time to abandon stuff if necessary, because after
       this we start updating history etc, and if that happens and there have,
       in fact, been errors, we'd need to work out how to roll back the history,
       which is a pain. */

    if (!checkOsis2ModLog())
      Logger.error("osis2mod has not reported success.")
    Logger.announceAll(true)



    /**************************************************************************/
    PackageContentHandler.processPostOsis2mod()
    getFileSizeIndicator()
    VersionAndHistoryHandler.appendHistoryLinesToStepConfigFile()
    createZip()
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun handleOsis2modCall ()
  {
    /**************************************************************************/
    /* Since there are occasions when we need to be able to run the relevant
       osis2mod command manually from the command line, and since it is always
       a pain to work out what the command line should look like, here is the
       information you need ...

         osis2mod.exe <outputPath> <osisFilePath> -v <versificationScheme> -z -c "<password>"

       where <outputPath> is eg ...\Sword\modules\texts\ztext\NIV2011 and
       <password> is a random string of letters, digits and selected special
       characters which is used as a password / encryption key when generating
       the module.

       You can optionally add '> logFile' to the end to redirect output.  And
       perhaps more useful is '> logfile 2>&1' which redirects both stdout and
       stderr to the same file.  But do that only if you are running direct from
       a command line, not if you are using the code here to run things under
       control of the converter -- there are problems with system utilities which
       mean this doesn't work properly -- see head-of-method comments to
       runCommand.
    */

    val usingStepOsis2Mod = "step" == ConfigData["stepOsis2modType"]!!
    val programName = if (usingStepOsis2Mod) ConfigData["stepStepOsis2ModFolderPath"]!! else ConfigData["stepCrosswireOsis2ModFolderPath"]!!
    val swordExternalConversionCommand: MutableList<String> = ArrayList()
    swordExternalConversionCommand.add("\"$programName\"")
    swordExternalConversionCommand.add("\"" + FileLocations.getSwordTextFolderPath() + "\"")
    swordExternalConversionCommand.add("\"" + FileLocations.getTempOsisFilePath() + "\"")

    if (usingStepOsis2Mod)
    {
      swordExternalConversionCommand.add("-V")
      swordExternalConversionCommand.add("\"" + FileLocations.getOsis2ModSupportFilePath() + "\"")
    }
    else
    {
      swordExternalConversionCommand.add("-v")
      swordExternalConversionCommand.add(ConfigData["stepVersificationScheme"]!!)
    }

    swordExternalConversionCommand.add("-z")

    val osis2modEncryptionKey = ConfigData["stepOsis2ModEncryptionKey"]
    if (null != osis2modEncryptionKey)
    {
      swordExternalConversionCommand.add("-c")
      swordExternalConversionCommand.add("\"" + osis2modEncryptionKey + "\"")
    }



    /**************************************************************************/
    /* If we have any grounds at all for giving up, now would be a good time to
       do it, before bothering with the remaining processing. */

    Logger.announceAll(true)
    Dbg.reportProgress("")



    /**************************************************************************/
    if (ConfigData.getAsBoolean("stepManualOsis2mod"))
    {
      val commandAsString = swordExternalConversionCommand.joinToString(" ") + " > ${FileLocations.getOsisToModLogFilePath()} 2>&1"
      MiscellaneousUtils.copyTextToClipboard(commandAsString)
      println("")
      println("The command to run osis2mod has been copied to the clipboard.  Open a plain vanilla command window and run it from there.")
      println("In case you need it, it is ...  $commandAsString")
      print("Hit ENTER here when osis2mod has completed: "); readlnOrNull()
    }
    else
    {
      runCommand("Running external postprocessing command for Sword: ", swordExternalConversionCommand, errorFilePath = FileLocations.getOsisToModLogFilePath())
      Dbg.reportProgress("osis2mod completed")
    }
  }


  /****************************************************************************/
  /* Checks the content of the OSIS log file to make sure it contains the word
     "SUCCESS", or to give an indication of just how bad things are. */

  private fun checkOsis2ModLog(): Boolean
  {
    val file = File(FileLocations.getOsisToModLogFilePath())
    if (!file.exists()) return false

    var errors = 0
    var fatals = 0
    var hadSuccess = false
    var info = 0
    var warnings = 0
    Logger.setPrefix("osis2mod")

    FileLocations.getInputStream(file.toString(), null)!!.bufferedReader().readLines().forEach {
      if (it.startsWith("WARNING(PARSE): SWORD does not search numeric entities"))
        return@forEach  // osis2mod doesn't like things like &#9999;, but apparently we need them and they do work.
      else if (it.startsWith("SUCCESS"))
      {
        Logger.info(it)
        hadSuccess = true
      }
      else if (it.contains("FATAL"))
      {
        ++fatals
        Logger.error(it)
      }
      else if (it.contains("ERROR"))
      {
        ++errors
        Logger.warning("Treated as a warning because osis2mod often overreacts: $it")
      }
      else if (it.contains("WARNING"))
      {
        ++warnings
        Logger.warning(it)
      }
      else if (it.contains("INFO("))
      {
        ++info
        Logger.info(it)
      }
    } // forEach

    Logger.setPrefix(null)

    if (fatals > 0)
      System.err.println("CAUTION: osis2mod.exe reports $fatals fatal error(s).  Please check the OSIS log file to see if the conversion to Sword format has worked.")
    else if (errors > 0)
      System.err.println("CAUTION: osis2mod.exe reports $errors error(s).  These have been treated here as non-fatal, because often they do not seem to reflect an actual problem, but please check the OSIS log file to see if the conversion to Sword format has worked.")
    else if (warnings > 0)
      System.err.println("CAUTION: osis2mod.exe reports $warnings warning(s).  Please check the OSIS log file to see if the conversion to Sword format has worked.")
    else if (!hadSuccess)
      System.err.println("CAUTION: osis2mod.exe has not reported success.  Please check the OSIS log file to see if the conversion to Sword format has worked.")

    return hadSuccess
  }


  /****************************************************************************/
  /* This is the _module_ zip, not the repository zip. */

  private fun createZip()
  {
    val zipPath: String = FileLocations.getSwordZipFilePath()
    val inputs = mutableListOf(FileLocations.getSwordConfigFolderPath(), FileLocations.getSwordTextFolderPath())
    if (StepFileUtils.fileOrFolderExists(FileLocations.getEncryptionAndBespokeOsisToModDataRootFolder())) inputs.add(FileLocations.getEncryptionAndBespokeOsisToModDataRootFolder())
    Zip.createZipFile(zipPath, 9, FileLocations.getInternalSwordFolderPath(), inputs)
  }


  /****************************************************************************/
  /* The Sword config file is supposed to give an indication of the module
     size (or at least, some size or other, and I'm assuming it's the module
     size). */

  private fun getFileSizeIndicator()
  {
    var size = File(Paths.get(FileLocations.getSwordTextFolderPath()).toString()).walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    size = ((size + 500) / 1000) * 1000 // Round to nearest 1000.
    ConfigData.put("stepModuleSize", size.toString(), true)
  }
}





/******************************************************************************/
object PackageContentHandler
{
  /****************************************************************************/
  fun processPreOsis2mod () = doIt(m_DataPreOsis2mod)
  fun processPostOsis2mod () = doIt(m_DataPostOsis2mod)


  /****************************************************************************/
  data class ProcessingDetails (val wantIt: () -> Boolean, val processor: ((String) -> Unit)?, val filePath: String)
  private fun doItAlways () = true
  private fun ifEncrypting () = ConfigData.getAsBoolean("stepEncryptionRequired")
  private fun ifUsingStepOsis2mod () = ConfigData["stepOsis2modType"]!!.lowercase() == "step"


  /****************************************************************************/
  private val m_DataPreOsis2mod = listOf(
    ProcessingDetails(::ifEncrypting,        ::encryptionDataHandler,  FileLocations.getEncryptionDataFilePath()),
    ProcessingDetails(::ifUsingStepOsis2mod, ::osis2modDataHandler,    FileLocations.getOsis2ModSupportFilePath()),
    ProcessingDetails(::doItAlways,          null,             FileLocations.getSwordConfigFolderPath()),
    ProcessingDetails(::doItAlways,          null,             Paths.get(FileLocations.getSwordTextFolderPath(), "dummyFile.txt").toString()),
  )


  /****************************************************************************/
  private val m_DataPostOsis2mod = listOf(
    ProcessingDetails(::doItAlways, null,                           FileLocations.getSwordZipFilePath()),
    ProcessingDetails(::doItAlways, ::featuresSummaryBibleStructureHandler, FileLocations.getTextFeaturesFilePath()),
    ProcessingDetails(::doItAlways, ::featuresSummaryRunParametersHandler,  FileLocations.getRunFeaturesFilePath()),
    ProcessingDetails(::doItAlways, ::osisSaver,                            FileLocations.makeInputOsisFilePath()),
    ProcessingDetails(::doItAlways, ::swordConfigFileHandler,               FileLocations.getSwordConfigFilePath()),
 )


  /****************************************************************************/
  private fun doIt (items: List<ProcessingDetails>)
  {
    items.filter{ it.wantIt() }.forEach{
      StepFileUtils.createFolderStructure(StepFileUtils.getParentFolderName(it.filePath))
      it.processor?.let { it1 -> it1(it.filePath) }
    }
  }


  /****************************************************************************/
  private fun osis2modDataHandler (filePath: String) = Osis_Osis2modInterface.instance().createSupportingDataIfRequired(filePath)
  private fun featuresSummaryBibleStructureHandler (filePath: String) = IssueAndInformationRecorder.processFeaturesSummaryBibleDetails(filePath, OsisTempDataCollection)
  private fun featuresSummaryRunParametersHandler (filePath: String) = IssueAndInformationRecorder.processFeaturesSummaryRunDetails(filePath)


  /****************************************************************************/
  /* Saves the OSIS to the InputOsis folder if appropriate ...

     If the original input for this run was OSIS, then we wish to retain that
     OSIS, and there is therefore nothing to do here.
  */

  private fun osisSaver (filePath: String)
  {
    val revisedName = "osis" + ConfigData["stepModuleName"]!! + ".xml"
    val revisedPath = Paths.get(FileLocations.getInputOsisFolderPath(), revisedName).toString()

    if ("osis" == ConfigData["stepOriginData"]!!) // We started from OSIS.  I simply need to convert the name to standard form.
    {
      val existingName = File(FileLocations.getInputOsisFilePath()!!).name
      if (!existingName.equals(revisedName, ignoreCase = true))
        StepFileUtils.renameFile(revisedPath, FileLocations.getInputOsisFilePath()!!)
    }

    else // Started from USX or OSIS.
    {
      StepFileUtils.deleteFileOrFolder(FileLocations.getInputOsisFolderPath())
      StepFileUtils.createFolderStructure(FileLocations.getInputOsisFolderPath())
      Dom.outputDomAsXml(OsisPhase2SavedDataCollection.getDocument(), revisedPath,null)
    }
  }


  /****************************************************************************/
  /* Obtains encryption data.

     There are two separate pieces of encryption data.  One is a longish
     random password which is passed to osis2mod; and the other is an
     encrypted form of this, which is stored in a special configuration file
     used by JSword when decrypting data.

     With offline STEP on Windows, this file needs to go into a special location
     within the user's home folder, and I do this here in the converter so as
     to avoid having to move it around manually.  With online STEP, I presume it
     also needs to go into a special location, but I don't know where that is.

     (I have a feeling that in fact it should go into the module's zip file, and
     that it will automatically be moved to the right place when the module is
     installed, but I remain unclear quite how to achieve that.)

     I also store a copy of this configuration file in the Metadata folder so
     that I can locate it easily and pass it to other people --
     Metadata/<moduleName>.conf. */

  private fun encryptionDataHandler (filePath: String)
  {
    /**************************************************************************/
    if (!ConfigData.getAsBoolean("stepEncryptionRequired"))
      return



    /**************************************************************************/
    val osis2modEncryptionKey = MiscellaneousUtils.generateRandomString(64)
    ConfigData.put("stepOsis2ModEncryptionKey", osis2modEncryptionKey, true)
    val obfuscationKey = "p0#8j..8jm@72k}28\$0-,j[\$lkoiqa#]"
    val stepEncryptionKey = MiscellaneousUtils.generateStepEncryptionKey(osis2modEncryptionKey, obfuscationKey)



    /**************************************************************************/
    /* Write the details to the file which controls encryption. */

    val writer = File(filePath).bufferedWriter()
    writer.write("[${ConfigData["stepModuleName"]!!}]"); writer.write("\n")
    writer.write("CipherKey=$stepEncryptionKey");        writer.write("\n")
    writer.write("STEPLocked=true");                     writer.write("\n")
    writer.close()
  }


  /****************************************************************************/
  /* Generates the Sword config file.  See https://crosswire.org/wiki/DevTools:conf_Files
     for more information. */

  private fun swordConfigFileHandler (filePath: String)
  {
    /**************************************************************************/
    swordConfigFileHandler_addCalculatedValuesToMetadata()



    /**************************************************************************/
    val configFile = File(filePath)
    VersionAndHistoryHandler.process()
    val lines = FileLocations.getInputStream(FileLocations.getSwordTemplateConfigFilePath(), null)!!.bufferedReader().readLines()
    val writer = configFile.bufferedWriter()



    /**************************************************************************/
    for (theLine in lines)
    {
      var line = theLine.trim()

      if ("\$includeCopyAsIsLines".equals(line, ignoreCase = true))
      {
        ConfigData.getCopyAsIsLines().forEach { writer.write(it); writer.write("\n")}
        continue
      }

      if ("\$includeChangeHistory".equals(line, ignoreCase = true))
      {
        VersionAndHistoryHandler.getHistoryLines().forEach { writer.write(it); writer.write("\n") }
        continue
      }

      if (line.startsWith("#!")) continue // Internal comment only.
      line = line.split("#!")[0].trim() // Remove any trailing comment.
      line = line.replace("@reversificationMap", m_ReversificationMap) // Could use ordinary dollar expansions here, but it's too slow because the map is so big.
      //Dbg.dCont(line, "stepOriginDataAdditionalInfo")
      writer.write(ConfigData.expandReferences(line, false)!!)
      writer.write("\n")
    }




    /**************************************************************************/
    writer.close()
  }


  /****************************************************************************/
  private fun swordConfigFileHandler_addCalculatedValuesToMetadata()
  {
    /**************************************************************************/
    var stepInfo = """
¬¬Sword module @(stepModuleName) created @(stepModuleCreationDate) (@(stepTextVersionSuppliedBySourceRepositoryOrOwnerOrganisation)).
¬@(stepThanks)
@(AddedValue)
"""

    stepInfo = stepInfo.replace("-", "&nbsp;")
      .replace("@(stepTextVersionSuppliedBySourceRepositoryOrOwnerOrganisation)", ConfigData["stepTextVersionSuppliedBySourceRepositoryOrOwnerOrganisation"] ?: "")
      .replace("@(stepTextModifiedDate)", ConfigData["stepTextModifiedDate"]!!)
      .replace("@(stepModuleName)", ConfigData["stepModuleName"]!!)
      .replace("@(stepThanks)", ConfigData["stepThanks"]!!)
      .replace("@(stepModuleCreationDate)", ConfigData["stepModuleCreationDate"]!!)
      //.replace("@(OsisSha256)", osisSha256)
      .replace("\n", "NEWLINE")

    stepInfo = stepInfo.replace("^¬*(&nbsp;)*\\s*\\u0001".toRegex(), "") // Get rid of entirely 'blank' lines.



    /**************************************************************************/
    val texts: MutableList<String> = ArrayList()
    if (ConfigData.getAsBoolean("stepAddedValueMorphology", "No")) texts.add(Translations.stringFormatWithLookup("V_AddedValue_Morphology"))
    if (ConfigData.getAsBoolean("stepAddedValueStrongs", "No")) texts.add(Translations.stringFormatWithLookup("V_AddedValue_Strongs"))



    /**************************************************************************/
    var text = ""
    if (texts.isNotEmpty())
    {
      text = Translations.stringFormatWithLookup("V_addedValue_AddedValue") + " "
      text += java.lang.String.join("; ", texts)
    }

    if (text.isNotEmpty()) text = "¬¬$text."
    stepInfo = stepInfo.replace("@(AddedValue)", text)



    /**************************************************************************/
    stepInfo = stepInfo.replace("¬", "<br>")
    stepInfo = ConfigData.expandReferences(stepInfo, false)!!
    stepInfo = stepInfo.replace("NEWLINE", "")
    ConfigData.put("stepConversionInfo", stepInfo, true)



    /**************************************************************************/
    ConfigData.put("stepDataPath", "./modules/texts/ztext/" + ConfigData["stepModuleName"] + "/", true)



    /**************************************************************************/
    var textSource = ConfigData["stepSourceRepositoryForText"] ?: ""
    if (textSource.isEmpty()) textSource = ConfigData["stepTextRepositoryOrganisationAbbreviatedName"] ?: ""
    if (textSource.isEmpty()) textSource = ConfigData["stepTextRepositoryOrganisationFullName"] ?: ""
    if (textSource.isEmpty()) textSource = "Unknown"

    var ownerOrganisation = ConfigData.getOrError("stepTextOwnerOrganisationFullName")
    if (ownerOrganisation.isNotEmpty()) ownerOrganisation = "&nbsp;&nbsp;Owning organisation: $ownerOrganisation"

    var textDisambiguatorForId = ConfigData.getOrError("stepDisambiguatorForId")
    if (textDisambiguatorForId.isBlank() || "unknown".equals(textDisambiguatorForId, ignoreCase = true)) textDisambiguatorForId = ""

    var textId: String = ConfigData["stepTextIdSuppliedBySourceRepositoryOrOwnerOrganisation"] ?: ""
    if (textId.isBlank() || "unknown".equals(textId, ignoreCase = true)) textId = ""

    val textCombinedId =
      if (textDisambiguatorForId.isNotEmpty() && textId.isNotEmpty())
        "$textDisambiguatorForId.$textId"
      else if (textId.isNotEmpty())
        "Version $textId"
      else
        ""

    textSource = "$textSource $ownerOrganisation $textCombinedId"
    ConfigData.put("stepTextSource", textSource, true)



    /**************************************************************************/
    /* For the sake of clarity and uniformity, ideally I'd save the
       reversificationMap as stepReversificationMap, and then simply go
       with the standard facilities for handling expansions.  Unfortunately,
       if I work that way, the reversificationMap will be expanded out into the
       element which contains it, and that element is then subject to @(...)
       expansion, which, on something as large as the reversificationMap can be,
       is horrendously slow.  So instead I simply arrange for
       stepReversificationMap to example to a special marker, and then
       I replace that marker with the designated value later. */

    m_ReversificationMap = swordConfigFileHandler_getReversificationMap(ReversificationData.getAllMoveGroups())
    ConfigData.put("stepReversificationMap", "@reversificationMap", true)



    /**************************************************************************/
    /* List of options taken from the documentation mentioned above.
       don't change the ordering here -- it's not entirely clear whether
       order matters, but it may do.

       Note, incidentally, that sometimes STEP displays an information button at
       the top of the screen indicating that the 'vocabulary feature' is not
       available.  This actually reflects the fact that the Strong's feature is
       not available in that Bible.

       I am not sure about the inclusion of OSISLemma below.  OSIS actually
       uses the lemma attribute of the w tag to record Strong's information,
       so I'm not clear whether we should have OSISLemma if lemma appears at
       all, even if only being used for Strong's; if it should be used if there
       are occurrences of lemma _not_ being used for Strong's; or if, in fact,
       it should be suppressed altogether.
    */

    var res = ""
    FeatureIdentifier.process(FileLocations.getTempOsisFilePath())
    if (FeatureIdentifier.hasLemma()) res += "GlobalOptionFilter=OSISLemma\n"
    if (FeatureIdentifier.hasMorphologicalSegmentation()) res += "GlobalOptionFilter=OSISMorphSegmentation\n"
    if (FeatureIdentifier.hasStrongs()) res += "GlobalOptionFilter=OSISStrongs\n"
    if (FeatureIdentifier.hasFootnotes()) res += "GlobalOptionFilter=OSISFootnotes\n"
    if (FeatureIdentifier.hasScriptureReferences()) res += "GlobalOptionFilter=OSISScriprefs\n" // Crosswire doc is ambiguous as to whether this should be plural or not.
    if (FeatureIdentifier.hasMorphology()) res += "GlobalOptionFilter=OSISMorph\n"
    if (FeatureIdentifier.hasNonCanonicalHeadings()) res += "GlobalOptionFilter=OSISHeadings\n"
    if (FeatureIdentifier.hasVariants()) res += "GlobalOptionFilter=OSISVariants\"\n"
    if (FeatureIdentifier.hasRedLetterWords()) res += "GlobalOptionFilter=OSISRedLetterWords\n"
    if (FeatureIdentifier.hasGlosses()) res += "GlobalOptionFilter=OSISGlosses\n"
    if (FeatureIdentifier.hasTransliteratedForms()) res += "GlobalOptionFilter=OSISXlit\n"
    if (FeatureIdentifier.hasEnumeratedWords()) res += "GlobalOptionFilter=OSISEnum\n"
    if (FeatureIdentifier.hasGlossaryLinks()) res += "GlobalOptionFilter=OSISReferenceLinks\n"
    if (FeatureIdentifier.hasStrongs()) res += "Feature=StrongsNumbers\n"
    if (!FeatureIdentifier.hasMultiVerseParagraphs()) res += "Feature=NoParagraphs\n"
    ConfigData.put("stepOptions", res, true)



    /**************************************************************************/
    ConfigData["stepInputFileDigests"] = Digest.makeFileDigests()
    ConfigData["stepDescription"] = ConfigData.makeStepDescription()
  }


  /****************************************************************************/
  /* More than a little complicated.  We have a list of mappings giving the
     source and standard verses which have been involved in reversification.

     We want to convert this into a list of mappings for display to the user.
     However, this could be a very _long_ list, and therefore perhaps unwieldy,
     so ideally it would be good to coalesce runs of verses into a single
     mapping.

     First off, there's no absolute guarantee this is ordered by ref, so we
     need to reorder based on the 'from' ref.

     Then I need to run though the list looking for adjacent references (which
     must be adjacent in terms of both the source and the standard reference);
     and then finally I need to output this lot in human-readable form.
  */

  private fun swordConfigFileHandler_getReversificationMap (data: List<ReversificationMoveGroup>): String
  {
    /**************************************************************************/
    if (data.isEmpty()) return ""



    /**************************************************************************/
    fun comparator (a: ReversificationMoveGroup, b: ReversificationMoveGroup): Int
    {
      var res = a.sourceRange.getLowAsRefKey().compareTo(b.sourceRange.getLowAsRefKey())
      if (0 == res) res = a.standardRange.getLowAsRefKey().compareTo(b.standardRange.getLowAsRefKey())
      return res
    }

    val mappings = data.sortedWith(::comparator)



    /**************************************************************************/
    val res = StringBuilder(100000)
    res.append("<p>The changes are as follows:<table>")



    /**************************************************************************/
    /* Convert the data into an HTML table, taking into account the fact that
       a value of zero for the verse number corresponds to a canonical title. */

    mappings.forEach {
      var sourceRef   = if (it.sourceRange  .isSingleReference()) it.sourceRange  .getLowAsRef().toString() else it.sourceRange  .toString()
      var standardRef = if (it.standardRange.isSingleReference()) it.standardRange.getLowAsRef().toString() else it.standardRange.toString()
      sourceRef   = sourceRef.  replace(":0", ":title")
      standardRef = standardRef.replace(":0", ":title")
      res.append("<tr><td>")
      res.append(sourceRef)
      res.append("</td><td>&#x25b6; ")
      res.append(standardRef)
      res.append("</td></tr>")
    }



    /**************************************************************************/
    res.append("</table>")
    return res.toString()
  }


  /****************************************************************************/
  private var m_ReversificationMap = ""
}
