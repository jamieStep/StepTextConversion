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
  fun getFileExtensionForOsis () = "xml"
  fun getFileExtensionForUsx()   = "usx"
  fun getFileExtensionForVl()    = "txt"


  /****************************************************************************/
  /* Root folder for text. */

  fun getRootFolderName () = m_RootFolderName
  private fun getRootFolderPath () = m_RootFolderPath


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
  private fun getMasterMiscellaneousFolderPath () = Paths.get(getOutputFolderPath(), "FilesForRepositoryEtc").toString()

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
  /* Text and run features, and also the file used to communicate
     versification information when using our bespoke osis2mod (the latter
     being absent if we are _not_ using the bespoke osis2mod).

     These can be created only when building a module starting at VL or USX.
     If the _only_ input is OSIS, then we can't build them at all presently,
     because I don't have any processing capable of generating these files from
     OSIS.

     If we have VL or USX, though, it's still possible at a later date that we
     might want to modify the OSIS and regenerate the module from that.  I'd
     much rather we didn't, but unfortunately it's a requirement.  Here I'm
     trusting it's relatively safe to generate the text and run features files
     and the bespoke osis2mod data when we do a run from VL or USX, and simply
     retain it for use if we do a later run from OSIS.  This isn't entirely
     true -- I can't legislate for what changes might be applied to OSIS.  In
     fact it wouldn't matter hugely if the text and run features files ended
     up being slightly wrong, because nothing relies upon them -- they're for
     interest only.  But it would be a problem if the changes invalidated the
     bespoke osis2mod data, because then there would be problems when the text
     was displayed in STEPBible.  So we'd better hope that doesn't happen.

     Anyway, in support of this, all of this various data is written to an
     'internal' folder, where it remains until overwritten.  From there, it's
     copied into the relevant places when we generate the module (and
     repository package).

     Methods below with 'Master' in their name refer to this internal copy.
     Other methods refer to the locations which the things assume when written
     to the module or the repository package

     So, the master structure looks like this:

       Text_deuHFA
         _InternalE_FilesForRepositoryEtc
           textFeatures
             ToBeRenamed (changed to reflect module name when module or repo package is generated)
               runFeatures.json
               textFeatures.json
           versification
             osis2modSupportToBeRenamed.json (on selected runs)

      And when the repository or module is created, you get:

        <moduleRoot>
          Usual gubbins ...
          step
            jsword-mods.d
              encryptionFileNameBasedOnModuleName.conf
              osis2modSupportFileNameBasedOnModuleName.conf -- Copied from master folder.

      and the repository package contains

        _README_.txt
        textFeatures
          Copy of ToBeRenamed, with name changed to reflect module name.

     */

  fun getTextFeaturesRootFolderPath () = Paths.get(getInternalSwordFolderPath(), "textFeatures").toString()
  fun getTextFeaturesFolderPath () = makeTextFeaturesFolderPath()
  fun getRunFeaturesFilePath () = Paths.get(getTextFeaturesFolderPath(), "runFeatures.json").toString()
  fun getTextFeaturesFilePath () = Paths.get(getTextFeaturesFolderPath(), "textFeatures.json").toString()

  fun getRepositoryReadMeFilePath () = Paths.get(getMasterMiscellaneousFolderPath(), "_README_.txt").toString()

  fun getOsis2ModSupportFolderPath() = Paths.get(getEncryptionAndBespokeOsisToModDataRootFolder(), "versification").toString()
  fun getOsis2ModSupportFilePath() = Paths.get(getOsis2ModSupportFolderPath(), getModuleName() + ".json").toString()


  /****************************************************************************/
  /* Miscellaneous. */

  fun getOsis2modVersificationDetailsFilePath () = "\$common/osis2modVersification.txt"



 /****************************************************************************/
 /* Repository. */

  fun getRepositoryPackageFilePath (): String { return Paths.get(getOutputFolderPath(), getRepositoryPackageFileName()).toString() }
  private fun getRepositoryPackageFileName () =
    "forRepository_" +
    ConfigData["stepLanguageCode3Char"]!! + "_" +
    ConfigData["stepVernacularAbbreviation"]!! +
    ConfigData["stepModuleNameAudienceRelatedSuffix"]!! +
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
  private fun makeOsisFileName () = "osis${ConfigData["stepModuleNameBase"]}.${getFileExtensionForOsis()}"
  private fun makeTextFeaturesFolderPath () = Paths.get(getTextFeaturesRootFolderPath(), getModuleName()).toString()


  /****************************************************************************/
  private var m_RootFolderName = ""
  private var m_RootFolderPath = ""
}
