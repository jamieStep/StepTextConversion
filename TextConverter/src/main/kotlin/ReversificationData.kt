/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.*
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.convertNumberToRepeatingString
import org.stepbible.textconverter.support.miscellaneous.Translations
import org.stepbible.textconverter.support.ref.*
import org.stepbible.textconverter.support.shared.Language
import org.stepbible.textconverter.support.stepexception.StepException
import java.io.File
import java.net.URL
import java.util.*

/******************************************************************************/
/**
 * Reads and stores reversification data, provides summary information about
 * it, and groups Move statements into blocks all of whose elements are
 * adjacent, and which can therefore be processed en masse.
 *
 * 22-Jun-23: The reversification data has been extended to contain some rows
 * with a SourceType of IfEmpty.  Strictly these are not actually
 * reversification-related -- they are used regardless of whether
 * reversification is applied, with a view to adding annotation to empty
 * verses.  In point of fact, 'empty' verses created by reversification always
 * have a footnote applied to them, and therefore are not affected by these
 * new rows.
 *
 * @author ARA "Jamie" Jamieson
**/

/******************************************************************************/
class CalloutDetails
{
  var standardVerse: Ref? = null
  var standardVerseIsCanonicalTitle = false
  var alternativeRefCollection: RefCollection? = null
  var alternativeRefCollectionHasPrefixPlusSign: Boolean = false
  var alternativeRefCollectionHasEmbeddedPlusSign: Boolean = false
  var sourceVerseCollection: RefCollection? = null
}


/******************************************************************************/
class ReversificationDataRow (rowNo: Int)
{
  lateinit var action: String
           var ancientVersions = ""
  lateinit var fields: List<String>
  lateinit var calloutDetails: CalloutDetails
           var footnoteLevel = -1
           var isInNoTestsSection = false
           var processingFlags = 0
           val rowNumber: Int
  lateinit var sourceRef: Ref
           var sourceRefAsRefKey = 0L
  lateinit var sourceType: String
  lateinit var standardRef: Ref
           var standardRefAsRefKey = 0L

           var originalAction: String? = null
           var originalSourceRef: String? = null
           var originalStandardRef: String? = null

  fun retainOriginalTextInSitu (): Boolean
  {
    return 0 != processingFlags.and(ReversificationData.C_RetainOriginalVersesInSitu)
  }

  override fun toString (): String
  {
    fun printableDetails (original: String?, revised: String): String
    {
      val changed = null != original && original.replace(" ", "").lowercase() != revised.replace(" ", "").lowercase()
      return if (changed) "$original [changed to $revised]" else revised
    }

    val sourceType  = ReversificationData.getField("SourceType", this)
    val sourceRef   = printableDetails(originalSourceRef, sourceRef.toString())
    val standardRef = printableDetails(originalStandardRef, standardRef.toString())
    val action      = printableDetails(originalAction, action)

    return "Row: " + rowNumber.toString() + " " +
      sourceType  + " " +
      sourceRef   + " " +
      standardRef + " " +
      action      + " " +
      ReversificationData.getField("NoteMarker", this)
  }

  init
  {
    rowNumber = rowNo
  }
}


/******************************************************************************/
class ReversificationMoveGroup (theRows: List<ReversificationDataRow>)
{
  fun getSourceBookAbbreviatedName (): String { return BibleBookNamesUsx.numberToAbbreviatedName(rows[0].sourceRef.getB()) }
  fun getStandardBookAbbreviatedName (): String { return BibleBookNamesUsx.numberToAbbreviatedName(rows[0].standardRef.getB()) }

  fun getSourceChapterRefAsString   (): String { return rows.first().sourceRef  .toString("bc")}
  fun getStandardChapterRefAsString (): String { return rows.first().standardRef.toString("bc")}

  var crossBook = false
  var isEntireChapter: Boolean = false
  val rows: List<ReversificationDataRow>
  val sourceRange: RefRange
  val standardRange: RefRange

  init
  {
    rows = theRows
    sourceRange = RefRange(theRows.first().sourceRef, theRows.last().sourceRef)
    standardRange = RefRange(theRows.first().standardRef, theRows.last().standardRef)
  }
}





object ReversificationData
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Package                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* General comments
     ================

     In general reversification rows are applicable so long as the sourceRef
     exists and the rule (if any) passes.  However, currently we are reigning
     back on copyright texts, something we check for below.  If a text is
     recognised as being copyright (which is flagged up either by a command-line
     flag or by finding tell-tale signs like the word 'copyright' in the
     metadata), we exclude rows which would entail moving verses to new
     chapters.  Or more accurately, we exclude _most_ such rows: more details
     below.

     Note that the reversification data as supplied from here may not be
     identical to the original.  There are various places where later
     processing is helped if we make minor changes here; and a few places
     where I suspect that the reversification data may be wrong, and correct it
     here.
  */

  /****************************************************************************/
  /* Repertoire of atomic actions.  I do not cater for flagging situations where
     things should be annotated, because I assume that circumstances
     permitting, _all_ should. */

  private const val C_Move                       = 0x0001
          const val C_Renumber                   = 0x0002
          const val C_CreateIfNecessary          = 0x0004
          const val C_ComplainIfExisted          = 0x0008
          const val C_ComplainIfDidNotExist      = 0x0010
          const val C_SourceIsPsalmTitle         = 0x0020
          const val C_StandardIsPsalmTitle       = 0x0040
          const val C_RetainOriginalVersesInSitu = 0x0080
  private const val C_IfEmpty                    = 0x0100





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Initialisation                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Does what it says on the tin.  The 'IfNecessary' part is there because we
  * need do this only on reversification runs.
  */

  fun process ()
  {
    /**************************************************************************/
    /* Assume reversification isn't doing anything useful until we know
       otherwise. */

    ConfigData.put("stepAddedValueReversification", "n", true)



    /**************************************************************************/
    /* Find out whether licensing conditions etc limit the kinds of changes we
       can apply. */

    val stepAbout = ConfigData["stepAbout"]!!.lowercase()
    val licenceDefinitelyBarsSignificantChanges = "®" in stepAbout || "©" in stepAbout || "(c)" in stepAbout || "copyright" in stepAbout || "trademark" in stepAbout
    val stepPermitSignificantTextRestructuring = ConfigData["stepPermitsSignificantTextRestructuring"] ?: "no"
    when (stepPermitSignificantTextRestructuring.lowercase())
    {
      "n", "no", "f", "false" -> m_LicensingTermsLimitWhatWeCanDo = false
      "y", "yes", "t", "true" -> m_LicensingTermsLimitWhatWeCanDo = true
      "aslicence"             -> m_LicensingTermsLimitWhatWeCanDo = !licenceDefinitelyBarsSignificantChanges
      else                    -> Logger.error("Invalid value for stepPermitSignificantTextRestructuring: $stepPermitSignificantTextRestructuring")
    }



    /**************************************************************************/
    /* Load and analyse the reversification data.  Then look for rows marked as
       Move or Renumber.  If there are enough of them, make a record of the
       fact that reversification will be doing a fair amount of work, because
       when this is the case, we mark the module as being ours -- we want
       something to distinguish it from modules to which we haven't added value,
       but at the same time we recognise that this may be interpreted as us
       claiming credit for a text which is largely someone else's work, so we
       try to avoid marking the module in this way too readily.  (This isn't the
       only form of added value which feeds into the decision, incidentally.) */

    initialise()
    val n = m_SelectedRows.count { 0 != it.processingFlags.and(C_Move.or(C_Renumber)) }
    if (n > C_ConfigurationFlags_ReversificationThresholdMarkingAFairAmountOfWork)
      ConfigData.put("stepAddedValueReversification", "y", true)
  }

  private var m_LicensingTermsLimitWhatWeCanDo: Boolean = true





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Getters                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /** Getters -- obtain data from a given data row.
  *
  * @param row Data row from which item is to be obtained
  * @return Item requested.
  */

  fun getAncientVersions (row: ReversificationDataRow): String { return row.ancientVersions }
  fun getProcessingDetails (row: ReversificationDataRow): Int { return row.processingFlags }
  fun getFootnoteA (row: ReversificationDataRow): String { return getFootnote(row, "Note A") }
  fun getFootnoteB (row: ReversificationDataRow): String { return getFootnote(row, "Note B") }
  fun getFootnoteDetails (row: ReversificationDataRow): CalloutDetails { return row.calloutDetails }
  fun getSourceRef (row: ReversificationDataRow): Ref { return row.sourceRef }
  fun getSourceRefAsRefKey (row: ReversificationDataRow): RefKey { return row.sourceRefAsRefKey }
  fun getStandardRef (row: ReversificationDataRow): Ref { return row.standardRef }
  fun getStandardRefAsRefKey (row: ReversificationDataRow): RefKey { return row.standardRefAsRefKey }



  /****************************************************************************/
  /**
  * Getters -- get aggregate details of all books, all reversification rows
  * of a given kind, etc.  Where possible / sensible, things are returned in
  * order.  LIst of book names, for instance, are ordered in Bible order.
  *
  * Note 1: getCrossReferenceMappings maps source to standard refKey.  If the
  *   target is a subverse, that's what you get.  If the reversification
  *   processor has been configured to turn all subverses into their owning
  *   verse, it's down to that processor to amalgamate things.
  *
  * @return Lists of things.
  */

  fun getAllBookNumbersAbbreviatedNames (): List<String> { return m_AllBooks } // All book names subject to reversification processing.
  fun getSourceBooksAbbreviatedNames (): List<String> { return m_SourceBooks }
  fun getStandardBooksAbbreviatedNames (): List<String> { return m_StandardBooks }

  fun getAllBookNumbersInvolvedInMoveActionsAbbreviatedNames (): List<String> { return m_AllBooksInvolvedInMoveActions } // Book names, but just those involved in move processing as either source or target.
  fun getSourceBooksInvolvedInMoveActionsAbbreviatedNames (): List<String> { return m_SourceBooksInvolvedInMoveActions }
  fun getStandardBooksInvolvedInMoveActionsAbbreviatedNames (): List<String> { return m_StandardBooksInvolvedInMoveActions }

  fun getBookMappings (): Set<String> { return m_BookMappings } // String of the form abc.xyz, indicating there is a mapping from book abc to xyz.
  fun getReferenceMappings (): Map<RefKey, RefKey> { return m_SelectedRows.filter { 0 != it.processingFlags.and(C_Renumber) }. associate { it.sourceRefAsRefKey to it.standardRefAsRefKey } } // Note 1 above.

  fun getAllMoveGroups (): List<ReversificationMoveGroup> { return m_MoveGroups }
  fun getMoveGroupsWithBookAsSource (abbreviatedName: String): List<ReversificationMoveGroup> { val bookNo = BibleBookNamesUsx.abbreviatedNameToNumber(abbreviatedName); return m_MoveGroups.filter { bookNo == it.sourceRange.getLowAsRef().getB() } }

  fun getNonMoveRowsWithBookAsSource   (abbreviatedName: String): List<ReversificationDataRow> {  val bookNo = BibleBookNamesUsx.abbreviatedNameToNumber(abbreviatedName); return m_SelectedRows.filter { 0 == it.processingFlags.and(C_Move) && bookNo == it.sourceRef.getB() } }
  fun getNonMoveRowsWithBookAsStandard (abbreviatedName: String): List<ReversificationDataRow> {  val bookNo = BibleBookNamesUsx.abbreviatedNameToNumber(abbreviatedName); return m_SelectedRows.filter { 0 == it.processingFlags.and(C_Move) && bookNo == it.standardRef.getB() } }

  fun getAllAcceptedRows (): List<ReversificationDataRow> { return m_SelectedRows }

  fun getIfEmptyRows (): List<ReversificationDataRow> { return m_IfEmptyRows }

  fun targetsDc (): Boolean
  {
    try
    {
      return m_StandardBooks.any { BibleAnatomy.isDc(BibleBookNamesUsx.abbreviatedNameToNumber(it)) }
    }
    catch (_: Exception)
    {
      return false
    }
  }


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
    val moveOrRenumberFlag = C_Move.or(C_Renumber)
    val standardRefs = m_SelectedRows.map { it.standardRefAsRefKey }.toSet()
    return m_SelectedRows.asSequence()
                         .filter { 2 == Ref.getS(it.standardRefAsRefKey) }
                         .filter { Ref.setS(it.standardRefAsRefKey, 1) !in standardRefs }
                         .filter { 0 != it.processingFlags.and(moveOrRenumberFlag) }
                         .map { it.standardRefAsRefKey }
                         .toSet()
  }



  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                     Private -- data initialisation                     **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private val m_IfEmptyRows: MutableList<ReversificationDataRow> = ArrayList(200)
  private val m_SelectedRows: MutableList<ReversificationDataRow> = ArrayList(10000)



  /****************************************************************************/
  /* Runs over the text, selects the relevant portion, and then parses and
     canonicalises the individual rows in that portion. */

  private fun convertToProcessedForm (rawData: List<String>)
  {
    var rowNumber = 0

    rawData.forEach {
      var rawRow = it
      val isInNoTestsSection = '\u0001' == rawRow.last() // See head-of-routine comments to readData.
      if (isInNoTestsSection) rawRow = it.substring(0, rawRow.length - 1)
      loadRow(rawRow, ++rowNumber, isInNoTestsSection, rawData.size)
    }

    Dbg.displayReversificationRows(getAllAcceptedRows())
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

  private fun convertToProcessedForm (fields: MutableList<String>, rowNumber: Int, isInNoTestsSection: Boolean): Pair<ReversificationDataRow, Boolean>
  {
    /**************************************************************************/
    var accepted = true
    val processedRow = ReversificationDataRow(rowNumber)
    processedRow.fields = fields
    processedRow.isInNoTestsSection = isInNoTestsSection
    processedRow.sourceType = getField("SourceType", processedRow).replace("EngTitle", "EnglishTitle").replace("AllBibles", "")



    /**************************************************************************/
    var sourceRefAsString   = usxifyFromStepFormat(getField("SourceRef", processedRow))
    var standardRefAsString = usxifyFromStepFormat(getField("StandardRef", processedRow))
    processedRow.action = getField("Action", processedRow)



    /**************************************************************************/
    /*
       * The reversification data talks about 4Es, but USX doesn't allow for it.
         If we have 4Es, then I ignore it.

       * For debug purposes it is often convenient to process only a few books.
         We need to ignore reversification data for any where the source
         reference is for a book which we are not processing. */

    if (sourceRefAsString.lowercase().contains("4es")) accepted = false
    if (accepted && !Dbg.wantToProcessBookByAbbreviatedName(sourceRefAsString.substring(0, 3))) accepted = false



    /**************************************************************************/
    /* Debug. */
    //Dbg.d(processedRow.rowNumber == 4863)
    //Dbg.d(processedRow.rowNumber)



   /**************************************************************************/
   /**************************************************************************/
   /* I'm not entirely convinced the reversification data is correct in all
      cases.  And even where it _is_ correct, it may be convenient to modify
      it to make later processing simpler. */
   /**************************************************************************/
   /**************************************************************************/

   /**************************************************************************/
   /* There are some places where source and standard ref are identical, and
      yet the action is shown (erroneously I believe) as a Move.  Change
      these to non-move. */

   if (sourceRefAsString == standardRefAsString && processedRow.action.contains("*"))
   {
     processedRow.originalAction = processedRow.action
     processedRow.action = processedRow.action.replace("*", "")
   }



   /**************************************************************************/
   /* If either sourceRef or standardRef refer to subverse zero, the subverse
      can be dropped -- USX doesn't recognise subverse zero, so we won't have
      anything marked as such in the input (it will just be the verse), and
      we don't want to create anything like that in the output. */

   if (sourceRefAsString.endsWith(".0"))
   {
     processedRow.originalSourceRef = sourceRefAsString
     sourceRefAsString = sourceRefAsString.replace(".0", "")
   }

   if (standardRefAsString.endsWith(".0"))
   {
     processedRow.originalStandardRef = standardRefAsString
     standardRefAsString = standardRefAsString.replace(".0", "")
   }



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

   if (standardRefAsString.contains("title"))
   {
     processedRow.originalStandardRef = standardRefAsString
     standardRefAsString = standardRefAsString.replace("title", RefBase.C_TitlePseudoVerseNumber.toString())
     if (!sourceRefAsString.contains("title")) // If we're using verses to create the title, make it appear they are creating subverses of the special verse.
     {
       val sourceVerse = Ref.rdUsx(sourceRefAsString).getV()
       standardRefAsString += convertNumberToRepeatingString(sourceVerse, 'a', 'z')
     }
     processedRow.processingFlags = processedRow.processingFlags or C_StandardIsPsalmTitle
   }

   if (sourceRefAsString.contains("title"))
   {
     processedRow.originalSourceRef = sourceRefAsString
     sourceRefAsString = sourceRefAsString.replace("title", RefBase.C_TitlePseudoVerseNumber.toString())
     processedRow.processingFlags = processedRow.processingFlags.or(C_SourceIsPsalmTitle)
  }



    /**************************************************************************/
    processedRow.sourceRef   = Ref.rdUsx(sourceRefAsString)
    processedRow.standardRef = Ref.rdUsx(standardRefAsString)

    processedRow.sourceRefAsRefKey   = processedRow.sourceRef.toRefKey()
    processedRow.standardRefAsRefKey = processedRow.standardRef.toRefKey()



    /**************************************************************************/
    setCalloutAndFootnoteLevel(processedRow)



    /**************************************************************************/
    /* It would actually be more efficient to do this earlier on and abandon
       further processing if it fails.  However, it is convenient to do a fair
       bit of the processing regardless, because it creates information which
       may be useful for debugging. */

    processedRow.sourceRef = Ref.rdUsx(usxifyFromStepFormat(sourceRefAsString))
    val ruleData = getField("Tests", processedRow)
    val tmp = if (processedRow.sourceType.equals("IfEmpty", ignoreCase = true)) null else processedRow.sourceRef // Don't test existence of sourceRef on an IfEmpty row (which actually has nothing directly to do with reversification).
    if (!ReversificationRuleEvaluator.rulePasses(tmp, ruleData, processedRow))
      accepted = false



    /**************************************************************************/
    /* Work out what we actually need to do to implement the required action.
       setupProcessingDetails will return false if the action is one we can't
       carry out (which, at the time of writing, will be where we are being
       asked to do something 'significant' like moving a verse, which might be
       beyond the pale as regards licensing conditions). */

    if (!setupProcessingDetails(processedRow))
      accepted = false



    /**************************************************************************/
    setAncientVersions(getField("Ancient Versions", processedRow))



    /**************************************************************************/
    /* Where verses are subject to Move's, recent changes require us sometimes
       to retain a copy of the original text in situ, as well as creating a new
       copy in the target location.  To a first approximation, we want to do
       this an any Move which moves stuff to a new chapter -- except on Psalms,
       where in some texts just about every chapter is renumbered, and retaining
       a copy would be way over the top.  In fact, it looks as though things may
       end up being more nuanced than this, and I'm wondering whether ultimately
       an automatic decision will be possible at all, but pro tem this code
       should be ok while we're still investigating. */

    if ( (0 != processedRow.processingFlags and C_Move) &&
         processedRow.sourceRef.toRefKey_bc() != processedRow.standardRef.toRefKey_bc() &&
         BibleBookNamesUsx.C_BookNo_Psa != processedRow.sourceRef.getB())
      processedRow.processingFlags = processedRow.processingFlags.or(C_RetainOriginalVersesInSitu)



    /**************************************************************************/
    return Pair(processedRow, accepted)
  }


  /****************************************************************************/
  /* This is revolting; I can only assume that the need for it became apparent
     late in the day, when it would have been too difficult to rejig the
     reversification data to handle it properly.

     Lots of rows are marked 'AllBibles' in the SourceType field, and all of
     these are marked KeepVerse.  Normally KeepVerse retains an existing verse
     and complains if the verse does not already exist.

     However, on AllBibles rows, KeepVerse is allowed to create verses if they
     don't already exist -- except that in a further twist, it has to issue a
     warning in some cases (but not all) if it has to create the verse.

     And just to make life thoroughly awful, the way I am required to
     distinguish these cases has to be based upon the contents of the FootnoteA
     column -- certain things there imply that a warning is needed, while others
     do not.  (The problem being here that this field is free-form text, so
     sooner or later it is going to change, sure as eggs is eggs, and I shan't
     realise that's an issue.) */

  private val m_NoteAOptionsText = listOf("At the end of this verse some manuscripts add information such as where this letter was written",
                                          "In some Bibles this verse is followed by the contents of Man.1 (Prayer of Manasseh)",
                                          "In some Bibles this verse starts on a different word")

  private fun getAllBiblesComplaintFlag (row: ReversificationDataRow): Int
  {
    val noteA = getField("Note A", row)
    return if (m_NoteAOptionsText.contains(noteA)) C_ComplainIfDidNotExist else 0
  }


  /****************************************************************************/
  /* Looks for a row containing a given piece of text.  Used to identify where
     the reversification data of interest to us starts and ends within the
     overall text.
  */

  private fun findLine (data: List<String>, lookFor: String, startAt: Int): Int
  {
    val lookForLower = lookFor.lowercase()
    for (i in startAt..< data.size)
      if (data[i].lowercase().startsWith(lookForLower)) return i

    throw StepException("Guard row $lookFor missing from reversification data")
  }


  /****************************************************************************/
  /**
   * Returns all of the reversification data which has been selected.  Entries
   * are in the same order as the rows of the raw reversification data.
   *
   * The data is split up into groups of similar actions.
   */

  private fun initialise ()
  {
    if (m_AlreadyInitialised) return
    m_AlreadyInitialised = true

    if (TextConverterProcessorReversification.runMe())
      Dbg.reportProgress("Reading reversification data from webpage.", 1)
    else
      Dbg.reportProgress("Reading reversification data from webpage.  (This contains data needed even if not reversifying.)", 1)

    val dataLocation = ConfigData["stepReversificationDataLocation"]!!
    if (!dataLocation.startsWith("http")) Logger.warning("Running with local copy of reversification data.")
    val rawData = readData(dataLocation)
    Dbg.reportProgress("Parsing reversification data", 1)
    val ixLow = findLine(rawData, "#DataStart(Expanded)", 0)
    val ixHigh = findLine(rawData, "#DataEnd", ixLow)
    convertToProcessedForm(rawData.subList(ixLow + 1, ixHigh))
    // validate() // Checks validity of the reversification data itself.  This used to matter when the data was still being developed.  Hopefully it's no longer required.  The old Java code is appended at the end of this file.
    Logger.announceAll(true)
    aggregateData()
    debugOutputDebugData()
  }

  private var m_AlreadyInitialised = false


  /****************************************************************************/
  /**
  * Adds details of a single row, assuming it passes the relevant tests.
  *
  * @param rawData The row in raw form.
  * @param rowNumber Ordinal number of row within reversification data -- for progress reporting and debugging.
  * @param isInNoTestsSection True if we're in the 'no tests' section of the data.
  * @param rawDataSize Number of rows in the raw data.
  */

  private fun loadRow (rawData: String, rowNumber: Int, isInNoTestsSection: Boolean, rawDataSize: Int)
  {
    /**************************************************************************/
    if (rowNumber == 1000 * (rowNumber / 1000))
      Dbg.reportProgress("Processing row $rowNumber of $rawDataSize", 2)



    /**************************************************************************/
    val fields = rawData.split("\t").map { it.trim() }

    if (m_Headers.isEmpty())
    {
      var n = -1
      fields.forEach { m_Headers[it] = ++n }
    }
    else
    {
      val (processedRow, accepted) = convertToProcessedForm(fields.toMutableList(), rowNumber, isInNoTestsSection)
      if (accepted) // We always need IfEmpty rows; but we need other rows only on reversification rows.
      {
        if (0 == processedRow.processingFlags and C_IfEmpty)
        {
          m_SelectedRows.add(processedRow)
          debugRecordReversificationRow(true, processedRow)
        }
        else
          m_IfEmptyRows.add(processedRow)
      }
      else // Not accepted.
        debugRecordReversificationRow(false, processedRow)
    }
  }


  /****************************************************************************/
  /* Reads text from the reversification file or web page.  There is one
     revolting kluge which needs describing here.  Very late in the day a
     requirement was added.  In the raw reversification data, there is a comment
     row which says 'NO TESTS:', reflecting the fact that subsequent
     reversification data rows do not have any tests as to applicability.
     I have been asked to ensure that any footnotes generated by any rows after
     the NO TESTS row come out at the beginning of the verse.  (In the normal
     course of events, if a row applied to a verse which had already been
     given content, the footnote would come out at the end of the existing
     content, and before any new content.)

     This new requirement is wrong on so many scores.  It relies on testing a
     comment, and being a comment there is nothing to require that it continue
     in its present form, or even that it exist at all; and it then requires
     that I use the existence of this comment to apply special processing to
     rows of reversification data, rather than making this requirement explicit
     in the data itself.

     Nonetheless, that is the requirement, so the processing here (and elsewhere
     in this file, which refers back to this routine) is as follows :-

     * I check for the special comment row.  In doing this, I have to assume
       both that the row continues to exist, and to exist in its present form,
       and that nowhere else in the reversification data is there any other
       comment which could be mistaken for this comment.

     * To rows which follow this comment, I append \u0001 here, as a marker for
       later use.

     * When I actually use the data gathered here, I look for this marker, and
       if present, I set a flag in the reversification data for use by
       downstream processing.
  */

  private fun readData (dataLocation: String): List<String>
  {
    var inNoTestsSectionAppend = ""

    val res: MutableList<String> = ArrayList(30000)

    val data = (if (dataLocation.contains("http")) URL(dataLocation).readText() else File(dataLocation).readText()).split("\n")

    data.forEach loop@ {
        var trimmed = it.trim()
        if (trimmed.isEmpty()) return@loop
        if (trimmed.startsWith("=")) return@loop // Rows of equals signs are underlines for headers in the raw data.

        if (trimmed.startsWith("#")) // # marks a comment, and can be ignored, except that we need to retain #DataStart and #DataEnd in order to recognise the section of data we require.
        {
          val lc = trimmed.lowercase()
          if (lc.contains("no tests:")) inNoTestsSectionAppend = "\u0001"
          if (!lc.startsWith("#datastart") && !lc.startsWith("#dataend")) return@loop
        }

        trimmed = trimmed.replace("٠", "") // That character is an Arabic numeral, which appears occasionally in the data to mark subverse zero.

        res.add(trimmed + inNoTestsSectionAppend)
      }

    return res
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

  private fun setCalloutAndFootnoteLevel (row: ReversificationDataRow)
  {
    /**************************************************************************/
    var x = getField("NoteMarker", row)
    when (x.trim().substring(0, 2).lowercase())
    {
      "ne" -> row.footnoteLevel = 0 // Necessary.
      "ac" -> row.footnoteLevel = 1 // Academic.
      else -> Logger.error("Reversification invalid note level: " + x[0])
    }

    val ix = x.indexOf(".")
    x = x.substring(ix + 1)



    /**************************************************************************/
    val cd = CalloutDetails()
    row.calloutDetails = cd



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
  /* Converts the information now available into details of the actual
     processing steps which will be required.

     Returns false if, based upon the reversification actions, we don't want
     this row after all.  This happens basically if we are doing anything
     which involves a Move on a copyright text where we have reason to believe
     that the licensing terms may limit what we can do. */

  private fun setupProcessingDetails (row: ReversificationDataRow): Boolean
  {
    /**************************************************************************/
    //Dbg.d(5120 == row.rowNumber)



    /**************************************************************************/
    /* Rationalise the action shown in the reversification data.  I need to
       reduce this to a list of the individual actions I actually need to
       perform, at the same time, ironing out any idiosyncrasies and making some
       changes to assist the processing.

       In essence, I'm concerned with two things.  First, I need to identify
       which actions involve moving things around physically; and second I
       need to reduce the various actions to a series of common atomic changes
       to be applied. */



    /**************************************************************************/
    /* To begin with, DIB has already marked things which he regards as moves.
       One particular point to note here is that where verses are ending up with
       the same neighbours as before, but _are_ being renumbered, in general
       these rows are _not_ marked as moves (because the update can be
       carried out in situ).  There are a few places where it _appears_ that we
       are operating in situ, but which are nonetheless flagged as moves.  In
       general, these are places where we have a small cycle of associated
       changes eg v1 -> v2 -> v3 -> v1.  I suspect in many of these cases that
       not all of the individual actions need to be flagged as moves, but I've
       gone with what DIB has marked up. */

    row.action = row.action.lowercase()
    var moveFlag = if (row.action.endsWith('*')) C_Move else 0
    row.originalAction = row.action
    row.action = row.action.replace("*", "")



    /**************************************************************************/
    /* Sometimes KeepVerse, MergedVerse and EmptyVerse are flagged as moves, but
       I'm not clear why, since in none of these cases is anything moved from
       one place to another. */

    if (row.action.contains("keep") || row.action.contains("merge") || row.action.contains("empty"))
    {
      moveFlag = 0
      row.originalAction = row.action
      row.action = row.action.replace("*", "")
    }



    /**************************************************************************/
    /* Some Renumber rows are not marked as Moves, because they simply take,
       say, a verse which falls at the start of one chapter and move it to the
       end of the previous one (this is particularly the case in Psalms).  I
       need to treat these as Moves nonetheless, because I turn chapter tags
       into enclosing tags rather then milestones, and therefore do physically
       need to shift things.  And in fact at the time of writing I think there
       are also some places where genuine Moves have not been marked as such,
       so here I force things to be flagged as Moves if the source book /
       chapter is not the same as the standard book / chapter. */

    else if (row.sourceRef.toRefKey_bc() != row.standardRef.toRefKey_bc())
      moveFlag = C_Move



    /**************************************************************************/
    /* We have already picked up RenumberVerse issues from above -- ie we've
       carried through what DIB had to say on the subject.  However, anything
       which starts with Renumber (RenumberVerse or RenumberTitle) needs to
       be forced to be a move if it involves a change of chapter.  DIB doesn't
       always flag these as such, but I need them marked as moves because I
       have _enclosing_ chapter tags (not milestones), so moving a verse to a
       new chapter must involve a physical move. */

    if (row.action.startsWith("renumber"))
    {
      if (row.sourceRef.toRefKey_bc() != row.sourceRef.toRefKey_bc()) moveFlag = C_Move
    }



    /**************************************************************************/
    /* Things get a bit confusing here.  We want to weed out rows which might
       make 'significant' changes to the text where the text is subject to
       copyright and we don't have permission to make changes.  But we've been
       messing around with the Action field to get rid of what I believe to be
       errors in the reversification data and / or to make processing more
       uniform.

       So, we evaluate things here only if m_LicensingTermsLimitWhatWeCanDo
       indicates the need to do so, and only if we believe we are moving
       stuff.

       Where this applies, I make the assumption that if the original version
       of the Action parameter indicated a Move, then this is definitely a
       Move which we wish to reject.

       We have an original version only if the processing has actually changed
       the action parameter.  If we don't have an original version, I assume
       that we can go by whether the Action field itself is marked as a Move
       (ie contains an asterisk).
     */

    if (m_LicensingTermsLimitWhatWeCanDo && 0 != moveFlag)
    {
      if (null != row.originalAction)
      {
        if ('*' in row.originalAction!!)
          return false
      }
      else if ('*' in row.action)
        return false
    }



    /**************************************************************************/
    /* The only thing we haven't taken into account is PsalmTitle.  However,
       that never involves a move, and is never marked as doing so, so there's
       nothing to do with that.  So we can now go ahead and set the Move flag
       if necessary. */

    row.processingFlags = row.processingFlags.or(moveFlag)



    /**************************************************************************/
    /* The next stage is to map the various possible compound actions as they
       appear in the reversification data to the atomic actions which implement
       them. */

    if (row.sourceType.equals("IfEmpty", ignoreCase = true))
      row.processingFlags = row.processingFlags.or(C_IfEmpty)

    else if (row.action.contains("renumber"))
    {
      if (!row.action.contains("title"))
        row.processingFlags = row.processingFlags.or(C_Renumber)
    }

    else if (row.action.contains("empty"))
      row.processingFlags = row.processingFlags.or(C_CreateIfNecessary)

    else if (row.action.contains("merge"))
      row.processingFlags = row.processingFlags.or(C_CreateIfNecessary)

    else if (row.action.contains("keep"))
    {
      if (row.sourceType.isEmpty()) // In the AllBibles section -- needs special processing.
      {
        row.processingFlags = row.processingFlags.or(C_CreateIfNecessary)
        row.processingFlags = row.processingFlags.or(getAllBiblesComplaintFlag(row))
      }
      else
        row.processingFlags = row.processingFlags.or(C_CreateIfNecessary)
    }

    else if (row.action.contains("psalm"))
      ;

    else
      throw StepException("Can't work out how to process &action")

      return true
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Data aggregation                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  private val m_MoveGroups: MutableList<ReversificationMoveGroup> = ArrayList()

  private lateinit var m_AllBooks: List<String>
  private lateinit var m_SourceBooks: List<String>
  private lateinit var m_StandardBooks: List<String>

  private lateinit var m_AllBooksInvolvedInMoveActions: List<String>
  private lateinit var m_SourceBooksInvolvedInMoveActions: List<String>
  private lateinit var m_StandardBooksInvolvedInMoveActions: List<String>

  private          var m_BookMappings: MutableSet<String> = HashSet()


  /****************************************************************************/
  /* Carries out the various forms of data aggregation required by callers. */

  private fun aggregateData ()
  {
    aggregateBooks()
    aggregateBooksInvolvedInMoveActions()
    aggregateMoveGroups()
    identifySpecialistMoves()
    recordBookMappings()
  }


  /****************************************************************************/
  private fun aggregateBooks ()
  {
    val allBooks:     MutableSet<Int> = HashSet()
    val bookMappings: MutableSet<String> = HashSet()
    val sourceBooks:  MutableSet<Int> = HashSet()
    val targetBooks:  MutableSet<Int> = HashSet()

    m_SelectedRows.forEach {
      allBooks.add(it.sourceRef.getB())
      allBooks.add(it.standardRef.getB())
      sourceBooks.add(it.sourceRef.getB())
      targetBooks.add(it.standardRef.getB())
      bookMappings.add(BibleBookNamesUsx.numberToAbbreviatedName(it.sourceRef.getB()) + "." + BibleBookNamesUsx.numberToAbbreviatedName(it.standardRef.getB()))
    }

    m_AllBooks      = allBooks   .sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
    m_SourceBooks   = sourceBooks.sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
    m_StandardBooks = targetBooks.sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
  }


  /****************************************************************************/
  private fun aggregateBooksInvolvedInMoveActions ()
  {
    val allBooks:    MutableSet<Int> = HashSet()
    val sourceBooks: MutableSet<Int> = HashSet()
    val targetBooks: MutableSet<Int> = HashSet()

    m_SelectedRows.filter { 0 != it.processingFlags.and(C_Move) }
      .forEach { allBooks.add(it.sourceRef.getB()); allBooks.add(it.standardRef.getB()); sourceBooks.add(it.sourceRef.getB()); targetBooks.add(it.standardRef.getB()) }

    m_AllBooksInvolvedInMoveActions      = allBooks   .sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
    m_SourceBooksInvolvedInMoveActions   = sourceBooks.sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
    m_StandardBooksInvolvedInMoveActions = targetBooks.sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
  }


  /****************************************************************************/
  /* Collects together consecutive Move rows which can be actioned as a block.

     Note that where dealing with subverses, groups do not extend across the
     owning verse boundary.

     The aim here is to identify groups of consecutive rows where ...

     * All the source elements are verses, or all are subverses.

     * All of the standard elements are verses, or all are subverses.

     * The source elements go up by one verse if they are verses, or by one
       subverse if they are subverses.

     * Ditto for standard elements.

     The reason for this rather fiddly method is that when processing Moves, it
     is desirable to move entire blocks if possible, rather than move one verse
     at a time.  This way, cross-verse-boundary markup is less of an issue --
     cross-boundary markup would be an issue only if it runs across the boundary
     of the entire block, rather than being an issue for each individual verse.
  */

  private fun aggregateMoveGroups ()
  {
    /**************************************************************************/
    val rows = m_SelectedRows.filter { 0 != it.processingFlags.and(C_Move) }
    if (rows.isEmpty()) return



    /**************************************************************************/
    val sortedRows = rows.sortedWith(::cfForMoveGroups)
    var  ixLow = 0
    while (ixLow < sortedRows.size)
    {
      var refKeySourcePrev = sortedRows[ixLow].sourceRefAsRefKey
      var refKeyStandardPrev = sortedRows[ixLow].standardRefAsRefKey
      val refKeySourceInterval = if (Ref.hasS(refKeySourcePrev)) 1 else RefBase.C_Multiplier
      val refKeyStandardInterval = if (Ref.hasS(refKeyStandardPrev)) 1 else RefBase.C_Multiplier

      var  ixHigh = ixLow + 1
      while (ixHigh < sortedRows.size)
      {
        val refKeySourceThis = sortedRows[ixHigh].sourceRefAsRefKey
        val refKeyStandardThis = sortedRows[ixHigh].standardRefAsRefKey
        if (refKeySourceThis - refKeySourcePrev != refKeySourceInterval) break
        if (refKeyStandardThis - refKeyStandardPrev != refKeyStandardInterval) break
        refKeySourcePrev = refKeySourceThis
        refKeyStandardPrev = refKeyStandardThis
        ++ixHigh
      }

      m_MoveGroups.add(ReversificationMoveGroup(sortedRows.subList(ixLow, ixHigh)))
      ixLow = ixHigh
    }
  }


  /****************************************************************************/
  private fun identifySpecialistMoves ()
  {
    fun process (grp: ReversificationMoveGroup)
    {
      if (grp.rows.first().sourceRef.hasS()) return
      if (grp.rows.first().standardRef.hasS()) return

      if (1 != grp.rows.first().sourceRef.getV()) return
      if (1 != grp.rows.first().standardRef.getV()) return

      if (BibleStructure.UsxUnderConstructionInstance().getLastVerseNo(grp.rows.first().sourceRef) != grp.rows.last().sourceRef.getV()) return
      if (BibleStructure.NrsvxInstance().getLastVerseNo(grp.rows.first().standardRef) != grp.rows.last().standardRef.getV()) return

      grp.isEntireChapter = true
    }

    m_MoveGroups.forEach { process(it) }
    m_MoveGroups.forEach { it.crossBook = it.rows.first().sourceRef.getB() != it.rows.first().standardRef.getB()
}
  }


  /****************************************************************************/
  /**
   * Gives ordering information.  Rows will be ordered on increasing source
   * refKey, and within that on increasing standard refKey.
   *
   * @param a Row to compare.
   * @param b Row to compare.
   *
   * @return  Ordering information.
   */

  private fun cfForMoveGroups (a: ReversificationDataRow, b: ReversificationDataRow): Int
  {
    fun zeroiseSubverseIfLacking (x: RefKey): RefKey
    {
      if (Ref.hasS(x)) return x
      return Ref.setS(x, 0)
    }

    val aSourceRefKey   = zeroiseSubverseIfLacking(a.sourceRefAsRefKey)
    val bSourceRefKey   = zeroiseSubverseIfLacking(b.sourceRefAsRefKey)
    val aStandardRefKey = zeroiseSubverseIfLacking(a.standardRefAsRefKey)
    val bStandardRefKey = zeroiseSubverseIfLacking(b.standardRefAsRefKey)

    return if (aSourceRefKey < bSourceRefKey) -1
           else if (aSourceRefKey > bSourceRefKey) 1
           else if (aStandardRefKey < bStandardRefKey) -1
           else if (aStandardRefKey > bStandardRefKey) 1
           else 0
  }

  /****************************************************************************/
  private fun recordBookMappings ()
  {
    fun addMapping (row: ReversificationDataRow)
    {
      val sourceBook   = BibleBookNamesUsx.numberToAbbreviatedName(row.sourceRef  .getB())
      val standardBook = BibleBookNamesUsx.numberToAbbreviatedName(row.standardRef.getB())
      m_BookMappings.add("$sourceBook.$standardBook")
    }

    m_SelectedRows.forEach { addMapping(it) }
  }






  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                        Private -- miscellaneous                        **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun getField (key: String, dataRow: ReversificationDataRow): String
  {
    val ix = m_Headers[key]!!
    return if (ix < dataRow.fields.size) dataRow.fields[ix] else ""
  }


  /****************************************************************************/
  /* Returns a fully formatted footnote. */

  private fun getFootnote (row: ReversificationDataRow, selector: String): String
  {
    /**************************************************************************/
    //Dbg.d(row.toString())



    /**************************************************************************/
    /* Is there in fact any footnote at all? */

    var content = getField(selector, row)
    if (content.isEmpty()) return ""



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



    /**************************************************************************/
    /* If the footnote has no associated reference, we can simply return the
       text itself. */

    if (1 == bits[1].length)
      return Translations.stringFormat(Language.Vernacular, getTextKey(bits[0].trim())!!)



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

    val refAsString = bits[1].replace("\u0001", "").trim()
    val containsSlash = '/' in refAsString
    val containsPlus = '+' in refAsString
    if (!containsSlash && !containsPlus)
    {
      val rc = RefCollection.rdUsx(usxifyFromStepFormat(refAsString), dflt = null, resolveAmbiguitiesAs = "v")
      return Translations.stringFormat(Language.Vernacular, getTextKey(bits[0].trim())!!, rc)
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

    val rawMessage = Translations.lookupText(Language.English, getTextKey(bits[0].trim())!!)
    val regex = "(?i)(?<pre>.*)(?<ref>%Ref.*?>)(?<post>.*)".toRegex()
    val match = regex.matchEntire(rawMessage)
    val refFormat = match!!.groups["ref"]!!.value

    val elts = refAsString.split('/', '+').map { Translations.stringFormat(refFormat, RefCollection.rdUsx(it.trim(), dflt = null, resolveAmbiguitiesAs = "v")) }
    val eltsAssembled = elts.joinToString(Translations.stringFormat(Language.Vernacular, if (containsSlash) "V_reversification_ancientVersionsAlternativeRefsSeparator" else "V_reversification_alternativeReferenceEmbeddedPlusSign"))
    return match.groups["pre"]!!.value + eltsAssembled + match.groups["post"]!!.value
  }


  /****************************************************************************/
  /**
   *  Given a piece of footnote text from the reversification data, gives back
   *  the corresponding key which we can use to look up translations.
   */

  private fun getTextKey (lookupVal: String): String?
  {
    val res = m_TextKeyMap[lookupVal]
     if (null != res) return res
     Logger.error("Reversification: Invalid footnote text: $lookupVal")
     return null
  }

  private val m_TextKeyMap: MutableMap<String, String> = HashMap() // Footnote texts.
  private val m_Headers: MutableMap<String, Int>  = TreeMap(String.CASE_INSENSITIVE_ORDER) // Headers of reversification data file.


  /****************************************************************************/
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
    /**************************************************************************/
    //Dbg.d(theStepRef)



    /**************************************************************************/
    /* Get the reference string into canonical form.  The input may contain
       commas or semicolons as collection separators, and since the parsing
       processing is set up to handle either, it's convenient here to convert
       them all to just one form. */

    val stepRef = theStepRef.replace(",", ";")
                            .replace("--", "-")
                            .replace(" +", "")
                            .replace("•", "") // \u2022 -- Arabic zero.
                            .replace("٠", "") // \u0660 -- Bullet point, used in some places instead of Arabic zero.
                            .replace("([1-9A-Za-z][A-Za-z][A-Za-z]\\.)".toRegex()) { it.value.replace(".", " ") }
                            .replace("(?i)title".toRegex(), "title")
                            .replace("(?i):T$".toRegex(), "")// We have things like 53:T as the alternative reference on some Psalm rows.  I change these back to be chapter references.


    /**************************************************************************/
    fun processCollectionElement (elt: String): String
    {
      return elt.split("-").joinToString("-"){ usxify_1(it) }
    }



    /**************************************************************************/
    return stepRef.split(";").joinToString(";"){ processCollectionElement(it) }
  }


  /****************************************************************************/
  /* The reversification data has its own view of how references should be
     represented, and to save having to include specialised code to cater for
     these, it's convenient to convert to USX format up-front. */

  private fun usxify_1 (theStepRef: String): String
  {
    /**************************************************************************/
    /* Replace the full stop after the book name with a space. */

    var stepRef = theStepRef
    if (stepRef.matches("...\\..*".toRegex()))
      stepRef = stepRef.substring(0, 3) + " " + stepRef.substring(4)



    /**************************************************************************/
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



    /**************************************************************************/
    return stepRef
  }





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
          Translations.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsNoReference")
        else
          Translations.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsMainRefFormat", RefCollection.rd(usxifyFromStepFormat(refAsString), null,"v"))
      }

      fun processMainElementDelimiter (delim: String): String
      {
        return when (delim)
        {
          "/" -> Translations.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsAlternativeRefsSeparator")
          "+" -> Translations.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsJointRefsSeparator")
          else -> throw StepException("AncientVersions delimiter not handled: $delim")
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
        equivalenceInformation = " " + Translations.stringFormat(m_EquivalenceInformationFormatString, m_EquivalenceInformationReferenceCollection!!)



      /************************************************************************/
      val tradition: String = Translations.stringFormat(Language.Vernacular, "V_reversification_language$m_Tradition")
      return Translations.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsTraditionFormat", "tradition", tradition, "main", mainEltsAsString, "equivalenceInformation", equivalenceInformation)
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
              m_EquivalenceInformationFormatString = ConfigData["V_reversification_ancientVersionsEquivalencePlus"]!!
              equivalenceInformation = equivalenceInformation!!.substring(1)
           }

           '=' ->
            {
              m_EquivalenceInformationFormatString = ConfigData["V_reversification_ancientVersionsEquivalenceEquals"]!!
              equivalenceInformation = equivalenceInformation!!.substring(1)
            }

            else ->
            {
              m_EquivalenceInformationFormatString = ConfigData["V_reversification_ancientVersionsEquivalenceUndecorated"]!!
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

    var res = resElements.joinToString(Translations.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsTraditionSeparator"))
    res = Translations.stringFormat(Language.Vernacular, "V_reversification_ancientVersions", res)
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
    //Dbg.dCont(theText ?: "", "Latin=4:17; 13.8-14:9 / C:1-30")



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
  private class DebugData
  {
     var  m_RowsAccepted: MutableList<ReversificationDataRow> = ArrayList() // All of the rows accepted.
     var  m_RowsAcceptedButShouldNotHaveBeen: MutableList<ReversificationDataRow> = ArrayList()
     var  m_RowsRejectedButShouldNotHaveBeen: MutableList<ReversificationDataRow> = ArrayList()
  }

  private  val  m_DebugData = DebugData()


  /****************************************************************************/
  private fun debugOutputDebugData ()
  {
    if (null == m_AnticipatedSourceType) return
    m_DebugData.m_RowsAcceptedButShouldNotHaveBeen.forEach { x -> Logger.info("Reversification row accepted but perhaps should not have been: $x") }
    m_DebugData.m_RowsRejectedButShouldNotHaveBeen.forEach { x -> Logger.info("Reversification row rejected but perhaps should not have been: $x") }
    m_DebugData.m_RowsAccepted.forEach { x -> Logger.info("Reversification row accepted as anticipated: $x") }
  }


  /****************************************************************************/
  /* stepDebugReversificationAnticipatedSourceType, if defined at all, should
     have one of the values English, Hebrew, Latin or Greek.

     The processing then expects rows whose SourceType contains the relevant
     string (case-insensitive) to be selected for processing, while others
     are not, and will output messages to that effect.  This isn't _quite_
     right (some rows are for specific variants of these like Latin2), but
     it will at least give us something at least vaguely useful for
     checking.
  */

  private var m_AnticipatedSourceType : String? = ""


  /****************************************************************************/
  /* For debugging -- records details of which rows were accepted and
     rejected. */

  private fun debugRecordReversificationRow (rowAccepted: Boolean, row: ReversificationDataRow)
  {
    /**************************************************************************/
    if (null == m_AnticipatedSourceType) return

    if (m_AnticipatedSourceType!!.isEmpty())
    {
      m_AnticipatedSourceType = ConfigData["stepDebugReversificationAnticipatedSourceType"]
      if (null == m_AnticipatedSourceType) return
      m_AnticipatedSourceType = m_AnticipatedSourceType!!.lowercase()
    }



    /**************************************************************************/
    /* Ignore lines where the source ref relates to a book we don't have. */

   if (!BibleStructure.UsxUnderConstructionInstance().bookExists(row.sourceRef.getB())) return



    /**************************************************************************/
    val sourceType = row.sourceType.lowercase()
    val containsAnticipatedSource = sourceType.contains(m_AnticipatedSourceType!!) || sourceType.isEmpty()



    /**************************************************************************/
    if (rowAccepted)
    {
      m_DebugData.m_RowsAccepted.add(row)
      if (!containsAnticipatedSource)
        m_DebugData.m_RowsAcceptedButShouldNotHaveBeen.add(row)
    }
    else
    {
      if (containsAnticipatedSource && Dbg.wantToProcessBook(row.sourceRef.getB()) )
        m_DebugData.m_RowsRejectedButShouldNotHaveBeen.add(row)
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Sets up a table converting actual text from the reversification data to
     key values against which translations are stored.  This serves both to
     give us the translations and also as a check that the data in the
     reversification file uses only things already known to us.
  */

  init
  {
    m_TextKeyMap["Some manuscripts have no text here. Others have text similar to"] = "V_emptyContentFootnote_someManuscriptsHaveNoTextHereOthersHaveTextSimilarTo"
    m_TextKeyMap["Some manuscripts have no text here"] = "V_emptyContentFootnote_someManuscriptsHaveNoTextHere"
    m_TextKeyMap["Some manuscripts have no text at"] = "V_emptyContentFootnote_someManuscriptsHaveNoTextAt"
    m_TextKeyMap["As normal in this Bible the text for this verse is included in the previous verse"] = "V_emptyContentFootnote_asNormalInThisBibleTheTextForThisVerseIsIncludedInThePreviousVerse"
    m_TextKeyMap["As normal in this Bible the text for this verse is included in an adjacent verse"] = "V_emptyContentFootnote_asNormalInThisBibleTheTextForThisVerseIsIncludedInAnAdjacentVerse"

    m_TextKeyMap["As normal in this Bible the text for this verse is included at"] = "V_reversification_asNormalInThisBibleTheTextForThisVerseIsIncludedAt"
    m_TextKeyMap["As normal in this Bible the text for this verse is included in the previous verse"] = "V_reversification_asNormalInThisBibleTheTextForThisVerseIsIncludedInThePreviousVerse"
    m_TextKeyMap["As normal in this Bible the text for this verse is merged with"] = "V_reversification_asNormalInThisBibleTheTextForThisVerseIsMergedWith"
    m_TextKeyMap["As normal in this Bible the text for this verse is merged with"] = "V_reversification_asNormalInThisBibleTheTextForThisVerseIsMergedWith"
    m_TextKeyMap["As normal in this Bible this verse includes the text of"] = "V_reversification_asNormalInThisBibleThisVerseIncludesTheTextOf"
    m_TextKeyMap["As normal in this Bible this verse is followed by the contents of"] = "V_reversification_asNormalInThisBibleThisVerseIsFollowedByTheContentsOf"
    m_TextKeyMap["At the end of this verse some manuscripts add information such as where this letter was written"] = "V_reversification_atTheEndOfThisVerseSomeManuscriptsAddInformationSuchAsWhereThisLetterWasWritten"
    m_TextKeyMap["from"] = "V_reversification_fromForUseAtStartOfBulkMoveBlock"
    m_TextKeyMap["In some Bibles only the start of this verse is present"] = "V_reversification_inSomeBiblesOnlyTheStartOfThisVerseIsPresent"
    m_TextKeyMap["In some Bibles similar text is found at"] = "V_reversification_inSomeBiblesSimilarTextIsFoundAt"
    m_TextKeyMap["In some Bibles the verse numbering here is"] = "V_reversification_inSomeBiblesTheVerseNumberingHereIs"
    m_TextKeyMap["In some Bibles this book is found at"] = "V_reversification_inSomeBiblesThisBookIsFoundAt"
    m_TextKeyMap["In some Bibles this chapter is a separate book"] = "V_reversification_inSomeBiblesThisChapterIsASeparateBook"
    m_TextKeyMap["In some Bibles this verse contains extra text"] = "V_reversification_inSomeBiblesThisVerseContainsExtraText"
    m_TextKeyMap["In some Bibles this verse is followed by the contents of"] = "V_reversification_inSomeBiblesThisVerseIsFollowedByTheContentsOf"
    m_TextKeyMap["In some Bibles this verse is followed by the contents of PrAzar or S3Y (Prayer of Azariah or Song of Three Youths/Children)"] = "V_reversification_inSomeBiblesThisVerseIsFollowedByTheContentsOfPrAzarOrS3Y"
    m_TextKeyMap["In some Bibles this verse is followed by the contents of Sus (Susanna) and Bel (Bel and the Dragon)"] = "V_reversification_inSomeBiblesThisVerseIsFollowedByTheContentsOfSusAndBel"
    m_TextKeyMap["In some Bibles this verse may contain text similar to"] = "V_reversification_inSomeBiblesThisVerseMayContainTextSimilarTo"
    m_TextKeyMap["In some Bibles this verse may not contain any text"] = "V_reversification_inSomeBiblesThisVerseMayNotContainAnyText"
    m_TextKeyMap["In some Bibles this verse starts on a different word"] = "V_reversification_inSomeBiblesThisVerseStartsOnADifferentWord"
    m_TextKeyMap["Normally in this Bible only the start of this verse is present"] = "V_reversification_normallyInThisBibleOnlyTheStartOfThisVerseIsPresent"
    m_TextKeyMap["Normally in this Bible similar text is found at"] = "V_reversification_normallyInThisBibleSimilarTextIsFoundAt"
    m_TextKeyMap["Normally in this Bible the verse numbering here is"] = "V_reversification_normallyInThisBibleTheVerseNumberingHereIs"
    m_TextKeyMap["Normally in this Bible this verse and the next occur as one verse that is numbered"] = "V_reversification_normallyInThisBibleThisVerseAndTheNextOccurAsOneVerseThatIsNumbered"
    m_TextKeyMap["Normally in this Bible this verse does not contain any text"] = "V_reversification_normallyInThisBibleThisVerseDoesNotContainAnyText"
    m_TextKeyMap["Normally in this Bible this verse includes words that are at"] = "V_reversification_normallyInThisBibleThisVerseIncludesWordsThatAreAt"
    m_TextKeyMap["Normally in this Bible this verse is followed by contents similar to"] = "V_reversification_normallyInThisBibleThisVerseIsFollowedByContentsSimilarTo"
    m_TextKeyMap["Normally in this Bible this verse is followed by the contents of"] = "V_reversification_normallyInThisBibleThisVerseIsFollowedByTheContentsOf"
    m_TextKeyMap["Normally in this Bible this verse is followed by the contents of PrAzar or S3Y (Prayer of Azariah or Song of Three Youths/Children)"] = "V_reversification_normallyInThisBibleThisVerseIsFollowedByTheContentsOfPrAzarOrS3Y"
    m_TextKeyMap["Normally in this Bible verse numbering here is"] = "V_reversification_normallyInThisBibleVerseNumberingHereIs"
    m_TextKeyMap["Similar words are found at"] = "V_reversification_similarWordsAreFoundAt"
    m_TextKeyMap["Some manuscripts have no text at"] = "V_reversification_someManuscriptsHaveNoTextAt"
    m_TextKeyMap["Some manuscripts have no text here. Others have text similar to"] = "V_reversification_someManuscriptsHaveNoTextHere.OthersHaveTextSimilarTo"
    m_TextKeyMap["The extra words are found at"] = "V_reversification_theExtraWordsAreFoundAt"
    m_TextKeyMap["This verse may not contain any text"] = "V_reversification_thisVerseMayNotContainAnyText"

    m_TextKeyMap["In some Bibles this verse is followed by the contents of Man.1 (Prayer of Manasseh)"] = "V_reversification_inSomeBiblesThisVerseIsFollowedByTheContentsOfMan1"
    m_TextKeyMap["In some Bibles this chapter is the separate book LJe or EpJ (Epistle of Jeremiah)"] = "V_reversification_inSomeBiblesThisIsTheSeparateBookLje"

    m_TextKeyMap["Greek"] = "V_reversification_languageGreek"
    m_TextKeyMap["Greek2"] = "V_reversification_languageGreek"
    m_TextKeyMap["Hebrew"] = "V_reversification_languageHebrew"
    m_TextKeyMap["Latin"] = "V_reversification_languageLatin"
    m_TextKeyMap["Latin2"] = "V_reversification_languageLatin"
  }
}