package org.stepbible.textconverter.osisinputonly

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.TranslatableFixedText
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefCollection
import org.stepbible.textconverter.support.shared.Language
import org.stepbible.textconverter.utils.Osis_FileProtocol
import org.w3c.dom.Document
import org.w3c.dom.Node


/******************************************************************************/
/**
 * Applies any necessary pre-processing to InputOsis.
 *
 * This is potentially applicable where the only input available is OSIS.
 *
 * You should definitely *not* use it is you are starting from anything other
 * than OSIS, because the earlier processing which turns that other input into
 * OSIS should be written to get the OSIS correct.
 *
 * The grey area comes where you have something other than OSIS, but on a
 * particular run are starting from OSIS, perhaps because someone has applied
 * tagging to the OSIS.  Here I still tend to the view that you should not rely
 * on the facilities here because whoever does the tagging should get things
 * right.
 *
 * Anyway, after that preamble, the purpose of the code here is to take an OSIS
 * file and apply XSLT fragments to it.
 *
 * A configuration parameter stepOsisXsltStylesheet defines the sheet to be
 * applied.
 *
 * Alternatively, you can pass the OSIS text to processRegex and apply regex
 * processing to it.
 *
 * @author ARA Jamieson
 */

object Osis_Preprocessor
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
  * Deletes all xmlns attributes from nodes below the osis node.  I think these
  * are irrelevant, and they make the XML document appreciably bigger.
  *
  * @param doc
  */

  fun deleteXmlns (doc: Document)
  {
    Dbg.reportProgress("OSIS: Deleting redundant attributes.")
    Dom.findNodeByName(doc, "osis")!!.getAllNodesBelow().forEach { it -= "xmlns" }
  }


  /****************************************************************************/
  /**
  * Just occasionally we may have books which lack chapters -- particularly
  * (exclusively?) in the DC.  For example, in the RV text, EsthGr starts at
  * chapter 10 (actually at 10:4).  Having missing chapters appears to be a
  * no-no, so this processing creates any missing chapters.
  *
  * @param doc
  */

  fun insertMissingChapters (doc: Document)
  {
    /**************************************************************************/
    Dbg.reportProgress("Creating missing chapters if necessary.")



    /**************************************************************************/
    fun addChapter (chapterNo: Int, firstExistingChapterNode: Node, ref: Ref)
    {
      val newRef = Ref.rd(ref)
      newRef.setC(chapterNo)
      newRef.clearV()

      val newChapterNode = doc.createNode("<chapter/>")
      newChapterNode["osisID"] = newRef.toStringOsis("bc")
      newChapterNode["sID"] = newChapterNode["osisID"]!!

      val newVerseSidNode = doc.createNode("<verse/>")
      newRef.setV(1)
      newVerseSidNode["osisID"] = newRef.toStringOsis()
      newVerseSidNode["sID"] = newRef.toStringOsis()

      val footnoteNode = Osis_FileProtocol.makeFootnoteNode(doc, newRef.toRefKey(), TranslatableFixedText.stringFormat(Language.Vernacular, "V_emptyContentFootnote_chapterMissingInThisTranslation"))
      val textNode = doc.createTextNode(TranslatableFixedText.stringFormatWithLookup("V_contentForEmptyVerse_verseWasMissing"))

      val newVerseEidNode = doc.createNode("<verse/>")
      newVerseEidNode["eID"] = newRef.toStringOsis()

      newChapterNode.appendChild(newVerseSidNode)
      newChapterNode.appendChild(footnoteNode)
      newChapterNode.appendChild(textNode)
      newChapterNode.appendChild(newVerseEidNode)

      Dom.insertNodeBefore(firstExistingChapterNode, newChapterNode)
    }



    /**************************************************************************/
    doc.findNodesByAttributeValue("div", "type", "book").forEach { bookNode ->
      Dbg.reportProgress("- Creating missing chapters for ${bookNode["osisID"]!!} if necessary.")
      val firstExistingChapterNode = bookNode.findNodeByName("chapter", false)!!
      val ref = Ref.rdOsis(firstExistingChapterNode["sID"]!!)
      val firstChapterNo = ref.getC()
      for (chapterNo in 1 ..< firstChapterNo)
        addChapter(chapterNo, firstExistingChapterNode, ref)
    }

    //Dbg.d(doc)
  }


  /****************************************************************************/
  /**
  * Applies any preprocessing regexes to the incoming OSIS.
  *
  * @param inputText Text to be processed.
  */

  fun processRegex (inputText: String): String
  {
    Dbg.reportProgress("OSIS: Applying regex pre-processing if necessary.")
    val regexes = ConfigData.getOsisRegexes()
    if (regexes.isEmpty()) return inputText

    var revisedText = inputText

    regexes.forEach {
      revisedText = applyRegex(it, revisedText)
    }

    return revisedText
  }


  /****************************************************************************/
  /**
  * Applies any XSLT manipulation to the OSIS file, and sets up UsxDataCollection
  * to hold the details.  To state the obvious, this can be called only after
  * the USX text has been read and converted to DOM format.
  *
  * @param doc: DOM to be processed.
  * @return Document (possibly revised)
  */

  fun processXslt (doc: Document): Document
  {
    val stylesheet = ConfigData["stepOsisXsltStylesheet"] ?: return doc
    Dbg.reportProgress("OSIS: Applying XSLT pre-processing if necessary.")
    return applyXslt(doc, stylesheet)
  }


  /****************************************************************************/
  /**
  * Turns div:chapter into chapter.  Turns milestone chapters into enclosing
  * chapters.
  *
  * @param doc: DOM to be processed.
  */

  fun tidyChapters (doc: Document): Document
  {
    /**************************************************************************/
    Dbg.reportProgress("OSIS: Tidying chapter structure.")



    /**************************************************************************/
    var doneSomething = false

    Dom.findNodesByAttributeValue(doc, "div", "type", "chapter").forEach {
      Dom.setNodeName(it, "chapter")
      Dom.deleteAttribute(it, "type")
      doneSomething = true
    }

    if (doneSomething) // The div form of chapter is already an enclosing node.
      return doc



    /**************************************************************************/
    /* Assume that if the first chapter has content then all chapters are
       containing nodes.  We therefore need to do things only if the first
       chapter does _not_ have content. */

    if (!Dom.findNodeByName(doc, "chapter")!!.hasChildNodes())
    {
      //Dom.findNodesByAttributeName(doc, "chapter","eID"). forEach(Dom::deleteNode)
      Dom.findNodesByAttributeValue(doc, "div","type", "book").forEach {
        makeEnclosingTags(it, "chapter")
      }

      Dom.findNodesByAttributeName(doc, "chapter","eID"). forEach(Dom::deleteNode)
    }



    /**************************************************************************/
    //Dbg.d(doc)
    return doc
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Applies any regex processing to the input text.  regexDetails is a pair,
     the first part of which should be a regex pattern, and the second of which
     is a replacement.

     I'm having a little trouble working out how to do this consistently, simply
     and with a reasonable degree of flexibility.  As things stand, unless
     the replacement contains @convertRef, I assume that the pattern and
     replacement are mutually compatible in terms of capturing groups, and apply
     a simple replacement.

     If it does contain @convertRef, I assume that the pattern contains a single
     capturing group which is a reference in vernacular form, and that the
     replacement is made up purely of @convertRefVernacularToOsis.
   */

  private fun applyRegex (regexDetails: Pair<Regex, String>, inputText: String): String
  {
    /**************************************************************************/
    fun convertRefVernacularToOsis (s: String) = RefCollection.rdVernacular(s).toStringUsx()
    var converter: ((String) -> String)? = null

    if ("@convertRefVernacularToOsis" in regexDetails.second)
      converter = ::convertRefVernacularToOsis



    /**************************************************************************/
    return if (null == converter)
      inputText.replace(regexDetails.first, regexDetails.second)
    else
      regexDetails.first.replace(inputText) { matchResult -> converter(matchResult.groupValues[1]) }
  }


  /****************************************************************************/
  private fun applyXslt (doc: Document, stylesheetContent: String): Document
  {
    return if ("xsl:stylesheet" in stylesheetContent)
      Dom.applyStylesheet(doc, stylesheetContent)
    else
      Dom.applyBasicStylesheet(doc, stylesheetContent)
  }


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
    allNodes.indices
      .filter { tagNameToBeProcessed == Dom.getNodeName(allNodes[it]) }
      .forEach { indexes.add(it) }



    /***************************************************************************/
    /* Turn things into enclosing nodes. */

    for (i in 0..< indexes.size - 1)
    {
      val targetNode = allNodes[indexes[i]]
      val targetNodeParent = Dom.getParent(targetNode)!!
      for (j in indexes[i] + 1 ..< indexes[i + 1])
      {
        val thisNode = allNodes[j]
        if (targetNodeParent == Dom.getParent(thisNode))
        {
          Dom.deleteNode(thisNode)
          targetNode.appendChild(thisNode)
        }
      }
    }



    /***************************************************************************/
    Dom.deleteNode(dummyNode)
  }
}
