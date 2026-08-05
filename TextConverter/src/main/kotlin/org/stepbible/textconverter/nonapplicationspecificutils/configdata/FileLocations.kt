/******************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.configdata

import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.get
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepFileUtils
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithoutStackTraceAbandonRun
import org.stepbible.textconverter.protocolagnosticutils.PA_Utils
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import java.io.InputStream


/******************************************************************************/
/**
 * A central location for handling file paths, thus making it easy to change
 * things.
 *
 * The overall structure for the data for a given text looks something like
 * this:
 *
 *      Text_gerHFA --- the root folder.
 *      |
 *      +-- InputUsx or InputVl or InputImp or InputOsis --- see notes.
 *      |
 *      +-- InputOsis
 *      |
 *      +-- Metadata
 *      |   |
 *      |   + -- step*.xlsx
 *      |   |
 *      |   + -- Possibly metadata.xml, licence.xml, etc.
 *      |
 *      +-- _Output_xxx (xxx = step, or public, or may have two _Output_ folders).
 *      |   |
 *      |   + -- InternalOsis
 *      |   |
 *      |   + -- Sword
 *      |   |    |
 *      |   |    + -- mods.d
 *      |   |    \
 *      \   \    + -- modules
 *      |   |    |
 *      \   \    + -- step
 *      \   |
 *      \   +-- textFeatures
 *      |   |
 *      |   +-- forRepository_GerHFA_S.zip
 *      |   \
 *      \   + -- GerHFA.zip
 *      \
 *      + -- converterLog.txt
 *      |
 *      + -- osis2modLog.txt
 *
 *
 * Root folders always start Text_.  This is followed by the 3-character ISO
 * language code in lower case, and then the abbreviated name of the text in
 * whatever form it is supplied (vernacular if that uses Roman characters,
 * otherwise English).  For historical reasons, some texts have an additional
 * suffix -- eg gerHFA_th.  And the whole may be terminated with _public to
 * indicate that this data is to be converted to a publicly available module,
 * _step to indicate that it is to be converted to a module for use only within
 * STEP, and _publicStep to indicate that it can be used to generate either.
 * Without any suffix, STEP-only is assumed.
 *
 * Module names are derived mainly from this root folder name.  They comprise
 * the language code with first character upper-cased (except where the
 * language is English or one of the ancient languages: in this case, the
 * language code is dropped), followed by the remainder of the folder name but
 * devoid of _public.
 *
 * The Input* folders contain whatever we have been given by way of input.
 * InputOsis will be present if either we have been given OSIS as the input,
 * or OSIS was generated previously (under which circumstances it is possible
 * to start processing direct from this OSIS if that is preferred).
 *
 * The Metadata folder must have a step*.xlsx file, which may or may not refer
 * out to other files.  Where we have the opportunity to pick up metadata from
 * files supplied to us (presently only with DBL texts) the metadata folder
 * may contain other files (with DBL that would be metadata.xml and
 * licence.xml).  LEGACY: step.conf was previously used in place of step*.xlsx,
 * and that should still work.
 *
 * The _Output folder contains all of the data generated and used for output
 * purposes.
 *
 * The InternalOsis folder contains the OSIS actually used in generating the
 * module.  This OSIS may differ from that in InputOsis because we may need
 * to tweak the OSIS in rather ad hoc ways, which are there simply to make
 * things work in our particular environment, but which should not be
 * incorporated into the OSIS stored in InputOsis (this latter data being
 * potentially available to third parties who would not wish to have our
 * tweaked data).
 *
 * The Sword folder contains things which go into the module.  The step
 * folder within this exists only if we are samifying or encrypting texts, in
 * which case it contains data which supports these two activities.
 *
 * textFeatures contains files which describe the input data and the processing
 * applied to it.  Nothing actively uses this: I am recording it purely in case
 * we need to identify texts with some particular characteristic at some point
 * -- perhaps because we have identified a problem which applies specifically
 * to such texts and need to rebuild them.
 *
 * forRepository_GerHFA_S.zip (or whatever) is a file whose contents are
 * stored in one of the STEP repositories.  It merely packages up various of
 * the other files described here.  The name will end _S if it contains data
 * for a STEP-only module, _P if it contains data for a public-facing module,
 * or _PS if it contains a public-facing module which can also be used as-is
 * within STEP.
 *
 * GerHFA.zip is the module.
 *
 * And finally, converterLog.txt and osis2modLog.txt contain information
 * describing the processing applied and highlighting any issues arising from
 * a particular run.
 *
 * @author ARA "Jamie" Jamieson
 */

object FileLocations: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/
  
  /****************************************************************************/
  /**
   * Returns a file path either to an "ordinary" file, or else to a file
   * within the resources section of the current JAR.  (This functionality is
   * noticeably different from everything else here, but since we're concerned
   * with file locations, and this processing does deal with locations, it seems
   * reasonable to have it here.)
   *
   * With the latest changes, the fileName argument should indeed be a name,
   * and not a path.  However, there are potential legacy issues here.  Earlier
   * implementations accepted full or partial paths here, and also required --
   * or at least accepted -- a hint at the start of the name (followed by a
   * slash, as though it were an element of a path) as to where to look.
   * The nature of the new processing is such that I _should_ be able to
   * accept an old-format parameter and simply ignore all but the last element
   * of it.  (Or I think so -- this will break if the earlier config did
   * anything too wild and wacky, but that's unlikely).
   *
   * The processing is slightly fiddly ...
   *
   * If the filename starts '@jarResources', this is taken as a firm indication
   * that the file resides in the resources section of the present JAR file.
   * Other than forcing the name to canonical form, I return it as is.
   *
   * Otherwise I ignore all but the filename portion of the parameter.
   *
   * There are now two options.
   *
   * The earlier version (which I have retained here in case we need
   * to revive it) looked first in the Metadata folder for the text itself
   * (and any structure below it).  If it failed to find the file it was
   * looking for, it would then move up a level and look in any Metadata
   * folder there ... and would continue until either it found what it was
   * looking for, or until it had reached the build area folder and had
   * nowhere else to go.
   *
   * The latest thinking is that this is overly complicated.  Instead, the
   * processing looks first in the text's own Metadata folder, and then in
   * the shared configuration folder (in both cases including any folder
   * structure below the folder in question).
   *
   * Which of the two forms of processing is used is determined by
   * C_WalkTree.
   *
   * Note that there is no problem in having a file of the given name at
   * different levels of the structure.  As soon as the first acceptable
   * file is found, the method returns without ever looking at higher
   * levels.
   *
   * @param fileName The file name (but see the discussion on legacy issues
   *   above).  May be either a file name (not a full path) or a regex.
   *
   * @param okIfNotExists As the name suggests. If the file does not exist
   *   and this parameter is false, the method throws an exception.
   *
   * @return Path.
   */

  fun getConfigFileInputPath (fileName: Any, okIfNotExists: Boolean = false): String?
  {
    /**************************************************************************/
    /* Determines which of the two forms of processing we use -- see head-of-
       routine comments. */

    val C_WalkTree = false



    /**************************************************************************/
    /* Assume that things know what they are doing, and that if they claim
       something exists in JAR Resources, it does. */

    if (fileName is String && fileName.lowercase().startsWith("@jarresources"))
      return fileName



    /**************************************************************************/
    val canonicalFilePath =
      if (fileName is String)
        fileName.replace("\\", "/").split("/").last() // Checks away all but the actual file name, so as to handle legacy data.
     else
       fileName



    /****************************************************************************/
    val nameMatches: (candidate: String) -> Boolean =
      when (canonicalFilePath)
      {
        is String -> { candidate -> candidate == canonicalFilePath }
        is Regex  -> { candidate -> candidate.matches(canonicalFilePath) }
        else      -> { _ -> false }
      }



    /****************************************************************************/
    /* Checks if a given folder contains a Metadata folder which, in turn,
       contains a file of the given name. */

   fun folderContainsFile (folderPath: String): Pair<String, String?>
   {
     val metadataFolder = if (folderPath.endsWith("Metadata")) File(folderPath) else File(folderPath, "Metadata")
     if (!metadataFolder.isDirectory) return Pair("continue", null)

     val matches = metadataFolder.walkTopDown()
       .filter { it.isFile && nameMatches(it.name) }
       .toList()

     return when (matches.size)
     {
       0 -> Pair("continue", null)
       1 -> Pair("ok", matches[0].path.toString())
       else -> throw StepExceptionWithoutStackTraceAbandonRun("Duplicate copies of $canonicalFilePath exist.")
     }
   }


    /**************************************************************************/
    /* I use my tree walker here regardless of which form of processing has
       been selected.  If we want to walk the full tree, that's obviously the
       correct thing to do.  If we don't, I simply set the start and end
       folders to be the same thing.

       This first portion below looks in the entire folder structure if C_WalkTree is
       true, or else in the text's Metadata folder if it is false. */

    var startFolder: String
    var endFolder: String

    if (C_WalkTree)
    {
      startFolder = getTextRootFolderPath()
      endFolder = if (C_WalkTree) getConverterBuildAreaFolderPath() else startFolder
    }
    else
    {
      startFolder = getTextMetadataFolderPath()
      endFolder = startFolder
    }

    var res = PA_Utils.walkUpPaths(startFolder, endFolder, ::folderContainsFile)



    /**************************************************************************/
    /* If that failed to find anything, we look in the shared configuration
       folder. */

    if ("NOT_FOUND" == res.first)
    {
        val sharedConfigDataFolder = getConverterSharedConfigurationDataRoot()
        res = PA_Utils.walkUpPaths(sharedConfigDataFolder, sharedConfigDataFolder, ::folderContainsFile)
    }



    /**************************************************************************/
    if ("NOT_FOUND" == res.first)
       return if (okIfNotExists) null else throw StepExceptionWithoutStackTraceAbandonRun("File '$fileName' does not exist.")
    else
      return (res.second!! as String).replace("\\", "/")
  }


  /****************************************************************************/
  /**
   * Returns an input stream either to an "ordinary" file, or else to a file
   * within the resources section of the current JAR.  (This functionality is
   * noticeably different from everything else here, but since we're concerned
   * with file locations, and this processing is location-dependent, it seems
   * reasonable to have it here.)
   * 
   * @param theFileName Name of file (not path).
   *
   * @return Stream plus full path to file, except where the data is coming
   *   from the JAR, in which case the path is null.
   */
  
  fun getInputStream (theFileName: String): Pair<InputStream?, String?>
  {
    /**************************************************************************/
    val expandedFilePath = getConfigFileInputPath(theFileName)!!



    /**************************************************************************/
    /* In resources section of JAR file?  (Not used at time of writing, but
       I've retained this just in case. */
    
    if (expandedFilePath.lowercase().startsWith("@jarresources"))
    {
      val ix = expandedFilePath.indexOf("/")
      val newFileName = expandedFilePath.substring(ix + 1)
      return Pair({}::class.java.getResourceAsStream("/$newFileName"), null)
    }
    
    
    
    /**************************************************************************/
    /* In ordinary file. */
    
    val file = File(expandedFilePath)
    val fileName = file.name
    val folderPath = Paths.get(file.parent).toString()
    val path = StepFileUtils.getSingleMatchingFileFromFolder(folderPath, ("\\Q$fileName\\E").toRegex()) ?: Pair(null, null)
    val pathAsString = path.toString()
    return Pair(FileInputStream(pathAsString), pathAsString)
  }

  
  /****************************************************************************/
  /* All the obvious things ... */

  /****************************************************************************/
  fun getFileExtensionForImp()   = "imp"
  fun getFileExtensionForOsis () = "xml"
  fun getFileExtensionForUsx()   = "usx"
  fun getFileExtensionForVl()    = "txt"


  /****************************************************************************/
  fun getConverterBuildAreaFolderPath () = ConfigData["stepTextConverterOverallDataRoot"]!!
  fun getConverterSharedConfigurationDataRoot () = ConfigData["stepTextConverterSharedConfigurationDataRoot"]!! // May be absent, in which case the build assumes there is no shared data.


  /****************************************************************************/
  /* Root folder for text. */

  fun getTextRootFolderName () = m_RootFolderName
  fun getTextRootFolderPath () = m_RootFolderPath


  /****************************************************************************/
  fun getPrefixForExternalDataInterface () = "externalDataInterface_"
  fun getPrefixForRepositoryOrganisationFiles () = "textRepositoryOrganisation_"
  fun getPrefixForTextOwnerFiles () = "textOwner_"


  /****************************************************************************/
  /* Log files. */

  fun getConverterLogFileName () = "converterLog.txt"
  fun getConverterLogFilePath () = Paths.get(getOutputFolderPath(), getConverterLogFileName()).toString()
  fun getOsisToModLogFileName () = "osis2ModLog.txt"
  fun getOsisToModLogFilePath () = Paths.get(getOutputFolderPath(), getOsisToModLogFileName()).toString()
  fun getDebugOutputFilePath () = Paths.get(getOutputFolderPath(), "debugLog.txt").toString()
  fun getTemporaryInvestigationsFolderPath() =
    if (null == ConfigData["stepTemporaryInvestigationsFolderPath"])
      Paths.get(getConverterBuildAreaFolderPath(), "_DebugOutput_").toString()
    else
      ConfigData["stepTemporaryInvestigationsFolderPath"]!!


  /****************************************************************************/
  /* Metadata. */

  fun getCommonRootFileName () = "commonRoot.conf"

  fun getBookNamesFileName () = "bookNames.tsv"
  fun getConfigDescriptorsFileName () = "configDataDescriptors.tsv"

  fun getTextMetadataFolderName () = "Metadata"
  fun getTextMetadataFolderPath () = Paths.get(m_RootFolderPath, getTextMetadataFolderName()).toString()

  fun getHistoryFileName () = "history.conf"
  private fun getConfigSpreadsheetFileName () = "step.*\\.xlsx".toRegex()
  private fun getConfigTextFileName () = "step.conf"
  fun getConfigSpreadsheetFilePath () = getConfigFileInputPath(getConfigSpreadsheetFileName(), true)
  fun getConfigTextFilePath () = getConfigFileInputPath(getConfigTextFileName(), true)
  fun getExistingHistoryFilePath () = getConfigFileInputPath(getHistoryFileName(), true) // If looking for an existing history file, search for it.
  fun getForcedHistoryFilePath () = Paths.get(getTextMetadataFolderPath(), getHistoryFileName()).toString() // If creating the history file, this is where it goes.

  fun getSharedCommonFolderPath () = Paths.get(getConverterSharedConfigurationDataRoot(), "Metadata", "_Common_").toString()
  fun getSharedCommonAutoloadFolderPath () = Paths.get(getConverterSharedConfigurationDataRoot(), "Metadata", "_Common_", "Autoload").toString()



  /****************************************************************************/
  /* Input folders. */

  fun getInputImpFolderPath  () = Paths.get(getTextRootFolderPath(), "InputImp" ).toString()
  fun getInputOsisFolderPath () = Paths.get(getTextRootFolderPath(), "InputOsis").toString()
  fun getInputUsxFolderPath  () = Paths.get(getTextRootFolderPath(), "InputUsx" ).toString()
  fun getInputVlFolderPath   () = Paths.get(getTextRootFolderPath(), "InputVl"  ).toString()

  fun getInputOsisFilePath (): String? // Can be called anything at all, but we cannot have more than one.
  {
    if (!StepFileUtils.fileOrFolderExists(getInputOsisFolderPath())) return null
    val res = StepFileUtils.getMatchingFilesFromFolder(getInputOsisFolderPath(), ".*\\.${getFileExtensionForOsis()}".toRegex())
    if (res.isEmpty()) return null
    if (1 != res.size) throw StepExceptionWithStackTraceAbandonRun("More than one OSIS file exists.")
    return res[0].toString()
  }

  fun getInputOsisTemporaryFileName () = "DONT_USE_ME.xml" // Name give to generated InputOsis files until we know the run has worked.

  // Lets us save the file speculatively.  Providing the processing goes ok, we rename it later.
  fun makeInputOsisFilePath (): String // If we are making a file path so as to store the output, we give it a name based on the module name.
  {
    return Paths.get(getInputOsisFolderPath(), "osis_${ConfigData["calcModuleName"]!!}.xml").toString()
  }

  fun getInputUsxFilePaths (): List<String>
  {
    if (!StepFileUtils.fileOrFolderExists(getInputUsxFolderPath())) return listOf()
    return StepFileUtils.getMatchingFilesFromFolder(getInputUsxFolderPath(), ".*\\.${getFileExtensionForUsx()}".toRegex()).map { it.toString() }.sorted()
  }

  fun getInputVlFilePaths (): List<String>
  {
    if (!StepFileUtils.fileOrFolderExists(getInputVlFolderPath())) return listOf()
    return StepFileUtils.getMatchingFilesFromFolder(getInputVlFolderPath(), ".*\\.${getFileExtensionForVl()}".toRegex()).map { it.toString() }.sorted()
  }

  fun getInputImpFilesExist  () = if (!StepFileUtils.fileOrFolderExists(getInputImpFolderPath()))  false else StepFileUtils.getMatchingFilesFromFolder(getInputImpFolderPath(),  ".*\\.${getFileExtensionForImp()}" .toRegex()).isNotEmpty()
  fun getInputOsisFileExists () = if (!StepFileUtils.fileOrFolderExists(getInputOsisFolderPath())) false else StepFileUtils.getMatchingFilesFromFolder(getInputOsisFolderPath(), ".*\\.${getFileExtensionForOsis()}".toRegex()).isNotEmpty()
  fun getInputUsxFilesExist  () = if (!StepFileUtils.fileOrFolderExists(getInputUsxFolderPath()))  false else StepFileUtils.getMatchingFilesFromFolder(getInputUsxFolderPath(),  ".*\\.${getFileExtensionForUsx()}" .toRegex()).isNotEmpty()
  fun getInputVlFilesExist   () = if (!StepFileUtils.fileOrFolderExists(getInputVlFolderPath()))   false else StepFileUtils.getMatchingFilesFromFolder(getInputVlFolderPath(),   ".*\\.${getFileExtensionForVl()}"  .toRegex()).isNotEmpty()



  /****************************************************************************/
  /* Internal folders etc.  Again OSIS is a complication.  Regardless of what
     we start out from -- USX, VL or OSIS -- we will be creating an OSIS file
     internally, and it's convenient to give that a fixed name.  However,
     if we end up copying this file to the repository package, we need at that
     time to give it a name which reflects the module name.  The xxx in the
     default name is intended to draw attention to the fact that the thing may
     need renaming -- if we have an xxx file in the repository, it's a sure sign
     I've forgotten to do something. */

  fun getInternalSwordFolderPath () = Paths.get(getOutputFolderPath(), "Sword").toString()

  fun getInternalOsisFolderPath  () = Paths.get(getOutputFolderPath(), "InternalOsis").toString()
  fun getInternalOsisFilePath    () = Paths.get(getInternalOsisFolderPath(), "internalOsis.${getFileExtensionForOsis()}").toString()

  fun getOutputFolderPath      () = Paths.get(getTextRootFolderPath(), "_Output_" + ConfigData["stepTargetAudience"]!!).toString()




  /****************************************************************************/
  /* Sword structure and the stuff which resides in it. */

  fun getEncryptionAndBespokeOsisToModDataRootFolder ()= Paths.get(getInternalSwordFolderPath(), "step").toString()
  fun getEncryptionDataFolderPath () = Paths.get(getEncryptionAndBespokeOsisToModDataRootFolder(), "jsword-mods.d").toString()
  fun getEncryptionDataFilePath () = Paths.get(getEncryptionDataFolderPath(), "${getModuleName().lowercase()}.conf").toString()

  fun getSwordConfigFilePath (): String { return Paths.get(getSwordConfigFolderPath(), "${getModuleName().lowercase()}.conf").toString() }
  fun getSwordConfigFolderPath (): String = Paths.get(getInternalSwordFolderPath(), "mods.d").toString()

  fun getSwordTemplateConfigFilePath () = "swordTemplateConfigFile.conf"

  fun getSwordTextFolderPath (): String
  {
    val substructure = ConfigData["calcSwordDataPath"]!! // Allows for the fact that this may be a Bible or a commentary, and they do in a different folder structure.
    val res = Paths.get(getInternalSwordFolderPath(), substructure.replace("./", ""))
    return res.toString()
    //Paths.get(Paths.get(getInternalSwordFolderPath(), "modules").toString(), "texts", "ztext", getModuleName()).toString()
  }

  fun getSwordZipFilePath () = Paths.get(getOutputFolderPath(), "${getModuleName()}.zip").toString()


  /****************************************************************************/
  /* Used when evaluating alternative schemes. */

  fun getVersificationFilePath () = Paths.get(getTextRootFolderPath(), "stepRawTextVersification.txt").toString()


  /****************************************************************************/
  fun getIssuesFilePath () = Paths.get(getTextMetadataFolderPath(), "issues.json").toString() // File recording any problems with the text.
  private fun getTextFeaturesRootFolderPath () = Paths.get(getInternalSwordFolderPath(), "textFeatures").toString()
  private fun getTextFeaturesFolderPath () = makeTextFeaturesFolderPath()
  fun getRunFeaturesFilePath () = Paths.get(getTextFeaturesFolderPath(), "runFeatures.json").toString()
  fun getTextFeaturesFilePath () = Paths.get(getTextFeaturesFolderPath(), "textFeatures.json").toString()

  private fun getOsis2ModSupportFolderPath() = Paths.get(getEncryptionAndBespokeOsisToModDataRootFolder(), "versification").toString()
  fun getOsis2ModSupportFilePath() = Paths.get(getOsis2ModSupportFolderPath(), getModuleName().lowercase() + ".json").toString()


  /****************************************************************************/
  /* Miscellaneous. */

  fun getCountryCodeInfoFileName () = "countryNamesToShortenedForm.tsv"
  fun getIsoLanguageCodesFileName () = "isoLanguageCodes.tsv"
  fun getOsis2modVersificationDetailsFilePath () = "osis2modVersification.txt"
  fun getVernacularTextDatabaseFilePath () = getConfigFileInputPath("vernacularTranslationsDb.txt")!!



 /****************************************************************************/
 /* Repository. */

  fun getRepositoryPackageFilePath (): String { return Paths.get(getOutputFolderPath(), getRepositoryPackageFileName()).toString() }
  private fun getRepositoryPackageFileName () =
    "forRepository_" +
    ConfigData["calcModuleName"]!! + "_" +
    ConfigData["stepTargetAudience"]!! +
    (if (ConfigData.getAsBoolean("calcOnlineUsageOnly")) "_onlineUsageOnly" else "") +
    ".zip"


  /****************************************************************************/
  /* Not yet fully integrated.  Strongs is intended for use when applying
     Strongs corrections automatically, and it's not clear we'll be doing that.

     And ThirdParty is for use where we have only OSIS available as input, and
     need to pick up an existing config file for use with it.  I don't have
     any experience of actually doing that, and possibly we won't work that way
     anyway -- I may require that a proper step.conf is set up, in which case
     this special-case processing will not be needed. */

  fun getStrongsCorrectionsFilePath () = "strongsCorrections.txt"


  /****************************************************************************/
  /**
   * Initialises details of file- and folder- names.
   */
  
  fun setRootFolderDetails ()
  {
    /**************************************************************************/
    /* rootFolderPath is supplied from the command line, and on Windows there's
       no guarantee it follows the upper- / lower-case layout of the actual
       folder name.  It's the latter which we want. */

    val rootFolderPath = CommandLineProcessor["rootFolder"]!!
    var f = File(rootFolderPath)
    if (!f.isAbsolute)
      f = File(Paths.get(ConfigData["stepTextConverterOverallDataRoot"]!!, rootFolderPath).toString())
    
    m_RootFolderPath = f.canonicalPath
    m_RootFolderName = File(m_RootFolderPath).name
  }




  
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/
  
  /****************************************************************************/
  private fun getModuleName () = ConfigData["calcModuleName"]!!
  private fun makeTextFeaturesFolderPath () = Paths.get(getTextFeaturesRootFolderPath(), getModuleName()).toString()


  /****************************************************************************/
  private var m_RootFolderName = ""
  private var m_RootFolderPath = ""
}
