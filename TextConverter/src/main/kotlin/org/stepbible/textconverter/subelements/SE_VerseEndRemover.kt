package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node


/******************************************************************************/
/**
* Verse ends are not mandatory in all of the inputs we have; and even if we
* have them, they may need repositioning in order to minimise cross-verse-
* boundary markup.
*
* It is therefore convenient to delete all of them, in order to get all inputs
* into the same shape.  They are reinstated later in 'optimal' (?) positions.
*
* At the same time, OSIS may have enclosing verses rather than milestones, and
* although it's kinda sad to replace them with milestones, doing so will
* simplify later processing by ensuring that all texts look the same.  And at
* the same time, I also need to make sure that the verse markers all carry
* sids.
*
* (I'm not sure whether this is an issue for USX as well: the code below, in
* so far as it targets enclosing nodes, is aimed at OSIS, and will simply do
* nothing on USX.)
*
* @author ARA "Jamie" Jamieson
*/

class SE_VerseEndRemover (dataCollection: X_DataCollection) : SE(dataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Protected                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun thingsIveDone() = listOf(ProcessRegistry.VerseMarkersReducedToSidsOnly)


  /****************************************************************************/
  override fun processRootNodeInternal (rootNode: Node)
  {
    Dbg.reportProgress("Deleting any existing verse ends for ${m_FileProtocol.getBookAbbreviation(rootNode)}.")
    deleteVerseEnds(rootNode)
    convertEnclosingVersesToSids(rootNode)
    markVersesWithSids(rootNode)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* The OSIS reference manual strongly counsels against using enclosing tags
     for verses.  Which of course means that I've encountered texts which have
     them.  It seems an awful shame to ditch them all, but to avoid complicating
     later processing, it's going to be easier if they are converted to sids.

     I can't recall offhand whether this would also be an issue for USX, but I'm
     assuming pro tem that if it _isn't_, running the following code will simply
     do nothing. */

  private fun convertEnclosingVersesToSids (rootNode: Node)
  {
    rootNode.findNodesByName(m_FileProtocol.tagName_verse(), false)
      .filter { it.hasChildNodes() }
      .forEach {
        Dom.promoteChildren(it)
      }
  }


  /****************************************************************************/
  /* Remove all existing verse ends, so we can insert them in what we see as
     the optimal position. */

  private fun deleteVerseEnds (rootNode: Node)
  {
    rootNode.findNodesByName(m_FileProtocol.tagName_chapter(), false).forEach { chapterNode ->
      chapterNode.findNodesByAttributeName(m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseEid()).forEach(Dom::deleteNode)
    }
  }



  /****************************************************************************/
  /* As for convertEnclosingVersesToSids, this _may_ be an OSIS-only issue, but
     running it against USX will simply leave things unchanged.

     If OSIS had enclosing nodes (rather than milestones), they will have had
     an osisID but not a sID.  I need to copy the osisID to the sID. */

  private fun markVersesWithSids (rootNode: Node)
  {
    rootNode.findNodesByName(m_FileProtocol.tagName_verse(), false)
      .filter { "osisID" in it && "sID" !in it }
      .forEach { it["sID"] = it["osisID"]!! }
  }
}