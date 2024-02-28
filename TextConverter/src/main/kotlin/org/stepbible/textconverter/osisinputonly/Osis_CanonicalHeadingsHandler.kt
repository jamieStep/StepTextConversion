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
 * This class deals with only a single aspect of things: texts may already be
 * marked up with canonical title tags (USX para:d or OSIS psalm:title) or
 * they may not; and so for the sake of later processing it is convenient
 * to reduce things to a common form:
 *
 * - If we are doing conversion-time reversification, I drop any title markup
 *   altogether, promoting the nodes which make up the title.
 *
 * - If we are _not_ doing conversion-time reversification (ie if we are
 *   doing runtime reversification or not applying reversification at all,
 *   both of which largely entail leaving the text as-is), I replace the
 *   title markup by formatting markup which achieves the same appearance
 *   when rendered.
 *
 *
 * Both of these may seem rather odd at first sight ...
 *
 * Why drop existing markup in the case of conversion-time reversification?
 * Because as far as that processing is concerned, a canonical title is marked
 * not by having a specific canonical title tag, but by the existence of
 * canonical text prior to v1.  Dropping the tag does not lose us anything from
 * that point of view, but it makes things more uniform because different
 * translators have different ideas regarding canonical title markup.
 *
 * And why change the tag when not doing conversion-time reversification?
 * Because para:d / psalm:title is a div-type tag, which can give a problem with
 * cross-boundary markup.  The change I make turns this into a span-type tag,
 * and I have processing which can cope with that.
 *
 *
 * One final thing.  All of the above relates to canonical titles at the *start*
 * of chapters.  A few psalms have canonical titles at the end.  Here, too, I
 * change the markup to a form of formatting markup -- this time because
 * STEPBible goes wrong when it encounters canonical title tags at the ends of
 * chapters, and starts putting verse numbers in the wrong place.
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
    val rootNode = m_DataCollection.getRootNode(BibleAnatomy.C_BookNumberForPsa) ?: return // 'return' because we may be processing a text which lacks Psalms, or may not be processing Psalms on this run.
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
  /* Replaces anything actually marked up as a canonical title (psalm:title)
     with a span-type markup.  This loses semantics, but it has the advantage
     of moving from a para markup which may give rise to cross-boundary
     markup to a spa-style markup which I can cope with. */

  private fun convertCanonicalTitleToFormattingMarkup (titleNodes: List<Node>, whereToPutPara: Char)
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
    /**************************************************************************/
    /* This gives a Pair containing two lists, the first containing all of the
       canonical title details which appear at the start of chapters, and the
       second giving those which appear at the end.  Just to be clear, we are
       looking for things actually marked para:d (in USX) or psalm:title (in
       OSIS); in general canonical titles are taken as any canonical text prior
       to v1, regardless of how that text was marked, but here we are
       concerned with stuff already specifically marked up. */

    val titleNodes = identifyCanonicalTitleLocations(chapterNode.getAllNodes())



    /**************************************************************************/
    /* If we're dealing with conversion-time reversification (ie if we're going
       to be physically restructuring the text), it will make things more
       uniform if we promote the content of any of these existing title nodes,
       and delete the nodes themselves.

       Otherwise, I simply replace the psalm:title with a span-type markup. */

    if ("conversiontime" == ConfigData["stepReversificationType"]!!)
      titleNodes.first.forEach { Dom.promoteChildren(it); Dom.deleteNode(it) } // Promote children of title node and then delete the node itself.
    else
      convertCanonicalTitleToFormattingMarkup(titleNodes.first, 'A')

    convertCanonicalTitleToFormattingMarkup(titleNodes.second, 'B')
 }


  /****************************************************************************/
  private val m_DataCollection = dataCollection
}
