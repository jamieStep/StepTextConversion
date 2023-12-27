/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.stepexception.StepException
import java.io.File
import java.nio.file.Paths
import java.text.SimpleDateFormat


/******************************************************************************/
/**
 * Works out what to do as regards raw inputs.
 *
 * To date, the vast majority of the modules we have processed for ourselves
 * have started life as raw USX.  Latterly we have started having to cater for
 * OSIS as a starting point, and have also reverted to handling VL in a few
 * cases.
 *
 * This present class looks at the folder structure and existing files to
 * determine what form of input is present.
 *
 * If the only 'Raw' folder is RawUsx, then it merely records this fact.
 * It does not arrange for anything else to happen because the main processing
 * can start from that.
 *
 * If the only Raw folder is RawOsis, it arranges for any necessary pre-
 * processing to be applied in order to populate the Osis folder.
 *
 * If there is a RawVL folder, it again arranges for pre-processing to be
 * applied, in order to create the necessary RawUsx files.
 *
 * At the time of writing, the only inputs we can work with are VL, Usx and
 * OSIS.
 *
 * @author ARA "Jamie" Jamieson
 */

object TextConverterProcessorRawInputManager : TextConverterProcessorBase
{
  /****************************************************************************/
  override fun banner (): String
  {
     return m_Banner
  }


  /****************************************************************************/
  /* runMe will return true only if we actually potentially need to do some
     form of preprocessing.  If we do, then we need to empty the folder into
     which the preprocessed data will go. */

  override fun pre (): Boolean
  {
    if (!runMe()) return true

    when (getRawInputFolderPath())
    {
      "usx"  -> deleteFiles(listOf(Pair(StandardFileLocations.getRawUsxFolderPath(), StandardFileLocations.getRawUsxFilePattern(null))))
      "osis" -> deleteFiles(listOf(Pair(StandardFileLocations.getOsisFolderPath(), ".*".toRegex())))
    }

    return true
  }


  /****************************************************************************/
  override fun runMe (): Boolean
  {
    return when (m_RunMe)
    {
      'N' -> false
      'Y' -> true
      else -> evaluateRunMe()
    }
  }



  /****************************************************************************/
  /**
   * @{inheritDoc}
   */

  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
    commandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
  }


  /****************************************************************************/
  override fun process (): Boolean
  {
    m_Preprocessor.process(makeRawInputFolderPath())
    return true
  }


  /****************************************************************************/
  /**
  * Returns the path for the folder in which the raw input resides.  Intended
  * mainly for use when creating the release package, in order to determine
  * which folder should be included in that package.
  */

  fun getRawInputFolderPath (): String
  {
    return makeRawInputFolderPath()
  }


  /****************************************************************************/
  /**
  * Returns the input type.  This is just the name of the raw input folder,
  * with 'Raw' removed, and converted to lower case.
  *
  * @return Input type.
  */

  fun getRawInputFolderType (): String
  {
    return m_InputFolderName.lowercase().replace("raw", "")
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun evaluateRunMe (): Boolean
  {
    /**************************************************************************/
    val rawInputFolders = StepFileUtils.getMatchingFoldersFromFolder(StandardFileLocations.getRootFolderName(), "Raw.*".toRegex()).map { it.fileName.toString() } .toMutableSet()



    /**************************************************************************/
    /* Look for forbidden situations. */

    if (rawInputFolders.isEmpty()) throw StepException("No raw input folders.")
    val hasRawOsisFolder = "RawOsis" in rawInputFolders; if (hasRawOsisFolder) rawInputFolders.remove("RawOsis")
    if (hasRawOsisFolder && rawInputFolders.isNotEmpty()) throw StepException("Can't have both RawOsis folder and another raw input folder.")
    val hasRawUsxFolder  = "RawUsx"  in rawInputFolders; if (hasRawUsxFolder)  rawInputFolders.remove("RawUsx")
    if (rawInputFolders.size > 2) throw StepException("Too many raw input folders -- don't know which one to use.")



    /**************************************************************************/
    if (hasRawUsxFolder && rawInputFolders.isEmpty()) // Must just be raw USX.
    {
      m_InputFolderName = "RawUsx"
      m_RunMe = 'N'
      return false
    }



    /**************************************************************************/
    /* That just leaves the possibility that we have some other input -- at the
       time of writing, VL or OSIS.  VL places its output into RawUsx; OSIS
       into Osis.  If the target folder doesn't exist, then create it. */

    m_InputFolderName = rawInputFolders.first()

    when (getRawInputFolderType())
    {
      "osis" ->
      {
        if (!StepFileUtils.fileExists(StandardFileLocations.getOsisFolderPath()))
          StepFileUtils.createFolderStructure(StandardFileLocations.getOsisFolderPath())
        m_Preprocessor = RawInputPreprocessor_osis()
        m_Banner = "Copying or preprocessing OSIS"
      }


      "vl" ->
      {
        if (!StepFileUtils.fileExists(StandardFileLocations.getRawUsxFolderPath()))
          StepFileUtils.createFolderStructure(StandardFileLocations.getRawUsxFolderPath())
        m_Preprocessor = RawInputPreprocessor_vl()
        m_Banner = "Converting VL to enhanced USX"
      }


      else -> throw StepException("Invalid input type: ${getRawInputFolderPath()}")
    }


    /**************************************************************************/
    m_RunMe = if (m_Preprocessor.runMe(makeRawInputFolderPath())) 'Y' else 'N'
    return 'Y' == m_RunMe
  }


  /****************************************************************************/
  private fun makeRawInputFolderPath (): String
  {
    return Paths.get(StandardFileLocations.getRootFolderName(), m_InputFolderName).toString()
  }


  /****************************************************************************/
  private lateinit var m_Banner: String
  private lateinit var m_InputFolderName: String
  private lateinit var m_Preprocessor: RawInputPreprocessor
  private var m_RunMe = '?'
}


/******************************************************************************/
abstract class RawInputPreprocessor
{
  /****************************************************************************/
  abstract fun process (inputFolderPath: String)
  abstract fun runMe (inputFolderPath: String): Boolean


  /****************************************************************************/
  protected fun getFormattedDateStamp (filePath: String): String
  {
    return m_DateFormatter.format(File(filePath).lastModified())
  }

  /****************************************************************************/
  /* Checks if the timestamp on the input file path has been recorded in any
     existing file in the USX folder. */

  protected fun sameTimestamp (inputFilePath: String, rawFolderPath: String, rawFileExtension: String): Boolean
  {
    val existingRawFiles = StepFileUtils.getMatchingFilesFromFolder(rawFolderPath, "(?i).*\\.$rawFileExtension".toRegex()).map { it.toString() }
    if (existingRawFiles.isEmpty()) return false
    val inputFileTimestamp = getFormattedDateStamp(inputFilePath)
    return inputFileTimestamp in existingRawFiles[0]
  }


  /****************************************************************************/
  protected fun makeUsxBookFileName (bookName: String, stamp: String, extension: String): String
  {
    return "${bookName}_$stamp.$extension"
  }


  /****************************************************************************/
  private val m_DateFormatter = SimpleDateFormat("yyyy_mm_dd__HH_mm_ss")
}

