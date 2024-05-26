package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefKey
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.util.*

/****************************************************************************/
/**
 * Provides a uniform place for handling the creation of and / or labelling
 * of empty verses.
 *
 * @author ARA "Jamie" Jamieson
 */

class EmptyVerseHandler (dataCollection: X_DataCollection)
{
  companion object {
    /**************************************************************************/
    /* 'Empty' verses are usually given some standard markup like, say, a dash,
       to indicate that they have not been left empty accidentally.
       Unfortunately, something (osis2mod? STEPBible?) suppresses adjacent
       verses whose content is too similar.  With a view to getting around this,
       I include here a stylised comment before the text node.  Later in the
       processing (ie at the point where I am outputting this in the form we
       will actually use to generate the module) I make use of this to avoid
       this unwanted suppression. */

    fun preventSuppressionOfEmptyVerses (dataCollection: X_DataCollection)
    {
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
   * Flags all verses which are currently empty.  The assumption is that this
   * will be called late, after any other empty-verse processing has had a
   * chance to fill verses in, and will pick up verses which were present but
   * empty in the original text.
   */

  fun markVersesWhichWereEmptyInTheRawText ()
  {
    m_DataCollection.getRootNodes().forEach { rootNode ->
      val emptyVerses = getEmptyVerses(rootNode)
      emptyVerses.forEach { annotateEmptyVerse(it) }
    }
  }


  /****************************************************************************/
  /**
   * The various other methods in this class let you create empty verses for
   * specific purposes.  If there remain any empty verses not marked with a
   * reason for being empty, it must be because they were empty in the raw USX,
   * and we need to add content and explanatory content to these too.
   *
   * @param sid Node to be processed.
   */

  private fun annotateEmptyVerse (sid: Node)
  {
    val sidAsRefKey = m_FileProtocol.readRef(sid[m_FileProtocol.attrName_verseSid()]!!).toRefKey()
    IssueAndInformationRecorder.verseEmptyInRawText(sidAsRefKey)
    val footnoteNode = m_FileProtocol.makeFootnoteNode(sid.ownerDocument, sidAsRefKey, ReversificationData.getFootnoteForEmptyVerses(sidAsRefKey), caller = null)
    Dom.insertNodeAfter(sid, footnoteNode)
    NodeMarker.setEmptyVerseType(sid, "emptyInRawText")
    Dom.insertNodeAfter(footnoteNode, createEmptyContent(sid.ownerDocument, m_Content_EmptyVerse))
  }


  /****************************************************************************/
  private fun getSidMap (rootNode: Node, nodeName: String): NavigableMap<RefKey, Node>
  {
    return Dom.findNodesByName(rootNode, nodeName, false)
      .filter { m_FileProtocol.attrName_verseSid() in it }
      .associateBy { m_FileProtocol.readRef(it[m_FileProtocol.attrName_verseSid()]!!).toRefKey() }.toSortedMap() as NavigableMap<RefKey, Node>
  }


  /****************************************************************************/
  /**
  * Creates an empty verse for use in an elision.  (In fact, the verse does
  * actually have content -- it carries a marker (a dash, for instance) to help
  * make it clear that it has been left empty deliberately.)  Note that no
  * footnote is added to the verse: if we are expanding out elisions, we add
  * a footnote only to the 'master' verse.
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

  fun createEmptyVerse (doc: Document, refKey: RefKey, wantEid: Boolean, reasonMarker: String): List<Node>
  {
    val sid = m_FileProtocol.makeVerseSidNode(doc, Pair(refKey, null))
    val content = createEmptyContent(doc, m_Content_EmptyVerse)
    NodeMarker.setEmptyVerseType(sid, reasonMarker) // May be overwritten with a more specific value by the caller.
    return if (wantEid)
      listOf(sid, content, m_FileProtocol.makeVerseEidNode(doc, Pair(refKey, null)))
    else
      listOf(sid, content)
  }


  /****************************************************************************/
  /**
  * Creates an empty verse for use in an elision.  (In fact, the verse does
  * actually have content -- it carries a marker (a dash, for instance) to help
  * make it clear that it has been left empty deliberately.)  Note that no
  * footnote is added to the verse: if we are expanding out elisions, we add
  * a footnote only to the 'master' verse.
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
           it.value.forEach { refKey -> doneSomething = true; createEmptyVerseForMissingVerse(rootNode, refKey, map[map.ceilingKey(refKey)]) }
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
   */

  fun createEmptyVerseForReversification (insertBefore: Node, sidRefKey: RefKey): Pair<Node, Node>
  {
    val start = m_FileProtocol.makeVerseSidNode(insertBefore.ownerDocument, Pair(sidRefKey, null))
    val end   = m_FileProtocol.makeVerseEidNode(insertBefore.ownerDocument, Pair(sidRefKey, null))

    NodeMarker.setEmptyVerseType(start, "reversification")

    Dom.insertNodeBefore(insertBefore, start)
    Dom.insertNodeBefore(insertBefore, end)

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
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Creates an empty verse to fill in a gap in the versification, and inserts
     it before the given node, or at the end of the parent chapter if
     insertBefore is null.  Returns a pair consisting of the sid and the eid. */

  private fun createEmptyVerseForMissingVerse (rootNode: Node, refKey: RefKey, insertBefore: Node?): Pair<Node, Node>
  {
    Logger.info(refKey, "Created verse which was missing from the original text.")

    val start = m_FileProtocol.makeVerseSidNode(rootNode.ownerDocument, Pair(refKey, null))
    val end   = m_FileProtocol.makeVerseEidNode(rootNode.ownerDocument, Pair(refKey, null))
    NodeMarker.setAddedFootnote(start).setEmptyVerseType(start, "missingInRawText")

    val ib = insertBefore ?: Dom.createNode(rootNode.ownerDocument,"<TempNode/>")
    Dom.insertNodeBefore(ib, start)
    val footnoteNode = m_FileProtocol.makeFootnoteNode(rootNode.ownerDocument, refKey, ReversificationData.getFootnoteForNewlyCreatedVerses(refKey))
    Dom.insertNodeBefore(ib, footnoteNode)
    Dom.insertNodeBefore(ib, createEmptyContent(rootNode.ownerDocument, m_Content_MissingVerse))
    Dom.insertNodeBefore(ib, end)

    if (null == insertBefore)
      Dom.deleteNode(ib)

    return Pair(start, end)
  }


  /****************************************************************************/
  private fun createEmptyContent (doc: Document, content: String) = Dom.createTextNode(doc, content)



  /****************************************************************************/
  private val m_DataCollection = dataCollection
  private val m_FileProtocol = dataCollection.getFileProtocol()
  private val m_Content_Elision = Translations.stringFormatWithLookup("V_contentForEmptyVerse_verseInElision")
  private val m_Content_EmptyVerse = Translations.stringFormatWithLookup("V_contentForEmptyVerse_verseEmptyInThisTranslation")
  private val m_Content_MissingVerse = Translations.stringFormatWithLookup("V_contentForEmptyVerse_verseWasMissing")
}
