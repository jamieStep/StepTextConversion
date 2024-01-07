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
 * A central location for handling file paths.
 *
 * @author ARA "Jamie" Jamieson
 */

object StandardFileLocations
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
   *   to be relative to the ConfigData folder), and the return value is just
   *   the original, but with $configData replaced by the path to the that folder.
   *
   * - $root is the root folder for the text.
   *
   * @param fileNameOrEntryNameWithinJar See above.
   * 
   * @param theCallingFilePath The path of the file from which this new file is
   *   being loaded (null if being called to load the initial file).
   * 
   * @return Path.
   */
  
  fun getInputPath (fileNameOrEntryNameWithinJar: String, theCallingFilePath: String?): String
  {
    /**************************************************************************/
    /* In resources section of jar FILE? */
    
    if (fileNameOrEntryNameWithinJar.lowercase().startsWith("\$common/"))
      return fileNameOrEntryNameWithinJar



    /**************************************************************************/
    /* $root: Root folder for text.  CAUTION: Not backward compatible.  In a
       previous version, this pointed to the Metadata folder.*/

    if (fileNameOrEntryNameWithinJar.lowercase().startsWith("\$root/"))
      return Paths.get(fileNameOrEntryNameWithinJar.replace("\$root", getRootFolderPath())).normalize().toString()



    /**************************************************************************/
    /* $metadata -- ie co-located with step.conf */

    if (fileNameOrEntryNameWithinJar.lowercase().startsWith("\$metadata/"))
      return Paths.get(fileNameOrEntryNameWithinJar.replace("\$metadata", getMetadataFolderPath())).normalize().toString()



    /**************************************************************************/
    /* General path, which is assumed to be relative to the calling path. */

    return if (Paths.get(fileNameOrEntryNameWithinJar).isAbsolute)
      Paths.get(fileNameOrEntryNameWithinJar).normalize().toString()
    else
    {
      val callingFilePath = if (null == theCallingFilePath || "step.conf" == theCallingFilePath) getMetadataFolderPath() else (File(theCallingFilePath)).parent
      Paths.get(callingFilePath, fileNameOrEntryNameWithinJar).normalize().toString()
    }
  }
    
  
  /****************************************************************************/
  /**
   * Returns an input stream either to an "ordinary" file, or else to a file
   * within the resources section of the current JAR.  (This functionality is
   * noticeably different from everything else here, but since we're concerned
   * with file locations, and this processing is location-dependent, it seems
   * reasonable to have it here.)
   * 
   * @param theFileNameOrEntryNameWithinJar If this starts with $common/, it
   *   is assumed to give the name of a file within the resources section of
   *   the current JAR.  Otherwise it is taken to be a full path name.
   * 
   * @param callingFilePath Used where the path required is given relative to
   *   another file.
   * 
   * @return Stream.
   */
  
  fun getInputStream (theFileNameOrEntryNameWithinJar: String, callingFilePath: String?): InputStream?
  {
    /**************************************************************************/
    val fileNameOrEntryNameWithinJar = getInputPath(theFileNameOrEntryNameWithinJar, callingFilePath)
    
    
    
    /**************************************************************************/
    /* In resources section of JAR file? */
    
    if (fileNameOrEntryNameWithinJar.lowercase().startsWith("\$common/"))
    {
      val ix = fileNameOrEntryNameWithinJar.indexOf("/")
      val fileName = fileNameOrEntryNameWithinJar.substring(ix + 1)
      return {}::class.java.getResourceAsStream("/$fileName")
    }
    
    
    
    /**************************************************************************/
    /* In ordinary file. */
    
    val file = File(fileNameOrEntryNameWithinJar)
    val folderPath = file.parent
    val fileName = file.name
    val path = StepFileUtils.getSingleMatchingFileFromFolder(folderPath, ("\\Q$fileName\\E").toRegex()) ?: return null
    return FileInputStream(path.toString())
  }
    
  
  /****************************************************************************/
  /* All the obvious things ... */

  /****************************************************************************/
  /* Root folder for text. */

  fun getRootFolderName () = m_RootFolderName
  fun getRootFolderPath () = m_RootFolderPath


  /****************************************************************************/
  /* Log files. */

  fun getConverterLogFilePath () = Paths.get(m_RootFolderPath, "converterLog.txt").toString()
  fun getOsisToModLogFilePath () = Paths.get(m_RootFolderPath, "osis2ModLog.txt").toString()
  fun getDebugOutputFilePath () = Paths.get(m_RootFolderPath, "debugLog.txt").toString()


  /****************************************************************************/
  /* Metadata. */

  fun getMetadataFolderPath () = Paths.get(m_RootFolderPath, "Metadata").toString()
  fun getStepConfigFileName () = "step.conf"
  fun getStepConfigFilePath () = Paths.get(getMetadataFolderPath(), getStepConfigFileName()).toString()


  /****************************************************************************/
  /* Input folders. */

  fun getInputOsisFolderPath () = Paths.get(getRootFolderPath(), "InputOsis").toString()
  fun getInputUsxFolderPath  () = Paths.get(getRootFolderPath(), "InputUsx" ).toString()
  fun getInputVlFolderPath   () = Paths.get(getRootFolderPath(), "InputVl"  ).toString()

  fun getInputOsisFilePath (): String?
  {
    if (!StepFileUtils.fileOrFolderExists(getInputOsisFolderPath())) return null
    val res = StepFileUtils.getMatchingFilesFromFolder(getInputOsisFolderPath(), ".*\\.xml".toRegex())
    if (res.isEmpty()) return null
    if (1 != res.size) throw StepException("More than one OSIS file exists.")
    return res[0].toString()
  }

  fun getInputUsxFilesExist (): Boolean
  {
    return if (!StepFileUtils.fileOrFolderExists(getInputVlFolderPath())) false else !StepFileUtils.folderIsEmpty(getInputVlFolderPath())
  }

  fun getInputVlFilePath (): String?
  {
    if (!StepFileUtils.fileOrFolderExists(getInputVlFolderPath())) return null
    val res = StepFileUtils.getMatchingFilesFromFolder(getInputVlFolderPath(), ".*\\.txt".toRegex())
    if (res.isEmpty()) return null
    if (1 != res.size) throw StepException("More than one VL file exists.")
    return res[0].toString()
  }


  /****************************************************************************/
  /* Internal folders etc. */

  fun getInternalUsxAFolderPath     () = Paths.get(getRootFolderPath(), "A_Usx" ).toString()
  fun getInternalUsxBFolderPath     () = Paths.get(getRootFolderPath(), "B_Usx" ).toString()
  fun getInternalOsisFolderPath     () = Paths.get(getRootFolderPath(), "C_Osis").toString()
  fun getInternalSwordFolderPath    () = Paths.get(getRootFolderPath(), "D_Sword").toString()
  fun getInternalTempOsisFolderPath () = Paths.get(getRootFolderPath(), "X_TempOsis").toString()
  fun getInternalTempOsisFilePath   () = Paths.get(getInternalTempOsisFolderPath(), "tempOsis.xml").toString()

  fun getDefaultInternalOsisFilePath () = Paths.get(getInternalOsisFolderPath(), "osis.xml").toString() // Used when creating a new file, when we haven't decided what to call it yet.
  fun getInternalOsisFilePath (): String
  {
    val res = StepFileUtils.getMatchingFilesFromFolder(getInternalOsisFolderPath(), ".*\\.xml".toRegex())
    if (res.isEmpty()) throw StepException("No OSIS file exists.")
    if (1 != res.size) throw StepException("More than one OSIS file exists.")
    return res[0].toString()
  }


  /****************************************************************************/
  /* Sword structure and the stuff which resides in it. */

  fun getEncryptionDataRootFolder ()= Paths.get(getInternalSwordFolderPath(), "step").toString()
  fun getEncryptionDataFolder () = Paths.get(getEncryptionDataRootFolder(), "jsword-mods.d").toString()
  fun getEncryptionDataFilePath (moduleName: String) = Paths.get(getEncryptionDataFolder(), moduleName).toString()
  fun getSwordConfigFolderPath (): String = Paths.get(getInternalSwordFolderPath(), "mods.d").toString()
  fun getSwordConfigFilePath (moduleName: String) = Paths.get(getSwordConfigFolderPath(), "$moduleName.conf").toString()
  fun getSwordTemplateConfigFilePath () = "\$common/swordTemplateConfigFile.conf"
  fun getSwordTextFolderPath (moduleName: String) = Paths.get(Paths.get(getInternalSwordFolderPath(), "modules").toString(), "texts", "ztext", moduleName).toString()
  fun getSwordZipFilePath (moduleName: String) = Paths.get(getInternalSwordFolderPath (), "$moduleName.zip").toString()


  /****************************************************************************/
  /* Versification details. */

  fun getVersificationFilePath () = Paths.get(getRootFolderPath(), "stepRawTextVersification.txt").toString()
  private fun getVersificationStructureForBespokeOsis2ModFileName () = ConfigData["stepModuleName"]!! + ".json"
  fun getVersificationStructureForBespokeOsis2ModFilePath () = Paths.get(getEncryptionDataRootFolder(), "versification", getVersificationStructureForBespokeOsis2ModFileName()).toString()


  /****************************************************************************/
  /* Text features. */

  private fun getTextFeaturesFileName () = "textFeatures.json"
  fun getTextFeaturesFilePath () = Paths.get(getTextFeaturesFolderPath(), getTextFeaturesFileName()).toString()
  fun getTextFeaturesFolderPath () = makeTextFeaturesFolderPath()


  /****************************************************************************/
  /* Miscellaneous. */

  fun getOsis2modVersificationDetailsFilePath () = "\$common/osis2modVersification.txt"
  fun getRepositoryPackageFilePath (): String { return Paths.get(getRootFolderPath(), getRepositoryPackageFileName()).toString() }
  fun getStrongsCorrectionsFilePath () = "\$common/strongsCorrections.txt"
  private fun getThirdPartySwordConfigFileName () = "sword.conf"
  fun getThirdPartySwordConfigFilePath () = Paths.get(getMetadataFolderPath(), getThirdPartySwordConfigFileName()).toString()
  private fun getVernacularBibleStructureFileName () = "vernacularBibleStructure.json"
  fun getVernacularBibleStructureFilePath () = Paths.get(getTextFeaturesFolderPath(), getVernacularBibleStructureFileName()).toString()



 /****************************************************************************/
  private fun getRepositoryPackageFileName () =
    "forRepository_" +
    ConfigData["stepLanguageCode3Char"]!! + "_" +
    ConfigData["stepVernacularAbbreviation"]!! +
    ConfigData["stepModuleNameAudienceRelatedSuffix"]!! +
    ".zip"



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
  /* Have to do this late, because it relies on stepModuleName, and that's not
     finalised until late. */

  private fun makeTextFeaturesFolderPath () = Paths.get(getInternalSwordFolderPath(), "textFeatures", ConfigData["stepModuleName"]).toString()


 

  
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/
  
  /****************************************************************************/
  private var m_RootFolderName = ""
  private var m_RootFolderPath = ""
}
