package org.stepbible.textconverter.usxinputonly

import org.stepbible.textconverter.subelements.SE_VerseEndInserter
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Document
import org.w3c.dom.Node


/******************************************************************************/
/**
* In a previous implementation, I did a lot of work here.  This reflected the
* fact that so far as I knew, all texts were likely to be in USX form (or in
* VL form, which I translated to USX).  Latterly, though, it has become
* apparent that we may need to work with OSIS as input -- either because that's
* all we have available, or because it is more convenient to apply tagging
* changes to OSIS than to USX etc.
*
* In the previous implementation, it seemed to make sense to apply things like
* reversification to USX.  Now that we have to cater for OSIS as an *input*
* (where previously to all intents and purposes it was an *output*), it is
* necessary to move much of this processing further down the processing chain,
* to a point where OSIS is available, either because it is what was supplied to
* us, or because I have generated it from USX etc.
*
* This means that the processing here, although still quite extensive, is much
* less extensive than was the case.  In the main, I concern myself here with
* getting the USX into canonical form (ironing out the differences between
* USX 2 and USX 3, for instance), and addressing certain things which we know
* people often get wrong in USX (or which, at least, they do in ways not to
* our liking).  What I *don't* do is any of the significant validation,
* restructuring, etc.
*
* Having said this, there may be a few aspects of the processing which are
* duplicated here or in the things which the present class relies upon.  In
* particular, I validate cross-references here when in fact I'm going to have
* to validate them in OSIS too; and I do this simply so as to avoid having to
* make too many changes to existing code.
*
* At the end of processing, two configuration parameters have been set up with
* information which will be useful later on:
*
* - stepOriginData will be VL, USX or OSIS.  This represents the
*   raw data upon which the run was based.  In other words, if InputVl exists,
*   it will be 'VL'; if InputUsx exists it will be USX< and if neither exists,
*   it will be OSIS.  With VL, however, the run may not have started from that
*   folder: VL is always subject to pre-processing to turn it into USX, and if
*   that USX already exists (and postdates the VL), that may have been used
*   instead if the command-line parameters permitted it.  Similarly InputUsx
*   *may* have been pre-processed to produce revised USX, and again if the
*   revised USX already exists, the run may have started with that.
*
* - stepOriginDataAdditionalInfo contains additional text
*   explaining this issue of pre-processing where we have started from the
*   pre-processed text.  (The parameter will be undefined where we have
*   started from the raw text.)
*
* @author ARA "Jamie" Jamieson
*/

object Usx_Tidier
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Applies general processing to a data collection.  Note that despite
  * disclaimers elsewhere, this does actually currently assume that each
  * book comes in a separate document.
*/
  fun process (dataCollection: X_DataCollection)
  {
    dataCollection.getDocuments().forEach(::doIt)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Canonicalises and generally tidies up a single document -- but does not
     fix any problems which I reckon might also turn up in OSIS when we use
     that as input: things like that I sort out later by messing around with
     the OSIS.

     IMPORTANT: This assumes that the data has already been read into an
     X_DataCollection, and book nodes have been converted into enclosing
     nodes. */

  private fun doIt (doc: Document)
  {
    /**************************************************************************/
    m_BookName = Dom.findNodeByName(doc, "book")!!["code"]!!
    Dbg.reportProgress("Processing ${Utils.prettifyBookAbbreviation(m_BookName)}.", 1)



    /**************************************************************************/
    deleteIgnorableTags(doc)                         // Anything of no interest to our processing.
    correctCommonUsxIssues(doc)                      // Correct common errors.
    simplePreprocessTagModifications(doc)            // Sort out things we don't think we like.
    convertTagsToLevelOneWhereAppropriate(doc)       // Some tags can have optional level numbers on their style attributes.  A missing level corresponds to leve 1, and it's convenient to force it to be overtly marked as level 1.
    Usx_CrossReferenceCanonicaliser.process(doc)     // Cross-refs can be represented in a number of different ways, and we'd rather have just one way.
    SE_VerseEndInserter(UsxDataCollection).process() // Position verse-ends to avoid cross-boundary markup as far as possible.
    addStylesToVerses(doc)                           // See documentation below.
    tidyUpMain(doc)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Simple changes                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* The functionality which converts to OSIS needs to distinguish sid and eid,
     and it can do this only if we set up a suitable style attribute. */

  private fun addStylesToVerses (doc: Document)
  {
    doc.findNodesByName("verse").forEach { it["style"] = if ("sid" in it) "sid" else "eid" }
  }


  /****************************************************************************/
  /* Convert eg style='q' to style='q1'. */

  private fun convertTagsToLevelOneWhereAppropriate(rootNode: Node)
  {
    val nodes = Dom.findNodesByAttributeName(rootNode, "*", "style")
    fun convertTag (tagNamePlusStyle: String)
    {
      val styleName = tagNamePlusStyle.split(":")[1]
      nodes.filter { styleName == Dom.getAttribute(it, "style") }
           .forEach { Dom.setAttribute(it, "style", styleName + 1) }
    }

    Usx_FileProtocol.getTagsWithNumberedLevels().forEach { convertTag(it) }
  }


  /****************************************************************************/
  /* There are common ways in which USX is misused.  I suspect in fact that
     some of these arise because people work with texts in USFM, and possibly
     USX and USFM are not fully aligned, or Paratext doesn't do an entirely
     seamless job in converting from USFM to USX.  Whatever the reason, we need
     to straighten things out as best we can. */

  private fun correctCommonUsxIssues (doc: Document)
  {
    /**************************************************************************/
    val C_ParaTranslations = mapOf("para:po" to "para:pmo",           // Epistle introductions.
                                   "para:lh" to "_X_contentOnly",     // List headers (I think these are new-ish, and are equivalent to HTML <ul>.
                                   "para:lf" to "_X_contentOnly")     // Ditto (footers).  In the one text I have seen which has lh and lf tags, they are used inconsistently, so I am simply dropping them.


    val C_CharTranslations= mapOf("char:cat" to "_X_suppressContent", // Used by Biblica to hold administrative information.
                                  "char:litl" to "_X_listTotal")      // Total field for list items (eg Manasseh _12_).



    /**************************************************************************/
    fun changeNode (translations: Map<String, String>, node: Node)
    {
      val translation = translations[Usx_FileProtocol.getExtendedNodeName(node)]?.split(":") ?: return
      Usx_FileProtocol.recordTagChange(node, translation[0], if (2 == translation.size) translation[1] else null, "Corrected raw USX markup")
    }


    /**************************************************************************/
    Dom.findNodesByName(doc, "para").forEach { changeNode(C_ParaTranslations, it) }
    Dom.findNodesByName(doc, "char").forEach { changeNode(C_CharTranslations, it) }
  }


  /****************************************************************************/
  /* There are certain kinds of tags which are meaningless in an electronic
     version of a text, or which we can't handle, and these I delete. */

  private fun deleteIgnorableTags (doc: Document)
  {
    Dom.findNodesByAttributeValue(doc, "para", "style", "toc\\d").forEach { Dom.deleteNode(it) }
    Dom.findNodesByName(doc, "figure").forEach { Dom.deleteNode(it) }
  }


  /****************************************************************************/
  /* Changes tagName or tagName+style for another tagName or tagName+style. */

  private fun simplePreprocessTagModifications (doc: Document)
  {
    val modifications = ConfigData["stepSimplePreprocessTagModifications"] ?: return
    if (modifications.isEmpty()) return

    val individualMods = modifications.split("|").map { it.trim() }
    individualMods.forEach { details ->
      val (from, to) = details.split("->").map { it.trim() }
      val (fromTag, fromStyle) = ( if (":" in from) from else "$from: " ).split(":")
      val (toTag,   toStyle)   = ( if (":" in to  ) to   else "$to: " ).split(":")

      val froms =
        if (":" in from)
          Dom.findNodesByAttributeValue(doc, fromTag, "style", fromStyle)
        else
          Dom.findNodesByName(doc, fromTag)

      val setStyle = ":" in to

      froms.forEach {
        if (setStyle)
          it["style"] = toStyle
        else
          it -= "style"

        Dom.setNodeName(it, toTag)

        it["_X_simplePreprocessTagModification"] = "$from becomes $to"
      }
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            General tidying                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun tidyUpMain (doc: Document)
  {
    tidyMoveNotesToStartOfVerseWhereNecessary(doc)
    tidyDeleteConsecutiveWhiteSpace(doc)
    tidyDeleteBlanksAtEndOfChapter(doc)
  }


  /****************************************************************************/
  /* I have this vague recollection that having para:b at the end of a chapter
     cause some kind of grief when it came to rendering, and since I can think
     of no good reason for retaining this or anything else blank-ish at the
     end. */

  private fun tidyDeleteBlanksAtEndOfChapter (doc: Document): Boolean
  {
    var res = false

    fun deleteTerminatingBlanks (chapter: Node)
    {
      while (true)
      {
        if (!chapter.hasChildNodes()) return
        val child = chapter.lastChild
        if (!Dom.isWhitespace(child) && "para" != Dom.getNodeName(child)) return
        if (child.hasChildNodes()) return
        Dom.deleteNode(child)
        res = true
      }
    }

    Dom.findNodesByName(doc, "chapter").forEach { deleteTerminatingBlanks(it) }

    return res
  }


  /****************************************************************************/
  /* Cosmetic only, but some books end up with a lot of newlines in them, which
     makes it difficult to read the USX. */

  private fun tidyDeleteConsecutiveWhiteSpace (doc: Document): Boolean
  {
    var res = false

    fun deleteWhiteSpace (node: Node)
    {
      val textContent = node.textContent
      if (1 == textContent.length) return
      res = true
      node.textContent = if (textContent.contains("\n")) "\n" else " "
    }

    Dom.findAllTextNodes(doc).filter { Dom.isWhitespace(it) }.forEach { deleteWhiteSpace(it) }
    return res
  }


  /****************************************************************************/
  /* Certain footnotes naturally find their place part way through a verse.  In
     some cases I have been asked to move these to the front of the verse,
     which is the task of this method.  (This is probably only ever going to be
     relevant to footnotes introduced during reversification processing, but
     since it could be useful for other things, may as well apply it here.) */

  private fun tidyMoveNotesToStartOfVerseWhereNecessary (doc: Document): Boolean
  {
    /**************************************************************************/
    var mostRecentSid: Node? = null
    var res = false
    fun processNode (node: Node)
    {
      when (Dom.getNodeName(node))
      {
        "verse" -> if (Dom.hasAttribute(node, "sid")) mostRecentSid = node

        "note" ->
          if (NodeMarker.hasMoveNoteToStartOfVerse(node))
          {
            res = true
            NodeMarker.deleteMoveNoteToStartOfVerse(node)
            Dom.deleteNode(node)
            Dom.insertNodeAfter(mostRecentSid!!, node)
          }
      } // when
    }

    Dom.getNodesInTree(doc).forEach { processNode(it) }

    return res
  }

    /**************************************************************************/
    private var m_BookName = ""
}