/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.*
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.makeFootnote
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.recordTagChange
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.reportBookBeingProcessed
import org.stepbible.textconverter.support.miscellaneous.Translations
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefCollection
import org.stepbible.textconverter.support.shared.Language
import org.w3c.dom.Document
import org.w3c.dom.Node




/******************************************************************************/
/**
 * Part 2 of the process which converts USX to enhanced USX.
 *
 * The processing is split into two parts because there are some things we have
 * to do prior to any reversification processing, and some which have to be done
 * afterwards.  This present class covers the 'afterwards'.
 *
 * In summary we are concerned here with recording and / or remedying any
 * shortfalls in the text as it stands after earlier processing, and responding
 * to any changes marked as 'pending' in earlier processing.
 *
 * @author ARA "Jamie" Jamieson
 */

object TextConverterProcessorUsxToEnhancedUsx2 : TextConverterProcessorBase()
 {
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Package                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner (): String
  {
    return "Creating enhanced USX -- Part 2"
  }


  /****************************************************************************/
  override fun getCommandLineOptions(commandLineProcessor: CommandLineProcessor)
  {
    commandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
  }


  /****************************************************************************/
  override fun pre (): Boolean
  {
    return true
  }


  /****************************************************************************/
  override fun process (): Boolean
  {
    BibleStructure.UsxUnderConstructionInstance().populateFromBookAndFileMapper(BibleBookAndFileMapperEnhancedUsx, wantWordCount = false) // Make sure we have up-to-date structural information.
    BibleBookAndFileMapperEnhancedUsx.iterateOverSelectedFiles(::processFile)
    XXXOsis2ModInterface.createSupportingData()
    return true
  }


  /****************************************************************************/
  override fun runMe (): Boolean
  {
     return C_InputType == InputType.USX
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Control                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Each of the processors here returns an indication of whether it has
     actually done anything -- the idea being that we need only write back
     to the USX file if any changes have been made.

     Having said that, the chances of getting through the whole of this lot
     _without_ making any changes is pretty slim; and is actually nil if you
     include the call to cosmeticChangesMain, because that's concerned with
     trying to make the enhanced USX files more human-readable, and that's
     _always_ going to do something. */

  private fun processFile (bookName: String, filePath: String, document: Document)
  {
    val C_MinimalChangesOnly = false // Introduced because DIB wanted a version of LXX to which I'd done as little as possible.  Should normally be false.

    //Dbg.d(document)
    reportBookBeingProcessed(document)
    TextConverterVersificationHealthCheck.checkBook(document)

    var changed = false // As a reminder below, 'or' does an OR _without_ short-circuit evaluation.
    changed = changed or handleCanonicalTitlesContainingVerses(document)
    changed = changed or handleVersesContainingCanonicalTitles(document)
    changed = changed or markManualEmptyContentMain(document)

    if (!C_MinimalChangesOnly)
    {
      if (XXXOsis2ModInterface.usingCrosswireOsis2Mod())
        changed = changed or EmptyVerseHandler.createEmptyVersesForKnownOsis2modScheme(document)
      else
        changed = changed or EmptyVerseHandler.createEmptyVersesForAdHocVersificationScheme(document)
    }

    changed = changed or EmptyVerseHandler.annotateEmptyVerses(document)
    changed = changed or deleteDummyVerses(document)
    changed = changed or generateContentForEmptyVersesMain(document)
    changed = changed or forceVersePerLineIfNecessaryMain(document)
    changed = changed or generateFootnotesForElisionMastersMain(document)
    changed = changed or tidyUpMain(document)
    changed = changed or cosmeticChangesMain(document)

    if (changed) Dom.outputDomAsXml(document, filePath, null)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Cosmetic                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Just some minor tweaks to make the USX more readable.  Make verses start
     on new lines etc. */

  private fun cosmeticChangesMain (document: Document): Boolean
  {
    /**************************************************************************/
    fun separateChapters (chapter: Node)
    {
      val newLines: Node = Dom.createTextNode(document, "\n\n\n\n\n")
      val comment: Node = Dom.createCommentNode(document, " ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++ ")
      val newLine: Node = Dom.createTextNode(document, "\n")
      Dom.insertNodeBefore(chapter, newLines)
      Dom.insertNodeBefore(chapter, comment)
      Dom.insertNodeBefore(chapter, newLine)
    }

    Dom.findNodesByName(document, "_X_chapter").forEach { separateChapters(it) }



    /**************************************************************************/
    fun separateVerses (verse: Node)
    {
      val newLine: Node = Dom.createTextNode(document, "\n")
      Dom.insertNodeAfter(verse, newLine)
    }

    Dom.findNodesByAttributeName(document, "verse", "eid").forEach { separateVerses(it) }



    /**************************************************************************/
    return true
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                       Content for empty verses                         **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Yet another pain.  There may be empty verses in the text for a number of
     reasons ...  They may have been empty in the raw text; we may have
     created them when expanding out elisions; reversification may have
     created them; and whether we have applied reversification or not, if we
     are targeting NRSVA, we may recognise that there are missing verses which
     I have been asked to generate.

     We don't always want an empty verse actually to be empty.  For instance,
     at the time of writing, we are putting dashed into empty verses
     generated when expanding out elisions.

     But regardless of content, there is a bug which we need to work around,
     in that something downstream of me suppresses consecutive verses which
     are genuinely empty, or which are 'nearly' identical.

     It turns out that you can get round this a) by always putting _something_
     into the verse, even if it is only a non-breaking space (ordinary spaces
     are no good -- they are ignored); and b) by alternating from one verse
     to the next, enclosing one verse in a meaningless char markup, and not
     enclosing the next one.

     Earlier processing will have placed _TEMP_emptyVerse everywhere where we
     need this, and this tag also indicates what content is required in the
     verse.  The present method uses this information to generate the necessary
     actual content.
  */

  private fun generateContentForEmptyVersesMain (document: Document): Boolean
  {
    val emptyContentMarkers = Dom.findNodesByName(document, "_TEMP_emptyVerse")
    for (i in emptyContentMarkers.indices)
    {
      var content = Dom.createTextNode(document, Dom.getAttribute(emptyContentMarkers[i], "_X_content")!!) as Node
      if (0 == i % 2)
      {
        val x = Dom.createNode(document, "<char style='no'/>")
        x.appendChild(content)
        content = x
      }

      Dom.insertNodeAfter(emptyContentMarkers[i], content)
      Dom.deleteNode(emptyContentMarkers[i])
    }

    return emptyContentMarkers.isNotEmpty()
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**               Footnotes for master verses in elisions                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Adds footnotes to elision masters, so long as they aren't tables and the
     elision contains at least three verses. */

  private fun generateFootnotesForElisionMastersMain (document: Document): Boolean
  {
    var res = false

    fun doIt (sid: Node)
    {
      if (Dom.hasAttribute(sid, "_X_generatedElisionToCoverTable")) return // If it's in a table it will already have a footnote, and we don't want to add to it.
      val nVersesInElision = Dom.getAttribute(sid, "_X_masterForElisionWithVerseCountOf")!!.toInt()
      if (nVersesInElision <= 2) return // No footnote if there is only one other verse in the elision.
      res = true

     val rc = RefCollection.rdUsx(Dom.getAttribute(sid, "_X_originalId")!!)
     val footnote = makeFootnote(document, Ref.rdUsx(Dom.getAttribute(sid, "sid")!!).toRefKey(), Translations.stringFormat(Language.Vernacular, "V_elision_containsTextFromOtherVerses", rc))
     Dom.insertNodeAfter(sid, footnote)
    }

    Dom.findNodesByAttributeName(document, "verse", "_X_masterForElisionWithVerseCountOf").forEach { doIt(it) }
    return res
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                        Missing or excess verses                        **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Delete the dummy verses which were introduced early on in the
     processing. */

  private fun deleteDummyVerses (document: Document): Boolean
  {
    /**************************************************************************/
    var res = false



    /**************************************************************************/
    fun doDeletion (sid: Node)
    {
      val content = sid.nextSibling; Dom.deleteNode(sid)
      if (null == content) return
      val eid = content.nextSibling; Dom.deleteNode(content)
      Dom.deleteNode(eid)
      res = true
    }


    /**************************************************************************/
    Dom.findNodesByAttributeName(document, "verse", "_TEMP_dummy").forEach { Dom.deleteNode(it) }
    Dom.findNodesByName(document, "_TEMP_dummy").forEach { doDeletion(it) }
    return res
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Canonical titles                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* We have seen at least one text (NIV2011) in which we have a canonical
     title (para:d) at the start of a chapter which para:d contains v1.
     osis2mod can't cope with this -- it outputs the verse number in the wrong
     place.

     The solution below may be somewhat half-hearted, in that it addresses the
     particular arrangement we have seen, but there may, I suspect, be others
     it can't cope with ...

     I _was_ going to turn the para:d into char:it and have done with it.
     Unfortunately, osis2mod doesn't like that either -- the verse number still
     ends up in the wrong place: just a _different_ wrong place.

     So, in the end, I have encapsulated every child of the para:d individually
     within char:it (which hopefully means I don't need to worry about the
     precised placement of verse:sid and verse:eid).  I have then turned the
     para:d into _X_contentOnly.  And finally I have bunged a para:p at the
     end of it to ensure this revised stuff, which I want to _look_ like a
     title, comes out on its own line. */

  private fun handleCanonicalTitlesContainingVerses (document: Document): Boolean
  {
    var res = false

    Dom.findNodesByAttributeValue(document, "para", "style", "d")
      .filter { null != Dom.findNodeByName(it, "verse", false) }
      .forEach { amendParaD(it, 'A'); res = true }

      return res
  }


  /****************************************************************************/
  /* We have seen at least one text (NIV2011) in which we have a verse at the
     end of a chapter which contains a canonical title (para:d).  osis2mod
     can't cope with this -- it outputs the verse number in the wrong place.

     The processing below is probably too fiddly to describe here in detail, and
     even then may well not cope with arrangements which I have not yet seen.

     However, for NIV2011 at least, it seems to do the job. */

  private fun handleVersesContainingCanonicalTitles (document: Document): Boolean
  {
    /**************************************************************************/
    var res = false



    /**************************************************************************/
    fun processParaD (paraD: Node, followingNodes: List<Node>)
    {
      amendParaD(paraD, 'B')
      return

      //val headingBlock = Dom.getAncestorNamed(paraD, "_X_headingBlock")
      //val eid = followingNodes.find { "verse" == Dom.getNodeName(it) && Dom.hasAttribute(it, "eid") }
      //Dom.deleteNode(eid!!)
      //Dom.insertNodeBefore(paraD, eid!!)
      //if (null != headingBlock) Dom.promoteChildren(headingBlock)
   }


    /**************************************************************************/
    fun processChapter (chapter: Node)
    {
      val allNodes = Dom.collectNodesInTree(chapter)
      var sidIx = -1
      for (i in allNodes.indices)
      {
        val node = allNodes[i]
        val nodeName = Dom.getNodeName(node)
        if ("verse" == nodeName)
        {
          sidIx = if (Dom.hasAttribute(node, "sid")) i else -1
        }
        else if ("para" == nodeName && "d" == Dom.getAttribute(node, "style"))
        {
          if (-1 != sidIx)
          {
            res = true
            processParaD(node, allNodes.subList(i + 1, allNodes.size))
          }
        }
      }
    }



    /**************************************************************************/
    Dom.findNodesByName(document, "_X_chapter").forEach { processChapter(it) }
    return res
  }


  /****************************************************************************/
  private fun amendParaD (paraD: Node, paraBeforeOrAfter: Char)
  {
     val document = paraD.ownerDocument
     val children = Dom.getChildren(paraD)
     children.filter { val x = Dom.getNodeName(it); x != "verse" && x != "note"}
             .forEach {
               val italicsNode = Dom.createNode(document, "<char style='it'/>")
               Dom.insertNodeBefore(it, italicsNode)
               Dom.deleteNode(it)
               italicsNode.appendChild(it)
             }

     recordTagChange(paraD, "_X_contentOnly", null, "Verse within para:d")
     Dom.setAttribute(paraD, "_X_wasCanonicalTitle", "y")
     val para = Dom.createNode(document, "<para style='p'/>")

     if ('A' == paraBeforeOrAfter)
       Dom.insertNodeAfter(paraD, para)
     else
       Dom.insertNodeBefore(paraD, para)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Force verse per line                           **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* On RTL texts, we used to have to force things to verse per line because
     otherwise things were rendered in the wrong order.  We may also have been
     told to force verse-per-line regardless. */

  private fun forceVersePerLineIfNecessaryMain (document: Document): Boolean
  {
    if (!ConfigData.getAsBoolean("stepForceVersePerLine"))
      return false

    fun addLineBreak (node: Node)
    {
      val lineBreak = Dom.createNode(document, "<_X_forceVersePerLineBreak/>")
      Dom.insertNodeAfter(node, lineBreak)
    }

    Dom.findNodesByAttributeName(document, "verse", "eid").forEach { addLineBreak(it) }
    return true
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Manual markup                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* At one stage I found it necessary to allow for special manually-introduced
     markup flags in the raw USX.  In particular, I needed to add a feature
     whereby I could manually insert empty verses into the raw text, but not
     have the footnotes added to these verses which are normally used to
     encourage the reader that the verse really was intended to be empty.
     (This was needed in Zec 4 of deu_HFA, where the translators had elided
     a lot of verses, and along with this had deliberately put the verses in
     the wrong order.)

     This present method gives a convenient place to deal with issues like this.
  */

  private fun markManualEmptyContentMain (document: Document): Boolean
  {
    var res = false

    fun process (node: Node)
    {
      when (Dom.getNodeName(node))
      {
        "#text" ->
          if ("\$wantEmptyContent".equals(node.textContent.trim(), ignoreCase = true))
          {
            res = true
            Dom.deleteNode(node)
          }
      }
    }

    Dom.collectNodesInTree(document).forEach { process(it) }

    return res
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            General tidying                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun tidyUpMain (document: Document): Boolean
  {
    var changed = false
    changed = changed or tidyMoveNotesToStartOfVerseWhereNecessary(document)
    changed = changed or tidyDeleteConsecutiveWhiteSpace(document)
    changed = changed or tidyDeleteBlanksAtEndOfChapter(document)
    return changed
  }


  /****************************************************************************/
  /* I have this vague recollection that having para:b at the end of a chapter
     cause some kind of grief when it came to rendering, and since I can think
     of no good reason for retaining this or anything else blank-ish at the
     end. */

  private fun tidyDeleteBlanksAtEndOfChapter (document: Document): Boolean
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

    Dom.findNodesByName(document, "_X_chapter").forEach { deleteTerminatingBlanks(it) }

    return res
  }


  /****************************************************************************/
  /* Cosmetic only, but some books end up with a lot of newlines in them, which
     makes it difficult to read the USX. */

  private fun tidyDeleteConsecutiveWhiteSpace (document: Document): Boolean
  {
    var res = false

    fun deleteWhiteSpace (node: Node)
    {
      val textContent = node.textContent
      if (1 == textContent.length) return
      res = true
      node.textContent = if (textContent.contains("\n")) "\n" else " "
    }

    Dom.findAllTextNodes(document).filter { Dom.isWhitespace(it) }.forEach { deleteWhiteSpace(it) }
    return res
  }


  /****************************************************************************/
  /* Certain footnotes naturally find their place part way through a verse.  In
     some cases I have been asked to move these to the front of the verse,
     which is the task of this method.  (This is probably only ever going to be
     relevant to footnotes introduced during reversification processing, but
     since it could be useful for other things, may as well apply it here.) */

  private fun tidyMoveNotesToStartOfVerseWhereNecessary (document: Document): Boolean
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

    Dom.collectNodesInTree(document).forEach { processNode(it) }

    return res
  }
}
