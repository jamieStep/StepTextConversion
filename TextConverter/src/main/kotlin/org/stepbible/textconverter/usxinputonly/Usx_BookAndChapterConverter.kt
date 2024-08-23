package org.stepbible.textconverter.usxinputonly

import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.utils.Usx_FileProtocol
import org.w3c.dom.Node
import org.w3c.dom.Document


/******************************************************************************/
/**
* USX book, chapter and verse are not, perhaps, all they might be ...
*
* The book tag does not enclose the content of the book.  Rather, it encloses
* the *title* of the book, with the remainder of the content following it.
* It is more convenient to have an enclosing tag which holds the entire
* content.  I address this turning the title of the book (as I say, initially
* contained within the book tag) into an attribute of the book tag, and then
* enclosing the entire content of the book within the book tag.
*
* Similarly the chapter tags do not enclose chapters.  Rather there is a tag
* at the start of the chapter only.  Again, I want to change this so that
* the chapters enclose their content.  This should be ok, because I have yet
* to see any markup run across a chapter boundary.
*
* Also, books, chapters and verses may have style attributes, which serve no
* useful purpose but get in the way, so I remove them here.
*
* And finally, earlier versions of USX had chapter and verse numbers rather
* than full sids, and it is convenient to convert them to sids.
*
* The purpose with all of this is partly to make processing easier generally,
* and partly to align with OSIS, which does have enclosing book and chapter
* tags.
*
* One other thing to note: I do not create verse eid tags here (and indeed
* remove any which already exist to improve uniformity -- not all texts
* have them).  I could, of course, sort this out here, but since we are
* likely to have to do this in OSIS anyway, to cater for situations where
* we don't have USX in the first place), I simply provide code to deal
* with that separately.
*
* @author ARA "Jamie" Jamieson
*/

object Usx_BookAndChapterConverter
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process (doc: Document) = Dom.findNodesByName(doc, "book").forEach(::process)


  /****************************************************************************/
  fun process (bookRoot: Node)
  {
    if (!turnBookTagsIntoEnclosingTags(bookRoot.ownerDocument)) return
    deleteEids(bookRoot)
    attemptToRemedyChaptersWhichAreNotDirectChildrenOfBookNode(bookRoot)
    validateChapterLocations(bookRoot)
    turnChapterTagsIntoEnclosingTags(bookRoot)
    sidifyContent(bookRoot)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Attempt to remedy any chapters which are not direct children of the
     book node.  I do this by splitting the parent node.  However, without
     further thought, I assume I can do this only if the chapter is a child
     of para:p, with the latter being a direct child of the book node. */

  private fun attemptToRemedyChaptersWhichAreNotDirectChildrenOfBookNode (bookRoot: Node)
  {
    val bookAbbrev = bookRoot["code"]!!
    val badChapters = getChaptersWhichAreNotChildrenOfBook(bookRoot)
    badChapters.forEach {
      val id = if ("sid" in it) it["sid"] else it["number"]
      if ("para:p" != Usx_FileProtocol.getExtendedNodeName(it.parentNode))
        Logger.error("Chapter $bookAbbrev $id is not a direct child of he book node, and its parent (${Usx_FileProtocol.getExtendedNodeName(it)}) is not one I can split currently.")
      else if (bookRoot != it.parentNode.parentNode)
        Logger.error("Chapter $bookAbbrev $id is not a direct child of he book node, and nor its parent.  This represents a case I can't currently handle.")
      else
        Dom.splitParentAtNode(it)
    }
  }


  /****************************************************************************/
  /* I _think_ we may have chapter eids in some cases, but we certainly don't
     in all cases.  To make things more uniform, I delete the eids.  Ditto for
     verses.  Chapters are dealt with shortly when I convert them to enclosing
     tags.  Verse ends I leave absent for the time being: something else will
     need to deal with them later.

     It's also convenient to get rid of style attributes, which do nothing for
     us and confuse later processing. */

  private fun deleteEids (bookRoot: Node)
  {
    val elements = Dom.findNodesByName(bookRoot, "chapter", false).toSet() union Dom.findNodesByName(bookRoot, "verse", false).toSet()
    elements.forEach { if ("eid" in it) Dom.deleteNode(it) else it -= "style" }
  }


  /****************************************************************************/
  fun getChaptersWhichAreNotChildrenOfBook (bookRoot: Node) = Dom.findNodesByName(bookRoot.ownerDocument, "chapter").filter { bookRoot != it.parentNode }


  /****************************************************************************/
  private fun makeEnclosingTags (parentNode: Node, tagNameToBeProcessed: String)
  {
    /***************************************************************************/
    /* Create a dummy node to make processing more uniform. */

    val dummyNode = Dom.createNode(parentNode.ownerDocument, "<$tagNameToBeProcessed _dummy_='y'/>")
    parentNode.appendChild(dummyNode)



    /***************************************************************************/
    /* Locate the nodes to be processed within the overall collection. */

    val allNodes = Dom.getAllNodesBelow(parentNode)
    val indexes: MutableList<Int> = mutableListOf()
    allNodes.indices.forEach {
      if (tagNameToBeProcessed == Dom.getNodeName(allNodes[it]))
        indexes.add(it)
    }



    /***************************************************************************/
    /* Turn things into enclosing nodes. */

    for (i in 0..< indexes.size - 1)
    {
      val targetNode = allNodes[indexes[i]]
      val targetNodeParent = Dom.getParent(targetNode)
      for (j in indexes[i] + 1 ..< indexes[i + 1])
      {
        val thisNode = allNodes[j]
        if (targetNodeParent == Dom.getParent(thisNode))
        {
          Dom.deleteNode(allNodes[j])
          targetNode.appendChild(thisNode)
        }
      }
    }



    /***************************************************************************/
    Dom.deleteNode(dummyNode)
  }


  /****************************************************************************/
  /* Where chapters or verses have numbers, give them sids instead. */

  private fun sidifyContent (bookRoot: Node)
  {
    val bookName = bookRoot["code"]!!
    var chapterSid = ""

    Dom.getAllNodesBelow(bookRoot).forEach {
      when (Dom.getNodeName(it))
      {
        "chapter" ->
        {
          if ("sid" !in it)
          {
            chapterSid = bookName + " " + it["number"]
            it -= "number"
            it["sid"] = chapterSid
          }
        } // chapter


        "verse" ->
        {
          if ("sid" !in it)
          {
            val sid = chapterSid + ":" + it["number"]
            it -= "number"
            it["sid"] = sid
          }
        } // verse
      } // when
    } // forEach
  } // fun


  /****************************************************************************/
  private fun turnBookTagsIntoEnclosingTags (doc: Document): Boolean
  {
    /**************************************************************************/
    /* Assume that if we have a book name with an _X_content attribute, we've
       already processed this document. */

    if (null != Dom.findNodeByAttributeName(doc, "book", Usx_FileProtocol.internalAttrNameFor_bookTitle())) return false



    /**************************************************************************/
    /* Move the content of any book node into a content attribute so we don't
       lose it.  (This probably relies upon the content not having any nasty
       characters or other tags.) */

    val bookNodes = Dom.findNodesByName(doc, "book")
    bookNodes.forEach {
      it -= "style" // If we have a style attribute, we don't need it, and it gets in the way.
      it[Usx_FileProtocol.internalAttrNameFor_bookTitle()] = it.textContent
      Dom.deleteChildren(it)
    }



    /**************************************************************************/
    makeEnclosingTags(Dom.findNodeByName(doc, "usx")!!, "book")
    return true
  }


  /****************************************************************************/
  private fun turnChapterTagsIntoEnclosingTags (bookRoot: Node) = makeEnclosingTags(bookRoot, "chapter")


 /****************************************************************************/
  /* The processing relies upon chapter tags being direct children of book.
     I do check for this not being the case earlier, and attempt to remedy it,
     but if I haven't been able to remedy it, I give up. */

  private fun validateChapterLocations (bookRoot: Node)
  {
    val bookAbbrev = bookRoot["code"]!!
    getChaptersWhichAreNotChildrenOfBook(bookRoot).forEach {
      val id = if ("sid" in it) it["sid"] else it["number"]
      Logger.error("Markup '${Dom.toString(it.parentNode)} runs across chapter boundary at chapter $bookAbbrev $id.")
    }
  }
}