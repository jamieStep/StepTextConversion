package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node


/****************************************************************************/
/**
 * Ad hoc tidying up -- for example tweaking stuff which otherwise seems to
 * come out wrong for no apparent reason.
 *
 * @author ARA "Jamie" Jamieson
 */

class SE_FinalInternalOsisTidier (dataCollection: X_DataCollection): SE(dataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun thingsIveDone () = listOf(ProcessRegistry.FeatureDataCollected)


  /****************************************************************************/
  override fun processRootNodeInternal (rootNode: Node)
  {
    Dbg.reportProgress("Last ditch tidying. ${m_FileProtocol.getBookAbbreviation(rootNode)}.")
    deleteTemporaryNodes(rootNode)
    handleUnacceptableCharacters(rootNode)
    handleAcrostic(rootNode)
    handleSelah(rootNode)
    handleSpeaker(rootNode)
    moveNotesInsideVerses(rootNode)
    collapseNestedStrongs(rootNode)
    handleCallouts(rootNode)
    deleteNotesFromHeaders(rootNode)
    handleHorizontalWhitespace(rootNode)
    handleVerticalWhitespace(rootNode)
    handle_X_tags(rootNode)
    NodeMarker.deleteAllMarkers(rootNode) // Remove any temporary markers.
    Dom.setNodeName(rootNode, "div") // Book nodes should be div, but we set them temporarily to <book> earlier.
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Some texts nest Strongs. */

  private fun collapseNestedStrongs (rootNode: Node)
  {
    while (true)
    {
      val nestedStrongs = Dom.findNodesByName(rootNode, m_FileProtocol.tagName_strong(), false)
        .filter { m_FileProtocol.attrName_strong() in it }
        .filter { it.parentNode.nodeName == m_FileProtocol.tagName_strong() }

      if (nestedStrongs.isEmpty()) break

      nestedStrongs.forEach {
        val parent = it.parentNode

        val lowerStrong = Dom.getAttribute(it, m_FileProtocol.attrName_strong())!!
        val upperStrong = Dom.getAttribute(parent, m_FileProtocol.attrName_strong())!!
        Dom.setAttribute(parent, m_FileProtocol.attrName_strong(), "$upperStrong $lowerStrong")

        val lowerContent = Dom.getChildren(it)
        lowerContent.forEach(Dom::deleteNode)
        Dom.addChildren(parent, lowerContent)

        Dom.deleteNode(it)
      }
    }
  }


  /****************************************************************************/
  private fun deleteNotesFromHeaders (rootNode: Node)
  {
    Dom.findNodesByName(rootNode, m_FileProtocol.tagName_chapter(), false).forEach(::deleteNotesFromHeadersInChapter)
  }


  /****************************************************************************/
  /* Experience shows that notes outside of verses give rise to callouts, but
     these aren't functional.  I therefore remove all notes nodes from headers
     outside of verses.  (Other notes outside of verses -- which are probably
     notes within canonical text which has been placed outside of verses --
     are flagged elsewhere as an error.) */

  private fun deleteNotesFromHeadersInChapter (chapterNode: Node)
  {
    var doneSomething = false
    var inVerse = false
    val allNodes = Dom.getNodesInTree(chapterNode)

    for (node in allNodes)
    {
      val nodeName = Dom.getNodeName(node)

      if (m_FileProtocol.tagName_verse() == nodeName)
        inVerse = m_FileProtocol.attrName_verseSid() in node
      else if (!inVerse && m_FileProtocol.isTitleNode(node) && !m_FileProtocol.isCanonicalTitleNode(node)) // The !isCanonicalTitleNode is there on the assumption notes in canonical titles work.  Need to check this.
        Dom.getNodesInTree(node).filter { m_FileProtocol.isNoteNode(it) }.forEach { Dom.deleteNode(it); doneSomething = true}
    }

    if (doneSomething)
    {
      Logger.warning(m_FileProtocol.readRef(chapterNode[m_FileProtocol.attrName_chapterSid()]!!).toRefKey(), "One or more <note> nodes suppressed in one or more non-canonical titles.")
      //Dbg.d(chapterNode.ownerDocument)
    }
  }


  /****************************************************************************/
  private fun deleteTemporaryNodes (rootNode: Node)
  {
    rootNode.getAllNodes().forEach {
      if (NodeMarker.hasDeleteMe(it))
        Dom.deleteNode(it)
    }
  }


  /****************************************************************************/
  /* A bit of a kluge.  The OSIS may end up containing _X_... tags (at the time
     of writing, only as a result of handling reversification / samification
     footnotes.

     Originally I was adding these during the USX processing, and could rely
     upon the USX-to-OSIS conversion processing to convert them to proper
     OSIS tags.  Now, however, I may also add them during the OSIS processing
     itself, and therefore need code to turn them into something 'proper'.

     If you make changes, below, you almost certainly want corresponding
     changes in usxToOsisTagConversionsEtc.conf at the places where the
     USX translations for the_X_ tags below are defined. */

  private fun handle_X_tags (rootNode: Node)
  {
    Dom.getNodesInTree(rootNode)
      .filter { Dom.getNodeName(it).startsWith("_X_") }
      .forEach {
        when (val nodeName = Dom.getNodeName(it))
        {
          "_X_reversificationCalloutAlternativeRefCollection" ->
          {
            Dom.setNodeName(it, "hi")
            it["style"] = "super"
            Dom.insertNodeAfter(it, Dom.createTextNode(rootNode.ownerDocument, " "))
          }



          "_X_reversificationCalloutSourceRefCollection" ->
          {
            Dom.setNodeName(it, "hi")
            it["style"] = "bold"
            Dom.insertNodeAfter(it, Dom.createTextNode(rootNode.ownerDocument, " "))
          }



          "_X_verseBoundaryWithinElidedTable" ->
          {
            Dom.deleteAllAttributes(it)
            it["type"] = "super"
            Dom.setNodeName(it, "hi")
          }



          "_X_subverseSeparator" -> // We may have marked subverse boundaries in earlier processing.  The latest view in this is that we don't want them in the output after all.
          {
            Dom.deleteNode(it)
          }



          else ->
            throw StepException("process_X_tags bad case: $nodeName")
        }
      }
  }


  /****************************************************************************/
  /* Acrostic elements come in two forms -- as titles and as hi:acrostic. In
     fact, STEP doesn't render either of them correctly, so we need to convert
     them to something else. */

  private fun handleAcrostic (rootNode: Node)
  {
    /************************************************************************/
    /* The span-type form. */

    val allNodes = Dom.getNodesInTree(rootNode)
    allNodes.filter { "hi:acrostic" == m_FileProtocol.getExtendedNodeName(it) } .forEach {
      it["type"] = "italic"
    }



    /**************************************************************************/
    /* The title form.  The bold/italic version used here represents DIB's
       preferred option.

       To make the native title:acrostic acceptable, you'd need a change in the
       stylesheet (STEP\step-web\scss\step-template.css):

         h3.canonicalHeading.acrostic {
           font-style: italic;
           font-weight: bold;
           color: #333;
           margin-bottom: 0;
           margin-left: 0;
           margin-top: 20px;
         } */

    allNodes.filter { "title:acrostic" == m_FileProtocol.getExtendedNodeName(it) } .forEach {
      // Turn the node itself into hi:italic.
      Dom.setNodeName(it, "hi"); Dom.deleteAllAttributes(it); it["type"] = "italic"

      // Create a bold node, insert it before the node itself, and then move the target node into the bold node.
      val bold = it.ownerDocument.createNode("<hi type='bold'/>")
      Dom.insertNodeBefore(it, bold)
      Dom.deleteNode(it)
      bold.appendChild(it)
    }
  }


  /****************************************************************************/
  private fun handleCallouts (rootNode: Node)
  {
    var notes = Dom.findNodesByName(rootNode, "note", false)
    notes = (notes.toSet() subtract handleCallouts_preventCommasBeingRenderedBetweenAdjacentCallouts(notes).toSet()).toList()
    handleCallouts_preventCommasBeingSuppressedBetweenAdjacentCallouts(notes)
  }


  /****************************************************************************/
  /* Two consecutive note markers in the OSIS end up being separated by a
     comma when rendered.  I've been asked to prevent this, and inserting a
     space between the two seems to do the trick.  I also check here that
     the two have the same callout, since I suspect we do want the comma if,
     for instance, the callouts are numbers (not that, at the time of writing,
     we ever get that, because all callouts seem to be rendered as down-
     arrows). */

  private fun handleCallouts_preventCommasBeingRenderedBetweenAdjacentCallouts (notes: List<Node>): List<Node>
  {
    val res: MutableList<Node> = mutableListOf()
    for (i in 0 ..< notes.size - 1)
      if (notes[i].nextSibling === notes[i + 1] && notes[i]["n"]!! == notes[i + 1]["n"]!!)
      {
        val textNode = Dom.createTextNode(notes[0].ownerDocument, " ")
        Dom.insertNodeAfter(notes[i], textNode)
        res += notes[i]
      }

    return res
  }


  /****************************************************************************/
  /* handleCallouts_preventCommasBeingRenderedBetweenAdjacentCallouts deals
     with the situation where we do not have commas between consecutive note
     callouts, and want to discourage the rendering from adding them off its
     own bat.

     The present method deals with the situation where we _do_ have commas
     and want to retain them, but we need to prevent the rendering from
     randomly suppressing them.  To be specific, we have discovered that with
     something like:

        ...>, <note ...

     the comma is sometimes dropped (but not always).  Introducing

        <hi type='normal'/>

     immediately before the note seems to fix this, and since it doesn't actually
     do anything, should not adversely affect third parties using the OSIS
     (although they may be a bit bemused by it).

     I attempt to be slightly more refined here, in that if it looks as though
     the text has already been tweaked in this manner, I don't do anything. */

  private fun handleCallouts_preventCommasBeingSuppressedBetweenAdjacentCallouts (notes: List<Node>): Boolean
  {
    var changed = false

    fun insertNode (before: Node)
    {
      val newNode = notes[0].ownerDocument.createNode("<hi type='normal'/>")
      Dom.insertNodeBefore(before, newNode)
      changed = true
    }

    notes
      .filter { null != it.previousSibling && "#text" != Dom.getNodeName(it.previousSibling) && "," == it.previousSibling.textContent.trim() }
      .forEach { insertNode(it) }

    return changed
  }


  /***************************************************************************/
  private fun handleHorizontalWhitespace (rootNode: Node)
  {
    /**************************************************************************/
    /* Remove whitespace at the start of titles. */

    Dom.findNodesByName(rootNode, "title", false).forEach {
      val children = Dom.getChildren(it)
      for (c in children)
      {
        if (!Dom.isWhitespace(c))
          break
        Dom.deleteNode(c)
      }
    }
  }


  /****************************************************************************/
  /* The OSIS Selah tag isn't formatted well, so convert it to italic. */

  private fun handleSelah (rootNode: Node)
  {
    Dom.findNodesByAttributeValue(rootNode, "l", "type", "selah").forEach {
      Dom.deleteAllAttributes(it)
        it["type"] = "italic"
        Dom.setNodeName(it, "hi")
    }
  }


  /****************************************************************************/
  /* Speaker is legit OSIS, but it doesn't get rendered well, so we need to do
     something about it.  More specifically, we convert it to

           <p><hi type='bold'><hi type='italic'> ... </hi></hi></p>
  */

  private fun handleSpeaker (rootNode: Node)
  {
    Dom.findNodesByName(rootNode, "speaker", false).forEach {
      val wrapperPara = Dom.createNode(it.ownerDocument, "<p/>")
      Dom.insertNodeBefore(it, wrapperPara)

      val wrapperHi = Dom.createNode(it.ownerDocument, "<hi type='bold'/>")
      wrapperPara.appendChild(wrapperHi)

      Dom.deleteNode(it)
      wrapperHi.appendChild(it)

      // Turn the node into hi:italic.
      Dom.deleteAllAttributes(it)
      it["type"] = "italic"
      Dom.setNodeName(it, "hi")
    }
  }


  /****************************************************************************/
  /* Not sure whether this is an issue, but at one point DIB was placing
     \u000c in files because it made his editor format things more neatly.
     Trouble is, this turns out to be an invalid character in XML. */

  private fun handleUnacceptableCharacters (rootNode: Node)
  {
    fun doIt (node: Node)
    {
      val s1 = node.textContent
      val s2 = s1.replace("\u000c", "")
      if (s1.length != s2.length)
        node.textContent = s2
    }


    Dom.findAllTextNodes(rootNode).forEach { doIt(it)}
  }


  /****************************************************************************/
  /* Various things to attempt to avoid excessive whitespace, or whitespace in
     unfortunate locations. */

  private fun handleVerticalWhitespace (rootNode: Node)
  {
    /**************************************************************************/
    /* Remove whitespace following linebreaks.  The main purpose of this is to
       avoid having newline characters after linebreaks, but there's no harm,
       and possibly some advantage, in getting rid of all whitespace after
       linebreaks. */

    val lbNodes = Dom.findNodesByName(rootNode, "lb", false)
    lbNodes.forEach {
      val sibling = it.nextSibling
      if (null != sibling && Dom.isWhitespace((sibling)))
        Dom.deleteNode(sibling)
    }



    /**************************************************************************/
    /* Remove whitespace before linebreaks.  Similar argument to above. */

    lbNodes.forEach {
      val sibling = it.previousSibling
      if (null != sibling && Dom.isWhitespace((sibling)))
        Dom.deleteNode(sibling)
    }



    /**************************************************************************/
    /* There seems to be some evidence that linebreak followed by 'l' gives too
       much vertical whitespace, so ditch the linebreaks in such cases. */

    lbNodes.forEach {
      val sibling = it.nextSibling
      if (null != sibling && "l" == Dom.getNodeName(sibling))
        Dom.deleteNode(it)
    }



    /**************************************************************************/
    /* Verse sid followed by 'l' or lb doesn't look good, because it leaves the
       verse number marooned on a line of its own. */

    Dom.findNodesByAttributeName(rootNode, "verse", "sID").forEach {
      val sibling = it.nextSibling
      if (null != sibling && ( ("l" == Dom.getNodeName(sibling) && "level" in sibling) || "lb" == Dom.getNodeName(sibling)))
      {
        Dom.deleteNode(sibling)
        Dom.insertNodeBefore(it, sibling)
      }
    }
  }


  /****************************************************************************/
  /* Some texts appear to place notes outside of verses (ie after the eid).
     If this happens, then at best the note doesn't appear, and at worst
     it messes up the positioning of the verse number. */

  private fun moveNotesInsideVerses (rootNode: Node)
  {
    var eid: Node? = null
    Dom.getNodesInTree(rootNode)
      .filter { !Dom.hasAncestorNamed(it, "note") } // Hitting the children of moved note nodes can mess the processing up.
      .forEach {
      when (Dom.getNodeName(it))
      {
        m_FileProtocol.tagName_verse() ->
          eid = if (m_FileProtocol.attrName_verseEid() in it) it else null

        m_FileProtocol.tagName_note() ->
          if (null != eid)
          {
            Dom.deleteNode(eid!!)
            Dom.insertNodeAfter(it, eid!!)
          }

        else ->
          if (!Dom.isWhitespace(it))
            eid = null
      }
    }
  }
}
