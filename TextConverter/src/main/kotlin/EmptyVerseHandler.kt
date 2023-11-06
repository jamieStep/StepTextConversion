package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.*
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.support.miscellaneous.Translations.stringFormatWithLookup
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.usx.Usx
import org.stepbible.textconverter.C_CreateEmptyChapters
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.util.*



/******************************************************************************/
/**
 * Handles the creation and annotation of empty verses.
 *
 * Empty verses may turn up for a number of reasons.  The raw text may simply
 * contain verses which are empty.  We may create them as part of elision
 * processing (an elided verse covering verses 1-4, for instance, is turned
 * into three empty verses -- verses 1-3 -- and then verse 4 contains the
 * entire text).  Reversification may create them where necessary.  And
 * finally after all other processing is complete, we compare the verses now
 * available against those required by the selected versification scheme, and
 * create empty verses to fill in any gaps.
 *
 * It has turned out to be convenient to group together all of these various
 * pieces of functionality in one place so that I can coordinate things like
 * whether the verses are supplied with footnotes or not, whether they are
 * given a marker (like a dash) to indicate they are deliberately empty, etc.
 *
 *
 * CAUTION !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * The processing here doesn't create entirely new books, and probably should
 * not be changed to do so.  If you are working against a versification scheme
 * which requires a book and your text does not have it, it is almost certainly
 * because you are working with a partial translation, and in this case you
 * probably want the output to be similarly incomplete.  Whether this always
 * works -- in particular with DC books -- I don't know; but assume this is
 * correct until you know otherwise.
 *
 * @author ARA "Jamie" Jamieson
 */

object EmptyVerseHandler
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
   * The various other methods in this class let you create empty verses for
   * specific purposes.  They all of them add an _X_reasonEmpty attribute to
   * sids to explain why they have been created empty.
   *
   * If there remain any empty verses without this attribute, it must be
   * because they were empty in the raw USX, and we need to add content and
   * explanatory content to these too.
   *
   * This method addresses this requirement.
   *
   * @param document Document to be processed.
   * @return True if changes are made.
   */

  fun annotateEmptyVerses (document: Document): Boolean
  {
    /************************************************************************/
    var isEmpty = false
    var sid: Node? = null
    var res = false



    /************************************************************************/
    fun addContent ()
    {
      res = true
      Dom.insertNodeAfter(sid!!, makeReferenceSpecificFootnote(document, Dom.getAttribute(sid!!, "sid")!!, "V_emptyContentFootnote_verseEmptyInThisTranslation"))
      Dom.insertNodeAfter(sid!!, makeContent(document, m_Content_EmptyVerse))
    }


    /************************************************************************/
    /* Run over the entire document checking each verse to see whether it is
       empty and also whether it already has a footnote. */

    val nodes = Dom.collectNodesInTree(document)
    for (i in nodes.indices)
    {
      /**********************************************************************/
      when (Dom.getNodeName(nodes[i]))
      {
        /********************************************************************/
        "verse" ->
        {
          if (Dom.hasAttribute(nodes[i], "sid"))
          {
            isEmpty = !Dom.hasAttribute(nodes[i], "_X_reasonEmpty") // Assume verse is empty until we know otherwise.  Except that we pretend it's non-empty if it's already marked as empty.
            sid = nodes[i]
            continue
          }

          if (isEmpty)
          {
            Dom.setAttribute(sid!!, "_X_reasonEmpty", "verseEmptyInThisTranslation")
            addContent()
          }

          isEmpty = false
          sid = null

          continue
        } // verse



        /********************************************************************/
        else ->
        {
          if (null != sid && !Usx.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(nodes[i]))
            isEmpty = false
        }
      } // when
    } // for



    /************************************************************************/
    return res
  } // fun


  /****************************************************************************/
  /**
   * Creates an empty verse for use in elisions, and inserts it before the
   * given node.
   *
   * An empty elision verse does have a content, but is not given a footnote.
   *
   * @param doc Document within which nodes are created.
   *
   * @param id sid / eid to be used in new verse.
   *
   * @return A pair containing the sid and eid nodes.
   */

  fun createEmptyVerseForElision (doc: Document, id: String): Pair<Node, Node>
  {
    val template = "<verse ID _X_generatedReason='inElision' _X_reasonEmpty='inElision' _X_elided='y' _X_originalId='$id'/>"
    val start = Dom.createNode(doc, template.replace("ID", "sid='$id'"))
    val end   = Dom.createNode(doc, template.replace("ID", "eid='$id'"))
    return Pair(start, end)
  }


  /****************************************************************************/
  /**
   * Creates an empty verse for use in elisions, and inserts it before the
   * given node.
   *
   * An empty elision verse does have a content, but is not given a footnote.
   *
   * @param insertBefore Node before which the verse and its content are to be
   *   inserted.  This needs to be the verse which was originally flagged as the
   *   elision.
   *
   * @param sid sid to be used in new verse.
   *
   * @return A pair containing the sid and eid nodes.
   */

  fun createEmptyVerseForElisionAndInsert (insertBefore: Node, sid: String): Pair<Node, Node>
  {
    val elisionSid = Dom.getAttribute(insertBefore, "sid")
    val template = "<verse ID _X_generatedReason='inElision' _X_reasonEmpty='inElision' _X_elided='y' _X_originalId='$elisionSid'/>"
    val start = Dom.createNode(insertBefore.ownerDocument, template.replace("ID", "sid='$sid'"))
    val end   = Dom.createNode(insertBefore.ownerDocument, template.replace("ID", "eid='$sid'"))

    Dom.insertNodeBefore(insertBefore, start)
    Dom.insertNodeBefore(insertBefore, makeContent(insertBefore.ownerDocument, m_Content_Elision))
    Dom.insertNodeBefore(insertBefore, end)

    return Pair(start, end)
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
  * @param document
  * @return True if changes were made.
  */

  fun createEmptyVersesForAdHocVersificationScheme (document: Document): Boolean
  {
    /**************************************************************************/
    val bookCode = Dom.findNodeByName(document,"_X_book")!!["code"]!!
    val bookNo = BibleBookNamesUsx.nameToNumber(bookCode)



    /**************************************************************************/
    val holes = BibleStructure.UsxUnderConstructionInstance().getMissingEmbeddedVersesForBook(bookNo)
    if (holes.isEmpty()) return false



    /**************************************************************************/
    val map = getSidMap(document, "verse")
    holes.forEach { createEmptyVerseForMissingVerse(document, it, map[map.ceilingKey(it)]) }
    return true
  }


  /****************************************************************************/
  /**
  * Checks for missing verses within the given document (and also for missing
  * chapters) and fills things in as necessary.  This version is for the case
  * where we are dealing with a known Crosswire osis2mod scheme, and therefore
  * know what verses each chapter is supposed to contain.
  *
  * @param document
  * @return True if changes were made.
  */

  fun createEmptyVersesForKnownOsis2modScheme (document: Document): Boolean
  {
    /**************************************************************************/
    var res = false
    val osis2modStructure = BibleStructure.Osis2modSchemeInstance(ConfigData["stepVersificationSchemeCanonical"]!!, true)
    val bookNumber = BibleBookNamesUsx.abbreviatedNameToNumber(Dom.findNodeByName(document,"_X_book")!!["code"]!!)
    BibleStructure.UsxUnderConstructionInstance().populateFromDom(document, wantWordCount = false)
    val diffs = BibleStructure.compareWithGivenScheme(bookNumber, BibleStructure.UsxUnderConstructionInstance(), osis2modStructure)



    /**************************************************************************/
    if (diffs.chaptersInTargetSchemeButNotInTextUnderConstruction.isNotEmpty() && C_CreateEmptyChapters)
    {
      res = true
      val map = getSidMap(document, "_X_chapter")
      diffs.chaptersInTargetSchemeButNotInTextUnderConstruction.forEach {
        val ix = map.ceilingKey(it)
        val insertBefore = if (null == ix) null else map[ix]
        createEmptyChapter(document, it, insertBefore)
      }
    }



    /**************************************************************************/
    if (diffs.versesInTargetSchemeButNotInTextUnderConstruction.isNotEmpty())
    {
      var versesOfInterest = diffs.versesInTargetSchemeButNotInTextUnderConstruction
      //Dbg.d(versesOfInterest.map { Ref.rd(it).toString() }.joinToString(", "))
      if (!C_ConfigurationFlags_GenerateVersesAtEndsOfChapters)
      {
        val x = versesOfInterest.toSet()
        versesOfInterest = versesOfInterest.filter { Ref.getV(it) < BibleStructure.UsxUnderConstructionInstance().getLastVerseNo(it) }
        val delta = x - versesOfInterest.toSet()
        //delta.sorted().forEach { Dbg.d(">>>>> " + Ref.rd(it)).toString() }
      }

      if (versesOfInterest.isNotEmpty())
      {
        res = true
        val map = getSidMap(document, "verse")
        versesOfInterest.forEach { createEmptyVerseForMissingVerse(document, it, map[map.ceilingKey(it)]) }
      }
    }


    /**************************************************************************/
    return res
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
   * @param sid sid to be used in new verse.
   *
   * @return A pair containing the sid and eid nodes.
   */

  fun createEmptyVerseForReversification (insertBefore: Node, sid: String): Pair<Node, Node>
  {
    val template = "<verse ID _X_generatedReason='requiredByReversification' _X_revAction='generatedVerse' _X_reasonEmpty='requiredByReversification'/>"
    val start = Dom.createNode(insertBefore.ownerDocument, template.replace("ID", "sid='$sid'"))
    val end   = Dom.createNode(insertBefore.ownerDocument, template.replace("ID", "eid='$sid'"))

    Dom.insertNodeBefore(insertBefore, start)
    Dom.insertNodeBefore(insertBefore, end)

    return Pair(start, end)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun createEmptyChapter (document: Document, refKey: RefKey, insertBefore: Node?, generatedReason: String = "Not found on completion of processing", reasonEmpty: String = "chapterWasMissing"): Node
  {
    Logger.warning(refKey, "Created chapter which was missing from the original text.")

    val newChapterRefKey = Ref.clearV(Ref.clearS(refKey))
    val newChapterRefAsString = Ref.rd(newChapterRefKey).toStringUsx()

    val chapterNode = Dom.createNode(document, "<_X_chapter sid='$newChapterRefAsString' _X_generatedReason='$generatedReason' _X_reasonEmpty='$reasonEmpty'/>")
    val dummyVerseSidNode = Dom.createNode(document,"<verse _TEMP_dummy='y' sid='$newChapterRefAsString:${RefBase.C_BackstopVerseNumber}'/>")
    val dummyVerseEidNode = Dom.createNode(document,"<verse _TEMP_dummy='y' eid='$newChapterRefAsString:${RefBase.C_BackstopVerseNumber}'/>")
    chapterNode.appendChild(dummyVerseSidNode)
    chapterNode.appendChild(dummyVerseEidNode)

    if (null == insertBefore)
      Dom.findNodeByName(document, "_X_book")!!.appendChild(chapterNode)
    else
      Dom.insertNodeBefore(insertBefore, chapterNode)

    return chapterNode
  }


  /****************************************************************************/
  /* Creates an empty verse to fill in a gap in the versification, and inserts
     it before the given node, or at the end of the parent chapter if
     insertBefore is null.  Returns a pair consisting of the sid and the eid.

     Note 2023-09-20
     ---------------

     The commented-out Dom.insertNodeBefore line was what was originally in the
     code.  It looked up the target verse to see if we had a specific override
     for the footnote text.  However, I think this was specific to cases where
     we were doing reversification, and I don't _think_ this method gets
     called for that.  So I have replaced it with the two lines which refer to
     footnoteText.
  */
  private fun createEmptyVerseForMissingVerse (document: Document, refKey: RefKey, insertBefore: Node?, generatedReason: String = "Not found on completion of processing", reasonEmpty: String = "verseWasMissing"): Pair<Node, Node>
  {
    Logger.warning(refKey, "Created verse which was missing from the original text.")

    val sidAsString = Ref.rd(refKey).toString()
    val template = "<verse ID _X_generatedReason='$generatedReason' _X_reasonEmpty='$reasonEmpty'/>"
    val start = Dom.createNode(document, template.replace("ID", "sid='$sidAsString'"))
    val end   = Dom.createNode(document, template.replace("ID", "eid='$sidAsString'"))

    val ib = insertBefore ?: Dom.createNode(document,"<_TEMP/>")


    Dom.insertNodeBefore(ib, start)
    val footnoteNode = MiscellaneousUtils.makeFootnote(document, refKey, text = stringFormatWithLookup("V_emptyContentFootnote_verseEmptyInThisTranslation") , callout = ConfigData["stepExplanationCallout"])  // See note for 2023-09-20 above.
    Dom.insertNodeBefore(ib, footnoteNode)  // See note for 2023-09-20 above.
    // Dom.insertNodeBefore(ib, makeReferenceSpecificFootnote(document, sidAsString, "V_emptyContentFootnote_verseEmptyInThisTranslation"))  // See note for 2023-09-20 above.
    Dom.insertNodeBefore(ib, makeContent(document, m_Content_MissingVerse))
    Dom.insertNodeBefore(ib, end)

    if (null == insertBefore)
      Dom.deleteNode(ib)

    return Pair(start, end)
  }


  /****************************************************************************/
  private fun getSidMap (document: Document, nodeName: String): NavigableMap<RefKey, Node>
  {
    return Dom.findNodesByName(document, nodeName)
      .filter { Dom.hasAttribute(it, "sid") }
      .associateBy { Ref.rd(Dom.getAttribute(it, "sid")!!).toRefKey() }.toSortedMap() as NavigableMap<RefKey, Node>
  }


  /****************************************************************************/
  private fun makeReferenceSpecificFootnote (doc: Document, sid: String, dflt: String): Node
  {
    val refKey = Ref.rdUsx(sid).toRefKey()
    val dataRow = m_VerseSpecificFootnoteText[refKey]
    val footnoteText = if (null == dataRow) stringFormatWithLookup(dflt) else ReversificationData.getFootnoteA(dataRow)
    return MiscellaneousUtils.makeFootnote(doc, refKey, text = footnoteText , callout = ConfigData["stepExplanationCallout"])
  }


  /****************************************************************************/
  /* Most (all?) 'empty' verses do in fact have some content, either to make it
     clear that they have deliberately been left empty, or at least because
     osis2mod seems to suppress genuinely empty verses.  There is a further
     complication here in that consecutive verses with identical content are
     also suppressed, and this is a risk particularly if you have a run of empty
     verses arising from elision processing.  I get round this by enclosing
     the content with some markup on alternate calls to this method. */

  private fun makeContent (doc: Document, content: String): Node
  {
    val textNode = Dom.createTextNode(doc, content)
    val contentNode = if (m_Toggle) Dom.createNode(doc, "<char style='no'/>") else textNode
    if (contentNode !== textNode) contentNode.appendChild(textNode)
    m_Toggle = !m_Toggle
    return contentNode
  }


  /****************************************************************************/
  private val m_Content_Elision = stringFormatWithLookup("V_contentForEmptyVerse_verseInElision")
  private val m_Content_EmptyVerse = stringFormatWithLookup("V_contentForEmptyVerse_verseEmptyInThisTranslation")
  private val m_Content_MissingVerse = stringFormatWithLookup("V_contentForEmptyVerse_verseWasMissing")
  private var m_Toggle = true
  private val m_VerseSpecificFootnoteText: MutableMap<RefKey, ReversificationDataRow> = mutableMapOf()


  /****************************************************************************/
  init
  {
    ReversificationData.getIfEmptyRows().forEach { m_VerseSpecificFootnoteText[it.sourceRefAsRefKey] = it }
  }
}