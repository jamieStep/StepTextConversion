package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.*
import org.w3c.dom.Document
import org.w3c.dom.Node



/******************************************************************************/
/**
 * Handles reversification (sort of).
 *
 * Not all Bibles are split up into verses in the same way.  A given piece of
 * Greek text may be labelled Mat 99:1 in one Bible, and Mat 100:2 in another.
 * This makes it difficult to support STEP's added value functionality.  In an
 * interlinear display, you cannot necessarily show verse Luk 12:1 of one text
 * against Luk 12:1 of another, because the two may be translations of different
 * chunks of the underlying text.  Similarly you need to take care when
 * displaying verse vocabulary: linking the vocabulary to a particular verse
 * number may not work, because in different Bibles, that verse number may
 * relate to different bits of Greek text.
 *
 * Reversification attempts to get round this by restructuring Bibles to fit
 * a standard scheme (our choice being NRSV(A)).  This may entail various
 * kinds of changes -- verses may remain in situ but be renumbered, may be moved
 * to other locations (even in different books), may be created ex nihilo, etc.
 * And in addition we may add footnotes to verses to given more information
 * about places where the structure departs from that commonly seen elsewhere.
 *
 * Unfortunately, reversification exists in two incarnations currently,
 * according to where this restructuring occurs.
 *
 * This present class contains much the easier of the two, because here we
 * retain the text in its original form, except perhaps for two relatively
 * minor changes: if the text lacks verses from the start or middle of
 * chapters, we create them; and we may add the footnotes referred to above.
 * Restructuring is something carried out on-the-fly during the rendering
 * process when it is needed in order to support our added value; and since
 * it is carried out at run-time, it is of no concern to us here.
 *
 * This approach means that when viewing the text stand-alone in STEPBible,
 * it looks almost exactly as it did when supplied to us, which is important
 * because very often licence conditions prevent us from making significant
 * changes.  We do still need the restructuring in support of STEPBible's
 * added value, but that can restructuring can be made temporary, and is
 * hopefully acceptable in support of things like searches, interlinear
 * display etc.
 *
 * The class TextConverterProcessorReversificationRemapVerses (qv) deals with
 * the more complicated form, where we restructure the text during the
 * conversion process.  This is likely to be of use only on a very few texts,
 * because of the licence conditions mentioned above.
 *
 * @author ARA "Jamie" Jamieson
 */

object TextConverterProcessorReversificationAnnotateOnly
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Indicates whether this functionality should be used on a particular run.
  * Note that the 'this functionality' here relates specifically to that
  * portion which may annotates verses and may create empty ones.  The class
  * also provides information for other purposes, and this may be used even if
  * the present method returns false.
  *
  * @return True if this functionality should be used.
  */

  fun runMe (): Boolean
  {
    return "conversiontime" == ConfigData["stepReversificationType"]!!.lowercase()
  }



  /****************************************************************************/
  /**
  * Applies the changes associated with this variant of reversification (ie the
  * one where any significant changes are left to STEPBible to apply at run
  * time).  This entails adding footnotes and possibly creating empty verses
  * where there are holes at the beginning or in the middle of chapters.
  *
  * @param document The document to be processed.
  * @param bookName The book covered by this document.
  */

  fun applyReversificationChanges (document: Document, bookName: String)
  {
    initialise()
    addMissingVerses(document, bookName)
    addFootnotes(document, bookName)
  }


  /****************************************************************************/
  /**
  * Returns information needed in STEPBible when we are rely upon it to handle
  * reversification-related text restructuring, rather than doing it ourselves
  * in the converter.
  *
  * The return value is a list of RefKey -> RefKey pairs, covering all
  * reversification rows where a source verse is renumbered or moved, given
  * the source and target RefKey.
  *
  * @return List of mappings, ordered by source RefKey.
  */
  
  fun getReversificationMappings (): List<Pair<RefKey, RefKey>>
  {
    initialise()

    val res: MutableList<Pair<RefKey, RefKey>> = mutableListOf()
    val renumbers = ReversificationData.getReferenceMappings()
    //renumbers.forEach { m_BibleStructure.jswordMappings.add(Pair(it.key, Ref.clearS(it.value))) }
    renumbers.forEach { res.add(Pair(it.key, it.value)) }

    val psalmTitles = ReversificationData.getAllAcceptedRows().filter { 0 != it.processingFlags.and(ReversificationData.C_StandardIsPsalmTitle) }
    psalmTitles.forEach { res.add(Pair(it.sourceRefAsRefKey, Ref.setV(it.standardRefAsRefKey, 0))) }

    res.sortBy { it.first }
    //res.forEach { Dbg.d("" + it.first + "=" + it.second)}
    return res
  }


  /****************************************************************************/
  /* This method has changed a lot over time.  In this latest incarnation:

     - It is always appropriate for the method to add a footnote, except
       a) If the reversification data indicates IfAbsent (this requires
       the verse to be created ex nihilo, and it gets the necessary footnote
       as part of that processing); or b) The data indicates IfEmpty and the
       verse is _not_ empty.

     - Except also that it gets the footnote only if the footnote level
       requires it.

     - It gets a plain vanilla callout marker (previously we were doing
       something clever to insert a related verse number as part of the
       canonical text).
  */

  private fun addFootnote (sidNode: Node, row: ReversificationDataRow)
  {
    /**************************************************************************/
    val action = row.action.lowercase()
    if ("ifabsent" == action)
      return



    /**************************************************************************/
    if ("ifempty" == row.action && !verseIsEmpty(sidNode))
      return



    /**************************************************************************/
    val calloutDetails = row.calloutDetails
    val document = sidNode.ownerDocument



    /**************************************************************************/
    /* This is the 'pukka' callout -- ie the piece of text which you click on in
       order to reveal the footnote.  Originally it was expected to be taken
       from the NoteMarker text in the reversification data.  However, we then
       found that STEP always rendered it as a down-arrow; and latterly DIB has
       decided he wants it that way even if STEP is fixed to display the actual
       callout text we request.  I therefore need to generate a down-arrow here,
       which is what the callout generator gives me. */

     val callout: String = m_FootnoteCalloutGenerator.get()



    /**************************************************************************/
    /* Insert the footnote itself. */

    var footnoteText = makeFootnoteText(row)
     val noteNode = MiscellaneousUtils.makeFootnote(document, row.standardRefAsRefKey, footnoteText, callout)
//    res.appendChild(noteNode)
//    res.appendChild(Dom.createTextNode(document, " "))



    /**************************************************************************/
    /* Bit of rather yucky special case processing.  I have been asked to
       force certain footnotes to the start of the owning verse, even if their
       natural position would be later.  I flag such notes here with a special
       attribute and then move them later. */

    if (row.isInNoTestsSection)
      Dom.setAttribute(noteNode, "_TEMP_moveNoteToStartOfVerse", "y")



//    /**************************************************************************/
//    /* Check if we need the text which will typically be superscripted and
//       bracketed. */
//
//    val alternativeRefCollection = calloutDetails.alternativeRefCollection
//    if (null != alternativeRefCollection)
//    {
//      val basicContent = if (calloutDetails.alternativeRefCollectionHasEmbeddedPlusSign)
//          alternativeRefCollection.getLowAsRef().toString("a") + Translations.stringFormatWithLookup("V_reversification_alternativeReferenceEmbeddedPlusSign") + alternativeRefCollection.getHighAsRef().toString("a")
//        else if (calloutDetails.alternativeRefCollectionHasPrefixPlusSign)
//          Translations.stringFormatWithLookup("V_reversification_alternativeReferencePrefixPlusSign") + alternativeRefCollection.toString("a")
//        else
//          alternativeRefCollection.toString("a")
//
//      val textNode = Dom.createTextNode(document, Translations.stringFormatWithLookup("V_reversification_alternativeReferenceFormat", basicContent))
//      val containerNode = Dom.createNode(document, "<_X_reversificationCalloutAlternativeRefCollection/>")
//      containerNode.appendChild(textNode)
//      res.appendChild(containerNode)
//    }



    /**************************************************************************/
    Dom.insertNodeAfter(sidNode, noteNode)
  }


  /****************************************************************************/
  /* Runs over all reversification rows for this book and adds footnotes as
     necessary. */

  private fun addFootnotes (document: Document, bookName: String)
  {
    val reversificationRows = m_FootnoteReversificationRows!![BibleBookNamesUsx.nameToNumber(bookName)] ?: return
    val sidNodes = Dom.findNodesByName(document, "_X_verse").filter { "sid" in it }. associateBy { Ref.rdUsx(it["sid"]!!).toRefKey() }
    reversificationRows.filter { it.sourceRefAsRefKey in sidNodes } .forEach { addFootnote(sidNodes[it.sourceRefAsRefKey]!!, it) }
  }


  /****************************************************************************/
  private fun addMissingVerses (document: Document, bookName: String)
  {
    val reversificationRows = m_IfAbsentReversificationRows[BibleBookNamesUsx.nameToNumber(bookName)] ?: return
    val sidRefKeys = Dom.findNodesByName(document, "_X_verse") .filter { "sid" in it } .map { Ref.rdUsx(it["sid"]!!).toRefKey() } .toSet()
    reversificationRows.filterNot { it.sourceRefAsRefKey in sidRefKeys } .forEach {
      val footnoteText = if (m_ReversificationNotesLevel >= it.footnoteLevel) makeFootnoteText(it) else null
      EmptyVerseHandler.createEmptyVerseForMissingVerse(document, it.sourceRefAsRefKey, footnoteText, "addedByReversification")
    }
  }


  /****************************************************************************/
  /* Converts the reversification footnote flavour required on this run to an
     integer value against which we can check each row to see if its footnote
     is required or not. */

  private fun getReversificationNotesLevel ()
  {
     when (ConfigData["stepReversificationFootnotesLevel"]!!.lowercase())
     {
       "basic" ->    m_ReversificationNotesLevel = C_ReversificationNotesLevel_Basic
       "academic" -> m_ReversificationNotesLevel = C_ReversificationNotesLevel_Academic
     }
  }


  /****************************************************************************/
  private fun initialise ()
  {
    if (null != m_FootnoteReversificationRows) return // Already initialised.

    getReversificationNotesLevel() // Determine what kind of footnotes are required on this run.
    m_FootnoteCalloutGenerator = MarkerHandlerFactory.createMarkerHandler(MarkerHandlerFactory.Type.FixedCharacter, ConfigData["stepExplanationCallout"]!!)
    ReversificationData.process() // Read the reversification data.

    val allReversificationRows = ReversificationData.getAllAcceptedRows()
    m_AllReversificationRows = allReversificationRows.groupBy { Ref.getB(it.sourceRefAsRefKey) }
    m_FootnoteReversificationRows = allReversificationRows.filter { wantFootnote(it) } .groupBy { Ref.getB(it.sourceRefAsRefKey) }
    m_IfAbsentReversificationRows = allReversificationRows.filter { "IfAbsent" == it.action} .groupBy { Ref.getB(it.sourceRefAsRefKey) }
  }


  /****************************************************************************/
  private fun makeFootnoteText (row: ReversificationDataRow): String
  {
    var text  = ReversificationData.getFootnoteB(row)
    text = text.replace("S3y", "S3Y") // DIB prefers this.
    val ancientVersions = if (m_ReversificationNotesLevel > C_ReversificationNotesLevel_Basic) ReversificationData.getAncientVersions(row) else null
    if (null != ancientVersions) text += " $ancientVersions"
    return text
  }


  /****************************************************************************/
  private fun verseIsEmpty (sidNode: Node): Boolean
  {
    if (sidNode.ownerDocument !== m_DocumentForWhichWeHaveEmptyVerses)
    {
      m_DocumentForWhichWeHaveEmptyVerses = sidNode.ownerDocument
      m_EmptyVersesForGivenDocument = EmptyVerseHandler.getEmptyVerses(m_DocumentForWhichWeHaveEmptyVerses!!)
    }

    return sidNode in m_EmptyVersesForGivenDocument
  }


  /****************************************************************************/
  /* Determines whether or not we want a footnote for this reversification data
     row. */

  private fun wantFootnote (row: ReversificationDataRow): Boolean
  {
    return row.footnoteLevel <= m_ReversificationNotesLevel
  }


  /****************************************************************************/
  private var m_DocumentForWhichWeHaveEmptyVerses: Document? = null
  private var m_EmptyVersesForGivenDocument: List<Node> = listOf()



  /****************************************************************************/
  /* Keyed on book number; gives details of all reversification rows which
     apply to that book. */

  private lateinit var m_IfAbsentReversificationRows: Map<Int, List<ReversificationDataRow>>
  private lateinit var m_AllReversificationRows: Map<Int, List<ReversificationDataRow>>
  private var m_FootnoteReversificationRows: Map<Int, List<ReversificationDataRow>>? = null


  /****************************************************************************/
  private const val C_ReversificationNotesLevel_None = 999
  private const val C_ReversificationNotesLevel_Basic = 0
  private const val C_ReversificationNotesLevel_Academic = 1
  private var m_ReversificationNotesLevel = C_ReversificationNotesLevel_None


  /****************************************************************************/
  /* Used where we want to have a standard footnote callout on reversified
     verses, rather than use the callout defined in the reversification data. */

  private lateinit var m_FootnoteCalloutGenerator: MarkerHandler
}