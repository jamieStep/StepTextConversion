/******************************************************************************/
package org.stepbible.textconverter.protocolagnosticutils.reversification

import org.stepbible.textconverter.applicationspecificutils.IssueAndInformationRecorder
import org.stepbible.textconverter.applicationspecificutils.Permissions
import org.stepbible.textconverter.applicationspecificutils.X_FileProtocol
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleAnatomy
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.TranslatableFixedText
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils.convertNumberToRepeatingString
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.nonapplicationspecificutils.shared.Language
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.util.*


/******************************************************************************/
/**
 * Various utilities for use with reversification.
 *
 * @author ARA "Jamie" Jamieson
 */

/******************************************************************************/
object PA_ReversificationUtilities
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            Initialisation                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/


  /****************************************************************************/
  fun setCalloutGenerator (calloutGenerator: (ReversificationDataRow) -> String) { m_CalloutGenerator = calloutGenerator }
  fun setNotesColumnName (notesColumnName: String) { m_NotesColumnName = notesColumnName }
  fun setFileProtocol (fileProtocol: X_FileProtocol) { m_FileProtocol = fileProtocol }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                Footnotes and creation of empty verses                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private lateinit var m_CalloutGenerator: (ReversificationDataRow) -> String
  private lateinit var m_FileProtocol: X_FileProtocol
  private var m_FootnoteLevelsOfInterest = ""
  private val m_IsCopyrightText = ConfigData.getAsBoolean("stepIsCopyrightText")
  private val m_KnowledgeLevelOfAudience = ConfigData["stepReversificationNoteType"]?.first()?.uppercaseChar() ?: 'B' // A(cademic) or B(asic).
  private lateinit var m_NotesColumnName: String


  /****************************************************************************/
  /**
   *  Adds a single footnote if conditions permit ...
   *
   *  - Each reversification row contains two fields which hold footnote
   *    details, one for use when we are applying conversion-time
   *    reversification, and one for use when applying runtime reversification.
   *    If the relevant field is empty, then obviously we don't output a
   *    footnote.  This is handled indirectly via makeFootnote, so there's
   *    no code for it here, but just be aware of the fact.
   *
   *  - We don't add footnotes to copyright texts.
   *
   *  - We don't add Acd footnotes on a non-academic run.
   *
   *  - We don't add Opt footnotes when doing runtime reversification.
   *
   *  @param verseNode Verse to which footnote is to be added.
   *  @param reversificationDataRow Reversification details.
   *  @param wordsAlreadyInSupposedlyEmptyVerse We need to know if a verse which
   *         is supposedly empty actually is empty.  We don't require it to be
   *         totally empty -- it may contain a little bit of text, such as a
   *         dash, for instance.
   */

  fun addFootnote (verseNode: Node, reversificationDataRow: ReversificationDataRow, wordsAlreadyInSupposedlyEmptyVerse: Int)
  {
    /**************************************************************************/
    /* Reversification rows have callout information marked A(cd), I(nf), N(ec)
       or O(pt).  Get a string indicating the ones of interest on the present
       run, based upon whether we are applying conversion-time or runtime
       reversification and whether we are targetting a basic or an academic
       audience. */

    fun getFootnoteLevelsOfInterest ()
    {
      m_FootnoteLevelsOfInterest = "AINO" // Assume we want all footnotes.

      if ("VersificationNote" == m_NotesColumnName) // If applying runtime reversification, we don't want Opt footnotes.
        m_FootnoteLevelsOfInterest = m_FootnoteLevelsOfInterest.replace("O", "")

      if ('B' == m_KnowledgeLevelOfAudience) // If this is a basic run, we don't want Acd footnotes.
        m_FootnoteLevelsOfInterest = m_FootnoteLevelsOfInterest.replace("A", "")
    }



    /**************************************************************************/
    if (m_IsCopyrightText)
      return



    /**************************************************************************/
    if (m_FootnoteLevelsOfInterest.isEmpty()) // This will be true only on the first call to the present method.
      getFootnoteLevelsOfInterest()



    /**************************************************************************/
    if (reversificationDataRow.footnoteLevel !in m_FootnoteLevelsOfInterest)
      return



    /**************************************************************************/
    /* 'I' notes are added only to empty verses.  I treat a verse as empty if
       it contains fewer than s words.  This is to allow for the possibility
       that the translators may have put eg an em dash into the verse to
       indicate that it's empty -- we still want the footnote if they have.

       Note that this test will give the verse as empty even if it already
       contains a footnote.  I'm not sure what to do here presently -- I _think_
       I've been told both that I should add a footnote regardless, and that I
       shouldn't.  Given this uncertainty, I've gone for the easier option, and
       just add the footnote regardless. */

    if ('I' == reversificationDataRow.footnoteLevel)
    {
      if (wordsAlreadyInSupposedlyEmptyVerse > 2)
        return
    }



    /**************************************************************************/
    makeFootnote(verseNode.ownerDocument, reversificationDataRow)?.forEach { footnoteNode -> Dom.insertNodeAfter(verseNode, footnoteNode) }
  }


  /****************************************************************************/
  /**
   *  Adds a single footnote to a canonical title node if conditions permit.
   *
   *  @param titleNode Title to which footnote is to be added.
   *  @param reversificationDataRow Reversification details.
   */

  fun addFootnoteToCanonicalTitle (titleNode: Node, reversificationDataRow: ReversificationDataRow)
  {
    // Unpleasant: I want to use addFootnote, because that has all the relevant
    // checks, but that adds things _after_ the node you pass to it, where here
    // I want the stuff to appear as the first child.  I therefore create a
    // temporary node to mark the place where I want things.

      val tempNode = Dom.createNode(titleNode.ownerDocument, "<X/>")
      titleNode.appendChild(tempNode)
      addFootnote(tempNode, reversificationDataRow, -1)
      Dom.deleteNode(tempNode)
    }


  /****************************************************************************/
  /**
  * Creates a new verse and returns the sid and eid.
  *
  * @param insertBefore Node before which the verse should be inserted.
  * @param refKeyForNewVerse What it says on the tin.
  * @return sid / eid node as a Pair.
  */


  fun createEmptyVerseForReversification (insertBefore: Node, refKeyForNewVerse: RefKey): Pair<Node, Node>
  {
    return m_FileProtocol.getEmptyVerseHandler().createEmptyVerseForReversification(insertBefore, refKeyForNewVerse)
  }


  /****************************************************************************/
  /* Creates the footnote construct.  We only get this far if we are sure we
     want a footnote.  At this point, the only reason for _not_ generating one
     is if the reversification data does not actually contain any footnote
     text. */

  private fun makeFootnote (ownerDocument: Document, dataRow: ReversificationDataRow): List<Node>?
  {
    /**************************************************************************/
    //Dbg.d(dataRow.toString())
    var content = getFootnoteContent(dataRow, m_NotesColumnName, m_KnowledgeLevelOfAudience)
    if (content.isEmpty())
      return null


    /**************************************************************************/
//    val calloutDetails = dataRow.calloutDetails
    val res: MutableList<Node> = mutableListOf()



    /**************************************************************************/
    /* This is the 'pukka' callout -- ie the piece of text which you click on in
       order to reveal the footnote.  Originally it was expected to be taken
       from the NoteMarker text in the reversification data.  However, we then
       found that STEP always rendered it as a down-arrow; and latterly DIB has
       decided he wants it that way even if STEP is fixed to display the actual
       callout text we request.  I therefore need to generate a down-arrow here,
       which is what the callout generator gives me. */

    val callout = m_CalloutGenerator(dataRow)



    /**************************************************************************/
    /* Insert the footnote itself. */

    content = content.replace("S3y", "S3Y") // DIB prefers this.

    val noteNode = m_FileProtocol.makeFootnoteNode(Permissions.FootnoteAction.AddFootnoteToGeneralVerseAffectedByReversification, ownerDocument, dataRow.standardRefAsRefKey, content, callout)
    if (null != noteNode)
    {
      res.add(noteNode)
      res.add(Dom.createTextNode(ownerDocument, " "))
      IssueAndInformationRecorder.addGeneratedFootnote(Ref.rd(dataRow.sourceRefAsRefKey).toString() + " (ReversificationFootnote)")
    }



    /**************************************************************************/
    return res.reversed()
  }


  /****************************************************************************/
  /* Returns the fully formatted content for a footnote.  whichFootnote should
     be either ReversificationNote or VersificationNote (in fact, only the
     latter at present, because we have no reason to deal with the former. */

  private fun getFootnoteContent (dataRow: ReversificationDataRow, whichFootnote: String, basicOrAcademic: Char): String
  {
    /**************************************************************************/
    //Dbg.d(dataRow.toString())
    //Dbg.d(2016 == dataRow.rowNumber)



    /**************************************************************************/
    /* Is there in fact any footnote at all? */

    var content = dataRow[whichFootnote]
    if (content.isEmpty()) return ""



    /**************************************************************************/
    /* Punctuation in the form of full stops may or may not be present.  If it's
       needed, though, we'll obtain it from the looked up text, so we don't need
       it here, and having it gets in the way. */

    if (content.endsWith(".")) content = content.substring(0, content.length - 1) // Remove trailing punctuation.  We'll add it to all strings later.
    content = content.replace("\\.\\s*%".toRegex(), "%").trim() // Don't want punctuation inside text which we will use as lookup keys.
    content = content.replace("%\\s*\\.".toRegex(), "%").trim() // Don't want punctuation outside text which we will use as lookup keys.



    /**************************************************************************/
    /* The footnote information comprises one or more text selectors enclosed
       by %-signs, along with zero or more reference strings.  Typically you
       would expect refs and selectors to alternate, but I do not assume this:
       I gather up all selectors, and separately all references, and then
       process the selectors in order, letting each take as many or as few
       reference details as it requires.

       A simple footnote entry looks like

         %In some Bibles the verse numbering here is% 3:1a.
    */

    val textSelectors = "%.*?%".toRegex().findAll(content).map { it.value.replace("%", "") }.toList()
    val texts = textSelectors.map { TranslatableFixedText.lookupText(Language.Vernacular, "V_reversification_[$it]") }
    val refs = content.split("%").filter { it.isNotEmpty() } .filter { it !in textSelectors }
    var preprocessedRefs = refs.map(::preprocessRef)



    /**************************************************************************/
    fun getFinalContent (lookedUpText: String, refs: List<Any>): String
    {
      return if (refs.isEmpty())
        lookedUpText
      else
        StepStringFormatter.format(lookedUpText, refs)
    }



    /**************************************************************************/
    var res = ""
    texts.forEach { text ->
      res += getFinalContent(text, preprocessedRefs) // Returns the text to be added to the footnote, and the number of references used up.
      val nRefsUsed = (text.length - text.replace("%ref", "").length) / 4
      preprocessedRefs = preprocessedRefs.subList(nRefsUsed, preprocessedRefs.size)
    }



    /**************************************************************************/
    /* If this is an academic run, we may need to add AncientVersion
       information. */

    if ('A' == basicOrAcademic)
      res += " " + dataRow.ancientVersions



    /**************************************************************************/
    return res.trim().replace("\\s+".toRegex(), " ")
  }


  /*****************************************************************************/
  /* Deals with individual reference strings -- things which may indeed represent
     a reference, but which may also be more complicated than that.  Returns
     a RefCollection where parsing is possible, or otherwise a string (which is
     probably the same as the input string). */

  private fun preprocessRef (putativeRef: String): Any
  {
    /**************************************************************************/
    var revisedRef = putativeRef.trim()



    /**************************************************************************/
    /* Yet another special case -- we now have some places which look like
       51:T */

    val putativeRefLc = revisedRef.lowercase()
    if (putativeRefLc.endsWith(":t") || putativeRefLc.endsWith(":title"))
      return revisedRef.split(":")[0] + ":Title" // Is it worth translating this?



    /**************************************************************************/
    val chunks: MutableMap<String, String> = mutableMapOf()
    var ix = 0



    /**************************************************************************/
    /* This identifies portions of the revisedRef which are bracketed by 'start'
       and 'end'.  It can cope with multiple instances of such chunks, and
       there is no need for start and end to be obviously related, nor for them
       to be the same length.  (Having start as an opening paren and end as a
       closing one is an obvious example, but you'll see uses below which
       aren't as neat.)

       It replaces each delimited instance by the result of calling 'processor'
       on the content of the instance (ie devoid of the bracket), and then
       stores this value in the 'chunks' map using a guaranteed unique key.

       At the same time, it replaces the delimited string (including the
       delimiters) with a placeholder of the form \u0001$key\u0002 where
       'key' is the key just alluded to.  This makes it possible to reintroduce
       the chunks into the final string later on. */

    fun splitAt (start: String, end: String, designator: String, processor: (String) -> String)
    {
      var ixHigh = 0
      var ixLow: Int
      while (true)
      {
        ixLow = revisedRef.indexOf(start, ixHigh)
        if (-1 == ixLow) break
        ixHigh = revisedRef.indexOf(end, ixLow)
        val key = "\u0001${++ix}\u0002"
        val chunk = revisedRef.substring(ixLow + start.length, ixHigh)
        val trailerStartsAt = ixHigh + end.length
        revisedRef = revisedRef.substring(0, ixLow) + key + if (trailerStartsAt >= revisedRef.length) "" else revisedRef.substring(trailerStartsAt)
        chunks[key] = if (chunk.isEmpty()) "" else processor(chunk)
      }
    }



    /**************************************************************************/
    /* Some refs contain further refs in parentheses.  The code below was
       written on the assumption that a given ref entry would never contain
       more than one parenthesised list. */

    fun parenthesisedCollectionHandler (text: String): String
    {
      val rc = RefCollection.rdUsx(usxifyFromStepFormat(text), dflt = null, resolveAmbiguitiesAs = "v")
      return TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_parenthesisedReferenceList", rc)
    }

    splitAt("(", ")", "Paren", ::parenthesisedCollectionHandler)



    /**************************************************************************/
    /* Some refs contain further refs in parentheses.  The code below was
       written on the assumption that a given ref entry would never contain
       more than one parenthesised list. */

    fun bracketEqualsHandler (text: String): String
    {
      val rc = RefCollection.rdUsx(usxifyFromStepFormat(text), dflt = null, resolveAmbiguitiesAs = "v")
      return TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_ancientVersionsEquivalenceEquals", rc) // Strictly this is nothing to do with ancientVersions, but the text for that can be used here.
    }

    splitAt("[=", "]", "BraEq", ::bracketEqualsHandler)



    /**************************************************************************/
    /* There are some notes which contain two references separated by a plus
       sign.  These are handled 'properly' here, arranging for them to be
       separated by the vernacular equivalent of a plus sign. */

    val C_PlusPat = "(?<chunk>(?<left>\\S+)\\s*\\+\\s*(?<right>\\S+))".toRegex()
    while (true)
    {
      val m = C_PlusPat.find(revisedRef) ?: break
      val leftRc = RefCollection.rdUsx(usxifyFromStepFormat(m.groups["left"]!!.value), dflt = null, resolveAmbiguitiesAs = "v")
      val rightRc = RefCollection.rdUsx(usxifyFromStepFormat(m.groups["right"]!!.value), dflt = null, resolveAmbiguitiesAs = "v")
      val key = "\u0001Plus${++ix}\u0002"
      revisedRef = revisedRef.replace(m.groups["chunk"]!!.value, key)
      chunks[key] = TranslatableFixedText.stringFormat(Language.Vernacular, "V_reversification_[plus]", leftRc, rightRc)
    }



    /**************************************************************************/
    /* If we haven't messed around with things at all so far, I'm going to
       assume that the text is just a reference / range / collection, which I
       can leave as-is and return just that. */

    if (chunks.isEmpty())
      return RefCollection.rdUsx(usxifyFromStepFormat(revisedRef), dflt = null, resolveAmbiguitiesAs = "v")



    /**************************************************************************/
    /* So we know now that this was a complex reference.  We may therefore
       have some references embedded around the other bits.  There is not a lot
       we can do with these, but at a guess we can just treat them in the same
       way as most references are treated in the V_reversification texts. */

    revisedRef = "[" + revisedRef.trim().replace("\u0001", "]\u0001")
                                        .replace("\u0002", "\u0002[") + "]" // Bracket places where they may be refs.

    fun plainVanillaHandler (text: String): String
    {
      val rc = RefCollection.rdUsx(usxifyFromStepFormat(text), dflt = null, resolveAmbiguitiesAs = "v")
      return StepStringFormatter.format(text, rc)
    }

    splitAt("[", "]", "Vanilla", ::plainVanillaHandler)



    /**************************************************************************/
    /* Now assemble all of the bits. */

    chunks.keys.forEach { revisedRef = revisedRef.replace(it, chunks[it]!!) }
    return revisedRef
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            Miscellaneous                               **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Creates a navigable map of chapter nodes, keyed on refKey and giving the
  * corresponding chapter node.
  *
  * @param rootNode Root node for book.
  * @return Map.
  */

  fun makeCanonicalTitlesMap (rootNode: Node): Map<Int, Node>
  {
    if (m_FileProtocol.getBookNumber(rootNode) !in BibleAnatomy.C_BookNumbersOfBooksWhichMayHaveCanonicalHeaders)
      return mapOf()

    val res = mutableMapOf<Int, Node>()
    rootNode.findNodesByName(m_FileProtocol.tagName_chapter()).forEach { chapterNode ->
      val canonicalTitleNode = chapterNode.getAllNodesBelow().find { m_FileProtocol.isCanonicalTitleNode(it) }
      if (null != canonicalTitleNode)
        res[Ref.getC(m_FileProtocol.getSidAsRefKey(chapterNode))] = canonicalTitleNode
    }

    return res
  }


  /****************************************************************************/
  /**
  * Creates a navigable map of chapter nodes, keyed on refKey and giving the
  * corresponding chapter node.
  *
  * @param rootNode Root node for book.
  * @return Map.
  */

  fun makeChapterSidMap (rootNode: Node): NavigableMap<RefKey, Node>
  {
    val res: NavigableMap<RefKey, Node> = TreeMap()
    rootNode.findNodesByName(m_FileProtocol.tagName_chapter()).forEach { res[m_FileProtocol.getSidAsRefKey(it)] = it }
    return res
  }


  /****************************************************************************/
  /**
  * Creates a navigable map of verse nodes, keyed on refKey and giving the
  * corresponding verse sid node.
  *
  * @param rootNode Root node for book.
  * @return Map.
  */

  fun makeVerseSidMap (rootNode: Node): NavigableMap<RefKey, Node>
  {
    val res: NavigableMap<RefKey, Node> = TreeMap()
    rootNode.findNodesByAttributeName(m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid()).forEach { res[m_FileProtocol.getSidAsRefKey(it)] = it }
    return res
  }


  /****************************************************************************/
  /**
   * The reversification data uses its own formats for references.  It's
   * convenient to convert this to USX for processing because I already have
   * code to handle USX.
   *
   * @param theStepRef Reference in STEP format.
   * @return String representation of an equivalent reference in USX format.
   *         Note that this may differ from the original in respect of
   *         whitespace, separators, etc, but since we need it only for
   *         parsing, that's not an issue.
   */

  fun usxifyFromStepFormat (theStepRef: String): String
  {
    /**************************************************************************/
    //Dbg.d(theStepRef)



    /**************************************************************************/
    /* Get the reference string into canonical form.  The input may contain
       commas or semicolons as collection separators, and since the parsing
       processing is set up to handle either, it's convenient here to convert
       them all to just one form. */

    val stepRef = theStepRef.replace(",", ";")
                            .replace("--", "-")
                            .replace("–", "-")
                            .replace(" +", "")
                            .replace("•", "") // \u2022 -- Arabic zero.
                            .replace("٠", "") // \u0660 -- Bullet point, used in some places instead of Arabic zero.
                            .replace("([1-9A-Za-z][A-Za-z][A-Za-z]\\.)".toRegex()) { it.value.replace(".", " ") }
                            .replace("(?i)title".toRegex(), "title")
                            .replace("(?i):T$".toRegex(), "")// We have things like 53:T as the alternative reference on some Psalm rows.  I change these back to be chapter references.


    /**************************************************************************/
    fun processCollectionElement (elt: String) = elt.split("-").joinToString("-"){ usxify1(it) }
    return stepRef.split(";").joinToString(";"){ processCollectionElement(it) }
  }


  /****************************************************************************/
  /* The reversification data has its own view of how references should be
     represented, and to save having to include specialised code to cater for
     these, it's convenient to convert to USX format up-front. */

  private fun usxify1 (theStepRef: String): String
  {
    /**************************************************************************/
    /* Replace the full stop after the book name with a space. */

    var stepRef = theStepRef
    if (stepRef.matches("...\\..*".toRegex()))
      stepRef = stepRef.substring(0, 3) + " " + stepRef.substring(4)



    /**************************************************************************/
    /* I _think_ we can forget subverse zero.  Otherwise, if we have numeric
       subverses, change them to alphabetic. */

    if (stepRef.endsWith(".0"))
      stepRef = stepRef.substring(0, stepRef.length - 2)
    else if (stepRef.matches(".*\\.\\d+$".toRegex()))
    {
      val ix = stepRef.lastIndexOf(".")
      val subverseNo = Integer.parseInt(stepRef.substring(ix + 1))
      stepRef = stepRef.substring(0, ix) + convertNumberToRepeatingString(subverseNo, 'a', 'z')
    }



    /**************************************************************************/
    return stepRef
  }
} // PA_ReversificationHandler
