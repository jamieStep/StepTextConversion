/******************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.configdata

import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepFileUtils
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithoutStackTraceAbandonRun
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
 *      |   + -- step.conf
 *      |   |
 *      |   + -- Possibly metadata.xml, licence.xml, etc.
 *      |
 *      +-- _Output_xxx (xxx = step, or public, or may have two _Output_ folders.
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
 * indicate that this data is to be converted to a publicly available module.
 * Without _public, the processing generates a STEP-only module.
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
 * The Metadata folder must have a step.conf file, which may or may not refer
 * out to other files.  Where we have the opportunity to pick up metadata from
 * files supplied to us (presently only with DBL texts) the metadata folder
 * may contain other files (with DBL that would be metadata.xml and
 * licence.xml).
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
   * with file locations, and this processing is location-dependent, it seems
   * reasonable to have it here.)
   *
   * There are a number of possible cases :-
   *
   * - If filePath starts with '$jarResources/', the file is actually within
   *   the resources section of this JAR file, and we simply return filePath
   *   as-is (except for replacing backslash by slash).
   *
   * - With one exception, any other directive is expanded out using standard
   *   configuration expansion, and then treated as an absolute path.
   *
   * - The one exception is that you can start a path '$find', in which case
   *   I scan the root folder, the metadata folder and the shared config
   *   folder in that order until I find the file.
   *
   * @param filePath The file path in our own internal format.
   *
   * @return Path.
   */
  
  fun getInputPath (filePath: String): String
  {
    /**************************************************************************/
    val canonicalFilePath = filePath.replace("\\", "/")



    /**************************************************************************/
    /* In resources section of jar FILE? */
    
    if (canonicalFilePath.lowercase().startsWith("\$jarresources"))
      return canonicalFilePath



    /**************************************************************************/
    /* Just locate it?  We look here through a series of folders, and attempt
       to locate the file anywhere under any of them.  The first match is
       returned.  See locateFile for more details. */

    if (canonicalFilePath.lowercase().startsWith("\$find"))
      return locateFile(canonicalFilePath) ?: throw StepExceptionWithStackTraceAbandonRun("Can't locate file $canonicalFilePath.")



    /**************************************************************************/
    /* Fully specified path. */

    return Paths.get(canonicalFilePath).normalize().toString()
  }


  /****************************************************************************/
  /* Locates a file.  More specifically ...

     We are concerned here only with paths which start $find/ (and we assume
     that the caller has already determined that the path _does_ start that
     way).

     The $find/ may be followed either by a file name ($find/myFile.txt) or by
     a relative path ($find/FolderA/FolderB/myFile.txt)

     The processing works as follows:

     - It looks for the _first_ file whose name matches (so if there's more
       than you'll never know).

     - It looks in various folders in a particular order: a) the root folder
       for the text; b) the metadata folder for the text; c) folders identified
       by the stepConfigFolderPaths setting in the StepTextConverterParameters
       environment variable (if any) -- and these it runs through in the order
       specified there.

     - It ignores any relative path information in the canonicalFilePath
       parameter (I continue to accept it, though, for the sake of backward
       compatibility).  It simply looks for the file in the folder and in
       any hierarchy below it.
  */

  private fun locateFile (canonicalFilePath: String): String?
  {
    /**************************************************************************/
    fun getPathsFromEnvironmentVariable (): List<String>
    {
      val x = ConfigData["stepConfigFolderPaths"] ?: return listOf()
      return x.split(",").map(String::trim)
    }



    /**************************************************************************/
    val endOfPath = canonicalFilePath.substring("\$find/".length) // Strip off $find/.
    val fileName = Paths.get(endOfPath).last().toString()



    /**************************************************************************/
    for (folderPath in listOf(getRootFolderPath(), getMetadataFolderPath()) + getPathsFromEnvironmentVariable())
    {
      val res = StepFileUtils.findFiles(folderPath, fileName)
      if (res.isNotEmpty())
        return res[0]
    }

    return null
  }
    
  
  /****************************************************************************/
  /**
   * Returns an input stream either to an "ordinary" file, or else to a file
   * within the resources section of the current JAR.  (This functionality is
   * noticeably different from everything else here, but since we're concerned
   * with file locations, and this processing is location-dependent, it seems
   * reasonable to have it here.)
   * 
   * @param filePath If this starts with $jarResources/, it
   *   is assumed to give the name of a file within the resources section of
   *   the current JAR.  Otherwise it is taken to be a full path name.
   *
   * @return Stream plus full path to file, except where the data is coming
   *   from the JAR, in which case the path is null.
   */
  
  fun getInputStream (filePath: String): Pair<InputStream?, String?>
  {
    /**************************************************************************/
    val expandedFilePath = getInputPath(filePath)



    /**************************************************************************/
    /* In resources section of JAR file? */
    
    if (expandedFilePath.lowercase().startsWith("\$jarresources"))
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
  /* Root folder for text. */

  fun getRootFolderName () = m_RootFolderName
  fun getRootFolderPath () = m_RootFolderPath


  /****************************************************************************/
  /* Log files. */

  fun getConverterLogFileName () = "converterLog.txt"
  fun getConverterLogFilePath () = Paths.get(getOutputFolderPath(), getConverterLogFileName()).toString()
  fun getOsisToModLogFileName () = "osis2ModLog.txt"
  fun getOsisToModLogFilePath () = Paths.get(getOutputFolderPath(), getOsisToModLogFileName()).toString()
  fun getDebugOutputFilePath () = Paths.get(getOutputFolderPath(), "debugLog.txt").toString()
  fun getTemporaryInvestigationsFolderPath() =
    if (null == ConfigData["stepTemporaryInvestigationsFolderPath"])
      Paths.get(ConfigData["stepTextConverterOverallDataRoot"]!!, "_DebugOutput_").toString()
    else
      ConfigData["stepTemporaryInvestigationsFolderPath"]!!


  /****************************************************************************/
  /* Metadata. */

  fun getBookNamesFilePath () = "\$jarResources/bookNames.tsv"
  fun getConfigDescriptorsFilePath () = "\$jarResources/configDataDescriptors.tsv"
  fun getMetadataFolderPath () = Paths.get(m_RootFolderPath, "Metadata").toString()
  fun getStepConfigFileName () = "step.conf"
  fun getStepConfigFilePath () = Paths.get(getMetadataFolderPath(), getStepConfigFileName()).toString()

  fun getExistingArchivedConfigZipFilePath (): String
  {
    val rootFolder = getRootFolderPath()
    val filePattern = makeExistingArchivedConfigFileName(".+").toRegex()
    val res = StepFileUtils.findFiles(rootFolder, filePattern)
    if (1 != res.size)
      throw StepExceptionWithoutStackTraceAbandonRun(if (res.isEmpty()) "No previous config archive found." else "More than one previous config archive found.")
    return res[0]
  }

  fun getNewArchivedConfigZipFilePath (): String
  {
    val fileName = makeExistingArchivedConfigFileName(ConfigData["stepTargetAudience"]!!)
    return Paths.get(getOutputFolderPath(), fileName).toString()
  }

  private fun makeExistingArchivedConfigFileName (targetAudience: String): String
  {
    val backslashes = if (".+" == targetAudience) "\\" else ""
    return "configFiles_${ConfigData["stepModuleName"]!!}_$targetAudience$backslashes.zip"
  }



  /****************************************************************************/
  /* Input folders. */

  fun getInputImpFolderPath  () = Paths.get(getRootFolderPath(), "InputImp" ).toString()
  fun getInputOsisFolderPath () = Paths.get(getRootFolderPath(), "InputOsis").toString()
  fun getInputUsxFolderPath  () = Paths.get(getRootFolderPath(), "InputUsx" ).toString()
  fun getInputVlFolderPath   () = Paths.get(getRootFolderPath(), "InputVl"  ).toString()

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
    return Paths.get(getInputOsisFolderPath(), "osis_${ConfigData["stepModuleName"]!!}.xml").toString()
  }

  fun getInputUsxFilePaths (): List<String>
  {
    if (!StepFileUtils.fileOrFolderExists(getInputUsxFolderPath())) return listOf()
    return StepFileUtils.getMatchingFilesFromFolder(getInputUsxFolderPath(), ".*\\.${getFileExtensionForUsx()}".toRegex()).map { it.toString() }
  }

  fun getInputVlFilePaths (): List<String>
  {
    if (!StepFileUtils.fileOrFolderExists(getInputVlFolderPath())) return listOf()
    return StepFileUtils.getMatchingFilesFromFolder(getInputVlFolderPath(), ".*\\.${getFileExtensionForVl()}".toRegex()).map { it.toString() }
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

  fun getOutputFolderPath      () = Paths.get(getRootFolderPath(), "_Output_" + ConfigData["stepTargetAudience"]!!).toString()




  /****************************************************************************/
  /* Sword structure and the stuff which resides in it. */

  fun getEncryptionAndBespokeOsisToModDataRootFolder ()= Paths.get(getInternalSwordFolderPath(), "step").toString()
  fun getEncryptionDataFolderPath () = Paths.get(getEncryptionAndBespokeOsisToModDataRootFolder(), "jsword-mods.d").toString()
  fun getEncryptionDataFilePath () = Paths.get(getEncryptionDataFolderPath(), "${getModuleName().lowercase()}.conf").toString()

  fun getSwordConfigFilePath (): String { return Paths.get(getSwordConfigFolderPath(), "${getModuleName().lowercase()}.conf").toString() }
  fun getSwordConfigFolderPath (): String = Paths.get(getInternalSwordFolderPath(), "mods.d").toString()

  fun getSwordTemplateConfigFilePath () = "\$jarResources/swordTemplateConfigFile.conf"

  fun getSwordTextFolderPath () = Paths.get(Paths.get(getInternalSwordFolderPath(), "modules").toString(), "texts", "ztext", getModuleName()).toString()
  fun getSwordZipFilePath () = Paths.get(getOutputFolderPath(), "${getModuleName()}.zip").toString()


  /****************************************************************************/
  /* Used when evaluating alternative schemes. */

  fun getVersificationFilePath () = Paths.get(getRootFolderPath(), "stepRawTextVersification.txt").toString()


  /****************************************************************************/
  private fun getTextFeaturesRootFolderPath () = Paths.get(getInternalSwordFolderPath(), "textFeatures").toString()
  fun getTextFeaturesFolderPath () = makeTextFeaturesFolderPath()
  fun getRunFeaturesFilePath () = Paths.get(getTextFeaturesFolderPath(), "runFeatures.json").toString()
  fun getTextFeaturesFilePath () = Paths.get(getTextFeaturesFolderPath(), "textFeatures.json").toString()

  private fun getOsis2ModSupportFolderPath() = Paths.get(getEncryptionAndBespokeOsisToModDataRootFolder(), "versification").toString()
  fun getOsis2ModSupportFilePath() = Paths.get(getOsis2ModSupportFolderPath(), getModuleName().lowercase() + ".json").toString()


  /****************************************************************************/
  /* Miscellaneous. */

  fun getCountryCodeInfoFilePath () = "\$jarResources/countryNamesToShortenedForm.tsv"
  fun getIsoLanguageCodesFilePath () = "\$jarResources/isoLanguageCodes.tsv"
  fun getOsis2modVersificationDetailsFilePath () = "\$jarResources/osis2modVersification.txt"
  fun getVernacularTextDatabaseFilePath () = locateFile("\$find/vernacularTranslationsDb.txt")!!



 /****************************************************************************/
 /* Repository. */

  fun getRepositoryPackageFilePath (): String { return Paths.get(getOutputFolderPath(), getRepositoryPackageFileName()).toString() }
  private fun getRepositoryPackageFileName () =
    "forRepository_" +
    ConfigData["stepModuleName"]!! + "_" +
    ConfigData["stepTargetAudience"]!! +
    (if (ConfigData.getAsBoolean("stepOnlineUsageOnly")) "_onlineUsageOnly" else "") +
    ".zip"


  /****************************************************************************/
  /* Not yet fully integrated.  Strongs is intended for use when applying
     Strongs corrections automatically, and it's not clear we'll be doing that.

     And ThirdParty is for use where we have only OSIS available as input, and
     need to pick up an existing config file for use with it.  I don't have
     any experience of actually doing that, and possibly we won't work that way
     anyway -- I may require that a proper step.conf is set up, in which case
     this special-case processing will not be needed. */

  fun getStrongsCorrectionsFilePath () = "\$jarResources/strongsCorrections.txt"


  /****************************************************************************/
  /**
   * Initialises details of file- and folder- names.
   * 
   * @param rootFolderPath Full path to root folder.
   */
  
  fun initialise (rootFolderPath: String)  
  {
    /**************************************************************************/
    /* rootFolderPath is supplied from the command line, and on Windows there's
       no guarantee it follows the upper- / lower-case layout of the actual
       folder name.  It's the latter which we want. */
    
    m_RootFolderPath = (File(rootFolderPath)).canonicalPath
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
  private fun getModuleName () = ConfigData["stepModuleName"]!!
  private fun makeTextFeaturesFolderPath () = Paths.get(getTextFeaturesRootFolderPath(), getModuleName()).toString()


  /****************************************************************************/
  private var m_RootFolderName = ""
  private var m_RootFolderPath = ""
}
