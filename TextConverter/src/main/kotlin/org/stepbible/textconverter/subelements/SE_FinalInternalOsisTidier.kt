package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node
import java.util.*


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
  private fun getNodeListByAttributeValue (allNodes: List<Node>, nodeName: String, attributeName: String, attributeValue: String): List<Node>
  {
    return allNodes
      .filter { nodeName == Dom.getNodeName(it) }
      .filter { attributeValue == it[attributeName] }
  }


  /****************************************************************************/
  override fun processRootNodeInternal (rootNode: Node)
  {
    /**************************************************************************/
    Dbg.reportProgress("Final OSIS tidying. ${m_FileProtocol.getBookAbbreviation(rootNode)}.")



    /**************************************************************************/
    /* Repeatedly building up a full list of nodes is expensive.  I therefore
       do it once here, and then create sublists of interest to the various
       processors which don't delete things from this list, and therefore
       don't undermine later processing. */

    deleteTemporaryNodes(rootNode)
    val allNodes = rootNode.getAllNodesBelow()
    val acrosticDivTypeList = allNodes.filter { "title:acrostic" == m_FileProtocol.getExtendedNodeName(it) }
    val acrosticSpanTypeList = allNodes.filter { "hi:acrostic" == m_FileProtocol.getExtendedNodeName(it) }
    val newLinesList = allNodes.filter { "l" == Dom.getNodeName(it) }
    val notesList = allNodes.filter { "note" == Dom.getNodeName(it) }
    val selahList = getNodeListByAttributeValue(allNodes, "l", "type", "selah")
    val speakerList = allNodes.filter { "speaker" == Dom.getNodeName(it) }
    val titlesList = allNodes.filter { "title" == Dom.getNodeName(it) }
    val xTagsList = allNodes.filter { Dom.getNodeName(it).startsWith("_X_")}



    /**************************************************************************/
    /* Things which do not tread on each other's toes, and can therefore run
       with the lists we established above. */

    allNodes.forEach(NodeMarker::deleteAllMarkers)
    deleteTemporaryAttributes(allNodes)
    collapseNestedStrongs(allNodes)
    handleSelah(selahList)
    handleAcrosticSpanType(acrosticSpanTypeList)
    handleAcrosticDivType(acrosticDivTypeList)
    handleSpeaker(speakerList)
    deleteHorizontalWhitespaceFromStartOfTitles(titlesList)
    handleCallouts(notesList)
    handle_X_tags(xTagsList)
    deleteAdjacentNewLineTags(newLinesList)



    /**************************************************************************/
    /* Things which need to run based upon the structure as it stands at the
       time they are called, and therefore can't rely upon the lists
       established above. */

    handleUnacceptableCharacters(rootNode)
    deleteNotesFromHeaders(rootNode)
    handleVerticalWhitespace(rootNode)
    moveNotesInsideVerses(rootNode)
    handleVersesWithinSpanTypeTags(rootNode)
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

  private fun collapseNestedStrongs (allNodes: List<Node>)
  {
    /**************************************************************************/
    val nodeMap = IdentityHashMap<Node, Boolean>()
    allNodes
      .filter { m_FileProtocol.tagName_strong() == Dom.getNodeName(it) }
      .filter { m_FileProtocol.attrName_strong() in it }
      .forEach { nodeMap[it] = true }



    /**************************************************************************/
    for (node in nodeMap.keys)
    {
      if (!nodeMap[node]!!)
        continue

      val children: MutableList<Node> = mutableListOf()
      var p = node
      while (true)
      {
        val firstChild = p.firstChild
        if (m_FileProtocol.tagName_strong() != Dom.getNodeName(firstChild))
          break

        if (m_FileProtocol.attrName_strong() !in firstChild)
          break

        children.add(firstChild)
        nodeMap[firstChild] = false
        p = firstChild
      }

      if (children.isEmpty())
        continue

      var revisedStrong = node[m_FileProtocol.attrName_strong()]!!
      children.forEach { revisedStrong += " " + it[m_FileProtocol.attrName_strong()]!! }
      node[m_FileProtocol.attrName_strong()] = revisedStrong
      children.reversed().forEach { descendant -> Dom.promoteChildren(descendant); Dom.deleteNode(descendant) }
    }

//    Dbg.d(allNodes[0].ownerDocument)
  } // fun


  /****************************************************************************/
  /* Not 100% sure about this ...  It is convenient, at some points in the
     processing, to add a newline to ensure that adjacent material doesn't end
     up on the same line -- and convenient to do this without worrying about
     whether we already have a newline.  However, I don't think we ever want to
     have two adjacent ones by the time we've finished.

     Note, incidentally, that I use <l level='...'/> to force newlines.  <lb/>
     sometimes seems to do nothing. */

  private fun deleteAdjacentNewLineTags (nodeList: List<Node>)
  {
    val newLineTags = nodeList.filter { !it.hasChildNodes() }
    for (i in 1 ..< newLineTags.size)
      if (newLineTags[i - 1] == newLineTags[i].previousSibling)
        Dom.deleteNode(newLineTags[i - 1])
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
    val allNodes = chapterNode.getAllNodesBelow()

    for (node in allNodes)
    {
      val nodeName = Dom.getNodeName(node)

      if (m_FileProtocol.tagName_verse() == nodeName)
        inVerse = m_FileProtocol.attrName_verseSid() in node
      else if (!inVerse && m_FileProtocol.isTitleNode(node) && !m_FileProtocol.isCanonicalTitleNode(node)) // The !isCanonicalTitleNode is there on the assumption notes in canonical titles work.  Need to check this.
        node.getAllNodesBelow().filter { m_FileProtocol.isNoteNode(it) }.forEach { Dom.deleteNode(it); doneSomething = true}
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
    rootNode.getAllNodesBelow().forEach {
      if (NodeMarker.hasDeleteMe(it))
        Dom.deleteNode(it)
    }
  }


  /****************************************************************************/
  private fun deleteTemporaryAttributes (nodeList: List<Node>)
  {
    nodeList
      .filter { "_t" in it }
      .forEach { node -> Dom.getAttributes(node).filter { it.key.startsWith("_") }. forEach { attr -> Dom.deleteAttribute(node, attr.key ) } }
  }


  /****************************************************************************/
  /* A bit of a kluge.  The OSIS may end up containing _X_... tags (at the time
     of writing, only as a result of handling reversification / samification
     footnotes).

     Originally I was adding these during the USX processing, and could rely
     upon the USX-to-OSIS conversion processing to convert them to proper
     OSIS tags.  Now, however, I may also add them during the OSIS processing
     itself, and therefore need code to turn them into something 'proper'.

     If you make changes, below, you almost certainly want corresponding
     changes in usxToOsisTagConversionsEtc.conf at the places where the
     USX translations for the_X_ tags below are defined. */

  private fun handle_X_tags (nodeList: List<Node>)
  {
    nodeList
      .filter { Dom.getNodeName(it).startsWith("_X_") }
      .forEach {
        when (val nodeName = Dom.getNodeName(it))
        {
          "_X_reversificationCalloutAlternativeRefCollection" ->
          {
            Dom.setNodeName(it, "hi")
            it["style"] = "super"
            Dom.insertNodeAfter(it, Dom.createTextNode(nodeList[0].ownerDocument, " "))
          }



          "_X_reversificationCalloutSourceRefCollection" ->
          {
            Dom.setNodeName(it, "hi")
            it["style"] = "bold"
            Dom.insertNodeAfter(it, Dom.createTextNode(nodeList[0].ownerDocument, " "))
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

  private fun handleAcrosticSpanType (nodeList: List<Node>)
  {
    /************************************************************************/
    /* The span-type form. */

    nodeList.filter { "hi:acrostic" == m_FileProtocol.getExtendedNodeName(it) } .forEach {
      it["type"] = "italic"
    }
  }


  /****************************************************************************/
  /* Acrostic elements come in two forms -- as titles and as hi:acrostic. In
     fact, STEP doesn't render either of them correctly, so we need to convert
     them to something else. */

  private fun handleAcrosticDivType (nodeList: List<Node>)
  {
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

    nodeList.forEach {
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
  private fun handleCallouts (nodeList: List<Node>)
  {
    val notes = (nodeList.toSet() subtract handleCallouts_preventCommasBeingRenderedBetweenAdjacentCallouts(nodeList).toSet()).toList()
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
  private fun deleteHorizontalWhitespaceFromStartOfTitles (nodeList: List<Node>)
  {
    /**************************************************************************/
    /* Remove whitespace at the start of titles. */

    nodeList.forEach {
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

  private fun handleSelah (nodeList: List<Node>)
  {
    nodeList.forEach {
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

  private fun handleSpeaker (nodeList: List<Node>)
  {
    nodeList.forEach {
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
      val s = node.textContent
      if (s.indexOf("\u000c") < 0) return
      node.textContent = s.replace("\u000c", "")
    }

    Dom.findAllTextNodes(rootNode).forEach { doIt(it)}
  }


  /****************************************************************************/
  /* It may seem that having verse tags within span-type tags -- say a verse
     tag within a hi:italic -- would be uncontroversial.  In fact, it is
     outlawed by the OSIS XSD, and seems to cause problems in osis2mod.

     I'm not sure what tags this might cover (it's not easy to work all the way
     through the XSD to check), so this may need to be extended in future. */

  private fun handleVersesWithinSpanTypeTags (rootNode: Node) // DON'T CHANGE THIS TO WORK USE A PREDEFINED NODE LIST -- NEED TO DETERMINE IT FROM THE STRUCTURE AS IT NOW STANDS.
  {
    val nodesOfInterest = rootNode.findNodesByName("hi")
    handleVersesWithinSpanTypeTags(nodesOfInterest)
  }


  /****************************************************************************/
  private fun handleVersesWithinSpanTypeTags (nodeList: List<Node>): Boolean
  {
    var res = false

    fun doIt (originalNode: Node)
    {
      val originalVerseTag = originalNode.findNodeByName("verse", false) ?: return // Nothing to do unless the tag contains a verse tag,
      res = true

      val newNode = Dom.cloneNode(originalNode.ownerDocument, originalNode) // Create a deep copy of the node, and insert it before the node itself.
      Dom.insertNodeBefore(originalNode, newNode)

      // We now want to delete everything _after_ the verse tag in the cloned node, and everything _before_ it in the original.
      val newVerseTag = newNode.findNodeByName("verse", false)
      var doDelete = false
      for (n in newNode.getAllNodesBelow())
        if (n === newVerseTag)
          doDelete = true
        else if (doDelete)
          try { Dom.deleteNode(n) } catch (_: Exception) {}


      for (n in originalNode.getAllNodesBelow())
        if (n === originalVerseTag)
          break
        else
          try { Dom.deleteNode(n) } catch (_: Exception) {}

      if (!originalNode.hasChildNodes())
        Dom.deleteNode(originalNode)

      if (!newNode.hasChildNodes())
        Dom.deleteNode(newNode)
    }


    nodeList.forEach(::doIt)
    return res
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
    /**************************************************************************/
    val allNodes = rootNode.getAllNodesBelow()



    /**************************************************************************/
    /* It's convenient to add an end marker to each note node.  That way
       when I run over all nodes as a list, I can use that to track whether I'm
       in a note node or not.  This is useful because I need to be able to
       detect when I'm inside a note node, and the alternative -- that of
       checking whether the current node has a note node as an ancestor -- is
       computationally expensive. */

    allNodes
      .filter { "note" == Dom.getNodeName(it) }
      .forEach {
        val marker = Dom.createNode(rootNode.ownerDocument, "<X_NoteEnd/>")
        it.appendChild(marker)
      }



    /**************************************************************************/
    var eid: Node? = null
    var inNote = false

    allNodes
      .filter { !Dom.hasAncestorNamed(it, "note") } // Hitting the children of moved note nodes can mess the processing up.
      .forEach {
        val nodeName = Dom.getNodeName(it)

        if (inNote && "X_NoteEnd" == nodeName)
        {
          inNote = false
          Dom.deleteNode(it)
          return@forEach
        }

        if (inNote)
          return@forEach

        when (nodeName)
        {
          m_FileProtocol.tagName_note() ->
            inNote = true


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
        } // when
      } // forEach
  } // fun
}
