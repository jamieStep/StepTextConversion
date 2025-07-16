package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithoutStackTraceAbandonRun
import java.io.File
import java.time.LocalDate


/******************************************************************************/
/**
 * Handles the history information required in the Sword configuration file.
 *
 *
 * ## Overview
 *
 * I have recently (March 2025) modified the way history and version information
 * is handled -- the previous version being rather too complicated for its own
 * good, and difficult to control.  This does have one slight downside, in that
 * I still need to be able to cope with legacy data, since I need to be able to
 * pick up version information from previous modules.
 *
 * The processing here is now handled by two mandatory command-line parameters
 * -- history and releaseNumber.
 *
 * history (stepHistory by the time we get here) may be a text string explaining
 * why a new module is being made, or the special values *FromMetadata* and
 * *AsIs*.
 *
 * *AsIs* says to use the previous history.  I take this as indicating a
 * special situation in which we want to retain both the previous history
 * and the previous version number -- ie we don't want to add a new history
 * line to the file.  This is mainly for my benefit, so that when I discover
 * I've screwed up the processing, I can generate an improved version without
 * at the same time updating the history.
 *
 * *FromMetadata* needs to be treated with caution.  As the name implies, it
 * attempts to obtain the history information from metadata supplied by the
 * translators.  I have introduced it mainly to streamline things when dealing
 * with the kinds of large collections of texts which we often receive from
 * DBL -- and indeed it is only set up to work with DBL metadata at present.
 * It needs to be carefully monitored, however: the DBL metadata is not well
 * documented, so far as I can see, and there is therefore no guarantee that
 * everyone will use the metadata tags in the same way.
 *
 * History information is stored in the step.conf file so that it continues
 * to be available after rebuilds.  It is stored in two separate tranches,
 * one for public versions, and one for STEPBible-only versions.
 *
 *
 *
 *
 *
 * ## Crosswire requirements
 *
 * Crosswire requires history lines to be of the form:
 *
 *   History_1.2=blah
 *
 * where the 1.2 is the version number.
 *
 * Crosswire recommend that the lines are stored most recent (ie latest
 * version number) first.  I follow this recommendation, although we cannot
 * necessarily assume that existing modules have followed this convention
 * (or indeed, I suppose, any convention at all).
 *
 * They also recommend that history lines should end with the date upon which
 * the change is being applied, in yyyy-mm-dd format.  This is, however, only a
 * recommendation: I do not follow it in the history lines which I generate, and
 * I cannot rely upon other people following it either.
 *
 * With the lines I generate myself, I use a stylised format as follows -- ie I
 * put the date at the front, where it is easier to take in at a glance.
 *
 *   History_1.2=2023-11-01 blah
 *
 *
 *
 *
 * ## Version numbers
 *
 * Crosswire require version numbers to be of the form 1.2, where 1 is the
 * major revision and 2 is the minor revision.
 *
 * It is difficult to follow this in any meaningful and consistent way.  Text
 * suppliers may not themselves follow this convention, and even if they do,
 * there is unlikely to be anything approaching consensus as to what constitutes
 * a major change and what a minor one.
 *
 * I therefore propose adopting the approach that unless we have good reason to
 * do otherwise, an up-issue occasioned by the translator supplying a new
 * package is treated as a full release, and an up-issue occasioned by us
 * revising our processing is treated as a dot release.  Nothing seeks to impose
 * these conventions though -- if you consider them useful, it is down to you
 * to use the command-line parameters as necessary to achieve them.
 *
 * One final point.  I guess it would be useful to include, as part of the
 * history information, any version number the suppliers have themselves
 * associated with the text.  Unfortunately I cannot think of any automated
 * way of doing this (even with DBL, there seems to be no reliable way of
 * identifying the relevant information), so it would have to be a manual
 * operation.
 *
 * @author ARA Jamieson
 */

object VersionAndHistoryHandler: ObjectInterface
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
  * We maintain separate collections of history lines for public and STEPBible-
  * only texts.  When creating a Sword config file, we need the collection
  * appropriate to the audience we are targetting, and the present method
  * supplies it.
  *
  * @return History lines.
  */

  fun getHistoryLinesForThisAudience (): List<String>
  {
    val audience = "_${ConfigData["stepTargetAudience"]!!}"
    return m_HistoryLinesForThisAudience.map { it.toString().replaceFirst(audience, "") }
  }


  /****************************************************************************/
  /**
  * Appends history lines to the step.conf file.  You can't call this until
  * 'process' has been called.  Where a given text can turn into both a public
  * and a STEPBible-only module, this returns *all* history lines for both
  * collections, so that all of them can be retained in the step.conf file for
  * future use.  Cannot be called until after 'process' has done its stuff.
  */

  fun appendHistoryLinesForAllAudiencesToHistoryFile ()
  {
    val writer = File(FileLocations.getExistingHistoryFilePath()!!).bufferedWriter(Charsets.UTF_8) // Should default to UTF-8 anyway, but I have my doubts.

    val data: MutableList<String> = mutableListOf()
    data.add("#!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
    data.add("#!")
    data.add("#! History lines.  There may a set for STEPBible; there may be a set for public;")
    data.add("#! or there may be both.")
    data.add("#!")
    data.add("#! You need to retain this file so that we can keep a full change history.  You")
    data.add("#! can make changes if you wish, but you should do so with care.")
    data.add("#!")
    data.add("#!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
    data.add("")

    data                            .forEach { writer.appendLine(it.toString()) }
    m_HistoryLinesForThisAudience   .forEach { writer.appendLine(it.toString()) }
    m_HistoryLinesNotForThisAudience.forEach { writer.appendLine(it.toString()) }
    writer.close()
  }


  /****************************************************************************/
  fun process ()
  {
    /**************************************************************************/
    /* History and revision normally start out coming from the command lines. */

    var history = ConfigData["stepHistory"]!!
    val releaseVersion = ConfigData["stepReleaseNumber"]!!



    /**************************************************************************/
    /* Certain settings require either that we don't up-version, or that we
       take information from supplied metadata (eg DBL). */

    val retainExisting = "=" == releaseVersion || "=".equals(history, ignoreCase = true)
    if ("FromMetadata".equals(history, ignoreCase = true))
    {
      val x = ConfigData["calcSupplierUpdateReason"]
      if (x.isNullOrEmpty())
        throw StepExceptionWithoutStackTraceAbandonRun("History was specified as FromMetadata, but no history information is available in the metadata.")
      history = x
    }



    /**************************************************************************/
    /* Get the parsed history lines for this target audience, and also those
       _not_ for this target audience. */

    fun parseHistoryLines (keys: List<String>): MutableList<ParsedHistoryLine>
    {
      val res: MutableMap<String, ParsedHistoryLine> = mutableMapOf()
      keys.forEach {
        val x = parseHistoryLine("$it=" + ConfigData[it]!!)
        res[x.stepVersionAsKey()] = x
      }

      return res.keys.sortedDescending().map { res[it]!! } .toMutableList()
    }

    val targetAudienceSelector = ConfigData["stepTargetAudience"]!!
    val allHistoryLineKeys = ConfigData.getKeys().filter { it.startsWith("History_") }
    m_HistoryLinesForThisAudience    = parseHistoryLines(allHistoryLineKeys.filter {  it.startsWith("History_$targetAudienceSelector") } )
    m_HistoryLinesNotForThisAudience = parseHistoryLines(allHistoryLineKeys.filter { !it.startsWith("History_$targetAudienceSelector") } )



    /**************************************************************************/
    /* We now need the most recent previous revision.  This is taken from
       history or else defaults to 1.0. */

    val previousVersion = if (m_HistoryLinesForThisAudience.isEmpty()) "1.0" else m_HistoryLinesForThisAudience[0].stepVersion
    val previousHistory = if (m_HistoryLinesForThisAudience.isEmpty()) "First STEPBible release." else m_HistoryLinesForThisAudience[0].text



    /**************************************************************************/
    /* If we're not updating, there's not a lot to do. */

    if (retainExisting || ( previousVersion == releaseVersion && previousHistory == history))
    {
      ConfigData.delete("stepReleaseNumber")
      ConfigData["stepReleaseNumber"] = previousVersion
      ConfigData["calcUpIssued"] = "n"
      return
    }



    /**************************************************************************/
    /* Work out the new revision. */

    val newVersion = when (releaseVersion)
    {
      "+" ->
      {
        val x = previousVersion.split(".")
        x[0] + "." + (x[1].toInt() + 1)
      }

      "++" ->
      {
        val x = previousVersion.split(".")
        "" + (x[0].toInt() + 1) + "." + x[1]
      }

      else ->
      {
        releaseVersion
      }
    }

    ConfigData.delete("stepReleaseNumber")
    ConfigData.put("stepReleaseNumber", newVersion, force = true)
    ConfigData["calcUpIssued"] = "y"
    m_HistoryLinesForThisAudience.add(0, ParsedHistoryLine(targetAudienceSelector, newVersion, dateToString(LocalDate.now()), history))



    /**************************************************************************/
    /* Delete old data -- we reinsert it later in revised form. */

    ConfigData.getKeys().filter { it.startsWith("History_") }. forEach { ConfigData.delete(it) }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun dateToString (moduleDate: LocalDate): String
  {
    return moduleDate.year.toString() + "-" + String.format("%02d", moduleDate.month.value) + "-" + String.format("%02d", moduleDate.dayOfMonth)
  }


  /****************************************************************************/
  /* Parses either our own stylised history line or an external one.  In the
     latter case, I don't really make any assumptions about the extent to which
     it can be parsed, beyond attempting to look for a yyyy-mm-yy date in it,
     which I extract and assume to be the date the particular revision was
     released. */

  private fun parseHistoryLine (line: String): ParsedHistoryLine
  {
    val mc = C_HistoryLineStepPat.matchEntire(line)!!
    return ParsedHistoryLine(mc.groups["targetAudience"]!!.value, mc.groups["stepVersion"]!!.value, mc.groups["date"]!!.value, mc.groups["text"]!!.value)
  }


  /****************************************************************************/
  private fun makeHistoryLine (targetAudience: String, stepVersion: String, moduleDate: String, text: String): String
  {
    val revisedText = text + (if (text.endsWith('.')) "" else ".")
    val revisedModuleDate = if (moduleDate.isBlank()) "" else "$moduleDate "
    return "History_${targetAudience}_$stepVersion=$revisedModuleDate $revisedText"
  }


  /****************************************************************************/
  /* Creates a suitable key for use when ordering history lines.  Keys are of
     the form 002.003 being the STEP version number in a canonical form.
     Reverse alphabetical ordering by key will give the lines in the required
     order. */

  private fun makeKey (stepVersion: String): String
  {
    val x = stepVersion.split('.')
    return String.format("%03d", x[0].toInt()) + "." + String.format("%03d", x[1].toInt())
  }


  /****************************************************************************/
  private data class ParsedHistoryLine (val targetAudience: String, val stepVersion: String, val moduleDate: String, val text: String)
  {
    fun stepVersionAsKey (): String { return makeKey(stepVersion) }
    override fun toString (): String { return makeHistoryLine(targetAudience, stepVersion, moduleDate, text) }
  }



  /****************************************************************************/
  private val C_HistoryLineStepPat = "(?i)History_(?<targetAudience>.*?)_(?<stepVersion>.*?)=(?<date>.*?)\\s+(?<text>.*)\\s*".toRegex()
  private var m_HistoryLinesForThisAudience: MutableList<ParsedHistoryLine> = mutableListOf()
  private var m_HistoryLinesNotForThisAudience: List<ParsedHistoryLine> = mutableListOf()
}
