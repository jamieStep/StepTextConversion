package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node


/******************************************************************************/
/**
* This class deals with verse eids.
*
* Because we cannot guarantee that earlier processing stages will always have
* supplied verse ends, earlier processing here will have removed any verse ends
* we *do* have so as to reduce things to a common state.
*
* We can't create OSIS suitable for saving in InputOsis for future use or for
* supply to a third party without adding verse ends, however.  Ideally I'd
* put these verse ends in an optimal position with a view to avoiding cross-
* boundary markup.  Unfortunately to do that requires a certain amount of
* violence to the structure (I have to replace semantic tags for canonical
* headings by formatting markup, for instance, and this would lose semantic
* information which third parties might prefer to retain).
*
* I therefore do the absolute minimum here, inserting verse ends immediately
* before the next verse start.  This gives us something we can make available
* for future use, even if from our own point of view it's not ideal.  I sort
* out better positioning later.
*
* @author ARA "Jamie" Jamieson
*/

class SE_BasicVerseEndInserter (dataCollection: X_DataCollection) : SE(dataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun myPrerequisites() = listOf(ProcessRegistry.VerseMarkersReducedToSidsOnly)
  override fun thingsIveDone() = listOf(ProcessRegistry.BasicVerseEndPositioning)


  /****************************************************************************/
  override fun processRootNodeInternal (rootNode: Node)
  {
    Dbg.reportProgress("Handling initial placement of verse-ends: ${m_FileProtocol.getBookAbbreviation(rootNode)}.")
    rootNode.findNodesByName(m_FileProtocol.tagName_chapter()).forEach(::insertVerseEnds)
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