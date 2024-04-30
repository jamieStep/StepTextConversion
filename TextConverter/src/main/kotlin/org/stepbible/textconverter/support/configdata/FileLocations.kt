/******************************************************************************/
package org.stepbible.textconverter.support.configdata

import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.stepexception.StepException
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
 *      +-- _Output
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

object FileLocations
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
   * - If fileNameOrEntryNameWithinJar starts with '$common/', the file is
   *   actually within the resources section of this JAR file, and we simply
   *   return fileNameOrEntryNameWithinJar as-is.</li>
   * 
   * - If fileNameOrEntryNameWithinJar starts with '$metadata/', it is assumed
   *   to be relative to the Metadata folder), and the return value is just
   *   the original, but with $configData replaced by the path to the that folder.
   *
   * - If fileNameOrEntryNameWithinJar has no parent folder, it is assumed to
   *   be relative to the calling file path.
   *
   * - $root is the root folder for the text.
   *
   * @param fileFolderPath See above.
   * 
   * @param fileName The path of the file from which this new file is
   *   being loaded (null if being called to load the initial file).
   * 
   * @return Path.
   */
  
  fun getInputPath (filePath: String): String
  {
    /**************************************************************************/
    /* In resources section of jar FILE? */
    
    if (filePath.lowercase().startsWith("\$common"))
      return filePath



    /**************************************************************************/
    /* $root: Root folder for text.  CAUTION: Not backward compatible.  In a
       previous version, this pointed to the Metadata folder.*/

    if (filePath.lowercase().startsWith("\$root/"))
      return Paths.get(filePath.replace("\$root", getRootFolderPath(), ignoreCase = true)).normalize().toString()



    /**************************************************************************/
    /* $metadata -- ie co-located with step.conf */

    if (filePath.lowercase().startsWith("\$metadata/"))
      return Paths.get(filePath.replace("\$metadata", getMetadataFolderPath(), ignoreCase = true)).normalize().toString()



    /**************************************************************************/
    /* General path, which is assumed to be relative to the calling path. */

    return Paths.get(filePath).normalize().toString()
  }
    
  
  /****************************************************************************/
  /**
   * Returns an input stream either to an "ordinary" file, or else to a file
   * within the resources section of the current JAR.  (This functionality is
   * noticeably different from everything else here, but since we're concerned
   * with file locations, and this processing is location-dependent, it seems
   * reasonable to have it here.)
   * 
   * @param filePath If this starts with $common/, it
   *   is assumed to give the name of a file within the resources section of
   *   the current JAR.  Otherwise it is taken to be a full path name.
   * 
   * @param fileName Used where the path required is given relative to
   *   another file.
   * 
   * @return Stream.
   */
  
  fun getInputStream (filePath: String): InputStream?
  {
    /**************************************************************************/
    /* In resources section of JAR file? */
    
    if (filePath.lowercase().startsWith("\$common/"))
    {
      val ix = filePath.indexOf("/")
      val newFileName = filePath.substring(ix + 1)
      return {}::class.java.getResourceAsStream("/$newFileName")
    }
    
    
    
    /**************************************************************************/
    /* In ordinary file. */
    
    val file = File(filePath)
     val fileName = file.name
     var folderPath = file.parent

    if (folderPath.lowercase().startsWith("\$root"))
      folderPath = folderPath.replace("\$root", getRootFolderPath(), ignoreCase = true)
    else if (folderPath.lowercase().startsWith("\$metadata"))
      folderPath = folderPath.replace("\$metadata", getMetadataFolderPath(), ignoreCase = true)

    folderPath = Paths.get(folderPath).normalize().toString()

   val path = StepFileUtils.getSingleMatchingFileFromFolder(folderPath, ("\\Q$fileName\\E").toRegex()) ?: return null
    return FileInputStream(path.toString())
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

  fun getConverterLogFilePath () = Paths.get(m_RootFolderPath, "converterLog.txt").toString()
  fun getOsisToModLogFilePath () = Paths.get(m_RootFolderPath, "osis2ModLog.txt").toString()
  fun getDebugOutputFilePath () = Paths.get(m_RootFolderPath, "debugLog.txt").toString()
  fun getTemporaryInvestigationsFolderPath() = ConfigData["stepTemporaryInvestigationsFolderPath"]!!


  /****************************************************************************/
  /* Metadata. */

  fun getMetadataFolderPath () = Paths.get(m_RootFolderPath, "Metadata").toString()
  fun getStepConfigFileName () = "step.conf"
  fun getStepConfigFilePath () = Paths.get(getMetadataFolderPath(), getStepConfigFileName()).toString()


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
    if (1 != res.size) throw StepException("More than one OSIS file exists.")
    return res[0].toString()
  }

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

  fun getInputOsisFileExists () = if (!StepFileUtils.fileOrFolderExists(getInputOsisFolderPath())) false else StepFileUtils.getMatchingFilesFromFolder(getInputOsisFolderPath(), ".*\\.${getFileExtensionForOsis()}".toRegex()).isNotEmpty()
  fun getInputUsxFilesExist  () = if (!StepFileUtils.fileOrFolderExists(getInputUsxFolderPath()))  false else StepFileUtils.getMatchingFilesFromFolder(getInputUsxFolderPath(), ".*\\.${getFileExtensionForUsx()}".toRegex()).isNotEmpty()
  fun getInputVlFilesExist   () = if (!StepFileUtils.fileOrFolderExists(getInputVlFolderPath()))   false else StepFileUtils.getMatchingFilesFromFolder(getInputVlFolderPath(), ".*\\.${getFileExtensionForVl()}".toRegex()).isNotEmpty()



  /****************************************************************************/
  /* Internal folders etc.  Again OSIS is a complication.  Regardless of what
     we start out from -- USX, VL or OSIS -- we will be creating an OSIS file
     internally, and it's convenient to give that a fixed name.  However,
     if we end up copying this file to the repository package, we need at that
     time to give it a name which reflects the module name.  The xxx in the
     default name is intended to draw attention to the fact that the thing may
     need renaming -- if we have an xxx file in the repository, it's a sure sign
     I've forgotten to do something. */

  fun getInternalSwordFolderPath               () = Paths.get(getOutputFolderPath(), "Sword").toString()

  fun getInternalOsisFolderPath                () = Paths.get(getOutputFolderPath(), "InternalOsis").toString()
  fun getInternalOsisFilePath                  () = Paths.get(getInternalOsisFolderPath(), "internalOsis.${getFileExtensionForOsis()}").toString()

  fun getOutputFolderPath                      () = Paths.get(getRootFolderPath(), "_Output").toString()




  /****************************************************************************/
  /* Sword structure and the stuff which resides in it. */

  fun getEncryptionAndBespokeOsisToModDataRootFolder ()= Paths.get(getInternalSwordFolderPath(), "step").toString()
  private fun getEncryptionDataFolder () = Paths.get(getEncryptionAndBespokeOsisToModDataRootFolder(), "jsword-mods.d").toString()
  fun getEncryptionDataFilePath () = Paths.get(getEncryptionDataFolder(), "${getModuleName()}.conf").toString()

  fun getSwordConfigFilePath (): String { return Paths.get(getSwordConfigFolderPath(), "${getModuleName()}.conf").toString() }
  fun getSwordConfigFolderPath (): String = Paths.get(getInternalSwordFolderPath(), "mods.d").toString()

  fun getSwordTemplateConfigFilePath () = "\$common/swordTemplateConfigFile.conf"

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
  fun getOsis2ModSupportFilePath() = Paths.get(getOsis2ModSupportFolderPath(), getModuleName() + ".json").toString()


  /****************************************************************************/
  /* Miscellaneous. */

  fun getOsis2modVersificationDetailsFilePath () = "\$common/osis2modVersification.txt"



 /****************************************************************************/
 /* Repository. */

  fun getRepositoryPackageFilePath (): String { return Paths.get(getOutputFolderPath(), getRepositoryPackageFileName()).toString() }
  private fun getRepositoryPackageFileName () =
    "forRepository_" +
    ConfigData["stepModuleName"]!! + "_" +
    ConfigData["stepTargetAudience"]!! +
    ".zip"


  /****************************************************************************/
  /* Not yet fully integrated.  Strongs is intended for use when applying
     Strongs corrections automatically, and it's not clear we'll be doing that.

     And ThirdParty is for use where we have only OSIS available as input, and
     need to pick up an existing config file for use with it.  I don't have
     any experience of actually doing that, and possibly we won't work that way
     anyway -- I may require that a proper step.conf is set up, in which case
     this special-case processing will not be needed. */

  fun getStrongsCorrectionsFilePath () = "\$common/strongsCorrections.txt"
  private fun getThirdPartySwordConfigFileName () = "sword.conf"
  fun getThirdPartySwordConfigFilePath () = Paths.get(getMetadataFolderPath(), getThirdPartySwordConfigFileName()).toString()


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
