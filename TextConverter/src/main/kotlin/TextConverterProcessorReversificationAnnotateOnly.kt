package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleBookAndFileMapperEnhancedUsx
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.bibledetails.BibleStructure
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.*
import org.w3c.dom.Document
import org.w3c.dom.Node



/******************************************************************************/
/**
 * Handles one flavour of reversification -- what DIB labels 'versification'.
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
 * We can handle this in either of two ways.  We can either restructure the
 * text during the conversion process so as to create a module which is fully
 * NRSVA compliant.  Or we can leave the text pretty much as-is, and then
 * carry out on-the-fly restructuring within STEPBible.
 *
 * The former of these is handled by [TextConverterProcessorXToUsxB_ReversificationProcessor],
 * and the advantages and disadvantages of this approach are discussed there.
 *
 * The approach which leaves restructuring to be handled within STEPBible is
 * handled in the present class.  This too has its advantages and disadvantages.
 *
 * The big advantage is that when the text is being displayed in STEPBible in
 * 'plain vanilla mode', it looks like the original as supplied by the
 * translators.  This is good both for users (who are not confronted with a text
 * with verses in the 'wrong' places or carrying the wrong verse numbers); and
 * it is good in that very often licence conditions preclude us from carrying
 * out the sort of significant restructuring required by the alternative
 * approach.
 *
 * The disadvantage is that it produces a module which can be displayed only
 * using our own bespoke variant of JSword etc.  This means it will not be
 * usable with older versions of offline STEPBible, and it also means we can't
 * make it available to third parties (although this latter fact is perhaps not
 * so much of an issue, because the licence conditions probably mean we couldn't
 * offer it to third parties anyway).
 *
 *
 * The processing here is very modest -- we simply take those rows from the
 * reversification data which are selected for this text, and apply to the
 * source verses any footnotes which those rows define.
 *
 * (Processing elsewhere may make a few other minor changes -- for example, we
 * cannot have missing verses within chapters, so we have to create any verses
 * to fill in any gaps.)
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
  * Applies the changes associated with this variant of reversification (ie the
  * one where any significant changes are left to STEPBible to apply at run
  * time).  This entails adding footnotes and possibly creating empty verses
  * where there are holes at the beginning or in the middle of chapters.
  *
  * @param document The document to be processed.
  * @param bookName The book covered by this document.
  * @return True if any changes made.
  */

  fun process (document: Document, bookName: String): Boolean
  {
    return if ("runtime" == ConfigData["stepReversificationType"]!!.lowercase())
      addFootnotes(document, bookName)
    else
      false
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* $$$ Do I always add the footnote if one is available, or do I need to take
     Nec / Acd / Opt into account?

     The reversification data defines various actions which must be applied
     to the text if we are actually restructuring it.  In this present class,
     we are _not_ restructuring -- we are merely adding footnotes.

     We are applying footnotes to _source_ verses, and the reversification
     data will have been filtered so that rows will have been selected only
     if their source verses exist.  There is therefore never any need to create
     verses here. */

  private fun addFootnote (sidNode: Node, row: ReversificationDataRow)
  {
    /**************************************************************************/
    /* This is the 'pukka' callout -- ie the piece of text which you click on in
       order to reveal the footnote.  Originally it was expected to be taken
       from the NoteMarker text in the reversification data.  However, we then
       found that STEP always rendered it as a down-arrow; and latterly DIB has
       decided he wants it that way even if STEP is fixed to display the actual
       callout text we request.  I therefore need to generate a down-arrow here,
       which is what the callout generator gives me. */

     val callout = m_FootnoteCalloutGenerator.get()



    /**************************************************************************/
    /* I have been asked to force certain footnotes to the start of the owning
       verse, even if their natural position would be later.  I flag such notes
       here with a special attribute and then move them later. */

    val footnoteText = makeFootnoteText(row) ?: return
    val noteNode = MiscellaneousUtils.makeFootnote(sidNode.ownerDocument, row.standardRefAsRefKey, footnoteText, callout)
    if ("AllBibles" == row.action)
      Dom.setAttribute(noteNode, "_TEMP_moveNoteToStartOfVerse", "y")
    Dom.insertNodeAfter(sidNode, noteNode)
  }


  /****************************************************************************/
  /* Runs over all reversification rows for this book and adds footnotes as
     necessary. */

  private fun addFootnotes (document: Document, bookName: String): Boolean
  {
    initialise()
    var res = false
    val reversificationRows = m_FootnoteReversificationRows!![BibleBookNamesUsx.nameToNumber(bookName)] ?: return false
    val sidNodes = Dom.findNodesByName(document, "verse").filter { "sid" in it }. associateBy { Ref.rdUsx(it["sid"]!!).toRefKey() }
    reversificationRows.filter { it.sourceRefAsRefKey in sidNodes } .forEach { res = true; addFootnote(sidNodes[it.sourceRefAsRefKey]!!, it) }
    return res
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

    BibleStructure.UsxUnderConstructionInstance().clear() // Force USX structure to be re-evaluated, including obtaining word counts.
    BibleStructure.UsxUnderConstructionInstance().populateFromBookAndFileMapper(BibleBookAndFileMapperEnhancedUsx, collection = "enhanced/reversificationAnnotate", true)

    getReversificationNotesLevel() // Determine what kind of footnotes are required on this run.
    m_FootnoteCalloutGenerator = MarkerHandlerFactory.createMarkerHandler(MarkerHandlerFactory.Type.FixedCharacter, ConfigData["stepExplanationCallout"]!!)
    //ReversificationData.process() // Read the reversification data.

    val allReversificationRows = ReversificationData.getAllAcceptedRows()
    m_AllReversificationRows = allReversificationRows.groupBy { Ref.getB(it.sourceRefAsRefKey) }
    m_FootnoteReversificationRows = allReversificationRows
      .filter { ReversificationData.wantFootnote(it, 'R', if (C_ReversificationNotesLevel_Basic == m_ReversificationNotesLevel) 'B' else 'A') }
      .groupBy { Ref.getB(it.sourceRefAsRefKey) }
    m_IfAbsentReversificationRows = allReversificationRows.filter { "IfAbsent" == it.action} .groupBy { Ref.getB(it.sourceRefAsRefKey) }
  }


  /****************************************************************************/
  private fun makeFootnoteText (row: ReversificationDataRow): String?
  {
    var text  = ReversificationData.getFootnoteVersification(row)
    if (text.isEmpty()) return null
    text = text.replace("S3y", "S3Y") // DIB prefers this.
    val ancientVersions = if (m_ReversificationNotesLevel > C_ReversificationNotesLevel_Basic) ReversificationData.getAncientVersions(row) else null
    if (null != ancientVersions) text += " $ancientVersions"
    return text
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