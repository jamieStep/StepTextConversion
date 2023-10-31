/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleAnatomy
import org.stepbible.textconverter.support.bibledetails.BibleStructure
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.*
import org.stepbible.textconverter.support.ref.RefFormatHandlerReaderVernacular.readEmbedded
import org.w3c.dom.Document
import org.w3c.dom.Node


/******************************************************************************/
/**
 * Parses, checks and possibly remaps cross-references.
 *
 * ## Overview
 *
 * I suppose eventually I may come across an aspect of USX which doesn't make
 * for complicated processing.  Unfortunately, this isn't one of them.
 *
 * - There are several tags involved in cross-reference markup,
 *
 * - There are different ways of using these tags.
 *
 * - There are certain rules which may or may not have been obeyed.
 *
 * - References are usually given in two forms -- USX and vernacular.
 *   In theory these should tie up, but they don't always.
 *
 * - References may or may not be syntactically correct.
 *
 * - References may or may not be semantically correct.
 *
 * - References may point to verses which don't actually exist in this text,
 *   something which may or may not have been anticipated in the markup.
 *
 * - References may or may not point to subverses, and the subverses may not
 *   have been identified in the original markup and / or may have had to be
 *   stripped out because the official Crosswire tools do not support them.
 *
 * - If reversification is being applied and is actually restructuring the
 *   text (fortunately something which it seems may no longer be happening),
 *   references which point to verses affected by reversification may have to
 *   be amended.
 *
 * - Etc.
 *
 *
 *
 *
 *
 * ## Slightly less of an overview and more of a detailed consideration.
 *
 * There are several tags involved in cross-reference processing.  The two of
 * most immediate interest are char:xt and ref, which are the ones which
 * actually carry details of the references to which we wish to link.  In
 * addition, 'note' may enclose these.  And 'note' itself may have a number
 * of other associated tags, which are of rather less interest.  I go into more
 * detail shortly.
 *
 * So we have to cater for either something like:
 *
 *     <char style='xt' link-href='GEN 2:1'>   1   </char>
 *     <ref loc='GEN 2:1'>   Gen 2:1   </ref>
 *
 * where I have introduced extra spacing to improve readability -- spacing
 * which is not present in the real markup.  In each of these examples,
 * the link-href or loc attribute is supposed to contain the USX form of the
 * target, and the content of the node forms, in STEP, the clickable text
 * which takes you to the reference (and is presumably the same target in
 * vernacular form, although I don't think there's anything which says it
 * *has* to be).
 *
 * In fact, the char:xt doesn't usually have the link-href attribute; more
 * often, it simply serves as a container for ref tags.
 *
 *
 *
 *
 *
 * ## The tags
 *
 * Cross-references and footnotes have quite a number of associated tags, some
 * of which contain reference information which matters, some of which contain
 * reference information which is not going to be relevant to STEP, and some
 * of which is not reference-related.  For the sake of forcing myself to
 * consider all of the options, if for no other reason, it is probably worth
 * working through things here, even though not all of the material is
 * directly relevant.
 *
 *
 * The examples below are taken from the USX v3.0 documentation.
 *
 * *ref*
 *     <ref loc="MAT 3:1-4">Mt 3.1-4</ref>,<ref loc="MAT 3:7-13">7-13</ref>
 *     <ref loc="MAT 3:4-4:5">Mt 3.4—4.5</ref>
 *     <ref loc="LUK 3-5">Lk 3—5</ref>
 *     <ref loc="MAT-LUK">Mt—Lk</ref>
 *     <ref loc="MAT 3:4-5:6">Matthew 3.4—5.6</ref>; <ref loc="LUK 7">Luke 7</ref>
 *
 * It appears to be permissible to have this tag in quite a number of different
 * contexts.  The loc parameter must always be in USX format, and either a
 * single reference or a range (not a collection).  Note that the range can
 * be very large: one of the examples above gives three entire books as the
 * range.  In each case, the content of the tag gives the range in vernacular
 * form.
 *
 * Other tags which may appear within *note* include :-
 *
 *  - char:xo, which gives the identifier of the verse in which the note
 *    appears, and is of no interest for our purposes.
 *
 *  - char:xop, which (I think) gives a preferred displayable representation
 *    of the value associated with xo, and again is of no interest.
 *
 * - char:xta, which is used to include non-cross reference text within a
 *   run of cross-references, so that you might have 'SEE Jn 3:16 AND
 *   Mat 1:1', where the SEE and AND appear within xta, and the references
 *   appear within xt.
 *
 * - char:xk and char:xq, representing keywords and scripture quotations
 *   respectively.  Unfortunately the documentation gives no example of
 *   their use.  I assume here they are simply carried through as-is
 *   (except possibly for reformatting).
 *
 * - char:xot, char:xnt and char:xdc I think are equivalent to char:xt.
 *   They are used where a particular tag is intended to take effect
 *   only if the OT, NT or DC is present (respectively).  I convert all
 *   of them to char:xt, and then subject them to the usual checks on
 *   whether the target exists.
 *
 * @author ARA "Jamie" Jamieson
 */

 object CrossReferenceProcessor
 {
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                  Public                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

   /****************************************************************************/
  public fun canonicaliseAndPatchUp (document: Document)
  {
    /**************************************************************************/
    /* Add an indication of which verse 'owns' each cross-reference. */

    addBelongsTo(document)



    /**************************************************************************/
    /* Turn char:xot, char:xnt and char:dc all to char:xt to simplify later
       processing.  Having done this, we now have all of the char:xt nodes
       we're ever going to have, so to save looking for them repeatedly in
       later processing, we can simply build up a list here. */

    canonicaliseCharXtsStandardiseStyles(document)
    val charXts = Dom.findNodesByAttributeValue(document, "char",  "style","xt")


    /**************************************************************************/
    /* A given char:xt should contain _either_ a link-href attribute or one or
       more ref nodes (or, in fact, possibly neither).  To avoid later
       confusion, I drop href-link in favour of ref where both exist. */

    canonicaliseCharXtsDealWithHrefLinkNodesWhichAlsoContainRefTags(charXts)



    /**************************************************************************/
    /* USX does allow for refs to be based purely upon vernacular text,
       more's the pity.  Where this is the case, try to work out the equivalent
       USX. */

    canonicaliseCharXtsReliantUponVernacularText(charXts)



    /**************************************************************************/
    /* Check that any href-links still present are valid. */

    canonicaliseCharXtsValidateHrefLinks(charXts)



    /**************************************************************************/
    /* Attempt to standardise the node structure within char:xt by introducing
       ref tags where necessary / possible. */

    canonicaliseCharXts(charXts)



    /**************************************************************************/
    /* We may possibly have some char:xt's which don't actually contain any ref
       tags.  Flag those. */

    canonicaliseCharXtsHandleNodesLackingRefs(charXts)



    /**************************************************************************/
    /* We have now done all we can with char:xt tags themselves.  Time to look
       at any contained ref tags.

       First, ref tags are _supposed_ to have loc tags, because they tell us
       where to point to.  If there are any which _don't_ have loc tags, turn
       them into plain text nodes.

       Note that unlike char:xt, we can't so readily build up a list of refs
       which will serve as input for everything, because at least some of the
       steps below remove refs or turn them into something else. */

    canonicaliseCharXtsConvertLoclessNodesToTextOnly(document)



    /**************************************************************************/
    /* USX caters for special references to appear within major headings.
       These are probably best converted to plan text, because they normally
       point to very large chunks of text -- eg whole chapters -- and those
       don't really work in STEP as cross-references, because cross-references
       are shown in pop-up windows, which don't readily accommodate vast
       amounts of text. */

    canonicaliseRefsDropRefsFromMajorHeadings(document)



    /**************************************************************************/
    /* Convert any ref containing an invalid loc attribute to _X_contentOnly. */

    canonicaliseRefsValidateLocAttributes(document)



    /**************************************************************************/
    /* In theory, ref 'loc' attributes shouldn't contain collections, but
       sometimes they don't.  In these cases, attempt to split them out into
       separate ref tags. */

    canonicaliseRefsSplitCollections(document)



    /**************************************************************************/
    /* Where refs point to non-existent locations, convert them to
       _X_contentOnly. */

    canonicaliseRefsCheckTargetsExist(document)



    /**************************************************************************/
    /* References to single-chapter books have to include chapter=1. */

    canonicaliseRefsConvertSingleChapterReferences(document)



    /**************************************************************************/
    /* Really of interest only on Biblica texts -- they have a distressing
       habit of enclosing _all_ footnotes in note:f, whereas really cross-
       references should be in note:x. */

    canonicaliseNotesCorrectNoteStyles(document)



    /**************************************************************************/
    canonicaliseRefsCompareLocAndContent(document)



    /**************************************************************************/
    reportWarnings()
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                  char:xt                               **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* It's convenient to convert xot, xnt and xdc all to xt so they can all be
     handled the same way.

     Also USX permits the actual cross-reference details to be recorded in a
     number of different ways:

     - They may be recorded on one or more ref tags within the char:xt.  This
       appears to be the commonest approach, and if I find it to be the case
       here, that's fine.

     - They may be recorded in an href-link attribute within the char:xt tag.
       In this case, I generate ref tags within the char:xt as necessary,
       thus reducing it to 'standard' form.

     to be recorded on one or more ref tags within the char:xt, or as an
     href-link attribute on the char:xt itself, or merely to be recorded within
     the char:xt in vernacular form.  This looks for char:xt's which lack the
     ref tag but which contain href-link, and creates the ref tag instead.
     This means that after this call, we can rely upon tags having a ref tag
     wherever they had one already, or where href-link permitted us to create
     one. */

  private fun canonicaliseCharXts (charXts: List<Node>)
  {
    /**************************************************************************/
    fun processXt (node: Node)
    {
      if (null != Dom.findNodeByName(node, "ref", false)) return // Already has a ref child -- assume that's all we need.

      val href = node["href-link"]
      if (null != href) // Use the href-link to create a ref node and place it under char:xt, inheriting the children from the latter.
      {
        val originalChildren = Dom.getChildren(node)
        Dom.deleteNodes(originalChildren)
        val newNode = Dom.createNode(node.ownerDocument, "<ref loc='$href' _X_action='Added refTag in order to canonicalise char:xt'/>")
        Dom.addChildren(newNode, originalChildren)
        node.appendChild(newNode)
        node -="href-link"
      }
    }



    /**************************************************************************/
    charXts.forEach { processXt(it) }
  }


  /****************************************************************************/
  /* We have seen some texts with ref tags but lacking loc parameters.  This
     seems rather bizarre, given that the whole raison d'etre of ref is to point
     to verses, and the loc tells you where to point.  However, it is deemed
     useful to retain the details as read-only information. */

  private fun canonicaliseCharXtsConvertLoclessNodesToTextOnly (document: Document)
  {
    Dom.findNodesByName(document, "ref")
      .filter { "loc" !in it }
      .forEach {
        MiscellaneousUtils.recordTagChange(it, "_X_contentOnly", null, "Was ref with no loc")
        recordWarning(it, "Was ref with no loc")
      }
  }


  /****************************************************************************/
  /* A given char:xt should contain _either_ a href-link or embedded refs, but
     not both.  (Possibly it may contain neither.)  If it does contain both,
     I throw away the link-href and just retain the refs -- I think trying to
     check the two are compatible is just a step too far. */

  private fun canonicaliseCharXtsDealWithHrefLinkNodesWhichAlsoContainRefTags (charXts: List<Node>)
  {
    charXts
      .filter { "href-link" in it && null != Dom.findNodeByName(it, "ref", false) }
      .forEach {
        recordWarning(it, "Deleted href-link attribute (${it["href-link"]!!}) in favour of existing ref tags")
        it -= "href-link"
      }
  }


  /****************************************************************************/
  /* In order to function as a cross-reference, any char:xt must contain one or
     more ref nodes by now. */

  private fun canonicaliseCharXtsHandleNodesLackingRefs (charXts: List<Node>)
  {
    charXts
      .filter { null == Dom.findNodeByName(it, "ref", false) }
      .forEach { recordWarning(it, "char:xt with no contained refNodes") }
  }


  /****************************************************************************/
  /* This deals with char:xt nodes which have no href-link and no embedded ref
     tag, leaving us dependent upon attempting to parse the vernacular
     content.  Note that the effect of this may be to create refs which
     themselves are collections (something which neither USX nor OSIS support),
     but we straighten that out later. */

  private fun canonicaliseCharXtsReliantUponVernacularText (charXts: List<Node>)
  {
    /**************************************************************************/
    fun process (node: Node)
    {
      val s = tryCreatingXtFromVernacularContent(node)
      if (null != s) recordWarning(node, "$s (was char:x?t)")
    }



    /**************************************************************************/
    charXts
      .filter { "href-link" !in it && null == Dom.findNodeByName(it, "ref", false) }
      .forEach { process(it) }
  }


  /****************************************************************************/
  /* USX makes provision for cross-references to be labelled as for use only if
     the text contains the OT, only if it contains the NT, or only if it
     contains the DC.  This does raise the question of what this means.  There
     seem to be several options:

     - If the relevant portion of the Bible is missing, we could simply
       expunge the cross-reference altogether.

     - We could retain something come what may, but convert it to plain text
       if the relevant portion is missing.

     - We could largely ignore the issue of whether the target portion is
       present or not, and leave it to later processing to see whether it
       can make the thing work.  (This later always checks to see whether the
       targets for the cross-references exist.

     This final option seems to be the most useful, so that's what I've gone
     with.  I do add a temporary attribute so that later processing won't
     issue warnings if the target turns out not to exist.
   */

  private fun canonicaliseCharXtsStandardiseStyles (document: Document)
  {
    fun changeToXt (node: Node, was: String) { MiscellaneousUtils.recordTagChange(node, "char", "xt", "Was $was") }

    Dom.findNodesByAttributeValue(document, "char", "style", "xot").forEach { changeToXt(it, "xot") }
    Dom.findNodesByAttributeValue(document, "char", "style", "xnt").forEach { changeToXt(it, "xnt") }
    Dom.findNodesByAttributeValue(document, "char", "style", "xdc").forEach { changeToXt(it, "xdc") }
  }


  /****************************************************************************/
  /* Checks that any href-links are valid references, and deletes ones which
     aren't. */

  private fun canonicaliseCharXtsValidateHrefLinks (charXts: List<Node>)
  {
    charXts
      .filter { "href-link" in it }
      .forEach {
        if (!validateUsx(it["href-link"]!!))
          recordWarning(it, "char:xt has invalid href-link (${it["href-link"]!!})")
          it -= "href-link"
      }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                   note                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* This code shouldn't exist.  It's here only because Biblica don't tend to
     use note:x even for cross-references.  Sadly to make sense of all this
     I'm going to have to go into some detail.

     note:f (and related tags note:ef and note:fe, neither of which I have ever
     seen being used) is employed to mark a plain vanilla footnote -- ie one
     whose main purpose is to hold explanatory text (text which in some
     instances can be quite large).

     note:x (and note:ex) is used basically to hold cross-references.

     The purpose of this method is to convert note:f to note:x where this seems
     appropriate.  At present this _always_ runs, which is perhaps not ideal,
     because I think it addresses an issue which is limited to Biblica texts.
     However, aside from using a little extra processing, I don't think it
     does any _harm_ to apply it universally.

     To my mind, footnotes essentially come in three flavours.  There is the
     extensive explanatory footnote; the 'pure' cross-reference (which may
     actually contain a few additional noise words, like 'See' -- 'See Jn 3:1');
     and a hybrid which contains a 'fair amount' of explanatory text, and some
     references too.

     The first of these genuinely should be contained within note:f.

     The second should genuinely be contained with note:x.

     The third requires a somewhat arbitrary call, since it could be contained
     in either.  My processing here looks to see how much plain text accompanies
     the cross-reference(s).  If there's a lot, it prefers note:f, otherwise
     note:x.

     Actually that's a slight simplification.  I have a table below which lists
     tag flavours which must be present (ref being the obvious one), and
     flavours which must _not_ be present (at the time of writing, char:fqa
     -- alternative translation -- because I kinda feel that this deserves to be
     treated as a fully-fledged footnote come what may).

     The choice between note:f and note:x matters, because the two give rise to
     different behaviours in STEP.  References within note:x are displayed
     rather like tool-tips when you hover the mouse over them, and then clicking
     on the verse reference within the tool-tip brings up a pop-up window
     containing the target text.

     Earlier processing in this class will have introduced a useful measure of
     uniformity, in that even though USX lets cross-references be recorded in a
     number of different ways, they will all have been reduced to <ref> tags by
     now.

     All I need do, therefore, is look for note:f tags which contain <ref> tags.
     The call I make then is based upon the amount of explanatory text within
     the enclosing note tag.
   */

  private fun canonicaliseNotesCorrectNoteStyles (document: Document)
  {
    /**************************************************************************/
    val C_NoOfCanonicalWordsWhichMeansThisIsANoteF = 6



    /**************************************************************************/
    /* This lists tags to look for.  The later tests run through these in order
       and give up if any of them returns false. */

    val checks = mapOf("ref" to true,        // Of interest if a ref node is present.
                       "char:fqa" to false)  // Not of interest if char:fqa (translation alternative) is present -- I assume this really _does_ need to come out as note:f.



    /**************************************************************************/
    /* Looks for things under 'node' of a given kind.  ifFound is true, then
       returns true if found and false if not found.  ifFound is false, the
       return value is inverted. */

    fun check (node: Node, check: String, ifFound: Boolean): Boolean
    {
      val res = if (":" in check)
      {
        val bits = check.split(":")
        null != Dom.findNodeByAttributeValue(node, bits[0], "style", bits[1])
      }
      else
        null != Dom.findNodeByName(node, check, false)

      return if (ifFound) res else !res
    }



    /**************************************************************************/
    Dom.findNodesByAttributeValue(document, "note", "style", "f").forEach { noteNode -> // We look only at note:f.
      if (false !in checks.map { check(noteNode, it.key, it.value) }) // Nothing to do unless the note tag contains the right flavours of node, and does not contain the wrong ones.
      {
        val charNodes = Dom.findNodesByName(noteNode, "char", false)
        val canonicalText = charNodes.joinToString(" ") { Dom.getCanonicalTextContentToAnyDepth(it) }
        if (StepStringUtils.wordCount(canonicalText) < C_NoOfCanonicalWordsWhichMeansThisIsANoteF)
          MiscellaneousUtils.recordTagChange(noteNode, "note", "x", "Style was 'f' but contains cross-reference details.")
      }
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                    ref                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Certain headings -- particularly those at the start of a book -- tend to
     contain ref tags which point to large chunks of text (multiple chapters,
     for instance).  We probably don't want these actually to be clickable,
     both because there is no point, when sitting at the front of the book
     then to look at 10 chapters of that book in a pop-up window, and also
     because trying to show a lot of text causes STEP to crash (or certainly
     did so at one time).  I therefore change these refs to _X_contentOnly. */

  private fun canonicaliseRefsDropRefsFromMajorHeadings (document: Document)
  {
    fun modifyRef (ref: Node)
    {
      MiscellaneousUtils.recordTagChange(ref, "_X_contentOnly", Dom.getAttribute(ref, "style"), "Probably not intended to be clickable")
    }

    fun modifyRefs (owner: Node)
    {
      Dom.findNodesByName(owner, "ref", false).forEach { modifyRef(it) }
    }

    Dom.findNodesByAttributeValue(document, "para", "style", "mr").forEach { modifyRefs(it) }
    Dom.findNodesByAttributeValue(document, "para", "style", "sr").forEach { modifyRefs(it) }
    Dom.findNodesByAttributeValue(document, "char", "style", "ior").forEach { modifyRefs(it) }
  }


  /****************************************************************************/
  /* In theory, the loc attributes on ref tags should not contain collections,
     but I've seen texts where they do.  I attempt here to split such refs
     out into multiple adjacent ones.  However, to do this, I also have to be
     able to parse the vernacular text; where I cannot do that, I report an
     issue, but leave the tag as-is in the hope that nothing downstream will
     break too egregiously

     I think I'm safe in assuming here that we'll have at most one collection
     in the vernacular text, because earlier processing will have sorted
     things out to ensure this is the case.  Thus it is 'simply' a case of
     marrying the individual elements of the USX text with those of the
     vernacular. */

  private fun canonicaliseRefsSplitCollections (document: Document)
  {
    /**************************************************************************/
    fun process (node: Node)
    {
      /************************************************************************/
      if (!m_CanReadAndWriteVernacular)
      {
        recordWarning(node, "loc attribute (${node["loc"]!!}) represents a reference collection, which is illegal")
        return
      }



      /************************************************************************/
      val usxRefCollection = RefCollection.rdUsx(node["loc"]!!) // Parse the loc attribute as a USX collection.
      val vernacularElts = readEmbedded(node.textContent.trim(), context=Ref.rdUsx(node["_X_belongsTo"]!!)) // Can't do the same with the content, because it may contain noise words like 'See'.
      var eltCollectionIx = 0 // I assume that the vernacularElts collection will contain just one collection, but it may be preceded by noise.

      var prefix: String? = null // Any noise before the first (sole) vernacular collection.
      var suffix: String? = null // Any noise after the last vernacular collection.
      if (vernacularElts[0] is RefFormatHandlerReaderVernacular.EmbeddedReferenceElementText)
        prefix = vernacularElts[eltCollectionIx++].text
      if (vernacularElts.last() is RefFormatHandlerReaderVernacular.EmbeddedReferenceElementText)
        suffix = vernacularElts.last().text

      val vernacularRefCollection = (vernacularElts[eltCollectionIx] as RefFormatHandlerReaderVernacular.EmbeddedReferenceElementRefCollection).rc
      if (usxRefCollection != vernacularRefCollection)
      {
        recordWarning(node, "USX reference and vernacular reference do not match")
        return
      }



      /************************************************************************/
      val usxElements = usxRefCollection.getElements()               // The individual refs or refRanges which make up the USX collection.
      val vernacularElements = vernacularRefCollection.getElements() // The individual refs or refRanges which make up the vernacular collection.

      val newNodes: MutableList<Node> = mutableListOf()              // The nodes which are going to replace the existing ref.
      if (null != prefix)                                            // Add any noise which needs to go at the front.
        newNodes.add(Dom.createTextNode(node.ownerDocument, prefix))

      var prevRef: Ref? = null
      var sep: String?

      for (ix in usxRefCollection.getElements().indices)
      {
        val thisRef = usxElements[ix].getLowAsRef()

        if (null != prevRef) // If we've had a previous Ref or RefRange, we need to follow it with a suitable separator.
        {
          sep = if (thisRef.isSameChapter(prevRef)) RefFormatHandlerWriterVernacular.getCollectionSeparatorSameChapter() else RefFormatHandlerWriterVernacular.getCollectionSeparatorDifferentChapters()
          newNodes.add(Dom.createTextNode(node.ownerDocument, "$sep "))
        }

        val newNode = Dom.createNode(node.ownerDocument, "<ref loc='${usxElements[ix]}' _X_generatedReason='Split from reference collection (${node["loc"]!!}) in original ref")
        newNode.appendChild(Dom.createTextNode(node.ownerDocument, vernacularElements[ix].toString("e")))
        newNodes.add(newNode)

        prevRef = thisRef
      }



      /************************************************************************/
      if (null != suffix) // Add any trailing noise words.
        newNodes.add(Dom.createTextNode(node.ownerDocument, suffix))



      /************************************************************************/
      Dom.insertNodesAfter(node, newNodes)
      Dom.deleteNode(node)
    }



    /**************************************************************************/
    Dom.findNodesByName(document, "ref")
      .filter { 1 != RefCollection.rdUsx(it["loc"]!!).getElementCount() }
      .forEach { process(it) }
  }


  /****************************************************************************/
  /* Checks for any loc parameters which have invalid loc attributes.  Reports
     them and changes the owning ref into an _X_contentOnly. */

  private fun canonicaliseRefsValidateLocAttributes (document: Document)
  {
    Dom.findNodesByName(document, "ref").forEach {
      if (!validateUsx(it["loc"]!!))
        recordWarning(it, "ref has invalid loc (${it["loc"]!!})")
        MiscellaneousUtils.recordTagChange(it, "_X_contentOnly", null, "Invalid loc")
    }
  }





 /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Reporting                               **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun recordWarning (node: Node, attributeValue: String)
  {
    node["_X_warning"] = attributeValue
    val refKey = Ref.rdUsx(node["_X_belongsTo"]!!).toRefKey()
    if (null == m_Warnings[refKey]) m_Warnings[refKey] = attributeValue
  }


  /****************************************************************************/
  private fun reportWarnings ()
  {
    m_Warnings.forEach { (refKey, text) -> Logger.warning(refKey, "Cross-reference: $text.")}
  }


  /****************************************************************************/
  private val m_Warnings: MutableMap<RefKey, String> = mutableMapOf()





  /****************************************************************************/
  /* Checks ref targets exist. */

  private fun canonicaliseRefsCheckTargetsExist (document: Document)
  {
    /**************************************************************************/
    fun processRef (node: Node)
    {
      val rc = RefCollection.rdUsx(node["loc"]!!)
      val problems = rc.getAllAsRefKeys().filter { !BibleStructure.UsxUnderConstructionInstance().bookExists(it) } .map { Ref.getB(it)}
      val report = when (node["_X_tagOrStyleChangedReason"]?.replace("Was ", ""))
      {
        "xot" -> !problems.all { BibleAnatomy.isOt(it) }
        "xnt" -> !problems.all { BibleAnatomy.isNt(it) }
        "xdc" -> !problems.all { BibleAnatomy.isDc(it) }
        else -> true
      }

      if (report)
        recordWarning(node, "Target does not exist: " + node["loc"]!!)
      MiscellaneousUtils.recordTagChange(node, "_X_contentOnly", null, "Target does not exist")
    }

    Dom.findNodesByName(document, "ref").forEach { processRef(it) }
  }


  /****************************************************************************/
  /* Adds chapter 1 to single chapter references which lack the chapter. */

  private fun canonicaliseRefsConvertSingleChapterReferences (document: Document)
  {
    fun processRef (node: Node)
    {
      var loc = node["loc"]!!
      if (":" in loc) return // Already have a chapter.
      if (!BibleAnatomy.isSingleChapterBook(loc)) return // Nothing to do.

      val bits = loc.split("\\s+".toRegex())
      loc = bits[0] + " 1:" + bits[1]
      node["loc"] = loc
      node["_X_attributeValueChangedReason"] = "Added v1 to single-chapter-book reference"
    }

    Dom.findNodesByName(document, "ref").forEach { processRef(it) }
  }


  /****************************************************************************/
  /* All ref tags should have loc attributes and content.  STEP uses the latter
     to provide the clickable text used to access cross-references.  I kind of
     _assume_, therefore, that the content and loc should both refer to the
     same reference, with the loc being the USX version and the content being
     the vernacular version.  There is actually -- so far as I know -- no
     _requirement_ for this to be the case, so I merely issue a warning here
     if the two do not tie up. */

  private fun canonicaliseRefsCompareLocAndContent (document: Document)
  {
    /**************************************************************************/
    if (!m_CanReadAndWriteVernacular) return



    /**************************************************************************/
    fun validate (node: Node)
    {
      /************************************************************************/
      val usxCollection = RefCollection.rdUsx(node["loc"]!!).getAllAsRefKeys()
      val vernacularCollection: List<RefKey>?



      /************************************************************************/
      try
      {
        /**********************************************************************/
        val x = readEmbedded(node.textContent.trim(), context=Ref.rdUsx(node["_X_belongsTo"]!!))
        val embeddedReferences = x.filterIsInstance<RefFormatHandlerReaderVernacular.EmbeddedReferenceElementRefCollection>()
        if (1 != embeddedReferences.count())
        {
          recordWarning(node, "Content is not a valid vernacular representation of a reference")
          return
        }



        /**********************************************************************/
        vernacularCollection = embeddedReferences[0].rc.getAllAsRefKeys()
      }
      catch (_: Exception)
      {
        recordWarning(node, "Content is not a valid vernacular representation of a reference")
        return
      }



      /************************************************************************/
      if (usxCollection != vernacularCollection)
        recordWarning(node, "USX reference and vernacular reference do not match")
    } // fun



    /**************************************************************************/
    Dom.findNodesByName(document, "ref").forEach { validate(it) }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                          Update cross-references                       **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* This section contains code used only by the original implementation of
     reversification.  That physically renumbered and or moved verses to new
     locations, and therefore references which pointed to the old location
     needed to be updated to point to the new one.  At the time of writing we
     are intending to implement reversification in a different manner, which
     means that the code below is no longer required.  I am retaining it here
     temporarily in case it is needed again. */

  fun updateCrossReferences (document: Document, mappings: Map<RefKey, RefKey>)
  {
    // Dummy version.
  }


//  /****************************************************************************/
//  /**
//  * Runs over the enhanced USX folder and updates any cross-references in the
//  * wake of reversification changes.
//  *
//  * @param mappings Maps old refKey to new one.
// */
//
//  fun updateCrossReferences (mappings: Map<RefKey, RefKey>)
//  {
//    if (mappings.isEmpty()) return
//    fun process (@Suppress("UNUSED_PARAMETER") bookName: String, @Suppress("UNUSED_PARAMETER") filePath: String, document: Document) { updateCrossReferences(document, mappings); }
//    BibleBookAndFileMapperEnhancedUsx.iterateOverSelectedFiles(::process)
//  }
//
//
//  /****************************************************************************/
//  /**
//  * Runs over a given document and updates any cross-references in the wake of
//  * reversification changes.
//  *
//  * I carry out only very rudimentary checks here.  The details are as follows:
//  *
//  * - I allow for the loc parameter to be a single reference or a range.
//  *
//  * - If the low reference (or only reference) is not subject to a mapping, I
//  *   assume that nothing will be (but do not check).
//  *
//  * - If the low reference (on ranges) is subject to a mapping but the high is
//  *   not, I treat this as an error and change the ref to _X_contentOnly.
//  *   However, I don't bother to check any of the other references which may
//  *   make up the range.
//  *
//  * - Otherwise, I update the loc parameter to reflect the mappings.  Note,
//  *   though, that I do *not* update the user-visible vernacular text
//  *   associated with the ref tag.  This would be difficult, but thankfully
//  *   that's not an issue, because I've been requested not to do it.
//  *
//  * @param mappings Maps old refKey to new one.
//  */
//
//  fun updateCrossReferences (document: Document, mappings: Map<RefKey, RefKey>)
//  {
//    fun oops (refNode: Node)
//    {
//      warning(refNode, "Cross-ref broken as a result of reversification")
//      MiscellaneousUtils.recordTagChange(refNode, "_X_contentOnly", null, "Cross-ref broken as a result of reversification")
//    }
//
//    fun processRef (refNode: Node)
//    {
//      var loc = Dom.getAttribute(refNode, "loc")!!
//      val range = RefRange.rdUsx(loc)
//
//      val lowRefKey = range.getLowAsRefKey()
//      val lowMapping = mappings[lowRefKey] ?: return // Slight cop out.  I assume that if the low ref isn't mapped, nothing will be; and that if it is, all should be.
//
//      val highRefKey = range.getHighAsRefKey()
//      val highMapping = mappings[lowRefKey]
//      if (null == highMapping) // If low is mapped, I expect high to be mapped as well.
//      {
//        oops(refNode)
//        return
//      }
//
//      loc = Ref.rd(lowMapping).toString()
//      if (lowRefKey != highRefKey) loc += "-" + Ref.rd(highMapping).toString()
//      Dom.setAttribute(refNode, "loc", loc)
//      Dom.setAttribute(refNode, "_X_attributeValueChangedReason", "Revised loc to reflect reversification")
//    }
//
//    Dom.findNodesByName(document, "ref").forEach { processRef(it) }
//  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Miscellaneous                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Add to each ref details of the chapter or verse to which it belongs. */

  private fun addBelongsTo (document: Document)
  {
    var chapterRef = ""
    var theRef = ""

    fun processNode (node: Node)
    {
      when (Dom.getNodeName(node))
      {
        "_X_chapter" -> chapterRef = Dom.getAttribute(node, "sid")!!
        "verse" -> theRef = if ("sid" in node) node["sid"]!! else chapterRef
        "ref" -> node["_X_belongsTo"] = theRef
        "char" ->
          if (node["style"]!!.matches("x.?t".toRegex()))
            node["_X_belongsTo"] = if (theRef.contains("-")) RefRange.rdUsx(theRef).getHighAsRef().toString() else theRef
      }
    }

    document.getAllNodes().forEach { processNode(it) }
  }


  /****************************************************************************/
  /* Called when we have a char:xt containing no ref information in USX form
     (which will happen if the char:xt lacks both href-link and embedded
     ref tags).

     This is complicated by the fact that both USX and OSIS require that the
     reference be in the form of either a single reference or a range, but I've
     seen texts where the reference was in the form of a collection.  In the
     case of it being a collection, it needs to be split out into multiple
     ref's, all of which go within the enclosing char:xt (and replace its
     existing content). */

  private fun generateContainedRefTagsUsingParsedVernacularContent (node: Node, embeddedDetails: List<RefFormatHandlerReaderVernacular.EmbeddedReferenceElement>)
  {
    /**************************************************************************/
    fun processElt (elt: RefFormatHandlerReaderVernacular.EmbeddedReferenceElement)
    {
      if (elt is RefFormatHandlerReaderVernacular.EmbeddedReferenceElementText)
      {
        if (elt.text.isNotBlank()) // I don't think it ever can be blank or empty, in fact, because that would be subsumed into the reference.
          node.appendChild(Dom.createTextNode(node.ownerDocument, elt.text))
      }

      else // A reference collection
      {
        val rcElt = elt as RefFormatHandlerReaderVernacular.EmbeddedReferenceElementRefCollection
        val rc = rcElt.rc
        val refNode = Dom.createNode(node.ownerDocument, "<ref _X_generatedReason='Converted from char:xt vernacular content'/>")
        refNode["_X_belongsTo"] = node["_X_belongsTo"]!!
        refNode["loc"] = rc.toStringUsx()
        refNode.textContent = elt.text  // Does this actually manage to carry through the original formatting?
        node.appendChild(refNode)
      } // else
    } // fun


    /**************************************************************************/
    Dom.deleteChildren(node)
    embeddedDetails.forEach { processElt(it) }
  }


  /****************************************************************************/
  /* At present this is called on char:xt only -- not sure whether I may need to
     be able to cope with ref as well at some point.

     char:xt _may_ contain a link-href attribute which gives the target of the
     cross-reference in USX form.  However, the USX standard does not mandate
     this, and if it is absent, we have to fall back on attempting to parse the
     vernacular text within the char:xt (which we can do only if
     stepUseVernacularFormats is 'Yes').

     Just to make this as difficult as possible a) Sometimes this text contains
     noise words which we have to cope with (like 'See also Jn 3:16'); and b)
     Sometimes it turns out to be a reference _collection_, and this has to be
     split out into multiple elements.
     */

  private fun tryCreatingXtFromVernacularContent (node: Node): String?
  {
    /**************************************************************************/
    if (!m_CanReadAndWriteVernacular)
      return "Needed to parse vernacular content but don't have details of format"



    /**************************************************************************/
    if (node.hasChildNodes())
      return "Needed to parse vernacular content, but can't cope with existence of child nodes"



    /**************************************************************************/
    /* The following will leave us with a list of EmbeddedReferenceElementText
       and EmbeddedReferenceElementRefCollection instances -- any number of
       them, but alternating.  (Hopefully at least one of the latter, or we
       haven't managed to parse the data at all.)

       Note that there is no guarantee as far as ordering is concerned -- if
       we have a mixture of the two, the list may start or end with either. */

    val textContent = node.textContent
    val context = Ref.rdUsx(node["_TEMP_belongsTo"]!!)
    val embeddedDetails =
      try
      {
        readEmbedded(textContent.trim(), context=context)
      }
      catch (e: Exception)
      {
        return "Couldn't parse vernacular text"
      }



    /**************************************************************************/
    /* If we have no EmbeddedReferenceElementRefCollection elements at all, then
       the string could not be parsed, which is a problem. */

    val nRefs = embeddedDetails.filterIsInstance<RefFormatHandlerReaderVernacular.EmbeddedReferenceElementRefCollection>().count()
    return if (0 == nRefs)
      "Vernacular content lacks any reference information"
    else
    {
      generateContainedRefTagsUsingParsedVernacularContent(node, embeddedDetails)
      null
    }
  }


  /****************************************************************************/
  private fun validateUsx (ref: String): Boolean
  {
    return try {
      RefCollection.rdUsx(ref)
      true
    }
    catch (_: Exception)
    {
      false
    }
  }


  /****************************************************************************/
  private var m_CanReadAndWriteVernacular =  ConfigData.getAsBoolean("stepUseVernacularFormats")
}
