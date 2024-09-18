package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.TranslatableFixedText
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefKey
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.shared.Language
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.util.*

/****************************************************************************/
/**
 * Provides a uniform place for handling the creation of and / or labelling
 * of empty verses.
 *
 * It may be useful to understand how this works and how it fits in with the
 * reversification processing.
 *
 * We must not have missing verses at the start of chapters or anywhere in
 * the middle.  (Missing verses at the *ends* of chapters are ok, and I
 * have been asked not to do anything to make good such verses.)
 *
 * In theory reversification is capable of supply certain missing verses.
 * This, however, is academic -- there is no guarantee that we will use
 * reversification on a given text, and even if we did, we are no longer
 * planning to have the reversification processing carry out the kind of
 * physical restructuring of the text which would be necessary.
 *
 * We therefore fill in empty verses entirely independently of
 * reversification.  And we may do this for a number of reasons:
 *
 * - We may expand elisions out into the individual verses which make
 *   them up.
 *
 * - We may process tables.  This is really just a special case of
 *   elision processing, because the verses which make up the table
 *   are just converted into a giant elision.
 *
 * - We may need to create new verses because even after all of this
 *   processing, holes remain in the text.
 *
 *
 * In addition to actually creating the verses, we may or may not need to
 * annotate them with footnotes.  In the general case, we know in advance
 * what the footnote should be.  But the reversification data contains
 * details of a few verses which are commonly missing, for which it holds
 * special footnotes, and we need to use those in preference where they
 * are relevant.
 *
 *
 * There are vaguely similar issues with empty verses.  Here at least we
 * are dealing with only one case -- that where the
 *
 * Missing verses may appear in the text for a number of reasons.
 *
 * - They may have been empty in the raw text.
 *
 * - We may have added them when expanding out elisions.
 *
 * - We may have created them when processing tables (which itself entails
 *   turning the table into a large elision, and therefore, from the point of
 *   view of handling empty verses, is not so very different from a plain
 *   vanilla elision).
 *
 * - We may have created them because there were holes in the text.  (Things do
 *   not work if verses are missing at the start of chapters or in the middle
 *   of them, so we have to fill in the blanks.  Verses missing at the *ends*
 *   of chapters are not a problem, and I have been asked not to fill in
 *   these trailing verses.)
 *
 *
 * The reversification data itself interacts with this in two ways -- one at
 * present purely theoretical, and one actual.
 *
 * In theory, the reversification data may recognise the need to supply an
 *
 * @author ARA "Jamie" Jamieson
 */

class PA_EmptyVerseHandler (fileProtocol: X_FileProtocol)
{
  companion object {
    /**************************************************************************/
    /**
     * 'Empty' verses are usually given some standard markup like, say, a dash,
     *  to indicate that they have not been left empty accidentally.
     *  Unfortunately, something (osis2mod? STEPBible?) suppresses adjacent
     *  verses whose content is too similar.  With a view to getting around
     *  this, I include here a stylised comment before the text node.  Later in
     *  the processing (ie at the point where I am outputting this in the form
     *  we will actually use to generate the module) I make use of this to avoid
     *  this unwanted suppression.
     *
     *  @param dataCollection Data to be processed.
     */

    fun preventSuppressionOfEmptyVerses (dataCollection: X_DataCollection)
    {
      Dbg.withReportProgressSub("Preventing suppression of empty verses.") {
        var toggle = true
        dataCollection.getRootNodes().forEach { rootNode ->
          rootNode.getAllNodesBelow().filter { NodeMarker.hasEmptyVerseType(it) }.forEach { verse ->
            toggle = !toggle
            if (toggle)
            {
              val content = verse.nextSibling
              Dom.deleteNode(content)
              val wrapper = dataCollection.getFileProtocol().makeDoNothingMarkup(verse.ownerDocument)
              wrapper.appendChild(content)
              Dom.insertNodeAfter(verse, wrapper)
            } // if
          } // forEach verse
        } // forEach rootNote
      } // Dbg.withReportProgressSub
    } // fun
  } // companion





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Creates an empty verse for use in an elision.  (In fact, the verse does
  * actually have content -- it carries a marker (a dash, for instance) to help
  * make it clear that it has been left empty deliberately.)  NOTE THAT NO
  * FOOTNOTE IS ADDED TO THE VERSE: IF WE ARE EXPANDING OUT ELISIONS, WE ADD
  * A FOOTNOTE ONLY TO THE 'MASTER' VERSE (and, depending upon permissions,
  * perhaps not even there).
  *
  * This is called both for verses which were empty in the raw text and for
  * empty verse created as a result of processing tables.  If they need to
  * be differentiated, it is down to the caller to organise that.
  *
  * @param doc Verse which is marked as being an elision.
  * @param refKey Identifies the scripture reference for the new verse.
  * @param wantEid Determines whether we return an eid.
  * @param reasonMarker Text to explain why the empty verse has been created.
  */

  fun createEmptyVerseForElision (doc: Document, refKey: RefKey, wantEid: Boolean, reasonMarker: String = "elision"): List<Node>
  {
    val sid = m_FileProtocol.makeVerseSidNode(doc, Pair(refKey, null))
    val content = createEmptyContent(doc, m_Content_Elision)
    NodeMarker.setEmptyVerseType(sid, reasonMarker) // May be overwritten with a more specific value by the caller.
    return if (wantEid)
      listOf(sid, content, m_FileProtocol.makeVerseEidNode(doc, Pair(refKey, null)))
    else
      listOf(sid, content)
  }


  /****************************************************************************/
  /**
  * Creates empty verses for an entire text.  As written below, using
  * getMissingEmbeddedVersesForText, this fills in only verses which are
  * missing at the start of chapters or in the middle -- not at the end.
  *
  * @param dataCollection Details of the input.
  * @return True if any verses were created.
  */

  fun createEmptyVersesForMissingVerses (dataCollection: X_DataCollection): Boolean
  {
    var doneSomething = false
    dataCollection.getBibleStructure().getMissingEmbeddedVersesForText()
      .groupBy { Ref.getB(it) }
      .forEach {
         val rootNode = dataCollection.getRootNode(it.key)
         if (null != rootNode)
         {
           val map = getSidMap(rootNode, m_FileProtocol.tagName_verse())
           it.value.forEach { refKey ->
             doneSomething = true
             //Dbg.d(refKey.toString() + " / " + map.ceilingKey(refKey)) /*^^^**/
             createEmptyVerseForMissingVerse(rootNode, refKey, map[map.ceilingKey(refKey)]) }
         }
      }

    return doneSomething
  }


  /****************************************************************************/
  /**
   * Creates an empty verse on behalf of reversification processing.
   *
   * This does not add any footnote -- I assume that reversification is going
   * to do that, because the footnote differs according to the command-line
   * parameters.
   *
   * @param insertBefore Node before which the verse and its content are to be
   *   inserted.
   *
   * @param sidRefKey sid to be used in new verse.
   *
   * @return A pair containing the sid and eid nodes.
   *
   * <span class='important'>DON'T BOTHER TOO MUCH ABOUT THIS -- IT IS USED ONLY
   * BY CONVERSION-TIME REVERSIFICATION, AND WE DON'T INTEND TO USE THAT.</span>
   */

  fun createEmptyVerseForReversification (insertBefore: Node, sidRefKey: RefKey): Pair<Node, Node>
  {
    val start = m_FileProtocol.makeVerseSidNode(insertBefore.ownerDocument, Pair(sidRefKey, null))
    val end   = m_FileProtocol.makeVerseEidNode(insertBefore.ownerDocument, Pair(sidRefKey, null))

    NodeMarker.setEmptyVerseType(start, "reversification")

    Dom.insertNodeBefore(insertBefore, start)
    Dom.insertNodeBefore(insertBefore, end)

    IssueAndInformationRecorder.addEmptyVerseGeneratedForReversification(Ref.rd(sidRefKey).toString())

    return Pair(start, end)
  }


  /****************************************************************************/
  /**
   * Flags all verses which are currently empty.  The assumption is that this
   * will be called late, after any other empty-verse processing has had a
   * chance to fill verses in, and will pick up verses which were present but
   * empty in the original text.
   */

  fun markVersesWhichWereEmptyInTheRawText (dataCollection: X_DataCollection)
  {
    Dbg.withReportProgressSub("Marking verses which were empty in the raw text.") {
      dataCollection.getRootNodes().forEach { rootNode ->
        val emptyVerses = getEmptyVerses(rootNode)
        emptyVerses.forEach { annotateVerseWhichWasEmptyInRawText(it) }
      }
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
  /*
   * The various other methods in this class let you create empty verses for
   * specific purposes.  If there remain any empty verses not marked with a
   * reason for being empty, it must be because they were empty in the raw USX,
   * and we need to add content and explanatory content to these too.
   *
   * @param sid Node to be processed.
   */

  private fun annotateVerseWhichWasEmptyInRawText (sid: Node)
  {
    val footnoteText = AnticipatedIfEmptyDetails.getFootnote(m_FileProtocol.getSidAsRefKey(sid)) ?: TranslatableFixedText.stringFormat(Language.Vernacular, "V_emptyContentFootnote_verseEmptyInThisTranslation")
    val sidAsRefKey = m_FileProtocol.readRef(sid[m_FileProtocol.attrName_verseSid()]!!).toRefKey()
    val footnoteNode = m_FileProtocol.makeFootnoteNode(Permissions.FootnoteAction.AddFootnoteToVerseWhichWasEmptyInRawText, sid.ownerDocument, sidAsRefKey, footnoteText, caller = null)
    if (null == footnoteNode)
    {
      NodeMarker.setEmptyVerseType(sid, "emptyInRawText")
      Dom.insertNodeAfter(sid, createEmptyContent(sid.ownerDocument, m_Content_EmptyVerse))
    }
    else
    {
      Dom.insertNodeAfter(sid, footnoteNode)
      NodeMarker.setEmptyVerseType(sid, "emptyInRawText")
      Dom.insertNodeAfter(footnoteNode, createEmptyContent(sid.ownerDocument, m_Content_EmptyVerse))
    }

    IssueAndInformationRecorder.addVerseWhichWasEmptyInTheRawText(m_FileProtocol.getSid(sid) + (if (null == footnoteNode) "" else " (with footnote)"))
    if (null != footnoteNode) IssueAndInformationRecorder.addGeneratedFootnote(m_FileProtocol.getSid(sid) + " (VerseEmptyInRawText)")
  }


  /****************************************************************************/
  private fun createEmptyContent (doc: Document, content: String) = Dom.createTextNode(doc, content)



  /****************************************************************************/
  /* Creates an empty verse to fill in a gap in the versification, and inserts
     it before the given node, or at the end of the parent chapter if
     insertBefore is null.  Returns a pair consisting of the sid and the eid. */

  private fun createEmptyVerseForMissingVerse (rootNode: Node, refKey: RefKey, insertBefore: Node?): Pair<Node, Node>
  {
    Logger.info(refKey, "Created verse which was missing from the original text.")

    val footnoteText = AnticipatedIfEmptyDetails.getFootnote(refKey) ?: TranslatableFixedText.stringFormat(Language.Vernacular, "V_emptyContentFootnote_verseWasMissing")

    val start = m_FileProtocol.makeVerseSidNode(rootNode.ownerDocument, Pair(refKey, null))
    val end   = m_FileProtocol.makeVerseEidNode(rootNode.ownerDocument, Pair(refKey, null))
    NodeMarker.setEmptyVerseType(start, "missingInRawText")

    val ib = insertBefore ?: Dom.createNode(rootNode.ownerDocument,"<TempNode/>")
    Dom.insertNodeBefore(ib, start)
    val footnoteNode = m_FileProtocol.makeFootnoteNode(Permissions.FootnoteAction.AddFootnoteToVerseGeneratedToFillHoles, rootNode.ownerDocument, refKey, footnoteText)
    if (null != footnoteNode) Dom.insertNodeBefore(ib, footnoteNode)
    Dom.insertNodeBefore(ib, createEmptyContent(rootNode.ownerDocument, m_Content_MissingVerse))
    Dom.insertNodeBefore(ib, end)

    if (null == insertBefore)
      Dom.deleteNode(ib)

    val sidAsString = Ref.rd(refKey).toString()
    IssueAndInformationRecorder.addVerseWhichWasMissingInTheRawText(sidAsString + (if (null == footnoteNode) "" else " (with footnote)"))
    if (null != footnoteNode) IssueAndInformationRecorder.addGeneratedFootnote("$sidAsString (VerseMissingInRawText)")

    return Pair(start, end)
  }


  /****************************************************************************/
  /* Returns a list of all the empty verses in the document.  A verse is
    regarded as empty if either it is indeed completely empty, or if it
    contains only whitespace. */

  private fun getEmptyVerses (rootNode: Node): List<Node>
  {
    /**************************************************************************/
    val res: MutableList<Node> = mutableListOf()
    var couldBeEmpty: Node? = null



    /**************************************************************************/
    fun processNode (node: Node)
    {
      if (m_FileProtocol.tagName_verse() == Dom.getNodeName(node))
      {
        if (m_FileProtocol.attrName_verseSid() in node)
          couldBeEmpty = node
        else
        {
          if (null != couldBeEmpty)
            res.add(couldBeEmpty!!)
          couldBeEmpty = null
        }
      }

      else if (!Dom.isTextNode(node) || node.textContent.isNotBlank())
        couldBeEmpty = null
    }



    /**************************************************************************/
    Dom.getAllNodesBelow(rootNode).forEach { processNode(it) }
    return res
  }


  /****************************************************************************/
  private fun getSidMap (rootNode: Node, nodeName: String): NavigableMap<RefKey, Node>
  {
    return Dom.findNodesByName(rootNode, nodeName, false)
      .filter { m_FileProtocol.attrName_verseSid() in it }
      .associateBy { m_FileProtocol.readRef(it[m_FileProtocol.attrName_verseSid()]!!).toRefKey() }.toSortedMap() as NavigableMap<RefKey, Node>
  }


  /****************************************************************************/
  private var m_FileProtocol: X_FileProtocol=fileProtocol
  private val m_Content_Elision by lazy { TranslatableFixedText.stringFormatWithLookup("V_contentForEmptyVerse_verseInElision") }
  private val m_Content_EmptyVerse by lazy { TranslatableFixedText.stringFormatWithLookup("V_contentForEmptyVerse_verseEmptyInThisTranslation") }
  private val m_Content_MissingVerse by lazy { TranslatableFixedText.stringFormatWithLookup("V_contentForEmptyVerse_verseWasMissing") }
}
