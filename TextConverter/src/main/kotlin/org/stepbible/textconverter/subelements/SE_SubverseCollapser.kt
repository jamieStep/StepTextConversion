package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.configdata.ConfigData
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
 * This is basically an issue only for public modules ...
 *
 * If we are running a module through our own osis2mod etc, subverses are not,
 * so far as I know, a problem.  If they appear in the input, the
 * reversification data should be able to cope.  If they appear in the output,
 * our modified version of JSword should cope.
 *
 * The problem comes where we have decided *not* to use our own osis2mod --
 * where we are using the Crosswire version because we wish to generate a
 * publicly-accessible version.
 *
 * Crosswire's osis2mod / JSword cannot, so far as I know, cope with subverses.
 * We therefore need to collapse them into the owning verse in this case,
 * marking their original boundaries (or for that matter not doing so) as
 * will be apparent below.  (I'm deliberately not giving details here because
 * we change our minds.)
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
    if ("P" !in ConfigData["stepTargetAudience"]!!) return
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
