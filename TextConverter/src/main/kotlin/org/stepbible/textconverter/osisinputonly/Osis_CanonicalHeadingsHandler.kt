package org.stepbible.textconverter.osisinputonly

import org.stepbible.textconverter.support.bibledetails.BibleAnatomy
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node


/******************************************************************************/
/**
 * Converts canonical titles to a form more amenable to processing.  Canonical
 * titles are complicated for a number of reasons -- main among them that
 * different translators represent them differently (eg do they have verse
 * numbers within them or not, is there only a single one at the start of a
 * chapter or might you have more than one, etc); because left as enclosing
 * nodes they may give rise to cross-boundary markup; and because they also
 * interact with reversification.
 *
 * As regards cross-boundary markup, the problem can be handled relatively
 * easily.  Looking at existing modules, canonical titles seem to be rendered
 * merely in standard italicised text on a line of their own.  The 'line of
 * their own' bit can be addressed by introducing an empty para.  And the
 * italics can be handled by a span-type tag -- which is something verse-end
 * placement can cope with.
 *
 * As regards reversification, there are three different situations to
 * consider:
 *
 * - The text may already be NRSVA-compliant (or may deviate only in a 'good'
 *   way).  In this case, but for the change of tagging already mentioned,
 *   I should be able to leave things as they are -- and indeed _ought_ to
 *   do that, to avoid changing the appearance of the text.
 *
 * - The same also holds good for those cases where we will be using our
 *   own osis2mod: we need to retain something akin to the original formatting
 *   in order to stay compliant with any licensing conditions, so I simply make
 *   the mild formatting change discussed above, and hope that any
 *   reversification information I supply to osis2mod will do the trick.
 *
 * - This just leaves those (probably very few) cases where we are actually
 *   going to restructure the text during the conversion process in order to
 *   render it NRSVA-compliant.  Given that at least some texts will lack
 *   any markup, I work here on the assumption that the best thing to do is
 *   reduce all texts to a common denominator by removing the markup
 *   altogether.
 *
  * @author ARA "Jamie" Jamieson
 */

class Osis_CanonicalHeadingsHandler (dataCollection: X_DataCollection)
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
  * Processes all canonical headings in Psalms.
  */

  fun process ()
  {
    //Dbg.d(m_DataCollection.getDocument())
    val rootNode = m_DataCollection.getRootNode(BibleAnatomy.C_BookNumberForPsa) ?: return // May not be processing Psalms.
    Dbg.reportProgress("Handling canonical headings.")
    rootNode.findNodesByName("chapter").forEach(::processCanonicalTitles)
    m_DataCollection.getProcessRegistry().iHaveDone(this, listOf(ProcessRegistry.CanonicalHeadingsCanonicalised))
    //Dbg.d(m_DataCollection.getDocument())
  }






  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Marks canonical titles which appear near the start of chapters, since
     these are the only ones we need to work with.  I make the assumption that
     once I hit chapter C_VerseCountThreshold, we're past the start of the
     chapter and therefore need not do anything else. */

  private fun identifyCanonicalTitleLocations (allNodesInChapter: List<Node>): Pair<List<Node>, List<Node>>
  {
    /**************************************************************************/
    val C_VerseCountThreshold = 3
    val titlesAtStart: MutableList<Node> = mutableListOf()
    val titlesAtEnd  : MutableList<Node> = mutableListOf()
    var verseCount = 0



    /**************************************************************************/
    allNodesInChapter.forEach {
      if ("verse" == Dom.getNodeName(it))
        ++verseCount
      else if (Osis_FileProtocol.isCanonicalTitleNode(it))
      {
        if (verseCount >= C_VerseCountThreshold)
          titlesAtEnd.add(it)
        else
          titlesAtStart.add(it)
      }
    }



    /**************************************************************************/
    return Pair(titlesAtStart, titlesAtEnd)
  }


  /****************************************************************************/
  private fun processCanonicalTitles (chapterNode: Node)
  {
    val titleNodes = identifyCanonicalTitleLocations(chapterNode.getAllNodes())

    if ("conversiontime" == ConfigData["stepReversificationType"]!!)
      titleNodes.first.forEach { Dom.promoteChildren(it); Dom.deleteNode(it) } // Promote children of title node and then delete the node itself.
    else
      processCanonicalTitles(titleNodes.first, 'A')

    processCanonicalTitles(titleNodes.second, 'B')
 }


  /****************************************************************************/
  /* Replaces any canonical title nodes with alternative markup. */

  private fun processCanonicalTitles (titleNodes: List<Node>, whereToPutPara: Char)
  {
    titleNodes.forEach {
      Dom.setNodeName(it, "hi") // Want to turn the header into italic.
      Dom.deleteAllAttributes(it)
      it["type"] = "italic"

      val paraNode = titleNodes[0].ownerDocument.createNode("<p/>")
      if ('A' == whereToPutPara)
        Dom.insertNodeAfter(it, paraNode)
      else
        Dom.insertNodeBefore(it, paraNode)
    }
  }


  /****************************************************************************/
  private val m_DataCollection = dataCollection
}
