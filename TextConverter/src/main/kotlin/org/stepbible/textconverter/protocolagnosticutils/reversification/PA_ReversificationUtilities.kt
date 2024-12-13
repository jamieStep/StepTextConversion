/******************************************************************************/
package org.stepbible.textconverter.protocolagnosticutils.reversification

import org.stepbible.textconverter.applicationspecificutils.IssueAndInformationRecorder
import org.stepbible.textconverter.applicationspecificutils.Permissions
import org.stepbible.textconverter.applicationspecificutils.X_FileProtocol
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleAnatomy
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.TranslatableFixedText
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MiscellaneousUtils.convertNumberToRepeatingString
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.findNodesByAttributeName
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.findNodesByName
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.getAllNodesBelow
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
    val calloutDetails = dataRow.calloutDetails
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

    val ancientVersions = if ('A' == m_KnowledgeLevelOfAudience) dataRow.ancientVersions else null
    if (null != ancientVersions) content += " $ancientVersions"

    val noteNode = m_FileProtocol.makeFootnoteNode(Permissions.FootnoteAction.AddFootnoteToGeneralVerseAffectedByReversification, ownerDocument, dataRow.standardRefAsRefKey, content, callout)
    if (null != noteNode)
    {
      res.add(noteNode)
      res.add(Dom.createTextNode(ownerDocument, " "))
      IssueAndInformationRecorder.addGeneratedFootnote(Ref.rd(dataRow.sourceRefAsRefKey).toString() + " (ReversificationFootnote)")
    }



    /**************************************************************************/
    /* Check if we need the text which will typically be superscripted and
       bracketed. */

    val alternativeRefCollection = calloutDetails.alternativeRefCollection
    if (null != alternativeRefCollection)
    {
      val basicContent = if (calloutDetails.alternativeRefCollectionHasEmbeddedPlusSign)
        alternativeRefCollection.getLowAsRef().toString("a") + TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferenceEmbeddedPlusSign") + alternativeRefCollection.getHighAsRef().toString("a")
      else if (calloutDetails.alternativeRefCollectionHasPrefixPlusSign)
        TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferencePrefixPlusSign") + alternativeRefCollection.toString("a")
      else
        alternativeRefCollection.toString("a")

      val textNode = Dom.createTextNode(ownerDocument, TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferenceFormat", basicContent))
      val containerNode = Dom.createNode(ownerDocument, "<_X_reversificationCalloutAlternativeRefCollection/>")
      containerNode.appendChild(textNode)
      res.add(containerNode)
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
    //Dbg.d(row.toString())



    /**************************************************************************/
    /* Is there in fact any footnote at all? */

    var content = dataRow[whichFootnote]
    if (content.isEmpty()) return ""



    /**************************************************************************/
    var res = ""



    /**************************************************************************/
    /* Time was when we would only ever have a single chunk of text, and either
       zero or one associated reference, which appeared after the text.  This
       is no longer the case.  I assume here that we may have ...

       a) Just a single piece of text within %-signs, being a lookup string for
          vernacular text which has no associated reference.

       b) A list starting with a ref, and then alternating between refs and
          %-delimited strings.

       c) A list starting with a %-delimited string, and then alternating
          between refs and %-delimited strings.

       I assume also that with the exception of case 1 above, every %-delimited
       string does have an associated ref, and that the ref precedes the string
       if the very first element is a non-delimited string (which I take to be
       a ref), or else the ref follows the string. */

    if (content.endsWith(".")) content = content.substring(0, content.length - 1) // Remove trailing punctuation.  We'll add it to all strings later.
    content = content.replace(".%", "%") // Don't want punctuation inside text which we will use as lookup keys.

    val elts = content.split("%") // Numbering from zero, even number elts are either empty or are refs; odd number elts are lookup keys.
    val offsetToCorrespondingReference = if (elts[0].isEmpty()) +1 else -1
    for (ix in 1 ..< elts.size step 2) // Pick up the lookup keys.  Each is assumed at this point to have an associated reference
    {
      val lookupKey = elts[ix]
      val ref = elts[ix + offsetToCorrespondingReference]
      res += " " + getFootnoteContent(lookupKey, ref)
    }

    if (!res.endsWith("."))
      res += "."



    /**************************************************************************/
    /* If this is an academic run, we may need to add AncientVersion
       information. */

    if ('A' == basicOrAcademic)
      res += " " + dataRow.ancientVersions



    /**************************************************************************/
    return res.trim()
  }


  /*****************************************************************************/
  private fun getFootnoteContent (lookupKey: String, ref: String): String
  {
    /**************************************************************************/
    /* In most cases, sorting out the reference collection is easy -- there may
       in theory be some ambiguity with single numbers as to what they represent
       (chapters, verses, etc), but we force that here by saying that unadorned
       numbers should be regarded as verses (which, in fact, I think unadorned
       numbers actually are); and in any case, the aim is simply to output
       stuff in the same form as it appears in the reversification data.

       The fly in the ointment are the few rows which contain multiple
       references which are separated by things like '/' and '+', and therefore
       can't be parsed as collections.  We'll deal with the easy cases first
       (the ones where we don't have these odd separators. */

    var refAsString = ref
    val containsSlash = '/' in refAsString
    val containsPlus = '+' in refAsString
    if (!containsSlash && !containsPlus)
    {
      if (refAsString.endsWith("."))
        refAsString = refAsString.substring(0, refAsString.length - 1)
      val rc = RefCollection.rdUsx(usxifyFromStepFormat(refAsString), dflt = null, resolveAmbiguitiesAs = "v")
      return TranslatableFixedText.stringFormat(Language.Vernacular, lookupKey, rc)
    }



    /**************************************************************************/
    /* Which just leaves the difficult case.  Unfortunately, there is at the
       time of writing just one row where the reference looks like 9:9a/2:35f,
       and of course the slash is a problem, because this cannot be parsed as a
       reference collection.  If I'm to have any chance of doing this in such
       a way that I can continue to support vernacular translation, this is
       going to be unpleasantly fiddly ...

       I start off by obtaining the basic text of the message in vernacular
       form.  This should have precisely one entry of the form %RefV<...>.
       I split this text up into that portion which appears the %RefV<...> bit,
       the %RefV<...> bit itself, and the portion which appears afterwards.

       I then use the %RefV<...> portion to format each of the references
       individually.  And then finally I join these formatted references
       together with the relevant separator, and stitch this together with the
       fixed portions of the text.
    */

    val rawMessage = TranslatableFixedText.lookupText(Language.English, getTextKey(lookupKey))
    val regex = "(?i)(?<pre>.*)(?<ref>%Ref.*?>)(?<post>.*)".toRegex()
    val match = regex.matchEntire(rawMessage)
    val refFormat = match!!.groups["ref"]!!.value

    val elts = refAsString.split('/', '+').map { TranslatableFixedText.stringFormat(refFormat, RefCollection.rdUsx(it.trim(), dflt = null, resolveAmbiguitiesAs = "v")) }
    val eltsAssembled = elts.joinToString(TranslatableFixedText.stringFormat(Language.Vernacular, if (containsSlash) "V_reversification_ancientVersionsAlternativeRefsSeparator" else "V_reversification_alternativeReferenceEmbeddedPlusSign"))
    return match.groups["pre"]!!.value + eltsAssembled + match.groups["post"]!!.value
  }


  /****************************************************************************/
  /**
   *  Given a piece of footnote text from the reversification data, gives back
   *  the corresponding key which we can use to look up TranslatableFixedText.
   */

  private fun getTextKey (lookupVal: String): String = "V_reversification_[${lookupVal.trim()}]"





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
      val canonicalTitleNode = chapterNode.getAllNodesBelow().find { m_FileProtocol.isCanonicalNode(chapterNode) }
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
