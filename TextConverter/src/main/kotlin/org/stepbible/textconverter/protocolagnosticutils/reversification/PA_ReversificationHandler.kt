/******************************************************************************/
package org.stepbible.textconverter.protocolagnosticutils.reversification

import org.stepbible.textconverter.applicationspecificutils.Original
import org.stepbible.textconverter.applicationspecificutils.Revised
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.protocolagnosticutils.PA
import java.util.*


/******************************************************************************/
/**
 * Processes reversification data.
 *
 *
 *
 *
 * ## Use
 *
 * This class should never be instantiated directly.  Callers wishing to use a
 * reversification handler should use the *instance* method of
 * [PA_ReversificationUtilities] to obtain an instance of a flavour of
 * reversification handler relevant to the present run.
 *
 *
 *
 *
 * ## Reversification flavours
 *
 * At one stage we were contemplating two forms of reversification -- one, which
 * I dubbed 'conversion-time', entailed physically restructuring the text during
 * the conversion process so as to end up with a module which was fully NRSVA
 * compliant.  The other ('runtime') involved -- at least to a first
 * approximation -- nothing more than recording details of the way in which the
 * text deviated from NRSVA, this information then being used by a revised form
 * of osis2mod and the run-time system to restructure the text on the fly when
 * NRSVA compliance was needed in support of STEPBible's added value features.
 *
 * At the time of writing, conversion-time restructuring is not really being
 * considered seriously, since such restructuring is ruled out by the licence
 * conditions on most copyright texts (and would also result in a Bible which
 * differed from the expectations of users acquainted with the text).
 *
 * Having said that it is no longer being considered seriously, in fact from
 * time to time it is mooted that we might actually occasionally use it after
 * all.  In view of this, I have attempted to put together the outline of
 * something which might do the job -- see [PA_ReversificationHandler_ConversionTime]
 * for more details.
 *
 * The present class serves as a base class for either form of reversification.
 *
 * @author ARA "Jamie" Jamieson
 */

/******************************************************************************/
open class PA_ReversificationHandler internal constructor (): PA ()
{
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
  }


  /****************************************************************************/
  /**
   * Lets a caller know whether the structure has been altered.
   *
   * @return True if structure has been altered.
   */

  internal fun hasAlteredStructure () = m_HasAlteredStructure
  private var m_HasAlteredStructure = false


  /****************************************************************************/
  /**
  * Sets a flag to indicate that the structure has been changed.
  */

  @Synchronized internal fun setAlteredStructure () { m_HasAlteredStructure = true }


  /****************************************************************************/
  /**
  * Returns a map which maps original RefKey to revised RefKey, so that cross-
  * references can be updated if necessary.
  *
  * This is meaningful only for conversion-time reversification.  In run-time
  * reversification, we do not alter the structure of the text, and therefore
  * if cross-references were correct to begin with, they remain correct.
  *
  * @return Map.
  */

  open fun getCrossReferenceMappings (): Map<Original<RefKey>, Revised<RefKey>> = mapOf()


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
  * This function returns meaningful information only with runtime
  * reversification -- the kind of information it could supply is not used with
  * conversion-time reversification.
  *
  * @return List of mappings, ordered by source RefKey.
  */

  open fun getRuntimeReversificationMappings (): List<Pair<SourceRef<RefKey>, StandardRef<RefKey>>> = listOf()


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
    return setOf()
//    val standardRefs = m_SelectedRows.map { it.standardRefAsRefKey }.toSet()
//    return m_SelectedRows.asSequence()
//                         .filter { 2 == Ref.getS(it.standardRefAsRefKey) }
//                         .filter { Ref.setS(it.standardRefAsRefKey, 1) !in standardRefs }
//                         .filter { Action.RenumberVerse == it.action }
//                         .map { it.standardRefAsRefKey }
//                         .toSet()
  }
} // PA_ReversificationHandler

@JvmInline value class SourceRef  <T> (val value: T)
@JvmInline value class StandardRef<T> (val value: T)













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





