package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.applicationspecificutils.*
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
 * NRSVA compliant (NRSVA being our chosen standard).  Or we can leave the text
 * pretty much as-is, and then carry out on-the-fly restructuring within
 * STEPBible.
 *
 * The former of these is handled elsewhere.
 *
 * The approach which leaves restructuring to be handled within STEPBible is
 * handled in the present class.
 *
 * The big advantage is that when the text is being displayed in STEPBible in
 * 'plain vanilla mode', it looks like the original as supplied by the
 * translators.  This is good for users (who are not confronted with a text
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
 * The processing here is very modest -- we simply take those rows from the
 * reversification data which are selected for this text, and apply to the
 * source verses any footnotes which those rows define.
 *
 * (Processing elsewhere may make a few other minor changes -- for example, we
 * cannot have missing verses within chapters, so we have to create any verses
 * to fill in any gaps.)
 *
 * Note that some rows would, in a run where we were actually restructuring the
 * text rather than 'just' recording what mappings are to be applied, create
 * verses where none exist at present.
 *
 * Even though we are in theory not restructuring the text, we do need to create
 * these empty verses here.
 *
 * @author ARA "Jamie" Jamieson
 */

object PA_RuntimeReversificationHandler: PA()
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
  * Does what's necessary to support runtime reversification
  *
  * @param dataCollection Data to be processed.
  */

  fun process (dataCollection: X_DataCollection)
  {
    extractCommonInformation(dataCollection)
    m_EmptyVerseHandler = PA_EmptyVerseHandler(dataCollection.getFileProtocol())
    m_FootnoteHandler = PA_ReversificationFootnoteHandler(m_FileProtocol)
    Dbg.withProcessingBooks("Applying reversification footnotes for runtime reversification ...") {
      dataCollection.getRootNodes().forEach(::processRootNode)
    }
  }


  /****************************************************************************/
  /*
  * Applies the changes associated with this variant of reversification (ie the
  * one where any significant changes are left to STEPBible to apply at run
  * time).  This entails adding footnotes and possibly creating empty verses
  * where there are holes at the beginning or in the middle of chapters.
  * Except a) we add footnotes only if academic notes have been selected; and
  * b) at present I don't create empty verses here after all -- later backstop
  * processing deals with that.
  *
  * @param rootNode The document to be processed.
  * @return True if any changes made.
  */

  private fun processRootNode (rootNode: Node)
  {
    if ("runtime" != ConfigData["stepReversificationType"]!!.lowercase())
      return

    if (!ConfigData.getAsBoolean("stepOkToGenerateFootnotes"))
      return

    Dbg.withProcessingBook(m_FileProtocol.getBookAbbreviation(rootNode)) {
      IssueAndInformationRecorder.setRuntimeReversification()
      addFootnotes(rootNode)
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
  /* Runs over all reversification rows for this book and adds footnotes as
     necessary. */

  private fun addFootnotes (rootNode: Node): Boolean
  {
    initialise()
    var res = false
    val reversificationRows = m_FootnoteReversificationRows!![m_FileProtocol.getBookNumber(rootNode)] ?: return false
    val sidNodes = Dom.findNodesByAttributeName(rootNode, m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid()). associateBy { m_FileProtocol.readRef(it, m_FileProtocol.attrName_verseSid()).toRefKey() }
    reversificationRows.filter { it.sourceRefAsRefKey in sidNodes } .forEach { res = true; m_FootnoteHandler.addFootnoteAndSourceVerseDetailsToVerse(sidNodes[it.sourceRefAsRefKey]!!, it, 'R') }
    // Previously had this in the .forEach above: addFootnote(sidNodes[it.sourceRefAsRefKey]!!, it)
    return res
  }


  /****************************************************************************/
  private fun createEmptyVerses (rootNode: Node)
  {
    /**************************************************************************/
    fun createEmptyVerse (row: ReversificationDataRow)
    {
      val thisRefKey = row.standardRefAsRefKey
      val nodes = m_EmptyVerseHandler.createEmptyVerse(rootNode.ownerDocument, thisRefKey, true, "Samification")
      val insertBefore = Dom.findNodesByAttributeName(rootNode, m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid())
         .find { m_FileProtocol.readRef(it[m_FileProtocol.attrName_verseSid()]!!).toRefKey() > thisRefKey }!!
      Dom.insertNodeBefore(insertBefore, nodes[0])
      Dom.insertNodeBefore(insertBefore, nodes[1])
      m_FootnoteHandler.addFootnoteAndSourceVerseDetailsToVerse(nodes[0], row, 'R')
    }



    /**************************************************************************/
    val reversificationRows = m_AllReversificationRows[m_FileProtocol.getBookNumber(rootNode)] ?: return
    reversificationRows
      .filter { 0 != (it.processingFlags and ReversificationData.C_CreateIfNecessary) } // Rows which might result in the creation of a verse.
      .filter { !m_DataCollection.getBibleStructure().verseExistsWithOrWithoutSubverses(it.standardRefAsRefKey) }  // Rows corresponding to verses which don't exist.
      .forEach(::createEmptyVerse)
  }


  /****************************************************************************/
  /* Converts the reversification footnote flavour required on this run to an
     integer value against which we can check each row to see if its footnote
     is required or not. */

  private fun getReversificationNotesLevel (): Int
  {
     when (ConfigData["stepReversificationFootnoteLevel"]!!.lowercase())
     {
       "basic" ->    m_ReversificationNotesLevel = C_ReversificationNotesLevel_Basic
       "academic" -> m_ReversificationNotesLevel = C_ReversificationNotesLevel_Academic
     }

     return m_ReversificationNotesLevel
  }


  /****************************************************************************/
  private fun initialise ()
  {
    if (null != m_FootnoteReversificationRows) return // Already initialised.

    m_FootnoteCalloutGenerator = MarkerHandlerFactory.createMarkerHandler(MarkerHandlerFactory.Type.FixedCharacter, ConfigData["stepExplanationCallout"]!!)

    getReversificationNotesLevel()

    val allReversificationRows = ReversificationData.getAllAcceptedRows()
    m_AllReversificationRows = allReversificationRows.groupBy { Ref.getB(it.sourceRefAsRefKey) }
    m_FootnoteReversificationRows = allReversificationRows
      //.filter { ReversificationData.outputFootnote(it, 'R', if (C_ReversificationNotesLevel_Basic == m_ReversificationNotesLevel) 'B' else 'A') }
      .groupBy { Ref.getB(it.sourceRefAsRefKey) }
    m_IfAbsentReversificationRows = allReversificationRows.filter { "IfAbsent" == it.action} .groupBy { Ref.getB(it.sourceRefAsRefKey) }
  }


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


  /****************************************************************************/
  private lateinit var m_FootnoteHandler: PA_ReversificationFootnoteHandler
}
