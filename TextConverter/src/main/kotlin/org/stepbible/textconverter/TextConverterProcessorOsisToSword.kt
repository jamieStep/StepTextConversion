/******************************************************************************/
package org.stepbible.textconverter

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_256
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.support.shared.SharedData
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.runCommand
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils.copyFile
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils.createFolderStructure
import org.stepbible.textconverter.support.miscellaneous.StepStringUtils.sentenceCaseFirstLetter
import org.stepbible.textconverter.support.miscellaneous.Translations
import org.stepbible.textconverter.support.miscellaneous.Zip
import java.io.File
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*

/******************************************************************************/
/**
 * Main program which handles the conversion of the STEP intermediate USX-ish
 * format to OSIS and validates the output.
 *
 * @author ARA "Jamie" Jamieson
 */

object TextConverterProcessorOsisToSword : TextConverterProcessorBase()
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
    return "Converting OSIS to Sword"
  }


  /****************************************************************************/
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
    commandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
    commandLineProcessor.addCommandLineOption("debugLevel", 1, "Debug level -- 0 => no debug, larger numbers => increasing amounts of debug.", null, "0", false)
  }


  /****************************************************************************/
  override fun pre (): Boolean
  {
    deleteFolder(StandardFileLocations.getEncryptionDataRootFolder())
    deleteFile(Pair(StandardFileLocations.getOsisToModLogFilePath(), null))
    deleteFile(Pair(StandardFileLocations.getSwordRootFolderPath(), null))
    createFolders(listOf(StandardFileLocations.getSwordRootFolderPath(),
                         StandardFileLocations.getSwordConfigFolderPath(),
                         StandardFileLocations.getSwordModuleFolderPath()))
    //deleteFiles(listOf(Pair(StandardFileLocations.getSwordConfigFolderPath(), "*.*")))
    //deleteFiles(listOf(Pair(StandardFileLocations.getSwordModuleFolderPath(), "*.*")))
    return true
  }


  /****************************************************************************/
  override fun runMe (): Boolean
  {
    return true
  }


  /****************************************************************************/
  override fun process (): Boolean
  {
    /**************************************************************************/
    m_ModuleName = ConfigData["stepModuleName"]!!
    createFolderStructure(StandardFileLocations.getSwordTextFolderPath(m_ModuleName))
    generateEncryptionData()
    if (!ConfigData.getAsBoolean("stepEncryptionApplied", "no")) Logger.warning("********** NOT ENCRYPTED **********")



    /**************************************************************************/
    /* Since there are occasions when we need to be able to run the relevant
       osis2mod command manually from the command line, and since it is always
       a pain to work out what the command line should look like, here is the
       information you need ...

         osis2mod.exe <outputPath> <osisFilePath> -v <versificationScheme> -z -c"<password>"

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

    Logger.setLogFile(StandardFileLocations.getConverterLogFilePath())

    val programName = if (XXXOsis2ModInterface.usingCrosswireOsis2Mod()) "osis2mod.exe" else "samisOsis2Mod.exe"

    val swordExternalConversionCommand: MutableList<String> = ArrayList()
    swordExternalConversionCommand.add("cmd")
    swordExternalConversionCommand.add("/C")
    swordExternalConversionCommand.add(programName)
    swordExternalConversionCommand.add("\"" + StandardFileLocations.getSwordTextFolderPath(m_ModuleName) + "\"")
    swordExternalConversionCommand.add("\"" + StandardFileLocations.getOsisFilePath() + "\"")

    if (XXXOsis2ModInterface.usingCrosswireOsis2Mod())
    {
      swordExternalConversionCommand.add("-v")
      swordExternalConversionCommand.add(ConfigData["stepVersificationScheme"]!!)
    }
    else
    {
      swordExternalConversionCommand.add("-V")
      swordExternalConversionCommand.add("\"" + StandardFileLocations.getVersificationStructureForBespokeOsis2ModFilePath() + "\"")
    }

    swordExternalConversionCommand.add("-z")

    val osis2modEncryptionKey = ConfigData["stepOsis2ModEncryptionKey"]
    if (null != osis2modEncryptionKey)
    {
      swordExternalConversionCommand.add("-c")
      swordExternalConversionCommand.add("\"" + osis2modEncryptionKey + "\"")
    }

    generateChangesFile()
    Dbg.reportProgress("")
    runCommand("Running external postprocessing command for Sword: ", swordExternalConversionCommand, errorFilePath = StandardFileLocations.getOsisToModLogFilePath())
    getFileSizeIndicator()
    generateConfigFile()
    checkOsis2ModLog()
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
  private fun addCalculatedValuesToMetadata()
  {
    /**************************************************************************/
    /* Deal with reversification details. */

    ConfigData.put("stepNoOfVersesAmendedByReversification", SharedData.VersesAmendedByReversification.size.toString(), true)
    ConfigData.put("stepNoOfVersesToWhichReversificationSimplyAddedNotes", SharedData.VersesToWhichReversificationSimplyAddedNotes.size.toString(), true)



    /**************************************************************************/
    val textMajorRevision = ConfigData["stepTextMajorRevisionNoSuppliedBySourceRepositoryOrOwnerOrganisation"]!!
    val textMinorRevision = ConfigData["stepTextMinorRevisionNoSuppliedBySourceRepositoryOrOwnerOrganisation"] ?: ""
    var textRevision = textMajorRevision + if (textMinorRevision.isEmpty()) "" else ".$textMinorRevision"
    if (textRevision.isEmpty()) textRevision = "Not known"
    ConfigData.put("stepTextRevision", textRevision, true)



    /**************************************************************************/
    val moduleDate: String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm").format(Date())
    val osisFilePath = File(StandardFileLocations.getOsisFilePath())
    val osisSha256: String = DigestUtils(SHA_256).digestAsHex(osisFilePath)
    var stepInfo = """
¬¬--Text version: @(stepTextRevision).
¬--@(stepTextModifiedDate).
¬--Sword module @(stepModuleName) created @(ModuleDate).
¬--OSIS SHA256: @(OsisSha256).
¬--@(stepThanks)
@(AddedValue)
"""

    stepInfo = stepInfo.replace("-", "&nbsp;")
      .replace("@(stepTextRevision)", ConfigData["stepTextRevision"] ?: "")
      .replace("@(stepTextModifiedDate)", ConfigData["stepTextModifiedDate"]!!)
      .replace("@(stepModuleName)", ConfigData["stepModuleName"]!!)
      .replace("@(stepThanks)", ConfigData["stepThanks"]!!)
      .replace("@(ModuleDate)", moduleDate)
      .replace("@(OsisSha256)", osisSha256)
      .replace("\n", "NEWLINE")

    stepInfo = stepInfo.replace("^¬*(&nbsp;)*\\s*\\u0001".toRegex(), "") // Get rid of entirely 'blank' lines.



    /**************************************************************************/
    if (!ConfigData.getAsBoolean("stepHasAddedValue"))
      stepInfo = stepInfo.replace("@(AddedValue)", "")
    else
    {
      val texts: MutableList<String> = ArrayList()
      var reversificationDetails: String? = null
      if (ConfigData.getAsBoolean("stepAddedValueMorphology", "No")) texts.add(Translations.stringFormatWithLookup("V_AddedValue_Morphology"))
      if (ConfigData.getAsBoolean("stepAddedValueStrongs", "No")) texts.add(Translations.stringFormatWithLookup("V_AddedValue_Strongs"))
      if (ConfigData.getAsBoolean("stepAddedValueReversification", "No"))
      {
        texts.add(Translations.stringFormatWithLookup("V_AddedValue_Reversification"))
        reversificationDetails = Translations.stringFormatWithLookup("V_reversification_LongDescription_" + sentenceCaseFirstLetter(ConfigData["stepReversificationType"]!!))
        reversificationDetails = reversificationDetails.replace("%d", SharedData.VersesAmendedByReversification.size.toString())
      }



      /**************************************************************************/
      var text = ""
      if (texts.isNotEmpty())
      {
        text = Translations.stringFormatWithLookup("V_addedValue_AddedValue") + " "
        text += java.lang.String.join("; ", texts)
      }

      if (null != reversificationDetails) text += "¬¬$reversificationDetails"
      stepInfo = stepInfo.replace("@(AddedValue)", text)
    } // else



    /**************************************************************************/
    stepInfo = stepInfo.replace("¬", "<br>")
    stepInfo = ConfigData.expandReferences(stepInfo, false)!!
    stepInfo = stepInfo.replace("NEWLINE", "")



    /**************************************************************************/
    ConfigData.put("stepModuleCreationDate", moduleDate, true)
    ConfigData.put("stepModuleName", m_ModuleName, true)
    ConfigData.put("stepOsisSha256", osisSha256, true)
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

    var textCombinedId = "$textDisambiguatorForId.$textId"
    textCombinedId = if ("." == textCombinedId) "" else "&nbsp;&nbsp;Id: $textCombinedId"

    textSource = textSource + ownerOrganisation + textCombinedId
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

  private fun checkOsis2ModLog()
  {
    var fatals = 0
    var hadSuccess = false
    var info = 0
    var warnings = 0
    Logger.setPrefix("osis2mod")

    StandardFileLocations.getInputStream(StandardFileLocations.getOsisToModLogFilePath(), null)!!.bufferedReader().readLines().forEach {
      if (it.startsWith("WARNING(PARSE): SWORD does not search numeric entities")) return@forEach  // osis2mod doesn't like things like &#9999;, but apparently we need them and they do work.
      if (it.startsWith("SUCCESS"))
      {
        Logger.info(it)
        hadSuccess = true
      }
      else if (it.contains("FATAL"))
      {
        ++fatals
        Logger.error(it)
      }
      else if (it.contains("WARNING"))
      {
        warnings++
        Logger.warning(it)
      }
      else if (it.contains("INFO(V11N)"))
      {
        info++
        Logger.info(it)
      }
    } // forEach

    Logger.setPrefix(null)

    if (fatals > 0)
      System.err.println("CAUTION: osis2mod.exe reports $fatals fatal error(s).  Please check the OSIS log file to see if the conversion to Sword format has worked.")
    else if (warnings > 0)
      System.err.println("CAUTION: osis2mod.exe reports $warnings warning(s).  Please check the OSIS log file to see if the conversion to Sword format has worked.")
    else if (!hadSuccess)
      System.err.println("CAUTION: osis2mod.exe has not reported success.  Please check the OSIS log file to see if the conversion to Sword format has worked.")
  }


  /****************************************************************************/
  private fun createZip()
  {
    val zipPath: String = StandardFileLocations.getSwordZipFilePath(m_ModuleName)
    val inputs = mutableListOf(StandardFileLocations.getSwordConfigFolderPath(), StandardFileLocations.getSwordTextFolderPath(m_ModuleName))
    if (ConfigData.getAsBoolean("stepEncryptionRequired")) inputs.add(StandardFileLocations.getEncryptionDataRootFolder())
    Zip.createZipFile(zipPath, 9, StandardFileLocations.getSwordRootFolderPath(), inputs)
  }


  /****************************************************************************/
  /* Generates a file containing change information. */

  private fun generateChangesFile()
  {
    if (!File(StandardFileLocations.getStepChangeHistoryFilePath()).exists()) return
    copyFile(StandardFileLocations.getSwordChangesFilePath(m_ModuleName), StandardFileLocations.getStepChangeHistoryFilePath())
  }


  /****************************************************************************/
  /* Generates the Sword config file.  See https://crosswire.org/wiki/DevTools:conf_Files
     for more information. */

  private fun generateConfigFile ()
  {
    /**************************************************************************/
    addCalculatedValuesToMetadata()
    val lines = StandardFileLocations.getInputStream(StandardFileLocations.getSwordTemplateConfigFilePath(), null)!!.bufferedReader().readLines()
    val writer = File(Paths.get(StandardFileLocations.getSwordConfigFilePath(m_ModuleName)).toString()).bufferedWriter()



    /**************************************************************************/
    for (x in lines)
    {
      var line = x.trim()
      if (line.startsWith("#!")) continue // Internal comment only.
      line = line.split("#!")[0].trim() // Remove any trailing comment.
      line = line.replace("@reversificationMap", m_ReversificationMap) // Could use ordinary dollar expansions here, but it's too slow because the map is so big.
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
    val withinModuleDecryptionFilePath = StandardFileLocations.getEncryptionDataFilePath("$m_ModuleName.conf").toString()
    createFolderStructure(StandardFileLocations.getEncryptionDataFolder())



    /**************************************************************************/
    val osis2modEncryptionKey = MiscellaneousUtils.generateRandomString(64)
    ConfigData.put("stepOsis2ModEncryptionKey", osis2modEncryptionKey, true)
    val obfuscationKey = "p0#8j..8jm@72k}28\$0-,j[\$lkoiqa#]"
    val stepEncryptionKey = MiscellaneousUtils.generateStepEncryptionKey(osis2modEncryptionKey, obfuscationKey)
    ConfigData.put("stepEncryptionApplied", "y", true)



    /**************************************************************************/
    /* The actual configuration file used by JSword. */

    //var writer = File(localMachineDecryptionFilePath).bufferedWriter()
    //writer.write("[$m_ModuleName]");              writer.write("\n");
    //writer.write("CipherKey=$stepEncryptionKey"); writer.write("\n");
    //writer.write("STEPLocked=true");              writer.write("\n");
    //writer.close()



    /**************************************************************************/
    /* And a copy of it in the Metadata folder, to make it easier to find if I
       need to pass it to anyone else. */

    val writer = File(withinModuleDecryptionFilePath).bufferedWriter()
    writer.write("[$m_ModuleName]");              writer.write("\n")
    writer.write("CipherKey=$stepEncryptionKey"); writer.write("\n")
    writer.write("STEPLocked=true");              writer.write("\n")
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
    var size = File(Paths.get(StandardFileLocations.getSwordTextFolderPath(m_ModuleName)).toString()).walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    size = ((size + 500) / 1000) * 1000 // Round to nearest 1000.
    ConfigData.put("stepModuleSize", size.toString(), true)
  }


  /****************************************************************************/
  private var m_ModuleName: String = ""
  private var m_ReversificationMap = ""
}