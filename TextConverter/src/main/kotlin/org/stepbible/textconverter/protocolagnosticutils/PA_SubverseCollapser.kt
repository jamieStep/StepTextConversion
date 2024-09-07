package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefKey
import org.stepbible.textconverter.applicationspecificutils.*
import org.w3c.dom.Node

/******************************************************************************/
/**
 * Collapses subverses into the owning verse.
 *
 * JSword cannot (I believe) cope with subverses.  This object collapses
 * subverses into the owning verse.
 *
 * @author ARA "Jamie" Jamieson
 */

object PA_SubverseCollapser: PA()
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
   * We may have subverses at present - either because they were present in the
   * raw USX or because reversification has created them.  At the time of
   * writing, we have made a decision that they should be collapsed into the
   * owning verse, which is handled by this present method
   * 
   * @param dataCollection Data to be processed.
   */

  fun process (dataCollection: X_DataCollection)
  {
    if ("P" !in ConfigData["stepTargetAudience"]!!) return
    extractCommonInformation(dataCollection)
    Dbg.reportProgress("Handling subverses.")
    dataCollection.getRootNodes().forEach { rootNode -> Dom.findNodesByName(rootNode, m_FileProtocol.tagName_chapter(), false).forEach(::doChapter) }
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
