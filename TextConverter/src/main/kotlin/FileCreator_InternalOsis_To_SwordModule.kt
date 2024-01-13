/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.support.shared.SharedData
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.runCommand
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.miscellaneous.Translations
import org.stepbible.textconverter.support.miscellaneous.Zip
import java.io.File
import java.nio.file.Paths
import java.util.*

/******************************************************************************/
/**
 * Main program which handles the conversion of the OSIS to a Sword module.
 *
 * @author ARA "Jamie" Jamieson
 */

object FileCreator_InternalOsis_To_SwordModule : ProcessingChainElement
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
  override fun takesInputFrom(): Pair<String, String> = Pair(FileLocations.getInternalOsisFolderPath(), FileLocations.getFileExtensionForOsis())


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
    StepFileUtils.deleteFolder(FileLocations.getInternalTempOsisFolderPath())
  }


  /****************************************************************************/
  override fun process ()
  {
    /**************************************************************************/
    StepFileUtils.createFolderStructure(FileLocations.getInternalSwordFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getSwordConfigFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getInternalSwordFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getTextFeaturesFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getInternalTempOsisFolderPath())



    /**************************************************************************/
    /* Apply any tweaks.  The resulting file is written to a temporary
       location, so we need to pick it up from there. */

    OsisTweaker.process(FileLocations.getInternalOsisFilePath())
    m_OsisFilePath = FileLocations.getInternalTempOsisFilePath()



    /**************************************************************************/
    StepFileUtils.createFolderStructure(FileLocations.getSwordTextFolderPath())
    generateEncryptionData()
    if (!ConfigData.getAsBoolean("stepEncryptionApplied", "no")) Logger.warning("********** NOT ENCRYPTED **********")



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
    swordExternalConversionCommand.add("\"" + m_OsisFilePath + "\"")

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



    /**************************************************************************/
    /* Again, now would be a good time to abandon stuff, because after this
       point we start updating history etc, and if that happens and there have,
       in fact, been errors, we'd need to work out how to roll back the history,
       which is a pain. */

    if (!checkOsis2ModLog())
      Logger.error("osis2mod has not reported success.")
    Logger.announceAll(true)



    /**************************************************************************/
    getFileSizeIndicator()
    generateSwordConfigFile()
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
  private fun addCalculatedValuesToMetadata()
  {
    /**************************************************************************/
    /* Deal with reversification details. */

    ConfigData.put("stepNoOfVersesAmendedByReversification", SharedData.VersesAmendedByReversification.size.toString(), true)
    ConfigData.put("stepNoOfVersesToWhichReversificationSimplyAddedNotes", SharedData.VersesToWhichReversificationSimplyAddedNotes.size.toString(), true)



    /**************************************************************************/
    val osisFilePath = File(m_OsisFilePath)
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
    /* $$$ The ValueAddedSupplier line below is the way to go in future.  I'm
       not changing the others yet, because the processing upon which they rely
       is likely to change. */

    val texts: MutableList<String> = ArrayList()
    val x = ValueAddedSupplier.getConsolidatedDetailsForStepAbout(); if (x.isNotEmpty()) texts.add(x)

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

    m_ReversificationMap = getReversificationMap(ReversificationData.getAllMoveGroups())
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
    val featureIdentifier = SharedData.SpecialFeatures
    featureIdentifier.process(osisFilePath.toString())
    // featureIdentifier.setFlagsToEnableVocabularyFeature(true);
    if (featureIdentifier.hasLemma()) res += "GlobalOptionFilter=OSISLemma\n"
    if (featureIdentifier.hasMorphologicalSegmentation()) res += "GlobalOptionFilter=OSISMorphSegmentation\n"
    if (featureIdentifier.hasStrongs()) res += "GlobalOptionFilter=OSISStrongs\n"
    if (featureIdentifier.hasFootnotes()) res += "GlobalOptionFilter=OSISFootnotes\n"
    if (featureIdentifier.hasScriptureReferences()) res += "GlobalOptionFilter=OSISScriprefs\n" // Crosswire doc is ambiguous as to whether this should be plural or not.
    if (featureIdentifier.hasMorphology()) res += "GlobalOptionFilter=OSISMorph\n"
    if (featureIdentifier.hasNonCanonicalHeadings()) res += "GlobalOptionFilter=OSISHeadings\n"
    if (featureIdentifier.hasVariants()) res += "GlobalOptionFilter=OSISVariants\"\n"
    if (featureIdentifier.hasRedLetterWords()) res += "GlobalOptionFilter=OSISRedLetterWords\n"
    if (featureIdentifier.hasGlosses()) res += "GlobalOptionFilter=OSISGlosses\n"
    if (featureIdentifier.hasTransliteratedForms()) res += "GlobalOptionFilter=OSISXlit\n"
    if (featureIdentifier.hasEnumeratedWords()) res += "GlobalOptionFilter=OSISEnum\n"
    if (featureIdentifier.hasGlossaryLinks()) res += "GlobalOptionFilter=OSISReferenceLinks\n"
    //if (!"None".equals(reversificationType)) res += "GlobalOptionFilter=Reversification" + reversificationType.substring(0, 1).toUpperCase() + reversificationType.substring(1) + "\n";
    if (featureIdentifier.hasStrongs()) res += "Feature=StrongsNumbers\n"
    if (!featureIdentifier.hasMultiVerseParagraphs()) res += "Feature=NoParagraphs\n"
    ConfigData.put("stepOptions", res, true)



    /**************************************************************************/
    ConfigData["stepInputFileDigests"] = DigestHandler.makeFileDigests()



    /**************************************************************************/
    val header = arrayOf(
      "################################################################################",
      "#",
      "# Special features :-",
      "#"
    )

    ConfigData.put("stepSpecialFeatures", "", true) // Set to empty string in case the processing below doesn't generate any special features.
    val additionalFeatures: Map<String, String> = featureIdentifier.getSpecialFeatures()
    if (additionalFeatures.isNotEmpty())
    {
      val lines: MutableList<String> = ArrayList()
      for (i in header.indices) lines.add(header[i])
      additionalFeatures.keys.stream().sorted().forEach { lines.add("#   $it: ${additionalFeatures[it]}") }
      lines.add("#\n")
      lines.add("################################################################################\n\n\n")
      ConfigData.put("stepSpecialFeatures", java.lang.String.join("", lines), true)
    }



    /**************************************************************************/
    ConfigData.put("stepDescription", ConfigData.makeStepDescription(), true)
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
  /* The module structure comprises ...

     - A folder which contains the Sword configuration file.

     - A folder which contains the text.

     - A folder which contains the encryption data (if the module is encrypted).

     - A folder which contains the JSON file needed to support our bespoke
       osis2mod (if required).

     Of these, all but the final one are created in the appropriate location.

     The final one is created in Internal_E_FilesForRepositoryEtc and is
     retained there in case we do later builds starting from OSIS, in which
     case we'd need to pick up this earlier copy.

     I need to copy the file across to the right place, rename it to reflect
     the name of the module, and alter its contents so that selected places
     (of which at the time of writing there is only one) also contain
     something which reflects the module name. */

  private fun copyModuleInputsToModuleStructure ()
  {
    if (!StepFileUtils.fileOrFolderExists(FileLocations.getMasterOsis2ModSupportFilePath())) return
    StepFileUtils.createFolderStructure((FileLocations.getOsis2ModSupportFolderPath()))

    fun alterText (line: String): String = line.replace("toBeRenamed", ConfigData["stepVersificationScheme"]!!)
    StepFileUtils.copyFileWithChanges(FileLocations.getOsis2ModSupportFilePath(), FileLocations.getMasterOsis2ModSupportFilePath(), ::alterText)
  }


  /****************************************************************************/
  /* This is the _module_ zip, not the repository zip. */

  private fun createZip()
  {
    copyModuleInputsToModuleStructure()
    val zipPath: String = FileLocations.getSwordZipFilePath()
    val inputs = mutableListOf(FileLocations.getSwordConfigFolderPath(), FileLocations.getSwordTextFolderPath(), FileLocations.getEncryptionAndBespokeOsisToModDataRootFolder())
    if (ConfigData.getAsBoolean("stepEncryptionRequired")) inputs.add(FileLocations.getEncryptionAndBespokeOsisToModDataRootFolder())
    Zip.createZipFile(zipPath, 9, FileLocations.getInternalSwordFolderPath(), inputs)
  }


  /****************************************************************************/
  /* Generates the Sword config file.  See https://crosswire.org/wiki/DevTools:conf_Files
     for more information. */

  private fun generateSwordConfigFile ()
  {
    /**************************************************************************/
    addCalculatedValuesToMetadata()
    val x = ValueAddedSupplier.getConsolidatedDetailsForSwordConfigFileComments()
    if (null != x) ConfigData["stepAddedValueLinesForSwordConfigComments"] = x



    /**************************************************************************/
    val configFile = File(FileLocations.getSwordConfigFilePath())
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
      //Dbg.d(line)
      writer.write(ConfigData.expandReferences(line, false)!!)
      writer.write("\n")
    }




    /**************************************************************************/
    writer.close()
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

  private fun generateEncryptionData ()
  {
    /**************************************************************************/
    if (!ConfigData.getAsBoolean("stepEncryptionRequired"))
      return



    /**************************************************************************/
    val withinModuleDecryptionFilePath = FileLocations.getEncryptionDataFilePath()
    StepFileUtils.createFolderStructure(FileLocations.getEncryptionDataFolder())



    /**************************************************************************/
    val osis2modEncryptionKey = MiscellaneousUtils.generateRandomString(64)
    ConfigData.put("stepOsis2ModEncryptionKey", osis2modEncryptionKey, true)
    val obfuscationKey = "p0#8j..8jm@72k}28\$0-,j[\$lkoiqa#]"
    val stepEncryptionKey = MiscellaneousUtils.generateStepEncryptionKey(osis2modEncryptionKey, obfuscationKey)
    ConfigData.put("stepEncryptionApplied", "y", true)



    /**************************************************************************/
    /* Write the details to the file which controls encryption. */

    val writer = File(withinModuleDecryptionFilePath).bufferedWriter()
    writer.write("[${ConfigData["stepModuleName"]!!}]"); writer.write("\n")
    writer.write("CipherKey=$stepEncryptionKey");        writer.write("\n")
    writer.write("STEPLocked=true");                     writer.write("\n")
    writer.close()
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

  private fun getReversificationMap (data: List<ReversificationMoveGroup>): String
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
  /* The Sword config file is supposed to give an indication of the module
     size (or at least, some size or other, and I'm assuming it's the module
     size). */
     
  private fun getFileSizeIndicator()
  {
    var size = File(Paths.get(FileLocations.getSwordTextFolderPath()).toString()).walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    size = ((size + 500) / 1000) * 1000 // Round to nearest 1000.
    ConfigData.put("stepModuleSize", size.toString(), true)
  }


  /****************************************************************************/
  private lateinit var m_OsisFilePath: String
  private var m_ReversificationMap = ""
}