package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesOsis
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.support.ref.RefCollection
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.stepexception.StepExceptionShouldHaveBeenOverridden
import org.w3c.dom.Document
import org.w3c.dom.Node

/******************************************************************************/
/**
 * It is convenient to have a common view of the USX and OSIS protocols.  In
 * fact to some extent this commonality is illusory, because the two do differ;
 * but they also have many similarities, and it is useful to build upon these.
 *
 * The definitions here are essentially limited to recording details of the
 * protocols themselves -- they tell you nothing at all about the text which
 * is being processed.
 *
 * This is a slight mish-mash.  The previous implementation dealt only with
 * USX, and did quite a lot with it.  The latest implementation has to cope
 * with OSIS as well as USX, and actually *needs* rather less by way of
 * functionality.  However, I wanted to retain the USX functionality in case it
 * ever proved to be useful again, and I have therefore included in the base
 * class all of the methods which were previously defined for USX, even though
 * not all of them are presently being used.
 *
 * One consequence of this is that the OSIS object derived from the base class
 * has stubs in place of a number of methods, because I don't want to spend
 * time implementing functionality which is not actually needed at present.
 *
 * @author ARA "Jamie" Jamieson
 */

open class X_FileProtocol
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  enum class ProtocolType { USX, OSIS, UNKNOWN }
  protected lateinit var Type: ProtocolType
  fun getProtocolType () = Type


  /****************************************************************************/
  open fun readRef (node: Node, attrId: String) = readRef(node[attrId]!!)
  open fun readRef (text: String) = readRefCollection(text).getFirstAsRef()
  open fun readRefCollection (node: Node, attrId: String) = readRefCollection(node[attrId]!!)
  open fun readRefCollection (text: String): RefCollection = throw StepExceptionShouldHaveBeenOverridden()

  open fun attrName_chapterSid (): String = throw StepExceptionShouldHaveBeenOverridden()
  open fun attrName_note () = "note"
  open fun attrName_verseEid (): String = throw StepExceptionShouldHaveBeenOverridden()
  open fun attrName_verseSid (): String = throw StepExceptionShouldHaveBeenOverridden()
  open fun attrName_strong (): String = throw StepExceptionShouldHaveBeenOverridden()

  open fun tagName_book (): String = throw StepExceptionShouldHaveBeenOverridden()
  open fun tagName_chapter (): String = throw StepExceptionShouldHaveBeenOverridden()
  open fun tagName_note (): String = throw StepExceptionShouldHaveBeenOverridden()
  open fun tagName_verse (): String = throw StepExceptionShouldHaveBeenOverridden()

  fun tagName_cell () = "cell"
  fun tagName_row () = "row"
  fun tagName_table () = "table"
  open fun attrName_tableHeaderCellStyle () = "style"

  fun getSid (sidVerse: Node) = sidVerse[attrName_verseSid()]!!
  open fun bookNameToNumber (name: String): Int = throw StepExceptionShouldHaveBeenOverridden()
  open fun getBookAbbreviation (doc: Document): String = throw StepExceptionShouldHaveBeenOverridden() // Assumes just one book per file.
  open fun getBookAbbreviation (rootNode: Node): String = throw StepExceptionShouldHaveBeenOverridden()
  open fun getBookNode (doc: Document): Node? = throw StepExceptionShouldHaveBeenOverridden()
  open fun getExtendedNodeName (node: Node): String = throw StepExceptionShouldHaveBeenOverridden()
  open fun getSidAsRefKey (sidVerse: Node) = readRef(getSid(sidVerse)).toRefKey()
  open fun getTagsWithNumberedLevels (): Set<String> = throw StepExceptionShouldHaveBeenOverridden()
  open fun insertBlankLineIntoTable (tableNode: Node): Unit = throw StepExceptionShouldHaveBeenOverridden()
  open fun isBookNode (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isCanonicalTitleNode (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isCrossReferenceFootnoteNode (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isDummySid (sidVerse: Node): Boolean = RefBase.isDummyValue(Ref.getV(getSidAsRefKey(sidVerse)))
  open fun isExplanatoryFootnoteNode (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isIntroductionNode (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isNoteNode (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isNumberedLevelTag (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isParaWhichCouldContainMultipleVerses (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isPlainVanillaPara (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isPoetryPara (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isSpanType (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isSpeakerNode (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isStrongsNode (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isXrefNode (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun makeCanonicalTitleNode (doc: Document): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun makeDoNothingMarkup (doc: Document): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun makeFootnoteNode (doc: Document, refKeyForOsisIdOfFootnote: RefKey, text: String, caller: String? = null): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun makeItalicsNode (doc: Document): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun makePlainVanillaParaNode (doc: Document): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun makeVerseEidNode (doc: Document, refKey: Pair<RefKey, RefKey?>): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun makeVerseEidNode (doc: Document, refAsString: String): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun makeVerseSidNode (doc: Document, refKey: Pair<RefKey, RefKey?>): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun makeVerseSidNode (doc: Document, refAsString: String): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun recordTagChange (node: Node, newTag: String, newStyleOrType: String? = null, reason: String? = null): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun refToString (refKey: RefKey): String = throw StepExceptionShouldHaveBeenOverridden()
  open fun standardiseCallout (noteNode: Node): Unit = throw StepExceptionShouldHaveBeenOverridden()
  open fun updateVerseSid (verse: Node, refKey: RefKey): Unit = throw StepExceptionShouldHaveBeenOverridden()


  /****************************************************************************/
  /**
  * Returns the TagDescriptor for a given node.
  *
  * @param node: Node
  * @return TagDescriptor
  */

  fun getTagDescriptor (node: Node): TagDescriptor
  {
    val key = getExtendedNodeName(node)
    return m_TagDetails[key] ?: m_TagDetails[Dom.getNodeName(node)]!!
  }


  /****************************************************************************/
  /**
   * Essentially this indicates the canonicity of a given node, which
   * whether we can step over the node when trying to place verse ends.  The
   * one exception is notes: they obviously aren't canonical, but we need to
   * pretend they are, because they need to remain as part of the verse.
   *
   * @param node
   * @return 'Y' (definitely contains canonical text or is a note node);
   *         'N' (definitely does not contain canonical text);
   *         '?' (may or may not contain canonical text -- deduce from context)
   *         'X' (a node of a type we are not presently catering.
   *         Plus the node which gave us the definite decision.
   */

  fun getVerseEndInteraction (node: Node): Pair<Char, Node>
  {
    var n = node
    while (true)
    {
      var key = Osis_FileProtocol.getExtendedNodeName(n)



      /************************************************************************/
      /* Deal with text and comments first. */

      if ('#' == key[0])
      {
        when (key[1])
        {
          'c' -> return Pair('N', n) // Comment: definitely non-canonical.

          't' -> // Text: Can skip whitespace.
          {
            if (n.textContent.trim().isEmpty())
              return Pair('N', n) // Can always skip whitespace.

            n = n.parentNode
            continue
          }
        } // when
      } // if ('#' == key[0])



      /************************************************************************/
      /* I _think_ it's also always ok to skip over an empty node. */

      if (!n.hasChildNodes())
        return Pair('N', n)



      /************************************************************************/
      /* Not text or comment.  Look up in table. */

      if (key.last() in "123456")  // USX node names may contain levels.  The lookup table we use here does not.
        key = key.substring(0, key.length - 1)

      if (isNoteNode(n) || isXrefNode(n)) return Pair('Y', n) // Treat notes and xrefs as though they were canonical, so they remain with the verse.

      val res = m_TagDetails[key]?.canonicity ?: m_TagDetails[Dom.getNodeName(node)]!!.canonicity // Try looking up the extended name, and failing that, the non-extended version.

      when (res)
      {
        'Y', 'N' -> return Pair(res, n)
        'X' -> throw StepException("Encountered node for which we have no canonicity details: ${Dom.toString(n)}.")
      }

      n = n.parentNode
    } // while
  }


  /****************************************************************************/
  /**
  * Returns an indication of whether a given node is canonical.
  *
  * @param node
  * @return True if node is canonical.
  */

  fun isCanonicalNode (node: Node): Boolean
  {
    var p = node
    while (true)
    {
      val canonicity = getTagDescriptor(node).canonicity
      if ('Y' == canonicity || "chapter" == Dom.getNodeName(p)) return true
      if ('N' == canonicity) break
      p = p.parentNode ?: return false
    }

    return false
  }


  /****************************************************************************/
  /**
   * Returns an indication of whether a given node is definitely canonical.
   *
   * @param node Node of interest.
   * @return True if node is inherently non-canonical.
   */

  fun isInherentlyCanonicalTag (node: Node) = 'Y' == (m_TagDetails[getExtendedNodeName(node)]?.canonicity ?: m_TagDetails[Dom.getNodeName(node)]!!.canonicity)


  /****************************************************************************/
  /**
   * Returns an indication of whether a given node is non-canonical in its own
   * right (not by reference to ancestors).
   *
   * @param node Node of interest.
   * @return True if node is inherently non-canonical.
   */

  fun isInherentlyNonCanonicalTag (node: Node) = 'N' == (m_TagDetails[getExtendedNodeName(node)]?.canonicity ?: m_TagDetails[Dom.getNodeName(node)]!!.canonicity)



  /****************************************************************************/
  /**
   * Returns an indication of whether a given node is non-canonical in its own right (not by reference to ancestors).
   *
   * @param node Node of interest.
   * @return True if node is inherently non-canonical.
   */

  fun isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag (node: Node): Boolean
  {
    var n = node
    while (true)
    {
      if (isInherentlyNonCanonicalTag(n)) return true
      if (isInherentlyCanonicalTag(n)) return false
      n = n.parentNode
      if (n is Document) return true
    }
  }
  
  
  
  
  
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Protected                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  data class TagDescriptor (val canonicity: Char, val span: Char)
  protected val m_TagDetails: MutableMap<String, TagDescriptor> = mutableMapOf()
}





/******************************************************************************/
/**
* Details of the file protocol for OSIS.  In order to make it possible to carry
* over details of the previous *USX* implementation, I have included here
* various methods which are actually not used at present, and which therefore
* I haven't bothered to implement correctly (or at all).
*/

object Osis_FileProtocol: X_FileProtocol()
{
  /****************************************************************************/
  override fun attrName_chapterSid () = "osisID"
  override fun attrName_strong () = "lemma"
  override fun attrName_tableHeaderCellStyle () = "%%%garbage%%%" // OSIS doesn't have one of these.
  override fun attrName_verseEid () = "eID"
  override fun attrName_verseSid () = "sID"

  override fun tagName_chapter () = "chapter"
  override fun tagName_note () = "note"
  override fun tagName_verse () = "verse"

  override fun bookNameToNumber (name: String) = BibleBookNamesOsis.nameToNumber(name)
  override fun getBookAbbreviation (doc: Document) = getBookAbbreviation(Dom.findNodeByName(doc,"book", false)!!)
  override fun getBookAbbreviation (rootNode: Node) = rootNode["osisID"]!!
  override fun getBookNode (doc: Document) = Dom.findNodeByName(doc, "book") ?: Dom.findNodeByAttributeValue(doc, "div", "type", "book")!!
  override fun isBookNode (node: Node) = "book" == Dom.getNodeName(node) || "div:book" == getExtendedNodeName(node)
  override fun isNoteNode (node: Node) = "note" == Dom.getNodeName(node)
  override fun isXrefNode (node: Node) = "note" == Dom.getNodeName(node)


  /****************************************************************************/
  /**
   * Returns either tagName or tagName plus type where the latter is defined.
   *
   * @param node: Node whose details are required.
   * @return Expanded tag name.
   */

   override fun getExtendedNodeName (node: Node): String
   {
      var res = Dom.getNodeName(node)
      if ("type" in node) res += ":" + node["type"]
      return res
   }


  /****************************************************************************/
  /**
   * Returns details of tags which have numbered levels.
   *
   * @return Tags with numbered levels.
   */

  override fun getTagsWithNumberedLevels () = setOf<String>()


  /****************************************************************************/
  /**
  * Inserts a blank line into a table.
  *
  * @param tableNode
  */

  override fun insertBlankLineIntoTable (tableNode: Node)
  {
    val blankLineA = Dom.createNode(tableNode.ownerDocument, "<p/>") // Need two of these to end up with one blank line when rendered.
    val blankLineB= Dom.createNode(tableNode.ownerDocument, "<p/>")
    Dom.insertNodeBefore(tableNode, blankLineA)
    Dom.insertNodeBefore(tableNode, blankLineB)
  }


  /****************************************************************************/
  /**
  * Returns an indication of whether the given node is a canonical tit;e.
  *
  * @return True if this is a canonical title.
  */

  override fun isCanonicalTitleNode (node: Node) = "title:psalm" == getExtendedNodeName(node)


  /****************************************************************************/
  /**
  * Returns an indication of whether the given node is cross-reference footnote.
  *
  * @return True if this is a cross-reference footnote.
  */

  override fun isCrossReferenceFootnoteNode (node: Node) = "note" == Dom.getNodeName(node) && "crossReference" == node["type"]


  /****************************************************************************/
  /**
  * Returns an indication of whether the given node is an explanatory footnote.
  *
  * @return True if this is an explanatory footnote.
  */

  override fun isExplanatoryFootnoteNode (node: Node) = "note" == Dom.getNodeName(node) && "explanation" == node["type"]


  /****************************************************************************/
  /**
  * Returns an indication of whether a node belongs to the ones which appear in introductory material.
  *
  * @param node Node of interest.
  * @return True if this is an introductory node.
  */

  override fun isIntroductionNode (node: Node) = false // $$$


  /****************************************************************************/
  /**
   * Returns an indication of whether a node is of a type which supports numbered levels.
   *
   * @param node Node of interest.
   * @return List of style attributes.
   */

  override fun isNumberedLevelTag (node: Node) = false


  /****************************************************************************/
  /**
  * Really not too sure about this.  It appears we have to record, in the Sword
  * config file, an indication of whether any paras contain multiple verses
  * (except I'm not sure actually that we do.  To this end, processing elsewhere
  * needs to know which things could in theory contain multiple verses.  This
  * purportedly tells them, except that I'm not 100% sure I've identified the
  * right collection ...
  *
  * @param node Node to check.
  * @return True if could in theory contain multiple verses.
  */

  override fun isParaWhichCouldContainMultipleVerses (node: Node): Boolean
  {
    if ("p" == Dom.getNodeName(node)) return true
    return false
  }


  /****************************************************************************/
  /**
  * Returns an indication of whether this node is a plain vanilla para.
  *
  * @param node Node to be examined.
  * @return True if this is a plain vanilla para.
  */

  override fun isPlainVanillaPara (node: Node): Boolean = "p" == Dom.getNodeName(node)


  /****************************************************************************/
  /**
  * Returns an indication of whether this node is a poetry para.
  *
  * @param node Node to be examined.
  * @return True if this is a poetry para.
  */

  override fun isPoetryPara (node: Node): Boolean = "l" == Dom.getNodeName(node)


  /****************************************************************************/
  /**
  * Returns an indication of whether this node is a span-type such as char.
  *
  * @param node Node to be examined.
  * @return True if this is a span type.
  */

  override fun isSpanType (node: Node) = 'Y' == m_TagDetails[Dom.getNodeName(node)]!!.span


  /****************************************************************************/
  /**
  * Returns an indication of whether this is a speaker node.
  *
  * @param node Node to be examined.
  * @return True if this is a span type.
  */

  override fun isSpeakerNode (node: Node) = "speaker" == Dom.getNodeName(node) || "_speaker" in node


  /****************************************************************************/
  /**
  * Determines if a given node is a Strong's node.
  *
  * @param node
  * @return True if this is a Strong's node.
  */

  override fun isStrongsNode (node: Node): Boolean = "w" == Dom.getNodeName(node) && "lemma" in node


  /****************************************************************************/
  /**
   * Does what it says on the tin.
   *
   * @param doc
   * @return Canonical title node.
   */

  override fun makeCanonicalTitleNode (doc: Document): Node
  {
    return doc.createNode("<title type='psalm' canonical='true'>")
  }



  /****************************************************************************/
  /**
  * Makes a do-nothing formatting node.  May seem bizarre, but there are some
  * circumstances where STEPBible formats things wrongly unless you wrap the
  * content in one of these.
  *
  * @param doc
  * @ return Do-nothing node.
  */

  override fun makeDoNothingMarkup (doc: Document) = Dom.createNode(doc, "<hi type='normal'/>")


  /****************************************************************************/
  /**
  * Does what it says on the tin.
  *
  * @param doc Document within which the footnote node is created.
  * @param refKeyForOsisIdOfFootnote Reference with which the footnote is associated.
  * @param text Text of footnote.
  * @param caller Callout for footnote.
  * @return Footnote node.
  */

  override fun makeFootnoteNode (doc: Document, refKeyForOsisIdOfFootnote: RefKey, text: String, caller: String?): Node
  {
    val theCaller = caller ?: m_ExplanationFootnoteCalloutGenerator.get()
    val id = Ref.rd(refKeyForOsisIdOfFootnote).toStringOsis()
    val footnoteNode = Dom.createNode(doc, "<note type='explanation' osisRef='$id' osisID='$id!${Globals.getUniqueIdCounter()}' n='$theCaller'/>")
    val textNode = Dom.createTextNode(doc, text)
    footnoteNode.appendChild(textNode)
    return footnoteNode
  }


  /****************************************************************************/
  /**
  * Makes an italics node.
  *
  * @param doc Document within which the node is created.
  * @return Node.
  */

  override fun makeItalicsNode (doc: Document) = Dom.createNode(doc, "<hi type='italic'/>")


  /****************************************************************************/
  /**
  * Makes a plain vanilla para node.
  *
  * @param doc Document within which the node is created.
  * @return Node.
  */

  override fun makePlainVanillaParaNode (doc: Document) = Dom.createNode(doc, "<p/>")


  /****************************************************************************/
  /**
  * Makes a verse eid tag.
  *
  * @param doc Document within which the tag is created.
  * @param refKey Reference details for sid attribute -- a single refKey, or a
  *   pair where we are dealing with an elision.
  * @return Tag.
  */

  override fun makeVerseEidNode (doc: Document, refKey: Pair<RefKey, RefKey?>): Node
  {
    val refAsString = if (null == refKey.second) Ref.rd(refKey.first).toStringOsis() else RefCollection.rd(listOf(refKey.first, refKey.second!!)).toStringOsis()
    return makeVerseEidNode(doc, refAsString)
  }


  /****************************************************************************/
  /**
  * Makes a verse eid tag.
  *
  * @param doc Document within which the tag is created.
  * @param refAsString
  * @return Tag.
  */

  override fun makeVerseEidNode (doc: Document, refAsString: String): Node
  {
    return Dom.createNode(doc, "<verse eID='$refAsString'/>")
  }


  /****************************************************************************/
  /**
  * Makes a verse sid tag.
  *
  * @param doc Document within which the tag is created.
  * @param refKey Reference details for sid attribute -- a single refKey, or a
  *   pair where we are dealing with an elision.
  * @return Tag.
  */

  override fun makeVerseSidNode (doc: Document, refKey: Pair<RefKey, RefKey?>): Node
  {
    val refAsString = if (null == refKey.second) Ref.rd(refKey.first).toStringOsis() else RefCollection.rd(listOf(refKey.first, refKey.second!!)).toStringOsis()
    return makeVerseSidNode(doc, refAsString)
  }


  /****************************************************************************/
  /**
  * Makes a verse eid tag.
  *
  * @param doc Document within which the tag is created.
  * @param refAsString
  * @return Tag.
  */

  override fun makeVerseSidNode (doc: Document, refAsString: String): Node
  {
    return Dom.createNode(doc, "<verse osisID='$refAsString' sID='$refAsString'/>")
  }


  /****************************************************************************/
  /**
   * Changes callout to reflect our house style.
   *
   * @param noteNode Note node to be updated.
   */

  override fun standardiseCallout (noteNode: Node)
  {
    noteNode["n"] = ConfigData[if ("explanation" == noteNode["type"]) "stepExplanationCallout" else "stepCrossReferenceCallout"]!!
  }


  /****************************************************************************/
  /**
  * Makes a verse sid tag.
  *
  * @param verse Verse node to be updated.
  * @param refKey Reference details for sid attribute.
  * @return Tag.
  */

  override fun updateVerseSid (verse: Node, refKey: RefKey)
  {
    val refAsString = Ref.rd(refKey).toStringOsis()
    verse["osisID"] = refAsString
    verse["sID"] = refAsString
  }


  /****************************************************************************/
  /**
  * Reads text which may contain either a single reference or a range.
  *
  * @param text
  * @return RefCollection.
  */

  override fun readRefCollection (text: String) = RefCollection.rdOsis(text)


  /****************************************************************************/
  /**
   * Changes the tagName and possibly the style of a given node, recording
   * details of what has happened.  In fact, this doesn't do any recording.
   * The method is here because we already had an equivalent method for USX
   * processing, so I need something for OSIS.  But while recording stuff in
   * USX was useful and permissible, here it is not.
   *
   * @param node Node to be changed.
   * @param newTag New name for node.
   * @param newStyleOrType New value for style or type attribute or null.
   * @param reason Why the change has been made.
   * @return The node itself.
   */

  override fun recordTagChange (node: Node, newTag: String, newStyleOrType: String?, reason: String?): Node
  {
    Dom.setNodeName(node, newTag)

    if (null == newStyleOrType)
      node -= "type"
    else
      node["type"] = newStyleOrType

    return node
  }


  /****************************************************************************/
  /**
  * Converts a refkey to a string representation.
  *
  * @param refKey
  * @return String representation.
  */

  override fun refToString (refKey: RefKey) = Ref.rd(refKey).toStringOsis()





  /****************************************************************************/
  /****************************************************************************/
  /****************************************************************************/
  /****************************************************************************/
  /****************************************************************************/

  private val m_ExplanationFootnoteCalloutGenerator: MarkerHandler = MarkerHandlerFactory.createMarkerHandler(MarkerHandlerFactory.Type.FixedCharacter, ConfigData["stepExplanationCallout"]!!)


  /****************************************************************************/
  init {
    Type = ProtocolType.OSIS

    //*** Replace any code below with the *** OSIS *** code generated by protocolDetails.xlsm. ***

    m_TagDetails["#text"] = TagDescriptor('?', 'N') // Text.  EndVerseInteraction is Skip for empty tags, and from context for others.
    m_TagDetails["#comment"] = TagDescriptor('N', 'N') // Comment.
    m_TagDetails["a"] = TagDescriptor('X', 'N') // Similar to an HTML link. The "http://..." is recorded in the href attribute of this element. The
    m_TagDetails["abbr"] = TagDescriptor('X', 'N') // Abbreviations should be encoded using this element. The expansion attribute records the full
    m_TagDetails["actor"] = TagDescriptor('X', 'N') // Actor is used to encode the name of an actor in a castItem element, which itself occurs in a

    m_TagDetails["caption"] = TagDescriptor('X', 'N') // Caption is used in the figure element to record the caption for a map, image or similar items.
    m_TagDetails["castGroup"] = TagDescriptor('X', 'N') // The castGroup element does not allow text content.
    m_TagDetails["castItem"] = TagDescriptor('X', 'N') // The castItem is a container that groups together information for a particular member of a
    m_TagDetails["castList"] = TagDescriptor('X', 'N') // The castList element appears in the work element of an OSIS document to contain one or
    m_TagDetails["catchWord"] = TagDescriptor('N', 'N') // The catchWord element is used in note and p elements that may appear in note

    m_TagDetails["chapter"] = TagDescriptor('Y', 'N') // Chapter is used as syntactic sugar for the div element. It will most often be found in nonbiblical  I turn div type='chapter' into <chapter>, so there is no need to worry about div type='chapter'.
    m_TagDetails["closer"] = TagDescriptor('Y', 'N') // The closer element is used for the closing portion of a letter, usually containing a final
    m_TagDetails["contributor"] = TagDescriptor('X', 'N') // The contributor element appears only in a work element. It is used to list a person or
    m_TagDetails["coverage"] = TagDescriptor('X', 'N') // The coverage element appears only in a work element. It is used to specify what is
    m_TagDetails["creator"] = TagDescriptor('X', 'N') // The creator element appears only in a work element. The person or organization principally

    m_TagDetails["date"] = TagDescriptor('X', 'N') // The date element is used to record a date in an OSIS document. The type attribute is used to
    m_TagDetails["description"] = TagDescriptor('X', 'N') // The description element is only within a work element. It is used to provide a reader
    m_TagDetails["div:acknowledgement"] = TagDescriptor('X', 'N') // I have to admit I have no clue how most of these are used.  I believe there are many we will never encounter.  Book and chapter are not relevant because I don't mark these as div.  Of the others, I think perhaps paragraph, section and subSection are of interest.
    m_TagDetails["div:afterword"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:annotant"] = TagDescriptor('X', 'N') //

    m_TagDetails["div:appendix"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:article"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:back"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:bibliography"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:body"] = TagDescriptor('X', 'N') //

    m_TagDetails["div:book"] = TagDescriptor('X', 'N') //   Won't encounter div:book because I change it internally to <book>.
    m_TagDetails["div:bookGroup"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:bridge"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:chapter"] = TagDescriptor('X', 'N') //   Won't encounter div:chapter because I change it to <chapter>.
    m_TagDetails["div:colophon"] = TagDescriptor('X', 'N') //

    m_TagDetails["div:commentary"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:concordance"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:coverPage"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:dedication"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:devotional"] = TagDescriptor('X', 'N') //

    m_TagDetails["div:entry"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:front"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:gazetteer"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:glossary"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:imprimatur"] = TagDescriptor('X', 'N') //

    m_TagDetails["div:index"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:introduction"] = TagDescriptor('N', 'N') //
    m_TagDetails["div:majorSection"] = TagDescriptor('N', 'N') //
    m_TagDetails["div:map"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:outline"] = TagDescriptor('X', 'N') //

    m_TagDetails["div:paragraph"] = TagDescriptor('?', 'N') //
    m_TagDetails["div:part"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:preface"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:publicationData"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:section"] = TagDescriptor('?', 'N') //

    m_TagDetails["div:subSection"] = TagDescriptor('?', 'N') //
    m_TagDetails["div:summary"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:tableofContents"] = TagDescriptor('X', 'N') //
    m_TagDetails["div:titlePage"] = TagDescriptor('X', 'N') //
    m_TagDetails["divineName"] = TagDescriptor('Y', 'Y') // The divineName element is used to mark the name of the Deity only. Other names,

    m_TagDetails["figure"] = TagDescriptor('X', 'N') // The figure element is used to insert maps, images and other materials into an OSIS document.
    m_TagDetails["foreign"] = TagDescriptor('Y', 'Y') // The foreign element is used to mark "foreign" words or phrases in a text. That is words or
    m_TagDetails["format"] = TagDescriptor('X', 'N') // The format element appears only in a work element. It is recommended that the format of a
    m_TagDetails["head"] = TagDescriptor('X', 'N') // The head element is used for book, chapter and other headings. While those are sometimes
    m_TagDetails["header"] = TagDescriptor('X', 'N') // The header element is a container that appears only in the osisCorpus or osisText elements.

    m_TagDetails["identifier"] = TagDescriptor('X', 'N') // The identifier element appears only in the work element. The value of the type attribute
    m_TagDetails["hi"] = TagDescriptor('?', 'Y') // The hi element provides a simple text highlighting mechanism.
    m_TagDetails["index"] = TagDescriptor('X', 'N') // The index element has no content and can appear anywhere in the body of an OSIS document.
    m_TagDetails["inscription"] = TagDescriptor('Y', 'Y') // The inscription element is used to mark text that reports a written inscription.
    m_TagDetails["item"] = TagDescriptor('?', 'N') // The item element is used within a list element to hold the content of each item in the list. An

    m_TagDetails["l"] = TagDescriptor('?', 'N') // The l element is used to mark separate lines in a lg (line group) element. This will be most
    m_TagDetails["label"] = TagDescriptor('N', 'N') // The label element is used in an item element to provide a label for the content of an item.
    m_TagDetails["language"] = TagDescriptor('X', 'N') // The language element appears only in a work element. There can be multiple language
    m_TagDetails["lb"] = TagDescriptor('N', 'N') // The lb element is used to indicate a typographical line break in a text. As a milestone type
    m_TagDetails["lg"] = TagDescriptor('?', 'N') // The lg element is generally used to group other lg (line group) and l (line) elements together.

    m_TagDetails["list"] = TagDescriptor('?', 'N') // The list element is used to represent lists and can include a head element for the list.
    m_TagDetails["mentioned"] = TagDescriptor('Y', 'Y') // The mentioned element is used when a name, for instance, is mentioned but not used as
    m_TagDetails["milestone"] = TagDescriptor('N', 'N') // The milestone element is used to mark a location in the text. It does not permit any
    m_TagDetails["milestoneEnd"] = TagDescriptor('N', 'N') // This element should not be used in current OSIS documents. It has been replaced by
    m_TagDetails["milestoneStart"] = TagDescriptor('N', 'N') // This element should not be used in current OSIS documents. It has been replaced by

    m_TagDetails["name"] = TagDescriptor('?', 'Y') // The name element is used to mark place, personal and other names in an OSIS text. The
    m_TagDetails["note"] = TagDescriptor('Y', 'N') // The note element is used for all notes on a text. Liberal use of the type attribute will enable
    m_TagDetails["osis"] = TagDescriptor('X', 'N') // The osis element is the root element of all OSIS texts.
    m_TagDetails["osisCorpus"] = TagDescriptor('X', 'N') // The osisCorpus element has no attributes and may have a header, followed by an
    m_TagDetails["osisText"] = TagDescriptor('X', 'N') // The osisText element is the main container for a text encoded in OSIS. It is composed of

    m_TagDetails["p"] = TagDescriptor('?', 'N') // The p element is used to mark paragraphs in a text. Since paragraphs are one of the most common
    m_TagDetails["publisher"] = TagDescriptor('X', 'N') // The publisher element occurs only in a work element.
    m_TagDetails["q"] = TagDescriptor('?', 'N') // The q element is used to mark quotations in a text.
    m_TagDetails["rdg"] = TagDescriptor('N', 'N') // The rdg element is used to record a variant reading of a text. Most often seen where a note says:
    m_TagDetails["reference"] = TagDescriptor('N', 'N') // The reference element is used to mark references in one text to another text. The type

    m_TagDetails["refSystem"] = TagDescriptor('X', 'N') // The refSystem element occurs only in a work element. It has text only content and is
    m_TagDetails["relation"] = TagDescriptor('X', 'N') // The relation element occurs only in a work element. It has only text only content and is
    m_TagDetails["revisionDesc"] = TagDescriptor('X', 'N') // The revisionDesc element is used only in a header element. It is used to record
    m_TagDetails["rights"] = TagDescriptor('X', 'N') // The rights element is used only in a work element. It is used to specify for a reader the
    m_TagDetails["role"] = TagDescriptor('X', 'N') // Role is used in a castItem element to identify the role of a particular actor.

    m_TagDetails["roleDesc"] = TagDescriptor('X', 'N') // The roleDesc element is used to provide a description of a role in a castItem element.
    m_TagDetails["row"] = TagDescriptor('?', 'N') // The row element occurs only in table elements and is used to contain cell elements.
    m_TagDetails["salute"] = TagDescriptor('?', 'N') // The salute element is used to mark a saluation or opening comments. It is most generally
    m_TagDetails["scope"] = TagDescriptor('X', 'N') // The scope element is used only in a work element. The general area covered by a text is
    m_TagDetails["seg"] = TagDescriptor('Y', 'N') // The seg element should be used for very small divisions, such as within word elements. The  Not sure whether to make this canonical or not, but it looks as though we're not really going to come across it.

    m_TagDetails["signed"] = TagDescriptor('Y', 'N') // The signed element is used to mark the signer of a letter within a closer element.
    m_TagDetails["source"] = TagDescriptor('X', 'N') // The source element appears only in a work element. It is used to indicate the source for a
    m_TagDetails["speaker"] = TagDescriptor('Y', 'N') // The speaker element is used to mark the speaker in a text. It will be used when the speaker
    m_TagDetails["speech"] = TagDescriptor('?', 'N') // The speech element is used to mark speeches in a text.
    m_TagDetails["subject"] = TagDescriptor('X', 'N') // The subject element occurs only in a work element. It consists only of text drawn from a

    m_TagDetails["table"] = TagDescriptor('?', 'N') // The table element contains an optional head element and one or more row elements.
    m_TagDetails["teiHeader"] = TagDescriptor('X', 'N') // The teiHeader element occurs only in a header element. It is used to contain a TEI
    m_TagDetails["title"] = TagDescriptor('X', 'N') // The title element is used to record a title both in a work element and elsewhere in an OSIS text.
    m_TagDetails["title:acrostic"] = TagDescriptor('Y', 'Y') // Acrostic title.
    m_TagDetails["title:continued"] = TagDescriptor('N', 'N') //

    m_TagDetails["title:main"] = TagDescriptor('N', 'N') //
    m_TagDetails["title:parallel"] = TagDescriptor('N', 'N') //
    m_TagDetails["title:psalm"] = TagDescriptor('?', 'N') //   We may need to look inside this, because some texts place verse tags inside the canonical title.
    m_TagDetails["title:sub"] = TagDescriptor('N', 'N') //
    m_TagDetails["titlePage"] = TagDescriptor('X', 'N') // The titlePage element is used to specify a particular title page for an OSIS document.

    m_TagDetails["transChange"] = TagDescriptor('Y', 'N') // The transChange element is used to mark text that is not present in the original  Moot point whether to regard this as canonical or not.
    m_TagDetails["type"] = TagDescriptor('X', 'N') // The type element occurs only in a work element. It is used to indicate to the reader the type of
    m_TagDetails["verse"] = TagDescriptor('Y', 'N') // The verse element should almost always be used in its milestoneable form. While some older  I've marked this as non-canonical purely because it does not normally contain anything.
    m_TagDetails["w"] = TagDescriptor('Y', 'N') // The w element is used to encode particular words in a text.  eg Strongs.
    m_TagDetails["work"] = TagDescriptor('X', 'N') // The work element occurs only in a header element. It provides all the basic identification and

    m_TagDetails["book"] = TagDescriptor('N', 'N') // I turn div type='book' into <book> to make processing easier.  There is no need for it to be marked as canonical, because the chapters it contains wil be marked as such.

    // *** End of replacement code. ***
  }
}





/******************************************************************************/
/**
* Details of the file protocol for USX.  A large part of this has been carried
* over from the previous implementation, but I have not put effort into making
* sure that all of the other stuff has been carried over correctly -- stuff
* which is not being used in the present implementation.
*/

object Usx_FileProtocol: X_FileProtocol()
{
  /****************************************************************************/
  override fun attrName_chapterSid () = "sid"
  override fun attrName_verseEid () = "eid"
  override fun attrName_verseSid () = "sid"
  override fun attrName_strong () = "lemma"
  override fun tagName_chapter () = "chapter"
  override fun tagName_note () = "note"
  override fun tagName_verse () = "verse"

  override fun attrName_tableHeaderCellStyle () = "style"

  fun internalAttrNameFor_bookTitle () = "_X_bookTitle"

  override fun getBookAbbreviation (doc: Document) = getBookAbbreviation(Dom.findNodeByName(doc,"book", false)!!)
  override fun getBookAbbreviation (rootNode: Node) = rootNode["code"]!!
  override fun bookNameToNumber (name: String) = BibleBookNamesUsx.nameToNumber(name)

  override fun getBookNode (doc: Document) = Dom.findNodeByName(doc, "book")
  override fun isBookNode (node: Node) = "book" == Dom.getNodeName(node)
  override fun isNoteNode (node: Node) = "note" == Dom.getNodeName(node)
  override fun isXrefNode (node: Node) = "note" == Dom.getNodeName(node)

  override fun isCanonicalTitleNode (node: Node) = "para:d" == Osis_FileProtocol.getExtendedNodeName(node)


  /****************************************************************************/
  /**
   * Returns either tagName or tagName plus style where the latter is defined.
   *
   * @param node: Node whose details are required.
   * @return Expanded tag name.
   */

   override fun getExtendedNodeName (node: Node): String
   {
      var res = Dom.getNodeName(node)
      if ("style" in node) res += ":" + node["style"]
      return res
   }


  /****************************************************************************/
  /**
   * Returns details of tags which have numbered levels.
   *
   * @return Tags with numbered levels.
   */

  override fun getTagsWithNumberedLevels () = m_TagsWithNumberedLevels


  /****************************************************************************/
  /**
  * Inserts a blank line into a table.
  *
  * @param tableNode
  */

  override fun insertBlankLineIntoTable (tableNode: Node)
  {
    val blankLineA = Dom.createNode(tableNode.ownerDocument, "<para style='b'/>") // Need two of these to end up with one blank line when rendered.
    val blankLineB= Dom.createNode(tableNode.ownerDocument, "<para style='b'/>")
    Dom.insertNodeBefore(tableNode, blankLineA)
    Dom.insertNodeBefore(tableNode, blankLineB)
  }


  /****************************************************************************/
  /**
  * Returns an indication of whether the given node is cross-reference footnote.
  *
  * @return True if this is a cross-reference footnote.
  */

  override fun isCrossReferenceFootnoteNode (node: Node) = getExtendedNodeName(node) in "ref char:xt"


  /****************************************************************************/
  /**
  * Returns an indication of whether the given node is an explanatory footnote.
  *
  * @return True if this is an explanatory footnote.
  */

  override fun isExplanatoryFootnoteNode (node: Node) = "note" == Dom.getNodeName(node) && "explanation" == node["type"]


  /****************************************************************************/
  /**
  * Returns an indication of whether a node belongs to the ones which appear
  * in introductory material.
  *
  * @param node Node of interest.
  * @return True if this is an introductory node.
  */

  override fun isIntroductionNode (node: Node): Boolean
  {
    var style = Dom.getAttribute(node, "style") ?: return false
    style = "." + style.replace("\\d+".toRegex(), "") + "."
    return C_IntroductionStyles.contains(style)
  }


  /****************************************************************************/
  /**
   * Returns an indication of whether a node is of a type which supports numbered levels.
   *
   * @param node Node of interest.
   * @return List of style attributes.
   */

  override fun isNumberedLevelTag (node: Node) = m_TagsWithNumberedLevels.contains(getExtendedNodeName(node))


  /****************************************************************************/
  /**
  * Really not too sure about this.  It appears we have to record, in the Sword
  * config file, an indication of whether any paras contain multiple verses
  * (except I'm not sure actually that we do.  To this end, processing elsewhere
  * needs to know which things could in theory contain multiple verses.  This
  * purportedly tells them, except that I'm not 100% sure I've identified the
  * right collection ...
  *
  * @param node Node to check.
  * @return True if could in theory contain multiple verses.
  */

  override fun isParaWhichCouldContainMultipleVerses (node: Node): Boolean
  {
    if ("para" != Dom.getNodeName(node)) return false
    val style = Dom.getAttribute(node, "style")
    return ("p" == style || "q" == style || "l" != style)
  }


  /****************************************************************************/
  /**
  * Returns an indication of whether this node is a plain vanilla para.
  *
  * @param node Node to be examined.
  * @return True if this is a plain vanilla para.
  */

  override fun isPlainVanillaPara (node: Node): Boolean = "para" == Dom.getNodeName(node) && "p" == node["style"]!!


  /****************************************************************************/
  /**
  * Returns an indication of whether this node is a poetry para.
  *
  * @param node Node to be examined.
  * @return True if this is a poetry para.
  */

  override fun isPoetryPara (node: Node): Boolean = getExtendedNodeName(node).startsWith("para:q")


  /****************************************************************************/
  /**
  * Returns an indication of whether this node is a span-type such as char.
  *
  * @param node Node to be examined.
  * @return True if this is a span type.
  */

  override fun isSpanType (node: Node) = "char" == Dom.getNodeName(node)


  /****************************************************************************/
  /**
  * Returns an indication of whether this is a speaker node.
  *
  * @param node Node to be examined.
  * @return True if this is a speaker node.
  */

  override fun isSpeakerNode (node: Node) = "para:sp" == getExtendedNodeName(node)


 /****************************************************************************/
 /**
  * Determines if a given node is a Strong's node.
  *
  * @param node
  * @return True if this is a Strong's node.
  */

  override fun isStrongsNode (node: Node): Boolean = "char" == Dom.getNodeName(node) && "strong" in node


  /****************************************************************************/
  /**
   * Does what it says on the tin.
   *
   * @param doc
   * @return Canonical title node.
   */

  override fun makeCanonicalTitleNode (doc: Document): Node
  {
    return doc.createNode("<para style='d'/>")
  }



  /****************************************************************************/
  /**
  * Makes a do-nothing formatting node.  May seem bizarre, but there are some
  * circumstances where STEPBible formats things wrongly unless you wrap the
  * content in one of these.
  *
  * @param doc
  * @ return Do-nothing node.
  */

  override fun makeDoNothingMarkup (doc: Document) = Dom.createNode(doc, "<char style='no'/>")


  /****************************************************************************/
  /**
  * Does what it says on the tin.
  *
  * @param doc Document within which the footnote node is created.
  * @param refKeyForOsisIdOfFootnote Reference with which the footnote is associated.
  * @param text Text of footnote.
  * @param caller Callout for footnote.
  * @return Footnote node.
  */

  override fun makeFootnoteNode (doc: Document, refKeyForOsisIdOfFootnote: RefKey, text: String, caller: String?): Node
  {
    TODO()
  }


  /****************************************************************************/
  /**
  * Makes an italics node.
  *
  * @param doc Document within which the node is created.
  * @return Node.
  */

  override fun makeItalicsNode (doc: Document) = Dom.createNode(doc, "<char style='it'/>")


  /****************************************************************************/
  /**
  * Makes a plain vanilla para node.
  *
  * @param doc Document within which the node is created.
  * @return Node.
  */

  override fun makePlainVanillaParaNode (doc: Document) = Dom.createNode(doc, "<para style='p'/>")


  /****************************************************************************/
  /**
  * Makes a verse eid tag.
  *
  * @param doc Document within which the tag is created.
  * @param refKey Reference details for sid attribute -- a single refKey, or a
  *   pair where we are dealing with an elision.
  * @return Tag.
  */

  override fun makeVerseEidNode (doc: Document, refKey: Pair<RefKey, RefKey?>): Node
  {
    val refAsString = if (null == refKey.second) Ref.rd(refKey.first).toStringOsis() else RefCollection.rd(listOf(refKey.first, refKey.second!!)).toStringOsis()
    return makeVerseEidNode(doc, refAsString)
  }


  /****************************************************************************/
  /**
  * Makes a verse eid tag.
  *
  * @param doc Document within which the tag is created.
  * @param refAsString
  * @return Tag.
  */

  override fun makeVerseEidNode (doc: Document, refAsString: String): Node
  {
    return Dom.createNode(doc, "<verse eid='$refAsString'/>")
  }


  /****************************************************************************/
  /**
  * Makes a verse sid tag.
  *
  * @param doc Document within which the tag is created.
  * @param refKey Reference details for sid attribute -- a single refKey, or a
  *   pair where we are dealing with an elision.
  * @return Tag.
  */

  override fun makeVerseSidNode (doc: Document, refKey: Pair<RefKey, RefKey?>): Node
  {
    val refAsString = if (null == refKey.second) Ref.rd(refKey.first).toStringOsis() else RefCollection.rd(listOf(refKey.first, refKey.second!!)).toStringOsis()
    return makeVerseSidNode(doc, refAsString)
  }


  /****************************************************************************/
  /**
  * Makes a verse sid tag.
  *
  * @param doc Document within which the tag is created.
  * @param refAsString
  * @return Tag.
  */

  override fun makeVerseSidNode (doc: Document, refAsString: String): Node
  {
    return Dom.createNode(doc, "<verse sid='$refAsString'/>")
  }


  /****************************************************************************/
  /**
   * Changes callout to reflect our house style.
   *
   * @param noteNode Note node to be updated.
   */

  override fun standardiseCallout (noteNode: Node)
  {
    noteNode["callout"] = ConfigData[if ("f" == noteNode["style"]) "stepExplanationCallout" else "stepCrossReferenceCallout"]!!
  }


  /****************************************************************************/
  /**
  * Updates a verse sid tag.
  *
  * @param verse Verse node to be updated.
  * @param refKey Reference details for sid attribute.
  * @return Tag.
  */

  override fun updateVerseSid (verse: Node, refKey: RefKey)
  {
    val refAsString = Ref.rd(refKey).toStringOsis()
    verse["sid"] = refAsString
  }


  /****************************************************************************/
  /**
  * Reads text which may contain either a single reference or a range.
  *
  * @param text
  * @return RefCollection.
  */

  override fun readRefCollection (text: String) = RefCollection.rdUsx(text)


  /****************************************************************************/
  /**
   * Changes the tagName and possibly the style of a given node, recording
   * details of what has happened.
   *
   * @param node Node to be changed.
   * @param newTag New name for node.
   * @param newStyleOrType New value for style or type attribute or null.
   * @param reason Why the change has been made.
   * @return The node itself.
   */

  override fun recordTagChange (node: Node, newTag: String, newStyleOrType: String?, reason: String?): Node
  {
    node["_X_origTag"] = getExtendedNodeName(node)
    node["_X_action"] = "Changed tagName or style/type"
    if (null != reason) node["_X_tagOrStyleChangedReason"] = reason
    Dom.setNodeName(node, newTag)

    if (null == newStyleOrType)
      node -= "style"
    else
      node["style"] = newStyleOrType

    return node
  }


  /****************************************************************************/
  /**
  * Converts a refkey to a string representation.
  *
  * @param refKey
  * @return String representation.
  */

  override fun refToString (refKey: RefKey) = Ref.rd(refKey).toStringUsx()





  /****************************************************************************/
  /****************************************************************************/
  /****************************************************************************/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private const val C_IntroductionStyles = ".ib.ie.iex.ili.im.imi.imq.imt.imte.io.iot.ip.ipi.ipq.ipr.iq.is.rem."
  private var m_TagsWithNumberedLevels: Set<String>


  /****************************************************************************/
  init {
    Type = ProtocolType.USX

    //*** Replace any code below with the *** USX *** code generated by protocolDetails.xlsm. ***

    m_TagDetails["#text"] = TagDescriptor('?', 'N') // Text.
    m_TagDetails["_X_bracketStart:ili"] = TagDescriptor('N', 'N') // Brackets the type of subpara indicated by the name.  Use is configurable, and STEP does not use it.
    m_TagDetails["_X_bracketEnd:ili"] = TagDescriptor('N', 'N') // Brackets the type of subpara indicated by the name.  Use is configurable, and STEP does not use it.
    m_TagDetails["_X_bracketStart:io"] = TagDescriptor('N', 'N') // Brackets the type of subpara indicated by the name.  Use is configurable, and STEP does not use it.
    m_TagDetails["_X_bracketEnd:io"] = TagDescriptor('N', 'N') // Brackets the type of subpara indicated by the name.  Use is configurable, and STEP does not use it.

    m_TagDetails["_X_bracketStart:li"] = TagDescriptor('?', 'N') // Brackets the type of subpara indicated by the name.  Use is configurable, and STEP does not use it.
    m_TagDetails["_X_bracketEnd:li"] = TagDescriptor('?', 'N') // Brackets the type of subpara indicated by the name.  Use is configurable, and STEP does not use it.
    m_TagDetails["_X_bracketStart:q"] = TagDescriptor('?', 'N') // Brackets the type of subpara indicated by the name.  Use is configurable, and STEP does not use it.
    m_TagDetails["_X_bracketEnd:q"] = TagDescriptor('?', 'N') // Brackets the type of subpara indicated by the name.  Use is configurable, and STEP does not use it.
    m_TagDetails["chapter"] = TagDescriptor('Y', 'N') // STEP version of chapter (encloses text, by contrast with USX milestones).  May actually contain non-canonical material, but I assume such material will appear within specific non-canonical tags.  Anything not in such tags is assumed to be canonical.

    m_TagDetails["_X_comment"] = TagDescriptor('N', 'N') // Comment.
    m_TagDetails["_X_contentOnly"] = TagDescriptor('?', 'N') // Used to replace an existing tag, when we want to retain just the content of the tag, but not do anything to render the tag itself.
    m_TagDetails["_X_headingBlock"] = TagDescriptor('?', 'N') // Encapsulates headings.
    m_TagDetails["_X_introductionBlock:book"] = TagDescriptor('N', 'N') // Encapsulates introductory material.
    m_TagDetails["_X_introductionBlock:chapter"] = TagDescriptor('N', 'N') // Encapsulates introductory material.

    m_TagDetails["_X_reversificationCalloutData"] = TagDescriptor('N', 'N') // Encapsulates callout information in reversified text.
    m_TagDetails["_X_reversificationMoveOriginalText"] = TagDescriptor('N', 'N') // Optionally on cross-chapter Moves we leave the original source in place (but changed, roughly speaking, to subverses of the preceding non-moved verse).  This enables us to ignore this text when carrying out validation.
    m_TagDetails["_X_reversificationSourceVerse"] = TagDescriptor('N', 'N') // A char-level marker which encloses an indicator of the source verse for a reversification action.
    m_TagDetails["_X_strong"] = TagDescriptor('Y', 'N') // My own Strongs markup.
    m_TagDetails["_X_subverseSeparator"] = TagDescriptor('N', 'N') // Used within verses to mark subverse boundaries.

    m_TagDetails["_X_tableWithNoContainedVerseTags"] = TagDescriptor('Y', 'N') // A table which is easy to process (it's vanishingly unlikely we'll ever hit one of these).
    m_TagDetails["_X_usx"] = TagDescriptor('N', 'N') // Overall header.
    m_TagDetails["_X_verseBoundaryWithinElidedTable"] = TagDescriptor('N', 'N') // Used to mark verse boundaries where we have elided verses so that a table lies entirely within a single verse.
    m_TagDetails["book"] = TagDescriptor('N', 'N') // Book.  See book:id.  We have this alternative just in case a text does not include the style attribute.
    m_TagDetails["book:id"] = TagDescriptor('N', 'N') // Book:Need to have this because it's in USX.  However, it is replaced pretty sharpish during processing by the _X_ equivalent.

    m_TagDetails["cell"] = TagDescriptor('?', 'N') // Cell within row.
    m_TagDetails["cell:tc#"] = TagDescriptor('?', 'N') // Cell within row.
    m_TagDetails["cell:tcc#"] = TagDescriptor('?', 'N') // Cell within row.
    m_TagDetails["cell:tcr#"] = TagDescriptor('?', 'N') // Cell within row.
    m_TagDetails["cell:th#"] = TagDescriptor('?', 'N') // Cell within row.

    m_TagDetails["cell:thr#"] = TagDescriptor('?', 'N') // Cell within row.
    m_TagDetails["chapter"] = TagDescriptor('Y', 'N') // Chapter.  See chapter:c.  We have this alternative just in case a text does not include the style attribute.
    m_TagDetails["chapter:c"] = TagDescriptor('?', 'N') // Chapter.  Need to have this because it's in USX.  However, it is replaced pretty sharpish during processing by the _X_ equivalent.
    m_TagDetails["char:add"] = TagDescriptor('N', 'Y') // Translator’s addition.  Needs to be AsParent because it may crop up in canonical psalm titles, which are placed outside canonical text. 03-Dec-22: Made this canonical, so it appears to be part of verse.
    m_TagDetails["char:bd"] = TagDescriptor('?', 'Y') // Bold text.

    m_TagDetails["char:bdit"] = TagDescriptor('?', 'Y') // Bold + italic text.
    m_TagDetails["char:bk"] = TagDescriptor('N', 'Y') // Quoted book title.
    m_TagDetails["char:dc"] = TagDescriptor('?', 'Y') // Deuterocanonical/LXX additions or insertions in the Protocanonical text.
    m_TagDetails["char:em"] = TagDescriptor('?', 'Y') // Emphasis text.
    m_TagDetails["char:fdc"] = TagDescriptor('N', 'Y') // Footnote material to be included only in publications that contain the Deuterocanonical/Apocrypha books.

    m_TagDetails["char:fk"] = TagDescriptor('N', 'Y') // A specific keyword/term from the text for which the footnote is being provided.
    m_TagDetails["char:fl"] = TagDescriptor('N', 'Y') // Footnote 'label' text.
    m_TagDetails["char:fm"] = TagDescriptor('N', 'Y') // Reference to caller of previous footnote.
    m_TagDetails["char:fp"] = TagDescriptor('N', 'Y') // Footnote additional paragraph.
    m_TagDetails["char:fq"] = TagDescriptor('N', 'Y') // Footnote translation quotation.

    m_TagDetails["char:fqa"] = TagDescriptor('N', 'Y') // Footnote alternate translation.
    m_TagDetails["char:fr"] = TagDescriptor('N', 'Y') // Footnote reference.
    m_TagDetails["char:ft"] = TagDescriptor('N', 'Y') // Footnote text.
    m_TagDetails["char:fv"] = TagDescriptor('N', 'Y') // Footnote verse number.
    m_TagDetails["char:fw"] = TagDescriptor('N', 'Y') // Footnote witness list.

    m_TagDetails["char:ior"] = TagDescriptor('N', 'Y') // Introduction outline reference range.
    m_TagDetails["char:iqt"] = TagDescriptor('N', 'Y') // Introduction quoted text.
    m_TagDetails["char:it"] = TagDescriptor('?', 'Y') // Italic text.
    m_TagDetails["char:jmp"] = TagDescriptor('N', 'Y') // Available for associating linking attributes to a span of text.
    m_TagDetails["char:k"] = TagDescriptor('N', 'Y') // Keyword / keyterm.

    m_TagDetails["char:lik"] = TagDescriptor('N', 'Y') // List entry “key” content.
    m_TagDetails["char:litl"] = TagDescriptor('N', 'Y') // List entry total.
    m_TagDetails["char:liv#"] = TagDescriptor('N', 'Y') // List entry “value” content.
    m_TagDetails["char:nd"] = TagDescriptor('?', 'Y') // Name of God.
    m_TagDetails["char:no"] = TagDescriptor('?', 'Y') // Normal text.

    m_TagDetails["char:ord"] = TagDescriptor('N', 'Y') // Ordinal number ending (i.e. in 1st — 1<char style="ord">st</char>).
    m_TagDetails["char:pn"] = TagDescriptor('N', 'Y') // Proper name.
    m_TagDetails["char:png"] = TagDescriptor('N', 'Y') // Geographic proper name (see doc -- particularly of use in China).
    m_TagDetails["char:pro"] = TagDescriptor('N', 'Y') // Pronunciation information – deprecated; use char:rb instead.
    m_TagDetails["char:qac"] = TagDescriptor('N', 'Y') // Used to indicate the acrostic letter within a poetic line.

    m_TagDetails["char:qs"] = TagDescriptor('N', 'Y') // Selah.
    m_TagDetails["char:qt"] = TagDescriptor('Y', 'Y') // Old Testament quotations in the New Testament, or other quotations.
    m_TagDetails["char:rb"] = TagDescriptor('N', 'Y') // Ruby glossing.
    m_TagDetails["char:rq"] = TagDescriptor('N', 'Y') // Inline quotation reference(s).
    m_TagDetails["char:sc"] = TagDescriptor('?', 'Y') // Small cap text.

    m_TagDetails["char:sig"] = TagDescriptor('Y', 'Y') // Signature of the author of an epistle.
    m_TagDetails["char:sls"] = TagDescriptor('?', 'Y') // Passage of text based on a secondary language or alternate text source.  (Used eg to highlight where the underlying text moves from Hebrew to Aramaic.)  Strictly this should be canonical, but I've seen some texts where it appears in Psalm titles, and since for processing reasons I've chosen to represent these as being non-canonical, I've had to mark this AsParent.
    m_TagDetails["char:sup"] = TagDescriptor('?', 'Y') // Superscript text.
    m_TagDetails["char:tl"] = TagDescriptor('Y', 'Y') // Transliterated (or foreign) word(s).  Eg Eli, Eli, lema sabachtani?
    m_TagDetails["char:va"] = TagDescriptor('N', 'Y') // Second (alternate) verse number.  Note that this is in the schema definition but is not mentioned in the documentation.

    m_TagDetails["char:vp"] = TagDescriptor('N', 'Y') // Published verse number -- a verse marking which would be used in the published text.  Note that this is in the schema definition but is not mentioned in the documentation.
    m_TagDetails["char:w"] = TagDescriptor('Y', 'Y') // Wordlist/glossary/dictionary entry.
    m_TagDetails["char:wa"] = TagDescriptor('N', 'Y') // Aramaic word list entry.
    m_TagDetails["char:wg"] = TagDescriptor('N', 'Y') // Greek word list entry.
    m_TagDetails["char:wh"] = TagDescriptor('N', 'Y') // Hebrew word list entry.

    m_TagDetails["char:wj"] = TagDescriptor('Y', 'Y') // Words of Jesus.
    m_TagDetails["char:xdc"] = TagDescriptor('N', 'Y') // For reference notes: References (or other material) to be included only in publications that contain the Deuterocanonical/Apocrypha books.  Deprecated.
    m_TagDetails["char:xk"] = TagDescriptor('N', 'Y') // For reference notes: A keyword from the scripture translation text which the target reference(s) also refer to.
    m_TagDetails["char:xnt"] = TagDescriptor('N', 'Y') // For reference notes: References (or other text) which is only to be included in publications that contain the New Testament books.
    m_TagDetails["char:xo"] = TagDescriptor('N', 'Y') // For reference notes: Cross-reference origin.

    m_TagDetails["char:xop"] = TagDescriptor('N', 'Y') // For reference notes: Published cross reference origin text.
    m_TagDetails["char:xot"] = TagDescriptor('N', 'Y') // For reference notes: References (or other text) which is only to be included in publications that contain the Old Testament books.
    m_TagDetails["char:xq"] = TagDescriptor('N', 'Y') // For reference notes: A quotation from the scripture text.
    m_TagDetails["char:xq"] = TagDescriptor('N', 'Y') // For reference notes: A quotation from the scripture text.
    m_TagDetails["char:xt"] = TagDescriptor('N', 'Y') // For reference notes: Cross reference target reference(s).

    m_TagDetails["char:xta"] = TagDescriptor('N', 'Y') // For reference notes: Target reference(s) extra / added text.
    m_TagDetails["figure"] = TagDescriptor('N', 'N') // Figure.
    m_TagDetails["note:ef"] = TagDescriptor('N', 'N') // Study note.
    m_TagDetails["note:ex"] = TagDescriptor('N', 'N') // Extended cross reference.
    m_TagDetails["note:f"] = TagDescriptor('N', 'N') // Footnote.

    m_TagDetails["note:fe"] = TagDescriptor('N', 'N') // Endnote.
    m_TagDetails["note:x"] = TagDescriptor('N', 'N') // Cross reference.
    m_TagDetails["optbreak"] = TagDescriptor('N', 'N') // Optional line break.
    m_TagDetails["para:b"] = TagDescriptor('N', 'N') // Blank line.
    m_TagDetails["para:cd"] = TagDescriptor('N', 'N') // Chapter description.

    m_TagDetails["para:cl"] = TagDescriptor('N', 'N') // The chapter “label” to be used when the chosen publishing presentation will render chapter divisions as headings (not drop cap numerals).
    m_TagDetails["para:cls"] = TagDescriptor('Y', 'N') // Closure of epistle.
    m_TagDetails["para:cp"] = TagDescriptor('N', 'N') // Published chapter number. Probably really non-canonical, but AsParent makes it less likely to mess things up.  Contents are suppressed anyway.
    m_TagDetails["para:d"] = TagDescriptor('Y', 'N') // Chapter description (canonical psalm title).
    m_TagDetails["para:h"] = TagDescriptor('N', 'N') // Running header.

    m_TagDetails["para:ib"] = TagDescriptor('N', 'N') // Introduction blank line.
    m_TagDetails["para:ide"] = TagDescriptor('N', 'N') // Some kind of identification -- not exactly sure what.
    m_TagDetails["para:ie"] = TagDescriptor('N', 'N') // Introduction end.
    m_TagDetails["para:iex"] = TagDescriptor('N', 'N') // Introduction explanatory or bridge text (e.g. explanation of missing book in a short Old Testament).  Although this is marked as 'introductory', it looks as though it can turn up elsewhere.
    m_TagDetails["para:ili#"] = TagDescriptor('N', 'N') // Introduction list item.

    m_TagDetails["para:im"] = TagDescriptor('N', 'N') // Introduction flush left (margin) paragraph.
    m_TagDetails["para:imi"] = TagDescriptor('N', 'N') // Indented introduction flush left (margin) paragraph.
    m_TagDetails["para:imq"] = TagDescriptor('N', 'N') // Introduction flush left (margin) quote from scripture text paragraph.
    m_TagDetails["para:imt#"] = TagDescriptor('N', 'N') // Introduction major title.
    m_TagDetails["para:imt#"] = TagDescriptor('N', 'N') // Introduction major title ending.

    m_TagDetails["para:io#"] = TagDescriptor('N', 'N') // Introduction outline entry.
    m_TagDetails["para:iot"] = TagDescriptor('N', 'N') // Introduction outline title.
    m_TagDetails["para:ip"] = TagDescriptor('N', 'N') // Introduction paragraph.
    m_TagDetails["para:ipi"] = TagDescriptor('N', 'N') // Introduction indented paragraph.
    m_TagDetails["para:ipq"] = TagDescriptor('N', 'N') // Introduction quote from scripture text paragraph.

    m_TagDetails["para:ipr"] = TagDescriptor('N', 'N') // Introduction right-aligned paragraph.
    m_TagDetails["para:iq#"] = TagDescriptor('N', 'N') // Introduction poetic line.
    m_TagDetails["para:is#"] = TagDescriptor('N', 'N') // Introduction section heading.
    m_TagDetails["para:lf"] = TagDescriptor('?', 'N') // List footer.
    m_TagDetails["para:lh"] = TagDescriptor('?', 'N') // List header.

    m_TagDetails["para:li#"] = TagDescriptor('?', 'N') // List entry.
    m_TagDetails["para:lim#"] = TagDescriptor('?', 'N') // Indented list entry.
    m_TagDetails["para:lit"] = TagDescriptor('N', 'N') // Liturgical note.
    m_TagDetails["para:litl"] = TagDescriptor('N', 'N') // List entry total.
    m_TagDetails["para:m"] = TagDescriptor('?', 'N') // Margin paragraph.

    m_TagDetails["para:mi"] = TagDescriptor('?', 'N') // Indented flush left paragraph.
    m_TagDetails["para:mr"] = TagDescriptor('N', 'N') // Major section reference range.
    m_TagDetails["para:ms#"] = TagDescriptor('N', 'N') // Major section heading.
    m_TagDetails["para:mt#"] = TagDescriptor('N', 'N') // Main title.
    m_TagDetails["para:mte"] = TagDescriptor('N', 'N') // Main title at introduction ending.

    m_TagDetails["para:nb"] = TagDescriptor('?', 'N') // Paragraph text, with no break from previous paragraph text (at chapter boundary).
    m_TagDetails["para:p"] = TagDescriptor('?', 'N') // Paragraph.
    m_TagDetails["para:pc"] = TagDescriptor('?', 'N') // Centered paragraph.
    m_TagDetails["para:ph#"] = TagDescriptor('?', 'N') // Indented paragraph with hanging indent.
    m_TagDetails["para:pi#"] = TagDescriptor('?', 'N') // Embedded text paragraph.

    m_TagDetails["para:pm"] = TagDescriptor('?', 'N') // Embedded text paragraph.
    m_TagDetails["para:pmc"] = TagDescriptor('?', 'N') // Embedded text closing.
    m_TagDetails["para:pmo"] = TagDescriptor('?', 'N') // Embedded text opening.
    m_TagDetails["para:pmr"] = TagDescriptor('?', 'N') // Embedded text refrain.
    m_TagDetails["para:po"] = TagDescriptor('?', 'N') // Opening of epistle.

    m_TagDetails["para:pr"] = TagDescriptor('?', 'N') // Embedded text refrain.
    m_TagDetails["para:pr"] = TagDescriptor('?', 'N') // Right-aligned paragraph.
    m_TagDetails["para:q#"] = TagDescriptor('Y', 'N') // Poetry.
    m_TagDetails["para:qa"] = TagDescriptor('N', 'N') // Acrostic heading.  Moot point as to whether this should be canonical or not, but it appears I _need_ it to be non-canonical.
    m_TagDetails["para:qc"] = TagDescriptor('Y', 'N') // Centered poetic line.

    m_TagDetails["para:qd"] = TagDescriptor('N', 'N') // Hebrew note.
    m_TagDetails["para:qm#"] = TagDescriptor('Y', 'N') // Embedded text poetic line.
    m_TagDetails["para:qr"] = TagDescriptor('Y', 'N') // Right-aligned poetic line.
    m_TagDetails["para:qs"] = TagDescriptor('Y', 'N') // Selah.
    m_TagDetails["para:r"] = TagDescriptor('N', 'N') // Parallel passage reference(s).

    m_TagDetails["para:rem"] = TagDescriptor('N', 'N') // Remark.  I _think_ I've seen this somewhere or other, although it doesn't appear to be valid USX.  Strictly should be NonCanonical, but usually needs to remain with adjacent items, and Canonical helps achieve that.
    m_TagDetails["para:s#"] = TagDescriptor('N', 'N') // Section heading.  At one point I was giving this as 'AsParent', so that headings within a verse were seen as canonical and therefore remained with the verse.  Now kind of think that may be wrong.
    m_TagDetails["para:sd#"] = TagDescriptor('N', 'N') // Semantic division (vertical whitespace).
    m_TagDetails["para:sp"] = TagDescriptor('N', 'N') // Speaker identification.
    m_TagDetails["para:sr"] = TagDescriptor('N', 'N') // Section reference range.  Strictly speaking, these aren't canonical.  However if a heading appears within a verse, I want it to be seen as a part of that verse.

    m_TagDetails["para:toc#"] = TagDescriptor('N', 'N') // Table of contents.
    m_TagDetails["para:toca#"] = TagDescriptor('N', 'N') // Alternate language long table of comments.
    m_TagDetails["periph"] = TagDescriptor('N', 'N') // Peripheral material.  The USX spec has a whole section on this, which I have not read.
    m_TagDetails["ref"] = TagDescriptor('N', 'N') // Reference.
    m_TagDetails["row"] = TagDescriptor('?', 'N') // Row within table.

    m_TagDetails["row:tr"] = TagDescriptor('?', 'N') // Row within table.
    m_TagDetails["sidebar"] = TagDescriptor('N', 'N') // Note.  I assume this is much the same as note.
    m_TagDetails["table"] = TagDescriptor('?', 'N') // Table.
    m_TagDetails["usx"] = TagDescriptor('N', 'N') // Root node.  Assume direct children are non-canonical.
    m_TagDetails["verse"] = TagDescriptor('N', 'N') // Verse.  See verse:v.  We have this alternative just in case a text does not include the style attribute.

    m_TagDetails["verse:v"] = TagDescriptor('N', 'N') // Verse.  Need to have this because it's in USX.  However, it is replaced pretty sharpish during processing by the _X_ equivalent.

    // *** End of replacement code. ***


    /**************************************************************************/
    m_TagsWithNumberedLevels = m_TagDetails.keys.filter { it.endsWith('#') } .map { it.substring(0, it.length - 1) } .toSet()

    m_TagDetails.keys.filter { it.endsWith('#') } .forEach {// Remove #'s from keys.
      m_TagDetails[it.substring(0, it.length - 1)] = m_TagDetails[it]!!
      m_TagDetails.remove(it)
    }
  } // init
}