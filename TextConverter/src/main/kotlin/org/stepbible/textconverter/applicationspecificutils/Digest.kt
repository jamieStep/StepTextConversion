package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepFileUtils
import java.io.File
import java.nio.file.Paths


/******************************************************************************/
/**
 * File digest handler.
 *
 * To give us added confidence that a given module has indeed been created from
 * the inputs available to us, it is useful to store digests for each input
 * file in head-of-file comments to the Sword configuration file, and to be able
 * to compare this data with digests calculated for the actual input data.
 *
 * @author ARA "Jamie" Jamieson
 */

object Digest: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Checks digests for the current inputs against digests in a previous Sword
  * configuration file (if any).
  */

  fun checkFileDigests ()
  {
    /**************************************************************************/
    if (!StepFileUtils.fileOrFolderExists(FileLocations.getSwordConfigFilePath()))
    {
      Rpt.report(level = 1, "Previous Sword configuration file does not exist, so not able to verify these are the same inputs as before.")
      return
    }



    /**************************************************************************/
    val parsedData = parseSwordConfigFile()
    if (null == parsedData)
    {
      Rpt.report(level = 1, "*** Previous Sword configuration does not contain digests etc for input files.")
      return
    }



    /**************************************************************************/
    val digests = getDigests()
    if (m_Input != parsedData.first.uppercase())
    {
      Rpt.report(level = 1, "*** Input source has changed -- was ${parsedData.first}, is now $m_Input.")
      return
    }



    /**************************************************************************/
    val newDigestsMap = digests.associate{ it.first to it.second }
    val oldDigestsMap = parsedData.second.associate{ it.first to it.second }
    val inNewNotInOld = newDigestsMap.keys - oldDigestsMap.keys
    val inOldNotInNew = oldDigestsMap.keys - newDigestsMap.keys
    val inBoth = oldDigestsMap.keys intersect newDigestsMap.keys
    val msgBits: MutableList<String> = mutableListOf()
    if (inNewNotInOld.isNotEmpty()) msgBits.add("*** File list has changed.  Following are in the current list but not in the previous list (or not under the same names): " + inNewNotInOld.joinToString(", ") + ".")
    if (inOldNotInNew.isNotEmpty()) msgBits.add("*** File list has changed.  Following were in the previous list but not in the current list (or not under the same names): " + inOldNotInNew.joinToString(", ") + ".")



    /**************************************************************************/
    if (inBoth.isNotEmpty())
    {
      val diffs = inBoth.filterNot { fileName -> newDigestsMap[fileName]!! == oldDigestsMap[fileName]!! }.joinToString(", ")
      if (diffs.isNotEmpty()) msgBits.add("*** Digests for the following files differ: $diffs.")
    }



    /**************************************************************************/
    if (msgBits.isEmpty())
      Rpt.report(level = 1, "The present inputs were indeed used to generate the existing version of the module.")
    else
      Rpt.report(level = 1, msgBits.joinToString("\n"))
  }


  /****************************************************************************/
  /**
  * Creates details of input file SHA256 digests in a form suitable for
  * inclusion in the Sword configuration file:
  *
  *    #  SHA256: gen.usx abcdefgh...
  *    #  SHA256: exo.usx ijklmnop...
  *
  * (With VL and OSIS input, there will be only a single entry in the data.)
  */

  fun makeFileDigests (): String
  {
    val inputOsisFilePath = FileLocations.getInputOsisFilePath()!!
    val versificationJsonFilePath = if ("runtime" == ConfigData["calcReversificationType"]) FileLocations.getOsis2ModSupportFilePath() else null
    var res = getDigests().joinToString("\n") { "#  SHA256: ${Paths.get(it.first).fileName}: ${it.second}" }
    res += "\n#  SHA256 for OSIS available for future input: ${Paths.get(inputOsisFilePath).fileName}: ${MiscellaneousUtils.getSha256(inputOsisFilePath)}"
    if (null != versificationJsonFilePath)
      res += "\n#  SHA256 for versification JSON: ${Paths.get(versificationJsonFilePath).fileName}: ${MiscellaneousUtils.getSha256(versificationJsonFilePath)}"
    return res
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Returns a list of pairs -- (fileName, SHA256). */

  private fun getDigests (): List<Pair<String, String>>
  {
    m_Input = ConfigData["calcOriginData"]!!
    val fileList = when (m_Input)
    {
      "osis" -> listOf(FileLocations.getInputOsisFilePath()!!)
      "vl"   -> FileLocations.getInputVlFilePaths()
      else   -> FileLocations.getInputUsxFilePaths()
    }

    return fileList.map { Pair(StepFileUtils.getFileName(it), MiscellaneousUtils.getSha256(it))}
  }


  /****************************************************************************/
  /* Returns a pair comprising (source, mappings), where mappings it itself a
     list of pairs, each made up of a file name (not full pathname) and the
     digest for that file. */

  private fun parseSwordConfigFile (): Pair<String, List<Pair<String, String>>>?
  {
    var source = ""
    val mappings: MutableList<Pair<String, String>> = mutableListOf()

    File(FileLocations.getSwordConfigFilePath()).readLines().forEach {
      if ("Input taken from:" in it)
        source = it.split(":")[1].trim()
      else if ("SHA256:" in it)
      {
        val x = it.split(":")[1].trim().split(" ")
        mappings.add(Pair(x[0], x[1]))
      }
    }

    return if ("" == source || mappings.isEmpty()) null else Pair(source.uppercase(), mappings)
  }


  /****************************************************************************/
  private lateinit var m_Input: String
}