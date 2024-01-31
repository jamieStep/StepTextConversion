package org.stepbible.textconverter.usxinputonly

import org.stepbible.textconverter.subelements.*
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.utils.Usx_FileProtocol
import org.w3c.dom.Document
import org.w3c.dom.Node


/******************************************************************************/
/**
* Takes data from InputUsx, applies any necessary pre-processing, and then
* converts the result to OSIS in InternalOsis.
*
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
* - stepProcessingOriginalData will be VL, USX or OSIS.  This represents the
*   raw data upon which the run was based.  In other words, if InputVl exists,
*   it will be 'VL'; if InputUsx exists it will be USX< and if neither exists,
*   it will be OSIS.  With VL, however, the run may not have started from that
*   folder: VL is always subject to pre-processing to turn it into USX, and if
*   that USX already exists (and postdates the VL), that may have been used
*   instead if the command-line parameters permitted it.  Similarly InputUsx
*   *may* have been pre-processed to produce revised USX, and again if the
*   revised USX already exists, the run may have started with that.
*
* - stepProcessingOriginalDataAdditionalInfo contains additional text
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
  fun process (items: Map<String, Document?>)
  {
    items.forEach { if (null != it.value) { m_BookName = it.key; m_Document = it.value!!; processDom() } }
  }


  /****************************************************************************/
  /* Canonicalises and generally tidies up a single document -- but does not
     fix any problems which I reckon might also turn up in OSIS when we use
     that as input: things like that I sort out later by messing around with
     the OSIS. */

  private fun processDom ()
  {
    /**************************************************************************/
    fun x () = Logger.announceAllAndTerminateImmediatelyIfErrors()



    /**************************************************************************/
    /* These are unique to USX input. */
    
    Dbg.reportProgress("Processing $m_BookName", 1)
    deleteIgnorableTags()                              // Anything of no interest to our processing.
    correctCommonUsxIssues()                           // Correct common errors.
    simplePreprocessTagModifications()                 // Sort out things we don't think we like.
    canonicaliseRootAndBook()                          // Basically turns book into an enclosing tag.
    canonicaliseChapterAndVerseStarts(); x()           // Make sure we have sids (rather than numbers); that there are no verse-ends (we deal with those ourselves) and that chapters are enclosing tags.
    convertTagsToLevelOneWhereAppropriate()            // Some tags can have optional level numbers on their style attributes.  A missing level corresponds to leve 1, and it's convenient to force it to be overtly marked as level 1.
    Usx_CrossReferenceCanonicaliser.process(m_Document)
    tidyUpMain()
    Usx_SE_FeatureCollector.doNothing(null)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Simple changes                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Turns the book node into an enclosing node. */

  private fun canonicaliseRootAndBook ()
  {
    val rootNode = Dom.findNodeByName(m_Document, "usx")!!
    val topLevelChildren = Dom.getChildren(rootNode)
    val ix = topLevelChildren.indexOfFirst { "book" == Dom.getNodeName(it) }
    val bookNode = topLevelChildren[ix]
    Dom.deleteChildren(bookNode)

    topLevelChildren.subList(ix + 1, topLevelChildren.size).forEach { Dom.deleteNode(it); bookNode.appendChild(it) }
    Dom.deleteAttribute(bookNode, "style")
  }


  /****************************************************************************/
  /* Different versions of USX have different ways of marking chapters and
     verses.

     USX 2 required markers at the start only, with the chapter or verse number
     identified with a 'number' attribute. USX 3 requires a separate milestone
     marker at both start and end, with a sid on the former and an eid on the
     latter, both of which are full references.  I therefore definitely want to
     accommodate both of these; but I feel that it's appropriate too to try to
     cater for combinations which more or less fit this picture, but aren't
     _quite_ right.

     I make the assumption below that the data will at least be consistent (so
     that if, for instance, we have _any_ chapter/sids, we'll have chapter/sids
     throughout). */

  private fun canonicaliseChapterAndVerseStarts ()
  {
    /**************************************************************************/
    /* Assume if we have any chapter:eids, we're going to have chapter:eids
       throughout; and that if we have any verse:eids, we'll have verse:eids
       throughout.  And because I want to position these things for myself, I
       need to delete all of them. */

    Dom.findNodesByAttributeName(m_Document, "chapter", "eid").forEach { Dom.deleteNode(it) }
    Dom.findNodesByAttributeName(m_Document, "verse",   "eid").forEach { Dom.deleteNode(it) }



    /**************************************************************************/
    /* In some texts, chapter and verse nodes carry style parameters.  I'm not
       sure what it's supposed to achieve, but it can get in the way of
       processing. */

    val chapters = Dom.findNodesByName(m_Document, "chapter")
    val verses   = Dom.findNodesByName(m_Document, "verse")
    chapters.forEach { it -= "style" }
    verses  .forEach { it -= "style" }



    /**************************************************************************/
    /* Temporarily add a dummy chapter sid at the end of the book, then turn
       each chapter into an enclosing chapter, and then get rid of the dummy
       chapter. */

    val bookNode = Dom.findNodeByName(m_Document, "book")!!
    val dummyChapter = Dom.createNode(m_Document, "<chapter _TEMP_dummy='y'/>")
    bookNode.appendChild(dummyChapter)
    val childrenOfBookNode = Dom.getChildren(bookNode)

    var low = 0
    while (true)
    {
      if ("chapter" != Dom.getNodeName(childrenOfBookNode[low])) // Move forward to the next chapter node (which will mark the start of the chapter being worked on).
      {
        ++low
        continue
      }

      val lowChapterNode = childrenOfBookNode[low]
      if (Dom.hasAttribute(lowChapterNode, "_TEMP_dummy")) break
      var high = low
      while ("chapter" != Dom.getNodeName(childrenOfBookNode[++high])) // Move forward to the next chapter (which will be off the end of the previous chapter).
        MiscellaneousUtils.doNothing()

      childrenOfBookNode.subList(low + 1, high).forEach { Dom.deleteNode(it); lowChapterNode.appendChild(it) } // Take the intervening nodes as children of the starting chapter node.

      low = high
    } // while.

    Dom.deleteNode(dummyChapter)



    /**************************************************************************/
    /* Having got this far, we have no eids.  I definitely do want sids,
       however, and possibly we have 'number' attributes instead of sids.  If
       so, we need to replace the 'number' parameters by sids. */

    if (null == chapters[0]["sid"]) // Assume if the first chapter does not have a sid, neither chapters nor verses will have them.
    {
      var chapterSid = ""

      fun addVerseSid (verse: Node)
      {
        verse["sid"] = "$chapterSid:${Dom.getAttribute(verse, "number")}"
        verse -= "number"
      }

      fun addChapterSid (chapter: Node)
      {
        val number = chapter["number"]!!
        chapterSid = "$m_BookName $number"
        chapter["sid"] = chapterSid
        chapter -= "number"
        Dom.findNodesByName(chapter, "verse", false).forEach { addVerseSid(it) }
      }

      chapters.forEach { addChapterSid(it) }
    }



    /**************************************************************************/
    /* Insert verse eids just before the next verse sid throughout.  I'll
       position them more appropriately later as part of the OSIS processing. */

    Dom.findNodesByName(m_Document, "chapter").forEach { chapter ->
      var prevSid = ""
      Dom.findNodesByName(chapter, "verse", false).forEach { verse ->
        if (prevSid.isNotEmpty())
        {
          val node = Dom.createNode(m_Document, "<verse eid='$prevSid'/>")
          Dom.insertNodeBefore(verse, node)
        }

        prevSid = verse["sid"]!!
      }

      val node = Dom.createNode(m_Document, "<verse eid='$prevSid'/>")
      chapter.appendChild(node)
    }
  }


  /****************************************************************************/
  /* Convert eg style='q' to style='q1'. */

  private fun convertTagsToLevelOneWhereAppropriate()
  {
    val nodes = Dom.findNodesByAttributeName(m_Document, "*", "style")
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

  private fun correctCommonUsxIssues ()
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
    Dom.findNodesByName(m_Document, "para").forEach { changeNode(C_ParaTranslations, it) }
    Dom.findNodesByName(m_Document, "char").forEach { changeNode(C_CharTranslations, it) }
  }


  /****************************************************************************/
  /* There are certain kinds of tags which are meaningless in an electronic
     version of a text, or which we can't handle, and these I delete. */

  private fun deleteIgnorableTags ()
  {
    Dom.findNodesByAttributeValue(m_Document, "para", "style", "toc\\d").forEach { Dom.deleteNode(it) }
    Dom.findNodesByName(m_Document, "figure").forEach { Dom.deleteNode(it) }
  }


  /****************************************************************************/
  /* Changes tagName or tagName+style for another tagName or tagName+style. */

  private fun simplePreprocessTagModifications ()
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
          Dom.findNodesByAttributeValue(m_Document, fromTag, "style", fromStyle)
        else
          Dom.findNodesByName(m_Document, fromTag)

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
  private fun tidyUpMain ()
  {
    tidyMoveNotesToStartOfVerseWhereNecessary()
    tidyDeleteConsecutiveWhiteSpace()
    tidyDeleteBlanksAtEndOfChapter()
  }


  /****************************************************************************/
  /* I have this vague recollection that having para:b at the end of a chapter
     cause some kind of grief when it came to rendering, and since I can think
     of no good reason for retaining this or anything else blank-ish at the
     end. */

  private fun tidyDeleteBlanksAtEndOfChapter (): Boolean
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

    Dom.findNodesByName(m_Document, "chapter").forEach { deleteTerminatingBlanks(it) }

    return res
  }


  /****************************************************************************/
  /* Cosmetic only, but some books end up with a lot of newlines in them, which
     makes it difficult to read the USX. */

  private fun tidyDeleteConsecutiveWhiteSpace (): Boolean
  {
    var res = false

    fun deleteWhiteSpace (node: Node)
    {
      val textContent = node.textContent
      if (1 == textContent.length) return
      res = true
      node.textContent = if (textContent.contains("\n")) "\n" else " "
    }

    Dom.findAllTextNodes(m_Document).filter { Dom.isWhitespace(it) }.forEach { deleteWhiteSpace(it) }
    return res
  }


  /****************************************************************************/
  /* Certain footnotes naturally find their place part way through a verse.  In
     some cases I have been asked to move these to the front of the verse,
     which is the task of this method.  (This is probably only ever going to be
     relevant to footnotes introduced during reversification processing, but
     since it could be useful for other things, may as well apply it here.) */

  private fun tidyMoveNotesToStartOfVerseWhereNecessary (): Boolean
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
          if (Dom.hasAttribute(node, "_TEMP_moveNoteToStartOfVerse"))
          {
            res = true
            Dom.deleteAttribute(node, "_TEMP_moveNoteToStartOfVerse")
            Dom.deleteNode(node)
            Dom.insertNodeAfter(mostRecentSid!!, node)
          }
      } // when
    }

    Dom.getNodesInTree(m_Document).forEach { processNode(it) }

    return res
  }

    /**************************************************************************/
    private var m_BookName = ""
    private lateinit var m_Document: Document
}