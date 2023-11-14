package org.stepbible.textconverter

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*


/******************************************************************************/
/**
 * Handles the history information required in the Sword configuration file.
 *
 * History is stored in history.txt in the Metadata folder. This file is
 * created the first time it is needed, and is updated by the processing here,
 * although the user is free to make manual adjustments to long as they conform
 * to the rules set out in the head-of-file comments to the history file.
 *
 * Sword has certain expectations as regards the way version and history
 * information will be used.
 *
 * Version numbers are expected to look like 2.1, with major and minor changes
 * being distinguished.  In fact it is very difficult to know whether a change
 * is major or minor without spending a lot of time comparing old and new --
 * I'd like to take this from the information which the text suppliers give us,
 * but there is no guarantee they all assess the impact of changes in the same
 * way, nor that they make obvious what they believe the impact is.
 *
 * In view of this, I simply assume that *any* new release occasioned by a
 * revised source package from the text suppliers is major, and any re-release
 * of an earlier module occasioned by a change to the conversion processing is
 * minor.  This is clearly too simplistic, but it is at least easy to be
 * consistent.
 *
 * History lines start off with History_a.b, where a.b is the internal
 * (ie STEP/Sword) version number.  Crosswire recommend that the accompanying
 * descriptive text ends with the module date.  I've ignored that and put the
 * date at the start of the line, where it is easier to scan from one line to
 * the next.
 *
 * I use a stylised format as follows:
 *
 *   History_1.2 2023-11-01 [TextSupplierVersion: blah] TextSupplierReason: blah
 *
 * TextSupplierReason will appear upon major releases; StepReason will appear
 * instead on minor revisions.
 *
 *
 * If the history file does not presently exist, it is created, regardless of
 * whether this is a release run or a trial run.  On a release run it will
 * contain a 'proper' history line.  On a trial run, it will contain a dummy
 * line with a STEP version of 0.0 to act as a placeholder.  This line will be
 * removed on any later run.
 *
 * On a run where the history file already exists it will be updated only on
 * release runs, not on trial runs.
 *
 * The config item stepTextRevision is set here, so it is important that this
 * code runs before that item is required.
 *
 * @author ARA Jamieson
 */

object VersionAndHistoryHandler
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
  * Does the stuff.  Note that you need to have called this routine before
  * anything makes use of stepTextRevision, because that's established here.
  */

  fun createHistoryFileIfNecessaryAndWorkOutVersionDetails ()
  {
    /**************************************************************************/
    /* If there is no existing history file, we can simply create one.
       createHistoryFile will fill in the necessary details. */

    if (!File(StandardFileLocations.getHistoryFilePath()).exists()) createHistoryFile()
    parseHistoryFile()
  }


  /****************************************************************************/
  /**
  * Updates the content of an existing history file.  To avoid having to
  * untangle the file manually if things go wrong, this should run late.
  */

  fun writeRevisedHistoryFile ()
  {
    /**************************************************************************/
    if (ReleaseType.Trial == m_ReleaseType) // We update the file only on release runs.
      return



    /**************************************************************************/
    val sortedKeys = m_HistoryEntries.keys.filter { "0.0" != m_HistoryEntries[it]!!.stepVersion }.sortedDescending()



    /**************************************************************************/
    BufferedWriter(FileWriter(StandardFileLocations.getHistoryFilePath())).use { writer ->
      for (line in m_HeaderCommentLines) { writer.write(line); writer.newLine() }
      writer.newLine()
      writer.newLine()

      sortedKeys. forEach { key ->
        val parsedHistoryLine = m_HistoryEntries[key]!!
        val historyLine = makeHistoryLine(parsedHistoryLine.stepVersion, parsedHistoryLine.moduleDate, parsedHistoryLine.supplierVersion, parsedHistoryLine.text)
        writer.write(historyLine)
        writer.newLine()
      } // forEach
    } // BufferedWriter.use
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* We can't just use a file-copy here, because the template file resides
     within the JAR file. */

  private fun createHistoryFile ()
  {
    val filePath = File(StandardFileLocations.getHistoryFilePath())
    val lines = StandardFileLocations.getInputStream(StandardFileLocations.getHistoryTemplateFilePath(), null)!!.bufferedReader().readLines()
    val writer = filePath.bufferedWriter()
    lines.forEach { writer.write(it); writer.write("\n") }
    writer.write(makeHistoryLine("0.0", C_DateFormat.format(Date()), "Unknown", "StepReason: DUMMY COMMENT.")); writer.write("\n")
    writer.close()
  }


  /****************************************************************************/
  /* This works out the new details -- what release we're now at, and what the
     history details should look like.  It then adds these details at the top
     of the collection, and also sets the stepTextRevision config parameter. */

  private fun getNewDetails ()
  {
    /**************************************************************************/
    /* Whether this is a major release depends upon whether the supplier version
       recorded in the most recent history line differs from the version we are
       now dealing with.  It is controlled also by a command-line parameter,
       which may say this is a trial module. */

    val thisSupplierVersion = ConfigData["stepTextVersionSuppliedBySourceRepositoryOrOwnerOrganisation"]!!
    val mostRecentHistoryDetails = m_HistoryEntries[m_HistoryEntries.firstKey()]!!
    val mostRecentPreviousSupplierVersion = mostRecentHistoryDetails.supplierVersion

    m_ReleaseType =
      if ("release" != ConfigData["stepRunType"]!!.lowercase())
        ReleaseType.Trial
      else if (mostRecentPreviousSupplierVersion == thisSupplierVersion)
        ReleaseType.Minor
      else
        ReleaseType.Major



    /**************************************************************************/
    /* Take the previous version from the most recent history entry.  The new
       version is then an amended version of that, with the modification
        depending upon whether this is a major or a minor release. */

    val previousStepVersion = m_HistoryEntries[m_HistoryEntries.firstKey()]!!.stepVersion
    val x = previousStepVersion.split('.')
    val newStepVersion = when (m_ReleaseType)
    {
      ReleaseType.Major -> (1 + Integer.parseInt(x[0])).toString() + ".0"
      else -> x[0] + "." + (1 + Integer.parseInt(x[1])).toString()
    }

    ConfigData["stepTextRevision"] = newStepVersion



    /**************************************************************************/
   val today = Date()
   val text =
      when (m_ReleaseType)
      {
        ReleaseType.Major -> "SupplierReason: " + ConfigData["stepSupplierUpdateReason"]!! + "; StepReason: Supplier updated source documents."
        else -> "StepReason: " + (ConfigData["stepUpdateReason"] ?: "Unspecified.")
      }

    val newHistoryDetails = ParsedHistoryLine(newStepVersion, C_DateFormat.format(today), ConfigData["stepTextVersionSuppliedBySourceRepositoryOrOwnerOrganisation"]!!, text)
    m_HistoryEntries[makeKey(newHistoryDetails)] = newHistoryDetails
  }


  /****************************************************************************/
  private fun makeHistoryLine (stepVersion: String, moduleDate: String, supplierVersion: String, text: String): String
  {
    val revisedText = text + (if (text.endsWith('.')) "" else ".")
    return "History_$stepVersion=$moduleDate [SupplierVersion: $supplierVersion] $revisedText"
  }


  /****************************************************************************/
  /* Creates a suitably key for use when ordering history lines.  Keys are of
     the form:

       20231107_002.003

     where the first part is the date from the history line, and the second part
     is the STEP version number in a canonical form.  Reverse alphabetical
     ordering by key will give the lines in the required order. */

  private fun makeKey (line: ParsedHistoryLine): String
  {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd", Locale.ENGLISH)
    val date = LocalDate.parse(line.moduleDate, formatter)
    val x = line.stepVersion.split(".")
    return date.year.toString() + String.format("%02d", date.month.value) + String.format("%02d", date.dayOfMonth) + "_" + String.format("%03d", x[0].toInt()) + "." + String.format("%03d", x[1].toInt())
  }


  /****************************************************************************/
  /* Reads the existing data into the internal structures. */

  private fun parseHistoryFile ()
  {
    File(StandardFileLocations.getHistoryFilePath()).bufferedReader().lines().forEach {
      val line = it.trim()
      val lineLc = line.lowercase()
      if (line.startsWith("#!"))
        m_HeaderCommentLines.add(line)
      else if (lineLc.startsWith("history"))
      {
        val parsedHistoryLine = parseHistoryLine(line)
        m_HistoryEntries[makeKey(parsedHistoryLine)] = parsedHistoryLine // date.time gives us a Long representation.  Using minus this as the key is an easy way of producing descending order.
      }
    }

    getNewDetails()
  }


  /****************************************************************************/
  private fun parseHistoryLine (line: String): ParsedHistoryLine
  {
    val mc = C_HistoryLinePat.matchEntire(line)!!
    return ParsedHistoryLine(mc.groups["stepVersion"]!!.value, mc.groups["date"]!!.value, mc.groups["supplierVersion"]!!.value, mc.groups["text"]!!.value)
  }


  /****************************************************************************/
  private enum class ReleaseType { Trial, Major, Minor, Unknown }
  private data class ParsedHistoryLine (val stepVersion: String, val moduleDate: String, val supplierVersion: String, val text: String)
  private val C_DateFormat = SimpleDateFormat("yyyy-MMM-dd")
  private val C_HistoryLinePat = "(?i)History_(?<stepVersion>.*?)=(?<date>.*?)\\s+\\[SupplierVersion: (?<supplierVersion>.*?)]\\s*(?<text>.*)".toRegex()
  private val m_HeaderCommentLines: MutableList<String> = mutableListOf()
  private var m_HistoryEntries: SortedMap<String, ParsedHistoryLine> = sortedMapOf()
  private var m_ReleaseType = ReleaseType.Unknown
}
