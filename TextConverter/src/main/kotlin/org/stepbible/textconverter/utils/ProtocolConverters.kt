package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.RefCollection
import org.w3c.dom.Document
import org.w3c.dom.Node
import javax.print.Doc

/******************************************************************************/
/**
* We are concerned here with USX, standard OSIS, extended OSIS and STEP-internal
* OSIS.
*
* All of the code in this file converts something to OSIS -- either USX to OSIS,
* or one of the forms of OSIS to one of the other forms.
*
* **Standard OSIS** is ... well, just OSIS.  'Standard' means that it contains
* just the standard OSIS tags and attributes.  It *doesn't* necessarily mean
* that the file complies with the OSIS XSD.  For example, translators often use
* list tags in the hope that this will simply generate fully indented
* paragraphs (it won't).  In OSIS, list tags are supposed to be encapsulated
* within the OSIS equivalent of HTML's ul tags.  However, there is no
* corresponding USX enclosing tag.  Fabricating one and position it correctly
* is difficult; it also tends to introduce cross-verse-boundary markup; things
* seem to work perfectly well without; and having it introduces excessive
* vertical whitespace when rendered.  We therefore don't bother with it.  This
* means that we are not conformant, and therefore limits our ability to share
* stuff.  But it does save a lot of work.
*
* If OSIS is all we have available for a given text, then it will be standard
* OSIS.  I also save, for possible future use, any OSIS which I generate from
* USX of VL on runs which originate with those and, for the sake of
* consistency, I always save that in standard OSIS form too.  It goes into
* the InputOsis folder -- which means that on *any* run which starts from
* OSIS, we will be dealing with standard OSIS, regardless of whether that
* was all we had, was generated from VL or USX, has ben manually tweaked, etc.
*
*
* **Extended OSIS** is basically just standard OSIS with some extra tags and
* attributes.  These are there to simplify processing and to retain information
* which standard OSIS cannot handle.  Extended OSIS is used only during a given
* run -- it is never stored.
*
*
* **STEP-internal OSIS** is needed because of issues within STEP (issues which
* I think really ought to be fixed within STEPBible itself, but probably won't
* be).  So, for example, OSIS has a <speaker> tag to indicate who is speaking
* (eg in Job and Song of Songs); and since it has this tag it seems to make
* sense to use it (and in any case, we have to cater for it, because OSIS
* supplied by third parties may contain it).  But STEPBible doesn't render it
* well, so we need to change it to pure formatting markup.  But we don't want
* this to be reflected in any OSIS we retain for future use, because to do so
* would mean we would have lost semantic information.  So, at the very last
* minute, I apply any tweaks needed to work around STEPBible bugs, thus
* creating STEP-internal OSIS, and it is this which is passed to osis2mod.
* This is in standard OSIS form, but is a temporary which can be disposed of
* once osis2mod has run (although I may perhaps retain it for debug purposes).
*/

class ProtocolConverters // Just here to give the documentation processor something to latch on to -- isn't intended to be used by anything.



/******************************************************************************/
/**
* See [ProtocolConverters].
*
* @author ARA "Jamie" Jamieson
*/

object ProtocolConverterExtendedOsisToStandardOsis
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process (doc: Document)
  {
    Dbg.reportProgress("Converting to standard OSIS.")
    m_Document = doc
    doMappings()
    Utils.deleteTemporaryAttributes(m_Document)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Private                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun doMappings ()
  {

  }


  /****************************************************************************/
  private lateinit var m_Document: Document
}




/******************************************************************************/
/**
* See [ProtocolConverters].
*
* @author ARA "Jamie" Jamieson
*/

object ProtocolConverterStandardOsisToExtendedOsis
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process (doc: Document)
  {
    /**************************************************************************/
    /* This one needs to be undone again.  It's more convenient to locate book
       nodes via a tag name rather than via an attribute value, so I change
       the tag name here, but the result isn't valid OSIS, so it will need to
       be changed back before I do anything 'official' with the OSIS. */

    Dom.findNodesByAttributeValue(doc, "div", "type", "book").forEach { Dom.setNodeName(it, "book") }



    /**************************************************************************/
    /* This can be left as-is.  div/type=chapter and <chapter> are synonyms in
       OSIS, but the <chapter> version is more convenient. */

    Dom.findNodesByAttributeValue(doc, "div", "type", "chapter").forEach { Dom.setNodeName(it, "chapter") }



    /**************************************************************************/
    markCanonicalHeaderLocations(doc)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Canonical headings normally appear at the start of Psalms, but occasionally
     (in Psalms and -- is it Habakkuk), you may get one at the end.  I need to
     distinguish between the two, because osis2mod / JSword / STEPBible gets
     confused if presented with a canonical heading at the end of a chapter,
     so the version of the OSIS which I feed to osis2mod has to be changed so
     that the heading is changed to some kind of characteristic formatting,
     rather than keeping it marked as a heading. */

  private fun markCanonicalHeaderLocations (doc: Document)
  {
    /**************************************************************************/
    var verseNo = 0



    /**************************************************************************/
    fun processNode (node: Node)
    {
      if (Osis_FileProtocol.isCanonicalHeader(node))
        Utils.addTemporaryAttribute(node, "_temp_canonicalHeaderLocation", if (verseNo <= 3) "start" else "end")
      else if ("chapter" == Dom.getNodeName(node))
        verseNo = 0
      else if ("verse" == Dom.getNodeName(node) && "sID" in node)
        verseNo = RefCollection.rdOsis(node["sID"]!!).getLowAsRef().getV()
    }



    /**************************************************************************/
    Dom.getNodesInTree(doc).forEach(::processNode)
  }
}





/******************************************************************************/
/**
* See [ProtocolConverters].
*
* @author ARA "Jamie" Jamieson
*/

object ProtocolConverterExtendedOsisToStepOsis
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Note that in this method, we assume that the OSIS file has already been
     created, and we apply changes which require an understanding of the DOM
     structure.  Compare and contrast with the pre method. */

  fun process (document: Document)
  {
    Dbg.reportProgress("Applying late tweaks.")
    m_Document = document
    doChanges()
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Private                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun doChanges ()
  {
    doCommaNote()
    doLg()
    doUnacceptableTextCharacters()
    doLineBreaks()
    doMappings()
  }


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

  private fun doCommaNote (): Boolean
  {
    var changed = false

    fun insertNode (before: Node)
    {
      val newNode = Dom.createNode(m_Document, "<hi type='normal'/>")
      Dom.insertNodeBefore(before, newNode)
      changed = true
    }

    Dom.findNodesByName(m_Document, "note")
      .filter { null != it.previousSibling && "#text" != Dom.getNodeName(it.previousSibling) && "," == it.previousSibling.textContent.trim() }
      .forEach { insertNode(it) }

    return changed
  }


  /****************************************************************************/
  /* Remove <lg> and promote children.  In theory, lg is required in OSIS (it's
     used to wrap list-type things).  However, it introduces excessive vertical
     whitespace when rendered, and nothing seems to reply upon it. */

  private fun doLg ()
  {
    Dom.findNodesByName(m_Document, "lg"). forEach {
      Dom.promoteChildren(it)
      Dom.deleteNode(it)
    }
  }


  /****************************************************************************/
  /* Have to admit I can't recall what this is supposed to achieve.  I think we
    can assume it's here to delete excess vertical whitespace, and it is
    _probably_ motivated by some of the stuff in Appendix F of the OSIS
    reference manual. */

  private fun doLineBreaks ()
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
    val lineBreaks = Dom.findNodesByName(m_Document, "l")
        .filter { "l" == Dom.getNodeName(it) && !it.hasChildNodes() }
    lineBreaks.forEach { processLineBreak(it) }
  }


  /****************************************************************************/
  /* Individual specific mappings. */

  private fun doMappings ()
  {
    /**************************************************************************/
    fun processNode (node: Node)
    {
      /************************************************************************/
      val extendedNodeName = Dom.getNodeName(node)



      /************************************************************************/
      /* Speaker is legit OSIS, but it doesn't get rendered well, so we need to
         do something about it.  More specifically, we convert it to

           <p><hi type='bold'><hi type='italic'> ... </hi></hi></p>
      */

      if ("speaker" == extendedNodeName)
      {
        val wrapperPara = Dom.createNode(node.ownerDocument, "<p/>")
        Dom.insertNodeBefore(node, wrapperPara)

         val wrapperHi = Dom.createTextNode(node.ownerDocument, "hi type='bold'/>")
         wrapperPara.appendChild(wrapperHi)

         Dom.deleteNode(node)
         wrapperHi.appendChild(node)

        // Turn the node into hi:italic.
        node -= "who"
        node["type"] = "italic"
        Dom.setNodeName(node, "hi")

        return
      }



      /************************************************************************/
      /* Selah doesn't get formatted well either, so change it to italic. */

      if ("l:selah" == extendedNodeName)
      {
        Dom.deleteAllAttributes(node)
        node["type"] = "italic"
        Dom.setNodeName(node, "hi")
        return
      }



      /************************************************************************/
      /* Canonical titles at the ends of books don't work either.  The code
         below turns this into a bold italic para, but I can't recall whether
         this is what we had before. */

      if (Osis_FileProtocol.isCanonicalHeader(node) && "end" == node["_temp_canonicalHeadingLocation"]!!)
      {
        val wrapperPara = Dom.createNode(node.ownerDocument, "<p/>")
        Dom.insertNodeBefore(node, wrapperPara)

        val wrapperHi = Dom.createTextNode(node.ownerDocument, "hi type='bold'/>")
        wrapperPara.appendChild(wrapperHi)

        Dom.deleteNode(node)
        wrapperHi.appendChild(node)

        // Turn the node into hi:italic.
        node -= "who"
        node["type"] = "italic"
        Dom.setNodeName(node, "hi")

        return
      }
    }




      /************************************************************************/

//    selah ->   <hi type='italic'>   (may be para or char in USX; both are treated the same way.)

      Dom.getNodesInTree(m_Document).forEach(::processNode)
  }


  /****************************************************************************/
  /* Removes invalid characters from text nodes.  In particular, DIB inserts
     \u000c because it results in nice formatting in his editor.  Unfortunately,
     this is an invalid character. */

  private fun doUnacceptableTextCharacters ()
  {
    fun doIt (node: Node)
    {
      val s1 = node.textContent
      val s2 = s1.replace("\u000c", "")
      if (s1.length != s2.length)
        node.textContent = s2
    }


    Dom.findAllTextNodes(m_Document).forEach { doIt(it)}
  }


  /****************************************************************************/
  private lateinit var m_Document: Document
}