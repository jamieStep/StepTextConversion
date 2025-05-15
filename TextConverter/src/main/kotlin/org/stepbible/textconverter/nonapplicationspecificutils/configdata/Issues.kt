/******************************************************************************/
package org.stepbible.textconverter.nonapplicationspecificutils.configdata



import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepFileUtils
import java.io.File


/******************************************************************************/
/**
 * Handles information about issues with the text.
 *
 * 'Issues' here is a bit of a catch-all.  It includes outline details of
 * changes which we may have to apply to the text manually in order to make it
 * work; things which we may need to report on the copyright page to admit to
 * changes we have been forced to make; and (TBD) information we might want to
 * give to text suppliers so that they can avoid repeat issues on future
 * releases.
 *
 *
 *
 * ## Data
 *
 * There are various kinds of issues, some of which merely need to be recorded,
 * some of which need to be reported to users and / or suppliers, and some of
 * which imply actions which need to be taken if rebuilding from a new version
 * of the text which has not been fixed.  It may be useful to give some
 * examples:
 *
 * - We received one text which appeared to have been corrupted.  There is
 *   nothing which can be done about this, other than report it to the
 *   supplier.  At present the jury is out on whether we record this as an
 *   issue, or whether we simply report it.
 *
 * - A text contained broken cross-references.  The USX was perfectly valid,
 *   but incomplete.  Although fixable in theory, there seemed little point
 *   in giving over effort to it.  Here I opted to record the existence of
 *   the issue (and we also intended to report it to the supplier, but I
 *   did not record a remedial action, and did not consider it worth mentioning
 *   on the copyright page.
 *
 * - A text contained invalid USX tags.  By inspection it was fairly clear what
 *   was intended, but it would have had no visible impact within STEP.  Here
 *   I recorded both the existence of the issue and the remedial action, but
 *   did not record any text to appear on the copyright page.  Again this is
 *   something we would report to the supplier.
 *
 * - All open access texts use Crosswire versification schemes, and osis2mod
 *   may restructure texts which don't conform exactly to the selected scheme.
 *   Given that we simply make the assumption there may be an issue here, I
 *   do not record the issue in the issues list, but the present class *does*
 *   arrange for it to be reported on the copyright page.
 *
 *
 *
 * ## File format
 *
 * The file should be called issues.json and be stored in the Metadata folder.
 * It should look something like this:
 *
 * ```
 *   {
 *     "moduleName": "che_CAC2012",
 *     "issues":
 *     [
 *         {
 *             "date": "2025-04-21",
 *             "affects": "USX",
 *             "description": "A number of files contain cross-references which lack the attribute used to indicate the target of the cross-reference.",
 * 	           "remedialAction": "None -- the cross-references themselves will not work, but the issue doesn't break anything.",
 * 	           "textForCopyrightPage": null
 *         }
 *     ]
 *   }
 *   ```
 *
 * If there are no issues to record, then there is no need to have the file.
 *
 * moduleName should give enough information to enable us to be certain which
 * module the file belongs to, should it become separated from other things.
 *
 * issues should always ne present.
 *
 * Each individual issue must have a date field.  The other fields shown above
 * are the ones I look for, but you can add other fields if you feel they would
 * be useful.
 *
 * Normally I anticipate issues are going to reflect changes which need to be
 * applied manually to the input.
 *
 * 'affects' says which form(s) of input need attention.
 *
 * 'description' is intended to help a human reader looking at the file: it is
 * not used by any processing.
 *
 * 'remedialAction' explains what changes are required.  If omotted, or if it
 * starts with the word 'None', then it is assumed that no changes are required.
 *
 * 'textForCopyrightPage' gives any text which should appear on the copyright
 * page to report the details to the user.
 *
 *
 *
 * ## Actions
 *
 * getDetailsForCopyrightPage returns details of any text which needs to appear
 * on the copyright page.  This includes any non-null textForCopyrightPage
 * entries from the issues file, along (possibly) with other standard text.
 * The value is returned as a per-formatted block.
 *
 * getRemedialActions returns details of any remedial actions which should have
 * been applied to the text, so we can confirm that they have indeed been
 * applied.
 *
 * @author ARA "Jamie" Jamieson
 */

object Issues: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Returns the information which needs to be added to the copyright page.
  * This covers our obligation to admit to changes which we have had to
  * apply and which might impact what people see.  More information appears in
  * the in-code comments.
  *
  * @return Information for copyright page, or an empty string if none.
  */

  fun getDetailsForCopyrightPage (): String
  {
    /**************************************************************************/
    /* Not too sure about this.  In reality there are _some_ kinds of changes
       that we _always_ have to make (expanding elisions, for example).  So
       long as I've got them right, these changes should have no material
       impact upon the content or structure of the data which the users see
       (although in a few cases, the changes _may_ be more apparent -- for
       example if I have to convert tables into elisions); and we have little
       control over what osis2mod may do.

       With copyright texts, the assumption has to be that we aren't allowed
       to make changes at all.

       These two things don't sit together too well.  I've decided here that
       on copyright texts we just can't reasonably admit to anything, even
       though on non-copyright texts (dealt with in the remainder of this
       method) I _do_ do so. */

    if (ConfigData.getAsBoolean("stepIsCopyrightText"))
      return ""



    /**************************************************************************/
    val addedValue: MutableList<String> = mutableListOf()
    if (ConfigData.getAsBoolean("stepAddedValueMorphology", "No")) addedValue.add(TranslatableFixedText.stringFormatWithLookup("V_addedValue_Morphology"))
    if (ConfigData.getAsBoolean("stepAddedValueStrongs", "No")) addedValue.add(TranslatableFixedText.stringFormatWithLookup("V_addedValue_Strongs"))
    var english    = TranslatableFixedText.stringFormatWithLookupEnglish("V_modification_FootnotesMayHaveBeenAdded")
    var vernacular = TranslatableFixedText.stringFormatWithLookup       ("V_modification_FootnotesMayHaveBeenAdded")
    var s = english
    if (vernacular != english) s += " / $vernacular"
    addedValue.add(s)




    /**************************************************************************/
    val amendments: MutableList<String> = mutableListOf()
    english    = TranslatableFixedText.stringFormatWithLookupEnglish("V_modification_VerseStructureMayHaveBeenModified", ConfigData["stepVersificationScheme"]!!)
    vernacular = TranslatableFixedText.stringFormatWithLookup       ("V_modification_VerseStructureMayHaveBeenModified", ConfigData["stepVersificationScheme"]!!)
    s = english
    if (vernacular != english) s += " / $vernacular"
    amendments.add(s)



    /**************************************************************************/
    val deletedBooks = ConfigData["stepDeletedBooks"]
    if (null != deletedBooks)
      amendments.add("Software limitations mean we have had to remove the following books: ${deletedBooks}.")



    /**************************************************************************/
    amendments.addAll(getCopyrightPageStatementsFromIssuesList())



    /**************************************************************************/
    var text = ""
    if (addedValue.isNotEmpty())
    {
      text += "<br>We have added the following information:<br><div style='padding-left:1em'>"
      text += addedValue.joinToString("<br>- ", prefix = "- ", postfix = "<br>")
      text += "</div><br>"
    }



    /**************************************************************************/
    if (amendments.isNotEmpty())
    {
      text += "<br>We may have made minor changes to the text so that our processing can handle it:<br><div style='padding-left:1em'>"
      text += amendments.joinToString("<br>- ", prefix = "- ", postfix = "<br>")
      text += "</div><br>"
    }



    /**************************************************************************/
    val acknowledgementOfDerivedWork = ConfigData["swordWordingForDerivedWorkStipulatedByTextSupplier"]
    if (null != acknowledgementOfDerivedWork) text += "<br>$acknowledgementOfDerivedWork"



    /**************************************************************************/
    return text
  }


  /****************************************************************************/
  /**
  * Returns a list of all remedial actions.
  *
  * @return Remedial actions.
  */

  fun getRemedialActions () : List<String>
  {
    loadIssuesList()
    return if (null == m_Issues)
      emptyList()
    else
      return m_Issues!!.issues.filter { !it.remedialAction.startsWith("None", ignoreCase = true) }.map { it.description + ": " + it.remedialAction }
  }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Private                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun getCopyrightPageStatementsFromIssuesList (): List<String>
  {
    loadIssuesList()
    return if (null == m_Issues)
      emptyList()
    else
      m_Issues!!.issues.mapNotNull { it.textForCopyrightPage }
  }


  /****************************************************************************/
  private fun loadIssuesList ()
  {
    if (null != m_Issues) // Don't re-read.
      return

    if (!StepFileUtils.fileOrFolderExists(FileLocations.getIssuesFilePath())) // There may not be an issues list.
      return

    val json = Json {
      ignoreUnknownKeys = true
      isLenient = true
      allowStructuredMapKeys = true
    }

    m_Issues = json.decodeFromString<ModuleIssues>(File(FileLocations.getIssuesFilePath()).readText())
  }


  /****************************************************************************/
  private var m_Issues: ModuleIssues? = null


  /****************************************************************************/
  @Serializable data class Issue (val date: String, val affects: String, val description: String, val remedialAction: String, val textForCopyrightPage: String?, val extra: Map<String, String> = emptyMap())
  @Serializable data class ModuleIssues (val moduleName: String, val issues: List<Issue>)
}
