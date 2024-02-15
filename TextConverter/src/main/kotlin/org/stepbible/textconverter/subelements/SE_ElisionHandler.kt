package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.shared.Language
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node

/****************************************************************************/
/**
 * Expands elisions which are marked as such in the raw text, or ones
 * generated as a consequence of table expansion.
 *
 * Temporary attributes are used as follows:
 *
 * - inTableElision: The processing looks for this on the incoming sid
 *   to determine whether it is processing a table or a plain vanilla elision.
 *
 * - _inElision is added to all sids which form part of the elision.
 *
 * - _emptyVerseType is added to the empty sids which make up the
 *   elision.  It will have the value 'elision' or 'tableElision'.
 *
 * - _originalId appears on the master sid only, and gives the original
 *   (pre-expansion) sid for the elision.
 *
 * - _masterForElisionOfLength is added to the master sid only,
 *   and gives a count of the number of elements making up the elision.
 *
 *
 * A footnote is added to the master sid, but only where the number of elements
 * in the elision exceeds two.
 *
 * @author ARA "Jamie" Jamieson
 */

class SE_ElisionHandler (dataCollection: X_DataCollection): SE(dataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun process (rootNode: Node) = doIt(rootNode)





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun doIt (rootNode: Node)
  {
    requireHasRun(SE_TableHandler::class.java)

    if (!ConfigData.getAsBoolean("stepExpandElisions")) return
    Dbg.reportProgress("Handling elisions for ${m_FileProtocol.getBookAbbreviation(rootNode)}.")

    var elidedVerses = Dom.findNodesByAttributeName(rootNode, m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid()).filter { m_FileProtocol.readRefCollection(it, m_FileProtocol.attrName_verseSid()).isRange() }
    elidedVerses.forEach { processElision(it) }

    // Deal with all eids.
    elidedVerses = Dom.findNodesByAttributeName(rootNode, m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseEid()).filter { '-' in it[m_FileProtocol.attrName_verseEid()]!! }
    elidedVerses.forEach { it[m_FileProtocol.attrName_verseEid()] = it[m_FileProtocol.attrName_verseEid()]!!.split("-")[1] }
  }


  /****************************************************************************/
  private fun addTemporaryAttributesToEmptyVerse (verse: Node, elisionType: String)
  {
    NodeMarker.setElisionType(verse, elisionType)
    NodeMarker.setEmptyVerseType(verse, elisionType)
  }


  /****************************************************************************/
  private fun addTemporaryAttributesToMasterVerse (verse: Node, elisionType: String)
  {
    val sidAsString = verse[m_FileProtocol.attrName_verseSid()]!!
    val rc = m_FileProtocol.readRefCollection(sidAsString)
    NodeMarker.setOriginalId(verse, rc.toString()) // Force to USX format.
    NodeMarker.setElisionType(verse, elisionType)
    NodeMarker.setMasterForElisionOfLength(verse, "" + (rc.getAllAsRefs().size + 1))
  }


  /****************************************************************************/
  /* I don't worry about eIDs here -- I can pick those up at the end of the
     processing.  For general elisions (at present), the empty verses come
     first and then the master verse.  For table elisions, the master comes
     first followed by the empty verses. */

  private fun processElision (verse: Node)
  {
    val refKeys = m_FileProtocol.readRefCollection(verse, m_FileProtocol.attrName_verseSid()).getAllAsRefKeys()
    IssueAndInformationRecorder.elidedVerse(refKeys, verse[m_FileProtocol.attrName_verseSid()]!!)

    if ("tableElision" == NodeMarker.getElisionType(verse))
       refKeys.subList(1, refKeys.size).forEach {
          val nodeList = m_EmptyVerseHandler.createEmptyVerseForElision(verse.ownerDocument, it) // Start, end, content, and optionally footnote.
          // addTemporaryAttributesToEmptyVerse(nodeList[0], "tableElision") Not needed -- we know this is already present.
          m_FileProtocol.updateVerseSid(verse, refKeys[0])
          Dom.insertNodesAfter(verse, nodeList)
       }
     else
       refKeys.subList(0, refKeys.size - 1).forEach {
         val nodeList = m_EmptyVerseHandler.createEmptyVerseForElision(verse.ownerDocument, it) // Start, end, content, and optionally footnote.
         addTemporaryAttributesToEmptyVerse(nodeList[0], "elision")
          m_FileProtocol.updateVerseSid(verse, refKeys.last())
         Dom.insertNodesBefore(verse, nodeList)
      }

    addTemporaryAttributesToMasterVerse(verse, NodeMarker.getElisionType(verse) ?: "elision")
    m_FileProtocol.updateVerseSid(verse, refKeys.last())
    if (refKeys.size <= 2) return // No footnote on master verse if there is only one other verse in the elision.

    val footnote = m_FileProtocol.makeFootnoteNode(verse.ownerDocument, m_FileProtocol.readRefCollection(verse[m_FileProtocol.attrName_verseSid()]!!).getLowAsRefKey(), Translations.stringFormat(Language.Vernacular, "V_elision_containsTextFromOtherVerses", m_FileProtocol.readRefCollection(verse[m_FileProtocol.attrName_verseSid()]!!)))
    Dom.insertNodeAfter(verse, footnote)
    NodeMarker.setAddedFootnote(verse)
  }


  /****************************************************************************/
  private val m_EmptyVerseHandler = EmptyVerseHandler(dataCollection)
}
