/******************************************************************************/
package org.stepbible.textconverter.support.configdata

import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
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
    /* $metadata -- ie co-located with config.conf */

    if (fileNameOrEntryNameWithinJar.lowercase().startsWith("\$metadata/"))
      return Paths.get(fileNameOrEntryNameWithinJar.replace("\$metadata", getMetadataFolderPath())).normalize().toString()



    /**************************************************************************/
    /* General path, which is assumed to be relative to the calling path. */

    return if (Paths.get(fileNameOrEntryNameWithinJar).isAbsolute)
      Paths.get(fileNameOrEntryNameWithinJar).normalize().toString()
    else
    {
      val callingFilePath = if (null == theCallingFilePath || "config.conf" == theCallingFilePath) getMetadataFolderPath() else (File(theCallingFilePath)).parent
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
      //return File(ClassLoader.getSystemResource(fileName).file).inputStream()
      //return Thread.currentThread().contextClassLoader.getResourceAsStream(fileName)
      //return File(getResource(this.javaClass, "/$fileName").file).inputStream()
      return {}::class.java.getResourceAsStream("/$fileName")
    }
    
    
    
    /**************************************************************************/
    /* In ordinary file. */
    
    val file = File(fileNameOrEntryNameWithinJar)
    val folderPath = file.parent
    val fileName = file.name
    val path = StepFileUtils.getSingleMatchingFileFromFolder(folderPath, fileName, false) ?: return null
    return FileInputStream(path.toString())
  }
    
  
  /****************************************************************************/
  /* All the obvious things ... */

  fun getConfigFileName (): String { return "config.conf"; }
  fun getConverterLogFilePath (): String { return m_ConverterLogFilePath }
  fun getEncryptionDataRootFolder (): String { return Paths.get(getSwordRootFolderPath(), "step").toString() }
  fun getEncryptionDataFolder (): String { return Paths.get(getEncryptionDataRootFolder(), "jsword-mods.d").toString() }
  fun getEncryptionDataFilePath (moduleName: String): String { return Paths.get(getEncryptionDataFolder(), moduleName).toString() }
  fun getEnhancedUsxFilePattern (): String { return "*.usx" }
  fun getEnhancedUsxFolderPath (): String { return m_EnhancedUsxFolderPath }
  fun getHistoryFilePath (): String { return return Paths.get(getMetadataFolderPath(), "history.conf").toString() }
  fun getHistoryTemplateFilePath (): String { return "\$common/historyTemplate.conf" }
  private fun getMetadataFolderPath (): String { return m_MetadataFolderPath }
  fun getOsisFilePath (): String { return m_OsisFilePath }
  fun getOsisFolderPath (): String { return m_OsisFolderPath }
  fun getOsisToModLogFilePath (): String { return m_OsisToModLogFilePath; }
  fun getOsis2modVersificationDetailsFilePath (): String { return "\$common/osis2modVersification.txt" }
  fun getPreprocessedUsxFolderPath (): String { return m_PreprocessedUsxFolderPath }
  fun getPreprocessorBatchFilePath (): String { return Paths.get(m_RootFolderPath, "Preprocessor", "preprocessor.bat").toString() }
  fun getPreprocessorExeFilePath (): String { return Paths.get(m_RootFolderPath, "Preprocessor", "preprocessor.exe").toString() }
  fun getPreprocessorJavaFilePath (): String { return Paths.get(m_RootFolderPath, "Preprocessor", "preprocessor.jar").toString() }
  fun getPreprocessorJavascriptFilePath (): String { return Paths.get(m_RootFolderPath, "Preprocessor", "preprocessor.js").toString() }
  fun getPreprocessorPythonFilePath (): String { return Paths.get(m_RootFolderPath, "Preprocessor", "preprocessor.py").toString() }
  fun getRawUsxFolderPath (): String { return m_RawUsxFolderPath }
  fun getRootFolderName (): String { return m_RootFolderName }
  fun getRootFolderPath (): String { return m_RootFolderPath }
  fun getStepChangeHistoryFilePath (): String { return m_StepChangeHistoryFilePath }
  fun getSwordChangesFilePath (moduleName:String): String { return Paths.get(getSwordTextFolderPath(moduleName), m_StepChangeHistoryFileName).toString() }
  fun getSwordConfigFolderPath (): String { return m_SwordConfigFolderPath }
  fun getSwordConfigFilePath (moduleName: String): String { return Paths.get(m_SwordConfigFolderPath, "$moduleName.conf").toString() }
  fun getSwordModuleFolderPath (): String { return m_SwordModuleFolderPath }
  fun getSwordRootFolderPath (): String { return m_SwordRootFolderPath }
  fun getSwordTemplateConfigFilePath (): String { return "\$common/swordTemplateConfigFile.conf"}
  fun getSwordTextFolderPath (moduleName: String): String { return Paths.get(m_SwordModuleFolderPath, "texts", "ztext", moduleName).toString() }
  fun getSwordZipFilePath (moduleName: String): String { return Paths.get(m_SwordRootFolderPath, "$moduleName.zip").toString() }
  private fun getTextFeaturesFileName (): String { return m_TextFeaturesFileName }
  fun getTextFeaturesFilePath (): String { return Paths.get(getTextFeaturesFolderPath(), getTextFeaturesFileName()).toString() }
  fun getTextFeaturesFolderPath (): String { return m_TextFeaturesFolderPath }
  private fun getVernacularBibleStructureFileName (): String { return m_VernacularBibleStructureFileName }
  fun getVernacularBibleStructureFilePath (): String { return Paths.get(getTextFeaturesFolderPath(), getVernacularBibleStructureFileName()).toString() }
  fun getVersificationFilePath (): String { return Paths.get(getRootFolderPath(), "stepRawTextVersification.txt").toString() }
  private fun getVersificationStructureForBespokeOsis2ModFileName (): String { return ConfigData["stepVersificationScheme"]!! + ".json" }
  fun getVersificationStructureForBespokeOsis2ModFilePath (): String { return Paths.get(getEncryptionDataRootFolder(), "versification", getVersificationStructureForBespokeOsis2ModFileName()).toString() }
  fun getVLFilePath (): String { return Paths.get(getRootFolderPath(), "VL", "vl.txt").toString() }


  /****************************************************************************/
  /**
   * Returns a pattern-match string for raw USX files.
   * 
   * @param ubsBookAbbreviation If null, the result is a regex which will match
   *   _any_ USX file.  Otherwise it will match the file for the specific
   *   book.
   *
   * @return Pattern-match string for raw USX files.
   */
  
  fun getRawUsxFilePattern (ubsBookAbbreviation: String?): String
  {
    val res = ConfigData.get("stepRawUsxFileNamePattern") ?: "\$abbrev\\.usx"
    return res.replace("\$abbrev", ubsBookAbbreviation ?: ".*?")
  }


  /****************************************************************************/
  /**
   * Initialises details of file- and folder- names.
   * 
   * @param rootFolderPath Full path to root folder.
   */
  
  fun initialise (rootFolderPath: String)  
  {
    // rootFolderPath is supplied from the command line, and on Windows there's
    // no guarantee it follows the upper- / lower-case layout of the actual
    // folder name.  It's the latter which we want.
    
    m_RootFolderPath = (File(rootFolderPath)).canonicalPath
    
    m_RootFolderName = File(m_RootFolderPath).name

    m_RawUsxFolderPath = Paths.get(m_RootFolderPath, "RawUsx").toString()
    
    m_PreprocessedUsxFolderPath = Paths.get(m_RootFolderPath, "PreprocessedUsx").toString()

    m_EnhancedUsxFolderPath = Paths.get(m_RootFolderPath, "EnhancedUsx").toString()
    
    m_OsisFolderPath = Paths.get(m_RootFolderPath, "Osis").toString()
    
    m_SwordRootFolderPath = Paths.get(m_RootFolderPath, "Sword").toString()
    
    m_SwordConfigFolderName = "mods.d"
    m_SwordConfigFolderPath = Paths.get(m_SwordRootFolderPath, m_SwordConfigFolderName).toString()
    
    m_SwordModuleFolderName = "modules"
    m_SwordModuleFolderPath = Paths.get(m_SwordRootFolderPath, m_SwordModuleFolderName).toString()
    
    m_ConverterLogFilePath = Paths.get(m_RootFolderPath, "converterLog.txt").toString()
    m_OsisToModLogFilePath = Paths.get(m_RootFolderPath, "osis2ModLog.txt").toString()
    
    m_MetadataFolderPath = Paths.get(m_RootFolderPath, "Metadata").toString()
    
    m_StepChangeHistoryFileName = "TH_development.txt"
    m_StepChangeHistoryFilePath = Paths.get(m_RootFolderPath, "Metadata", m_StepChangeHistoryFileName).toString()
    m_OsisFilePath = Paths.get(m_OsisFolderPath, m_RootFolderName.replace("Text_", "") + "_osis.xml").toString()
    
    m_TextFeaturesFolderPath = Paths.get(m_SwordRootFolderPath, "textFeatures").toString()
    m_TextFeaturesFileName = "textFeatures.json"
    
    m_VernacularBibleStructureFolderPath = Paths.get(m_SwordRootFolderPath, "textFeatures").toString()
    m_VernacularBibleStructureFileName = "vernacularBibleStructure.json"
  }


 

  
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/
  
  /****************************************************************************/
  private var m_ConverterLogFilePath = ""
  private var m_EnhancedUsxFolderPath = ""
  private var m_MetadataFolderPath = ""
  private var m_OsisFilePath = ""
  private var m_OsisFolderPath = ""
  private var m_OsisToModLogFilePath = ""
  private var m_PreprocessedUsxFolderPath = ""
  private var m_RawUsxFolderPath = ""
  private var m_RootFolderName = ""
  private var m_RootFolderPath = ""
  private var m_StepChangeHistoryFileName = ""
  private var m_StepChangeHistoryFilePath = ""
  private var m_SwordConfigFolderName = ""
  private var m_SwordConfigFolderPath = ""
  private var m_SwordModuleFolderName = ""
  private var m_SwordModuleFolderPath = ""
  private var m_SwordRootFolderPath = ""
  private var m_TextFeaturesFileName = ""
  private var m_TextFeaturesFolderPath = ""
  private var m_VernacularBibleStructureFileName = ""
  private var m_VernacularBibleStructureFolderPath = ""
}