/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.io.path.Path
import kotlin.io.path.exists


/******************************************************************************/
/**
 * Handles the change history file.
 *
 * A template for the file appears in the Resources section.  The file itself
 * is held in the Metadata folder for the given text.
 *
 * The processing here copies the template file to the Metadata folder if no
 * local copy yet exists.  Either way, it may then add a new row to the file.
 *
 * The processing here differs according to whether we are dealing with a source
 * like DBL (where we can pick up information directly from a metadata file),
 * or with some other source where we are not in that position.
 *
 * With DBL, it checks to see if the versionId in the text supplier's metadata
 * is the same as that in the most recent history row (assuming there is a
 * history row).  If it is not, it automatically generates a new row to cater
 * for this new version, and then exits.
 *
 * Otherwise (with DBL or not), it looks to see if the stepInternalUpdateReason
 * command-line parameter has been supplied, in which case it generates a row
 * carrying the most recent version number, but with the value of this parameter
 * as explanation for the change.
 *
 * As a reminder, history rows are of the form:
 *
 *    History yyyy-mm-dd <versionId> Explanatory text
 *
 * where
 *
 * yyyy-mm-dd is the date on which the module was generated (_not_, in the
 * case of supplier-instigated changes, the date on which the supplier made
 * the change -- that's often too difficult to identify reliable).
 *
 * <versionId> is the version identifier which relates to the supplied text
 * itself (ie the id most recently assigned by the text supplier).
 *
 * See historyTemplate.conf in the Resources section of this file for more
 * details.
 *
 * @author ARA "Jamie" Jamieson
 */

object ChangeHistoryHandler
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process ()
  {
    /*************************************************************************/
    /* Optional command-line parameter.  If present, this indicates this run
       is creating a new module which we intend to re-issue, and is doing so
       for internal reasons -- eg because we've noticed a bug in the
       conversion processing which has affected the text and need to remedy
       matters. */

    val stepInternalUpdateReason = ConfigData["stepInternalUpdateReason"]



    /*************************************************************************/
    /* Regardless of the source of the data, we need to look at any
       existing history information, and pick out the most recent history
       line.  We need the line itself so we know what the most recent
       supplier-version was; and we need to know its index so we can insert
       any new row ahead of it.  (We need to retain all of the lines, so that
       we can output a revised file containing all of the old data as well as
       the new stuff.)

       On the very first run for a given module, there will not be any history
       file, so here we work as though the history file were a copy of the
       template history file which can be found in the resources section of
       this JAR file. */

    val targetPath = StandardFileLocations.getHistoryFilePath()
    val sourcePath = if (Path(StandardFileLocations.getHistoryFilePath()).exists()) targetPath else StandardFileLocations.getHistoryTemplateFilePath()
    val lines = StandardFileLocations.getInputStream(sourcePath, null)!!.bufferedReader().use { it.readText() } .lines().toMutableList()
    val firstExistingHistoryLineIx = lines.indexOfFirst { it.matches("(?i)history.*".toRegex()) }

    val mostRecentStoredSupplierVersion =
      if (-1 == firstExistingHistoryLineIx)
        "%dummy%"
      else lines[firstExistingHistoryLineIx].split("||")[1].trim()



    /*************************************************************************/
    /* We need do something here only if this is a new version from the
       supplier, or if this is a re-issue for internal reasons, or both. */

    val newSupplierVersion = ConfigData["stepTextVersionSuppliedBySourceRepositoryOrOwnerOrganisation"]!!.replace("\\s+", "")
    val updatedSupplierVersion = !mostRecentStoredSupplierVersion.equals(newSupplierVersion, ignoreCase = true)

    if (null == stepInternalUpdateReason && !updatedSupplierVersion)
      return



    /*************************************************************************/
    /* reasonForUpdate is based upon either the command line parameter, or
       a configuration parameter (possibly derived from DBL metadata where
       we're dealing with DBL texts), or both. */

    var reasonForUpdate = ""
    if (updatedSupplierVersion)
      reasonForUpdate = "TextSupplierComment: ${ConfigData.get("stepSupplierUpdateReason")!!}"

    if (reasonForUpdate.isNotEmpty() && null != stepInternalUpdateReason)
      reasonForUpdate += " || "

    if (null != stepInternalUpdateReason)
      reasonForUpdate += stepInternalUpdateReason



    /*************************************************************************/
    /* Earlier on, we ruled out the possibility that no history line was
       needed.  We therefore know that a line is needed, and the standard
       format requires that it starts 'History yyyy-mm-dd <versionId> ...' */

    val currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val newHistoryLine = "History $currentDate || $newSupplierVersion || $reasonForUpdate"



    /*************************************************************************/
    /* Insert the new line at the head of the history lines. */

    if (-1 == firstExistingHistoryLineIx)
      lines.add(newHistoryLine)
    else
      lines.add(firstExistingHistoryLineIx, newHistoryLine)

    File(targetPath).writeText(lines.joinToString("\n"))
  }
}

