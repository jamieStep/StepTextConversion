package org.stepbible.textconverter.utils

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
  open fun isHeadingTag (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isInherentlyCanonicalTag (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isInherentlyNonCanonicalTag (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isIntroductionNode (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isNumberedLevelTag (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isParaWhichCouldContainMultipleVerses (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isPlainVanillaPara (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isSpanType (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isSpeakerNode (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun isStrongsNode (node: Node): Boolean = throw StepExceptionShouldHaveBeenOverridden()
  open fun makeDoNothingMarkup (doc: Document): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun makeFootnoteNode (doc: Document, refKeyForOsisIdOfFootnote: RefKey, text: String, caller: String? = null): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun makeItalicsNode (doc: Document): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun makePlainVanillaParaNode (doc: Document): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun makeVerseEidNode (doc: Document, refKey: RefKey): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun makeVerseSidNode (doc: Document, refKey: RefKey): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun recordTagChange (node: Node, newTag: String, newStyleOrType: String? = null, reason: String? = null): Node = throw StepExceptionShouldHaveBeenOverridden()
  open fun refToString (refKey: RefKey): String = throw StepExceptionShouldHaveBeenOverridden()
  open fun standardiseCallout (noteNode: Node): Unit = throw StepExceptionShouldHaveBeenOverridden()
  open fun updateVerseSid (verse: Node, refKey: RefKey): Unit = throw StepExceptionShouldHaveBeenOverridden()


  /****************************************************************************/
  /**
   * Returns an indication of whether a given node is canonical in its own
   * right or falls under a canonical node with no non-canonical nodes
   * intervening.
   *
   * @param node Node of interest.
   * @return True if node is inherently non-canonical.
   */

  fun isInherentlyCanonicalTagOrIsUnderCanonicalTag (node: Node): Boolean
  {
    var n = node
    while (true)
    {
      if (isInherentlyCanonicalTag(n)) return true
      if (isInherentlyNonCanonicalTag(n)) return false
      n = n.parentNode
      if (n is Document) return false
    }
  }


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
   * Returns an indication of whether a given node is a heading tag.
   *
   * @param node Node of interest.
   * @return True if node is a heading tag.
   */

  override fun isHeadingTag (node: Node) = 'Y' == m_OsisTagDetails[Dom.getNodeName(node)]!!.title


  /****************************************************************************/
  /**
   * Returns an indication of whether a given node is definitely canonical.
   *
   * @param node Node of interest.
   * @return True if node is inherently non-canonical.
   */

  override fun isInherentlyCanonicalTag (node: Node) = 'Y' == m_OsisTagDetails[Dom.getNodeName(node)]!!.canonical


  /****************************************************************************/
  /**
   * Returns an indication of whether a given node is non-canonical in its own
   * right (not by reference to ancestors).
   *
   * @param node Node of interest.
   * @return True if node is inherently non-canonical.
   */

  override fun isInherentlyNonCanonicalTag (node: Node) = 'N' == m_OsisTagDetails[Dom.getNodeName(node)]!!.canonical


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
  * Returns an indication of whether this node is a span-type such as char.
  *
  * @param node Node to be examined.
  * @return True if this is a span type.
  */

  override fun isSpanType (node: Node) = 'Y' == m_OsisTagDetails[Dom.getNodeName(node)]!!.span


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
  * @param refKey Reference details for eid attribute.
  * @return Tag.
  */

  override fun makeVerseEidNode (doc: Document, refKey: RefKey): Node
  {
    val refAsString = Ref.rd(refKey).toStringOsis()
    return Dom.createNode(doc, "<verse eID='$refAsString'/>")
  }


  /****************************************************************************/
  /**
  * Makes a verse sid tag.
  *
  * @param doc Document within which the tag is created.
  * @param refKey Reference details for sid attribute.
  * @return Tag.
  */

  override fun makeVerseSidNode (doc: Document, refKey: RefKey): Node
  {
    val refAsString = Ref.rd(refKey).toStringOsis()
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

  data class OsisTagDescriptor (val canonical: Char, val title: Char, val span: Char)
  private val m_OsisTagDetails: MutableMap<String, OsisTagDescriptor> = mutableMapOf()

  init {

    //*** Replace any code below with code generated by osisReference.xlsm. ***

    m_OsisTagDetails["a"] = OsisTagDescriptor('N', 'N', 'N') // Similar to an HTML link. The "http://..." is recorded in the href attribute of this element. The
    m_OsisTagDetails["abbr"] = OsisTagDescriptor('N', 'N', 'N') // Abbreviations should be encoded using this element. The expansion attribute records the full
    m_OsisTagDetails["actor"] = OsisTagDescriptor('N', 'N', 'N') // Actor is used to encode the name of an actor in a castItem element, which itself occurs in a
    m_OsisTagDetails["caption"] = OsisTagDescriptor('N', 'N', 'N') // Caption is used in the figure element to record the caption for a map, image or similar items.
    m_OsisTagDetails["castGroup"] = OsisTagDescriptor('N', 'N', 'N') // The castGroup element does not allow text content.

    m_OsisTagDetails["castItem"] = OsisTagDescriptor('N', 'N', 'N') // The castItem is a container that groups together information for a particular member of a
    m_OsisTagDetails["castList"] = OsisTagDescriptor('N', 'N', 'N') // The castList element appears in the work element of an OSIS document to contain one or
    m_OsisTagDetails["catchWord"] = OsisTagDescriptor('N', 'N', 'N') // The catchWord element is used in note and p elements that may appear in note
    m_OsisTagDetails["chapter"] = OsisTagDescriptor('Y', 'N', 'N') // Chapter is used as syntactic sugar for the div element. It will most often be found in nonbiblical  I turn div type='chapter' into <chapter>, so there is no need to worry about div type='chapter'.
    m_OsisTagDetails["closer"] = OsisTagDescriptor('Y', 'N', 'N') // The closer element is used for the closing portion of a letter, usually containing a final

    m_OsisTagDetails["contributor"] = OsisTagDescriptor('N', 'N', 'N') // The contributor element appears only in a work element. It is used to list a person or
    m_OsisTagDetails["coverage"] = OsisTagDescriptor('N', 'N', 'N') // The coverage element appears only in a work element. It is used to specify what is
    m_OsisTagDetails["creator"] = OsisTagDescriptor('N', 'N', 'N') // The creator element appears only in a work element. The person or organization principally
    m_OsisTagDetails["date"] = OsisTagDescriptor('N', 'N', 'N') // The date element is used to record a date in an OSIS document. The type attribute is used to
    m_OsisTagDetails["description"] = OsisTagDescriptor('N', 'N', 'N') // The description element is only within a work element. It is used to provide a reader

    m_OsisTagDetails["div"] = OsisTagDescriptor('N', 'N', 'N') // The div element is the basic divider for all OSIS texts.  So far as I can see, all uses of div are non-canonical, with the exception of book and chapter, and these I change internally to be book or chapter anyway.
    m_OsisTagDetails["divineName"] = OsisTagDescriptor('Y', 'N', 'Y') // The divineName element is used to mark the name of the Deity only. Other names,
    m_OsisTagDetails["figure"] = OsisTagDescriptor('N', 'N', 'N') // The figure element is used to insert maps, images and other materials into an OSIS document.
    m_OsisTagDetails["foreign"] = OsisTagDescriptor('Y', 'N', 'Y') // The foreign element is used to mark "foreign" words or phrases in a text. That is words or
    m_OsisTagDetails["format"] = OsisTagDescriptor('N', 'N', 'N') // The format element appears only in a work element. It is recommended that the format of a

    m_OsisTagDetails["head"] = OsisTagDescriptor('N', 'Y', 'N') // The head element is used for book, chapter and other headings. While those are sometimes
    m_OsisTagDetails["header"] = OsisTagDescriptor('N', 'N', 'N') // The header element is a container that appears only in the osisCorpus or osisText elements.
    m_OsisTagDetails["identifier"] = OsisTagDescriptor('N', 'N', 'N') // The identifier element appears only in the work element. The value of the type attribute
    m_OsisTagDetails["hi"] = OsisTagDescriptor('?', 'N', 'Y') // The hi element provides a simple text highlighting mechanism.
    m_OsisTagDetails["index"] = OsisTagDescriptor('N', 'N', 'N') // The index element has no content and can appear anywhere in the body of an OSIS document.

    m_OsisTagDetails["inscription"] = OsisTagDescriptor('Y', 'N', 'Y') // The inscription element is used to mark text that reports a written inscription.
    m_OsisTagDetails["item"] = OsisTagDescriptor('Y', 'N', 'N') // The item element is used within a list element to hold the content of each item in the list. An
    m_OsisTagDetails["l"] = OsisTagDescriptor('Y', 'N', 'N') // The l element is used to mark separate lines in a lg (line group) element. This will be most
    m_OsisTagDetails["label"] = OsisTagDescriptor('N', 'N', 'N') // The label element is used in an item element to provide a label for the content of an item.
    m_OsisTagDetails["language"] = OsisTagDescriptor('N', 'N', 'N') // The language element appears only in a work element. There can be multiple language

    m_OsisTagDetails["lb"] = OsisTagDescriptor('N', 'N', 'N') // The lb element is used to indicate a typographical line break in a text. As a milestone type
    m_OsisTagDetails["lg"] = OsisTagDescriptor('Y', 'N', 'N') // The lg element is generally used to group other lg (line group) and l (line) elements together.
    m_OsisTagDetails["list"] = OsisTagDescriptor('Y', 'N', 'N') // The list element is used to represent lists and can include a head element for the list.
    m_OsisTagDetails["mentioned"] = OsisTagDescriptor('Y', 'N', 'Y') // The mentioned element is used when a name, for instance, is mentioned but not used as
    m_OsisTagDetails["milestone"] = OsisTagDescriptor('N', 'N', 'N') // The milestone element is used to mark a location in the text. It does not permit any

    m_OsisTagDetails["milestoneEnd"] = OsisTagDescriptor('N', 'N', 'N') // This element should not be used in current OSIS documents. It has been replaced by
    m_OsisTagDetails["milestoneStart"] = OsisTagDescriptor('N', 'N', 'N') // This element should not be used in current OSIS documents. It has been replaced by
    m_OsisTagDetails["name"] = OsisTagDescriptor('Y', 'N', 'Y') // The name element is used to mark place, personal and other names in an OSIS text. The
    m_OsisTagDetails["note"] = OsisTagDescriptor('N', 'N', 'N') // The note element is used for all notes on a text. Liberal use of the type attribute will enable
    m_OsisTagDetails["osis"] = OsisTagDescriptor('N', 'N', 'N') // The osis element is the root element of all OSIS texts.

    m_OsisTagDetails["osisCorpus"] = OsisTagDescriptor('N', 'N', 'N') // The osisCorpus element has no attributes and may have a header, followed by an
    m_OsisTagDetails["osisText"] = OsisTagDescriptor('N', 'N', 'N') // The osisText element is the main container for a text encoded in OSIS. It is composed of
    m_OsisTagDetails["p"] = OsisTagDescriptor('?', 'N', 'N') // The p element is used to mark paragraphs in a text. Since paragraphs are one of the most common
    m_OsisTagDetails["publisher"] = OsisTagDescriptor('N', 'N', 'N') // The publisher element occurs only in a work element.
    m_OsisTagDetails["q"] = OsisTagDescriptor('Y', 'N', 'N') // The q element is used to mark quotations in a text.

    m_OsisTagDetails["rdg"] = OsisTagDescriptor('N', 'N', 'N') // The rdg element is used to record a variant reading of a text. Most often seen where a note says:
    m_OsisTagDetails["reference"] = OsisTagDescriptor('N', 'N', 'N') // The reference element is used to mark references in one text to another text. The type
    m_OsisTagDetails["refSystem"] = OsisTagDescriptor('N', 'N', 'N') // The refSystem element occurs only in a work element. It has text only content and is
    m_OsisTagDetails["relation"] = OsisTagDescriptor('N', 'N', 'N') // The relation element occurs only in a work element. It has text only content and is
    m_OsisTagDetails["revisionDesc"] = OsisTagDescriptor('N', 'N', 'N') // The revisionDesc element is used only in a header element. It is used to record

    m_OsisTagDetails["rights"] = OsisTagDescriptor('N', 'N', 'N') // The rights element is used only in a work element. It is used to specify for a reader the
    m_OsisTagDetails["role"] = OsisTagDescriptor('N', 'N', 'N') // Role is used in a castItem element to identify the role of a particular actor.
    m_OsisTagDetails["roleDesc"] = OsisTagDescriptor('N', 'N', 'N') // The roleDesc element is used to provide a description of a role in a castItem element.
    m_OsisTagDetails["row"] = OsisTagDescriptor('?', 'N', 'N') // The row element occurs only in table elements and is used to contain cell elements.
    m_OsisTagDetails["salute"] = OsisTagDescriptor('Y', 'N', 'N') // The salute element is used to mark a salutation or opening comments. It is most generally

    m_OsisTagDetails["scope"] = OsisTagDescriptor('N', 'N', 'N') // The scope element is used only in a work element. The general area covered by a text is
    m_OsisTagDetails["seg"] = OsisTagDescriptor('Y', 'N', 'N') // The seg element should be used for very small divisions, such as within word elements. The  Not sure whether to make this canonical or not, but it looks as though we're not really going to come across it.
    m_OsisTagDetails["signed"] = OsisTagDescriptor('Y', 'N', 'N') // The signed element is used to mark the signer of a letter within a closer element.
    m_OsisTagDetails["source"] = OsisTagDescriptor('N', 'N', 'N') // The source element appears only in a work element. It is used to indicate the source for a
    m_OsisTagDetails["speaker"] = OsisTagDescriptor('Y', 'N', 'N') // The speaker element is used to mark the speaker in a text. It will be used when the speaker

    m_OsisTagDetails["speech"] = OsisTagDescriptor('Y', 'N', 'N') // The speech element is used to mark speeches in a text.
    m_OsisTagDetails["subject"] = OsisTagDescriptor('N', 'N', 'N') // The subject element occurs only in a work element. It consists only of text drawn from a
    m_OsisTagDetails["table"] = OsisTagDescriptor('?', 'N', 'N') // The table element contains an optional head element and one or more row elements.
    m_OsisTagDetails["teiHeader"] = OsisTagDescriptor('N', 'N', 'N') // The teiHeader element occurs only in a header element. It is used to contain a TEI
    m_OsisTagDetails["title"] = OsisTagDescriptor('N', 'N', 'N') // The title element is used to record a title both in a work element and elsewhere in an OSIS text.

    m_OsisTagDetails["titlePage"] = OsisTagDescriptor('N', 'N', 'N') // The titlePage element is used to specify a particular title page for an OSIS document.
    m_OsisTagDetails["transChange"] = OsisTagDescriptor('N', 'N', 'N') // The transChange element is used to mark text that is not present in the original  Moot point whether to regard this as canonical or not.
    m_OsisTagDetails["type"] = OsisTagDescriptor('N', 'N', 'N') // The type element occurs only in a work element. It is used to indicate to the reader the type of
    m_OsisTagDetails["verse"] = OsisTagDescriptor('N', 'N', 'N') // The verse element should almost always be used in its milestoneable form. While some older  I've marked this as non-canonical purely because it does not normally contain anything.
    m_OsisTagDetails["w"] = OsisTagDescriptor('Y', 'N', 'N') // The w element is used to encode particular words in a text.  eg Strongs.

    m_OsisTagDetails["word"] = OsisTagDescriptor('Y', 'N', 'N') //   I'm not sure whether this exists.  It appears in examples in the OSIS ref man, but is not described there.
    m_OsisTagDetails["work"] = OsisTagDescriptor('N', 'N', 'N') // The work element occurs only in a header element. It provides all the basic identification and
    m_OsisTagDetails["book"] = OsisTagDescriptor('N', 'N', 'N') // I turn div type='book' into <book> to make processing easier.  There is no need for it to be marked as canonical, because the chapters it contains wil be marked as such.
    m_OsisTagDetails["#comment"] = OsisTagDescriptor('N', 'N', 'N') //
    m_OsisTagDetails["#text"] = OsisTagDescriptor('?', '?', 'N') //

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
   * Returns an indication of whether a given node is a heading tag.
   *
   * @param node Node of interest.
   * @return True if node is a heading tag.
   */

  override fun isHeadingTag (node: Node) = m_TagsHeadings.contains(getExtendedNodeName(node))


  /****************************************************************************/
  /**
   * Returns an indication of whether a given node is definitely canonical.
   * Really intended for Strong's tags because we can be confident that these
   * contain canonical text, and it's much quicker if we don't have to examine
   * the content of the tag to ascertain that.
   *
   * @param node Node of interest.
   * @return True if node is inherently non-canonical.
   */

  override fun isInherentlyCanonicalTag (node: Node) = "char:w" == getExtendedNodeName(node)


  /****************************************************************************/
  /**
   * Returns an indication of whether a given node is non-canonical in its own right (not by reference to ancestors).
   *
   * @param node Node of interest.
   * @return True if node is inherently non-canonical.
   */

  override fun isInherentlyNonCanonicalTag (node: Node): Boolean
  {
    var extendedNodeName = getExtendedNodeName(node)
    if (isNumberedLevelTag(node)) extendedNodeName += "1" // Force numbered level tags to have a digit on the end.
    return m_TagsNonCanonical.contains(extendedNodeName.replace("\\d+$".toRegex(), "#"))
  }


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
  * @param refKey Reference details for eid attribute.
  * @return Tag.
  */

  override fun makeVerseEidNode (doc: Document, refKey: RefKey): Node
  {
    val refAsString = Ref.rd(refKey).toStringUsx()
    return Dom.createNode(doc, "<verse eid='$refAsString'/>")
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
  * Makes a verse sid tag.
  *
  * @param doc Document within which the tag is created.
  * @param refKey Reference details for sid attribute.
  * @return Tag.
  */

  override fun makeVerseSidNode (doc: Document, refKey: RefKey): Node
  {
    val refAsString = Ref.rd(refKey).toStringUsx()
    return Dom.createNode(doc, "<verse sid='$refAsString'/>")
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


  /****************************************************************************/
  /*                                                                          */
  /*           Insert here the code generated by usxReference.xlsm.           */
  /*                                                                          */
  /****************************************************************************/

  private val m_TagsHeadings = setOf(
    "para:cd",
    "para:cl",
    "para:d",
    "para:mr",
    "para:ms#",
    "para:mt#",
    "para:mte",
    "para:r",
    "para:s#",
    "para:sd#",
    "para:sp",
    "para:sr",
    )


  private val m_TagsNonCanonical = setOf(
    "#comment",
    "_X_bracketStart:ili",
    "_X_bracketEnd:ili",
    "_X_bracketStart:io",
    "_X_bracketEnd:io",
    "_X_comment",
    "_X_introductionBlock:book",
    "_X_introductionBlock:chapter",
    "_X_reversificationCalloutData",
    "_X_reversificationMoveOriginalText",
    "_X_reversificationSourceVerse",
    "_X_subverseSeparator",
    "_X_verseBoundaryWithinElidedTable",
    "char:add",
    "char:bk",
    "char:fdc",
    "char:fk",
    "char:fl",
    "char:fm",
    "char:fp",
    "char:fq",
    "char:fqa",
    "char:fr",
    "char:ft",
    "char:fv",
    "char:fw",
    "char:ior",
    "char:iqt",
    "char:jmp",
    "char:k",
    "char:lik",
    "char:litl",
    "char:liv#",
    "char:ord",
    "char:pn",
    "char:png",
    "char:pro",
    "char:qac",
    "char:qs",
    "char:rb",
    "char:rq",
    "char:va",
    "char:vp",
    "char:wa",
    "char:wg",
    "char:wh",
    "char:xdc",
    "char:xk",
    "char:xnt",
    "char:xo",
    "char:xop",
    "char:xot",
    "char:xq",
    "char:xq",
    "char:xt",
    "char:xta",
    "figure",
    "note:ef",
    "note:ex",
    "note:f",
    "note:fe",
    "note:x",
    "optbreak",
    "para:b",
    "para:cd",
    "para:cl",
    "para:cp",
    "para:h",
    "para:ib",
    "para:ide",
    "para:ie",
    "para:iex",
    "para:ili#",
    "para:im",
    "para:imi",
    "para:imq",
    "para:imt#",
    "para:imt#",
    "para:io#",
    "para:iot",
    "para:ip",
    "para:ipi",
    "para:ipq",
    "para:ipr",
    "para:iq#",
    "para:is#",
    "para:lit",
    "para:litl",
    "para:mr",
    "para:ms#",
    "para:mt#",
    "para:mte",
    "para:qa",
    "para:qd",
    "para:r",
    "para:rem",
    "para:s#",
    "para:sd#",
    "para:sp",
    "para:sr",
    "para:toc#",
    "para:toca#",
    "periph",
    "ref",
    "sidebar",
    "verse",
    "verse:v",
  )


  private val m_TagsWithNumberedLevels = setOf(
    "cell:tc",
    "cell:tcc",
    "cell:tcr",
    "cell:th",
    "cell:thr",
    "char:liv",
    "para:ili",
    "para:imt",
    "para:imt",
    "para:io",
    "para:iq",
    "para:is",
    "para:li",
    "para:lim", // Not sure whether this is a typo, or whether it was in an earlier version of USX.  Doesn't appear in the 4.7.0 documentation.  I presently treat it like li.
    "para:ms",
    "para:mt",
    "para:ph",
    "para:pi",
    "para:q",
    "para:qm",
    "para:s",
    "para:sd",
    "para:toc",
    "para:toca",
  )



  /****************************************************************************/
  /*                                                                          */
  /*               End of code generated by usxReference.xlsm.                */
  /*                                                                          */
  /****************************************************************************/
}