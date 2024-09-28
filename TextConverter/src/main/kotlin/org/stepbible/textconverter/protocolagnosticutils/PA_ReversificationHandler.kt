/******************************************************************************/
package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.applicationspecificutils.IssueAndInformationRecorder
import org.stepbible.textconverter.applicationspecificutils.Permissions
import org.stepbible.textconverter.applicationspecificutils.ReversificationRuleEvaluator
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.TranslatableFixedText
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils.convertNumberToRepeatingString
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.StepStringUtils.capitaliseWords
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.findNodesByAttributeName
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.nonapplicationspecificutils.shared.Language
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionBase
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.stepbible.textconverter.protocolagnosticutils.PA_ReversificationHandler.CalloutDetails
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.net.URL
import java.util.*


/******************************************************************************/
/**
 * Reads and stores reversification data and provides summary information about
 * it.
 *
 *
 *
 *
 * ## Use
 *
 * This class should never be instantiated directly.  Callers wishing to use a
 * reversification handler should use the *instance* method to return an
 * instance of a flavour of reversification handler relevant to the present
 * run.
 *
 *
 *
 *
 * ## Reversification flavours
 *
 * At one stage we were contemplating two forms of reversification -- one, which
 * I dubbed 'conversion-time' entailed physically restructuring the text during
 * the conversion process so as to end up with a module which was fully NRSVA
 * compliant.  The other ('runtime') involved -- at least to a first
 * approximation, nothing more than recording details of the way in which the
 * text deviated from NRSVA, this information then being used by a revised form
 * of osis2mod and the run-time system to restructure the text on the fly when
 * NRSVA compliance was needed in support of STEPBible's added value features.
 *
 * At the time of writing, conversion-time restructuring is no longer being
 * considered seriously, since such restructuring is ruled out by the licence
 * conditions on most copyright texts (and would also result in a Bible which
 * differed from the expectations of users acquainted with the text).
 *
 * In fact, I have retained my old conversion-time processing, although the
 * chances of it working without further attention are negligible.
 *
 * The present class analyses the data only to the extent needed for runtime
 * reversification.  In fact, this analysis is also relevant to conversion-
 * time restructuring, although that needs to build upon it to create
 * additional data structures of its own.
 *
 *
 *
 *
 *
 * ## About the data
 *
 * Each reversification row is made up of a number of named fields, and gives
 * rise to a single action.
 *
 * Not all rows are applicable to all texts.  Most rows have an associated
 * Test field, and the row applies only if all of the tests pass.  (Typical
 * tests include checking whether a given verse exists, whether a given verse is
 * the last in its owning chapter, or whether the content-length of two verses
 * stand in a particular relationship.)  The Test field may be empty, in which
 * case the test is assumed to pass.  Note that length tests applied to elided
 * verses always fail, because we have no way of determining the correct length
 * of an elided field.
 *
 * In some cases, the name of the action may imply additional tests not already
 * in the Test field -- checking the source ref for existence, or the standard
 * ref for non-existence, or whatever.  The processing automatically adds these
 * tests into the Test field here.
 *
 * All of this is taken into account automatically within the *process* method
 * here, so that by the time this method returns, the appropriate
 * reversification rows will have been selected for use by the caller.
 *
 *
 *
 *
 *
 * ## Data externally available $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$
 *
 * I make available the following data for external use:
 *
 * - A full list of all selected rows.  This is needed in support of adding
 *   footnotes when permitted, and may also be useful for debugging purposes.
 *
 * - A list of those rows which may entail creating empty verses.  I have to
 *   create such verses during the conversion process, because osis2mod
 *   cannot cope if there are holes in the versification structure.
 *
 * - A list of those rows which have to be mentioned in the JSON file required
 *   by osis2mod.
 *
 *
 * Note that the reversification processing may create subverses in some
 * cases.  osis2mod and JSword cannot handle subverses and they therefore need
 * to be removed from the output.  In view of this it may seem odd that details
 * of such verses are not available from the present class.  However, the
 * processing at large must remove *all* subverses, whether generated by
 * reversification or present in the raw text, so knowing which subverses
 * were generated specifically by reversification is of no value to it.
 *
 * @author ARA "Jamie" Jamieson
 */

/******************************************************************************/
open class PA_ReversificationHandler protected constructor (): PA()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Companion object                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  companion object
  {
    /**************************************************************************/
    /**
    * Returns an instance of the flavour of reversification handler appropriate
    * to the present run.
    *
    * @return Instance of reversification handler.
    */

    fun instance (): PA_ReversificationHandler
    {
      if (null == m_Instance)
        m_Instance = if (ConfigData.getAsBoolean("stepConversionTimeReversification", "no")) TODO() else PA_ReversificationHandler_RunTime
      return m_Instance!!
    }

    private var m_Instance: PA_ReversificationHandler_RunTime? = null


    /**************************************************************************/
    /**
     * The reversification data uses its own formats for references.  It's
     * convenient to convert this to USX for processing.
     *
     * @param theStepRef Reference in STEP format.
     * @return String representation of an equivalent reference in USX format.
     *         Note that this may differ from the original in respect of
     *         whitespace, separators, etc, but since we need it only for
     *         parsing, that's not an issue.
     */

    fun usxifyFromStepFormat (theStepRef: String): String
    {
      /************************************************************************/
      //Dbg.d(theStepRef)



      /************************************************************************/
      /* Get the reference string into canonical form.  The input may contain
         commas or semicolons as collection separators, and since the parsing
         processing is set up to handle either, it's convenient here to convert
         them all to just one form. */

      val stepRef = theStepRef.replace(",", ";")
                              .replace("--", "-")
                              .replace("–", "-")
                              .replace(" +", "")
                              .replace("•", "") // \u2022 -- Arabic zero.
                              .replace("٠", "") // \u0660 -- Bullet point, used in some places instead of Arabic zero.
                              .replace("([1-9A-Za-z][A-Za-z][A-Za-z]\\.)".toRegex()) { it.value.replace(".", " ") }
                              .replace("(?i)title".toRegex(), "title")
                              .replace("(?i):T$".toRegex(), "")// We have things like 53:T as the alternative reference on some Psalm rows.  I change these back to be chapter references.


      /************************************************************************/
      fun processCollectionElement (elt: String) = elt.split("-").joinToString("-"){ usxify1(it) }
      return stepRef.split(";").joinToString(";"){ processCollectionElement(it) }
    }


    /**************************************************************************/
    /* The reversification data has its own view of how references should be
       represented, and to save having to include specialised code to cater for
       these, it's convenient to convert to USX format up-front. */

    private fun usxify1 (theStepRef: String): String
    {
      /************************************************************************/
      /* Replace the full stop after the book name with a space. */

      var stepRef = theStepRef
      if (stepRef.matches("...\\..*".toRegex()))
        stepRef = stepRef.substring(0, 3) + " " + stepRef.substring(4)



      /************************************************************************/
      /* I _think_ we can forget subverse zero.  Otherwise, if we have numeric
         subverses, change them to alphabetic. */

      if (stepRef.endsWith(".0"))
        stepRef = stepRef.substring(0, stepRef.length - 2)
      else if (stepRef.matches(".*\\.\\d+$".toRegex()))
      {
        val ix = stepRef.lastIndexOf(".")
        val subverseNo = Integer.parseInt(stepRef.substring(ix + 1))
        stepRef = stepRef.substring(0, ix) + convertNumberToRepeatingString(subverseNo, 'a', 'z')
      }



      /************************************************************************/
      return stepRef
    }


    /**************************************************************************/
    /* Used within the present class to indicate which checks should be applied
       when trying to determine which reversification rows to select. */

    private const val NoCheck                            = 0x00000000
    private const val CheckSourceRefExists               = 0x00000001
    private const val CheckStandardRefNonExistent        = 0x00000002
    private const val CheckStandardRefEmptyOrNonExistent = 0x00000004

  } // companion object





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  open fun process (dataCollection: X_DataCollection)
  {
    extractCommonInformation(dataCollection, wantBibleStructure = true)
    setReversificationNotesLevelForRun()
    load(dataCollection)
  }


  /****************************************************************************/
  /**
  * Returns information needed by our own version of osis2mod and JSword to
  * handle the situation where we have a Bible which is not NRSV(A)-compliant,
  * and which we are not restructuring during the conversion process.
  *
  * The return value is a list of RefKey -> RefKey pairs, mapping source verse
  * to standard verse.  (On PsalmTitle rows, the verse for the standard ref is
  * set to zero.)
  *
  * @return List of mappings, ordered by source RefKey.
  */

  open fun getRuntimeReversificationMappings (): List<Pair<RefKey, RefKey>> = listOf()


  /****************************************************************************/
  /**
  * Returns a list of all accepted rows.
  *
  * @return List of accepted rows.
  */

  fun getSelectedRows () = m_SelectedRows


  /****************************************************************************/
  /**
  * Returns a string representation of all accepted rows.  Intended mainly for
  * debugging and, if recorded within the STEP repository package for the
  * module, to make it possible to identify modules with certain
  * characteristics.
  *
  * @return List of accepted rows.
  */

  fun getSelectedRowsAsStrings () = m_SelectedRows.map { it.toString() }


  /****************************************************************************/
  /**
  * Some Moves or Renumbers target subverse 2 of a verse; and for some (but not
  * all) of these, there is no corresponding row which targets subverse 1.
  * For these, the reversification data implicitly assumes that the original
  * verse itself serves as subverse 1.  Thus, for example, we have something
  * which targets Num 20:28b, but nothing which targets Num 20:28a.  The
  * reversification data can be taken as assuming that 20:28 itself serves as
  * 20:28a, and does not need to be handled during the reversification
  * processing.
  *
  * I need to know about all cases where this is the case, because during
  * validation I need to know what source text fed into a particular
  * standard verse.
  *
  * This method returns a set of all the subverse 2 refKeys where this is the
  * case.
  *
  * @return Set of refKeys as described above.
  */

  fun getImplicitRenumbers (): Set<RefKey>
  {
    val standardRefs = m_SelectedRows.map { it.standardRefAsRefKey }.toSet()
    return m_SelectedRows.asSequence()
                         .filter { 2 == Ref.getS(it.standardRefAsRefKey) }
                         .filter { Ref.setS(it.standardRefAsRefKey, 1) !in standardRefs }
                         .filter { Action.RenumberVerse == it.action }
                         .map { it.standardRefAsRefKey }
                         .toSet()
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Data-load                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* This section loads the data.  At the end of processing, m_SelectedRows
     contains a full collection of ReversificationDataRow entries, representing
     the reversification rows which have been selected.

     I also canonicalise the data while doing this.  This caters for several
     issues:

     - In some cases, it seems to me that information has been placed in one
       field which should really appear in a different one.

     - I parse some of the more complex fields into constituent elements.

     - The data does not handle scripture references in an entirely
       consistent manner.  Some follow USX.  Some _almost_ follow USX, but
       have slightly the wrong syntax.  I remedy the latter.

     - Also there are some places which contain things which are really
       intended to be scripture references, but which don't quite fit the
       mould because they need to be extended to carry information which doesn't
       naturally fit into a scripture reference.  I try to sort these out, such
       that if I need to convert these entries to vernacular form, I stand a
       better chance of doing so.
   */

  /****************************************************************************/
  /**
  * Controls the overall loading of the data.
  *
  * @param dataCollection Data to which the reversification data is to be
  *   applied.
  */

  private fun load (dataCollection: X_DataCollection)
  {
    Dbg.withReportProgressSub("Reading reversification data and checking applicability.") {
      load_1(dataCollection)
    }

    if (m_SelectedRows.isNotEmpty())
      IssueAndInformationRecorder.setRuntimeReversification()
  }


  /****************************************************************************/
  /* Locates the relevant data within the input file and then reads it in and
     arranges to parse and canonicalise it. */

  private fun load_1 (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    /* Make commonly-used items readily available. */

    m_DataCollection = dataCollection
    m_BibleStructure = dataCollection.getBibleStructure()
    m_FileProtocol = dataCollection.getFileProtocol()
    m_RuleEvaluator = ReversificationRuleEvaluator(dataCollection)



    /**************************************************************************/
    /* Read the full data from the location determined by the configuration
       data.  Preferably this will be our online data repository, where the
       data can be guaranteed to be up to date.  But you can also use a local
       file, for example if that will help with testing. */

    val dataLocation = ConfigData["stepExternalDataPath_ReversificationData"]!!
    if (!dataLocation.startsWith("http")) Logger.warning("Running with local copy of reversification data.")
    val rawData = (if (dataLocation.contains("http")) URL(dataLocation).readText() else File(dataLocation).readText()).split("\n")



    /****************************************************************************/
    /* Used when Looks for a row containing a given piece of text.  Used to
       identify where the reversification data of interest to us starts and ends
       within the overall text. */

    fun findLine (data: List<String>, lookFor: String, startAt: Int): Int
    {
      val lookForLower = lookFor.lowercase()
      for (i in startAt..< data.size)
        if (data[i].lowercase().startsWith(lookForLower)) return i

      throw StepExceptionBase("Guard row $lookFor missing from reversification data")
    }



    /**************************************************************************/
    /* I do at least make the assumption that the online data and any local
       copy will follow precisely the same format.  The data of interest is
       delimited by two markers, and then we need to remove from the resulting
       sublist comment lines (which start with '#'), blank lines, lines starting
       '=' (which at one point were used to underline headings), and lines
       starting with a single quote (which latterly has been used at the start
       of heading lines, so that they don't necessarily start with an equals
       sign as I think they used to.

       I keep a row count for debugging purposes -- it makes progress reporting
       more meaningful, and also makes it easier to determine which line is
       being processed if something goes wrong. */

    val ixLow = findLine(rawData, "#DataStart(Expanded)", 0)
    val ixHigh = findLine(rawData, "#DataEnd", ixLow)
    val filteredData = rawData.subList(ixLow + 1, ixHigh).map { it.trim() }.filterNot { it.startsWith('#') || it.isBlank() || it.startsWith('=') || it.startsWith('\'')  }
    Dbg.withProcessingBooks("Parsing reversification data (total of ${filteredData.size} rows) ...") { // Report progress in the same way as when processing books.
      var rowNumber = 0
      filteredData.forEach { loadRow(it, ++rowNumber) }
      Logger.announceAllAndTerminateImmediatelyIfErrors()
      debugOutputDebugData()
    }

    //Dbg.d(m_SelectedRows)
  }


  /****************************************************************************/
  /* Adds details of a single row, assuming it passes the relevant tests. */

  private fun loadRow (rawData: String, rowNumber: Int)
  {
    /**************************************************************************/
    //Dbg.d(rowNumber)
    //Dbg.d(rawData)
    //Dbg.dCont(rawData, "Gen.32:33")



    /**************************************************************************/
    if (rowNumber == 1000 * (rowNumber / 1000))
      Dbg.withProcessingBook(rowNumber.toString()) {} // This implausible-looking call presses into service something not really intended for this, but which lets us report multiple progress items on a single line.



    /**************************************************************************/
    /* Fields in the reversification row are tab-separated.  Split them out,
       create a ReversificationDataRow to hold the data.  The slightly odd code
       below works as follows:

       If we haven't yet initialised the column map, we can take it that the
       data we have read is correct -- we take it as-is, split it out, and
       that gives us all of the column names.

       If we _have_ initialised the column map, we may, on occasion, have a
       truncated row, if the reversification data should end with blank fields
       but they have been missed off.  In this case I add a load of trailing
       tabs to give the impression that the data is longer than it actually
       was, so that I'm guaranteed to have all the fields I need. */

    val fields = (if (ReversificationDataRow.headersInitialised()) rawData + "\t\t\t\t\t\t\t\t" else rawData).split("\t").map { it.trim() }.toMutableList()
    val rawRow = ReversificationDataRow(rowNumber)
    rawRow.fields = fields



    /**************************************************************************/
    /* If this row is actually the header row, we merely want to record what
       the headers are and map them to field numbers, but not store the row as
       if it where a data row. */

    if (!ReversificationDataRow.headersInitialised())
    {
      var n = -1
      fields.forEach { ReversificationDataRow.setHeader(it, ++n) }
      return
    }



    /**************************************************************************/
    /* We may want to ignore some rows.  The reversification data may, in a few
       cases, refer to books which actually do not exist in the USX naming
       scheme, and we can therefore never process them.  And if this is a
       debugging run, being applied only to selected books, we can ignore rows
       for any other books. */

    if (ignoreRow(rawRow))
      return



    /**************************************************************************/
    /* There are various anomalies in the data, or things which I'd prefer were
       represented differently.  Sort these out. */

    canonicaliseAndCorrectData(rawRow)



    /**************************************************************************/
    /* Checks that the row does actually apply, and performs miscellaneous
       additional processing -- things like parsing the NoteMarker and
       AncientVersions fields, which are quite complicated.  Returns null if
       the row is not accepted. */

    val processedRow = convertToProcessedForm(rawRow)
    if (null != processedRow)
      m_SelectedRows.add(processedRow)



    /**************************************************************************/
    /* May be useful for debugging. */

    if (null == processedRow)
      debugRecordReversificationRowRejected(rawRow)
    else
      debugRecordReversificationRowAccepted(processedRow)
  }


  /****************************************************************************/
  /* There are some inconsistencies / infelicities in the reversification data,
     and processing is easier if these are sorted out.  This method leaves
     the 'fields' array of dataRow unchanged.  However, the action, sourceRef
     and standardRef fields may be set using revised copies of these fields,
     so as to correct the inconsistencies.

     This does mean that these specific individual fields may not exactly
     mimic the content of the 'fields' array, but on the other hand it means
     that the 'fields' array is still as read from the reversification data,
     which may simplify debugging. */

  private fun canonicaliseAndCorrectData (dataRow: ReversificationDataRow)
  {
    /**************************************************************************/
    fun augmentTest (s: String)
    {
      var test = dataRow["Tests"]
      if (test.isNotEmpty()) test += " & "
      dataRow["Tests"] = test + s
    }



    /**************************************************************************/
    /* At the time of writing we have alternative names for some Action values.
       The deprecated ones I swap for the preferred values.

       Some Actions implicitly require checks for the existence of the source
       ref.  These tests I add to the Test field.

       Some require that the standard ref not exist; and some require that
       either that the standard ref not exist, or that it exist and be empty.

       At some point in the future, the reversification data may be altered to
       include these tests.  Pro tem, I'm adding them to the Test field here. */

    var sourceRef = dataRow["StandardRef"]
    var standardRef = dataRow["StandardRef"]
    val x = capitaliseWords(dataRow["Action"].replace("*", "")).replace(" ", "") // '*' is used to mark actions which change the location of a verse.  With the processing as it stands, this is not relevant.
    val action = C_ActionsWhichShouldBeRenamed[Action.valueOf(x)] ?: Action.valueOf(x)

    if (0 != action.checks and CheckSourceRefExists)
      augmentTest("$sourceRef=Exist")

    if (0 != action.checks and CheckStandardRefEmptyOrNonExistent)
      augmentTest("$standardRef=EmptyOrNotExist")
    else if (0 != action.checks and CheckStandardRefNonExistent)
      augmentTest("$standardRef=NotExist")

    dataRow.action = action
    dataRow.isMove = "*" in dataRow["Action"]
    action.isMove = dataRow.isMove




    /**************************************************************************/
    /* There are several parts to sorting out the references.  First step is to
       convert them to USX form (unfortunately they aren't in that form in the
       reversification data -- or at least, not the source or standard ref). */

    sourceRef   = usxifyFromStepFormat(dataRow["SourceRef"])
    standardRef = usxifyFromStepFormat(dataRow["StandardRef"])
    dataRow.sourceIsPsalmTitle = "title" in sourceRef.lowercase()



    /**************************************************************************/
    /* If either sourceRef or standardRef refer to subverse zero, the subverse
       can be dropped -- USX doesn't recognise subverse zero, so we won't have
       anything marked as such in the input (it will just be the verse), and
       we don't want to create anything like that in the output. */

    if (sourceRef.endsWith(".0"))
      sourceRef = sourceRef.replace(".0", "")

    if (standardRef.endsWith(".0"))
      standardRef = standardRef.replace(".0", "")



    /**************************************************************************/
    /* It is rather convenient to treat anything involving a title as actually
       involving a verse.  I distinguish this verse by giving it a special verse
       number (499 at the time of writing).

       One downside to using this is that if I fabricate a canonical title,
       it's going to come out right at the end of the chapter, so I need to
       move it back when I've finished processing.  From that point of view,
       using v0 as the special marker would be better.  But the problem is that
       everything is set up to believe that a reference whose verse number is
       zero is a chapter reference, and that makes life very difficult.

       Of course, this will only work if I pre-process the text to change any
       existing canonical titles to be of this same form (and if I change them
       back again before going on to generate the OSIS).  Clearly this is a
       complication, both in terms of having to do it, and also in terms of the
       confusion it's likely to cause when it comes to maintaining the code.  I
       _think_ it's worth it, though. */

    if ("title" in standardRef.lowercase())
    {
      standardRef = standardRef.replaceFirst("(?i)title".toRegex(), RefBase.C_TitlePseudoVerseNumber.toString())
      if ("title" !in sourceRef.lowercase()) // If, rather than an existing title, we're using verses to create the title, make it appear they are creating subverses of the special verse.
      {
        val sourceVerse = Ref.rdUsx(sourceRef).getV()
        standardRef += convertNumberToRepeatingString(sourceVerse, 'a', 'z')
      }
    }

    if ("title" in sourceRef.lowercase())
      sourceRef = sourceRef.replace("(?i)title".toRegex(), RefBase.C_TitlePseudoVerseNumber.toString())



    /**************************************************************************/
    dataRow.sourceRef = Ref.rdUsx(sourceRef)
    dataRow.standardRef = Ref.rdUsx(standardRef)
    dataRow.sourceRefAsRefKey   = dataRow.sourceRef.toRefKey()
    dataRow.standardRefAsRefKey = dataRow.standardRef.toRefKey()
  }


  /****************************************************************************/
  /* Identifies rows which should not be processed. */

  private fun ignoreRow (dataRow: ReversificationDataRow): Boolean
  {
    /**************************************************************************/
    /* Probably temporary only.  Currently the data contains things like eg
       Renumber before Est.1:2, which I think should be going shortly. */

    val action = dataRow["Action"]
    if (action.lowercase().contains("est."))
      return true



    /**************************************************************************/
    /* The reversification data contains a few rows for 4Esdras.  We need to
       weed these out, because the USX scheme doesn't recognise this book. */

    val sourceRef = dataRow["SourceRef"]
    if (sourceRef.contains("4es", ignoreCase = true))
      return true



    /**************************************************************************/
    /* For debug purposes it is often convenient to process only a few books.
       We need to ignore reversification data for any where the source
      reference is for a book which we are not processing. */

    if (!Dbg.wantToProcessBookByAbbreviatedName(sourceRef.substring(0, 3))) return true



    /**************************************************************************/
    return false
  }


  /****************************************************************************/
  /* Canonicalises the contents of a single row and adds it to the various
     collections if it passes any applicability tests.  Note that by the
     time we get here, we can be sure there are no blank rows or comment rows
     in the data.

     You may observe that there are a number of points within the processing
     where it becomes apparent that the row being processed does not apply to
     the present text, and from an efficiency point of view it would make sense
     to abandon further processing at that point.  However, it is convenient to
     stick with it to the end, because the processed form of the row may be
     useful for debugging purposes. */

  private fun convertToProcessedForm (dataRow: ReversificationDataRow): ReversificationDataRow?
  {
    /**************************************************************************/
    //Dbg.d(5406 == dataRow.rowNumber)



    /**************************************************************************/
    /* Check to see if the row actually applies at all.  There are several parts
       to this.

       First 'Does this row apply?' check: Does it relate to a book which we are
       actually processing on this run?  (It might not do if we're dealing with
       a partial text, or have opted to process only selected books, perhaps to
       speed up debugging.) */

    if (!m_BibleStructure!!.bookExists(dataRow.sourceRef))
      return null



    /**************************************************************************/
    /* Second 'Does this row apply?' check: If the Test field contains anything,
       does the rule pass? */

    val ruleData = dataRow["Tests"]
    if (!m_RuleEvaluator.rulePasses(ruleData, dataRow.toString()))
      return null



    /**************************************************************************/
    setCalloutAndFootnoteLevel(dataRow)
    setAncientVersions(dataRow["Ancient Versions"])



    /**************************************************************************/
    return dataRow
  }


  /****************************************************************************/
  /* New format callout processing.

     In early 2023, the original NoteMarker content was changed.  This method
     extracts salient information from that field.  The notes below give details
     of the format and the processing.

     A 'full' NoteMarker field is now of the form :-

       Lvl. (V)^<sourceDetails>

     or, as an example :-

       Acd. (1)^12

     (I say 'full' here because some of the elements are optional.  This is
     spelt out below.)

     One item of terminology before we proceed.  In the normal course of events,
     where a verse has been affected by reversification, we include what I have
     generally referred to as an 'embedded source reference' within the
     canonical text.  Originally this was output in square brackets, so we might
     have something like :-

       2) ^ [1:1] This text is now in verse 2, but came from v 1 of ch 1.

     where 2) is the actual verse indicator displayed to the user, and [1:1]
     indicates that the content originally came from 1:1.

     In most cases, the embedded source reference comprises just the verse
     number of the source reference, and we have dropped the square brackets in
     order to make it look more like a verse number, so that the user is more
     likely to read the text as being numbered in the way that they are
     familiar with.

     In some cases, though -- where the source verse is in a different chapter
     or a different book from the standard verse -- we need to include either
     the chapter or the book and chapter.  And when we have this additional
     context, we presently try to present it in superscript form, so that it
     still leaves the verse part looking like a verse number.

     I always try to format this information in vernacular form, incidentally.


     The various parts of the new NoteMarker field are as follows :-

     * Lvl controls whether or not a footnote should be generated to explain
       what reversification is doing (or might do) here.  A value of 'Nec'
       indicates that the footnote should be generated on _any_ reversification
       run.  A value of 'Acd' indicates that the footnote should be generated
       only on academic reversification runs.

     * <oldData> gives the information which appeared in this field previously
       (ie before the new format data was adopted).  It is being retained just in
       case, and may be dropped at some point.

     * (V) indicates that this reversification action produces data which should
       be marked with a <verse> tag, and gives the verse number.  (The remainder
       of the sid / eid for the verse comes from the book and chapter of the
       StandardRef.)  It is optional; if absent, the data being handled by this
       reversification row is not flagged as a verse.

       I do not need to rely upon this.  I can determine whether or not the
       given text is to be marked with <verse> tags by reference to the
       StandardRef field.  If that identifies a verse, or identifies subverse
       zero of a verse, then we want the verse tag; if it identifies a subverse
       other than subverse zero, we do not want the verse tag.  And the sid /
       eid is simply the StandardRef.

     * The caret indicates that a footnote callout appears.  The only purpose
       it serves is to indicate that the footnote callout (assuming Lvl requires
       a footnote) appears after the start of the verse and before the source
       details which are set out in the remainder of the NoteMarker field.  And
       since it appears on every single row in the reversification data, and
       always in the same position, I do not need to rely upon it.

     * The remainder of the NoteMarker field gives the details upon which the
       embedded source reference is based.  It is optional.  If it is empty, or
       comprises just [-], [+] or [0] (which are old options, and may be dropped
       in future), no embedded source reference is generated.

       Otherwise, I believe I can, in almost all cases, deduce what this portion
       of the data is telling me from the SourceRef and StandardRef.  Note,
       though, that this processing needs to be overridden using the data in the
       NoteMarker field in some places, and if I have to cater for overrides,
       there may be little advantage in retaining the existing processing

       - If the source and standard ref are identical (as on most / all
         KeepVerse rows), the embedded source reference is suppressed.

       - If the source and standard ref are in the same book and chapter, the
         embedded source ref consists of the source verse (or verse and
         subverse) only.

       - If they are in the same book, but different chapters, the embedded
         source ref consists of the source chapter and verse.

       - If they are in different books, the embedded source ref consists of
         the book, chapter and verse (but subject to some special processing
         discussed below).

       - Where the embedded reference consists of more than just the verse
         number, the non-verse portion is to be formatted differently from the
         verse portion (presently by superscripting it).

       - Except that this analysis of both the content and the formatting may be
         overridden in some places TBD.


       I mentioned special processing above.  Where we have blocks of text being
       moved between books (and _only_ there), having a full embedded source
       reference at the start of every verse would probably be oppressive.  In
       these cases, we insert a header at the start of the block of verses
       giving the source book and chapter, and then change the embedded
       references to give the verse number only, relying upon the header to
       provide the context.  Or more accurately, I leave the embedded reference
       in the _first_ verse in a block as book, chapter and verse, but turn all
       of the remaining ones into verse only.

       Further, if the result of this means that the embedded reference
       identifies the same verse number as the verse in which it appears -- if,
       for example, S3Y 1:1 would contain an embedded reference of '1' -- we
       suppress the embedded source reference altogether.
  */

  val C_FootnoteLevelNec = 0
  val C_FootnoteLevelAcd  = 1
  val C_FootnoteLevelOpt = 2

  private fun setCalloutAndFootnoteLevel (dataRow: ReversificationDataRow)
  {
    /**************************************************************************/
    var x = dataRow["NoteMarker"]
    when (x.trim().substring(0, 2).lowercase())
    {
      "ne" -> dataRow.footnoteLevel = C_FootnoteLevelNec // Necessary.
      "ac" -> dataRow.footnoteLevel = C_FootnoteLevelAcd  // Academic.
      "op" -> dataRow.footnoteLevel = C_FootnoteLevelOpt // Not exactly sure what this means, but it is used in a slightly complicated way.
      else -> Logger.error("Reversification invalid note level: " + x[0])
    }

    val ix = x.indexOf(".")
    x = x.substring(ix + 1)



    /**************************************************************************/
    val cd = CalloutDetails()
    dataRow.calloutDetails = cd



    /**************************************************************************/
    var s = x.replace("\\s+".toRegex(), "").replace("^", "") // Don't want spaces or the up-arrow which indicates that the footnote is to be included (it always is).
    if (s.contains("("))
    {
      val xx = s.split("(")[1].split(")")[0]
      s = s.replace("\\(.+?\\)".toRegex(), "")
      if (xx[0].lowercase() == "t")
        cd.standardVerseIsCanonicalTitle = true
      else
        cd.standardVerse = Ref.rdUsx(usxifyFromStepFormat(xx), null, "v")
    }



    /**************************************************************************/
    if (s.contains("["))
    {
      var xx = s.split("[")[1].split("]")[0]
      s = s.replace("\\[.+?]".toRegex(), "")

      if (xx.startsWith("+"))
      {
        cd.alternativeRefCollectionHasPrefixPlusSign = true
        xx = xx.substring(1)
      }

      if (xx.contains("+"))
      {
        cd.alternativeRefCollectionHasEmbeddedPlusSign = true
        xx = xx.replace("+", ",")
      }

      cd.alternativeRefCollection = RefCollection.rdUsx(usxifyFromStepFormat(xx), null, "v")
    }



    /**************************************************************************/
    if (s.isNotEmpty())
      cd.sourceVerseCollection = RefCollection.rdUsx(usxifyFromStepFormat(s), null, "v")
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Footnotes                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  private var m_ReversificationType = '?'
  private lateinit var m_CalloutGenerator: (ReversificationDataRow) -> String
  private lateinit var m_NotesColumnName: String


  /****************************************************************************/
  /**
  * Runs over all verses affected by the reversification data, and applies any
  * footnotes which may be required.
  */

  protected fun addFootnotes (notesColumnName: String, calloutGenerator: (ReversificationDataRow) -> String)
  {
    /**************************************************************************/
    if (getSelectedRows().isEmpty() || !ConfigData.getAsBoolean("stepOkToGenerateFootnotes"))
      return



    /**************************************************************************/
    m_ReversificationType = if (PA_ReversificationHandler_RunTime === instance()) 'R' else 'C'
    m_NotesColumnName = notesColumnName
    m_CalloutGenerator = calloutGenerator


    /**************************************************************************/
    val selectedRowsGroupsByBook = getSelectedRows().groupBy { it.sourceRef.getB() }
    Dbg.withProcessingBooks("Applying reversification footnotes ...") {
      selectedRowsGroupsByBook.forEach { bookNoAndRows ->
        val bookNode = m_DataCollection.getRootNode(bookNoAndRows.key)!!
        Dbg.withProcessingBook(m_FileProtocol.getBookAbbreviation(bookNode)) {
          addFootnotes(bookNode, bookNoAndRows.value)
        }
      }
    }
  }


  /****************************************************************************/
  /* Deals with footnotes on a single book. */

  private fun addFootnotes (rootNode: Node, reversificationRows: List<ReversificationDataRow>)
  {
    val reversificationNoteType = ConfigData["stepReversificationNoteType"]?.first()?.uppercaseChar() ?: 'B' // A(cademic) or B(asic).

    val sids = rootNode.findNodesByAttributeName(m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid()).associateBy {
      m_FileProtocol.readRef(m_FileProtocol.getSid(it)).toRefKey()
    } // Maps RefKey to verse node for all sids in the book.

    reversificationRows.forEach { // Create footnote nodes if appropriate, and insert them after the sid.
      val footnoteNodes = makeFootnote(rootNode.ownerDocument, it, reversificationNoteType) ?: return@forEach
      val sid = sids[it.sourceRefAsRefKey] ?: throw StepExceptionWithStackTraceAbandonRun("Attempting to apply reversification footnote to a non-existent sid: ${it.sourceRef}.")
      footnoteNodes.forEach { footnoteNode -> Dom.insertNodeAfter(sid, footnoteNode) }
    }
  }


  /****************************************************************************/
  /**
   * Both flavours of reversification may add footnotes under some
   * circumstances.  I leave the control of this to the flavours themselves, but
   * the actual content etc is constructed in a common manner and is handled
   * here.
   *
   * @param ownerDocument
   * @param row Reversification row being handled.
   * @param reversificationNoteTypeForRun B(asic) or A(cademic).
   * @return List of nodes which between them constitute the note.
   */

  private fun makeFootnote (ownerDocument: Document, row: ReversificationDataRow, reversificationNoteTypeForRun: Char): List<Node>?
  {
    return when (reversificationNoteTypeForRun)
    {
      'B' -> if (C_FootnoteLevelNec == row.footnoteLevel) makeFootnote1(ownerDocument, row, 'B') else null
      'A' -> makeFootnote1(ownerDocument, row, 'A')
      else -> throw StepExceptionWithStackTraceAbandonRun("getFootnote: Invalid parameter.")
    }
  }


  /****************************************************************************/
  /* Creates the footnote construct.  We only get this far if we are sure we
     want a footnote.  At this point, the only reason for _not_ generating one
     is if the reversification data does not actually contain any footnote
     text. */

  private fun makeFootnote1 (ownerDocument: Document, dataRow: ReversificationDataRow, basicOrAcademic: Char): List<Node>?
  {
    /**************************************************************************/
    var content = getFootnoteContent(dataRow, m_NotesColumnName, basicOrAcademic)
    if (content.isEmpty())
      return null


    /**************************************************************************/
    val calloutDetails = dataRow.calloutDetails
    val res: MutableList<Node> = mutableListOf()



    /**************************************************************************/
    /* This is the 'pukka' callout -- ie the piece of text which you click on in
       order to reveal the footnote.  Originally it was expected to be taken
       from the NoteMarker text in the reversification data.  However, we then
       found that STEP always rendered it as a down-arrow; and latterly DIB has
       decided he wants it that way even if STEP is fixed to display the actual
       callout text we request.  I therefore need to generate a down-arrow here,
       which is what the callout generator gives me. */

    val callout = m_CalloutGenerator(dataRow)



    /**************************************************************************/
    /* Insert the footnote itself. */

    content = content.replace("S3y", "S3Y") // DIB prefers this.

    val ancientVersions = if (m_ReversificationNotesLevelForRun > C_FootnoteLevelNec) dataRow.ancientVersions else null
    if (null != ancientVersions) content += " $ancientVersions"

    val noteNode = m_FileProtocol.makeFootnoteNode(Permissions.FootnoteAction.AddFootnoteToGeneralVerseAffectedByReversification, ownerDocument, dataRow.standardRefAsRefKey, content, callout)
    if (null != noteNode)
    {
      res.add(noteNode)
      res.add(Dom.createTextNode(ownerDocument, " "))
      IssueAndInformationRecorder.addGeneratedFootnote(Ref.rd(dataRow.sourceRefAsRefKey).toString() + " (ReversificationFootnote)")
    }



    /**************************************************************************/
    /* Check if we need the text which will typically be superscripted and
       bracketed. */

    val alternativeRefCollection = calloutDetails.alternativeRefCollection
    if (null != alternativeRefCollection)
    {
      val basicContent = if (calloutDetails.alternativeRefCollectionHasEmbeddedPlusSign)
        alternativeRefCollection.getLowAsRef().toString("a") + TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferenceEmbeddedPlusSign") + alternativeRefCollection.getHighAsRef().toString("a")
      else if (calloutDetails.alternativeRefCollectionHasPrefixPlusSign)
        TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferencePrefixPlusSign") + alternativeRefCollection.toString("a")
      else
        alternativeRefCollection.toString("a")

      val textNode = Dom.createTextNode(ownerDocument, TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferenceFormat", basicContent))
      val containerNode = Dom.createNode(ownerDocument, "<_X_reversificationCalloutAlternativeRefCollection/>")
      containerNode.appendChild(textNode)
      res.add(containerNode)
    }



    /**************************************************************************/
    return res.reversed()
  }


  /****************************************************************************/
  /* Returns the fully formatted content for a footnote.  whichFootnote should
     be either ReversificationNote or VersificationNote (in fact, only the
     latter at present, because we have no reason to deal with the former. */

  private fun getFootnoteContent (dataRow: ReversificationDataRow, whichFootnote: String, basicOrAcademic: Char): String
  {
    /**************************************************************************/
    //Dbg.d(row.toString())



    /**************************************************************************/
    /* Is there in fact any footnote at all? */

    var content = dataRow[whichFootnote]
    if (content.isEmpty()) return ""



    /**************************************************************************/
    var res = ""



    /**************************************************************************/
    /* Time was when we would only ever have a single chunk of text, and either
       zero or one associated reference, which appeared after the text.  This
       is not longer the case.  We may have one or two chunks of text; and
       there may or may not be a reference associated with either or both of
       them, and references may occur either at the front or the end. */

    if (content.endsWith(".%.")) content = content.substring(0, content.length - 3) + "%."
    val textParts = content.split("%")
    val refs: MutableList<String> = mutableListOf()
    val texts: MutableList<String> = mutableListOf()
    for (i in textParts.indices step 2)
    {
      refs.add(textParts[i].trim())
      texts.add(if (i + 1 < textParts.size) textParts[i + 1] else "")
    }

    for (i in texts.indices)
      if (texts[i].isNotEmpty())
      {
        val thisChunk: String
        val x = TranslatableFixedText.lookupText(Language.Vernacular, getTextKey(texts[i]))

        thisChunk = if (x.startsWith("%ref"))
          refs[i].trim() + " %" + texts[i] + "%"
        else if (x.contains("%ref"))
          "%" + texts[i] + "% " + refs[i + 1]
        else
          "%" + texts[i] + "%"

        res += " " + getFootnoteContent(thisChunk)
      }



    /**************************************************************************/
    /* If this is an academic run, we may need to add AncientVersion
       information. */

    if ('A' == basicOrAcademic)
      res += " " + dataRow.ancientVersions



    /**************************************************************************/
    return res.trim()
  }


  /*****************************************************************************/
  /* Support method for the previous method.  This works the way it does (taking
     a possible concatenation of a text string and a reference) because I've
     hived it off from an earlier method which was created when footnotes only
     ever consisted of one string to look up. */

  private fun getFootnoteContent (contentAsSupplied: String): String
  {
    /**************************************************************************/
    /* A change in December 2023 introduced some NoteB footnotes which have
       a reference at the front, and then '%in some Bibles%' or '%in most Bibles%'.
       It is convenient to move the reference to the end, to convert these
       entries to the same form as the footnotes which we have processed
       previously. */

    var content = contentAsSupplied
    val contentLowercase = content.lowercase()
    if ("%in some bibles%" in contentLowercase || "%in most bibles%" in contentLowercase)
    {
      val ix = content.indexOf(' ')
      val ref = content.substring(0, ix).trim()
      val text = content.substring(ix + 1).trim()
      content = "$text $ref"
    }



    /**************************************************************************/
    /* All footnote entries have a leading %, and sometimes (but not
       consistently) they have a trailing full stop.  We don't want either of
       these. */

    content = content.substring(1) // Get rid of leading %.
    if ('.' == content.last()) content = content.substring(0, content.length - 1)



    /**************************************************************************/
    /* What we are now left with is the message text, with its end marked with
       %, and then optionally a reference.  We split at the %-sign, having
       first appended \u0001 (which subsequently we get rid of again) to ensure
       that split always returns two elements. */

    content += "\u0001" // Ensure split always gives two elements.
    val bits = content.split("%")
    val key = getTextKey(bits[0])



    /**************************************************************************/
    /* If the footnote has no associated reference, we can simply return the
       text itself. */

    if (1 == bits[1].length)
      return TranslatableFixedText.stringFormat(Language.Vernacular, key)



    /**************************************************************************/
    /* In most cases, sorting out the reference collection is easy -- there may
       in theory be some ambiguity with single numbers as to what they represent
       (chapters, verses, etc), but we force that here by saying that unadorned
       numbers should be regarded as verses (which, in fact, I think unadorned
       numbers actually are); and in any case, the aim is simply to output
       stuff in the same form as it appears in the reversification data.

       The fly in the ointment are the few rows which contain multiple
       references which are separated by things like '/' and '+', and therefore
       can't be parsed as collections.  We'll deal with the easy cases first
       (the ones where we don't have these odd separators. */

    var refAsString = bits[1].replace("\u0001", "").trim()
    val containsSlash = '/' in refAsString
    val containsPlus = '+' in refAsString
    if (!containsSlash && !containsPlus)
    {
      if (refAsString.endsWith("."))
        refAsString = refAsString.substring(0, refAsString.length - 1)
      val rc = RefCollection.rdUsx(usxifyFromStepFormat(refAsString), dflt = null, resolveAmbiguitiesAs = "v")
      return TranslatableFixedText.stringFormat(Language.Vernacular, key, rc)
    }



    /**************************************************************************/
    /* Which just leaves the difficult case.  Unfortunately, there is at the
       time of writing just one row where the reference looks like 9:9a/2:35f,
       and of course the slash is a problem, because this cannot be parsed as a
       reference collection.  If I'm to have any chance of doing this in such
       a way that I can continue to support vernacular translation, this is
       going to be unpleasantly fiddly ...

       I start off by obtaining the basic text of the message in vernacular
       form.  This should have precisely one entry of the form %RefV<...>.
       I split this text up into that portion which appears the %RefV<...> bit,
       the %RefV<...> bit itself, and the portion which appears afterwards.

       I then use the %RefV<...> portion to format each of the references
       individually.  And then finally I join these formatted references
       together with the relevant separator, and stitch this together with the
       fixed portions of the text.
    */

    val rawMessage = TranslatableFixedText.lookupText(Language.English, getTextKey(bits[0].trim()))
    val regex = "(?i)(?<pre>.*)(?<ref>%Ref.*?>)(?<post>.*)".toRegex()
    val match = regex.matchEntire(rawMessage)
    val refFormat = match!!.groups["ref"]!!.value

    val elts = refAsString.split('/', '+').map { TranslatableFixedText.stringFormat(refFormat, RefCollection.rdUsx(it.trim(), dflt = null, resolveAmbiguitiesAs = "v")) }
    val eltsAssembled = elts.joinToString(TranslatableFixedText.stringFormat(Language.Vernacular, if (containsSlash) "V_reversification_ancientVersionsAlternativeRefsSeparator" else "V_reversification_alternativeReferenceEmbeddedPlusSign"))
    return match.groups["pre"]!!.value + eltsAssembled + match.groups["post"]!!.value
  }


  /****************************************************************************/
  /**
   *  Given a piece of footnote text from the reversification data, gives back
   *  the corresponding key which we can use to look up TranslatableFixedText.
   */

  private fun getTextKey (lookupVal: String) = "V_reversification_[${lookupVal.trim()}]"


  /****************************************************************************/
  private fun setReversificationNotesLevelForRun ()
  {
    when (ConfigData["stepReversificationFootnoteLevel"]!!.lowercase())
    {
      "basic" ->    m_ReversificationNotesLevelForRun = C_FootnoteLevelNec
      "academic" -> m_ReversificationNotesLevelForRun = C_FootnoteLevelAcd
    }
  }


  /****************************************************************************/
  private var m_ReversificationNotesLevelForRun = -1 //




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                      Private -- Ancient versions                       **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* You get one of these per tradition in the AncientVersions information. */

  internal class AncientVersionsTradition (theContent: String)
  {
    /**************************************************************************/
    override fun toString (): String
    {
      /************************************************************************/
      /* This first block deals with the main elements associated with a given
         tradition.  For example, the bit after the equals sign in

           Greek=12:18 / 12:17b+13:1a

         A slash is used quite commonly here to separate alternatives.  The
         above example is atypical, in that it also uses a plus-sign as a
         delimiter.

         The processing below is revoltingly messy, and even then is perhaps
         not as flexible as other aspects of the vernacular-related formatting,
         in that it assumes that a multi-element collection will always be
         shown in the same order as in the reversification data, and that it
         will be separated by delimiters in the same way as in the
         reversification data. */

      fun processMainElementRefCollection (refAsString: String): String
      {
        return if ("--" == refAsString)
          TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsNoReference")
        else
          TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsMainRefFormat", RefCollection.rd(usxifyFromStepFormat(refAsString), null,"v"))
      }

      fun processMainElementDelimiter (delim: String): String
      {
        return when (delim)
        {
          "/" -> TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsAlternativeRefsSeparator")
          "+" -> TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsJointRefsSeparator")
          else -> throw StepExceptionBase("AncientVersions delimiter not handled: $delim")
        }
      }

      val delimitedMainElementsElements = m_MainElements.split("((?=^)|(?<=^))".replace("^", "[/+]").toRegex()).toMutableList() // Split with the delimiters shown in square brackets, and retain the delimiters.
      delimitedMainElementsElements.forEachIndexed { ix, content -> if ((ix and 1) == 0) delimitedMainElementsElements[ix] = processMainElementRefCollection(content) }
      delimitedMainElementsElements.forEachIndexed { ix, content -> if ((ix and 1) == 1) delimitedMainElementsElements[ix] = processMainElementDelimiter(content) }
      val mainEltsAsString = delimitedMainElementsElements.joinToString("")



      /************************************************************************/
      /* This section caters for anything within square brackets. */

      var equivalenceInformation = ""
      if (null != m_EquivalenceInformationReferenceCollection)
        equivalenceInformation = " " + TranslatableFixedText.stringFormat(m_EquivalenceInformationFormatString, m_EquivalenceInformationReferenceCollection!!)



      /************************************************************************/
      val tradition: String = TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_language$m_Tradition")
      return TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsTraditionFormat", "tradition", tradition, "main", mainEltsAsString, "equivalenceInformation", equivalenceInformation)
    }


    /**************************************************************************/
    private var m_EquivalenceInformationReferenceCollection: RefCollection? = null
    private var m_EquivalenceInformationFormatString = ""
    private var m_MainElements = ""
    private var m_Tradition = ""


    /**************************************************************************/
    init
    {
       /************************************************************************/
       //Dbg.dCont(theContent, "/")



      /************************************************************************/
      /* Get rid of spaces, which only serve to complicate things and aren't
         of use to the processing. */

      var content = theContent.replace(" ", "")



      /************************************************************************/
      /* Split off the tradition. */

       var ix = content.indexOf("=")
       m_Tradition = content.substring(0, ix)
       content = content.substring(ix + 1)



      /************************************************************************/
      /* Remove any trailing bullet point.  Bullet points act as separators
         between traditions.  Ideally I'd have split traditions out by splitting
         at these separators, but unfortunately it looks as though in some cases
         a bullet point has been used to mark subverse zero.  (It _shouldn't_
         have been -- DIB has been using an Arabic Arabic numeral zero to mark
         subverse zero.  However, the two characters look almost identical, so
         it's easy to use the wrong one.) */

      if (content.last() == '\u2022') // Bullet point.
        content = content.substring(0, content.length - 1)



      /************************************************************************/
      /* Split off any trailing equivalence information (ie the bit in square
         brackets). */

      var equivalenceInformation: String? = null
      ix = content.indexOf("[")
      if (ix >= 0)
      {
        equivalenceInformation = content.substring(ix + 1).replace("]", "")
        content = content.substring(0, ix)
      }



      /************************************************************************/
      m_MainElements = content



      /************************************************************************/
      /* If we have equivalence information, turn it into an appropriate
         collection.  Life is made a bit easier here by the fact that we don't
         need to worry about slashes and double-dashes in the equivalence
         information, because there aren't any -- they're all pukka
         references. */

      if (null != equivalenceInformation)
      {
        when (equivalenceInformation!!.first())
        {
          '+' ->
           {
              m_EquivalenceInformationFormatString = TranslatableFixedText.lookupText(Language.Vernacular, "V_reversification_ancientVersionsEquivalencePlus")
              equivalenceInformation = equivalenceInformation!!.substring(1)
           }

           '=' ->
            {
              m_EquivalenceInformationFormatString = TranslatableFixedText.lookupText(Language.Vernacular, "V_reversification_ancientVersionsEquivalenceEquals")
              equivalenceInformation = equivalenceInformation!!.substring(1)
            }

            else ->
            {
              m_EquivalenceInformationFormatString = TranslatableFixedText.lookupText(Language.Vernacular, "V_reversification_ancientVersionsEquivalenceUndecorated")
              equivalenceInformation = equivalenceInformation!!.substring(1)
           }
        }

        m_EquivalenceInformationReferenceCollection = RefCollection.rdUsx(usxifyFromStepFormat(equivalenceInformation!!))
      }
    }
  }


  /****************************************************************************/
  /**
   * Converts the ancient version information into a vernacular string
   * containing vernacular references as far as this is possible.
   *
   * @return Ancient version information.
   */

   private fun ancientVersionsToString (traditions: List<AncientVersionsTradition>): String
   {
    val resElements: MutableList<String> = ArrayList()
    traditions.forEach { x -> resElements.add(x.toString()) }
    if (resElements.isEmpty()) return ""

    var res = resElements.joinToString(TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsTraditionSeparator"))
    res = TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersions", res)
    return res
  }


  /****************************************************************************/
  /* Converts the raw AncientVersion information into string format.

     Ancient version information looks like ...

       ( Hebrew=1:1 | Greek=3:16 | ... )   ['|' = bullet point]

     The references are complicated ...

     * They may be a simple reference, a reference range, or a collection.
       All of these are in STEP format with alphabetic subverses.

     * Or they may be a list of alternatives, separated by ' / ', each
       element itself being a reference, a range or a collection.

     * Or they may consist of --, indicating that there _is_ no reference.

     * And they may contain additional information in square brackets --
       information which may itself start with an equals sign or a plus sign
       (or with neither), the significance which I am currently unaware of.

     * The processing here splits these various bits out into
       AncientVersionsChunks, one per tradition.
   */

  private fun setAncientVersions (theText: String?): String
  {
    /**************************************************************************/
    //Dbg.dCont(theText ?: "", "(Greek=1:14 / 1:14a)")



    /**************************************************************************/
    if (theText.isNullOrEmpty()) return ""



    /**************************************************************************/
    val traditions: MutableList<AncientVersionsTradition> = ArrayList()



    /**************************************************************************/
    var text = theText.substring(0, theText.length - 1).substring(1) // Get rid of the enclosing parens.



    /**************************************************************************/
    /* Some entries have things with equals signs in square brackets to
       indicate that the reference list is in some manner similar to another
       alternative.  This would get in the way of the processing below, so
       temporarily I replace the equals sign. */

    text = text.replace("[=", "[\u0001")



    /**************************************************************************/
    val languageTraditions = listOf(*text.split("(?=\\b(Hebrew=|Latin=|Greek=))".toRegex()).toTypedArray())
    languageTraditions.subList(1, languageTraditions.size).forEach { traditions.add(AncientVersionsTradition(it)) }
    return ancientVersionsToString(traditions)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                        Private -- debug support                        **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* stepDbgReversificationAnticipatedSourceType, if defined at all, should
     have one of the values English, Hebrew, Latin or Greek.

     The processing then expects rows whose SourceType contains the relevant
     string (case-insensitive) to be selected for processing, while others
     are not, and will output messages to that effect.  This isn't _quite_
     right (some rows are for specific variants of these like Latin2), but
     it will at least give us something at least vaguely useful for
     checking.
  */

  private val m_AnticipatedSourceType : String? by lazy { ConfigData["stepDbgReversificationAnticipatedSourceType"]?.lowercase() }


  /****************************************************************************/
  private class DebugData
  {
    var m_RowsAccepted: MutableList<ReversificationDataRow> = ArrayList() // All of the rows accepted.
    var m_RowsAcceptedButShouldNotHaveBeen: MutableList<ReversificationDataRow> = ArrayList()
    var m_RowsRejectedButShouldNotHaveBeen: MutableList<ReversificationDataRow> = ArrayList()
  }

  private val  m_DebugData = DebugData()


  /****************************************************************************/
  /* Hasn't been used recently.  At one stage I thought it useful to check how
     the selected rows stacked up against the rows we might have expected for
     a text which we knew a priori should probably follow, say, the Latin
     versification scheme.

     This runs only if we have recorded our anticipated source time; and given
     that it hasn't run for a long time, it may be that it no longer works, for
     all I know. */

  private fun debugOutputDebugData ()
  {
    if (null == m_AnticipatedSourceType) return
    m_DebugData.m_RowsAcceptedButShouldNotHaveBeen.forEach { x -> Logger.info("Reversification row accepted but perhaps should not have been: $x") }
    m_DebugData.m_RowsRejectedButShouldNotHaveBeen.forEach { x -> Logger.info("Reversification row rejected but perhaps should not have been: $x") }
    m_DebugData.m_RowsAccepted.forEach { x -> Logger.info("Reversification row accepted as anticipated: $x") }
  }


  /****************************************************************************/
  /* For debugging -- records details of which rows were accepted and
     rejected. */

  private fun debugRecordReversificationRowAccepted (dataRow: ReversificationDataRow)
  {
    /**************************************************************************/
    /* This flavour of debugging is relevant only where we are anticipating
       the text will conform reasonably closely to one of the common
       versification schemes like Latin, and have indicated as much. */

    if (null == m_AnticipatedSourceType) return



    /**************************************************************************/
    /* Ignore lines where the source ref relates to a book we don't have. */

   if (!m_DataCollection.getBibleStructure().bookExists(dataRow.sourceRef.getB())) return



   /**************************************************************************/
    val sourceType = dataRow["SourceType"].lowercase()
    val containsAnticipatedSource = sourceType.contains(m_AnticipatedSourceType!!)
    m_DebugData.m_RowsAccepted.add(dataRow)
    if (!containsAnticipatedSource)
      m_DebugData.m_RowsAcceptedButShouldNotHaveBeen.add(dataRow)
  }


  /****************************************************************************/
  /* For debugging -- records details of which rows were accepted and
     rejected. */

  private fun debugRecordReversificationRowRejected (dataRow: ReversificationDataRow)
  {
    /**************************************************************************/
    /* This flavour of debugging is relevant only where we are anticipating
       the text will conform reasonably closely to one of the common
       versification schemes like Latin, and have indicated as much. */

    if (null == m_AnticipatedSourceType) return



    /**************************************************************************/
    /* Ignore lines where the source ref relates to a book we don't have. */

   if (!m_DataCollection.getBibleStructure().bookExists(dataRow.sourceRef.getB())) return



   /**************************************************************************/
    val sourceType = dataRow["SourceType"].lowercase()
    val containsAnticipatedSource = sourceType.contains(m_AnticipatedSourceType!!)
    if (containsAnticipatedSource && Dbg.wantToProcessBook(dataRow.sourceRef.getB()))
      m_DebugData.m_RowsRejectedButShouldNotHaveBeen.add(dataRow)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private lateinit var m_RuleEvaluator: ReversificationRuleEvaluator
  private val m_SelectedRows: MutableList<ReversificationDataRow> = ArrayList(10000)


  /****************************************************************************/
  /* Some actions are deprecated and need to be renamed during data load.

     Some actions implicitly require a check for the existence of the source
     ref.

     Some actions require we check that the standard ref does not exist.

     Some actions require that the standard ref either does not exist, or else
     is empty.

     The information here is used in canonicaliseAndCorrectData to make
     suitable modifications to the incoming data. */

  private val C_ActionsWhichShouldBeRenamed = mapOf(Action.IfAbsent to Action.MissingVerse, Action.IfEmpty to Action.EmptyVerse)



  /****************************************************************************/
  /* Action is an enum with one entry for each possible action, deprecated or
     not.

     Each action may have associated with it one or more of a few standard
     actions (like reporting that the source has been remapped to a new
     location).  This is not to preclude additional processing being associated
     with the action -- it just covers the processing which a number of
     different actions have in common.

     There is no need to worry about action information for the two deprecated
     entries, because these are replaced with other items during data load. */

  enum class Action (val checks: Int, var actions: Int = 0, var isMove: Boolean = false) {
    EmptyVerse         (checks = CheckStandardRefEmptyOrNonExistent),
    KeepVerse          (checks = NoCheck),
    MergedVerse        (checks = CheckSourceRefExists),
    MissingVerse       (checks = CheckStandardRefNonExistent),
    PsalmTitle         (checks = CheckSourceRefExists),
    RenumberTitle      (checks = CheckSourceRefExists),
    RenumberVerse      (checks = CheckSourceRefExists), // With the processing as it stands, I don't need to distinguish Move and Renumber.

    IfAbsent           (checks = NoCheck), // These are really only dummies -- they're deprecated and are replaced during data load.
    IfEmpty            (checks = NoCheck)
  }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                          Significant classes                           **/
  /**                             CalloutDetails                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Data parsed out of the rather complicated NoteMarker field of the
     reversification data. */

  class CalloutDetails
  {
    var standardVerse: Ref? = null
    var standardVerseIsCanonicalTitle = false
    var alternativeRefCollection: RefCollection? = null
    var alternativeRefCollectionHasPrefixPlusSign: Boolean = false
    var alternativeRefCollectionHasEmbeddedPlusSign: Boolean = false
    var sourceVerseCollection: RefCollection? = null
  }
} // PA_ReversificationHandler


/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                           Significant classes                            **/
/**                          ReversificationDataRow                          **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/


/******************************************************************************/
/* A single row of the reversification data. */

class ReversificationDataRow (rowNo: Int)
{
  enum class AttachFootnoteTo { Source, Standard, Unknown }
  lateinit var action: PA_ReversificationHandler.Action
           var ancientVersions = ""
           var attachFootnoteTo = AttachFootnoteTo.Unknown
  lateinit var fields: MutableList<String>
  lateinit var calloutDetails: CalloutDetails
           var footnoteLevel = -1
           var isMove = false
           val rowNumber = rowNo // Publicly accessible only for debugging.
           var sourceIsPsalmTitle = false
  lateinit var sourceRef: Ref
           var sourceRefAsRefKey = 0L
  lateinit var standardRef: Ref
           var standardRefAsRefKey = 0L

  companion object {
    private val m_Headers: MutableMap<String, Int>  = TreeMap(String.CASE_INSENSITIVE_ORDER) // Headers of reversification data file.
    fun headersInitialised () = m_Headers.isNotEmpty()
    fun setHeader (name: String, ix: Int) { m_Headers[name] = ix }
  }



  /****************************************************************************/
  fun getField (key: String): String { val ix = m_Headers[key]!!; return if (ix < fields.size) fields[ix] else "" }
  fun setField (key: String, value: String) { val ix = m_Headers[key]!!; fields[ix] = value }


  /****************************************************************************/
  override fun toString (): String
  {
    return "Row: " + rowNumber.toString() + " " +
      getField("SourceType") + " " +
      getField("SourceRef") + " " +
      getField("StandardRef") + " " +
      getField("Action") + " " +
      getField("NoteMarker")
  }
} // class ReversificationDataRow

operator fun ReversificationDataRow.get (fieldName: String): String { return this.getField(fieldName) }
operator fun ReversificationDataRow.set (fieldName: String, value: String) { this.setField(fieldName, value) }













/******************************************************************************/
/* Code from the most recent previous version.   The first portion -- headed
   'Data aggregation' -- contains code needed to pick up Moves and to
   accumulate them into collections such that the collection can be moved en
   masse, rather than moving verses one at a time.  Something along these lines
   will be needed if we ever decide to reinstate conversion-time
   restructuring. */

//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                           Data aggregation                             **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  private val m_SelectedRows: MutableList<ReversificationDataRow> = ArrayList(10000)
//
//  private val m_MoveGroups: MutableList<ReversificationMoveGroup> = ArrayList()
//
//  private lateinit var m_AllBooks: List<String>
//  private lateinit var m_SourceBooks: List<String>
//  private lateinit var m_StandardBooks: List<String>
//
//  private lateinit var m_AllBooksInvolvedInMoveActions: List<String>
//  private lateinit var m_SourceBooksInvolvedInMoveActions: List<String>
//  private lateinit var m_StandardBooksInvolvedInMoveActions: List<String>
//
//
//  /****************************************************************************/
//  /* Carries out the various forms of data aggregation required by callers. */
//
//  private fun aggregateData ()
//  {
//    extractIfAbsentAndIfEmptyRows(AnticipatedIfAbsentDetails.getMap(), "ifabsent")
//    extractIfAbsentAndIfEmptyRows(AnticipatedIfEmptyDetails.getMap(), "ifempty")
//
//    aggregateBooks()
//    aggregateBooksInvolvedInMoveActions()
//
//    if (PROBABLY_NOT_WORKING_PA_ConversionTimeReversification().isRunnable())
//    {
//      aggregateMoveGroups()
//      markSpecialistMoves()
//    }
//
//    recordBookMappings()
//  }
//
//
//  /****************************************************************************/
//  /* Generates lists containing:
//
//     a) All books mentioned in the selected reversification rows.
//     b) All source books.
//     c) All standard books.
//  */
//
//  private fun aggregateBooks ()
//  {
//    val allBooks:     MutableSet<Int> = HashSet()
//    val bookMappings: MutableSet<String> = HashSet()
//    val sourceBooks:  MutableSet<Int> = HashSet()
//    val targetBooks:  MutableSet<Int> = HashSet()
//
//    m_SelectedRows.forEach {
//      allBooks.add(it.sourceRef.getB())
//      allBooks.add(it.standardRef.getB())
//      sourceBooks.add(it.sourceRef.getB())
//      targetBooks.add(it.standardRef.getB())
//      bookMappings.add(BibleBookNamesUsx.numberToAbbreviatedName(it.sourceRef.getB()) + "." + BibleBookNamesUsx.numberToAbbreviatedName(it.standardRef.getB()))
//    }
//
//    m_AllBooks      = allBooks   .sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
//    m_SourceBooks   = sourceBooks.sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
//    m_StandardBooks = targetBooks.sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
//  }
//
//
//  /****************************************************************************/
//  /* Generates lists containing:
//
//     a) All books involved in Move actions.
//     b) All source books.
//     c) All standard books.
//  */
//
//  private fun aggregateBooksInvolvedInMoveActions ()
//  {
//    val allBooks:    MutableSet<Int> = HashSet()
//    val sourceBooks: MutableSet<Int> = HashSet()
//    val targetBooks: MutableSet<Int> = HashSet()
//
//    m_SelectedRows.filter { 0 != it.processingFlags.and(C_Move) }
//      .forEach { allBooks.add(it.sourceRef.getB()); allBooks.add(it.standardRef.getB()); sourceBooks.add(it.sourceRef.getB()); targetBooks.add(it.standardRef.getB()) }
//
//    m_AllBooksInvolvedInMoveActions      = allBooks   .sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
//    m_SourceBooksInvolvedInMoveActions   = sourceBooks.sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
//    m_StandardBooksInvolvedInMoveActions = targetBooks.sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
//  }
//
//
//  /****************************************************************************/
//  /* Records footnote details for IfAbsent and IfEmpty rows and then removes
//     these rows from the ones to be processed.  Note that it doesn't matter
//     whether I choose sourceRef or standardRef for key purposes, because they
//     are the same.  And nor does it matter whether I choose the reversification
//     footnote or the versification one, because again they are the same. */
//
//  private fun extractIfAbsentAndIfEmptyRows (map: MutableMap<RefKey, String>, action: String)
//  {
//    m_SelectedRows.filter { action == it.action }. forEach { map[it.sourceRefAsRefKey] = getFootnoteVersification(it) }
//    m_SelectedRows.removeIf { action == it.action }
//  }
//
//
//  /****************************************************************************/
//  /* Generates a set containing source/standard pairs detailing which source
//     books map to which standard books. */
//
//  private fun recordBookMappings ()
//  {
//    fun addMapping (row: ReversificationDataRow)
//    {
//      val sourceBook   = BibleBookNamesUsx.numberToAbbreviatedName(row.sourceRef  .getB())
//      val standardBook = BibleBookNamesUsx.numberToAbbreviatedName(row.standardRef.getB())
//      m_BookMappings.add("$sourceBook.$standardBook")
//    }
//
//    m_SelectedRows.forEach { addMapping(it) }
//  }
//
//
//  private lateinit var m_AllBooks: List<String>
//  private lateinit var m_SourceBooks: List<String>
//  private lateinit var m_StandardBooks: List<String>//
//
//
//
//
//
//
//    dataRow.processingFlags = dataRow.processingFlags.or(
//      when (dataRow.action)
//      {
//        "emptyverse"     -> C_CreateIfNecessary
//        "ifabsent"       -> C_CreateIfNecessary
//        "ifempty"        -> 0
//        "keepverse"      -> getAllBiblesComplaintFlag(dataRow)
//        "mergedverse"    -> C_CreateIfNecessary
//        "missingverse"   -> C_CreateIfNecessary
//        "psalmtitle"     -> C_ComplainIfStandardRefDidNotExist
//        "renumbertitle"  -> C_ComplainIfStandardRefExisted.or(C_StandardIsPsalmTitle).or(if ("title" in getField("SourceRef", dataRow).lowercase()) C_SourceIsPsalmTitle else 0).or(if ("title" in getField("StandardRef", dataRow).lowercase()) C_StandardIsPsalmTitle else 0)
//        "renumberverse"  -> C_ComplainIfStandardRefExisted.or(C_Renumber)
//        "renumberverse*" -> C_ComplainIfStandardRefExisted.or(C_Renumber).or(C_Move)
//        else             -> 0
//    })


//  /****************************************************************************/
//  /* This is revolting; I can only assume that the need for it became apparent
//     late in the day, when it would have been too difficult to rejig the
//     reversification data to handle it properly.
//
//     Lots of rows are marked 'AllBibles' in the SourceType field, and most of
//     these are marked KeepVerse.  Normally KeepVerse retains an existing verse
//     and complains if the verse does not already exist.
//
//     However, on AllBibles rows, KeepVerse is allowed to create verses if they
//     don't already exist -- except that in a further twist, it has to issue a
//     warning in some cases (but not all) if it has to create the verse.
//
//     And just to make life thoroughly awful, the way I am required to
//     distinguish these cases has to be based upon the contents of the FootnoteA
//     column -- certain things there imply that a warning is needed, while others
//     do not.  (The problem being here that this field is free-form text, so
//     sooner or later it is going to change, sure as eggs is eggs, and I shan't
//     realise that's an issue.) */
//
//  private val m_NoteAOptionsText = listOf("At the end of this verse some manuscripts add information such as where this letter was written",
//                                          "In some Bibles this verse is followed by the contents of Man.1 (Prayer of Manasseh)",
//                                          "In some Bibles this verse starts on a different word")
//
//  private fun getAllBiblesComplaintFlag (row: ReversificationDataRow): Int
//  {
//    val noteA = getField("Reversification Note", row)
//    return if (m_NoteAOptionsText.contains(noteA)) C_ComplainIfStandardRefDidNotExist else 0
//  }





