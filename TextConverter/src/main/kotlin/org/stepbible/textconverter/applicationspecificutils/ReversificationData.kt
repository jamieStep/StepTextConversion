///******************************************************************************/
//package org.stepbible.textconverter.applicationspecificutils
//
//import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.*
//import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
//import org.stepbible.textconverter.nonapplicationspecificutils.configdata.TranslatableFixedText
//import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
//import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
//import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils.convertNumberToRepeatingString
//import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
//import org.stepbible.textconverter.nonapplicationspecificutils.shared.Language
//import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
//import org.stepbible.textconverter.protocolagnosticutils.PROBABLY_NOT_WORKING_PA_ConversionTimeReversification
//import java.io.File
//import java.net.URL
//import java.util.*
//
///******************************************************************************/
///**
// * Reads and stores reversification data, provides summary information about
// * it, and applies structuring to certain collections of the data.
// *
// * Note that not all data stored alongside the reversification data in the STEP
// * repository is actually reversification-related -- latterly some rows marked
// * IfAbsent and IfEmpty have been added, which apply regardless of whether
// * we are reversifying or not.  I handle these here nonetheless: they may not be
// * to do with reversification, but they are bundled with the reversification
// * data.
// *
// *
// *
// *
// * ## Reversification flavours
// *
// * There are two flavours of reversification processing.  One, which I have
// * dubbed 'conversionTime', generates a fully NRSVA-compliant module during
// * the conversion process.  The other ('runTime') leaves the text largely as
// * received from the translators (apart from the addition of some footnotes),
// * and relies upon the text being restructured on the fly at run time when the
// * user employs STEP's added-value features which require the text temporarily
// * to be NRSVA-compliant.
// *
// * The former of these is unlikely to be used to any great extent, because it
// * may make wholesale changes to the structure of the text, something ruled
// * out by the licence conditions applied to many texts.  Indeed, at the time
// * of writing, we do not anticipate making use of it at all.  Functionality
// * specific to conversion-time reversification I have therefore endeavoured
// * to split out here so that anyone maintaining things will not waste time
// * upon it.
// *
// * CAVEAT: One word of warning.  DIB refers to conversionTime processing
// * as reversification, and runTime as versification.  I struggle with this,
// * having got used to using reversification to mean pretty much anything which
// * makes use of the reversification data.  I limit myself to my own
// * terminology below -- or I will do so provided I remember.
// *
// *
// *
// *
// * ## Reversification actions
// *
// * The following actions may be applied during conversionTime processing:
// *
// * a) We may simply annotate a verse or a canonical heading.
// *
// * b) In some cases, if the verse which we wish to annotate does not exist
// *    we may create it.
// *
// * c) We may change the number of a verse, but leave it in situ (ie with the
// *    same neighbours as before).
// *
// * d) We may change both the verse reference and the physical location of the
// *    verse (perhaps even moving it to a different book, even a book which
// *    does not exist in the text as supplied to us).
// *
// * e) We may combine multiple verses or subverses to generate a single verse.
// *
// * f) We may turn one or two verses into a canonical title.
// *
// *
// * In what follows, I will refer to options c-f as restructuring actions.
// *
// *
// *
// *
// *
// * ## Reversification data -- detailed discussion
// *
// * I normally pick the data up directly from STEP's online repository.  This
// * file contains *two* copies of the data, one in compact form and one in
// * extended form, and we take the latter.  Each copy is demarcated by lines
// * containing #DataStart and #DataEnd, and we want the data from the *second*
// * pair.  From this, we exclude blank lines and comments, and what remains is
// * the data of interest.
// *
// * I'll now work through the various fields (not necessarily in the order in
// * which they appear in the file).
// *
// * - SourceType: Usually this is there for information only (ie is not used by
// *   the processing), and indicates, for example, that a particular row
// *   typically applies only to texts which follow the Latin (Vulgate)
// *   versification.
// *
// *   However, it is not used entirely consistently.  On a few rows it contains
// *   IfAbsent or IfEmpty.  This, I believe, should actually appear in the
// *   Action column, because these implicitly define the action to be taken if
// *   this row is activated.
// *
// * - SourceRef gives a verse number which is to be located in the raw text.
// *   In general, if this verse does not exist, the data row upon which it
// *   appears does not apply.  But there are exceptions to this -- for
// *   example, the IfAbsent rows mentioned above.  Here the action is intended
// *   to create a verse -- a verse which, with the present reversification data
// *   happens always to be the same as the source ref); and clearly if we
// *   demanded that the verse we want to create already exist, we would never
// *   create it.  On these rows (and some others), therefore, we cannot apply
// *   the source-must-exist test.
// *
// *   For any of the restructuring actions, incidentally, this gives the verse
// *   which is to be manipulated.
// *
// * - StandardRef: On restructuring actions, StandardRef indicates where the
// *   SourceRef should end up.  On other actions, it's rather less clear: I
// *   have always struggled to understand why we need both a SourceRef and a
// *   StandardRef on non-restructuring actions and why in some cases the two
// *   are different.
// *
// * - Action indicates the type of action required.  On restructuring actions,
// *   an asterisk appended to the action indicates that the source verse is
// *   being moved to another location.  There are some issues here, in that
// *   asterisks are not always used in the way I should expect.  This is
// *   discussed in more detail in a later section.
// *
// *   Some of the actions here may, in fact, be rather superfluous.  For
// *   example, the reversification data calls in some cases for verses to
// *   be created if they would otherwise be absent.  However, we have
// *   general backstop processing which does this anyway (or more specifically,
// *   fills in missing verses which occur at the start of chapters or in the
// *   middle, since those kinds of holes cause things to break -- I don't fill
// *   in verses which may be missing at the *ends* of chapters).
// *
// *   However, there is no *harm* ni handling things here and elsewhere, and
// *   doing things here does give us the chance to put reversification-specific
// *   footnotes on these newly-created verses, where otherwise I would add only
// *   generic footnotes.
// *
// * - NoteMarker is a somewhat complicated field which determines whether
// *   footnotes should be added at all (and if they should, how comprehensive
// *   the footnote should be) and also provides a verse number which is
// *   embedded in the canonical text to make it *look* as though a given verse
// *   has not been reversified.
// *
// * - NoteA gives the footnote to be added to the *standard* verse (ie the verse
// *   after restructuring) where footnotes are indeed being added and we are
// *   doing conversionTime restructuring.  (Except that at present we are _not_
// *   doing conversionTime restructuring, and no longer anticipate doing so.)
// *
// * - NoteB gives the footnote to be added to the *source* verse where footnotes
// *   are indeed being added and we are doing runTime restructuring.
// *
// * - AncientVersions gives additional information to be added to footnotes
// *   where we are adding footnotes and are generating a module aimed at an
// *   academic audience.
// *
// * - Tests gives tests which have to pass in order for the data row to be
// *   applicable.
// *
// *
// *
// *
// * ## Processing -- conversionTime restructuring
// *
// * A row applies only if the tests in the Tests field pass.  (This field may be
// * empty, in which case, the test is assumed always to pass.)
// *
// * Furthermore, the row applies only if a test applied to the SourceRef passes.
// * This test differs according to the type of action being applied, and is
// * discussed in more detail below.
// *
// *
// * *Restructuring options (except those involving psalm titles)*
// *
// * - The Action value is RenumberVerse (with or without an asterisk).
// *
// * - The SourceRef must exist.
// *
// * - For RenumberVerse the content of the SourceRef is left in situ, but
// *   the verse reference is changed to that for the StandardRef.  For
// *   RenumberVerse* the source content is moved to a new location and
// *   renumbered.
// *
// * - The standard verse may or may not be annotated -- see discussion below.
// *
// * @author ARA "Jamie" Jamieson
//**/
//
///******************************************************************************/
//object ReversificationData
//{
//  private data class ActionDescriptor (val involvesAMapping: Boolean, val canCreateTargetIfNecessary: Boolean)
//  private val m_ActionDescriptors: Map<String, ActionDescriptor>
//  init {
//    //List is probably incorrect.  Plus need to sort out the ActionDescriptor arguments and do something with it.
//    m_ActionDescriptors = mapOf(
//      "emptyverse"    to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "keepverse"     to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "keepverse*"    to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "emptyverse"    to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "emptyverse"    to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "mergedverse"   to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "mergedverse*"  to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "missingverse"  to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "missingverse*" to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "psalmtitle"    to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "renumber"      to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "renumbertitle" to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "mergedverse*"  to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "mergedverse"   to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "mergedverse*"  to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "mergedverse"   to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true),
//      "mergedverse*"  to ActionDescriptor(involvesAMapping = true, canCreateTargetIfNecessary = true))
//    }
//  }
//
//
//
//
//
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**        Public -- for use only within reversification processing        **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  /**
//   * The reversification data uses its own formats for references.  It's
//   * convenient to convert this to USX for processing.
//   *
//   * @param theStepRef Reference in STEP format.
//   * @return String representation of an equivalent reference in USX format.
//   *         Note that this may differ from the original in respect of
//   *         whitespace, separators, etc, but since we need it only for
//   *         parsing, that's not an issue.
//   */
//
//  fun usxifyFromStepFormat (theStepRef: String): String
//  {
//    /**************************************************************************/
//    //Dbg.d(theStepRef)
//
//
//
//    /**************************************************************************/
//    /* Get the reference string into canonical form.  The input may contain
//       commas or semicolons as collection separators, and since the parsing
//       processing is set up to handle either, it's convenient here to convert
//       them all to just one form. */
//
//    val stepRef = theStepRef.replace(",", ";")
//                            .replace("--", "-")
//                            .replace("–", "-")
//                            .replace(" +", "")
//                            .replace("•", "") // \u2022 -- Arabic zero.
//                            .replace("٠", "") // \u0660 -- Bullet point, used in some places instead of Arabic zero.
//                            .replace("([1-9A-Za-z][A-Za-z][A-Za-z]\\.)".toRegex()) { it.value.replace(".", " ") }
//                            .replace("(?i)title".toRegex(), "title")
//                            .replace("(?i):T$".toRegex(), "")// We have things like 53:T as the alternative reference on some Psalm rows.  I change these back to be chapter references.
//
//
//    /**************************************************************************/
//    fun processCollectionElement (elt: String) = elt.split("-").joinToString("-"){ usxify_1(it) }
//    return stepRef.split(";").joinToString(";"){ processCollectionElement(it) }
//  }
//
//
//  /****************************************************************************/
//  /* The reversification data has its own view of how references should be
//     represented, and to save having to include specialised code to cater for
//     these, it's convenient to convert to USX format up-front. */
//
//  private fun usxify_1 (theStepRef: String): String
//  {
//    /**************************************************************************/
//    /* Replace the full stop after the book name with a space. */
//
//    var stepRef = theStepRef
//    if (stepRef.matches("...\\..*".toRegex()))
//      stepRef = stepRef.substring(0, 3) + " " + stepRef.substring(4)
//
//
//
//    /**************************************************************************/
//    /* I _think_ we can forget subverse zero.  Otherwise, if we have numeric
//       subverses, change them to alphabetic. */
//
//    if (stepRef.endsWith(".0"))
//      stepRef = stepRef.substring(0, stepRef.length - 2)
//    else if (stepRef.matches(".*\\.\\d+$".toRegex()))
//    {
//      val ix = stepRef.lastIndexOf(".")
//      val subverseNo = Integer.parseInt(stepRef.substring(ix + 1))
//      stepRef = stepRef.substring(0, ix) + convertNumberToRepeatingString(subverseNo, 'a', 'z')
//    }
//
//
//
//    /**************************************************************************/
//    return stepRef
//  }
//
//
//
//
//
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                                 Getters                                **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  /**
//  * Determines whether a given footnote should be output or not.
//  *
//  * With academic modules, we always output footnotes.  With basic modules, we
//  * suppress footnotes on runtime runs.  (This suppression was introduced on
//  * 04-Jun-24; before that, basic modules always included Nec notes.)
//  *
//  * @param row Data row containing the footnote.
//  * @param reversificationType R(untime) or C(onversionTime).
//  * @param reversificationNoteType B(asic) or A(cademic).
//  * @return True if note should be output.
//  */
//
//  fun wantFootnote (row: ReversificationDataRow, reversificationType: Char, reversificationNoteType: Char): Boolean
//  {
//    return when ("$reversificationType$reversificationNoteType")
//    {
//      "CB" -> C_FootnoteLevelNec == row.footnoteLevel // Should not see "C." because conversion-time restructuring is no longer supported at present.
//      "CA" -> true
//
//      "RB" -> C_FootnoteLevelNec == row.footnoteLevel
//      "RA" -> true
//      else -> throw StepExceptionWithStackTraceAbandonRun("outputFootnote: Invalid parameter.")
//    }
//  }
//
//
//  /****************************************************************************/
//  /** Getters -- obtain data from a given data row.
//  *
//  * @param row Data row from which item is to be obtained
//  * @return Item requested.
//  */
//
//  fun getAncientVersions (row: ReversificationDataRow) = row.ancientVersions
//  fun getFootnoteReversification (row: ReversificationDataRow) = getFootnote(row, "Reversification Note")
//  fun getFootnoteVersification (row: ReversificationDataRow) = getFootnote(row, "Versification Note")
//  fun getSourceType (row: ReversificationDataRow) = getField("sourceType", row)
//
//
//
//
//  /****************************************************************************/
//  /**
//  * Getters -- get aggregate details of all books, all reversification rows
//  * of a given kind, etc.  Where possible / sensible, things are returned in
//  * order.  List of book names, for instance, are ordered in Bible order.
//  *
//  * Note 1: getAugmentedReferenceMappings maps source to standard refKey.  If
//  *   the target is a subverse, that's what you get.  If the reversification
//  *   processor has been configured to turn all subverses into their owning
//  *   verse, it's down to that processor to amalgamate things.
//  *
//  * @return Lists of things.
//  */
//
//  fun getAllAcceptedRows (): List<ReversificationDataRow> { return m_SelectedRows }
//  fun getAugmentedReferenceMappings (): Map<RefKey, Pair<RefKey, ReversificationDataRow>> { return m_SelectedRows.filter { 0 != it.processingFlags.and(C_Renumber) }. associate { it.sourceRefAsRefKey to Pair(it.standardRefAsRefKey, it) } } // Note 1 above.
//
//
//  /****************************************************************************/
//  /**
//  * Some Moves or Renumbers target subverse 2 of a verse; and for some (but not
//  * all) of these, there is no corresponding row which targets subverse 1.
//  * For these, the reversification data implicitly assumes that the original
//  * verse itself serves as subverse 1.  Thus, for example, we have something
//  * which targets Num 20:28b, but nothing which targets Num 20:28a.  The
//  * reversification data can be taken as assuming that 20:28 itself serves as
//  * 20:28a, and does not need to be handled during the reversification
//  * processing.
//  *
//  * I need to know about all cases where this is the case, because during
//  * validation I need to know what source text fed into a particular
//  * standard verse.
//  *
//  * This method returns a set of all the subverse 2 refKeys where this is the
//  * case.
//  *
//  * @return Set of refKeys as described above.
//  */
//
//  fun getImplicitRenumbers (): Set<RefKey>
//  {
//    val moveOrRenumberFlag = C_Move.or(C_Renumber)
//    val standardRefs = m_SelectedRows.map { it.standardRefAsRefKey }.toSet()
//    return m_SelectedRows.asSequence()
//                         .filter { 2 == Ref.getS(it.standardRefAsRefKey) }
//                         .filter { Ref.setS(it.standardRefAsRefKey, 1) !in standardRefs }
//                         .filter { 0 != it.processingFlags.and(moveOrRenumberFlag) }
//                         .map { it.standardRefAsRefKey }
//                         .toSet()
//  }
//
//
//  /****************************************************************************/
//  /**
//  * Returns information needed by our own version of osis2mod and JSword to
//  * handle the situation where we have a Bible which is not NRSV(A)-compliant,
//  * and which we are not restructuring during the conversion process.  By *not*
//  * restructuring, we are able to ensure that when displayed stand-alone, the
//  * text is in it's 'natural' form; but we then need this additional information
//  * so that the necessary modifications can be made on-the-fly to align it with
//  * NRSV(A) when using added-value features such as interlinear display.
//  *
//  * The return value is a list of RefKey -> RefKey pairs, mapping source verse to
//  * standard verse.
//  *
//  * We set out from the collection of all selected reversification rows -- ie all
//  * those rows whose source verse exists (where this is required -- it isn't
//  * required on things like, for instance, EmptyVerse), and whose rule passes.
//  * Starting from this:
//  *
//  * - KeepVerse rows are included only if marked as Nec.
//  *
//  * - IfEmpty, IfAbsent and EmptyVerse rows are excluded if their target
//  *   verse already exists.
//  *
//  * - Everything else is included.
//  *
//  * - With PsalmTitle rows (ie rows which target the canonical title of the
//  *   standard ref), the target verse number is given as zero.
//  *
//  * There is a further wrinkle, in that with MergedVerse and EmptyVerse rows,
//  * the source has to be shown as a subverse of the source verse as it appears
//  * in the reversification data.  This will start from 'b' if there is also a
//  * Move or Renumber for the same source verse, and from 'a' otherwise.
//  *
//  * @return List of mappings, ordered by source RefKey.
//  */
//
//  fun getRuntimeReversificationMappings (): List<Pair<RefKey, RefKey>>
//  {
//    /**************************************************************************/
//    fun filterFun (row: ReversificationDataRow): Boolean
//    {
//        return if ("allbibles".equals(getFieldOriginal("SourceType", row), ignoreCase = true))
//          false
//        else when (row.action)
//        {
//          "keepverse" -> C_FootnoteLevelNec == row.footnoteLevel
//          "ifempty", "ifabsent", "emptyverse" -> !InternalOsisDataCollection.getBibleStructure().thingExists(row.standardRef)
//          else -> true
//        }
//    }
//
//
//
//    /**************************************************************************/
////    fun filterFun (row: ReversificationDataRow): Boolean
////    {
////      return when (row.action)
////      {
////        "emptyverse" -> true
////        "keepverse" -> true
////        "keepverse*" -> true
////        "mergedverse" -> true
////        "mergedverse*" -> true
////        "missingverse" -> true
////        "missingverse*" -> true
////        "psalmtitle" -> true
////        "renumber" -> true
////        "renumbertitle" -> true
////        "renumberverse" -> true
////        "renumberverse*" -> true
////        else -> throw StepExceptionWithStackTraceAbandonRun("Invalid row.action: ${row.action}.")
////      }
////    }
//
//
//
//    /**************************************************************************/
//    return getAllAcceptedRows()
//      .filter(::filterFun)
//      .map {
//        if ("psalmtitle" == it.action)
//          Pair(it.sourceRefAsRefKey, Ref.setV(it.standardRefAsRefKey, 0))
//        else
//          Pair(it.sourceRefAsRefKey, it.standardRefAsRefKey)
//      }
//      .sortedBy { it.first }
//    }
//
//
//
//
//
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                               Data load                                **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  /**
//  * Does what it says on the tin.
//  */
//
//  fun process (dataCollection: X_DataCollection)
//  {
//    Dbg.withReportProgressSub("Reading reversification data.  (This contains data needed even if not reversifying.)") {
//      doIt(dataCollection)
//    }
//  }
//
//
//  /****************************************************************************/
//  /* Locates the relevant data within the input file and then reads it in and
//     arranges to parse and filter it. */
//
//  private fun doIt (dataCollection: X_DataCollection)
//  {
//    /**************************************************************************/
//    m_DataCollection = dataCollection
//    m_BibleStructure = dataCollection.getBibleStructure()
//    m_RuleEvaluator = ReversificationRuleEvaluator(dataCollection)
//
//
//
//    /**************************************************************************/
//    val dataLocation = ConfigData["stepExternalDataPath_ReversificationData"]!!
//    if (!dataLocation.startsWith("http")) Logger.warning("Running with local copy of reversification data.")
//    val rawData = (if (dataLocation.contains("http")) URL(dataLocation).readText() else File(dataLocation).readText()).split("\n")
//
//
//
//    /**************************************************************************/
//    val ixLow = findLine(rawData, "#DataStart(Expanded)", 0)
//    val ixHigh = findLine(rawData, "#DataEnd", ixLow)
//    val filteredData = rawData.subList(ixLow + 1, ixHigh).map { it.trim() }.filterNot { it.startsWith('#') || it.isBlank() || it.startsWith('=') || it.startsWith('\'')  }
//    Rpt.reportWithContinuation(level = 1, "Parsing reversification data (total of ${filteredData.size} rows) ...") { // Report progress in the same way as when processing books.
//      var rowNumber = 0
//      filteredData.forEach { loadRow(it, ++rowNumber) }
//      //$$$Dbg.displayReversificationRows(getAllAcceptedRows())
//      Logger.announceAllAndTerminateImmediatelyIfErrors()
//      aggregateData()
//      debugOutputDebugData()
//    }
//  }
//
//
//
//  /****************************************************************************/
//  /* Looks for a row containing a given piece of text.  Used to identify where
//     the reversification data of interest to us starts and ends within the
//     overall text.
//  */
//
//  private fun findLine (data: List<String>, lookFor: String, startAt: Int): Int
//  {
//    val lookForLower = lookFor.lowercase()
//    for (i in startAt..< data.size)
//      if (data[i].lowercase().startsWith(lookForLower)) return i
//
//    throw StepExceptionWithStackTraceAbandonRun("Guard row $lookFor missing from reversification data")
//  }
//
//
//
//
//
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                                Parsing                                 **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  /* Adds details of a single row, assuming it passes the relevant tests. */
//
//  private fun loadRow (rawData: String, rowNumber: Int)
//  {
//    /**************************************************************************/
//    //Dbg.d(rowNumber)
//    //Dbg.d(rawData)
//    //Dbg.dCont(rawData, "Gen.32:33")
//
//
//
//    /**************************************************************************/
//    if (rowNumber == 1000 * (rowNumber / 1000))
//      Rpt.reportAsContinuation(rowNumber.toString()) // Want the progress output, but don't want to run anything under control of withProcessingBook.
//
//
//
//    /**************************************************************************/
//    val fields = rawData.split("\t").map { it.trim() }.toMutableList()
//    val dataRow = ReversificationDataRow(this, rowNumber)
//    dataRow.fields = fields
//    dataRow.originalFields = fields.map { it } // We want a _copy_.
//
//
//
//    /**************************************************************************/
//    if (m_Headers.isEmpty())
//    {
//      var n = -1
//      fields.forEach { m_Headers[it] = ++n }
//      return
//    }
//
//
//    /**************************************************************************/
//    if (ignoreRow(dataRow))
//      return
//
//
//
//    /**************************************************************************/
//    canonicaliseAndCorrectData(dataRow)
//
//
//
//    /**************************************************************************/
//    val (processedRow, accepted) = convertToProcessedForm(dataRow)
//    if (accepted)
//      m_SelectedRows.add(processedRow)
//
//    debugRecordReversificationRow(accepted, processedRow)
//  }
//
//
//  /****************************************************************************/
//  /* There are some inconsistencies / infelicities in the reversification data,
//     and processing is easier if these are sorted out. */
//
//  private fun canonicaliseAndCorrectData (dataRow: ReversificationDataRow)
//  {
//    /**************************************************************************/
//    /* Some rows are marked IfEmpty or IfAbsent in the SourceType column.  I
//       think that's wrong -- these should be moved to the Action column, and
//       the SourceType should be set to AllBibles.  (These also need
//       special processing: normally we demand that the source verse be present
//       before we accept a reversification row for processing.  But these
//       particular rows at present are intended to create the standard verse
//       if necessary -- and yet in all cases, the source and standard verse
//       are the same in the present reversification data.  Clearly,
//       therefore, we have to ignore the normal requirement that the
//       source verse must exist.) */
//
//    val sourceType = getField("SourceType",  dataRow)
//    if ("IfAbsent".equals(sourceType, ignoreCase = true))
//    {
//      setField(dataRow, "Action", "ifabsent")
//      setField(dataRow, "SourceType", "AllBibles")
//    }
//    else if ("IfEmpty".equals(sourceType, ignoreCase = true))
//    {
//      setField(dataRow, "Action", "ifempty")
//      setField(dataRow, "SourceType", "AllBibles")
//    }
//
//
//
//    /**************************************************************************/
//    /* Convert the action field to camelback with uppercase first letter. */
//
//    var action = getField("action", dataRow)
//    action = action.split("\\s+".toRegex()).joinToString(""){ it.substring(0, 1).uppercase() + it.substring(1).lowercase() }
//
//
//
//    /**************************************************************************/
//    /* Prettify the SourceType. */
//
//    setField(dataRow, "SourceType", getField("SourceType", dataRow).replace("EngTitle", "EnglishTitle"))
//
//
//
//    /**************************************************************************/
//    /* There are several parts to sorting out the references.  First step is to
//       convert them to USX form (unfortunately they aren't in that form in the
//       reversification data). */
//
//    var sourceRef   = usxifyFromStepFormat(getField("SourceRef", dataRow))
//    var standardRef = usxifyFromStepFormat(getField("StandardRef", dataRow))
//
//
//
//    /**************************************************************************/
//    /* The Action field contains an asterisk where verses are being moved.  Or
//       it does in theory.  In practice, it seems to contain the asterisk in a
//       few places where verses are being renumbered in situ or are not being
//       renumbered or moved at all.  Get rid of these superfluous asterisks. */
//
//    if (sourceRef == standardRef)
//      action = action.replace("*", "")
//    else if ("renumberverse" !in action)
//      action = action.replace("*", "")
//
//
//
//    /**************************************************************************/
//    /* If either sourceRef or standardRef refer to subverse zero, the subverse
//       can be dropped -- USX doesn't recognise subverse zero, so we won't have
//       anything marked as such in the input (it will just be the verse), and
//       we don't want to create anything like that in the output. */
//
//    if (sourceRef.endsWith(".0"))
//      sourceRef = sourceRef.replace(".0", "")
//
//    if (standardRef.endsWith(".0"))
//      standardRef = standardRef.replace(".0", "")
//
//
//
//    /**************************************************************************/
//    /* It is rather convenient to treat anything involving a title as actually
//       involving a verse.  I distinguish this verse by giving it a special verse
//       number (499 at the time of writing).
//
//       One downside to using this is that if I fabricate a canonical title,
//       it's going to come out right at the end of the chapter, so I need to
//       move it back when I've finished processing.  From that point of view,
//       using v0 as the special marker would be better.  But the problem is that
//       everything is set up to believe that a reference whose verse number is
//       zero is a chapter reference, and that makes life very difficult.
//
//       Of course, this will only work if I pre-process the text to change any
//       existing canonical titles to be of this same form (and if I change them
//       back again before going on to generate the OSIS).  Clearly this is a
//       complication, both in terms of having to do it, and also in terms of the
//       confusion it's likely to cause when it comes to maintaining the code.  I
//       _think_ it's worth it, though. */
//
//    if (standardRef.contains("title"))
//    {
//      standardRef = standardRef.replace("title", RefBase.C_TitlePseudoVerseNumber.toString())
//      if (!sourceRef.contains("title")) // If we're using verses to create the title, make it appear they are creating subverses of the special verse.
//      {
//        val sourceVerse = Ref.rdUsx(sourceRef).getV()
//        standardRef += convertNumberToRepeatingString(sourceVerse, 'a', 'z')
//      }
//    }
//
//    if (sourceRef.contains("title"))
//      sourceRef = sourceRef.replace("title", RefBase.C_TitlePseudoVerseNumber.toString())
//
//
//
//    /**************************************************************************/
//    setField(dataRow, "Action", action)
//    setField(dataRow, "SourceRef", sourceRef)
//    setField(dataRow, "StandardRef", standardRef)
//   }
//
//
//  /****************************************************************************/
//  /* Identifies rows which should not be processed. */
//
//  private fun ignoreRow (row: ReversificationDataRow): Boolean
//  {
//    /**************************************************************************/
//    /* The reversification data contains a few rows for 4Esdras.  We need to
//       weed these out, because the USX scheme doesn't recognise this book.
//       Don't be tempted to filter these out earlier on in the processing,
//       because we need them still to be recognised in the row-number setting. */
//
//    val sourceRef = getField("SourceRef", row)
//    if (sourceRef.contains("4es", ignoreCase = true))
//      return true
//
//
//
//    /**************************************************************************/
//    /* For debug purposes it is often convenient to process only a few books.
//       We need to ignore reversification data for any where the source
//      reference is for a book which we are not processing. */
//
//    if (!Dbg.wantToProcessBookByAbbreviatedName(sourceRef.substring(0, 3))) return true
//
//
//
//    /**************************************************************************/
//    return false
//  }
//
//
//  /****************************************************************************/
//  /* Canonicalises the contents of a single row and adds it to the various
//     collections if it passes any applicability tests.  Note that by the
//     time we get here, we can be sure there are no blank rows or comment rows
//     in the data.
//
//     You may observe that there are a number of points within the processing
//     where it becomes apparent that the row being processed does not apply to
//     the present text, and from an efficiency point of view it would make sense
//     to abandon further processing at that point.  However, it is convenient to
//     stick with it to the end, because the processed form of the row may be
//     useful for debugging purposes. */
//
//  private fun convertToProcessedForm (dataRow: ReversificationDataRow): Pair<ReversificationDataRow, Boolean>
//  {
//    /**************************************************************************/
//    //Dbg.d(5406 == dataRow.rowNumber)
//
//
//
//    /**************************************************************************/
//    var accepted = false
//    dataRow.action = getField("Action", dataRow).replace(" ", "").lowercase()
//    dataRow.sourceRef   = Ref.rdUsx(getField("SourceRef", dataRow))
//    dataRow.standardRef = Ref.rdUsx(getField("StandardRef", dataRow))
//    dataRow.sourceRefAsRefKey   = dataRow.sourceRef.toRefKey()
//    dataRow.standardRefAsRefKey = dataRow.standardRef.toRefKey()
//
//
//
//    /**************************************************************************/
//    /* It would actually be more efficient to do this earlier on and abandon
//       further processing if it fails.  However, it is convenient to do a fair
//       bit of the processing regardless, because it creates information which
//       may be useful for debugging. */
//
//    val ruleData = getField("Tests", dataRow)
////    val sourceRef = if ("AllBibles" == getField("SourceType", dataRow)) null else dataRow.sourceRef
//    val sourceRef = if ("ifabsent" == getField("Action", dataRow)) null else dataRow.sourceRef // Don't want the source test on rows which are intended to create the source.
//    if (!m_BibleStructure.bookExists(dataRow.sourceRef))
//      accepted = false // Without this, AllBibles rows would turn up even where we don't have the source book, and I don't think that's what we want.
//    else if (m_RuleEvaluator.rulePasses(sourceRef, ruleData, dataRow))
//      accepted = true
//
//
//
//    /**************************************************************************/
//    /* Set up flags to indicate what kind of processing is required. */
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
//
//
//
//
//    /**************************************************************************/
//    setCalloutAndFootnoteLevel(dataRow)
//    setAncientVersions(getField("Ancient Versions", dataRow))
//
//
//
//    /**************************************************************************/
//    return Pair(dataRow, accepted)
//  }
//
//
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
//
//
//  /****************************************************************************/
//  /* New format callout processing.
//
//     In early 2023, the original NoteMarker content was changed.  This method
//     extracts salient information from that field.  The notes below give details
//     of the format and the processing.
//
//     A 'full' NoteMarker field is now of the form :-
//
//       Lvl. (V)^<sourceDetails>
//
//     or, as an example :-
//
//       Acd. (1)^12
//
//     (I say 'full' here because some of the elements are optional.  This is
//     spelt out below.)
//
//     One item of terminology before we proceed.  In the normal course of events,
//     where a verse has been affected by reversification, we include what I have
//     generally referred to as an 'embedded source reference' within the
//     canonical text.  Originally this was output in square brackets, so we might
//     have something like :-
//
//       2) ^ [1:1] This text is now in verse 2, but came from v 1 of ch 1.
//
//     where 2) is the actual verse indicator displayed to the user, and [1:1]
//     indicates that the content originally came from 1:1.
//
//     In most cases, the embedded source reference comprises just the verse
//     number of the source reference, and we have dropped the square brackets in
//     order to make it look more like a verse number, so that the user is more
//     likely to read the text as being numbered in the way that they are
//     familiar with.
//
//     In some cases, though -- where the source verse is in a different chapter
//     or a different book from the standard verse -- we need to include either
//     the chapter or the book and chapter.  And when we have this additional
//     context, we presently try to present it in superscript form, so that it
//     still leaves the verse part looking like a verse number.
//
//     I always try to format this information in vernacular form, incidentally.
//
//
//     The various parts of the new NoteMarker field are as follows :-
//
//     * Lvl controls whether or not a footnote should be generated to explain
//       what reversification is doing (or might do) here.  A value of 'Nec'
//       indicates that the footnote should be generated on _any_ reversification
//       run.  A value of 'Acd' indicates that the footnote should be generated
//       only on academic reversification runs.
//
//     * <oldData> gives the information which appeared in this field previously
//       (ie before the new format data was adopted).  It is being retained just in
//       case, and may be dropped at some point.
//
//     * (V) indicates that this reversification action produces data which should
//       be marked with a <verse> tag, and gives the verse number.  (The remainder
//       of the sid / eid for the verse comes from the book and chapter of the
//       StandardRef.)  It is optional; if absent, the data being handled by this
//       reversification row is not flagged as a verse.
//
//       I do not need to rely upon this.  I can determine whether or not the
//       given text is to be marked with <verse> tags by reference to the
//       StandardRef field.  If that identifies a verse, or identifies subverse
//       zero of a verse, then we want the verse tag; if it identifies a subverse
//       other than subverse zero, we do not want the verse tag.  And the sid /
//       eid is simply the StandardRef.
//
//     * The caret indicates that a footnote callout appears.  The only purpose
//       it serves is to indicate that the footnote callout (assuming Lvl requires
//       a footnote) appears after the start of the verse and before the source
//       details which are set out in the remainder of the NoteMarker field.  And
//       since it appears on every single row in the reversification data, and
//       always in the same position, I do not need to rely upon it.
//
//     * The remainder of the NoteMarker field gives the details upon which the
//       embedded source reference is based.  It is optional.  If it is empty, or
//       comprises just [-], [+] or [0] (which are old options, and may be dropped
//       in future), no embedded source reference is generated.
//
//       Otherwise, I believe I can, in almost all cases, deduce what this portion
//       of the data is telling me from the SourceRef and StandardRef.  Note,
//       though, that this processing needs to be overridden using the data in the
//       NoteMarker field in some places, and if I have to cater for overrides,
//       there may be little advantage in retaining the existing processing
//
//       - If the source and standard ref are identical (as on most / all
//         KeepVerse rows), the embedded source reference is suppressed.
//
//       - If the source and standard ref are in the same book and chapter, the
//         embedded source ref consists of the source verse (or verse and
//         subverse) only.
//
//       - If they are in the same book, but different chapters, the embedded
//         source ref consists of the source chapter and verse.
//
//       - If they are in different books, the embedded source ref consists of
//         the book, chapter and verse (but subject to some special processing
//         discussed below).
//
//       - Where the embedded reference consists of more than just the verse
//         number, the non-verse portion is to be formatted differently from the
//         verse portion (presently by superscripting it).
//
//       - Except that this analysis of both the content and the formatting may be
//         overridden in some places TBD.
//
//
//       I mentioned special processing above.  Where we have blocks of text being
//       moved between books (and _only_ there), having a full embedded source
//       reference at the start of every verse would probably be oppressive.  In
//       these cases, we insert a header at the start of the block of verses
//       giving the source book and chapter, and then change the embedded
//       references to give the verse number only, relying upon the header to
//       provide the context.  Or more accurately, I leave the embedded reference
//       in the _first_ verse in a block as book, chapter and verse, but turn all
//       of the remaining ones into verse only.
//
//       Further, if the result of this means that the embedded reference
//       identifies the same verse number as the verse in which it appears -- if,
//       for example, S3Y 1:1 would contain an embedded reference of '1' -- we
//       suppress the embedded source reference altogether.
//  */
//
//  private const val C_FootnoteLevelNec = 0
//  private const val C_FootnoteLevelAc  = 1
//  private const val C_FootnoteLevelOpt = 2
//
//  private fun setCalloutAndFootnoteLevel (row: ReversificationDataRow)
//  {
//    /**************************************************************************/
//    var x = getField("NoteMarker", row)
//    when (x.trim().substring(0, 2).lowercase())
//    {
//      "ne" -> row.footnoteLevel = C_FootnoteLevelNec // Necessary.
//      "ac" -> row.footnoteLevel = C_FootnoteLevelAc  // Academic.
//      "op" -> row.footnoteLevel = C_FootnoteLevelOpt // Not exactly sure what this means, but it is used in a slightly complicated way.
//      else -> Logger.error("Reversification invalid note level: " + x[0])
//    }
//
//    val ix = x.indexOf(".")
//    x = x.substring(ix + 1)
//
//
//
//    /**************************************************************************/
//    val cd = CalloutDetails()
//    row.calloutDetails = cd
//
//
//
//    /**************************************************************************/
//    var s = x.replace("\\s+".toRegex(), "").replace("^", "") // Don't want spaces or the up-arrow which indicates that the footnote is to be included (it always is).
//    if (s.contains("("))
//    {
//      val xx = s.split("(")[1].split(")")[0]
//      s = s.replace("\\(.+?\\)".toRegex(), "")
//      if (xx[0].lowercase() == "t")
//        cd.standardVerseIsCanonicalTitle = true
//      else
//        cd.standardVerse = Ref.rdUsx(usxifyFromStepFormat(xx), null, "v")
//    }
//
//
//
//    /**************************************************************************/
//    if (s.contains("["))
//    {
//      var xx = s.split("[")[1].split("]")[0]
//      s = s.replace("\\[.+?]".toRegex(), "")
//
//      if (xx.startsWith("+"))
//      {
//        cd.alternativeRefCollectionHasPrefixPlusSign = true
//        xx = xx.substring(1)
//      }
//
//      if (xx.contains("+"))
//      {
//        cd.alternativeRefCollectionHasEmbeddedPlusSign = true
//        xx = xx.replace("+", ",")
//      }
//
//      cd.alternativeRefCollection = RefCollection.rdUsx(usxifyFromStepFormat(xx), null, "v")
//    }
//
//
//
//    /**************************************************************************/
//    if (s.isNotEmpty())
//      cd.sourceVerseCollection = RefCollection.rdUsx(usxifyFromStepFormat(s), null, "v")
//  }
//
//
//
//
//
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
//
//
//
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                        Private -- miscellaneous                        **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  fun getField (key: String, dataRow: ReversificationDataRow): String
//  {
//    val ix = m_Headers[key]!!
//    return if (ix < dataRow.fields.size) dataRow.fields[ix] else ""
//  }
//
//
//  /****************************************************************************/
//  fun getFieldOriginal (key: String, dataRow: ReversificationDataRow): String
//  {
//    val ix = m_Headers[key]!!
//    return if (ix < dataRow.originalFields.size) dataRow.fields[ix] else ""
//  }
//
//
//  /****************************************************************************/
//  /* Returns a fully formatted footnote. */
//
//  private fun getFootnote (row: ReversificationDataRow, selector: String): String
//  {
//    /**************************************************************************/
//    //Dbg.d(row.toString())
//
//
//
//    /**************************************************************************/
//    /* Is there in fact any footnote at all? */
//
//    var content = getField(selector, row)
//    if (content.isEmpty()) return ""
//
//
//
//    /**************************************************************************/
//    var res = ""
//
//
//
//    /**************************************************************************/
//    /* Time was when we would only ever have a single chunk of text, and either
//       zero or one associated reference, which appeared after the text.  This
//       is not longer the case.  We may have one or two chunks of text; and
//       there may or may not be a reference associated with either or both of
//       them, and references may occur either at the front or the end. */
//
//    if (content.endsWith(".%.")) content = content.substring(0, content.length - 3) + "%."
//    val textParts = content.split("%")
//    val refs: MutableList<String> = mutableListOf()
//    val texts: MutableList<String> = mutableListOf()
//    for (i in textParts.indices step 2)
//    {
//      refs.add(textParts[i].trim())
//      texts.add(if (i + 1 < textParts.size) textParts[i + 1] else "")
//    }
//
//    for (i in texts.indices)
//      if (texts[i].isNotEmpty())
//      {
//        val thisChunk: String
//        val x = TranslatableFixedText.lookupText(Language.Vernacular, getTextKey(texts[i]))
//
//        thisChunk = if (x.startsWith("%ref"))
//          refs[i].trim() + " %" + texts[i] + "%"
//        else if (x.contains("%ref"))
//          "%" + texts[i] + "% " + refs[i + 1]
//        else
//          "%" + texts[i] + "%"
//
//        res += " " + getFootnote(thisChunk)
//      }
//
//    return res.trim()
//  }
//
//
//  /*****************************************************************************/
//  /* Support method for the previous method.  This works the way it does (taking
//     a possible concatenation of a text string and a reference) because I've
//     hived it off from an earlier method which was created when footnotes only
//     ever consisted of one string to look up. */
//
//  private fun getFootnote (contentAsSupplied: String): String
//  {
//    /**************************************************************************/
//    /* A change in December 2023 introduced some NoteB footnotes which have
//       a reference at the front, and then '%in some Bibles%' or '%in most Bibles%'.
//       It is convenient to move the reference to the end, to convert these
//       entries to the same form as the footnotes which we have processed
//       previously. */
//
//    var content = contentAsSupplied
//    val contentLowercase = content.lowercase()
//    if ("%in some bibles%" in contentLowercase || "%in most bibles%" in contentLowercase)
//    {
//      val ix = content.indexOf(' ')
//      val ref = content.substring(0, ix).trim()
//      val text = content.substring(ix + 1).trim()
//      content = "$text $ref"
//    }
//
//
//
//    /**************************************************************************/
//    /* All footnote entries have a leading %, and sometimes (but not
//       consistently) they have a trailing full stop.  We don't want either of
//       these. */
//
//    content = content.substring(1) // Get rid of leading %.
//    if ('.' == content.last()) content = content.substring(0, content.length - 1)
//
//
//
//    /**************************************************************************/
//    /* What we are now left with is the message text, with its end marked with
//       %, and then optionally a reference.  We split at the %-sign, having
//       first appended \u0001 (which subsequently we get rid of again) to ensure
//       that split always returns two elements. */
//
//    content += "\u0001" // Ensure split always gives two elements.
//    val bits = content.split("%")
//    val key = getTextKey(bits[0])
//
//
//
//    /**************************************************************************/
//    /* If the footnote has no associated reference, we can simply return the
//       text itself. */
//
//    if (1 == bits[1].length)
//      return TranslatableFixedText.stringFormat(Language.Vernacular, key)
//
//
//
//    /**************************************************************************/
//    /* In most cases, sorting out the reference collection is easy -- there may
//       in theory be some ambiguity with single numbers as to what they represent
//       (chapters, verses, etc), but we force that here by saying that unadorned
//       numbers should be regarded as verses (which, in fact, I think unadorned
//       numbers actually are); and in any case, the aim is simply to output
//       stuff in the same form as it appears in the reversification data.
//
//       The fly in the ointment are the few rows which contain multiple
//       references which are separated by things like '/' and '+', and therefore
//       can't be parsed as collections.  We'll deal with the easy cases first
//       (the ones where we don't have these odd separators. */
//
//    var refAsString = bits[1].replace("\u0001", "").trim()
//    val containsSlash = '/' in refAsString
//    val containsPlus = '+' in refAsString
//    if (!containsSlash && !containsPlus)
//    {
//      if (refAsString.endsWith("."))
//        refAsString = refAsString.substring(0, refAsString.length - 1)
//      val rc = RefCollection.rdUsx(usxifyFromStepFormat(refAsString), dflt = null, resolveAmbiguitiesAs = "v")
//      return TranslatableFixedText.stringFormat(Language.Vernacular, key, rc)
//    }
//
//
//
//    /**************************************************************************/
//    /* Which just leaves the difficult case.  Unfortunately, there is at the
//       time of writing just one row where the reference looks like 9:9a/2:35f,
//       and of course the slash is a problem, because this cannot be parsed as a
//       reference collection.  If I'm to have any chance of doing this in such
//       a way that I can continue to support vernacular translation, this is
//       going to be unpleasantly fiddly ...
//
//       I start off by obtaining the basic text of the message in vernacular
//       form.  This should have precisely one entry of the form %RefV<...>.
//       I split this text up into that portion which appears the %RefV<...> bit,
//       the %RefV<...> bit itself, and the portion which appears afterwards.
//
//       I then use the %RefV<...> portion to format each of the references
//       individually.  And then finally I join these formatted references
//       together with the relevant separator, and stitch this together with the
//       fixed portions of the text.
//    */
//
//    val rawMessage = TranslatableFixedText.lookupText(Language.English, getTextKey(bits[0].trim()))
//    val regex = "(?i)(?<pre>.*)(?<ref>%Ref.*?>)(?<post>.*)".toRegex()
//    val match = regex.matchEntire(rawMessage)
//    val refFormat = match!!.groups["ref"]!!.value
//
//    val elts = refAsString.split('/', '+').map { TranslatableFixedText.stringFormat(refFormat, RefCollection.rdUsx(it.trim(), dflt = null, resolveAmbiguitiesAs = "v")) }
//    val eltsAssembled = elts.joinToString(TranslatableFixedText.stringFormat(Language.Vernacular, if (containsSlash) "V_reversification_ancientVersionsAlternativeRefsSeparator" else "V_reversification_alternativeReferenceEmbeddedPlusSign"))
//    return match.groups["pre"]!!.value + eltsAssembled + match.groups["post"]!!.value
//  }
//
//
//  /****************************************************************************/
//  /**
//   *  Given a piece of footnote text from the reversification data, gives back
//   *  the corresponding key which we can use to look up TranslatableFixedText.
//   */
//
//  private fun getTextKey (lookupVal: String) = "V_reversification_[${lookupVal.trim()}]"
//
//  private val m_Headers: MutableMap<String, Int>  = TreeMap(String.CASE_INSENSITIVE_ORDER) // Headers of reversification data file.
//
//
//  /****************************************************************************/
//  private fun setField (dataRow: ReversificationDataRow, key: String, value: String)
//  {
//    val ix = m_Headers[key]!!
//    dataRow.fields[ix] = value
//  }
//
//
//
//
//
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                      Private -- Ancient versions                       **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  // $$$ I have not revamped this, but have no reason to believe it needs changing.
//  /****************************************************************************/
//  /* You get one of these per tradition in the AncientVersions information. */
//
//  internal class AncientVersionsTradition (theContent: String)
//  {
//    /**************************************************************************/
//    override fun toString (): String
//    {
//      /************************************************************************/
//      /* This first block deals with the main elements associated with a given
//         tradition.  For example, the bit after the equals sign in
//
//           Greek=12:18 / 12:17b+13:1a
//
//         A slash is used quite commonly here to separate alternatives.  The
//         above example is atypical, in that it also uses a plus-sign as a
//         delimiter.
//
//         The processing below is revoltingly messy, and even then is perhaps
//         not as flexible as other aspects of the vernacular-related formatting,
//         in that it assumes that a multi-element collection will always be
//         shown in the same order as in the reversification data, and that it
//         will be separated by delimiters in the same way as in the
//         reversification data. */
//
//      fun processMainElementRefCollection (refAsString: String): String
//      {
//        return if ("--" == refAsString)
//          TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsNoReference")
//        else
//          TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsMainRefFormat", RefCollection.rd(usxifyFromStepFormat(refAsString), null,"v"))
//      }
//
//      fun processMainElementDelimiter (delim: String): String
//      {
//        return when (delim)
//        {
//          "/" -> TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsAlternativeRefsSeparator")
//          "+" -> TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsJointRefsSeparator")
//          else -> throw StepExceptionWithStackTraceAbandonRun("AncientVersions delimiter not handled: $delim")
//        }
//      }
//
//      val delimitedMainElementsElements = m_MainElements.split("((?=^)|(?<=^))".replace("^", "[/+]").toRegex()).toMutableList() // Split with the delimiters shown in square brackets, and retain the delimiters.
//      delimitedMainElementsElements.forEachIndexed { ix, content -> if ((ix and 1) == 0) delimitedMainElementsElements[ix] = processMainElementRefCollection(content) }
//      delimitedMainElementsElements.forEachIndexed { ix, content -> if ((ix and 1) == 1) delimitedMainElementsElements[ix] = processMainElementDelimiter(content) }
//      val mainEltsAsString = delimitedMainElementsElements.joinToString("")
//
//
//
//      /************************************************************************/
//      /* This section caters for anything within square brackets. */
//
//      var equivalenceInformation = ""
//      if (null != m_EquivalenceInformationReferenceCollection)
//        equivalenceInformation = " " + TranslatableFixedText.stringFormat(m_EquivalenceInformationFormatString, m_EquivalenceInformationReferenceCollection!!)
//
//
//
//      /************************************************************************/
//      val tradition: String = TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_language$m_Tradition")
//      return TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsTraditionFormat", "tradition", tradition, "main", mainEltsAsString, "equivalenceInformation", equivalenceInformation)
//    }
//
//
//    /**************************************************************************/
//    private var m_EquivalenceInformationReferenceCollection: RefCollection? = null
//    private var m_EquivalenceInformationFormatString = ""
//    private var m_MainElements = ""
//    private var m_Tradition = ""
//
//
//    /**************************************************************************/
//    init
//    {
//       /************************************************************************/
//       //Dbg.dCont(theContent, "/")
//
//
//
//      /************************************************************************/
//      /* Get rid of spaces, which only serve to complicate things and aren't
//         of use to the processing. */
//
//      var content = theContent.replace(" ", "")
//
//
//
//      /************************************************************************/
//      /* Split off the tradition. */
//
//       var ix = content.indexOf("=")
//       m_Tradition = content.substring(0, ix)
//       content = content.substring(ix + 1)
//
//
//
//      /************************************************************************/
//      /* Remove any trailing bullet point.  Bullet points act as separators
//         between traditions.  Ideally I'd have split traditions out by splitting
//         at these separators, but unfortunately it looks as though in some cases
//         a bullet point has been used to mark subverse zero.  (It _shouldn't_
//         have been -- DIB has been using an Arabic Arabic numeral zero to mark
//         subverse zero.  However, the two characters look almost identical, so
//         it's easy to use the wrong one.) */
//
//      if (content.last() == '\u2022') // Bullet point.
//        content = content.substring(0, content.length - 1)
//
//
//
//      /************************************************************************/
//      /* Split off any trailing equivalence information (ie the bit in square
//         brackets). */
//
//      var equivalenceInformation: String? = null
//      ix = content.indexOf("[")
//      if (ix >= 0)
//      {
//        equivalenceInformation = content.substring(ix + 1).replace("]", "")
//        content = content.substring(0, ix)
//      }
//
//
//
//      /************************************************************************/
//      m_MainElements = content
//
//
//
//      /************************************************************************/
//      /* If we have equivalence information, turn it into an appropriate
//         collection.  Life is made a bit easier here by the fact that we don't
//         need to worry about slashes and double-dashes in the equivalence
//         information, because there aren't any -- they're all pukka
//         references. */
//
//      if (null != equivalenceInformation)
//      {
//        when (equivalenceInformation!!.first())
//        {
//          '+' ->
//           {
//              m_EquivalenceInformationFormatString = TranslatableFixedText.lookupText(Language.Vernacular, "V_reversification_ancientVersionsEquivalencePlus")
//              equivalenceInformation = equivalenceInformation!!.substring(1)
//           }
//
//           '=' ->
//            {
//              m_EquivalenceInformationFormatString = TranslatableFixedText.lookupText(Language.Vernacular, "V_reversification_ancientVersionsEquivalenceEquals")
//              equivalenceInformation = equivalenceInformation!!.substring(1)
//            }
//
//            else ->
//            {
//              m_EquivalenceInformationFormatString = TranslatableFixedText.lookupText(Language.Vernacular, "V_reversification_ancientVersionsEquivalenceUndecorated")
//              equivalenceInformation = equivalenceInformation!!.substring(1)
//           }
//        }
//
//        m_EquivalenceInformationReferenceCollection = RefCollection.rdUsx(usxifyFromStepFormat(equivalenceInformation!!))
//      }
//    }
//  }
//
//
//  /****************************************************************************/
//  /**
//   * Converts the ancient version information into a vernacular string
//   * containing vernacular references as far as this is possible.
//   *
//   * @return Ancient version information.
//   */
//
//   private fun ancientVersionsToString (traditions: List<AncientVersionsTradition>): String
//   {
//    val resElements: MutableList<String> = ArrayList()
//    traditions.forEach { x -> resElements.add(x.toString()) }
//    if (resElements.isEmpty()) return ""
//
//    var res = resElements.joinToString(TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsTraditionSeparator"))
//    res = TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersions", res)
//    return res
//  }
//
//
//  /****************************************************************************/
//  /* Converts the raw AncientVersion information into string format.
//
//     Ancient version information looks like ...
//
//       ( Hebrew=1:1 | Greek=3:16 | ... )   ['|' = bullet point]
//
//     The references are complicated ...
//
//     * They may be a simple reference, a reference range, or a collection.
//       All of these are in STEP format with alphabetic subverses.
//
//     * Or they may be a list of alternatives, separated by ' / ', each
//       element itself being a reference, a range or a collection.
//
//     * Or they may consist of --, indicating that there _is_ no reference.
//
//     * And they may contain additional information in square brackets --
//       information which may itself start with an equals sign or a plus sign
//       (or with neither), the significance which I am currently unaware of.
//
//     * The processing here splits these various bits out into
//       AncientVersionsChunks, one per tradition.
//   */
//
//  private fun setAncientVersions (theText: String?): String
//  {
//    /**************************************************************************/
//    //Dbg.dCont(theText ?: "", "(Greek=1:14 / 1:14a)")
//
//
//
//    /**************************************************************************/
//    if (theText.isNullOrEmpty()) return ""
//
//
//
//    /**************************************************************************/
//    val traditions: MutableList<AncientVersionsTradition> = ArrayList()
//
//
//
//    /**************************************************************************/
//    var text = theText.substring(0, theText.length - 1).substring(1) // Get rid of the enclosing parens.
//
//
//
//    /**************************************************************************/
//    /* Some entries have things with equals signs in square brackets to
//       indicate that the reference list is in some manner similar to another
//       alternative.  This would get in the way of the processing below, so
//       temporarily I replace the equals sign. */
//
//    text = text.replace("[=", "[\u0001")
//
//
//
//    /**************************************************************************/
//    val languageTraditions = listOf(*text.split("(?=\\b(Hebrew=|Latin=|Greek=))".toRegex()).toTypedArray())
//    languageTraditions.subList(1, languageTraditions.size).forEach { traditions.add(AncientVersionsTradition(it)) }
//    return ancientVersionsToString(traditions)
//  }
//
//
//
//
//
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                        Private -- debug support                        **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  private class DebugData
//  {
//     var  m_RowsAccepted: MutableList<ReversificationDataRow> = ArrayList() // All of the rows accepted.
//     var  m_RowsAcceptedButShouldNotHaveBeen: MutableList<ReversificationDataRow> = ArrayList()
//     var  m_RowsRejectedButShouldNotHaveBeen: MutableList<ReversificationDataRow> = ArrayList()
//  }
//
//  private  val  m_DebugData = DebugData()
//
//
//  /****************************************************************************/
//  private fun debugOutputDebugData ()
//  {
//    if (null == m_AnticipatedSourceType) return
//    m_DebugData.m_RowsAcceptedButShouldNotHaveBeen.forEach { x -> Logger.info("Reversification row accepted but perhaps should not have been: $x") }
//    m_DebugData.m_RowsRejectedButShouldNotHaveBeen.forEach { x -> Logger.info("Reversification row rejected but perhaps should not have been: $x") }
//    m_DebugData.m_RowsAccepted.forEach { x -> Logger.info("Reversification row accepted as anticipated: $x") }
//  }
//
//
//  /****************************************************************************/
//  /* stepDbgReversificationAnticipatedSourceType, if defined at all, should
//     have one of the values English, Hebrew, Latin or Greek.
//
//     The processing then expects rows whose SourceType contains the relevant
//     string (case-insensitive) to be selected for processing, while others
//     are not, and will output messages to that effect.  This isn't _quite_
//     right (some rows are for specific variants of these like Latin2), but
//     it will at least give us something at least vaguely useful for
//     checking.
//  */
//
//  private var m_AnticipatedSourceType : String? = ""
//
//
//  /****************************************************************************/
//  /* For debugging -- records details of which rows were accepted and
//     rejected. */
//
//  private fun debugRecordReversificationRow (rowAccepted: Boolean, row: ReversificationDataRow)
//  {
//    /**************************************************************************/
//    if (null == m_AnticipatedSourceType) return
//
//    if (m_AnticipatedSourceType!!.isEmpty())
//    {
//      m_AnticipatedSourceType = ConfigData["stepDbgReversificationAnticipatedSourceType"]
//      if (null == m_AnticipatedSourceType) return
//      m_AnticipatedSourceType = m_AnticipatedSourceType!!.lowercase()
//    }
//
//
//
//    /**************************************************************************/
//    /* Ignore lines where the source ref relates to a book we don't have. */
//
//   if (!m_DataCollection.getBibleStructure().bookExists(row.sourceRef.getB())) return
//
//
//
//    /**************************************************************************/
//    val sourceType = getField("SourceType", row).lowercase()
//    val containsAnticipatedSource = sourceType.contains(m_AnticipatedSourceType!!) || sourceType.isEmpty()
//
//
//
//    /**************************************************************************/
//    if (rowAccepted)
//    {
//      m_DebugData.m_RowsAccepted.add(row)
//      if (!containsAnticipatedSource)
//        m_DebugData.m_RowsAcceptedButShouldNotHaveBeen.add(row)
//    }
//    else
//    {
//      if (containsAnticipatedSource && Dbg.wantToProcessBook(row.sourceRef.getB()) )
//        m_DebugData.m_RowsRejectedButShouldNotHaveBeen.add(row)
//    }
//  }
//
//
//
//
//
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                                Private                                 **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  private lateinit var m_BibleStructure: BibleStructure
//  private lateinit var m_DataCollection: X_DataCollection
//
//
//  /****************************************************************************/
//  /* Repertoire of atomic actions.  I do not cater for flagging situations where
//     things should be annotated, because I assume that circumstances
//     permitting, _all_ should. */
//
//  private const val C_Move                             = 0x0001
//          const val C_Renumber                         = 0x0002
//          const val C_CreateIfNecessary                = 0x0004
//  private const val C_ComplainIfStandardRefExisted     = 0x0008
//          const val C_ComplainIfStandardRefDidNotExist = 0x0010
//          const val C_SourceIsPsalmTitle               = 0x0020
//          const val C_StandardIsPsalmTitle             = 0x0040
//
//
//  /****************************************************************************/
//  private lateinit var m_RuleEvaluator: ReversificationRuleEvaluator
//
//
//
//
//
//
//
//
//
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**              Used only by conversion-time reversification              **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  /* Conversion-time reversification entails physically restructuring a text so
//     that it conforms to NRSVA.  At one time this was the only option we
//     supported by way of reversification.  Since then we have implemented
//     samification, under which we do not restructure the text at all during
//     conversion processing.  It is unlikely that we will wish to revert to
//     conversion-time reversification, but I have been asked to retain the code
//     just in case.  Conversion-time reversification relies on the present class
//     creating various supporting structures.  I have gathered together all of the
//     relevant code here.  If you are tasked with maintaining this class, you can
//     probably ignore everything which follows.  (As it stands, conversion-time
//     reversification probably doesn't work anyway -- the converter has changes a
//     lot since we last had cause to use it.) */
//
//

//}

///******************************************************************************/
///**
// * The reversification data contains some things which aren't really
// * reversification data at all -- they are things which record the fact that
// * certain verses are very commonly absent or empty, and which give a
// * footnote to be applied to such verses if they are, indeed, absent or empty
// * in a given text.  This present class deals with the ifAbsent verses.
// *
// * @author ARA "Jamie" Jamieson
// */
//
//object AnticipatedIfAbsentDetails {
//  fun getMap () = m_Map
//  fun getFootnote (refKey: RefKey) = m_Map[refKey]
//  private val m_Map: MutableMap<RefKey, String> = mutableMapOf()
//}
//
//
//
//
//
///******************************************************************************/
///**
// * The reversification data contains some things which aren't really
// * reversification data at all -- they are things which record the fact that
// * certain verses are very commonly absent or empty, and which give a
// * footnote to be applied to such verses if they are, indeed, absent or empty
// * in a given text.  This present class deals with the ifEmpty verses.
// *
// * @author ARA "Jamie" Jamieson
// */
//
//object AnticipatedIfEmptyDetails {
//  fun getMap () = m_Map
//  fun getFootnote (refKey: RefKey) = m_Map[refKey]
//  private val m_Map: MutableMap<RefKey, String> = mutableMapOf()
//}
//
//
//
//
//
///******************************************************************************/
///**
// * Data parsed out of the rather complicated NoteMarker field of the
// * reversification data.
// *
// * @author ARA "Jamie" Jamieson
// */
//
//class CalloutDetails
//{
//  var standardVerse: Ref? = null
//  var standardVerseIsCanonicalTitle = false
//  var alternativeRefCollection: RefCollection? = null
//  var alternativeRefCollectionHasPrefixPlusSign: Boolean = false
//  var alternativeRefCollectionHasEmbeddedPlusSign: Boolean = false
//  var sourceVerseCollection: RefCollection? = null
//}
//
//
//
//
//
///******************************************************************************/
///**
// * A single row of the reversification data.
// *
// * @author ARA "Jamie" Jamieson
// */
//
//class ReversificationDataRow (owningInstance: ReversificationData, rowNo: Int)
//{
//  lateinit var action: String
//           var ancientVersions = ""
//  lateinit var fields: MutableList<String>
//  lateinit var originalFields: List<String>
//  lateinit var calloutDetails: CalloutDetails
//           var footnoteLevel = -1
//           var processingFlags = 0
//           val rowNumber = rowNo // Publicly accessible only for debugging.
//  lateinit var sourceRef: Ref
//           var sourceRefAsRefKey = 0L
//  lateinit var standardRef: Ref
//           var standardRefAsRefKey = 0L
//           var owner = owningInstance
//
//
//  /****************************************************************************/
//  /**
//  * Returns an indication of whether the row involves a psalm title.
//  *
//  * @return True if row involves a psalm title.
//  */
//
//  fun sourceIsPsalmTitle () = 0 != processingFlags.and(ReversificationData.C_SourceIsPsalmTitle)
//
//
//  /****************************************************************************/
//  /**
//  * Returns an indication of whether the row involves renumbering.
//  *
//  * @return True if row involves renumbering.
//  */
//
//  fun isRenumber () = 0 != processingFlags.and(ReversificationData.C_Renumber)
//
//
//  /****************************************************************************/
//  /**
//  * Footnotes for AllBibles rows need to be moved to the start of the verse.
//  */
//
//  fun requiresNotesToBeMovedToStartOfVerse () = "AllBibles" == owner.getField("SourceType", this)
//
//
//  /****************************************************************************/
//  override fun toString (): String
//  {
//    return "Row: " + rowNumber.toString() + " " +
//      owner.getFieldOriginal("SourceType" , this) + " " +
//      owner.getFieldOriginal("SourceRef"  , this) + " " +
//      owner.getFieldOriginal("StandardRef", this) + " " +
//      owner.getFieldOriginal("Action"     , this) + " " +
//      owner.getFieldOriginal("NoteMarker" , this)
//  }
//}
//
