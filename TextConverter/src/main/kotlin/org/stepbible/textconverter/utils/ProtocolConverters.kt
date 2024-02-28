package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.w3c.dom.Document
import org.w3c.dom.Node

/******************************************************************************/
/**
* Phase 1 processing is responsible for supplying us with OSIS in a form which
* could be passed to third parties.
*
* (Almost.  OSIS requires that lists and poetry be encapsulated in tags
* equivalent to HTML's ul.  It's difficult to do this because there is no USX
* equivalent, and it gets in the way of processing and actually makes the
* rendered appearance _worse_.  Given that things seem to work perfectly well
* without them, I don't bother with them.  To this extent, therefore, the OSIS
* may not comply with standards.)
*
* Aside from that caveat, we would be able to supply this OSIS to third parties
* if we wished to.
*
* However, this format is not very amenable to further processing.  To this
* end, [ProtocolConverterOsisForThirdPartiesToInternalOsis] converts it to a
* more useful form (and should therefore be used early in the processing).
*
* The bulk of the rest of the processing then makes use of this form and adds
* to it.  Working this way makes processing easier, but the result is
* something which cannot be passed to osis2mod.  At the end of OSIS processing,
* therefore, you use [ProtocolConverterInternalOsisToOsisWhichOsis2modCanUse]
* to convert the data into a form which osis2mod *can* work with.  This class
* removes the temporary props from the data, and also performs various totally
* implausible modifications which experience suggests are necessary in order
* for STEP to render stuff correctly.
*
* This form may possibly be retained for debugging, but other than that, it
* should be regarded as throw-away: nothing else should make use of it.
*
* (One of the reasons for this are the ad hoc changes mentioned above.  Some
* of them involve ditching semantic markup which STEP renders wrongly in
* favour of formatting markup over which we have slightly more control.  Doing
* this, of course, means that we lose semantic information, and we don't really
* want to retain an impoverished version of the text.
*
* @author ARA "Jamie" Jamieson
*/

class ProtocolConverters // Just here to give the documentation processor something to latch on to -- isn't intended to be used by anything.



/******************************************************************************/
/**
* See [ProtocolConverters].
*
* @author ARA "Jamie" Jamieson
*/

object ProtocolConverterInternalOsisToOsisWhichOsis2modCanUse
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Applies modifications to the working OSIS so that it can be fed to osis2mod.
  *
  * @param dataCollection
  */

  fun process (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    Dbg.reportProgress("Converting OSIS for use by osis2mod.")
    val doc = dataCollection.getDocument()



    /**************************************************************************/
    /* Introduce pointless markup around notes to get round some random problem
       in STEP rendering. */

    doCommaNote(doc)



    /**************************************************************************/
    /* Sort out individual characters which would otherwise not work. */

    doUnacceptableTextCharacters(doc)



    /**************************************************************************/
    /* Tidy up vertical whitespace. */

    doLineBreaks(doc)



    /**************************************************************************/
    /* Some nodes were generated for temporary purposes and need to go.  Others
       may need some somewhat random modifications to sort out problems in STEP
        rendering. */

    doc.getAllNodes().forEach {
      if (NodeMarker.hasDeleteMe(it))
        Dom.deleteNode(it)
      else
        doMapping(it)
    }



    /**************************************************************************/
    /* In the course of processing, we introduce quite a lot of temporary
       markers.  I doubt osis2mod would be unduly worried, but get rid of them
       just in case. */

    NodeMarker.deleteAllMarkers(dataCollection)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Private                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* We have discovered that with something like:

        ...>, <note ...

     the comma is sometimes dropped (but not always).  Introducing

        <hi type='normal'/>

     immediately before the note seems to fix this, and since it doesn't actually
     do anything, should not adversely affect third parties using the OSIS
     (although they may be a bit bemused by it).

     I attempt to be slightly more refined here, in that if it looks as though
     the text has already been tweaked in this manner, I don't do anything. */

  private fun doCommaNote (doc: Document): Boolean
  {
    var changed = false

    fun insertNode (before: Node)
    {
      val newNode = doc.createNode("<hi type='normal'/>")
      Dom.insertNodeBefore(before, newNode)
      changed = true
    }

    Dom.findNodesByName(doc, "note")
      .filter { null != it.previousSibling && "#text" != Dom.getNodeName(it.previousSibling) && "," == it.previousSibling.textContent.trim() }
      .forEach { insertNode(it) }

    return changed
  }


  /****************************************************************************/
  /* Have to admit I can't recall what this is supposed to achieve.  I think we
    can assume it's here to delete excess vertical whitespace, and it is
    _probably_ motivated by some of the stuff in Appendix F of the OSIS
    reference manual. */

  private fun doLineBreaks (doc: Document)
  {
    /**************************************************************************/
    fun processLineBreak (node: Node)
    {
      var okToDelete = true
      var n: Node? = node
      while (true)
      {
        n = Dom.getPreviousSibling(n!!) ?: break
        if (Dom.isWhitespace(n)) continue
        val nodeName: String = Dom.getNodeName(n)
        if ("p" == nodeName) break
        if ("l" == nodeName && !n.hasChildNodes()) continue
        if ("verse" == nodeName) continue
        okToDelete = false
        break
      }

      if (!okToDelete) return

      n = node
      while (true)
      {
        n = Dom.getPreviousSibling(n!!) ?: break
        val nodeName: String = Dom.getNodeName(n)
        if (Dom.isWhitespace(n)) continue
        if ("p" == nodeName) break
        if ("l" == nodeName && !n.hasChildNodes()) continue
        if ("verse" == nodeName) continue
        okToDelete = false
        break
      }

      if (okToDelete) Dom.deleteNode(node)
    }



    /**************************************************************************/
    val lineBreaks = Dom.findNodesByName(doc, "l")
        .filter { "l" == Dom.getNodeName(it) && !it.hasChildNodes() }
    lineBreaks.forEach { processLineBreak(it) }
  }


  /****************************************************************************/
  /* Individual specific mappings. */

  private fun doMapping (node: Node)
  {
    /**************************************************************************/
    when (Osis_FileProtocol.getExtendedNodeName(node))
    {
      /************************************************************************/
      /* There are various other forms of 'l' tag.  Some of these carry 'type'
         attributes, and I have to admit to not knowing how to handle them (so
         hope we'll never see them).  But there are other plain vanilla ones
         which, for some bizarre reason sometimes don't get rendered correctly
         if I leave them as-is, but are ok if I insert an entirely irrelevant
         markup before them -- see discussion of xWeird in
        usxToOsisTagConversionsEtc.conf. */

      "l" ->
      {
        val pointlessNode = node.ownerDocument.createNode("<hi type='normal'/>")
        Dom.insertNodeBefore(node, pointlessNode)
        return
      }



      /************************************************************************/
      /* Selah doesn't get formatted well, so change it to italic. */

      "l:selah" ->
      {
        Dom.deleteAllAttributes(node)
        node["type"] = "italic"
        Dom.setNodeName(node, "hi")
        return
      }



      /************************************************************************/
      /* In collapsing tables, we will have marked what were the verse
         boundaries.  Here we turn them into superscripts. */

      "_X_verseBoundaryWithinElidedTable" ->
      {
        Dom.deleteAllAttributes(node)
        node["type"] = "super"
        Dom.setNodeName(node, "hi")
        return
      }



      /************************************************************************/
      /* Subverse boundaries may have been marked in earlier processing.  If so,
         the present requirement is that we don't mark them after all. */

      "_X_subverseSeparator" ->
      {
        Dom.deleteNode(node)
        return
      }



      /************************************************************************/
      /* Speaker is legit OSIS, but it doesn't get rendered well, so we need to
         do something about it.  More specifically, we convert it to

           <p><hi type='bold'><hi type='italic'> ... </hi></hi></p>
      */

      "speaker" ->
      {
        val wrapperPara = Dom.createNode(node.ownerDocument, "<p/>")
        Dom.insertNodeBefore(node, wrapperPara)

        val wrapperHi = Dom.createNode(node.ownerDocument, "<hi type='bold'/>")
        wrapperPara.appendChild(wrapperHi)

        Dom.deleteNode(node)
        wrapperHi.appendChild(node)

        // Turn the node into hi:italic.
        Dom.deleteAllAttributes(node)
        node["type"] = "italic"
        Dom.setNodeName(node, "hi")

        return
      }



      /************************************************************************/
      /* Acrostic elements come in two forms -- as titles and as hi:acrostic.
         In fact, STEP doesn't render either of them correctly, so we need to
         convert them to something else.

         Here we deal with the title form.  The bold/italic version used here
         represents DIB's preferred option.

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

      "title:acrostic" ->
      {
        // Turn the node itself into hi:italic.
        Dom.setNodeName(node, "hi"); Dom.deleteAllAttributes(node); node["type"] = "italic"

        // Create a bold node, insert it before the node itself, and then move the target node into the bold node.
        val bold = node.ownerDocument.createNode("<hi type='bold'/>")
        Dom.insertNodeBefore(node, bold)
        Dom.deleteNode(node)
        bold.appendChild(node)
      }



      /************************************************************************/
      /* The other form of acrostic. */

      "hi:acrostic" ->
      {
        node["type"] = "italic"
        return
      }
    }
  }


  /****************************************************************************/
  /* Removes invalid characters from text nodes.  In particular, DIB inserts
     \u000c because it results in nice formatting in his editor.  Unfortunately,
     this is an invalid character. */

  private fun doUnacceptableTextCharacters (doc: Document)
  {
    fun doIt (node: Node)
    {
      val s1 = node.textContent
      val s2 = s1.replace("\u000c", "")
      if (s1.length != s2.length)
        node.textContent = s2
    }


    Dom.findAllTextNodes(doc).forEach { doIt(it)}
  }
}




/******************************************************************************/
/**
* See [ProtocolConverters].
*
* @author ARA "Jamie" Jamieson
*/

object ProtocolConverterOsisForThirdPartiesToInternalOsis
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    val doc = dataCollection.getDocument()



    /**************************************************************************/
    /* This one needs to be undone again.  It's more convenient to locate book
       nodes via a tag name rather than via an attribute value, so I change
       the tag name here, but the result isn't valid OSIS, so it will need to
       be changed back before we do anything 'official' with the OSIS. */

    doc.findNodesByAttributeValue("div", "type", "book").forEach { Dom.setNodeName(it, "book") }



    /**************************************************************************/
    /* This can be left as-is.  div/type=chapter and <chapter> are synonyms in
       OSIS, but the <chapter> version is more convenient. */

    doc.findNodesByAttributeValue("div", "type", "chapter").forEach { Dom.setNodeName(it, "chapter") }



    /**************************************************************************/
    /* OSIS supports (or strictly, _requires_ that bullet-point lists and
       poetry tags be wrapped in enclosing tags.  These get in the way of other
       processing and don't actually seem to be relevant to whether things work
       or not, so it's convenient to ditch them. */

    removeListBrackets(doc)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Private                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Removes list brackets -- <lg> etc.  This gives us invalid OSIS, but it
     still works and renders better than if we retain the <lg>.  Plus unless we
     do this, we almost invariably end up with cross-boundary markup. */

  private fun removeListBrackets (doc: Document)
  {
    doc.findNodesByName("lg").forEach { Dom.promoteChildren(it); Dom.deleteNode(it) }
    //doc.findNodesByName("list").forEach { Dom.promoteChildren(it); Dom.deleteNode(it) }
  }
}
