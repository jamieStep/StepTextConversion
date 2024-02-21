package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.contains
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.miscellaneous.set
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node

/******************************************************************************/
/**
 * Collapses subverses into the owning verse.
 *
 * At present I think the jury is still rather out on this.  Crosswire's
 * osis2mod / JSword cannot, so far as I know, cope with subverses.  Our own
 * can, at least in theory, although at the time of writing I'm not entirely
 * convinced it always receives enough information to be able to do so.
 *
 * We have a number of cases to consider:
 *
 * - We may have a raw text which is fully NRSV(A) compliant, or at most lacks
 *   some verses which NRSV(A) expects.  To be fully compliant, it would have
 *   to be without subverses, so there is definitely nothing for the present
 *   processing to do there.
 *
 * - We may have a text which is NRSV(A) compliant in the above sense but for
 *   the existence of subverses.  We have said here that we will definitely
 *   use our own osis2mod on this because it can cope with subverses.  I don't
 *   think it would need to be reversified (and if it were, reversification
 *   presumably would not do anything), so there *will* be subverses in the
 *   module, and we are indeed reliant upon our stuff to cope.
 *
 * - We may have a text which requires reversification, and we may decide to
 *   apply conversion-time reversification.  This is supposed to create an
 *   OSIS which is fully NRSV(A) compliant, so we would be reliant upon
 *   reversification a) turning any already-existing subverses into verses;
 *   and b) not creating any subverses.  I have not been asked to collapse
 *   any subverses here, but rather to report them as indicative of drop-offs
 *   in the reversification data.
 *
 * - We may have a text which requires reversification, and we may decide to
 *   apply runtime reversification.  In this case the idea is that we don't
 *   apply reversification-related changes up-front: the text is passed
 *   through as-is, and it's down to our stuff to cope.  I'm not too sure
 *   about this case, but at present I'm assuming that the text just is what
 *   it is, and our osis2mod etc has to be able to handle it.
 *
 *
 * All of which suggests that this class presently doesn't need to do anything
 * at all.  I've retained it against the inevitable time when we decide that it
 * *does* need to do something after all, but at present the main method simply
 * returns immediately.
 *
 * Incidentally, the reason I have my doubts about our stuff always coping is
 * that as presently defined, it does not always receive information about the
 * existence of subverses, and I think it does need this information ahead of
 * time.
 *
 * It receives information about the structure of the text (which books, which
 * chapters per book, which *verses* per book), but this information says
 * nothing about subverses.  And it receives information about applicable
 * reversification Moves and Renumbers.  These will tell it about subverses if
 * the Move or Renumber begins with a subverse or ends with one.  But it won't
 * hear about subverses which were there in the raw text and which are subject
 * to reversification KeepVerse; and nor will it hear about subverses which
 * appeared in the raw text and were not subject to any reversification
 * statement at all.
 *
 * @author ARA "Jamie" Jamieson
 */

open class SE_SubverseCollapser (dataCollection: X_DataCollection): SE(dataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun myPrerequisites() = listOf(ProcessRegistry.EnhancedVerseEndPositioning)
  override fun thingsIveDone () = listOf(ProcessRegistry.SubversesCollapsed)


  /****************************************************************************/
  /**
   * We may have subverses at present - either because they were present in the
   * raw USX or because reversification has created them.  At the time of
   * writing, we have made a decision that they should be collapsed into the
   * owning verse, which is handled by this present method. */

  override fun processRootNodeInternal (rootNode: Node)
  {
    return // $$$ See head-of-class comments.
    Dbg.reportProgress("Handling subverses.")
    Dom.findNodesByName(rootNode, m_FileProtocol.tagName_chapter(), false).forEach { doChapter(it) }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun doChapter (chapter: Node)
  {
    /**************************************************************************/
    //Dbg.d(chapter)
    //if (Dbg.dCont(Dom.toString(chapter), "13"))
    //  Dbg.outputDom(document)



    /**************************************************************************/
    val allVerses = Dom.findNodesByName(chapter, "verse", false)
    val (allSids, allEids) = allVerses.partition { m_FileProtocol.attrName_verseSid() in it }
    val sidToEidMapping = allSids.zip(allEids).toMap()
    val sidGroups = allSids.groupBy { m_FileProtocol.readRef(it, m_FileProtocol.attrName_verseSid()).toRefKey_bcv() } // Group together all sids for the same verse.



    /**************************************************************************/
    fun handleVerseEnd (sidNode: Node)
    {
      val eidNode = sidToEidMapping[sidNode]!!
      val separator = Dom.createNode(chapter.ownerDocument, "<_X_subverseSeparator/>")
      Dom.insertNodeBefore(eidNode, separator)
      Dom.deleteNode(eidNode)
    }



    /**************************************************************************/
    fun processGroup (verseRefKey: RefKey, group: List<Node>)
    {
      val lastRefKey = m_FileProtocol.readRef(group.last(), m_FileProtocol.attrName_verseSid()).toRefKey()

      if (!Ref.hasS(lastRefKey)) return // If the group doesn't end with a subverse, it can't have any subverses at all.

      if (1 == group.size)
      {
        Logger.error("Verse consisting of just a single subverse: ${Ref.rd(verseRefKey)}")
        return
      }

      val firstRefKey = m_FileProtocol.readRef(group[0], m_FileProtocol.attrName_verseSid()).toRefKey()
      val coverage = group[0][m_FileProtocol.attrName_verseSid()]!! + "-" + group.last()[m_FileProtocol.attrName_verseSid()]!!
      group.subList(1, group.size).forEach { Dom.deleteNode(it) }
      group[0][m_FileProtocol.attrName_verseSid()] = m_FileProtocol.refToString(Ref.clearS(firstRefKey))
      NodeMarker.setSubverseCoverage(group[0], coverage)

      group.subList(0, group.size - 1).forEach { handleVerseEnd(it) }
    }



    /**************************************************************************/
    sidGroups.forEach { processGroup(it.key, it.value) }
  }
}
