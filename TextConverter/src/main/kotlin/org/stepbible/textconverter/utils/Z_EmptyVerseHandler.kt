package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.stepexception.StepExceptionShouldHaveBeenOverridden
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

open class Z_EmptyVerseHandler (fileProtocol: Z_FileProtocol)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * The various other methods in this class let you create empty verses for
   * specific purposes.  They all of them add an _temp_addedFootnote attribute
   * to sids to explain why they have been created empty.
   *
   * If there remain any empty verses without this attribute, it must be
   * because they were empty in the raw USX, and we need to add content and
   * explanatory content to these too.
   *
   * This method addresses this requirement.
   *
   * @param doc Document to be processed.
   * @return True if changes are made.
   */

  fun annotateEmptyVerses (doc: Document): Boolean
  {
    /************************************************************************/
    var isEmpty = false
    var sid: Node? = null
    var res = false



    /************************************************************************/
    fun addContent ()
    {
      res = true
      val sidAsRefKey = m_FileProtocol.readRef(sid!![m_FileProtocol.attrName_verseSid()]!!).toRefKey()
      val footnoteNode = m_FileProtocol.makeFootnoteNode(doc, sidAsRefKey, ReversificationData.getFootnoteForEmptyVerses(sidAsRefKey))
      Dom.insertNodeAfter(sid!!, footnoteNode)
      Dom.insertNodeAfter(sid!!, nullMarkupToPreventEmpty(doc, m_Content_EmptyVerse))
    }


    /************************************************************************/
    /* Run over the entire document checking each verse to see whether it is
       empty and also whether it already has a footnote. */

    val nodes = Dom.getNodesInTree(doc)
    for (i in nodes.indices)
    {
      /**********************************************************************/
      when (Dom.getNodeName(nodes[i]))
      {
        /********************************************************************/
        m_FileProtocol.tagName_verse() ->
        {
          if (m_FileProtocol.attrName_verseEid() in nodes[i])
          {
            isEmpty = "_temp_addedFootnote" !in nodes[i] // Assume verse is empty until we know otherwise.  Except that we pretend it's non-empty if it's already marked as empty.
            sid = nodes[i]
            continue
          }

          if (isEmpty)
          {
            Utils.addTemporaryAttribute(nodes[i], "_temp_addedFootnote", "verseEmptyInThisTranslation")
            Utils.addTemporaryAttribute(nodes[i], "_temp_emptyVerse", "y")
            addContent()
          }

          isEmpty = false
          sid = null

          continue
        } // verse



        /********************************************************************/
        else ->
        {
          if (null != sid && !m_FileProtocol.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(nodes[i]))
            isEmpty = false
        }
      } // when
    } // for



    /************************************************************************/
    return res
  } // fun


  /****************************************************************************/
  private fun createEmptyChapter (document: Document, refKey: RefKey, insertBefore: Node?, generatedReason: String = "Not found on completion of processing", reasonEmpty: String = "chapterWasMissing"): Node
  {
    Logger.warning(refKey, "Created chapter which was missing from the original text.")

    val newChapterRefKey = Ref.clearV(Ref.clearS(refKey))
    val newChapterRefAsString = Ref.rd(newChapterRefKey).toStringUsx()

    val chapterNode = Dom.createNode(document, "<chapter sid='$newChapterRefAsString' _X_generatedReason='$generatedReason' _X_reasonEmpty='$reasonEmpty'/>")
    val dummyVerseSidNode = Dom.createNode(document,"<verse _TEMP_dummy='y' sid='$newChapterRefAsString:${RefBase.C_BackstopVerseNumber}'/>")
    val dummyVerseEidNode = Dom.createNode(document,"<verse _TEMP_dummy='y' eid='$newChapterRefAsString:${RefBase.C_BackstopVerseNumber}'/>")
    chapterNode.appendChild(dummyVerseSidNode)
    chapterNode.appendChild(dummyVerseEidNode)

    if (null == insertBefore)
      Dom.findNodeByName(document, "book")!!.appendChild(chapterNode)
    else
      Dom.insertNodeBefore(insertBefore, chapterNode)

    return chapterNode
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
  * Checks for missing verses within the given document (and also for missing
  * chapters) and fills things in as necessary.  This version is for the case
  * where we are dealing with an ad hoc versification scheme, and therefore do
  * not know where each chapter is supposed to end (and consequently cannot
  * determine if we need to add trailing verses to the text under
  * construction).
  *
  * @param rootNode Root node of book.
  * @return True if changes were made.
  */

  fun createEmptyVersesForAdHocVersificationScheme (rootNode: Node, bibleStructure: Z_BibleStructure): Boolean
  {
    /**************************************************************************/
    val bookCode = m_FileProtocol.getBookCode(rootNode)
    val bookNo = m_FileProtocol.bookNameToNumber(bookCode)



    /**************************************************************************/
    /* This may look a little counterintuitive.  getMissingEmbeddedVersesForBook
       runs through chapters in verse order, looking for missing verses.
       (A verse n is regarded as missing if it does not exist, but there are
       later verses in the chapter, such that m > n.  We don't worry about
       verses which may be missing at the _end_ of a chapter.)

       However, in a few texts, the translators have deliberately put verses in
       the wrong order, and we don't want to process these, because that would
       give us duplicates.  Hence the 'filter' below. */

    val holes = bibleStructure.getMissingEmbeddedVersesForBook(bookNo, 0, 0, 0)
      .filter { !bibleStructure.verseExistsWithOrWithoutSubverses(it) }
    if (holes.isEmpty()) return false



    /**************************************************************************/
    val map = getSidMap(rootNode, m_FileProtocol.tagName_verse())
    holes.forEach { createEmptyVerseForMissingVerse(rootNode, it, map[map.ceilingKey(it)]) }
    return true
  }


  /****************************************************************************/
  /**
  * Creates an empty verse for use in an elision.  (In fact, the verse does
  * actually have content -- it carries a marker (a dash, for instance) to help
  * make it clear that it has been left empty deliberately.)  Note that no
  * footnote is added to the verse: if we are expanding out elisions, we add
  * a footnote only to the 'master' verse.
  *
  * @param doc Verse which is marked as being an elision.
  */

  fun createEmptyVerseForElision (doc: Document, refKey: RefKey): List<Node>
  {
    val sid = m_FileProtocol.makeVerseSid(doc, refKey)
    Utils.addTemporaryAttribute(sid, "_temp_inElision", "y")
    Utils.addTemporaryAttribute(sid, "_temp_addedFootnote", "inElision")
    Utils.addTemporaryAttribute(sid, "_temp_emptyVerse", "y")

    val nullContent = makeNullMarkupToPreventSimilarContentBeingSuppressed(doc, m_Content_Elision)
    val eid = m_FileProtocol.makeVerseEid(doc, refKey)
    Utils.addTemporaryAttribute(eid, "_temp_inElision", "y")
    Utils.addTemporaryAttribute(eid, "_temp_addedFootnote", "inElision")
    Utils.addTemporaryAttribute(eid, "_temp_emptyVerse", "y")

    return listOf(sid, nullContent, eid)
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

  fun createEmptyVersesForMissingVerses (dataCollection: Z_DataCollection): Boolean
  {
    var doneSomething = false
    dataCollection.BibleStructure.getMissingEmbeddedVersesForText()
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
   * to do that.
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
    val start = m_FileProtocol.makeVerseSid(insertBefore.ownerDocument, sidRefKey)
    val end   = m_FileProtocol.makeVerseEid(insertBefore.ownerDocument, sidRefKey)

    Dom.insertNodeBefore(insertBefore, start)
    Dom.insertNodeBefore(insertBefore, end)

    return Pair(start, end)
  }


  /****************************************************************************/
  /**
  * Returns a list of all the empty verses in the document.  A verse is
  * regarded as empty if either it is indeed completely empty, or if it contains
  * only whitespace.
  *
  * @param document The document to be examined.
  * @return List of sid nodes for empty verses.
  */

  fun getEmptyVerses (document: Document): List<Node>
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
            res.add(node)
          couldBeEmpty = null
        }
      }

      else if (!Dom.isTextNode(node) || node.textContent.isNotBlank())
        couldBeEmpty = null
    }



    /**************************************************************************/
    Dom.getNodesInTree(document).forEach { processNode(it) }
    return res
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Protected                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  protected open fun nullMarkupToPreventEmpty (doc: Document, content: String): Node { throw StepExceptionShouldHaveBeenOverridden() }
  protected val m_FileProtocol = fileProtocol





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

    val sidAsString = m_FileProtocol.refToString(refKey)
    val template = "<verse ID/>"
    val start = Dom.createNode(rootNode.ownerDocument, template.replace("ID", "${m_FileProtocol.attrName_verseSid()}='$sidAsString'"))
    val end   = Dom.createNode(rootNode.ownerDocument, template.replace("ID", "${m_FileProtocol.attrName_verseSid()}='$sidAsString'"))
    Utils.addTemporaryAttribute(start,"_temp_addedFootnote", "missingOnCompletionOfProcessing")
    Utils.addTemporaryAttribute(start, "_temp_emptyVerse", "y")

    val ib = insertBefore ?: Dom.createNode(rootNode.ownerDocument,"<_TEMP/>")
    Dom.insertNodeBefore(ib, start)
    val footnoteNode = m_FileProtocol.makeFootnoteNode(rootNode.ownerDocument, refKey, ReversificationData.getFootnoteForNewlyCreatedVerses(refKey))
    Dom.insertNodeBefore(ib, footnoteNode)
    Dom.insertNodeBefore(ib, makeNullMarkupToPreventSimilarContentBeingSuppressed(rootNode.ownerDocument, m_Content_MissingVerse))
    Dom.insertNodeBefore(ib, end)

    if (null == insertBefore)
      Dom.deleteNode(ib)

    return Pair(start, end)
  }


  /****************************************************************************/
  /* 'Empty' verses are usually given some standard markup like, say, a dash,
     to indicate that they have not been left empty accidentally.
     Unfortunately, something (osis2mod? STEPBible) suppresses adjacent verses
     whose content is too similar.

     In a slightly half-hearted attempt to get round this, I surround the
     content on alternative calls to the present method with hi:normal.  This
     isn't _guaranteed_ to work -- we might create an empty v1 early on in the
     processing and an empty v2 later, and the toggle may have been altered in
     between the two calls in an unfortunate way, but hopefully it will be good
     enough. */

  private fun makeNullMarkupToPreventSimilarContentBeingSuppressed (doc: Document, content: String): Node
  {
    m_Toggle = !m_Toggle
    return if (m_Toggle)
      Dom.createTextNode(doc, content)
    else
      nullMarkupToPreventEmpty(doc, content)
  }


  /****************************************************************************/
  private val m_Content_Elision = Translations.stringFormatWithLookup("V_contentForEmptyVerse_verseInElision")
  private val m_Content_EmptyVerse = Translations.stringFormatWithLookup("V_contentForEmptyVerse_verseEmptyInThisTranslation")
  private val m_Content_MissingVerse = Translations.stringFormatWithLookup("V_contentForEmptyVerse_verseWasMissing")
  private var m_Toggle = false
}





/******************************************************************************/
object Osis_EmptyVerseHandler : Z_EmptyVerseHandler(Osis_FileProtocol)
{
  override fun nullMarkupToPreventEmpty (doc: Document, content: String): Node
  {
    val x = Dom.createNode(doc, "<hi type='normal'/>")
    x.appendChild(Dom.createTextNode(doc, content))
    return x
  }
}


/******************************************************************************/
object Usx_EmptyVerseHandler : Z_EmptyVerseHandler(Usx_FileProtocol)
{
  override fun nullMarkupToPreventEmpty (doc: Document, content: String): Node
  {
    TODO()
    val x = Dom.createNode(doc, "<hi type='normal'/>")
    x.appendChild(Dom.createTextNode(doc, content))
    return x
  }
}
