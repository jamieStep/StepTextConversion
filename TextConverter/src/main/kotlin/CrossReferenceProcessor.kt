/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleAnatomy
import org.stepbible.textconverter.support.bibledetails.BibleBookAndFileMapperEnhancedUsx
import org.stepbible.textconverter.support.bibledetails.BibleStructureTextUnderConstruction
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.support.miscellaneous.StepStringFormatter
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
 * which is not present in the real markup.  A more detailed discussion of
 * these tags and the variations upon them appears later.  For now, the
 * important take-away message is that each gives the target reference(s)
 * in two forms -- a USX format attribute (loc or link-href) which tells
 * teh processing where to link to, and a (presumably) vernacular form
 * which is visible to the user, and which they can click on to activate
 * the link.  (This vernacular form is given by the *content* of the tag.
 * It is *presumably* going to be a vernacular representation of the target,
 * although I don't recall seeing anything which mandates this.)
 *
 * So far as I can see, the two tags serve the same ends in much the same
 * way -- except a) that char:xt may actually *contain* a ref tag which
 * does all the work; and b) that the link-href attribute is optional, and
 * in the absence of a contained ref tag, you would need to parse the tag
 * content to work out where the tag is supposed to be pointing.
 *
 * So things we might check / might do :-
 *
 * - Generate a ref tag within char:x?t where they lack a ref tag but do
 *   have a link-href.  This means that I only then need to deal with
 *   refs.  This I *do* do.
 *
 * - Parse the content of char:x?t tags which lack link-href to determine
 *   where the tag is pointing, and then create refs for these too.
 *   I'm not doing this at present -- I do have code which might do the job
 *   but it may take a fair bit of work to get it going, and in any case
 *   it will only work if I have details of how vernacular references are
 *   formatted.  Fortunately, I don't think I've seen many char:xt's of this
 *   kind.
 *
 * - Check that the loc parameter of all actual or generated refs are legit.
 *   Again, I don't do this -- I'm assuming, somewhat riskily, that the
 *   translators have got it right.
 *
 * - Check that the content of actual and generated refs point to the same
 *   place as do the corresponding loc attributes.  (I don't do that either,
 *   since it would require me to parse vernacular text.)
 *
 * - Check that the text contains the relevant target references, so that
 *   cross-referencing will actually work.  This I *do* do.  If the targets
 *   do not exist I issue a warning (except where xnt, xot or xdc give the
 *   impression that the translators didn't *expect* the target to be present)
 *   and turn the tag into plain text.
 *
 * - Subsequent to reversification, update references.  I *do* do this, but
 *   only in a slightly half-hearted manner.  Reversification may change the
 *   verse numbers assigned to particular pieces of text, and presumably
 *   cross-references which pointed to them previously need still to point to
 *   them now.  To this end, I update the USX references which appear in the
 *   loc attribute of refs.  However, I *don't* update the associated
 *   displayable text.  For all the reasons set out above, this would be
 *   difficult to do anyway, but I've specifically been asked not to do it.
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
 * References to entire books or chapters I turn into plain text, either because
 * it doesn't seem to make sense to treat them as cross-references, or because
 * at one time STEP would crash or hang if trying to cross-reference a large
 * amount of text.
 *
 *
 * *char:xt*
 *      <char style="xt" link-href="GEN 2:1">1</char>
 *
 * This ostensibly gives target references.  In this case, though, the target
 * references are based purely upon the text which appears as the content of the
 * tag (and will therefore be supplied in vernacular form only).  The link-href
 * parameter is optional.  If present, it should give an equivalent scripture
 * reference in USX format for use in any glossaries etc.  This makes it
 * possible to give very terse references (as the '1' in the example above),
 * and still to have something meaningful in the glossary.  The USX ref manual
 * says that if the content comprises just a single number, it should be
 * interpreted as a chapter number (in the context of the owning book), and not
 * as a verse number.
 *
 * As outlined above, char:xt may already contain a ref tag, in which case we
 * already have all we need, and I leave the char:xt as-is.  If char:xt has
 * link-href, I generate a ref tag and put it below the char:xt.  Otherwise,
 * I warn about the issue and drop the tag.
 *
 *
 * *note*
 *     <note caller="..." style="x|ex">
 *       ... Various tags.
 *     </note>
 *
 *
 * The note tag encapsulates various of the other tags discussed here (notably
 * char:xt and ref).  In STEP it arranges for details of the cross-reference
 * to be placed in the left margin (or at any rate, it used to).
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
  /**                             Canonicalisation                           **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun canonicalise (document: Document)
  {
    addBelongsTo(document)
    canonicaliseCharXt(document)
    //addBelongsTo(document)
    convertLoclessRefsToTextOnly(document)
    dropRefsFromMajorHeadings(document)
    //validate(document)
    checkRefTargetsExist(document)
    convertSingleChapterReferences(document)
    deleteBelongsTo(document)
  }


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
        "verse" -> theRef = if (Dom.hasAttribute(node, "sid")) Dom.getAttribute(node, "sid")!! else chapterRef
        "ref" -> Dom.setAttribute(node, "_TEMP_belongsTo", theRef)
        "char" ->
          if ("xt" == Dom.getAttribute(node, "style"))
            Dom.setAttribute(node, "_TEMP_belongsTo", if (theRef.contains("-")) RefRange.rdUsx(theRef).getHighAsRef().toString() else theRef)
      }
    }

    Dom.collectNodesInTree(document).forEach { processNode(it) }
  }


  /****************************************************************************/
  /* Converts x?t to xt, and creates ref tags where necessary / possible. */

  private fun canonicaliseCharXt (document: Document)
  {
    /**************************************************************************/
    fun changeToXt (node: Node)
    {
      MiscellaneousUtils.recordTagChange(node, "char", "xt", "Reduced to common form")
    }

    Dom.findNodesByAttributeValue(document, "char", "style", "xot").forEach { changeToXt(it) }
    Dom.findNodesByAttributeValue(document, "char", "style", "xnt").forEach { changeToXt(it) }
    Dom.findNodesByAttributeValue(document, "char", "style", "xdc").forEach { changeToXt(it) }



    /**************************************************************************/
    fun processXt (node: Node)
    {
      if (null != Dom.findNodeByName(node, "ref", false)) return // Already has a ref child -- assume that's all we need.

      val href = Dom.getAttribute(node, "href-link")
      if (null == href) // No href-link, so no way of handling it.
      {
        if (!tryCreatingXtFromVernacularContent(node))
        {
          warning(node, "Can't identify target: " + Dom.toString(node) + " / " + node.textContent)
          MiscellaneousUtils.recordTagChange(node, "_X_contentOnly", null, "Can't identify target")
        }
      }

      else // We have a href-link.  Use it to create a ref node and place it under char:xt, inheriting the children from the latter.
      {
        val originalChildren = Dom.getChildren(node)
        Dom.deleteNodes(originalChildren)
        val newNode = Dom.createNode(document, "<ref loc='$href' _X_tagGeneratedReason='Canonicalise char:xt'/>")
        Dom.addChildren(newNode, originalChildren)
        node.appendChild(newNode)
        Dom.deleteAttribute(node, "href-link")
      }
    }

    Dom.findNodesByAttributeValue(document, "char", "style", "xt").forEach { processXt(it) }
  }


  /****************************************************************************/
  /* Checks ref targets exist. */

  private fun checkRefTargetsExist (document: Document)
  {
    /**************************************************************************/
    fun processRef (node: Node)
    {
      val rc = RefCollection.rdUsx(Dom.getAttribute(node, "loc")!!)
      val foundError = rc.getAllAsRefs().firstOrNull { !BibleStructureTextUnderConstruction.hasBook(it) }

      if (null != foundError)
      {
        val was = Dom.getAttribute(node, "_X_origTag")
        if (null != was && "char:xot" != was && "char:xnt" != was && "char:xdc" != was)
          error(node, "Target does not exist: " + Dom.getAttribute(node, "loc")!!)
        MiscellaneousUtils.recordTagChange(node, "_X_contentOnly", null, "Target does not exist")
      }
    }

    Dom.findNodesByName(document, "ref").forEach { processRef(it) }
  }


  /****************************************************************************/
  /* We have seen some texts with ref tags but lacking loc parameters.  This
     seems rather bizarre, given that the whole raison d'etre of ref is to point
     to verses, and the loc tells you where to point.  However, it is deemed
     useful to retain the details as read-only information. */

  private fun convertLoclessRefsToTextOnly (document: Document)
  {
    Dom.findNodesByName(document, "ref")
      .filter { !Dom.hasAttribute(it, "loc") }
      .forEach { MiscellaneousUtils.recordTagChange(it, "_X_contentOnly", null, "Was ref with no loc") }
  }


  /****************************************************************************/
  /* Adds chapter 1 to single chapter references which lack the chapter. */

  private fun convertSingleChapterReferences (document: Document)
  {
    fun processRef (node: Node)
    {
      var loc = Dom.getAttribute(node, "loc") ?: return // Of course, it always _should_ have loc.
      if (loc.contains(":")) return // Already have a chapter.
      if (!BibleAnatomy.isSingleChapterBook(loc)) return // Nothing to do.

      val bits = loc.split("\\s+".toRegex())
      loc = bits[0] + " 1:" + bits[1]
      Dom.setAttribute(node, "loc", loc)
      Dom.setAttribute(node, "_X_attributeValueChangedReason", "Added v1 to single-chapter-book reference")
    }

    Dom.findNodesByName(document, "ref").forEach { processRef(it) }
  }


  /****************************************************************************/
  /* Get rid of the temporary markers. */

  private fun deleteBelongsTo (document: Document)
  {
    Dom.findNodesByAttributeName(document, "*", "_TEMP_belongsTo").forEach { Dom.deleteAttribute(it, "_TEMP_belongsTo")}
  }


  /****************************************************************************/
  /* Certain headings -- particularly those at the start of a book -- tend to
     contain ref tags which point to large chunks of text (multiple chapters,
     for instance).  We probably don't want these actually to be clickable,
     both because there is no point, when sitting at the front of the book
     then to look at 10 chapters of that book in a separate window, and also
     because trying to show a lot of text causes STEP to crash (or certainly
     did so at one time).  I therefore change these refs to _X_contentOnly. */

  private fun dropRefsFromMajorHeadings (document: Document)
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

  private fun tryCreatingXtFromVernacularContent (node: Node): Boolean
  {
    /**************************************************************************/
    if (!m_CanReadAndWriteVernacular) return false



    /**************************************************************************/
    /* The following will leave us with a list of EmbeddedReferenceElementText
       and EmbeddedReferenceElementRefCollection instances -- any number of
       them, but alternating.  (Hopefully at least one of the latter, or we
       haven't managed to parse the data at all.)

       Note that there is no guarantee as far as ordering is concerned -- if
       we have a mixture of the two, the list may start or end with either. */

    val textContent = node.textContent
    val belongsTo = Dom.getAttribute(node, "_TEMP_belongsTo")!!
    val context = Ref.rdUsx(belongsTo)
    val embeddedDetails = readEmbedded(textContent.trim(), context=context)



    /**************************************************************************/
    /* If we have no EmbeddedReferenceElementRefCollection elements at all, then
       the string could not be parsed, which is a problem. */

    val nRefs = embeddedDetails.filterIsInstance<RefFormatHandlerReaderVernacular.EmbeddedReferenceElementRefCollection>().count()
    if (0 == nRefs) return false



    /**************************************************************************/
    /* Both USX and OSIS require that a single reference tag contains either a
       single reference or a single reference range -- collections are not
       permitted.  However, I've seen quite a number of texts which do in fact
       have collections.  I've tried retaining them as such, but that doesn't
       work, so here if I have a collection I have to generate multiple
       refs.  Whatever I generate here replaces the char:xt, which I don't think
       is actually needed (and if it is, I can't work out what it should look
       like). */

    val elt = embeddedDetails.filterIsInstance<RefFormatHandlerReaderVernacular.EmbeddedReferenceElementRefCollection>().first()
    val rc = elt.rc
    val refNodes: MutableList<Node> = mutableListOf()
    rc.getElements().forEach {
      val refNode = Dom.createNode(node.ownerDocument, "<ref _X_generatedReason='Converted from char:xt'/>")
      Dom.setAttribute(refNode, "loc", it.toStringUsx())
      refNode.textContent = it.toStringVernacular("e")
      refNodes.add(refNode)
     }

     refNodes.indices.forEach {
       Dom.insertNodeBefore(node, refNodes[it])
       if (it != refNodes.size - 1)
       {
         val thisRef = RefRange.rdUsx(Dom.getAttribute(refNodes[it], "loc")!!).getHighAsRef()
         val nextRef = RefRange.rdUsx(Dom.getAttribute(refNodes[it + 1], "loc")!!).getLowAsRef()
         val sep =
           if (thisRef.getB() == nextRef.getB() && thisRef.getC() == nextRef.getC())
             RefFormatHandlerWriterVernacular.getCollectionSeparatorSameChapter() + " "
           else
             RefFormatHandlerWriterVernacular.getCollectionSeparatorDifferentChapters() + " "

         Dom.insertNodeBefore(node, Dom.createTextNode(node.ownerDocument, sep))
       }
     }

     Dom.deleteNode(node)

    return true
  }



  /****************************************************************************/
  /* By the time we get here, all cross-references should be held in ref tags.
     This method checks that the content of the tag can be parsed, and also that
     this vernacular text corresponds to the text held in the loc attribute.

     Currently I change the ref tag into a content-only tag if I can't parse the
     loc parameter.  Anything else I give the benefit of the doubt to: I report
     it as a warning, but I still retain the ref tag as such.

     NOT USED AT PRESENT.  This gets too complicated.
   */

  private fun validate (document: Document)
  {
    /**************************************************************************/
    if (!m_CanReadAndWriteVernacular) return



    /**************************************************************************/
    fun validateRef (ref: Node)
    {
      /************************************************************************/
      val usxCollection: RefCollection
      try
      {
        usxCollection = RefCollection.rdUsx(Dom.getAttribute(ref, "loc")!!)
      }
      catch (_: Exception)
      {
        warning(ref, "Could not parse loc parameter")
        MiscellaneousUtils.recordTagChange(ref, "_X_contentOnly", null, "Could not parse loc parameter")
        return
      }



      /************************************************************************/
      val vernacularDetails: List<RefFormatHandlerReaderVernacular.EmbeddedReferenceElement>
      try
      {
        println(ref.textContent)
        val context = RefCollection.rdUsx(Dom.getAttribute(ref, "loc")!!).getHighAsRef()
        vernacularDetails = readEmbedded(ref.textContent, context = context)
      }
      catch (_: Exception)
      {
        warning(ref, "Could not parse loc parameter")
        MiscellaneousUtils.recordTagChange(ref, "_X_contentOnly", null, "Could not parse vernacular content")
        return
      }


      /************************************************************************/
      val vernacularCollection = RefCollection()
      vernacularDetails.filterIsInstance<RefFormatHandlerReaderVernacular.EmbeddedReferenceElementRefCollection>().forEach { vernacularCollection.add(it.rc) }
      if (vernacularCollection.getElementCount() != usxCollection.getElementCount())
      {
        warning(ref, "Element counts differ between USX details and vernacular details")
        // MiscellaneousUtils.recordTagChange(ref, "_X_contentOnly", null, "Element counts differ between USX details and vernacular details")
        return
      }

      for (i in usxCollection.getElements().indices)
      {
        val usxRefOrRange        = usxCollection.getElements()[i]
        val vernacularRefOrRange = vernacularCollection.getElements()[i]
        if (usxRefOrRange.getLowAsRefKey () != vernacularRefOrRange.getLowAsRefKey() ||
            usxRefOrRange.getHighAsRefKey() != vernacularRefOrRange.getHighAsRefKey())
        {
          warning(ref, "Mismatch between USX and vernacular content")
          // MiscellaneousUtils.recordTagChange(ref, "_X_contentOnly", null, "Mismatch between USX and vernacular content")
          return
        }
      }
    }



    /**************************************************************************/
    Dom.findNodesByName(document, "ref").forEach { validateRef(it) }
  }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                          Update cross-references                       **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Runs over the enhanced USX folder and updates any cross-references in the
  * wake of reversification changes.
  *
  * @param mappings Maps old refKey to new one.
 */

  fun updateCrossReferences (mappings: Map<RefKey, RefKey>)
  {
    if (mappings.isEmpty()) return
    fun process (@Suppress("UNUSED_PARAMETER") filePath: String, document: Document) { updateCrossReferences(document, mappings); }
    BibleBookAndFileMapperEnhancedUsx.iterateOverSelectedFiles(::process)
  }


  /****************************************************************************/
  /**
  * Runs over a given document and updates any cross-references in the wake of
  * reversification changes.
  *
  * I carry out only very rudimentary checks here.  The details are as follows:
  *
  * - I allow for the loc parameter to be a single reference or a range.
  *
  * - If the low reference (or only reference) is not subject to a mapping, I
  *   assume that nothing will be (but do not check).
  *
  * - If the low reference (on ranges) is subject to a mapping but the high is
  *   not, I treat this as an error and change the ref to _X_contentOnly.
  *   However, I don't bother to check any of the other references which may
  *   make up the range.
  *
  * - Otherwise, I update the loc parameter to reflect the mappings.  Note,
  *   though, that I do *not* update the user-visible vernacular text
  *   associated with the ref tag.  This would be difficult, but thankfully
  *   that's not an issue, because I've been requested not to do it.
  *
  * @param mappings Maps old refKey to new one.
  */

  fun updateCrossReferences (document: Document, mappings: Map<RefKey, RefKey>)
  {
    fun oops (refNode: Node)
    {
      warning(refNode, "Cross-ref broken as a result of reversification")
      MiscellaneousUtils.recordTagChange(refNode, "_X_contentOnly", null, "Cross-ref broken as a result of reversification")
    }

    fun processRef (refNode: Node)
    {
      var loc = Dom.getAttribute(refNode, "loc")!!
      val range = RefRange.rdUsx(loc)

      val lowRefKey = range.getLowAsRefKey()
      val lowMapping = mappings[lowRefKey] ?: return // Slight cop out.  I assume that if the low ref isn't mapped, nothing will be; and that if it is, all should be.

      val highRefKey = range.getHighAsRefKey()
      val highMapping = mappings[lowRefKey]
      if (null == highMapping) // If low is mapped, I expect high to be mapped as well.
      {
        oops(refNode)
        return
      }

      loc = Ref.rd(lowMapping).toString()
      if (lowRefKey != highRefKey) loc += "-" + Ref.rd(highMapping).toString()
      Dom.setAttribute(refNode, "loc", loc)
      Dom.setAttribute(refNode, "_X_attributeValueChangedReason", "Revised loc to reflect reversification")
    }

    Dom.findNodesByName(document, "ref").forEach { processRef(it) }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Miscellaneous                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun error (node: Node, text: String)
  {
    Logger.warning(getRefKey(node), text)
  }


  /****************************************************************************/
  private fun warning (node: Node, text: String)
  {
    Logger.warning(getRefKey(node), text)
  }


  /****************************************************************************/
  private fun getRefKey (node: Node): RefKey
  {
    return Ref.rdUsx(Dom.getAttribute(node, "_TEMP_belongsTo")!!).toRefKey()
  }


  /****************************************************************************/
  private var m_CanReadAndWriteVernacular =  ConfigData.getAsBoolean("stepUseVernacularFormats")
}
