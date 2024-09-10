package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.applicationspecificutils.*
import org.w3c.dom.Document
import org.w3c.dom.Node


/******************************************************************************/
/**
* This class deals with verse eids.
*
* Any OSIS we create from Builder_InitialOsisRepresentationFrom... objects is
* required to contain verse eids.  Not all of the input formats for which we
* cater guarantee this, so this class adds eids if necessary.
*
* It does this in a simple-minded manner, merely adding eids immediately before
* the next sid.  This is valid, and generates a representation suitable for
* later processing, although later processing may move the eids around
* (obviously within limits) to try to avoid cross-boundary markup.
*
* @author ARA "Jamie" Jamieson
*/

object PA_BasicVerseEndInserter: PA()
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
  * Processes the document.  If we already have at least one verse eid, I
  * assume all eids are already in place, and therefore do nothing.
  *
  * @param dataCollection
  */

  fun process (dataCollection: X_DataCollection)
  {
    extractCommonInformation(dataCollection)
    val rootNodes = dataCollection.getRootNodes()

    if (null != rootNodes[0].findNodeByAttributeName(m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseEid()))
      return // Already have eids.

    Dbg.withProcessingBooks("Handling initial placement of verse-ends ...") {
      rootNodes.forEach {
        Dbg.withProcessingBook(m_FileProtocol.getBookAbbreviation(it)) {
          it.findNodesByName(m_FileProtocol.tagName_chapter()).forEach(::insertVerseEnds)
        }
      }
    }
  }


  /****************************************************************************/
  /**
  * Processes the document.  If we already have at least one verse eid, I
  * assume all eids are already in place, and therefore do nothing.
  *
  * @param doc
  * @param fileProtocol
  */

  fun process (doc: Document, fileProtocol: X_FileProtocol)
  {
    m_FileProtocol = fileProtocol
    val rootNodes = m_FileProtocol.getBookNodes(doc)

    if (null != rootNodes[0].findNodeByAttributeName(m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseEid()))
      return // Already have eids.

    Dbg.withProcessingBooks("Handling initial placement of verse-ends ... ") {
      rootNodes.forEach {
        Dbg.withProcessingBook(m_FileProtocol.getBookAbbreviation(it)) {
          it.findNodesByName(m_FileProtocol.tagName_chapter()).forEach(::insertVerseEnds)
        }
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
  private fun insertVerseEnds (chapterNode: Node)
  {
    val verseNodes = chapterNode.findNodesByName(m_FileProtocol.tagName_verse())
    for (ix in 1 ..< verseNodes.size)
    {
      val eidNode = m_FileProtocol.makeVerseEidNode(chapterNode.ownerDocument, verseNodes[ix - 1][m_FileProtocol.attrName_verseSid()]!!)
      Dom.insertNodeBefore(verseNodes[ix], eidNode)
    }

    val eidNode = m_FileProtocol.makeVerseEidNode(chapterNode.ownerDocument, verseNodes.last()[m_FileProtocol.attrName_verseSid()]!!)
    chapterNode.appendChild(eidNode)
  }
}