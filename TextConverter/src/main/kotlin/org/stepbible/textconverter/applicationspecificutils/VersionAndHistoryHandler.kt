package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import java.io.File
import java.time.LocalDate


/******************************************************************************/
/**
 * Handles the history information required in the Sword configuration file.
 *
 * History and version information is normally up-issued except where the
 * history line would be basically the same as the most recent one (if any).
 * In that case, I assume that things should *not* be up-issued, since very
 * likely this is just a rerun of the most recent build in order to correct a
 * bug, or else we are producing both a public and a STEP-only version of this
 * module, have just produced one of them, and are mow simply producing the
 * other.  (You can force an up-issue -- ie can ignore this check -- using the
 * *forceUpIssue* flag on the command line.)
 *
 * The Crosswire documentation gives details of a version indicator and history
 * details which are to appear in the Sword configuration file.
 *
 * To date in general this has come from information placed by the user into the
 * step.conf file -- perhaps supplemented by any configuration data which is
 * provided by the text suppliers.
 *
 * Latterly, we have encountered a new situation, where we have, as our input,
 * OSIS, along with an actual Sword configuration file from a previous version
 * of the module.
 *
 * We therefore need to be able to cope with both sources of input.  In
 * addition, existing history lines may be of our own making, and may therefore
 * be in a format with which we are familiar, or may have been supplied by a
 * third party (in which case the format is predefined only to a very limited
 * degree.  We need to be able to cope with both.
 *
 * In the case where we are dealing with OSIS input and an existing Sword config
 * file, we also need a strategy for coping with the possibility that we have
 * relevant information in both the step.conf and the third party file (which
 * the user is required to have stored as sword.conf in the Metadata folder).
 *
 *
 *
 * ## History information
 *
 * Crosswire requires history lines to be of the form:
 *
 *   History_1.2=blah
 *
 * where the 1.2 is the version number.
 *
 * Such lines may appear anywhere in either or both of step.conf and sword.conf.
 * Normally one would expect them to be contiguous (something I guarantee when
 * I am outputting history lines myself), but this cannot be assumed when
 * dealing with existing data.  Crosswire recommends that they be stored in
 * chronological (version number) order, most recent first.  Again, this cannot
 * be assumed, although I enforce it when generating history lines myself.
 *
 * Crosswire recommend that history lines should end with the date upon which
 * the change is being applied, in yyyy-mm-dd format.  This is, however, only a
 * recommendation: I do not follow it in the history lines which I generate, and
 * I cannot rely upon other people following it either.
 *
 * With the lines I generate myself, I use a stylised format as follows:
 *
 *   History_1.2=2023-11-01 [SupplierVersion: blah] SupplierReason: blah or
 *   History_1.2=2023-11-01 [StepVersion: blah] StepReason: blah or
 *
 * the former where the new release reflects a change made by the supplier,
 * and the latter where the release reflects a change we have made ourselves.
 * Occasionally, we may have both STEP- and supplier- details.  This extended
 * information still appears on a single line.
 *
 * The bracketed SupplierVersion is the version number as applied by the
 * organisation which has supplied the text.  There's no guarantee we'll have
 * one, and no guarantee, if we do, that it will follow the Crosswire format for
 * version numbers, which is why I record it separately.
 *
 * After this I give the reason for the update.  If this is a new release from
 * the text supplier, the reason will start 'SupplierReason' and will give
 * any details which they themselves have provided.  If the new release has been
 * occasioned by changes we have made to the converter or whatever, it will
 * start StepReason, and will give our own explanation.
 *
 * Where we are dealing with a sword.conf file as well as step.conf, it is
 * perfectly possibly that both may contain previous history lines.  In this
 * case, I take all history lines from both.  If both files have a history line
 * for a given version, I take the line from step.conf.
 *
 * As well as writing the history lines to the module's Sword configuration file,
 * I also copy them to the end of the step.conf file, so that they will be
 * available for any future runs.
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
 * I therefore adopt the expedient that by default, any change resulting from
 * a new release by the text suppliers is major, and any change arising from us
 * altering the conversion process is minor.  (There is absolutely no reason why
 * this should be the case -- it is perfectly possible that a revised package
 * from the text suppliers has no visible impact at all, while changes we make to
 * the conversion process may be very significant.  But it is at lest easy to be
 * consistent.)
 *
 * By default, therefore, we look at the suppliers' version number for the text
 * being processed, and compare it with the one used last time.  If the two
 * differ, it's a major release, otherwise it's a minor one.  We don't always
 * have a version number from the text suppliers, however.  In this case, the
 * change would be treated as minor.  However it is possible to override this on
 * the command line.
 *
 * This aspect of processing is controlled by the runType command line
 * parameter.
 *
 * If this does not contain the word 'release', then none of the processing here
 * is applied (ie we don't modify the history or the version), the assumption being
 * that this is a test or evaluation run, and we aren't up-issuing.
 *
 * If runType *does* contain 'release', there are three possibilities:
 *
 * - If just as the value 'release', the decision as to whether this constitutes a
 *   major or minor release is left to the processing here, and will be based upon
 *   the supplier's version numbers as described above.
 *
 * - If it also contains the word 'major', it will be treated as a major release.
 *
 * - Otherwise, if it contains the word 'minor', it will be treated as a minor release.
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
  * Returns a list of history lines in order.  Can be called only after
  * 'process' has been called.  This should be used only when setting up the
  * Sword config file (where we want only those lines relevant to the present
  * target audience).
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
  * 'process' has been called.
  */

  fun appendHistoryLinesForAllAudiencesToStepConfigFile ()
  {
    val nonHistoryLines =
      File(FileLocations.getStepConfigFilePath())
        .readLines(Charsets.UTF_8)
        .filter { !it.trim().lowercase().startsWith("history_") }
        .dropLastWhile { it.trim().isEmpty() }

    val writer = File(FileLocations.getStepConfigFilePath()).bufferedWriter(Charsets.UTF_8) // Should default to UTF-8 anyway, but I have my doubts.
    nonHistoryLines.forEach { writer.write(it); writer.write("\n") }
    writer.write("\n")
    m_HistoryLinesForThisAudience   .forEach { writer.write(it.toString()); writer.write("\n") }
    m_HistoryLinesNotForThisAudience.forEach { writer.write(it.toString()); writer.write("\n") }
    writer.close()
  }


  /****************************************************************************/
  fun process ()
  {
    /**************************************************************************/
    fun parseHistoryLines (keys: List<String>): MutableList<ParsedHistoryLine>
    {
      val res: MutableMap<String, ParsedHistoryLine> = mutableMapOf()
      keys.forEach {
        val x = parseHistoryLine("$it=" + ConfigData[it]!!)
        res[x.stepVersionAsKey()] = x
      }

      return res.keys.sortedDescending().map { res[it]!! } .toMutableList()
    }



    /**************************************************************************/
    /* Get the parsed history lines for this target audience, and also those
       _not_ for this target audience. */

    val targetAudienceSelector = ConfigData["stepTargetAudience"]!!
    val allHistoryLineKeys = ConfigData.getKeys().filter { it.startsWith("History_") }
    m_HistoryLinesForThisAudience    = parseHistoryLines(allHistoryLineKeys.filter {  it.startsWith("History_$targetAudienceSelector") } )
    m_HistoryLinesNotForThisAudience = parseHistoryLines(allHistoryLineKeys.filter { !it.startsWith("History_$targetAudienceSelector") } )



    /**************************************************************************/
    /* We now need the most recent previous revision.  This is taken from
       history or else defaults to 1.0. */

    val previousStepVersion: String
    run {
      val fallbackValue = "1.0"
      val versionFromHistory = if (m_HistoryLinesForThisAudience.isEmpty()) "1.0" else m_HistoryLinesForThisAudience[0].stepVersion
      val maxKey = maxOf(makeKey(fallbackValue), makeKey(versionFromHistory))
      val x = maxKey.split('.')
      previousStepVersion = x[0].toInt().toString() + "." + x[1].toInt().toString()
    }



    /**************************************************************************/
    /* If the reason details haven't changed, then I assume we're simply having
       another attempt at building the same module, and don't want to update
       history and version -- unless we're forcing an up-issue. */

    val newText ="SupplierReason: " + ConfigData.get("stepSupplierUpdateReason", "N/A") + "; StepReason: ${ConfigData.get("stepStepUpdateReason", "N/A")}."
    val prevText = if (m_HistoryLinesForThisAudience.isEmpty()) "" else m_HistoryLinesForThisAudience[0].text
    if (newText.lowercase() == prevText.lowercase() && !ConfigData.getAsBoolean("stepForceUpIssue", "no"))
    {
      ConfigData["stepTextRevision"] = previousStepVersion
      ConfigData["stepUpIssued"] = "n"
      return
    }



    /**************************************************************************/
    /* If this is a major revision, then we take the previous highest revision,
       throw away any dot part, and then add one to the whole number part.
       If this is a minor revision, we just up the dot part by one. */

    val newStepVersion = getNewVersion(previousStepVersion)
    ConfigData["stepTextRevision"] = newStepVersion
    ConfigData["stepUpIssued"] = "y"
    m_HistoryLinesForThisAudience.add(0, ParsedHistoryLine(targetAudienceSelector, newStepVersion, dateToString(LocalDate.now()), ConfigData["stepTextVersionSuppliedBySourceRepositoryOrOwnerOrganisation"]!!, newText))



    /**************************************************************************/
    /* Delete. */

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
    return ParsedHistoryLine(mc.groups["targetAudience"]!!.value, mc.groups["stepVersion"]!!.value, mc.groups["date"]!!.value, mc.groups["supplierVersion"]!!.value, mc.groups["text"]!!.value)
  }


  /****************************************************************************/
  /* This works out the new details -- what release we're now at, and what the
     history details should look like.  It then adds these details at the top
     of the collection, and also sets the stepTextRevision config parameter. */

  private fun getNewVersion (previousStepVersion: String): String
  {
    when (ConfigData["stepReleaseType"]!!.lowercase())
    {
      "major" -> m_ReleaseType = ReleaseType.Major
      "minor" -> m_ReleaseType = ReleaseType.Minor
    }



    /**************************************************************************/
    /* Take the previous version from the most recent history entry.  The new
       version is then an amended version of that, with the modification
       depending upon whether this is a major or a minor release. */

    val x = previousStepVersion.split('.')
    return when (m_ReleaseType)
    {
      ReleaseType.Major -> (1 + Integer.parseInt(x[0])).toString() + ".0"
      else -> x[0] + "." + (1 + Integer.parseInt(x[1])).toString()
    }
  }


  /****************************************************************************/
  private fun makeHistoryLine (targetAudience: String, stepVersion: String, moduleDate: String, supplierVersion: String, text: String): String
  {
    val revisedText = text + (if (text.endsWith('.')) "" else ".")
    val revisedModuleDate = if (moduleDate.isBlank()) "" else "$moduleDate "
    return "History_${targetAudience}_$stepVersion=$revisedModuleDate[SupplierVersion: ${supplierVersion.ifEmpty { "N/A" }}] $revisedText"
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
  private data class ParsedHistoryLine (val targetAudience: String, val stepVersion: String, val moduleDate: String, val supplierVersion: String, var text: String)
  {
    fun stepVersionAsKey (): String { return makeKey(stepVersion) }
    override fun toString (): String { return makeHistoryLine(targetAudience, stepVersion, moduleDate, supplierVersion, text) }
  }



  /****************************************************************************/
  private enum class ReleaseType { Major, Minor, Unknown }
  private val C_HistoryLineStepPat = "(?i)History_(?<targetAudience>.*?)_(?<stepVersion>.*?)=(?<date>.*?)\\s+\\[SupplierVersion: (?<supplierVersion>.*?)]\\s*(?<text>.*)".toRegex()
  private var m_ReleaseType = ReleaseType.Unknown
  private var m_HistoryLinesForThisAudience: MutableList<ParsedHistoryLine> = mutableListOf()
  private var m_HistoryLinesNotForThisAudience: List<ParsedHistoryLine> = mutableListOf()
}
