package org.stepbible.textconverter.usxinputonly

import org.stepbible.textconverter.support.bibledetails.BibleAnatomy
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.*
import org.stepbible.textconverter.support.ref.RefFormatHandlerReaderVernacular.readEmbedded
import org.stepbible.textconverter.utils.Usx_FileProtocol
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.util.*

/******************************************************************************/
/**
 * Parses, checks and possibly remaps cross-references.  USX only.
 *
 * ## Overview
 *
 * I suppose eventually I may come across an aspect of USX which doesn't make
 * for complicated processing.  Unfortunately, this isn't one of them.
 *
 * - There are several tags involved in cross-reference markup.
 *
 * - There are different ways of using these tags.
 *
 * - There are certain rules which may or may not have been obeyed.
 *
 * - References within a cross-reference are usually given in both of two forms
 *   -- USX and vernacular.  In theory these should tie up, but they don't
 *   always do so.
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
 * ## Slightly less of an overview and more of a detailed consideration
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
 * often, it simply serves as a container for ref tags.  (I think it may be
 * a hang-over from earlier versions of USX.)
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
 * contexts.  The loc parameter must always be in USX format, and be either a
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
 *   appear within xt.  (In fact noise words like this appear relatively
 *   frequently.  Unfortunately it is frequently the case that they are *not*
 *   enclosed in char:xta.)
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

 object Usx_CrossReferenceCanonicaliser
 {
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                  Public                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

   /****************************************************************************/
   fun process (doc: Document) = doc.findNodesByName("book").forEach(::process)
   fun process (rootNode: Node) = doIt(rootNode)





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                  Private                               **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun doIt (rootNode: Node)
  {
    /**************************************************************************/
    /* Turn char:xot, char:xnt and char:dc all to char:xt to simplify later
       processing.  Having done this, we now have all of the char:xt nodes
       we're ever going to have, so to save looking for them repeatedly in
       later processing, we can simply build up some lists here. */

    val charXts = canonicaliseCharXtsStandardiseStyles(rootNode)
    var charXtsHavingRefs = canonicaliseCharXtsFindCharXtsHavingRefs(rootNode)
    var charXtsLackingRefs = charXts - charXtsHavingRefs.toSet()



    /**************************************************************************/
    /* Add an indication of which verse 'owns' each cross-reference.  I need to
       do this early because it's used when generating diagnostics.  However
       I may also need to add to the list later if I generate any additional
       refs. */

    addBelongsTo(rootNode)



    /**************************************************************************/
    /* A given char:xt should contain _either_ a link-href attribute or one or
       more ref nodes (or, in fact, possibly neither).  To avoid later
       confusion, I drop href-link in favour of ref where both exist. */

    canonicaliseCharXtsDealWithHrefLinkNodesWhichAlsoContainRefTags(charXtsHavingRefs)



    /**************************************************************************/
    /* Where href-links remain, convert them to refs. */

    var diffs = canonicaliseCharXtsConvertHrefsToRefs(charXts)
    charXtsLackingRefs = charXtsLackingRefs - diffs.toSet()
    var haveGeneratedNewRefs = diffs.isNotEmpty()



    /**************************************************************************/
    /* USX does allow for refs to be based purely upon vernacular text,
       more's the pity.  Where this is the case, try to work out the equivalent
       USX. */

    diffs = canonicaliseCharXtsReliantUponVernacularText(charXtsLackingRefs)
    charXtsLackingRefs = charXtsLackingRefs - diffs.toSet()
    charXtsHavingRefs = charXtsHavingRefs + diffs.toSet()

    haveGeneratedNewRefs = haveGeneratedNewRefs || diffs.isNotEmpty()
    if (haveGeneratedNewRefs) addBelongsTo(rootNode)



    /**************************************************************************/
    /* We may possibly have some char:xt's which still don't actually contain
       any ref tags.  Flag those. */

    canonicaliseCharXtsHandleNodesLackingRefs(charXtsLackingRefs)



    /**************************************************************************/
    /* We have now done all we can with char:xt tags themselves.  Time to look
       at any contained ref tags.

       First, ref tags are _supposed_ to have loc tags, because they tell us
       where to point to.  If there are any which _don't_ have loc tags, turn
       them into plain text nodes. */

    var refs = Dom.findNodesByName(rootNode, "ref", false)
    diffs = canonicaliseCharXtsConvertLoclessNodesToTextOnly(refs)
    refs = refs - diffs.toSet()



    /**************************************************************************/
    /* USX caters for special references to appear within major headings.
       These are probably best converted to plan text, because they normally
       point to very large chunks of text -- eg whole chapters -- and those
       don't really work in STEP as cross-references, because cross-references
       are shown in pop-up windows, which don't readily accommodate vast
       amounts of text. */

    diffs = canonicaliseRefsDropRefsFromMajorHeadings(rootNode)
    refs = refs - diffs.toSet()



    /**************************************************************************/
    /* Convert any ref containing an invalid loc attribute to _X_contentOnly. */

    diffs = canonicaliseRefsValidateLocAttributes(refs)
    refs = refs - diffs.toSet()



    /**************************************************************************/
    /* In theory, ref 'loc' attributes shouldn't contain collections, but
       sometimes they do.  In these cases, attempt to split them out into
       separate ref tags. */

    refs = refs + canonicaliseRefsSplitCollections(refs)



    /**************************************************************************/
    /* References to single-chapter books have to include chapter=1. */

    canonicaliseRefsConvertSingleChapterReferences(refs)



    /**************************************************************************/
    /* The Crosswire version of osis2mod doesn't support subverses, so if we
       have a loc which targets subverses, I take the somewhat arbitrary
       decision to replace them with their owning verses. */

    //$$$SubverseProcessor.canonicaliseRefsConvertCrossVerseSubverseRangesToVersesIfNecessary(refs)



    /**************************************************************************/
    canonicaliseRefsCompareLocAndContent(refs)



    /**************************************************************************/
    /* Really of interest only on Biblica texts -- they have a distressing
       habit of enclosing _all_ footnotes in note:f, whereas really cross-
       references should be in note:x. */

    if ("Biblica".equals(ConfigData["stepFileSelectorForOwnerOrganisation"], ignoreCase = true))
      canonicaliseNotesCorrectNoteStyles(rootNode)



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
  /* By the time we get here, href-link will have been removed from any nodes
     which also contain ref nodes, since the two serve the same purpose, and
     if any nodes have both, I make the assumption that ref can be relied upon.

     This method processes any remaining href-link nodes, and creates ref
     nodes below them instead. */

  private fun canonicaliseCharXtsConvertHrefsToRefs (charXts: List<Node>): List<Node>
  {
    /**************************************************************************/
    val res: MutableList<Node> = mutableListOf()



    /**************************************************************************/
    fun processXt (node: Node)
    {
      val href = node["href-link"]!!
      val originalChildren = Dom.getChildren(node)
      Dom.deleteNodes(originalChildren)
      val newNode = Dom.createNode(node.ownerDocument, "<ref loc='$href' _X_action='Added refTag in order to canonicalise char:xt'/>")
      Dom.addChildren(newNode, originalChildren)
      node.appendChild(newNode)
      node -= "href-link"
      res.add(node)
    }



    /**************************************************************************/
    charXts.filter { "href-link" in it } .forEach { processXt(it) }
    return res
  }


  /****************************************************************************/
  /* We have seen some texts with ref tags but lacking loc parameters.  This
     seems rather bizarre, given that the whole raison d'etre of ref is to point
     to verses, and the loc tells you where to point.  However, it is deemed
     useful to retain the details as read-only information. */

  private fun canonicaliseCharXtsConvertLoclessNodesToTextOnly (refs: List<Node>): List<Node>
  {
    val res: MutableList<Node> = mutableListOf()
    refs
      .filter { "loc" !in it }
      .forEach {
        Usx_FileProtocol.recordTagChange(it, "_X_contentOnly", null, "Was ref with no loc")
        recordWarning(it, "Was ref with no loc")
        res.add(it)
      }

      return res
  }


  /****************************************************************************/
  /* A given char:xt should contain _either_ a href-link or embedded refs, but
     not both.  (Possibly it may contain neither.)  If it does contain both,
     I throw away the link-href and just retain the refs -- I think trying to
     check the two are compatible is just a step too far. */

  private fun canonicaliseCharXtsDealWithHrefLinkNodesWhichAlsoContainRefTags (charXtsHavingRefs: List<Node>)
  {
    charXtsHavingRefs
      .filter { "href-link" in it }
      .forEach {
        recordWarning(it, "Deleted href-link attribute (${it["href-link"]!!}) in favour of existing ref tags")
        it -= "href-link"
      }
  }


  /****************************************************************************/
  private fun canonicaliseCharXtsFindCharXtsHavingRefs (rootNode: Node): List<Node>
  {
    /**************************************************************************/
    val res: MutableList<Node> = mutableListOf()



    /**************************************************************************/
    fun findXtAncestor (node: Node)
    {
      var parent = node.parentNode
      while (null != parent)
      {
        if ("char" == parent.nodeName && "xt" == parent["style"])
        {
          res.add(parent)
          return
        }

        parent = parent.parentNode
      }
    }



    /**************************************************************************/
    Dom.findNodesByName(rootNode, "ref", false).forEach { findXtAncestor(it) }
    return res
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
     but we straighten that out later.  Returns a list of nodes which have
     been amended. */

  private fun canonicaliseCharXtsReliantUponVernacularText (charXtsLackingRefs: List<Node>): List<Node>
  {
    /**************************************************************************/
    val res: MutableList<Node> = mutableListOf()
    val owners = IdentityHashMap<Node, Ref>()



    /**************************************************************************/
    var mostRecentRef: Ref? = null
    fun makeOwners ()
    {
      charXtsLackingRefs[0].ownerDocument.getAllNodes().forEach {
        when (Dom.getNodeName(it))
        {
          "chapter", "verse" ->
          {
            if ("sid" in it)
              mostRecentRef = RefCollection.rdUsx(it["sid"]!!).getLowAsRef()
          }

          "char" ->
          {
            if ("xt" == it["style"])
              owners[it] = mostRecentRef!!
          }
        } // when
      } // forEach
    } // fun



    /**************************************************************************/
    fun process (node: Node)
    {
      val s = tryCreatingXtFromVernacularContent(node, owners)
      if (null != s)
      {
        recordWarning(node, "$s (was char:x?t)")
        res.add(node)
      }
    }



    /**************************************************************************/
    var toBeProcessed = charXtsLackingRefs.filter { "href-link" !in it }
    if (toBeProcessed.isNotEmpty())
    {
       makeOwners()
       toBeProcessed.forEach { process(it) }
    }

    return res
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
       can make the thing work.  (This later processing always checks to see
       whether the targets for the cross-references exist.)

     This final option seems to be the most useful, so that's what I've gone
     with.  I do add a temporary attribute so that later processing won't
     issue warnings if the target turns out not to exist.
   */

  private fun canonicaliseCharXtsStandardiseStyles (rootNode: Node): List<Node>
  {
    val res: MutableList<Node> = mutableListOf()

    Dom.findNodesByName(rootNode, "char", false).forEach {
      when (val style = it["style"]!!)
      {
        "xt" -> res.add(it)
        "xot", "xnt", "xdc" -> { res.add(it); Usx_FileProtocol.recordTagChange(it, "char", "xt", "Was $style") }
      }
    }

    return res
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
     containing the target text. */

  private fun canonicaliseNotesCorrectNoteStyles (rootNode: Node)
  {
    val C_NoOfCanonicalWordsWhichMeansThisIsANoteF = 6
    rootNode.findNodesByAttributeValue("note", "style", "f").forEach { noteNode -> // We look only at note:f.
      if (null != noteNode.findNodeByName("ref", false)) /* &&
          null == Dom.findNodeByAttributeValue(noteNode, "char", "style", "fqa"))*/ // Nothing to do unless the note tag contains the right flavours of node, and does not contain the wrong ones.
      {
        val charNodes = noteNode.findNodesByName("char", false)
        val canonicalText = charNodes.joinToString(" ") { Dom.getCanonicalTextContentToAnyDepth(it) }
        if (StepStringUtils.wordCount(canonicalText) < C_NoOfCanonicalWordsWhichMeansThisIsANoteF)
          Usx_FileProtocol.recordTagChange(noteNode, "note", "x", "Style was 'f' but contains cross-reference details.")
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

  private fun canonicaliseRefsDropRefsFromMajorHeadings (rootNode: Node): List<Node>
  {
    val res: MutableList<Node> = mutableListOf()

    fun modifyRef (ref: Node)
    {
      Usx_FileProtocol.recordTagChange(ref, "_X_contentOnly", Dom.getAttribute(ref, "style"), "Probably not intended to be clickable")
      res.add(ref)
    }

    fun modifyRefs (owner: Node) = owner.findNodesByName("ref", false).forEach { modifyRef(it) }

    rootNode.findNodesByAttributeValue("para", "style", "mr" ).forEach { modifyRefs(it) }
    rootNode.findNodesByAttributeValue("para", "style", "sr" ).forEach { modifyRefs(it) }
    rootNode.findNodesByAttributeValue("char", "style", "ior").forEach { modifyRefs(it) }

    return res
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

  private fun canonicaliseRefsSplitCollections (refs: List<Node>): List<Node>
  {
    /**************************************************************************/
    val res: MutableList<Node> = mutableListOf()



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
        res.add(newNode)

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
    refs
      .filter { 1 != RefCollection.rdUsx(it["loc"]!!).getElementCount() }
      .forEach { process(it) }

    return res
  }


  /****************************************************************************/
  /* Checks for any loc parameters which have invalid loc attributes.  Reports
     them and changes the owning ref into an _X_contentOnly. */

  private fun canonicaliseRefsValidateLocAttributes (refs: List<Node>): List<Node>
  {
    val res: MutableList<Node> = mutableListOf()
    refs.forEach {
      if (!validateUsx(it["loc"]!!))
      {
        recordWarning(it, "ref has invalid loc (${it["loc"]!!})")
        Usx_FileProtocol.recordTagChange(it, "_X_contentOnly", null, "Invalid loc")
        res.add(it)
      }
    }

    return res
  }





 /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Reporting                               **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun recordInfo (node: Node, attributeValue: String)
  {
    Logger.info(RefCollection.rdUsx(node["_X_belongsTo"]!!).getFirstAsRefKey(), attributeValue)
  }


  /****************************************************************************/
  private fun recordWarning (node: Node, attributeValue: String)
  {
    node["_X_warning"] = attributeValue
    val refKey = RefCollection.rdUsx(node["_X_belongsTo"]!!).getFirstAsRefKey()
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
  /* Adds chapter 1 to single chapter references which lack the chapter. */

  private fun canonicaliseRefsConvertSingleChapterReferences (refs: List<Node>)
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

    refs.forEach { processRef(it) }
  }


  /****************************************************************************/
  /* All ref tags should have loc attributes and content.  STEP uses the latter
     to provide the clickable text used to access cross-references.  I kind of
     _assume_, therefore, that the content and loc should both refer to the
     same reference, with the loc being the USX version and the content being
     the vernacular version.  There is actually -- so far as I know -- no
     _requirement_ for this to be the case, so I merely issue a warning here
     if the two do not tie up. */

  private fun canonicaliseRefsCompareLocAndContent (refs: List<Node>)
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
    refs.forEach { validate(it) }
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

  fun updateCrossReferences (rootNode: Node, mappings: Map<RefKey, RefKey>)
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
//      UsxFileFormat.recordTagChange(refNode, "_X_contentOnly", null, "Cross-ref broken as a result of reversification")
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

  private fun addBelongsTo (rootNode: Node)
  {
    var chapterRef = ""
    var theRef = ""

    fun processNode (node: Node)
    {
      when (Dom.getNodeName(node))
      {
        "chapter" -> chapterRef = Dom.getAttribute(node, "sid")!!
        "verse" -> theRef = if ("sid" in node) node["sid"]!! else chapterRef
        "verseSid" -> theRef = if ("sid" in node) node["sid"]!! else chapterRef
        "ref" -> node["_X_belongsTo"] = theRef
        "char" ->
          if ("xt" == node["style"])
            node["_X_belongsTo"] = if (theRef.contains("-")) RefRange.rdUsx(theRef).getHighAsRef().toString() else theRef
      }
    }

    rootNode.getAllNodes().forEach { processNode(it) }
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
          node.appendChild(node.ownerDocument.createNode(elt.text))
      }

      else // A reference collection
      {
        val rcElt = elt as RefFormatHandlerReaderVernacular.EmbeddedReferenceElementRefCollection
        val rc = rcElt.rc
        val refNode = node.ownerDocument.createNode("<ref _X_generatedReason='Converted from char:xt vernacular content'/>")
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

  private fun tryCreatingXtFromVernacularContent (node: Node, owners: IdentityHashMap<Node, Ref>): String?
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
    val context = owners[node]
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
