package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.contains
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.miscellaneous.set
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node


/****************************************************************************/
/**
 * Ad hoc tidying up -- for example tweaking stuff which otherwise seems to
 * come out wrong for no apparent reason.
 *
 * @author ARA "Jamie" Jamieson
 */

class SE_LastDitchTidier (dataCollection: X_DataCollection): SE(dataCollection)
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
    moveNotesInsideVerses(rootNode)
    collapseNestedStrongs(rootNode)
    removeCommasFromMultipleDownArrowFootnoteMarkers(rootNode)
    postProcessNodesMarkedAsRetainOriginal(rootNode)
  }


  /****************************************************************************/
  /**
  * This is horrible.
  *
  * Normally when I convert from USX to OSIS, I can simply assume that this
  * OSIS will be retained as it is.
  *
  * In a few instances, though, I have found it expedient to change the OSIS to
  * something else in subsequent processing.  Canonical titles are a case in
  * point: the OSIS is <title>, which is a div-type tag, and this can give
  * problems with cross-boundary markup.  I therefore change it to a span-type
  * markup which gives the same appearance more or less, but which avoids the
  * cross-boundary issue -- the point being that a span can be broken by
  * terminating it before a verse boundary, and then starting a new span
  * after the boundary.
  *
  * Continuing with this particular example, the processing came to rely upon
  * this.  But then we discovered a problem -- that if the canonical title
  * contained notes, the notes were rendered within a <title> tag, but not
  * within a span.
  *
  * In general, I don't know how I'm going to address this, because there may
  * well be texts which have notes and which _do_ give cross-boundary
  * problems.
  *
  * However, we have come across one text -- NETfull2 -- which has notes and
  * does _not_ have cross-boundary issues.  I can't get rid of the span
  * processing here without breaking other things.  The best I can do is to
  * turn the span-style markup back into a <title> tag late in the day, and
  * it is this which gives ruse to the X.retainOriginal attribute processed
  * here.
  *
  * This method looks at nodes marked as X.retainOriginal, and combines their
  * tag name and attributes with a view to picking them up again later.
  *
  */

  fun preProcessNodesMarkedRetainOriginal ()
  {
    m_DataCollection.getRootNodes().forEach { preProcessNodesMarkedRetainOriginal(it) }
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
  /* See the discussion of the public preProcessNodesMarkedRetainOriginal
     above.

     This method takes the information saved in the _retainOriginal attribute
     and uses it to alter the tag-type and attributes. */

  private fun postProcessNodesMarkedAsRetainOriginal (rootNode: Node)
  {
    Dom.getNodesInTree(rootNode)
      .filter { "X.retainOriginal" in it }
      .forEach { node->
        val (tagName, attributes) = node["X.retainOriginal"]!!.split("£££")
        Dom.setNodeName(node, tagName)

        Dom.deleteAllAttributes(node)

        attributes.split("££").forEach {
          val (key, value) = it.split("=")
          node[key] = value
        } // attributes
      } // forEach node
  }


  /****************************************************************************/
  /* See the public version of this method above. */

  private fun preProcessNodesMarkedRetainOriginal (rootNode: Node)
  {
    Dom.getNodesInTree(rootNode)
      .filter { "X.retainOriginal" in it }
      .forEach { node ->
        Dom.deleteAttribute(node, "X.retainOriginal")
        val newSetting = Dom.getNodeName(node) + "£££" + Dom.getAttributes(node).map { it.key + "=" + it.value }.joinToString("££")
        node["X.retainOriginal"] = newSetting
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


  /****************************************************************************/
  /* Two consecutive note markers in the OSIS end up being separated by a
     comma when rendered.  I've been asked to prevent this, and inserting a
     space between the two seems to do the trick.  I also check here that
     the two have the same callout, since I suspect we do want the comma if,
     for instance, the callouts are numbers (not that, at the time of writing,
     we ever get that, because all callouts seem to be rendered as down-
     arrows). */

  private fun removeCommasFromMultipleDownArrowFootnoteMarkers (rootNode: Node)
  {
    val notes = Dom.findNodesByName(rootNode, "note", false)
    for (i in 0 ..< notes.size - 1)
      if (notes[i].nextSibling === notes[i + 1] && notes[i]["n"]!! == notes[i + 1]["n"]!!)
      {
        val textNode = Dom.createTextNode(rootNode.ownerDocument, " ")
        Dom.insertNodeAfter(notes[i], textNode)
      }
  }
}
