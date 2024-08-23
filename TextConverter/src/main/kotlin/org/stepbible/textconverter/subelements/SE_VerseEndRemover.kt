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
    Dbg.reportProgress("- Preparing verse tags for ${m_FileProtocol.getBookAbbreviation(rootNode)}.")
    val allVerseTags = rootNode.findNodesByName(m_FileProtocol.tagName_verse(), false)

    allVerseTags.filter { it.hasChildNodes() } .forEach(Dom::promoteChildren) // Replace enclosing sids by a non-enclosing sid, followed by the children of the original.

    allVerseTags.forEach { // Delete eids, and ensure that any remaining verse tags are marked with a sid (the latter will simply be ignored when processing USX).
      if (m_FileProtocol.attrName_verseEid() in it)
        Dom.deleteNode(it)
      else if ("osisID" in it && "sID" !in it)
       it["sID"] = it["osisID"]!!
    }
  }
}