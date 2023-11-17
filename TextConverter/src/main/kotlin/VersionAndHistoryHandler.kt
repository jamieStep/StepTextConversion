package org.stepbible.textconverter

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import java.io.File
import java.time.LocalDate


/******************************************************************************/
/**
 * Handles the history information required in the Sword configuration file.
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
 *   History_1.2=2023-11-01 [TextSupplierVersion: blah] TextSupplierReason: blah
 *
 * The bracketed TextSupplierVersion is the version number as applied by the
 * organisation which has supplied the text.  There's no guarantee we'll have
 * one, and no guarantee, if we do, that it will follow the Crosswire format for
 * version numbers, which is why I record it separately.
 *
 * After this I give the reason for the update.  If this is a new release from
 * the text supplier, the reason will start 'TextSupplierReason' and will give
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
  * process has been called.
  *
  * @return History lines.
  */

  fun getHistoryLines (): List<String>
  {
    return m_HistoryLines.map { it.toString() }
  }


  /****************************************************************************/
  /**
  * Appends history lines to the main config file.  You can't call this until
  * 'process' has been called.
  */

  fun appendHistoryLinesToStepConfigFile ()
  {
    val nonHistoryLines =
      File(StandardFileLocations.getStepConfigFilePath())
        .readLines()
        .filter { !it.trim().lowercase().startsWith("history_") }
        .dropLastWhile { it.trim().isEmpty() }

    val writer = File(StandardFileLocations.getStepConfigFilePath()).bufferedWriter()
    nonHistoryLines.forEach { writer.write(it); writer.write("\n") }
    writer.write("\n")
    m_HistoryLines.forEach { writer.write(it.toString()); writer.write("\n") }
    writer.close()
  }


  /****************************************************************************/
  fun process ()
  {
    /**************************************************************************/
    /* Get a list of any existing history lines in descending version order,
       along with the version number from the most recent history line, or 0.0
       if there are no history lines. */

    run {
      val historyLinesFromStepConfig = getHistoryLinesFromConfigData()
      val historyLinesFromThirdPartyConfig = getHistoryLinesFromThirdPartyConfig()
      val combinedHistoryLines = (historyLinesFromStepConfig + historyLinesFromThirdPartyConfig) // If there are clashes, STEP config wins.
      m_HistoryLines = combinedHistoryLines.keys.sortedDescending().map { combinedHistoryLines[it]!! } .toMutableList()
    }



    /**************************************************************************/
    /* We need to have the history information available on all runs, but we
       update things only on release runs. */

    if ("release" != ConfigData["stepRunType"]!!.lowercase())
      return



    /**************************************************************************/
    /* We now need the most recent previous revision.  This is the latest of
       anything available from the STEP configuration information, any third
       party Sword config file, or anything we can obtain from the history.
       if none of these works, then we take it as 0.0. */

    val previousVersion: String
    run {
      val versionFromConfig = ConfigData["stepVersion"] ?: "0.0"
      val versionFromThirdPartyVersionStatement = getVersionFromThirdPartyConfig()
      val versionFromHistory = if (m_HistoryLines.isEmpty()) "0.0" else m_HistoryLines[0].stepVersion
      val maxKey = maxOf(makeKey(versionFromConfig), makeKey(versionFromThirdPartyVersionStatement), makeKey(versionFromHistory))
      val x = maxKey.split('.')
      previousVersion = x[0].toInt().toString() + "." + x[1].toInt().toString()
    }



    /**************************************************************************/
    val newStepVersion = getNewVersion(previousVersion, m_HistoryLines)
    ConfigData["stepTextRevision"] = newStepVersion



    /**************************************************************************/
    val today = dateToString(LocalDate.now())
    val text =
       when (m_ReleaseType)
       {
         ReleaseType.Major -> "SupplierReason: " + ConfigData.get("stepSupplierUpdateReason", "Unknown") + "; StepReason: ${ConfigData.get("stepUpdateReason", "Supplier updated source documents.")}"
         else -> "StepReason: " + (ConfigData["stepUpdateReason"] ?: "Unspecified.")
       }

    ConfigData.getKeys().filter { it.startsWith("stepHistory_") }. forEach { ConfigData.delete(it) }
    val newHistoryLine = makeHistoryLine(newStepVersion, today, ConfigData["stepTextVersionSuppliedBySourceRepositoryOrOwnerOrganisation"]!!, text)
    ConfigData["stepHistory_$newStepVersion"] = newHistoryLine
    m_HistoryLines.forEach { ConfigData["stepHistory_${it.stepVersion}"] = makeHistoryLine(it.stepVersion, it.moduleDate, it.supplierVersion, it.text)}
    m_HistoryLines.add(0, ParsedHistoryLine(true, newStepVersion, today, ConfigData["stepTextVersionSuppliedBySourceRepositoryOrOwnerOrganisation"]!!, text))


    /**************************************************************************/
    if ("osis" == StandardFileLocations.getRawInputFolderType())
      createUpdatedSwordConfigFileFromThirdPartyFile()
    else
    {
// $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* This is for use only when taking OSIS as the input, when the assumption is
     that you will have been given a ready-made Sword config file, and need to
     update it and store the result into the Sword mods.d folder (which is
     assumed to exist).  You can't call this until you have already called
     'process'. */

  private fun createUpdatedSwordConfigFileFromThirdPartyFile ()
  {
    val x =
      File(StandardFileLocations.getThirdPartySwordConfigFilePath())
        .readLines()
        .dropLastWhile { it.trim().isEmpty() }
        .map { it.trim() }

    val linesToBeCarriedThrough =
      x.filter { val lc = it.lowercase(); !lc.startsWith("history_") && !lc.startsWith("version")&& !lc.startsWith("datapath") }
       .filter { !(it.startsWith('[') && it.endsWith(']')) }


    val writer = File(StandardFileLocations.getSwordConfigFilePath(ConfigData["stepModuleName"]!!)).bufferedWriter()

    writer.write("[${ConfigData["stepModuleName"]!!}]"); writer.write("\n")
    writer.write("# " + ConfigData["stepAdminLine"]!!); writer.write("\n");
    writer.write("DataPath=./modules/texts/ztext/${ConfigData["stepModuleName"]!!}/"); writer.write("\n");
    linesToBeCarriedThrough.forEach { writer.write(it); writer.write("\n") }
    writer.write("\n")
    writer.write("Version=" + ConfigData["stepTextRevision"]!!); writer.write("\n")
    m_HistoryLines.forEach { writer.write(it.toString()); writer.write("\n") }

    writer.close()
  }


  /****************************************************************************/
  private fun dateToString (moduleDate: LocalDate): String
  {
    return moduleDate.year.toString() + "-" + String.format("%02d", moduleDate.month.value) + "-" + String.format("%02d", moduleDate.dayOfMonth)
  }


  /****************************************************************************/
  /* Looks for any history lines in the standard configuration data. */

  private fun getHistoryLinesFromConfigData (): Map<String, ParsedHistoryLine>
  {
    val res: MutableMap<String, ParsedHistoryLine> = mutableMapOf()
    ConfigData.getKeys().filter { it.startsWith("History_") }. forEach {
      val x = parseHistoryLine("$it=" + ConfigData[it]!!)
      res[x.stepVersionAsKey()] = x
    }

    return res
  }


  /****************************************************************************/
  /* Looks for any history lines in any third party configuration file.  This
     is basically of interest where we are taking OSIS as our original input,
     and have therefore been supplied with a prebuilt Sword configuration file,
     rather than constructing one ourselves. */

  private fun getHistoryLinesFromThirdPartyConfig (): Map<String, ParsedHistoryLine>
  {
    val filePath = StandardFileLocations.getThirdPartySwordConfigFilePath()

    if (!File(filePath).exists())
      return mapOf()

    val res: MutableMap<String, ParsedHistoryLine> = mutableMapOf()
    File(filePath)
      .readLines()
      .filter { it.trim().lowercase().startsWith("history_") }
      .forEach { val x = parseHistoryLine(it); res[x.stepVersionAsKey()] = x }
    return res
  }


  /****************************************************************************/
  /* If a third party Sword config file exists and it contains a Version=,
     return the version from that. */

  private fun getVersionFromThirdPartyConfig (): String
  {
    val filePath = StandardFileLocations.getThirdPartySwordConfigFilePath()
    if (!File(filePath).exists())
      return "0.0"

    val line = File(filePath)
      .readLines()
      .firstOrNull { it.trim().lowercase().replace("\\s+".toRegex(), "").startsWith("version=") }
      ?: return "0.0"

    return line.split("=")[1].replace("\\s+".toRegex(), "")
  }


  /****************************************************************************/
  /* Parses either our own stylised history line or an external one.  In the
     latter case, I don't really make any assumptions about the extent to which
     it can be parsed, beyond attempting to look for a yyyy-mm-yy date in it,
     which I extract and assume to be the date the particular revision was
     released. */

  private fun parseHistoryLine (line: String): ParsedHistoryLine
  {
    var mc = C_HistoryLineStepPat.matchEntire(line)
    if (null != mc)
      return ParsedHistoryLine(true, mc.groups["stepVersion"]!!.value, mc.groups["date"]!!.value, mc.groups["supplierVersion"]!!.value, mc.groups["text"]!!.value)

    mc = C_HistoryLineNonStepPat.matchEntire(line)!!
    return ParsedHistoryLine(false, mc.groups["stepVersion"]!!.value, mc.groups["date"]?.value ?: "", "",(mc.groups["preDate"]!!.value + " " + mc.groups["postDate"]!!.value).replace("\\s+".toRegex(), " "))
  }


  /****************************************************************************/
  /* This works out the new details -- what release we're now at, and what the
     history details should look like.  It then adds these details at the top
     of the collection, and also sets the stepTextRevision config parameter. */

  private fun getNewVersion (previousStepVersion: String, historyLines: List<ParsedHistoryLine>): String
  {
    /**************************************************************************/
    /* Unless stepRunType contains 'release', this is a non-release run.

       If it _does_ contain 'release' we need to know whether it's a major
       release or not.  It is if stepRunType included the word 'major'.  It's
       a minor release if it included the word 'minor'.  If it included neither,
       then it's a major release if the version number supplied by the text
       supplier differs from the previous version number. */

    val thisSupplierVersion = ConfigData["stepTextVersionSuppliedBySourceRepositoryOrOwnerOrganisation"]!!
    val previousSupplierVersion = if (historyLines.isEmpty() || !historyLines[0].isStepFormat) "Unknown" else historyLines[0].supplierVersion

    m_ReleaseType = ReleaseType.NonRelease
    if ("release" == ConfigData["stepRunType"]!!)
    {
      when (ConfigData["stepReleaseType"])
      {
        "major" -> m_ReleaseType = ReleaseType.Major
        "minor" -> m_ReleaseType = ReleaseType.Minor
        else    -> m_ReleaseType = if (previousSupplierVersion == thisSupplierVersion) ReleaseType.Minor else ReleaseType.Major
      }
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
  private fun makeHistoryLine (stepVersion: String, moduleDate: String, supplierVersion: String, text: String): String
  {
    val revisedText = text + (if (text.endsWith('.')) "" else ".")
    val revisedModuleDate = if (moduleDate.isBlank()) "" else "$moduleDate "
    return "History_$stepVersion#=$revisedModuleDate[SupplierVersion: ${supplierVersion.ifEmpty { "Unknown" }}] $revisedText"
  }


  /****************************************************************************/
  /* Creates a suitably key for use when ordering history lines.  Keys are of
     the form 002.003 being the STEP version number in a canonical form.
     Reverse alphabetical ordering by key will give the lines in the required
     order. */

  private fun makeKey (stepVersion: String): String
  {
    val x = stepVersion.split('.')
    return String.format("%03d", x[0].toInt()) + "." + String.format("%03d", x[1].toInt())
  }


  /****************************************************************************/
  private data class ParsedHistoryLine (val isStepFormat: Boolean, val stepVersion: String, val moduleDate: String, val supplierVersion: String, val text: String)
  {
    fun stepVersionAsKey (): String { return makeKey(stepVersion) }
    override fun toString (): String { return makeHistoryLine(stepVersion, moduleDate, supplierVersion, text) }
  }



  /****************************************************************************/
  private enum class ReleaseType { NonRelease, Major, Minor, Unknown }
  private val C_HistoryLineNonStepPat = "(?i)History_(?<stepVersion>.*?)=(?<preDate>.*?)(?<date>\\d\\d\\d\\d-\\d\\d-\\d\\d)?(?<postDate>.*?)".toRegex()
  private val C_HistoryLineStepPat = "(?i)History_(?<stepVersion>.*?)=(?<date>.*?)\\s+\\[SupplierVersion: (?<supplierVersion>.*?)]\\s*(?<text>.*)".toRegex()
  private var m_ReleaseType = ReleaseType.Unknown
  private lateinit var m_HistoryLines: MutableList<ParsedHistoryLine>

}
