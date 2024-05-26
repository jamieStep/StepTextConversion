package org.stepbible.textconverter.osisinputonly

import org.stepbible.textconverter.support.bibledetails.BibleAnatomy
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node


/******************************************************************************/
/**
 * Canonical titles are awkward, because the different versification traditions
 * handle them different, and also different translators make them up in
 * different ways.
 *
 * The purpose of the present class is to make changes as necessary in order
 * to ensure both that they end up in a form which reversification processing
 * can handle and -- ultimately -- obviously that they end up looking correct.
 *
 * I won't go into further detail here because the processing currently is still
 * subject to change as we come across new texts which do things in novel ways.
 * The detail appears within the head-of-method comments below.
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
    rootNode.findNodesByName("chapter").forEach(::doIt)
    m_DataCollection.getProcessRegistry().iHaveDone(this, listOf(ProcessRegistry.CanonicalHeadingsCanonicalised))
    //Dbg.d(rootNode.ownerDocument)
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
  /* There are a few Psalms where the canonical title contains the content of
     what, in other texts, comprises the first _two_ verses.  In some texts we
     have something like:

       <para:d><v1>Blah</v1></para:d>  <para:d><v2>Blah</v2></para:d>

     Or in other words, we have _two_ canonical title tags, each containing
     one verse.  Later processing is simplified if we combine the two into a
     single tag. */

  private fun combineAdjacentCanonicalHeaders (titleNodesInChapter: List<Node>): List<Node>
  {
    if (titleNodesInChapter.size < 2) // Nothing to combine if fewer than two titles.
      return titleNodesInChapter

    //Dbg.d(Dom.toString(titleNodesInChapter[0].parentNode))
    //Dbg.d(titleNodesInChapter[0].ownerDocument)

    if (!Dom.nodesAreAdjacent(titleNodesInChapter[0], titleNodesInChapter[1], ignoreWhitespace = true)) // Can't combine titles which aren't adjacent.
      return titleNodesInChapter

    val childNodesOfSecondNode = Dom.getChildren(titleNodesInChapter[1])
    Dom.deleteNode(titleNodesInChapter[1])
    Dom.addChildren(titleNodesInChapter[0], childNodesOfSecondNode)
    return listOf(titleNodesInChapter[0]) // There's now only the one title node in the chapter.
  }


  /****************************************************************************/
  private fun doIt (chapterNode: Node)
  {
    /**************************************************************************/
    /* This gives a Pair containing two lists, the first containing all of the
       canonical title details which appear at the start of chapters, and the
       second giving those which appear at the end.  Just to be clear, we are
       looking specifically for things actually marked para:d (in USX) or
       psalm:title (in OSIS); elsewhere canonical titles are taken as any
       canonical text prior to v1, regardless of how that text was marked,
       but here we are concerned with stuff already specifically marked up. */

    val titleNodes = identifyCanonicalTitleLocations(chapterNode.getAllNodesBelow()).first
    if (titleNodes.isEmpty())
      return



    /**************************************************************************/
    /* If we get this far, we have either one or two title nodes at the start
       of the chapter.  If we have two, we combine them.  As a result, there is
       only one title node for handleCasesWhereCanonicalTitleOverlapsWithVerse
       to handle. */

    combineAdjacentCanonicalHeaders(titleNodes)
    handleCasesWhereCanonicalTitleOverlapsWithVerse(titleNodes[0])
}


  /****************************************************************************/
  /* This receives all canonical title nodes, and processes some where the
     canonical title _contains_ the v1 marker.

     (In what follows, my examples are based upon USX markup, but the processing
     here should work with either USX or OSIS.)

     Unfortunately, life is complicated by the fact that not only do different
     versification traditions differ as to whether the record canonical titles
     separately or include them in v1; but also different texts have different
     approaches to markup.

     We are concerned here with two particular approaches, both distinguishable
     from the various others by the fact that they have a canonical title tag,
     and that a verse marker appears within that tag.

     The first alternative occurs where we have something like:

       <para:d><v1>The entire text of v1</para>

    In other words, it occurs where the whole of the first verse has been
    enclosed in the canonical heading tag.

    This, I believe, needs to be left as-is.


    The other alternative looks something like:

      <para:d><v1>This is heading text</para>This is verse 1 text proper ...

    or in other words where we have cross-boundary markup in which v1 contains
    both the text which should appear in the canonical header and text which
    in other Bibles would appear in v1 itself.

    This we do need to address -- by which I mean it needs to turn into:

      <para:d>This is the heading</para><v1> This is verse 1 text proper ...


   There is on further wrinkle here, in that the need for this processing was
   uncovered after we encountered one particular text which looked like this,
   and that text also had a <para:b> to force a blank line before 'This is
   verse 1 text proper'.  After making the above change, this blank line is
   no longer needed, so I remove it.


   Note that there are a few psalms which the canonical title contains what,
   in other Bibles, would be not the first verse, but the first two verses.
   I have not got my head around what the issues might be here, so at present
   I am adopting the simple expedient of assuming there _are_ none. */

  private fun handleCasesWhereCanonicalTitleOverlapsWithVerse (titleNode: Node)
  {
    /**************************************************************************/
    /* Extracts the text content from the header, strips whitespace, and
       returns the length of the string. */

    val fileProtocol = m_DataCollection.getFileProtocol()
    fun getHeaderContentLength (node: Node): Int
    {
      var res = 0
      Dom.getAllNodesBelow(node).forEach {
        if (fileProtocol.isCanonicalNode(it) && Dom.isTextNode(it))
          res += it.textContent.replace("\\s+".toRegex(), "").length
      }

      return res
    }



    /**************************************************************************/
    /* We're interested only in header nodes which contain verse tags. */

    val v1 = Dom.findNodeByName(titleNode, "verse", false) ?: return



    /**************************************************************************/
    var canonicalTitleTextLength = 0
    var v1Length = 0
    var accumulating = false



    /**************************************************************************/
    /* Get the owning chapter, and then an appropriately ordered list of all of
       the nodes in the chapter.  When we hit the canonical title, we obtain
       the length of its text content.  When we hit the first verse node, we
       start accumulating canonical textual content for the verse, and we
       continue doing that until we hit the verse start for the first verse
       outside of the canonical title tag. */

    val chapterNode = Dom.findAncestorByNodeName(v1, fileProtocol.tagName_chapter())!!
    for (node in Dom.getAllNodesBelow(chapterNode))
    {
      if (fileProtocol.isCanonicalTitleNode(node))
        canonicalTitleTextLength = getHeaderContentLength(node)

      else if (fileProtocol.tagName_verse() == Dom.getNodeName(node) && fileProtocol.attrName_verseSid() in node)
      {
        if (Dom.isAncestorOf(titleNode, node))
          accumulating = true
        else
          break
      }

      else if (accumulating && fileProtocol.isCanonicalNode(node) && Dom.isTextNode(node))
        v1Length += node.textContent.replace("\\s+".toRegex(), "").length
    } // forChapterNode.



    /**************************************************************************/
    /* See if the canonical title and v1 coincide.  If so, leave as-is. */

    if (v1Length == canonicalTitleTextLength)
      return



    /**************************************************************************/
    /* The more complicated case -- where we need to remove some text from v1
       and put it into the header. */

    Dom.deleteNode(v1)
    Dom.insertNodeAfter(titleNode, v1)
    var nodeForConsideration = v1.nextSibling



    /**************************************************************************/
    /* Deletes consecutive blank nodes and returns the first non-blank one. */

    fun deleteBlank (node: Node) = Dom.iterateOverYoungerSiblingsSatisfyingCondition(node, fn = Dom::deleteNode, test = Dom::isWhitespace, giveUpOnFailedTest = true, includeStart = true).second



    /**************************************************************************/
    /* Rather half-hearted.  This looks to see if the text was originally
       forcing a blank line between the heading text and the verse text.
       If it was, this is no longer needed now we have moved stuff into the
       heading tag.  I say this is half-hearted because at present it works
       only on OSIS which originated from USX and carries temporary tags like
       _usx to tell us what the original looked like.  May have to beef this
       up at some point. */

    nodeForConsideration = deleteBlank(nodeForConsideration)
    if (null != nodeForConsideration && "_usx" in nodeForConsideration && "para:b" == nodeForConsideration["_usx"])
    {
      val x = nodeForConsideration.nextSibling
      Dom.deleteNode(nodeForConsideration)
      nodeForConsideration = x
      deleteBlank(nodeForConsideration)
    }
  } // fun


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
  private val m_DataCollection = dataCollection
}
